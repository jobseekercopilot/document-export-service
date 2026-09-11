package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.dto.ProfessionalContact;
import com.jobseekercopilot.documentexport.dto.ProfessionalLink;
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
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
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
    void professionalContactRendersAsClickableLabelledLinksAcrossFormats()
            throws Exception {
        ProfessionalContact contact = new ProfessionalContact(
                "+44 20 7946 0958",
                List.of(
                        new ProfessionalLink(
                                "GitHub",
                                "https://github.com/example-developer"),
                        new ProfessionalLink(
                                "Portfolio",
                                "https://portfolio.example.test")));

        byte[] docxBytes = docxExportService().export(document(), contact);
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            XWPFParagraph header = docx.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains(
                            "+44 20 7946 0958"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(header.getText().contains("GitHub"));
            assertTrue(header.getText().contains("Portfolio"));
            assertTrue(header.getText().contains(
                    "GitHub: https://github.com/example-developer"));
            assertTrue(header.getText().contains(
                    "Portfolio: https://portfolio.example.test"));
            assertEquals(
                    List.of(
                            "https://github.com/example-developer",
                            "https://portfolio.example.test"),
                    header.getRuns().stream()
                            .filter(XWPFHyperlinkRun.class::isInstance)
                            .map(XWPFHyperlinkRun.class::cast)
                            .map(XWPFHyperlinkRun::text)
                            .toList());
            assertEquals(
                    List.of(
                            "https://github.com/example-developer",
                            "https://portfolio.example.test"),
                    header.getRuns().stream()
                            .filter(XWPFHyperlinkRun.class::isInstance)
                            .map(XWPFHyperlinkRun.class::cast)
                            .map(run -> run.getHyperlink(docx))
                            .filter(java.util.Objects::nonNull)
                            .map(link -> link.getURL())
                            .toList());
        }

        byte[] pdfBytes = pdfExportService().export(document(), contact);
        String text = pdfText(pdfBytes);
        String compactText = text.replaceAll("\\s+", "");
        assertTrue(text.contains("+44 20 7946 0958"));
        assertTrue(compactText.contains(
                "GitHub:https://github.com/example-developer"));
        assertTrue(compactText.contains(
                "Portfolio:https://portfolio.example.test"));
        PdfReader reader = new PdfReader(pdfBytes);
        try {
            assertEquals(
                    java.util.Set.of(
                            "https://github.com/example-developer",
                            "https://portfolio.example.test"),
                    new java.util.LinkedHashSet<>(pdfLinks(reader)));
        } finally {
            reader.close();
        }
    }

    @Test
    void multipleVisibleProfessionalLinksWrapInsideDocumentBounds()
            throws Exception {
        ProfessionalContact contact = new ProfessionalContact(
                "+44 20 7946 0958",
                List.of(
                        new ProfessionalLink(
                                "GitHub",
                                "https://github.example.test/example-developer"),
                        new ProfessionalLink(
                                "Portfolio",
                                "https://portfolio.example.test/case-studies"),
                        new ProfessionalLink(
                                "Writing",
                                "https://writing.example.test/software-delivery"),
                        new ProfessionalLink(
                                "Community",
                                "https://community.example.test/open-source")));

        byte[] docxBytes = docxExportService().export(document(), contact);
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            XWPFParagraph header = docx.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains(
                            "GitHub: https://github.example.test/example-developer"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(header.getText().contains(
                    "Community: https://community.example.test/open-source"));
            assertEquals(
                    4,
                    header.getRuns().stream()
                            .filter(XWPFHyperlinkRun.class::isInstance)
                            .count());
            assertTrue(!header.isPageBreak());
        }

        PdfReader reader = new PdfReader(
                pdfExportService().export(document(), contact));
        try {
            List<PdfLinkAnnotation> annotations = pdfLinkAnnotations(reader);
            assertEquals(
                    java.util.Set.of(
                            "https://github.example.test/example-developer",
                            "https://portfolio.example.test/case-studies",
                            "https://writing.example.test/software-delivery",
                            "https://community.example.test/open-source"),
                    annotations.stream()
                            .map(PdfLinkAnnotation::url)
                            .collect(java.util.stream.Collectors.toSet()));
            assertTrue(annotations.size() >= 4);
            assertTrue(
                    annotations.stream()
                            .map(annotation -> Math.round(annotation.bottom()))
                            .distinct()
                            .count() >= 2,
                    "Multiple visible links should wrap across header lines");
            for (PdfLinkAnnotation annotation : annotations) {
                assertTrue(annotation.left() >= PdfExportService.LEFT - 1);
                assertTrue(annotation.right() <= PdfExportService.RIGHT + 1);
                assertTrue(annotation.bottom() >= PdfExportService.BOTTOM - 1);
                assertTrue(annotation.top() <= PdfExportService.TOP + 1);
            }
        } finally {
            reader.close();
        }
    }

    @Test
    void professionalContactDoesNotDuplicateExistingGeneratedLink() throws Exception {
        GeneratedDocumentResponse generated = document().content("""
                Tailored CV

                Alex Candidate
                alex@example.com
                https://github.com/example-developer

                Personal Summary
                First paragraph.
                """);
        ProfessionalContact contact = new ProfessionalContact(
                null,
                List.of(new ProfessionalLink(
                        "GitHub",
                        "https://github.com/example-developer")));

        byte[] bytes = pdfExportService().export(generated, contact);
        PdfReader reader = new PdfReader(bytes);
        try {
            assertEquals(
                    List.of("https://github.com/example-developer"),
                    pdfLinks(reader));
        } finally {
            reader.close();
        }
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
            assertTrue(docx.getParagraphs().stream()
                    .noneMatch(XWPFParagraph::isPageBreak));
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
            assertTrue(docx.getParagraphs().stream()
                    .noneMatch(XWPFParagraph::isPageBreak));
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

    @Test
    void internalEvidenceMetadataIsExcludedFromBothPublicFormats()
            throws Exception {
        GeneratedDocumentResponse document = document().content("""
                Tailored CV

                Alex Candidate
                alex@example.com
                London

                Professional Profile
                Builds reliable services. (evidenceIds: ["PROFILE.SKILL.1"])

                Technical Skills
                Java · Spring Boot
                """);

        byte[] docxBytes = docxExportService().export(document);
        byte[] pdfBytes = pdfExportService().export(document);

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxBytes))) {
            assertTrue(bodyText(docx).contains("Builds reliable services."));
            assertTrue(!bodyText(docx).contains("evidenceIds"));
            assertTrue(!bodyText(docx).contains("PROFILE.SKILL.1"));
        }
        String pdfText = pdfText(pdfBytes);
        assertTrue(pdfText.contains("Builds reliable services."));
        assertTrue(!pdfText.contains("evidenceIds"));
        assertTrue(!pdfText.contains("PROFILE.SKILL.1"));
    }

    @Test
    void flatSkillsAndEntrySeparatorsAreMoreReadableAcrossFormats()
            throws Exception {
        GeneratedDocumentResponse document = document().content("""
                Tailored CV

                Alex Candidate

                Technical Skills
                Java, Spring Boot, Angular, AWS

                Professional Experience
                Software Engineer - Example Ltd
                January 2021 – Present
                - Delivered reliable services.
                """);

        String pdfText = pdfText(pdfExportService().export(document));
        assertTrue(pdfText.contains("Java · Spring Boot · Angular · AWS"));
        assertTrue(pdfText.contains("Software Engineer — Example Ltd"));

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxExportService().export(document)))) {
            String docxText = bodyText(docx);
            assertTrue(docxText.contains("Java · Spring Boot · Angular · AWS"));
            assertTrue(docxText.contains("Software Engineer — Example Ltd"));
        }
    }

    @Test
    void pdfKeepsNormalSizedBulletOnOnePage() throws Exception {
        String filler = IntStream.rangeClosed(1, 27)
                .mapToObj(index -> "- Supporting delivery point %02d with concise evidence."
                        .formatted(index))
                .collect(Collectors.joining("\n"));
        String target = "TARGETSTART "
                + "implemented a carefully governed cross-service migration with automated testing, "
                + "accessible output, deterministic recovery, operational diagnostics and documented "
                + "release controls while preserving the complete user journey TARGETEND.";
        GeneratedDocumentResponse document = document().content("""
                Tailored CV

                Alex Candidate
                alex@example.com
                London

                Professional Experience
                Platform Engineer — Example Ltd
                January 2021 – Present
                %s
                - %s
                """.formatted(filler, target));

        List<String> pages = pdfPageTexts(
                pdfExportService().export(document));

        List<String> targetPages = pages.stream()
                .filter(page -> page.contains("TARGETSTART")
                        || page.contains("TARGETEND"))
                .toList();
        assertEquals(1, targetPages.size());
        assertTrue(targetPages.get(0).contains("TARGETSTART"));
        assertTrue(targetPages.get(0).contains("TARGETEND"));
    }

    @Test
    void coverLetterClosingAndSignatureRemainOnTheSamePdfPage()
            throws Exception {
        String body = IntStream.rangeClosed(1, 18)
                .mapToObj(index -> ("Paragraph %02d explains relevant delivery experience, "
                        + "collaboration, testing and maintainable implementation in clear professional prose. "
                        + "It connects confirmed evidence to the advertised role without inventing facts.")
                        .formatted(index))
                .collect(Collectors.joining("\n\n"));
        GeneratedDocumentResponse document = coverLetter().content("""
                Developer Cover Letter

                Alex Candidate
                alex@example.com
                London

                Application for Developer at Example Ltd

                Dear Hiring Manager,

                %s

                FINAL-BODY I would welcome the opportunity to bring this relevant delivery experience to the team.

                FINAL-CLOSING Thank you for considering my application.

                Yours faithfully,
                Alex Candidate
                """.formatted(body));

        List<String> pages = pdfPageTexts(
                pdfExportService().export(document));
        String signaturePage = pages.stream()
                .filter(page -> page.contains("Yours faithfully"))
                .findFirst()
                .orElseThrow();

        assertTrue(pages.size() > 1);
        assertTrue(signaturePage.contains("Paragraph 18"));
        assertTrue(signaturePage.contains("FINAL-BODY"));
        assertTrue(signaturePage.contains("FINAL-CLOSING"));
        assertTrue(signaturePage.contains("Alex Candidate"));
    }

    @Test
    void pdfDoesNotDropEntryHeadingAtAPageBoundary()
            throws Exception {
        for (int fillerCount = 1; fillerCount <= 36; fillerCount++) {
            String filler = IntStream.rangeClosed(1, fillerCount)
                    .mapToObj(index -> "- Delivery evidence %02d covers implementation, testing and collaboration."
                            .formatted(index))
                    .collect(Collectors.joining("\n"));
            GeneratedDocumentResponse document = document().content("""
                    Tailored CV

                    Alex Candidate
                    alex@example.com
                    London

                    Professional Profile
                    Experienced engineer delivering reliable full-stack applications with tested services, accessible interfaces and maintainable cloud infrastructure for cross-functional product teams.

                    Technical Skills
                    Java · Spring Boot · Angular · AWS · Automated testing · CI/CD

                    Professional Experience
                    First Role — Example Ltd
                    January 2021 – December 2023
                    %s
                    BOUNDARY ROLE — Next Employer
                    January 2024 – Present
                    - BOUNDARY FIRST BULLET delivered a reliable service.
                    """.formatted(filler));

            List<String> pages = pdfPageTexts(
                    pdfExportService().export(document));
            String boundaryPage = pages.stream()
                    .filter(page -> page.contains("BOUNDARY ROLE")
                            || page.contains("BOUNDARY FIRST BULLET"))
                    .findFirst()
                    .orElseThrow();

            assertTrue(boundaryPage.contains("BOUNDARY ROLE"),
                    "Dropped boundary role with filler count " + fillerCount);
            assertTrue(boundaryPage.contains("January 2024"),
                    "Orphaned boundary date with filler count " + fillerCount);
            assertTrue(boundaryPage.contains("BOUNDARY FIRST BULLET"),
                    "Orphaned boundary bullet with filler count " + fillerCount);
        }
    }

    @Test
    void cvDoesNotCreateASparseSecondPageForAShortFinalSection()
            throws Exception {
        GeneratedDocumentResponse document = document().content("""
                Tailored CV

                Alex Candidate
                alex@example.com
                London

                Professional Profile
                Software Developer with experience in REST APIs, Spring Boot and Java. Designs microservices, builds accessible Angular features and works with product teams to deliver reliable releases.

                Technical Skills
                REST APIs · Spring Boot · Java · Angular · Microservices · TypeScript · Automated Testing · Docker · AWS · CI/CD · PostgreSQL

                Professional Experience
                Software Developer — BrightTech Solutions
                July 2021 – Present
                - Designs Java and Spring Boot microservices, builds Angular features and reviews code.
                - Reduced deployment time and introduced contract testing across six services.
                - Develops accessible cloud products used by operations teams.

                Software Engineering Intern — CodeBridge Ltd
                June 2020 – August 2020
                - Delivered TypeScript components and API integration tests in an Agile team.
                - Automated a repetitive regression check and supported sprint demonstrations.

                Selected Projects
                Application Delivery Platform — Lead developer
                January 2024 – June 2025
                Led a five-person project that joined Java services, Angular workflows and deployment telemetry into one secure delivery platform.
                - Delivered the first release on schedule with accessible keyboard workflows and automated tests.

                Education and Qualifications
                - AWS Certified Developer – Associate, Amazon Web Services, April 2025
                - BSc Computer Science, University of Birmingham, June 2021
                """);

        List<String> pages = pdfPageTexts(pdfExportService().export(document));

        assertEquals(1, pages.size());
        assertTrue(pages.get(0).contains("Education and Qualifications"));
        assertTrue(pages.get(0).contains("BSc Computer Science"));
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxExportService().export(document)))) {
            assertTrue(docx.getParagraphs().stream()
                    .noneMatch(XWPFParagraph::isPageBreak));
        }
    }

    @Test
    void sparseTwoPageCvUsesTheSameSemanticBoundaryAcrossFormats()
            throws Exception {
        GeneratedDocumentResponse document = sparseTwoPageCv();

        List<String> naturalPages = pdfPageTexts(
                pdfExportServiceWithoutSemanticBreaks().export(document));
        assertEquals(2, naturalPages.size());
        assertTrue(naturalPages.get(0).contains("Selected Projects"));
        assertTrue(!naturalPages.get(1).contains("Selected Projects"));
        assertTrue(naturalPages.get(1).contains(
                "Education and Qualifications"));

        List<String> pages = pdfPageTexts(pdfExportService().export(document));

        assertEquals(2, pages.size());
        assertTrue(pages.get(0).contains("Professional Experience"));
        assertTrue(!pages.get(0).contains("Selected Projects"));
        assertTrue(pages.get(1).contains("Selected Projects"));
        assertTrue(pages.get(1).contains("Application Delivery Platform"));
        assertTrue(pages.get(1).contains("Education and Qualifications"));
        assertTrue(pages.get(1).contains("BSc Computer Science"));

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxExportService().export(document)))) {
            List<XWPFParagraph> pageBreaks = docx.getParagraphs().stream()
                    .filter(XWPFParagraph::isPageBreak)
                    .toList();
            assertEquals(1, pageBreaks.size());
            assertEquals("Selected Projects", pageBreaks.get(0).getText());
        }
    }

    @Test
    void naturallyBalancedTwoPageCvDoesNotReceiveAForcedBreak()
            throws Exception {
        GeneratedDocumentResponse document = sparseTwoPageCv();
        String additionalEducation = IntStream.rangeClosed(1, 8)
                .mapToObj(index -> ("- Additional accredited module %02d in "
                        + "software delivery and collaborative engineering.")
                        .formatted(index))
                .collect(Collectors.joining("\n"));
        document.content(document.getContent()
                + "\n"
                + additionalEducation);

        assertEquals(
                2,
                pdfPageTexts(pdfExportService().export(document)).size());
        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxExportService().export(document)))) {
            assertTrue(docx.getParagraphs().stream()
                    .noneMatch(XWPFParagraph::isPageBreak));
        }
    }

    @Test
    void docxEmitsPaginationControlsForHeadingsBulletsAndClosing()
            throws Exception {
        GeneratedDocumentResponse document = document().content("""
                Tailored CV

                Alex Candidate

                Selected Projects

                Job Seeker Copilot — Founder
                May 2026 – Present
                - Delivered a working platform.
                """);

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(docxExportService().export(document)))) {
            XWPFParagraph heading = docx.getParagraphs().stream()
                    .filter(paragraph -> "Selected Projects".equals(paragraph.getText()))
                    .findFirst()
                    .orElseThrow();
            XWPFParagraph bullet = docx.getParagraphs().stream()
                    .filter(paragraph -> "Delivered a working platform.".equals(paragraph.getText()))
                    .findFirst()
                    .orElseThrow();

            assertTrue(heading.isKeepNext());
            assertTrue(heading.getCTP().getPPr().isSetKeepLines());
            assertTrue(bullet.getCTP().getPPr().isSetKeepLines());
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
        // Use a generous wall-clock render budget so this pagination test does
        // not flake under heavy parallel CI load (the strict production budget
        // is exercised deterministically in RenderBudgetTest).
        limits.setRenderMaxDuration(Duration.ofMinutes(5));
        ExportFontProvider fontProvider = new ExportFontProvider(limits);
        PdfParagraphFactory paragraphs =
                new PdfParagraphFactory(fontProvider);
        return new DocxExportService(
                new RenderBudget(limits),
                new CvPaginationPlanner(paragraphs));
    }

    private PdfExportService pdfExportService() {
        DocumentExportLimits limits = new DocumentExportLimits();
        // Use a generous wall-clock render budget so this pagination test does
        // not flake under heavy parallel CI load (the strict production budget
        // is exercised deterministically in RenderBudgetTest).
        limits.setRenderMaxDuration(Duration.ofMinutes(5));
        ExportFontProvider fontProvider = new ExportFontProvider(limits);
        PdfParagraphFactory paragraphs =
                new PdfParagraphFactory(fontProvider);
        return new PdfExportService(
                new RenderBudget(limits),
                paragraphs,
                new CvPaginationPlanner(paragraphs));
    }

    private PdfExportService pdfExportServiceWithoutSemanticBreaks() {
        DocumentExportLimits limits = new DocumentExportLimits();
        // Use a generous wall-clock render budget so this pagination test does
        // not flake under heavy parallel CI load (the strict production budget
        // is exercised deterministically in RenderBudgetTest).
        limits.setRenderMaxDuration(Duration.ofMinutes(5));
        ExportFontProvider fontProvider = new ExportFontProvider(limits);
        PdfParagraphFactory paragraphs =
                new PdfParagraphFactory(fontProvider);
        CvPaginationPlanner disabledPlanner =
                new CvPaginationPlanner(paragraphs) {
                    @Override
                    public Plan plan(DocumentTemplate template) {
                        return Plan.none();
                    }
                };
        return new PdfExportService(
                new RenderBudget(limits),
                paragraphs,
                disabledPlanner);
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

    private GeneratedDocumentResponse sparseTwoPageCv() {
        return new GeneratedDocumentResponse()
                .id(UUID.fromString(
                        "10000000-0000-0000-0000-000000000004"))
                .documentType(
                        GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .title("Tailored CV")
                .content("""
                        Tailored CV

                        Alex Candidate
                        alex@example.test
                        London

                        Professional Profile
                        Full-stack software developer delivering reliable Java services and accessible Angular interfaces across collaborative, safety-conscious product teams.

                        Technical Skills
                        Java · Spring Boot · Angular · TypeScript · REST APIs · Microservices · Docker · AWS · PostgreSQL · Automated Testing · Playwright · CI/CD

                        Professional Experience
                        Full-Stack Software Developer — Resonate Systems
                        January 2021 – August 2024
                        - Delivered tested Java and Spring microservices with Angular interfaces while collaborating across engineering and product disciplines.
                        - Added unit, integration and contract checks to support dependable releases of safety-conscious operational software.
                        - Mentored new engineers and supported apprentices building a substantial Angular training application.

                        Website Administrator — Example Digital
                        August 2024 – January 2025
                        - Improved client websites, analysed lead-generation data and communicated clearly with customers about practical changes.
                        - Used structured sales reporting to identify useful follow-up actions while keeping client records accurate.

                        Community Coordinator — Example Community Centre
                        December 2024 – September 2025
                        - Coordinated services, maintained accurate records and supported residents through dependable operational routines.

                        Selected Projects
                        Application Delivery Platform — Lead developer
                        May 2025 – Present
                        - Designed a multi-service Java and Angular product with provider integrations, document generation and end-to-end tests.
                        - Owned API contracts, containerised development and cross-service quality decisions from discovery through pre-beta testing.

                        Codecademy Docs — Open-source contributor
                        December 2021
                        - Authored educational Java material explaining five established creational design patterns for software learners.

                        Education and Qualifications
                        - AWS re/Start Programme, July 2021
                        - AWS Certified Cloud Practitioner, July 2021
                        - BSc Computer Science, University of Birmingham, June 2021
                        - Diploma in Software Development, Example College, June 2019
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

    private List<String> pdfPageTexts(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            java.util.ArrayList<String> pages = new java.util.ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page));
            }
            return List.copyOf(pages);
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

    private List<PdfLinkAnnotation> pdfLinkAnnotations(PdfReader reader) {
        java.util.ArrayList<PdfLinkAnnotation> links = new java.util.ArrayList<>();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            PdfArray annotations = reader.getPageN(page).getAsArray(PdfName.ANNOTS);
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
                PdfArray bounds = annotation.getAsArray(PdfName.RECT);
                if (uri == null || bounds == null || bounds.size() != 4) {
                    continue;
                }
                float x1 = bounds.getAsNumber(0).floatValue();
                float y1 = bounds.getAsNumber(1).floatValue();
                float x2 = bounds.getAsNumber(2).floatValue();
                float y2 = bounds.getAsNumber(3).floatValue();
                links.add(new PdfLinkAnnotation(
                        uri.toUnicodeString(),
                        Math.min(x1, x2),
                        Math.min(y1, y2),
                        Math.max(x1, x2),
                        Math.max(y1, y2)));
            }
        }
        return List.copyOf(links);
    }

    private record PdfLinkAnnotation(
            String url,
            float left,
            float bottom,
            float right,
            float top) {
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
