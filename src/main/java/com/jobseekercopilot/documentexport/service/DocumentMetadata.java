package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;

record DocumentMetadata(
        String title,
        String author,
        String subject,
        String description,
        String version) {

    static final String AUTHOR = "Job Seeker Copilot";
    static final String SUBJECT =
            "Accessible job application document export";
    static final String DESCRIPTION =
            "Owner-controlled document rendered by Job Seeker Copilot";
    static final String CREATOR =
            "Job Seeker Copilot Document Export Service";

    private static final int MAX_TITLE_CODE_POINTS = 200;

    static DocumentMetadata from(GeneratedDocumentResponse document) {
        String title = boundedSingleLine(
                document.getTitle(),
                "Generated document",
                MAX_TITLE_CODE_POINTS);
        String version = document.getVersion() != null
                && document.getVersion() > 0
                ? Integer.toString(document.getVersion())
                : "";
        return new DocumentMetadata(
                title,
                AUTHOR,
                SUBJECT,
                DESCRIPTION,
                version);
    }

    String keywords() {
        return version.isBlank()
                ? "job-application-document"
                : "job-application-document; document-version=" + version;
    }

    private static String boundedSingleLine(
            String value,
            String fallback,
            int maximumCodePoints) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value
                .codePoints()
                .map(codePoint -> Character.isISOControl(codePoint)
                        ? ' '
                        : codePoint)
                .collect(
                        StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString()
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maximumCodePoints) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, maximumCodePoints);
        return normalized.substring(0, end).trim();
    }
}
