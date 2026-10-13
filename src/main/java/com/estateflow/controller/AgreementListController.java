package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.AgreementListRow;
import com.estateflow.model.enums.AgreementFilter;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;

public class AgreementListController extends AbstractController {

    @FXML private TableView<AgreementListRow> table;
    @FXML private TextField searchField;
    @FXML private ComboBox<AgreementFilter> filterBox;
    @FXML private Label countLabel;

    public AgreementListController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now(ctx.clock());
        filterBox.getItems().setAll(AgreementFilter.values());
        filterBox.setValue(AgreementFilter.ALL);
        filterBox.valueProperty().addListener((o, a, b) -> reload());
        table.setPlaceholder(new Label("No agreements match. Create one with \"New agreement\"."));
        TableUtil.column(table, "No.", 60, AgreementListRow::agreementId);
        TableUtil.column(table, "Property", 170, AgreementListRow::propertyName);
        TableUtil.column(table, "Tenant", 160, AgreementListRow::tenantName);
        TableUtil.column(table, "Start", 110, r -> DateFormats.format(r.startDate()));
        TableUtil.column(table, "Effective end", 120, r -> DateFormats.format(r.effectiveEndDate()));
        TableUtil.column(table, "Monthly rent", 110, r -> MoneyUtil.format(r.monthlyRent()));
        TableUtil.column(table, "Status", 140, r -> r.status() == AgreementStatus.TERMINATED ? "Terminated"
                : r.startDate().isAfter(today) ? "Upcoming" : r.effectiveEndDate().isBefore(today) ? "Expired" : "Active");
        reload();
    }

    @FXML
    void reload() {
        String text = searchField.getText();
        AgreementFilter filter = filterBox.getValue();
        UiTask.run(() -> ctx.agreements().searchRows(text, filter), rows -> {
            table.getItems().setAll(rows);
            countLabel.setText(rows.size() + " agreement(s) shown");
        });
    }

    @FXML
    private void onClear() {
        searchField.clear();
        filterBox.setValue(AgreementFilter.ALL);
        reload();
    }

    private AgreementListRow selected() {
        AgreementListRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            AlertUtil.info("Agreement", "Select an agreement first.");
        }
        return row;
    }

    @FXML
    private void onAdd() {
        nav.<AgreementFormController>showDialog("agreement-form.fxml", "New rental agreement", c -> c.init(this::reload));
    }

    @FXML
    private void onDetails() {
        AgreementListRow row = selected();
        if (row != null) {
            nav.<AgreementDetailController>showDialog("agreement-detail.fxml", "Agreement " + row.agreementId(),
                    c -> c.init(row.agreementId(), this::reload));
        }
    }

    @FXML
    private void onLedger() {
        AgreementListRow row = selected();
        if (row != null) {
            RentLedgerController ledger = nav.show("ledger.fxml");
            ledger.select(row.agreementId());
        }
    }
}
