package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.service.MemberResourceAccessService;
import com.iflytek.skillhub.service.MySkillAppService;
import com.iflytek.skillhub.service.NamespacePortalQueryAppService;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API tokens (sk_ bearer) may read the knowledge base, "my skills" and "my namespaces" through
 * /api/v1 GET routes, but every knowledge write and the /api/web surface stay closed to them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ApiTokenReadOnlyAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private KnowledgeAppService knowledgeAppService;

    @MockBean
    private MemberResourceAccessService memberResourceAccessService;

    @MockBean
    private MySkillAppService mySkillAppService;

    @MockBean
    private NamespacePortalQueryAppService namespacePortalQueryAppService;

    @BeforeEach
    void setUp() {
        given(namespaceMemberRepository.findByUserId("alice"))
                .willReturn(List.of(new NamespaceMember(1L, "alice", NamespaceRole.MEMBER)));
    }

    @Test
    void tokenCanCallEveryReadOnlyKnowledgeAndPersonalRoute() throws Exception {
        given(knowledgeAppService.openContent(eq(100L), isNull(), any(), anyBoolean()))
                .willReturn(textFile());
        given(knowledgeAppService.openImage(eq(100L), eq(5L), isNull(), any()))
                .willReturn(textFile());

        for (String path : List.of(
                "/api/v1/knowledge/bases",
                "/api/v1/knowledge/bases/team-a/handbook",
                "/api/v1/knowledge/bases/team-a/handbook/folders",
                "/api/v1/knowledge/bases/team-a/handbook/documents",
                "/api/v1/knowledge/documents/search?q=onboarding",
                "/api/v1/knowledge/documents/100",
                "/api/v1/knowledge/documents/100/versions",
                "/api/v1/knowledge/documents/100/content",
                "/api/v1/knowledge/documents/100/images",
                "/api/v1/knowledge/documents/100/images/5/content",
                "/api/v1/me/skills",
                "/api/v1/me/stars",
                "/api/v1/me/subscriptions",
                "/api/v1/me/namespaces",
                "/api/v1/me/namespaces/page")) {
            mockMvc.perform(get(path).with(token("alice")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void tokenCallerIsProjectedWithUserIdAndNamespaceRoles() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/bases").with(token("alice")))
                .andExpect(status().isOk());

        ArgumentCaptor<KnowledgeAppService.Caller> caller = ArgumentCaptor.forClass(KnowledgeAppService.Caller.class);
        verify(knowledgeAppService).listBases(caller.capture());
        assertThat(caller.getValue().userId()).isEqualTo("alice");
        assertThat(caller.getValue().namespaceRoles()).isEqualTo(Map.of(1L, NamespaceRole.MEMBER));
        assertThat(caller.getValue().platformRoles()).containsExactly("USER");

        mockMvc.perform(get("/api/v1/knowledge/documents/100/content").with(token("alice")));
        // The member resource guard (namespace membership + download capability) runs for tokens too.
        verify(memberResourceAccessService).check(eq(Map.of("documentId", "100")), eq("alice"), eq(Set.of("USER")),
                eq(false), eq(true), eq(false));
    }

    @Test
    void tokenCannotWriteKnowledge() throws Exception {
        for (var request : List.of(
                post("/api/v1/knowledge/bases").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/knowledge/bases/team-a/handbook").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/knowledge/bases/team-a/handbook/folders").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/knowledge/bases/team-a/handbook/folders/3"),
                multipart("/api/v1/knowledge/bases/team-a/handbook/documents").file("file", "x".getBytes(StandardCharsets.UTF_8)),
                put("/api/v1/knowledge/documents/100").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/knowledge/documents/100"),
                multipart("/api/v1/knowledge/documents/100/versions").file("file", "x".getBytes(StandardCharsets.UTF_8)),
                post("/api/v1/knowledge/documents/100/versions/1/restore"))) {
            mockMvc.perform(request.with(token("alice")).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.msg").value(containsString("/api/v1/knowledge/")));
        }
        verifyNoInteractions(knowledgeAppService);
    }

    @Test
    void webSurfaceStaysSessionOnlyForTokens() throws Exception {
        for (String path : List.of("/api/web/knowledge/bases", "/api/web/me/skills", "/api/web/me/namespaces")) {
            mockMvc.perform(get(path).with(token("alice")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.msg").value(containsString(path)));
        }
        verifyNoInteractions(knowledgeAppService, mySkillAppService);
    }

    private static KnowledgeAppService.FileContent textFile() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        return new KnowledgeAppService.FileContent("notes.txt", "text/plain", bytes.length,
                KnowledgePreviewKind.TEXT, () -> new ByteArrayInputStream(bytes));
    }

    /** Mirrors what ApiTokenAuthenticationFilter builds for a token with the default scope set. */
    static RequestPostProcessor token(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", "", "api_token", Set.of("USER"));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("SCOPE_skill:read"),
                new SimpleGrantedAuthority("SCOPE_skill:publish"),
                new SimpleGrantedAuthority("SCOPE_skill:delete"))));
    }
}
