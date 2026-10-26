package com.estateflow.service;

import com.estateflow.dto.*;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.*;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, read-only assistant queries delegated to the application's permission-checked services. */
final class PropertyAssistantDataQueries {
    private static final int LIMIT = 15;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final OwnerService owners;
    private final TenantService tenants;
    private final PropertyService properties;
    private final AgreementService agreements;
    private final PaymentService payments;
    private final ExpenseService expenses;
    private final DepositService deposits;
    private final Authorizer auth;
    private final AdvancedAnalyticsService analytics;
    private final DashboardService dashboard;
    private final LeaseRenewalService leases;
    private final MaintenanceService maintenance;
    private final WorkOrderService workOrders;

    PropertyAssistantDataQueries(OwnerService owners, TenantService tenants, PropertyService properties,
                                 AgreementService agreements, PaymentService payments, ExpenseService expenses,
                                 DepositService deposits, Authorizer auth, AdvancedAnalyticsService analytics,
                                 DashboardService dashboard, LeaseRenewalService leases,
                                 MaintenanceService maintenance, WorkOrderService workOrders) {
        this.owners=owners; this.tenants=tenants; this.properties=properties; this.agreements=agreements;
        this.payments=payments; this.expenses=expenses; this.deposits=deposits; this.auth=auth; this.analytics=analytics;
        this.dashboard=dashboard; this.leases=leases; this.maintenance=maintenance; this.workOrders=workOrders;
    }

    String answer(PropertyAssistantService.Topic topic, String q, LocalDate from, LocalDate to,
                  String periodLabel, int days, LocalDate today) throws EstateFlowException {
        return switch (topic) {
            case TENANTS -> tenantList(q);
            case OWNERS -> ownerList();
            case PROPERTIES -> propertyList(q);
            case OCCUPANCY -> occupancy();
            case PORTFOLIO -> portfolio(from,to,periodLabel,today);
            case RENT -> rent(q,from,to,periodLabel);
            case LEASES -> leaseList(days,today);
            case MAINTENANCE -> maintenanceList(q);
            case AGREEMENTS -> agreementList(q,today);
            case PAYMENTS -> paymentList(from,to,periodLabel);
            case EXPENSES -> expenseSummary(from,to,periodLabel);
            case DEPOSITS -> depositSummary(from,to);
            case FINANCIALS -> financials(from,to,periodLabel,q);
            default -> null;
        };
    }

    private String tenantList(String q) throws EstateFlowException {
        if (tenants==null) return unavailable("tenant");
        List<Tenant> all=tenants.findAll();
        List<Tenant> rows=q.contains("active tenant")||q.contains("current tenant")?all.stream().filter(Tenant::isActive).toList():all;
        if(rows.isEmpty()) return "No matching tenant records were found.";
        return "Tenants — "+rows.size()+" record(s):\n"+rows.stream().limit(LIMIT)
                .map(t->"• "+safe(t.getFullName())+(t.isActive()?" (active)":" (inactive)"))
                .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size());
    }

    private String ownerList() throws EstateFlowException {
        if(owners==null)return unavailable("owner"); List<Owner> rows=owners.findAll();
        if(rows.isEmpty())return "No owner records were found.";
        return "Owners — "+rows.size()+" record(s):\n"+rows.stream().limit(LIMIT)
                .map(o->"• "+safe(o.getFullName())+(o.isActive()?" (active)":" (inactive)"))
                .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size());
    }

    private String propertyList(String q) throws EstateFlowException {
        if(properties==null)return unavailable("property");
        List<PropertyOccupancyRow> all=properties.findRows("").stream().filter(PropertyOccupancyRow::active).toList();
        List<PropertyOccupancyRow> rows=q.contains("vacant")?all.stream().filter(r->!r.occupied()).toList():
                q.contains("occupied")?all.stream().filter(PropertyOccupancyRow::occupied).toList():all;
        if(rows.isEmpty())return q.contains("vacant")?"There are no active vacant properties.":"No active properties were found.";
        String body=rows.stream().limit(LIMIT).map(r->"• "+safe(r.propertyName())+location(r.city())+" — "+
                (r.occupied()?"occupied"+(blank(r.tenantName())?"":" by "+safe(r.tenantName())):"vacant")+
                (r.listedRent()==null?"":", listed rent "+money(r.listedRent())))
                .reduce((a,b)->a+"\n"+b).orElse("");
        return (q.contains("vacant")?"Active vacant properties":"Active properties")+" — "+rows.size()+" of "+all.size()+":\n"+body+more(rows.size())+
                "\nOccupancy is derived from agreements effective today.";
    }

    private String occupancy() throws EstateFlowException {
        DashboardStats s=dashboard.stats();
        return "Current occupancy: "+s.occupancyPercent()+"% — "+s.occupiedProperties()+" occupied and "+s.vacantProperties()+
                " vacant out of "+s.totalProperties()+" active properties. This is a current snapshot, not historical vacancy.";
    }

    private String portfolio(LocalDate from,LocalDate to,String label,LocalDate today) throws EstateFlowException {
        DashboardStats s=dashboard.stats(); AdvancedAnalyticsReport r=analytics.report(from,to);
        String tenantCount=tenants==null?"not available":Long.toString(tenants.findAll().stream().filter(Tenant::isActive).count());
        String agreementCount=agreements==null?"not available":Long.toString(agreements.findAllRows().stream().filter(a->a.status()==AgreementStatus.ACTIVE&&
                !a.startDate().isAfter(today)&&!a.effectiveEndDate().isBefore(today)).count());
        var p=r.profitability();
        return "Portfolio summary ("+label+")\n• Active properties: "+s.totalProperties()+" ("+s.occupiedProperties()+" occupied, "+s.vacantProperties()+" vacant; "+s.occupancyPercent()+"%)\n"+
                "• Active tenants: "+tenantCount+"\n• Agreements effective today: "+agreementCount+"\n• Rent actually received: "+money(p.rentReceived())+
                "\n• Operating expenses: "+money(p.expenses())+"\n• Net operating income (NOI): "+money(p.noi())+
                "\nNOI is valid rent received minus recorded operating expenses in this period; rent billed is not treated as cash received.";
    }

    private String rent(String q,LocalDate from,LocalDate to,String label) throws EstateFlowException {
        if(containsAny(q,"who hasn't paid","who has not paid","which tenant","tenants who","who owes","unpaid tenant")) {
            auth.require(Permission.VIEW_TENANT_INSIGHTS);
            if(payments==null)return unavailable("overdue rent"); List<OverdueRentRow> rows=payments.findOverdue();
            if(rows.isEmpty())return "No currently overdue rent charges were found.";
            return "Currently overdue live rent charges — "+rows.size()+":\n"+rows.stream().limit(LIMIT).map(r->"• "+safe(r.tenantName())+
                    " — "+safe(r.propertyName())+", balance "+money(r.balance())+", "+r.daysOverdue()+" day(s) overdue (due "+r.dueDate().format(DATE)+")")
                    .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size())+"\nVoided payments and cancelled charges are excluded.";
        }
        AdvancedAnalyticsReport r=analytics.report(from,to);
        BigDecimal arrears=r.arrears().stream().map(AdvancedAnalyticsReport.ArrearsBucket::balance).reduce(BigDecimal.ZERO,BigDecimal::add);
        return "Rent collection for charges due "+from+" through "+to+": "+money(r.collection().collectedToDate())+" received against "+
                money(r.collection().billed())+" billed ("+r.collection().ratePercent()+"% paid to date); "+money(r.collection().outstanding())+
                " remains on those charges.\nCurrent overdue live rent across all due dates: "+money(arrears)+". Voided payments and cancelled charges are excluded.\n"+
                "The collection rate is paid-to-date divided by charges due in the selected period.";
    }

    private String leaseList(int days,LocalDate today) throws EstateFlowException {
        List<ExpiringLeaseRow> rows=leases.endingWithinWarningWindow(days);
        if(rows.isEmpty())return "No lease ends within the next "+days+" days (through "+today.plusDays(days)+").";
        return "Leases ending in the next "+days+" days ("+today+" through "+today.plusDays(days)+") — "+rows.size()+":\n"+
                rows.stream().limit(LIMIT).map(r->"• "+safe(r.tenantName())+" — "+safe(r.propertyName())+", ends "+r.effectiveEndDate().format(DATE)+
                        ", monthly rent "+money(r.monthlyRent())).reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size());
    }

    private String maintenanceList(String q) throws EstateFlowException {
        List<MaintenanceReportRow> rows=maintenance.findRows(null).stream().filter(r->r.status()==MaintenanceStatus.OPEN||r.status()==MaintenanceStatus.IN_PROGRESS).toList();
        String result="Open or in-progress maintenance requests — "+rows.size()+":\n"+(rows.isEmpty()?"None.":rows.stream().limit(LIMIT)
                .map(r->"• "+safe(r.title())+" — "+safe(r.propertyName())+", "+r.status()+", "+r.priority()+
                        (r.reportedDate()==null?"":", reported "+r.reportedDate().format(DATE))).reduce((a,b)->a+"\n"+b).orElse(""))+more(rows.size());
        if(q.contains("work order")||q.contains("vendor")) {
            if(!workOrders.isAvailable())return result+"\nWork-order details require the manual v1.7 migration.";
            List<WorkOrderRow> orders=workOrders.search(null,null,null,null,null,null,false).stream()
                    .filter(w->w.status()!=WorkOrderStatus.COMPLETED&&w.status()!=WorkOrderStatus.CANCELLED).toList();
            result+="\nOpen work orders — "+orders.size()+":\n"+(orders.isEmpty()?"None.":orders.stream().limit(LIMIT).map(w->"• "+safe(w.title())+
                    " — "+safe(w.propertyName())+", "+w.status()+(w.dueDate()==null?"":", due "+w.dueDate().format(DATE))).reduce((a,b)->a+"\n"+b).orElse(""))+more(orders.size());
        }
        return result;
    }

    private String agreementList(String q,LocalDate today) throws EstateFlowException {
        if(agreements==null)return unavailable("agreement"); List<AgreementListRow> all=agreements.findAllRows();
        List<AgreementListRow> rows=q.contains("active")||q.contains("current")?all.stream().filter(a->a.status()==AgreementStatus.ACTIVE&&
                !a.startDate().isAfter(today)&&!a.effectiveEndDate().isBefore(today)).toList():all;
        if(rows.isEmpty())return "No matching rental agreements were found.";
        return "Rental agreements — "+rows.size()+":\n"+rows.stream().limit(LIMIT).map(a->"• "+safe(a.tenantName())+" — "+safe(a.propertyName())+
                ", "+a.status()+", "+a.startDate().format(DATE)+" to "+a.effectiveEndDate().format(DATE)+", monthly rent "+money(a.monthlyRent()))
                .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size());
    }

    private String paymentList(LocalDate from,LocalDate to,String label) throws EstateFlowException {
        if(payments==null)return unavailable("payment"); List<PaymentReportRow> rows=payments.search(null,from,to,false);
        if(rows.isEmpty())return "No valid payments were recorded from "+from+" through "+to+".";
        return "Valid payments "+label+" — "+rows.size()+":\n"+rows.stream().limit(LIMIT).map(r->"• "+r.paymentDate().format(DATE)+" — "+
                money(r.amount())+", "+safe(r.tenantName())+" at "+safe(r.propertyName())+" (rent period "+r.periodStart()+")")
                .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size())+"\nVoided payments are excluded.";
    }

    private String expenseSummary(LocalDate from,LocalDate to,String label) throws EstateFlowException {
        if(expenses==null)return unavailable("expense"); expenses.ensureReady(); List<ExpenseRow> rows=expenses.search(null,null,from,to,false);
        BigDecimal total=rows.stream().map(r->r.expense().getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(rows.isEmpty())return "No non-deleted expenses were recorded from "+from+" through "+to+".";
        return "Recorded expenses "+label+": "+money(total)+" across "+rows.size()+" record(s).\n"+rows.stream().limit(LIMIT).map(r->"• "+
                r.expense().getExpenseDate().format(DATE)+" — "+money(r.expense().getAmount())+", "+safe(r.propertyName())+", "+r.expense().getCategory())
                .reduce((a,b)->a+"\n"+b).orElse("")+more(rows.size())+"\nSoft-deleted expenses are excluded.";
    }

    private String depositSummary(LocalDate from,LocalDate to) throws EstateFlowException {
        if(deposits==null)return unavailable("deposit"); deposits.ensureReady(); DepositReport r=deposits.report(from,to,null,null);
        return "Security deposit position: refundable liability held now is "+money(r.liability())+". From "+from+" through "+to+
                ", collections were "+money(r.collected())+", deductions "+money(r.deducted())+", refunds "+money(r.refunded())+
                ". Held deposits are liabilities, not rental income.";
    }

    private String financials(LocalDate from,LocalDate to,String label,String q) throws EstateFlowException {
        AdvancedAnalyticsReport analyticsReport=analytics.report(from,to);
        ProfitabilityReport r=analyticsReport.profitability();
        if(containsAny(q,"most","top ","highest")) {
            List<ProfitabilityReport.PropertyProfit> rows=r.properties().stream().sorted(Comparator.comparing(ProfitabilityReport.PropertyProfit::rentReceived).reversed()).toList();
            if(rows.isEmpty())return "No property-level rent receipts were recorded for "+label+".";
            int count=3; Matcher m=Pattern.compile("top\\s+(\\d{1,2})").matcher(q);if(m.find())try{count=Math.max(1,Math.min(LIMIT,Integer.parseInt(m.group(1))));}catch(NumberFormatException ignored){}
            return "Properties ranked by rent actually received for "+from+" through "+to+":\n"+rows.stream().limit(count).map(p->"• "+safe(p.propertyName())+
                    " — received "+money(p.rentReceived())+", expenses "+money(p.expenses())+", NOI "+money(p.noi())).reduce((a,b)->a+"\n"+b).orElse("")+"\n"+
                    "Ranking uses valid cash receipts by payment date, not rent charged; voided payments are excluded.";
        }
        String answer="Cash-basis financials "+label+" ("+from+" through "+to+"):\n• Rent charged by due date: "+money(r.rentCharged())+
                "\n• Valid rent received by payment date: "+money(r.rentReceived())+"\n• Recorded operating expenses: "+money(r.expenses())+
                "\n• Net operating income (NOI): "+money(r.noi())+"\nNOI is rent received minus recorded operating expenses. Charges and payments use different dates; NOI is not total profit.";
        if(containsAny(q,"trend","changed","change","compare")) {
            answer+="\n\nMonthly actuals (income received, expenses, NOI):\n"+r.months().stream()
                    .map(m->"• "+m.month()+" — "+money(m.income())+", "+money(m.expenses())+", NOI "+money(m.noi()))
                    .reduce((a,b)->a+"\n"+b).orElse("No monthly rows are available.");
            if(q.contains("why"))answer+="\nThe report can show the timing and size of the change, but it does not identify a causal reason.";
        }
        if(q.contains("forecast")) {
            answer+="\n\nRun-rate estimates for the next six months (flat average of the selected actual period; not a prediction):\n"+
                    analyticsReport.forecast().stream().limit(6).map(f->"• "+f.month()+" — estimated receipts "+money(f.estimatedRentReceived())+
                            ", expenses "+money(f.estimatedOperatingExpenses())+", NOI "+money(f.estimatedNoi()))
                            .reduce((a,b)->a+"\n"+b).orElse("No run-rate estimates are available.");
        }
        return answer;
    }

    private static String more(int count){return count>LIMIT?"\nShowing the first "+LIMIT+".":"";}
    private static String safe(String s){return s==null||s.isBlank()?"(unnamed)":s.replaceAll("[\\p{Cntrl}]"," ").trim();}
    private static String location(String s){return s==null||s.isBlank()?"":" ("+safe(s)+")";}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String money(BigDecimal n){return MoneyUtil.format(n==null?BigDecimal.ZERO:n);}
    private static String unavailable(String entity){return "The "+entity+" service is unavailable.";}
    private static boolean containsAny(String q,String...terms){for(String t:terms)if(q.contains(t))return true;return false;}
}
