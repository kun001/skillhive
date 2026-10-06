package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Member capabilities supplement, rather than replace, ownership and lifecycle rules. */
@Service
public class MemberResourcePolicy {
    private final NamespaceMemberRepository members;

    public MemberResourcePolicy(NamespaceMemberRepository members) { this.members = members; }

    public boolean canRead(Long namespaceId, String userId, Set<String> roles) {
        return isSuperAdmin(roles) || member(namespaceId, userId) != null;
    }

    public boolean canEdit(Long namespaceId, String userId, Set<String> roles) {
        NamespaceMember member = member(namespaceId, userId);
        return isSuperAdmin(roles) || member != null && member.canEdit();
    }

    public boolean canDownload(Long namespaceId, String userId, Set<String> roles) {
        NamespaceMember member = member(namespaceId, userId);
        return isSuperAdmin(roles) || member != null && member.canDownload();
    }

    public void assertRead(Long namespaceId, String userId, Set<String> roles) {
        if (!canRead(namespaceId, userId, roles)) throw new DomainForbiddenException("error.namespace.membership.required");
    }

    public void assertEdit(Long namespaceId, String userId, Set<String> roles) {
        if (!canEdit(namespaceId, userId, roles)) throw new DomainForbiddenException("error.member.edit.denied");
    }

    public void assertDownload(Long namespaceId, String userId, Set<String> roles) {
        if (!canDownload(namespaceId, userId, roles)) throw new DomainForbiddenException("error.member.download.denied");
    }

    private NamespaceMember member(Long namespaceId, String userId) {
        return userId == null ? null : members.findByNamespaceIdAndUserId(namespaceId, userId).orElse(null);
    }

    private boolean isSuperAdmin(Set<String> roles) { return roles != null && roles.contains("SUPER_ADMIN"); }
}
