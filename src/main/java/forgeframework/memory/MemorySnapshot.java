package forgeframework.memory;

import java.util.Map;

/**
 * {@code meminfo} 명령이 반환하는 전체 스냅샷.
 *
 * <p>포맷팅(표 형태로 예쁘게 출력하는 것)은 이 클래스의 책임이 아니라
 * {@code MeminfoCommand}(Shell 계층)의 책임이다 — Kernel/MemoryManager는
 * 순수 데이터만 반환한다.</p>
 */
public record MemorySnapshot(
        int totalFrames,
        int frameSize,
        int usedFrames,
        int freeFrames,
        Map<Integer, HeapSnapshot> heapByPid,
        long tlbHits,
        long tlbMisses,
        double tlbHitRatio
) {
}
