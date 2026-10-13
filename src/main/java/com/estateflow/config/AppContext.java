package com.estateflow.config;

import com.estateflow.dao.*;
import com.estateflow.dao.jdbc.*;
import com.estateflow.db.DatabaseManager;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReceiptWriter;
import com.estateflow.service.*;

import java.time.Clock;

/** Composition root: the only place that constructs and wires infrastructure, DAOs and services. */
public class AppContext {

    private final AppConfig config;
    private final Clock clock;
    private final DatabaseManager databaseManager;
    private final OwnerService ownerService;
    private final TenantService tenantService;
    private final PropertyService propertyService;
    private final AgreementService agreementService;
    private final PaymentService paymentService;
    private final MaintenanceService maintenanceService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final ReceiptService receiptService;
    private final SearchService searchService;
    private final RentReminderService rentReminderService;
    private final AuditService auditService;

    public AppContext(AppConfig config) {
        this(config, Clock.systemDefaultZone());
    }

    public AppContext(AppConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.databaseManager = new DatabaseManager(config);
        TransactionManager tx = new JdbcTransactionManager(databaseManager);

        OwnerDao ownerDao = new JdbcOwnerDao(databaseManager);
        TenantDao tenantDao = new JdbcTenantDao(databaseManager);
        PropertyDao propertyDao = new JdbcPropertyDao(databaseManager);
        AgreementDao agreementDao = new JdbcAgreementDao(databaseManager);
        RentChargeDao chargeDao = new JdbcRentChargeDao(databaseManager);
        PaymentDao paymentDao = new JdbcPaymentDao(databaseManager);
        MaintenanceDao maintenanceDao = new JdbcMaintenanceDao(databaseManager);
        AuditDao auditDao = new JdbcAuditDao(databaseManager);
        ReportingDao reportingDao = new JdbcReportingDao(databaseManager);

        int warningDays = config.leaseExpiryWarningDays();
        auditService = new AuditService(auditDao, clock);
        ownerService = new OwnerService(ownerDao, propertyDao, tx, auditService);
        tenantService = new TenantService(tenantDao, agreementDao, tx, auditService, clock);
        propertyService = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao, tx, auditService, clock);
        agreementService = new AgreementService(agreementDao, chargeDao, paymentDao, propertyDao, tenantDao,
                tx, auditService, clock, warningDays);
        paymentService = new PaymentService(paymentDao, chargeDao, reportingDao, tx, auditService, clock);
        maintenanceService = new MaintenanceService(maintenanceDao, propertyDao, agreementDao, reportingDao, tx, auditService, clock);
        dashboardService = new DashboardService(reportingDao, clock, warningDays);
        reportService = new ReportService(reportingDao, new CsvWriter(), clock, config.rentReminderDays());
        receiptService = new ReceiptService(paymentDao, reportingDao, new ReceiptWriter(), clock);
        searchService = new SearchService(ownerDao, tenantDao, agreementDao, reportingDao, clock);
        rentReminderService = new RentReminderService(reportingDao, clock, config.rentReminderDays());
    }

    public AppConfig config() { return config; }
    public Clock clock() { return clock; }
    public DatabaseManager databaseManager() { return databaseManager; }
    public OwnerService owners() { return ownerService; }
    public TenantService tenants() { return tenantService; }
    public PropertyService properties() { return propertyService; }
    public AgreementService agreements() { return agreementService; }
    public PaymentService payments() { return paymentService; }
    public MaintenanceService maintenance() { return maintenanceService; }
    public DashboardService dashboard() { return dashboardService; }
    public ReportService reports() { return reportService; }
    public ReceiptService receipts() { return receiptService; }
    public SearchService search() { return searchService; }
    public RentReminderService reminders() { return rentReminderService; }
    public AuditService audit() { return auditService; }
}
