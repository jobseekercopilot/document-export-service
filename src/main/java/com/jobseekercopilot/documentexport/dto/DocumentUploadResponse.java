package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Response containing uploaded and latest document file metadata")
public class DocumentUploadResponse {
    private UUID generatedDocumentId;
    private DocumentExportItem uploadedFile;
    private List<DocumentExportItem> regeneratedFiles;
    private LatestDocumentFiles latestFiles;
    private String message;
}
