package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 새 디렉터리를 생성하는 명령어. 사용법: mkdir &lt;name&gt;
 */
public final class MkdirCommand implements Command {

    private final ShellContext context;

    public MkdirCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "mkdir";
    }

    @Override
    public String description() {
        return "새 디렉터리를 생성합니다. (mkdir <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
//        아래 조건식을 사용하게 된다면 인자를 하나씩 떨어뜨려서 명령어를 전달 시
//        ex) mkdir d 9
//        첫번째 인자인 "d"만 전달되고 나머지 9는 아무런 경고없이 무시되는 문제가 있음
//        TouchCommand.java, RMCommand.java도 동일한 문제를 가지고 있어 모두 수정한다.
//        if (args.length < 1) {
//            return SystemCallResult.failure("사용법: mkdir <name>");
//        }

        // 해결 버전
        if ( args.length != 1 ) {
            return SystemCallResult.failure("사용법: mkdir <name> (공백 없는 이름 하나만 입력)");
        }

        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.MKDIR, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        DirectoryEntryDto dto = (DirectoryEntryDto) result.getData();
        return SystemCallResult.success("디렉터리가 생성되었습니다: " + dto.name());
    }
}
