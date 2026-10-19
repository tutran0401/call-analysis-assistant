package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.validation.UploadedFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Bộ đo độc lập đọc giá trị từ cấu trúc dữ liệu, không từ văn bản đầu ra. Dữ liệu dựng tay, không cần sample-data. */
class RawValueOracleTest {

    private static final String CALL_ID = "CALL-0001-ABCD";

    private final RawValueOracle oracle = new RawValueOracle();

    private static UploadedFile file(String name, String content) {
        return new UploadedFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsSensitiveColumnsByTheirHeaderSchema() {
        String endCall = """
                #H1\t#ts\t#tag\tappUserId\tcallId\tsessionId\tplatform
                #H5\t#ts\t#tag\tip\tport\tcandidateType
                1\t1789700841389\tinfo\tUTGMJUPAY7W\t%s\t01a0b27b-a008-7aaa\tios
                5\t1789700841390\tcandidate\t14.238.152.70\t3478\trelay
                """.formatted(CALL_ID);

        Set<String> values = oracle.extract(List.of(file("caller_endcall.log", endCall)), CALL_ID);

        assertThat(values).contains("UTGMJUPAY7W", "01a0b27b-a008-7aaa", "14.238.152.70")
                .doesNotContain("ios", "3478", "relay", CALL_ID);
    }

    @Test
    void readsJsonFieldsFreeTextIdsSdpSecretsAndCandidateIps() {
        String signaling = """
                {"callId": "%s", "events": [{"cmd": "INIT_CALL", "csid": "YB7ssNf5", "isp": "MOBIFONE"}]}
                """.formatted(CALL_ID);
        String webrtc = """
                init VCoreConfig{ userId=UTGMJUPAY7W, deviceId=DN6OHLBYCPK, platform=ios }
                sdp: v=0\\r\\na=ice-ufrag:dTDZ\\r\\na=ice-pwd:Q2mM7cdhG5Ewr1sOz9\\r\\na=fingerprint:sha-256 90:21:A7:EE
                candidate: Cand[:514682300:0:udp:2122260224:113.161.45.67:57611:host::0]
                masked pdp_ip0:100.81.44.x/32 and turnSessionInfo="DE7D****0919" loopback 127.0.0.1
                """;

        Set<String> values = oracle.extract(
                List.of(file("signaling.json", signaling), file("caller_webrtc.log", webrtc)), CALL_ID);

        assertThat(values).contains("YB7ssNf5", "UTGMJUPAY7W", "DN6OHLBYCPK", "dTDZ", "Q2mM7cdhG5Ewr1sOz9",
                "90:21:A7:EE", "113.161.45.67");
        assertThat(values).as("ISP là Internal; giá trị client đã tự che và loopback không tính")
                .doesNotContain("MOBIFONE", "100.81.44.x", "DE7D****0919", "127.0.0.1");
    }

    @Test
    void matchesOnlyStandaloneOccurrences() {
        List<String> values = List.of("14.238.152.70", "dTDZ", "USER42");

        assertThat(RawValueOracle.foundIn("IP 14.238.152.70, ufrag dTDZ.", values))
                .containsExactly("14.238.152.70", "dTDZ");
        assertThat(RawValueOracle.foundIn("114.238.152.701 xdTDZx USER_3f2a1b USER421", values)).isEmpty();
    }

    @Test
    void describesALeakWithoutRevealingIt() {
        assertThat(RawValueOracle.describe("UTGMJUPAY7W")).isEqualTo("UT…(11 ký tự)").doesNotContain("GMJ");
    }
}
