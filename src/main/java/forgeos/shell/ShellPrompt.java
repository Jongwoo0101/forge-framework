package forgeos.shell;

import forgeos.common.ForgeOSConstants;

/**
 * ForgeShell의 프롬프트 문자열을 관리하는 클래스.
 *
 * <p>추후 현재 작업 디렉터리, 사용자 이름 등을 반영한
 * 동적인 프롬프트로 확장할 수 있도록 별도 클래스로 분리했다.</p>
 */
public class ShellPrompt {

    /**
     * 현재 프롬프트 문자열을 반환한다.
     *
     * @return 프롬프트 문자열
     */
    public String render() {
        return ForgeOSConstants.SHELL_PROMPT;
    }
}
