package com.jobseekercopilot.documentexport.exception;

public class DocumentExportException extends RuntimeException {
    public DocumentExportException(String message) {
        super(message);
    }

    public DocumentExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
