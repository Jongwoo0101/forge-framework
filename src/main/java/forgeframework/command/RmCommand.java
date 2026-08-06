package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 파일 또는 비어있는 디렉터리를 삭제하는 명령어. 사용법: rm &lt;name&gt;
 */
public final class RmCommand implements Command {

    private final ShellContext context;

    public RmCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "rm";
    }

    @Override
    public String description() {
        return "파일 또는 빈 디렉터리를 삭제합니다. (rm <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        // MkdirCommand.java 33 ~ 36 line 참고
        /* if (args.length < 1) {
            return SystemCallResult.failure("사용법: rm <name>");
        } */

        if (args.length != 1) {
            return SystemCallResult.failure("사용법: rm <name> (공백 없는 이름 하나만 입력)");
        }

        return kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.RM, new String[]{context.getCwd(), args[0]}
        ));
    }
}
