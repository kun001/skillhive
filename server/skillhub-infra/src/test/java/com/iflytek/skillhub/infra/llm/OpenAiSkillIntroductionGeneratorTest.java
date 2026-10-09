package com.iflytek.skillhub.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OpenAiSkillIntroductionGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private String completion;
    private int status;

    @BeforeEach
    void start() throws Exception {
        status = 200;
        completion = "{\"zh\":{\"functionDescription\":\"创建界面\",\"usageInstructions\":\"提供代码及需求。\",\"examplePrompt\":\"帮我创建首页\"},\"en\":{\"functionDescription\":\"Build interfaces\",\"usageInstructions\":\"Provide code and requirements.\",\"examplePrompt\":\"Build my homepage\"}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = status == 200 ? mapper.writeValueAsString(java.util.Map.of("choices", java.util.List.of(
                    java.util.Map.of("finish_reason", "stop", "message", java.util.Map.of("content", completion))))) : "private-provider-error";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private SkillIntroductionProperties config(String key, String format) {
        return new SkillIntroductionProperties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", key,
                "test-model", Duration.ofSeconds(2), Duration.ofSeconds(3), 0.2, 2048, 500, 3, Duration.ofSeconds(1), format);
    }

    @Test
    void usesConfiguredProviderAndLimitsUntrustedInput() throws Exception {
        var content = new OpenAiSkillIntroductionGenerator(config("test-key", "json_schema"), mapper).generate("x".repeat(1000));
        assertThat(content.zh().functionDescription()).isEqualTo("创建界面");
        assertThat(content.zh().usageInstructions()).isEqualTo("提供代码及需求。\n\n例如：“帮我创建首页”");
        assertThat(content.en().usageInstructions()).isEqualTo("Provide code and requirements.\n\nFor example: “Build my homepage”");
        var request = mapper.readTree(requestBody.get());
        assertThat(request.path("model").asText()).isEqualTo("test-model");
        assertThat(request.path("messages").path(1).path("content").asText()).contains("x".repeat(500)).doesNotContain("x".repeat(501));
        assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.has("tools")).isFalse();
        assertThat(auth.get()).isEqualTo("Bearer test-key");
    }

    @Test
    void supportsLocalProvidersWithoutKeysOrStructuredOutput() throws Exception {
        new OpenAiSkillIntroductionGenerator(config("", "none"), mapper).generate("source");
        assertThat(auth.get()).isNull();
        assertThat(mapper.readTree(requestBody.get()).has("response_format")).isFalse();
    }

    @Test
    void rejectsEmptyAndIncompleteContent() {
        completion = "{\"zh\":{},\"en\":{}}";
        assertThatThrownBy(() -> new OpenAiSkillIntroductionGenerator(config("", "json_object"), mapper).generate("source"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEnglishReturnedInTheChineseFields() {
        completion = completion.replace("创建界面", "Build interfaces");
        assertThatThrownBy(() -> new OpenAiSkillIntroductionGenerator(config("", "json_schema"), mapper).generate("source"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid introduction language");
    }

    @Test
    void doesNotExposeProviderErrorBody() {
        status = 401;
        assertThatThrownBy(() -> new OpenAiSkillIntroductionGenerator(config("", "none"), mapper).generate("source"))
                .hasMessage("Introduction provider request failed").hasMessageNotContaining("private-provider-error");
        assertThat(config("secret-value", "none").toString()).doesNotContain("secret-value");
    }

    @Test
    void validatesDeploymentConfiguration() {
        assertThatThrownBy(() -> new SkillIntroductionProperties(true, "file:///tmp/model", "", "model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0.2, 2048, 500, 3, Duration.ofSeconds(1), "none"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
