package com.jobseekercopilot.documentexport.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RenderBudgetTest {

    @Test
    void permitsContentWithinAllBudgets() {
        DocumentExportLimits limits = new DocumentExportLimits();
        GeneratedDocumentResponse document = document("Short content");

        assertDoesNotThrow(() -> new RenderBudget(limits)
                .start(document, DocumentTemplate.from(document)));
    }

    @Test
    void rejectsCharacterBlockAndEstimatedPageBudgetViolations() {
        DocumentExportLimits characterLimits =
                new DocumentExportLimits();
        characterLimits.setRenderMaxInputCharacters(5);
        GeneratedDocumentResponse characters = document("Too long");
        var characterError = assertThrows(
                DocumentExportException.class,
                () -> new RenderBudget(characterLimits).start(
                        characters,
                        DocumentTemplate.from(characters)));
        assertTrue(characterError.getMessage().contains("character"));

        DocumentExportLimits blockLimits = new DocumentExportLimits();
        blockLimits.setRenderMaxBlocks(1);
        GeneratedDocumentResponse blocks = document("One paragraph");
        var blockError = assertThrows(
                DocumentExportException.class,
                () -> new RenderBudget(blockLimits).start(
                        blocks,
                        DocumentTemplate.from(blocks)));
        assertTrue(blockError.getMessage().contains("block"));

        DocumentExportLimits pageLimits = new DocumentExportLimits();
        pageLimits.setRenderCharactersPerLine(1);
        pageLimits.setRenderLinesPerPage(1);
        pageLimits.setRenderMaxEstimatedPages(2);
        GeneratedDocumentResponse pages = document("Many estimated lines");
        var pageError = assertThrows(
                DocumentExportException.class,
                () -> new RenderBudget(pageLimits).start(
                        pages,
                        DocumentTemplate.from(pages)));
        assertTrue(pageError.getMessage().contains("page"));
    }

    @Test
    void enforcesTimeOutputAndActualPdfPageBudgets()
            throws IOException {
        AtomicLong time = new AtomicLong();
        DocumentExportLimits timeLimits = new DocumentExportLimits();
        timeLimits.setRenderMaxDuration(Duration.ofNanos(10));
        GeneratedDocumentResponse document = document("Content");
        RenderBudget.Session session =
                new RenderBudget(timeLimits, time::get).start(
                        document,
                        DocumentTemplate.from(document));
        time.set(11);
        var timeError = assertThrows(
                DocumentExportException.class,
                session::checkDeadline);
        assertTrue(timeError.getMessage().contains("time"));

        DocumentExportLimits outputLimits = new DocumentExportLimits();
        outputLimits.setRenderMaxOutputBytes(3);
        RenderBudget.Session outputSession =
                new RenderBudget(outputLimits).start(
                        document,
                        DocumentTemplate.from(document));
        try (BoundedByteArrayOutputStream output =
                     outputSession.output()) {
            output.write(new byte[] {1, 2, 3});
            assertThrows(IOException.class, () -> output.write(4));
        }

        DocumentExportLimits pageLimits = new DocumentExportLimits();
        pageLimits.setRenderMaxEstimatedPages(1);
        RenderBudget budget = new RenderBudget(pageLimits);
        assertDoesNotThrow(() -> budget.validatePdfPages(1));
        assertThrows(
                DocumentExportException.class,
                () -> budget.validatePdfPages(2));
    }

    private GeneratedDocumentResponse document(String content) {
        return new GeneratedDocumentResponse()
                .title("CV")
                .content(content);
    }
}
