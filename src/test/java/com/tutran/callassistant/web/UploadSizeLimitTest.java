package com.tutran.callassistant.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** F04 ở tầng container (cổng thật, Tomcat): file quá 10 MB -> 413 kèm thông báo thân thiện và request_id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"call-analysis.ai.provider=fake", "server.address=127.0.0.1"})
@ActiveProfiles("web")
class UploadSizeLimitTest {

    @LocalServerPort
    private int port;

    @Test
    void fileOverTheLimitGetsAFriendly413() throws Exception {
        String boundary = "----sizelimit";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"caller_endcall.log\"\r\n"
                + "Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + 10 * 1024 * 1024 + 1 + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(tail, 0, body, body.length - tail.length, tail.length);

        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/chat"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("10 MB").contains("\"requestId\":\"req-");
    }
}
