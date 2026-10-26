package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.*;
import com.estateflow.model.Owner;
import com.estateflow.model.Tenant;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;

/** Global search results across owners, tenants, properties, agreements and payments. */
public class SearchController extends AbstractController {

    @FXML private TextField searchField;
    @FXML private Label summaryLabel;
    @FXML private TabPane tabs;
    @FXML private Tab ownersTab, tenantsTab, propertiesTab, agreementsTab, paymentsTab;
    @FXML private TableView<Owner> ownerTable;
    @FXML private TableView<Tenant> tenantTable;
    @FXML private TableView<PropertyOccupancyRow> propertyTable;
    @FXML private TableView<AgreementListRow> agreementTable;
    @FXML private TableView<PaymentReportRow> paymentTable;

    public SearchController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        TableUtil.column(ownerTable, "Name", 200, Owner::getFullName);
        TableUtil.column(ownerTable, "Phone", 130, Owner::getPhone);
        TableUtil.column(ownerTable, "Email", 200, Owner::getEmail);
        TableUtil.column(ownerTable, "Status", 80, o -> o.isActive() ? "Active" : "Inactive");
        TableUtil.column(tenantTable, "Name", 200, Tenant::getFullName);
        TableUtil.column(tenantTable, "Phone", 130, Tenant::getPhone);
        TableUtil.column(tenantTable, "Email", 200, Tenant::getEmail);
        TableUtil.column(tenantTable, "Status", 80, t -> t.isActive() ? "Active" : "Inactive");
        TableUtil.column(propertyTable, "Property", 190, PropertyOccupancyRow::propertyName);
        TableUtil.column(propertyTable, "City", 110, PropertyOccupancyRow::city);
        TableUtil.column(propertyTable, "Owner", 160, PropertyOccupancyRow::ownerName);
        TableUtil.column(propertyTable, "Occupancy", 100, r -> r.occupied() ? "Occupied" : "Vacant");
        TableUtil.column(propertyTable, "Tenant today", 160, PropertyOccupancyRow::tenantName);
        propertyTable.setOnMouseClicked(e -> {
            PropertyOccupancyRow row = propertyTable.getSelectionModel().getSelectedItem();
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && row != null) {
                nav.<PropertyDetailController>showDialog("property-detail.fxml", "Property details", c -> c.init(row.propertyId()));
            }
        });
        TableUtil.column(agreementTable, "No.", 60, AgreementListRow::agreementId);
        TableUtil.column(agreementTable, "Property", 180, AgreementListRow::propertyName);
        TableUtil.column(agreementTable, "Tenant", 160, AgreementListRow::tenantName);
        TableUtil.column(agreementTable, "Start", 110, r -> fmt.date(r.startDate()));
        TableUtil.column(agreementTable, "Effective end", 120, r -> fmt.date(r.effectiveEndDate()));
        TableUtil.column(paymentTable, "Receipt", 130, PaymentReportRow::receiptNumber);
        TableUtil.column(paymentTable, "Date", 105, r -> fmt.date(r.paymentDate()));
        TableUtil.column(paymentTable, "Tenant", 160, PaymentReportRow::tenantName);
        TableUtil.column(paymentTable, "Property", 160, PaymentReportRow::propertyName);
        TableUtil.column(paymentTable, "Amount", 110, r -> fmt.money(r.amount()));
        TableUtil.column(paymentTable, "Status", 80, r -> r.voided() ? "VOID" : "Valid");
        for (TableView<?> t : new TableView<?>[]{ownerTable, tenantTable, propertyTable, agreementTable, paymentTable}) {
            t.setPlaceholder(new Label("No matches."));
        }
    }

    /** Called by the sidebar search box. */
    public void search(String text) {
        searchField.setText(text);
        onSearch();
    }

    @FXML
    private void onSearch() {
        String text = searchField.getText();
        UiTask.run(() -> ctx.search().search(text), r -> {
            ownerTable.getItems().setAll(r.owners());
            tenantTable.getItems().setAll(r.tenants());
            propertyTable.getItems().setAll(r.properties());
            agreementTable.getItems().setAll(r.agreements());
            paymentTable.getItems().setAll(r.payments());
            ownersTab.setText("Owners (" + r.owners().size() + ")");
            tenantsTab.setText("Tenants (" + r.tenants().size() + ")");
            propertiesTab.setText("Properties (" + r.properties().size() + ")");
            agreementsTab.setText("Agreements (" + r.agreements().size() + ")");
            paymentsTab.setText("Payments (" + r.payments().size() + ")");
            summaryLabel.setText(r.total() == 0 ? "Nothing found." : r.total() + " match(es)");
            for (Tab tab : tabs.getTabs()) {
                if (!tab.getText().endsWith("(0)")) {
                    tabs.getSelectionModel().select(tab);
                    break;
                }
            }
        });
    }
}
