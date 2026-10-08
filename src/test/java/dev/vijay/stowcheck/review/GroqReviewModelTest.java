package dev.vijay.stowcheck.review;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the Groq client against a local stub server, so no real API calls or keys are needed. */
class GroqReviewModelTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private HttpServer server;
    private int status;
    private String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GroqReviewModel model() {
        return new GroqReviewModel("test-key", "openai/gpt-oss-120b",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", json);
    }

    @Test
    void sendsStrictSchemaAndParsesTheAnswer() {
        status = 200;
        responseBody = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":
                "{\\"findings\\":[{\\"index\\":0,\\"verdict\\":\\"CONFIRMED\\",\\"explanation\\":\\"Typo.\\",\\"suggestedFix\\":\\"Use 6\\"}],\\"carrierMessage\\":\\"Please resend.\\"}"}}],
                 "usage":{"prompt_tokens":812,"completion_tokens":95}}
                """;

        ReviewModel.Result result = model().review("Finding 0: ERROR CHECK_DIGIT");

        assertThat(result.review().findings()).singleElement().satisfies(f -> {
            assertThat(f.verdict()).isEqualTo(ModelReview.Verdict.CONFIRMED);
            assertThat(f.suggestedFix()).isEqualTo("Use 6");
        });
        assertThat(result.review().carrierMessage()).isEqualTo("Please resend.");
        assertThat(result.inputTokens()).isEqualTo(812);
        assertThat(result.outputTokens()).isEqualTo(95);

        assertThat(authHeader.get()).isEqualTo("Bearer test-key");
        JsonNode sent = json.readTree(requestBody.get());
        assertThat(sent.get("model").asString()).isEqualTo("openai/gpt-oss-120b");
        assertThat(sent.at("/response_format/type").asString()).isEqualTo("json_schema");
        assertThat(sent.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        assertThat(sent.at("/messages/0/role").asString()).isEqualTo("system");
        assertThat(sent.at("/messages/1/content").asString()).contains("CHECK_DIGIT");
    }

    @Test
    void reportsRateLimitClearly() {
        status = 429;
        responseBody = "{\"error\":{\"message\":\"rate limited\"}}";

        assertThatThrownBy(() -> model().review("x"))
                .isInstanceOf(ReviewUnavailableException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void reportsRejectedKey() {
        status = 401;
        responseBody = "{\"error\":{\"message\":\"invalid key\"}}";

        assertThatThrownBy(() -> model().review("x"))
                .isInstanceOf(ReviewUnavailableException.class)
                .hasMessageContaining("key was rejected");
    }

    @Test
    void reportsTruncatedAnswer() {
        status = 200;
        responseBody = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"findi\"}}]}";

        assertThatThrownBy(() -> model().review("x"))
                .isInstanceOf(ReviewUnavailableException.class)
                .hasMessageContaining("cut off");
    }
}
