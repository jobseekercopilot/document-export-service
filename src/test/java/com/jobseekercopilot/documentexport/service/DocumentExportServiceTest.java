package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.DocumentKind;
import com.jobseekercopilot.documentexport.dto.DocumentUploadResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.dto.StoreDocumentFileResponse;
import com.jobseekercopilot.documentexport.exception.DownstreamServiceException;
import com.jobseekercopilot.generated.documentstoreservice.api.DocumentFilesApi;
import com.jobseekercopilot.generated.documentstoreservice.api.GeneratedDocumentsApi;
import com.jobseekercopilot.generated.documentstoreservice.model.CreateDocumentFileRequest;
import com.jobseekercopilot.generated.documentstoreservice.model.CreateDocumentRequest;
import com.jobseekercopilot.generated.documentstoreservice.model.DocumentFileResponse;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExportServiceTest {

    private static final String OWNER = "owner-123";
    private static final String APPLICATION_ID = "application-123";

    @Mock private GeneratedDocumentsApi readerGeneratedDocumentsApi;
    @Mock private GeneratedDocumentsApi producerGeneratedDocumentsApi;
    @Mock private DocumentFilesApi producerDocumentFilesApi;
    @Mock private DocumentFilesApi readerDocumentFilesApi;
    @Mock private RestTemplate restTemplate;

    private DocumentExportService service;

    @BeforeEach
    void setUp() {
        service = new DocumentExportService(
                readerGeneratedDocumentsApi,
                producerGeneratedDocumentsApi,
                producerDocumentFilesApi,
                readerDocumentFilesApi,
                new DocxImportService(),
                new DocxExportService(),
                new PdfExportService(),
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
        when(readerGeneratedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenReturn(document(documentId, GeneratedDocumentResponse.DocumentTypeEnum.CV));
        when(producerDocumentFilesApi.createDocumentFile(any(), isNull(), eq(OWNER))).thenReturn(
                new DocumentFileResponse().id(docxId),
                new DocumentFileResponse().id(pdfId));

        DocumentExportResponse response = service.exportDocument(
                documentId,
                new DocumentExportRequest(List.of(ExportFormat.DOCX, ExportFormat.PDF)),
                OWNER);

        assertEquals(documentId, response.getDocumentId());
        assertEquals(2, response.getExports().size());
        assertEquals(docxId, response.getExports().get(0).getFileId());
        assertEquals(ExportFormat.DOCX, response.getExports().get(0).getFormat());
        assertEquals(
                "/api/v1/document-files/" + docxId + "/download",
                response.getExports().get(0).getDownloadUrl());
        assertEquals(pdfId, response.getExports().get(1).getFileId());
        assertEquals(DocumentExportService.PDF_MIME_TYPE, response.getExports().get(1).getMimeType());

        ArgumentCaptor<CreateDocumentFileRequest> captor =
                ArgumentCaptor.forClass(CreateDocumentFileRequest.class);
        verify(producerDocumentFilesApi, org.mockito.Mockito.times(2))
                .createDocumentFile(captor.capture(), isNull(), eq(OWNER));
        verify(readerGeneratedDocumentsApi).getDocumentById(documentId, OWNER);
        assertEquals(
                CreateDocumentFileRequest.FileTypeEnum.DOCX,
                captor.getAllValues().get(0).getFileType());
        assertEquals(
                CreateDocumentFileRequest.FileTypeEnum.PDF,
                captor.getAllValues().get(1).getFileType());

        byte[] docxBytes = Base64.getDecoder().decode(
                captor.getAllValues().get(0).getFileContentBase64());
        byte[] pdfBytes = Base64.getDecoder().decode(
                captor.getAllValues().get(1).getFileContentBase64());
        assertTrue(docxBytes.length > 0);
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(pdfBytes, 4));
    }

    @Test
    void handlesDocumentFetchFailure() {
        UUID documentId = UUID.randomUUID();
        when(readerGeneratedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenThrow(new RestClientException("down"));

        assertThrows(
                DownstreamServiceException.class,
                () -> service.exportDocument(
                        documentId,
                        new DocumentExportRequest(List.of(ExportFormat.DOCX)),
                        OWNER));
    }

    @Test
    void handlesDocumentFileSaveFailure() {
        UUID documentId = UUID.randomUUID();
        when(readerGeneratedDocumentsApi.getDocumentById(documentId, OWNER))
                .thenReturn(document(documentId, GeneratedDocumentResponse.DocumentTypeEnum.CV));
        when(producerDocumentFilesApi.createDocumentFile(any(), isNull(), eq(OWNER)))
                .thenThrow(new RestClientException("down"));

        assertThrows(
                DownstreamServiceException.class,
                () -> service.exportDocument(
                        documentId,
                        new DocumentExportRequest(List.of(ExportFormat.PDF)),
                        OWNER));
    }

    @Test
    void editedDocxCreatesInactiveVersionAndMatchingPdfBeforeActivation() throws Exception {
        UUID currentId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        UUID uploadedId = UUID.randomUUID();
        UUID pdfId = UUID.randomUUID();
        byte[] editedDocx = docx(
                "Taylor Candidate",
                "Personal Summary",
                "Edited authoritative achievement");
        MockMultipartFile file = upload(editedDocx);
        GeneratedDocumentResponse current =
                document(currentId, GeneratedDocumentResponse.DocumentTypeEnum.CV);
        GeneratedDocumentResponse replacement = replacement(replacementId);
        StoreDocumentFileResponse uploaded = uploadedFile(uploadedId, replacementId);
        DocumentFileResponse pdf = pdfFile(pdfId, replacementId);

        when(readerGeneratedDocumentsApi.getDocumentById(currentId, OWNER))
                .thenReturn(current);
        when(producerGeneratedDocumentsApi.createDocument(any(), anyString(), eq(OWNER)))
                .thenAnswer(invocation -> {
                    CreateDocumentRequest request = invocation.getArgument(0);
                    return replacement.content(request.getContent());
                });
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId))).thenReturn(uploaded);
        when(producerDocumentFilesApi.createDocumentFile(any(), anyString(), eq(OWNER)))
                .thenReturn(pdf);
        when(producerGeneratedDocumentsApi.activateDocumentVersion(
                APPLICATION_ID,
                DocumentKind.CV.name(),
                replacementId,
                OWNER)).thenReturn(replacement.active(true));
        when(readerDocumentFilesApi.getLatestFilesForDocument(replacementId, OWNER))
                .thenReturn(List.of(docxFile(uploadedId, replacementId), pdf));

        DocumentUploadResponse response = service.uploadReplacement(
                currentId,
                file,
                DocumentKind.CV,
                ExportFormat.DOCX,
                OWNER,
                "replacement-request-123");

        ArgumentCaptor<CreateDocumentRequest> documentRequest =
                ArgumentCaptor.forClass(CreateDocumentRequest.class);
        ArgumentCaptor<String> documentKey = ArgumentCaptor.forClass(String.class);
        verify(producerGeneratedDocumentsApi).createDocument(
                documentRequest.capture(),
                documentKey.capture(),
                eq(OWNER));
        assertEquals(OWNER, documentRequest.getValue().getUserId());
        assertEquals(APPLICATION_ID, documentRequest.getValue().getApplicationId());
        assertEquals(Boolean.FALSE, documentRequest.getValue().getActive());
        assertEquals(
                CreateDocumentRequest.SourceTypeEnum.UPLOADED,
                documentRequest.getValue().getSourceType());
        assertTrue(documentRequest.getValue().getContent()
                .contains("Edited authoritative achievement"));
        assertFalse(documentRequest.getValue().getContent().contains("Stale generated text"));

        ArgumentCaptor<CreateDocumentFileRequest> pdfRequest =
                ArgumentCaptor.forClass(CreateDocumentFileRequest.class);
        ArgumentCaptor<String> pdfKey = ArgumentCaptor.forClass(String.class);
        verify(producerDocumentFilesApi).createDocumentFile(
                pdfRequest.capture(),
                pdfKey.capture(),
                eq(OWNER));
        byte[] pdfBytes = Base64.getDecoder().decode(
                pdfRequest.getValue().getFileContentBase64());
        String pdfText = pdfText(pdfBytes);
        assertTrue(pdfText.contains("Taylor Candidate"));
        assertTrue(pdfText.contains("Personal Summary"));
        assertTrue(pdfText.contains("Edited authoritative achievement"));
        assertFalse(pdfText.contains("Stale generated text"));
        assertNotEquals(documentKey.getValue(), pdfKey.getValue());

        ArgumentCaptor<HttpEntity> uploadEntity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(
                anyString(),
                uploadEntity.capture(),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId));
        assertEquals(OWNER, uploadEntity.getValue().getHeaders().getFirst("X-Document-Owner"));
        assertTrue(uploadEntity.getValue().getHeaders().containsKey("Idempotency-Key"));
        verify(producerGeneratedDocumentsApi).activateDocumentVersion(
                APPLICATION_ID,
                DocumentKind.CV.name(),
                replacementId,
                OWNER);
        verify(readerDocumentFilesApi).getLatestFilesForDocument(replacementId, OWNER);
        InOrder writeOrder = inOrder(
                producerGeneratedDocumentsApi,
                restTemplate,
                producerDocumentFilesApi);
        writeOrder.verify(producerGeneratedDocumentsApi)
                .createDocument(any(), anyString(), eq(OWNER));
        writeOrder.verify(restTemplate).postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId));
        writeOrder.verify(producerDocumentFilesApi)
                .createDocumentFile(any(), anyString(), eq(OWNER));
        writeOrder.verify(producerGeneratedDocumentsApi).activateDocumentVersion(
                APPLICATION_ID,
                DocumentKind.CV.name(),
                replacementId,
                OWNER);

        assertEquals(replacementId, response.getGeneratedDocumentId());
        assertEquals(uploadedId, response.getUploadedFile().getFileId());
        assertEquals(pdfId, response.getRegeneratedFiles().get(0).getFileId());
    }

    @Test
    void partialPdfFailureNeverActivatesTheIncompleteReplacement() throws Exception {
        UUID currentId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        byte[] editedDocx = docx("Taylor Candidate", "Edited content");
        GeneratedDocumentResponse replacement = replacement(replacementId);

        when(readerGeneratedDocumentsApi.getDocumentById(currentId, OWNER))
                .thenReturn(document(currentId, GeneratedDocumentResponse.DocumentTypeEnum.CV));
        when(producerGeneratedDocumentsApi.createDocument(any(), anyString(), eq(OWNER)))
                .thenReturn(replacement);
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId))).thenReturn(uploadedFile(UUID.randomUUID(), replacementId));
        when(producerDocumentFilesApi.createDocumentFile(any(), anyString(), eq(OWNER)))
                .thenThrow(new RestClientException("pdf store unavailable"));

        assertThrows(
                DownstreamServiceException.class,
                () -> service.uploadReplacement(
                        currentId,
                        upload(editedDocx),
                        DocumentKind.CV,
                        ExportFormat.DOCX,
                        OWNER,
                        "partial-failure-123"));

        verify(producerGeneratedDocumentsApi, never()).activateDocumentVersion(
                anyString(), anyString(), any(), anyString());
        verifyNoInteractions(readerDocumentFilesApi);
    }

    @Test
    void retryUsesTheSameDownstreamOperationKeysAndReplacementVersion() throws Exception {
        UUID currentId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        byte[] editedDocx = docx("Taylor Candidate", "Retry-safe edit");
        GeneratedDocumentResponse replacement = replacement(replacementId);
        StoreDocumentFileResponse uploaded = uploadedFile(UUID.randomUUID(), replacementId);
        DocumentFileResponse pdf = pdfFile(UUID.randomUUID(), replacementId);

        when(readerGeneratedDocumentsApi.getDocumentById(currentId, OWNER))
                .thenReturn(document(currentId, GeneratedDocumentResponse.DocumentTypeEnum.CV));
        when(producerGeneratedDocumentsApi.createDocument(any(), anyString(), eq(OWNER)))
                .thenReturn(replacement);
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId))).thenReturn(uploaded);
        when(producerDocumentFilesApi.createDocumentFile(any(), anyString(), eq(OWNER)))
                .thenReturn(pdf);
        when(producerGeneratedDocumentsApi.activateDocumentVersion(
                APPLICATION_ID,
                DocumentKind.CV.name(),
                replacementId,
                OWNER)).thenReturn(replacement.active(true));
        when(readerDocumentFilesApi.getLatestFilesForDocument(replacementId, OWNER))
                .thenReturn(List.of(docxFile(uploaded.getId(), replacementId), pdf));

        for (int attempt = 0; attempt < 2; attempt++) {
            service.uploadReplacement(
                    currentId,
                    upload(editedDocx),
                    DocumentKind.CV,
                    ExportFormat.DOCX,
                    OWNER,
                    "stable-retry-key-123");
        }

        ArgumentCaptor<String> documentKeys = ArgumentCaptor.forClass(String.class);
        verify(producerGeneratedDocumentsApi, org.mockito.Mockito.times(2))
                .createDocument(any(), documentKeys.capture(), eq(OWNER));
        assertEquals(documentKeys.getAllValues().get(0), documentKeys.getAllValues().get(1));

        ArgumentCaptor<String> pdfKeys = ArgumentCaptor.forClass(String.class);
        verify(producerDocumentFilesApi, org.mockito.Mockito.times(2))
                .createDocumentFile(any(), pdfKeys.capture(), eq(OWNER));
        assertEquals(pdfKeys.getAllValues().get(0), pdfKeys.getAllValues().get(1));

        ArgumentCaptor<HttpEntity> uploadEntities = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).postForObject(
                anyString(),
                uploadEntities.capture(),
                eq(StoreDocumentFileResponse.class),
                eq(replacementId));
        assertEquals(
                uploadEntities.getAllValues().get(0).getHeaders().getFirst("Idempotency-Key"),
                uploadEntities.getAllValues().get(1).getHeaders().getFirst("Idempotency-Key"));
    }

    @Test
    void wrongDocumentKindFailsBeforeAnyReplacementWrite() throws Exception {
        UUID currentId = UUID.randomUUID();
        when(readerGeneratedDocumentsApi.getDocumentById(currentId, OWNER))
                .thenReturn(document(
                        currentId,
                        GeneratedDocumentResponse.DocumentTypeEnum.COVER_LETTER));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadReplacement(
                        currentId,
                        upload(docx("Edited cover letter")),
                        DocumentKind.CV,
                        ExportFormat.DOCX,
                        OWNER,
                        "wrong-kind-123"));

        assertTrue(exception.getMessage().contains("does not match"));
        verifyNoInteractions(
                producerGeneratedDocumentsApi,
                producerDocumentFilesApi,
                readerDocumentFilesApi,
                restTemplate);
    }

    @Test
    void unlinkedDocumentFailsBeforeAnyReplacementWrite() throws Exception {
        UUID currentId = UUID.randomUUID();
        GeneratedDocumentResponse current =
                document(currentId, GeneratedDocumentResponse.DocumentTypeEnum.CV)
                        .applicationId(null);
        when(readerGeneratedDocumentsApi.getDocumentById(currentId, OWNER))
                .thenReturn(current);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadReplacement(
                        currentId,
                        upload(docx("Edited CV")),
                        DocumentKind.CV,
                        ExportFormat.DOCX,
                        OWNER,
                        "unlinked-123"));

        assertTrue(exception.getMessage().contains("linked to an application"));
        verifyNoInteractions(
                producerGeneratedDocumentsApi,
                producerDocumentFilesApi,
                readerDocumentFilesApi,
                restTemplate);
    }

    @Test
    void pdfReplacementValueIsRejectedBeforeAnyStoreCall() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "edited.pdf",
                DocumentExportService.PDF_MIME_TYPE,
                "%PDF-1.4".getBytes());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadReplacement(
                        UUID.randomUUID(),
                        file,
                        DocumentKind.CV,
                        ExportFormat.PDF,
                        OWNER,
                        "pdf-replacement-123"));

        assertTrue(exception.getMessage().contains("Only .docx"));
        verifyNoInteractions(
                readerGeneratedDocumentsApi,
                producerGeneratedDocumentsApi,
                producerDocumentFilesApi,
                readerDocumentFilesApi,
                restTemplate);
    }

    private GeneratedDocumentResponse document(
            UUID documentId,
            GeneratedDocumentResponse.DocumentTypeEnum type) {
        return new GeneratedDocumentResponse()
                .id(documentId)
                .userId(OWNER)
                .jobId("job-123")
                .applicationId(APPLICATION_ID)
                .documentType(type)
                .title(type == GeneratedDocumentResponse.DocumentTypeEnum.CV
                        ? "Tailored CV"
                        : "Tailored Cover Letter")
                .content("Stale generated text")
                .version(1)
                .active(true);
    }

    private GeneratedDocumentResponse replacement(UUID documentId) {
        return document(documentId, GeneratedDocumentResponse.DocumentTypeEnum.CV)
                .version(2)
                .active(false)
                .sourceType(GeneratedDocumentResponse.SourceTypeEnum.UPLOADED);
    }

    private StoreDocumentFileResponse uploadedFile(UUID fileId, UUID documentId) {
        StoreDocumentFileResponse response = new StoreDocumentFileResponse();
        response.setId(fileId);
        response.setGeneratedDocumentId(documentId);
        response.setFileType(ExportFormat.DOCX);
        response.setFileName("edited.docx");
        response.setMimeType(DocumentExportService.DOCX_MIME_TYPE);
        return response;
    }

    private DocumentFileResponse docxFile(UUID fileId, UUID documentId) {
        return new DocumentFileResponse()
                .id(fileId)
                .generatedDocumentId(documentId)
                .fileType(DocumentFileResponse.FileTypeEnum.DOCX)
                .fileName("edited.docx")
                .mimeType(DocumentExportService.DOCX_MIME_TYPE);
    }

    private DocumentFileResponse pdfFile(UUID fileId, UUID documentId) {
        return new DocumentFileResponse()
                .id(fileId)
                .generatedDocumentId(documentId)
                .fileType(DocumentFileResponse.FileTypeEnum.PDF)
                .fileName("tailored-cv.pdf")
                .mimeType(MediaType.APPLICATION_PDF_VALUE);
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "edited.docx",
                DocumentExportService.DOCX_MIME_TYPE,
                bytes);
    }

    private byte[] docx(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                document.createParagraph().createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private String pdfText(byte[] bytes) throws IOException {
        PdfReader reader = new PdfReader(bytes);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }
}
