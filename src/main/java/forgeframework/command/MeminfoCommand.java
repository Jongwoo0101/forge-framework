package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.memory.HeapSnapshot;
import forgeframework.memory.MemorySnapshot;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 물리 메모리 / 프로세스별 힙 / TLB 사용 현황을 출력하는 명령어.
 *
 * <p>Kernel/MemoryManager는 {@link MemorySnapshot}이라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — PsCommand와 동일한
 * 원칙을 따른다.</p>
 */
public final class MeminfoCommand implements Command {

    @Override
    public String name() {
        return "meminfo";
    }

    @Override
    public String description() {
        return "물리 메모리/힙/TLB 사용 현황을 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(SystemCallType.MEMINFO));
        if (!result.isSuccess()) {
            return result;
        }

        MemorySnapshot snapshot = (MemorySnapshot) result.getData();
        StringBuilder sb = new StringBuilder();

        sb.append("[Physical Memory]\n");
        sb.append(String.format(
                "Frame Size: %d, Total Frames: %d (Total: %d bytes)%n",
                snapshot.frameSize(), snapshot.totalFrames(),
                (long) snapshot.frameSize() * snapshot.totalFrames()
        ));
        sb.append(String.format(
                "Used Frames: %d, Free Frames: %d%n",
                snapshot.usedFrames(), snapshot.freeFrames()
        ));

        sb.append("\n[Process Heap]\n");
        if (snapshot.heapByPid().isEmpty()) {
            sb.append("등록된 프로세스가 없습니다.\n");
        } else {
            sb.append(String.format("%-5s | %-10s | %-10s | %s%n", "PID", "CAPACITY", "USED", "FREE"));
            for (HeapSnapshot heap : snapshot.heapByPid().values()) {
                sb.append(String.format(
                        "%-5d | %-10d | %-10d | %d%n",
                        heap.pid(), heap.capacity(), heap.used(), heap.free()
                ));
            }
        }

        sb.append("\n[TLB]\n");
        sb.append(String.format(
                "Hits: %d, Misses: %d, Hit Ratio: %.1f%%",
                snapshot.tlbHits(), snapshot.tlbMisses(), snapshot.tlbHitRatio() * 100
        ));

        return SystemCallResult.success(sb.toString());
    }
}
