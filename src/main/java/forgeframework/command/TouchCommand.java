package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 빈 파일을 생성하는 명령어. 사용법: touch &lt;name&gt;
 */
public final class TouchCommand implements Command {

    private final ShellContext context;

    public TouchCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "touch";
    }

    @Override
    public String description() {
        return "빈 파일을 생성합니다. (touch <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        // MkdirCommand.java 33 ~ 36 line 참고
        /* if (args.length < 1) {
            return SystemCallResult.failure("사용법: touch <name>");
        } */

        if ( args.length != 1 ) {
            return SystemCallResult.failure("사용법: touch <name> (공백 없는 이름 하나만 입력)");
        }

        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.TOUCH, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        DirectoryEntryDto dto = (DirectoryEntryDto) result.getData();
        return SystemCallResult.success("파일이 생성되었습니다: " + dto.name());
    }
}
