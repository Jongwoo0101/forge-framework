package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 작업 디렉터리를 변경하는 명령어. 사용법: cd &lt;path&gt;
 *
 * <p>Kernel이 대상 경로가 유효한 디렉터리인지 검증하고 절대경로로 해석해서
 * 돌려주면, 성공한 경우에만 이 명령어가 {@link ShellContext}의 CWD를 갱신한다
 * (Kernel 자신은 상태를 갖지 않는다).</p>
 */
public final class CdCommand implements Command {

    private final ShellContext context;

    public CdCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "cd";
    }

    @Override
    public String description() {
        return "작업 디렉터리를 변경합니다. (cd <path>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        String target = (args.length > 0) ? args[0] : "/";
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.CD, new String[]{context.getCwd(), target}
        ));
        if (result.isSuccess()) {
            context.setCwd((String) result.getData());
        }
        return result;
    }
}
