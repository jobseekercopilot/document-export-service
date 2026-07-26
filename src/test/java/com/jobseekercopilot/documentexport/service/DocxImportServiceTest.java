package com.jobseekercopilot.documentexport.service;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxImportServiceTest {

    private final DocxImportService service = new DocxImportService();

    @Test
    void importsHeadersParagraphsTablesAndCustomFooters() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
            policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("candidate@example.test");
            document.createParagraph().createRun().setText("Edited summary");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Skill");
            table.getRow(0).getCell(1).setText("Java");
            policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("Available on request");
            document.write(output);
            bytes = output.toByteArray();
        }

        String imported = service.importContent(bytes);

        assertTrue(imported.contains("candidate@example.test"));
        assertTrue(imported.contains("Edited summary"));
        assertTrue(imported.contains("Skill | Java"));
        assertTrue(imported.contains("Available on request"));
    }

    @Test
    void removesTheGeneratedBrandFooterFromCanonicalContent() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Edited summary");
            XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
            policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText(DocumentTemplate.BRAND_FOOTER);
            document.write(output);
            bytes = output.toByteArray();
        }

        String imported = service.importContent(bytes);

        assertTrue(imported.contains("Edited summary"));
        assertFalse(imported.contains(DocumentTemplate.BRAND_FOOTER));
    }

    @Test
    void rejectsCorruptAndTextlessDocuments() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.importContent("not a docx".getBytes()));

        byte[] empty;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.write(output);
            empty = output.toByteArray();
        }
        assertThrows(IllegalArgumentException.class, () -> service.importContent(empty));
    }
}
