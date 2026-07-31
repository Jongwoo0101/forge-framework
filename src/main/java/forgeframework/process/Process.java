package forgeframework.process;

public class Process {
    private final String name;
    private final ProcessControlBlock pcb;

    public Process(int pid, String name) {
        this.name = name;
        this.pcb = new ProcessControlBlock(pid);
    }

    public String getName() { return name; }
    public ProcessControlBlock getPcb() { return pcb; }
}