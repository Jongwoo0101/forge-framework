package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.util.Arrays;

/**
 * 파일 내용을 덮어쓰는 명령어. 사용법: write &lt;name&gt; &lt;text...&gt;
 *
 * <p>ForgeShell이 입력을 공백 기준으로 토큰화하기 때문에, text에 공백이
 * 여러 단어로 들어오면 args[1] 이후를 전부 공백으로 다시 합쳐서 원래
 * 문자열을 복원한다.</p>
 */
public final class WriteCommand implements Command {

    private final ShellContext context;

    public WriteCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String description() {
        return "파일 내용을 덮어씁니다. (write <name> <text>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: write <name> <text>");
        }
        String content = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        return kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.WRITE, new String[]{context.getCwd(), args[0], content}
        ));
    }
}
