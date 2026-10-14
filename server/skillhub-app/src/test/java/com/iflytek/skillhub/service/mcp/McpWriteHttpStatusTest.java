package com.iflytek.skillhub.service.mcp;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.iflytek.skillhub.controller.McpWriteController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.exception.McpTokenErrorHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class McpWriteHttpStatusTest {
    @Test void deliberateDenialsReturnTheirStatusAndNoInternalExceptionText() throws Exception {
        var writes = mock(McpWriteService.class);
        var responses = mock(ApiResponseFactory.class);
        when(responses.errorMessage(anyInt(), anyString())).thenAnswer(call ->
                new ApiResponse<Void>(call.getArgument(0), call.getArgument(1), null, Instant.EPOCH, "qa"));
        var mvc = MockMvcBuilders.standaloneSetup(new McpWriteController(writes, responses, mock(McpGatewayService.class)))
                .setControllerAdvice(new McpTokenErrorHandler(responses)).build();
        for (HttpStatus status : new HttpStatus[]{HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT}) {
            doThrow(new ResponseStatusException(status, "Operation unavailable")).when(writes)
                    .uploadDocument(anyString(), anyString(), any(), any(), anyMap(), any());
            mvc.perform(post("/api/v1/mcp/knowledge/team/base/uploads").contentType("application/json")
                    .content("{\"idempotencyKey\":\"qa\",\"fileName\":\"file.txt\",\"contentBase64\":\"aGVsbG8=\"}"))
                    .andExpect(status().is(status.value())).andExpect(jsonPath("$.code").value(status.value()))
                    .andExpect(jsonPath("$.msg").value("Operation unavailable"));
        }
    }
}
