package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.*;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.knowledge.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.*;
import com.iflytek.skillhub.dto.McpUploadRequest;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class McpWriteServiceTest {
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final KnowledgeAppService knowledge = mock(KnowledgeAppService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    private final McpWriteService service = new McpWriteService(namespaces, members, mock(SkillRepository.class),
            mock(KnowledgeDocumentRepository.class), bases, mock(SkillPublishService.class),
            mock(SkillGovernanceService.class), mock(SkillPackageArchiveExtractor.class), knowledge, jdbc, mapper, mock(AuditLogService.class));
    private final PlatformPrincipal actor = new PlatformPrincipal("alice", "Alice", null, null, "api_token", Set.of("SUPER_ADMIN"));
    private final McpUploadRequest file = new McpUploadRequest("retry", "guide.txt", "aGVsbG8=", null, null, null, null);
    private MockHttpServletRequest request(Set<Long> ids) {
        var request = new MockHttpServletRequest();
        request.setAttribute(McpTokenGrants.ATTRIBUTE, new McpTokenGrants(ids, null));
        request.setAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE, 9L);
        return request;
    }
    private Namespace team() {
        var team = new Namespace("team", "Team", "alice");
        ReflectionTestUtils.setField(team, "id", 1L);
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(team));
        var base = new KnowledgeBase(1L, "docs", "Docs", null, "alice");
        ReflectionTestUtils.setField(base, "id", 4L);
        when(bases.findByNamespaceIdAndSlugForUpdate(1L, "docs")).thenReturn(Optional.of(base));
        return team;
    }
    @Test void administratorTokenCannotWriteOutsideMembershipOrExplicitResourceGrant() {
        team();
        assertThatThrownBy(() -> service.uploadDocument("team", "docs", file, actor, Map.of(), request(Set.of(2L))))
                .isInstanceOf(ResponseStatusException.class);
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.uploadDocument("team", "docs", file, actor, Map.of(), request(Set.of(1L))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc, knowledge);
    }
    @Test void editingRevocationAndFrozenNamespaceBlockMutation() {
        Namespace team = team();
        var member = new NamespaceMember(1L, "alice", NamespaceRole.MEMBER);
        ReflectionTestUtils.setField(member, "canEdit", false);
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.uploadDocument("team", "docs", file, actor, Map.of(), request(Set.of(1L))))
                .isInstanceOf(ResponseStatusException.class);
        team.setStatus(NamespaceStatus.FROZEN);
        assertThatThrownBy(() -> service.uploadDocument("team", "docs", file, actor, Map.of(), request(Set.of(1L))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc, knowledge);
    }
    @Test void reusingAnOperationKeyWithChangedBytesCannotPerformAnotherWrite() {
        team();
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.of(new NamespaceMember(1L, "alice", NamespaceRole.MEMBER)));
        when(jdbc.queryForMap(anyString(), eq(9L), eq("knowledge_upload"), eq("retry")))
                .thenReturn(Map.of("request_hash", "different"));
        assertThatThrownBy(() -> service.uploadDocument("team", "docs", file, actor, Map.of(1L, NamespaceRole.MEMBER), request(Set.of(1L))))
                .isInstanceOf(ResponseStatusException.class);
        verify(knowledge, never()).uploadDocument(anyString(), anyString(), any(), any(), any(), any(), any(), any());
    }
}
