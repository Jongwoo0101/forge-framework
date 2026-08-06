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
