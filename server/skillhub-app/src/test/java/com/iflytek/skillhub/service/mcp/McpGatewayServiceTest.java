package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class McpGatewayServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpBackendClient backend = mock(McpBackendClient.class);
    private final McpGatewayService gateway = new McpGatewayService(mapper, backend, "https://skillhive.example.com");

    private UsernamePasswordAuthenticationToken token(String user, String... scopes) {
        return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(user, user, null, null, "api_token", Set.of("SUPER_ADMIN")),
                null, java.util.Arrays.stream(scopes).map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList());
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/v1/mcp");
        request.addHeader("Authorization", "Bearer test-personal-token");
        request.addHeader("Accept", "application/json, text/event-stream");
        return request;
    }
    private com.fasterxml.jackson.databind.JsonNode rpc(String method, Object params) {
        return mapper.valueToTree(java.util.Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
    }

    @Test
    void transportRejectsSessionsWriteScopesAndUntrustedOrigins() {
        var session = new UsernamePasswordAuthenticationToken(new PlatformPrincipal("alice", "Alice", null, null, "local", Set.of()), null, List.of());
        assertThatThrownBy(() -> gateway.checkTransport(session, request())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> gateway.checkTransport(token("alice", "skill:read", "skill:publish"), request())).isInstanceOf(ResponseStatusException.class);
        var hostile = request();
        hostile.addHeader("Origin", "https://hostile.example.com");
        assertThatThrownBy(() -> gateway.checkTransport(token("alice", "knowledge:read"), hostile)).isInstanceOf(ResponseStatusException.class);
        gateway.checkTransport(token("alice", "knowledge:read"), request());
        verifyNoInteractions(backend);
    }

    @Test
    void listsOnlyGrantedDomainsAndRejectsDirectCrossScopeCalls() {
        var auth = token("alice", "skill:read");
        var listed = gateway.handle(rpc("tools/list", java.util.Map.of()), auth, "Bearer alice").getBody();
        assertThat(listed.toString()).contains("search_skills").doesNotContain("search_knowledge");
        var denied = gateway.handle(rpc("tools/call", java.util.Map.of("name", "get_document", "arguments", java.util.Map.of("documentId", 20))), auth, "Bearer alice").getBody();
        assertThat(denied.path("result").path("isError").asBoolean()).isTrue();
        verifyNoInteractions(backend);
    }

    @Test
    void eachCallForwardsOnlyItsOwnBearerAndDoesNotReuseAnotherUsersResult() throws Exception {
        when(backend.json("/api/v1/knowledge/bases", "Bearer alice")).thenReturn(mapper.readTree("[{\"slug\":\"alice-team\"}]"));
        when(backend.json("/api/v1/knowledge/bases", "Bearer bob")).thenReturn(mapper.readTree("[]"));
        var call = rpc("tools/call", java.util.Map.of("name", "list_knowledge_bases"));
        var alice = gateway.handle(call, token("alice", "knowledge:read"), "Bearer alice").getBody();
        var bob = gateway.handle(call, token("bob", "knowledge:read"), "Bearer bob").getBody();
        assertThat(alice.toString()).contains("alice-team");
        assertThat(bob.toString()).doesNotContain("alice-team");
        verify(backend).json("/api/v1/knowledge/bases", "Bearer alice");
        verify(backend).json("/api/v1/knowledge/bases", "Bearer bob");
    }

    @Test
    void deniedIdReadNeverReturnsBackendPrivateContent() throws Exception {
        when(backend.json("/api/v1/knowledge/documents/200", "Bearer alice")).thenThrow(new java.io.IOException("HTTP 403 secret title"));
        var result = gateway.handle(rpc("tools/call", java.util.Map.of("name", "read_document", "arguments", java.util.Map.of("documentId", 200))),
                token("alice", "knowledge:read"), "Bearer alice").getBody();
        assertThat(result.path("result").path("isError").asBoolean()).isTrue();
        assertThat(result.toString()).doesNotContain("secret title");
        verify(backend, never()).file(anyString(), anyString());
    }

    @Test
    void documentsReadAsExtractedMarkdownAndTeamGuidesNeedNoDomainScope() throws Exception {
        when(backend.json("/api/v1/knowledge/documents/7/text", "Bearer alice")).thenReturn(mapper.readTree(
                "{\"documentId\":7,\"versionNumber\":2,\"fileName\":\"plan.docx\",\"status\":\"COMPLETED\",\"truncated\":false,"
                        + "\"summary\":\"迁移计划\",\"content\":\"# 计划\"}"));
        var read = gateway.handle(rpc("tools/call", java.util.Map.of("name", "read_document", "arguments", java.util.Map.of("documentId", 7))),
                token("alice", "knowledge:read"), "Bearer alice").getBody().path("result");
        assertThat(read.path("content").path(0).path("text").asText()).contains("plan.docx", "Summary: 迁移计划", "untrusted");
        assertThat(read.path("content").path(1).path("resource").path("mimeType").asText()).isEqualTo("text/markdown");
        assertThat(read.path("content").path(1).path("resource").path("text").asText()).isEqualTo("# 计划");
        verify(backend, never()).file(anyString(), anyString());

        var badFormat = gateway.handle(rpc("tools/call", java.util.Map.of("name", "read_document", "arguments",
                java.util.Map.of("documentId", 7, "format", "html"))), token("alice", "knowledge:read"), "Bearer alice").getBody();
        assertThat(badFormat.path("error").path("code").asInt()).isEqualTo(-32602);

        when(backend.json("/api/v1/namespaces/team/guide", "Bearer alice")).thenReturn(mapper.readTree(
                "{\"namespace\":\"team\",\"namespaceDisplayName\":\"Team\",\"content\":\"# 约定\"}"));
        var guide = gateway.handle(rpc("tools/call", java.util.Map.of("name", "get_team_guide", "arguments", java.util.Map.of("namespace", "team"))),
                token("alice", "skill:read"), "Bearer alice").getBody().path("result");
        assertThat(guide.path("content").path(1).path("resource").path("text").asText()).isEqualTo("# 约定");
    }

    @Test
    void invalidArgumentsCannotChooseAnArbitraryBackendUrl() {
        for (var arguments : List.of(java.util.Map.of("documentId", -1), java.util.Map.of("documentId", 1, "url", "https://hostile.example"))) {
            var response = gateway.handle(rpc("tools/call", java.util.Map.of("name", "get_document", "arguments", arguments)),
                    token("alice", "knowledge:read"), "Bearer alice").getBody();
            assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        }
        verifyNoInteractions(backend);
    }

    @Test
    void supportsInitializeNotificationsAndRejectsBatchRequests() {
        var auth = token("alice", "knowledge:read");
        var result = gateway.handle(rpc("initialize", java.util.Map.of("protocolVersion", "2025-11-25")), auth, "Bearer alice").getBody();
        assertThat(result.path("result").path("protocolVersion").asText()).isEqualTo("2025-11-25");
        var notification = mapper.valueToTree(java.util.Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        assertThat(gateway.handle(notification, auth, "Bearer alice").getStatusCode().value()).isEqualTo(202);
        assertThat(gateway.handle(mapper.createArrayNode().add(rpc("ping", java.util.Map.of())), auth, "Bearer alice").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void scopedTokensExposeIndependentWriteToolsAndRejectUngrantableUpdateTools() throws Exception {
        var auth = token("alice", "mcp:access", "knowledge:upload");
        gateway.checkTransport(auth, request());
        var listed = gateway.handle(rpc("tools/list", java.util.Map.of()), auth, "Bearer alice").getBody();
        assertThat(listed.toString()).contains("upload_document").doesNotContain("read_document", "delete_document", "upload_skill", "update_document");
        var tool = java.util.stream.StreamSupport.stream(listed.path("result").path("tools").spliterator(), false)
                .filter(candidate -> "upload_document".equals(candidate.path("name").asText())).findFirst().orElseThrow();
        assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        var call = rpc("tools/call", java.util.Map.of("name", "upload_document", "arguments", java.util.Map.of(
                "namespace", "team", "base", "docs", "fileName", "guide.txt", "contentBase64", "aGVsbG8=", "idempotencyKey", "once")));
        when(backend.write(eq("/api/v1/mcp/knowledge/team/docs/uploads"), eq("POST"), any(), eq("Bearer alice")))
                .thenReturn(mapper.readTree("{\"id\":9}"));
        assertThat(gateway.handle(call, auth, "Bearer alice").getBody().path("result").path("isError").asBoolean()).isFalse();
        var invalid = rpc("tools/call", java.util.Map.of("name", "upload_document", "arguments", java.util.Map.of(
                "namespace", "team", "base", "docs", "fileName", "guide.txt", "contentBase64", "aGVsbG8=", "idempotencyKey", "once", "confirmed", true)));
        assertThat(gateway.handle(invalid, auth, "Bearer alice").getBody().path("error").path("code").asInt()).isEqualTo(-32602);
    }
}
