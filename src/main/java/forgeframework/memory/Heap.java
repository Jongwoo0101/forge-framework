package forgeframework.memory;

import forgeframework.exception.ForgeOSException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 프로세스 하나의 힙 영역을 관리하는 free-list 기반 first-fit 할당기.
 * <p>실제 sbrk/brk처럼, 필요한 만큼만 커지고 free()해도 곧바로 OS에 반납하지는
 * 않는다(내부적으로 free 블록으로 남아 재사용을 기다린다). 물리 프레임/페이지
 * 할당 같은 저수준 작업은 이 클래스가 알지 못한다 — {@link MemoryManager}가
 * {@link #grow(long)}를 통해 필요한 만큼 용량을 늘려주는 식으로 계층을 분리했다.</p>
 */
public final class Heap {

    private final List<HeapBlock> blocks = new ArrayList<>();
    private long capacity = 0;

    public long getCapacity() {
        return capacity;
    }

    public long getUsedBytes() {
        long used = 0;
        for (HeapBlock block : blocks) {
            if (!block.isFree()) {
                used += block.getSize();
            }
        }
        return used;
    }

    public long getFreeBytes() {
        return capacity - getUsedBytes();
    }

    /**
     * 힙의 총 용량을 늘린다. 늘어난 구간은 하나의 자유 블록으로 추가된다.
     * 물리 프레임을 실제로 확보하는 것은 {@link MemoryManager}의 책임이며,
     * 이 메서드는 순수하게 "장부 상의 용량"만 늘린다.
     * @param additionalBytes 추가할 바이트 수
     */
    public void grow(long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        blocks.add(new HeapBlock(capacity, additionalBytes, true));
        capacity += additionalBytes;
        mergeAdjacentFreeBlocks();
    }

    /**
     * Heap 객체에서 마지막 블록이 Free 상태라면 그 크기를 반환하는 메서드를 만들고, {@link MemoryManager}는
     * 그 크기를 뺀 순수하게 부족한 크기만큼만 페이지를 요청해야함
     */
    public long getEndFreeSize() {
        if (!blocks.isEmpty()) {
            HeapBlock last = blocks.getLast();
            if (last.isFree()) {
                return last.getSize();
            }
        }
        return 0;
    }

    /**
     * first-fit으로 size바이트를 할당한다. 기존 자유 블록 중 맞는 게 없으면
     * null을 반환한다 — 이 경우 호출자({@link MemoryManager})가 {@link #grow}로
     * 용량을 늘린 뒤 다시 시도해야 한다.
     *
     * @param size 요청 크기
     * @return 할당된 블록의 시작 주소, 공간이 없으면 null
     */
    public Long allocate(long size) {
        for (int i = 0; i < blocks.size(); i++) {
            HeapBlock block = blocks.get(i);
            if (block.isFree() && block.getSize() >= size) {
                long address = block.getStartAddress();
                if (block.getSize() == size) {
                    block.setFree(false);
                } else {
                    HeapBlock allocated = new HeapBlock(address, size, false);
                    HeapBlock remainder = new HeapBlock(address + size, block.getSize() - size, true);
                    blocks.remove(i);
                    blocks.add(i, remainder);
                    blocks.add(i, allocated);
                }
                return address;
            }
        }
        return null;
    }

    /**
     * 주소로 블록을 찾아 해제하고, 인접한 자유 블록과 병합한다.
     *
     * @param address 해제할 블록의 시작 주소
     * @throws ForgeOSException 해당 주소에 할당된 블록이 없는 경우
     */
    public void free(long address) {
        for (HeapBlock block : blocks) {
            if (block.getStartAddress() == address && !block.isFree()) {
                block.setFree(true);
                mergeAdjacentFreeBlocks();
                return;
            }
        }
        throw new ForgeOSException("잘못된 주소이거나 이미 해제된 블록입니다: " + address);
    }

    private void mergeAdjacentFreeBlocks() {
        blocks.sort(Comparator.comparingLong(HeapBlock::getStartAddress));
        for (int i = 0; i < blocks.size() - 1; ) {
            HeapBlock current = blocks.get(i);
            HeapBlock next = blocks.get(i + 1);
            if (current.isFree() && next.isFree()) {
                current.setSize(current.getSize() + next.getSize());
                blocks.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /**
     * 테스트/디버깅 용도로 현재 블록 목록의 읽기 전용 뷰를 제공한다.
     */
    List<HeapBlock> getBlocksView() {
        return Collections.unmodifiableList(blocks);
    }
}
