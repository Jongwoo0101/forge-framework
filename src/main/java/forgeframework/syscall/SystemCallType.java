package forgeframework.syscall;

/**
 * Kernel이 처리할 수 있는 시스템 콜의 종류.
 * Phase가 진행됨에 따라 Process, Memory, FileSystem 등
 * 각 서브시스템에 대응하는 항목이 계속 추가될 예정이다.
 * Phase 1에서는 커널 자체 기능(HELP, SHUTDOWN, UPTIME)만 정의한다.
 */
public enum SystemCallType {

    /** 사용 가능한 명령어 목록 조회. */
    HELP,

    /** 시스템 종료. */
    SHUTDOWN,

    /** 커널 가동 시간 조회. */
    UPTIME,

    /** 현재 실행 및 대기 중인 프로세스 상태 목록 조회. */
    PS,

    /** 새로운 프로세스 생성 요청. */
    EXEC,

    /** 특정 프로세스의 강제 종료 요청 */
    KILL
}
