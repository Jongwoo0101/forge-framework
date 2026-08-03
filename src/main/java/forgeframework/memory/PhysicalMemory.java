package forgeframework.memory;

/**
 * 시뮬레이션할 물리 메모리 전체.
 *
 * <p>고정 개수의 {@link Frame} 배열로 표현되며, 실제 RAM처럼 유한한 크기를 갖는다.
 * frame 할당은 first-fit(첫 번째로 비어있는 프레임)으로 수행한다.</p>
 */
public final class PhysicalMemory {

    private final Frame[] frames;
    private final int frameSize;

    public PhysicalMemory(int totalFrames, int frameSize) {
        this.frameSize = frameSize;
        this.frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            frames[i] = new Frame(i);
        }
    }

    /**
     * 비어있는 프레임 하나를 찾아 pid에게 할당한다.
     *
     * @param pid 프레임을 사용할 프로세스의 pid
     * @return 할당된 프레임, 남은 프레임이 없으면 null
     */
    public Frame allocateFrame(int pid) {
        for (Frame frame : frames) {
            if (!frame.isAllocated()) {
                frame.allocate(pid);
                return frame;
            }
        }
        return null;
    }

    /**
     * 프레임을 반납한다.
     *
     * @param frameNumber 반납할 프레임 번호
     */
    public void freeFrame(int frameNumber) {
        frames[frameNumber].release();
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getTotalFrames() {
        return frames.length;
    }

    public int getUsedFrameCount() {
        int count = 0;
        for (Frame frame : frames) {
            if (frame.isAllocated()) {
                count++;
            }
        }
        return count;
    }

    public int getFreeFrameCount() {
        return frames.length - getUsedFrameCount();
    }
}
