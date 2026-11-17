package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.config.HtmlPreviewConfig;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.service.knowledge.HtmlPreviewService;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class HtmlPreviewControllerTest {
    private static final String TICKET = "a".repeat(32);
    private static final String CONTENT_PATH = "/_html-preview/" + TICKET + "/content";
    private final HtmlPreviewService service = mock(HtmlPreviewService.class);
    private final HtmlPreviewConfig.Origins origins = new HtmlPreviewConfig.Origins(
            URI.create("https://preview.example.net"), URI.create("https://skillhive.example.com"));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new HtmlPreviewController(mock(ApiResponseFactory.class), service, origins)).build();

    @Test
    void rejectsDirectNavigationBeforeLookingUpTheTicket() throws Exception {
        mvc.perform(get(CONTENT_PATH).header("Sec-Fetch-Dest", "document"))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(service);
    }

    @Test
    void expiredTicketStaysNotFound() throws Exception {
        when(service.open(eq(TICKET), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get(CONTENT_PATH).header("Sec-Fetch-Dest", "iframe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesTheOriginalHtmlWithARestrictivePolicyOnlyInsideTheFrame() throws Exception {
        byte[] file = "<p>原型</p>".getBytes(StandardCharsets.UTF_8);
        when(service.open(eq(TICKET), any())).thenReturn(new KnowledgeAppService.FileContent(
                "prototype.html", "application/octet-stream", file.length, KnowledgePreviewKind.HTML,
                () -> new ByteArrayInputStream(file)));
        var response = mvc.perform(get(CONTENT_PATH).header("Sec-Fetch-Dest", "iframe"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("sandbox allow-scripts", "connect-src 'none'", "form-action 'none'");
        assertThat(response.getContentAsString()).contains("<p>原型</p>");
    }
}
