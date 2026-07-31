package forgeos.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 하나의 로그 이벤트를 표현하는 불변 데이터 클래스.
 *
 * <p>발생 시각, 로그 레벨, 메시지를 포함하며
 * {@link EventLogger}가 생성하여 등록된 {@link LogListener}들에게 전달한다.</p>
 */
public final class LogEntry {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;

    public LogEntry(LogLevel level, String message) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 콘솔 출력 등에 사용할 형식화된 문자열을 반환한다.
     *
     * @return "[HH:mm:ss.SSS] [LEVEL] message" 형태의 문자열
     */
    public String toFormattedString() {
        return String.format(
                "[%s] [%s] %s",
                timestamp.format(TIMESTAMP_FORMAT),
                level,
                message
        );
    }
}
