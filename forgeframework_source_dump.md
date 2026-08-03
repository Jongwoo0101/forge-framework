# ForgeFramework Source Dump

총 Java 파일 수 : **34개**

---

## Files

- `Main.java`
- `boot/BootManager.java`
- `boot/BootStage.java`
- `command/Command.java`
- `command/CommandRegistry.java`
- `command/ExecCommand.java`
- `command/HelpCommand.java`
- `command/KillCommand.java`
- `command/PsCommand.java`
- `command/SchedulerCommand.java`
- `command/ShutdownCommand.java`
- `command/UnknownCommand.java`
- `command/UptimeCommand.java`
- `common/ForgeOSConstants.java`
- `exception/ForgeOSException.java`
- `hardware/HardwareTimer.java`
- `kernel/Kernel.java`
- `logger/ConsoleLogListener.java`
- `logger/EventLogger.java`
- `logger/LogEntry.java`
- `logger/LogLevel.java`
- `logger/LogListener.java`
- `process/Process.java`
- `process/ProcessControlBlock.java`
- `process/ProcessManager.java`
- `process/ProcessState.java`
- `process/scheduler/FcfsScheduler.java`
- `process/scheduler/RoundRobinScheduler.java`
- `process/scheduler/Scheduler.java`
- `shell/ForgeShell.java`
- `shell/ShellPrompt.java`
- `syscall/SystemCallRequest.java`
- `syscall/SystemCallResult.java`
- `syscall/SystemCallType.java`

---

# 1. Main.java

**Path**
`src/main/java/forgeframework/Main.java`

```java
package forgeframework;

import forgeframework.boot.BootManager;
import forgeframework.kernel.Kernel;
import forgeframework.logger.ConsoleLogListener;
import forgeframework.logger.EventLogger;
import forgeframework.shell.ForgeShell;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * ForgeOS 애플리케이션의 진입점.
 * 현재 Phase2 완료
 * <p>실행 순서: EventLogger 준비 → BootManager 부팅(내부에서 Kernel 초기화)
 * → ForgeShell 실행.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        forceUtf8Console();

        EventLogger logger = new EventLogger();
        logger.addListener(new ConsoleLogListener());

        BootManager bootManager = new BootManager(logger);
        Kernel kernel = bootManager.boot();

        ForgeShell shell = new ForgeShell(kernel);
        shell.run();
    }

    /**
     * 실행 환경의 로케일 설정과 무관하게 한글 등이 깨지지 않도록
     * 표준 출력/에러 스트림을 UTF-8로 강제한다.
     */
    private static void forceUtf8Console() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }
}
```

---

# 2. boot/BootManager.java

**Path**
`src/main/java/forgeframework/boot/BootManager.java`

```java
package forgeframework.boot;

import forgeframework.common.ForgeOSConstants;
import forgeframework.hardware.HardwareTimer;
import forgeframework.kernel.Kernel;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
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
            kernel.registerProcessManager(processManager);

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
```

---

# 3. boot/BootStage.java

**Path**
`src/main/java/forgeframework/boot/BootStage.java`

```java
package forgeframework.boot;

/**
 * ForgeOS 부팅 과정의 각 단계를 나타내는 열거형.
 *
 * <p>{@link BootManager}는 이 단계를 순서대로 진행하며,
 * 각 단계마다 설명 메시지를 로그로 남긴다.</p>
 */
public enum BootStage {

    /** 하드웨어(가상) 점검 단계. */
    HARDWARE_CHECK("하드웨어 점검 중..."),

    /** 로거 초기화 단계. */
    LOGGER_INIT("이벤트 로거 초기화 중..."),

    /** 커널 초기화 단계. */
    KERNEL_INIT("커널 초기화 중..."),

    /** 서브시스템 초기화 단계. */
    SUBSYSTEM_INIT("서브시스템 초기화 중..."),

    /** 쉘 준비 완료 단계. */
    SHELL_READY("ForgeShell 준비 완료");

    private final String description;

    BootStage(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

---

# 4. command/Command.java

**Path**
`src/main/java/forgeframework/command/Command.java`

```java
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
```

---

# 5. command/CommandRegistry.java

**Path**
`src/main/java/forgeframework/command/CommandRegistry.java`

```java
package forgeframework.command;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용 가능한 {@link Command}들을 이름으로 조회할 수 있도록 관리하는 레지스트리.
 *
 * <p>등록 순서를 유지하기 위해 {@link LinkedHashMap}을 사용하며,
 * 등록되지 않은 이름으로 조회 시 {@link UnknownCommand}(Null Object)를 반환한다.</p>
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * 명령어를 레지스트리에 등록한다.
     *
     * @param command 등록할 명령어
     */
    public void register(Command command) {
        commands.put(command.name(), command);
    }

    /**
     * 이름으로 명령어를 조회한다.
     *
     * @param name 조회할 명령어 이름
     * @return 등록된 명령어, 없으면 {@link UnknownCommand}
     */
    public Command resolve(String name) {
        return commands.getOrDefault(name, new UnknownCommand(name));
    }

    /**
     * 등록된 모든 명령어를 반환한다. help 명령에서 사용된다.
     *
     * @return 등록된 명령어 컬렉션 (등록 순서 유지)
     */
    public Collection<Command> getAll() {
        return commands.values();
    }
}
```

---

# 6. command/ExecCommand.java

**Path**
`src/main/java/forgeframework/command/ExecCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 새 프로세스를 생성하는 명령어.
 *
 * <p>사용법: {@code exec <이름> [burstTime]}. burstTime을 생략하면
 * 커널의 기본 burst time이 적용된다.</p>
 */
public final class ExecCommand implements Command {
    @Override public String name() { return "exec"; }
    @Override public String description() { return "새 프로세스를 생성합니다. (exec <이름> [burstTime])"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.EXEC, args));
    }
}
```

---

# 7. command/HelpCommand.java

**Path**
`src/main/java/forgeframework/command/HelpCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 등록된 모든 명령어의 목록과 설명을 출력하는 명령어.
 *
 * <p>명령어 목록 자체는 Shell 계층의 관심사({@link CommandRegistry})이므로
 * 여기서 직접 조합하되, 이벤트 기록을 위해 {@link Kernel#handleSystemCall}은
 * 반드시 거친다 (Shell → Kernel 직접 접근 금지 원칙 준수).</p>
 */
public final class HelpCommand implements Command {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "사용 가능한 명령어 목록을 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        kernel.handleSystemCall(new SystemCallRequest(SystemCallType.HELP));

        StringBuilder builder = new StringBuilder("사용 가능한 명령어:\n");
        for (Command command : registry.getAll()) {
            builder.append(String.format("  %-10s %s%n", command.name(), command.description()));
        }
        return SystemCallResult.success(builder.toString().stripTrailing());
    }
}
```

---

# 8. command/KillCommand.java

**Path**
`src/main/java/forgeframework/command/KillCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

public final class KillCommand implements Command {
    @Override public String name() { return "kill"; }
    @Override public String description() { return "프로세스를 강제 종료합니다. (kill <PID>)"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.KILL, args));
    }
}
```

---

# 9. command/PsCommand.java

**Path**
`src/main/java/forgeframework/command/PsCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

public final class PsCommand implements Command {
    @Override public String name() { return "ps"; }
    @Override public String description() { return "프로세스 상태 목록을 출력합니다."; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.PS));
    }
}
```

---

# 10. command/SchedulerCommand.java

**Path**
`src/main/java/forgeframework/command/SchedulerCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 현재 스케줄러를 조회하거나 런타임에 교체하는 명령어.
 * 사용법: scheduler [fcfs|rr]
 */
public final class SchedulerCommand implements Command {
    @Override public String name() { return "scheduler"; }
    @Override public String description() { return "스케줄러를 조회하거나 변경합니다. (scheduler [fcfs|rr])"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.SCHEDULER, args));
    }
}
```

---

# 11. command/ShutdownCommand.java

**Path**
`src/main/java/forgeframework/command/ShutdownCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 시스템을 종료하는 명령어.
 */
public final class ShutdownCommand implements Command {

    @Override
    public String name() {
        return "shutdown";
    }

    @Override
    public String description() {
        return "ForgeOS를 종료합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.SHUTDOWN));
    }
}
```

---

# 12. command/UnknownCommand.java

**Path**
`src/main/java/forgeframework/command/UnknownCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallResult;

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
```

---

# 13. command/UptimeCommand.java

**Path**
`src/main/java/forgeframework/command/UptimeCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 커널의 가동 시간을 조회하는 명령어.
 */
public final class UptimeCommand implements Command {

    @Override
    public String name() {
        return "uptime";
    }

    @Override
    public String description() {
        return "커널 가동 시간을 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.UPTIME));
    }
}
```

---

# 14. common/ForgeOSConstants.java

**Path**
`src/main/java/forgeframework/common/ForgeOSConstants.java`

```java
package forgeframework.common;

/**
 * ForgeOS 전역에서 사용되는 상수를 모아둔 클래스.
 *
 * <p>매직 넘버 및 매직 스트링 사용을 방지하기 위해
 * 프로젝트 전반에서 반복적으로 쓰이는 값들을 이곳에 정의한다.</p>
 */
public final class ForgeOSConstants {

    /** OS 이름. */
    public static final String OS_NAME = "ForgeFramework";

    /** OS 버전. */
    public static final String OS_VERSION = "1.0-phase2";

    /** Shell 프롬프트 기본 문자열. */
    public static final String SHELL_PROMPT = "forgeframework> ";

    /** 부팅 단계 사이의 연출용 대기 시간(ms). */
    public static final long BOOT_STAGE_DELAY_MS = 150L;

    /** 명령어 파싱 시 사용하는 구분자. */
    public static final String COMMAND_DELIMITER = " ";

    /** exec 시 burstTime 인자를 생략했을 때 적용되는 기본 실행 시간(tick). */
    public static final long DEFAULT_BURST_TIME = 5L;

    /** 선점형 스케줄러의 기본 타임 퀀텀(tick). */
    public static final int DEFAULT_TIME_QUANTUM = 3;

    /** HardwareTimer의 1 tick당 실제 대기 시간(ms). */
    public static final long TICK_INTERVAL_MS = 1000L;

    private ForgeOSConstants() {
        // 인스턴스화 방지
    }
}
```

---

# 15. exception/ForgeOSException.java

**Path**
`src/main/java/forgeframework/exception/ForgeOSException.java`

```java
package forgeframework.exception;

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
```

---

# 16. hardware/HardwareTimer.java

**Path**
`src/main/java/forgeframework/hardware/HardwareTimer.java`

```java
package forgeframework.hardware;

import forgeframework.common.ForgeOSConstants;
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
                Thread.sleep(ForgeOSConstants.TICK_INTERVAL_MS);
                kernel.handleTimerInterrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

---

# 17. kernel/Kernel.java

**Path**
`src/main/java/forgeframework/kernel/Kernel.java`

```java
package forgeframework.kernel;

import forgeframework.common.ForgeOSConstants;
import forgeframework.exception.ForgeOSException;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.process.Process;
import forgeframework.process.ProcessManager;
import forgeframework.process.ProcessState;
import forgeframework.process.scheduler.FcfsScheduler;
import forgeframework.process.scheduler.RoundRobinScheduler;
import forgeframework.process.scheduler.Scheduler;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.time.Duration;
import java.time.Instant;

/**
 * ForgeOS의 유일한 관리자(Kernel).
 *
 * <p>Singleton 패턴으로 구현되어 시스템 전체에서 단 하나의 인스턴스만 존재하며,
 * Facade 패턴으로서 모든 서브시스템(Process, Memory, FileSystem 등)에 대한
 * 단일 접근 창구 역할을 한다.</p>
 */
public final class Kernel {

    private static Kernel instance;

    private final EventLogger logger;
    private final Instant bootTime;
    private boolean running;

    private ProcessManager processManager;

    private Kernel(EventLogger logger) {
        this.logger = logger;
        this.bootTime = Instant.now();
        this.running = true;
    }

    public static synchronized Kernel initialize(EventLogger logger) {
        if (instance != null) {
            throw new ForgeOSException("Kernel은 이미 초기화되었습니다.");
        }
        instance = new Kernel(logger);
        return instance;
    }

    public static synchronized Kernel getInstance() {
        if (instance == null) {
            throw new ForgeOSException("Kernel이 아직 초기화되지 않았습니다.");
        }
        return instance;
    }

    /**
     * ProcessManager를 커널에 등록한다.
     * BootManager의 SUBSYSTEM_INIT 단계에서 호출된다.
     *
     * @param processManager 등록할 프로세스 매니저
     */
    public void registerProcessManager(ProcessManager processManager) {
        this.processManager = processManager;
    }

    /**
     * HardwareTimer로부터 발생하는 타이머 인터럽트를 처리한다.
     * ProcessManager에게 인터럽트 발생을 알려 Context Switch 등의 스케줄링을 유도한다.
     */
    public void handleTimerInterrupt() {
        if (processManager != null) {
            processManager.handleTimerInterrupt();
        }
    }

    public SystemCallResult handleSystemCall(SystemCallRequest request) {
        SystemCallType type = request.getType();
        logger.log(LogLevel.DEBUG, "System call received: " + type);

        return switch (type) {
            case HELP -> handleHelp();
            case SHUTDOWN -> handleShutdown();
            case UPTIME -> handleUptime();
            case PS -> handlePs();
            case EXEC -> handleExec(request.getArgs());
            case KILL -> handleKill(request.getArgs());
            case SCHEDULER -> handleScheduler(request.getArgs());
        };
    }

    private SystemCallResult handleHelp() {
        return SystemCallResult.success("등록된 명령어 목록은 Shell에서 제공됩니다.");
    }

    private SystemCallResult handleShutdown() {
        running = false;
        logger.log(LogLevel.INFO, "커널 종료 절차를 시작합니다.");
        return SystemCallResult.success("ForgeOS를 종료합니다.");
    }

    private SystemCallResult handleUptime() {
        Duration uptime = Duration.between(bootTime, Instant.now());
        String formatted = formatDuration(uptime);
        return SystemCallResult.success("가동 시간: " + formatted, uptime);
    }

    private SystemCallResult handlePs() {
        if (processManager == null) {
            return SystemCallResult.failure("ProcessManager가 로드되지 않았습니다.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s | %-10s | %-8s | %-10s | %s\n", "PID", "STATE", "CPU_TIME", "BURST", "NAME"));
        sb.append("-".repeat(55));

        for (Process p : processManager.getAllProcesses().values()) {
            String stateIndicator = (p.getPcb().getState() == ProcessState.RUNNING) ? "*" : " ";
            sb.append(String.format("\n%-5d | %-10s | %-8d | %-10d | %s%s",
                    p.getPcb().getPid(), p.getPcb().getState(), p.getPcb().getCpuTimeUsed(),
                    p.getPcb().getBurstTime(), p.getName(), stateIndicator));
        }
        return SystemCallResult.success(sb.toString());
    }

    private SystemCallResult handleExec(String[] args) {
        if (processManager == null) {
            return SystemCallResult.failure("ProcessManager가 로드되지 않았습니다.");
        }
        if (args.length == 0) {
            return SystemCallResult.failure("사용법: exec <프로세스명> [burstTime]");
        }

        try {
            String name = args[0];
            long burstTime = (args.length > 1)
                    ? Long.parseLong(args[1])
                    : ForgeOSConstants.DEFAULT_BURST_TIME;

            if (burstTime <= 0) {
                return SystemCallResult.failure("burstTime은 1 이상이어야 합니다.");
            }

            Process p = processManager.createProcess(name, burstTime);
            return SystemCallResult.success(
                    "프로세스가 생성되었습니다. (PID: " + p.getPcb().getPid() + ", burstTime: " + burstTime + ")"
            );
        } catch (NumberFormatException e) {
            return SystemCallResult.failure("burstTime은 숫자여야 합니다.");
        }
    }

    private SystemCallResult handleKill(String[] args) {
        if (processManager == null) {
            return SystemCallResult.failure("ProcessManager가 로드되지 않았습니다.");
        }
        if (args.length == 0) {
            return SystemCallResult.failure("사용법: kill <PID>");
        }
        try {
            int pid = Integer.parseInt(args[0]);
            boolean success = processManager.killProcess(pid);
            if (success) {
                return SystemCallResult.success("프로세스(PID: " + pid + ")가 종료되었습니다.");
            } else {
                return SystemCallResult.failure("존재하지 않거나 이미 종료된 프로세스입니다.");
            }
        } catch (NumberFormatException e) {
            return SystemCallResult.failure("PID는 숫자여야 합니다.");
        }
    }

    /**
     * 현재 스케줄러를 조회하거나(인자 없음) 런타임에 교체한다(인자로 fcfs|rr 전달).
     *
     * @param args 비어있으면 조회, args[0]이 fcfs/rr이면 해당 알고리즘으로 교체
     */
    private SystemCallResult handleScheduler(String[] args) {
        if (processManager == null) {
            return SystemCallResult.failure("ProcessManager가 로드되지 않았습니다.");
        }

        if (args.length == 0) {
            return SystemCallResult.success("현재 스케줄러: " + processManager.getSchedulerName());
        }

        Scheduler newScheduler = switch (args[0].toLowerCase()) {
            case "fcfs" -> new FcfsScheduler();
            case "rr", "roundrobin", "round-robin" -> new RoundRobinScheduler();
            default -> null;
        };

        if (newScheduler == null) {
            return SystemCallResult.failure("알 수 없는 스케줄링 알고리즘입니다: " + args[0] + " (fcfs|rr)");
        }

        processManager.setScheduler(newScheduler);
        return SystemCallResult.success("스케줄러가 " + newScheduler.getName() + "(으)로 변경되었습니다.");
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public boolean isRunning() {
        return running;
    }
}
```

---

# 18. logger/ConsoleLogListener.java

**Path**
`src/main/java/forgeframework/logger/ConsoleLogListener.java`

```java
package forgeframework.logger;

/**
 * 로그 이벤트를 표준 출력(콘솔)에 출력하는 기본 {@link LogListener} 구현체.
 *
 * <p>추후 파일 저장, 원격 전송 등 다른 형태의 리스너를 추가하더라도
 * {@link EventLogger}의 로직은 변경할 필요가 없다 (OCP 준수).</p>
 */
public class ConsoleLogListener implements LogListener {

    @Override
    public void onLogEvent(LogEntry entry) {
        System.out.println(entry.toFormattedString());
    }
}
```

---

# 19. logger/EventLogger.java

**Path**
`src/main/java/forgeframework/logger/EventLogger.java`

```java
package forgeframework.logger;

import java.util.ArrayList;
import java.util.List;

public class EventLogger {

    private final List<LogListener> listeners = new ArrayList<>();

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public synchronized void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message);
        notifyListeners(entry);
    }

    private void notifyListeners(LogEntry entry) {
        for (LogListener listener : listeners) {
            listener.onLogEvent(entry);
        }
    }
}
```

---

# 20. logger/LogEntry.java

**Path**
`src/main/java/forgeframework/logger/LogEntry.java`

```java
package forgeframework.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 하나의 로그 이벤트를 표현하는 불변 데이터 클래스.
 *
 * <p>발생 시각, 로그 레벨, 메시지를 포함하며
 * {@link EventLogger}가 생성하여 등록된 {@link LogListener}들에게 전달한다.</p>
 */
public final class LogEntry {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;

    public LogEntry(LogLevel level, String message) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 콘솔 출력 등에 사용할 형식화된 문자열을 반환한다.
     *
     * @return "[HH:mm:ss.SSS] [LEVEL] message" 형태의 문자열
     */
    public String toFormattedString() {
        return String.format(
                "[%s] [%s] %s",
                timestamp.format(TIMESTAMP_FORMAT),
                level,
                message
        );
    }
}
```

---

# 21. logger/LogLevel.java

**Path**
`src/main/java/forgeframework/logger/LogLevel.java`

```java
package forgeframework.logger;

/**
 * 로그 이벤트의 심각도 수준을 나타내는 열거형.
 */
public enum LogLevel {

    /** 일반적인 정보성 로그. */
    INFO,

    /** 경고성 로그. 즉시 문제는 아니지만 주의가 필요함. */
    WARN,

    /** 오류 로그. 기능 수행에 실패했음을 의미. */
    ERROR,

    /** 디버깅 목적의 상세 로그. */
    DEBUG
}
```

---

# 22. logger/LogListener.java

**Path**
`src/main/java/forgeframework/logger/LogListener.java`

```java
package forgeframework.logger;

/**
 * 로그 이벤트를 수신하는 Observer 인터페이스.
 *
 * <p>{@link EventLogger}(Subject)에 등록되면
 * 새로운 로그가 발생할 때마다 {@link #onLogEvent(LogEntry)}가 호출된다.</p>
 */
public interface LogListener {

    /**
     * 새로운 로그 이벤트가 발생했을 때 호출된다.
     *
     * @param entry 발생한 로그 이벤트
     */
    void onLogEvent(LogEntry entry);
}
```

---

# 23. process/Process.java

**Path**
`src/main/java/forgeframework/process/Process.java`

```java
package forgeframework.process;

public class Process {
    private final String name;
    private final ProcessControlBlock pcb;

    public Process(int pid, String name, long burstTime) {
        this.name = name;
        this.pcb = new ProcessControlBlock(pid, burstTime);
    }

    public String getName() { return name; }
    public ProcessControlBlock getPcb() { return pcb; }
}
```

---

# 24. process/ProcessControlBlock.java

**Path**
`src/main/java/forgeframework/process/ProcessControlBlock.java`

```java
package forgeframework.process;

/**
 * Process Control Block.
 *
 * <p><b>[버그 수정]</b> 기존 구현에는 burstTime(총 필요 실행 시간) 개념이 없어서
 * 프로세스가 CPU를 할당받으면 영원히 RUNNING 상태에 머물렀다. FCFS는 선점을
 * 하지 않기 때문에, 이 경우 맨 처음 실행된 프로세스가 CPU를 절대 반납하지 않고
 * 이후 생성된 모든 프로세스가 기아(starvation) 상태에 빠지는 치명적 버그로
 * 이어졌다. burstTime을 추가하고, 누적 실행 시간이 burstTime에 도달하면
 * 완료로 판단할 수 있도록 {@link #isBurstComplete()}를 제공한다.</p>
 *
 * <p><b>[버그 수정]</b> {@code state}와 {@code cpuTimeUsed}는 HardwareTimer의
 * 백그라운드 스레드(쓰기)와 Shell의 메인 스레드(ps 명령 등, 읽기)가 동시에
 * 접근한다. 두 필드 모두 {@code volatile}로 선언하지 않으면 자바 메모리 모델상
 * 한 스레드의 변경이 다른 스레드에 즉시 보이지 않을 수 있다(가시성 문제).
 * 쓰기는 여전히 ProcessManager의 synchronized 메서드 안에서만 일어나므로
 * volatile만으로 가시성 문제는 충분히 해결된다.</p>
 */
public class ProcessControlBlock {

    private final int pid;
    private final long burstTime;

    private volatile ProcessState state;
    private volatile long cpuTimeUsed;

    public ProcessControlBlock(int pid, long burstTime) {
        this.pid = pid;
        this.burstTime = burstTime;
        this.state = ProcessState.NEW;
        this.cpuTimeUsed = 0;
    }

    public int getPid() {
        return pid;
    }

    public long getBurstTime() {
        return burstTime;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public long getCpuTimeUsed() {
        return cpuTimeUsed;
    }

    public void incrementCpuTime() {
        this.cpuTimeUsed++;
    }

    /**
     * 누적 실행 시간이 burstTime에 도달했는지 여부.
     *
     * @return 프로세스가 필요한 만큼 다 실행되었으면 true
     */
    public boolean isBurstComplete() {
        return cpuTimeUsed >= burstTime;
    }
}
```

---

# 25. process/ProcessManager.java

**Path**
`src/main/java/forgeframework/process/ProcessManager.java`

```java
package forgeframework.process;

import forgeframework.common.ForgeOSConstants;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.process.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 프로세스의 생성, 상태 전이, 문맥 교환(Context Switch)을 관리하는 매니저.
 */
public class ProcessManager {
    private final EventLogger logger;
    private Scheduler scheduler;

    private final Map<Integer, Process> processTable = new LinkedHashMap<>();
    private final AtomicInteger pidGenerator = new AtomicInteger(1);

    private Process currentRunningProcess = null;

    // 선점형 스케줄러용 타임 퀀텀
    private final int timeQuantum = ForgeOSConstants.DEFAULT_TIME_QUANTUM;
    private int quantumTick = 0;

    public ProcessManager(EventLogger logger, Scheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
        logger.log(LogLevel.INFO, "ProcessManager initialized [Scheduler: " + scheduler.getName() + "]");
    }

    /**
     * 새 프로세스를 생성한다.
     *
     * @param name      프로세스 이름
     * @param burstTime 총 필요 실행 시간(tick). 이 시간만큼 CPU를 사용하면 자동으로 종료된다.
     * @return 생성된 프로세스
     */
    public synchronized Process createProcess(String name, long burstTime) {
        int pid = pidGenerator.getAndIncrement();
        Process newProcess = new Process(pid, name, burstTime);

        processTable.put(pid, newProcess);
        newProcess.getPcb().setState(ProcessState.READY);
        scheduler.addProcess(newProcess);

        logger.log(LogLevel.INFO,
                "Process created: [PID=" + pid + "] " + name + " (burstTime=" + burstTime + ")");
        return newProcess;
    }

    /**
     * burstTime을 생략하고 기본값으로 프로세스를 생성한다.
     *
     * @param name 프로세스 이름
     * @return 생성된 프로세스
     */
    public synchronized Process createProcess(String name) {
        return createProcess(name, ForgeOSConstants.DEFAULT_BURST_TIME);
    }

    /**
     * 현재 적용된 스케줄러의 이름을 반환한다 (scheduler 명령의 조회용).
     *
     * @return 스케줄러 이름
     */
    public synchronized String getSchedulerName() {
        return scheduler.getName();
    }

    /**
     * 스케줄링 알고리즘을 런타임에 교체한다.
     *
     * <p>기존 스케줄러의 ready queue에 남아있던 프로세스는 그대로 잃어버리지 않도록
     * 새 스케줄러로 옮겨준다. 현재 CPU를 사용 중인 프로세스는 교체와 무관하게
     * 계속 실행되며, 다음 Context Switch부터 새 알고리즘이 적용된다.</p>
     *
     * @param newScheduler 새로 적용할 스케줄러
     */
    public synchronized void setScheduler(Scheduler newScheduler) {
        while (!scheduler.isEmpty()) {
            newScheduler.addProcess(scheduler.selectNextProcess());
        }
        this.scheduler = newScheduler;
        logger.log(LogLevel.INFO, "Scheduler changed to: " + newScheduler.getName());
    }

    public synchronized boolean killProcess(int pid) {
        Process target = processTable.get(pid);
        if (target == null || target.getPcb().getState() == ProcessState.TERMINATED) {
            return false;
        }

        target.getPcb().setState(ProcessState.TERMINATED);

        if (currentRunningProcess != null && currentRunningProcess.getPcb().getPid() == pid) {
            currentRunningProcess = null;
            quantumTick = 0;
            if (!scheduler.isEmpty()) {
                contextSwitch();
            }
        } else {
            scheduler.removeProcess(target);
        }

        logger.log(LogLevel.WARN, "Process killed: [PID=" + pid + "]");
        return true;
    }

    public Map<Integer, Process> getAllProcesses() {
        return processTable;
    }

    /**
     * Kernel로부터 Timer Interrupt가 발생했을 때 호출된다.
     *
     * <p><b>[버그 수정]</b> 기존 코드는 {@code scheduler.isPreemptive()}가 false인 경우
     * (FCFS) burst 완료 여부와 무관하게 절대 contextSwitch()를 호출하지 않아서,
     * 최초 실행된 프로세스가 영원히 CPU를 독점하고 나머지 프로세스는 기아 상태에
     * 빠지는 문제가 있었다. burst 완료 여부는 스케줄러의 선점 정책과 별개로
     * 항상 먼저 확인하도록 순서를 바꿨다 — "일을 다 끝낸 프로세스는 선점형이든
     * 아니든 CPU를 반납해야 한다"는 것은 스케줄링 알고리즘과 무관한 규칙이기 때문이다.</p>
     */
    public synchronized void handleTimerInterrupt() {
        if (currentRunningProcess != null) {
            currentRunningProcess.getPcb().incrementCpuTime();
            quantumTick++;

            if (currentRunningProcess.getPcb().isBurstComplete()) {
                completeCurrentProcess();
                return;
            }

            if (scheduler.isPreemptive() && quantumTick >= timeQuantum) {
                contextSwitch();
            }
        } else if (!scheduler.isEmpty()) {
            contextSwitch();
        }
    }

    /**
     * 현재 실행 중인 프로세스가 burstTime을 모두 소진했을 때 호출된다.
     * TERMINATED로 전이시키고, ready queue에 남은 프로세스가 있으면 즉시 다음
     * 프로세스로 문맥을 교환한다.
     */
    private void completeCurrentProcess() {
        Process finished = currentRunningProcess;
        finished.getPcb().setState(ProcessState.TERMINATED);
        logger.log(LogLevel.INFO,
                "Process completed: [PID=" + finished.getPcb().getPid() + "] " + finished.getName());

        currentRunningProcess = null;
        quantumTick = 0;

        if (!scheduler.isEmpty()) {
            contextSwitch();
        }
    }

    private void contextSwitch() {
        Process prevProcess = currentRunningProcess;

        if (prevProcess != null && prevProcess.getPcb().getState() == ProcessState.RUNNING) {
            prevProcess.getPcb().setState(ProcessState.READY);
            scheduler.addProcess(prevProcess);
        }

        Process nextProcess = scheduler.selectNextProcess();

        if (nextProcess != null) {
            nextProcess.getPcb().setState(ProcessState.RUNNING);
            currentRunningProcess = nextProcess;
            quantumTick = 0;

            if (prevProcess == null || prevProcess.getPcb().getPid() != nextProcess.getPcb().getPid()) {
                logger.log(LogLevel.DEBUG, "Context Switch: -> [" + nextProcess.getName() + "]");
            }
        } else {
            currentRunningProcess = null;
        }
    }
}
```

---

# 26. process/ProcessState.java

**Path**
`src/main/java/forgeframework/process/ProcessState.java`

```java
package forgeframework.process;

public enum ProcessState {
    NEW,
    READY,
    RUNNING,
    WAITING,
    TERMINATED
}
```

---

# 27. process/scheduler/FcfsScheduler.java

**Path**
`src/main/java/forgeframework/process/scheduler/FcfsScheduler.java`

```java
package forgeframework.process.scheduler;

import forgeframework.process.Process;
import java.util.LinkedList;
import java.util.Queue;

/**
 * First Come, First Served (FCFS) 스케줄러 구현체.
 *
 * <p>먼저 도착한 프로세스를 먼저 실행하며, 비선점형(Non-preemptive)으로 동작한다.
 * 프로세스가 스스로 종료되거나 I/O를 요청하기 전까지 CPU를 반환하지 않는다.</p>
 */
public class FcfsScheduler implements Scheduler {

    private final Queue<Process> readyQueue = new LinkedList<>();

    @Override
    public String getName() {
        return "FCFS (First Come First Served)";
    }

    @Override
    public void addProcess(Process process) {
        readyQueue.offer(process);
    }

    @Override
    public Process selectNextProcess() {
        return readyQueue.poll();
    }

    @Override
    public void removeProcess(Process process) {
        readyQueue.remove(process);
    }

    @Override
    public boolean isEmpty() {
        return readyQueue.isEmpty();
    }

    @Override
    public boolean isPreemptive() {
        return false; // 비선점형 스케줄러
    }
}
```

---

# 28. process/scheduler/RoundRobinScheduler.java

**Path**
`src/main/java/forgeframework/process/scheduler/RoundRobinScheduler.java`

```java
package forgeframework.process.scheduler;

import forgeframework.process.Process;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Round Robin (RR) 스케줄러 구현체.
 *
 * <p>FIFO 큐를 사용하며, 선점형(Preemptive)으로 동작한다.
 * 타임 퀀텀 만료 시 ProcessManager에 의해 다시 큐로 돌아온다.</p>
 */
public class RoundRobinScheduler implements Scheduler {

    private final Queue<Process> readyQueue = new LinkedList<>();

    @Override
    public String getName() {
        return "Round Robin (RR)";
    }

    @Override
    public void addProcess(Process process) {
        readyQueue.offer(process);
    }

    @Override
    public Process selectNextProcess() {
        return readyQueue.poll();
    }

    @Override
    public void removeProcess(Process process) {
        readyQueue.remove(process);
    }

    @Override
    public boolean isEmpty() {
        return readyQueue.isEmpty();
    }

    @Override
    public boolean isPreemptive() {
        return true; // 선점형 스케줄러
    }
}
```

---

# 29. process/scheduler/Scheduler.java

**Path**
`src/main/java/forgeframework/process/scheduler/Scheduler.java`

```java
package forgeframework.process.scheduler;

import forgeframework.process.Process;

/**
 * CPU 스케줄링 알고리즘의 전략(Strategy) 인터페이스.
 */
public interface Scheduler {

    /**
     * 스케줄러의 이름을 반환한다. (예: "FCFS", "Round Robin")
     */
    String getName();

    /**
     * 새로운 프로세스를 Ready Queue에 추가한다.
     * @param process 추가할 프로세스
     */
    void addProcess(Process process);

    /**
     * 다음으로 실행할 프로세스를 선택하여 반환한다.
     * @return 실행할 프로세스 (없으면 null)
     */
    Process selectNextProcess();

    /**
     * 프로세스를 큐에서 제거한다. (종료 등)
     * @param process 제거할 프로세스
     */
    void removeProcess(Process process);

    /**
     * 현재 스케줄링 큐가 비어있는지 확인한다.
     */
    boolean isEmpty();

    /**
     * 해당 스케줄러가 선점형(Preemptive)인지 여부를 반환한다.
     * @return 선점형이면 true, 비선점형이면 false
     */
    boolean isPreemptive();
}
```

---

# 30. shell/ForgeShell.java

**Path**
`src/main/java/forgeframework/shell/ForgeShell.java`

```java
package forgeframework.shell;

import forgeframework.command.Command;
import forgeframework.command.CommandRegistry;
import forgeframework.command.HelpCommand;
import forgeframework.command.ShutdownCommand;
import forgeframework.command.UptimeCommand;
import forgeframework.command.PsCommand;
import forgeframework.command.ExecCommand;
import forgeframework.command.KillCommand;
import forgeframework.command.SchedulerCommand;
import forgeframework.common.ForgeOSConstants;
import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallResult;

import java.util.Scanner;

public class ForgeShell {

    private final Kernel kernel;
    private final CommandRegistry registry;
    private final ShellPrompt prompt;
    private final Scanner input;

    public ForgeShell(Kernel kernel) {
        this.kernel = kernel;
        this.registry = new CommandRegistry();
        this.prompt = new ShellPrompt();
        this.input = new Scanner(System.in);
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        registry.register(new HelpCommand(registry));
        registry.register(new ShutdownCommand());
        registry.register(new UptimeCommand());

        registry.register(new PsCommand());
        registry.register(new ExecCommand());
        registry.register(new KillCommand());
        registry.register(new SchedulerCommand());
    }

    public void run() {
        System.out.println(ForgeOSConstants.OS_NAME + " Shell에 오신 것을 환영합니다. 'help'를 입력해보세요.");

        while (kernel.isRunning()) {
            System.out.print(prompt.render());

            if (!input.hasNextLine()) {
                break;
            }

            String line = input.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            handleLine(line);
        }

        input.close();
    }

    private void handleLine(String line) {
        String[] tokens = line.split(ForgeOSConstants.COMMAND_DELIMITER);
        String commandName = tokens[0];
        String[] args = (tokens.length > 1)
                ? java.util.Arrays.copyOfRange(tokens, 1, tokens.length)
                : new String[0];

        Command command = registry.resolve(commandName);
        SystemCallResult result = command.execute(kernel, args);

        System.out.println(result.getMessage());
    }
}
```

---

# 31. shell/ShellPrompt.java

**Path**
`src/main/java/forgeframework/shell/ShellPrompt.java`

```java
package forgeframework.shell;

import forgeframework.common.ForgeOSConstants;

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
```

---

# 32. syscall/SystemCallRequest.java

**Path**
`src/main/java/forgeframework/syscall/SystemCallRequest.java`

```java
package forgeframework.syscall;

/**
 * Shell/Command 계층이 Kernel에게 전달하는 시스템 콜 요청.
 *
 * <p>Shell은 절대로 Kernel의 서브시스템에 직접 접근하지 않으며,
 * 반드시 이 요청 객체를 통해서만 {@code Kernel.handleSystemCall()}을 호출한다.</p>
 */
public final class SystemCallRequest {

    private final SystemCallType type;
    private final String[] args;

    public SystemCallRequest(SystemCallType type, String[] args) {
        this.type = type;
        this.args = (args == null) ? new String[0] : args;
    }

    public SystemCallRequest(SystemCallType type) {
        this(type, new String[0]);
    }

    public SystemCallType getType() {
        return type;
    }

    public String[] getArgs() {
        return args;
    }
}
```

---

# 33. syscall/SystemCallResult.java

**Path**
`src/main/java/forgeframework/syscall/SystemCallResult.java`

```java
package forgeframework.syscall;

/**
 * Kernel이 시스템 콜 처리 후 반환하는 결과.
 *
 * <p>성공 여부, 사용자에게 보여줄 메시지, 부가 데이터를 포함한다.
 * 정적 팩토리 메서드({@link #success}, {@link #failure})를 통해서만 생성한다.</p>
 */
public final class SystemCallResult {

    private final boolean success;
    private final String message;
    private final Object data;

    private SystemCallResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static SystemCallResult success(String message) {
        return new SystemCallResult(true, message, null);
    }

    public static SystemCallResult success(String message, Object data) {
        return new SystemCallResult(true, message, data);
    }

    public static SystemCallResult failure(String message) {
        return new SystemCallResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
```

---

# 34. syscall/SystemCallType.java

**Path**
`src/main/java/forgeframework/syscall/SystemCallType.java`

```java
package forgeframework.syscall;

public enum SystemCallType {
    HELP,
    SHUTDOWN,
    UPTIME,
    PS,
    EXEC,
    KILL,
    SCHEDULER
}
```

---

