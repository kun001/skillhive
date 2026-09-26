package com.iflytek.skillhub.domain.knowledge;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Access rules for knowledge bases. Knowledge is never anonymous: every read requires
 * membership in the owning team namespace (or the platform super-admin role).
 *
 * <p>{@code role} is the caller's role in the owning namespace, or {@code null} when the
 * caller is not a member.</p>
 */
@Component
public class KnowledgeAccessPolicy {

    static final String SUPER_ADMIN = "SUPER_ADMIN";

    public boolean canRead(NamespaceRole role, Set<String> platformRoles) {
        return role != null || isSuperAdmin(platformRoles);
    }

    public boolean canCreateBase(Namespace namespace, NamespaceRole role, Set<String> platformRoles) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ACTIVE
                && (isNamespaceAdmin(role) || isSuperAdmin(platformRoles));
    }

    public boolean canManageBase(Namespace namespace, KnowledgeBase base, NamespaceRole role, Set<String> platformRoles) {
        return isWritable(namespace, base) && (isNamespaceAdmin(role) || isSuperAdmin(platformRoles));
    }

    /** Uploading files, adding versions and creating folders are open to every member. */
    public boolean canContribute(Namespace namespace, KnowledgeBase base, NamespaceRole role, Set<String> platformRoles) {
        return isWritable(namespace, base) && (role != null || isSuperAdmin(platformRoles));
    }

    public boolean canManageDocument(Namespace namespace,
                                     KnowledgeBase base,
                                     KnowledgeDocument document,
                                     String userId,
                                     NamespaceRole role,
                                     Set<String> platformRoles) {
        return canContribute(namespace, base, role, platformRoles)
                && (Objects.equals(document.getOwnerId(), userId)
                        || isNamespaceAdmin(role)
                        || isSuperAdmin(platformRoles));
    }

    public boolean canManageFolder(Namespace namespace,
                                   KnowledgeBase base,
                                   KnowledgeFolder folder,
                                   String userId,
                                   NamespaceRole role,
                                   Set<String> platformRoles) {
        return canContribute(namespace, base, role, platformRoles)
                && (Objects.equals(folder.getCreatedBy(), userId)
                        || isNamespaceAdmin(role)
                        || isSuperAdmin(platformRoles));
    }

    private boolean isWritable(Namespace namespace, KnowledgeBase base) {
        return namespace.getStatus() == NamespaceStatus.ACTIVE
                && base.getStatus() == KnowledgeBaseStatus.ACTIVE;
    }

    private boolean isNamespaceAdmin(NamespaceRole role) {
        return role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains(SUPER_ADMIN);
    }
}
