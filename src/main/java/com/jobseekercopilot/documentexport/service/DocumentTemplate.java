package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DocumentTemplate {
    public static final String BRAND_FOOTER = "Generated with Job Seeker Copilot";

    private static final Set<String> CV_HEADINGS = Set.of(
            "personal summary",
            "profile",
            "core skills",
            "key skills",
            "skills",
            "work history",
            "employment history",
            "professional experience",
            "qualifications",
            "education");

    private final DocumentKind kind;
    private final DocumentMetadata metadata;
    private final List<Block> blocks;

    private DocumentTemplate(
            DocumentKind kind,
            DocumentMetadata metadata,
            List<Block> blocks) {
        this.kind = kind;
        this.metadata = metadata;
        this.blocks = List.copyOf(blocks);
    }

    public static DocumentTemplate from(GeneratedDocumentResponse document) {
        DocumentKind kind = DocumentKind.from(document);
        DocumentMetadata metadata = DocumentMetadata.from(document);
        String title = title(metadata, kind);
        List<String> paragraphs = paragraphs(document.getContent());
        if (!paragraphs.isEmpty() && sameText(paragraphs.get(0), title)) {
            paragraphs = paragraphs.subList(1, paragraphs.size());
        }

        Header header = header(paragraphs, title, kind);
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(BlockStyle.TITLE, header.title()));
        if (!header.subtitle().isBlank()) {
            blocks.add(new Block(BlockStyle.SUBTITLE, header.subtitle()));
        }
        if (!header.contact().isBlank()) {
            blocks.add(new Block(BlockStyle.CONTACT, header.contact()));
        }
        blocks.add(new Block(BlockStyle.ACCENT_LINE, ""));

        int contentStart = header.contentStart();
        if (kind == DocumentKind.CV) {
            addCvBlocks(blocks, paragraphs.subList(contentStart, paragraphs.size()));
        } else {
            addCoverLetterBlocks(blocks, paragraphs.subList(contentStart, paragraphs.size()));
        }
        blocks.add(new Block(BlockStyle.FOOTER, BRAND_FOOTER));

        return new DocumentTemplate(
                kind,
                metadata,
                blocks);
    }

    public DocumentKind kind() {
        return kind;
    }

    public List<Block> blocks() {
        return blocks;
    }

    DocumentMetadata metadata() {
        return metadata;
    }

    private static Header header(List<String> paragraphs, String title, DocumentKind kind) {
        if (paragraphs.isEmpty()) {
            return new Header(title, subtitle(title, kind), "", 0);
        }
        List<String> firstLines = lines(paragraphs.get(0));
        if (firstLines.isEmpty()) {
            return new Header(title, subtitle(title, kind), "", 1);
        }
        boolean looksLikeContact = kind == DocumentKind.COVER_LETTER
                ? !firstLines.get(0).toLowerCase(Locale.ROOT).startsWith("dear ")
                : !isCvHeading(firstLines.get(0));
        if (!looksLikeContact) {
            return new Header(title, subtitle(title, kind), "", 0);
        }
        String candidateName = firstLines.get(0);
        String contact = firstLines.size() > 1
                ? String.join(" | ", firstLines.subList(1, firstLines.size()))
                : "";
        return new Header(candidateName, subtitle(title, kind), contact, 1);
    }

    private static String subtitle(String title, DocumentKind kind) {
        if (title == null || title.isBlank()) {
            return kind == DocumentKind.COVER_LETTER ? "Application" : "";
        }
        String cleaned = title.trim()
                .replaceAll("(?i)^tailored\\s+", "")
                .replaceAll("(?i)\\s+cv$", "")
                .replaceAll("(?i)\\s+curriculum vitae$", "")
                .replaceAll("(?i)\\s+cover letter$", "")
                .trim();
        if (kind == DocumentKind.COVER_LETTER) {
            return cleaned.isBlank() ? "Application" : "Application for " + cleaned;
        }
        if (cleaned.equalsIgnoreCase("cv") || cleaned.equalsIgnoreCase("curriculum vitae")) {
            return "";
        }
        return cleaned.equalsIgnoreCase(title.trim()) ? "" : cleaned;
    }

    private static void addCvBlocks(List<Block> blocks, List<String> paragraphs) {
        for (String paragraph : paragraphs) {
            List<String> lines = lines(paragraph);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (isCvHeading(line)) {
                    blocks.add(new Block(BlockStyle.SECTION_HEADING, line));
                } else if (line.startsWith("- ")) {
                    blocks.add(new Block(BlockStyle.BULLET, line.substring(2).trim()));
                } else if (index == 0 && lines.size() > 1 && !paragraph.contains(".")) {
                    blocks.add(new Block(BlockStyle.ROLE_HEADING, line));
                } else {
                    blocks.add(new Block(BlockStyle.PARAGRAPH, line));
                }
            }
        }
    }

    private static void addCoverLetterBlocks(List<Block> blocks, List<String> paragraphs) {
        for (String paragraph : paragraphs) {
            List<String> lines = lines(paragraph);
            if (lines.isEmpty()) {
                continue;
            }
            blocks.add(new Block(BlockStyle.PARAGRAPH, String.join("\n", lines)));
        }
    }

    private static String title(
            DocumentMetadata metadata,
            DocumentKind kind) {
        if (!"Generated document".equals(metadata.title())) {
            return metadata.title();
        }
        return kind == DocumentKind.COVER_LETTER
                ? "Cover Letter"
                : "Curriculum Vitae";
    }

    private static List<String> paragraphs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return Arrays.stream(content.split("\\R\\s*\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static List<String> lines(String paragraph) {
        return Arrays.stream(paragraph.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static boolean isCvHeading(String line) {
        return CV_HEADINGS.contains(line.toLowerCase(Locale.ROOT));
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    public enum DocumentKind {
        CV,
        COVER_LETTER;

        static DocumentKind from(GeneratedDocumentResponse document) {
            if (document.getDocumentType() != null
                    && document.getDocumentType().toString().equalsIgnoreCase("COVER_LETTER")) {
                return COVER_LETTER;
            }
            String title = document.getTitle() == null ? "" : document.getTitle().toLowerCase(Locale.ROOT);
            return title.contains("cover letter") ? COVER_LETTER : CV;
        }
    }

    public enum BlockStyle {
        TITLE,
        SUBTITLE,
        CONTACT,
        ACCENT_LINE,
        SECTION_HEADING,
        ROLE_HEADING,
        PARAGRAPH,
        BULLET,
        FOOTER
    }

    public record Block(BlockStyle style, String text) {
        public Block {
            if (style == null) {
                throw new IllegalArgumentException(
                        "Render block style is required");
            }
            text = text == null ? "" : text;
        }
    }

    private record Header(String title, String subtitle, String contact, int contentStart) {}
}
