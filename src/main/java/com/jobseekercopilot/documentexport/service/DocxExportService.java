package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.dto.ProfessionalContact;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;

@Service
public class DocxExportService {
    private static final String DARK_TEXT = "111827";
    private static final String MUTED_TEXT = "4B5563";
    private static final String ACCENT_BLUE = "2563EB";

    private final RenderBudget renderBudget;
    private final CvPaginationPlanner paginationPlanner;

    public DocxExportService(
            RenderBudget renderBudget,
            CvPaginationPlanner paginationPlanner) {
        this.renderBudget = renderBudget;
        this.paginationPlanner = paginationPlanner;
    }

    public byte[] export(GeneratedDocumentResponse document) {
        return export(document, null);
    }

    public byte[] export(
            GeneratedDocumentResponse document,
            ProfessionalContact professionalContact) {
        DocumentTemplate template = DocumentTemplate.from(
                document,
                professionalContact);
        RenderBudget.Session session =
                renderBudget.start(document, template);
        try (XWPFDocument docx = new XWPFDocument();
             BoundedByteArrayOutputStream output = session.output()) {
            configureMetadata(docx, template.metadata());
            configurePage(docx);
            addFooter(docx);
            BigInteger bulletNumbering = configureBulletNumbering(docx);
            CvPaginationPlanner.Plan paginationPlan =
                    paginationPlanner.plan(template);
            for (int index = 0; index < template.blocks().size(); index++) {
                session.checkDeadline();
                addBlock(
                        docx,
                        template.blocks().get(index),
                        bulletNumbering,
                        paginationPlan.breaksBefore(index));
            }
            docx.write(output);
            session.checkDeadline();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new DocumentExportException("Failed to create DOCX export", exception);
        }
    }

    private void addBlock(
            XWPFDocument docx,
            DocumentTemplate.Block block,
            BigInteger bulletNumbering,
            boolean pageBreakBefore) {
        int paragraphCount = docx.getParagraphs().size();
        switch (block.style()) {
            case TITLE -> addTitle(docx, block.text());
            case SUBTITLE -> addSubtitle(docx, block.text());
            case CONTACT -> addContact(docx, block);
            case ACCENT_LINE -> addAccentLine(docx);
            case SECTION_HEADING -> addSectionHeading(docx, block.text());
            case ROLE_HEADING -> addRoleHeading(docx, block.text());
            case ENTRY_META -> addEntryMeta(docx, block.text());
            case SKILLS -> addSkills(docx, block.text());
            case BULLET -> addBullet(
                    docx,
                    block.text(),
                    bulletNumbering);
            case PARAGRAPH, SIGN_OFF -> addParagraph(docx, block.text());
            case FOOTER -> {
                // Footer is added through the document section so it repeats on every page.
            }
        }
        if (docx.getParagraphs().size() > paragraphCount) {
            XWPFParagraph paragraph = docx.getParagraphs()
                    .get(docx.getParagraphs().size() - 1);
            if (pageBreakBefore) {
                paragraph.setPageBreak(true);
            }
            if (block.keepWithNext()) {
                paragraph.setKeepNext(true);
            }
            if (block.keepTogether()) {
                var properties = paragraph.getCTP().isSetPPr()
                        ? paragraph.getCTP().getPPr()
                        : paragraph.getCTP().addNewPPr();
                if (!properties.isSetKeepLines()) {
                    properties.addNewKeepLines();
                }
            }
        }
    }

    private void configurePage(XWPFDocument docx) {
        CTSectPr section = docx.getDocument().getBody().isSetSectPr()
                ? docx.getDocument().getBody().getSectPr()
                : docx.getDocument().getBody().addNewSectPr();
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        BigInteger margin = BigInteger.valueOf(1008); // 0.7 inch in twentieths of a point.
        margins.setTop(margin);
        margins.setBottom(margin);
        margins.setLeft(margin);
        margins.setRight(margin);
    }

    private void configureMetadata(
            XWPFDocument docx,
            DocumentMetadata metadata) {
        var properties = docx.getProperties().getCoreProperties();
        properties.setTitle(metadata.title());
        properties.setCreator(metadata.author());
        properties.setSubjectProperty(metadata.subject());
        properties.setDescription(metadata.description());
        properties.setKeywords(metadata.keywords());
        if (!metadata.version().isBlank()) {
            properties.setVersion(metadata.version());
            properties.setRevision(metadata.version());
        }
        properties.setContentStatus("Final");
    }

    private BigInteger configureBulletNumbering(XWPFDocument docx) {
        XWPFNumbering numbering = docx.createNumbering();
        CTAbstractNum definition = CTAbstractNum.Factory.newInstance();
        definition.setAbstractNumId(BigInteger.ZERO);
        var level = definition.addNewLvl();
        level.setIlvl(BigInteger.ZERO);
        level.addNewStart().setVal(BigInteger.ONE);
        level.addNewNumFmt().setVal(STNumberFormat.BULLET);
        level.addNewLvlText().setVal("\u2022");
        var indentation = level.addNewPPr().addNewInd();
        indentation.setLeft(BigInteger.valueOf(360));
        indentation.setHanging(BigInteger.valueOf(180));
        BigInteger abstractId = numbering.addAbstractNum(
                new XWPFAbstractNum(definition));
        return numbering.addNum(abstractId);
    }

    private void addFooter(XWPFDocument docx) {
        CTSectPr section = docx.getDocument().getBody().isSetSectPr()
                ? docx.getDocument().getBody().getSectPr()
                : docx.getDocument().getBody().addNewSectPr();
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(docx, section);
        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setStyle("Footer");
        addTextRuns(
                paragraph,
                DocumentTemplate.BRAND_FOOTER,
                8,
                false,
                MUTED_TEXT);
    }

    private void addTitle(XWPFDocument docx, String title) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingAfter(60);
        paragraph.setStyle("Title");
        addTextRuns(
                paragraph,
                title == null || title.isBlank()
                        ? "Generated Document"
                        : title,
                22,
                true,
                DARK_TEXT);
    }

    private void addSubtitle(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(70);
        paragraph.setStyle("Subtitle");
        addTextRuns(paragraph, text, 11, false, MUTED_TEXT);
    }

    private void addContact(
            XWPFDocument docx,
            DocumentTemplate.Block block) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(100);
        paragraph.setStyle("Normal");
        if (block.inlineSegments().isEmpty()) {
            addTextRuns(paragraph, block.text(), 9, false, MUTED_TEXT);
        } else {
            addInlineRuns(paragraph, block.inlineSegments(), 9, false, MUTED_TEXT);
        }
    }

    private void addAccentLine(XWPFDocument docx) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
        paragraph.setSpacingAfter(260);
        paragraph.setStyle("Normal");
        XWPFRun run = paragraph.createRun();
        applyFont(run, 1, false, ACCENT_BLUE);
        run.setText(" ");
    }

    private void addSectionHeading(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingBefore(210);
        paragraph.setSpacingAfter(85);
        paragraph.setStyle("Heading1");
        addTextRuns(paragraph, text, 12, true, ACCENT_BLUE);
    }

    private void addRoleHeading(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingBefore(150);
        paragraph.setSpacingAfter(30);
        paragraph.setStyle("Heading2");
        addTextRuns(paragraph, text, 11, true, DARK_TEXT);
    }

    private void addEntryMeta(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(55);
        paragraph.setStyle("Normal");
        addTextRuns(paragraph, text, 9, false, MUTED_TEXT);
    }

    private void addSkills(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(1.12);
        paragraph.setStyle("Normal");
        addTextRuns(paragraph, text, 10, false, DARK_TEXT);
    }

    private void addParagraph(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(1.08);
        paragraph.setStyle("Normal");
        addTextRuns(paragraph, text, 10, false, DARK_TEXT);
    }

    private void addBullet(
            XWPFDocument docx,
            String text,
            BigInteger bulletNumbering) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setStyle("ListParagraph");
        paragraph.setNumID(bulletNumbering);
        paragraph.setSpacingAfter(60);
        addTextRuns(paragraph, text, 10, false, DARK_TEXT);
    }

    private void addTextWithBreaks(XWPFRun run, String text) {
        String[] lines = text == null ? new String[] { "" } : text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                run.addBreak();
            }
            run.setText(lines[index]);
        }
    }

    private void applyFont(XWPFRun run, int size, boolean bold, String color) {
        run.setFontFamily(ExportFontProvider.DOCX_FONT_FAMILY);
        for (XWPFRun.FontCharRange range
                : XWPFRun.FontCharRange.values()) {
            run.setFontFamily(
                    ExportFontProvider.DOCX_FONT_FAMILY,
                    range);
        }
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        var properties = run.getCTR().isSetRPr()
                ? run.getCTR().getRPr()
                : run.getCTR().addNewRPr();
        var language = properties.sizeOfLangArray() == 0
                ? properties.addNewLang()
                : properties.getLangArray(0);
        language.setVal("en-GB");
        language.setEastAsia("en-GB");
        language.setBidi("en-GB");
    }

    private void addTextRuns(
            XWPFParagraph paragraph,
            String text,
            int size,
            boolean bold,
            String color) {
        for (AccessibleText.Segment segment
                : AccessibleText.segments(text)) {
            if (segment.isLink()) {
                XWPFHyperlinkRun link =
                        paragraph.createHyperlinkRun(segment.url());
                applyFont(link, size, bold, ACCENT_BLUE);
                link.setUnderline(UnderlinePatterns.SINGLE);
                link.setText(segment.text());
            } else {
                XWPFRun run = paragraph.createRun();
                applyFont(run, size, bold, color);
                addTextWithBreaks(run, segment.text());
            }
        }
    }

    private void addInlineRuns(
            XWPFParagraph paragraph,
            java.util.List<DocumentTemplate.InlineSegment> segments,
            int size,
            boolean bold,
            String color) {
        for (DocumentTemplate.InlineSegment segment : segments) {
            if (segment.isLink()) {
                XWPFHyperlinkRun link = paragraph.createHyperlinkRun(segment.url());
                applyFont(link, size, bold, ACCENT_BLUE);
                link.setUnderline(UnderlinePatterns.SINGLE);
                link.setText(segment.text());
            } else {
                XWPFRun run = paragraph.createRun();
                applyFont(run, size, bold, color);
                addTextWithBreaks(run, segment.text());
            }
        }
    }
}
