package com.iflytek.skillhub.infra.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeTextExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Simple Markdown extraction with Apache POI and PDFBox. Structure is kept only where it is
 * cheap and reliable: Word headings, lists and tables, one section per slide, sheet or PDF page.
 * Legacy binary Office formats, images, archives and PDFs without a text layer are unsupported.
 * Macros and scripts are never run; POI's zip-bomb guards stay on.
 */
@Component
public class PoiPdfKnowledgeTextExtractor implements KnowledgeTextExtractor {
    static final String VERSION = "1";
    private static final Set<String> PLAIN = Set.of("md", "markdown", "mdx", "txt", "csv", "json");
    private static final Set<String> OFFICE = Set.of("docx", "pptx", "xlsx");
    private static final int MAX_SHEET_ROWS = 500;
    private static final int MAX_SHEET_COLUMNS = 30;
    private static final int MIN_PDF_CHARS_PER_PAGE = 10;

    static {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(256L * 1024 * 1024);
    }

    @Override
    public boolean supports(String extension) {
        String value = normalize(extension);
        return PLAIN.contains(value) || OFFICE.contains(value) || "pdf".equals(value);
    }

    @Override
    public Result extract(String extension, InputStream content, int maxChars) throws Exception {
        String value = normalize(extension);
        Out out = new Out(maxChars);
        String extractor;
        switch (value) {
            case "docx" -> { docx(content, out); extractor = "poi-docx"; }
            case "pptx" -> { pptx(content, out); extractor = "poi-pptx"; }
            case "xlsx" -> { xlsx(content, out); extractor = "poi-xlsx"; }
            case "pdf" -> {
                extractor = "pdfbox";
                if (!pdf(content, out)) {
                    return Result.unsupported(extractor + "/" + VERSION);
                }
            }
            default -> {
                if (!PLAIN.contains(value)) {
                    return Result.unsupported("none");
                }
                plain(content, out);
                extractor = "plain";
            }
        }
        String markdown = out.markdown();
        return markdown.isBlank() ? Result.unsupported(extractor + "/" + VERSION)
                : new Result(markdown, extractor + "/" + VERSION, out.truncated());
    }

    // ---------------------------------------------------------------- plain text

    private void plain(InputStream content, Out out) throws IOException {
        // Read a little beyond the limit; the rest of a huge file is never loaded.
        int limit = (int) Math.min(Integer.MAX_VALUE - 8L, (long) out.maxChars * 4 + 4);
        out.raw(decode(content.readNBytes(limit)));
    }

    /** UTF-8 first (BOM stripped); GB18030 covers legacy Chinese text files. */
    static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
        } catch (CharacterCodingException ignored) {
            // A UTF-8 file cut at the read limit can end mid-character; prefer lenient UTF-8 then.
            String lenient = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
            long replaced = lenient.chars().filter(c -> c == '�').count();
            return replaced <= 2 ? lenient : new String(bytes, offset, bytes.length - offset, Charset.forName("GB18030"));
        }
    }

    // ---------------------------------------------------------------- Word

    private void docx(InputStream content, Out out) throws IOException {
        try (XWPFDocument document = new XWPFDocument(content)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (out.full()) {
                    break;
                }
                if (element instanceof XWPFParagraph paragraph) {
                    paragraph(document, paragraph, out);
                } else if (element instanceof XWPFTable table) {
                    List<List<String>> rows = new ArrayList<>();
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText());
                        }
                        rows.add(cells);
                    }
                    out.table(rows);
                }
            }
        }
    }

    private void paragraph(XWPFDocument document, XWPFParagraph paragraph, Out out) {
        String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        int level = headingLevel(document, paragraph);
        if (level > 0) {
            out.block("#".repeat(level) + " " + oneLine(text));
        } else if (paragraph.getNumID() != null) {
            int indent = paragraph.getNumIlvl() == null ? 0 : Math.min(paragraph.getNumIlvl().intValue(), 6);
            out.line("  ".repeat(indent) + "- " + oneLine(text));
        } else {
            out.block(text);
        }
    }

    /** Built-in style names stay English ("heading 1", "Title") even in localized Word. */
    static int headingLevel(XWPFDocument document, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return 0;
        }
        XWPFStyle style = document.getStyles() == null ? null : document.getStyles().getStyle(styleId);
        String name = (style != null && style.getName() != null ? style.getName() : styleId).toLowerCase(Locale.ROOT).replace(" ", "");
        if (name.equals("title")) {
            return 1;
        }
        if (name.startsWith("heading") && name.length() == "heading".length() + 1 && Character.isDigit(name.charAt(name.length() - 1))) {
            return Math.min(Math.max(name.charAt(name.length() - 1) - '0', 1), 6);
        }
        return 0;
    }

    // ---------------------------------------------------------------- PowerPoint

    private void pptx(InputStream content, Out out) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(content)) {
            int number = 0;
            for (XSLFSlide slide : slideShow.getSlides()) {
                if (out.full()) {
                    break;
                }
                number++;
                String title = slide.getTitle() == null ? "" : oneLine(slide.getTitle());
                out.block("## Slide " + number + (title.isEmpty() ? "" : ": " + title));
                for (XSLFShape shape : slide.getShapes()) {
                    slideShape(shape, title, out);
                }
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape text && text.getPlaceholder() == Placeholder.BODY
                                && text.getText() != null && !text.getText().isBlank()) {
                            out.block("> " + oneLine(text.getText()));
                        }
                    }
                }
            }
        }
    }

    private void slideShape(XSLFShape shape, String title, Out out) {
        if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                slideShape(child, title, out);
            }
        } else if (shape instanceof XSLFTable table) {
            List<List<String>> rows = new ArrayList<>();
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) {
                    cells.add(cell.getText());
                }
                rows.add(cells);
            }
            out.table(rows);
        } else if (shape instanceof XSLFTextShape text) {
            Placeholder placeholder = text.getPlaceholder();
            if (placeholder == Placeholder.TITLE || placeholder == Placeholder.CENTERED_TITLE
                    || placeholder == Placeholder.SLIDE_NUMBER || placeholder == Placeholder.DATETIME
                    || placeholder == Placeholder.FOOTER) {
                return;
            }
            for (XSLFTextParagraph paragraph : text.getTextParagraphs()) {
                String value = paragraph.getText() == null ? "" : oneLine(paragraph.getText());
                if (value.isEmpty() || value.equals(title)) {
                    continue;
                }
                if (paragraph.isBullet()) {
                    out.line("  ".repeat(Math.min(Math.max(paragraph.getIndentLevel(), 0), 6)) + "- " + value);
                } else {
                    out.block(value);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Excel

    private void xlsx(InputStream content, Out out) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (XSSFWorkbook workbook = new XSSFWorkbook(content)) {
            for (Sheet sheet : workbook) {
                if (out.full()) {
                    break;
                }
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    if (rows.size() >= MAX_SHEET_ROWS) {
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    int last = Math.min(Math.max(row.getLastCellNum(), 0), MAX_SHEET_COLUMNS);
                    for (int column = 0; column < last; column++) {
                        cells.add(cellText(row.getCell(column), formatter));
                    }
                    if (cells.stream().anyMatch(cell -> !cell.isBlank())) {
                        rows.add(cells);
                    }
                }
                if (!rows.isEmpty()) {
                    out.block("## " + oneLine(sheet.getSheetName()));
                    out.table(rows);
                }
            }
        }
    }

    /** Formulas are never evaluated; their cached results are used. */
    static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell);
        }
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC -> formatter.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue()).toUpperCase(Locale.ROOT);
            default -> "";
        };
    }

    // ---------------------------------------------------------------- PDF

    /** Returns false for encrypted PDFs and PDFs without a usable text layer (scans). */
    private boolean pdf(InputStream content, Out out) throws IOException {
        byte[] bytes = content.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                return false;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pages = document.getNumberOfPages();
            long visible = 0;
            for (int page = 1; page <= pages && !out.full(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                visible += text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
                if (!text.isEmpty()) {
                    out.block("## Page " + page);
                    out.block(text);
                }
            }
            return visible >= (long) Math.max(1, pages) * MIN_PDF_CHARS_PER_PAGE;
        } catch (InvalidPasswordException encrypted) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String normalize(String extension) {
        return extension == null ? "" : extension.strip().toLowerCase(Locale.ROOT);
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    /** Markdown accumulator that stops growing at the character limit. */
    static final class Out {
        private final int maxChars;
        private final StringBuilder text = new StringBuilder();
        private boolean truncated;

        Out(int maxChars) {
            this.maxChars = Math.max(1, maxChars);
        }

        boolean full() {
            return text.length() >= maxChars;
        }

        boolean truncated() {
            return truncated || text.length() > maxChars;
        }

        void raw(String value) {
            append(value);
        }

        /** A paragraph-level block separated by a blank line. */
        void block(String value) {
            int length = text.length();
            if (length > 0 && !(length > 1 && text.charAt(length - 1) == '\n' && text.charAt(length - 2) == '\n')) {
                append(text.charAt(length - 1) == '\n' ? "\n" : "\n\n");
            }
            append(value);
            append("\n");
        }

        /** A line that stays attached to the previous one, such as a list item. */
        void line(String value) {
            if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
                append("\n");
            }
            append(value);
            append("\n");
        }

        void table(List<List<String>> rows) {
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            if (columns == 0) {
                return;
            }
            StringBuilder table = new StringBuilder();
            for (int index = 0; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                table.append('|');
                for (int column = 0; column < columns; column++) {
                    String cell = column < row.size() && row.get(column) != null ? row.get(column) : "";
                    table.append(' ').append(oneLine(cell).replace("|", "\\|")).append(" |");
                }
                table.append('\n');
                if (index == 0) {
                    table.append('|').append(" --- |".repeat(columns)).append('\n');
                }
            }
            block(table.toString().stripTrailing());
        }

        String markdown() {
            String value = text.length() > maxChars ? text.substring(0, maxChars) : text.toString();
            return value.strip();
        }

        private void append(String value) {
            if (full()) {
                truncated = true;
                return;
            }
            text.append(value);
        }
    }
}
