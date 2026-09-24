package com.tutran.callassistant.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndCallSchemaClassifierTest {

    @Test
    void classifiesBySchemaContentNotByNumericLabel() {
        // Same field sets as sample.md's #H1..#H9, but the numeric label is irrelevant to
        // classification - real sample files reuse these same field sets under different
        // #HN numbers (see EndCallLogParser's class doc), so only content must matter here.
        assertThat(EndCallSchemaClassifier.classify(List.of(
                "appUserId", "callId", "callMode", "callType", "callUserId", "duration", "end",
                "fromTag", "partnerAppUserId", "partnerCallUserId", "platform", "role", "sessionId",
                "status", "toTag", "version"))).isEqualTo("CALL_SUMMARY");

        assertThat(EndCallSchemaClassifier.classify(List.of("msg", "status", "type")))
                .isEqualTo("LOG_MESSAGE");

        assertThat(EndCallSchemaClassifier.classify(List.of(
                "ackCmd", "cmd", "cseq", "fromTag", "payload", "seq", "toTag", "ts", "tsRecv")))
                .isEqualTo("SIGNALING_CMD");

        assertThat(EndCallSchemaClassifier.classify(List.of("category", "cmd", "data", "execTime", "subcmd")))
                .isEqualTo("QOS");

        assertThat(EndCallSchemaClassifier.classify(List.of("msg", "originator", "signal")))
                .isEqualTo("SIGNAL");

        assertThat(EndCallSchemaClassifier.classify(List.of(
                "address", "candidateType", "foundation", "id", "ip", "networkType", "port")))
                .isEqualTo("LOCAL_CANDIDATE");

        assertThat(EndCallSchemaClassifier.classify(List.of("initCallConfig", "webrtcConfig")))
                .isEqualTo("INIT_CONFIG");

        assertThat(EndCallSchemaClassifier.classify(List.of("audio.audioLevel", "audio.audioMos", "transport.rttMs")))
                .isEqualTo("PERIODIC_STATS");

        assertThat(EndCallSchemaClassifier.classify(List.of(
                "audio.audioMos", "answer.countRecvInvite", "setup.startTime", "endCall.duration")))
                .isEqualTo("END_CALL_SUMMARY");

        assertThat(EndCallSchemaClassifier.classify(List.of("totally", "unrecognized", "fields")))
                .isEqualTo("UNKNOWN_LOG_SCHEMA");
    }
}
