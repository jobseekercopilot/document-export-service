package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.dto.DocumentExportItem;
import com.jobseekercopilot.documentexport.dto.DocumentExportRequest;
import com.jobseekercopilot.documentexport.dto.DocumentExportResponse;
import com.jobseekercopilot.documentexport.dto.DocumentKind;
import com.jobseekercopilot.documentexport.dto.DocumentUploadResponse;
import com.jobseekercopilot.documentexport.dto.ExportFormat;
import com.jobseekercopilot.documentexport.dto.LatestDocumentFiles;
import com.jobseekercopilot.documentexport.dto.StoreDocumentFileResponse;
import com.jobseekercopilot.documentexport.exception.DownstreamServiceException;
import com.jobseekercopilot.generated.documentstoreservice.api.DocumentFilesApi;
import com.jobseekercopilot.generated.documentstoreservice.api.GeneratedDocumentsApi;
import com.jobseekercopilot.generated.documentstoreservice.model.CreateDocumentFileRequest;
import com.jobseekercopilot.generated.documentstoreservice.model.DocumentFileResponse;
import com.jobseekercopilot.generated.documentstoreservice.model.GeneratedDocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentExportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExportService.class);
    private static final String DOCUMENT_OWNER_HEADER = "X-Document-Owner";

    public static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String PDF_MIME_TYPE = "application/pdf";

    private final GeneratedDocumentsApi generatedDocumentsApi;
    private final DocumentFilesApi producerDocumentFilesApi;
    private final DocumentFilesApi readerDocumentFilesApi;
    private final DocxExportService docxExportService;
    private final PdfExportService pdfExportService;
    private final RestTemplate restTemplate;

    @Value("${services.document-store-service.base-url:http://localhost:8089}")
    private String documentStoreBaseUrl;

    public DocumentExportService(
            GeneratedDocumentsApi generatedDocumentsApi,
            @Qualifier("documentStoreProducerFilesApi")
            DocumentFilesApi producerDocumentFilesApi,
            @Qualifier("documentStoreReaderFilesApi")
            DocumentFilesApi readerDocumentFilesApi,
            DocxExportService docxExportService,
            PdfExportService pdfExportService,
            RestTemplate restTemplate) {
        this.generatedDocumentsApi = generatedDocumentsApi;
        this.producerDocumentFilesApi = producerDocumentFilesApi;
        this.readerDocumentFilesApi = readerDocumentFilesApi;
        this.docxExportService = docxExportService;
        this.pdfExportService = pdfExportService;
        this.restTemplate = restTemplate;
    }

    public DocumentExportResponse exportDocument(
            UUID documentId,
            DocumentExportRequest request,
            String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Document export started documentId={} formats={}",
                documentId,
                request == null || request.getFormats() == null ? 0 : request.getFormats().size());
        GeneratedDocumentResponse document = fetchDocument(documentId, documentOwner);
        List<DocumentExportItem> exports = new ArrayList<>();

        for (ExportFormat format : new LinkedHashSet<>(request.getFormats())) {
            exports.add(exportAndSave(document, format, documentOwner));
        }

        log.info("Document export completed documentId={} exportCount={} durationMs={}",
                documentId,
                exports.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return DocumentExportResponse.builder()
                .documentId(documentId)
                .exports(exports)
                .build();
    }

    public DocumentUploadResponse uploadReplacement(
            UUID generatedDocumentId,
            MultipartFile file,
            DocumentKind documentKind,
            ExportFormat uploadedFormat,
            String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Document export replacement upload started generatedDocumentId={} documentKind={} uploadedFormat={} sizeBytes={}",
                generatedDocumentId,
                documentKind,
                uploadedFormat,
                file == null ? 0 : file.getSize());
        fetchDocument(generatedDocumentId, documentOwner);
        validateUpload(file, documentKind, uploadedFormat);

        StoreDocumentFileResponse uploaded = uploadToStore(
                generatedDocumentId,
                file,
                uploadedFormat,
                documentOwner);
        GeneratedDocumentResponse document = fetchDocument(generatedDocumentId, documentOwner);
        DocumentFileResponse pdf = saveFile(
                generatedDocumentId,
                ExportFormat.PDF,
                fileName(document, ExportFormat.PDF),
                PDF_MIME_TYPE,
                pdfExportService.export(document),
                documentOwner);
        List<DocumentExportItem> regeneratedFiles = List.of(toExportItem(pdf));
        String message = "Document replaced successfully. PDF version has been updated.";

        LatestDocumentFiles latestFiles = latestFiles(generatedDocumentId, documentOwner);
        log.info("Document export replacement upload completed generatedDocumentId={} uploadedFormat={} durationMs={}",
                generatedDocumentId,
                uploadedFormat,
                (System.nanoTime() - startedAt) / 1_000_000);
        return DocumentUploadResponse.builder()
                .generatedDocumentId(generatedDocumentId)
                .uploadedFile(toExportItem(uploaded))
                .regeneratedFiles(regeneratedFiles)
                .latestFiles(latestFiles)
                .message(message)
                .build();
    }

    private GeneratedDocumentResponse fetchDocument(UUID documentId, String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Calling document-store-service get generated document documentId={}", documentId);
        try {
            GeneratedDocumentResponse document =
                    generatedDocumentsApi.getDocumentById(documentId, documentOwner);
            if (document == null || document.getId() == null) {
                throw new DownstreamServiceException("Document store returned no generated document", null);
            }
            log.info("document-store-service returned generated document documentId={} documentType={} durationMs={}",
                    documentId,
                    document.getDocumentType(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return document;
        } catch (RestClientException exception) {
            log.warn("document-store-service failed to fetch generated document documentId={} durationMs={} error={}",
                    documentId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(),
                    exception);
            throw new DownstreamServiceException("Document store failed to fetch generated document", exception);
        }
    }

    private DocumentExportItem exportAndSave(
            GeneratedDocumentResponse document,
            ExportFormat format,
            String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Document render started documentId={} format={}", document.getId(), format);
        byte[] bytes = switch (format) {
            case DOCX -> docxExportService.export(document);
            case PDF -> pdfExportService.export(document);
        };
        log.info("Document render completed documentId={} format={} sizeBytes={} durationMs={}",
                document.getId(),
                format,
                bytes == null ? 0 : bytes.length,
                (System.nanoTime() - startedAt) / 1_000_000);
        String mimeType = mimeType(format);
        String fileName = fileName(document, format);

        DocumentFileResponse savedFile =
                saveFile(document.getId(), format, fileName, mimeType, bytes, documentOwner);
        if (savedFile == null || savedFile.getId() == null) {
            throw new DownstreamServiceException("Document store returned no exported file ID", null);
        }

        return DocumentExportItem.builder()
                .fileId(savedFile.getId())
                .format(format)
                .fileName(fileName)
                .mimeType(mimeType)
                .downloadUrl("/api/v1/document-files/" + savedFile.getId() + "/download")
                .build();
    }

    private StoreDocumentFileResponse uploadToStore(
            UUID generatedDocumentId,
            MultipartFile file,
            ExportFormat uploadedFormat,
            String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Calling document-store-service upload replacement generatedDocumentId={} format={} sizeBytes={}",
                generatedDocumentId,
                uploadedFormat,
                file == null ? 0 : file.getSize());
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(mimeType(uploadedFormat)));
            body.add("file", new HttpEntity<>(resource, fileHeaders));
            body.add("fileType", uploadedFormat.name());
            body.add("source", "USER_UPLOADED");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(DOCUMENT_OWNER_HEADER, documentOwner);

            StoreDocumentFileResponse response = restTemplate.postForObject(
                    documentStoreBaseUrl + "/api/v1/documents/{generatedDocumentId}/files/upload",
                    new HttpEntity<>(body, headers),
                    StoreDocumentFileResponse.class,
                    generatedDocumentId);
            log.info("document-store-service upload replacement returned generatedDocumentId={} fileId={} durationMs={}",
                    generatedDocumentId,
                    response == null ? null : response.getId(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return response;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded file");
        } catch (RestClientException exception) {
            log.warn("document-store-service upload replacement failed generatedDocumentId={} durationMs={} error={}",
                    generatedDocumentId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(),
                    exception);
            throw new DownstreamServiceException("Document store failed to save uploaded " + uploadedFormat + " file", exception);
        }
    }

    private LatestDocumentFiles latestFiles(UUID generatedDocumentId, String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Calling document-store-service latest files generatedDocumentId={}", generatedDocumentId);
        try {
            List<DocumentFileResponse> latest =
                    readerDocumentFilesApi.getLatestFilesForDocument(
                            generatedDocumentId,
                            documentOwner);
            DocumentExportItem docx = null;
            DocumentExportItem pdf = null;
            for (DocumentFileResponse file : latest == null ? List.<DocumentFileResponse>of() : latest) {
                if (file.getFileType() == DocumentFileResponse.FileTypeEnum.DOCX && docx == null) {
                    docx = toExportItem(file);
                }
                if (file.getFileType() == DocumentFileResponse.FileTypeEnum.PDF && pdf == null) {
                    pdf = toExportItem(file);
                }
            }
            log.info("document-store-service latest files returned generatedDocumentId={} count={} durationMs={}",
                    generatedDocumentId,
                    latest == null ? 0 : latest.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return LatestDocumentFiles.builder().docx(docx).pdf(pdf).build();
        } catch (RestClientException exception) {
            log.warn("document-store-service latest files failed generatedDocumentId={} durationMs={} error={}",
                    generatedDocumentId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(),
                    exception);
            throw new DownstreamServiceException("Document store failed to fetch latest document files", exception);
        }
    }

    private DocumentExportItem toExportItem(StoreDocumentFileResponse file) {
        if (file == null || file.getId() == null) {
            throw new DownstreamServiceException("Document store returned no uploaded file ID", null);
        }
        ExportFormat format = file.getFileType();
        return DocumentExportItem.builder()
                .fileId(file.getId())
                .format(format)
                .fileName(file.getFileName())
                .mimeType(file.getMimeType())
                .downloadUrl("/api/v1/document-files/" + file.getId() + "/download")
                .build();
    }

    private DocumentExportItem toExportItem(DocumentFileResponse file) {
        if (file == null || file.getId() == null) {
            throw new DownstreamServiceException("Document store returned no exported file ID", null);
        }
        ExportFormat format = ExportFormat.valueOf(file.getFileType().getValue());
        return DocumentExportItem.builder()
                .fileId(file.getId())
                .format(format)
                .fileName(file.getFileName())
                .mimeType(file.getMimeType())
                .downloadUrl("/api/v1/document-files/" + file.getId() + "/download")
                .build();
    }

    private DocumentFileResponse saveFile(
            UUID documentId,
            ExportFormat format,
            String fileName,
            String mimeType,
            byte[] bytes,
            String documentOwner) {
        long startedAt = System.nanoTime();
        log.info("Calling document-store-service save exported file documentId={} format={} sizeBytes={}",
                documentId,
                format,
                bytes == null ? 0 : bytes.length);
        CreateDocumentFileRequest request = new CreateDocumentFileRequest()
                .generatedDocumentId(documentId)
                .fileType(toStoreFileType(format))
                .fileName(fileName)
                .mimeType(mimeType)
                .fileContentBase64(Base64.getEncoder().encodeToString(bytes));
        try {
            DocumentFileResponse response =
                    producerDocumentFilesApi.createDocumentFile(request, documentOwner);
            log.info("document-store-service save exported file returned documentId={} format={} fileId={} durationMs={}",
                    documentId,
                    format,
                    response == null ? null : response.getId(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return response;
        } catch (RestClientException exception) {
            log.warn("document-store-service save exported file failed documentId={} format={} durationMs={} error={}",
                    documentId,
                    format,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(),
                    exception);
            throw new DownstreamServiceException("Document store failed to save exported " + format + " file", exception);
        }
    }

    private CreateDocumentFileRequest.FileTypeEnum toStoreFileType(ExportFormat format) {
        return switch (format) {
            case DOCX -> CreateDocumentFileRequest.FileTypeEnum.DOCX;
            case PDF -> CreateDocumentFileRequest.FileTypeEnum.PDF;
        };
    }

    private String mimeType(ExportFormat format) {
        return switch (format) {
            case DOCX -> DOCX_MIME_TYPE;
            case PDF -> PDF_MIME_TYPE;
        };
    }

    private String fileName(GeneratedDocumentResponse document, ExportFormat format) {
        String extension = format.name().toLowerCase(Locale.ROOT);
        String prefix = slug(document.getTitle());
        return prefix + "-" + document.getId() + "." + extension;
    }

    private void validateUpload(MultipartFile file, DocumentKind documentKind, ExportFormat uploadedFormat) {
        if (documentKind == null) {
            throw new IllegalArgumentException("documentKind is required");
        }
        if (uploadedFormat == null) {
            throw new IllegalArgumentException("uploadedFormat is required");
        }
        if (uploadedFormat != ExportFormat.DOCX) {
            throw new IllegalArgumentException("Only .docx files can be uploaded.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is required");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!originalName.endsWith("." + uploadedFormat.name().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Uploaded file extension does not match " + uploadedFormat);
        }
        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)
                && !mimeType(uploadedFormat).equals(contentType)) {
            throw new IllegalArgumentException("Uploaded file MIME type does not match " + uploadedFormat);
        }
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "document";
        }
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            return "document";
        }
        return slug.length() > 40 ? slug.substring(0, 40).replaceAll("-$", "") : slug;
    }
}
