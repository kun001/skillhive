package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.io.InputStream;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Hands skill file content and packages to callers, auditing reads made with an API token.
 *
 * <p>Only token reads are audited: they are how scripts and AI tools (e.g. the MCP server) pull
 * skill content, while web-session and anonymous reads stay unrecorded to keep the log signal-rich.</p>
 */
@Service
public class SkillContentAppService {

    static final String ACTION_FILE_READ = "SKILL_FILE_READ";
    static final String ACTION_DOWNLOAD = "SKILL_DOWNLOAD";
    static final String TARGET_VERSION = "SKILL_VERSION";

    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SkillContentAppService(SkillQueryService skillQueryService,
                                  SkillDownloadService skillDownloadService,
                                  AuditLogService auditLogService,
                                  RequestIdAccessor requestIdAccessor) {
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    public InputStream fileContent(String namespace, String slug, String version, String path,
                                   String userId, Map<Long, NamespaceRole> userNsRoles, AuditRequestContext audit) {
        SkillQueryService.SkillFileContent file =
                skillQueryService.openFileContent(namespace, slug, version, path, userId, userNsRoles);
        recordTokenRead(ACTION_FILE_READ, file.skillId(), file.versionId(), file.version(),
                namespace, slug, null, path, userId, audit);
        return file.content();
    }

    public InputStream fileContentByTag(String namespace, String slug, String tagName, String path,
                                        String userId, Map<Long, NamespaceRole> userNsRoles, AuditRequestContext audit) {
        SkillQueryService.SkillFileContent file =
                skillQueryService.openFileContentByTag(namespace, slug, tagName, path, userId, userNsRoles);
        recordTokenRead(ACTION_FILE_READ, file.skillId(), file.versionId(), file.version(),
                namespace, slug, tagName, path, userId, audit);
        return file.content();
    }

    public SkillDownloadService.DownloadResult downloadLatest(String namespace, String slug, String userId,
                                                              Map<Long, NamespaceRole> userNsRoles,
                                                              AuditRequestContext audit) {
        return audited(skillDownloadService.downloadLatest(namespace, slug, userId, userNsRoles),
                namespace, slug, null, userId, audit);
    }

    public SkillDownloadService.DownloadResult downloadVersion(String namespace, String slug, String version,
                                                               String userId, Map<Long, NamespaceRole> userNsRoles,
                                                               AuditRequestContext audit) {
        return audited(skillDownloadService.downloadVersion(namespace, slug, version, userId, userNsRoles),
                namespace, slug, null, userId, audit);
    }

    public SkillDownloadService.DownloadResult downloadByTag(String namespace, String slug, String tagName,
                                                             String userId, Map<Long, NamespaceRole> userNsRoles,
                                                             AuditRequestContext audit) {
        return audited(skillDownloadService.downloadByTag(namespace, slug, tagName, userId, userNsRoles),
                namespace, slug, tagName, userId, audit);
    }

    private SkillDownloadService.DownloadResult audited(SkillDownloadService.DownloadResult result,
                                                        String namespace, String slug, String tagName,
                                                        String userId, AuditRequestContext audit) {
        recordTokenRead(ACTION_DOWNLOAD, result.skillId(), result.versionId(), result.version(),
                namespace, slug, tagName, null, userId, audit);
        return result;
    }

    private void recordTokenRead(String action, Long skillId, Long versionId, String version,
                                 String namespace, String slug, String tagName, String path,
                                 String userId, AuditRequestContext audit) {
        if (audit == null || audit.apiTokenId() == null) {
            return;
        }
        auditLogService.record(
                userId,
                action,
                TARGET_VERSION,
                versionId,
                requestIdAccessor.current(),
                audit.clientIp(),
                audit.userAgent(),
                AuditDetail.builder()
                        .put("skillId", skillId)
                        .put("namespace", namespace)
                        .put("slug", slug)
                        .put("version", version)
                        .put("tag", tagName)
                        .put("path", path)
                        .put("authMethod", "api_token")
                        .put("apiTokenId", audit.apiTokenId())
                        .build());
    }
}
