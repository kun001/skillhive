package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackageExtractorTest {

    @Test
    void extractCanonicalizesCaseInsensitiveSkillMd() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        byte[] zipBytes = createZip(Map.of(
                "skill.md", "---\nname: test\n---\n".getBytes(),
                "README.md", "# readme".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(entries.stream().noneMatch(e -> e.path().equals("skill.md")));
    }

    @Test
    void skipsWindowsStyleDirectoryEntries() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("my-skill\\"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("my-skill/SKILL.md"));
            zos.write("---\nname: test\n---\n".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", baos.toByteArray());

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(1, entries.size());
        assertEquals("SKILL.md", entries.get(0).path());
    }

    @Test
    void acceptsPackageWithDefaultMaxFileCountOf500() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", createZip(packageWithFileCount(500)));

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(500, entries.size());
    }

    @Test
    void rejectsPackageAboveDefaultMaxFileCountOf500() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", createZip(packageWithFileCount(501)));

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class, () -> extractor.extract(file));

        assertEquals("error.skill.publish.package.invalid", error.messageCode());
        assertEquals("Too many files: max 500", error.messageArgs()[0]);
    }

    private Map<String, byte[]> packageWithFileCount(int fileCount) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", "---\nname: test\n---\n".getBytes());
        for (int i = 1; i < fileCount; i++) {
            entries.put("docs/file-" + i + ".md", ("# file " + i).getBytes());
        }
        return entries;
    }

    private byte[] createZip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
