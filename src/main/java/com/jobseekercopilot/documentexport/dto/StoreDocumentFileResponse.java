package com.jobseekercopilot.documentexport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreDocumentFileResponse {
    private UUID id;
    private UUID generatedDocumentId;
    private ExportFormat fileType;
    private String fileName;
    private String mimeType;
}
