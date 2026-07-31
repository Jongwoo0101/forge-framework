package forgeos.logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ForgeOS 내 모든 이벤트를 기록하는 로거.
 *
 * <p>Observer 패턴의 Subject 역할을 수행하며,
 * 등록된 {@link LogListener}들에게 로그 발생을 통지한다.
 * Boot, Kernel, Shell 등 모든 계층이 공통으로 이 로거를 사용한다.</p>
 */
public class EventLogger {

    private final List<LogListener> listeners = new ArrayList<>();

    /**
     * 로그 리스너를 등록한다.
     *
     * @param listener 등록할 리스너
     */
    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    /**
     * 로그 리스너 등록을 해제한다.
     *
     * @param listener 해제할 리스너
     */
    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    /**
     * 새로운 로그 이벤트를 기록하고 모든 리스너에게 통지한다.
     *
     * @param level   로그 심각도
     * @param message 로그 메시지
     */
    public void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message);
        notifyListeners(entry);
    }

    private void notifyListeners(LogEntry entry) {
        for (LogListener listener : listeners) {
            listener.onLogEvent(entry);
        }
    }
}
