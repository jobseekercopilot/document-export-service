package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.DocumentKind;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.dto.StoreDocumentFileResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExportServiceTest {

    private static final String OWNER = "owner-123";

    @Mock private GeneratedDocumentsApi generatedDocumentsApi;
    @Mock private DocumentFilesApi producerDocumentFilesApi;
    @Mock private DocumentFilesApi readerDocumentFilesApi;
    @Mock private DocxUploadInspector docxUploadInspector;
    @Mock private RestTemplate restTemplate;

    private DocumentExportService service;

    @BeforeEach
    void setUp() {
        DocumentExportLimits limits = new DocumentExportLimits();
        RenderBudget renderBudget = new RenderBudget(limits);
        service = new DocumentExportService(
                generatedDocumentsApi,
                producerDocumentFilesApi,
                readerDocumentFilesApi,
                new DocxExportService(renderBudget),
                new PdfExportService(
                        renderBudget,
                        new ExportFontProvider(limits)),
                docxUploadInspector,
                restTemplate);
        ReflectionTestUtils.setField(
                service,
                "documentStoreBaseUrl",
                "http://document-store.test");
    }

    @Test
    void fetchesDocumentCreatesDocxAndPdfSavesBothAndReturnsDownloadUrls() {
        UUID documentId = UUID.randomUUID();
        UUID docxId = UUID.randomUUID();
        UUID pdfId = UUID.randomUUID();
        when(generatedDocumentsApi.getDocumentById(documentId, OWNER)).thenReturn(document(documentId));
        when(producerDocumentFilesApi.createDocumentFile(any(), eq(OWNER))).thenReturn(
                new DocumentFileResponse().id(docxId),
                new DocumentFileResponse().id(pdfId));

        DocumentExportResponse response = service.exportDocument(documentId,
                new DocumentExportRequest(List.of(ExportFormat.DOCX, ExportFormat.PDF)),
                OWNER);

        assertEquals(documentId, response.getDocumentId());
        assertEquals(2, response.getExports().size());
        assertEquals(docxId, response.getExports().get(0).getFileId());
        assertEquals(ExportFormat.DOCX, response.getExports().get(0).getFormat());
        assertEquals("/api/v1/document-files/" + docxId + "/download", response.getExports().get(0).getDownloadUrl());
        assertEquals(pdfId, response.getExports().get(1).getFileId());
        assertEquals("application/pdf", response.getExports().get(1).getMimeType());

        ArgumentCaptor<CreateDocumentFileRequest> captor = ArgumentCaptor.forClass(CreateDocumentFileRequest.class);
        verify(producerDocumentFilesApi, org.mockito.Mockito.times(2))
                .createDocumentFile(captor.capture(), eq(OWNER));
        verify(generatedDocumentsApi).getDocumentById(documentId, OWNER);
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
        when(generatedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenThrow(new RestClientException("down"));

        assertThrows(DownstreamServiceException.class,
                () -> service.exportDocument(
                        documentId,
                        new DocumentExportRequest(List.of(ExportFormat.DOCX)),
                        OWNER));
    }

    @Test
    void handlesDocumentFileSaveFailure() {
        UUID documentId = UUID.randomUUID();
        when(generatedDocumentsApi.getDocumentById(documentId, OWNER)).thenReturn(document(documentId));
        when(producerDocumentFilesApi.createDocumentFile(any(), eq(OWNER)))
                .thenThrow(new RestClientException("down"));

        assertThrows(DownstreamServiceException.class,
                () -> service.exportDocument(
                        documentId,
                        new DocumentExportRequest(List.of(ExportFormat.PDF)),
                        OWNER));
    }

    @Test
    void replacementBindsTheSameOwnerToEveryStoreReadAndWrite() {
        UUID documentId = UUID.randomUUID();
        UUID uploadedId = UUID.randomUUID();
        UUID generatedPdfId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "edited.docx",
                DocumentExportService.DOCX_MIME_TYPE,
                "docx bytes".getBytes());
        StoreDocumentFileResponse uploaded = new StoreDocumentFileResponse();
        uploaded.setId(uploadedId);
        uploaded.setGeneratedDocumentId(documentId);
        uploaded.setFileType(ExportFormat.DOCX);
        uploaded.setFileName("edited.docx");
        uploaded.setMimeType(DocumentExportService.DOCX_MIME_TYPE);

        when(generatedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenReturn(document(documentId));
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(documentId))).thenReturn(uploaded);
        when(producerDocumentFilesApi.createDocumentFile(any(), eq(OWNER)))
                .thenReturn(new DocumentFileResponse()
                        .id(generatedPdfId)
                        .fileType(DocumentFileResponse.FileTypeEnum.PDF)
                        .fileName("tailored-cv.pdf")
                        .mimeType(MediaType.APPLICATION_PDF_VALUE));
        when(readerDocumentFilesApi.getLatestFilesForDocument(documentId, OWNER))
                .thenReturn(List.of(
                        new DocumentFileResponse()
                                .id(uploadedId)
                                .fileType(DocumentFileResponse.FileTypeEnum.DOCX)
                                .fileName("edited.docx")
                                .mimeType(DocumentExportService.DOCX_MIME_TYPE),
                        new DocumentFileResponse()
                                .id(generatedPdfId)
                                .fileType(DocumentFileResponse.FileTypeEnum.PDF)
                                .fileName("tailored-cv.pdf")
                                .mimeType(MediaType.APPLICATION_PDF_VALUE)));

        service.uploadReplacement(
                documentId,
                file,
                DocumentKind.CV,
                ExportFormat.DOCX,
                OWNER);

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(
                anyString(),
                entity.capture(),
                eq(StoreDocumentFileResponse.class),
                eq(documentId));
        assertEquals(OWNER, entity.getValue().getHeaders().getFirst("X-Document-Owner"));
        assertEquals(1, entity.getValue().getHeaders().get("X-Document-Owner").size());
        verify(generatedDocumentsApi, org.mockito.Mockito.times(2))
                .getDocumentById(documentId, OWNER);
        verify(producerDocumentFilesApi).createDocumentFile(any(), eq(OWNER));
        verify(readerDocumentFilesApi).getLatestFilesForDocument(documentId, OWNER);
        verify(docxUploadInspector).inspect(file);
    }

    @Test
    void replacementOperationPropagatesRetryKeysAndReusesExistingPdf() {
        UUID documentId = UUID.randomUUID();
        UUID uploadedId = UUID.randomUUID();
        UUID existingPdfId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "edited.docx",
                DocumentExportService.DOCX_MIME_TYPE,
                "stable docx bytes".getBytes());
        StoreDocumentFileResponse uploaded =
                new StoreDocumentFileResponse();
        uploaded.setId(uploadedId);
        uploaded.setGeneratedDocumentId(documentId);
        uploaded.setFileType(ExportFormat.DOCX);
        uploaded.setFileName("edited.docx");
        uploaded.setMimeType(DocumentExportService.DOCX_MIME_TYPE);
        List<DocumentFileResponse> currentFiles = List.of(
                new DocumentFileResponse()
                        .id(uploadedId)
                        .fileType(DocumentFileResponse.FileTypeEnum.DOCX)
                        .fileName("edited.docx")
                        .mimeType(DocumentExportService.DOCX_MIME_TYPE),
                new DocumentFileResponse()
                        .id(existingPdfId)
                        .fileType(DocumentFileResponse.FileTypeEnum.PDF)
                        .fileName("tailored-cv.pdf")
                        .mimeType(MediaType.APPLICATION_PDF_VALUE));

        when(generatedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenReturn(document(documentId));
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(documentId))).thenReturn(uploaded);
        when(readerDocumentFilesApi.getLatestFilesForDocument(
                documentId, OWNER))
                .thenReturn(currentFiles);

        var response = service.uploadReplacement(
                documentId,
                file,
                DocumentKind.CV,
                ExportFormat.DOCX,
                OWNER,
                "replacement-operation");

        assertEquals(
                existingPdfId,
                response.getRegeneratedFiles().get(0).getFileId());
        ArgumentCaptor<HttpEntity> upload =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(
                anyString(),
                upload.capture(),
                eq(StoreDocumentFileResponse.class),
                eq(documentId));
        assertEquals(
                "replacement-operation:docx",
                upload.getValue().getHeaders()
                        .getFirst("Idempotency-Key"));
        verify(readerDocumentFilesApi, times(2))
                .getLatestFilesForDocument(documentId, OWNER);
        verify(producerDocumentFilesApi, never())
                .createDocumentFile(any(), anyString());
        verify(restTemplate, never()).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(DocumentFileResponse.class));
    }

    private GeneratedDocumentResponse document(UUID documentId) {
        return new GeneratedDocumentResponse()
                .id(documentId)
                .title("Tailored CV")
                .content("First paragraph.\n\nSecond paragraph.");
    }
}
