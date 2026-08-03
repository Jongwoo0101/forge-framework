package forgeframework.logger;

import java.util.ArrayList;
import java.util.List;

public class EventLogger {

    private final List<LogListener> listeners = new ArrayList<>();

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public synchronized void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message);
        notifyListeners(entry);
    }

    private void notifyListeners(LogEntry entry) {
        for (LogListener listener : listeners) {
            listener.onLogEvent(entry);
        }
    }
}
