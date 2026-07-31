package forgeos.exception;

/**
 * ForgeOS 내부에서 발생하는 모든 예외의 최상위 클래스.
 *
 * <p>커널, 부트 매니저, 서브시스템 등에서 발생하는 예외는
 * 모두 이 예외를 상속하여 일관된 예외 처리 체계를 유지한다.</p>
 */
public class ForgeOSException extends RuntimeException {

    public ForgeOSException(String message) {
        super(message);
    }

    public ForgeOSException(String message, Throwable cause) {
        super(message, cause);
    }
}
