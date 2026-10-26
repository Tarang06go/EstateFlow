package com.estateflow.service;

import com.estateflow.dao.ExpenseDao;
import com.estateflow.dao.MaintenanceDao;
import com.estateflow.dao.PropertyDao;
import com.estateflow.dto.AuditEntry;
import com.estateflow.exception.*;
import com.estateflow.model.Expense;
import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.Property;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.support.FakeAuditDao;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private final ExpenseDao expenseDao = mock(ExpenseDao.class);
    private final PropertyDao propertyDao = mock(PropertyDao.class);
    private final MaintenanceDao maintenanceDao = mock(MaintenanceDao.class);
    private FakeAuditDao auditDao;
    private ExpenseService service;
    private Property property;

    private ExpenseService serviceFor(Authorizer auth) {
        return new ExpenseService(expenseDao, propertyDao, maintenanceDao, new PassThroughTransactionManager(),
                new AuditService(auditDao, CLOCK, auth), CLOCK, auth);
    }

    @BeforeEach
    void setUp() {
        auditDao = new FakeAuditDao();
        service = serviceFor(TestAuth.admin());
        property = new Property();
        property.setPropertyId(7L);
        property.setName("Flat 7");
        property.setActive(true);
        when(propertyDao.findById(7L)).thenReturn(Optional.of(property));
        when(expenseDao.insert(any())).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            e.setExpenseId(55L);
            return e;
        });
    }

    private Expense valid() {
        Expense e = new Expense();
        e.setPropertyId(7L);
        e.setCategory(ExpenseCategory.UTILITIES);
        e.setAmount(new BigDecimal("1250.50"));
        e.setExpenseDate(TODAY);
        e.setDescription("  Electricity bill September ");
        e.setPaymentMethod(PaymentMethod.UPI);
        e.setReferenceNo("  INV-1 ");
        return e;
    }

    private void assertInvalid(Expense e, String fragment) {
        ValidationException ex = assertThrows(ValidationException.class, () -> service.create(e));
        assertTrue(ex.getMessage().toLowerCase().contains(fragment.toLowerCase()), ex.getMessage());
        verify(expenseDao, never()).insert(any());
    }

    // ---- create + audit ----

    @Test
    void createStoresTrimmedValuesAndAuditsWithTheActingUser() throws Exception {
        Expense saved = service.create(valid());
        assertEquals(55L, saved.getExpenseId());
        assertEquals("Electricity bill September", saved.getDescription());
        assertEquals("INV-1", saved.getReferenceNo());
        assertEquals(new BigDecimal("1250.50"), saved.getAmount());
        assertEquals(1, auditDao.entries.size());
        AuditEntry a = auditDao.entries.get(0);
        assertEquals("EXPENSE", a.entityType());
        assertEquals(55L, a.entityId());
        assertEquals("CREATED", a.action());
        assertEquals("admin-test", a.actorUsername());
        assertEquals(1L, a.actorUserId());
        assertTrue(a.newValue().contains("1250.50") && a.newValue().contains("UTILITIES"));
    }

    @Test
    void managerMayWriteViewerAndSignedOutMayNot() throws Exception {
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.MANAGER)).create(valid()));
        for (Authorizer denied : new Authorizer[]{TestAuth.as(Role.VIEWER), TestAuth.signedOut()}) {
            ExpenseService s = serviceFor(denied);
            assertThrows(AuthorizationException.class, () -> s.create(valid()));
            assertThrows(AuthorizationException.class, () -> s.update(withId(valid(), 55L)));
            assertThrows(AuthorizationException.class, () -> s.delete(55L, "reason"));
        }
        verify(expenseDao, times(1)).insert(any()); // only the manager's
        verify(expenseDao, never()).update(any());
        verify(expenseDao, never()).softDelete(anyLong(), any(), any());
    }

    @Test
    void viewerCanReadButSignedOutCannot() throws Exception {
        when(expenseDao.search(any(), any(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of());
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.VIEWER)).search(null, null, null, null, false));
        assertThrows(AuthorizationException.class, () -> serviceFor(TestAuth.signedOut()).search(null, null, null, null, false));
        assertThrows(AuthorizationException.class, () -> serviceFor(TestAuth.signedOut()).get(1));
    }

    // ---- validation ----

    @Test
    void requiredFieldsAreChecked() {
        Expense e = valid(); e.setPropertyId(null); assertInvalid(e, "property");
        e = valid(); e.setCategory(null); assertInvalid(e, "category");
        e = valid(); e.setExpenseDate(null); assertInvalid(e, "date");
        e = valid(); e.setPaymentMethod(null); assertInvalid(e, "payment method");
        e = valid(); e.setDescription("   "); assertInvalid(e, "description");
        e = valid(); e.setDescription("x".repeat(256)); assertInvalid(e, "description");
        e = valid(); e.setReferenceNo("r".repeat(61)); assertInvalid(e, "reference");
        assertThrows(ValidationException.class, () -> service.create(null));
    }

    @Test
    void invalidAmountsAreRejected() {
        for (String bad : new String[]{"0", "0.00", "-1", "-0.01", "10.999", "10000000000.00"}) {
            Expense e = valid();
            e.setAmount(new BigDecimal(bad));
            assertThrows(ValidationException.class, () -> service.create(e), bad);
        }
        Expense e = valid(); e.setAmount(null); assertInvalid(e, "amount");
        verify(expenseDao, never()).insert(any());
    }

    @Test
    void amountBoundariesAreAccepted() throws Exception {
        Expense small = valid(); small.setAmount(new BigDecimal("0.01"));
        assertEquals(new BigDecimal("0.01"), service.create(small).getAmount());
        Expense whole = valid(); whole.setAmount(new BigDecimal("500")); whole.setDescription("whole rupees");
        assertEquals(new BigDecimal("500.00"), service.create(whole).getAmount());
        Expense max = valid(); max.setAmount(new BigDecimal("9999999999.99")); max.setDescription("max");
        assertDoesNotThrow(() -> service.create(max));
    }

    @Test
    void dateBoundaries() throws Exception {
        Expense tomorrow = valid(); tomorrow.setExpenseDate(TODAY.plusDays(1));
        assertInvalid(tomorrow, "future");
        Expense ancient = valid(); ancient.setExpenseDate(LocalDate.of(1999, 12, 31));
        assertInvalid(ancient, "before");
        Expense today = valid(); today.setExpenseDate(TODAY);
        assertDoesNotThrow(() -> service.create(today));
        Expense earliest = valid(); earliest.setExpenseDate(LocalDate.of(2000, 1, 1)); earliest.setDescription("earliest");
        assertDoesNotThrow(() -> service.create(earliest));
    }

    @Test
    void missingPropertyIsReported() {
        when(propertyDao.findById(99L)).thenReturn(Optional.empty());
        Expense e = valid(); e.setPropertyId(99L);
        assertThrows(EntityNotFoundException.class, () -> service.create(e));
    }

    @Test
    void identicalExpenseIsRejectedAsADuplicate() {
        when(expenseDao.existsIdentical(eq(7L), eq(ExpenseCategory.UTILITIES), eq(new BigDecimal("1250.50")), eq(TODAY),
                eq("Electricity bill September"), isNull())).thenReturn(true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(valid()));
        assertEquals("EXP-01", ex.getRuleId());
        verify(expenseDao, never()).insert(any());
    }

    // ---- maintenance link ----

    private MaintenanceRequest request(long id, long propertyId, MaintenanceStatus status) {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestId(id);
        r.setPropertyId(propertyId);
        r.setStatus(status);
        return r;
    }

    @Test
    void linkedExpenseRulesAreEnforced() throws Exception {
        when(maintenanceDao.findById(1L)).thenReturn(Optional.of(request(1, 7, MaintenanceStatus.RESOLVED)));
        when(maintenanceDao.findById(2L)).thenReturn(Optional.of(request(2, 8, MaintenanceStatus.RESOLVED)));
        when(maintenanceDao.findById(3L)).thenReturn(Optional.of(request(3, 7, MaintenanceStatus.CANCELLED)));

        Expense ok = valid(); ok.setCategory(ExpenseCategory.REPAIRS); ok.setMaintenanceRequestId(1L);
        assertEquals(1L, service.create(ok).getMaintenanceRequestId());

        Expense otherProperty = valid(); otherProperty.setCategory(ExpenseCategory.REPAIRS); otherProperty.setMaintenanceRequestId(2L);
        assertEquals("EXP-02", assertThrows(BusinessRuleException.class, () -> service.create(otherProperty)).getRuleId());
        Expense cancelled = valid(); cancelled.setCategory(ExpenseCategory.REPAIRS); cancelled.setMaintenanceRequestId(3L);
        assertEquals("EXP-02", assertThrows(BusinessRuleException.class, () -> service.create(cancelled)).getRuleId());
        Expense wrongCategory = valid(); wrongCategory.setCategory(ExpenseCategory.INSURANCE); wrongCategory.setMaintenanceRequestId(1L);
        assertEquals("EXP-02", assertThrows(BusinessRuleException.class, () -> service.create(wrongCategory)).getRuleId());
        Expense missing = valid(); missing.setCategory(ExpenseCategory.REPAIRS); missing.setMaintenanceRequestId(404L);
        assertThrows(EntityNotFoundException.class, () -> service.create(missing));
    }

    @Test
    void secondActiveExpenseForTheSameRequestIsRefusedByTheDatabaseConstraint() throws Exception {
        when(maintenanceDao.findById(1L)).thenReturn(Optional.of(request(1, 7, MaintenanceStatus.RESOLVED)));
        doThrow(new com.estateflow.exception.DuplicateKeyException("dup",
                new SQLException("dup", "23000", 1062), "uq_expense_active_maintenance")).when(expenseDao).insert(any());
        Expense e = valid(); e.setCategory(ExpenseCategory.MAINTENANCE); e.setMaintenanceRequestId(1L);
        assertEquals("EXP-03", assertThrows(BusinessRuleException.class, () -> service.create(e)).getRuleId());
        assertTrue(auditDao.entries.isEmpty());
    }

    // ---- update ----

    private Expense withId(Expense e, long id) {
        e.setExpenseId(id);
        return e;
    }

    private Expense stored(long id, boolean deleted) {
        Expense s = valid();
        s.setExpenseId(id);
        s.setDescription("Electricity bill September");
        s.setReferenceNo("INV-1");
        if (deleted) {
            s.setDeletedAt(java.time.LocalDateTime.of(2026, 10, 9, 8, 0));
            s.setDeleteReason("typo");
        }
        return s;
    }

    @Test
    void updateAuditsOldAndNewValues() throws Exception {
        Expense before = stored(55, false);
        Expense after = stored(55, false);
        after.setAmount(new BigDecimal("1300.00"));
        when(expenseDao.findById(55L)).thenReturn(Optional.of(before), Optional.of(after));
        when(expenseDao.update(any())).thenReturn(1);
        Expense edit = withId(valid(), 55L);
        edit.setAmount(new BigDecimal("1300.00"));
        Expense result = service.update(edit);
        assertEquals(new BigDecimal("1300.00"), result.getAmount());
        AuditEntry a = auditDao.entries.get(0);
        assertEquals("UPDATED", a.action());
        assertTrue(a.oldValue().contains("1250.50") && a.newValue().contains("1300.00"));
        assertEquals("admin-test", a.actorUsername());
    }

    @Test
    void deletedOrMissingExpensesCannotBeEdited() {
        when(expenseDao.findById(55L)).thenReturn(Optional.of(stored(55, true)));
        assertEquals("EXP-04", assertThrows(BusinessRuleException.class, () -> service.update(withId(valid(), 55L))).getRuleId());
        when(expenseDao.findById(56L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.update(withId(valid(), 56L)));
        assertThrows(ValidationException.class, () -> service.update(valid()), "update needs an id");
        verify(expenseDao, never()).update(any());
    }

    @Test
    void raceWhereTheRowIsDeletedBetweenReadAndWriteIsReported() {
        when(expenseDao.findById(55L)).thenReturn(Optional.of(stored(55, false)));
        when(expenseDao.update(any())).thenReturn(0);
        assertEquals("EXP-04", assertThrows(BusinessRuleException.class, () -> service.update(withId(valid(), 55L))).getRuleId());
        assertTrue(auditDao.entries.isEmpty());
    }

    // ---- soft delete ----

    @Test
    void deleteIsASoftDeleteWithAReasonAndAnAuditEntry() throws Exception {
        when(expenseDao.findById(55L)).thenReturn(Optional.of(stored(55, false)));
        when(expenseDao.softDelete(eq(55L), eq("entered twice"), any())).thenReturn(1);
        service.delete(55L, "  entered twice ");
        verify(expenseDao).softDelete(eq(55L), eq("entered twice"), eq(java.time.LocalDateTime.of(2026, 10, 10, 8, 0)));
        AuditEntry a = auditDao.entries.get(0);
        assertEquals("DELETED", a.action());
        assertTrue(a.newValue().contains("entered twice") && a.newValue().contains("deleted"));
        assertEquals("admin-test", a.actorUsername());
    }

    @Test
    void deleteNeedsAReasonAndAnExistingActiveExpense() {
        assertThrows(ValidationException.class, () -> service.delete(55L, "  "));
        assertThrows(ValidationException.class, () -> service.delete(55L, null));
        assertThrows(ValidationException.class, () -> service.delete(55L, "x".repeat(256)));
        when(expenseDao.findById(55L)).thenReturn(Optional.of(stored(55, true)));
        assertEquals("EXP-04", assertThrows(BusinessRuleException.class, () -> service.delete(55L, "again")).getRuleId());
        when(expenseDao.findById(57L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.delete(57L, "gone"));
        verify(expenseDao, never()).softDelete(anyLong(), any(), any());
        assertTrue(auditDao.entries.isEmpty());
    }

    // ---- search / setup / audit failure ----

    @Test
    void searchRejectsAnInvertedDateRange() {
        assertThrows(ValidationException.class, () -> service.search(null, null, TODAY, TODAY.minusDays(1), false));
        assertDoesNotThrow(() -> service.search(null, null, TODAY, TODAY, false));
    }

    @Test
    void missingTableGivesASetupMessage() {
        when(expenseDao.isAvailable()).thenReturn(false);
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> service.ensureReady());
        assertTrue(e.getMessage().contains("v1.4_expense.sql"));
    }

    @Test
    void anAuditFailureAbortsTheOperation() {
        auditDao.failInserts = true;
        assertThrows(DataAccessException.class, () -> service.create(valid()));
    }
}
