package forgeframework.filesystem;

/**
 * {@code cat} 명령의 반환 DTO.
 */
public record FileContentDto(String name, String content) {
}
