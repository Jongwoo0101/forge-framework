package forgeos.boot;

import forgeos.common.ForgeOSConstants;
import forgeos.kernel.Kernel;
import forgeos.logger.EventLogger;
import forgeos.logger.LogLevel;

/**
 * ForgeOS의 부팅 절차를 담당하는 매니저.
 *
 * <p>{@link BootStage}에 정의된 순서대로 부팅 단계를 진행하며,
 * KERNEL_INIT 단계에서 {@link Kernel} Singleton을 초기화한다.
 * 각 단계의 진행 상황은 {@link EventLogger}를 통해 기록된다.</p>
 */
public class BootManager {

    private final EventLogger logger;
    private Kernel kernel;

    public BootManager(EventLogger logger) {
        this.logger = logger;
    }

    /**
     * 전체 부팅 절차를 순차적으로 실행한다.
     *
     * @return 초기화가 완료된 Kernel 인스턴스
     */
    public Kernel boot() {
        printBanner();
        for (BootStage stage : BootStage.values()) {
            runStage(stage);
        }
        return kernel;
    }

    private void runStage(BootStage stage) {
        logger.log(LogLevel.INFO, stage.getDescription());

        if (stage == BootStage.KERNEL_INIT) {
            kernel = Kernel.initialize(logger);
        }

        delay();
    }

    private void delay() {
        try {
            Thread.sleep(ForgeOSConstants.BOOT_STAGE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printBanner() {
        System.out.println("=================================================");
        System.out.println(" " + ForgeOSConstants.OS_NAME + " v" + ForgeOSConstants.OS_VERSION);
        System.out.println(" Operating System Kernel Architecture Simulator");
        System.out.println("=================================================");
    }
}
