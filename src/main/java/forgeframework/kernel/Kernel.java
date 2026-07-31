package forgeframework.kernel;

import forgeframework.exception.ForgeOSException;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.time.Duration;
import java.time.Instant;

/**
 * ForgeOS의 유일한 관리자(Kernel).
 *
 * <p>Singleton 패턴으로 구현되어 시스템 전체에서 단 하나의 인스턴스만 존재하며,
 * Facade 패턴으로서 모든 서브시스템(Process, Memory, FileSystem 등)에 대한
 * 단일 접근 창구 역할을 한다.</p>
 *
 * <p>Phase 1에서는 서브시스템 매니저가 아직 존재하지 않으므로
 * 커널 자체 기능(HELP, SHUTDOWN, UPTIME)만 처리한다.
 * 이후 Phase에서 {@code registerManager()} 형태의 확장 지점을 통해
 * Process/Memory/FileSystem Manager 등을 등록받아 위임하는 구조로 확장한다.</p>
 */
public final class Kernel {

    private static Kernel instance;

    private final EventLogger logger;
    private final Instant bootTime;
    private boolean running;

    private Kernel(EventLogger logger) {
        this.logger = logger;
        this.bootTime = Instant.now();
        this.running = true;
    }

    /**
     * Kernel Singleton 인스턴스를 최초 1회 초기화한다.
     * BootManager의 KERNEL_INIT 단계에서만 호출되어야 한다.
     *
     * @param logger 커널이 사용할 이벤트 로거
     * @return 초기화된 Kernel 인스턴스
     */
    public static synchronized Kernel initialize(EventLogger logger) {
        if (instance != null) {
            throw new ForgeOSException("Kernel은 이미 초기화되었습니다.");
        }
        instance = new Kernel(logger);
        return instance;
    }

    /**
     * 이미 초기화된 Kernel Singleton 인스턴스를 반환한다.
     *
     * @return Kernel 인스턴스
     * @throws ForgeOSException 아직 초기화되지 않은 경우
     */
    public static synchronized Kernel getInstance() {
        if (instance == null) {
            throw new ForgeOSException("Kernel이 아직 초기화되지 않았습니다.");
        }
        return instance;
    }

    /**
     * 시스템 콜을 처리하는 유일한 진입점.
     *
     * <p>Shell/Command 계층은 반드시 이 메서드를 통해서만 커널 기능에 접근한다.</p>
     *
     * @param request 처리할 시스템 콜 요청
     * @return 처리 결과
     */
    public SystemCallResult handleSystemCall(SystemCallRequest request) {
        SystemCallType type = request.getType();
        logger.log(LogLevel.DEBUG, "System call received: " + type);

        return switch (type) {
            case HELP -> handleHelp();
            case SHUTDOWN -> handleShutdown();
            case UPTIME -> handleUptime();
        };
    }

    private SystemCallResult handleHelp() {
        return SystemCallResult.success("등록된 명령어 목록은 Shell에서 제공됩니다.");
    }

    private SystemCallResult handleShutdown() {
        running = false;
        logger.log(LogLevel.INFO, "커널 종료 절차를 시작합니다.");
        return SystemCallResult.success("ForgeOS를 종료합니다.");
    }

    private SystemCallResult handleUptime() {
        Duration uptime = Duration.between(bootTime, Instant.now());
        String formatted = formatDuration(uptime);
        return SystemCallResult.success("가동 시간: " + formatted, uptime);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 커널이 현재 실행 중인지 여부를 반환한다.
     * Shell의 REPL 루프 종료 조건으로 사용된다.
     *
     * @return 실행 중이면 true
     */
    public boolean isRunning() {
        return running;
    }
}
