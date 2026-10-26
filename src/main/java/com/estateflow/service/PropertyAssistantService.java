package com.estateflow.service;

import com.estateflow.dto.AdvancedAnalyticsReport;
import com.estateflow.dto.AssistantReply;
import com.estateflow.dto.DashboardOverview;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ValidationException;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, local-only assistant. Answers come from authorized application services; no model or network is used. */
public final class PropertyAssistantService {
    private static final int QUESTION_LIMIT = 500;
    private static final int REPORT_MONTHS = 12;

    private final AdvancedAnalyticsService analytics;
    private final DashboardService dashboard;
    private final LeaseRenewalService leases;
    private final MaintenanceService maintenance;
    private final WorkOrderService workOrders;
    private final Authorizer auth;
    private final Clock clock;
    private final int leaseWarningDays;
    private final PropertyAssistantDataQueries dataQueries;
    private final GeneralAssistantProvider generalProvider;
    private final Map<Long, ConversationContext> conversations = new ConcurrentHashMap<>();

    public PropertyAssistantService(AdvancedAnalyticsService analytics, DashboardService dashboard,
                                    LeaseRenewalService leases, MaintenanceService maintenance,
                                    WorkOrderService workOrders, Authorizer auth, Clock clock,
                                    int leaseWarningDays) {
        this.analytics = analytics;
        this.dashboard = dashboard;
        this.leases = leases;
        this.maintenance = maintenance;
        this.workOrders = workOrders;
        this.auth = auth;
        this.clock = clock;
        this.leaseWarningDays = leaseWarningDays;
        this.dataQueries = null;
        this.generalProvider = GeneralAssistantProvider.disabled();
    }

    public PropertyAssistantService(AdvancedAnalyticsService analytics, DashboardService dashboard,
                                    LeaseRenewalService leases, MaintenanceService maintenance,
                                    WorkOrderService workOrders, Authorizer auth, Clock clock, int leaseWarningDays,
                                    OwnerService owners, TenantService tenants, PropertyService properties,
                                    AgreementService agreements, PaymentService payments, ExpenseService expenses,
                                    DepositService deposits) {
        this(analytics,dashboard,leases,maintenance,workOrders,auth,clock,leaseWarningDays,owners,tenants,properties,
                agreements,payments,expenses,deposits,GeneralAssistantProvider.disabled());
    }

    public PropertyAssistantService(AdvancedAnalyticsService analytics, DashboardService dashboard,
                                    LeaseRenewalService leases, MaintenanceService maintenance,
                                    WorkOrderService workOrders, Authorizer auth, Clock clock, int leaseWarningDays,
                                    OwnerService owners, TenantService tenants, PropertyService properties,
                                    AgreementService agreements, PaymentService payments, ExpenseService expenses,
                                    DepositService deposits, GeneralAssistantProvider generalProvider) {
        this.analytics=analytics; this.dashboard=dashboard; this.leases=leases; this.maintenance=maintenance;
        this.workOrders=workOrders; this.auth=auth; this.clock=clock; this.leaseWarningDays=leaseWarningDays;
        this.generalProvider=generalProvider==null?GeneralAssistantProvider.disabled():generalProvider;
        this.dataQueries=new PropertyAssistantDataQueries(owners,tenants,properties,agreements,payments,expenses,deposits,
                auth,analytics,dashboard,leases,maintenance,workOrders);
    }

    public AssistantReply answer(String question) throws EstateFlowException {
        var user = auth.require(Permission.VIEW_DATA);
        String text = question == null ? "" : question.trim();
        if (text.isEmpty() || text.length() > QUESTION_LIMIT) {
            throw new ValidationException("Enter a question up to 500 characters.", "question");
        }
        String query = text.toLowerCase(Locale.ROOT);
        String target = navigationTarget(query);
        if (target != null) return new AssistantReply(text,"Opening "+screenName(target)+". EstateFlow will enforce the permissions for that screen.",
                "Whitelisted in-app navigation",LocalDateTime.now(clock),target);
        if (dataQueries != null) {
            Topic topic=classify(query);
            ConversationContext previous=conversations.get(user.id());
            boolean followUp=topic==Topic.FOLLOW_UP;
            if(followUp){
                if(previous==null)return reply(text,"Which EstateFlow query should I continue? Ask for tenants, vacant properties, rent, leases, maintenance, or portfolio performance.","Clarification");
                topic=previous.topic();
                if(query.matches(".*\\btop\\s+\\d+\\b.*"))topic=Topic.FINANCIALS;
            }
            if(topic==Topic.AMBIGUOUS)return reply(text,"What would you like me to list or summarize: tenants, owners, properties, agreements, payments, expenses, deposits, or maintenance?","Clarification");
            LocalDate today=LocalDate.now(clock);
            Period period=periodFor(query,topic,followUp?previous.period():null,today);
            int days=requestedDays(query,followUp?previous.days():leaseWarningDays);
            if(topic==Topic.HELP)return reply(text,help(query),"EstateFlow workflow guidance");
            if(topic==Topic.GENERAL){
                var answer=generalProvider.answer(text);
                if(answer.isPresent())return reply(text,answer.get(),"Configured general assistant provider");
                return reply(text,"This installation has no general-purpose AI provider configured. I can explain EstateFlow workflows and answer supported questions from authorized application records, but I won't guess at unrelated questions.","Local capability notice");
            }
            String answer=dataQueries.answer(topic,query,period.from(),period.to(),period.label(),days,today);
            if(answer==null)return reply(text,"I couldn't map that request to a supported EstateFlow query. Please name the record or report you want.","Clarification");
            conversations.put(user.id(),new ConversationContext(topic,period,days));
            if(conversations.size()>64)conversations.keySet().removeIf(id->!id.equals(user.id()));
            return reply(text,answer,"Authorized EstateFlow records · "+period.label());
        }
        String answer;
        String source;
        if (containsAny(query, "forecast", "trend", "profit", "noi", "income", "expense")) {
            AdvancedAnalyticsReport r = analytics.report(reportFrom(), LocalDate.now(clock));
            answer = "Actuals for " + r.from() + " through " + r.to() + ": rent received " + r.profitability().rentReceived()
                    + ", operating expenses " + r.profitability().expenses() + ", NOI " + r.profitability().noi() + ". "
                    + "The next six months use a flat average from this period: estimated monthly receipts "
                    + r.forecast().getFirst().estimatedRentReceived() + ", expenses " + r.forecast().getFirst().estimatedOperatingExpenses()
                    + ", NOI " + r.forecast().getFirst().estimatedNoi() + ". This is a run-rate estimate, not a prediction.";
            source = "Advanced Analytics; actuals and run-rate estimate";
        } else if (containsAny(query, "rent", "arrears", "overdue", "collection", "outstanding")
                && !containsAny(query, "maintenance", "work order", "vendor", "repair")) {
            AdvancedAnalyticsReport r = analytics.report(reportFrom(), LocalDate.now(clock));
            BigDecimal overdue = r.arrears().stream().map(AdvancedAnalyticsReport.ArrearsBucket::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            answer = "Current overdue live rent balances total " + overdue + ". For charges due " + r.from() + " through " + r.to()
                    + ", " + r.collection().ratePercent() + "% has been paid to date; remaining balance on those charges is "
                    + r.collection().outstanding() + ". These figures exclude cancelled charges and voided payments.";
            source = "Advanced Analytics; current arrears and due-charge collection metrics";
        } else if (containsAny(query, "lease", "expir", "renew")) {
            List<ExpiringLeaseRow> rows = leases.endingWithinWarningWindow(leaseWarningDays);
            BigDecimal rentAtRisk = rows.stream().map(ExpiringLeaseRow::monthlyRent).reduce(BigDecimal.ZERO, BigDecimal::add);
            String earliest = rows.stream().map(ExpiringLeaseRow::effectiveEndDate).min(LocalDate::compareTo)
                    .map(Object::toString).orElse("none");
            answer = rows.size() + " lease(s) end within the configured " + leaseWarningDays + "-day warning window. "
                    + "Current monthly rent at risk is " + rentAtRisk + ". Earliest end date: " + earliest + ".";
            source = "Lease records and configured warning window";
        } else if (containsAny(query, "maintenance", "work order", "vendor", "repair")) {
            var summary = maintenance.summary();
            int open = summary.countByStatus().getOrDefault(com.estateflow.model.enums.MaintenanceStatus.OPEN, 0)
                    + summary.countByStatus().getOrDefault(com.estateflow.model.enums.MaintenanceStatus.IN_PROGRESS, 0);
            String orderSummary;
            boolean workOrderDataAvailable = workOrders.isAvailable();
            if (workOrderDataAvailable) {
                int overdue = workOrders.search(null, null, null, null, null, null, true).size();
                orderSummary = " Overdue work orders in the current result set: " + (overdue == 1000 ? "at least 1000" : overdue) + ".";
            } else {
                orderSummary = " Vendor and work-order details need the manual v1.7 migration.";
            }
            answer = "There are " + open + " open or in-progress maintenance request(s). Recorded maintenance costs are "
                    + summary.totalCost() + " (resolved " + summary.resolvedCost() + ", in progress " + summary.inProgressCost() + ")." + orderSummary;
            source = "Maintenance summary" + (workOrderDataAvailable ? " and current work orders" : "");
        } else if (containsAny(query, "occupancy", "vacant", "occupied", "property")) {
            var stats = dashboard.stats();
            answer = stats.occupancyPercent() + "% of active properties are occupied (" + stats.occupiedProperties() + " occupied, "
                    + stats.vacantProperties() + " vacant of " + stats.totalProperties() + "). This is current occupancy, not a historical vacancy rate.";
            source = "Dashboard occupancy snapshot";
        } else {
            answer = "I can summarize rent collection and arrears, leases ending soon, occupancy and vacancies, maintenance/work orders, or actual profitability and its labeled run-rate estimate. Ask about one of those topics.";
            source = "Local help";
        }
        return new AssistantReply(text, answer, source, LocalDateTime.now(clock));
    }

    private LocalDate reportFrom() {
        return YearMonth.from(LocalDate.now(clock)).minusMonths(REPORT_MONTHS - 1L).atDay(1);
    }

    public void clearConversation() { conversations.clear(); }

    private AssistantReply reply(String question,String answer,String source) {
        return new AssistantReply(question,answer,source,LocalDateTime.now(clock));
    }

    private static Topic classify(String q) {
        if(q.matches("^(what about|and|same for|how about|top \\d+|show me the top \\d+).*"))return Topic.FOLLOW_UP;
        if(containsAny(q,"income changed","income change","why did income","cash flow changed","cash flow change"))return Topic.FINANCIALS;
        if(containsAny(q,"how do i","how to","where do i","what does","explain","permission","role","how can i"))return Topic.HELP;
        if(containsAny(q,"portfolio","overall performance","property performance","summarize my portfolio","summarise my portfolio"))return Topic.PORTFOLIO;
        if(containsAny(q,"who hasn't paid","who has not paid","which tenant","tenants who","who owes","unpaid tenant"))return Topic.RENT;
        if(containsAny(q,"tenant","tenants","renter")&&containsAny(q,"list","show","who","tenant","register","active"))return Topic.TENANTS;
        if(containsAny(q,"owner","owners","landlord")&&containsAny(q,"list","show","who","owner","active"))return Topic.OWNERS;
        if(containsAny(q,"noi","profit","income","earned","cash flow","financial","forecast","trend","most rental","highest rent","top property","generated most","generated the most"))return Topic.FINANCIALS;
        if(containsAny(q,"vacant","occupied","occupancy"))return Topic.PROPERTIES;
        if(containsAny(q,"property","properties")&&containsAny(q,"list","show","all","which","performance","top"))return Topic.PROPERTIES;
        if(containsAny(q,"lease expiry","lease expiration","lease expiring","leases expiring","leases ending","expire","expir","renewal"))return Topic.LEASES;
        if(containsAny(q,"maintenance","work order","work orders","repair","vendor"))return Topic.MAINTENANCE;
        if(containsAny(q,"agreement","agreements","lease list","rental contracts"))return Topic.AGREEMENTS;
        if(containsAny(q,"payment","payments","receipt","receipts","ledger"))return Topic.PAYMENTS;
        if(containsAny(q,"expense","expenses","operating costs"))return Topic.EXPENSES;
        if(containsAny(q,"deposit","deposits","security deposit"))return Topic.DEPOSITS;
        if(containsAny(q,"rent","arrears","overdue","collection","outstanding","hasn't paid","have not paid"))return Topic.RENT;
        if(containsAny(q,"how many properties","occupancy","vacancy"))return Topic.OCCUPANCY;
        if(containsAny(q,"list","show me","summarize","summarise"))return Topic.AMBIGUOUS;
        return Topic.GENERAL;
    }

    private static Period periodFor(String q,Topic topic,Period previous,LocalDate today) {
        YearMonth month=YearMonth.from(today);
        if(q.contains("last month")||q.contains("previous month")){
            month=month.minusMonths(1);return new Period(month.atDay(1),month.atEndOfMonth(),month.toString());
        }
        if(q.contains("this month")||q.contains("current month"))return new Period(month.atDay(1),month.atEndOfMonth(),month.toString());
        if(previous!=null&&(q.startsWith("what about")||q.startsWith("same for")||q.matches("^(and|how about) (last|this) month.*")))return previous;
        if(topic==Topic.PORTFOLIO||topic==Topic.FINANCIALS&&containsAny(q,"trend","year","last 12","past year","changed","change","compare")){
            YearMonth start=month.minusMonths(11);return new Period(start.atDay(1),today,start+" through "+today);
        }
        return new Period(month.atDay(1),month.atEndOfMonth(),month.toString());
    }

    private static int requestedDays(String q,int fallback) {
        Matcher m=Pattern.compile("(?:next|within|in the next)\\s+(\\d{1,3})\\s+days?").matcher(q);
        if(!m.find())return fallback;
        try{return Math.max(1,Math.min(365,Integer.parseInt(m.group(1))));}catch(NumberFormatException ignored){return fallback;}
    }

    private static String navigationTarget(String q) {
        if(!(q.startsWith("open ")||q.startsWith("go to ")||q.startsWith("navigate to ")||q.startsWith("take me to ")))return null;
        if(containsAny(q,"new tenant form","tenant form"))return "tenant-form.fxml";
        if(containsAny(q,"new property form","property form"))return "property-form.fxml";
        if(containsAny(q,"new agreement form","agreement form"))return "agreement-form.fxml";
        if(q.contains("tenants")||q.contains("tenant"))return "tenants.fxml";if(q.contains("owners")||q.contains("owner"))return "owners.fxml";
        if(q.contains("properties")||q.contains("property list")||q.contains("property"))return "properties.fxml";
        if(q.contains("agreements")||q.contains("leases"))return "agreements.fxml";
        if(q.contains("rent ledger")||q.contains("ledger"))return "ledger.fxml";
        if(q.contains("payments"))return "payments.fxml";if(q.contains("maintenance"))return "maintenance.fxml";
        if(q.contains("work orders"))return "work-orders.fxml";if(q.contains("expenses"))return "expenses.fxml";
        if(q.contains("deposits"))return "deposits.fxml";if(q.contains("reports"))return "reports.fxml";
        if(q.contains("analytics"))return "analytics.fxml";if(q.contains("dashboard"))return "dashboard.fxml";
        if(q.contains("expiring leases"))return "expiring.fxml";return null;
    }

    private static String screenName(String target) {
        return switch(target){case "tenant-form.fxml"->"new tenant form";case "property-form.fxml"->"new property form";
            case "agreement-form.fxml"->"new agreement form";default->target.replace(".fxml","").replace('-',' ');};
    }

    private static String help(String q) {
        if(containsAny(q,"add a property","property"))return "To add a property, open Properties and choose Add property. Select an active owner and enter the property details before saving. This requires MANAGE_RECORDS.";
        if(containsAny(q,"register a tenant","add a tenant","tenant"))return "Open Tenants, choose Add tenant, enter the required details, and save. This requires MANAGE_RECORDS.";
        if(containsAny(q,"agreement","lease","rental"))return "Open Agreements and choose New agreement. Select an active property and tenant, set the term, rent, and required deposit, review the generated charge schedule, then save. This requires MANAGE_RECORDS.";
        if(containsAny(q,"record rent","record payment","collect rent","receipt"))return "Open Rent Ledger, select a due charge, and choose Record payment. A receipt is available for a valid payment. Recording payments requires RECORD_PAYMENTS.";
        if(containsAny(q,"void","cancel payment"))return "Open Payments, select the payment, choose Void, and enter a reason. A voided payment stays in the audit trail and is excluded from collection totals. This requires RECORD_PAYMENTS.";
        if(containsAny(q,"maintenance","work order","repair"))return "Open Maintenance to create or update a request. Requests follow the supported status workflow. Vendor assignments and work orders are managed in Work Orders and require MANAGE_RECORDS.";
        if(containsAny(q,"report","analytics"))return "Open Reports or Advanced Analytics. Report access requires VIEW_REPORTS. Collection uses charges by due date and valid receipts by payment date, shown separately.";
        if(containsAny(q,"collection rate","rent collected","arrears","outstanding rent"))return "Collection rate is valid payments received to date divided by live rent charges due in the selected period. Current arrears are live due charges that remain overdue; cancelled charges and voided payments are excluded.";
        if(containsAny(q,"dashboard","occupancy","noi","net operating income"))return "Dashboard occupancy is occupied active properties divided by all active properties today. NOI is valid rent received minus recorded operating expenses for the selected period; it is not rent billed or total profit.";
        if(containsAny(q,"permission","role","access"))return "Service permissions include VIEW_DATA for records, VIEW_REPORTS for reports, VIEW_TENANT_INSIGHTS for tenant-identifying arrears, MANAGE_RECORDS for business record changes, and RECORD_PAYMENTS for recording or voiding payments. Your role may have fewer permissions.";
        return "I can explain supported EstateFlow workflows and metrics for properties, tenants, agreements, rent, payments, maintenance, reports, and permissions.";
    }

    enum Topic { TENANTS, OWNERS, PROPERTIES, OCCUPANCY, PORTFOLIO, RENT, LEASES, MAINTENANCE, AGREEMENTS, PAYMENTS, EXPENSES, DEPOSITS, FINANCIALS, HELP, GENERAL, AMBIGUOUS, FOLLOW_UP }
    private record Period(LocalDate from,LocalDate to,String label){}
    private record ConversationContext(Topic topic,Period period,int days){}

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
