package com.tutran.callassistant.ingest.file;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndCallSchemaClassifierTest {

    private final EndCallSchemaClassifier classifier = new EndCallSchemaClassifier();

    @Test
    void classifiesBySchemaContentNotByNumericLabel() {
        // Cùng tập field như #H1..#H9 trong sample-data/sample.md, nhưng nhãn số hoàn toàn không liên
        // quan đến việc phân loại - file mẫu thật dùng lại đúng các tập field này dưới những số #HN
        // khác nhau (xem Javadoc của EndCallLogParser), nên ở đây chỉ nội dung mới quan trọng.
        assertThat(classifier.classify(List.of(
                "appUserId", "callId", "callMode", "callType", "callUserId", "duration", "end",
                "fromTag", "partnerAppUserId", "partnerCallUserId", "platform", "role", "sessionId",
                "status", "toTag", "version"))).isEqualTo("CALL_SUMMARY");

        assertThat(classifier.classify(List.of("msg", "status", "type")))
                .isEqualTo("LOG_MESSAGE");

        assertThat(classifier.classify(List.of(
                "ackCmd", "cmd", "cseq", "fromTag", "payload", "seq", "toTag", "ts", "tsRecv")))
                .isEqualTo("SIGNALING_CMD");

        assertThat(classifier.classify(List.of("category", "cmd", "data", "execTime", "subcmd")))
                .isEqualTo("QOS");

        assertThat(classifier.classify(List.of("msg", "originator", "signal")))
                .isEqualTo("SIGNAL");

        assertThat(classifier.classify(List.of(
                "address", "candidateType", "foundation", "id", "ip", "networkType", "port")))
                .isEqualTo("LOCAL_CANDIDATE");

        assertThat(classifier.classify(List.of("initCallConfig", "webrtcConfig")))
                .isEqualTo("INIT_CONFIG");

        assertThat(classifier.classify(List.of("audio.audioLevel", "audio.audioMos", "transport.rttMs")))
                .isEqualTo("PERIODIC_STATS");

        assertThat(classifier.classify(List.of(
                "audio.audioMos", "answer.countRecvInvite", "setup.startTime", "endCall.duration")))
                .isEqualTo("END_CALL_SUMMARY");

        assertThat(classifier.classify(List.of("totally", "unrecognized", "fields")))
                .isEqualTo(EndCallSchemaClassifier.UNKNOWN_SCHEMA);
    }
}
