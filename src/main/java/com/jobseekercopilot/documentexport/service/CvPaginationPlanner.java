package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.ColumnText;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.stereotype.Component;

/**
 * Selects a conservative semantic page boundary for unusually unbalanced
 * two-page CVs. The plan is shared by the PDF and DOCX renderers so an export
 * family does not move different sections between pages.
 */
@Component
public class CvPaginationPlanner {

    static final float CONTENT_HEIGHT =
            PdfExportService.TOP - PdfExportService.BOTTOM;
    private static final float SPARSE_FINAL_PAGE_RATIO = 0.32f;
    private static final float MIN_PLANNED_PAGE_RATIO = 0.32f;
    private static final float MAX_PLANNED_PAGE_RATIO = 0.82f;

    private final PdfParagraphFactory paragraphs;

    public CvPaginationPlanner(PdfParagraphFactory paragraphs) {
        this.paragraphs = paragraphs;
    }

    public Plan plan(DocumentTemplate template) {
        if (template.kind() != DocumentTemplate.DocumentKind.CV) {
            return Plan.none();
        }

        List<DocumentTemplate.Block> blocks = template.blocks();
        Pagination natural = paginate(blocks, OptionalInt.empty());
        if (natural.pageFill().size() != 2
                || natural.finalPageFill()
                > CONTENT_HEIGHT * SPARSE_FINAL_PAGE_RATIO) {
            return Plan.none();
        }

        Candidate best = null;
        for (int index = 1; index < blocks.size(); index++) {
            if (blocks.get(index).style()
                    != DocumentTemplate.BlockStyle.SECTION_HEADING) {
                continue;
            }
            Pagination candidatePagination = paginate(
                    blocks,
                    OptionalInt.of(index));
            if (candidatePagination.pageFill().size() != 2
                    || !candidatePagination.pageFill().stream()
                    .allMatch(this::isSafePageFill)) {
                continue;
            }
            float score = Math.abs(
                    candidatePagination.pageFill().get(0)
                            - candidatePagination.pageFill().get(1));
            Candidate candidate = new Candidate(index, score);
            if (best == null || candidate.score() < best.score()) {
                best = candidate;
            }
        }
        return best == null
                ? Plan.none()
                : new Plan(OptionalInt.of(best.blockIndex()));
    }

    private Pagination paginate(
            List<DocumentTemplate.Block> blocks,
            OptionalInt breakBeforeBlock) {
        List<Float> pageFill = new ArrayList<>();
        float y = PdfExportService.TOP;
        for (int index = 0; index < blocks.size(); index++) {
            if (breakBeforeBlock.isPresent()
                    && breakBeforeBlock.getAsInt() == index
                    && y < PdfExportService.TOP) {
                pageFill.add(PdfExportService.TOP - y);
                y = PdfExportService.TOP;
            }

            DocumentTemplate.Block block = blocks.get(index);
            if (index == 0 || !blocks.get(index - 1).keepWithNext()) {
                List<Paragraph> keepChain = keepChainParagraphs(
                        blocks,
                        index);
                if (!fits(keepChain, y)
                        && fits(keepChain, PdfExportService.TOP)) {
                    pageFill.add(PdfExportService.TOP - y);
                    y = PdfExportService.TOP;
                }
            }

            if (block.style()
                    == DocumentTemplate.BlockStyle.ACCENT_LINE) {
                if (y < PdfExportService.BOTTOM + 24) {
                    pageFill.add(PdfExportService.TOP - y);
                    y = PdfExportService.TOP;
                }
                y -= 21;
                continue;
            }

            Paragraph paragraph = paragraphs.paragraphFor(block);
            if (paragraph == null) {
                continue;
            }
            boolean canKeepTogether = block.keepTogether()
                    && fits(List.of(paragraph), PdfExportService.TOP);
            paragraph.setKeepTogether(canKeepTogether);
            if (canKeepTogether) {
                if (!fits(List.of(paragraph), y)) {
                    pageFill.add(PdfExportService.TOP - y);
                    y = PdfExportService.TOP;
                }
            } else if (y < PdfExportService.BOTTOM + 24) {
                pageFill.add(PdfExportService.TOP - y);
                y = PdfExportService.TOP;
            }
            y = flow(paragraph, y, pageFill);
        }
        if (y < PdfExportService.TOP || pageFill.isEmpty()) {
            pageFill.add(PdfExportService.TOP - y);
        }
        return new Pagination(List.copyOf(pageFill));
    }

    private float flow(
            Paragraph paragraph,
            float top,
            List<Float> pageFill) {
        ColumnText column = new ColumnText(null);
        column.addElement(new Paragraph(paragraph));
        float y = top;
        while (true) {
            Layout layout = layout(column, y);
            y = layout.yLine();
            if (!ColumnText.hasMoreText(layout.status())) {
                return y;
            }
            pageFill.add(PdfExportService.TOP - y);
            y = PdfExportService.TOP;
        }
    }

    private List<Paragraph> keepChainParagraphs(
            List<DocumentTemplate.Block> blocks,
            int start) {
        List<Paragraph> keepChain = new ArrayList<>();
        for (int index = start; index < blocks.size(); index++) {
            DocumentTemplate.Block block = blocks.get(index);
            Paragraph paragraph = paragraphs.paragraphFor(block);
            if (paragraph != null) {
                keepChain.add(paragraph);
            }
            if (!block.keepWithNext()) {
                break;
            }
        }
        return List.copyOf(keepChain);
    }

    private boolean fits(List<Paragraph> paragraphsToFit, float top) {
        if (paragraphsToFit == null || paragraphsToFit.isEmpty()) {
            return true;
        }
        Layout fullPage = layout(
                paragraphsToFit,
                PdfExportService.TOP);
        if (ColumnText.hasMoreText(fullPage.status())) {
            return false;
        }
        Layout available = top == PdfExportService.TOP
                ? fullPage
                : layout(paragraphsToFit, top);
        return !ColumnText.hasMoreText(available.status())
                && available.linesWritten() >= fullPage.linesWritten();
    }

    private Layout layout(
            List<Paragraph> paragraphsToFit,
            float top) {
        ColumnText column = new ColumnText(null);
        for (Paragraph paragraph : paragraphsToFit) {
            column.addElement(new Paragraph(paragraph));
        }
        return layout(column, top);
    }

    private Layout layout(ColumnText column, float top) {
        column.setSimpleColumn(
                PdfExportService.LEFT,
                PdfExportService.BOTTOM,
                PdfExportService.RIGHT,
                top);
        try {
            int status = column.go(true);
            return new Layout(
                    status,
                    column.getLinesWritten(),
                    column.getYLine());
        } catch (DocumentException exception) {
            throw new DocumentExportException(
                    "Failed to preflight CV pagination", exception);
        }
    }

    private boolean isSafePageFill(float height) {
        return height >= CONTENT_HEIGHT * MIN_PLANNED_PAGE_RATIO
                && height <= CONTENT_HEIGHT * MAX_PLANNED_PAGE_RATIO;
    }

    public record Plan(OptionalInt breakBeforeBlock) {
        public Plan {
            breakBeforeBlock = breakBeforeBlock == null
                    ? OptionalInt.empty()
                    : breakBeforeBlock;
        }

        static Plan none() {
            return new Plan(OptionalInt.empty());
        }

        public boolean breaksBefore(int blockIndex) {
            return breakBeforeBlock.isPresent()
                    && breakBeforeBlock.getAsInt() == blockIndex;
        }
    }

    private record Candidate(int blockIndex, float score) {
    }

    private record Pagination(List<Float> pageFill) {
        private float finalPageFill() {
            return pageFill.get(pageFill.size() - 1);
        }
    }

    private record Layout(
            int status,
            int linesWritten,
            float yLine) {
    }
}
