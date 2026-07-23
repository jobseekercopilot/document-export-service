package com.jobseekercopilot.documentexport.exception;

public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
