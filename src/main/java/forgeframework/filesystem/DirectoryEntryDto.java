package forgeframework.filesystem;

/**
 * {@code ls} 결과에서 파일/폴더 하나를 나타내는 DTO.
 */
public record DirectoryEntryDto(String name, String type, int size) {
}
