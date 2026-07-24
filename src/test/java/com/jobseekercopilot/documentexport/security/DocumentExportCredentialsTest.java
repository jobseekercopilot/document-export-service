package com.jobseekercopilot.documentexport.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DocumentExportCredentialsTest {

    private static final String GATEWAY =
            "test-only-document-export-gateway-token-32-bytes";
    private static final String PRODUCER =
            "test-only-document-store-producer-token-32-bytes";
    private static final String READER =
            "test-only-document-store-reader-token-32-bytes";

    @Test
    void acceptsDistinctStrongCredentials() {
        DocumentExportCredentials credentials =
                new DocumentExportCredentials(GATEWAY, PRODUCER, READER);

        assertEquals(GATEWAY, credentials.gatewayToken());
        assertEquals(PRODUCER, credentials.documentStoreProducerToken());
        assertEquals(READER, credentials.documentStoreReaderToken());
    }

    @Test
    void rejectsMissingOrShortCredentials() {
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentExportCredentials("", PRODUCER, READER));
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentExportCredentials(GATEWAY, "too-short", READER));
    }

    @Test
    void rejectsCredentialsSharedAcrossTrustBoundaries() {
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentExportCredentials(GATEWAY, GATEWAY, READER));
        assertThrows(
                IllegalStateException.class,
                () -> new DocumentExportCredentials(GATEWAY, PRODUCER, PRODUCER));
    }
}
