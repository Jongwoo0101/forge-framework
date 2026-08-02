package forgeframework.process;

public class Process {
    private final String name;
    private final ProcessControlBlock pcb;

    public Process(int pid, String name, long burstTime) {
        this.name = name;
        this.pcb = new ProcessControlBlock(pid, burstTime);
    }

    public String getName() { return name; }
    public ProcessControlBlock getPcb() { return pcb; }
}
