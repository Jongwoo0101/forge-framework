package forgeframework.filesystem;


import java.util.ArrayList;
import java.util.List;

/**
 * 파일 또는 디렉터리의 메타데이터.
 * @author Jongwoo0101
 * 실제 유닉스 파일 시스템처럼 이름은 inode에 저장하지 않는다 — 이름은
 * 오직 {@link Directory}의 엔트리(이름 → inode 번호)에만 존재한다. 그래서
 * 같은 파일에 여러 이름(하드링크)을 붙이는 것도 개념적으로는 가능한 구조지만,
 * 지금 단계에서는 링크 기능 자체를 만들지 않았다.
 * 추후 실제 운영체제를 만들 수 있는 귀인이 나타난다면 이 inode에 관한 소스코드를
 * 실제 유닉스 파일 시스템처럼 구현해주세요.
 */
public final class Inode {
    private final int inodeNumber;
    private final InodeType type;
    private long size;
    private List<Integer> blocks = new ArrayList<>();

    // 진심 IntelliJ 개거슬린다. 저 위에서 초기화가 안되었을 수 있습니다 ㅇㅈㄹ
    // 이 아래 생성자 덕분에 빨간줄은 꺼졌는데 노란줄 계속 쳐 뜨네
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















