package com.iflytek.skillhub.controller.portal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.knowledge.KnowledgePurgeResponse;
import com.iflytek.skillhub.service.MemberResourceAccessService;
import com.iflytek.skillhub.service.knowledge.KnowledgePurgeService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Web routes for permanently purging archived knowledge files. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class KnowledgePurgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private KnowledgePurgeService purgeService;

    /** The member resource guard is covered elsewhere; here it lets the request reach the controller. */
    @MockBean
    private MemberResourceAccessService memberResourceAccessService;

    @Test
    void confirmedPurgeReturnsTheFreedBytes() throws Exception {
        given(purgeService.purgeDocument(eq(21L), eq(true), any(), any()))
                .willReturn(new KnowledgePurgeResponse(1, 1300L, null));

        mockMvc.perform(delete("/api/web/knowledge/documents/21/purge").param("confirm", "true")
                        .with(authentication(session())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.purgedDocuments").value(1))
                .andExpect(jsonPath("$.data.freedBytes").value(1300));
    }

    @Test
    void missingConfirmationIsPassedOnAsFalseAndRejected() throws Exception {
        given(purgeService.purgeDocument(eq(21L), eq(false), any(), any()))
                .willThrow(new DomainBadRequestException("error.knowledge.purge.confirmRequired"));

        mockMvc.perform(delete("/api/web/knowledge/documents/21/purge")
                        .with(authentication(session())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("confirm=true")));
        verify(purgeService).purgeDocument(eq(21L), eq(false), any(), any());
    }

    @Test
    void batchPurgeReportsWhatIsLeft() throws Exception {
        given(purgeService.purgeArchived(eq("team-a"), eq("handbook"), eq(true), any(), any()))
                .willReturn(new KnowledgePurgeResponse(100, 5000L, 3L));

        mockMvc.perform(delete("/api/web/knowledge/bases/team-a/handbook/archived-documents").param("confirm", "true")
                        .with(authentication(session())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purgedDocuments").value(100))
                .andExpect(jsonPath("$.data.remainingArchived").value(3));
    }

    @Test
    void purgeRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/web/knowledge/documents/21/purge").param("confirm", "true").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private static UsernamePasswordAuthenticationToken session() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
