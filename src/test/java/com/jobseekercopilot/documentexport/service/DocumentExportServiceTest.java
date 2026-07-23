package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.exception.DownstreamServiceException;
import com.jobseekercopilot.generated.documentstoreservice.api.DocumentFilesApi;
import com.jobseekercopilot.generated.documentstoreservice.api.GeneratedDocumentsApi;
import com.jobseekercopilot.generated.documentstoreservice.model.CreateDocumentFileRequest;
import com.jobseekercopilot.generated.documentstoreservice.model.DocumentFileResponse;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExportServiceTest {

    @Mock private GeneratedDocumentsApi generatedDocumentsApi;
    @Mock private DocumentFilesApi documentFilesApi;
    @Mock private RestTemplate restTemplate;

    private DocumentExportService service;

    @BeforeEach
    void setUp() {
        service = new DocumentExportService(
                generatedDocumentsApi,
                documentFilesApi,
                new DocxExportService(),
                new PdfExportService(),
                restTemplate);
    }

    @Test
    void fetchesDocumentCreatesDocxAndPdfSavesBothAndReturnsDownloadUrls() {
        UUID documentId = UUID.randomUUID();
        UUID docxId = UUID.randomUUID();
        UUID pdfId = UUID.randomUUID();
        when(generatedDocumentsApi.getDocumentById(documentId)).thenReturn(document(documentId));
        when(documentFilesApi.createDocumentFile(any())).thenReturn(
                new DocumentFileResponse().id(docxId),
                new DocumentFileResponse().id(pdfId));

        DocumentExportResponse response = service.exportDocument(documentId,
                new DocumentExportRequest(List.of(ExportFormat.DOCX, ExportFormat.PDF)));

        assertEquals(documentId, response.getDocumentId());
        assertEquals(2, response.getExports().size());
        assertEquals(docxId, response.getExports().get(0).getFileId());
        assertEquals(ExportFormat.DOCX, response.getExports().get(0).getFormat());
        assertEquals("/api/v1/document-files/" + docxId + "/download", response.getExports().get(0).getDownloadUrl());
        assertEquals(pdfId, response.getExports().get(1).getFileId());
        assertEquals("application/pdf", response.getExports().get(1).getMimeType());

        ArgumentCaptor<CreateDocumentFileRequest> captor = ArgumentCaptor.forClass(CreateDocumentFileRequest.class);
        verify(documentFilesApi, org.mockito.Mockito.times(2)).createDocumentFile(captor.capture());
        assertEquals(CreateDocumentFileRequest.FileTypeEnum.DOCX, captor.getAllValues().get(0).getFileType());
        assertEquals(CreateDocumentFileRequest.FileTypeEnum.PDF, captor.getAllValues().get(1).getFileType());
        assertEquals(DocumentExportService.DOCX_MIME_TYPE, captor.getAllValues().get(0).getMimeType());
        assertEquals(DocumentExportService.PDF_MIME_TYPE, captor.getAllValues().get(1).getMimeType());

        byte[] docxBytes = Base64.getDecoder().decode(captor.getAllValues().get(0).getFileContentBase64());
        byte[] pdfBytes = Base64.getDecoder().decode(captor.getAllValues().get(1).getFileContentBase64());
        assertTrue(docxBytes.length > 0);
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(pdfBytes, 4));
    }

    @Test
    void handlesDocumentFetchFailure() {
        UUID documentId = UUID.randomUUID();
        when(generatedDocumentsApi.getDocumentById(documentId)).thenThrow(new RestClientException("down"));

        assertThrows(DownstreamServiceException.class,
                () -> service.exportDocument(documentId, new DocumentExportRequest(List.of(ExportFormat.DOCX))));
    }

    @Test
    void handlesDocumentFileSaveFailure() {
        UUID documentId = UUID.randomUUID();
        when(generatedDocumentsApi.getDocumentById(documentId)).thenReturn(document(documentId));
        when(documentFilesApi.createDocumentFile(any())).thenThrow(new RestClientException("down"));

        assertThrows(DownstreamServiceException.class,
                () -> service.exportDocument(documentId, new DocumentExportRequest(List.of(ExportFormat.PDF))));
    }

    private GeneratedDocumentResponse document(UUID documentId) {
        return new GeneratedDocumentResponse()
                .id(documentId)
                .title("Tailored CV")
                .content("First paragraph.\n\nSecond paragraph.");
    }
}
