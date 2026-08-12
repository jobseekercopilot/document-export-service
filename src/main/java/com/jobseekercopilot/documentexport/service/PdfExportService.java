package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import com.lowagie.text.Anchor;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfStructureElement;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.List;

@Service
public class PdfExportService {
    private static final Color DARK_TEXT = new Color(17, 24, 39);
    private static final Color MUTED_TEXT = new Color(75, 85, 99);
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);
    private static final PdfName ARTIFACT =
            new PdfName("Artifact");
    private static final float PAGE_WIDTH = 595;
    private static final float PAGE_HEIGHT = 842;
    private static final float LEFT = 50;
    private static final float RIGHT = PAGE_WIDTH - 50;
    private static final float TOP = PAGE_HEIGHT - 50;
    private static final float BOTTOM = 58;

    private final RenderBudget renderBudget;
    private final ExportFontProvider fontProvider;

    public PdfExportService(
            RenderBudget renderBudget,
            ExportFontProvider fontProvider) {
        this.renderBudget = renderBudget;
        this.fontProvider = fontProvider;
    }

    public byte[] export(GeneratedDocumentResponse generatedDocument) {
        DocumentTemplate template = DocumentTemplate.from(generatedDocument);
        RenderBudget.Session session =
                renderBudget.start(generatedDocument, template);
        try (BoundedByteArrayOutputStream output = session.output()) {
            Document pdf = new Document(
                    new Rectangle(PAGE_WIDTH, PAGE_HEIGHT),
                    LEFT,
                    PAGE_WIDTH - RIGHT,
                    PAGE_HEIGHT - TOP,
                    BOTTOM);
            PdfWriter writer = PdfWriter.getInstance(pdf, output);
            writer.setTagged();
            writer.getExtraCatalog().put(
                    PdfName.LANG,
                    new PdfString("en-GB"));
            writer.setPageEvent(
                    new BrandingFooter(font(8, Font.NORMAL, MUTED_TEXT)));
            configureMetadata(pdf, writer, template.metadata());
            pdf.open();
            PdfStructureElement documentStructure =
                    new PdfStructureElement(
                            writer.getStructureTreeRoot(),
                            PdfName.DOCUMENT);
            RenderCursor cursor = new RenderCursor(
                    pdf,
                    writer,
                    session);
            List<DocumentTemplate.Block> blocks = template.blocks();
            for (int index = 0; index < blocks.size();) {
                session.checkDeadline();
                DocumentTemplate.Block block = blocks.get(index);
                if (index == 0 || !blocks.get(index - 1).keepWithNext()) {
                    cursor.ensureFits(keepChainParagraphs(blocks, index));
                }
                if (block.style() == DocumentTemplate.BlockStyle.BULLET) {
                    PdfStructureElement listStructure =
                            new PdfStructureElement(
                                    documentStructure,
                                    PdfName.L);
                    while (index < blocks.size()
                            && blocks.get(index).style()
                            == DocumentTemplate.BlockStyle.BULLET) {
                        session.checkDeadline();
                        cursor.add(
                                bulletParagraph(
                                        blocks.get(index).text()),
                                new PdfStructureElement(
                                        listStructure,
                                        PdfName.LI),
                                blocks.get(index).keepTogether());
                        index++;
                    }
                } else {
                    addBlock(
                            cursor,
                            documentStructure,
                            block);
                    index++;
                }
            }
            pdf.close();
            session.checkDeadline();
            byte[] bytes = output.toByteArray();
            validatePdf(bytes);
            return bytes;
        } catch (DocumentExportException exception) {
            throw exception;
        } catch (DocumentException exception) {
            throw new DocumentExportException("Failed to create PDF export", exception);
        } catch (Exception exception) {
            throw new DocumentExportException("Failed to create PDF export", exception);
        }
    }

    private void configureMetadata(
            Document pdf,
            PdfWriter writer,
            DocumentMetadata metadata) {
        pdf.addTitle(metadata.title());
        pdf.addAuthor(metadata.author());
        pdf.addSubject(metadata.subject());
        pdf.addCreator(DocumentMetadata.CREATOR);
        pdf.addKeywords(metadata.keywords());
        if (!metadata.version().isBlank()) {
            writer.getInfo().put(
                    new PdfName("DocumentVersion"),
                    new PdfString(metadata.version()));
        }
    }

    private void addBlock(
            RenderCursor cursor,
            PdfStructureElement documentStructure,
            DocumentTemplate.Block block) throws DocumentException {
        switch (block.style()) {
            case TITLE -> cursor.add(
                    titleParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H1),
                    block.keepTogether());
            case SUBTITLE -> cursor.add(
                    subtitleParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case CONTACT -> cursor.add(
                    contactParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case ACCENT_LINE -> cursor.addAccentLine();
            case SECTION_HEADING -> cursor.add(
                    sectionHeadingParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H2),
                    block.keepTogether());
            case ROLE_HEADING -> cursor.add(
                    roleHeadingParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H3),
                    block.keepTogether());
            case ENTRY_META -> cursor.add(
                    entryMetaParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case SKILLS -> cursor.add(
                    skillsParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case BULLET -> cursor.add(
                    bulletParagraph(block.text()),
                    new PdfStructureElement(
                            new PdfStructureElement(
                                    documentStructure,
                                    PdfName.L), PdfName.LI),
                    block.keepTogether());
            case PARAGRAPH, SIGN_OFF -> cursor.add(
                    bodyParagraph(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case FOOTER -> {
                // Footer is supplied by the PDF page event so every page is consistent.
            }
        }
    }

    private List<Paragraph> keepChainParagraphs(
            List<DocumentTemplate.Block> blocks,
            int start) {
        java.util.ArrayList<Paragraph> paragraphs = new java.util.ArrayList<>();
        for (int index = start; index < blocks.size(); index++) {
            DocumentTemplate.Block block = blocks.get(index);
            Paragraph paragraph = paragraphFor(block);
            if (paragraph != null) {
                paragraphs.add(paragraph);
            }
            if (!block.keepWithNext()) {
                break;
            }
        }
        return List.copyOf(paragraphs);
    }

    private Paragraph paragraphFor(DocumentTemplate.Block block) {
        return switch (block.style()) {
            case TITLE -> titleParagraph(block.text());
            case SUBTITLE -> subtitleParagraph(block.text());
            case CONTACT -> contactParagraph(block.text());
            case SECTION_HEADING -> sectionHeadingParagraph(block.text());
            case ROLE_HEADING -> roleHeadingParagraph(block.text());
            case ENTRY_META -> entryMetaParagraph(block.text());
            case SKILLS -> skillsParagraph(block.text());
            case PARAGRAPH, SIGN_OFF -> bodyParagraph(block.text());
            case BULLET -> bulletParagraph(block.text());
            case ACCENT_LINE, FOOTER -> null;
        };
    }

    private Paragraph titleParagraph(String title) {
        Font font = font(22, Font.BOLD, DARK_TEXT);
        Paragraph paragraph = new Paragraph(
                title == null || title.isBlank()
                        ? "Generated Document"
                        : title,
                font);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private Paragraph subtitleParagraph(String text) {
        Font font = font(11, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setSpacingAfter(5);
        return paragraph;
    }

    private Paragraph contactParagraph(String text) {
        Font font = font(9, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setSpacingAfter(9);
        return paragraph;
    }

    private Paragraph sectionHeadingParagraph(String text) {
        Font font = font(12, Font.BOLD, ACCENT_BLUE);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(5);
        return paragraph;
    }

    private Paragraph roleHeadingParagraph(String text) {
        Font font = font(11, Font.BOLD, DARK_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setSpacingBefore(8);
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private Paragraph entryMetaParagraph(String text) {
        Font font = font(9, Font.NORMAL, MUTED_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private Paragraph skillsParagraph(String text) {
        Font font = font(10, Font.NORMAL, DARK_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setLeading(15);
        paragraph.setSpacingAfter(7);
        return paragraph;
    }

    private Paragraph bodyParagraph(String text) {
        Font font = font(10, Font.NORMAL, DARK_TEXT);
        Paragraph paragraph = paragraph(text, font);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(7);
        return paragraph;
    }

    private Paragraph bulletParagraph(String text) {
        Font font = font(10, Font.NORMAL, DARK_TEXT);
        Paragraph paragraph = paragraph("\u2022 " + text, font);
        paragraph.setIndentationLeft(18);
        paragraph.setFirstLineIndent(-9);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(4);
        return paragraph;
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

    private void validatePdf(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        try {
            renderBudget.validatePdfPages(reader.getNumberOfPages());
            PdfObject structureRoot = reader.getCatalog().get(
                    PdfName.STRUCTTREEROOT);
            if (structureRoot == null
                    || reader.getCatalog().get(PdfName.LANG) == null
                    || !hasStructureRole(
                            structureRoot,
                            PdfName.H1)) {
                throw new DocumentExportException(
                        "Rendered PDF is missing structural accessibility metadata");
            }
        } finally {
            reader.close();
        }
    }

    private boolean hasStructureRole(
            PdfObject object,
            PdfName expectedRole) {
        PdfObject resolved = PdfReader.getPdfObject(object);
        if (resolved instanceof PdfArray array) {
            return array.getElements().stream().anyMatch(
                    child -> hasStructureRole(
                            child,
                            expectedRole));
        }
        if (!(resolved instanceof PdfDictionary dictionary)) {
            return false;
        }
        if (expectedRole.equals(
                dictionary.getAsName(PdfName.S))) {
            return true;
        }
        PdfObject children = dictionary.get(PdfName.K);
        return children != null
                && hasStructureRole(children, expectedRole);
    }

    private static class RenderCursor {

        private final Document document;
        private final PdfWriter writer;
        private final RenderBudget.Session session;
        private float y = TOP;

        private RenderCursor(
                Document document,
                PdfWriter writer,
                RenderBudget.Session session) {
            this.document = document;
            this.writer = writer;
            this.session = session;
        }

        private void add(
                Paragraph paragraph,
                PdfStructureElement structure,
                boolean keepTogether)
                throws DocumentException {
            boolean canKeepTogether = keepTogether
                    && fits(List.of(paragraph), TOP);
            paragraph.setKeepTogether(canKeepTogether);
            if (canKeepTogether) {
                ensureFits(List.of(paragraph));
            } else {
                ensureSpace();
            }
            ColumnText column = new ColumnText(
                    writer.getDirectContent());
            column.addElement(paragraph);
            while (true) {
                session.checkDeadline();
                PdfContentByte canvas =
                        writer.getDirectContent();
                column.setCanvas(canvas);
                column.setSimpleColumn(
                        LEFT,
                        BOTTOM,
                        RIGHT,
                        y);
                canvas.beginMarkedContentSequence(structure);
                int status = column.go();
                canvas.endMarkedContentSequence();
                y = column.getYLine();
                if (!ColumnText.hasMoreText(status)) {
                    return;
                }
                newPage();
            }
        }

        private void ensureFits(List<Paragraph> paragraphs) {
            if (paragraphs == null || paragraphs.isEmpty()) {
                ensureSpace();
                return;
            }
            if (!fits(paragraphs, y) && fits(paragraphs, TOP)) {
                newPage();
            }
        }

        private boolean fits(List<Paragraph> paragraphs, float top) {
            Layout fullPage = layout(paragraphs, TOP);
            if (ColumnText.hasMoreText(fullPage.status())) {
                return false;
            }
            Layout available = top == TOP
                    ? fullPage
                    : layout(paragraphs, top);
            return !ColumnText.hasMoreText(available.status())
                    && available.linesWritten() >= fullPage.linesWritten();
        }

        private Layout layout(List<Paragraph> paragraphs, float top) {
            ColumnText measurement = new ColumnText(
                    writer.getDirectContent());
            for (Paragraph paragraph : paragraphs) {
                measurement.addElement(new Paragraph(paragraph));
            }
            measurement.setSimpleColumn(
                    LEFT,
                    BOTTOM,
                    RIGHT,
                    top);
            try {
                int status = measurement.go(true);
                return new Layout(status, measurement.getLinesWritten());
            } catch (DocumentException exception) {
                throw new DocumentExportException(
                        "Failed to measure PDF content", exception);
            }
        }

        private record Layout(int status, int linesWritten) {
        }

        private void addAccentLine() {
            if (y < BOTTOM + 24) {
                newPage();
            }
            PdfContentByte canvas = writer.getDirectContent();
            canvas.beginMarkedContentSequence(ARTIFACT);
            canvas.setColorStroke(ACCENT_BLUE);
            canvas.setLineWidth(1.2f);
            canvas.moveTo(LEFT, y - 3);
            canvas.lineTo(RIGHT, y - 3);
            canvas.stroke();
            canvas.endMarkedContentSequence();
            y -= 21;
        }

        private void ensureSpace() {
            if (y < BOTTOM + 24) {
                newPage();
            }
        }

        private void newPage() {
            document.newPage();
            y = TOP;
        }
    }

    private static class BrandingFooter extends PdfPageEventHelper {

        private final Font font;

        private BrandingFooter(Font font) {
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Phrase phrase = new Phrase(DocumentTemplate.BRAND_FOOTER, font);
            canvas.beginMarkedContentSequence(ARTIFACT);
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, phrase,
                    (document.left() + document.right()) / 2,
                    document.bottom() - 22,
                    0);
            canvas.endMarkedContentSequence();
        }
    }
}
