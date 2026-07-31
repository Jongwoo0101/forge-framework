package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallResult;

/**
 * Shell에서 실행 가능한 명령어를 표현하는 인터페이스 (Command 패턴).
 *
 * <p>모든 구현체는 반드시 {@link Kernel#handleSystemCall}을 통해서만
 * 실제 기능을 수행해야 하며, 커널 서브시스템에 직접 접근해서는 안 된다.</p>
 */
public interface Command {

    /**
     * 명령어 이름 (Shell에 입력하는 문자열).
     *
     * @return 명령어 이름
     */
    String name();

    /**
     * help 명령에서 보여줄 한 줄 설명.
     *
     * @return 명령어 설명
     */
    String description();

    /**
     * 명령어를 실행한다.
     *
     * @param kernel 시스템 콜을 전달할 Kernel
     * @param args   명령줄 인자 (명령어 이름 제외)
     * @return 실행 결과
     */
    SystemCallResult execute(Kernel kernel, String[] args);
}
