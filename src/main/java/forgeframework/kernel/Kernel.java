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
