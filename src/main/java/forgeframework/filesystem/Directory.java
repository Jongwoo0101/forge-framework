package forgeframework.filesystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 디렉터리 하나가 담고 있는 엔트리(이름 → inode 번호) 목록.
 *
 * <p>실제 파일 시스템이라면 이 매핑 자체도 데이터 블록에 직렬화되어야 하지만,
 * 지금 단계에서는 시뮬레이션의 편의를 위해 자바 객체로 메모리에 유지한다
 * (inode의 DIRECTORY 타입과 1:1로 대응하는 보조 자료구조로
 * {@link FileSystemManager}가 관리). {@code LinkedHashMap}을 써서 생성
 * 순서가 {@code ls}/{@code tree} 출력에 유지되도록 했다.</p>
 */
public final class Directory {

    private final Map<String, Integer> entries = new LinkedHashMap<>();

    public void addEntry(String name, int inodeNumber) {
        entries.put(name, inodeNumber);
    }

    public void removeEntry(String name) {
        entries.remove(name);
    }

    public Integer resolve(String name) {
        return entries.get(name);
    }

    public Map<String, Integer> getEntries() {
        return Collections.unmodifiableMap(entries);
    }
}
