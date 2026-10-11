package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SkillContentAppServiceTest {

    private final SkillQueryService skillQueryService = mock(SkillQueryService.class);
    private final SkillDownloadService skillDownloadService = mock(SkillDownloadService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RequestIdAccessor requestIdAccessor = mock(RequestIdAccessor.class);
    private final SkillContentAppService service =
            new SkillContentAppService(skillQueryService, skillDownloadService, auditLogService, requestIdAccessor);

    @Test
    void tokenFileReadIsAudited() {
        when(requestIdAccessor.current()).thenReturn("req-1");
        when(skillQueryService.openFileContent("team", "demo", "1.0.0", "SKILL.md", "alice", Map.of()))
                .thenReturn(new SkillQueryService.SkillFileContent(5L, 9L, "1.0.0", new ByteArrayInputStream(new byte[0])));

        service.fileContent("team", "demo", "1.0.0", "SKILL.md", "alice", Map.of(),
                new AuditRequestContext("10.0.0.1", "mcp", 42L));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("alice"), eq("SKILL_FILE_READ"), eq("SKILL_VERSION"), eq(9L),
                eq("req-1"), eq("10.0.0.1"), eq("mcp"), detail.capture());
        assertThat(detail.getValue())
                .contains("\"skillId\":5")
                .contains("\"path\":\"SKILL.md\"")
                .contains("\"authMethod\":\"api_token\"")
                .contains("\"apiTokenId\":42");
    }

    @Test
    void tokenDownloadIsAuditedWithTheServedVersion() {
        when(skillDownloadService.downloadByTag("team", "demo", "latest", "alice", Map.of()))
                .thenReturn(new SkillDownloadService.DownloadResult(() -> new ByteArrayInputStream(new byte[0]),
                        "demo.zip", 0, "application/zip", null, false, 5L, 9L, "1.2.0"));

        service.downloadByTag("team", "demo", "latest", "alice", Map.of(), new AuditRequestContext("ip", "ua", 42L));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("alice"), eq("SKILL_DOWNLOAD"), eq("SKILL_VERSION"), eq(9L),
                any(), eq("ip"), eq("ua"), detail.capture());
        assertThat(detail.getValue()).contains("\"version\":\"1.2.0\"").contains("\"tag\":\"latest\"");
    }

    @Test
    void sessionAndAnonymousReadsAreNotAudited() {
        when(skillQueryService.openFileContent(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SkillQueryService.SkillFileContent(5L, 9L, "1.0.0", new ByteArrayInputStream(new byte[0])));
        when(skillDownloadService.downloadLatest(any(), any(), any(), any()))
                .thenReturn(new SkillDownloadService.DownloadResult(() -> new ByteArrayInputStream(new byte[0]),
                        "demo.zip", 0, "application/zip", null, false, 5L, 9L, "1.0.0"));

        service.fileContent("team", "demo", "1.0.0", "SKILL.md", "alice", Map.of(), new AuditRequestContext("ip", "browser"));
        service.downloadLatest("global", "demo", null, Map.of(), new AuditRequestContext("ip", "curl"));
        service.downloadLatest("global", "demo", null, Map.of(), null);

        verifyNoInteractions(auditLogService);
    }
}
