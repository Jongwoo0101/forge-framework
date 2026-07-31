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
    public static final String OS_VERSION = "1.0-phase2";

    /** Shell 프롬프트 기본 문자열. */
    public static final String SHELL_PROMPT = "forgeframework> ";

    /** 부팅 단계 사이의 연출용 대기 시간(ms). */
    public static final long BOOT_STAGE_DELAY_MS = 150L;

    /** 명령어 파싱 시 사용하는 구분자. */
    public static final String COMMAND_DELIMITER = " ";

    private ForgeOSConstants() {
        // 인스턴스화 방지
    }
}
