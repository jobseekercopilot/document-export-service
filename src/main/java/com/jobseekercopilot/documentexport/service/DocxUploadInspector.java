package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Component
public class DocxUploadInspector {

    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCUMENT_XML = "word/document.xml";
    private static final Set<String> FORBIDDEN_EXACT_ENTRIES = Set.of(
            "word/vbaproject.bin",
            "word/vbadata.xml");
    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
            "word/activex/",
            "word/embeddings/",
            "customui/");

    private final DocumentExportLimits limits;

    public DocxUploadInspector(DocumentExportLimits limits) {
        this.limits = limits;
    }

    public void inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is required");
        }
        long compressedBytes = file.getSize();
        if (compressedBytes < 1
                || compressedBytes > limits.getUploadMaxCompressedBytes()) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX exceeds the compressed byte limit");
        }

        ScanState state = new ScanState(compressedBytes);
        try (InputStream input = new BufferedInputStream(file.getInputStream());
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                inspectEntry(zip, entry, state);
                zip.closeEntry();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Uploaded file is not a readable DOCX package");
        }

        state.finish();
        validateWithPoi(file);
    }

    private void inspectEntry(
            ZipInputStream zip,
            ZipEntry entry,
            ScanState state) throws IOException {
        String name = validateEntryName(entry.getName());
        String canonicalName = name.toLowerCase(Locale.ROOT);
        if (!state.names.add(canonicalName)) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX contains duplicate ZIP entries");
        }
        state.entries++;
        if (state.entries > limits.getUploadMaxEntries()) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX exceeds the ZIP entry-count limit");
        }

        if (FORBIDDEN_EXACT_ENTRIES.contains(canonicalName)
                || FORBIDDEN_PREFIXES.stream()
                        .anyMatch(canonicalName::startsWith)
                || canonicalName.endsWith(".exe")
                || canonicalName.endsWith(".js")
                || canonicalName.endsWith(".jar")) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX contains active or embedded content");
        }

        boolean capture = CONTENT_TYPES.equals(name)
                || canonicalName.endsWith(".rels");
        EntryData data = readEntry(zip, capture, state);
        if (entry.getCompressedSize() > 0
                && data.expandedBytes
                > entry.getCompressedSize()
                        * (long) limits.getUploadMaxCompressionRatio()) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX exceeds the entry compression-ratio limit");
        }

        if (CONTENT_TYPES.equals(name)) {
            state.hasContentTypes = true;
            inspectContentTypes(data.bytes);
        }
        if (DOCUMENT_XML.equals(name)) {
            state.hasDocumentXml = true;
        }
        if (canonicalName.endsWith(".rels")) {
            inspectRelationships(data.bytes);
        }
    }

    private EntryData readEntry(
            ZipInputStream zip,
            boolean capture,
            ScanState state) throws IOException {
        ByteArrayOutputStream bytes = capture
                ? new ByteArrayOutputStream()
                : null;
        byte[] buffer = new byte[8192];
        long entryBytes = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            entryBytes += read;
            state.expandedBytes += read;
            if (entryBytes > limits.getUploadMaxEntryBytes()) {
                throw new IllegalArgumentException(
                        "Uploaded DOCX exceeds the per-entry byte limit");
            }
            if (state.expandedBytes > limits.getUploadMaxExpandedBytes()) {
                throw new IllegalArgumentException(
                        "Uploaded DOCX exceeds the expanded byte limit");
            }
            if (bytes != null) {
                if ((long) bytes.size() + read
                        > limits.getUploadMaxRelationshipBytes()) {
                    throw new IllegalArgumentException(
                            "Uploaded DOCX metadata entry is too large");
                }
                bytes.write(buffer, 0, read);
            }
        }
        return new EntryData(
                entryBytes,
                bytes == null ? new byte[0] : bytes.toByteArray());
    }

    private String validateEntryName(String value) {
        if (value == null
                || value.isBlank()
                || value.length()
                        > limits.getUploadMaxEntryNameCharacters()
                || value.indexOf('\0') >= 0
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.contains("\\")
                || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX contains an unsafe ZIP entry path");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new IllegalArgumentException(
                        "Uploaded DOCX contains an unsafe ZIP entry path");
            }
        }
        return value;
    }

    private void inspectContentTypes(byte[] bytes) {
        String contentTypes =
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
        if (contentTypes.contains("macroenabled")
                || contentTypes.contains("vbaproject")
                || contentTypes.contains("activex")) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX contains macro-enabled content");
        }
    }

    private void inspectRelationships(byte[] bytes) {
        try {
            var document = secureDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bytes));
            var relationships = document.getElementsByTagNameNS(
                    "*",
                    "Relationship");
            for (int index = 0; index < relationships.getLength(); index++) {
                Element relationship =
                        (Element) relationships.item(index);
                if ("external".equalsIgnoreCase(
                        relationship.getAttribute("TargetMode"))) {
                    throw new IllegalArgumentException(
                            "Uploaded DOCX contains an external relationship");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (ParserConfigurationException
                 | SAXException
                 | IOException exception) {
            throw new IllegalArgumentException(
                    "Uploaded DOCX contains malformed relationship metadata");
        }
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private void validateWithPoi(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             XWPFDocument ignored = new XWPFDocument(input)) {
            // Opening the package proves the required OOXML parts are parseable.
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Uploaded file is not a structurally valid DOCX document");
        }
    }

    private final class ScanState {

        private final long compressedBytes;
        private final Set<String> names = new HashSet<>();
        private int entries;
        private long expandedBytes;
        private boolean hasContentTypes;
        private boolean hasDocumentXml;

        private ScanState(long compressedBytes) {
            this.compressedBytes = compressedBytes;
        }

        private void finish() {
            if (!hasContentTypes || !hasDocumentXml) {
                throw new IllegalArgumentException(
                        "Uploaded DOCX is missing required OOXML parts");
            }
            long denominator = Math.max(1024, compressedBytes);
            if (expandedBytes
                    > denominator
                            * (long) limits.getUploadMaxCompressionRatio()) {
                throw new IllegalArgumentException(
                        "Uploaded DOCX exceeds the package compression-ratio limit");
            }
        }
    }

    private record EntryData(long expandedBytes, byte[] bytes) {
    }
}
