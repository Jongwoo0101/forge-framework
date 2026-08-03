package forgeframework.boot;

import forgeframework.common.ForgeOSConstants;
import forgeframework.hardware.HardwareTimer;
import forgeframework.kernel.Kernel;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.memory.MemoryManager;
import forgeframework.process.ProcessManager;
import forgeframework.process.scheduler.RoundRobinScheduler;
import forgeframework.process.scheduler.Scheduler;

/**
 * ForgeFramework의 부팅 절차를 담당하는 매니저.
 */
public class BootManager {

    private final EventLogger logger;
    private Kernel kernel;
    private HardwareTimer timer;

    public BootManager(EventLogger logger) {
        this.logger = logger;
    }

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
        } else if (stage == BootStage.SUBSYSTEM_INIT) {

            // 원하는 스케줄러로 변경 가능 (전략 패턴)
            Scheduler activeScheduler = new RoundRobinScheduler();

            ProcessManager processManager = new ProcessManager(logger, activeScheduler);
            MemoryManager memoryManager = new MemoryManager(
                    logger,
                    ForgeOSConstants.TOTAL_FRAMES,
                    ForgeOSConstants.FRAME_SIZE,
                    ForgeOSConstants.TLB_CAPACITY
            );

            // 프로세스가 종료되면(kill 또는 burst 완료) MemoryManager가 자원을 회수하도록 연결
            processManager.setTerminationListener(memoryManager::releaseProcess);

            kernel.registerProcessManager(processManager);
            kernel.registerMemoryManager(memoryManager);

            timer = new HardwareTimer(kernel);
            timer.start();
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
        System.out.println(" Operating System Kernel Architecture Engine");
        System.out.println("=================================================");
    }
}
