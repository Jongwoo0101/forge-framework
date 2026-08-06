package forgeframework.process.scheduler;

import forgeframework.process.Process;
import java.util.LinkedList;
import java.util.Queue;

/**
 * First Come, First Served (FCFS) 스케줄러 구현체.
 *
 * <p>먼저 도착한 프로세스를 먼저 실행하며, 비선점형(Non-preemptive)으로 동작한다.
 * 프로세스가 스스로 종료되거나 I/O를 요청하기 전까지 CPU를 반환하지 않는다.</p>
 */
public class FcfsScheduler implements Scheduler {

    private final Queue<Process> readyQueue = new LinkedList<>();

    @Override
    public String getName() {
        return "FCFS (First Come First Served)";
    }

    @Override
    public void addProcess(Process process) {
        readyQueue.offer(process);
    }

    @Override
    public Process selectNextProcess() {
        return readyQueue.poll();
    }

    @Override
    public void removeProcess(Process process) {
        readyQueue.remove(process);
    }

    @Override
    public boolean isEmpty() {
        return readyQueue.isEmpty();
    }

    @Override
    public boolean isPreemptive() {
        return false; // 비선점형 스케줄러
    }
}
