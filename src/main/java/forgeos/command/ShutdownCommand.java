package forgeos.command;

import forgeos.kernel.Kernel;
import forgeos.syscall.SystemCallRequest;
import forgeos.syscall.SystemCallResult;
import forgeos.syscall.SystemCallType;

/**
 * 시스템을 종료하는 명령어.
 */
public final class ShutdownCommand implements Command {

    @Override
    public String name() {
        return "shutdown";
    }

    @Override
    public String description() {
        return "ForgeOS를 종료합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.SHUTDOWN));
    }
}
