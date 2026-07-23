package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Response containing exported files")
public class DocumentExportResponse {

    private UUID documentId;
    private List<DocumentExportItem> exports;
}
