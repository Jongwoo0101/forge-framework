package forgeos.command;

import forgeos.kernel.Kernel;
import forgeos.syscall.SystemCallRequest;
import forgeos.syscall.SystemCallResult;
import forgeos.syscall.SystemCallType;

/**
 * 커널의 가동 시간을 조회하는 명령어.
 */
public final class UptimeCommand implements Command {

    @Override
    public String name() {
        return "uptime";
    }

    @Override
    public String description() {
        return "커널 가동 시간을 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.UPTIME));
    }
}
