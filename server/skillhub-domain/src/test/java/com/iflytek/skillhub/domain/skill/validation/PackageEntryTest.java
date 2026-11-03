package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageEntryTest {

    @Test
    void streamingContentIsOpenedLazilyAndCanBeReopened() throws Exception {
        byte[] bytes = "streamed content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger opens = new AtomicInteger();
        PackageEntry entry = PackageEntry.streaming(
                "README.md", bytes.length, "text/markdown", () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(bytes);
                });

        assertThat(opens).hasValue(0);
        assertThat(entry.content()).isEqualTo(bytes);
        try (var input = entry.openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(opens).hasValue(2);
    }

    @Test
    void materializationRejectsContentWhoseSizeChanged() {
        PackageEntry entry = PackageEntry.streaming(
                "README.md", 3, "text/markdown",
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertThatThrownBy(entry::content)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size changed");
    }

    @Test
    void spilledEntryRoundTripsAndDeletesItsFileOnce() throws Exception {
        Path file = Files.createTempFile("skillhive-entry-", ".bin");
        Files.writeString(file, "spilled");
        PackageEntry entry = PackageEntry.spilled("dir/notes.txt", 7, "text/plain", file);
        PackageEntry moved = entry.withPath("notes.txt");
        assertThat(entry.spilled()).isTrue();
        assertThat(entry.retainsBytes()).isFalse();
        assertThat(moved.content()).isEqualTo("spilled".getBytes(StandardCharsets.UTF_8));
        entry.close();
        assertThat(Files.exists(file)).isTrue();
        moved.close();
        assertThat(Files.exists(file)).isFalse();
        moved.close();
        assertThat(Files.exists(file)).isFalse();
    }
}
