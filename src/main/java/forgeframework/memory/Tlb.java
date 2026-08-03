package forgeframework.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translation Lookaside Buffer.
 *
 * <p>(pid, 가상 페이지 번호) → 물리 프레임 번호 매핑을 캐싱해서, 매번 PageTable을
 * 조회하지 않고도 최근에 쓴 주소를 빠르게 다시 찾을 수 있게 한다. 용량이 다 차면
 * LRU(가장 오래 안 쓴 항목)를 내보낸다.</p>
 *
 * <p>hit/miss 카운터를 함께 들고 있어 {@code meminfo}에서 적중률을 보여줄 수 있다.
 * 프로세스가 종료되면 {@link #invalidateForPid(int)}로 그 프로세스의 항목만
 * 선택적으로 제거한다 (다른 프로세스의 캐시는 유지).</p>
 */
public final class Tlb {

    private final int capacity;
    private final Map<Long, Integer> cache;

    private long hitCount;
    private long missCount;

    public Tlb(int capacity) {
        this.capacity = capacity;
        // accessOrder=true 로 LRU 순서를 유지하고, removeEldestEntry로 용량 초과 시 자동 제거
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                return size() > Tlb.this.capacity;
            }
        };
    }

    private static long key(int pid, int pageNumber) {
        return ((long) pid << 32) | (pageNumber & 0xFFFFFFFFL);
    }

    /**
     * (pid, pageNumber)에 대한 캐시를 조회한다. hit/miss 카운터가 함께 갱신된다.
     *
     * @return 캐시된 프레임 번호, 없으면 null (miss)
     */
    public synchronized Integer lookup(int pid, int pageNumber) {
        Integer frameNumber = cache.get(key(pid, pageNumber));
        if (frameNumber != null) {
            hitCount++;
        } else {
            missCount++;
        }
        return frameNumber;
    }

    public synchronized void put(int pid, int pageNumber, int frameNumber) {
        cache.put(key(pid, pageNumber), frameNumber);
    }

    /**
     * 특정 프로세스에 대한 캐시 항목만 모두 제거한다 (프로세스 종료 시 호출).
     *
     * @param pid 무효화할 프로세스의 pid
     */
    public synchronized void invalidateForPid(int pid) {
        cache.keySet().removeIf(key -> (key >>> 32) == pid);
    }

    public synchronized long getHitCount() {
        return hitCount;
    }

    public synchronized long getMissCount() {
        return missCount;
    }

    public synchronized double getHitRatio() {
        long total = hitCount + missCount;
        return (total == 0) ? 0.0 : (double) hitCount / total;
    }
}
