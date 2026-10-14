package com.tutran.callassistant.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** S01-S06 (input) của PROJECT_SPEC.md mục 6.4, bằng giá trị nhạy cảm tổng hợp. */
class InputSanitizerTest {

    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SensitiveDataSanitizer engine = new SensitiveDataSanitizer(new Pseudonymizer("test-salt"));
    private final InputSanitizer sanitizer = new InputSanitizer(engine);

    private String clean(String text) {
        return sanitizer.sanitizeText(text, new SanitizationSummary());
    }

    @Test
    void s01_jwtInLogIsDropped() {
        String out = clean("token refreshed value " + JWT + " ok");

        assertThat(out).doesNotContain("eyJ").contains("[REDACTED]");
    }

    @Test
    void s02_authorizationHeaderIsRedacted() {
        assertThat(clean("Authorization: Bearer " + JWT)).isEqualTo("Authorization: [REDACTED]");
        assertThat(clean("authorization=Basic dXNlcjpwYXNz")).isEqualTo("authorization=[REDACTED]");
        assertThat(clean("{\"Authorization\":\"Bearer abc.def.ghi123\"}")).isEqualTo("{\"Authorization\":\"[REDACTED]\"}");
    }

    @Test
    void s03_apiKeyInExceptionMessageIsDropped() {
        String out = clean("IllegalStateException: call failed, api_key=sk-proj-abcdefghijklmnop1234 rejected");

        assertThat(out).doesNotContain("sk-proj").contains("api_key=[REDACTED]");
        assertThat(clean("key sk-abcdefghijklmnopqrstuvwx leaked")).doesNotContain("sk-abcdef");
    }

    @Test
    void s04_phoneAndEmailAreMasked() {
        String out = clean("phone=0987654321 callback +84 987 654 321 mail nguyen.van@example.com");

        assertThat(out).doesNotContain("0987654321").doesNotContain("987 654 321").doesNotContain("@example.com");
        assertThat(out).contains("[PHONE_REDACTED]").contains("[EMAIL_REDACTED]");
    }

    @Test
    void s08_phoneAndIpGluedToUnderscoreAreStillMasked() {
        // Lỗ do security-eval (Sprint 3 T6) tìm ra: tên file "0933444555_webrtc.log" lọt nguyên số điện thoại.
        String out = clean("Tên file 0933444555_webrtc.log; key ip_113.161.45.67; user_0987654321_x");

        assertThat(out).doesNotContain("0933444555").doesNotContain("113.161.45.67").doesNotContain("0987654321");
        assertThat(out).contains("[PHONE_REDACTED]_webrtc.log");
    }

    @Test
    void s05_clientIpAndDeviceIdAreMaskedOrPseudonymized() {
        String out = clean("candidate ip=113.161.45.67 port 5000, ipv6 2001:db8::1, deviceId=ABCD-1234-EFGH");

        assertThat(out).doesNotContain("113.161.45.67").doesNotContain("2001:db8").doesNotContain("ABCD-1234");
        assertThat(out).contains("[IP_REDACTED]").containsPattern("deviceId=DEVICE_[0-9a-f]{6}");
    }

    @Test
    void s05_userIdIsPseudonymizedConsistently() {
        String out = clean("user_id=123456 and again user_id=123456 and other user_id=999");

        String first = out.substring(out.indexOf("USER_"), out.indexOf("USER_") + 11);
        assertThat(out).doesNotContain("123456").doesNotContain("=999");
        assertThat(out.split(first, -1)).hasSize(3);
        assertThat(out).containsPattern("other user_id=USER_[0-9a-f]{6}");
    }

    @Test
    void s06_nestedJsonIsSanitizedAtEveryDepth() throws Exception {
        JsonNode input = mapper.readTree("""
                {"call": {"appUserId": "u-42", "legs": [
                    {"deviceId": "D-1", "network": {"ip": "10.20.30.40"}, "note": "call 0912345678 failed"},
                    {"auth": {"Authorization": "Bearer xyz12345678", "token": 987654}}]},
                 "metrics": {"mos": 3.1, "packetLoss": 9.8}}""");

        SanitizedContext result = sanitizer.sanitize(input);
        String json = result.content().toString();

        assertThat(json).doesNotContain("u-42").doesNotContain("D-1").doesNotContain("10.20.30.40")
                .doesNotContain("0912345678").doesNotContain("xyz12345678").doesNotContain("987654");
        assertThat(result.content().at("/metrics/mos").asDouble()).isEqualTo(3.1);
        assertThat(result.content().at("/metrics/packetLoss").asDouble()).isEqualTo(9.8);
        assertThat(result.summary().total()).isGreaterThanOrEqualTo(6);
        assertThat(result.residual()).isZero();
    }

    @Test
    void sdpBlockTurnUrlAndIceCredentialsAreRemoved() throws Exception {
        String sdp = "v=0\\r\\no=- 4611 2 IN IP4 127.0.0.1\\r\\na=ice-ufrag:WXYZ\\r\\na=ice-pwd:supersecretpwd1234567890\\r\\n"
                + "a=fingerprint:sha-256 AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89\\r\\n";
        JsonNode input = mapper.readTree("{\"msg\": \"offer {\\\"sdp\\\": \\\"" + sdp.replace("\\", "\\\\")
                + "\\\"} server turn:14.238.152.70:3478?transport=udp\"}");

        SanitizedContext result = sanitizer.sanitize(input);
        String json = result.content().toString();

        assertThat(json).doesNotContain("supersecretpwd").doesNotContain("WXYZ").doesNotContain("14.238.152.70")
                .doesNotContain("AB:CD:EF").doesNotContain("127.0.0.1");
        assertThat(json).contains("turn:[TURN_SERVER_REDACTED]");
        assertThat(result.residual()).isZero();
    }

    @Test
    void fieldNamedSdpIsReplacedWholesale() throws Exception {
        SanitizedContext result = sanitizer.sanitize(mapper.readTree("{\"sdpOffer\": \"anything at all\"}"));

        assertThat(result.content().get("sdpOffer").asText()).isEqualTo("[SDP_REDACTED]");
    }

    @Test
    void harmlessCallDataIsLeftAlone() {
        String harmless = "10:00:06.320 OK_ACK LegA CONFIRMED ts=1789700841389 version 0.3.24 mos=2.9 loss=9.8% "
                + "duration 95.3 s rtt 120 ms elapsed [000:011] EV02";

        assertThat(clean(harmless)).isEqualTo(harmless);
        assertThat(engine.countResidual(harmless)).isZero();
    }

    @Test
    void loopbackAndWildcardAddressesOfTheServerItselfAreNotClientIps() {
        String text = "Tomcat bind 127.0.0.1:8080 and 0.0.0.0:80 but client 127a.1.1.1x";

        assertThat(clean("http-nio-127.0.0.1-8080 on 0.0.0.0")).isEqualTo("http-nio-127.0.0.1-8080 on 0.0.0.0");
        assertThat(clean("client 10.0.0.5 and server 127.0.0.1")).isEqualTo("client [IP_REDACTED] and server 127.0.0.1");
        assertThat(engine.countResidual(text)).isZero();
    }

    @Test
    void sanitizingTwiceChangesNothingMore() {
        String once = clean("user_id=123456 ip 1.2.3.4 turn:1.2.3.4:3478 Authorization: Bearer " + JWT + " a@b.com");

        assertThat(clean(once)).isEqualTo(once);
        assertThat(engine.countResidual(once)).isZero();
    }

    @Test
    void sameSaltGivesSamePseudonymAndDifferentSaltDoesNot() {
        Pseudonymizer a = new Pseudonymizer("salt-a");
        assertThat(a.pseudonym("USER", "42")).isEqualTo(new Pseudonymizer("salt-a").pseudonym("USER", "42"));
        assertThat(a.pseudonym("USER", "42")).isNotEqualTo(new Pseudonymizer("salt-b").pseudonym("USER", "42"));
        assertThat(a.pseudonym("USER", "42")).matches("USER_[0-9a-f]{6}");
    }
}
