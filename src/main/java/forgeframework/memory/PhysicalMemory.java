package forgeframework.memory;

/**
 * 시뮬레이션할 물리 메모리 전체.
 *
 * <p>실제 프레임 배열/할당 로직은 {@link FrameTable}에 위임한다(composition).
 * "물리 메모리"와 "프레임 테이블"은 실제 OS 교재에서도 사실상 같은 자료구조를
 * 가리키는 경우가 많은데, 여기서는 프레임 번호로 소유자/페이지를 역조회하는 기능을
 * 별도 클래스로 명시적으로 분리해서 노출하기 위해 두 클래스로 나눠두었다. frame 할당은
 * 여전히 first-fit(첫 번째로 비어있는 프레임)으로 수행한다.</p>
 */
public final class PhysicalMemory {

    private final FrameTable frameTable;

    public PhysicalMemory(int totalFrames, int frameSize) {
        this.frameTable = new FrameTable(totalFrames, frameSize);
    }

    /**
     * 비어있는 프레임 하나를 찾아 pid의 pageNumber에 할당한다.
     *
     * @param pid        프레임을 사용할 프로세스의 pid
     * @param pageNumber 이 프레임이 매핑될 가상 페이지 번호 (Frame Table 역조회에 사용됨)
     * @return 할당된 프레임, 남은 프레임이 없으면 null
     */
    public Frame allocateFrame(int pid, int pageNumber) {
        return frameTable.allocate(pid, pageNumber);
    }

    /**
     * 프레임을 반납한다.
     *
     * @param frameNumber 반납할 프레임 번호
     */
    public void freeFrame(int frameNumber) {
        frameTable.free(frameNumber);
    }

    public int getFrameSize() {
        return frameTable.getFrameSize();
    }

    public int getTotalFrames() {
        return frameTable.getTotalFrames();
    }

    public int getUsedFrameCount() {
        return frameTable.getUsedCount();
    }

    public int getFreeFrameCount() {
        return frameTable.getFreeCount();
    }

    /**
     * 프레임 번호로 소유자/페이지를 역조회해야 하는 곳(frametable 명령 등)에서
     * 사용할 FrameTable 참조를 반환한다.
     */
    public FrameTable getFrameTable() {
        return frameTable;
    }
}
