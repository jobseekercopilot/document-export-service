package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

@Service
public class DocxExportService {
    private static final String FONT = "Aptos";
    private static final String DARK_TEXT = "111827";
    private static final String MUTED_TEXT = "4B5563";
    private static final String ACCENT_BLUE = "2563EB";

    public byte[] export(GeneratedDocumentResponse document) {
        try (XWPFDocument docx = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(docx);
            addFooter(docx);
            DocumentTemplate.from(document).blocks().forEach(block -> addBlock(docx, block));
            docx.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new DocumentExportException("Failed to create DOCX export", exception);
        }
    }

    private void addBlock(XWPFDocument docx, DocumentTemplate.Block block) {
        switch (block.style()) {
            case TITLE -> addTitle(docx, block.text());
            case SUBTITLE -> addSubtitle(docx, block.text());
            case CONTACT -> addContact(docx, block.text());
            case ACCENT_LINE -> addAccentLine(docx);
            case SECTION_HEADING -> addSectionHeading(docx, block.text());
            case ROLE_HEADING -> addRoleHeading(docx, block.text());
            case BULLET -> addBullet(docx, block.text());
            case PARAGRAPH -> addParagraph(docx, block.text());
            case FOOTER -> {
                // Footer is added through the document section so it repeats on every page.
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

    private void addFooter(XWPFDocument docx) {
        CTSectPr section = docx.getDocument().getBody().isSetSectPr()
                ? docx.getDocument().getBody().getSectPr()
                : docx.getDocument().getBody().addNewSectPr();
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(docx, section);
        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 8, false, MUTED_TEXT);
        run.setText(DocumentTemplate.BRAND_FOOTER);
    }

    private void addTitle(XWPFDocument docx, String title) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingAfter(60);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 22, true, DARK_TEXT);
        run.setText(title == null || title.isBlank() ? "Generated Document" : title);
    }

    private void addSubtitle(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(70);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 11, false, MUTED_TEXT);
        run.setText(text);
    }

    private void addContact(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 9, false, MUTED_TEXT);
        run.setText(text);
    }

    private void addAccentLine(XWPFDocument docx) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
        paragraph.setSpacingAfter(260);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 1, false, ACCENT_BLUE);
        run.setText(" ");
    }

    private void addSectionHeading(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingBefore(210);
        paragraph.setSpacingAfter(85);
        XWPFRun run = paragraph.createRun();
        run.setUnderline(UnderlinePatterns.SINGLE);
        applyFont(run, 12, true, ACCENT_BLUE);
        run.setText(text);
    }

    private void addRoleHeading(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingBefore(110);
        paragraph.setSpacingAfter(40);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 11, true, DARK_TEXT);
        run.setText(text);
    }

    private void addParagraph(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(1.08);
        XWPFRun run = paragraph.createRun();
        applyFont(run, 10, false, DARK_TEXT);
        addTextWithBreaks(run, text);
    }

    private void addBullet(XWPFDocument docx, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setIndentationLeft(360);
        paragraph.setIndentationHanging(180);
        paragraph.setSpacingAfter(60);
        XWPFRun bullet = paragraph.createRun();
        applyFont(bullet, 10, false, ACCENT_BLUE);
        bullet.setText("\u2022 ");
        XWPFRun run = paragraph.createRun();
        applyFont(run, 10, false, DARK_TEXT);
        run.setText(text);
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
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
    }
}
