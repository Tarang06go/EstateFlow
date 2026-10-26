package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.RentReminderRow;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/** Overdue, due-today and upcoming rent, one row per unpaid charge. */
public class RentRemindersController extends AbstractController {

    @FXML private Label hintLabel, overdueValue, todayValue, upcomingValue, upcomingLabel, totalValue;
    @FXML private TableView<RentReminderRow> table;

    public RentRemindersController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        int days = ctx.reminders().getWindowDays();
        hintLabel.setText("Unpaid rent that is overdue, due today, or due within the next " + days
                + " day(s). Change rent.reminder.days in application.properties to adjust the window.");
        upcomingLabel.setText("Due in the next " + days + " day(s)");
        table.setPlaceholder(new Label("Nothing to chase: no overdue or upcoming rent."));
        TableUtil.column(table, "Status", 100, r -> switch (r.category()) {
            case OVERDUE -> "OVERDUE";
            case DUE_TODAY -> "DUE TODAY";
            case UPCOMING -> "Upcoming";
        });
        TableUtil.column(table, "Property", 170, RentReminderRow::propertyName);
        TableUtil.column(table, "Tenant", 160, RentReminderRow::tenantName);
        TableUtil.column(table, "Phone", 120, RentReminderRow::tenantPhone);
        TableUtil.column(table, "Period start", 110, r -> fmt.date(r.periodStart()));
        TableUtil.column(table, "Due date", 110, r -> fmt.date(r.dueDate()));
        TableUtil.column(table, "Days", 120, r -> r.daysUntilDue() < 0 ? (-r.daysUntilDue()) + " late"
                : r.daysUntilDue() == 0 ? "today" : "in " + r.daysUntilDue());
        TableUtil.column(table, "Balance", 110, r -> fmt.money(r.balance()));
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(RentReminderRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("row-overdue", "row-today", "row-upcoming");
                if (!empty && row != null) {
                    getStyleClass().add(switch (row.category()) {
                        case OVERDUE -> "row-overdue";
                        case DUE_TODAY -> "row-today";
                        case UPCOMING -> "row-upcoming";
                    });
                }
            }
        });
        reload();
    }

    @FXML
    void reload() {
        UiTask.run(() -> ctx.reminders().reminders(), this::render);
    }

    private void render(List<RentReminderRow> rows) {
        table.getItems().setAll(rows);
        overdueValue.setText(String.valueOf(count(rows, RentReminderRow.Category.OVERDUE)));
        todayValue.setText(String.valueOf(count(rows, RentReminderRow.Category.DUE_TODAY)));
        upcomingValue.setText(String.valueOf(count(rows, RentReminderRow.Category.UPCOMING)));
        totalValue.setText(fmt.money(rows.stream().map(RentReminderRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private static long count(List<RentReminderRow> rows, RentReminderRow.Category category) {
        return rows.stream().filter(r -> r.category() == category).count();
    }

    @FXML
    private void onLedger() {
        RentReminderRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Rent reminders", "Select a row first.");
            return;
        }
        RentLedgerController ledger = nav.show("ledger.fxml");
        ledger.select(row.agreementId());
    }

    @FXML
    private void onExport() {
        File file = FileDialogs.chooseReport(nav.primaryStage(), "rent-reminders");
        if (file != null) {
            UiTask.run(() -> ctx.reports().exportRentReminders(file.toPath(), FileDialogs.formatOf(file)),
                    n -> AlertUtil.info("Export complete", n + " row(s) written to " + file.getAbsolutePath()));
        }
    }
}
