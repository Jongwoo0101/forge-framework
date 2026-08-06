package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.filesystem.FileListDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 디렉터리 내용을 나열하는 명령어. 사용법: ls [path]
 *
 * <p>Kernel/FileSystemManager는 {@link FileListDto}라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — PsCommand/MeminfoCommand와
 * 동일한 원칙을 따른다.</p>
 */
public final class LsCommand implements Command {

    private final ShellContext context;

    public LsCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return "디렉터리 내용을 나열합니다. (ls [path])";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        String target = (args.length > 0) ? args[0] : ".";
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.LS, new String[]{context.getCwd(), target}
        ));
        if (!result.isSuccess()) {
            return result;
        }

        FileListDto dto = (FileListDto) result.getData();
        StringBuilder sb = new StringBuilder();
        sb.append(dto.currentPath()).append('\n');
        sb.append(String.format("%-20s | %-10s | %s%n", "NAME", "TYPE", "SIZE"));
        for (DirectoryEntryDto entry : dto.entries()) {
            sb.append(String.format("%-20s | %-10s | %d%n", entry.name(), entry.type(), entry.size()));
        }
        return SystemCallResult.success(sb.toString().stripTrailing());
    }
}
