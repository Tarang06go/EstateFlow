package com.estateflow.service;

import com.estateflow.dto.*;
import com.estateflow.dto.AdvancedAnalyticsReport.*;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.model.Tenant;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropertyAssistantServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private final AdvancedAnalyticsService analytics = mock(AdvancedAnalyticsService.class);
    private final DashboardService dashboard = mock(DashboardService.class);
    private final LeaseRenewalService leases = mock(LeaseRenewalService.class);
    private final MaintenanceService maintenance = mock(MaintenanceService.class);
    private final WorkOrderService workOrders = mock(WorkOrderService.class);
    private final OwnerService owners = mock(OwnerService.class);
    private final TenantService tenants = mock(TenantService.class);
    private final PropertyService properties = mock(PropertyService.class);
    private final AgreementService agreements = mock(AgreementService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final ExpenseService expenses = mock(ExpenseService.class);
    private final DepositService deposits = mock(DepositService.class);

    private PropertyAssistantService service() {
        return new PropertyAssistantService(analytics, dashboard, leases, maintenance, workOrders,
                TestAuth.admin(), CLOCK, 30);
    }

    private PropertyAssistantService fullService(Authorizer auth) {
        return new PropertyAssistantService(analytics,dashboard,leases,maintenance,workOrders,auth,CLOCK,30,
                owners,tenants,properties,agreements,payments,expenses,deposits);
    }

    @Test void financialQuestionUsesAuthorizedAnalyticsAndLabelsTheEstimate() throws Exception {
        AdvancedAnalyticsReport report = new AdvancedAnalyticsReport(LocalDate.of(2025, 11, 1), TODAY,
                new CollectionMetrics(2, 1, bd("2000"), bd("1500"), bd("500"), bd("75.00")),
                new OccupancySummary(2, 1, 1, bd("50.00")), List.of(new ArrearsBucket("1-30 days", 1, bd("500"))),
                new ProfitabilityReport(LocalDate.of(2025, 11, 1), TODAY, null, bd("2000"), bd("1500"), bd("300"),
                        bd("1200"), BigDecimal.ZERO, List.of(), List.of(), List.of()),
                List.of(new ForecastMonth(YearMonth.of(2026, 11), bd("100"), bd("50"), bd("50"))),
                new LeaseExpirySummary(30, 0, BigDecimal.ZERO, List.of()),
                new MaintenanceSummary(Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of()), true, List.of());
        when(analytics.report(any(), eq(TODAY))).thenReturn(report);
        var reply = service().answer("Explain the cash flow trend");
        assertTrue(reply.answer().contains("NOI 1200"));
        assertTrue(reply.answer().contains("not a prediction"));
        assertEquals("Advanced Analytics; actuals and run-rate estimate", reply.source());
        assertEquals(LocalDateTime.of(2026, 10, 10, 0, 0), reply.generatedAt());
        verify(analytics).report(LocalDate.of(2025, 11, 1), TODAY);
    }

    @Test void leaseQuestionSummarizesCurrentExpiryExposure() throws Exception {
        when(leases.endingWithinWarningWindow(30)).thenReturn(List.of(
                new ExpiringLeaseRow(1, "A", "T", LocalDate.of(2026, 10, 20), 10, bd("900"), false)));
        var reply = service().answer("Which leases are expiring?");
        assertTrue(reply.answer().contains("1 lease(s)"));
        assertTrue(reply.answer().contains("900"));
        assertTrue(reply.answer().contains("2026-10-20"));
    }

    @Test void rentCollectionQuestionUsesAuthorizedAnalyticsAndCurrentArrears() throws Exception {
        AdvancedAnalyticsReport report = new AdvancedAnalyticsReport(LocalDate.of(2025, 11, 1), TODAY,
                new CollectionMetrics(4, 2, bd("4000"), bd("2500"), bd("1500"), bd("62.50")),
                new OccupancySummary(2, 1, 1, bd("50.00")), List.of(new ArrearsBucket("1-30 days", 1, bd("700"))),
                null, List.of(), null, null, true, List.of());
        when(analytics.report(any(), eq(TODAY))).thenReturn(report);
        var reply=service().answer("Summarize current rent arrears and collection rate");
        assertTrue(reply.answer().contains("700"));
        assertTrue(reply.answer().contains("62.50%"));
        verify(analytics).report(LocalDate.of(2025, 11, 1),TODAY);
    }

    @Test void maintenanceQuestionUsesCurrentAuthorizedMaintenanceSummary() throws Exception {
        when(maintenance.summary()).thenReturn(new MaintenanceSummary(
                Map.of(MaintenanceStatus.OPEN,2,MaintenanceStatus.IN_PROGRESS,1), Map.of(),
                bd("125"),bd("75"),null,List.of(),List.of()));
        var reply=service().answer("Summarize maintenance and overdue work orders");
        assertTrue(reply.answer().contains("3 open or in-progress"));
        assertTrue(reply.answer().contains("200"));
        assertTrue(reply.answer().contains("manual v1.7 migration"));
        verify(maintenance).summary();
    }

    @Test void propertyPerformanceQuestionUsesAuthorizedOccupancySnapshot() throws Exception {
        when(dashboard.stats()).thenReturn(new DashboardStats(8,6,2,bd("75.00"),4,1,
                bd("6500"),bd("500"),1,Map.of()));
        var reply=service().answer("Summarize property occupancy and vacancies");
        assertTrue(reply.answer().contains("75.00%"));
        assertTrue(reply.answer().contains("6 occupied, 2 vacant of 8"));
        verify(dashboard).stats();
    }

    @Test void unsupportedQuestionGetsLocalHelpAndInputIsBounded() throws Exception {
        assertTrue(service().answer("Tell me a joke").answer().contains("I can summarize"));
        assertThrows(ValidationException.class, () -> service().answer("  "));
        assertThrows(ValidationException.class, () -> service().answer("x".repeat(501)));
    }

    @Test void signedOutUserIsRejectedBeforeReadingAnySources() {
        PropertyAssistantService secured = new PropertyAssistantService(analytics, dashboard, leases, maintenance,
                workOrders, TestAuth.signedOut(), CLOCK, 30);
        assertThrows(AuthorizationException.class, () -> secured.answer("rent"));
        verifyNoInteractions(analytics, dashboard, leases, maintenance, workOrders);
    }

    @Test void tenantListReturnsAuthorizedNamesButNoContactOrIdentityDetails() throws Exception {
        Tenant tenant=new Tenant("Asha Rao","555-0100","asha@example.test","Private address");
        tenant.setIdProofNumber("ID-SECRET");
        when(tenants.findAll()).thenReturn(List.of(tenant));
        String answer=fullService(TestAuth.admin()).answer("Give me a list of my tenants").answer();
        assertTrue(answer.contains("Asha Rao"));
        assertFalse(answer.contains("555-0100"));
        assertFalse(answer.contains("ID-SECRET"));
    }

    @Test void vacantPropertiesAreListedFromCurrentOccupancyRows() throws Exception {
        when(properties.findRows("")).thenReturn(List.of(
                new PropertyOccupancyRow(1,"Garden Flat","Pune","Owner",true,false,null,bd("18000")),
                new PropertyOccupancyRow(2,"Lake House","Pune","Owner",true,true,"Current Tenant",bd("25000"))));
        String answer=fullService(TestAuth.admin()).answer("Show all vacant properties").answer();
        assertTrue(answer.contains("Garden Flat"));
        assertFalse(answer.contains("Lake House"));
        verify(properties).findRows("");
    }

    @Test void portfolioSummaryUsesActualServicesAndLabelsCashBasis() throws Exception {
        when(dashboard.stats()).thenReturn(new DashboardStats(2,1,1,bd("50.00"),1,0,bd("900"),BigDecimal.ZERO,0,Map.of()));
        when(tenants.findAll()).thenReturn(List.of(new Tenant("Asha","1","x",null)));
        when(agreements.findAllRows()).thenReturn(List.of());
        when(analytics.report(any(),eq(TODAY))).thenReturn(report(LocalDate.of(2025,11,1),TODAY));
        String answer=fullService(TestAuth.admin()).answer("Summarise my portfolio").answer();
        assertTrue(answer.contains("Active properties: 2"));
        assertTrue(answer.contains("Rent actually received"));
        assertTrue(answer.contains("not treated as cash received"));
        verify(analytics).report(LocalDate.of(2025,11,1),TODAY);
    }

    @Test void tenantIdentifyingOverdueAnswerRequiresDedicatedPermission() {
        PropertyAssistantService viewer=fullService(TestAuth.as(com.estateflow.model.enums.Role.VIEWER));
        assertThrows(AuthorizationException.class,()->viewer.answer("Which tenants haven't paid rent?"));
        verifyNoInteractions(payments);
    }

    @Test void tenantArrearsQueryUsesLiveOverdueServiceAndOmitsPhone() throws Exception {
        when(payments.findOverdue()).thenReturn(List.of(new OverdueRentRow(1,2,"Garden Flat","Asha", "555-0100",
                LocalDate.of(2026,9,1),LocalDate.of(2026,9,5),bd("1000"),bd("0"),bd("1000"),35)));
        String answer=fullService(TestAuth.admin()).answer("Who hasn't paid rent?").answer();
        assertTrue(answer.contains("Asha"));
        assertTrue(answer.contains(com.estateflow.util.MoneyUtil.format(bd("1000"))));
        assertFalse(answer.contains("555-0100"));
        verify(payments).findOverdue();
    }

    @Test void leaseQueryHonorsRequestedDaysAndReturnsRealRows() throws Exception {
        when(leases.endingWithinWarningWindow(45)).thenReturn(List.of(new ExpiringLeaseRow(1,"Garden Flat","Asha",
                LocalDate.of(2026,11,20),41,bd("18000"),false)));
        String answer=fullService(TestAuth.admin()).answer("Which leases expire in the next 45 days?").answer();
        assertTrue(answer.contains("next 45 days"));
        assertTrue(answer.contains("Garden Flat"));
        verify(leases).endingWithinWarningWindow(45);
    }

    @Test void followupLastMonthReusesTopicAndChangesOnlyReportingPeriod() throws Exception {
        when(analytics.report(any(),any())).thenReturn(report(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        PropertyAssistantService assistant=fullService(TestAuth.admin());
        assistant.answer("Summarize this month's rent collection");
        assistant.answer("What about last month?");
        verify(analytics).report(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));
        verify(analytics).report(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));
    }

    @Test void navigationIsWhitelistedAndDoesNotPerformRecordMutation() throws Exception {
        var reply=fullService(TestAuth.admin()).answer("Open tenants");
        assertEquals("tenants.fxml",reply.navigationTarget());
        assertTrue(reply.answer().contains("permissions"));
        verifyNoInteractions(tenants,payments,expenses,properties);
    }

    @Test void workflowQuestionsExplainRealPermissionAndGeneralQuestionsDoNotPretendToUseAi() throws Exception {
        var howTo=fullService(TestAuth.admin()).answer("How do I record rent?");
        assertTrue(howTo.answer().contains("RECORD_PAYMENTS"));
        var general=fullService(TestAuth.admin()).answer("What is the capital of France?");
        assertTrue(general.answer().contains("no general-purpose AI provider"));
    }

    @Test void emptyTenantListAndAmbiguousListQuestionAreHandledWithoutGuessing() throws Exception {
        when(tenants.findAll()).thenReturn(List.of());
        assertTrue(fullService(TestAuth.admin()).answer("List tenants").answer().contains("No matching tenant records"));
        var clarify=fullService(TestAuth.admin()).answer("Show me a list");
        assertTrue(clarify.answer().contains("What would you like me to list"));
        verify(tenants).findAll();
    }

    @Test void failedRecordServiceIsNotReportedAsSuccessfulResult() {
        when(tenants.findAll()).thenThrow(new IllegalStateException("temporary read failure"));
        assertThrows(IllegalStateException.class,()->fullService(TestAuth.admin()).answer("List tenants"));
    }

    @Test void topThreeFollowupRanksPropertiesByCashReceipts() throws Exception {
        ProfitabilityReport actual=new ProfitabilityReport(LocalDate.of(2025,11,1),TODAY,null,bd("3000"),bd("2700"),bd("100"),bd("2600"),
                BigDecimal.ZERO,List.of(),List.of(new ProfitabilityReport.PropertyProfit(1,"Low",true,bd("1000"),bd("500"),bd("0"),bd("500")),
                new ProfitabilityReport.PropertyProfit(2,"High",true,bd("2000"),bd("2000"),bd("100"),bd("1900"))),List.of());
        AdvancedAnalyticsReport report=new AdvancedAnalyticsReport(LocalDate.of(2025,11,1),TODAY,
                new CollectionMetrics(2,2,bd("3000"),bd("2700"),bd("300"),bd("90.00")),new OccupancySummary(2,2,0,bd("100.00")),
                List.of(),actual,List.of(),new LeaseExpirySummary(30,0,BigDecimal.ZERO,List.of()),
                new MaintenanceSummary(Map.of(),Map.of(),BigDecimal.ZERO,BigDecimal.ZERO,null,List.of(),List.of()),true,List.of());
        when(analytics.report(any(),any())).thenReturn(report);
        when(dashboard.stats()).thenReturn(new DashboardStats(0,0,0,bd("0"),0,0,bd("0"),bd("0"),0,Map.of()));
        when(tenants.findAll()).thenReturn(List.of());
        PropertyAssistantService assistant=fullService(TestAuth.admin());
        assistant.answer("Summarize my portfolio");
        String answer=assistant.answer("Show me the top 2").answer();
        assertTrue(answer.indexOf("High")<answer.indexOf("Low"));
        assertTrue(answer.contains("cash receipts"));
    }

    private AdvancedAnalyticsReport report(LocalDate from,LocalDate to) {
        return new AdvancedAnalyticsReport(from,to,new CollectionMetrics(1,1,bd("2000"),bd("1500"),bd("500"),bd("75.00")),
                new OccupancySummary(2,1,1,bd("50.00")),List.of(new ArrearsBucket("1-30 days",1,bd("500"))),
                new ProfitabilityReport(from,to,null,bd("2000"),bd("1500"),bd("300"),bd("1200"),BigDecimal.ZERO,List.of(),List.of(),List.of()),
                List.of(),new LeaseExpirySummary(30,0,BigDecimal.ZERO,List.of()),
                new MaintenanceSummary(Map.of(),Map.of(),BigDecimal.ZERO,BigDecimal.ZERO,null,List.of(),List.of()),true,List.of());
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
