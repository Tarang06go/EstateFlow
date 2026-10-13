package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.DashboardCharts;
import com.estateflow.dto.DashboardStats;
import com.estateflow.dto.MonthlyAmount;
import com.estateflow.util.MoneyUtil;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DashboardController extends AbstractController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    private record Data(DashboardStats stats, DashboardCharts charts) {
    }

    @FXML private Label propertiesValue, occupiedValue, vacantValue, occupancyValue, agreementsValue, endingValue,
            endingLabel, collectedValue, outstandingValue, overdueValue, collectionEmpty, occupancyEmpty, maintenanceEmpty;
    @FXML private FlowPane maintenanceBox;
    @FXML private Button refreshButton;
    @FXML private BarChart<String, Number> collectionChart, maintenanceChart;
    @FXML private PieChart occupancyChart;

    public DashboardController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        endingLabel.setText("Leases ending within " + ctx.agreements().getWarningDays() + " days");
        refresh();
    }

    @FXML
    private void refresh() {
        UiTask.run(() -> new Data(ctx.dashboard().stats(), ctx.dashboard().charts()), this::render, refreshButton);
    }

    private void render(Data data) {
        DashboardStats s = data.stats();
        propertiesValue.setText(String.valueOf(s.totalProperties()));
        occupiedValue.setText(String.valueOf(s.occupiedProperties()));
        vacantValue.setText(String.valueOf(s.vacantProperties()));
        occupancyValue.setText(s.occupancyPercent().toPlainString() + "%");
        agreementsValue.setText(String.valueOf(s.agreementsOccupyingToday()));
        endingValue.setText(String.valueOf(s.leasesEndingSoon()));
        collectedValue.setText(MoneyUtil.format(s.rentCollectedThisMonth()));
        outstandingValue.setText(MoneyUtil.format(s.outstandingBalance()));
        overdueValue.setText(String.valueOf(s.overdueCharges()));
        maintenanceBox.getChildren().clear();
        s.openMaintenanceByPriority().forEach((priority, count) -> {
            Label value = new Label(String.valueOf(count));
            value.getStyleClass().add("card-value");
            Label name = new Label(priority.name());
            name.getStyleClass().add("card-label");
            VBox card = new VBox(value, name);
            card.getStyleClass().add("card");
            maintenanceBox.getChildren().add(card);
        });

        fillBars(collectionChart, collectionEmpty, data.charts().rentCollected(), "Collected");
        fillBars(maintenanceChart, maintenanceEmpty, data.charts().maintenanceCosts(), "Cost");

        occupancyChart.getData().clear();
        boolean noProperties = s.totalProperties() == 0;
        occupancyChart.setVisible(!noProperties);
        occupancyEmpty.setVisible(noProperties);
        if (!noProperties) {
            occupancyChart.getData().add(new PieChart.Data("Occupied (" + s.occupiedProperties() + ")", s.occupiedProperties()));
            occupancyChart.getData().add(new PieChart.Data("Vacant (" + s.vacantProperties() + ")", s.vacantProperties()));
        }
    }

    private void fillBars(BarChart<String, Number> chart, Label emptyLabel, List<MonthlyAmount> series, String name) {
        boolean empty = MonthlyAmount.allZero(series);
        chart.setVisible(!empty);
        emptyLabel.setVisible(empty);
        chart.getData().clear();
        if (empty) {
            return;
        }
        XYChart.Series<String, Number> bars = new XYChart.Series<>();
        bars.setName(name);
        for (MonthlyAmount m : series) {
            bars.getData().add(new XYChart.Data<>(MONTH.format(m.month()), m.amount()));
        }
        chart.getData().add(bars);
    }
}
