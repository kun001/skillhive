package com.iflytek.skillhub.domain.knowledge;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validates logical upload paths and image metadata; does not parse Markdown content. */
@Component
public class KnowledgeMarkdownPolicy {
    public static final int MAX_IMAGES = 100;
    private final KnowledgeFilePolicy filePolicy;

    public KnowledgeMarkdownPolicy(KnowledgeFilePolicy filePolicy) { this.filePolicy = filePolicy; }

    public record Image(String path, String filename, long sizeBytes) {}

    public String validate(String filename, long sizeBytes, String sourcePath, List<Image> images) {
        String path = validatePath(sourcePath == null ? filename : sourcePath);
        if (!basename(path).equals(filename)) throw invalidPath();
        if (!images.isEmpty() && filePolicy.previewKindFor(filePolicy.extensionOf(filename)) != KnowledgePreviewKind.MARKDOWN) {
            throw new DomainBadRequestException("error.knowledge.markdown.required");
        }
        if (images.size() > MAX_IMAGES) throw new DomainBadRequestException("error.knowledge.markdown.tooManyImages", MAX_IMAGES);
        var paths = new HashSet<String>();
        long total = sizeBytes;
        for (Image image : images) {
            String imagePath = validatePath(image.path());
            if (!basename(imagePath).equals(image.filename()) || !paths.add(imagePath)) throw invalidPath();
            String extension = filePolicy.validateUpload(image.filename(), image.sizeBytes());
            if (filePolicy.previewKindFor(extension) != KnowledgePreviewKind.IMAGE) {
                throw new DomainBadRequestException("error.knowledge.markdown.imageRequired", image.filename());
            }
            total += image.sizeBytes();
            if (total > KnowledgeFilePolicy.MAX_FILE_SIZE_BYTES) {
                throw new DomainBadRequestException("error.knowledge.markdown.tooLarge");
            }
        }
        return path;
    }

    public String validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1024 || path.startsWith("/")
                || path.contains("\\") || path.contains(":") || path.chars().anyMatch(Character::isISOControl)) throw invalidPath();
        for (String part : path.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) throw invalidPath();
        }
        return path;
    }

    private static String basename(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    private static DomainBadRequestException invalidPath() {
        return new DomainBadRequestException("error.knowledge.markdown.pathInvalid");
    }
}
