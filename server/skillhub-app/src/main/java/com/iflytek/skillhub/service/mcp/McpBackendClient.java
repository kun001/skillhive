package com.iflytek.skillhub.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Read-only loopback adapter. Every request re-enters the existing bearer, scope, membership,
 * visibility, download and audit guards. No shared credentials, cookies, redirects or user URLs.
 */
@Component
public class McpBackendClient {
    private static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper;
    private final String baseUrl;

    public McpBackendClient(ObjectMapper mapper, @Value("${server.port:8080}") int port) {
        this.mapper = mapper;
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public record File(byte[] bytes, String contentType) {}

    public JsonNode json(String path, String authorization) throws IOException, InterruptedException {
        var body = mapper.readTree(file(path, authorization).bytes());
        if (body.path("code").asInt(-1) != 0 || !body.has("data")) throw new IOException("Invalid backend response");
        return body.get("data");
    }

    public File file(String path, String authorization) throws IOException, InterruptedException {
        if (!path.startsWith("/api/v1/") && !path.startsWith("/api/web/skills")) {
            throw new IllegalArgumentException("Unsupported backend path");
        }
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization).header("Accept", "*/*").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() != 200) {
                // Do not leak upstream bodies or resource coordinates on denied requests.
                throw new IOException("SkillHive denied or failed the read (HTTP " + response.statusCode() + ")");
            }
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("Response exceeds the MCP size limit");
            return new File(bytes, response.headers().firstValue("Content-Type").orElse("application/octet-stream"));
        }
    }
}
