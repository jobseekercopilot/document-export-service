package com.jobseekercopilot.documentexport.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocxImportService {

    public String importContent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded DOCX is empty");
        }

        List<String> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFHeader header : document.getHeaderList()) {
                appendHeaderOrFooter(blocks, header.getParagraphs(), header.getTables());
            }
            for (IBodyElement element : document.getBodyElements()) {
                appendBodyElement(blocks, element);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                appendHeaderOrFooter(blocks, footer.getParagraphs(), footer.getTables());
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Uploaded file is not a readable DOCX");
        }

        List<String> meaningfulBlocks = blocks.stream()
                .map(this::normalise)
                .filter(value -> !value.isBlank())
                .filter(value -> !value.equalsIgnoreCase(DocumentTemplate.BRAND_FOOTER))
                .toList();
        if (meaningfulBlocks.isEmpty()) {
            throw new IllegalArgumentException("Uploaded DOCX contains no readable text");
        }
        return String.join("\n\n", meaningfulBlocks);
    }

    private void appendHeaderOrFooter(
            List<String> blocks,
            List<XWPFParagraph> paragraphs,
            List<XWPFTable> tables) {
        paragraphs.forEach(paragraph -> appendParagraph(blocks, paragraph));
        tables.forEach(table -> appendTable(blocks, table));
    }

    private void appendBodyElement(List<String> blocks, IBodyElement element) {
        if (element instanceof XWPFParagraph paragraph) {
            appendParagraph(blocks, paragraph);
        } else if (element instanceof XWPFTable table) {
            appendTable(blocks, table);
        }
    }

    private void appendParagraph(List<String> blocks, XWPFParagraph paragraph) {
        String text = normalise(paragraph.getText());
        if (text.isBlank()) {
            return;
        }
        if (paragraph.getNumID() != null && !text.startsWith("- ")) {
            text = "- " + text;
        }
        blocks.add(text);
    }

    private void appendTable(List<String> blocks, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = normalise(cell.getText());
                if (!value.isBlank()) {
                    cells.add(value);
                }
            }
            if (!cells.isEmpty()) {
                blocks.add(String.join(" | ", cells));
            }
        }
    }

    private String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .strip();
    }
}
