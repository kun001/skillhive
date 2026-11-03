package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.storage.StorageQuotaExceededException;
import com.iflytek.skillhub.domain.storage.StorageQuotaGuard;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SkillPublishAdmissionTest {

    @Mock
    private StorageQuotaGuard storageQuotaGuard;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;

    private SkillPublishAdmission admission;

    @BeforeEach
    void setUp() {
        admission = new SkillPublishAdmission(
                storageQuotaGuard,
                new PublishExtractionBudget(0),
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                new SkillMetadataParser(),
                new SkillPublishProperties());
    }

    @Test
    void extractsWhenTheDeclaredSizeFitsTheRemainingQuota() throws Exception {
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 0L)).thenReturn(1024L);
        MockMultipartFile file = zipFile(skillMd("demo-skill", "1.0.0"));
        AtomicBoolean extracted = new AtomicBoolean();

        String result = admission.extract("team", "alice", file, (upload, budget) -> {
            extracted.set(true);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(extracted).isTrue();
        verify(storageQuotaGuard, never()).assertCanStore(anyString(), anyLong(), anyLong());
    }

    @Test
    void rejectsBeforeExtractWhenTheDeclaredSizeExceedsTheRemainingQuota() throws Exception {
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 0L)).thenReturn(10L);
        doThrow(new StorageQuotaExceededException(100, 90, 80))
                .when(storageQuotaGuard).assertCanStore(eq("alice"), longThat(size -> size > 10), eq(0L));
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", new byte[80]);
        AtomicBoolean extracted = new AtomicBoolean();

        assertThatThrownBy(() -> admission.extract("team", "alice", file, (upload, budget) -> {
            extracted.set(true);
            return "ok";
        })).isInstanceOf(StorageQuotaExceededException.class);

        assertThat(extracted).isFalse();
    }

    @Test
    void allowsExtractWhenReplacingAnUnpublishedVersionFreesEnoughQuota() throws Exception {
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 0L)).thenReturn(10L);
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 80L)).thenReturn(Long.MAX_VALUE);
        Namespace namespace = new Namespace("team", "Team", "alice");
        ReflectionTestUtils.setField(namespace, "id", 7L);
        Skill skill = new Skill(7L, "demo-skill", "alice", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(skill, "id", 3L);
        SkillVersion version = new SkillVersion(3L, "1.0.0", "alice");
        version.setStatus(SkillVersionStatus.UPLOADED);
        version.setTotalSize(80L);
        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(7L, "demo-skill", "alice")).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(3L, "1.0.0")).thenReturn(Optional.of(version));

        MockMultipartFile file = zipFile(skillMd("demo-skill", "1.0.0"));
        AtomicBoolean extracted = new AtomicBoolean();

        admission.extract("team", "alice", file, (upload, budget) -> {
            extracted.set(true);
            return "ok";
        });

        assertThat(extracted).isTrue();
        verify(storageQuotaGuard, never()).assertCanStore(anyString(), anyLong(), anyLong());
    }

    @Test
    void doesNotTreatAPublishedVersionAsReclaimable() throws Exception {
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 0L)).thenReturn(10L);
        doThrow(new StorageQuotaExceededException(100, 90, 80))
                .when(storageQuotaGuard).assertCanStore(eq("alice"), longThat(size -> size > 10), eq(0L));
        Namespace namespace = new Namespace("team", "Team", "alice");
        ReflectionTestUtils.setField(namespace, "id", 7L);
        Skill skill = new Skill(7L, "demo-skill", "alice", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(skill, "id", 3L);
        SkillVersion version = new SkillVersion(3L, "1.0.0", "alice");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setTotalSize(10_000L);
        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(7L, "demo-skill", "alice")).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(3L, "1.0.0")).thenReturn(Optional.of(version));
        MockMultipartFile file = zipFile(skillMd("demo-skill", "1.0.0"));
        AtomicBoolean extracted = new AtomicBoolean();

        assertThatThrownBy(() -> admission.extract("team", "alice", file, (upload, budget) -> {
            extracted.set(true);
            return "ok";
        })).isInstanceOf(StorageQuotaExceededException.class);

        assertThat(extracted).isFalse();
    }

    @Test
    void rejectsDuringExtractWhenUncompressedEntriesExceedTheRemainingQuota() throws Exception {
        when(storageQuotaGuard.acceptedIncomingBytes("alice", 0L)).thenReturn(1_000L);
        doThrow(new StorageQuotaExceededException(1000, 0, 5_000))
                .when(storageQuotaGuard).assertCanStore(eq("alice"), eq(5_000L), eq(0L));
        MockMultipartFile file = zipFile(skillMd("demo-skill", "1.0.0"));

        assertThatThrownBy(() -> admission.extract("team", "alice", file, (upload, budget) -> {
            budget.ensureWithinQuota(5_000L);
            return "ok";
        })).isInstanceOf(StorageQuotaExceededException.class);
    }

    private static String skillMd(String name, String version) {
        return "---\nname: " + name + "\ndescription: demo\nversion: " + version + "\n---\nBody\n";
    }

    private static MockMultipartFile zipFile(String skillMd) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write(skillMd.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new MockMultipartFile("file", "skill.zip", "application/zip", baos.toByteArray());
    }
}
