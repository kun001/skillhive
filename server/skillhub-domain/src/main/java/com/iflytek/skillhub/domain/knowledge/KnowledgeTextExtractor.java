package com.iflytek.skillhub.domain.knowledge;

import java.io.InputStream;

/** Port that turns a stored file into Markdown. Input bytes are untrusted. */
public interface KnowledgeTextExtractor {

    /**
     * @param markdown extracted text, or {@code null} when the format or file is not supported
     * @param extractor short identifier of the method used, stored with the text
     * @param truncated whether text beyond the configured limit was dropped
     */
    record Result(String markdown, String extractor, boolean truncated) {
        public static Result unsupported(String extractor) {
            return new Result(null, extractor, false);
        }

        public boolean supported() {
            return markdown != null;
        }
    }

    /** Whether files with this lower-case extension are attempted at all. */
    boolean supports(String extension);

    Result extract(String extension, InputStream content, int maxChars) throws Exception;
}
