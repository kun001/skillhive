package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.token.McpTokenGrants;
import com.iflytek.skillhub.domain.audit.AuditDetail;
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
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.NamespaceGuideResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team guides: readable by team members (including their API tokens within the token's team
 * scope), editable by team owners and admins. Non-members see the team as not found.
 */
@Service
public class NamespaceGuideAppService {
    private final NamespaceRepository namespaces;
    private final NamespaceGuideRepository guides;
    private final UserAccountRepository users;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    public NamespaceGuideAppService(NamespaceRepository namespaces, NamespaceGuideRepository guides,
                                    UserAccountRepository users, AuditLogService auditLogService,
                                    RequestIdAccessor requestIdAccessor, Clock clock) {
        this.namespaces = namespaces;
        this.guides = guides;
        this.users = users;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NamespaceGuideResponse getGuide(String slug, String userId, Map<Long, NamespaceRole> roles) {
        Namespace namespace = memberNamespace(slug, roles);
        return toResponse(namespace, guides.findById(namespace.getId()), roles);
    }

    @Transactional
    public NamespaceGuideResponse updateGuide(String slug, String content, String userId, Map<Long, NamespaceRole> roles,
                                              AuditRequestContext audit) {
        Namespace namespace = memberNamespace(slug, roles);
        if (!canEdit(namespace, roles)) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
        String value = content == null ? "" : content.strip();
        if (value.length() > NamespaceGuide.MAX_LENGTH) {
            throw new DomainBadRequestException("error.namespace.guide.tooLong", NamespaceGuide.MAX_LENGTH);
        }
        Optional<NamespaceGuide> saved;
        if (value.isEmpty()) {
            guides.findById(namespace.getId()).ifPresent(guide -> guides.deleteById(namespace.getId()));
            saved = Optional.empty();
        } else {
            NamespaceGuide guide = guides.findById(namespace.getId())
                    .map(existing -> {
                        existing.update(value, userId, clock.instant());
                        return existing;
                    })
                    .orElseGet(() -> new NamespaceGuide(namespace.getId(), value, userId, clock.instant()));
            saved = Optional.of(guides.save(guide));
        }
        auditLogService.record(userId, "NAMESPACE_GUIDE_UPDATE", "NAMESPACE", namespace.getId(),
                requestIdAccessor.current(),
                audit != null ? audit.clientIp() : null,
                audit != null ? audit.userAgent() : null,
                AuditDetail.builder().put("length", value.length()).build());
        return toResponse(namespace, saved, roles);
    }

    private Namespace memberNamespace(String slug, Map<Long, NamespaceRole> roles) {
        String normalized = slug == null ? "" : slug.strip().replaceFirst("^@", "");
        Namespace namespace = namespaces.findBySlug(normalized)
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.slug.notFound", normalized));
        if (roles == null || roles.get(namespace.getId()) == null || !McpTokenGrants.namespaceAllowed(namespace.getId())) {
            throw new DomainNotFoundException("error.namespace.slug.notFound", normalized);
        }
        return namespace;
    }

    private static boolean canEdit(Namespace namespace, Map<Long, NamespaceRole> roles) {
        NamespaceRole role = roles.get(namespace.getId());
        return namespace.getStatus() == NamespaceStatus.ACTIVE && (role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN)
                && McpTokenGrants.current() == null;
    }

    private NamespaceGuideResponse toResponse(Namespace namespace, Optional<NamespaceGuide> guide, Map<Long, NamespaceRole> roles) {
        String updatedBy = guide.map(NamespaceGuide::getUpdatedBy)
                .map(id -> users.findByIdIn(List.of(id)).stream().findFirst().map(UserAccount::getDisplayName).orElse(id))
                .orElse(null);
        return new NamespaceGuideResponse(namespace.getSlug(), namespace.getDisplayName(),
                guide.map(NamespaceGuide::getContent).orElse(""), updatedBy,
                guide.map(NamespaceGuide::getUpdatedAt).orElse(null), canEdit(namespace, roles));
    }
}
