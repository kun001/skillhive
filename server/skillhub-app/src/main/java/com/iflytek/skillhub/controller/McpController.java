package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.mcp.McpGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Stateless Streamable HTTP endpoint, included in the existing server image. */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {
    private final McpGatewayService gateway;

    public McpController(McpGatewayService gateway) { this.gateway = gateway; }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @RateLimit(category = "mcp", authenticated = 120)
    public ResponseEntity<JsonNode> post(@RequestBody JsonNode message, Authentication authentication,
                                         HttpServletRequest request) {
        gateway.checkTransport(authentication, request);
        return gateway.handle(message, authentication, request.getHeader("Authorization"));
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE})
    public ResponseEntity<Void> unsupported(Authentication authentication, HttpServletRequest request) {
        gateway.checkTransport(authentication, request);
        // No server-to-client stream or persistent sessions are advertised.
        return ResponseEntity.status(405).header("Allow", "POST").build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, Object>> transportError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(java.util.Map.of(
                "jsonrpc", "2.0", "error", java.util.Map.of("code", -32000, "message", exception.getReason())));
    }
}
