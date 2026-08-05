package forgeframework.memory;

/**
 * 프로세스 하나의 가상 주소 공간 전체를 표현한다.
 *
 * <p>지금까지는 {@link MemoryManager}가 {@code Map<Integer, Heap>}과
 * {@code Map<Integer, PageTable>} 두 개의 별도 Map을 병렬로 관리했는데, 이 둘은
 * 항상 같은 pid에 대해 함께 생성되고 함께 제거되는 "한 프로세스의 가상 메모리"라는
 * 하나의 개념이다. 두 Map을 따로 관리하면 이론적으로 한쪽만 등록/해제되는 불일치가
 * 생길 여지가 있는데, 이 클래스로 묶어서 pid 하나당 Map 엔트리 하나만 존재하도록
 * 단순화했다.</p>
 *
 * <p>추후 Stack이 추가되면 이 클래스가 Heap과 함께 Stack도 소유하게 될 것이다
 * (지금은 Phase 3.5 범위에서 제외).</p>
 */
public final class VirtualAddressSpace {

    private final int pid;
    private final Heap heap = new Heap();
    private final PageTable pageTable = new PageTable();

    public VirtualAddressSpace(int pid) {
        this.pid = pid;
    }

    public int getPid() {
        return pid;
    }

    public Heap getHeap() {
        return heap;
    }

    public PageTable getPageTable() {
        return pageTable;
    }
}
