package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicPrePublishValidatorTest {

    private final BasicPrePublishValidator validator = new BasicPrePublishValidator();

    @Test
    void shouldWarnOnObviousCredentialLeakWithHelpfulLocation() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Secret Skill
                version: 1.0.0
                ---
                token=sk-abcdefghijklmnopqrstuvwxyz123456
                """.getBytes(StandardCharsets.UTF_8),
                91,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Secret Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(error ->
                error.contains("SKILL.md")
                        && error.contains("line 5")
                        && error.contains("looks like a")));
    }

    @Test
    void shouldAllowOrdinaryTextFiles() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Safe Skill
                version: 1.0.0
                ---
                """.getBytes(StandardCharsets.UTF_8),
                45,
                "text/markdown"
        );
        PackageEntry readme = new PackageEntry(
                "README.md",
                "This skill documents safe usage.".getBytes(StandardCharsets.UTF_8),
                31,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd, readme),
                new SkillMetadata("Safe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldIgnoreObviousPlaceholderSecrets() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                token=YOUR_TOKEN_HERE
                api_key=example-key-value
                """.getBytes(StandardCharsets.UTF_8),
                102,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldNotWarnOnRuntimeExpressionsAssignedToSensitiveVariables() {
        PackageEntry script = new PackageEntry(
                "scripts/oauth.py",
                """
                refresh_token = token_response.get("refresh_token")
                client_secret = configured_secret
                self._client_secret = credentials.client_secret
                access_token = ensure_valid_access_token(session)
                headers = build_headers(access_token=access_token)
                client_secret = "prefix-" + configured_secret
                access_token = token_v2
                access_token = configuredToken123
                refresh_token = foo123bar456
                token = ("static-prefix-") + configuredToken
                access_token = ("static_prefix_") + configured_token
                """.getBytes(StandardCharsets.UTF_8),
                476,
                "text/x-python"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("OAuth Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldKeepWarningOnHardcodedAndProviderSpecificCredentials() {
        PackageEntry script = new PackageEntry(
                "scripts/leaked.js",
                """
                client_secret = "literalcredential123"
                github_token = "ghp_abcdefghijklmnopqrstuvwxyz1234"
                const token = "javascriptcredential123";
                const config = { token: "objectcredential123", };
                const options = { token: "multipropertycredential123", endpoint: "/api" };
                const escaped = { token: "credential\\\"value123" };
                const emptyFirst = { token: "", password: "passwordafterempty123" };
                const dynamicFirst = { token: configuredToken, password: "passwordafterdynamic123" };
                token=("wrappedcredential123");
                token="assertedcredential123" as const;
                """.getBytes(StandardCharsets.UTF_8),
                568,
                "text/javascript"
        );
        PackageEntry configuration = new PackageEntry(
                "config/settings.env",
                "token=barecredential123 // leaked\n".getBytes(StandardCharsets.UTF_8),
                35,
                "text/plain"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script, configuration),
                new SkillMetadata("Unsafe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 1")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 2")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 3")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 4")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 5")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 6")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 7")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 8")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 9")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 10")));
        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.contains("config/settings.env line 1")));
    }

    @Test
    void shouldNotWarnOnEmptyOrShortSensitiveLiterals() {
        PackageEntry script = new PackageEntry(
                "scripts/defaults.py",
                """
                token = ""
                client_secret = "short"
                password = 'unset'
                """.getBytes(StandardCharsets.UTF_8),
                58,
                "text/x-python"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("Defaults Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldScanDeeplyNestedSingleLineObjectWithoutOverflowingRegexStack() {
        String content = "const config = "
                + "{ nested: ".repeat(5_000)
                + "{ token: \"literalcredential123\""
                + " }".repeat(5_001)
                + ";";
        PackageEntry script = new PackageEntry(
                "scripts/deeply-nested.js",
                content.getBytes(StandardCharsets.UTF_8),
                content.length(),
                "text/javascript"
        );
        PrePublishValidator.SkillPackageContext context = new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("Deeply Nested Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        );

        ValidationResult result = assertDoesNotThrow(() -> validator.validate(context));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("line 1")));
    }

    @Test
    void streamingScanMatchesMaterializedLineSplit() throws Exception {
        String nested = "intro\r\npassword = \"example-placeholder\"\n"
                + "token=sk-abcdefghijklmnopqrstuvwxyz123456\r"
                + "api_key: your-real-looking-value\u2028"
                + "ghp_abcdefghijklmnopqrstuvwxyz\n";
        assertSameWarnings("notes.txt", nested.getBytes(StandardCharsets.UTF_8));

        StringBuilder longLine = new StringBuilder("preface\n");
        longLine.append("a".repeat(70_000));
        longLine.append("ghp_abcdefghijklmnopqrstuvwxyz");
        longLine.append("b".repeat(2_000));
        longLine.append('\n');
        longLine.append("tail");
        assertSameWarnings("notes.txt", longLine.toString().getBytes(StandardCharsets.UTF_8));

        assertSameWarnings("notes.txt", ("a".repeat(80_000) + "\nsafe\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void spilledAndInMemoryEntriesProduceTheSameCredentialWarnings() throws Exception {
        String text = "line 1\ntoken=sk-abcdefghijklmnopqrstuvwxyz123456\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        PackageEntry memory = new PackageEntry("README.md", bytes, bytes.length, "text/markdown");
        Path file = Files.createTempFile("skillhive-credential-", ".txt");
        try {
            Files.write(file, bytes);
            try (PackageEntry spilled = PackageEntry.spilled("README.md", bytes.length, "text/markdown", file)) {
                assertEquals(warningsOf(memory), warningsOf(spilled));
                assertTrue(warningsOf(spilled).get(0).contains("line 2"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private void assertSameWarnings(String path, byte[] bytes) {
        List<String> expected = new ArrayList<>();
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            validator.inspectLine(path, index + 1, lines[index], expected);
        }
        PackageEntry entry = new PackageEntry(path, bytes, bytes.length, "text/plain");
        assertEquals(expected, warningsOf(entry));
    }

    private List<String> warningsOf(PackageEntry entry) {
        return validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(entry),
                new SkillMetadata("Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        )).warnings();
    }
}
