package forgeframework.memory;

/**
 * 물리 메모리의 최소 단위인 프레임 하나.
 *
 * <p>어느 프로세스가 이 프레임을 점유하고 있는지(ownerPid)를 함께 들고 있어서,
 * 프로세스가 종료될 때 자신이 소유한 프레임을 일괄 회수할 수 있게 한다.</p>
 */
public final class Frame {

    private final int frameNumber;
    private boolean allocated;
    private int ownerPid = -1;

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

    void allocate(int pid) {
        this.allocated = true;
        this.ownerPid = pid;
    }

    void release() {
        this.allocated = false;
        this.ownerPid = -1;
    }
}
