package forgeframework.filesystem;

import java.util.Arrays;

/**
 * disk.img 역할을 하는 가상 디스크.
 *
 * <p>고정 크기의 데이터 블록 배열({@code byte[][]})로 구성된다. 실제 파일
 * 내용은 문자열을 바이트로 변환해 블록 단위로 저장/조회된다.</p>
 */
public final class VirtualDisk {

    private final byte[][] blocks;
    private final int blockSize;

    public VirtualDisk(int totalBlocks, int blockSize) {
        this.blockSize = blockSize;
        this.blocks = new byte[totalBlocks][blockSize];
    }

    /**
     * 블록 하나를 읽는다. 방어적 복사본을 반환한다.
     *
     * @param blockNumber 읽을 블록 번호
     * @return 블록 내용의 복사본 (길이는 항상 blockSize)
     */
    public byte[] readBlock(int blockNumber) {
        return blocks[blockNumber].clone();
    }

    /**
     * 블록 하나에 데이터를 쓴다. data가 blockSize보다 짧으면 나머지는 0으로 채운다.
     *
     * @param blockNumber 쓸 블록 번호
     * @param data        기록할 데이터 (blockSize 이하여야 함)
     */
    public void writeBlock(int blockNumber, byte[] data) {
        byte[] target = blocks[blockNumber];
        Arrays.fill(target, (byte) 0);
        System.arraycopy(data, 0, target, 0, Math.min(data.length, blockSize));
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getTotalBlocks() {
        return blocks.length;
    }
}
