package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 가상 주소를 물리 주소로 변환해 보여주는 명령어 (Paging + TLB 동작 확인용).
 * 사용법: translate &lt;PID&gt; &lt;virtualAddress&gt;
 */
public final class TranslateCommand implements Command {
    @Override public String name() { return "translate"; }
    @Override public String description() { return "가상 주소를 물리 주소로 변환합니다. (translate <PID> <vaddr>)"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.TRANSLATE, args));
    }
}
