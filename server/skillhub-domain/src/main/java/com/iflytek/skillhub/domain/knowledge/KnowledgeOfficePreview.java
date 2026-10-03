package com.iflytek.skillhub.domain.knowledge;

import java.util.List;

/** A bounded, derived preview of an immutable published file version. */
public record KnowledgeOfficePreview(Status status, String kind, int pageCount, int pageLimit,
                                     List<PreviewSheet> sheets, int sheetLimit, int rowLimit, int columnLimit) {
    public enum Status { PROCESSING, READY, FAILED, UNAVAILABLE }

    public KnowledgeOfficePreview {
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
    }

    public static KnowledgeOfficePreview unavailable() {
        return new KnowledgeOfficePreview(Status.UNAVAILABLE, null, 0, 5, List.of(), 3, 100, 20);
    }

    public record PreviewSheet(String name, List<List<PreviewCell>> rows, List<PreviewMerge> merges, boolean truncated) {}
    public record PreviewCell(String text, boolean bold, String align) {}
    public record PreviewMerge(int row, int column, int rowSpan, int columnSpan) {}
}
