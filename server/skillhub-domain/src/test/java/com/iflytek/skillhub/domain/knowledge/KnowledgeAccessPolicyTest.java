package com.iflytek.skillhub.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeAccessPolicyTest {

    @Test
    void globalKnowledgeIsOnlyVisibleToPlatformAdmins() {
        Namespace global = new Namespace("global", "Global", "system");
        global.setType(NamespaceType.GLOBAL);
        assertThat(policy.canRead(global, NamespaceRole.MEMBER, Set.of())).isFalse();
        assertThat(policy.canRead(global, NamespaceRole.OWNER, Set.of())).isFalse();
        assertThat(policy.canRead(global, null, Set.of("SUPER_ADMIN"))).isTrue();
        assertThat(policy.canRead(global, NamespaceRole.MEMBER, Set.of("SKILL_ADMIN"))).isTrue();
        assertThat(policy.canRead(global, NamespaceRole.MEMBER, Set.of("USER_ADMIN"))).isTrue();
    }

    private final KnowledgeAccessPolicy policy = new KnowledgeAccessPolicy();
    private final KnowledgeBase base = new KnowledgeBase(1L, "handbook", "Handbook", null, "owner");

    private static Namespace team(NamespaceStatus status) {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(status);
        return namespace;
    }

    @Test
    void readRequiresMembershipOrSuperAdmin() {
        assertThat(policy.canRead(NamespaceRole.MEMBER, Set.of())).isTrue();
        assertThat(policy.canRead(null, Set.of())).isFalse();
        assertThat(policy.canRead(null, Set.of("SKILL_ADMIN"))).isFalse();
        assertThat(policy.canRead(null, Set.of("SUPER_ADMIN"))).isTrue();
    }

    @Test
    void onlyTeamAdminsCreateBasesInActiveTeamNamespaces() {
        Namespace active = team(NamespaceStatus.ACTIVE);
        assertThat(policy.canCreateBase(active, NamespaceRole.OWNER, Set.of())).isTrue();
        assertThat(policy.canCreateBase(active, NamespaceRole.ADMIN, Set.of())).isTrue();
        assertThat(policy.canCreateBase(active, NamespaceRole.MEMBER, Set.of())).isFalse();
        assertThat(policy.canCreateBase(team(NamespaceStatus.FROZEN), NamespaceRole.OWNER, Set.of())).isFalse();

        Namespace global = new Namespace("global", "Global", "owner");
        global.setType(NamespaceType.GLOBAL);
        assertThat(policy.canCreateBase(global, null, Set.of("SUPER_ADMIN"))).isFalse();
    }

    @Test
    void everyMemberContributesWhileNamespaceIsActive() {
        assertThat(policy.canContribute(team(NamespaceStatus.ACTIVE), base, NamespaceRole.MEMBER, Set.of())).isTrue();
        assertThat(policy.canContribute(team(NamespaceStatus.ACTIVE), base, null, Set.of())).isFalse();
        assertThat(policy.canContribute(team(NamespaceStatus.FROZEN), base, NamespaceRole.OWNER, Set.of())).isFalse();
    }

    @Test
    void documentsAreManagedByTheirOwnerOrNamespaceAdmins() {
        Namespace namespace = team(NamespaceStatus.ACTIVE);
        KnowledgeDocument document = new KnowledgeDocument(1L, null, "abc", "Doc", null, "alice");

        assertThat(policy.canManageDocument(namespace, base, document, "alice", NamespaceRole.MEMBER, Set.of())).isTrue();
        assertThat(policy.canManageDocument(namespace, base, document, "bob", NamespaceRole.MEMBER, Set.of())).isFalse();
        assertThat(policy.canManageDocument(namespace, base, document, "bob", NamespaceRole.ADMIN, Set.of())).isTrue();
        assertThat(policy.canManageDocument(namespace, base, document, "alice", null, Set.of())).isFalse();
    }

    @Test
    void foldersAreManagedByTheirCreatorOrNamespaceAdmins() {
        Namespace namespace = team(NamespaceStatus.ACTIVE);
        KnowledgeFolder folder = new KnowledgeFolder(1L, null, "Leave", "alice");

        assertThat(policy.canManageFolder(namespace, base, folder, "alice", NamespaceRole.MEMBER, Set.of())).isTrue();
        assertThat(policy.canManageFolder(namespace, base, folder, "bob", NamespaceRole.MEMBER, Set.of())).isFalse();
        assertThat(policy.canManageFolder(namespace, base, folder, "bob", NamespaceRole.OWNER, Set.of())).isTrue();
    }
}
