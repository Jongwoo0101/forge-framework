package forgeframework.process.scheduler;

import forgeframework.process.Process;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Round Robin (RR) 스케줄러 구현체.
 *
 * <p>FIFO 큐를 사용하며, 선점형(Preemptive)으로 동작한다.
 * 타임 퀀텀 만료 시 ProcessManager에 의해 다시 큐로 돌아온다.</p>
 */
public class RoundRobinScheduler implements Scheduler {

    private final Queue<Process> readyQueue = new LinkedList<>();

    @Override
    public String getName() {
        return "Round Robin (RR)";
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
        return true; // 선점형 스케줄러
    }
}
