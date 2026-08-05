package forgeframework.memory;

/**
 * 물리 메모리의 최소 단위인 프레임 하나.
 *
 * <p>어느 프로세스가 이 프레임을 점유하고 있는지(ownerPid)뿐 아니라, 그 프로세스의
 * 어느 가상 페이지에 매핑되어 있는지(pageNumber)도 함께 들고 있다. 이 두 정보 덕분에
 * "프레임 번호만 가지고 역으로 소유자/페이지를 즉시 찾는" Frame Table 조회가 가능하다.</p>
 */
public final class Frame {

    private final int frameNumber;
    private boolean allocated;
    private int ownerPid = -1;
    private int pageNumber = -1;

    public Frame(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public int getOwnerPid() {
        return ownerPid;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    void allocate(int pid, int pageNumber) {
        this.allocated = true;
        this.ownerPid = pid;
        this.pageNumber = pageNumber;
    }

    void release() {
        this.allocated = false;
        this.ownerPid = -1;
        this.pageNumber = -1;
    }
}