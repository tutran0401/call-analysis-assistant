package com.tutran.callassistant.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Điểm vào của CLI: đọc tên lệnh từ tham số đầu tiên và giao phần còn lại cho
 * {@link CliCommand} tương ứng.
 *
 * <p>Dispatcher không biết có những lệnh nào - nó nhận vào danh sách mọi {@link CliCommand} đã đăng
 * ký và tự dựng bảng tra theo tên. Thêm một lệnh mới (ví dụ {@code evaluate} của Evaluation Runner ở
 * sprint sau) là thêm đúng một class, không sửa ở đây.
 */
@Component
public class CommandLineDispatcher implements CommandLineRunner {

    private final Map<String, CliCommand> commandsByName;
    private final ConsoleWriter console;

    public CommandLineDispatcher(List<CliCommand> commands, ConsoleWriter console) {
        this.commandsByName = commands.stream().collect(Collectors.toMap(
                CliCommand::name, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        this.console = console;
    }

    @Override
    public void run(String... args) {
        try {
            if (args.length == 0) {
                printUsage();
                return;
            }
            CliCommand command = commandsByName.get(args[0]);
            if (command == null) {
                printUsage();
                return;
            }
            command.execute(List.of(args).subList(1, args.length));
        } catch (Exception e) {
            console.error("Command failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Client Elasticsearch giữ thread nền sống, nên process sẽ không tự tắt sau khi lệnh
            // chạy xong nếu không gọi exit tường minh.
            System.exit(0);
        }
    }

    private void printUsage() {
        console.line("Usage:");
        commandsByName.values().forEach(command -> console.line("  " + command.usage()));
    }
}
