package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.*;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Property-management dashboard. Every figure comes from DashboardService.overview (real queries); nothing is
 * hardcoded. Metric definitions: rent collected = valid payments dated in the month; expected = live rent charges
 * due in the month; outstanding = live charges already due minus valid payments; occupancy = active properties
 * with an agreement in force today / all active properties.
 */
public class DashboardController extends AbstractController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    @FXML private Label subtitleLabel;
    @FXML private ComboBox<Integer> periodBox;
    @FXML private Button refreshButton;
    @FXML private HBox quickActions, kpiRow;
    @FXML private VBox portfolioStrip, financeCard, occupancyCard, activityCard, maintenanceCard;
    @FXML private GridPane actionGrid;

    public DashboardController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        subtitleLabel.setText("Portfolio overview as of " + fmt.date(LocalDate.now(ctx.clock())));
        periodBox.getItems().setAll(com.estateflow.config.UserPreferences.DASHBOARD_MONTHS);
        periodBox.setConverter(new StringConverter<>() {
            public String toString(Integer m) { return m == null ? "" : "Last " + m + " months"; }
            public Integer fromString(String s) { return null; }
        });
        periodBox.setValue(ctx.preferences().dashboardMonths());
        periodBox.valueProperty().addListener((o, a, b) -> refresh());
        quickActions.getChildren().addAll(
                quick("Add property", () -> nav.<PropertyFormController>showDialog("property-form.fxml", "Add property", c -> c.init(null, this::refresh))),
                quick("Add tenant", () -> nav.<TenantFormController>showDialog("tenant-form.fxml", "Add tenant", c -> c.init(null, this::refresh))),
                quick("New agreement", () -> nav.<AgreementFormController>showDialog("agreement-form.fxml", "New rental agreement", c -> c.init(this::refresh))),
                quick("Record payment", () -> {
                    nav.show("ledger.fxml");
                    nav.toast("Pick the agreement, select the rent charge, then Record payment");
                }),
                quick("New maintenance request", () -> nav.<MaintenanceFormController>showDialog("maintenance-form.fxml", "New maintenance request", c -> c.init(null, this::refresh))));
        if (!ctx.authorizer().can(com.estateflow.security.Permission.MANAGE_RECORDS)) {
            quickActions.setVisible(false);
            quickActions.setManaged(false);
        }
        refresh();
    }

    private Button quick(String text, Runnable action) {
        Button b = new Button(text, Icons.icon("plus", 15));
        b.setOnAction(e -> action.run());
        return b;
    }

    @FXML
    private void refresh() {
        Integer months = periodBox.getValue() == null ? 6 : periodBox.getValue();
        UiTask.run(() -> ctx.dashboard().overview(months), this::render, refreshButton);
    }

    // ------------------------------------------------------------------ rendering

    private void render(DashboardOverview o) {
        DashboardStats s = o.stats();
        MonthlyAmount thisCollected = o.collected().get(o.collected().size() - 1);
        MonthlyAmount thisExpected = o.expected().get(o.expected().size() - 1);
        int open = s.openMaintenanceByPriority().values().stream().mapToInt(Integer::intValue).sum();
        int urgent = s.openMaintenanceByPriority().getOrDefault(MaintenancePriority.URGENT, 0)
                + s.openMaintenanceByPriority().getOrDefault(MaintenancePriority.HIGH, 0);

        kpiRow.getChildren().setAll(
                kpi("Rent collected this month", fmt.money(s.rentCollectedThisMonth()), "Billed this month: " + fmt.money(thisExpected.amount()), ""),
                kpi("Outstanding rent", fmt.money(s.outstandingBalance()), s.overdueCharges() + " overdue charge(s)",
                        s.outstandingBalance().signum() > 0 ? "danger" : "success"),
                kpi("Occupancy", s.occupancyPercent().stripTrailingZeros().toPlainString() + "%",
                        s.occupiedProperties() + " of " + s.totalProperties() + " active properties occupied", ""),
                kpi("Open maintenance", String.valueOf(open), urgent + " high or urgent", urgent > 0 ? "danger" : ""));

        // Phase 3: this month's NOI. Optional extra: silently skipped if the v1.4 migration is not applied yet.
        LocalDate today = LocalDate.now(ctx.clock());
        UiTask.run(() -> ctx.profitability().report(today.withDayOfMonth(1), today, null), noi -> {
            kpiRow.getChildren().removeIf(n -> "noi-kpi".equals(n.getId()));
            Node card = kpi("Net operating income", fmt.money(noi.noi()),
                    "This month: received " + fmt.money(noi.rentReceived()) + ", expenses " + fmt.money(noi.expenses()),
                    noi.noi().signum() < 0 ? "danger" : "success");
            card.setId("noi-kpi");
            kpiRow.getChildren().add(card);
        }, failure -> { });

        // Phase 4: refundable deposit liability. Optional extra: skipped silently if the v1.5 migration is not applied yet.
        UiTask.run(() -> ctx.deposits().report(today.withDayOfMonth(1), today, null, null), dep -> {
            kpiRow.getChildren().removeIf(n -> "deposit-kpi".equals(n.getId()));
            Node card = kpi("Deposits held (liability)", fmt.money(dep.liability()), "Refundable to tenants; not income", "");
            card.setId("deposit-kpi");
            kpiRow.getChildren().add(card);
        }, failure -> { });

        HBox strip = new HBox(0);
        strip.getChildren().addAll(stat("Active properties", String.valueOf(s.totalProperties())),
                stat("Agreements in force", String.valueOf(s.agreementsOccupyingToday())),
                stat("Vacant today", String.valueOf(s.vacantProperties())),
                stat("Leases ending within " + ctx.agreements().getWarningDays() + " days", String.valueOf(s.leasesEndingSoon())));
        portfolioStrip.getChildren().setAll(strip);

        financeCard.getChildren().setAll(cardHeader("Rent: billed vs collected", "Last " + o.months() + " months, by calendar month"),
                financeChart(o));
        occupancyCard.getChildren().setAll(cardHeader("Occupancy", "Active properties today"), occupancyChart(s));

        actionGrid.getChildren().clear();
        actionGrid.getColumnConstraints().setAll(half(), half());
        actionGrid.add(overdueCard(o), 0, 0);
        actionGrid.add(expiringCard(o), 1, 0);
        actionGrid.add(vacantCard(o), 0, 1);
        actionGrid.add(maintenanceListCard(o, open), 1, 1);

        activityCard.getChildren().setAll(cardHeader("Recent activity", ctx.audit().isEnabled()
                ? "Latest recorded operations" : "Audit trail not enabled"), activity(o));
        maintenanceCard.getChildren().setAll(cardHeader("Maintenance cost", "Recorded cost of in-progress and resolved work, per month"),
                maintenanceChart(o));
    }

    private static ColumnConstraints half() {
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        c.setHgrow(Priority.ALWAYS);
        return c;
    }

    private VBox kpi(String label, String value, String note, String tone) {
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        if (!tone.isEmpty()) {
            v.getStyleClass().add(tone);
        }
        Label n = new Label(note);
        n.getStyleClass().add("kpi-note");
        VBox card = new VBox(4, l, v, n);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(200);
        return card;
    }

    private VBox stat(String label, String value) {
        Label v = new Label(value);
        v.getStyleClass().add("list-row-title");
        v.setStyle("-fx-font-size: 18px;");
        Label l = new Label(label);
        l.getStyleClass().add("caption");
        VBox box = new VBox(2, v, l);
        box.setPadding(new Insets(0, 36, 0, 0));
        return box;
    }

    private VBox cardHeader(String title, String subtitle) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("card-subtitle");
        VBox box = new VBox(2, t, s);
        box.setPadding(new Insets(0, 0, 8, 0));
        return box;
    }

    // ------------------------------------------------------------------ charts

    private static String compact(Number n) {
        double v = n.doubleValue();
        if (v >= 1_000_000) {
            return String.format(Locale.ENGLISH, "%.1fM", v / 1_000_000);
        }
        return v >= 1_000 ? String.format(Locale.ENGLISH, "%.0fk", v / 1_000) : String.format(Locale.ENGLISH, "%.0f", v);
    }

    private static NumberAxis moneyAxis() {
        NumberAxis axis = new NumberAxis();
        axis.setTickLabelFormatter(new StringConverter<>() {
            public String toString(Number n) { return compact(n); }
            public Number fromString(String s) { return 0; }
        });
        axis.setMinorTickVisible(false);
        axis.setForceZeroInRange(true);
        return axis;
    }

    private Node emptyState(String text, double height) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-state");
        StackPane p = new StackPane(l);
        p.setMinHeight(height);
        p.setAlignment(Pos.CENTER);
        return p;
    }

    private void addSeries(BarChart<String, Number> chart, String name, List<MonthlyAmount> data) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (MonthlyAmount m : data) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(MONTH.format(m.month()), m.amount());
            point.nodeProperty().addListener((o, a, node) -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip(MONTH.format(m.month()) + "\n" + name + ": " + fmt.money(m.amount())));
                }
            });
            series.getData().add(point);
        }
        chart.getData().add(series);
    }

    private Node financeChart(DashboardOverview o) {
        if (MonthlyAmount.allZero(o.collected()) && MonthlyAmount.allZero(o.expected())) {
            return emptyState("No rent billed or collected in this period yet.", 250);
        }
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), moneyAxis());
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setLegendSide(javafx.geometry.Side.TOP);
        chart.setCategoryGap(24);
        chart.setBarGap(3);
        chart.setPrefHeight(270);
        chart.setMinHeight(270);
        addSeries(chart, "Collected", o.collected());
        addSeries(chart, "Billed", o.expected());
        return chart;
    }

    private Node maintenanceChart(DashboardOverview o) {
        if (MonthlyAmount.allZero(o.maintenanceCosts())) {
            return emptyState("No maintenance cost recorded in this period.", 230);
        }
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), moneyAxis());
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(28);
        chart.setPrefHeight(230);
        chart.setMinHeight(230);
        addSeries(chart, "Cost", o.maintenanceCosts());
        return chart;
    }

    private Node occupancyChart(DashboardStats s) {
        if (s.totalProperties() == 0) {
            return emptyState("Add a property to see occupancy.", 250);
        }
        PieChart pie = new PieChart();
        pie.setAnimated(false);
        pie.setLabelsVisible(false);
        pie.setLegendVisible(true);
        pie.setLegendSide(javafx.geometry.Side.BOTTOM);
        pie.setStartAngle(90);
        pie.setPrefHeight(250);
        pie.setMinHeight(250);
        pie.getData().add(new PieChart.Data("Occupied (" + s.occupiedProperties() + ")", s.occupiedProperties()));
        pie.getData().add(new PieChart.Data("Vacant (" + s.vacantProperties() + ")", s.vacantProperties()));
        return pie;
    }

    // ------------------------------------------------------------------ action lists

    private VBox listCard(String title, String subtitle, int total, String viewAllText, Runnable viewAll, List<Node> rows,
                          String emptyText) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label badge = new Label(String.valueOf(total));
        badge.getStyleClass().addAll("badge", total == 0 ? "badge-neutral" : "badge-warning");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button all = new Button(viewAllText);
        all.getStyleClass().add("btn-ghost");
        all.setOnAction(e -> viewAll.run());
        HBox head = new HBox(8, t, badge, spacer, all);
        head.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("card-subtitle");
        VBox card = new VBox(2, head, sub);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(220);
        if (rows.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("empty-state");
            empty.setPadding(new Insets(24, 0, 0, 0));
            card.getChildren().add(empty);
        } else {
            VBox box = new VBox(rows.toArray(new Node[0]));
            box.setPadding(new Insets(8, 0, 0, 0));
            card.getChildren().add(box);
        }
        return card;
    }

    private HBox row(String title, String sub, String right, String badgeText) {
        Label t = new Label(title);
        t.getStyleClass().add("list-row-title");
        t.setMaxWidth(220);
        Label s = new Label(sub);
        s.getStyleClass().add("list-row-sub");
        VBox left = new VBox(1, t, s);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, left, spacer);
        row.getStyleClass().add("list-row");
        if (badgeText != null) {
            Label b = new Label(badgeText);
            b.getStyleClass().addAll("badge", TableUtil.badgeClass(badgeText));
            row.getChildren().add(b);
        }
        if (right != null) {
            Label r = new Label(right);
            r.getStyleClass().add("list-row-amount");
            row.getChildren().add(r);
        }
        return row;
    }

    private Node overdueCard(DashboardOverview o) {
        List<Node> rows = o.overdue().stream().map(r -> (Node) row(r.propertyName(), r.tenantName() + "  |  due " + fmt.date(r.dueDate()),
                fmt.money(r.balance()), (-r.daysUntilDue()) + " days late")).toList();
        return listCard("Overdue rent", "Oldest unpaid charges first", o.stats().overdueCharges(), "View all",
                () -> nav.show("overdue.fxml"), rows, "Nothing overdue. Rent is up to date.");
    }

    private Node expiringCard(DashboardOverview o) {
        List<Node> rows = o.expiring().stream().map(r -> (Node) row(r.propertyName(), r.tenantName() + "  |  ends " + fmt.date(r.effectiveEndDate()),
                fmt.money(r.monthlyRent()), r.daysRemaining() + " days")).toList();
        return listCard("Leases ending soon", "Within " + ctx.agreements().getWarningDays() + " days", o.stats().leasesEndingSoon(),
                "View all", () -> nav.show("expiring.fxml"), rows, "No agreements are about to end.");
    }

    private Node vacantCard(DashboardOverview o) {
        List<Node> rows = o.vacant().stream().map(r -> (Node) row(r.propertyName(), r.city() + "  |  " + r.ownerName(),
                fmt.money(r.listedRent()), null)).toList();
        return listCard("Vacant properties", "Active properties with no agreement today", o.stats().vacantProperties(),
                "View all", () -> nav.show("properties.fxml"), rows, "Every active property is let.");
    }

    private Node maintenanceListCard(DashboardOverview o, int open) {
        List<Node> rows = o.openMaintenance().stream().map(r -> (Node) row(r.title(), r.propertyName() + "  |  reported " + fmt.date(r.reportedDate()),
                null, r.priority().name())).toList();
        return listCard("Open maintenance", "Most urgent first", open, "View all", () -> nav.show("maintenance.fxml"), rows,
                "No open maintenance requests.");
    }

    private Node activity(DashboardOverview o) {
        if (!ctx.audit().isEnabled()) {
            return emptyState("Apply db/migrations/v1.1_audit_log.sql to record activity.", 120);
        }
        if (o.recentActivity().isEmpty()) {
            return emptyState("No activity recorded yet.", 120);
        }
        VBox box = new VBox();
        for (AuditEntry e : o.recentActivity()) {
            box.getChildren().add(row(ActivityText.describe(e), ActivityText.when(e, ctx.clock()), null, null));
        }
        return box;
    }
}
