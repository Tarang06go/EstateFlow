package com.estateflow.util;

import com.estateflow.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place that turns an exception into a dialog and, when unexpected, one log entry (Section 7.3).
 * Expected business failures are shown and not logged.
 */
public final class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    static final String DATABASE_MESSAGE = "The database operation failed. Check that MySQL is running. "
            + "Details were written to the log.";
    static final String UNEXPECTED_MESSAGE = "Unexpected error. Details were written to the log.";

    private ErrorHandler() {
    }

    public static void handle(Throwable t) {
        if (t instanceof ValidationException e) {
            AlertUtil.warning("Please check the form", e.getMessage());
        } else if (t instanceof BusinessRuleException e) {
            AlertUtil.warning("Not allowed", e.getMessage());
        } else if (t instanceof EntityNotFoundException e) {
            AlertUtil.warning("Record not found", e.getMessage());
        } else if (t instanceof DuplicateEntityException e) {
            AlertUtil.warning("Already exists", e.getMessage());
        } else if (t instanceof ReportExportException e) {
            log.warn("Export failed", e);
            AlertUtil.error("Export failed", e.getMessage());
        } else if (t instanceof ConfigurationException e) {
            log.error("Configuration error", e);
            AlertUtil.error("Configuration error", e.getMessage());
        } else if (t instanceof DataAccessException e) {
            log.error("Database operation failed", e);
            AlertUtil.error("Database error", DATABASE_MESSAGE);
        } else {
            log.error("Unexpected error", t);
            AlertUtil.error("Unexpected error", UNEXPECTED_MESSAGE);
        }
    }

    /** True for failures where the screen should reload because the record changed or vanished. */
    public static boolean shouldRefresh(Throwable t) {
        return t instanceof EntityNotFoundException;
    }
}
