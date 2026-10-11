package com.iflytek.skillhub.controller.portal;

import static com.iflytek.skillhub.controller.portal.ApiTokenReadOnlyAccessTest.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs token reads through the real knowledge service and member resource guard: a token only
 * sees its own team's knowledge, and file content still requires the download capability.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ApiTokenKnowledgeIsolationTest {

    private static final long TEAM_A = 1L;
    private static final long TEAM_B = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceRepository namespaceRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @MockBean
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @MockBean
    private KnowledgeDocumentVersionRepository knowledgeDocumentVersionRepository;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private AuditLogService auditLogService;

    private NamespaceMember aliceInTeamA;

    @BeforeEach
    void setUp() {
        Namespace teamA = namespace(TEAM_A, "team-a");
        Namespace teamB = namespace(TEAM_B, "team-b");
        given(namespaceRepository.findById(TEAM_A)).willReturn(Optional.of(teamA));
        given(namespaceRepository.findById(TEAM_B)).willReturn(Optional.of(teamB));
        given(namespaceRepository.findBySlug("team-a")).willReturn(Optional.of(teamA));
        given(namespaceRepository.findBySlug("team-b")).willReturn(Optional.of(teamB));

        KnowledgeBase handbook = base(10L, TEAM_A, "handbook");
        KnowledgeBase secret = base(20L, TEAM_B, "secret");
        given(knowledgeBaseRepository.findById(10L)).willReturn(Optional.of(handbook));
        given(knowledgeBaseRepository.findById(20L)).willReturn(Optional.of(secret));
        given(knowledgeBaseRepository.findByNamespaceIdAndSlug(TEAM_A, "handbook")).willReturn(Optional.of(handbook));
        given(knowledgeBaseRepository.findByNamespaceIdAndSlug(TEAM_B, "secret")).willReturn(Optional.of(secret));

        KnowledgeDocumentVersion versionA = version(1000L, 100L, "knowledge/a.txt");
        KnowledgeDocumentVersion versionB = version(2000L, 200L, "knowledge/b.txt");
        given(knowledgeDocumentRepository.findById(100L)).willReturn(Optional.of(document(100L, 10L, versionA)));
        given(knowledgeDocumentRepository.findById(200L)).willReturn(Optional.of(document(200L, 20L, versionB)));
        given(knowledgeDocumentVersionRepository.findById(1000L)).willReturn(Optional.of(versionA));
        given(knowledgeDocumentVersionRepository.findById(2000L)).willReturn(Optional.of(versionB));
        given(objectStorageService.getObject(anyString()))
                .willAnswer(invocation -> new ByteArrayInputStream("team file".getBytes(StandardCharsets.UTF_8)));

        aliceInTeamA = new NamespaceMember(TEAM_A, "alice", NamespaceRole.MEMBER);
        given(namespaceMemberRepository.findByUserId("alice")).willReturn(List.of(aliceInTeamA));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(TEAM_A, "alice")).willReturn(Optional.of(aliceInTeamA));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(TEAM_B, "alice")).willReturn(Optional.empty());
    }

    @Test
    void tokenReadsOwnTeamFileContentAndIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents/100/content")
                        .with(token("alice", "knowledge:read"))
                        .requestAttr("apiTokenId", 7L))
                .andExpect(status().isOk())
                .andExpect(content().string("team file"));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("alice"), eq("KNOWLEDGE_DOCUMENT_DOWNLOAD"), eq("KNOWLEDGE_DOCUMENT"),
                eq(100L), any(), any(), any(), detail.capture());
        assertThat(detail.getValue())
                .contains("\"authMethod\":\"api_token\"")
                .contains("\"apiTokenId\":7")
                .contains("\"version\":1");
    }

    @Test
    void tokenPreviewIsAuditedAsPreview() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents/100/content?disposition=inline")
                        .with(token("alice", "knowledge:read")))
                .andExpect(status().isOk());

        verify(auditLogService).record(eq("alice"), eq("KNOWLEDGE_DOCUMENT_PREVIEW"), eq("KNOWLEDGE_DOCUMENT"),
                eq(100L), any(), any(), any(), anyString());
    }

    @Test
    void tokenCannotSeeAnotherTeamsKnowledge() throws Exception {
        for (String path : List.of(
                "/api/v1/knowledge/bases/team-b/secret",
                "/api/v1/knowledge/bases/team-b/secret/documents",
                "/api/v1/knowledge/bases/team-b/secret/folders",
                "/api/v1/knowledge/documents/200",
                "/api/v1/knowledge/documents/200/versions",
                "/api/v1/knowledge/documents/200/content")) {
            mockMvc.perform(get(path).with(token("alice", "knowledge:read")))
                    .andExpect(status().is4xxClientError())
                    .andExpect(status().is(org.hamcrest.Matchers.oneOf(403, 404)))
                    // Rejected by the membership check, not by the token route policy.
                    .andExpect(jsonPath("$.msg").value(not(containsString("API"))));
        }
        verify(objectStorageService, never()).getObject(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void tokenWithoutDownloadPermissionCannotFetchContent() throws Exception {
        aliceInTeamA.setCanDownload(false);

        for (String path : List.of(
                "/api/v1/knowledge/documents/100/content",
                "/api/v1/knowledge/documents/100/content?disposition=inline")) {
            mockMvc.perform(get(path).with(token("alice", "knowledge:read")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.msg").value(not(containsString("API"))));
        }
        verify(objectStorageService, never()).getObject(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void superAdminTokenReadsKnowledgeWithTeamMembershipOnly() throws Exception {
        given(namespaceMemberRepository.findByUserId("root")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/knowledge/bases").with(principal("root", "api_token", "knowledge:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        for (String path : List.of(
                "/api/v1/knowledge/bases/team-b/secret",
                "/api/v1/knowledge/documents/200",
                "/api/v1/knowledge/documents/200/content",
                "/api/v1/knowledge/documents/100/content")) {
            mockMvc.perform(get(path).with(principal("root", "api_token", "knowledge:read")))
                    .andExpect(status().is(org.hamcrest.Matchers.oneOf(403, 404)));
        }
        verify(knowledgeBaseRepository, never()).findByStatus(any());
        verify(objectStorageService, never()).getObject(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void superAdminWebSessionStillReadsEveryTeam() throws Exception {
        given(namespaceMemberRepository.findByUserId("root")).willReturn(List.of());

        mockMvc.perform(get("/api/web/knowledge/documents/200/content").with(principal("root", "github")))
                .andExpect(status().isOk())
                .andExpect(content().string("team file"));
    }

    /** A SUPER_ADMIN principal signed in through {@code provider}, with the given token scopes. */
    private static RequestPostProcessor principal(String userId, String provider, String... scopes) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", "", provider, Set.of("USER", "SUPER_ADMIN"));
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(List.of(
                new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private static Namespace namespace(long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "owner");
        ReflectionTestUtils.setField(namespace, "id", id);
        return namespace;
    }

    private static KnowledgeBase base(long id, long namespaceId, String slug) {
        KnowledgeBase base = new KnowledgeBase(namespaceId, slug, slug, "", "owner");
        ReflectionTestUtils.setField(base, "id", id);
        return base;
    }

    private static KnowledgeDocumentVersion version(long id, long documentId, String objectKey) {
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(
                documentId, 1, objectKey, "text/plain", "notes.txt", 9L, "sha", null, "owner");
        ReflectionTestUtils.setField(version, "id", id);
        version.publishDirectly("owner");
        return version;
    }

    private static KnowledgeDocument document(long id, long baseId, KnowledgeDocumentVersion version) {
        KnowledgeDocument document = new KnowledgeDocument(baseId, null, "notes-" + id, "notes.txt", "", "owner");
        ReflectionTestUtils.setField(document, "id", id);
        document.publish(version, "txt");
        return document;
    }
}
