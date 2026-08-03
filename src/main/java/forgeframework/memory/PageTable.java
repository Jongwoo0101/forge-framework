package forgeframework.memory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 프로세스 한 개의 가상 페이지 번호 → 물리 프레임 번호 매핑을 관리하는 페이지 테이블.
 *
 * <p>단순화를 위해 valid bit를 별도 필드로 두지 않고, "매핑이 Map에 없으면 invalid"로
 * 취급한다. Phase 3에는 스왑(디스크)이 없어 페이지가 물리 메모리에 없는 상태 자체가
 * 존재하지 않으므로(항상 즉시 할당), 이 단순화로 충분하다.</p>
 */
public final class PageTable {

    private final Map<Integer, Integer> mapping = new HashMap<>();

    public void map(int pageNumber, int frameNumber) {
        mapping.put(pageNumber, frameNumber);
    }

    public void unmap(int pageNumber) {
        mapping.remove(pageNumber);
    }

    /**
     * @param pageNumber 조회할 가상 페이지 번호
     * @return 매핑된 프레임 번호, 매핑이 없으면 null
     */
    public Integer translate(int pageNumber) {
        return mapping.get(pageNumber);
    }

    public Set<Integer> mappedPages() {
        return mapping.keySet();
    }

    public Collection<Integer> mappedFrames() {
        return mapping.values();
    }
}
