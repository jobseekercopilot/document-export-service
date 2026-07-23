package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to export a generated document into one or more formats")
public class DocumentExportRequest {

    @NotEmpty(message = "formats is required")
    @Schema(description = "Export formats to create", example = "[\"DOCX\", \"PDF\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ExportFormat> formats;
}
