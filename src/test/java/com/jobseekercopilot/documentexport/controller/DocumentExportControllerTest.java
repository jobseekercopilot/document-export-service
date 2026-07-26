package com.jobseekercopilot.documentexport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.documentexport.dto.DocumentExportItem;
import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.DocumentUploadResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.security.DocumentExportCredentials;
import com.jobseekercopilot.documentexport.security.DocumentExportIdentityFilter;
import com.jobseekercopilot.documentexport.service.DocumentExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentExportController.class)
@Import({DocumentExportIdentityFilter.class, DocumentExportCredentials.class})
class DocumentExportControllerTest {

    private static final String GATEWAY_TOKEN =
            "test-only-document-export-gateway-token-32-bytes";
    private static final String OWNER = "owner-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DocumentExportService documentExportService;

    @Test
    void exportDocumentReturnsCreated() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(documentExportService.exportDocument(eq(documentId), any(), eq(OWNER)))
                .thenReturn(DocumentExportResponse.builder()
                .documentId(documentId)
                .exports(List.of(DocumentExportItem.builder()
                        .fileId(fileId)
                        .format(ExportFormat.DOCX)
                        .fileName("cv.docx")
                        .mimeType(DocumentExportService.DOCX_MIME_TYPE)
                        .downloadUrl("/api/v1/document-files/" + fileId + "/download")
                        .build()))
                .build());

        mockMvc.perform(post("/api/v1/document-exports/documents/{documentId}", documentId)
                        .header(DocumentExportIdentityFilter.SERVICE_TOKEN_HEADER, GATEWAY_TOKEN)
                        .header(DocumentExportIdentityFilter.OWNER_HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.exports[0].fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.exports[0].downloadUrl").value("/api/v1/document-files/" + fileId + "/download"));
        verify(documentExportService).exportDocument(eq(documentId), any(), eq(OWNER));
    }

    @Test
    void replacementForwardsTheStableRetryKey() throws Exception {
        UUID currentId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "edited.docx",
                DocumentExportService.DOCX_MIME_TYPE,
                "synthetic".getBytes());
        when(documentExportService.uploadReplacement(
                eq(currentId),
                any(),
                any(),
                eq(ExportFormat.DOCX),
                eq(OWNER),
                eq("retry-key-123"))).thenReturn(DocumentUploadResponse.builder()
                .generatedDocumentId(replacementId)
                .build());

        mockMvc.perform(multipart(
                        "/api/v1/document-exports/documents/{documentId}/upload",
                        currentId)
                        .file(file)
                        .param("documentKind", "CV")
                        .param("uploadedFormat", "DOCX")
                        .header(DocumentExportIdentityFilter.SERVICE_TOKEN_HEADER, GATEWAY_TOKEN)
                        .header(DocumentExportIdentityFilter.OWNER_HEADER, OWNER)
                        .header("Idempotency-Key", "retry-key-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedDocumentId").value(replacementId.toString()));

        verify(documentExportService).uploadReplacement(
                eq(currentId),
                any(),
                any(),
                eq(ExportFormat.DOCX),
                eq(OWNER),
                eq("retry-key-123"));
    }

    @Test
    void missingServiceIdentityFailsClosed() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/document-exports/documents/{documentId}", documentId)
                        .header(DocumentExportIdentityFilter.OWNER_HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidServiceIdentityFailsClosed() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/document-exports/documents/{documentId}", documentId)
                        .header(
                                DocumentExportIdentityFilter.SERVICE_TOKEN_HEADER,
                                "wrong-test-token-that-is-at-least-32-bytes")
                        .header(DocumentExportIdentityFilter.OWNER_HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void missingOwnerContextFailsClosed() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/document-exports/documents/{documentId}", documentId)
                        .header(DocumentExportIdentityFilter.SERVICE_TOKEN_HEADER, GATEWAY_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_CONTEXT_REQUIRED"));
    }

    @Test
    void duplicateOwnerContextFailsClosed() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/document-exports/documents/{documentId}", documentId)
                        .header(DocumentExportIdentityFilter.SERVICE_TOKEN_HEADER, GATEWAY_TOKEN)
                        .header(
                                DocumentExportIdentityFilter.OWNER_HEADER,
                                OWNER,
                                "victim-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_CONTEXT_REQUIRED"));
    }
}
