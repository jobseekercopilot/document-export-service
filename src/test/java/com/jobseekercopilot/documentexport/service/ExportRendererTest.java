package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRendererTest {

    @Test
    void docxExportCreatesReadableDocumentWithTitleAndParagraphs() throws Exception {
        GeneratedDocumentResponse document = document();

        byte[] bytes = docxExportService().export(document);

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
        byte[] bytes = docxExportService().export(document());

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

        byte[] bytes = docxExportService().export(document);

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
        byte[] bytes = pdfExportService().export(document());

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
        byte[] bytes = pdfExportService().export(coverLetter());

        assertTrue(bytes.length > 0);
        assertEquals("%PDF", new String(bytes, 0, 4));
        String text = pdfText(bytes);
        assertTrue(text.contains("Alex Candidate"));
        assertTrue(text.contains("Application for Developer"));
        assertTrue(text.contains("Dear Hiring Manager,"));
        assertTrue(text.contains("Kind regards"));
        assertTrue(text.contains(DocumentTemplate.BRAND_FOOTER));
    }

    @Test
    void docxPreservesUkUnicodeHeadingsLinksAndApprovedFontPolicy()
            throws Exception {
        byte[] bytes = docxExportService().export(accessibleDocument());

        try (XWPFDocument docx =
                     new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = bodyText(docx);
            assertTrue(text.contains("Zoë O’Connor"));
            assertTrue(text.contains("Bristol, BS1 5AH"));
            assertTrue(text.contains("Māori localisation"));
            assertTrue(text.contains("naïve façade; £95k"));
            assertTrue(docx.getParagraphs().stream().anyMatch(
                    paragraph -> "Personal Summary".equals(
                            paragraph.getText())
                            && "Heading1".equals(
                                    paragraph.getStyleID())));

            var runs = docx.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .toList();
            assertTrue(runs.stream().allMatch(run ->
                    ExportFontProvider.DOCX_FONT_FAMILY.equals(
                            run.getFontFamily())));
            assertTrue(runs.stream().allMatch(
                    ExportRendererTest::hasUkLanguage));
            assertTrue(runs.stream()
                    .filter(XWPFHyperlinkRun.class::isInstance)
                    .map(XWPFHyperlinkRun.class::cast)
                    .map(run -> run.getHyperlink(docx))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(link ->
                            "https://example.test/zoe".equals(
                                    link.getURL())));
        }

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                assertTrue(
                        !name.endsWith(".ttf")
                                && !name.endsWith(".otf")
                                && !name.endsWith(".woff")
                                && !name.endsWith(".woff2"),
                        "DOCX must not embed an unreviewed font asset: "
                                + name);
            }
        }
    }

    @Test
    void pdfPreservesReadingOrderLinksTagsAndEmbeddedApprovedFont()
            throws Exception {
        byte[] bytes = pdfExportService().export(accessibleDocument());

        String text = pdfText(bytes);
        assertInOrder(
                text,
                "Zoë O’Connor",
                "Personal Summary",
                "Māori localisation",
                DocumentTemplate.BRAND_FOOTER);
        assertTrue(text.contains("naïve façade; £95k"));

        PdfReader reader = new PdfReader(bytes);
        try {
            assertNotNull(reader.getCatalog().get(
                    PdfName.STRUCTTREEROOT));
            assertEquals(
                    "en-GB",
                    reader.getCatalog()
                            .getAsString(PdfName.LANG)
                            .toUnicodeString());
            assertTrue(hasHttpsLink(reader, 1));
            assertTrue(hasEmbeddedDejaVuFont(reader, 1));
        } finally {
            reader.close();
        }
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

    private DocxExportService docxExportService() {
        DocumentExportLimits limits = new DocumentExportLimits();
        return new DocxExportService(new RenderBudget(limits));
    }

    private PdfExportService pdfExportService() {
        DocumentExportLimits limits = new DocumentExportLimits();
        return new PdfExportService(
                new RenderBudget(limits),
                new ExportFontProvider(limits));
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

    private GeneratedDocumentResponse accessibleDocument() {
        return new GeneratedDocumentResponse()
                .id(UUID.randomUUID())
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Zoë O’Connor
                        zoe@example.com
                        Bristol, BS1 5AH

                        Personal Summary
                        Built cafés and public APIs across the UK — accessibility first.
                        Portfolio: https://example.test/zoe.

                        Core Skills
                        - Māori localisation: naïve façade; £95k.
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

    private static boolean hasUkLanguage(XWPFRun run) {
        if (!run.getCTR().isSetRPr()
                || run.getCTR().getRPr().sizeOfLangArray() == 0) {
            return false;
        }
        return "en-GB".equals(
                run.getCTR().getRPr().getLangArray(0).getVal());
    }

    private void assertInOrder(String text, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = text.indexOf(value);
            assertTrue(
                    current > previous,
                    "Expected readable text order for "
                            + value
                            + " in: "
                            + text);
            previous = current;
        }
    }

    private boolean hasHttpsLink(PdfReader reader, int page) {
        PdfArray annotations =
                reader.getPageN(page).getAsArray(PdfName.ANNOTS);
        if (annotations == null) {
            return false;
        }
        for (PdfObject object : annotations.getElements()) {
            PdfDictionary annotation =
                    (PdfDictionary) PdfReader.getPdfObject(object);
            PdfDictionary action = annotation.getAsDict(PdfName.A);
            PdfString uri = action == null
                    ? null
                    : action.getAsString(PdfName.URI);
            if (uri != null
                    && "https://example.test/zoe".equals(
                            uri.toUnicodeString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEmbeddedDejaVuFont(PdfReader reader, int page) {
        PdfDictionary resources =
                reader.getPageN(page).getAsDict(PdfName.RESOURCES);
        PdfDictionary fonts = resources == null
                ? null
                : resources.getAsDict(PdfName.FONT);
        if (fonts == null) {
            return false;
        }
        return fonts.getKeys().stream().anyMatch(key -> {
            PdfDictionary font =
                    (PdfDictionary) PdfReader.getPdfObject(
                            fonts.get(key));
            if (font == null) {
                return false;
            }
            String name = String.valueOf(font.get(PdfName.BASEFONT));
            PdfArray descendants =
                    font.getAsArray(PdfName.DESCENDANTFONTS);
            PdfDictionary descendant = descendants == null
                    || descendants.isEmpty()
                    ? null
                    : (PdfDictionary) PdfReader.getPdfObject(
                            descendants.getPdfObject(0));
            PdfDictionary descriptor = descendant == null
                    ? null
                    : descendant.getAsDict(PdfName.FONTDESCRIPTOR);
            return name.contains("DejaVuSans")
                    && descriptor != null
                    && descriptor.get(PdfName.FONTFILE2) != null
                    && font.get(PdfName.TOUNICODE) != null;
        });
    }
}
