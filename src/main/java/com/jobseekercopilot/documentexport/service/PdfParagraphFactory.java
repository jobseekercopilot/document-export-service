package com.jobseekercopilot.documentexport.service;

import com.lowagie.text.Anchor;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import java.awt.Color;
import org.springframework.stereotype.Component;

@Component
class PdfParagraphFactory {

    private static final Color DARK_TEXT = new Color(17, 24, 39);
    private static final Color MUTED_TEXT = new Color(75, 85, 99);
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);

    private final ExportFontProvider fontProvider;

    PdfParagraphFactory(ExportFontProvider fontProvider) {
        this.fontProvider = fontProvider;
    }

    Paragraph paragraphFor(DocumentTemplate.Block block) {
        return switch (block.style()) {
            case TITLE -> title(block.text());
            case SUBTITLE -> subtitle(block.text());
            case CONTACT -> contact(block);
            case SECTION_HEADING -> sectionHeading(block.text());
            case ROLE_HEADING -> roleHeading(block.text());
            case ENTRY_META -> entryMeta(block.text());
            case SKILLS -> skills(block.text());
            case PARAGRAPH, SIGN_OFF -> body(block.text());
            case BULLET -> bullet(block.text());
            case ACCENT_LINE, FOOTER -> null;
        };
    }

    Paragraph title(String title) {
        Font font = font(22, Font.BOLD, DARK_TEXT);
        Paragraph paragraph = new Paragraph(
                title == null || title.isBlank()
                        ? "Generated Document"
                        : title,
                font);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    Paragraph subtitle(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(11, Font.NORMAL, MUTED_TEXT));
        paragraph.setSpacingAfter(5);
        return paragraph;
    }

    Paragraph contact(DocumentTemplate.Block block) {
        Font font = font(9, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = block.inlineSegments().isEmpty()
                ? paragraph(block.text(), font)
                : paragraph(block.inlineSegments(), font);
        paragraph.setSpacingAfter(9);
        return paragraph;
    }

    Paragraph sectionHeading(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(12, Font.BOLD, ACCENT_BLUE));
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(5);
        return paragraph;
    }

    Paragraph roleHeading(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(11, Font.BOLD, DARK_TEXT));
        paragraph.setSpacingBefore(8);
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    Paragraph entryMeta(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(9, Font.NORMAL, MUTED_TEXT));
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    Paragraph skills(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(10, Font.NORMAL, DARK_TEXT));
        paragraph.setLeading(15);
        paragraph.setSpacingAfter(7);
        return paragraph;
    }

    Paragraph body(String text) {
        Paragraph paragraph = paragraph(
                text,
                font(10, Font.NORMAL, DARK_TEXT));
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(7);
        return paragraph;
    }

    Paragraph bullet(String text) {
        Paragraph paragraph = paragraph(
                "\u2022 " + text,
                font(10, Font.NORMAL, DARK_TEXT));
        paragraph.setIndentationLeft(18);
        paragraph.setFirstLineIndent(-9);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    Font footerFont() {
        return font(8, Font.NORMAL, MUTED_TEXT);
    }

    private Font font(float size, int style, Color color) {
        return new Font(fontProvider.pdfFont(), size, style, color);
    }

    private Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph();
        for (AccessibleText.Segment segment
                : AccessibleText.segments(text)) {
            if (segment.isLink()) {
                Font linkFont = new Font(
                        fontProvider.pdfFont(),
                        font.getSize(),
                        font.getStyle() | Font.UNDERLINE,
                        ACCENT_BLUE);
                Anchor anchor = new Anchor(segment.text(), linkFont);
                anchor.setReference(segment.url());
                paragraph.add(anchor);
            } else {
                paragraph.add(new Phrase(segment.text(), font));
            }
        }
        return paragraph;
    }

    private Paragraph paragraph(
            java.util.List<DocumentTemplate.InlineSegment> segments,
            Font font) {
        Paragraph paragraph = new Paragraph();
        for (DocumentTemplate.InlineSegment segment : segments) {
            if (segment.isLink()) {
                Font linkFont = new Font(
                        fontProvider.pdfFont(),
                        font.getSize(),
                        font.getStyle() | Font.UNDERLINE,
                        ACCENT_BLUE);
                Anchor anchor = new Anchor(segment.text(), linkFont);
                anchor.setReference(segment.url());
                paragraph.add(anchor);
            } else {
                paragraph.add(new Phrase(segment.text(), font));
            }
        }
        return paragraph;
    }
}
