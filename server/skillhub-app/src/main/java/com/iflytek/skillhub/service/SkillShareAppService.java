package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.MemberResourcePolicy;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillInstallability;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillShare;
import com.iflytek.skillhub.domain.skill.SkillShareRepository;
import com.iflytek.skillhub.domain.skill.SkillShareVisibility;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.SharedSkillResponse;
import com.iflytek.skillhub.dto.SkillShareSettingsResponse;
import com.iflytek.skillhub.dto.UpdateSkillShareRequest;
import com.iflytek.skillhub.dto.VisitorShareExpiration;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SkillShareAppService {
    private final SkillShareRepository shares;
    private final SkillRepository skills;
    private final SkillLifecycleProjectionService lifecycle;
    private final NamespaceRepository namespaces;
    private final MemberResourcePolicy memberAccess;
    private final VisibilityChecker visibilityChecker;
    private final SkillQueryService skillQuery;
    private final SkillDownloadService downloads;
    private final AuditLogService auditLog;
    private final RequestIdAccessor requestId;
    private final Clock clock;

    public SkillShareAppService(SkillShareRepository shares, SkillRepository skills, SkillLifecycleProjectionService lifecycle,
                                NamespaceRepository namespaces, SkillQueryService skillQuery, SkillDownloadService downloads,
                                AuditLogService auditLog, RequestIdAccessor requestId, Clock clock,
                                MemberResourcePolicy memberAccess, VisibilityChecker visibilityChecker) {
        this.shares = shares;
        this.skills = skills;
        this.lifecycle = lifecycle;
        this.namespaces = namespaces;
        this.memberAccess = memberAccess;
        this.visibilityChecker = visibilityChecker;
        this.skillQuery = skillQuery;
        this.downloads = downloads;
        this.auditLog = auditLog;
        this.requestId = requestId;
        this.clock = clock;
    }

    public record Caller(String userId, Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {}

    public boolean canManage(Long namespaceId, String ownerId, Caller caller) {
        return caller.userId() != null && namespaces.findById(namespaceId)
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .map(namespace -> isShareManager(namespace, ownerId, caller))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SkillShareSettingsResponse settings(Long skillId, Caller caller) {
        requireManager(skills.findById(skillId).orElseThrow(SkillShareAppService::unavailable), caller);
        return settings(shares.findById(skillId).orElse(null));
    }

    @Transactional
    public SkillShareSettingsResponse update(Long skillId, UpdateSkillShareRequest request,
                                             Caller caller, AuditRequestContext audit) {
        Skill skill = skills.findByIdForUpdate(skillId).orElseThrow(SkillShareAppService::unavailable);
        requireManager(skill, caller);
        var existing = shares.findById(skillId);
        Instant now = clock.instant();
        Instant expiration = VisitorShareExpiration.resolve(request.enabled(), request.expiration(),
                existing.map(SkillShare::getExpiresAt).orElse(null), now);
        SkillShare saved = null;
        if (request.enabled()) {
            currentVersion(skill);
            SkillShare share = existing.orElseGet(() -> new SkillShare(skillId));
            share.updateExpiration(expiration, now);
            if (request.visibility() != null) share.setVisibility(request.visibility());
            saved = shares.save(share);
        } else {
            existing.ifPresent(shares::delete);
        }
        auditLog.record(caller.userId(), "SKILL_SHARING", "SKILL", skillId, requestId.current(),
                audit.clientIp(), audit.userAgent(), AuditDetail.builder()
                        .put("enabled", request.enabled())
                        .put("visibility", saved == null ? null : saved.getVisibility().name())
                        .put("expiresAt", expiration == null ? null : expiration.toString()).build());
        return settings(saved);
    }

    @Transactional(readOnly = true)
    public SharedSkillResponse detail(String shareId, Caller caller) {
        SharedSkill shared = resolve(shareId);
        Skill skill = shared.skill();
        SkillShare link = shared.link();
        if (link.getVisibility() == SkillShareVisibility.ENTRY) {
            boolean canOpen = caller.userId() != null
                    && visibilityChecker.canAccess(skill, caller.userId(), caller.namespaceRoles())
                    && (shared.namespace().getType() == NamespaceType.GLOBAL
                    || memberAccess.canRead(skill.getNamespaceId(), caller.userId(), caller.platformRoles()));
            var workspace = canOpen ? new SharedSkillResponse.SkillWorkspace(shared.namespace().getSlug(), skill.getSlug()) : null;
            return new SharedSkillResponse(skill.getDisplayName(), null, null, link.getExpiresAt(), link.getVisibility(), workspace);
        }
        return new SharedSkillResponse(skill.getDisplayName(), skill.getSummary(), shared.version().getVersion(),
                link.getExpiresAt(), link.getVisibility(), null);
    }

    @Transactional(readOnly = true)
    public InputStream documentation(String shareId, String version) {
        SharedSkill shared = resolvePublic(shareId);
        if (!shared.version().getVersion().equals(version)) throw unavailable();
        return skillQuery.openSharedDocumentation(shared.version());
    }

    @Transactional
    public SkillDownloadService.DownloadResult download(String shareId) {
        SharedSkill shared = resolvePublic(shareId);
        return downloads.downloadSharedVersion(shared.skill(), shared.version());
    }

    private SharedSkill resolvePublic(String shareId) {
        SharedSkill shared = resolve(shareId);
        if (shared.link().getVisibility() != SkillShareVisibility.PUBLIC) throw unavailable();
        return shared;
    }

    private SharedSkill resolve(String shareId) {
        UUID token;
        try {
            token = UUID.fromString(shareId);
        } catch (IllegalArgumentException e) {
            throw unavailable();
        }
        SkillShare share = shares.findByShareId(token)
                .or(() -> shares.findByEntryShareId(token))
                .filter(candidate -> token.equals(candidate.getShareId()))
                .filter(candidate -> candidate.isActiveAt(clock.instant()))
                .orElseThrow(SkillShareAppService::unavailable);
        Skill skill = skills.findById(share.getSkillId()).orElseThrow(SkillShareAppService::unavailable);
        Namespace namespace = namespaces.findById(skill.getNamespaceId())
                .filter(candidate -> candidate.getStatus() == NamespaceStatus.ACTIVE)
                .orElseThrow(SkillShareAppService::unavailable);
        return new SharedSkill(skill, currentVersion(skill), namespace, share);
    }

    private SkillVersion currentVersion(Skill skill) {
        if (skill.isHidden() || skill.getStatus() != SkillStatus.ACTIVE) {
            throw unavailable();
        }
        SkillVersion version = lifecycle.resolvePublishedVersion(skill);
        if (!SkillInstallability.isInstallableVersion(version) || !skill.getId().equals(version.getSkillId())) {
            throw unavailable();
        }
        return version;
    }

    private void requireManager(Skill skill, Caller caller) {
        if (!canManage(skill.getNamespaceId(), skill.getOwnerId(), caller)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
    }

    private boolean isShareManager(Namespace namespace, String ownerId, Caller caller) {
        if (caller.platformRoles().contains("SUPER_ADMIN")) return true;
        boolean skillOwner = caller.userId().equals(ownerId);
        if (namespace.getType() == NamespaceType.GLOBAL) {
            return skillOwner && caller.platformRoles().contains("SKILL_ADMIN");
        }
        NamespaceRole role = caller.namespaceRoles().get(namespace.getId());
        if (role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN) return true;
        if (!skillOwner || role == null) return false;
        var permissions = memberAccess.filePermissions(namespace.getId(), caller.userId(), caller.platformRoles());
        return permissions.canEdit() && permissions.canDownload();
    }

    private static SkillShareSettingsResponse settings(SkillShare share) {
        return share == null ? new SkillShareSettingsResponse(null, null, null)
                : new SkillShareSettingsResponse(share.getShareId().toString(), share.getExpiresAt(), share.getVisibility());
    }

    private static DomainNotFoundException unavailable() {
        return new DomainNotFoundException("error.skill.share.notFound");
    }

    private record SharedSkill(Skill skill, SkillVersion version, Namespace namespace, SkillShare link) {}
}
