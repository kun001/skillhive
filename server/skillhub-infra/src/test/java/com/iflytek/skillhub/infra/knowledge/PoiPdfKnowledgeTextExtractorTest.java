package com.iflytek.skillhub.infra.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.knowledge.KnowledgeTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

class PoiPdfKnowledgeTextExtractorTest {
    private final PoiPdfKnowledgeTextExtractor extractor = new PoiPdfKnowledgeTextExtractor();

    @Test
    void wordHeadingsListsAndTablesBecomeMarkdown() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            // Localized Word keeps the built-in English style name; ids are often plain numbers.
            CTStyle heading = CTStyle.Factory.newInstance();
            heading.setStyleId("2");
            heading.setType(STStyleType.PARAGRAPH);
            heading.addNewName().setVal("heading 2");
            document.createStyles().addStyle(new XWPFStyle(heading));

            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText("报销制度");
            XWPFParagraph section = document.createParagraph();
            section.setStyle("2");
            section.createRun().setText("适用范围");
            document.createParagraph().createRun().setText("本制度适用于全体员工。");
            XWPFParagraph item = document.createParagraph();
            item.setNumID(BigInteger.ONE);
            item.createRun().setText("差旅费");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("类别");
            table.getRow(0).getCell(1).setText("上限");
            table.getRow(1).getCell(0).setText("住宿");
            table.getRow(1).getCell(1).setText("500|元");
            document.write(out);
            bytes = out.toByteArray();
        }

        var result = extractor.extract("docx", new ByteArrayInputStream(bytes), 100_000);

        assertThat(result.supported()).isTrue();
        assertThat(result.extractor()).isEqualTo("poi-docx/1");
        assertThat(result.markdown()).contains("# 报销制度", "## 适用范围", "本制度适用于全体员工。", "- 差旅费",
                "| 类别 | 上限 |", "| --- | --- |", "| 住宿 | 500\\|元 |");
    }

    @Test
    void slidesBecomeSectionsWithTextAndNotes() throws Exception {
        byte[] bytes;
        try (XMLSlideShow slideShow = new XMLSlideShow(); var out = new ByteArrayOutputStream()) {
            var layout = slideShow.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide = slideShow.createSlide(layout);
            slide.getPlaceholder(0).setText("季度计划");
            XSLFTextShape body = slide.getPlaceholder(1);
            body.clearText();
            body.addNewTextParagraph().addNewTextRun().setText("完成数据库迁移");
            XSLFNotes notes = slideShow.getNotesSlide(slide);
            for (XSLFTextShape shape : notes.getPlaceholders()) {
                if (shape.getPlaceholder() == Placeholder.BODY) {
                    shape.setText("讲解时强调风险");
                }
            }
            slideShow.write(out);
            bytes = out.toByteArray();
        }

        var markdown = extractor.extract("pptx", new ByteArrayInputStream(bytes), 100_000).markdown();

        assertThat(markdown).contains("## Slide 1: 季度计划", "完成数据库迁移", "> 讲解时强调风险");
        assertThat(markdown.indexOf("季度计划")).isEqualTo(markdown.lastIndexOf("季度计划"));
    }

    @Test
    void sheetsBecomeTablesWithCachedFormulaResults() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("预算");
            sheet.createRow(0).createCell(0).setCellValue("项目");
            sheet.getRow(0).createCell(1).setCellValue("金额");
            sheet.createRow(1).createCell(0).setCellValue("服务器");
            sheet.getRow(1).createCell(1).setCellValue(1200);
            sheet.createRow(2).createCell(0).setCellValue("合计");
            XSSFCell total = sheet.getRow(2).createCell(1);
            total.setCellFormula("B2*2");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(total);
            workbook.write(out);
            bytes = out.toByteArray();
        }

        var markdown = extractor.extract("xlsx", new ByteArrayInputStream(bytes), 100_000).markdown();

        assertThat(markdown).contains("## 预算", "| 项目 | 金额 |", "| 服务器 | 1200 |", "| 合计 | 2400 |");
        assertThat(markdown).doesNotContain("B2*2");
    }

    @Test
    void pdfTextIsExtractedPerPageAndScansAreUnsupported() throws Exception {
        var text = extractor.extract("pdf", new ByteArrayInputStream(pdf("Quarterly migration plan for the order service")), 100_000);
        assertThat(text.supported()).isTrue();
        assertThat(text.markdown()).contains("## Page 1", "Quarterly migration plan for the order service");

        var scan = extractor.extract("pdf", new ByteArrayInputStream(pdf(null)), 100_000);
        assertThat(scan.supported()).isFalse();
        assertThat(scan.extractor()).isEqualTo("pdfbox/1");
    }

    @Test
    void plainTextKeepsContentDecodesLegacyChineseAndTruncates() throws Exception {
        assertThat(extractor.extract("md", new ByteArrayInputStream("﻿# 标题\n正文".getBytes(StandardCharsets.UTF_8)), 1000).markdown())
                .isEqualTo("# 标题\n正文");
        assertThat(extractor.extract("txt", new ByteArrayInputStream("年假制度说明".getBytes(Charset.forName("GB18030"))), 1000).markdown())
                .isEqualTo("年假制度说明");

        KnowledgeTextExtractor.Result truncated = extractor.extract("txt",
                new ByteArrayInputStream("a".repeat(5000).getBytes(StandardCharsets.UTF_8)), 1000);
        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.markdown()).hasSize(1000);
    }

    @Test
    void legacyOfficeImagesAndArchivesAreNotAttempted() {
        assertThat(extractor.supports("docx")).isTrue();
        assertThat(extractor.supports("PDF")).isTrue();
        assertThat(extractor.supports("doc")).isFalse();
        assertThat(extractor.supports("png")).isFalse();
        assertThat(extractor.supports("zip")).isFalse();
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
