package forgeos.syscall;

/**
 * Kernel이 시스템 콜 처리 후 반환하는 결과.
 *
 * <p>성공 여부, 사용자에게 보여줄 메시지, 부가 데이터를 포함한다.
 * 정적 팩토리 메서드({@link #success}, {@link #failure})를 통해서만 생성한다.</p>
 */
public final class SystemCallResult {

    private final boolean success;
    private final String message;
    private final Object data;

    private SystemCallResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static SystemCallResult success(String message) {
        return new SystemCallResult(true, message, null);
    }

    public static SystemCallResult success(String message, Object data) {
        return new SystemCallResult(true, message, data);
    }

    public static SystemCallResult failure(String message) {
        return new SystemCallResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
