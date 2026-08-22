package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RenderBudget {

    private final DocumentExportLimits limits;
    private final LongSupplier nanoTime;

    @Autowired
    public RenderBudget(DocumentExportLimits limits) {
        this(limits, System::nanoTime);
    }

    RenderBudget(DocumentExportLimits limits, LongSupplier nanoTime) {
        this.limits = limits;
        this.nanoTime = nanoTime;
    }

    public Session start(
            GeneratedDocumentResponse document,
            DocumentTemplate template) {
        validateInput(document, template);
        long maximumNanos = limits.getRenderMaxDuration().toNanos();
        if (maximumNanos < 1) {
            throw new DocumentExportException(
                    "Render duration budget must be positive");
        }
        long startedAt = nanoTime.getAsLong();
        return new Session(
                startedAt,
                saturatedAdd(startedAt, maximumNanos));
    }

    public void validatePdfPages(int pages) {
        if (pages < 1 || pages > limits.getRenderMaxEstimatedPages()) {
            throw new DocumentExportException(
                    "Rendered PDF exceeds the configured page budget");
        }
    }

    private void validateInput(
            GeneratedDocumentResponse document,
            DocumentTemplate template) {
        int titleCharacters = length(document == null ? null : document.getTitle());
        int contentCharacters =
                length(document == null ? null : document.getContent());
        long totalCharacters = (long) titleCharacters + contentCharacters;
        if (totalCharacters > limits.getRenderMaxInputCharacters()) {
            throw new DocumentExportException(
                    "Document input exceeds the configured character budget");
        }
        if (template.blocks().size() > limits.getRenderMaxBlocks()) {
            throw new DocumentExportException(
                    "Document input exceeds the configured block budget");
        }
        long estimatedLines = template.blocks().stream()
                .mapToLong(block -> estimatedLines(block.text()))
                .sum();
        long estimatedPages = Math.max(
                1,
                divideRoundUp(
                        estimatedLines,
                        limits.getRenderLinesPerPage()));
        if (estimatedPages > limits.getRenderMaxEstimatedPages()) {
            throw new DocumentExportException(
                    "Document input exceeds the configured page budget");
        }
    }

    private long estimatedLines(String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        return value.lines()
                .mapToLong(line -> Math.max(
                        1,
                        divideRoundUp(
                                line.codePointCount(0, line.length()),
                                limits.getRenderCharactersPerLine())))
                .sum();
    }

    private long divideRoundUp(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public final class Session {

        private final long startedAt;
        private final long deadline;

        private Session(long startedAt, long deadline) {
            this.startedAt = startedAt;
            this.deadline = deadline;
        }

        public BoundedByteArrayOutputStream output() {
            return new BoundedByteArrayOutputStream(
                    limits.getRenderMaxOutputBytes());
        }

        public void checkDeadline() {
            if (nanoTime.getAsLong() > deadline) {
                throw new DocumentExportException(
                        "Document render exceeded the configured time budget");
            }
        }

        public long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(
                    Math.max(0, nanoTime.getAsLong() - startedAt));
        }
    }
}
