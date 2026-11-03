package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Shared package-policy rules for path normalization, extension allowlists, and lightweight file
 * signature validation.
 */
public final class SkillPackagePolicy {

    public static final int MAX_FILE_COUNT = 500;
    /** Scripts and source files. The security scanner fully parses these, so the cap stays small. */
    public static final long MAX_SCRIPT_FILE_SIZE = 2L * 1024 * 1024; // 2MB
    /** Images, documents, and other non-script package content, including SKILL.md. */
    public static final long MAX_DATA_FILE_SIZE = 100L * 1024 * 1024; // 100MB
    /**
     * Data/media ceiling. Prefer {@link #MAX_DATA_FILE_SIZE} or {@link #MAX_SCRIPT_FILE_SIZE}.
     * Kept so existing callers that treat this as the non-script single-file cap stay aligned.
     */
    public static final long MAX_SINGLE_FILE_SIZE = MAX_DATA_FILE_SIZE;
    public static final long MAX_TOTAL_PACKAGE_SIZE = 100L * 1024 * 1024; // 100MB
    /**
     * Entries larger than this are kept off the heap during publish. The upload setting may
     * override it; re-release uses this default.
     */
    public static final long MEMORY_ENTRY_THRESHOLD_BYTES = 1024L * 1024L; // 1MB
    public static final String SCRIPT_TOO_LARGE = "error.skill.publish.file.scriptTooLarge";
    public static final String DATA_TOO_LARGE = "error.skill.publish.file.dataTooLarge";
    public static final String SKILL_MD_PATH = "SKILL.md";
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // Documentation
            ".md", ".txt", ".json", ".yaml", ".yml", ".html", ".css", ".csv", ".pdf",
            // Configuration and schemas
            ".toml", ".xml", ".xsd", ".xsl", ".dtd", ".ini", ".cfg", ".env",
            // Scripts and source code
            ".js", ".cjs", ".mjs", ".ts", ".py", ".sh", ".rb", ".go", ".rs", ".java", ".kt",
            ".lua", ".sql", ".r", ".bat", ".ps1", ".zsh", ".bash",
            // Images
            ".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp", ".ico",
            // Office documents
            ".doc", ".xls", ".ppt", ".docx", ".xlsx", ".pptx"
    );

    /**
     * Extensions whose contents are executed or fully parsed (scanner, interpreters, compilers).
     * Classification is by the final extension only, so {@code SKILL.md} stays in the data tier.
     * Includes script suffixes already treated as text in this policy, plus the extra script-like
     * suffixes that must not inherit the larger data/media cap.
     */
    public static final Set<String> SCRIPT_EXTENSIONS = Set.of(
            ".py", ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
            ".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs",
            ".java", ".kt", ".go", ".rs", ".rb", ".php", ".lua", ".pl",
            ".sql", ".r"
    );

    private SkillPackagePolicy() {
    }

    /** True when the file's final extension is a script or source file. */
    public static boolean isScriptPath(String path) {
        return SCRIPT_EXTENSIONS.contains(extensionOf(path));
    }

    public static long limitFor(String path, long scriptLimit, long dataLimit) {
        return isScriptPath(path) ? scriptLimit : dataLimit;
    }

    public static String tooLargeMessageCode(String path) {
        return isScriptPath(path) ? SCRIPT_TOO_LARGE : DATA_TOO_LARGE;
    }

    public static DomainBadRequestException fileTooLarge(String path, long size, long limit) {
        return new DomainBadRequestException(
                tooLargeMessageCode(path),
                path,
                formatMegabytes(size),
                formatMegabytes(limit));
    }

    /**
     * Bilingual detail embedded in aggregated package-validation errors.
     * The upload path prefers {@link #fileTooLarge} so the locale files can translate it.
     */
    public static String tooLargeDetail(String path, long size, long limit) {
        String actual = formatMegabytes(size);
        String max = formatMegabytes(limit);
        if (isScriptPath(path)) {
            return "File too large: " + path + " (" + size + " bytes, max: " + limit + "). "
                    + "脚本或源码文件超过 " + max + " MB 上限（当前 " + actual + " MB）。"
                    + " / Script or source file exceeds the " + max + " MB limit.";
        }
        return "File too large: " + path + " (" + size + " bytes, max: " + limit + "). "
                + "文件超过该类文件 " + max + " MB 上限（当前 " + actual + " MB）。"
                + " / File exceeds the " + max + " MB limit for this file type.";
    }

    public static String formatMegabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0L, bytes) / (1024.0 * 1024.0));
    }

    static String extensionOf(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static String normalizeEntryPath(String rawPath) {
        if (rawPath == null) {
            throw new IllegalArgumentException("Package entry path is missing");
        }

        String sanitized = rawPath.replace('\\', '/').trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Package entry path is empty");
        }
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            throw new IllegalArgumentException("Package entry path must be relative: " + rawPath);
        }
        if (sanitized.contains(":")) {
            throw new IllegalArgumentException("Package entry path contains an invalid drive or scheme prefix: " + rawPath);
        }

        Path normalized = Paths.get(sanitized).normalize();
        String canonical = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || canonical.isBlank()) {
            throw new IllegalArgumentException("Package entry path is invalid: " + rawPath);
        }
        if (canonical.equals(".") || canonical.equals("..") || canonical.startsWith("../")) {
            throw new IllegalArgumentException("Package entry path escapes package root: " + rawPath);
        }
        if (!sanitized.equals(canonical)) {
            throw new IllegalArgumentException("Package entry path must be normalized: " + rawPath);
        }

        return canonicalizeSkillMdPath(canonical);
    }

    public static String canonicalizeSkillMdPath(String normalizedPath) {
        int slashIndex = normalizedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        if (!SKILL_MD_PATH.equalsIgnoreCase(fileName)) {
            return normalizedPath;
        }
        if (slashIndex < 0) {
            return SKILL_MD_PATH;
        }
        return normalizedPath.substring(0, slashIndex + 1) + SKILL_MD_PATH;
    }

    public static boolean hasAllowedExtension(String path) {
        return ALLOWED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    public static String validateContentMatchesExtension(String path, byte[] content) {
        return validateContentMatchesExtension(path, () -> new ByteArrayInputStream(content));
    }

    public static String validateContentMatchesExtension(String path, PackageEntry entry) {
        return validateContentMatchesExtension(path, entry::openStream);
    }

    private static String validateContentMatchesExtension(String path, PackageEntry.ContentSource source) {
        String lowerPath = path.toLowerCase();
        try {
            if (lowerPath.endsWith(".png")) {
                return matchesPrefix(source, 8, (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                        ? null
                        : mismatch(path);
            }
            if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                return matchesPrefix(source, 3, (byte) 0xff, (byte) 0xd8, (byte) 0xff) ? null : mismatch(path);
            }
            if (lowerPath.endsWith(".svg")) {
                try (InputStream input = source.open()) {
                    if (!isUtf8Text(input)) {
                        return mismatch(path);
                    }
                }
                try (InputStream input = source.open()) {
                    return containsSvgTag(input) ? null : mismatch(path);
                }
            }
            if (lowerPath.endsWith(".gif")) {
                return matchesPrefix(source, 4, 'G', 'I', 'F', '8') ? null : mismatch(path);
            }
            if (lowerPath.endsWith(".webp")) {
                try (InputStream input = source.open()) {
                    byte[] header = readAtMost(input, 12);
                    return header.length >= 12
                            && hasPrefix(header, 'R', 'I', 'F', 'F')
                            && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P'
                            ? null
                            : mismatch(path);
                }
            }
            if (lowerPath.endsWith(".ico")) {
                return matchesPrefix(source, 4, 0x00, 0x00, 0x01, 0x00) ? null : mismatch(path);
            }
            if (lowerPath.endsWith(".pdf")) {
                return matchesPrefix(source, 4, '%', 'P', 'D', 'F') ? null : mismatch(path);
            }
            if (isTextExtension(lowerPath)) {
                try (InputStream input = source.open()) {
                    return isUtf8Text(input) ? null : mismatch(path);
                }
            }
            return null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read package entry: " + path, exception);
        }
    }

    private static String mismatch(String path) {
        return "File content does not match extension: " + path;
    }

    private static boolean matchesPrefix(PackageEntry.ContentSource source, int headerLength, int... prefix)
            throws IOException {
        try (InputStream input = source.open()) {
            return hasPrefix(readAtMost(input, headerLength), prefix);
        }
    }

    private static boolean isTextExtension(String path) {
        return path.endsWith(".md") || path.endsWith(".txt")
                || path.endsWith(".json") || path.endsWith(".yaml") || path.endsWith(".yml")
                || path.endsWith(".js") || path.endsWith(".cjs") || path.endsWith(".mjs")
                || path.endsWith(".ts") || path.endsWith(".py") || path.endsWith(".sh")
                || path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".csv")
                || path.endsWith(".toml") || path.endsWith(".xml") || path.endsWith(".xsd")
                || path.endsWith(".xsl") || path.endsWith(".dtd") || path.endsWith(".ini")
                || path.endsWith(".cfg") || path.endsWith(".env")
                || path.endsWith(".rb") || path.endsWith(".go") || path.endsWith(".rs")
                || path.endsWith(".java") || path.endsWith(".kt") || path.endsWith(".lua")
                || path.endsWith(".sql") || path.endsWith(".r")
                || path.endsWith(".bat") || path.endsWith(".ps1")
                || path.endsWith(".zsh") || path.endsWith(".bash");
    }

    static boolean isUtf8Text(byte[] content) {
        try {
            return isUtf8Text(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * UTF-8 with the same rejection rules as decoding the whole file: any NUL byte fails, and a
     * malformed sequence fails. The decoder keeps only a fixed buffer.
     */
    private static boolean isUtf8Text(InputStream input) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer inputBuffer = ByteBuffer.allocate(8192);
        CharBuffer outputBuffer = CharBuffer.allocate(8192);
        byte[] readBuffer = new byte[4096];
        while (true) {
            int read = input.read(readBuffer);
            if (read > 0) {
                int offset = 0;
                while (offset < read) {
                    if (!inputBuffer.hasRemaining() && !drainUtf8(decoder, inputBuffer, outputBuffer, false)) {
                        return false;
                    }
                    int count = Math.min(inputBuffer.remaining(), read - offset);
                    for (int index = 0; index < count; index++) {
                        if (readBuffer[offset + index] == 0) {
                            return false;
                        }
                    }
                    inputBuffer.put(readBuffer, offset, count);
                    offset += count;
                }
            }
            boolean end = read < 0;
            if (!drainUtf8(decoder, inputBuffer, outputBuffer, end)) {
                return false;
            }
            if (end) {
                return true;
            }
        }
    }

    private static boolean drainUtf8(
            CharsetDecoder decoder,
            ByteBuffer inputBuffer,
            CharBuffer outputBuffer,
            boolean endOfInput
    ) {
        inputBuffer.flip();
        while (true) {
            CoderResult result = decoder.decode(inputBuffer, outputBuffer, endOfInput);
            outputBuffer.clear();
            if (result.isOverflow()) {
                continue;
            }
            if (result.isError()) {
                return false;
            }
            if (endOfInput) {
                CoderResult flush = decoder.flush(outputBuffer);
                outputBuffer.clear();
                if (flush.isError()) {
                    return false;
                }
                if (flush.isOverflow()) {
                    continue;
                }
            }
            inputBuffer.compact();
            return true;
        }
    }

    /**
     * Leading trim matches {@link String#trim()}: only characters {@code <= U+0020} are skipped.
     * The caller has already rejected invalid UTF-8 and NUL bytes.
     */
    private static boolean containsSvgTag(InputStream input) throws IOException {
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        char[] buffer = new char[8192];
        int matched = 0;
        boolean skippingLeading = true;
        String needle = "<svg";
        int read;
        while ((read = reader.read(buffer)) != -1) {
            for (int index = 0; index < read; index++) {
                char current = buffer[index];
                if (skippingLeading) {
                    if (current <= ' ') {
                        continue;
                    }
                    skippingLeading = false;
                }
                char lower = Character.toLowerCase(current);
                if (lower == needle.charAt(matched)) {
                    matched++;
                    if (matched == needle.length()) {
                        return true;
                    }
                } else {
                    matched = lower == '<' ? 1 : 0;
                }
            }
        }
        return false;
    }

    private static byte[] readAtMost(InputStream input, int max) throws IOException {
        byte[] buffer = new byte[max];
        int offset = 0;
        while (offset < max) {
            int read = input.read(buffer, offset, max - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        return offset == buffer.length ? buffer : Arrays.copyOf(buffer, offset);
    }

    private static boolean hasPrefix(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((content[index] & 0xff) != (prefix[index] & 0xff)) {
                return false;
            }
        }
        return true;
    }
}
