package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGuide;
import com.iflytek.skillhub.domain.namespace.NamespaceGuideRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NamespaceGuideAppServiceTest {
    private static final long NAMESPACE_ID = 7L;
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceGuideRepository guides = mock(NamespaceGuideRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private NamespaceGuideAppService service;
    private Namespace namespace;

    @BeforeEach
    void setUp() {
        service = new NamespaceGuideAppService(namespaces, guides, users, audit, new RequestIdAccessor(), clock);
        namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setStatus(NamespaceStatus.ACTIVE);
        ReflectionTestUtils.setField(namespace, "id", NAMESPACE_ID);
        when(namespaces.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(users.findByIdIn(any())).thenReturn(List.of());
        when(guides.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Map<Long, NamespaceRole> role(NamespaceRole role) {
        return Map.of(NAMESPACE_ID, role);
    }

    @Test
    void membersReadAndNonMembersSeeNotFound() {
        when(guides.findById(NAMESPACE_ID)).thenReturn(Optional.of(new NamespaceGuide(NAMESPACE_ID, "# 约定", "alice", clock.instant())));

        var guide = service.getGuide("@team-a", "bob", role(NamespaceRole.MEMBER));

        assertThat(guide.content()).isEqualTo("# 约定");
        assertThat(guide.canEdit()).isFalse();
        assertThat(guide.updatedBy()).isEqualTo("alice");
        assertThatThrownBy(() -> service.getGuide("team-a", "mallory", Map.of())).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void onlyOwnersAndAdminsEditAndEveryChangeIsAudited() {
        when(guides.findById(NAMESPACE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateGuide("team-a", "x", "bob", role(NamespaceRole.MEMBER), null))
                .isInstanceOf(DomainForbiddenException.class);

        var saved = service.updateGuide("team-a", "  # 团队约定  ", "carol", role(NamespaceRole.ADMIN), null);

        assertThat(saved.content()).isEqualTo("# 团队约定");
        assertThat(saved.canEdit()).isTrue();
        verify(audit).record(eq("carol"), eq("NAMESPACE_GUIDE_UPDATE"), eq("NAMESPACE"), eq(NAMESPACE_ID), any(), any(), any(), anyString());
    }

    @Test
    void blankContentRemovesTheGuideAndOverlongContentIsRejected() {
        when(guides.findById(NAMESPACE_ID)).thenReturn(Optional.of(new NamespaceGuide(NAMESPACE_ID, "old", "alice", clock.instant())));

        assertThat(service.updateGuide("team-a", "   ", "owner", role(NamespaceRole.OWNER), null).content()).isEmpty();
        verify(guides).deleteById(NAMESPACE_ID);

        assertThatThrownBy(() -> service.updateGuide("team-a", "x".repeat(NamespaceGuide.MAX_LENGTH + 1), "owner",
                role(NamespaceRole.OWNER), null)).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void frozenTeamsCannotBeEdited() {
        namespace.setStatus(NamespaceStatus.FROZEN);
        assertThatThrownBy(() -> service.updateGuide("team-a", "x", "owner", role(NamespaceRole.OWNER), null))
                .isInstanceOf(DomainForbiddenException.class);
        verify(guides, never()).save(any());
    }
}
