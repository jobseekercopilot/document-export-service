package com.jobseekercopilot.documentexport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documentExportOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jobseeker Copilot - Document Export API")
                .version("1.0.0")
                .description("Exports generated document content into DOCX and PDF files and saves them to document-store-service."));
    }
}
