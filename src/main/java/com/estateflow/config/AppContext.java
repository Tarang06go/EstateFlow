package com.estateflow.config;

import com.estateflow.dao.*;
import com.estateflow.dao.jdbc.*;
import com.estateflow.db.DatabaseManager;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.report.CsvWriter;
import com.estateflow.util.UiFormat;
import com.estateflow.report.ReceiptWriter;
import com.estateflow.security.Authorizer;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.Session;
import com.estateflow.service.*;
import com.estateflow.service.biometric.*;

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
    private final Session session;
    private final Authorizer authorizer;
    private final DepositService depositService;
    private final LeaseRenewalService leaseRenewalService;
    private final ExpenseService expenseService;
    private final ProfitabilityService profitabilityService;
    private final AuthService authService;
    private final UserManagementService userManagementService;
    private final UserPreferences preferences;
    private final UiFormat uiFormat;
    private final DocumentService documentService;
    private final InspectionService inspectionService;
    private final VendorService vendorService;
    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final AdvancedAnalyticsService advancedAnalyticsService;
    private final PropertyAssistantService propertyAssistantService;
    private final RentCollectionInsightsService rentCollectionInsightsService;
    private final FacialVerificationService facialVerificationService;

    public AppContext(AppConfig config) {
        this(config, Clock.systemDefaultZone());
    }

    public AppContext(AppConfig config, Clock clock) {
        this(config, clock, UserPreferences.load(UserPreferences.defaultFile()));
    }

    public AppContext(AppConfig config, Clock clock, UserPreferences preferences) {
        this.config = config;
        this.preferences = preferences;
        this.uiFormat = new UiFormat(preferences);
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

        session = new Session();
        authorizer = new Authorizer(session);
        UserDao userDao = new JdbcUserDao(databaseManager);
        PasswordHasher hasher = new PasswordHasher();

        int warningDays = config.leaseExpiryWarningDays();
        auditService = new AuditService(auditDao, clock, authorizer);
        authService = new AuthService(userDao, hasher, session, tx, clock, auditService);
        userManagementService = new UserManagementService(userDao, hasher, tx, authorizer, auditService);
        DepositDao depositDao = new JdbcDepositDao(databaseManager);
        depositService = new DepositService(depositDao, agreementDao, tx, auditService, clock, authorizer);
        leaseRenewalService = new LeaseRenewalService(agreementDao, new JdbcLeaseDao(databaseManager), chargeDao, propertyDao, tenantDao, depositDao, tx, auditService, clock, authorizer);
        expenseService = new ExpenseService(new JdbcExpenseDao(databaseManager), propertyDao, maintenanceDao, tx, auditService, clock, authorizer);
        profitabilityService = new ProfitabilityService(new JdbcProfitabilityDao(databaseManager), new CsvWriter(), tx, clock, authorizer);
        ownerService = new OwnerService(ownerDao, propertyDao, reportingDao, tx, auditService, clock, authorizer);
        tenantService = new TenantService(tenantDao, agreementDao, reportingDao, tx, auditService, clock, authorizer);
        propertyService = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao, tx, auditService, clock, authorizer);
        agreementService = new AgreementService(agreementDao, chargeDao, paymentDao, propertyDao, tenantDao,
                tx, auditService, clock, warningDays, authorizer);
        paymentService = new PaymentService(paymentDao, chargeDao, reportingDao, tx, auditService, clock, authorizer);
        maintenanceService = new MaintenanceService(maintenanceDao, propertyDao, agreementDao, reportingDao, tx, auditService, clock, authorizer);
        dashboardService = new DashboardService(reportingDao, agreementDao, auditService, clock, warningDays, authorizer);
        reportService = new ReportService(reportingDao, new CsvWriter(), clock, config.rentReminderDays(), agreementDao, warningDays, authorizer);
        receiptService = new ReceiptService(paymentDao, reportingDao, new ReceiptWriter(), clock, authorizer);
        searchService = new SearchService(ownerDao, tenantDao, agreementDao, reportingDao, clock, authorizer);
        rentReminderService = new RentReminderService(reportingDao, clock, config.rentReminderDays(), authorizer);
        documentService = new DocumentService(new JdbcDocumentDao(databaseManager), new DocumentStorage(DocumentStorage.defaultRoot()), tx, auditService, authorizer);
        inspectionService = new InspectionService(new JdbcInspectionDao(databaseManager), propertyDao, agreementDao, maintenanceService, tx, auditService, clock, authorizer);
        VendorDao vendorDao = new JdbcVendorDao(databaseManager);
        vendorService = new VendorService(vendorDao, tx, auditService, authorizer);
        workOrderService = new WorkOrderService(new JdbcWorkOrderDao(databaseManager), propertyDao, vendorDao,
                maintenanceService, expenseService, tx, auditService, clock, authorizer);
        notificationService = new NotificationService(rentReminderService, leaseRenewalService, workOrderService,
                preferences, clock, warningDays, authorizer);
        advancedAnalyticsService = new AdvancedAnalyticsService(reportingDao, profitabilityService, maintenanceService,
                leaseRenewalService, workOrderService, tx, new CsvWriter(), clock, warningDays, authorizer);
        propertyAssistantService = new PropertyAssistantService(advancedAnalyticsService, dashboardService,
                leaseRenewalService, maintenanceService, workOrderService, authorizer, clock, warningDays,
                ownerService, tenantService, propertyService, agreementService, paymentService, expenseService, depositService,
                config.assistantProvider().equals("none") ? com.estateflow.service.GeneralAssistantProvider.disabled() : null);
        rentCollectionInsightsService = new RentCollectionInsightsService(new JdbcRentCollectionInsightsDao(databaseManager),
                tx, clock, new CsvWriter(), authorizer);
        FaceModelFiles faceModels = new FaceModelFiles(config.faceModelsDirectory());
        CameraProvider faceCamera = new OpenCvCameraProvider();
        FaceEngine faceEngine = new OpenCvFaceEngine(faceModels, (float) config.faceDetectionScoreThreshold());
        TemplateKeyProvider faceKeys = createFaceKeyProvider();
        facialVerificationService = new FacialVerificationService(faceCamera, faceEngine, faceModels,
                new JdbcBiometricDao(databaseManager), userDao, new AesGcmFaceTemplateCipher(faceKeys),
                faceKeys, authService, authorizer, auditService, clock, config.faceMatchThreshold(),
                java.time.Duration.ofSeconds(config.faceCameraTimeoutSeconds()), config.faceLoginEnabled());
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
    public UserPreferences preferences() { return preferences; }
    public UiFormat format() { return uiFormat; }
    public DepositService deposits() { return depositService; }
    public LeaseRenewalService leases() { return leaseRenewalService; }
    public ExpenseService expenses() { return expenseService; }
    public ProfitabilityService profitability() { return profitabilityService; }
    public Session session() { return session; }
    public Authorizer authorizer() { return authorizer; }
    public AuthService auth() { return authService; }
    public UserManagementService users() { return userManagementService; }
    public DocumentService documents() { return documentService; }
    public InspectionService inspections() { return inspectionService; }
    public VendorService vendors() { return vendorService; }
    public WorkOrderService workOrders() { return workOrderService; }
    public NotificationService notifications() { return notificationService; }
    public AdvancedAnalyticsService analytics() { return advancedAnalyticsService; }
    public PropertyAssistantService assistant() { return propertyAssistantService; }
    public RentCollectionInsightsService collectionInsights() { return rentCollectionInsightsService; }
    public FacialVerificationService facialVerification() { return facialVerificationService; }

    private static TemplateKeyProvider createFaceKeyProvider() {
        if (System.getProperty("os.name", "").toLowerCase().contains("windows")) return new TemplateKeyProvider() {
            private volatile TemplateKeyProvider delegate;
            private TemplateKeyProvider delegate() {
                if(delegate==null) synchronized(this) { if(delegate==null) delegate=new LocalDpapiTemplateKeyProvider(new KeyProtector() {
                    private volatile KeyProtector dpapi;
                    private KeyProtector dpapi() {
                        if(dpapi==null) synchronized(this) { if(dpapi==null) dpapi=new WindowsDpapiKeyProtector(); }
                        return dpapi;
                    }
                    public byte[] protect(byte[] key){return dpapi().protect(key);}
                    public byte[] unprotect(byte[] wrapped){return dpapi().unprotect(wrapped);}
                }); }
                return delegate;
            }
            public String deviceId(){return delegate().deviceId();}
            public byte[] createOrLoadKey(long userId){return delegate().createOrLoadKey(userId);}
            public java.util.Optional<byte[]> loadExistingKey(long userId){return delegate().loadExistingKey(userId);}
            public void deleteKey(long userId){delegate().deleteKey(userId);}
        };
        return new TemplateKeyProvider() {
            public String deviceId() { return "unsupported-platform"; }
            public byte[] createOrLoadKey(long userId) { throw new IllegalStateException("Windows DPAPI is required."); }
            public java.util.Optional<byte[]> loadExistingKey(long userId) { return java.util.Optional.empty(); }
            public void deleteKey(long userId) { }
        };
    }
}
