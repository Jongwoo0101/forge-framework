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
