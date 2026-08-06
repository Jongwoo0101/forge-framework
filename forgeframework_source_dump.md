# ForgeFramework Source Dump

총 Java 파일 수 : **73개**

---

## Files

- `Main.java`
- `boot/BootManager.java`
- `boot/BootStage.java`
- `command/CatCommand.java`
- `command/CdCommand.java`
- `command/Command.java`
- `command/CommandRegistry.java`
- `command/ExecCommand.java`
- `command/FrameTableCommand.java`
- `command/FreeCommand.java`
- `command/HelpCommand.java`
- `command/KillCommand.java`
- `command/LsCommand.java`
- `command/MallocCommand.java`
- `command/MeminfoCommand.java`
- `command/MkdirCommand.java`
- `command/PsCommand.java`
- `command/PwdCommand.java`
- `command/RmCommand.java`
- `command/SchedulerCommand.java`
- `command/ShutdownCommand.java`
- `command/TouchCommand.java`
- `command/TranslateCommand.java`
- `command/TreeCommand.java`
- `command/UnknownCommand.java`
- `command/UptimeCommand.java`
- `command/WriteCommand.java`
- `common/ForgeOSConstants.java`
- `exception/ForgeOSException.java`
- `filesystem/Bitmap.java`
- `filesystem/Directory.java`
- `filesystem/DirectoryEntryDto.java`
- `filesystem/FileContentDto.java`
- `filesystem/FileListDto.java`
- `filesystem/FileSystemManager.java`
- `filesystem/Inode.java`
- `filesystem/InodeType.java`
- `filesystem/SuperBlock.java`
- `filesystem/TreeNodeDto.java`
- `filesystem/VirtualDisk.java`
- `hardware/HardwareTimer.java`
- `kernel/Kernel.java`
- `logger/ConsoleLogListener.java`
- `logger/EventLogger.java`
- `logger/LogEntry.java`
- `logger/LogLevel.java`
- `logger/LogListener.java`
- `memory/Frame.java`
- `memory/FrameInfo.java`
- `memory/FrameTable.java`
- `memory/Heap.java`
- `memory/HeapBlock.java`
- `memory/HeapSnapshot.java`
- `memory/MemoryManager.java`
- `memory/MemorySnapshot.java`
- `memory/PageTable.java`
- `memory/PhysicalMemory.java`
- `memory/Tlb.java`
- `memory/TranslationResult.java`
- `memory/VirtualAddressSpace.java`
- `process/Process.java`
- `process/ProcessControlBlock.java`
- `process/ProcessManager.java`
- `process/ProcessState.java`
- `process/scheduler/FcfsScheduler.java`
- `process/scheduler/RoundRobinScheduler.java`
- `process/scheduler/Scheduler.java`
- `shell/ForgeShell.java`
- `shell/ShellContext.java`
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
import forgeframework.filesystem.FileSystemManager;
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

            FileSystemManager fileSystemManager = new FileSystemManager(
                    logger,
                    ForgeOSConstants.TOTAL_BLOCKS,
                    ForgeOSConstants.BLOCK_SIZE,
                    ForgeOSConstants.TOTAL_INODES
            );
            kernel.registerFileSystemManager(fileSystemManager);

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

# 4. command/CatCommand.java

**Path**
`src/main/java/forgeframework/command/CatCommand.java`

```java
package forgeframework.command;

import forgeframework.filesystem.FileContentDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 파일 내용을 출력하는 명령어. 사용법: cat &lt;name&gt;
 */
public final class CatCommand implements Command {

    private final ShellContext context;

    public CatCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "cat";
    }

    @Override
    public String description() {
        return "파일 내용을 출력합니다. (cat <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        if (args.length < 1) {
            return SystemCallResult.failure("사용법: cat <name>");
        }
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.CAT, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        FileContentDto dto = (FileContentDto) result.getData();
        return SystemCallResult.success(dto.content());
    }
}
```

---

# 5. command/CdCommand.java

**Path**
`src/main/java/forgeframework/command/CdCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 작업 디렉터리를 변경하는 명령어. 사용법: cd &lt;path&gt;
 *
 * <p>Kernel이 대상 경로가 유효한 디렉터리인지 검증하고 절대경로로 해석해서
 * 돌려주면, 성공한 경우에만 이 명령어가 {@link ShellContext}의 CWD를 갱신한다
 * (Kernel 자신은 상태를 갖지 않는다).</p>
 */
public final class CdCommand implements Command {

    private final ShellContext context;

    public CdCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "cd";
    }

    @Override
    public String description() {
        return "작업 디렉터리를 변경합니다. (cd <path>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        String target = (args.length > 0) ? args[0] : "/";
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.CD, new String[]{context.getCwd(), target}
        ));
        if (result.isSuccess()) {
            context.setCwd((String) result.getData());
        }
        return result;
    }
}
```

---

# 6. command/Command.java

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

# 7. command/CommandRegistry.java

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

# 8. command/ExecCommand.java

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

# 9. command/FrameTableCommand.java

**Path**
`src/main/java/forgeframework/command/FrameTableCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.memory.FrameInfo;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.util.List;

/**
 * 물리 프레임 전체의 상태(할당 여부, 소유 pid, 매핑된 페이지 번호)를 표로 출력하는 명령어.
 *
 * <p>Kernel/MemoryManager는 {@link FrameInfo} 리스트라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — MeminfoCommand/PsCommand와
 * 동일한 원칙을 따른다.</p>
 */
public final class FrameTableCommand implements Command {

    @Override
    public String name() {
        return "frametable";
    }

    @Override
    public String description() {
        return "물리 프레임 테이블(프레임별 소유자/페이지 매핑)을 출력합니다.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SystemCallResult execute(Kernel kernel, String[] args) {
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(SystemCallType.FRAMETABLE));
        if (!result.isSuccess()) {
            return result;
        }

        List<FrameInfo> frames = (List<FrameInfo>) result.getData();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s | %-6s | %-5s | %s%n", "FRAME", "STATUS", "PID", "PAGE"));
        for (FrameInfo frame : frames) {
            sb.append(String.format(
                    "%-6d | %-6s | %-5s | %s%n",
                    frame.frameNumber(),
                    frame.allocated() ? "USED" : "FREE",
                    frame.allocated() ? String.valueOf(frame.ownerPid()) : "-",
                    frame.allocated() ? String.valueOf(frame.pageNumber()) : "-"
            ));
        }

        return SystemCallResult.success(sb.toString().stripTrailing());
    }
}
```

---

# 10. command/FreeCommand.java

**Path**
`src/main/java/forgeframework/command/FreeCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 할당된 메모리를 해제하는 명령어. 사용법: free &lt;PID&gt; &lt;address&gt;
 */
public final class FreeCommand implements Command {
    @Override public String name() { return "free"; }
    @Override public String description() { return "할당된 메모리를 해제합니다. (free <PID> <address>)"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.FREE, args));
    }
}
```

---

# 11. command/HelpCommand.java

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

# 12. command/KillCommand.java

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

# 13. command/LsCommand.java

**Path**
`src/main/java/forgeframework/command/LsCommand.java`

```java
package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.filesystem.FileListDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 디렉터리 내용을 나열하는 명령어. 사용법: ls [path]
 *
 * <p>Kernel/FileSystemManager는 {@link FileListDto}라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — PsCommand/MeminfoCommand와
 * 동일한 원칙을 따른다.</p>
 */
public final class LsCommand implements Command {

    private final ShellContext context;

    public LsCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return "디렉터리 내용을 나열합니다. (ls [path])";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        String target = (args.length > 0) ? args[0] : ".";
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.LS, new String[]{context.getCwd(), target}
        ));
        if (!result.isSuccess()) {
            return result;
        }

        FileListDto dto = (FileListDto) result.getData();
        StringBuilder sb = new StringBuilder();
        sb.append(dto.currentPath()).append('\n');
        sb.append(String.format("%-20s | %-10s | %s%n", "NAME", "TYPE", "SIZE"));
        for (DirectoryEntryDto entry : dto.entries()) {
            sb.append(String.format("%-20s | %-10s | %d%n", entry.name(), entry.type(), entry.size()));
        }
        return SystemCallResult.success(sb.toString().stripTrailing());
    }
}
```

---

# 14. command/MallocCommand.java

**Path**
`src/main/java/forgeframework/command/MallocCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 프로세스의 힙에 메모리를 할당하는 명령어. 사용법: malloc &lt;PID&gt; &lt;size&gt;
 */
public final class MallocCommand implements Command {
    @Override public String name() { return "malloc"; }
    @Override public String description() { return "힙에 메모리를 할당합니다. (malloc <PID> <size>)"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.MALLOC, args));
    }
}
```

---

# 15. command/MeminfoCommand.java

**Path**
`src/main/java/forgeframework/command/MeminfoCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.memory.HeapSnapshot;
import forgeframework.memory.MemorySnapshot;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 물리 메모리 / 프로세스별 힙 / TLB 사용 현황을 출력하는 명령어.
 *
 * <p>Kernel/MemoryManager는 {@link MemorySnapshot}이라는 순수 데이터만 반환하고,
 * 표 형태로 꾸미는 건 이 클래스(Shell 계층)의 책임이다 — PsCommand와 동일한
 * 원칙을 따른다.</p>
 */
public final class MeminfoCommand implements Command {

    @Override
    public String name() {
        return "meminfo";
    }

    @Override
    public String description() {
        return "물리 메모리/힙/TLB 사용 현황을 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(SystemCallType.MEMINFO));
        if (!result.isSuccess()) {
            return result;
        }

        MemorySnapshot snapshot = (MemorySnapshot) result.getData();
        StringBuilder sb = new StringBuilder();

        sb.append("[Physical Memory]\n");
        sb.append(String.format(
                "Frame Size: %d, Total Frames: %d (Total: %d bytes)%n",
                snapshot.frameSize(), snapshot.totalFrames(),
                (long) snapshot.frameSize() * snapshot.totalFrames()
        ));
        sb.append(String.format(
                "Used Frames: %d, Free Frames: %d%n",
                snapshot.usedFrames(), snapshot.freeFrames()
        ));

        sb.append("\n[Process Heap]\n");
        if (snapshot.heapByPid().isEmpty()) {
            sb.append("등록된 프로세스가 없습니다.\n");
        } else {
            sb.append(String.format("%-5s | %-10s | %-10s | %s%n", "PID", "CAPACITY", "USED", "FREE"));
            for (HeapSnapshot heap : snapshot.heapByPid().values()) {
                sb.append(String.format(
                        "%-5d | %-10d | %-10d | %d%n",
                        heap.pid(), heap.capacity(), heap.used(), heap.free()
                ));
            }
        }

        sb.append("\n[TLB]\n");
        sb.append(String.format(
                "Hits: %d, Misses: %d, Hit Ratio: %.1f%%",
                snapshot.tlbHits(), snapshot.tlbMisses(), snapshot.tlbHitRatio() * 100
        ));

        return SystemCallResult.success(sb.toString());
    }
}
```

---

# 16. command/MkdirCommand.java

**Path**
`src/main/java/forgeframework/command/MkdirCommand.java`

```java
package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 새 디렉터리를 생성하는 명령어. 사용법: mkdir &lt;name&gt;
 */
public final class MkdirCommand implements Command {

    private final ShellContext context;

    public MkdirCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "mkdir";
    }

    @Override
    public String description() {
        return "새 디렉터리를 생성합니다. (mkdir <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
//        아래 조건식을 사용하게 된다면 인자를 하나씩 떨어뜨려서 명령어를 전달 시
//        ex) mkdir d 9
//        첫번째 인자인 "d"만 전달되고 나머지 9는 아무런 경고없이 무시되는 문제가 있음
//        TouchCommand.java, RMCommand.java도 동일한 문제를 가지고 있어 모두 수정한다.
//        if (args.length < 1) {
//            return SystemCallResult.failure("사용법: mkdir <name>");
//        }

        // 해결 버전
        if ( args.length != 1 ) {
            return SystemCallResult.failure("사용법: mkdir <name> (공백 없는 이름 하나만 입력)");
        }

        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.MKDIR, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        DirectoryEntryDto dto = (DirectoryEntryDto) result.getData();
        return SystemCallResult.success("디렉터리가 생성되었습니다: " + dto.name());
    }
}
```

---

# 17. command/PsCommand.java

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

# 18. command/PwdCommand.java

**Path**
`src/main/java/forgeframework/command/PwdCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallResult;

/**
 * 현재 작업 디렉터리를 출력하는 명령어.
 *
 * <p>CWD는 Kernel이 아니라 Shell({@link ShellContext})이 들고 있는 상태이므로,
 * 이 명령어는 시스템 콜을 전혀 보내지 않는다 (Kernel의 무상태성을 지키기 위한
 * 의도적인 예외).</p>
 */
public final class PwdCommand implements Command {

    private final ShellContext context;

    public PwdCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "pwd";
    }

    @Override
    public String description() {
        return "현재 작업 디렉터리를 출력합니다.";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        return SystemCallResult.success(context.getCwd());
    }
}
```

---

# 19. command/RmCommand.java

**Path**
`src/main/java/forgeframework/command/RmCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 파일 또는 비어있는 디렉터리를 삭제하는 명령어. 사용법: rm &lt;name&gt;
 */
public final class RmCommand implements Command {

    private final ShellContext context;

    public RmCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "rm";
    }

    @Override
    public String description() {
        return "파일 또는 빈 디렉터리를 삭제합니다. (rm <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        // MkdirCommand.java 33 ~ 36 line 참고
        /* if (args.length < 1) {
            return SystemCallResult.failure("사용법: rm <name>");
        } */

        if (args.length != 1) {
            return SystemCallResult.failure("사용법: rm <name> (공백 없는 이름 하나만 입력)");
        }

        return kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.RM, new String[]{context.getCwd(), args[0]}
        ));
    }
}
```

---

# 20. command/SchedulerCommand.java

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

# 21. command/ShutdownCommand.java

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

# 22. command/TouchCommand.java

**Path**
`src/main/java/forgeframework/command/TouchCommand.java`

```java
package forgeframework.command;

import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 빈 파일을 생성하는 명령어. 사용법: touch &lt;name&gt;
 */
public final class TouchCommand implements Command {

    private final ShellContext context;

    public TouchCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "touch";
    }

    @Override
    public String description() {
        return "빈 파일을 생성합니다. (touch <name>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        // MkdirCommand.java 33 ~ 36 line 참고
        /* if (args.length < 1) {
            return SystemCallResult.failure("사용법: touch <name>");
        } */

        if ( args.length != 1 ) {
            return SystemCallResult.failure("사용법: touch <name> (공백 없는 이름 하나만 입력)");
        }

        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.TOUCH, new String[]{context.getCwd(), args[0]}
        ));
        if (!result.isSuccess()) {
            return result;
        }
        DirectoryEntryDto dto = (DirectoryEntryDto) result.getData();
        return SystemCallResult.success("파일이 생성되었습니다: " + dto.name());
    }
}
```

---

# 23. command/TranslateCommand.java

**Path**
`src/main/java/forgeframework/command/TranslateCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

/**
 * 가상 주소를 물리 주소로 변환해 보여주는 명령어 (Paging + TLB 동작 확인용).
 * 사용법: translate &lt;PID&gt; &lt;virtualAddress&gt;
 */
public final class TranslateCommand implements Command {
    @Override public String name() { return "translate"; }
    @Override public String description() { return "가상 주소를 물리 주소로 변환합니다. (translate <PID> <vaddr>)"; }
    @Override public SystemCallResult execute(Kernel kernel, String[] args) {
        return kernel.handleSystemCall(new SystemCallRequest(SystemCallType.TRANSLATE, args));
    }
}
```

---

# 24. command/TreeCommand.java

**Path**
`src/main/java/forgeframework/command/TreeCommand.java`

```java
package forgeframework.command;

import forgeframework.filesystem.TreeNodeDto;
import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.util.List;

/**
 * 디렉터리 구조를 트리 형태로 출력하는 명령어. 사용법: tree [path]
 *
 * <p>Kernel/FileSystemManager는 재귀적 DTO({@link TreeNodeDto})만 반환하고,
 * ├──/└── 같은 ASCII 트리 렌더링은 전적으로 이 클래스의 책임이다.</p>
 */
public final class TreeCommand implements Command {

    private final ShellContext context;

    public TreeCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "tree";
    }

    @Override
    public String description() {
        return "디렉터리 구조를 트리 형태로 출력합니다. (tree [path])";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        String target = (args.length > 0) ? args[0] : ".";
        SystemCallResult result = kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.TREE, new String[]{context.getCwd(), target}
        ));
        if (!result.isSuccess()) {
            return result;
        }

        TreeNodeDto root = (TreeNodeDto) result.getData();
        StringBuilder sb = new StringBuilder();
        render(sb, root, "", true, true);
        return SystemCallResult.success(sb.toString().stripTrailing());
    }

    private void render(StringBuilder sb, TreeNodeDto node, String prefix, boolean isLast, boolean isRoot) {
        String suffix = (node.isDirectory() && !node.name().endsWith("/")) ? "/" : "";
        if (isRoot) {
            sb.append(node.name()).append(suffix).append('\n');
        } else {
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(node.name()).append(suffix).append('\n');
        }

        String childPrefix = isRoot ? "" : prefix + (isLast ? "    " : "│   ");
        List<TreeNodeDto> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            render(sb, children.get(i), childPrefix, i == children.size() - 1, false);
        }
    }
}
```

---

# 25. command/UnknownCommand.java

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

# 26. command/UptimeCommand.java

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

# 27. command/WriteCommand.java

**Path**
`src/main/java/forgeframework/command/WriteCommand.java`

```java
package forgeframework.command;

import forgeframework.kernel.Kernel;
import forgeframework.shell.ShellContext;
import forgeframework.syscall.SystemCallRequest;
import forgeframework.syscall.SystemCallResult;
import forgeframework.syscall.SystemCallType;

import java.util.Arrays;

/**
 * 파일 내용을 덮어쓰는 명령어. 사용법: write &lt;name&gt; &lt;text...&gt;
 *
 * <p>ForgeShell이 입력을 공백 기준으로 토큰화하기 때문에, text에 공백이
 * 여러 단어로 들어오면 args[1] 이후를 전부 공백으로 다시 합쳐서 원래
 * 문자열을 복원한다.</p>
 */
public final class WriteCommand implements Command {

    private final ShellContext context;

    public WriteCommand(ShellContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String description() {
        return "파일 내용을 덮어씁니다. (write <name> <text>)";
    }

    @Override
    public SystemCallResult execute(Kernel kernel, String[] args) {
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: write <name> <text>");
        }
        String content = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        return kernel.handleSystemCall(new SystemCallRequest(
                SystemCallType.WRITE, new String[]{context.getCwd(), args[0], content}
        ));
    }
}
```

---

# 28. common/ForgeOSConstants.java

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
    public static final String OS_VERSION = "1.0-phase4";

    /**
     * Shell 프롬프트 접두사. 실제 프롬프트는 ShellPrompt가
     * {@code PREFIX + ":" + cwd + SUFFIX} 형태로 CWD를 반영해 렌더링한다.
     */
    public static final String SHELL_PROMPT_PREFIX = "forgeframework";

    /** Shell 프롬프트 접미사. */
    public static final String SHELL_PROMPT_SUFFIX = "> ";

    /** 부팅 단계 사이의 연출용 대기 시간(ms). */
    public static final long BOOT_STAGE_DELAY_MS = 150L;

    /** 명령어 파싱 시 사용하는 구분자. */
    public static final String COMMAND_DELIMITER = "\\s+";

    /** exec 시 burstTime 인자를 생략했을 때 적용되는 기본 실행 시간(tick). */
    public static final long DEFAULT_BURST_TIME = 5L;

    /** 선점형 스케줄러의 기본 타임 퀀텀(tick). */
    public static final int DEFAULT_TIME_QUANTUM = 3;

    /** HardwareTimer의 1 tick당 실제 대기 시간(ms). */
    public static final long TICK_INTERVAL_MS = 1000L;

    /** 프레임(및 페이지) 하나의 크기(byte). */
    public static final int FRAME_SIZE = 4;

    /** 물리 메모리의 총 프레임 개수. */
    public static final int TOTAL_FRAMES = 16;

    /** TLB가 캐싱할 수 있는 (pid, 페이지) 항목 최대 개수. */
    public static final int TLB_CAPACITY = 4;

    /** 가상 디스크의 블록 하나 크기(byte). */
    public static final int BLOCK_SIZE = 16;

    /** 가상 디스크의 총 블록 개수. */
    public static final int TOTAL_BLOCKS = 16;

    /** 파일 시스템의 총 inode 개수 (루트 디렉터리 포함). */
    public static final int TOTAL_INODES = 16;

    private ForgeOSConstants() {
        // 인스턴스화 방지
    }
}
```

---

# 29. exception/ForgeOSException.java

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

# 30. filesystem/Bitmap.java

**Path**
`src/main/java/forgeframework/filesystem/Bitmap.java`

```java
package forgeframework.filesystem;

/**
 * inode 번호와 데이터 블록 번호 둘 다에 재사용되는 범용 할당 비트맵.
 *
 * <p>어떤 인덱스가 비어있는지 순차 탐색으로 찾아 할당한다(first-fit).
 * 실패 시 예외 대신 -1을 반환한다 — 호출부(FileSystemManager)가 롤백 여부를
 * 판단해야 하는 경우가 많아서, 예외보다는 값으로 실패를 전달하는 쪽이 더 유연하다.</p>
 */
public final class Bitmap {

    private final boolean[] used;

    public Bitmap(int size) {
        this.used = new boolean[size];
    }

    /**
     * 비어있는 인덱스 하나를 찾아 사용 중으로 표시한다.
     *
     * @return 할당된 인덱스, 남은 공간이 없으면 -1
     */
    public int allocate() {
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    public void free(int index) {
        used[index] = false;
    }

    public boolean isUsed(int index) {
        return used[index];
    }

    public int size() {
        return used.length;
    }

    public int getUsedCount() {
        int count = 0;
        for (boolean b : used) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public int getFreeCount() {
        return used.length - getUsedCount();
    }
}
```

---

# 31. filesystem/Directory.java

**Path**
`src/main/java/forgeframework/filesystem/Directory.java`

```java
package forgeframework.filesystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 디렉터리 하나가 담고 있는 엔트리(이름 → inode 번호) 목록.
 *
 * <p>실제 파일 시스템이라면 이 매핑 자체도 데이터 블록에 직렬화되어야 하지만,
 * 지금 단계에서는 시뮬레이션의 편의를 위해 자바 객체로 메모리에 유지한다
 * (inode의 DIRECTORY 타입과 1:1로 대응하는 보조 자료구조로
 * {@link FileSystemManager}가 관리). {@code LinkedHashMap}을 써서 생성
 * 순서가 {@code ls}/{@code tree} 출력에 유지되도록 했다.</p>
 */
public final class Directory {

    private final Map<String, Integer> entries = new LinkedHashMap<>();

    public void addEntry(String name, int inodeNumber) {
        entries.put(name, inodeNumber);
    }

    public void removeEntry(String name) {
        entries.remove(name);
    }

    public Integer resolve(String name) {
        return entries.get(name);
    }

    public Map<String, Integer> getEntries() {
        return Collections.unmodifiableMap(entries);
    }
}
```

---

# 32. filesystem/DirectoryEntryDto.java

**Path**
`src/main/java/forgeframework/filesystem/DirectoryEntryDto.java`

```java
package forgeframework.filesystem;

/**
 * {@code ls} 결과에서 파일/폴더 하나를 나타내는 DTO.
 */
public record DirectoryEntryDto(String name, String type, int size) {
}
```

---

# 33. filesystem/FileContentDto.java

**Path**
`src/main/java/forgeframework/filesystem/FileContentDto.java`

```java
package forgeframework.filesystem;

/**
 * {@code cat} 명령의 반환 DTO.
 */
public record FileContentDto(String name, String content) {
}
```

---

# 34. filesystem/FileListDto.java

**Path**
`src/main/java/forgeframework/filesystem/FileListDto.java`

```java
package forgeframework.filesystem;

import java.util.List;

/**
 * {@code ls} 명령의 최종 반환 DTO. currentPath는 상대경로/CWD를 반영해
 * 해석된 절대경로다.
 */
public record FileListDto(String currentPath, List<DirectoryEntryDto> entries) {
}
```

---

# 35. filesystem/FileSystemManager.java

**Path**
`src/main/java/forgeframework/filesystem/FileSystemManager.java`

```java
package forgeframework.filesystem;

import forgeframework.exception.ForgeOSException;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파일 시스템 전체를 총괄하는 매니저 (Facade).
 *
 * <p>Kernel은 무상태(stateless)를 유지해야 하므로, 현재 작업 디렉터리(CWD)는
 * 이 클래스도 Kernel도 아닌 Shell({@code ShellContext})이 들고 있다. 모든
 * public 메서드는 호출부(Kernel)로부터 CWD와 대상 경로를 함께 전달받아,
 * 그때그때 절대경로로 해석한 뒤 동작한다 — 즉 이 클래스 자체에는 "현재
 * 어디에 있는지"에 대한 상태가 전혀 없다.</p>
 *
 * <p>경로 해석은 항상 {@code .}/{@code ..}을 포함해 이 클래스 안에서
 * 통합 처리한다({@link #normalizeComponents}).</p>
 */
public final class FileSystemManager {

    private final EventLogger logger;
    private final VirtualDisk disk;
    private final Bitmap blockBitmap;
    private final Bitmap inodeBitmap;
    private final Inode[] inodeTable;
    private final Map<Integer, Directory> directories = new HashMap<>();
    private final SuperBlock superBlock;

    public FileSystemManager(EventLogger logger, int totalBlocks, int blockSize, int totalInodes) {
        this.logger = logger;
        this.disk = new VirtualDisk(totalBlocks, blockSize);
        this.blockBitmap = new Bitmap(totalBlocks);
        this.inodeBitmap = new Bitmap(totalInodes);
        this.inodeTable = new Inode[totalInodes];

        int rootInodeNumber = inodeBitmap.allocate();
        inodeTable[rootInodeNumber] = new Inode(rootInodeNumber, InodeType.DIRECTORY);
        directories.put(rootInodeNumber, new Directory());

        this.superBlock = new SuperBlock(totalBlocks, blockSize, totalInodes, rootInodeNumber);
        logger.log(LogLevel.INFO,
                "FileSystemManager initialized [blocks=" + totalBlocks + ", blockSize=" + blockSize
                        + ", inodes=" + totalInodes + "]");
    }

    // ===================== 경로 해석 유틸리티 =====================

    /**
     * cwd와 targetPath를 조합해 "." / ".." 을 전부 처리한 경로 구성요소 목록을 만든다.
     * targetPath가 "/"로 시작하면 절대경로로 취급하고, 아니면 cwd 기준 상대경로로 취급한다.
     */
    private List<String> normalizeComponents(String cwd, String targetPath) {
        String base = (targetPath != null && targetPath.startsWith("/")) ? targetPath : combine(cwd, targetPath);
        String[] rawParts = base.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : rawParts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
                continue;
            }
            stack.addLast(part);
        }
        return new ArrayList<>(stack);
    }

    private String combine(String cwd, String targetPath) {
        if (targetPath == null || targetPath.isBlank() || targetPath.equals(".")) {
            return cwd;
        }
        return cwd.equals("/") ? "/" + targetPath : cwd + "/" + targetPath;
    }

    /** 경로 구성요소 목록을 루트부터 순서대로 따라가며 inode 번호를 찾는다. */
    private int walk(List<String> parts) {
        int current = superBlock.getRootInodeNumber();
        for (String part : parts) {
            Directory dir = directories.get(current);
            if (dir == null) {
                throw new ForgeOSException("디렉터리가 아닙니다.");
            }
            Integer next = dir.resolve(part);
            if (next == null) {
                throw new ForgeOSException("경로를 찾을 수 없습니다: " + part);
            }
            current = next;
        }
        return current;
    }

    private String toAbsolutePath(List<String> parts) {
        return parts.isEmpty() ? "/" : "/" + String.join("/", parts);
    }

    private int allocateInode(InodeType type) {
        int inodeNumber = inodeBitmap.allocate();
        if (inodeNumber == -1) {
            throw new ForgeOSException("inode가 부족합니다.");
        }
        inodeTable[inodeNumber] = new Inode(inodeNumber, type);
        if (type == InodeType.DIRECTORY) {
            directories.put(inodeNumber, new Directory());
        }
        return inodeNumber;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    // ===================== 공개 API =====================

    /**
     * targetPath가 유효한 디렉터리인지 확인하고, 해석된 절대경로를 반환한다.
     * Shell은 성공 시 이 반환값으로 자신의 CWD 상태를 갱신한다.
     */
    public synchronized String cd(String cwd, String targetPath) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        int inodeNumber = walk(parts);
        if (inodeTable[inodeNumber].getType() != InodeType.DIRECTORY) {
            throw new ForgeOSException("디렉터리가 아닙니다: " + targetPath);
        }
        return toAbsolutePath(parts);
    }

    public synchronized FileListDto ls(String cwd, String targetPath) {
        String resolvedTarget = (targetPath == null || targetPath.isBlank()) ? "." : targetPath;
        List<String> parts = normalizeComponents(cwd, resolvedTarget);
        int inodeNumber = walk(parts);
        Inode inode = inodeTable[inodeNumber];
        if (inode.getType() != InodeType.DIRECTORY) {
            throw new ForgeOSException("디렉터리가 아닙니다: " + targetPath);
        }

        Directory dir = directories.get(inodeNumber);
        List<DirectoryEntryDto> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dir.getEntries().entrySet()) {
            Inode childInode = inodeTable[entry.getValue()];
            entries.add(new DirectoryEntryDto(entry.getKey(), childInode.getType().name(), (int) childInode.getSize()));
        }
        return new FileListDto(toAbsolutePath(parts), entries);
    }

    public synchronized DirectoryEntryDto mkdir(String cwd, String targetPath) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        if (parts.isEmpty()) {
            throw new ForgeOSException("루트는 이 작업의 대상이 될 수 없습니다.");
        }
        String name = parts.get(parts.size() - 1);
        int parentInode = walk(parts.subList(0, parts.size() - 1));
        Directory parentDir = directories.get(parentInode);
        if (parentDir == null) {
            throw new ForgeOSException("상위 경로가 디렉터리가 아닙니다.");
        }
        if (parentDir.resolve(name) != null) {
            throw new ForgeOSException("이미 존재하는 이름입니다: " + name);
        }

        int newInodeNumber = allocateInode(InodeType.DIRECTORY);
        parentDir.addEntry(name, newInodeNumber);
        logger.log(LogLevel.INFO, "Directory created: " + toAbsolutePath(parts));
        return new DirectoryEntryDto(name, "DIRECTORY", 0);
    }

    /**
     * touch는 실제 유닉스와 동일하게 이미 있는 파일이면 조용히 성공(idempotent)한다.
     * 단, 같은 이름의 디렉터리가 이미 있으면 오류를 던진다.
     */
    public synchronized DirectoryEntryDto touch(String cwd, String targetPath) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        if (parts.isEmpty()) {
            throw new ForgeOSException("루트는 이 작업의 대상이 될 수 없습니다.");
        }
        String name = parts.get(parts.size() - 1);
        int parentInode = walk(parts.subList(0, parts.size() - 1));
        Directory parentDir = directories.get(parentInode);
        if (parentDir == null) {
            throw new ForgeOSException("상위 경로가 디렉터리가 아닙니다.");
        }

        Integer existing = parentDir.resolve(name);
        if (existing != null) {
            Inode existingInode = inodeTable[existing];
            if (existingInode.getType() != InodeType.FILE) {
                throw new ForgeOSException("이미 디렉터리로 존재합니다: " + name);
            }
            return new DirectoryEntryDto(name, "FILE", (int) existingInode.getSize());
        }

        int newInodeNumber = allocateInode(InodeType.FILE);
        parentDir.addEntry(name, newInodeNumber);
        logger.log(LogLevel.INFO, "File created: " + toAbsolutePath(parts));
        return new DirectoryEntryDto(name, "FILE", 0);
    }

    /**
     * 파일 또는 "비어있는" 디렉터리를 삭제한다. 비어있지 않은 디렉터리는 거부한다.
     */
    public synchronized void rm(String cwd, String targetPath) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        if (parts.isEmpty()) {
            throw new ForgeOSException("루트 디렉터리는 삭제할 수 없습니다.");
        }
        String name = parts.get(parts.size() - 1);
        int parentInode = walk(parts.subList(0, parts.size() - 1));
        Directory parentDir = directories.get(parentInode);
        if (parentDir == null) {
            throw new ForgeOSException("상위 경로가 디렉터리가 아닙니다.");
        }
        Integer childInodeNumber = parentDir.resolve(name);
        if (childInodeNumber == null) {
            throw new ForgeOSException("존재하지 않는 경로입니다: " + targetPath);
        }

        Inode childInode = inodeTable[childInodeNumber];
        if (childInode.getType() == InodeType.DIRECTORY) {
            Directory childDir = directories.get(childInodeNumber);
            if (childDir != null && !childDir.getEntries().isEmpty()) {
                throw new ForgeOSException("비어있지 않은 디렉터리는 삭제할 수 없습니다: " + targetPath);
            }
            directories.remove(childInodeNumber);
        } else {
            for (int blockNumber : childInode.getBlocks()) {
                blockBitmap.free(blockNumber);
            }
        }

        inodeBitmap.free(childInodeNumber);
        parentDir.removeEntry(name);
        logger.log(LogLevel.INFO, "Removed: " + toAbsolutePath(parts));
    }

    /**
     * 파일 내용을 content로 완전히 덮어쓴다(append 아님). 디스크 공간이 부족하면
     * 이미 확보한 새 블록을 롤백하고, 기존 파일 내용은 그대로 보존한다.
     */
    public synchronized int write(String cwd, String targetPath, String content) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        int inodeNumber = walk(parts);
        Inode inode = inodeTable[inodeNumber];
        if (inode.getType() != InodeType.FILE) {
            throw new ForgeOSException("디렉터리에는 쓸 수 없습니다: " + targetPath);
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int blockSize = disk.getBlockSize();
        int blocksNeeded = (bytes.length == 0) ? 0 : ceilDiv(bytes.length, blockSize);

        List<Integer> newBlocks = new ArrayList<>();
        for (int i = 0; i < blocksNeeded; i++) {
            int blockNumber = blockBitmap.allocate();
            if (blockNumber == -1) {
                for (int bn : newBlocks) {
                    blockBitmap.free(bn);
                }
                throw new ForgeOSException("디스크 공간이 부족합니다.");
            }
            newBlocks.add(blockNumber);
        }

        // 새 블록 확보에 전부 성공한 뒤에야 기존 블록을 반납한다.
        // 중간에 실패하면 위에서 예외를 던지고 끝나므로, 기존 파일 내용이 보존된다.
        for (int oldBlock : inode.getBlocks()) {
            blockBitmap.free(oldBlock);
        }

        for (int i = 0; i < newBlocks.size(); i++) {
            int start = i * blockSize;
            int end = Math.min(start + blockSize, bytes.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(bytes, start, chunk, 0, end - start);
            disk.writeBlock(newBlocks.get(i), chunk);
        }

        inode.setBlocks(newBlocks);
        inode.setSize(bytes.length);
        logger.log(LogLevel.INFO, "File written: " + toAbsolutePath(parts) + " (" + bytes.length + "B)");
        return bytes.length;
    }

    public synchronized FileContentDto cat(String cwd, String targetPath) {
        List<String> parts = normalizeComponents(cwd, targetPath);
        int inodeNumber = walk(parts);
        Inode inode = inodeTable[inodeNumber];
        if (inode.getType() != InodeType.FILE) {
            throw new ForgeOSException("디렉터리는 cat으로 읽을 수 없습니다: " + targetPath);
        }

        byte[] buffer = new byte[(int) inode.getSize()];
        int offset = 0;
        for (int blockNumber : inode.getBlocks()) {
            byte[] blockData = disk.readBlock(blockNumber);
            int copyLen = Math.min(buffer.length - offset, blockData.length);
            System.arraycopy(blockData, 0, buffer, offset, copyLen);
            offset += copyLen;
        }

        String name = parts.isEmpty() ? "/" : parts.get(parts.size() - 1);
        return new FileContentDto(name, new String(buffer, StandardCharsets.UTF_8));
    }

    public synchronized TreeNodeDto tree(String cwd, String targetPath) {
        String resolvedTarget = (targetPath == null || targetPath.isBlank()) ? "." : targetPath;
        List<String> parts = normalizeComponents(cwd, resolvedTarget);
        int inodeNumber = walk(parts);
        String name = parts.isEmpty() ? "/" : parts.get(parts.size() - 1);
        return buildTree(inodeNumber, name);
    }

    private TreeNodeDto buildTree(int inodeNumber, String name) {
        Inode inode = inodeTable[inodeNumber];
        if (inode.getType() == InodeType.FILE) {
            return new TreeNodeDto(name, false, List.of());
        }
        Directory dir = directories.get(inodeNumber);
        List<TreeNodeDto> children = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dir.getEntries().entrySet()) {
            children.add(buildTree(entry.getValue(), entry.getKey()));
        }
        return new TreeNodeDto(name, true, children);
    }
}
```

---

# 36. filesystem/Inode.java

**Path**
`src/main/java/forgeframework/filesystem/Inode.java`

```java
package forgeframework.filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 파일 또는 디렉터리의 메타데이터.
 *
 * <p>실제 유닉스 파일 시스템처럼 이름은 inode에 저장하지 않는다 — 이름은
 * 오직 {@link Directory}의 엔트리(이름 → inode 번호)에만 존재한다. 그래서
 * 같은 파일에 여러 이름(하드링크)을 붙이는 것도 개념적으로는 가능한 구조지만,
 * 지금 단계에서는 링크 기능 자체를 만들지 않았다.</p>
 */
public final class Inode {

    private final int inodeNumber;
    private final InodeType type;
    private long size;
    private List<Integer> blocks = new ArrayList<>();

    public Inode(int inodeNumber, InodeType type) {
        this.inodeNumber = inodeNumber;
        this.type = type;
    }

    public int getInodeNumber() {
        return inodeNumber;
    }

    public InodeType getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<Integer> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<Integer> blocks) {
        this.blocks = blocks;
    }
}
```

---

# 37. filesystem/InodeType.java

**Path**
`src/main/java/forgeframework/filesystem/InodeType.java`

```java
package forgeframework.filesystem;

/**
 * inode가 파일인지 디렉터리인지 구분하는 열거형.
 */
public enum InodeType {
    FILE,
    DIRECTORY
}
```

---

# 38. filesystem/SuperBlock.java

**Path**
`src/main/java/forgeframework/filesystem/SuperBlock.java`

```java
package forgeframework.filesystem;

/**
 * 파일 시스템 전체의 메타데이터를 담는 슈퍼 블록.
 */
public final class SuperBlock {

    private final int totalBlocks;
    private final int blockSize;
    private final int totalInodes;
    private final int rootInodeNumber;

    public SuperBlock(int totalBlocks, int blockSize, int totalInodes, int rootInodeNumber) {
        this.totalBlocks = totalBlocks;
        this.blockSize = blockSize;
        this.totalInodes = totalInodes;
        this.rootInodeNumber = rootInodeNumber;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getTotalInodes() {
        return totalInodes;
    }

    public int getRootInodeNumber() {
        return rootInodeNumber;
    }
}
```

---

# 39. filesystem/TreeNodeDto.java

**Path**
`src/main/java/forgeframework/filesystem/TreeNodeDto.java`

```java
package forgeframework.filesystem;

import java.util.List;

/**
 * {@code tree} 명령용 재귀적 트리 구조 DTO. 렌더링(├──/└── 등)은 전적으로
 * TreeCommand(Shell 계층)의 책임이다.
 */
public record TreeNodeDto(String name, boolean isDirectory, List<TreeNodeDto> children) {
}
```

---

# 40. filesystem/VirtualDisk.java

**Path**
`src/main/java/forgeframework/filesystem/VirtualDisk.java`

```java
package forgeframework.filesystem;

import java.util.Arrays;

/**
 * disk.img 역할을 하는 가상 디스크.
 *
 * <p>고정 크기의 데이터 블록 배열({@code byte[][]})로 구성된다. 실제 파일
 * 내용은 문자열을 바이트로 변환해 블록 단위로 저장/조회된다.</p>
 */
public final class VirtualDisk {

    private final byte[][] blocks;
    private final int blockSize;

    public VirtualDisk(int totalBlocks, int blockSize) {
        this.blockSize = blockSize;
        this.blocks = new byte[totalBlocks][blockSize];
    }

    /**
     * 블록 하나를 읽는다. 방어적 복사본을 반환한다.
     *
     * @param blockNumber 읽을 블록 번호
     * @return 블록 내용의 복사본 (길이는 항상 blockSize)
     */
    public byte[] readBlock(int blockNumber) {
        return blocks[blockNumber].clone();
    }

    /**
     * 블록 하나에 데이터를 쓴다. data가 blockSize보다 짧으면 나머지는 0으로 채운다.
     *
     * @param blockNumber 쓸 블록 번호
     * @param data        기록할 데이터 (blockSize 이하여야 함)
     */
    public void writeBlock(int blockNumber, byte[] data) {
        byte[] target = blocks[blockNumber];
        Arrays.fill(target, (byte) 0);
        System.arraycopy(data, 0, target, 0, Math.min(data.length, blockSize));
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getTotalBlocks() {
        return blocks.length;
    }
}
```

---

# 41. hardware/HardwareTimer.java

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

# 42. kernel/Kernel.java

**Path**
`src/main/java/forgeframework/kernel/Kernel.java`

```java
package forgeframework.kernel;

import forgeframework.common.ForgeOSConstants;
import forgeframework.exception.ForgeOSException;
import forgeframework.filesystem.DirectoryEntryDto;
import forgeframework.filesystem.FileContentDto;
import forgeframework.filesystem.FileListDto;
import forgeframework.filesystem.FileSystemManager;
import forgeframework.filesystem.TreeNodeDto;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;
import forgeframework.memory.FrameInfo;
import forgeframework.memory.MemoryManager;
import forgeframework.memory.MemorySnapshot;
import forgeframework.memory.TranslationResult;
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
import java.util.List;

/**
 * ForgeOS의 유일한 관리자(Kernel).
 * 유일한 관리자는 개뿔 나중에 framework api화 시킬때 이거 하나하나 다 뜯어고쳐야함 ㅅㅂ
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
    private MemoryManager memoryManager;
    private FileSystemManager fileSystemManager;

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
     * MemoryManager를 커널에 등록한다.
     * BootManager의 SUBSYSTEM_INIT 단계에서 호출된다.
     *
     * @param memoryManager 등록할 메모리 매니저
     */
    public void registerMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * FileSystemManager를 커널에 등록한다.
     * BootManager의 SUBSYSTEM_INIT 단계에서 호출된다.
     *
     * @param fileSystemManager 등록할 파일 시스템 매니저
     */
    public void registerFileSystemManager(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
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
            case MALLOC -> handleMalloc(request.getArgs());
            case FREE -> handleFree(request.getArgs());
            case MEMINFO -> handleMeminfo();
            case TRANSLATE -> handleTranslate(request.getArgs());
            case FRAMETABLE -> handleFrameTable();
            case CD -> handleCd(request.getArgs());
            case LS -> handleLs(request.getArgs());
            case MKDIR -> handleMkdir(request.getArgs());
            case TOUCH -> handleTouch(request.getArgs());
            case RM -> handleRm(request.getArgs());
            case WRITE -> handleWrite(request.getArgs());
            case CAT -> handleCat(request.getArgs());
            case TREE -> handleTree(request.getArgs());
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

            Process p = processManager.createProcess(name, burstTime); // 1. 프로세스 생성 및 ReadyQueue 등록
            if (memoryManager != null) {
                memoryManager.registerProcess(p.getPcb().getPid()); // 2, 메모리 공간 (Heap, PageTable) 초기화 / 메모리 자원 완전 할당
            }
            processManager.readyProcess(p.getPcb().getPid()); // 비로소 스케줄러에 진입

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

    /**
     * 프로세스의 힙에 size바이트를 할당한다. 사용법: malloc &lt;PID&gt; &lt;size&gt;
     */
    private SystemCallResult handleMalloc(String[] args) {
        if (memoryManager == null) {
            return SystemCallResult.failure("MemoryManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: malloc <PID> <size>");
        }
        try {
            int pid = Integer.parseInt(args[0]);
            long size = Long.parseLong(args[1]);
            if (size <= 0) {
                return SystemCallResult.failure("size는 1 이상이어야 합니다.");
            }
            long address = memoryManager.malloc(pid, size);
            return SystemCallResult.success(
                    "PID " + pid + ": " + size + "바이트 할당됨 (가상 주소: " + address + ")"
            );
        } catch (NumberFormatException e) {
            return SystemCallResult.failure("PID와 size는 숫자여야 합니다.");
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 프로세스의 힙에서 address에 할당된 블록을 해제한다. 사용법: free &lt;PID&gt; &lt;address&gt;
     */
    private SystemCallResult handleFree(String[] args) {
        if (memoryManager == null) {
            return SystemCallResult.failure("MemoryManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: free <PID> <address>");
        }
        try {
            int pid = Integer.parseInt(args[0]);
            long address = Long.parseLong(args[1]);
            memoryManager.free(pid, address);
            return SystemCallResult.success("PID " + pid + ": 주소 " + address + " 해제됨");
        } catch (NumberFormatException e) {
            return SystemCallResult.failure("PID와 address는 숫자여야 합니다.");
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 물리 메모리/힙/TLB 사용 현황 스냅샷을 반환한다. 표 형태로 꾸미는 것은
     * MeminfoCommand(Shell 계층)의 책임이라, 여기서는 순수 데이터만 반환한다.
     */
    private SystemCallResult handleMeminfo() {
        if (memoryManager == null) {
            return SystemCallResult.failure("MemoryManager가 로드되지 않았습니다.");
        }
        MemorySnapshot snapshot = memoryManager.getSnapshot();
        return SystemCallResult.success("", snapshot);
    }

    /**
     * 가상 주소를 물리 주소로 변환한다 (Paging + TLB 동작을 직접 확인하기 위한 명령).
     * 사용법: translate &lt;PID&gt; &lt;virtualAddress&gt;
     */
    private SystemCallResult handleTranslate(String[] args) {
        if (memoryManager == null) {
            return SystemCallResult.failure("MemoryManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: translate <PID> <virtualAddress>");
        }
        try {
            int pid = Integer.parseInt(args[0]);
            long virtualAddress = Long.parseLong(args[1]);
            TranslationResult result = memoryManager.translate(pid, virtualAddress);
            return SystemCallResult.success(String.format(
                    "가상주소 %d -> 물리주소 %d (페이지 #%d -> 프레임 #%d, TLB %s)",
                    result.virtualAddress(), result.physicalAddress(),
                    result.pageNumber(), result.frameNumber(),
                    result.tlbHit() ? "HIT" : "MISS"
            ));
        } catch (NumberFormatException e) {
            return SystemCallResult.failure("PID와 주소는 숫자여야 합니다.");
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 물리 프레임 전체의 상태(할당 여부, 소유 pid, 매핑된 페이지 번호)를 반환한다
     * (Frame Table을 직접 확인하기 위한 명령). 표 형태로 꾸미는 것은
     * FrameTableCommand(Shell 계층)의 책임이라, 여기서는 순수 데이터만 반환한다.
     */
    private SystemCallResult handleFrameTable() {
        if (memoryManager == null) {
            return SystemCallResult.failure("MemoryManager가 로드되지 않았습니다.");
        }
        List<FrameInfo> snapshot = memoryManager.getFrameTableSnapshot();
        return SystemCallResult.success("", snapshot);
    }

    /**
     * targetPath가 유효한 디렉터리인지 확인하고, 해석된 절대경로를 데이터로 반환한다.
     * 사용법: cd &lt;path&gt;. args = [cwd, targetPath]
     */
    private SystemCallResult handleCd(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: cd <path>");
        }
        try {
            String resolved = fileSystemManager.cd(args[0], args[1]);
            return SystemCallResult.success(resolved, resolved);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 디렉터리 내용을 나열한다. 표로 꾸미는 것은 LsCommand(Shell 계층)의 책임이라,
     * 여기서는 FileListDto(순수 데이터)만 반환한다. args = [cwd, targetPath]
     */
    private SystemCallResult handleLs(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: ls [path]");
        }
        try {
            FileListDto dto = fileSystemManager.ls(args[0], args[1]);
            return SystemCallResult.success("", dto);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 새 디렉터리를 생성한다. args = [cwd, targetPath]
     */
    private SystemCallResult handleMkdir(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: mkdir <name>");
        }
        try {
            DirectoryEntryDto dto = fileSystemManager.mkdir(args[0], args[1]);
            return SystemCallResult.success("", dto);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 크기 0인 빈 파일을 생성한다 (이미 있으면 조용히 성공). args = [cwd, targetPath]
     */
    private SystemCallResult handleTouch(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: touch <name>");
        }
        try {
            DirectoryEntryDto dto = fileSystemManager.touch(args[0], args[1]);
            return SystemCallResult.success("", dto);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 파일 또는 비어있는 디렉터리를 삭제한다. args = [cwd, targetPath]
     */
    private SystemCallResult handleRm(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: rm <name>");
        }
        try {
            fileSystemManager.rm(args[0], args[1]);
            return SystemCallResult.success("삭제되었습니다: " + args[1]);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 파일 내용을 덮어쓴다. args = [cwd, targetPath, content]
     */
    private SystemCallResult handleWrite(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 3) {
            return SystemCallResult.failure("사용법: write <name> <text>");
        }
        try {
            int written = fileSystemManager.write(args[0], args[1], args[2]);
            return SystemCallResult.success(written + "바이트 기록됨", written);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 파일 내용을 읽는다. args = [cwd, targetPath]
     */
    private SystemCallResult handleCat(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: cat <name>");
        }
        try {
            FileContentDto dto = fileSystemManager.cat(args[0], args[1]);
            return SystemCallResult.success("", dto);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
    }

    /**
     * 경로 하위 전체를 재귀적으로 탐색해 트리 구조(DTO)를 반환한다.
     * 렌더링(├──/└── 등)은 TreeCommand(Shell 계층)의 책임이다. args = [cwd, targetPath]
     */
    private SystemCallResult handleTree(String[] args) {
        if (fileSystemManager == null) {
            return SystemCallResult.failure("FileSystemManager가 로드되지 않았습니다.");
        }
        if (args.length < 2) {
            return SystemCallResult.failure("사용법: tree [path]");
        }
        try {
            TreeNodeDto dto = fileSystemManager.tree(args[0], args[1]);
            return SystemCallResult.success("", dto);
        } catch (ForgeOSException e) {
            return SystemCallResult.failure(e.getMessage());
        }
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

# 43. logger/ConsoleLogListener.java

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

        // DEBUG 레벨의 로그는 출력에서 제외 (입력 방해 등의 이유로 추후 이 if 절 삭제 후 배포 얘정)
        if (entry.getLevel() != LogLevel.DEBUG) {
            System.out.println(entry.toFormattedString());
        }
    }
}
```

---

# 44. logger/EventLogger.java

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

# 45. logger/LogEntry.java

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

# 46. logger/LogLevel.java

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

# 47. logger/LogListener.java

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

# 48. memory/Frame.java

**Path**
`src/main/java/forgeframework/memory/Frame.java`

```java
package forgeframework.memory;

/**
 * 물리 메모리의 최소 단위인 프레임 하나.
 *
 * <p>어느 프로세스가 이 프레임을 점유하고 있는지(ownerPid)뿐 아니라, 그 프로세스의
 * 어느 가상 페이지에 매핑되어 있는지(pageNumber)도 함께 들고 있다. 이 두 정보 덕분에
 * "프레임 번호만 가지고 역으로 소유자/페이지를 즉시 찾는" Frame Table 조회가 가능하다.</p>
 */
public final class Frame {

    private final int frameNumber;
    private boolean allocated;
    private int ownerPid = -1;
    private int pageNumber = -1;

    public Frame(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public int getOwnerPid() {
        return ownerPid;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    void allocate(int pid, int pageNumber) {
        this.allocated = true;
        this.ownerPid = pid;
        this.pageNumber = pageNumber;
    }

    void release() {
        this.allocated = false;
        this.ownerPid = -1;
        this.pageNumber = -1;
    }
}
```

---

# 49. memory/FrameInfo.java

**Path**
`src/main/java/forgeframework/memory/FrameInfo.java`

```java
package forgeframework.memory;

/**
 * {@code frametable} 명령이 반환하는 프레임 하나의 상태.
 */
public record FrameInfo(int frameNumber, boolean allocated, int ownerPid, int pageNumber) {
}
```

---

# 50. memory/FrameTable.java

**Path**
`src/main/java/forgeframework/memory/FrameTable.java`

```java
package forgeframework.memory;

import java.util.List;

/**
 * 프레임 번호 → (소유 프로세스, 매핑된 가상 페이지 번호)를 즉시 역조회할 수 있는
 * 프레임 테이블.
 *
 * <p>배열 인덱스 자체가 프레임 번호이므로 조회는 항상 O(1)이다. {@link PhysicalMemory}는
 * 이 클래스를 감싸서(composition) 기존 공개 API(allocateFrame/freeFrame 등)를
 * 그대로 유지하고, 역조회가 필요한 곳({@code frametable} 명령 등)에서는 이 클래스를
 * 직접 사용한다.</p>
 */
public final class FrameTable {

    private final Frame[] frames;
    private final int frameSize;

    public FrameTable(int totalFrames, int frameSize) {
        this.frameSize = frameSize;
        this.frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            frames[i] = new Frame(i);
        }
    }

    /**
     * 비어있는 프레임 하나를 찾아 pid의 pageNumber에 할당한다.
     *
     * @param pid        프레임을 사용할 프로세스의 pid
     * @param pageNumber 이 프레임이 매핑될 가상 페이지 번호
     * @return 할당된 프레임, 남은 프레임이 없으면 null
     */
    public Frame allocate(int pid, int pageNumber) {
        for (Frame frame : frames) {
            if (!frame.isAllocated()) {
                frame.allocate(pid, pageNumber);
                return frame;
            }
        }
        return null;
    }

    public void free(int frameNumber) {
        frames[frameNumber].release();
    }

    /**
     * 프레임 번호로 소유자/페이지 정보를 즉시 역조회한다 — 이게 Frame Table의 핵심 기능이다.
     *
     * @param frameNumber 조회할 프레임 번호
     * @return 해당 프레임 객체 (할당 여부, 소유 pid, 페이지 번호를 담고 있음)
     */
    public Frame lookup(int frameNumber) {
        return frames[frameNumber];
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getTotalFrames() {
        return frames.length;
    }

    public int getUsedCount() {
        int count = 0;
        for (Frame frame : frames) {
            if (frame.isAllocated()) {
                count++;
            }
        }
        return count;
    }

    public int getFreeCount() {
        return frames.length - getUsedCount();
    }

    public List<Frame> getAllFrames() {
        return List.of(frames);
    }
}
```

---

# 51. memory/Heap.java

**Path**
`src/main/java/forgeframework/memory/Heap.java`

```java
package forgeframework.memory;

import forgeframework.exception.ForgeOSException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 프로세스 하나의 힙 영역을 관리하는 free-list 기반 first-fit 할당기.
 * <p>실제 sbrk/brk처럼, 필요한 만큼만 커지고 free()해도 곧바로 OS에 반납하지는
 * 않는다(내부적으로 free 블록으로 남아 재사용을 기다린다). 물리 프레임/페이지
 * 할당 같은 저수준 작업은 이 클래스가 알지 못한다 — {@link MemoryManager}가
 * {@link #grow(long)}를 통해 필요한 만큼 용량을 늘려주는 식으로 계층을 분리했다.</p>
 */
public final class Heap {

    private final List<HeapBlock> blocks = new ArrayList<>();
    private long capacity = 0;

    public long getCapacity() {
        return capacity;
    }

    public long getUsedBytes() {
        long used = 0;
        for (HeapBlock block : blocks) {
            if (!block.isFree()) {
                used += block.getSize();
            }
        }
        return used;
    }

    public long getFreeBytes() {
        return capacity - getUsedBytes();
    }

    /**
     * 힙의 총 용량을 늘린다. 늘어난 구간은 하나의 자유 블록으로 추가된다.
     * 물리 프레임을 실제로 확보하는 것은 {@link MemoryManager}의 책임이며,
     * 이 메서드는 순수하게 "장부 상의 용량"만 늘린다.
     * @param additionalBytes 추가할 바이트 수
     */
    public void grow(long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        blocks.add(new HeapBlock(capacity, additionalBytes, true));
        capacity += additionalBytes;
        mergeAdjacentFreeBlocks();
    }

    /**
     * Heap 객체에서 마지막 블록이 Free 상태라면 그 크기를 반환하는 메서드를 만들고, {@link MemoryManager}는
     * 그 크기를 뺀 순수하게 부족한 크기만큼만 페이지를 요청해야함
     */
    public long getEndFreeSize() {
        if (!blocks.isEmpty()) {
            HeapBlock last = blocks.getLast();
            if (last.isFree()) {
                return last.getSize();
            }
        }
        return 0;
    }

    /**
     * first-fit으로 size바이트를 할당한다. 기존 자유 블록 중 맞는 게 없으면
     * null을 반환한다 — 이 경우 호출자({@link MemoryManager})가 {@link #grow}로
     * 용량을 늘린 뒤 다시 시도해야 한다.
     *
     * @param size 요청 크기
     * @return 할당된 블록의 시작 주소, 공간이 없으면 null
     */
    public Long allocate(long size) {
        for (int i = 0; i < blocks.size(); i++) {
            HeapBlock block = blocks.get(i);
            if (block.isFree() && block.getSize() >= size) {
                long address = block.getStartAddress();
                if (block.getSize() == size) {
                    block.setFree(false);
                } else {
                    HeapBlock allocated = new HeapBlock(address, size, false);
                    HeapBlock remainder = new HeapBlock(address + size, block.getSize() - size, true);
                    blocks.remove(i);
                    blocks.add(i, remainder);
                    blocks.add(i, allocated);
                }
                return address;
            }
        }
        return null;
    }

    /**
     * 주소로 블록을 찾아 해제하고, 인접한 자유 블록과 병합한다.
     *
     * @param address 해제할 블록의 시작 주소
     * @throws ForgeOSException 해당 주소에 할당된 블록이 없는 경우
     */
    public void free(long address) {
        for (HeapBlock block : blocks) {
            if (block.getStartAddress() == address && !block.isFree()) {
                block.setFree(true);
                mergeAdjacentFreeBlocks();
                return;
            }
        }
        throw new ForgeOSException("잘못된 주소이거나 이미 해제된 블록입니다: " + address);
    }

    private void mergeAdjacentFreeBlocks() {
        blocks.sort(Comparator.comparingLong(HeapBlock::getStartAddress));
        for (int i = 0; i < blocks.size() - 1; ) {
            HeapBlock current = blocks.get(i);
            HeapBlock next = blocks.get(i + 1);
            if (current.isFree() && next.isFree()) {
                current.setSize(current.getSize() + next.getSize());
                blocks.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /**
     * 테스트/디버깅 용도로 현재 블록 목록의 읽기 전용 뷰를 제공한다.
     */
    List<HeapBlock> getBlocksView() {
        return Collections.unmodifiableList(blocks);
    }
}
```

---

# 52. memory/HeapBlock.java

**Path**
`src/main/java/forgeframework/memory/HeapBlock.java`

```java
package forgeframework.memory;

/**
 * 힙 안의 연속된 바이트 구간 하나. 할당된 블록이거나 자유 블록이다.
 *
 * <p>{@link Heap} 내부 구현 세부사항이라 패키지 외부에는 노출하지 않는다 — meminfo 등
 * 외부에 보여줄 정보는 {@link HeapSnapshot}처럼 요약된 형태로만 제공한다.</p>
 */
final class HeapBlock {

    private final long startAddress;
    private long size;
    private boolean free;

    HeapBlock(long startAddress, long size, boolean free) {
        this.startAddress = startAddress;
        this.size = size;
        this.free = free;
    }

    long getStartAddress() {
        return startAddress;
    }

    long getSize() {
        return size;
    }

    void setSize(long size) {
        this.size = size;
    }

    boolean isFree() {
        return free;
    }

    void setFree(boolean free) {
        this.free = free;
    }
}
```

---

# 53. memory/HeapSnapshot.java

**Path**
`src/main/java/forgeframework/memory/HeapSnapshot.java`

```java
package forgeframework.memory;

/**
 * 프로세스 한 개의 힙 사용 현황 요약. {@code meminfo} 명령 출력에 사용된다.
 */
public record HeapSnapshot(int pid, long capacity, long used, long free) {
}
```

---

# 54. memory/MemoryManager.java

**Path**
`src/main/java/forgeframework/memory/MemoryManager.java`

```java
package forgeframework.memory;

import forgeframework.exception.ForgeOSException;
import forgeframework.logger.EventLogger;
import forgeframework.logger.LogLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메모리 서브시스템 전체를 총괄하는 매니저.
 *
 * <p>{@link PhysicalMemory}(물리 프레임), 프로세스별 {@link VirtualAddressSpace}
 * (가상 주소 공간 = Heap + PageTable), {@link Tlb}(주소 변환 캐시)를 조합해서
 * "가상 주소만 아는 프로세스"와 "실제로는 유한한 물리 메모리" 사이를 중개한다.</p>
 *
 * <p>Kernel이 ProcessManager를 다루는 것과 동일하게, Shell/Command 계층은 이
 * 클래스에 직접 접근하지 않고 반드시 Kernel을 거친다.</p>
 */
public final class MemoryManager {

    private final EventLogger logger;
    private final PhysicalMemory physicalMemory;
    private final Tlb tlb;

    private final Map<Integer, VirtualAddressSpace> addressSpaces = new LinkedHashMap<>();

    public MemoryManager(EventLogger logger, int totalFrames, int frameSize, int tlbCapacity) {
        this.logger = logger;
        this.physicalMemory = new PhysicalMemory(totalFrames, frameSize);
        this.tlb = new Tlb(tlbCapacity);
        logger.log(LogLevel.INFO,
                "MemoryManager initialized [frames=" + totalFrames + ", frameSize=" + frameSize + "]");
    }

    /**
     * 새 프로세스를 위한 빈 가상 주소 공간(Heap + PageTable)을 준비한다.
     * 프로세스 생성(exec) 시 Kernel이 호출한다.
     *
     * @param pid 등록할 프로세스의 pid
     */
    public synchronized void registerProcess(int pid) {
        addressSpaces.put(pid, new VirtualAddressSpace(pid));
        logger.log(LogLevel.INFO, "Memory space registered: [PID=" + pid + "]");
    }

    /**
     * 프로세스가 점유하고 있던 모든 물리 프레임/힙/TLB 항목을 회수한다.
     * ProcessManager의 종료 리스너를 통해 프로세스가 끝날 때(kill 또는 burst 완료
     * 자연 종료 모두) 호출된다.
     *
     * @param pid 회수할 프로세스의 pid
     */
    public synchronized void releaseProcess(int pid) {
        VirtualAddressSpace addressSpace = addressSpaces.remove(pid);
        if (addressSpace != null) {
            for (int frameNumber : addressSpace.getPageTable().mappedFrames()) {
                physicalMemory.freeFrame(frameNumber);
            }
        }
        tlb.invalidateForPid(pid);
        logger.log(LogLevel.INFO, "Memory space released: [PID=" + pid + "]");
    }

    /**
     * 프로세스의 힙에서 size바이트를 할당한다.
     *
     * <p>기존 자유 블록으로 충당이 안 되면 필요한 만큼 페이지 단위로 물리 프레임을
     * 새로 확보한 뒤(전부 확보 못 하면 이미 확보한 것까지 롤백) 힙 용량을 늘려서
     * 재시도한다.</p>
     *
     * @param pid  할당받을 프로세스의 pid
     * @param size 요청 크기(byte)
     * @return 할당된 가상 주소
     * @throws ForgeOSException 프로세스가 등록되지 않았거나 물리 메모리가 부족한 경우
     */
    public synchronized long malloc(int pid, long size) {
        VirtualAddressSpace addressSpace = addressSpaces.get(pid);
        if (addressSpace == null) {
            throw new ForgeOSException("등록되지 않은 프로세스입니다: PID " + pid);
        }
        Heap heap = addressSpace.getHeap();

        Long address = heap.allocate(size);
        if (address != null) {
            logger.log(LogLevel.DEBUG, "Memory allocated (existing block): [PID=" + pid + "] " + size + "B");
            return address;
        }

        growHeapForAllocation(pid, addressSpace, size);

        address = heap.allocate(size);
        if (address == null) {
            // grow가 성공했는데도 allocate가 실패하는 건 내부 로직 버그이지 사용자 입력 문제가 아니다.
            throw new ForgeOSException("메모리 할당 중 내부 오류가 발생했습니다.");
        }
        logger.log(LogLevel.INFO, "Memory allocated: [PID=" + pid + "] " + size + "B at address " + address);
        return address;
    }

    private void growHeapForAllocation(int pid, VirtualAddressSpace addressSpace, long size) {
        Heap heap = addressSpace.getHeap();
        PageTable pageTable = addressSpace.getPageTable();

        int pageSize = physicalMemory.getFrameSize();
        long endFree = heap.getEndFreeSize();
        long actualNeeded = Math.max(0, size - endFree);

        long newCapacity = heap.getCapacity() + actualNeeded;
        int pagesNeeded = (int) (ceilDiv(newCapacity, pageSize) - ceilDiv(heap.getCapacity(), pageSize));

        int startPage = (int) (heap.getCapacity() / pageSize);

        List<Integer> newFrames = new ArrayList<>();
        for (int i = 0; i < pagesNeeded; i++) {
            int pageNumber = startPage + i;
            // 프레임 할당과 동시에 pageNumber를 넘겨서, Frame Table이 바로 역조회
            // 가능한 상태(어느 pid의 어느 페이지인지)로 만들어둔다.
            Frame frame = physicalMemory.allocateFrame(pid, pageNumber);
            if (frame == null) {
                // 부분 확보 상태로 남기지 않도록 지금까지 확보한 프레임을 전부 되돌린다.
                for (int frameNumber : newFrames) {
                    physicalMemory.freeFrame(frameNumber);
                }
                throw new ForgeOSException("메모리가 부족합니다 (물리 프레임 부족)");
            }
            newFrames.add(frame.getFrameNumber());
            pageTable.map(pageNumber, frame.getFrameNumber());
        }

        heap.grow((long) pagesNeeded * pageSize);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    /**
     * 프로세스의 힙에서 address에 할당된 블록을 해제한다.
     *
     * @param pid     소유 프로세스의 pid
     * @param address 해제할 가상 주소
     * @throws ForgeOSException 프로세스가 등록되지 않았거나 유효하지 않은 주소인 경우
     */
    public synchronized void free(int pid, long address) {
        VirtualAddressSpace addressSpace = addressSpaces.get(pid);
        if (addressSpace == null) {
            throw new ForgeOSException("등록되지 않은 프로세스입니다: PID " + pid);
        }
        addressSpace.getHeap().free(address);
        logger.log(LogLevel.INFO, "Memory freed: [PID=" + pid + "] address=" + address);
    }

    /**
     * 가상 주소를 물리 주소로 변환한다. TLB를 먼저 확인하고, miss면 PageTable을
     * 조회한 뒤 TLB에 채워 넣는다 (Paging + TLB 캐싱을 눈으로 확인할 수 있는 진입점).
     *
     * @param pid           대상 프로세스의 pid
     * @param virtualAddress 변환할 가상 주소
     * @return 변환 결과
     * @throws ForgeOSException 프로세스가 등록되지 않았거나 매핑되지 않은 주소인 경우
     */
    public synchronized TranslationResult translate(int pid, long virtualAddress) {
        VirtualAddressSpace addressSpace = addressSpaces.get(pid);
        if (addressSpace == null) {
            throw new ForgeOSException("등록되지 않은 프로세스입니다: PID " + pid);
        }
        if (virtualAddress < 0) {
            throw new ForgeOSException("가상 주소는 0 이상이어야 합니다.");
        }

        PageTable pageTable = addressSpace.getPageTable();
        int pageSize = physicalMemory.getFrameSize();
        int pageNumber = (int) (virtualAddress / pageSize);
        int offset = (int) (virtualAddress % pageSize);

        Integer frameNumber = tlb.lookup(pid, pageNumber);
        boolean hit = (frameNumber != null);
        if (!hit) {
            frameNumber = pageTable.translate(pageNumber);
            if (frameNumber == null) {
                throw new ForgeOSException("매핑되지 않은 가상 주소입니다: " + virtualAddress);
            }
            tlb.put(pid, pageNumber, frameNumber);
        }

        long physicalAddress = (long) frameNumber * pageSize + offset;
        return new TranslationResult(virtualAddress, pageNumber, frameNumber, physicalAddress, hit);
    }

    /**
     * 현재 메모리 사용 현황 전체를 스냅샷으로 반환한다 (meminfo 명령용 데이터).
     */
    public synchronized MemorySnapshot getSnapshot() {
        Map<Integer, HeapSnapshot> heapSnapshots = new LinkedHashMap<>();
        for (Map.Entry<Integer, VirtualAddressSpace> entry : addressSpaces.entrySet()) {
            Heap heap = entry.getValue().getHeap();
            heapSnapshots.put(entry.getKey(), new HeapSnapshot(
                    entry.getKey(), heap.getCapacity(), heap.getUsedBytes(), heap.getFreeBytes()
            ));
        }

        return new MemorySnapshot(
                physicalMemory.getTotalFrames(),
                physicalMemory.getFrameSize(),
                physicalMemory.getUsedFrameCount(),
                physicalMemory.getFreeFrameCount(),
                heapSnapshots,
                tlb.getHitCount(),
                tlb.getMissCount(),
                tlb.getHitRatio()
        );
    }

    /**
     * 물리 프레임 전체의 현재 상태를 프레임 번호 순서로 스냅샷 반환한다
     * ({@code frametable} 명령용 데이터). Kernel/MemoryManager는 순수 데이터만
     * 반환하고, 표로 꾸미는 건 FrameTableCommand(Shell 계층)의 책임이다.
     */
    public synchronized List<FrameInfo> getFrameTableSnapshot() {
        List<FrameInfo> snapshot = new ArrayList<>();
        for (Frame frame : physicalMemory.getFrameTable().getAllFrames()) {
            snapshot.add(new FrameInfo(
                    frame.getFrameNumber(), frame.isAllocated(), frame.getOwnerPid(), frame.getPageNumber()
            ));
        }
        return snapshot;
    }
}
```

---

# 55. memory/MemorySnapshot.java

**Path**
`src/main/java/forgeframework/memory/MemorySnapshot.java`

```java
package forgeframework.memory;

import java.util.Map;

/**
 * {@code meminfo} 명령이 반환하는 전체 스냅샷.
 *
 * <p>포맷팅(표 형태로 예쁘게 출력하는 것)은 이 클래스의 책임이 아니라
 * {@code MeminfoCommand}(Shell 계층)의 책임이다 — Kernel/MemoryManager는
 * 순수 데이터만 반환한다.</p>
 */
public record MemorySnapshot(
        int totalFrames,
        int frameSize,
        int usedFrames,
        int freeFrames,
        Map<Integer, HeapSnapshot> heapByPid,
        long tlbHits,
        long tlbMisses,
        double tlbHitRatio
) {
}
```

---

# 56. memory/PageTable.java

**Path**
`src/main/java/forgeframework/memory/PageTable.java`

```java
package forgeframework.memory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 프로세스 한 개의 가상 페이지 번호 → 물리 프레임 번호 매핑을 관리하는 페이지 테이블.
 *
 * <p>단순화를 위해 valid bit를 별도 필드로 두지 않고, "매핑이 Map에 없으면 invalid"로
 * 취급한다. Phase 3에는 스왑(디스크)이 없어 페이지가 물리 메모리에 없는 상태 자체가
 * 존재하지 않으므로(항상 즉시 할당), 이 단순화로 충분하다.</p>
 */
public final class PageTable {

    private final Map<Integer, Integer> mapping = new HashMap<>();

    public void map(int pageNumber, int frameNumber) {
        mapping.put(pageNumber, frameNumber);
    }

    public void unmap(int pageNumber) {
        mapping.remove(pageNumber);
    }

    /**
     * @param pageNumber 조회할 가상 페이지 번호
     * @return 매핑된 프레임 번호, 매핑이 없으면 null
     */
    public Integer translate(int pageNumber) {
        return mapping.get(pageNumber);
    }

    public Set<Integer> mappedPages() {
        return mapping.keySet();
    }

    public Collection<Integer> mappedFrames() {
        return mapping.values();
    }
}
```

---

# 57. memory/PhysicalMemory.java

**Path**
`src/main/java/forgeframework/memory/PhysicalMemory.java`

```java
package forgeframework.memory;

/**
 * 시뮬레이션할 물리 메모리 전체.
 *
 * <p>실제 프레임 배열/할당 로직은 {@link FrameTable}에 위임한다(composition).
 * "물리 메모리"와 "프레임 테이블"은 실제 OS 교재에서도 사실상 같은 자료구조를
 * 가리키는 경우가 많은데, 여기서는 프레임 번호로 소유자/페이지를 역조회하는 기능을
 * 별도 클래스로 명시적으로 분리해서 노출하기 위해 두 클래스로 나눠두었다. frame 할당은
 * 여전히 first-fit(첫 번째로 비어있는 프레임)으로 수행한다.</p>
 */
public final class PhysicalMemory {

    private final FrameTable frameTable;

    public PhysicalMemory(int totalFrames, int frameSize) {
        this.frameTable = new FrameTable(totalFrames, frameSize);
    }

    /**
     * 비어있는 프레임 하나를 찾아 pid의 pageNumber에 할당한다.
     *
     * @param pid        프레임을 사용할 프로세스의 pid
     * @param pageNumber 이 프레임이 매핑될 가상 페이지 번호 (Frame Table 역조회에 사용됨)
     * @return 할당된 프레임, 남은 프레임이 없으면 null
     */
    public Frame allocateFrame(int pid, int pageNumber) {
        return frameTable.allocate(pid, pageNumber);
    }

    /**
     * 프레임을 반납한다.
     *
     * @param frameNumber 반납할 프레임 번호
     */
    public void freeFrame(int frameNumber) {
        frameTable.free(frameNumber);
    }

    public int getFrameSize() {
        return frameTable.getFrameSize();
    }

    public int getTotalFrames() {
        return frameTable.getTotalFrames();
    }

    public int getUsedFrameCount() {
        return frameTable.getUsedCount();
    }

    public int getFreeFrameCount() {
        return frameTable.getFreeCount();
    }

    /**
     * 프레임 번호로 소유자/페이지를 역조회해야 하는 곳(frametable 명령 등)에서
     * 사용할 FrameTable 참조를 반환한다.
     */
    public FrameTable getFrameTable() {
        return frameTable;
    }
}
```

---

# 58. memory/Tlb.java

**Path**
`src/main/java/forgeframework/memory/Tlb.java`

```java
package forgeframework.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translation Lookaside Buffer.
 *
 * <p>(pid, 가상 페이지 번호) → 물리 프레임 번호 매핑을 캐싱해서, 매번 PageTable을
 * 조회하지 않고도 최근에 쓴 주소를 빠르게 다시 찾을 수 있게 한다. 용량이 다 차면
 * LRU(가장 오래 안 쓴 항목)를 내보낸다.</p>
 *
 * <p>hit/miss 카운터를 함께 들고 있어 {@code meminfo}에서 적중률을 보여줄 수 있다.
 * 프로세스가 종료되면 {@link #invalidateForPid(int)}로 그 프로세스의 항목만
 * 선택적으로 제거한다 (다른 프로세스의 캐시는 유지).</p>
 */
public final class Tlb {

    private final int capacity;
    private final Map<Long, Integer> cache;

    private long hitCount;
    private long missCount;

    public Tlb(int capacity) {
        this.capacity = capacity;
        // accessOrder=true 로 LRU 순서를 유지하고, removeEldestEntry로 용량 초과 시 자동 제거
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                return size() > Tlb.this.capacity;
            }
        };
    }

    private static long key(int pid, int pageNumber) {
        return ((long) pid << 32) | (pageNumber & 0xFFFFFFFFL);
    }

    /**
     * (pid, pageNumber)에 대한 캐시를 조회한다. hit/miss 카운터가 함께 갱신된다.
     *
     * @return 캐시된 프레임 번호, 없으면 null (miss)
     */
    public synchronized Integer lookup(int pid, int pageNumber) {
        Integer frameNumber = cache.get(key(pid, pageNumber));
        if (frameNumber != null) {
            hitCount++;
        } else {
            missCount++;
        }
        return frameNumber;
    }

    public synchronized void put(int pid, int pageNumber, int frameNumber) {
        cache.put(key(pid, pageNumber), frameNumber);
    }

    /**
     * 특정 프로세스에 대한 캐시 항목만 모두 제거한다 (프로세스 종료 시 호출).
     *
     * @param pid 무효화할 프로세스의 pid
     */
    public synchronized void invalidateForPid(int pid) {
        cache.keySet().removeIf(key -> (key >>> 32) == pid);
    }

    public synchronized long getHitCount() {
        return hitCount;
    }

    public synchronized long getMissCount() {
        return missCount;
    }

    public synchronized double getHitRatio() {
        long total = hitCount + missCount;
        return (total == 0) ? 0.0 : (double) hitCount / total;
    }
}
```

---

# 59. memory/TranslationResult.java

**Path**
`src/main/java/forgeframework/memory/TranslationResult.java`

```java
package forgeframework.memory;

/**
 * 가상 주소 → 물리 주소 변환 결과.
 *
 * @param tlbHit TLB에서 바로 찾았으면 true, PageTable까지 조회했으면 false(miss)
 */
public record TranslationResult(
        long virtualAddress,
        int pageNumber,
        int frameNumber,
        long physicalAddress,
        boolean tlbHit
) {
}
```

---

# 60. memory/VirtualAddressSpace.java

**Path**
`src/main/java/forgeframework/memory/VirtualAddressSpace.java`

```java
package forgeframework.memory;

/**
 * 프로세스 하나의 가상 주소 공간 전체를 표현한다.
 *
 * <p>지금까지는 {@link MemoryManager}가 {@code Map<Integer, Heap>}과
 * {@code Map<Integer, PageTable>} 두 개의 별도 Map을 병렬로 관리했는데, 이 둘은
 * 항상 같은 pid에 대해 함께 생성되고 함께 제거되는 "한 프로세스의 가상 메모리"라는
 * 하나의 개념이다. 두 Map을 따로 관리하면 이론적으로 한쪽만 등록/해제되는 불일치가
 * 생길 여지가 있는데, 이 클래스로 묶어서 pid 하나당 Map 엔트리 하나만 존재하도록
 * 단순화했다.</p>
 *
 * <p>추후 Stack이 추가되면 이 클래스가 Heap과 함께 Stack도 소유하게 될 것이다
 * (지금은 Phase 3.5 범위에서 제외).</p>
 */
public final class VirtualAddressSpace {

    private final int pid;
    private final Heap heap = new Heap();
    private final PageTable pageTable = new PageTable();

    public VirtualAddressSpace(int pid) {
        this.pid = pid;
    }

    public int getPid() {
        return pid;
    }

    public Heap getHeap() {
        return heap;
    }

    public PageTable getPageTable() {
        return pageTable;
    }
}
```

---

# 61. process/Process.java

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

# 62. process/ProcessControlBlock.java

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

# 63. process/ProcessManager.java

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
import java.util.function.IntConsumer;

/**
 * 프로세스의 생성, 상태 전이, 문맥 교환(Context Switch)을 관리하는 매니저.
 */
public class ProcessManager {
    private final EventLogger logger;
    private Scheduler scheduler;

    private final Map<Integer, Process> processTable = new LinkedHashMap<>();
    private final AtomicInteger pidGenerator = new AtomicInteger(1);

    private Process currentRunningProcess = null;

    /**
     * 프로세스가 종료(kill 또는 burst 완료로 인한 자연 종료)될 때 통지받는 리스너.
     * MemoryManager가 프로세스 종료 시 힙/페이지 테이블/TLB 항목을 회수할 수 있도록
     * BootManager가 연결해준다. ProcessManager는 MemoryManager의 존재 자체를 몰라도
     * 되도록(패키지 의존 방향을 process → memory로 만들지 않기 위해) 이런 콜백 형태로
     * 분리했다.
     */
    private IntConsumer terminationListener = pid -> { };

    // 선점형 스케줄러용 타임 퀀텀
    private final int timeQuantum = ForgeOSConstants.DEFAULT_TIME_QUANTUM;
    private int quantumTick = 0;

    public ProcessManager(EventLogger logger, Scheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
        logger.log(LogLevel.INFO, "ProcessManager initialized [Scheduler: " + scheduler.getName() + "]");
    }

    /**
     * 프로세스 종료 시 통지받을 리스너를 등록한다.
     *
     * @param listener 종료된 프로세스의 pid를 전달받을 콜백. null이면 아무 동작도 하지 않는 no-op으로 대체된다.
     */
    public void setTerminationListener(IntConsumer listener) {
        this.terminationListener = (listener != null) ? listener : (pid -> { });
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
        newProcess.getPcb().setState(ProcessState.NEW);
        // 이 아래에서 addProcess 호출 하면 x. 메모리 할당 등 준비가 덜 끝난 new 상태이기 때문
        // scheduler.addProcess(newProcess);

        logger.log(LogLevel.INFO,
                "Process created: [PID=" + pid + "] " + name + " (burstTime=" + burstTime + ")");
        return newProcess;
    }

    public synchronized void readyProcess(int pid) {
        Process p = processTable.get(pid);
        p.getPcb().setState(ProcessState.READY);
        scheduler.addProcess(p);
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
        terminationListener.accept(pid);
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
        terminationListener.accept(finished.getPcb().getPid());

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

# 64. process/ProcessState.java

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

# 65. process/scheduler/FcfsScheduler.java

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

# 66. process/scheduler/RoundRobinScheduler.java

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

# 67. process/scheduler/Scheduler.java

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

# 68. shell/ForgeShell.java

**Path**
`src/main/java/forgeframework/shell/ForgeShell.java`

```java
package forgeframework.shell;

import forgeframework.command.CatCommand;
import forgeframework.command.CdCommand;
import forgeframework.command.Command;
import forgeframework.command.CommandRegistry;
import forgeframework.command.ExecCommand;
import forgeframework.command.FrameTableCommand;
import forgeframework.command.FreeCommand;
import forgeframework.command.HelpCommand;
import forgeframework.command.KillCommand;
import forgeframework.command.LsCommand;
import forgeframework.command.MallocCommand;
import forgeframework.command.MeminfoCommand;
import forgeframework.command.MkdirCommand;
import forgeframework.command.PsCommand;
import forgeframework.command.PwdCommand;
import forgeframework.command.RmCommand;
import forgeframework.command.SchedulerCommand;
import forgeframework.command.ShutdownCommand;
import forgeframework.command.TouchCommand;
import forgeframework.command.TranslateCommand;
import forgeframework.command.TreeCommand;
import forgeframework.command.UptimeCommand;
import forgeframework.command.WriteCommand;
import forgeframework.common.ForgeOSConstants;
import forgeframework.kernel.Kernel;
import forgeframework.syscall.SystemCallResult;

import java.util.Scanner;

public class ForgeShell {

    private final Kernel kernel;
    private final CommandRegistry registry;
    private final ShellContext context;
    private final ShellPrompt prompt;
    private final Scanner input;

    public ForgeShell(Kernel kernel) {
        this.kernel = kernel;
        this.registry = new CommandRegistry();
        this.context = new ShellContext();
        this.prompt = new ShellPrompt(context);
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
        registry.register(new MallocCommand());
        registry.register(new FreeCommand());
        registry.register(new MeminfoCommand());
        registry.register(new TranslateCommand());
        registry.register(new FrameTableCommand());

        // Phase 4 — File System
        registry.register(new PwdCommand(context));
        registry.register(new CdCommand(context));
        registry.register(new LsCommand(context));
        registry.register(new MkdirCommand(context));
        registry.register(new TouchCommand(context));
        registry.register(new RmCommand(context));
        registry.register(new WriteCommand(context));
        registry.register(new CatCommand(context));
        registry.register(new TreeCommand(context));
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

# 69. shell/ShellContext.java

**Path**
`src/main/java/forgeframework/shell/ShellContext.java`

```java
package forgeframework.shell;

/**
 * Shell이 소유하는 상태를 담는 컨테이너.
 *
 * <p>Kernel은 무상태(stateless)를 유지해야 API 서버(Web/GUI)로 재사용하기
 * 쉬워진다. 그래서 현재 작업 디렉터리(CWD) 같은 "세션 상태"는 Kernel이나
 * FileSystemManager가 아니라 여기, Shell 계층에 둔다. CD/PWD/파일시스템
 * 명령어들은 생성자로 이 객체를 주입받아 CWD를 읽고(쓰기는 CdCommand만) 사용한다
 * — {@link HelpCommand}가 {@link CommandRegistry}를 주입받는 것과 동일한 패턴이다.</p>
 */
public final class ShellContext {

    private String currentWorkingDirectory = "/";

    public String getCwd() {
        return currentWorkingDirectory;
    }

    public void setCwd(String cwd) {
        this.currentWorkingDirectory = cwd;
    }
}
```

---

# 70. shell/ShellPrompt.java

**Path**
`src/main/java/forgeframework/shell/ShellPrompt.java`

```java
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
```

---

# 71. syscall/SystemCallRequest.java

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

# 72. syscall/SystemCallResult.java

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

# 73. syscall/SystemCallType.java

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
    SCHEDULER,
    MALLOC,
    FREE,
    MEMINFO,
    TRANSLATE,
    FRAMETABLE,
    CD,
    LS,
    MKDIR,
    TOUCH,
    RM,
    WRITE,
    CAT,
    TREE
}
```

---

