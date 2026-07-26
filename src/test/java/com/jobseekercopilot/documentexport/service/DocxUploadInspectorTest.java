package com.jobseekercopilot.documentexport.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocxUploadInspectorTest {

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml"
                ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """;
    private static final String DOCUMENT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>Safe</w:t></w:r></w:p></w:body>
            </w:document>
            """;

    @Test
    void acceptsAWellFormedMacroFreeDocx() throws Exception {
        MockMultipartFile file = file(validDocx());

        assertDoesNotThrow(() -> inspector(new DocumentExportLimits())
                .inspect(file));
    }

    @Test
    void rejectsCompressedAndExpandedByteBudgetViolations()
            throws Exception {
        byte[] valid = validDocx();
        DocumentExportLimits compressedLimits =
                new DocumentExportLimits();
        compressedLimits.setUploadMaxCompressedBytes(valid.length - 1L);

        var compressed = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(compressedLimits).inspect(file(valid)));
        assertTrue(compressed.getMessage().contains("compressed byte"));

        DocumentExportLimits expandedLimits = new DocumentExportLimits();
        expandedLimits.setUploadMaxExpandedBytes(512);
        Map<String, byte[]> entries = baseEntries();
        entries.put(
                "word/media/large.txt",
                "A".repeat(4096).getBytes(StandardCharsets.UTF_8));

        var expanded = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(expandedLimits)
                        .inspect(file(zip(entries))));
        assertTrue(expanded.getMessage().contains("expanded byte"));
    }

    @Test
    void rejectsEntryCountAndCompressionRatioViolations()
            throws Exception {
        DocumentExportLimits entryLimits = new DocumentExportLimits();
        entryLimits.setUploadMaxEntries(2);
        Map<String, byte[]> countEntries = baseEntries();
        countEntries.put("word/styles.xml", "<styles/>".getBytes());

        var count = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(entryLimits)
                        .inspect(file(zip(countEntries))));
        assertTrue(count.getMessage().contains("entry-count"));

        DocumentExportLimits ratioLimits = new DocumentExportLimits();
        ratioLimits.setUploadMaxCompressionRatio(2);
        Map<String, byte[]> ratioEntries = baseEntries();
        ratioEntries.put(
                "word/media/compressed.txt",
                "B".repeat(8192).getBytes(StandardCharsets.UTF_8));

        var ratio = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(ratioLimits)
                        .inspect(file(zip(ratioEntries))));
        assertTrue(ratio.getMessage().contains("compression-ratio"));
    }

    @Test
    void rejectsTraversalAndDuplicateEntryNames() throws Exception {
        Map<String, byte[]> traversalEntries = baseEntries();
        traversalEntries.put(
                "../outside.xml",
                "<outside/>".getBytes(StandardCharsets.UTF_8));

        var traversal = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(traversalEntries))));
        assertTrue(traversal.getMessage().contains("unsafe ZIP entry"));

        byte[] duplicate = zipWithDuplicates(
                "[Content_Types].xml",
                "[content_types].xml");
        var duplicates = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(duplicate)));
        assertTrue(duplicates.getMessage().contains("duplicate ZIP"));
    }

    @Test
    void rejectsMacrosEmbeddedObjectsAndExternalRelationships()
            throws Exception {
        Map<String, byte[]> macroEntries = baseEntries();
        macroEntries.put("word/vbaProject.bin", new byte[] {1, 2, 3});
        var macro = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(macroEntries))));
        assertTrue(macro.getMessage().contains("active or embedded"));

        Map<String, byte[]> embeddedEntries = baseEntries();
        embeddedEntries.put(
                "word/embeddings/object1.bin",
                new byte[] {1});
        assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(embeddedEntries))));

        Map<String, byte[]> relationshipEntries = baseEntries();
        relationshipEntries.put(
                "word/_rels/document.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                    Target="https://outside.example.test"
                    TargetMode="External"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));
        var relationship = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(relationshipEntries))));
        assertTrue(relationship.getMessage().contains(
                "external relationship"));
    }

    @Test
    void rejectsMalformedOrIncompletePackages() throws Exception {
        Map<String, byte[]> malformed = baseEntries();
        malformed.put(
                "word/document.xml",
                "<not-word/>".getBytes(StandardCharsets.UTF_8));

        var malformedError = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(malformed))));
        assertTrue(malformedError.getMessage().contains(
                "structurally valid"));

        Map<String, byte[]> incomplete = new LinkedHashMap<>();
        incomplete.put(
                "[Content_Types].xml",
                CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        var incompleteError = assertThrows(
                IllegalArgumentException.class,
                () -> inspector(new DocumentExportLimits())
                        .inspect(file(zip(incomplete))));
        assertTrue(incompleteError.getMessage().contains(
                "required OOXML"));
    }

    private DocxUploadInspector inspector(DocumentExportLimits limits) {
        return new DocxUploadInspector(limits);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "safe.docx",
                DocumentExportService.DOCX_MIME_TYPE,
                bytes);
    }

    private byte[] validDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText("Safe synthetic document");
            document.write(output);
            return output.toByteArray();
        }
    }

    private Map<String, byte[]> baseEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "[Content_Types].xml",
                CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        entries.put(
                "word/document.xml",
                DOCUMENT_XML.getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private byte[] zipWithDuplicates(String first, String second)
            throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(first));
            zip.write(CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(second));
            zip.write(CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(DOCUMENT_XML.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }
}
