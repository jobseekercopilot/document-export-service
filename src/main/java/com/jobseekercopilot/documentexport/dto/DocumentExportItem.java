package com.jobseekercopilot.documentexport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "Single exported document file result")
public class DocumentExportItem {

    private UUID fileId;
    private ExportFormat format;
    private String fileName;
    private String mimeType;
    private String downloadUrl;
}
