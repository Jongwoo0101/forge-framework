package forgeframework.boot;

/**
 * ForgeOS 부팅 과정의 각 단계를 나타내는 열거형.
 *
 * <p>{@link BootManager}는 이 단계를 순서대로 진행하며,
 * 각 단계마다 설명 메시지를 로그로 남긴다.</p>
 */
public enum BootStage {

    /** 하드웨어(가상) 점검 단계. */
    HARDWARE_CHECK("하드웨어 점검 중..."),

    /** 로거 초기화 단계. */
    LOGGER_INIT("이벤트 로거 초기화 중..."),

    /** 커널 초기화 단계. */
    KERNEL_INIT("커널 초기화 중..."),

    /** 서브시스템 초기화 단계. */
    SUBSYSTEM_INIT("서브시스템 초기화 중..."),

    /** 쉘 준비 완료 단계. */
    SHELL_READY("ForgeShell 준비 완료");

    private final String description;

    BootStage(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
