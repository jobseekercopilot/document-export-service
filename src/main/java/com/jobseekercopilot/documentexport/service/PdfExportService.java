package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.dto.ProfessionalContact;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
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

import java.util.List;

@Service
public class PdfExportService {
    private static final java.awt.Color ACCENT_BLUE =
            new java.awt.Color(37, 99, 235);
    private static final PdfName ARTIFACT =
            new PdfName("Artifact");
    private static final float PAGE_WIDTH = 595;
    private static final float PAGE_HEIGHT = 842;
    static final float LEFT = 50;
    static final float RIGHT = PAGE_WIDTH - 50;
    static final float TOP = PAGE_HEIGHT - 50;
    static final float BOTTOM = 58;

    private final RenderBudget renderBudget;
    private final PdfParagraphFactory paragraphs;
    private final CvPaginationPlanner paginationPlanner;

    public PdfExportService(
            RenderBudget renderBudget,
            PdfParagraphFactory paragraphs,
            CvPaginationPlanner paginationPlanner) {
        this.renderBudget = renderBudget;
        this.paragraphs = paragraphs;
        this.paginationPlanner = paginationPlanner;
    }

    public byte[] export(GeneratedDocumentResponse generatedDocument) {
        return export(generatedDocument, null);
    }

    public byte[] export(
            GeneratedDocumentResponse generatedDocument,
            ProfessionalContact professionalContact) {
        DocumentTemplate template = DocumentTemplate.from(
                generatedDocument,
                professionalContact);
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
                    new BrandingFooter(paragraphs.footerFont()));
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
            CvPaginationPlanner.Plan paginationPlan =
                    paginationPlanner.plan(template);
            for (int index = 0; index < blocks.size();) {
                session.checkDeadline();
                if (paginationPlan.breaksBefore(index)) {
                    cursor.startNewPage();
                }
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
                                paragraphs.bullet(
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
                    paragraphs.title(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H1),
                    block.keepTogether());
            case SUBTITLE -> cursor.add(
                    paragraphs.subtitle(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case CONTACT -> cursor.add(
                    paragraphs.contact(block),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case ACCENT_LINE -> cursor.addAccentLine();
            case SECTION_HEADING -> cursor.add(
                    paragraphs.sectionHeading(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H2),
                    block.keepTogether());
            case ROLE_HEADING -> cursor.add(
                    paragraphs.roleHeading(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.H3),
                    block.keepTogether());
            case ENTRY_META -> cursor.add(
                    paragraphs.entryMeta(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case SKILLS -> cursor.add(
                    paragraphs.skills(block.text()),
                    new PdfStructureElement(
                            documentStructure, PdfName.P),
                    block.keepTogether());
            case BULLET -> cursor.add(
                    paragraphs.bullet(block.text()),
                    new PdfStructureElement(
                            new PdfStructureElement(
                                    documentStructure,
                                    PdfName.L), PdfName.LI),
                    block.keepTogether());
            case PARAGRAPH, SIGN_OFF -> cursor.add(
                    paragraphs.body(block.text()),
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
        return paragraphs.paragraphFor(block);
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

        private void startNewPage() {
            if (y < TOP) {
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
