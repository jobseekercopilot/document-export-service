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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
            assertTrue(text.contains("Java: Built APIs"));
            assertTrue(docx.getParagraphs().stream().anyMatch(
                    paragraph -> "Java: Built APIs".equals(
                            paragraph.getText())
                            && "ListParagraph".equals(
                                    paragraph.getStyleID())
                            && paragraph.getNumID() != null));
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
        assertTrue(!text.contains(DocumentTemplate.BRAND_FOOTER));
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
        assertTrue(!text.contains(DocumentTemplate.BRAND_FOOTER));
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
                    .map(link -> link.getURL())
                    .toList()
                    .equals(List.of("https://example.test/zoe")));
            assertEquals(
                    "7",
                    docx.getProperties()
                            .getCoreProperties().getVersion());
            assertMetadataIsPrivacyBounded(
                    Map.of(
                            "title", docx.getProperties()
                                    .getCoreProperties().getTitle(),
                            "author", docx.getProperties()
                                    .getCoreProperties().getCreator(),
                            "subject", docx.getProperties()
                                    .getCoreProperties().getSubject(),
                            "keywords", docx.getProperties()
                                    .getCoreProperties().getKeywords(),
                            "version", docx.getProperties()
                                    .getCoreProperties().getVersion()));
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
                "Māori localisation");
        assertTrue(text.contains("naïve façade; £95k"));

        PdfReader reader = new PdfReader(bytes);
        try {
            assertNotNull(reader.getCatalog().get(
                    PdfName.STRUCTTREEROOT));
            List<PdfName> structureRoles = pdfStructureRoles(
                    reader.getCatalog().get(
                            PdfName.STRUCTTREEROOT));
            assertTrue(structureRoles.contains(PdfName.H1));
            assertTrue(structureRoles.contains(PdfName.L));
            assertTrue(structureRoles.contains(PdfName.LI));
            assertEquals(
                    "en-GB",
                    reader.getCatalog()
                            .getAsString(PdfName.LANG)
                            .toUnicodeString());
            assertEquals(
                    List.of("https://example.test/zoe"),
                    pdfLinks(reader));
            assertTrue(hasEmbeddedDejaVuFont(reader, 1));
            assertEquals("7", reader.getInfo().get("DocumentVersion"));
            assertMetadataIsPrivacyBounded(reader.getInfo());
        } finally {
            reader.close();
        }
    }

    @Test
    void extractedHeadingListAndUnicodeTextIsEquivalentAcrossFormats()
            throws Exception {
        GeneratedDocumentResponse document = accessibleDocument();
        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        String docxText;
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            docxText = bodyText(docx);
            assertTrue(docx.getParagraphs().stream().anyMatch(
                    paragraph -> "Personal Summary".equals(
                            paragraph.getText())
                            && "Heading1".equals(
                                    paragraph.getStyleID())));
            assertTrue(docx.getParagraphs().stream().anyMatch(
                    paragraph -> "Māori localisation: naïve façade; £95k."
                            .equals(paragraph.getText())
                            && paragraph.getNumID() != null));
        }

        String pdfText = pdfText(pdfBytes);
        assertTrue(pdfText.contains("\u2022"));
        assertEquals(
                normalizedExtractedText(docxText),
                normalizedExtractedText(pdfText));
    }

    @Test
    void improvedCvSectionNamesRenderAsGovernedStructureAcrossFormats()
            throws Exception {
        List<String> expectedHeadings = List.of(
                "Technical profile",
                "Projects",
                "Technical skills",
                "Education and qualifications",
                "Additional experience",
                "Work history");
        GeneratedDocumentResponse document = improvedSectionDocument();
        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        String docxText;
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            docxText = bodyText(docx);
            assertEquals(
                    expectedHeadings,
                    docx.getParagraphs().stream()
                            .filter(paragraph -> "Heading1".equals(
                                    paragraph.getStyleID()))
                            .map(paragraph -> paragraph.getText())
                            .toList());
        }

        String pdfText = pdfText(pdfBytes);
        PdfReader reader = new PdfReader(pdfBytes);
        try {
            List<PdfName> roles = pdfStructureRoles(
                    reader.getCatalog().get(PdfName.STRUCTTREEROOT));
            assertEquals(
                    expectedHeadings.size(),
                    roles.stream()
                            .filter(PdfName.H2::equals)
                            .count());
        } finally {
            reader.close();
        }
        assertInOrder(pdfText, expectedHeadings.toArray(String[]::new));
        assertEquals(
                normalizedExtractedText(docxText),
                normalizedExtractedText(pdfText));
    }

    @Test
    void professionalProfileProducerHeadingRemainsStructural()
            throws Exception {
        GeneratedDocumentResponse document =
                professionalProfileDocument();
        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        String docxText;
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            docxText = bodyText(docx);
            assertEquals(
                    "Tailored CV",
                    docx.getParagraphs().get(0).getText());
            assertTrue(docx.getParagraphs().stream().anyMatch(
                    paragraph -> "Professional Profile".equals(
                            paragraph.getText())
                            && "Heading1".equals(
                                    paragraph.getStyleID())));
        }

        String pdfText = pdfText(pdfBytes);
        PdfReader reader = new PdfReader(pdfBytes);
        try {
            assertEquals(
                    1,
                    pdfStructureRoles(
                            reader.getCatalog().get(
                                    PdfName.STRUCTTREEROOT)).stream()
                            .filter(PdfName.H2::equals)
                            .count());
        } finally {
            reader.close();
        }
        assertEquals(
                normalizedExtractedText(docxText),
                normalizedExtractedText(pdfText));
    }

    @Test
    void unknownMarkupAndUnsafeSchemesRemainInertPlainText()
            throws Exception {
        GeneratedDocumentResponse document = unknownContentDocument();
        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            assertTrue(bodyText(docx).contains(
                    "<custom data-note=\"keep plain\"> & "
                            + "javascript:alert(1)"));
            assertEquals(
                    0,
                    docx.getParagraphs().stream()
                            .flatMap(paragraph ->
                                    paragraph.getRuns().stream())
                            .filter(XWPFHyperlinkRun.class::isInstance)
                            .count());
        }

        String pdfText = pdfText(pdfBytes);
        assertTrue(pdfText.contains(
                "<custom data-note=\"keep plain\"> & "
                        + "javascript:alert(1)"));
        PdfReader reader = new PdfReader(pdfBytes);
        try {
            assertEquals(List.of(), pdfLinks(reader));
        } finally {
            reader.close();
        }
    }

    @Test
    void richCvContentFlowsAcrossPagesWithoutTruncationOrPadding()
            throws Exception {
        List<String> evidenceItems = IntStream.rangeClosed(1, 120)
                .mapToObj(index -> "Evidence item %03d: designed, delivered and validated synthetic capability."
                        .formatted(index))
                .toList();
        GeneratedDocumentResponse document =
                richMultiPageDocument(evidenceItems);
        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        String docxText;
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            docxText = bodyText(docx);
            assertEquals(
                    evidenceItems,
                    docx.getParagraphs().stream()
                            .filter(paragraph -> paragraph.getNumID() != null)
                            .map(paragraph -> paragraph.getText())
                            .toList());
        }

        PdfReader reader = new PdfReader(pdfBytes);
        try {
            assertTrue(reader.getNumberOfPages() > 1);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertTrue(
                        !normalizedExtractedText(
                                extractor.getTextFromPage(page)).isBlank(),
                        "Rendered PDF must not contain a padded blank page: "
                                + page);
            }
            List<PdfName> roles = pdfStructureRoles(
                    reader.getCatalog().get(
                            PdfName.STRUCTTREEROOT));
            assertTrue(roles.contains(PdfName.H1));
            assertTrue(roles.contains(PdfName.H2));
            assertTrue(roles.contains(PdfName.L));
            assertEquals(
                    120,
                    roles.stream()
                            .filter(PdfName.LI::equals)
                            .count());
        } finally {
            reader.close();
        }

        String pdfText = pdfText(pdfBytes);
        assertInOrder(pdfText, evidenceItems.toArray(String[]::new));
        String expectedText = expectedRichExtractedText(evidenceItems);
        assertEquals(
                expectedText,
                normalizedExtractedText(docxText));
        assertEquals(
                expectedText,
                normalizedExtractedText(pdfText));
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
                .version(7)
                .userId("private-user-123")
                .jobId("private-job-456")
                .applicationId("private-application-789")
                .createdBy("private-owner@example.test")
                .originalFilename("private-original-name.docx")
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
                        Unsafe references stay plain: http://example.test/old javascript:alert(1) https://user:secret@example.test/private.

                        Core Skills
                        - Māori localisation: naïve façade; £95k.
                        """);
    }

    private GeneratedDocumentResponse improvedSectionDocument() {
        return new GeneratedDocumentResponse()
                .id(UUID.fromString(
                        "10000000-0000-0000-0000-000000000001"))
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Alex Candidate
                        alex@example.test
                        London

                        Technical profile
                        Builds accessible services with deterministic delivery.

                        Projects
                        Project Atlas
                        Delivered a governed migration.

                        Technical skills
                        - Java
                        - PostgreSQL

                        Education and qualifications
                        BSc Computer Science.

                        Additional experience
                        Community technology mentor.

                        Work history
                        Software Engineer
                        Built reliable services.
                        """);
    }

    private GeneratedDocumentResponse professionalProfileDocument() {
        return new GeneratedDocumentResponse()
                .id(UUID.fromString(
                        "10000000-0000-0000-0000-000000000003"))
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Professional Profile
                        Builds reliable services with governed delivery.
                        """);
    }

    private GeneratedDocumentResponse richMultiPageDocument(
            List<String> evidenceItems) {
        String bullets = evidenceItems.stream()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
        return new GeneratedDocumentResponse()
                .id(UUID.fromString(
                        "10000000-0000-0000-0000-000000000002"))
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Alex Candidate
                        alex@example.test
                        London

                        Technical profile
                        Accessible platform engineer with UK delivery experience.
                        Portfolio: https://example.test/alex.

                        Projects
                        Project Atlas
                        Delivered a synthetic multi-service migration.

                        Technical skills
                        %s

                        Education and qualifications
                        BSc Computer Science with first-class honours.

                        Additional experience
                        Supported community technology workshops and mentoring.
                        """.formatted(bullets));
    }

    private String expectedRichExtractedText(
            List<String> evidenceItems) {
        return normalizedExtractedText("""
                Alex Candidate
                alex@example.test | London
                Technical profile
                Accessible platform engineer with UK delivery experience.
                Portfolio: https://example.test/alex.
                Projects
                Project Atlas
                Delivered a synthetic multi-service migration.
                Technical skills
                %s
                Education and qualifications
                BSc Computer Science with first-class honours.
                Additional experience
                Supported community technology workshops and mentoring.
                """.formatted(String.join("\n", evidenceItems)));
    }

    private GeneratedDocumentResponse unknownContentDocument() {
        return new GeneratedDocumentResponse()
                .id(UUID.randomUUID())
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Alex Candidate

                        Personal Summary
                        <custom data-note="keep plain"> & javascript:alert(1)
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

    private List<String> pdfLinks(PdfReader reader) {
        java.util.ArrayList<String> links = new java.util.ArrayList<>();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            PdfArray annotations =
                    reader.getPageN(page).getAsArray(PdfName.ANNOTS);
            if (annotations == null) {
                continue;
            }
            for (PdfObject object : annotations.getElements()) {
                PdfDictionary annotation =
                        (PdfDictionary) PdfReader.getPdfObject(object);
                PdfDictionary action = annotation.getAsDict(PdfName.A);
                PdfString uri = action == null
                        ? null
                        : action.getAsString(PdfName.URI);
                if (uri != null) {
                    links.add(uri.toUnicodeString());
                }
            }
        }
        return List.copyOf(links);
    }

    private List<PdfName> pdfStructureRoles(PdfObject object) {
        java.util.ArrayList<PdfName> roles =
                new java.util.ArrayList<>();
        collectPdfStructureRoles(object, roles);
        return List.copyOf(roles);
    }

    private void collectPdfStructureRoles(
            PdfObject object,
            List<PdfName> roles) {
        PdfObject resolved = PdfReader.getPdfObject(object);
        if (resolved instanceof PdfArray array) {
            for (PdfObject child : array.getElements()) {
                collectPdfStructureRoles(child, roles);
            }
            return;
        }
        if (!(resolved instanceof PdfDictionary dictionary)) {
            return;
        }
        PdfName role = dictionary.getAsName(PdfName.S);
        if (role != null) {
            roles.add(role);
        }
        PdfObject children = dictionary.get(PdfName.K);
        if (children != null) {
            collectPdfStructureRoles(children, roles);
        }
    }

    private String normalizedExtractedText(String value) {
        return value
                .replace(DocumentTemplate.BRAND_FOOTER, "")
                .replace("\u2022", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void assertMetadataIsPrivacyBounded(Map<String, String> metadata) {
        String values = metadata.values().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" "));
        assertTrue(values.contains("Tailored CV"));
        assertTrue(values.contains(DocumentMetadata.AUTHOR));
        assertTrue(values.contains("7"));
        assertTrue(!values.contains("private-user-123"));
        assertTrue(!values.contains("private-job-456"));
        assertTrue(!values.contains("private-application-789"));
        assertTrue(!values.contains("private-owner@example.test"));
        assertTrue(!values.contains("private-original-name.docx"));
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
