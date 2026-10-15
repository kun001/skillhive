package com.iflytek.skillhub.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiKnowledgeSummaryGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private String completion;
    private int status;

    @BeforeEach
    void start() throws Exception {
        status = 200;
        completion = "<think>internal</think>{\"summary\":\"说明差旅报销的标准和流程。\",\"keywords\":[\"差旅\",\"报销\",\"差旅\",\"  \"]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = status == 200 ? mapper.writeValueAsString(Map.of("choices", List.of(
                    Map.of("finish_reason", "stop", "message", Map.of("content", completion))))) : "private-provider-error";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private KnowledgeSummaryProperties config() {
        return new KnowledgeSummaryProperties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", null,
                "local-model", Duration.ofSeconds(2), Duration.ofSeconds(3), 0.2, 1024, 500, 3, Duration.ofSeconds(1),
                "json_schema", 5000, "none");
    }

    @Test
    void sendsBoundedUntrustedTextAndParsesDistinctKeywords() throws Exception {
        var summary = new OpenAiKnowledgeSummaryGenerator(config(), mapper).generate("差旅制度", "正".repeat(1000));

        assertThat(summary.summary()).isEqualTo("说明差旅报销的标准和流程。");
        assertThat(summary.keywords()).containsExactly("差旅", "报销");
        var request = mapper.readTree(requestBody.get());
        assertThat(request.path("model").asText()).isEqualTo("local-model");
        assertThat(request.path("messages").path(1).path("content").asText())
                .contains("<title>差旅制度</title>").contains("正".repeat(500)).doesNotContain("正".repeat(501));
        assertThat(request.path("response_format").path("json_schema").path("name").asText()).isEqualTo("knowledge_summary");
        assertThat(request.has("tools")).isFalse();
        assertThat(request.path("reasoning_effort").asText()).isEqualTo("none");
    }

    @Test
    void rejectsInvalidOutputAndHidesProviderErrors() {
        var generator = new OpenAiKnowledgeSummaryGenerator(config(), mapper);
        completion = "{\"summary\":\"\",\"keywords\":[]}";
        assertThatThrownBy(() -> generator.generate("t", "x")).isInstanceOf(IllegalArgumentException.class);
        completion = "not json";
        assertThatThrownBy(() -> generator.generate("t", "x")).isInstanceOf(Exception.class);
        status = 500;
        assertThatThrownBy(() -> generator.generate("t", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-provider-error");
    }

    @Test
    void enabledConfigurationMustNameAModelAndAnHttpEndpoint() {
        assertThatThrownBy(() -> new KnowledgeSummaryProperties(true, "http://localhost:1234/v1", null, "m", Duration.ofSeconds(2),
                Duration.ofSeconds(3), 0.2, 1024, 500, 3, Duration.ofSeconds(1), "json_schema", 5000, "extreme"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeSummaryProperties(true, "file:///tmp", null, "m", Duration.ofSeconds(2),
                Duration.ofSeconds(3), 0.2, 1024, 500, 3, Duration.ofSeconds(1), "json_schema", 5000, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeSummaryProperties(true, "http://localhost:1234/v1", null, " ", Duration.ofSeconds(2),
                Duration.ofSeconds(3), 0.2, 1024, 500, 3, Duration.ofSeconds(1), "json_schema", 5000, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new KnowledgeSummaryProperties(false, null, "secret", null, null, null, 0, 0, 0, 0, null, null, 0, null).toString())
                .doesNotContain("secret");
    }
}
