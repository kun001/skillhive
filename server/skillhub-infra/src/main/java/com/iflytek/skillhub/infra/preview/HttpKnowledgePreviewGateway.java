package com.iflytek.skillhub.infra.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.knowledge.KnowledgeOfficePreview;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Streams originals to the internal renderer; no original Office file is sent to the browser. */
@Component
public class HttpKnowledgePreviewGateway implements KnowledgePreviewGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpKnowledgePreviewGateway.class);
    private final ObjectMapper mapper;
    private final String url;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();

    public HttpKnowledgePreviewGateway(ObjectMapper mapper,
                                       @Value("${skillhub.knowledge.preview-url:${SKILLHUB_KNOWLEDGE_PREVIEW_URL:}}") String url) {
        this.mapper = mapper;
        this.url = url.replaceAll("/+$", "");
    }

    @Override
    public KnowledgeOfficePreview find(String key) {
        if (url.isBlank()) return KnowledgeOfficePreview.unavailable();
        return manifest(HttpRequest.newBuilder(URI.create(url + "/previews/" + key))
                .timeout(Duration.ofSeconds(10)).GET().build());
    }

    @Override
    public KnowledgeOfficePreview submit(String key, String extension, long size, InputStream content) {
        if (url.isBlank()) return KnowledgeOfficePreview.unavailable();
        var body = HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> content), size);
        return manifest(HttpRequest.newBuilder(URI.create(url + "/previews/" + key + "?extension=" + extension))
                .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/octet-stream").POST(body).build());
    }

    private KnowledgeOfficePreview manifest(HttpRequest request) {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() / 100 != 2) return KnowledgeOfficePreview.unavailable();
            return mapper.readValue(response.body(), KnowledgeOfficePreview.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return KnowledgeOfficePreview.unavailable();
        } catch (IOException exception) {
            log.warn("Knowledge preview renderer unavailable: {}", exception.getClass().getSimpleName());
            return KnowledgeOfficePreview.unavailable();
        }
    }

    @Override
    public byte[] page(String key, int page) {
        if (url.isBlank()) return null;
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/previews/" + key + "/pages/" + page))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException exception) {
            return null;
        }
    }
}
