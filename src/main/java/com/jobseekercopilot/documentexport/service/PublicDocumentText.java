package com.jobseekercopilot.documentexport.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Defensive public projection for retained documents created by older generators. */
final class PublicDocumentText {

    private static final Pattern INTERNAL_ANNOTATION = Pattern.compile(
            "(?is)\\s*[({\\[]?\\s*[\"']?"
                    + "(?:evidenceIds|contentPaths|claimId|reviewText|ledgerId|ledgerSha256)"
                    + "[\"']?\\s*[:=].*$");

    private PublicDocumentText() {
    }

    static String visible(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher annotation = INTERNAL_ANNOTATION.matcher(value);
        String visible = annotation.find()
                ? value.substring(0, annotation.start())
                : value;
        return visible.trim();
    }
}
