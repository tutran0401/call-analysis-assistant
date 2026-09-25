package com.tutran.callassistant.cli;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.port.in.AnalyzeCallUseCase;
import com.tutran.callassistant.application.port.out.CallDirectoryScanner;
import com.tutran.callassistant.application.port.out.ReportPresenter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Phân tích mọi cuộc gọi tìm thấy dưới một hoặc nhiều thư mục gốc - đáp ứng yêu cầu "demo tối thiểu
 * 5 cuộc gọi bằng CLI" của Sprint 1 (mục 5.3).
 */
@Component
@Order(30)
public class DemoCommand implements CliCommand {

    private static final int SEPARATOR_WIDTH = 80;

    private final AnalyzeCallUseCase analyzeCall;
    private final CallDirectoryScanner directoryScanner;
    private final ReportPresenter presenter;
    private final ConsoleWriter console;

    public DemoCommand(AnalyzeCallUseCase analyzeCall, CallDirectoryScanner directoryScanner,
                       ReportPresenter presenter, ConsoleWriter console) {
        this.analyzeCall = analyzeCall;
        this.directoryScanner = directoryScanner;
        this.presenter = presenter;
        this.console = console;
    }

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public String usage() {
        return "demo <dataRoot> [<dataRoot> ...]";
    }

    @Override
    public void execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            console.line("Usage: " + usage());
            return;
        }
        SignalingSourcePreference signalingSource = AnalyzeCommand.signalingSourceFrom(arguments);
        for (String dataRoot : arguments) {
            if (dataRoot.startsWith("--")) {
                continue;
            }
            for (Path callDirectory : directoryScanner.callDirectoriesUnder(Path.of(dataRoot))) {
                console.separator(SEPARATOR_WIDTH);
                console.line("Call folder: " + callDirectory);
                presenter.present(analyzeCall.analyze(CallReference.ofDirectory(callDirectory), signalingSource));
            }
        }
    }
}
