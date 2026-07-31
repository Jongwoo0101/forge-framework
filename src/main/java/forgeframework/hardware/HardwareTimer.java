package forgeframework.hardware;

import forgeframework.kernel.Kernel;

/**
 * 일정한 주기(Tick)마다 커널에 Timer Interrupt를 발생시키는 가상 하드웨어 타이머.
 */
public class HardwareTimer {
    private final Kernel kernel;
    private final Thread timerThread;
    private volatile boolean running = true;

    public HardwareTimer(Kernel kernel) {
        this.kernel = kernel;
        this.timerThread = new Thread(this::runTimer, "Hardware-Timer-Thread");
        this.timerThread.setDaemon(true);
    }

    public void start() {
        timerThread.start();
    }

    public void stop() {
        running = false;
    }

    private void runTimer() {
        while (running && kernel.isRunning()) {
            try {
                Thread.sleep(1000); // 1초마다 1 Tick
                kernel.handleTimerInterrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}