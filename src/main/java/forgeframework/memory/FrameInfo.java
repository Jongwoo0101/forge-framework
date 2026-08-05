package forgeframework.memory;

/**
 * {@code frametable} 명령이 반환하는 프레임 하나의 상태.
 */
public record FrameInfo(int frameNumber, boolean allocated, int ownerPid, int pageNumber) {
}
