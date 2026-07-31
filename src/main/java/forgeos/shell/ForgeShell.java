package forgeos.shell;

import forgeos.command.Command;
import forgeos.command.CommandRegistry;
import forgeos.command.HelpCommand;
import forgeos.command.ShutdownCommand;
import forgeos.command.UptimeCommand;
import forgeos.common.ForgeOSConstants;
import forgeos.kernel.Kernel;
import forgeos.syscall.SystemCallResult;

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
