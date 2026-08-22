package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable service-identity or owner-context rejection")
public record ServiceIdentityError(
        @Schema(example = "AUTHENTICATION_REQUIRED") String code,
        @Schema(example = "Valid service authentication is required.") String message) {
}
