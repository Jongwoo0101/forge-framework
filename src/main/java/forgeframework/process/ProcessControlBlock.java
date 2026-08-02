package forgeframework.process;

/**
 * Process Control Block.
 *
 * <p><b>[버그 수정]</b> 기존 구현에는 burstTime(총 필요 실행 시간) 개념이 없어서
 * 프로세스가 CPU를 할당받으면 영원히 RUNNING 상태에 머물렀다. FCFS는 선점을
 * 하지 않기 때문에, 이 경우 맨 처음 실행된 프로세스가 CPU를 절대 반납하지 않고
 * 이후 생성된 모든 프로세스가 기아(starvation) 상태에 빠지는 치명적 버그로
 * 이어졌다. burstTime을 추가하고, 누적 실행 시간이 burstTime에 도달하면
 * 완료로 판단할 수 있도록 {@link #isBurstComplete()}를 제공한다.</p>
 *
 * <p><b>[버그 수정]</b> {@code state}와 {@code cpuTimeUsed}는 HardwareTimer의
 * 백그라운드 스레드(쓰기)와 Shell의 메인 스레드(ps 명령 등, 읽기)가 동시에
 * 접근한다. 두 필드 모두 {@code volatile}로 선언하지 않으면 자바 메모리 모델상
 * 한 스레드의 변경이 다른 스레드에 즉시 보이지 않을 수 있다(가시성 문제).
 * 쓰기는 여전히 ProcessManager의 synchronized 메서드 안에서만 일어나므로
 * volatile만으로 가시성 문제는 충분히 해결된다.</p>
 */
public class ProcessControlBlock {

    private final int pid;
    private final long burstTime;

    private volatile ProcessState state;
    private volatile long cpuTimeUsed;

    public ProcessControlBlock(int pid, long burstTime) {
        this.pid = pid;
        this.burstTime = burstTime;
        this.state = ProcessState.NEW;
        this.cpuTimeUsed = 0;
    }

    public int getPid() {
        return pid;
    }

    public long getBurstTime() {
        return burstTime;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public long getCpuTimeUsed() {
        return cpuTimeUsed;
    }

    public void incrementCpuTime() {
        this.cpuTimeUsed++;
    }

    /**
     * 누적 실행 시간이 burstTime에 도달했는지 여부.
     *
     * @return 프로세스가 필요한 만큼 다 실행되었으면 true
     */
    public boolean isBurstComplete() {
        return cpuTimeUsed >= burstTime;
    }
}
