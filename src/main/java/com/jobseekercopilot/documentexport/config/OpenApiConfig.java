package com.jobseekercopilot.documentexport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documentExportOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jobseeker Copilot - Document Export API")
                .version("2.0.0")
                .description("""
                        Exports generated document content into DOCX and PDF files and saves them
                        to document-store-service. Every export operation requires the approved
                        Gateway service identity and its trusted document-owner context.
                        """))
                .components(new Components().addSecuritySchemes(
                        "serviceToken",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Service-Token")));
    }
}
