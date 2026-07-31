package forgeos.logger;

/**
 * 로그 이벤트를 수신하는 Observer 인터페이스.
 *
 * <p>{@link EventLogger}(Subject)에 등록되면
 * 새로운 로그가 발생할 때마다 {@link #onLogEvent(LogEntry)}가 호출된다.</p>
 */
public interface LogListener {

    /**
     * 새로운 로그 이벤트가 발생했을 때 호출된다.
     *
     * @param entry 발생한 로그 이벤트
     */
    void onLogEvent(LogEntry entry);
}
