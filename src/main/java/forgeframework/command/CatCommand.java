package forgeframework.command;

import forgeframework.filesystem.FileContentDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 파일 내용을 출력하는 명령어. 사용법: cat &lt;name&gt;
 */
public final class CatCommand implements Command {

    private final ShellContext context;

    public CatCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "cat";
    }

    @Override
    public String description() {
        return "파일 내용을 출력합니다. (cat <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        if (args.length < 1) {
            return SystemCallResult.failure("사용법: cat <name>");
        }
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.CAT, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        FileContentDto dto = (FileContentDto) result.getData();
        return SystemCallResult.success(dto.content());
    }
}
