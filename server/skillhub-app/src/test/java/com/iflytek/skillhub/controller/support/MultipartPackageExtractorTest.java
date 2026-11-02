package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartPackageExtractorTest {

    @Test
    void extractCanonicalizesCaseInsensitiveSkillMd() throws Exception {
        MultipartPackageExtractor extractor = new MultipartPackageExtractor(
                new SkillPublishProperties(),
                new ObjectMapper()
        );
        MockMultipartFile skillMd = new MockMultipartFile(
                "files",
                "skill.md",
                "text/markdown",
                "---\nname: test\n---\n".getBytes()
        );

        MultipartPackageExtractor.ExtractedPackage extracted = extractor.extract(
                new MockMultipartFile[] {skillMd},
                "{\"namespace\":\"global\",\"slug\":\"test\"}"
        );

        assertEquals(1, extracted.entries().size());
        assertTrue(extracted.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(extracted.entries().stream().noneMatch(e -> e.path().equals("skill.md")));
    }

    @Test
    void singleFileLimitIsInclusiveAndOneByteMoreIsRejected() throws Exception {
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxSingleFileSize(1024);
        properties.setMaxPackageSize(4096);
        MultipartPackageExtractor extractor = new MultipartPackageExtractor(properties, new ObjectMapper());
        String payload = "{\"namespace\":\"global\",\"slug\":\"test\"}";
        MockMultipartFile skillMd = new MockMultipartFile("files", "SKILL.md", "text/markdown", "---\nname: test\n---\n".getBytes());

        MultipartPackageExtractor.ExtractedPackage extracted = extractor.extract(
                new MockMultipartFile[] {skillMd, new MockMultipartFile("files", "data.txt", "text/plain", new byte[1024])},
                payload);
        assertEquals(2, extracted.entries().size());

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class, () -> extractor.extract(
                new MockMultipartFile[] {skillMd, new MockMultipartFile("files", "data.txt", "text/plain", new byte[1025])},
                payload));
        assertTrue(String.valueOf(error.messageArgs()[0]).contains("File too large"), String.valueOf(error.messageArgs()[0]));
    }
}
