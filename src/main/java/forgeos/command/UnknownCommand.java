package forgeos.command;

import forgeos.kernel.Kernel;
import forgeos.syscall.SystemCallResult;

/**
 * 등록되지 않은 명령어가 입력되었을 때 반환되는 Null Object.
 *
 * <p>{@link CommandRegistry#resolve(String)}에서 null 대신 이 객체를 반환함으로써
 * 호출부의 null 체크 분기를 제거한다.</p>
 */
public final class UnknownCommand implements Command {

    private final String inputName;

    public UnknownCommand(String inputName) {
        this.inputName = inputName;
    }

    @Override
    public String name() {
        return inputName;
    }

    @Override
    public String description() {
        return "알 수 없는 명령어";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return SystemCallResult.failure(
                "'" + inputName + "': 알 수 없는 명령어입니다. 'help'를 입력해 사용 가능한 명령어를 확인하세요."
        );
    }
}
