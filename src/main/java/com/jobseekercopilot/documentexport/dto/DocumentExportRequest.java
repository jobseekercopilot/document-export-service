package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Request to export a generated document into one or more formats")
public class DocumentExportRequest {

    @NotEmpty(message = "formats is required")
    @Schema(description = "Export formats to create", example = "[\"DOCX\", \"PDF\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ExportFormat> formats;

    @Valid
    @Schema(description = "Optional owner-authorised contact data to render; omitted for legacy documents")
    private ProfessionalContact professionalContact;

    public DocumentExportRequest(List<ExportFormat> formats) {
        this(formats, null);
    }

    public DocumentExportRequest(
            List<ExportFormat> formats,
            ProfessionalContact professionalContact) {
        this.formats = formats;
        this.professionalContact = professionalContact;
    }
}
