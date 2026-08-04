package forgeframework.logger;

/**
 * 로그 이벤트를 표준 출력(콘솔)에 출력하는 기본 {@link LogListener} 구현체.
 *
 * <p>추후 파일 저장, 원격 전송 등 다른 형태의 리스너를 추가하더라도
 * {@link EventLogger}의 로직은 변경할 필요가 없다 (OCP 준수).</p>
 */
public class ConsoleLogListener implements LogListener {

    @Override
    public void onLogEvent(LogEntry entry) {

        // DEBUG 레벨의 로그는 출력에서 제외 (입력 방해 등의 이유로 추후 이 if 절 삭제 후 배포 얘정)
        if (entry.getLevel() != LogLevel.DEBUG) {
            System.out.println(entry.toFormattedString());
        }
    }
}
