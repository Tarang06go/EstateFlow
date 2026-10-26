package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.ProfitabilityReport;
import com.estateflow.dto.ProfitabilityReport.CategoryAmount;
import com.estateflow.dto.ProfitabilityReport.MonthlyProfit;
import com.estateflow.dto.ProfitabilityReport.PropertyProfit;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.Property;
import com.estateflow.report.ReportFormat;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Income, expenses and NOI for a period: totals, monthly trend, expense categories and a property comparison. */
public class ProfitabilityController extends AbstractController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<ReportFormat> formatBox;
    @FXML private Button refreshButton;
    @FXML private HBox kpiRow;
    @FXML private Label noteLabel, resultLabel;
    @FXML private VBox trendCard, categoryCard, propertyCard;

    public ProfitabilityController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        fromPicker.setValue(today.withDayOfMonth(1).minusMonths(5));
        toPicker.setValue(today);
        formatBox.getItems().setAll(ReportFormat.values());
        formatBox.setValue(ReportFormat.PDF);
        UiTask.run(() -> {
            ctx.expenses().ensureReady();
            return ctx.properties().findAll();
        }, props -> {
            propertyBox.getItems().setAll(props);
            refresh();
        }, failure -> {
            noteLabel.setText("Profitability is unavailable until the v1.4 migration is applied.");
            ErrorHandler.handle(failure);
        });
    }

    @FXML
    private void refresh() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        Long propertyId = propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId();
        UiTask.run(() -> ctx.profitability().report(from, to, propertyId), this::render, refreshButton);
    }

    // ------------------------------------------------------------------ rendering

    private void render(ProfitabilityReport r) {
        kpiRow.getChildren().setAll(
                kpi("Rent received", fmt.money(r.rentReceived()), "Payments received in the period", ""),
                kpi("Rent charged", fmt.money(r.rentCharged()), "Billed with a due date in the period (not necessarily received)", ""),
                kpi("Operating expenses", fmt.money(r.expenses()), r.categories().size() + " categor" + (r.categories().size() == 1 ? "y" : "ies"), ""),
                kpi("Net operating income", fmt.money(r.noi()), "Rent received minus operating expenses",
                        r.noi().signum() < 0 ? "danger" : "success"));
        String note = "NOI = rent received - operating expenses. It excludes financing (loan interest and repayments), income tax, "
                + "depreciation and capital works, and is not total profit. Security deposits are not rent and are never included.";
        if (r.maintenanceFromRequests().signum() > 0) {
            note += " Expenses include " + fmt.money(r.maintenanceFromRequests()) + " of resolved maintenance cost that has no expense "
                    + "record yet; it is counted once and will be replaced by the expense if you link one.";
        }
        noteLabel.setText(note);

        renderTrend(r);
        renderCategories(r);
        renderProperties(r);
    }

    private void renderTrend(ProfitabilityReport r) {
        trendCard.getChildren().setAll(header("Monthly income and expenses", "Cash received and costs recorded in each month"));
        boolean empty = r.months().stream().allMatch(m -> m.income().signum() == 0 && m.expenses().signum() == 0);
        if (empty) {
            trendCard.getChildren().add(emptyState("No rent received and no expenses in this period.", 230));
            return;
        }
        LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), moneyAxis(true));
        chart.setAnimated(false);
        chart.setLegendSide(javafx.geometry.Side.TOP);
        chart.setPrefHeight(300);
        chart.setMinHeight(300);
        chart.setCreateSymbols(true);
        addLine(chart, "Rent received", r.months(), MonthlyProfit::income);
        addLine(chart, "Expenses", r.months(), MonthlyProfit::expenses);
        addLine(chart, "NOI", r.months(), MonthlyProfit::noi);
        trendCard.getChildren().add(chart);
    }

    private void addLine(LineChart<String, Number> chart, String name, List<MonthlyProfit> months,
                         java.util.function.Function<MonthlyProfit, BigDecimal> value) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (MonthlyProfit m : months) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(MONTH.format(m.month()), value.apply(m));
            point.nodeProperty().addListener((o, a, node) -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip(MONTH.format(m.month()) + "\n" + name + ": " + fmt.money(value.apply(m))));
                }
            });
            series.getData().add(point);
        }
        chart.getData().add(series);
    }

    private void renderCategories(ProfitabilityReport r) {
        categoryCard.getChildren().setAll(header("Expense breakdown by category", "Share of operating expenses in the period"));
        if (r.categories().isEmpty()) {
            categoryCard.getChildren().add(emptyState("No expenses recorded in this period.", 140));
            return;
        }
        TableView<CategoryAmount> table = new TableView<>();
        TableUtil.column(table, "Category", 170, c -> c.category().getDisplayLabel());
        TableUtil.column(table, "Amount", 130, c -> fmt.money(c.amount()));
        TableUtil.column(table, "Share", 80, c -> share(c.amount(), r.expenses()));
        table.getItems().setAll(r.categories());
        fit(table, r.categories().size());
        table.setPrefWidth(400);
        PieChart pie = new PieChart();
        pie.setAnimated(false);
        pie.setLegendVisible(false);
        pie.setPrefHeight(240);
        for (CategoryAmount c : r.categories()) {
            pie.getData().add(new PieChart.Data(c.category().getDisplayLabel(), c.amount().doubleValue()));
        }
        HBox row = new HBox(16, table, pie);
        HBox.setHgrow(pie, Priority.ALWAYS);
        categoryCard.getChildren().add(row);
    }

    private void renderProperties(ProfitabilityReport r) {
        propertyCard.getChildren().setAll(header("Property comparison", "Rent received, expenses and NOI for each property, best NOI first"));
        if (r.properties().isEmpty()) {
            propertyCard.getChildren().add(emptyState("No properties to compare yet.", 120));
            return;
        }
        TableView<PropertyProfit> table = new TableView<>();
        TableUtil.column(table, "Property", 210, p -> p.active() ? p.propertyName() : p.propertyName() + " (inactive)");
        TableUtil.column(table, "Rent charged", 120, p -> fmt.money(p.rentCharged()));
        TableUtil.column(table, "Rent received", 120, p -> fmt.money(p.rentReceived()));
        TableUtil.column(table, "Expenses", 120, p -> fmt.money(p.expenses()));
        TableUtil.column(table, "NOI", 120, p -> fmt.money(p.noi()));
        table.getItems().setAll(r.properties());
        fit(table, r.properties().size());
        propertyCard.getChildren().add(table);
        boolean anyMoney = r.properties().stream().anyMatch(p -> p.rentReceived().signum() != 0 || p.expenses().signum() != 0);
        if (anyMoney) {
            BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), moneyAxis(true));
            chart.setAnimated(false);
            chart.setLegendSide(javafx.geometry.Side.TOP);
            chart.setCategoryGap(20);
            chart.setPrefHeight(280);
            chart.setMinHeight(280);
            List<PropertyProfit> top = r.properties().stream().limit(8).toList();
            addBars(chart, "Rent received", top, PropertyProfit::rentReceived);
            addBars(chart, "Expenses", top, PropertyProfit::expenses);
            addBars(chart, "NOI", top, PropertyProfit::noi);
            propertyCard.getChildren().add(chart);
        }
    }

    private void addBars(BarChart<String, Number> chart, String name, List<PropertyProfit> rows,
                         java.util.function.Function<PropertyProfit, BigDecimal> value) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (PropertyProfit p : rows) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(p.propertyName(), value.apply(p));
            point.nodeProperty().addListener((o, a, node) -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip(p.propertyName() + "\n" + name + ": " + fmt.money(value.apply(p))));
                }
            });
            series.getData().add(point);
        }
        chart.getData().add(series);
    }

    // ------------------------------------------------------------------ export

    @FXML
    private void onExport() {
        ReportFormat format = formatBox.getValue() == null ? ReportFormat.PDF : formatBox.getValue();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        Long propertyId = propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId();
        try {
            InputParser.requiredDate("from", "From date", from);
            InputParser.requiredDate("to", "To date", to);
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save property profitability report");
        chooser.setInitialFileName("property-profitability." + format.extension());
        chooser.getExtensionFilters().add(format == ReportFormat.PDF
                ? new FileChooser.ExtensionFilter("PDF document", "*.pdf") : new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(nav.primaryStage());
        if (file == null) {
            return;
        }
        File target = file.getName().contains(".") ? file : new File(file.getParentFile(), file.getName() + "." + format.extension());
        UiTask.run(() -> ctx.profitability().exportProperties(from, to, propertyId, target.toPath(), format),
                rows -> resultLabel.setText(rows + " property row(s) written to " + target.getAbsolutePath()));
    }

    // ------------------------------------------------------------------ helpers

    private static String share(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return "-";
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static void fit(TableView<?> table, int rows) {
        table.setFixedCellSize(30);
        table.setPrefHeight(rows * 30 + 36);
        table.setMinHeight(rows * 30 + 36);
    }

    private VBox kpi(String label, String value, String note, String valueClass) {
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        if (!valueClass.isEmpty()) {
            v.getStyleClass().add(valueClass);
        }
        Label n = new Label(note);
        n.getStyleClass().add("kpi-note");
        n.setWrapText(true);
        VBox card = new VBox(4, l, v, n);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(200);
        return card;
    }

    private VBox header(String title, String subtitle) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("card-subtitle");
        VBox box = new VBox(2, t, s);
        box.setPadding(new Insets(0, 0, 8, 0));
        return box;
    }

    private Node emptyState(String text, double height) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-state");
        StackPane p = new StackPane(l);
        p.setMinHeight(height);
        p.setAlignment(Pos.CENTER);
        return p;
    }

    private static NumberAxis moneyAxis(boolean zero) {
        NumberAxis axis = new NumberAxis();
        axis.setTickLabelFormatter(new StringConverter<>() {
            public String toString(Number n) {
                double v = n.doubleValue();
                double a = Math.abs(v);
                String s = a >= 1_000_000 ? String.format(Locale.ENGLISH, "%.1fM", a / 1_000_000)
                        : a >= 1_000 ? String.format(Locale.ENGLISH, "%.0fk", a / 1_000) : String.format(Locale.ENGLISH, "%.0f", a);
                return v < 0 ? "-" + s : s;
            }
            public Number fromString(String s) { return 0; }
        });
        axis.setMinorTickVisible(false);
        axis.setForceZeroInRange(zero);
        return axis;
    }
}
