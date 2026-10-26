package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.AdvancedAnalyticsReport;
import com.estateflow.dto.AdvancedAnalyticsReport.*;
import com.estateflow.dto.RentCollectionInsightsReport;
import com.estateflow.dto.RentCollectionInsightsReport.*;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.report.ReportFormat;
import com.estateflow.util.ErrorHandler;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.TableUtil;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

/** Actual portfolio analytics, risk summaries and clearly separated run-rate estimates. */
public final class AnalyticsController extends AbstractController {
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<ReportFormat> formatBox;
    @FXML private ComboBox<PropertyChoice> propertyFilter;
    @FXML private HBox kpiRow;
    @FXML private VBox trendCard;
    @FXML private TableView<ArrearsBucket> arrearsTable;
    @FXML private TableView<LeaseExpiryMonth> leaseTable;
    @FXML private TableView<VendorPerformance> vendorTable;
    @FXML private TableView<MonthlyTrend> collectionTrendTable;
    @FXML private TableView<PropertyBreakdown> propertyCollectionTable;
    @FXML private TableView<TenantBreakdown> tenantCollectionTable;
    @FXML private TableView<ChargeFollowUp> followUpTable;
    @FXML private TableView<LatePaymentBehavior> paymentBehaviorTable;
    @FXML private javafx.scene.layout.VBox tenantInsightCard, behaviorCard;
    @FXML private Label collectionTotalsLabel, collectionDefinitionLabel, collectionStatusLabel;
    @FXML private Label noteLabel, maintenanceLabel, statusLabel;

    private AdvancedAnalyticsReport lastReport;

    public AnalyticsController(AppContext ctx, SceneNavigator nav) { super(ctx, nav); }

    @FXML private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        fromPicker.setValue(today.withDayOfMonth(1).minusMonths(11));
        toPicker.setValue(today);
        formatBox.getItems().setAll(ReportFormat.values());
        formatBox.setValue(ReportFormat.PDF);
        formatBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ReportFormat value) { return value == null ? "" : value.name(); }
            @Override public ReportFormat fromString(String value) { return null; }
        });
        propertyFilter.getItems().add(new PropertyChoice(null, "All properties"));
        propertyFilter.setValue(propertyFilter.getItems().getFirst());
        propertyFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(PropertyChoice value) { return value == null ? "All properties" : value.name(); }
            @Override public PropertyChoice fromString(String value) { return null; }
        });
        TableUtil.column(arrearsTable, "Age", 130, ArrearsBucket::label);
        TableUtil.column(arrearsTable, "Charges", 90, ArrearsBucket::charges);
        TableUtil.column(arrearsTable, "Balance", 140, r -> fmt.money(r.balance()));
        TableUtil.column(leaseTable, "Ending month", 150, r -> r.month().toString());
        TableUtil.column(leaseTable, "Leases", 90, LeaseExpiryMonth::leasesEnding);
        TableUtil.column(leaseTable, "Monthly rent at risk", 190, r -> fmt.money(r.monthlyRentAtRisk()));
        TableUtil.column(vendorTable, "Vendor", 180, VendorPerformance::vendorName);
        TableUtil.column(vendorTable, "Completed", 100, VendorPerformance::completedOrders);
        TableUtil.column(vendorTable, "On time", 130, r -> r.onTimeOrders() + "/" + r.dueDatedOrders() + " (" + r.onTimePercent() + "%)");
        TableUtil.column(vendorTable, "Actual cost", 140, r -> fmt.money(r.actualCost()));
        TableUtil.column(vendorTable, "Avg. days", 100, VendorPerformance::averageDaysToComplete);
        TableUtil.column(collectionTrendTable, "Month", 100, r -> r.month().toString());
        TableUtil.column(collectionTrendTable, "Charges due", 130, r -> fmt.money(r.chargesDue()));
        TableUtil.column(collectionTrendTable, "Paid on these charges", 160, r -> fmt.money(r.collectedOnSelectedCharges()));
        TableUtil.column(collectionTrendTable, "Outstanding", 130, r -> fmt.money(r.outstanding()));
        TableUtil.column(collectionTrendTable, "Cash received", 130, r -> fmt.money(r.paymentsReceived()));
        TableUtil.column(propertyCollectionTable, "Property", 180, PropertyBreakdown::propertyName);
        TableUtil.column(propertyCollectionTable, "Charges", 80, PropertyBreakdown::chargeCount);
        TableUtil.column(propertyCollectionTable, "Billed", 120, r -> fmt.money(r.chargesDue()));
        TableUtil.column(propertyCollectionTable, "Collected", 120, r -> fmt.money(r.collected()));
        TableUtil.column(propertyCollectionTable, "Outstanding", 130, r -> fmt.money(r.outstanding()));
        TableUtil.column(propertyCollectionTable, "Receipts in period", 150, r -> fmt.money(r.paymentsReceived()));
        TableUtil.column(tenantCollectionTable, "Tenant", 180, TenantBreakdown::tenantName);
        TableUtil.column(tenantCollectionTable, "Property", 160, TenantBreakdown::propertyName);
        TableUtil.column(tenantCollectionTable, "Charges", 80, TenantBreakdown::chargeCount);
        TableUtil.column(tenantCollectionTable, "Billed", 120, r -> fmt.money(r.chargesDue()));
        TableUtil.column(tenantCollectionTable, "Collected", 120, r -> fmt.money(r.collected()));
        TableUtil.column(tenantCollectionTable, "Outstanding", 130, r -> fmt.money(r.outstanding()));
        TableUtil.column(tenantCollectionTable, "Receipts in period", 150, r -> fmt.money(r.paymentsReceived()));
        TableUtil.column(followUpTable, "Due date", 105, r -> fmt.date(r.dueDate()));
        TableUtil.column(followUpTable, "Days overdue", 105, ChargeFollowUp::daysOverdue);
        TableUtil.column(followUpTable, "Property", 160, ChargeFollowUp::propertyName);
        TableUtil.column(followUpTable, "Tenant", 170, r -> r.tenantName() == null ? "Restricted" : r.tenantName());
        TableUtil.column(followUpTable, "Charge", 90, ChargeFollowUp::chargeId);
        TableUtil.column(followUpTable, "Balance", 120, r -> fmt.money(r.balance()));
        TableUtil.column(paymentBehaviorTable, "Tenant", 170, r -> r.tenantName() == null ? "Restricted" : r.tenantName());
        TableUtil.column(paymentBehaviorTable, "Property", 150, LatePaymentBehavior::propertyName);
        TableUtil.column(paymentBehaviorTable, "Settled charges", 120, LatePaymentBehavior::fullySettledCharges);
        TableUtil.column(paymentBehaviorTable, "Late settlements", 120, LatePaymentBehavior::lateCharges);
        TableUtil.column(paymentBehaviorTable, "History", 180, r -> r.sufficientHistory() ? (r.repeatedLatePattern() ? "Repeated late" : "No repeated pattern") : "Insufficient history");
        boolean tenantAccess = ctx.authorizer().can(com.estateflow.security.Permission.VIEW_TENANT_INSIGHTS);
        tenantInsightCard.setManaged(tenantAccess); tenantInsightCard.setVisible(tenantAccess);
        behaviorCard.setManaged(tenantAccess); behaviorCard.setVisible(tenantAccess);
        UiTask.run(() -> ctx.properties().findAll(), properties -> properties.stream()
                .map(p -> new PropertyChoice(p.getPropertyId(), p.getName() + (p.isActive() ? "" : " (inactive)")))
                .forEach(propertyFilter.getItems()::add), collectionStatusLabel);
        refresh();
    }

    @FXML private void refresh() {
        LocalDate from = fromPicker.getValue(), to = toPicker.getValue();
        UiTask.run(() -> ctx.analytics().report(from, to), this::render, statusLabel);
        PropertyChoice choice = propertyFilter.getValue();
        Long propertyId = choice == null ? null : choice.id();
        UiTask.run(() -> ctx.collectionInsights().report(from, to, propertyId), this::renderCollection, collectionStatusLabel);
    }

    private void renderCollection(RentCollectionInsightsReport r) {
        CollectionTotals t = r.totals();
        collectionTotalsLabel.setText("Charges due: " + fmt.money(t.chargesDue()) + " across " + t.chargeCount()
                + " live charges · collected on those charges through " + fmt.date(r.to()) + ": " + fmt.money(t.collectedOnSelectedCharges())
                + " (" + t.collectionPercent() + "%) · outstanding on those charges: " + fmt.money(t.outstandingOnSelectedCharges())
                + " · valid receipts dated in period: " + fmt.money(t.paymentsReceivedInPeriod())
                + " (" + fmt.money(t.paymentsAppliedToEarlierCharges()) + " applied to charges due before the period)");
        collectionDefinitionLabel.setText("Period is inclusive. Charges are grouped by due date; receipts by payment date. Collection is allocated to the selected due-period charges through the period end. Current overdue balances use "
                + fmt.date(r.asOf()) + ". A payment applied to an earlier due period is shown separately. Voided payments and cancelled charges are excluded.");
        collectionTrendTable.getItems().setAll(r.monthlyTrend());
        propertyCollectionTable.getItems().setAll(r.properties());
        tenantCollectionTable.getItems().setAll(r.tenants());
        followUpTable.getItems().setAll(r.followUp());
        paymentBehaviorTable.getItems().setAll(r.paymentBehavior());
        collectionStatusLabel.setText("Updated · period " + r.from() + " to " + r.to() + " · arrears as of " + r.asOf());
    }

    private void render(AdvancedAnalyticsReport r) {
        lastReport = r;
        var collection = r.collection();
        var occupancy = r.occupancy();
        var leases = r.leaseExpiry();
        kpiRow.getChildren().setAll(
                kpi("Collection rate", collection.ratePercent() + "%", "Paid to date against charges due in the selected period"),
                kpi("Outstanding rent", fmt.money(collection.outstanding()), "Balance on those selected-period charges"),
                kpi("Portfolio occupancy", occupancy.percent() + "%", occupancy.occupiedProperties() + " occupied of " + occupancy.activeProperties() + " active"),
                kpi("Lease expiries", String.valueOf(leases.leasesEnding()), "Next " + leases.warningDays() + " days · " + fmt.money(leases.monthlyRentAtRisk()) + " monthly rent at risk"));
        arrearsTable.getItems().setAll(r.arrears());
        leaseTable.getItems().setAll(leases.byMonth());
        vendorTable.getItems().setAll(r.vendorPerformance());
        maintenanceLabel.setText("Maintenance: " + r.maintenance().totalRequests() + " total requests · "
                + fmt.money(r.maintenance().totalCost()) + " recorded costs (" + fmt.money(r.maintenance().resolvedCost())
                + " resolved, " + fmt.money(r.maintenance().inProgressCost()) + " in progress) · average resolution "
                + (r.maintenance().averageResolutionDays() == null ? "n/a" : r.maintenance().averageResolutionDays() + " days"));
        noteLabel.setText("Actuals use saved records. Collection rate matches valid payments to the rent charges due in this period, regardless of payment date. "
                + "Forecast uses the selected period's average monthly rent received and operating expenses, held flat for six future months; it assumes no growth or known lease, vacancy, or cost changes. "
                + (!r.vendorAnalyticsAvailable() ? "Vendor performance is unavailable because the v1.7 work-order schema is not available." : r.vendorPerformance().isEmpty()
                ? "No completed, vendor-assigned work orders match the selected completion period." : "Vendor performance uses completed work orders in the selected completion period."));
        renderTrend(r);
        statusLabel.setText("Updated from current records · " + r.from() + " to " + r.to());
    }

    private VBox kpi(String title, String value, String hint) {
        VBox card = new VBox(5);
        card.getStyleClass().add("card");
        Label heading = new Label(title); heading.getStyleClass().add("card-title");
        Label amount = new Label(value); amount.getStyleClass().add("kpi-value");
        Label caption = new Label(hint); caption.getStyleClass().add("caption"); caption.setWrapText(true);
        card.getChildren().setAll(heading, amount, caption);
        HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
        return card;
    }

    private void renderTrend(AdvancedAnalyticsReport r) {
        trendCard.getChildren().clear();
        Label title = new Label("Actual cash flow and forecast estimate"); title.getStyleClass().add("card-title");
        Label subtitle = new Label("Actuals end with the selected period; forecast begins after the current month."); subtitle.getStyleClass().add("caption");
        LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        chart.setAnimated(false); chart.setLegendVisible(true); chart.setPrefHeight(330); chart.setMinHeight(300);
        XYChart.Series<String, Number> actual = new XYChart.Series<>(); actual.setName("Actual NOI");
        for (var month : r.profitability().months()) actual.getData().add(new XYChart.Data<>(month.month().toString(), month.noi()));
        XYChart.Series<String, Number> forecast = new XYChart.Series<>(); forecast.setName("Forecast NOI (estimate)");
        for (var month : r.forecast()) forecast.getData().add(new XYChart.Data<>(month.month().toString(), month.estimatedNoi()));
        chart.getData().setAll(actual, forecast);
        trendCard.getChildren().setAll(title, subtitle, chart);
    }

    @FXML private void onExport() {
        if (lastReport == null) { ErrorHandler.handle(new IllegalStateException("Refresh analytics before exporting.")); return; }
        ReportFormat format = formatBox.getValue() == null ? ReportFormat.PDF : formatBox.getValue();
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export advanced analytics");
        chooser.setInitialFileName("advanced-analytics." + format.extension());
        chooser.getExtensionFilters().add(format == ReportFormat.PDF
                ? new FileChooser.ExtensionFilter("PDF document", "*.pdf")
                : new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = chooser.showSaveDialog(nav.primaryStage());
        if (file == null) return;
        if (!file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + "." + format.extension());
        File target = file;
        UiTask.run(() -> ctx.analytics().export(fromPicker.getValue(), toPicker.getValue(), target.toPath(), format),
                count -> statusLabel.setText(count + " analytics rows exported to " + target.getAbsolutePath()));
    }

    @FXML private void onExportCollection() {
        ReportFormat format = formatBox.getValue() == null ? ReportFormat.PDF : formatBox.getValue();
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export rent collection insights");
        chooser.setInitialFileName("rent-collection-insights." + format.extension());
        chooser.getExtensionFilters().add(format == ReportFormat.PDF
                ? new FileChooser.ExtensionFilter("PDF document", "*.pdf") : new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = chooser.showSaveDialog(nav.primaryStage());
        if (file == null) return;
        if (!file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + "." + format.extension());
        File target = file; PropertyChoice choice = propertyFilter.getValue(); Long propertyId = choice == null ? null : choice.id();
        UiTask.run(() -> ctx.collectionInsights().export(fromPicker.getValue(), toPicker.getValue(), propertyId, target.toPath(), format),
                count -> collectionStatusLabel.setText(count + " collection insight rows exported to " + target.getAbsolutePath()));
    }

    private record PropertyChoice(Long id, String name) {
        @Override public String toString() { return name; }
    }
}
