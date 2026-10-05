package com.tutran.callassistant.testsupport;

import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.analysis.verdict.RuleVerdictEngine;
import com.tutran.callassistant.application.AnalysisReportComposer;
import com.tutran.callassistant.application.CallAnalysisPipeline;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.SignalingSourceResolver;
import com.tutran.callassistant.application.ai.AiContextBuilder;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.application.ai.TimeLimitedAiCaller;
import com.tutran.callassistant.application.guardrail.AiResponseGuard;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.application.request.RuleRequestParser;
import com.tutran.callassistant.ingest.file.ContentBasedLogFileTypeDetector;
import com.tutran.callassistant.ingest.file.ContentUploadValidator;
import com.tutran.callassistant.ingest.file.DirectoryClientLogSource;
import com.tutran.callassistant.report.ReportSchemaGuard;
import com.tutran.callassistant.report.RuleBasedReportAssembler;
import com.tutran.callassistant.report.TextReportRenderer;
import com.tutran.callassistant.sanitize.InputSanitizer;
import com.tutran.callassistant.sanitize.OutputSanitizer;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dựng {@link ChatAnalysisService} thật (parser, validator, pipeline, sanitizer, guardrail, composer đều là
 * code thật) chỉ thay hai thứ: AI provider và nguồn signaling (thế chỗ Elasticsearch). Dùng chung cho test
 * orchestration và test Evaluation Runner.
 */
public final class ChatServices {

    public static final SensitiveDataSanitizer ENGINE = new SensitiveDataSanitizer(new Pseudonymizer("test-salt"));

    private ChatServices() {
    }

    public static ChatAnalysisService create(AiProvider provider, long aiTimeoutMillis, SignalingEventSource indexed) {
        Map<SignalingSourcePreference, SignalingEventSource> sources = new EnumMap<>(SignalingSourcePreference.class);
        sources.put(SignalingSourcePreference.INDEXED, indexed);

        CallAnalysisPipeline pipeline = new CallAnalysisPipeline(new SignalingSourceResolver(sources),
                DirectoryClientLogSource.withDefaults(), SampleCalls.timelineBuilder(),
                CallMetricsCalculator.withDefaults(), RuleVerdictEngine.withDefaults(),
                RuleBasedReportAssembler.withDefaults(), new ReportSchemaGuard());
        AiPromptFactory prompts = new AiPromptFactory();
        return new ChatAnalysisService(new RuleRequestParser(),
                new ContentUploadValidator(ContentBasedLogFileTypeDetector.withDefaults(), 10 * 1024 * 1024),
                pipeline, new AiContextBuilder(IssueCategoryRegistry.withDefaults()), new InputSanitizer(ENGINE),
                prompts, provider, new TimeLimitedAiCaller(aiTimeoutMillis), new AiResponseGuard(prompts),
                new AnalysisReportComposer(), new OutputSanitizer(ENGINE), new ReportSchemaGuard(),
                new TextReportRenderer(), new Pseudonymizer("test-salt"));
    }
}
