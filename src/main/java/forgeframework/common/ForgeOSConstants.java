package forgeframework.common;

/**
 * ForgeOS 전역에서 사용되는 상수를 모아둔 클래스.
 *
 * <p>매직 넘버 및 매직 스트링 사용을 방지하기 위해
 * 프로젝트 전반에서 반복적으로 쓰이는 값들을 이곳에 정의한다.</p>
 */
public final class ForgeOSConstants {

    /** OS 이름. */
    public static final String OS_NAME = "ForgeFramework";

    /** OS 버전. */
    public static final String OS_VERSION = "1.0-phase3";

    /** Shell 프롬프트 기본 문자열. */
    public static final String SHELL_PROMPT = "forgeframework> ";

    /** 부팅 단계 사이의 연출용 대기 시간(ms). */
    public static final long BOOT_STAGE_DELAY_MS = 150L;

    /** 명령어 파싱 시 사용하는 구분자. */
    public static final String COMMAND_DELIMITER = "\\s+";

    /** exec 시 burstTime 인자를 생략했을 때 적용되는 기본 실행 시간(tick). */
    public static final long DEFAULT_BURST_TIME = 5L;

    /** 선점형 스케줄러의 기본 타임 퀀텀(tick). */
    public static final int DEFAULT_TIME_QUANTUM = 3;

    /** HardwareTimer의 1 tick당 실제 대기 시간(ms). */
    public static final long TICK_INTERVAL_MS = 1000L;

    /** 프레임(및 페이지) 하나의 크기(byte). */
    public static final int FRAME_SIZE = 4;

    /** 물리 메모리의 총 프레임 개수. */
    public static final int TOTAL_FRAMES = 16;

    /** TLB가 캐싱할 수 있는 (pid, 페이지) 항목 최대 개수. */
    public static final int TLB_CAPACITY = 4;

    private ForgeOSConstants() {
        // 인스턴스화 방지
    }
}
