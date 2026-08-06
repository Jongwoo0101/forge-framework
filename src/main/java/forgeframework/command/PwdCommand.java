package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallResult;

/**
 * 현재 작업 디렉터리를 출력하는 명령어.
 *
 * <p>CWD는 Kernel이 아니라 Shell({@link ShellContext})이 들고 있는 상태이므로,
 * 이 명령어는 시스템 콜을 전혀 보내지 않는다 (Kernel의 무상태성을 지키기 위한
 * 의도적인 예외).</p>
 */
public final class PwdCommand implements Command {

    private final ShellContext context;

    public PwdCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "pwd";
    }

    @Override
    public String description() {
        return "현재 작업 디렉터리를 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return SystemCallResult.success(context.getCwd());
    }
}
