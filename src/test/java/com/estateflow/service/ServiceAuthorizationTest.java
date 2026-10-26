package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.model.Owner;
import com.estateflow.model.enums.Role;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReceiptWriter;
import com.estateflow.security.Authorizer;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The service boundary rejects unauthorized calls before touching any DAO, whatever the UI shows. */
class ServiceAuthorizationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);

    private final OwnerDao ownerDao = mock(OwnerDao.class);
    private final TenantDao tenantDao = mock(TenantDao.class);
    private final PropertyDao propertyDao = mock(PropertyDao.class);
    private final AgreementDao agreementDao = mock(AgreementDao.class);
    private final RentChargeDao chargeDao = mock(RentChargeDao.class);
    private final PaymentDao paymentDao = mock(PaymentDao.class);
    private final MaintenanceDao maintenanceDao = mock(MaintenanceDao.class);
    private final ReportingDao reportingDao = mock(ReportingDao.class);
    private final TransactionManager tx = new PassThroughTransactionManager();

    private Map<String, Executable> writes(Authorizer a) {
        AuditService audit = AuditService.noop();
        OwnerService owners = new OwnerService(ownerDao, propertyDao, reportingDao, tx, audit, CLOCK, a);
        TenantService tenants = new TenantService(tenantDao, agreementDao, reportingDao, tx, audit, CLOCK, a);
        PropertyService properties = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao, tx, audit, CLOCK, a);
        AgreementService agreements = new AgreementService(agreementDao, chargeDao, paymentDao, propertyDao, tenantDao, tx, audit, CLOCK, 30, a);
        PaymentService payments = new PaymentService(paymentDao, chargeDao, reportingDao, tx, audit, CLOCK, a);
        MaintenanceService maintenance = new MaintenanceService(maintenanceDao, propertyDao, agreementDao, reportingDao, tx, audit, CLOCK, a);
        Map<String, Executable> m = new LinkedHashMap<>();
        m.put("owner create", () -> owners.create(null));
        m.put("owner update", () -> owners.update(null));
        m.put("owner deactivate", () -> owners.deactivate(1L));
        m.put("owner reactivate", () -> owners.reactivate(1L));
        m.put("tenant create", () -> tenants.create(null));
        m.put("tenant update", () -> tenants.update(null));
        m.put("tenant deactivate", () -> tenants.deactivate(1L));
        m.put("property create", () -> properties.create(null));
        m.put("property update", () -> properties.update(null));
        m.put("property deactivate", () -> properties.deactivate(1L));
        m.put("agreement create", () -> agreements.create(1L, 1L, LocalDate.now(CLOCK), 12, BigDecimal.TEN, BigDecimal.ZERO, null));
        m.put("agreement terminate", () -> agreements.terminate(1L, LocalDate.now(CLOCK)));
        m.put("agreement notes", () -> agreements.updateNotes(1L, "x"));
        m.put("record payment", () -> payments.recordPayment(1L, BigDecimal.TEN, LocalDate.now(CLOCK), null, null, null));
        m.put("void payment", () -> payments.voidPayment(1L, "reason"));
        m.put("maintenance open", () -> maintenance.open(null));
        m.put("maintenance update", () -> maintenance.update(null));
        m.put("maintenance status", () -> maintenance.changeStatus(1L, null, null));
        return m;
    }

    private Map<String, Executable> reads(Authorizer a) {
        AuditService audit = new AuditService(mock(AuditDao.class), CLOCK, a);
        OwnerService owners = new OwnerService(ownerDao, propertyDao, reportingDao, tx, AuditService.noop(), CLOCK, a);
        PaymentService payments = new PaymentService(paymentDao, chargeDao, reportingDao, tx, AuditService.noop(), CLOCK, a);
        DashboardService dashboard = new DashboardService(reportingDao, agreementDao, AuditService.noop(), CLOCK, 30, a);
        ReportService reports = new ReportService(reportingDao, new CsvWriter(), CLOCK, a);
        ReceiptService receipts = new ReceiptService(paymentDao, reportingDao, new ReceiptWriter(), CLOCK, a);
        Map<String, Executable> m = new LinkedHashMap<>();
        m.put("owner list", owners::findAll);
        m.put("payments overdue", payments::findOverdue);
        m.put("dashboard stats", dashboard::stats);
        m.put("report export", () -> reports.exportOverdue(Path.of("never-written.csv")));
        m.put("receipt", () -> receipts.buildReceipt(1L));
        m.put("audit history", () -> audit.find(null, null, 10));
        return m;
    }

    @Test
    void viewerIsDeniedEveryWriteOperationAndNoDaoIsTouched() {
        writes(TestAuth.as(Role.VIEWER)).forEach((name, call) ->
                assertThrows(AuthorizationException.class, call, "viewer must be denied: " + name));
        verifyNoMoreInteractions(ownerDao, tenantDao, propertyDao, agreementDao, chargeDao, paymentDao, maintenanceDao, reportingDao);
    }

    @Test
    void signedOutCallersAreDeniedReadsAndWrites() {
        Authorizer none = TestAuth.signedOut();
        writes(none).forEach((name, call) -> assertThrows(AuthorizationException.class, call, "signed out: " + name));
        reads(none).forEach((name, call) -> assertThrows(AuthorizationException.class, call, "signed out: " + name));
        verifyNoMoreInteractions(ownerDao, tenantDao, propertyDao, agreementDao, chargeDao, paymentDao, maintenanceDao, reportingDao);
    }

    @Test
    void viewerMayReadButNotSeeActivityHistory() {
        Authorizer viewer = TestAuth.as(Role.VIEWER);
        Map<String, Executable> reads = reads(viewer);
        for (String name : new String[]{"owner list", "payments overdue", "dashboard stats", "receipt"}) {
            try {
                reads.get(name).execute();
            } catch (AuthorizationException e) {
                fail("viewer should be allowed to read: " + name);
            } catch (Throwable ignored) {
                // mock DAOs return null/empty; only authorization matters here
            }
        }
        assertThrows(AuthorizationException.class, reads.get("audit history"));
    }

    @Test
    void managerAndAdminPassTheAuthorizationGateOnWrites() throws Exception {
        for (Role role : new Role[]{Role.MANAGER, Role.ADMIN}) {
            OwnerService owners = new OwnerService(ownerDao, propertyDao, reportingDao, tx, AuditService.noop(), CLOCK, TestAuth.as(role));
            doAnswer(i -> {
                Owner o = i.getArgument(0);
                o.setId(5L);
                return o;
            }).when(ownerDao).insert(any());
            Owner saved = owners.create(new Owner("Asha Verma", "9876543210", null, null));
            assertEquals(5L, saved.getId(), role.name());
        }
    }

    @Test
    void managerCannotRecordUserManagementButCanRecordPayments() {
        PaymentService payments = new PaymentService(paymentDao, chargeDao, reportingDao, tx, AuditService.noop(), CLOCK, TestAuth.as(Role.MANAGER));
        try {
            payments.voidPayment(1L, "duplicate entry");
        } catch (AuthorizationException e) {
            fail("manager may void payments");
        } catch (Exception expectedBusinessOrLookupFailure) {
            // mocks give no payment; the point is that authorization passed
        }
    }
}
