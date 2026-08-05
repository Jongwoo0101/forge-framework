package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.memory.FrameInfo;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.util.List;

/**
 * 물리 프레임 전체의 상태(할당 여부, 소유 pid, 매핑된 페이지 번호)를 표로 출력하는 명령어.
 *
 * <p>Kernel/MemoryManager는 {@link FrameInfo} 리스트라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — MeminfoCommand/PsCommand와
 * 동일한 원칙을 따른다.</p>
 */
public final class FrameTableCommand implements Command {

    @Override
    public String name() {
        return "frametable";
    }

    @Override
    public String description() {
        return "물리 프레임 테이블(프레임별 소유자/페이지 매핑)을 출력합니다.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SystemCallResult execute(Kernel kernel, String[] args) {
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(SystemCallType.FRAMETABLE));
        if (!result.isSuccess()) {
            return result;
        }

        List<FrameInfo> frames = (List<FrameInfo>) result.getData();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s | %-6s | %-5s | %s%n", "FRAME", "STATUS", "PID", "PAGE"));
        for (FrameInfo frame : frames) {
            sb.append(String.format(
                    "%-6d | %-6s | %-5s | %s%n",
                    frame.frameNumber(),
                    frame.allocated() ? "USED" : "FREE",
                    frame.allocated() ? String.valueOf(frame.ownerPid()) : "-",
                    frame.allocated() ? String.valueOf(frame.pageNumber()) : "-"
            ));
        }

        return SystemCallResult.success(sb.toString().stripTrailing());
    }
}
