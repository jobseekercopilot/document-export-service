package com.jobseekercopilot.documentexport.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DocumentExportCredentials {

    static final int MINIMUM_TOKEN_BYTES = 32;

    private final String gatewayToken;
    private final String documentStoreProducerToken;
    private final String documentStoreReaderToken;

    public DocumentExportCredentials(
            @Value("${document-export.security.gateway-token}") String gatewayToken,
            @Value("${document-export.security.document-store-producer-token}")
            String documentStoreProducerToken,
            @Value("${document-export.security.document-store-reader-token}")
            String documentStoreReaderToken) {
        this.gatewayToken = validate(gatewayToken, "Document Export gateway token");
        this.documentStoreProducerToken = validate(
                documentStoreProducerToken,
                "Document Store producer token");
        this.documentStoreReaderToken = validate(
                documentStoreReaderToken,
                "Document Store reader token");
        requireDistinct(gatewayToken, documentStoreProducerToken, documentStoreReaderToken);
    }

    public String gatewayToken() {
        return gatewayToken;
    }

    public String documentStoreProducerToken() {
        return documentStoreProducerToken;
    }

    public String documentStoreReaderToken() {
        return documentStoreReaderToken;
    }

    private static String validate(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(label + " must contain at least 32 bytes.");
        }
        return value;
    }

    private static void requireDistinct(String... values) {
        for (int first = 0; first < values.length; first++) {
            for (int second = first + 1; second < values.length; second++) {
                if (MessageDigest.isEqual(
                        values[first].getBytes(StandardCharsets.UTF_8),
                        values[second].getBytes(StandardCharsets.UTF_8))) {
                    throw new IllegalStateException(
                            "Service identity tokens must be distinct.");
                }
            }
        }
    }
}
