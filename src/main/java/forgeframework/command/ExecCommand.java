package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 새 프로세스를 생성하는 명령어.
 *
 * <p>사용법: {@code exec <이름> [burstTime]}. burstTime을 생략하면
 * 커널의 기본 burst time이 적용된다.</p>
 */
public final class ExecCommand implements Command {
    @Override public String name() { return "exec"; }
    @Override public String description() { return "새 프로세스를 생성합니다. (exec <이름> [burstTime])"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.EXEC, args));
    }
}
