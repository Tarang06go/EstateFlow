package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.*;
import com.estateflow.model.Tenant;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/** Tenant profile dialog. ID proof numbers are masked; full values stay in the edit form only. */
public class TenantDetailController extends AbstractDialogController {

    @FXML private Label nameLabel, contactLabel, statusBadge;
    @FXML private HBox kpiRow;
    @FXML private Tab agreementsTab, paymentsTab, maintenanceTab;
    @FXML private TableView<AgreementListRow> agreementTable;
    @FXML private TableView<PaymentReportRow> paymentTable;
    @FXML private TableView<MaintenanceReportRow> maintenanceTable;

    public TenantDetailController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        agreementTable.setPlaceholder(new Label("No agreements for this tenant."));
        TableUtil.column(agreementTable, "No.", 60, AgreementListRow::agreementId);
        TableUtil.column(agreementTable, "Property", 190, AgreementListRow::propertyName);
        TableUtil.column(agreementTable, "Start", 110, r -> fmt.date(r.startDate()));
        TableUtil.column(agreementTable, "Effective end", 120, r -> fmt.date(r.effectiveEndDate()));
        TableUtil.column(agreementTable, "Monthly rent", 120, r -> fmt.money(r.monthlyRent()));
        TableUtil.column(agreementTable, "Status", 110, r -> r.status() == AgreementStatus.TERMINATED ? "Terminated"
                : r.startDate().isAfter(today) ? "Upcoming" : r.effectiveEndDate().isBefore(today) ? "Expired" : "Active");
        paymentTable.setPlaceholder(new Label("No payments recorded for this tenant."));
        TableUtil.column(paymentTable, "Receipt", 130, PaymentReportRow::receiptNumber);
        TableUtil.column(paymentTable, "Date", 110, r -> fmt.date(r.paymentDate()));
        TableUtil.column(paymentTable, "Property", 170, PaymentReportRow::propertyName);
        TableUtil.column(paymentTable, "Rent period", 110, r -> fmt.date(r.periodStart()));
        TableUtil.column(paymentTable, "Amount", 110, r -> fmt.money(r.amount()));
        TableUtil.column(paymentTable, "Status", 80, r -> r.voided() ? "VOID" : "Valid");
        maintenanceTable.setPlaceholder(new Label("No maintenance requests from this tenant."));
        TableUtil.column(maintenanceTable, "No.", 55, MaintenanceReportRow::requestId);
        TableUtil.column(maintenanceTable, "Property", 160, MaintenanceReportRow::propertyName);
        TableUtil.column(maintenanceTable, "Title", 220, MaintenanceReportRow::title);
        TableUtil.column(maintenanceTable, "Priority", 90, MaintenanceReportRow::priority);
        TableUtil.column(maintenanceTable, "Status", 110, MaintenanceReportRow::status);
        TableUtil.column(maintenanceTable, "Reported", 110, r -> fmt.date(r.reportedDate()));
    }

    void init(long tenantId) {
        UiTask.run(() -> ctx.tenants().details(tenantId), this::render);
    }

    private void render(TenantDetails d) {
        Tenant t = d.tenant();
        nameLabel.setText(t.getFullName());
        String id = t.getIdProofType() == null ? "No ID proof on file" : t.getIdProofType() + " " + Masking.idNumber(t.getIdProofNumber());
        contactLabel.setText(t.getPhone() + (t.getEmail() == null ? "" : "   |   " + t.getEmail()) + "   |   " + id
                + (t.getEmergencyContactName() == null ? "" : "   |   Emergency: " + t.getEmergencyContactName()));
        statusBadge.setText(t.isActive() ? "Active" : "Inactive");
        statusBadge.getStyleClass().setAll("badge", t.isActive() ? "badge-success" : "badge-danger");
        LocalDate today = LocalDate.now(ctx.clock());
        long inForce = d.agreements().stream().filter(a -> !a.startDate().isAfter(today) && !a.effectiveEndDate().isBefore(today)).count();
        kpiRow.getChildren().setAll(kpi("Outstanding rent", fmt.money(d.outstandingBalance()), "Charges already due, unpaid",
                        d.outstandingBalance().signum() > 0 ? "danger" : "success"),
                kpi("Agreements in force", String.valueOf(inForce), d.agreements().size() + " in total", ""),
                kpi("Payments", String.valueOf(d.payments().size()), "Including voided entries", ""));
        agreementTable.getItems().setAll(d.agreements());
        paymentTable.getItems().setAll(d.payments());
        maintenanceTable.getItems().setAll(d.maintenance());
        agreementsTab.setText("Agreements (" + d.agreements().size() + ")");
        paymentsTab.setText("Payments (" + d.payments().size() + ")");
        maintenanceTab.setText("Maintenance (" + d.maintenance().size() + ")");
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
        return card;
    }

    @FXML
    private void onClose() {
        close();
    }
}
