package com.iflytek.skillhub.exception;

import com.iflytek.skillhub.controller.McpWriteController;
import com.iflytek.skillhub.controller.McpKnowledgeController;
import com.iflytek.skillhub.controller.TokenController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Preserve deliberate MCP/issuance denials instead of turning them into generic server errors. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {McpWriteController.class, McpKnowledgeController.class, TokenController.class})
public class McpTokenErrorHandler {
    private final ApiResponseFactory responses;
    public McpTokenErrorHandler(ApiResponseFactory responses) { this.responses = responses; }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> denied(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(responses.errorMessage(error.getStatusCode().value(),
                error.getReason() == null ? "MCP operation is unavailable" : error.getReason()));
    }
}
