package com.estateflow.exception;

/**
 * File output failed during report export. Cause is typically IOException.
 */
public class ReportExportException extends EstateFlowException {

    public ReportExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
