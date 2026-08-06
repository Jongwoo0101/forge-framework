package forgeframework.shell;

import forgeframework.common.ForgeOSConstants;

/**
 * ForgeShell의 프롬프트 문자열을 관리하는 클래스.
 *
 * <p>Phase 4부터는 현재 작업 디렉터리(CWD)를 반영해
 * {@code forgeframework:/usr/local> } 형태로 동적으로 렌더링한다.
 * CWD 상태 자체는 {@link ShellContext}가 들고 있으므로, 이 클래스는
 * 매 호출마다 그 값을 읽어 포맷팅만 담당한다.</p>
 */
public class ShellPrompt {

    private final ShellContext context;

    public ShellPrompt(ShellContext context) {
        this.context = context;
    }

    /**
     * 현재 프롬프트 문자열을 반환한다. (예: {@code forgeframework:/usr/local> })
     *
     * @return 프롬프트 문자열
     */
    public String render() {
        return ForgeOSConstants.SHELL_PROMPT_PREFIX + ":" + context.getCwd() + ForgeOSConstants.SHELL_PROMPT_SUFFIX;
    }
}
