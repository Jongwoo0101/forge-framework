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
