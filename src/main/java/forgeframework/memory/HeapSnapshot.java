package forgeframework.memory;

/**
 * 프로세스 한 개의 힙 사용 현황 요약. {@code meminfo} 명령 출력에 사용된다.
 */
public record HeapSnapshot(int pid, long capacity, long used, long free) {
}
