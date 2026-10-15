package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.*;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.*;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.*;
import com.iflytek.skillhub.dto.McpKnowledgeRequest;
import com.iflytek.skillhub.dto.knowledge.*;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class McpKnowledgeCrudTest {
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    private final KnowledgeAppService knowledge = mock(KnowledgeAppService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpWriteService service = new McpWriteService(namespaces, members, mock(SkillRepository.class), documents, bases,
            mock(SkillPublishService.class), mock(SkillGovernanceService.class), mock(SkillPackageArchiveExtractor.class),
            knowledge, jdbc, mapper, mock(AuditLogService.class));
    private final PlatformPrincipal actor = new PlatformPrincipal("alice", "Alice", null, null, "api_token", Set.of("SUPER_ADMIN"));
    private final Map<Long, NamespaceRole> owner = Map.of(1L, NamespaceRole.OWNER);
    private NamespaceMember member;

    @BeforeEach void setup() {
        var team = new Namespace("team", "Team", "alice");
        ReflectionTestUtils.setField(team, "id", 1L);
        var base = new KnowledgeBase(1L, "docs", "Docs", null, "alice");
        ReflectionTestUtils.setField(base, "id", 5L);
        var document = new KnowledgeDocument(5L, null, "guide", "Guide", null, "alice");
        ReflectionTestUtils.setField(document, "id", 7L);
        member = new NamespaceMember(1L, "alice", NamespaceRole.OWNER);
        when(namespaces.findById(1L)).thenReturn(Optional.of(team));
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(team));
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.of(member));
        when(bases.findById(5L)).thenReturn(Optional.of(base));
        when(bases.findByNamespaceIdAndSlugForUpdate(1L, "docs")).thenReturn(Optional.of(base));
        when(documents.findByIdForUpdate(7L)).thenReturn(Optional.of(document));
    }

    private MockHttpServletRequest request(Set<Long> baseIds) {
        var request = new MockHttpServletRequest();
        request.setAttribute(McpTokenGrants.ATTRIBUTE, new McpTokenGrants(Set.of(1L), baseIds));
        request.setAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE, 9L);
        request.setAttribute("userId", "alice");
        return request;
    }

    private String hash(Object parameters) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(parameters)));
    }

    private void allow(String operation, String key, Object parameters) throws Exception {
        when(jdbc.queryForMap(anyString(), eq(9L), eq(operation), eq(key))).thenReturn(Map.of("request_hash", hash(parameters)));
    }

    @Test void baseCreationNeedsNamespaceWideGrantAndCurrentTeamAdminRole() throws Exception {
        var change = new McpKnowledgeRequest.CreateBase("create", "team", "new-docs", "Docs", null);
        assertThatThrownBy(() -> service.createBase(change, actor, owner, request(Set.of(5L))))
                .isInstanceOf(ResponseStatusException.class);
        member.setRole(NamespaceRole.MEMBER);
        assertThatThrownBy(() -> service.createBase(change, actor, Map.of(1L, NamespaceRole.MEMBER), request(null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc, knowledge);
        member.setRole(NamespaceRole.OWNER);
        allow("knowledge_base_create", "create", change);
        when(knowledge.createBase(any(), any(), any())).thenReturn(
                new KnowledgeBaseResponse(8L, "team", "Team", "new-docs", "Docs", null, "ACTIVE", 0, null, true, true));
        assertThat(service.createBase(change, actor, owner, request(null)).path("id").asLong()).isEqualTo(8L);
    }

    @Test void updatesRecheckBaseGrantEditingAndOwnershipEvenForPlatformAdministrators() {
        var change = new McpKnowledgeRequest.UpdateDocument("edit", "Changed", null, null);
        assertThatThrownBy(() -> service.updateDocument(7L, change, actor, owner, request(Set.of(6L))))
                .isInstanceOf(ResponseStatusException.class);
        member.setRole(NamespaceRole.MEMBER);
        member.setCanEdit(false);
        assertThatThrownBy(() -> service.updateDocument(7L, change, actor, Map.of(1L, NamespaceRole.MEMBER), request(null)))
                .isInstanceOf(ResponseStatusException.class);
        member.setCanEdit(true);
        var other = new KnowledgeDocument(5L, null, "other", "Other", null, "bob");
        when(documents.findByIdForUpdate(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateDocument(7L, change, actor, Map.of(1L, NamespaceRole.MEMBER), request(null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
        verify(knowledge, never()).updateDocument(anyLong(), any(), any(), any());
    }

    @Test void staleVersionCannotPublishAndIdenticalRetryDoesNotCreateAnotherVersion() throws Exception {
        var change = new McpKnowledgeRequest.UploadVersion("version", "guide.txt", "aGVsbG8=", 1, "updated");
        var params = Map.of("documentId", 7L, "change", change);
        allow("knowledge_version_upload", "version", params);
        when(knowledge.getDocument(eq(7L), any())).thenReturn(detail(2));
        assertThatThrownBy(() -> service.uploadVersion(7L, change, actor, owner, request(null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verify(knowledge, never()).uploadVersion(anyLong(), any(), anyString(), any(), any());

        when(knowledge.getDocument(eq(7L), any())).thenReturn(detail(1));
        var result = new KnowledgeDocumentVersionResponse(12L, 2, "guide.txt", "text/plain", 5, "hash", "updated", null, null, true);
        when(knowledge.uploadVersion(eq(7L), any(), eq("updated"), any(), any())).thenReturn(result);
        assertThat(service.uploadVersion(7L, change, actor, owner, request(null)).path("versionNumber").asInt()).isEqualTo(2);
        when(jdbc.queryForMap(anyString(), eq(9L), eq("knowledge_version_upload"), eq("version")))
                .thenReturn(Map.of("request_hash", hash(params), "result", mapper.writeValueAsString(result)));
        when(knowledge.getDocument(eq(7L), any())).thenReturn(detail(2));
        assertThat(service.uploadVersion(7L, change, actor, owner, request(null)).path("versionNumber").asInt()).isEqualTo(2);
        verify(knowledge, times(1)).uploadVersion(eq(7L), any(), eq("updated"), any(), any());
        member.setRole(NamespaceRole.MEMBER);
        member.setCanEdit(false);
        assertThatThrownBy(() -> service.uploadVersion(7L, change, actor, Map.of(1L, NamespaceRole.MEMBER), request(null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    private KnowledgeDocumentDetailResponse detail(int version) {
        return new KnowledgeDocumentDetailResponse(new KnowledgeDocumentResponse(7L, 5L, null, "Guide", null,
                "guide.txt", "txt", "text/plain", 5L, version, "TEXT", null, null, null, true, true),
                null, List.of(), "hash", null, null, List.of());
    }

    @Test void folderMutationsKeepParametersAndRejectChangedRetryKeys() throws Exception {
        var change = new McpKnowledgeRequest.Folder("folder", "Policies", 3L);
        var params = Map.of("namespace", "team", "base", "docs", "folderId", 4L, "change", change);
        allow("knowledge_folder_update", "folder", params);
        when(knowledge.updateFolder(eq("team"), eq("docs"), eq(4L), any(), any(), any()))
                .thenReturn(new KnowledgeFolderResponse(4L, 3L, "Policies", 0, true));
        assertThat(service.updateFolder("team", "docs", 4L, change, actor, owner, request(Set.of(5L)))
                .path("parentId").asLong()).isEqualTo(3L);
        assertThatThrownBy(() -> service.updateFolder("team", "docs", 4L,
                new McpKnowledgeRequest.Folder("folder", "Other", null), actor, owner, request(null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verify(knowledge, times(1)).updateFolder(eq("team"), eq("docs"), eq(4L), any(), any(), any());
    }
}
