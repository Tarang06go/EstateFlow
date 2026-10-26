package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.DepositActivityRow;
import com.estateflow.dto.DepositReport;
import com.estateflow.dto.DepositReport.GroupTotal;
import com.estateflow.dto.DepositRow;
import com.estateflow.model.Property;
import com.estateflow.model.Tenant;
import com.estateflow.service.DepositService;
import com.estateflow.service.DepositService.Grouping;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/** Deposits held (by agreement, tenant or property), refundable liability and period activity, all from live records. */
public class DepositsController extends AbstractController {

    @FXML private ComboBox<Property> propertyBox;
    @FXML private ComboBox<Tenant> tenantBox;
    @FXML private ComboBox<Grouping> viewBox;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private Button refreshButton, openButton;
    @FXML private HBox kpiRow;
    @FXML private Label heldHint;
    @FXML private TableView<DepositRow> rowTable;
    @FXML private TableView<GroupTotal> groupTable;
    @FXML private TableView<DepositActivityRow> activityTable;

    public DepositsController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        fromPicker.setValue(today.withDayOfMonth(1).minusMonths(2));
        toPicker.setValue(today);
        viewBox.getItems().setAll(Grouping.values());
        viewBox.setValue(Grouping.AGREEMENT);
        viewBox.valueProperty().addListener((o, a, b) -> refresh());

        rowTable.setPlaceholder(new Label("No deposits held and none required."));
        TableUtil.column(rowTable, "Agreement", 80, r -> "#" + r.agreementId());
        TableUtil.column(rowTable, "Property", 150, DepositRow::propertyName);
        TableUtil.column(rowTable, "Tenant", 150, DepositRow::tenantName);
        TableUtil.column(rowTable, "Required", 100, r -> fmt.money(r.required()));
        TableUtil.column(rowTable, "Held (original)", 110, r -> fmt.money(r.totals().original()));
        TableUtil.column(rowTable, "Deductions", 100, r -> fmt.money(r.totals().deducted()));
        TableUtil.column(rowTable, "Refunds", 100, r -> fmt.money(r.totals().refunded()));
        TableUtil.column(rowTable, "Carried out", 100, r -> fmt.money(r.totals().transferredOut()));
        TableUtil.column(rowTable, "Balance", 110, r -> fmt.money(r.totals().balance()));
        TableUtil.column(rowTable, "Status", 110, r -> r.status().getDisplayLabel() + (r.renewed() ? " (renewed)" : ""));

        groupTable.setPlaceholder(new Label("No deposits held and none required."));
        TableUtil.column(groupTable, "Group", 300, GroupTotal::label);
        TableUtil.column(groupTable, "Agreements", 90, GroupTotal::agreements);
        TableUtil.column(groupTable, "Held (original)", 120, g -> fmt.money(g.original()));
        TableUtil.column(groupTable, "Deductions", 110, g -> fmt.money(g.deducted()));
        TableUtil.column(groupTable, "Refunds", 110, g -> fmt.money(g.refunded()));
        TableUtil.column(groupTable, "Carried out", 110, g -> fmt.money(g.transferredOut()));
        TableUtil.column(groupTable, "Balance", 120, g -> fmt.money(g.balance()));

        activityTable.setPlaceholder(new Label("No deposit collections, deductions or refunds in this period."));
        TableUtil.column(activityTable, "Date", 100, a -> fmt.date(a.txn().date()));
        TableUtil.column(activityTable, "Type", 100, a -> a.txn().type().getDisplayLabel());
        TableUtil.column(activityTable, "Agreement", 90, a -> "#" + a.agreementId());
        TableUtil.column(activityTable, "Property", 150, DepositActivityRow::propertyName);
        TableUtil.column(activityTable, "Tenant", 150, DepositActivityRow::tenantName);
        TableUtil.column(activityTable, "Amount", 110, a -> fmt.money(a.txn().amount()));
        TableUtil.column(activityTable, "Reason / reference", 260, a -> a.txn().reason() != null ? a.txn().reason()
                + (a.txn().referenceNo() == null ? "" : " (" + a.txn().referenceNo() + ")") : a.txn().referenceNo());

        UiTask.run(() -> {
            ctx.deposits().ensureReady();
            return new Object[]{ctx.properties().findAll(), ctx.tenants().findAll()};
        }, data -> {
            @SuppressWarnings("unchecked") java.util.List<Property> props = (java.util.List<Property>) data[0];
            @SuppressWarnings("unchecked") java.util.List<Tenant> tenants = (java.util.List<Tenant>) data[1];
            propertyBox.getItems().setAll(props);
            tenantBox.getItems().setAll(tenants);
            refresh();
        }, failure -> {
            rowTable.setPlaceholder(new Label("Deposits are unavailable until the v1.5 migration is applied."));
            ErrorHandler.handle(failure);
        });
    }

    @FXML
    private void refresh() {
        Long propertyId = propertyBox.getValue() == null ? null : propertyBox.getValue().getPropertyId();
        Long tenantId = tenantBox.getValue() == null ? null : tenantBox.getValue().getId();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        UiTask.run(() -> ctx.deposits().report(from, to, propertyId, tenantId), this::render, refreshButton);
    }

    private void render(DepositReport r) {
        long held = r.rows().stream().filter(x -> x.status() == com.estateflow.model.enums.DepositStatus.HELD).count();
        kpiRow.getChildren().setAll(
                kpi("Refundable liability", fmt.money(r.liability()), "Held now for " + held + " tenant lease(s); not income", ""),
                kpi("Collected in period", fmt.money(r.collected()), "New deposits received", ""),
                kpi("Deductions in period", fmt.money(r.deducted()), "Kept for damage or arrears", ""),
                kpi("Refunds in period", fmt.money(r.refunded()), "Returned to tenants", ""));
        Grouping view = viewBox.getValue() == null ? Grouping.AGREEMENT : viewBox.getValue();
        boolean byAgreement = view == Grouping.AGREEMENT;
        rowTable.setVisible(byAgreement);
        rowTable.setManaged(byAgreement);
        groupTable.setVisible(!byAgreement);
        groupTable.setManaged(!byAgreement);
        openButton.setDisable(!byAgreement);
        heldHint.setText(byAgreement ? "One row per agreement. Pending = required by the lease but not collected yet."
                : "Totals per " + view.name().toLowerCase() + ".");
        rowTable.getItems().setAll(r.rows());
        groupTable.getItems().setAll(DepositService.group(r.rows(), view));
        activityTable.getItems().setAll(r.activity());
    }

    @FXML
    private void onOpen() {
        DepositRow row = rowTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Open deposit", "Select an agreement row first.");
            return;
        }
        nav.<DepositDetailController>showDialog("deposit-detail.fxml", "Deposit - agreement " + row.agreementId(),
                c -> c.init(row.agreementId(), this::refresh));
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
}
