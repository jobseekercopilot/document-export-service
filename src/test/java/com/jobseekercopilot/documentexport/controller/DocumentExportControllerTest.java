package com.jobseekercopilot.documentexport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.documentexport.dto.DocumentExportItem;
import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.service.DocumentExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentExportController.class)
class DocumentExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DocumentExportService documentExportService;

    @Test
    void exportDocumentReturnsCreated() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(documentExportService.exportDocument(eq(documentId), any())).thenReturn(DocumentExportResponse.builder()
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentExportRequest(List.of(ExportFormat.DOCX)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.exports[0].fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.exports[0].downloadUrl").value("/api/v1/document-files/" + fileId + "/download"));
    }
}
