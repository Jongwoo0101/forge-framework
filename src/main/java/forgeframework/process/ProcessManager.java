package forgeframework.process;

import forgeframework.common.ForgeOSConstants;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.process.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * 프로세스의 생성, 상태 전이, 문맥 교환(Context Switch)을 관리하는 매니저.
 */
public class ProcessManager {
    private final EventLogger logger;
    private Scheduler scheduler;

    private final Map<Integer, Process> processTable = new LinkedHashMap<>();
    private final AtomicInteger pidGenerator = new AtomicInteger(1);

    private Process currentRunningProcess = null;

    /**
     * 프로세스가 종료(kill 또는 burst 완료로 인한 자연 종료)될 때 통지받는 리스너.
     * MemoryManager가 프로세스 종료 시 힙/페이지 테이블/TLB 항목을 회수할 수 있도록
     * BootManager가 연결해준다. ProcessManager는 MemoryManager의 존재 자체를 몰라도
     * 되도록(패키지 의존 방향을 process → memory로 만들지 않기 위해) 이런 콜백 형태로
     * 분리했다.
     */
    private IntConsumer terminationListener = pid -> { };

    // 선점형 스케줄러용 타임 퀀텀
    private final int timeQuantum = ForgeOSConstants.DEFAULT_TIME_QUANTUM;
    private int quantumTick = 0;

    public ProcessManager(EventLogger logger, Scheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
        logger.log(LogLevel.INFO, "ProcessManager initialized [Scheduler: " + scheduler.getName() + "]");
    }

    /**
     * 프로세스 종료 시 통지받을 리스너를 등록한다.
     *
     * @param listener 종료된 프로세스의 pid를 전달받을 콜백. null이면 아무 동작도 하지 않는 no-op으로 대체된다.
     */
    public void setTerminationListener(IntConsumer listener) {
        this.terminationListener = (listener != null) ? listener : (pid -> { });
    }

    /**
     * 새 프로세스를 생성한다.
     *
     * @param name      프로세스 이름
     * @param burstTime 총 필요 실행 시간(tick). 이 시간만큼 CPU를 사용하면 자동으로 종료된다.
     * @return 생성된 프로세스
     */
    public synchronized Process createProcess(String name, long burstTime) {
        int pid = pidGenerator.getAndIncrement();
        Process newProcess = new Process(pid, name, burstTime);

        processTable.put(pid, newProcess);
        newProcess.getPcb().setState(ProcessState.NEW);
        // 이 아래에서 addProcess 호출 하면 x. 메모리 할당 등 준비가 덜 끝난 new 상태이기 때문
        // scheduler.addProcess(newProcess);

        logger.log(LogLevel.INFO,
                "Process created: [PID=" + pid + "] " + name + " (burstTime=" + burstTime + ")");
        return newProcess;
    }

    public synchronized void readyProcess(int pid) {
        Process p = processTable.get(pid);
        p.getPcb().setState(ProcessState.READY);
        scheduler.addProcess(p);
    }

    /**
     * burstTime을 생략하고 기본값으로 프로세스를 생성한다.
     *
     * @param name 프로세스 이름
     * @return 생성된 프로세스
     */
    public synchronized Process createProcess(String name) {
        return createProcess(name, ForgeOSConstants.DEFAULT_BURST_TIME);
    }

    /**
     * 현재 적용된 스케줄러의 이름을 반환한다 (scheduler 명령의 조회용).
     *
     * @return 스케줄러 이름
     */
    public synchronized String getSchedulerName() {
        return scheduler.getName();
    }

    /**
     * 스케줄링 알고리즘을 런타임에 교체한다.
     *
     * <p>기존 스케줄러의 ready queue에 남아있던 프로세스는 그대로 잃어버리지 않도록
     * 새 스케줄러로 옮겨준다. 현재 CPU를 사용 중인 프로세스는 교체와 무관하게
     * 계속 실행되며, 다음 Context Switch부터 새 알고리즘이 적용된다.</p>
     *
     * @param newScheduler 새로 적용할 스케줄러
     */
    public synchronized void setScheduler(Scheduler newScheduler) {
        while (!scheduler.isEmpty()) {
            newScheduler.addProcess(scheduler.selectNextProcess());
        }
        this.scheduler = newScheduler;
        logger.log(LogLevel.INFO, "Scheduler changed to: " + newScheduler.getName());
    }

    public synchronized boolean killProcess(int pid) {
        Process target = processTable.get(pid);
        if (target == null || target.getPcb().getState() == ProcessState.TERMINATED) {
            return false;
        }

        target.getPcb().setState(ProcessState.TERMINATED);

        if (currentRunningProcess != null && currentRunningProcess.getPcb().getPid() == pid) {
            currentRunningProcess = null;
            quantumTick = 0;
            if (!scheduler.isEmpty()) {
                contextSwitch();
            }
        } else {
            scheduler.removeProcess(target);
        }

        logger.log(LogLevel.WARN, "Process killed: [PID=" + pid + "]");
        terminationListener.accept(pid);
        return true;
    }

    public Map<Integer, Process> getAllProcesses() {
        return processTable;
    }

    /**
     * Kernel로부터 Timer Interrupt가 발생했을 때 호출된다.
     *
     * <p><b>[버그 수정]</b> 기존 코드는 {@code scheduler.isPreemptive()}가 false인 경우
     * (FCFS) burst 완료 여부와 무관하게 절대 contextSwitch()를 호출하지 않아서,
     * 최초 실행된 프로세스가 영원히 CPU를 독점하고 나머지 프로세스는 기아 상태에
     * 빠지는 문제가 있었다. burst 완료 여부는 스케줄러의 선점 정책과 별개로
     * 항상 먼저 확인하도록 순서를 바꿨다 — "일을 다 끝낸 프로세스는 선점형이든
     * 아니든 CPU를 반납해야 한다"는 것은 스케줄링 알고리즘과 무관한 규칙이기 때문이다.</p>
     */
    public synchronized void handleTimerInterrupt() {
        if (currentRunningProcess != null) {
            currentRunningProcess.getPcb().incrementCpuTime();
            quantumTick++;

            if (currentRunningProcess.getPcb().isBurstComplete()) {
                completeCurrentProcess();
                return;
            }

            if (scheduler.isPreemptive() && quantumTick >= timeQuantum) {
                contextSwitch();
            }
        } else if (!scheduler.isEmpty()) {
            contextSwitch();
        }
    }

    /**
     * 현재 실행 중인 프로세스가 burstTime을 모두 소진했을 때 호출된다.
     * TERMINATED로 전이시키고, ready queue에 남은 프로세스가 있으면 즉시 다음
     * 프로세스로 문맥을 교환한다.
     */
    private void completeCurrentProcess() {
        Process finished = currentRunningProcess;
        finished.getPcb().setState(ProcessState.TERMINATED);
        logger.log(LogLevel.INFO,
                "Process completed: [PID=" + finished.getPcb().getPid() + "] " + finished.getName());
        terminationListener.accept(finished.getPcb().getPid());

        currentRunningProcess = null;
        quantumTick = 0;

        if (!scheduler.isEmpty()) {
            contextSwitch();
        }
    }

    private void contextSwitch() {
        Process prevProcess = currentRunningProcess;

        if (prevProcess != null && prevProcess.getPcb().getState() == ProcessState.RUNNING) {
            prevProcess.getPcb().setState(ProcessState.READY);
            scheduler.addProcess(prevProcess);
        }

        Process nextProcess = scheduler.selectNextProcess();

        if (nextProcess != null) {
            nextProcess.getPcb().setState(ProcessState.RUNNING);
            currentRunningProcess = nextProcess;
            quantumTick = 0;

            if (prevProcess == null || prevProcess.getPcb().getPid() != nextProcess.getPcb().getPid()) {
                logger.log(LogLevel.DEBUG, "Context Switch: -> [" + nextProcess.getName() + "]");
            }
        } else {
            currentRunningProcess = null;
        }
    }
}
