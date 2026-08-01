# ForgeFramework Source Dump

총 Java 파일 수 : **33개**

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
import forgeframework.process.scheduler.FcfsScheduler;
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

            // 원하는 스케줄러로 변경 가능 (전략 패턴 적용 추후엔 scheduler 명령어로 원하는 스케줄러 선택 가능)
            Scheduler activeScheduler = new RoundRobinScheduler();
            // Scheduler activeScheduler = new FcfsScheduler();

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

public final class ExecCommand implements Command {
    @Override public String name() { return "exec"; }
    @Override public String description() { return "새 프로세스를 생성합니다. (exec <이름>)"; }
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

# 10. command/ShutdownCommand.java

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

# 11. command/UnknownCommand.java

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

# 12. command/UptimeCommand.java

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

# 13. common/ForgeOSConstants.java

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

    private ForgeOSConstants() {
        // 인스턴스화 방지
    }
}
```

---

# 14. exception/ForgeOSException.java

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

# 15. hardware/HardwareTimer.java

**Path**
`src/main/java/forgeframework/hardware/HardwareTimer.java`

```java
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
```

---

# 16. kernel/Kernel.java

**Path**
`src/main/java/forgeframework/kernel/Kernel.java`

```java
package forgeframework.kernel;

import forgeframework.exception.ForgeOSException;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.process.Process;
import forgeframework.process.ProcessManager;
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
 *
 * <p>Phase 1에서는 서브시스템 매니저가 아직 존재하지 않으므로
 * 커널 자체 기능(HELP, SHUTDOWN, UPTIME)만 처리한다.
 * 이후 Phase에서 {@code registerProcessManager()} 형태의 확장 지점을 통해
 * Process/Memory/FileSystem Manager 등을 등록받아 위임하는 구조로 확장한다.</p>
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

    /**
     * Kernel Singleton 인스턴스를 최초 1회 초기화한다.
     * BootManager의 KERNEL_INIT 단계에서만 호출되어야 한다.
     *
     * @param logger 커널이 사용할 이벤트 로거
     * @return 초기화된 Kernel 인스턴스
     */
    public static synchronized Kernel initialize(EventLogger logger) {
        if (instance != null) {
            throw new ForgeOSException("Kernel은 이미 초기화되었습니다.");
        }
        instance = new Kernel(logger);
        return instance;
    }

    /**
     * 이미 초기화된 Kernel Singleton 인스턴스를 반환한다.
     *
     * @return Kernel 인스턴스
     * @throws ForgeOSException 아직 초기화되지 않은 경우
     */
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

    /**
     * 시스템 콜을 처리하는 유일한 진입점.
     *
     * <p>Shell/Command 계층은 반드시 이 메서드를 통해서만 커널 기능에 접근한다.</p>
     *
     * @param request 처리할 시스템 콜 요청
     * @return 처리 결과
     */
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
        sb.append(String.format("%-5s | %-12s | %-10s | %s\n", "PID", "STATE", "CPU_TIME", "NAME"));
        sb.append("-".repeat(50));

        for (Process p : processManager.getAllProcesses().values()) {
            String stateIndicator = (p.getPcb().getState() == forgeframework.process.ProcessState.RUNNING) ? "*" : " ";
            sb.append(String.format("\n%-5d | %-12s | %-10d | %s%s",
                    p.getPcb().getPid(), p.getPcb().getState(), p.getPcb().getCpuTimeUsed(), p.getName(), stateIndicator));
        }
        return SystemCallResult.success(sb.toString());
    }

    private SystemCallResult handleExec(String[] args) {
        if (processManager == null) {
            return SystemCallResult.failure("ProcessManager가 로드되지 않았습니다.");
        }
        if (args.length == 0) {
            return SystemCallResult.failure("사용법: exec <프로세스명>");
        }
        Process p = processManager.createProcess(args[0]);
        return SystemCallResult.success("프로세스가 생성되었습니다. (PID: " + p.getPcb().getPid() + ")");
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

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 커널이 현재 실행 중인지 여부를 반환한다.
     * Shell의 REPL 루프 종료 조건으로 사용된다.
     *
     * @return 실행 중이면 true
     */
    public boolean isRunning() {
        return running;
    }
}
```

---

# 17. logger/ConsoleLogListener.java

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

# 18. logger/EventLogger.java

**Path**
`src/main/java/forgeframework/logger/EventLogger.java`

```java
package forgeframework.logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ForgeOS 내 모든 이벤트를 기록하는 로거.
 *
 * <p>Observer 패턴의 Subject 역할을 수행하며,
 * 등록된 {@link LogListener}들에게 로그 발생을 통지한다.
 * Boot, Kernel, Shell 등 모든 계층이 공통으로 이 로거를 사용한다.</p>
 */
public class EventLogger {

    private final List<LogListener> listeners = new ArrayList<>();

    /**
     * 로그 리스너를 등록한다.
     *
     * @param listener 등록할 리스너
     */
    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    /**
     * 로그 리스너 등록을 해제한다.
     *
     * @param listener 해제할 리스너
     */
    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    /**
     * 새로운 로그 이벤트를 기록하고 모든 리스너에게 통지한다.
     *
     * @param level   로그 심각도
     * @param message 로그 메시지
     */
    public void log(LogLevel level, String message) {
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

# 19. logger/LogEntry.java

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

# 20. logger/LogLevel.java

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

# 21. logger/LogListener.java

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

# 22. process/Process.java

**Path**
`src/main/java/forgeframework/process/Process.java`

```java
package forgeframework.process;

public class Process {
    private final String name;
    private final ProcessControlBlock pcb;

    public Process(int pid, String name) {
        this.name = name;
        this.pcb = new ProcessControlBlock(pid);
    }

    public String getName() { return name; }
    public ProcessControlBlock getPcb() { return pcb; }
}
```

---

# 23. process/ProcessControlBlock.java

**Path**
`src/main/java/forgeframework/process/ProcessControlBlock.java`

```java
package forgeframework.process;

public class ProcessControlBlock {
    private final int pid;
    private ProcessState state;
    private long cpuTimeUsed;

    public ProcessControlBlock(int pid) {
        this.pid = pid;
        this.state = ProcessState.NEW;
        this.cpuTimeUsed = 0;
    }

    public int getPid() { return pid; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { this.state = state; }
    public long getCpuTimeUsed() { return cpuTimeUsed; }
    public void incrementCpuTime() { this.cpuTimeUsed++; }
}
```

---

# 24. process/ProcessManager.java

**Path**
`src/main/java/forgeframework/process/ProcessManager.java`

```java
package forgeframework.process;

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
    private final Scheduler scheduler;

    private final Map<Integer, Process> processTable = new LinkedHashMap<>();
    private final AtomicInteger pidGenerator = new AtomicInteger(1);

    private Process currentRunningProcess = null;

    // 선점형 스케줄러용 타임 퀀텀 (3 Ticks)
    private final int timeQuantum = 3;
    private int quantumTick = 0;

    public ProcessManager(EventLogger logger, Scheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
        logger.log(LogLevel.INFO, "ProcessManager initialized [Scheduler: " + scheduler.getName() + "]");
    }

    public synchronized Process createProcess(String name) {
        int pid = pidGenerator.getAndIncrement();
        Process newProcess = new Process(pid, name);

        processTable.put(pid, newProcess);
        newProcess.getPcb().setState(ProcessState.READY);
        scheduler.addProcess(newProcess);

        logger.log(LogLevel.INFO, "Process created: [PID=" + pid + "] " + name);
        return newProcess;
    }

    public synchronized boolean killProcess(int pid) {
        Process target = processTable.get(pid);
        if (target == null || target.getPcb().getState() == ProcessState.TERMINATED) {
            return false;
        }

        target.getPcb().setState(ProcessState.TERMINATED);

        if (currentRunningProcess != null && currentRunningProcess.getPcb().getPid() == pid) {
            currentRunningProcess = null;
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
     */
    public synchronized void handleTimerInterrupt() {
        if (currentRunningProcess != null) {
            currentRunningProcess.getPcb().incrementCpuTime();
            quantumTick++;

            // 스케줄러가 선점형(Preemptive)일 때만 타임 퀀텀 검사 후 강제 교환
            if (scheduler.isPreemptive() && quantumTick >= timeQuantum) {
                contextSwitch();
            }
        } else {
            // 실행 중인 프로세스가 없으면 Ready Queue를 확인하여 스케줄링
            if (!scheduler.isEmpty()) {
                contextSwitch();
            }
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

# 25. process/ProcessState.java

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

# 26. process/scheduler/FcfsScheduler.java

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

# 27. process/scheduler/RoundRobinScheduler.java

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

# 28. process/scheduler/Scheduler.java

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

# 29. shell/ForgeShell.java

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
import forgeframework.common.ForgeOSConstants;
import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallResult;

import java.util.Scanner;

/**
 * ForgeOS의 사용자 인터페이스인 CLI Shell.
 *
 * <p>사용자 입력을 받아 {@link CommandRegistry}를 통해 해당하는
 * {@link Command}를 찾아 실행한다.
 *
 * <p><b>중요:</b> ForgeShell은 절대로 Kernel의 서브시스템에 직접 접근하지 않는다.
 * 모든 기능 실행은 반드시 Command → Kernel.handleSystemCall() 경로를 거친다.</p>
 */
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

        // Phase 2: 프로세스 관리 명령어 등록
        registry.register(new PsCommand());
        registry.register(new ExecCommand());
        registry.register(new KillCommand());
    }

    /**
     * Shell의 REPL(Read-Eval-Print Loop)을 실행한다.
     * Kernel이 실행 중(running) 상태인 동안 계속 반복된다.
     */
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

# 30. shell/ShellPrompt.java

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

# 31. syscall/SystemCallRequest.java

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

# 32. syscall/SystemCallResult.java

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

# 33. syscall/SystemCallType.java

**Path**
`src/main/java/forgeframework/syscall/SystemCallType.java`

```java
package forgeframework.syscall;

/**
 * Kernel이 처리할 수 있는 시스템 콜의 종류.
 * Phase가 진행됨에 따라 Process, Memory, FileSystem 등
 * 각 서브시스템에 대응하는 항목이 계속 추가될 예정이다.
 * Phase 1에서는 커널 자체 기능(HELP, SHUTDOWN, UPTIME)만 정의한다.
 */
public enum SystemCallType {

    /** 사용 가능한 명령어 목록 조회. */
    HELP,

    /** 시스템 종료. */
    SHUTDOWN,

    /** 커널 가동 시간 조회. */
    UPTIME,

    /** 현재 실행 및 대기 중인 프로세스 상태 목록 조회. */
    PS,

    /** 새로운 프로세스 생성 요청. */
    EXEC,

    /** 특정 프로세스의 강제 종료 요청 */
    KILL
}
```

---

