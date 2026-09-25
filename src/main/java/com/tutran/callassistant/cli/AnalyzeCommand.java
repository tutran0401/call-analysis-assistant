package com.tutran.callassistant.cli;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.port.in.AnalyzeCallUseCase;
import com.tutran.callassistant.application.port.out.ReportPresenter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/** Phân tích một cuộc gọi và in report ra. */
@Component
@Order(20)
public class AnalyzeCommand implements CliCommand {

    /** Đọc thẳng {@code signaling.json} thay vì query Elasticsearch. */
    static final String FROM_FILE_FLAG = "--from-file";

    private final AnalyzeCallUseCase analyzeCall;
    private final ReportPresenter presenter;
    private final ConsoleWriter console;

    public AnalyzeCommand(AnalyzeCallUseCase analyzeCall, ReportPresenter presenter, ConsoleWriter console) {
        this.analyzeCall = analyzeCall;
        this.presenter = presenter;
        this.console = console;
    }

    @Override
    public String name() {
        return "analyze";
    }

    @Override
    public String usage() {
        return "analyze <callDir> [" + FROM_FILE_FLAG + "]";
    }

    @Override
    public void execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            console.line("Usage: " + usage());
            return;
        }
        CallReference call = CallReference.ofDirectory(Path.of(arguments.get(0)));
        presenter.present(analyzeCall.analyze(call, signalingSourceFrom(arguments)));
    }

    static SignalingSourcePreference signalingSourceFrom(List<String> arguments) {
        return arguments.contains(FROM_FILE_FLAG)
                ? SignalingSourcePreference.LOCAL_FILE
                : SignalingSourcePreference.INDEXED;
    }
}
