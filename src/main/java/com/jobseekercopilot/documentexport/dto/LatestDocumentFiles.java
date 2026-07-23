package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Latest available DOCX and PDF files for a generated document")
public class LatestDocumentFiles {
    private DocumentExportItem docx;
    private DocumentExportItem pdf;
}
