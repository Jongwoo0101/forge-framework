package forgeframework.filesystem;

/**
 * 파일 시스템 전체의 메타데이터를 담는 슈퍼 블록.
 */
public final class SuperBlock {

    private final int totalBlocks;
    private final int blockSize;
    private final int totalInodes;
    private final int rootInodeNumber;

    public SuperBlock(int totalBlocks, int blockSize, int totalInodes, int rootInodeNumber) {
        this.totalBlocks = totalBlocks;
        this.blockSize = blockSize;
        this.totalInodes = totalInodes;
        this.rootInodeNumber = rootInodeNumber;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getTotalInodes() {
        return totalInodes;
    }

    public int getRootInodeNumber() {
        return rootInodeNumber;
    }
}
