package forgeframework.process;

public class ProcessControlBlock {
    private final int pid;
    private ProcessState state;
    private long cpuTimeUsed;

    public ProcessControlBlock(int pid) {
        this.pid = pid;
        this.state = ProcessState.NEW;
        this.cpuTimeUsed = 0;
    }

    public int getPid() { return pid; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { this.state = state; }
    public long getCpuTimeUsed() { return cpuTimeUsed; }
    public void incrementCpuTime() { this.cpuTimeUsed++; }
}