package com.jobseekercopilot.documentexport.config;

import com.jobseekercopilot.generated.documentstoreservice.api.DocumentFilesApi;
import com.jobseekercopilot.generated.documentstoreservice.api.GeneratedDocumentsApi;
import com.jobseekercopilot.generated.documentstoreservice.client.ApiClient;
import com.jobseekercopilot.generated.documentstoreservice.client.auth.ApiKeyAuth;
import com.jobseekercopilot.documentexport.security.DocumentExportCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DocumentStoreClientConfig {

    @Bean
    GeneratedDocumentsApi generatedDocumentsApi(
            RestTemplateBuilder builder,
            DocumentExportCredentials credentials,
            @Value("${services.document-store-service.base-url:http://localhost:8089}") String baseUrl) {
        return new GeneratedDocumentsApi(apiClient(
                builder,
                baseUrl,
                credentials.documentStoreReaderToken()));
    }

    @Bean("documentStoreProducerFilesApi")
    DocumentFilesApi documentStoreProducerFilesApi(
            RestTemplateBuilder builder,
            DocumentExportCredentials credentials,
            @Value("${services.document-store-service.base-url:http://localhost:8089}") String baseUrl) {
        return new DocumentFilesApi(apiClient(
                builder,
                baseUrl,
                credentials.documentStoreProducerToken()));
    }

    @Bean("documentStoreReaderFilesApi")
    DocumentFilesApi documentStoreReaderFilesApi(
            RestTemplateBuilder builder,
            DocumentExportCredentials credentials,
            @Value("${services.document-store-service.base-url:http://localhost:8089}") String baseUrl) {
        return new DocumentFilesApi(apiClient(
                builder,
                baseUrl,
                credentials.documentStoreReaderToken()));
    }

    @Bean
    RestTemplate restTemplate(
            RestTemplateBuilder builder,
            DocumentExportCredentials credentials) {
        return builder
                .additionalInterceptors(new ServiceTokenInterceptor(
                        credentials.documentStoreProducerToken()))
                .build();
    }

    private ApiClient apiClient(RestTemplateBuilder builder, String baseUrl, String serviceToken) {
        ApiClient apiClient = new ApiClient(builder.build());
        apiClient.setBasePath(baseUrl);
        ApiKeyAuth serviceIdentity = (ApiKeyAuth) apiClient.getAuthentication("serviceToken");
        serviceIdentity.setApiKey(serviceToken);
        return apiClient;
    }
}
