package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.MaintenanceSummary;
import com.estateflow.dto.MonthlyAmount;
import com.estateflow.dto.PropertyCost;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class MaintenanceAnalyticsController extends AbstractController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    @FXML private FlowPane statusBox, priorityBox;
    @FXML private Label resolvedCost, inProgressCost, totalCost, averageDays, costEmpty;
    @FXML private BarChart<String, Number> costChart;
    @FXML private TableView<PropertyCost> propertyTable;

    public MaintenanceAnalyticsController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        propertyTable.setPlaceholder(new Label("No maintenance cost recorded yet."));
        TableUtil.column(propertyTable, "Property", 220, PropertyCost::propertyName);
        TableUtil.column(propertyTable, "Requests with cost", 130, PropertyCost::requests);
        TableUtil.column(propertyTable, "Total cost", 120, c -> fmt.money(c.cost()));
        reload();
    }

    @FXML
    void reload() {
        UiTask.run(() -> ctx.maintenance().summary(), this::render);
    }

    private void render(MaintenanceSummary s) {
        fill(statusBox, s.countByStatus());
        fill(priorityBox, s.openByPriority());
        resolvedCost.setText(fmt.money(s.resolvedCost()));
        inProgressCost.setText(fmt.money(s.inProgressCost()));
        totalCost.setText(fmt.money(s.totalCost()));
        averageDays.setText(s.averageResolutionDays() == null ? "n/a" : s.averageResolutionDays().toPlainString());
        propertyTable.getItems().setAll(s.costByProperty());
        boolean empty = MonthlyAmount.allZero(s.costByMonth());
        costChart.setVisible(!empty);
        costEmpty.setVisible(empty);
        costChart.getData().clear();
        if (!empty) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (MonthlyAmount m : s.costByMonth()) {
                series.getData().add(new XYChart.Data<>(MONTH.format(m.month()), m.amount()));
            }
            costChart.getData().add(series);
        }
    }

    private static <K extends Enum<K>> void fill(FlowPane pane, Map<K, Integer> counts) {
        pane.getChildren().clear();
        counts.forEach((key, count) -> {
            Label value = new Label(String.valueOf(count));
            value.getStyleClass().add("card-value");
            Label name = new Label(key.name());
            name.getStyleClass().add("card-label");
            VBox card = new VBox(value, name);
            card.getStyleClass().add("card");
            pane.getChildren().add(card);
        });
    }

    @FXML
    private void onBack() {
        nav.show("maintenance.fxml");
    }
}
