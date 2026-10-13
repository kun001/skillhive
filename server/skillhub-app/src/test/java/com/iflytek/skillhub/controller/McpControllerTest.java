package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.service.mcp.McpBackendClient;
import com.iflytek.skillhub.service.mcp.McpGatewayService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpControllerTest {
    private final McpBackendClient backend = mock(McpBackendClient.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new McpController(
            new McpGatewayService(new ObjectMapper(), backend, "https://skillhive.example.com"))).build();
    private final UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("alice", "Alice", null, null, "api_token", Set.of()), null,
            List.of(new SimpleGrantedAuthority("SCOPE_knowledge:read")));

    @Test
    void endpointPreservesJsonRpcAndNotificationStatus() throws Exception {
        mvc.perform(post("/api/v1/mcp").principal(token).header("Authorization", "Bearer test")
                .header("Accept", "application/json, text/event-stream").contentType("application/json")
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.serverInfo.name").value("skillhive"));
        mvc.perform(post("/api/v1/mcp").principal(token).header("Authorization", "Bearer test")
                .header("Accept", "application/json, text/event-stream").contentType("application/json")
                .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
    }

    @Test
    void untrustedOriginAndUnsupportedProtocolAreHttpErrors() throws Exception {
        mvc.perform(post("/api/v1/mcp").principal(token).header("Authorization", "Bearer test")
                .header("Origin", "https://hostile.example.com").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mcp").principal(token).header("Authorization", "Bearer test")
                .header("MCP-Protocol-Version", "unknown").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(backend);
    }

    @Test
    void requiresBearerAndDoesNotExposeAnSseSession() throws Exception {
        mvc.perform(get("/api/v1/mcp").principal(token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/mcp").principal(token).header("Authorization", "Bearer test"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        verifyNoInteractions(backend);
    }
}
