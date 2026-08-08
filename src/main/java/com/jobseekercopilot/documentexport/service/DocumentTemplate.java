package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class DocumentTemplate {
    public static final String BRAND_FOOTER = "Generated with Job Seeker Copilot";

    private static final Set<String> CV_HEADINGS = Set.of(
            "personal summary",
            "profile",
            "technical profile",
            "professional profile",
            "core skills",
            "key skills",
            "skills",
            "technical skills",
            "projects",
            "selected projects",
            "work history",
            "employment history",
            "professional experience",
            "additional experience",
            "qualifications",
            "education",
            "education and qualifications");

    private static final Set<String> ENTRY_SECTIONS = Set.of(
            "professional experience",
            "work history",
            "employment history",
            "additional experience",
            "projects",
            "selected projects");

    private static final Set<String> SKILL_SECTIONS = Set.of(
            "core skills",
            "key skills",
            "skills",
            "technical skills");

    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)^(?:(?:january|february|march|april|may|june|july|august|"
                    + "september|october|november|december)\\s+)?\\d{4}"
                    + "\\s*[–—-]\\s*(?:(?:january|february|march|april|may|"
                    + "june|july|august|september|october|november|december)"
                    + "\\s+)?(?:\\d{4}|present|current)$");

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
        blocks.add(new Block(BlockStyle.TITLE, header.title(), true, true));
        if (!header.subtitle().isBlank()) {
            blocks.add(new Block(BlockStyle.SUBTITLE, header.subtitle(), true, true));
        }
        if (!header.contact().isBlank()) {
            blocks.add(new Block(BlockStyle.CONTACT, header.contact(), true, true));
        }
        blocks.add(new Block(BlockStyle.ACCENT_LINE, "", false, true));

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
        String section = "";
        for (String paragraph : paragraphs) {
            List<String> lines = lines(paragraph);
            for (String line : lines) {
                if (isCvHeading(line)) {
                    section = line.toLowerCase(Locale.ROOT);
                    blocks.add(new Block(
                            BlockStyle.SECTION_HEADING,
                            line,
                            true,
                            true));
                } else if (line.startsWith("- ")) {
                    blocks.add(new Block(
                            BlockStyle.BULLET,
                            line.substring(2).trim(),
                            false,
                            true));
                } else if (SKILL_SECTIONS.contains(section)) {
                    blocks.add(new Block(
                            BlockStyle.SKILLS,
                            readableSkills(line),
                            false,
                            true));
                } else if (ENTRY_SECTIONS.contains(section)
                        && isDateRange(line)) {
                    blocks.add(new Block(
                            BlockStyle.ENTRY_META,
                            line,
                            true,
                            true));
                } else if (ENTRY_SECTIONS.contains(section)
                        && isEntryHeading(line)) {
                    blocks.add(new Block(
                            BlockStyle.ROLE_HEADING,
                            line.replace(" - ", " — "),
                            true,
                            true));
                } else {
                    blocks.add(new Block(
                            BlockStyle.PARAGRAPH,
                            line,
                            false,
                            true));
                }
            }
        }
    }

    private static void addCoverLetterBlocks(List<Block> blocks, List<String> paragraphs) {
        int firstAdded = blocks.size();
        for (String paragraph : paragraphs) {
            List<String> lines = lines(paragraph);
            if (lines.isEmpty()) {
                continue;
            }
            String text = String.join("\n", lines);
            BlockStyle style = text.toLowerCase(Locale.ROOT).startsWith("yours ")
                    ? BlockStyle.SIGN_OFF
                    : BlockStyle.PARAGRAPH;
            blocks.add(new Block(style, text, false, true));
        }
        if (blocks.size() - firstAdded >= 2
                && blocks.get(blocks.size() - 1).style() == BlockStyle.SIGN_OFF) {
            int keepFrom = Math.max(firstAdded, blocks.size() - 5);
            for (int index = keepFrom; index < blocks.size() - 1; index++) {
                blocks.set(
                        index,
                        blocks.get(index).withKeepWithNext(true));
            }
        }
    }

    private static boolean isDateRange(String line) {
        return DATE_RANGE.matcher(line.trim()).matches();
    }

    private static boolean isEntryHeading(String line) {
        String value = line.trim();
        return value.length() <= 360
                && (value.contains(" — ") || value.contains(" - "))
                && !value.endsWith(".")
                && !value.endsWith("!")
                && !value.endsWith("?");
    }

    private static String readableSkills(String line) {
        if (!line.contains(",")) {
            return line;
        }
        return Arrays.stream(line.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining(" · "));
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
                .map(PublicDocumentText::visible)
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
        ENTRY_META,
        SKILLS,
        PARAGRAPH,
        BULLET,
        SIGN_OFF,
        FOOTER
    }

    public record Block(
            BlockStyle style,
            String text,
            boolean keepWithNext,
            boolean keepTogether) {
        public Block(BlockStyle style, String text) {
            this(style, text, false, false);
        }

        public Block {
            if (style == null) {
                throw new IllegalArgumentException(
                        "Render block style is required");
            }
            text = text == null ? "" : text;
        }

        Block withKeepWithNext(boolean value) {
            return new Block(style, text, value, keepTogether);
        }
    }

    private record Header(String title, String subtitle, String contact, int contentStart) {}
}
