package forgeframework.memory;

/**
 * 힙 안의 연속된 바이트 구간 하나. 할당된 블록이거나 자유 블록이다.
 *
 * <p>{@link Heap} 내부 구현 세부사항이라 패키지 외부에는 노출하지 않는다 — meminfo 등
 * 외부에 보여줄 정보는 {@link HeapSnapshot}처럼 요약된 형태로만 제공한다.</p>
 */
final class HeapBlock {

    private final long startAddress;
    private long size;
    private boolean free;

    HeapBlock(long startAddress, long size, boolean free) {
        this.startAddress = startAddress;
        this.size = size;
        this.free = free;
    }

    long getStartAddress() {
        return startAddress;
    }

    long getSize() {
        return size;
    }

    void setSize(long size) {
        this.size = size;
    }

    boolean isFree() {
        return free;
    }

    void setFree(boolean free) {
        this.free = free;
    }
}
