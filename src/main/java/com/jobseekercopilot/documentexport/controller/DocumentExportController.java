package com.jobseekercopilot.documentexport.controller;

import com.jobseekercopilot.documentexport.dto.DocumentKind;
import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.DocumentUploadResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.dto.ServiceIdentityError;
import com.jobseekercopilot.documentexport.security.DocumentExportIdentityFilter;
import com.jobseekercopilot.documentexport.service.DocumentExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-exports")
@RequiredArgsConstructor
@Tag(name = "Document Exports", description = "Endpoints for exporting generated documents to DOCX and PDF")
@SecurityRequirement(name = "serviceToken")
public class DocumentExportController {

    private final DocumentExportService documentExportService;

    @PostMapping("/documents/{documentId}")
    @Operation(
            summary = "Export generated document",
            description = "Creates DOCX and/or PDF exports and saves them to document-store-service",
            parameters = @Parameter(
                    name = DocumentExportIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    description = "Owner context bound by the authenticated Gateway",
                    required = true,
                    schema = @Schema(type = "string")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Exports created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid export request"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Service authentication failed",
                    content = @Content(schema = @Schema(implementation = ServiceIdentityError.class))),
            @ApiResponse(responseCode = "502", description = "Document store or export generation failed")
    })
    public ResponseEntity<DocumentExportResponse> exportDocument(
            @Parameter(description = "UUID of the generated document") @PathVariable UUID documentId,
            @Parameter(hidden = true)
            @RequestAttribute(DocumentExportIdentityFilter.OWNER_ATTRIBUTE) String documentOwner,
            @Parameter(
                    description = "Stable operation key for replay-safe per-format Store writes",
                    required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DocumentExportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentExportService.exportDocument(
                        documentId,
                        request,
                        documentOwner,
                        idempotencyKey));
    }

    @PostMapping(value = "/documents/{documentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload user-edited document file",
            description = "Stores an uploaded DOCX or PDF replacement and returns the latest available files",
            parameters = @Parameter(
                    name = DocumentExportIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    description = "Owner context bound by the authenticated Gateway",
                    required = true,
                    schema = @Schema(type = "string")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload saved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid upload request"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Service authentication failed",
                    content = @Content(schema = @Schema(implementation = ServiceIdentityError.class))),
            @ApiResponse(responseCode = "502", description = "Document store failed")
    })
    public ResponseEntity<DocumentUploadResponse> uploadReplacement(
            @Parameter(description = "UUID of the generated document") @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentKind") DocumentKind documentKind,
            @RequestParam("uploadedFormat") ExportFormat uploadedFormat,
            @Parameter(description = "Stable Tracker workflow key for replay-safe Store file writes")
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Parameter(hidden = true)
            @RequestAttribute(DocumentExportIdentityFilter.OWNER_ATTRIBUTE) String documentOwner) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentExportService.uploadReplacement(
                        documentId,
                        file,
                        documentKind,
                        uploadedFormat,
                        documentOwner,
                        idempotencyKey));
    }
}
