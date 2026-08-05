package forgeframework.memory;

import java.util.List;

/**
 * 프레임 번호 → (소유 프로세스, 매핑된 가상 페이지 번호)를 즉시 역조회할 수 있는
 * 프레임 테이블.
 *
 * <p>배열 인덱스 자체가 프레임 번호이므로 조회는 항상 O(1)이다. {@link PhysicalMemory}는
 * 이 클래스를 감싸서(composition) 기존 공개 API(allocateFrame/freeFrame 등)를
 * 그대로 유지하고, 역조회가 필요한 곳({@code frametable} 명령 등)에서는 이 클래스를
 * 직접 사용한다.</p>
 */
public final class FrameTable {

    private final Frame[] frames;
    private final int frameSize;

    public FrameTable(int totalFrames, int frameSize) {
        this.frameSize = frameSize;
        this.frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            frames[i] = new Frame(i);
        }
    }

    /**
     * 비어있는 프레임 하나를 찾아 pid의 pageNumber에 할당한다.
     *
     * @param pid        프레임을 사용할 프로세스의 pid
     * @param pageNumber 이 프레임이 매핑될 가상 페이지 번호
     * @return 할당된 프레임, 남은 프레임이 없으면 null
     */
    public Frame allocate(int pid, int pageNumber) {
        for (Frame frame : frames) {
            if (!frame.isAllocated()) {
                frame.allocate(pid, pageNumber);
                return frame;
            }
        }
        return null;
    }

    public void free(int frameNumber) {
        frames[frameNumber].release();
    }

    /**
     * 프레임 번호로 소유자/페이지 정보를 즉시 역조회한다 — 이게 Frame Table의 핵심 기능이다.
     *
     * @param frameNumber 조회할 프레임 번호
     * @return 해당 프레임 객체 (할당 여부, 소유 pid, 페이지 번호를 담고 있음)
     */
    public Frame lookup(int frameNumber) {
        return frames[frameNumber];
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getTotalFrames() {
        return frames.length;
    }

    public int getUsedCount() {
        int count = 0;
        for (Frame frame : frames) {
            if (frame.isAllocated()) {
                count++;
            }
        }
        return count;
    }

    public int getFreeCount() {
        return frames.length - getUsedCount();
    }

    public List<Frame> getAllFrames() {
        return List.of(frames);
    }
}
