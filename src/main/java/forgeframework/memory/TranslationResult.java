package forgeframework.memory;

/**
 * 가상 주소 → 물리 주소 변환 결과.
 *
 * @param tlbHit TLB에서 바로 찾았으면 true, PageTable까지 조회했으면 false(miss)
 */
public record TranslationResult(
        long virtualAddress,
        int pageNumber,
        int frameNumber,
        long physicalAddress,
        boolean tlbHit
) {
}
