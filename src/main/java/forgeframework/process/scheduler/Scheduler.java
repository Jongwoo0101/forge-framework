package forgeframework.process.scheduler;

import forgeframework.process.Process;

/**
 * CPU 스케줄링 알고리즘의 전략(Strategy) 인터페이스.
 */
public interface Scheduler {

    /**
     * 스케줄러의 이름을 반환한다. (예: "FCFS", "Round Robin")
     */
    String getName();

    /**
     * 새로운 프로세스를 Ready Queue에 추가한다.
     * @param process 추가할 프로세스
     */
    void addProcess(Process process);

    /**
     * 다음으로 실행할 프로세스를 선택하여 반환한다.
     * @return 실행할 프로세스 (없으면 null)
     */
    Process selectNextProcess();

    /**
     * 프로세스를 큐에서 제거한다. (종료 등)
     * @param process 제거할 프로세스
     */
    void removeProcess(Process process);

    /**
     * 현재 스케줄링 큐가 비어있는지 확인한다.
     */
    boolean isEmpty();

    /**
     * 해당 스케줄러가 선점형(Preemptive)인지 여부를 반환한다.
     * @return 선점형이면 true, 비선점형이면 false
     */
    boolean isPreemptive();
}
