package forgeframework.filesystem;

import java.util.List;

/**
 * {@code tree} 명령용 재귀적 트리 구조 DTO. 렌더링(├──/└── 등)은 전적으로
 * TreeCommand(Shell 계층)의 책임이다.
 */
public record TreeNodeDto(String name, boolean isDirectory, List<TreeNodeDto> children) {
}
