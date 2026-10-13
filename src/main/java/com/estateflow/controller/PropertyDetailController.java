package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.*;
import com.estateflow.model.Property;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

import java.time.LocalDate;

/** Property detail dialog: overview plus agreement, payment and maintenance history. */
public class PropertyDetailController extends AbstractDialogController {

    @FXML private Label titleLabel, subtitleLabel, ownerValue, typeValue, detailsValue, rentValue, tenantValue,
            agreementValue, outstandingValue, maintenanceCostValue;
    @FXML private Tab agreementsTab, paymentsTab, maintenanceTab;
    @FXML private TableView<AgreementListRow> agreementTable;
    @FXML private TableView<PaymentReportRow> paymentTable;
    @FXML private TableView<MaintenanceReportRow> maintenanceTable;

    public PropertyDetailController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        TableUtil.column(agreementTable, "No.", 60, AgreementListRow::agreementId);
        TableUtil.column(agreementTable, "Tenant", 190, AgreementListRow::tenantName);
        TableUtil.column(agreementTable, "Start", 110, r -> DateFormats.format(r.startDate()));
        TableUtil.column(agreementTable, "Effective end", 120, r -> DateFormats.format(r.effectiveEndDate()));
        TableUtil.column(agreementTable, "Monthly rent", 110, r -> MoneyUtil.format(r.monthlyRent()));
        TableUtil.column(agreementTable, "Status", 120, r -> r.status() == AgreementStatus.TERMINATED ? "Terminated"
                : r.startDate().isAfter(today) ? "Upcoming" : r.effectiveEndDate().isBefore(today) ? "Expired" : "Active");
        agreementTable.setPlaceholder(new Label("No agreements for this property yet."));
        TableUtil.column(paymentTable, "Receipt", 130, PaymentReportRow::receiptNumber);
        TableUtil.column(paymentTable, "Date", 105, r -> DateFormats.format(r.paymentDate()));
        TableUtil.column(paymentTable, "Tenant", 150, PaymentReportRow::tenantName);
        TableUtil.column(paymentTable, "Period", 105, r -> DateFormats.format(r.periodStart()));
        TableUtil.column(paymentTable, "Amount", 105, r -> MoneyUtil.format(r.amount()));
        TableUtil.column(paymentTable, "Method", 110, r -> r.method().getDisplayLabel());
        TableUtil.column(paymentTable, "Status", 80, r -> r.voided() ? "VOID" : "Valid");
        paymentTable.setPlaceholder(new Label("No payments recorded for this property."));
        TableUtil.column(maintenanceTable, "No.", 55, MaintenanceReportRow::requestId);
        TableUtil.column(maintenanceTable, "Title", 220, MaintenanceReportRow::title);
        TableUtil.column(maintenanceTable, "Priority", 80, MaintenanceReportRow::priority);
        TableUtil.column(maintenanceTable, "Status", 100, MaintenanceReportRow::status);
        TableUtil.column(maintenanceTable, "Reported", 105, r -> DateFormats.format(r.reportedDate()));
        TableUtil.column(maintenanceTable, "Resolved", 105, r -> DateFormats.format(r.resolvedDate()));
        TableUtil.column(maintenanceTable, "Cost", 90, r -> r.cost() == null ? "" : MoneyUtil.format(r.cost()));
        maintenanceTable.setPlaceholder(new Label("No maintenance requests for this property."));
    }

    void init(long propertyId) {
        UiTask.run(() -> ctx.properties().details(propertyId), this::render);
    }

    private void render(PropertyDetails d) {
        Property p = d.property();
        titleLabel.setText(p.getName() + (p.isActive() ? "" : "  (inactive)"));
        subtitleLabel.setText(p.getAddressLine() + ", " + p.getCity());
        ownerValue.setText(d.ownerName() + (d.ownerPhone().isEmpty() ? "" : "  |  " + d.ownerPhone()));
        typeValue.setText(String.valueOf(p.getPropertyType()));
        detailsValue.setText((p.getBedrooms() == null ? "" : p.getBedrooms() + " bedroom(s)  ")
                + (p.getAreaSqft() == null ? "" : p.getAreaSqft().toPlainString() + " sq ft"));
        rentValue.setText(MoneyUtil.format(p.getListedRent()));
        AgreementListRow current = d.currentAgreement();
        tenantValue.setText(current == null ? "Vacant today" : current.tenantName());
        agreementValue.setText(current == null ? "-" : "#" + current.agreementId() + ", "
                + DateFormats.format(current.startDate()) + " to " + DateFormats.format(current.effectiveEndDate())
                + ", rent " + MoneyUtil.format(current.monthlyRent()));
        outstandingValue.setText(MoneyUtil.format(d.outstandingBalance()));
        outstandingValue.getStyleClass().removeAll("status-bad", "status-ok");
        outstandingValue.getStyleClass().add(d.outstandingBalance().signum() > 0 ? "status-bad" : "status-ok");
        maintenanceCostValue.setText(MoneyUtil.format(d.maintenanceCost()));
        agreementTable.getItems().setAll(d.agreements());
        paymentTable.getItems().setAll(d.payments());
        maintenanceTable.getItems().setAll(d.maintenance());
        agreementsTab.setText("Agreements (" + d.agreements().size() + ")");
        paymentsTab.setText("Payments (" + d.payments().size() + ")");
        maintenanceTab.setText("Maintenance (" + d.maintenance().size() + ")");
    }

    @FXML
    private void onClose() {
        close();
    }
}
