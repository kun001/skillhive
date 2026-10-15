package com.iflytek.skillhub.domain.knowledge;

import java.util.List;

/** Port for an optional external summarizer. The text it receives is untrusted file content. */
public interface KnowledgeSummaryGenerator {

    record Summary(String summary, List<String> keywords) {
    }

    Summary generate(String title, String markdown) throws Exception;
}
