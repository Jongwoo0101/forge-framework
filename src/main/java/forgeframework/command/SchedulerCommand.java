package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 현재 스케줄러를 조회하거나 런타임에 교체하는 명령어.
 * 사용법: scheduler [fcfs|rr]
 */
public final class SchedulerCommand implements Command {
    @Override public String name() { return "scheduler"; }
    @Override public String description() { return "스케줄러를 조회하거나 변경합니다. (scheduler [fcfs|rr])"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.SCHEDULER, args));
    }
}
