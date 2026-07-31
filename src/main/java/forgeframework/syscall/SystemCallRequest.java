package forgeframework.syscall;

/**
 * Shell/Command 계층이 Kernel에게 전달하는 시스템 콜 요청.
 *
 * <p>Shell은 절대로 Kernel의 서브시스템에 직접 접근하지 않으며,
 * 반드시 이 요청 객체를 통해서만 {@code Kernel.handleSystemCall()}을 호출한다.</p>
 */
public final class SystemCallRequest {

    private final SystemCallType type;
    private final String[] args;

    public SystemCallRequest(SystemCallType type, String[] args) {
        this.type = type;
        this.args = (args == null) ? new String[0] : args;
    }

    public SystemCallRequest(SystemCallType type) {
        this(type, new String[0]);
    }

    public SystemCallType getType() {
        return type;
    }

    public String[] getArgs() {
        return args;
    }
}
