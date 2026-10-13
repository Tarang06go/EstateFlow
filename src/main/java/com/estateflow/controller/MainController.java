package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/** Main window shell: sidebar navigation into the content area. */
public class MainController extends AbstractController {

    @FXML private StackPane content;
    @FXML private TextField globalSearch;

    public MainController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        nav.setContentArea(content);
        showDashboard();
    }

    @FXML private void showDashboard() { nav.show("dashboard.fxml"); }
    @FXML private void showOwners() { nav.show("owners.fxml"); }
    @FXML private void showTenants() { nav.show("tenants.fxml"); }
    @FXML private void showProperties() { nav.show("properties.fxml"); }
    @FXML private void showAgreements() { nav.show("agreements.fxml"); }
    @FXML private void showLedger() { nav.show("ledger.fxml"); }
    @FXML private void showOverdue() { nav.show("overdue.fxml"); }
    @FXML private void showExpiring() { nav.show("expiring.fxml"); }
    @FXML private void showMaintenance() { nav.show("maintenance.fxml"); }
    @FXML private void showReports() { nav.show("reports.fxml"); }
    @FXML private void showPayments() { nav.show("payments.fxml"); }
    @FXML private void showReminders() { nav.show("reminders.fxml"); }
    @FXML private void showAudit() { nav.show("audit.fxml"); }

    @FXML
    private void onGlobalSearch() {
        SearchController results = nav.show("search.fxml");
        results.search(globalSearch.getText());
    }
}
