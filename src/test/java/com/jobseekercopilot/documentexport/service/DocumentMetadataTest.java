package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentMetadataTest {

    @Test
    void boundsTitleByUnicodeCodePointAndRemovesControlCharacters() {
        String title = "  Candidate\n"
                + "\u0000"
                + "\uD83D\uDE80".repeat(220);

        DocumentMetadata metadata = DocumentMetadata.from(
                new GeneratedDocumentResponse()
                        .title(title)
                        .version(4));

        assertEquals(
                200,
                metadata.title().codePointCount(
                        0,
                        metadata.title().length()));
        assertTrue(metadata.title().codePoints()
                .noneMatch(Character::isISOControl));
        assertEquals("4", metadata.version());
    }

    @Test
    void omitsInvalidVersionAndUsesGenericFallbackTitle() {
        DocumentMetadata metadata = DocumentMetadata.from(
                new GeneratedDocumentResponse()
                        .title("\n\u0000\t")
                        .version(0));

        assertEquals("Generated document", metadata.title());
        assertEquals("", metadata.version());
        assertEquals(
                "job-application-document",
                metadata.keywords());
    }
}
