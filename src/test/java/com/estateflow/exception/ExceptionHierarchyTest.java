package com.estateflow.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    @DisplayName("ValidationException retains message and fieldName")
    void testValidationException() {
        ValidationException ex = new ValidationException("Invalid phone number", "phone");
        assertEquals("Invalid phone number", ex.getMessage());
        assertEquals("phone", ex.getFieldName());
        assertTrue(ex instanceof EstateFlowException);
    }

    @Test
    @DisplayName("BusinessRuleException carries ruleId")
    void testBusinessRuleException() {
        BusinessRuleException ex = new BusinessRuleException("Cannot deactivate owner with active property", "BR-02");
        assertEquals("Cannot deactivate owner with active property", ex.getMessage());
        assertEquals("BR-02", ex.getRuleId());
        assertTrue(ex instanceof EstateFlowException);
    }

    @Test
    @DisplayName("DataAccessException retains SQLException cause and message")
    void testDataAccessException() {
        SQLException sqlEx = new SQLException("Connection refused", "08S01", 1045);
        DataAccessException ex = new DataAccessException("Failed to query owners", sqlEx);

        assertEquals("Failed to query owners", ex.getMessage());
        assertSame(sqlEx, ex.getCause());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    @DisplayName("DuplicateKeyException retains constraint name")
    void testDuplicateKeyException() {
        SQLException sqlEx = new SQLException("Duplicate entry '9876543210' for key 'uq_owner_phone'", "23000", 1062);
        DuplicateKeyException ex = new DuplicateKeyException("Phone already in use", sqlEx, "uq_owner_phone");

        assertEquals("Phone already in use", ex.getMessage());
        assertEquals("uq_owner_phone", ex.getConstraintName());
        assertSame(sqlEx, ex.getCause());
        assertTrue(ex instanceof DataAccessException);
    }

    @Test
    @DisplayName("ReportExportException carries IOException cause")
    void testReportExportException() {
        IOException ioEx = new IOException("Disk full");
        ReportExportException ex = new ReportExportException("Export failed", ioEx);

        assertEquals("Export failed", ex.getMessage());
        assertSame(ioEx, ex.getCause());
        assertTrue(ex instanceof EstateFlowException);
    }

    @Test
    @DisplayName("EntityNotFoundException and DuplicateEntityException")
    void testOtherBusinessExceptions() {
        EntityNotFoundException enf = new EntityNotFoundException("Owner 42 not found");
        assertEquals("Owner 42 not found", enf.getMessage());
        assertTrue(enf instanceof EstateFlowException);

        DuplicateEntityException dup = new DuplicateEntityException("Phone number already exists");
        assertEquals("Phone number already exists", dup.getMessage());
        assertTrue(dup instanceof EstateFlowException);
    }
}
