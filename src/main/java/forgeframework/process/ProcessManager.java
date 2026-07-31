package forgeframework.process;

import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.process.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 프로세스의 생성, 상태 전이, 문맥 교환(Context Switch)을 관리하는 매니저.
 */
public class ProcessManager {
    private final EventLogger logger;
    private final Scheduler scheduler;

    private final Map<Integer, Process> processTable = new LinkedHashMap<>();
    private final AtomicInteger pidGenerator = new AtomicInteger(1);

    private Process currentRunningProcess = null;

    // 선점형 스케줄러용 타임 퀀텀 (3 Ticks)
    private final int timeQuantum = 3;
    private int quantumTick = 0;

    public ProcessManager(EventLogger logger, Scheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
        logger.log(LogLevel.INFO, "ProcessManager initialized [Scheduler: " + scheduler.getName() + "]");
    }

    public synchronized Process createProcess(String name) {
        int pid = pidGenerator.getAndIncrement();
        Process newProcess = new Process(pid, name);

        processTable.put(pid, newProcess);
        newProcess.getPcb().setState(ProcessState.READY);
        scheduler.addProcess(newProcess);

        logger.log(LogLevel.INFO, "Process created: [PID=" + pid + "] " + name);
        return newProcess;
    }

    public synchronized boolean killProcess(int pid) {
        Process target = processTable.get(pid);
        if (target == null || target.getPcb().getState() == ProcessState.TERMINATED) {
            return false;
        }

        target.getPcb().setState(ProcessState.TERMINATED);

        if (currentRunningProcess != null && currentRunningProcess.getPcb().getPid() == pid) {
            currentRunningProcess = null;
        } else {
            scheduler.removeProcess(target);
        }

        logger.log(LogLevel.WARN, "Process killed: [PID=" + pid + "]");
        return true;
    }

    public Map<Integer, Process> getAllProcesses() {
        return processTable;
    }

    /**
     * Kernel로부터 Timer Interrupt가 발생했을 때 호출된다.
     */
    public synchronized void handleTimerInterrupt() {
        if (currentRunningProcess != null) {
            currentRunningProcess.getPcb().incrementCpuTime();
            quantumTick++;

            // 스케줄러가 선점형(Preemptive)일 때만 타임 퀀텀 검사 후 강제 교환
            if (scheduler.isPreemptive() && quantumTick >= timeQuantum) {
                contextSwitch();
            }
        } else {
            // 실행 중인 프로세스가 없으면 Ready Queue를 확인하여 스케줄링
            if (!scheduler.isEmpty()) {
                contextSwitch();
            }
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