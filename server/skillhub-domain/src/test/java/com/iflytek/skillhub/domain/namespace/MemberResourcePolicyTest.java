package com.iflytek.skillhub.domain.namespace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberResourcePolicyTest {
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final MemberResourcePolicy policy = new MemberResourcePolicy(members);

    @Test
    void readOnlyMemberCanReadButCannotEditOrDownloadAndChangesApplyImmediately() {
        NamespaceMember member = new NamespaceMember(7L, "guest", NamespaceRole.MEMBER);
        member.setCanEdit(false);
        member.setCanDownload(false);
        when(members.findByNamespaceIdAndUserId(7L, "guest")).thenReturn(Optional.of(member));
        assertThat(policy.canRead(7L, "guest", Set.of())).isTrue();
        assertThatThrownBy(() -> policy.assertEdit(7L, "guest", Set.of())).isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> policy.assertDownload(7L, "guest", Set.of())).isInstanceOf(DomainForbiddenException.class);
        member.setCanDownload(true);
        assertThat(policy.canDownload(7L, "guest", Set.of())).isTrue();
        assertThat(policy.canEdit(7L, "guest", Set.of())).isFalse();
        member.setCanEdit(true);
        member.setCanDownload(false);
        assertThat(policy.canEdit(7L, "guest", Set.of())).isTrue();
        assertThat(policy.canDownload(7L, "guest", Set.of())).isFalse();
    }

    @Test
    void removedMemberAndAnonymousCannotRead() {
        assertThat(policy.canRead(7L, "outsider", Set.of())).isFalse();
        assertThat(policy.canRead(7L, null, Set.of())).isFalse();
    }

    @Test
    void administratorsRetainFullCapabilities() {
        for (NamespaceRole role : Set.of(NamespaceRole.ADMIN, NamespaceRole.OWNER)) {
            NamespaceMember member = new NamespaceMember(7L, "admin", role);
            member.setCanEdit(false);
            member.setCanDownload(false);
            when(members.findByNamespaceIdAndUserId(7L, "admin")).thenReturn(Optional.of(member));
            assertThat(policy.canEdit(7L, "admin", Set.of())).isTrue();
            assertThat(policy.canDownload(7L, "admin", Set.of())).isTrue();
        }
        assertThat(policy.canEdit(7L, "platform-admin", Set.of("SUPER_ADMIN"))).isTrue();
    }
}
