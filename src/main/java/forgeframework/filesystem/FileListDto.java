package forgeframework.filesystem;

import java.util.List;

/**
 * {@code ls} 명령의 최종 반환 DTO. currentPath는 상대경로/CWD를 반영해
 * 해석된 절대경로다.
 */
public record FileListDto(String currentPath, List<DirectoryEntryDto> entries) {
}
