package com.iflytek.skillhub.domain.knowledge;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * File rules for knowledge uploads: an extension allow-list, a size ceiling, and the
 * server-side content type and preview mode for each extension. The client-supplied
 * content type is never trusted.
 */
@Component
public class KnowledgeFilePolicy {

    public static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    static final int MAX_FILENAME_LENGTH = 256;
    static final int MAX_TITLE_LENGTH = 256;
    static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final int MAX_CHANGE_NOTE_LENGTH = 512;
    static final int MAX_FOLDER_NAME_LENGTH = 128;

    private static final Pattern FORBIDDEN_NAME_CHARS = Pattern.compile("[\\\\/\\p{Cntrl}]");

    private record FileType(String contentType, KnowledgePreviewKind previewKind) {
    }

    private static final Map<String, FileType> FILE_TYPES = Map.ofEntries(
            Map.entry("pdf", new FileType("application/pdf", KnowledgePreviewKind.PDF)),
            Map.entry("png", new FileType("image/png", KnowledgePreviewKind.IMAGE)),
            Map.entry("jpg", new FileType("image/jpeg", KnowledgePreviewKind.IMAGE)),
            Map.entry("jpeg", new FileType("image/jpeg", KnowledgePreviewKind.IMAGE)),
            Map.entry("gif", new FileType("image/gif", KnowledgePreviewKind.IMAGE)),
            Map.entry("webp", new FileType("image/webp", KnowledgePreviewKind.IMAGE)),
            Map.entry("md", new FileType("text/markdown", KnowledgePreviewKind.MARKDOWN)),
            Map.entry("markdown", new FileType("text/markdown", KnowledgePreviewKind.MARKDOWN)),
            Map.entry("txt", new FileType("text/plain", KnowledgePreviewKind.TEXT)),
            Map.entry("csv", new FileType("text/csv", KnowledgePreviewKind.NONE)),
            Map.entry("json", new FileType("application/json", KnowledgePreviewKind.NONE)),
            Map.entry("doc", new FileType("application/msword", KnowledgePreviewKind.OFFICE)),
            Map.entry("docx", new FileType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    KnowledgePreviewKind.OFFICE)),
            Map.entry("xls", new FileType("application/vnd.ms-excel", KnowledgePreviewKind.NONE)),
            Map.entry("xlsx", new FileType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    KnowledgePreviewKind.NONE)),
            Map.entry("ppt", new FileType("application/vnd.ms-powerpoint", KnowledgePreviewKind.OFFICE)),
            Map.entry("pptx", new FileType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    KnowledgePreviewKind.OFFICE)),
            Map.entry("zip", new FileType("application/zip", KnowledgePreviewKind.NONE)),
            Map.entry("rar", new FileType("application/vnd.rar", KnowledgePreviewKind.NONE)),
            Map.entry("7z", new FileType("application/x-7z-compressed", KnowledgePreviewKind.NONE))
    );

    /** Validates an upload and returns its normalized (lower-case) extension. */
    public String validateUpload(String filename, long sizeBytes) {
        String name = validateFilename(filename);
        if (sizeBytes <= 0) {
            throw new DomainBadRequestException("error.knowledge.file.empty");
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new DomainBadRequestException("error.knowledge.file.tooLarge", MAX_FILE_SIZE_BYTES / (1024 * 1024));
        }
        String extension = extensionOf(name);
        if (extension.isEmpty() || !FILE_TYPES.containsKey(extension)) {
            throw new DomainBadRequestException("error.knowledge.file.typeNotAllowed", extension.isEmpty() ? name : extension);
        }
        return extension;
    }

    public String validateFilename(String filename) {
        String name = filename == null ? "" : filename.strip();
        if (name.isEmpty()) {
            throw new DomainBadRequestException("error.knowledge.file.nameRequired");
        }
        if (name.length() > MAX_FILENAME_LENGTH || FORBIDDEN_NAME_CHARS.matcher(name).find()
                || name.equals(".") || name.equals("..")) {
            throw new DomainBadRequestException("error.knowledge.file.nameInvalid");
        }
        return name;
    }

    public String contentTypeFor(String extension) {
        FileType type = FILE_TYPES.get(normalizeExtension(extension));
        return type == null ? "application/octet-stream" : type.contentType();
    }

    public KnowledgePreviewKind previewKindFor(String extension) {
        FileType type = FILE_TYPES.get(normalizeExtension(extension));
        return type == null ? KnowledgePreviewKind.NONE : type.previewKind();
    }

    public String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return normalizeExtension(filename.substring(dot + 1));
    }

    /** Title defaults to the file name without its extension. */
    public String normalizeTitle(String title, String filename) {
        String value = title == null || title.isBlank() ? stripExtension(filename) : title.strip();
        if (value.isEmpty()) {
            throw new DomainBadRequestException("error.knowledge.document.titleRequired");
        }
        if (value.length() > MAX_TITLE_LENGTH) {
            throw new DomainBadRequestException("error.knowledge.document.titleTooLong", MAX_TITLE_LENGTH);
        }
        return value;
    }

    public String normalizeDescription(String description) {
        return normalizeOptional(description, MAX_DESCRIPTION_LENGTH, "error.knowledge.document.descriptionTooLong");
    }

    public String normalizeChangeNote(String changeNote) {
        return normalizeOptional(changeNote, MAX_CHANGE_NOTE_LENGTH, "error.knowledge.version.changeNoteTooLong");
    }

    public String normalizeFolderName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty()) {
            throw new DomainBadRequestException("error.knowledge.folder.nameRequired");
        }
        if (value.length() > MAX_FOLDER_NAME_LENGTH || FORBIDDEN_NAME_CHARS.matcher(value).find()) {
            throw new DomainBadRequestException("error.knowledge.folder.nameInvalid");
        }
        return value;
    }

    private String normalizeOptional(String value, int maxLength, String tooLongCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new DomainBadRequestException(tooLongCode, maxLength);
        }
        return stripped;
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.strip();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot).strip() : name;
    }

    private String normalizeExtension(String extension) {
        return extension == null ? "" : extension.strip().toLowerCase(Locale.ROOT);
    }
}
