package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSignalingSourceTest {

    @Test
    void indexFindsCallDirectoriesAtAnyDepthIgnoringCase(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("success").resolve("ABC-123"));
        Files.createDirectories(root.resolve("fail").resolve("DEF-456"));
        CallDirectoryIndex index = new CallDirectoryIndex(root);

        assertThat(index.find("abc-123")).contains(root.resolve("success").resolve("ABC-123"));
        assertThat(index.find("DEF-456")).isPresent();
        assertThat(index.find("nope")).isEmpty();
        assertThat(index.find(null)).isEmpty();
    }

    @Test
    void missingRootYieldsNoMatchInsteadOfFailing(@TempDir Path root) {
        assertThat(new CallDirectoryIndex(root.resolve("does-not-exist")).find("x")).isEmpty();
    }

    @Test
    void unknownCallGivesEmptyEventsSoTheFallbackChainContinues(@TempDir Path root) {
        TreeSignalingSource source = new TreeSignalingSource(new CallDirectoryIndex(root),
                LocalFileSignalingSource.withDefaults());

        assertThat(source.fetch(new CallReference("UNKNOWN-CALL", root.resolve("elsewhere"))).isEmpty()).isTrue();
    }

    @Test
    void readsRealSignalingFromTheSampleTreeRegardlessOfTheUploadDirectory(@TempDir Path uploadDir) {
        String callId = SampleCalls.SUCCESS_FULL_LOGS;
        TreeSignalingSource source = new TreeSignalingSource(new CallDirectoryIndex(SampleCalls.requireSampleData()),
                LocalFileSignalingSource.withDefaults());

        var events = source.fetch(new CallReference(callId, uploadDir));

        assertThat(events.events()).isNotEmpty();
        assertThat(events.events()).allMatch(e -> e.callId().equalsIgnoreCase(callId));
    }
}
