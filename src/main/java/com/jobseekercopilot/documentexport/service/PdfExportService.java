package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class PdfExportService {
    private static final Color DARK_TEXT = new Color(17, 24, 39);
    private static final Color MUTED_TEXT = new Color(75, 85, 99);
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);

    public byte[] export(GeneratedDocumentResponse generatedDocument) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(new Rectangle(595, 842), 50, 50, 50, 58);
            PdfWriter writer = PdfWriter.getInstance(pdf, output);
            writer.setPageEvent(new BrandingFooter());
            pdf.open();
            DocumentTemplate.from(generatedDocument).blocks().forEach(block -> addBlock(pdf, block));
            pdf.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new DocumentExportException("Failed to create PDF export", exception);
        } catch (Exception exception) {
            throw new DocumentExportException("Failed to create PDF export", exception);
        }
    }

    private void addBlock(Document pdf, DocumentTemplate.Block block) {
        switch (block.style()) {
            case TITLE -> addTitle(pdf, block.text());
            case SUBTITLE -> addSubtitle(pdf, block.text());
            case CONTACT -> addContact(pdf, block.text());
            case ACCENT_LINE -> addAccentLine(pdf);
            case SECTION_HEADING -> addSectionHeading(pdf, block.text());
            case ROLE_HEADING -> addRoleHeading(pdf, block.text());
            case BULLET -> addBullet(pdf, block.text());
            case PARAGRAPH -> addParagraph(pdf, block.text());
            case FOOTER -> {
                // Footer is supplied by the PDF page event so every page is consistent.
            }
        }
    }

    private void addTitle(Document pdf, String title) {
        Font font = new Font(Font.HELVETICA, 22, Font.BOLD, DARK_TEXT);
        Paragraph paragraph = new Paragraph(title == null || title.isBlank() ? "Generated Document" : title, font);
        paragraph.setSpacingAfter(4);
        pdf.add(paragraph);
    }

    private void addSubtitle(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 11, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(5);
        pdf.add(paragraph);
    }

    private void addContact(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(9);
        pdf.add(paragraph);
    }

    private void addAccentLine(Document pdf) {
        LineSeparator line = new LineSeparator(1.2f, 100, ACCENT_BLUE, Element.ALIGN_LEFT, 0);
        Paragraph paragraph = new Paragraph();
        paragraph.add(line);
        paragraph.setSpacingAfter(18);
        pdf.add(paragraph);
    }

    private void addSectionHeading(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT_BLUE);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setKeepTogether(true);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(5);
        pdf.add(paragraph);
    }

    private void addRoleHeading(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 11, Font.BOLD, DARK_TEXT);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setKeepTogether(true);
        paragraph.setSpacingBefore(6);
        paragraph.setSpacingAfter(3);
        pdf.add(paragraph);
    }

    private void addParagraph(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 10, Font.NORMAL, DARK_TEXT);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(7);
        pdf.add(paragraph);
    }

    private void addBullet(Document pdf, String text) {
        Font font = new Font(Font.HELVETICA, 10, Font.NORMAL, DARK_TEXT);
        Paragraph paragraph = new Paragraph("\u2022 " + text, font);
        paragraph.setIndentationLeft(18);
        paragraph.setFirstLineIndent(-9);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(4);
        pdf.add(paragraph);
    }

    private static class BrandingFooter extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Font font = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED_TEXT);
            Phrase phrase = new Phrase(DocumentTemplate.BRAND_FOOTER, font);
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, phrase,
                    (document.left() + document.right()) / 2,
                    document.bottom() - 22,
                    0);
        }
    }
}
