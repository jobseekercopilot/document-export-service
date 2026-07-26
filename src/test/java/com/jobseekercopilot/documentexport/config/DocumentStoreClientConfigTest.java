package com.jobseekercopilot.documentexport.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobseekercopilot.documentexport.security.DocumentExportCredentials;
import com.jobseekercopilot.generated.documentstoreservice.client.auth.ApiKeyAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class DocumentStoreClientConfigTest {

    private static final String GATEWAY =
            "test-only-document-export-gateway-token-32-bytes";
    private static final String PRODUCER =
            "test-only-document-store-producer-token-32-bytes";
    private static final String READER =
            "test-only-document-store-reader-token-32-bytes";

    @Test
    void assignsReaderAndProducerCredentialsToTheCorrectGeneratedClients() {
        DocumentStoreClientConfig config = new DocumentStoreClientConfig();
        DocumentExportCredentials credentials =
                new DocumentExportCredentials(GATEWAY, PRODUCER, READER);
        RestTemplateBuilder builder = new RestTemplateBuilder();

        ApiKeyAuth readIdentity = (ApiKeyAuth) config.documentStoreReaderDocumentsApi(
                        builder,
                        credentials,
                        "http://document-store.test")
                .getApiClient()
                .getAuthentication("serviceToken");
        ApiKeyAuth writeDocumentIdentity = (ApiKeyAuth) config.documentStoreProducerDocumentsApi(
                        builder,
                        credentials,
                        "http://document-store.test")
                .getApiClient()
                .getAuthentication("serviceToken");
        ApiKeyAuth writeIdentity = (ApiKeyAuth) config.documentStoreProducerFilesApi(
                        builder,
                        credentials,
                        "http://document-store.test")
                .getApiClient()
                .getAuthentication("serviceToken");
        ApiKeyAuth readFileIdentity = (ApiKeyAuth) config.documentStoreReaderFilesApi(
                        builder,
                        credentials,
                        "http://document-store.test")
                .getApiClient()
                .getAuthentication("serviceToken");

        assertEquals(READER, readIdentity.getApiKey());
        assertEquals(PRODUCER, writeDocumentIdentity.getApiKey());
        assertEquals(READER, readFileIdentity.getApiKey());
        assertEquals(PRODUCER, writeIdentity.getApiKey());
    }
}
