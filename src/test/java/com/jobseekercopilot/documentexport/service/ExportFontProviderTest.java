package com.jobseekercopilot.documentexport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportFontProviderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void fallsBackToApprovedRuntimeDejaVuSans() {
        ExportFontProvider provider = new ExportFontProvider(
                new DocumentExportLimits());

        assertEquals(
                "DejaVuSans",
                provider.pdfFont().getPostscriptFontName());
        assertTrue(provider.pdfFont().isEmbedded());
    }

    @Test
    void rejectsConfiguredFilesOutsideTheApprovedFontPolicy()
            throws Exception {
        Path unapproved = temporaryDirectory.resolve("OtherFont.ttf");
        Files.write(unapproved, new byte[] {1, 2, 3});
        DocumentExportLimits limits = new DocumentExportLimits();
        limits.setPdfFontPath(unapproved.toString());

        var exception = assertThrows(
                DocumentExportException.class,
                () -> new ExportFontProvider(limits).pdfFont());

        assertTrue(exception.getMessage().contains("approved"));
    }
}
