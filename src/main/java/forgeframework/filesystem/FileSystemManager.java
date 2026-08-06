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
