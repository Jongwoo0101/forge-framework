package forgeframework.filesystem;

/**
 * inode 번호와 데이터 블록 번호 둘 다에 재사용되는 범용 할당 비트맵.
 *
 * <p>어떤 인덱스가 비어있는지 순차 탐색으로 찾아 할당한다(first-fit).
 * 실패 시 예외 대신 -1을 반환한다 — 호출부(FileSystemManager)가 롤백 여부를
 * 판단해야 하는 경우가 많아서, 예외보다는 값으로 실패를 전달하는 쪽이 더 유연하다.</p>
 */
public final class Bitmap {

    private final boolean[] used;

    public Bitmap(int size) {
        this.used = new boolean[size];
    }

    /**
     * 비어있는 인덱스 하나를 찾아 사용 중으로 표시한다.
     *
     * @return 할당된 인덱스, 남은 공간이 없으면 -1
     */
    public int allocate() {
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    public void free(int index) {
        used[index] = false;
    }

    public boolean isUsed(int index) {
        return used[index];
    }

    public int size() {
        return used.length;
    }

    public int getUsedCount() {
        int count = 0;
        for (boolean b : used) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public int getFreeCount() {
        return used.length - getUsedCount();
    }
}