package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.MemberResourcePolicy;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillShare;
import com.iflytek.skillhub.domain.skill.SkillShareRepository;
import com.iflytek.skillhub.domain.skill.SkillShareVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillShareAppServiceTest {
    @Test
    void aPendingUploadKeepsThePreviousReleaseSharedUntilPublication() {
        SkillShareRepository shares = mock(SkillShareRepository.class);
        SkillRepository skills = mock(SkillRepository.class);
        NamespaceRepository namespaces = mock(NamespaceRepository.class);
        SkillVersionRepository versions = mock(SkillVersionRepository.class);
        SkillQueryService skillQuery = mock(SkillQueryService.class);
        SkillDownloadService downloads = mock(SkillDownloadService.class);
        SkillShareAppService service = new SkillShareAppService(shares, skills,
                new SkillLifecycleProjectionService(versions), namespaces, skillQuery, downloads,
                mock(AuditLogService.class), mock(RequestIdAccessor.class), Clock.systemUTC(),
                mock(MemberResourcePolicy.class), new VisibilityChecker(namespaces));

        Skill skill = new Skill(4L, "shared-skill", "owner", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(skill, "id", 12L);
        SkillVersion published = version(20L, "1.0.0", SkillVersionStatus.PUBLISHED);
        SkillVersion uploaded = version(21L, "1.1.0", SkillVersionStatus.UPLOADED);
        skill.setLatestVersionId(21L);
        SkillShare link = new SkillShare(12L);

        when(shares.findByShareId(link.getShareId())).thenReturn(Optional.of(link));
        when(skills.findById(12L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(4L)).thenReturn(Optional.of(new Namespace("team", "Team", "owner")));
        when(versions.findById(21L)).thenReturn(Optional.of(uploaded));
        when(versions.findBySkillIdAndStatus(12L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(published));

        String token = link.getShareId().toString();
        var guest = new SkillShareAppService.Caller(null, Map.of(), Set.of());
        assertEquals("1.0.0", service.detail(token, guest).version());
        assertThrows(DomainNotFoundException.class, () -> service.documentation(token, "1.1.0"));
        service.documentation(token, "1.0.0");
        service.download(token);
        verify(skillQuery).openSharedDocumentation(published);
        verify(downloads).downloadSharedVersion(skill, published);

        uploaded.setStatus(SkillVersionStatus.PUBLISHED);
        assertEquals("1.1.0", service.detail(token, guest).version());
        assertThrows(DomainNotFoundException.class, () -> service.documentation(token, "1.0.0"));
        service.documentation(token, "1.1.0");
        service.download(token);
        verify(skillQuery).openSharedDocumentation(uploaded);
        verify(downloads).downloadSharedVersion(skill, uploaded);

        link.setVisibility(SkillShareVisibility.ENTRY);
        assertNotEquals(token, ReflectionTestUtils.getField(link, "shareId").toString());
        when(shares.findByShareId(link.getShareId())).thenReturn(Optional.empty());
        when(shares.findByEntryShareId(link.getShareId())).thenReturn(Optional.of(link));
        var entry = service.detail(token, guest);
        assertEquals(SkillShareVisibility.ENTRY, entry.visibility());
        assertNull(entry.summary());
        assertNull(entry.version());
        assertNull(entry.memberWorkspace());
        assertThrows(DomainNotFoundException.class, () -> service.documentation(token, "1.1.0"));
        assertThrows(DomainNotFoundException.class, () -> service.download(token));
    }

    private static SkillVersion version(Long id, String version, SkillVersionStatus status) {
        SkillVersion skillVersion = new SkillVersion(12L, version, "owner");
        ReflectionTestUtils.setField(skillVersion, "id", id);
        skillVersion.setStatus(status);
        skillVersion.setDownloadReady(true);
        return skillVersion;
    }
}
