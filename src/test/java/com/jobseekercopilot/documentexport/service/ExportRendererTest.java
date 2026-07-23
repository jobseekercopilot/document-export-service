package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRendererTest {

    @Test
    void docxExportCreatesReadableDocumentWithTitleAndParagraphs() throws Exception {
        GeneratedDocumentResponse document = document();

        byte[] bytes = new DocxExportService().export(document);

        assertTrue(bytes.length > 0);
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = bodyText(docx);
            assertTrue(text.contains("Alex Candidate"));
            assertTrue(text.contains("alex@example.com | London, SW1A 1AA"));
            assertTrue(text.contains("Personal Summary"));
            assertTrue(text.contains("First paragraph."));
            assertTrue(text.contains("Core Skills"));
            assertTrue(text.contains("\u2022 Java: Built APIs"));
            assertTrue(footerText(docx).contains(DocumentTemplate.BRAND_FOOTER));
        }
    }

    @Test
    void cvDocxExportDoesNotDuplicateTitle() throws Exception {
        byte[] bytes = new DocxExportService().export(document());

        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            long titleCount = docx.getParagraphs().stream()
                    .filter(paragraph -> "Tailored CV".equals(paragraph.getText()))
                    .count();
            assertEquals(0, titleCount);
            assertEquals("Alex Candidate", docx.getParagraphs().get(0).getText());
        }
    }

    @Test
    void coverLetterDocxExportCreatesProfessionalHeaderAndFooter() throws Exception {
        GeneratedDocumentResponse document = coverLetter();

        byte[] bytes = new DocxExportService().export(document);

        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = bodyText(docx);
            assertTrue(text.contains("Alex Candidate"));
            assertTrue(text.contains("Application for Developer"));
            assertTrue(text.contains("alex@example.com | London"));
            assertTrue(text.contains("Dear Hiring Manager,"));
            assertTrue(text.contains("I am writing to apply for the role."));
            assertTrue(text.contains("Kind regards"));
            assertTrue(footerText(docx).contains(DocumentTemplate.BRAND_FOOTER));
        }
    }

    @Test
    void cvPdfExportCreatesPdfBytesWithExpectedTextAndFooter() throws Exception {
        byte[] bytes = new PdfExportService().export(document());

        assertTrue(bytes.length > 0);
        assertEquals("%PDF", new String(bytes, 0, 4));
        String text = pdfText(bytes);
        assertTrue(text.contains("Alex Candidate"));
        assertTrue(text.contains("alex@example.com | London, SW1A 1AA"));
        assertTrue(text.contains("Personal Summary"));
        assertTrue(text.contains("Core Skills"));
        assertTrue(text.contains(DocumentTemplate.BRAND_FOOTER));
    }

    @Test
    void coverLetterPdfExportCreatesPdfBytesWithExpectedTextAndFooter() throws Exception {
        byte[] bytes = new PdfExportService().export(coverLetter());

        assertTrue(bytes.length > 0);
        assertEquals("%PDF", new String(bytes, 0, 4));
        String text = pdfText(bytes);
        assertTrue(text.contains("Alex Candidate"));
        assertTrue(text.contains("Application for Developer"));
        assertTrue(text.contains("Dear Hiring Manager,"));
        assertTrue(text.contains("Kind regards"));
        assertTrue(text.contains(DocumentTemplate.BRAND_FOOTER));
    }

    private GeneratedDocumentResponse document() {
        return new GeneratedDocumentResponse()
                .id(UUID.randomUUID())
                .documentType(GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Alex Candidate
                        alex@example.com
                        London, SW1A 1AA

                        Personal Summary
                        First paragraph.

                        Core Skills
                        - Java: Built APIs
                        """);
    }

    private GeneratedDocumentResponse coverLetter() {
        return new GeneratedDocumentResponse()
                .id(UUID.randomUUID())
                .documentType(GeneratedDocumentResponse.DocumentTypeEnum.COVER_LETTER)
                .title("Developer Cover Letter")
                .content("""
                        Developer Cover Letter

                        Alex Candidate
                        alex@example.com
                        London

                        Dear Hiring Manager,

                        I am writing to apply for the role.

                        Kind regards
                        Alex Candidate
                        """);
    }

    private String bodyText(XWPFDocument docx) {
        return docx.getParagraphs().stream()
                .map(paragraph -> paragraph.getText())
                .collect(Collectors.joining("\n"));
    }

    private String footerText(XWPFDocument docx) {
        return docx.getFooterList().stream()
                .flatMap(footer -> footer.getParagraphs().stream())
                .map(paragraph -> paragraph.getText())
                .collect(Collectors.joining("\n"));
    }

    private String pdfText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }
}
