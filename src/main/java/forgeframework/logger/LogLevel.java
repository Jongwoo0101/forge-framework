package forgeframework.logger;

/**
 * 로그 이벤트의 심각도 수준을 나타내는 열거형.
 */
public enum LogLevel {

    /** 일반적인 정보성 로그. */
    INFO,

    /** 경고성 로그. 즉시 문제는 아니지만 주의가 필요함. */
    WARN,

    /** 오류 로그. 기능 수행에 실패했음을 의미. */
    ERROR,

    /** 디버깅 목적의 상세 로그. */
    DEBUG
}
