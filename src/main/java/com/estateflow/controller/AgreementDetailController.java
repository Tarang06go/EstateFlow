package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.RentalAgreement;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;

public class AgreementDetailController extends AbstractDialogController {

    @FXML private Label summaryLabel;
    @FXML private TextArea notesArea;
    @FXML private DatePicker terminationPicker;
    @FXML private Button notesButton, terminateButton;
    private long agreementId;
    private Runnable onChanged = () -> { };

    public AgreementDetailController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(long agreementId, Runnable onChanged) {
        this.agreementId = agreementId;
        this.onChanged = onChanged;
        load();
    }

    private void load() {
        UiTask.run(() -> {
            RentalAgreement a = ctx.agreements().get(agreementId);
            return new Object[]{a, ctx.agreements().effectiveStatus(a)};
        }, data -> {
            RentalAgreement a = (RentalAgreement) data[0];
            summaryLabel.setText("Agreement " + a.getAgreementId() + " - " + data[1] + "\n"
                    + DateFormats.format(a.getStartDate()) + " to " + DateFormats.format(a.getEffectiveEndDate())
                    + (a.getTerminationDate() == null ? "" : " (terminated early; original end " + DateFormats.format(a.getEndDate()) + ")")
                    + "\nRent " + MoneyUtil.format(a.getMonthlyRent()) + " per month, deposit " + MoneyUtil.format(a.getSecurityDeposit()));
            notesArea.setText(a.getNotes());
            boolean terminable = a.getTerminationDate() == null && !LocalDate.now(ctx.clock()).isAfter(a.getEndDate());
            terminateButton.setDisable(!terminable);
            terminationPicker.setDisable(!terminable);
        });
    }

    @FXML
    private void onSaveNotes() {
        String notes = notesArea.getText();
        UiTask.run(() -> ctx.agreements().updateNotes(agreementId, notes), a -> {
            onChanged.run();
            load();
        }, notesButton);
    }

    @FXML
    private void onTerminate() {
        LocalDate date = terminationPicker.getValue();
        if (date == null) {
            AlertUtil.info("Terminate agreement", "Choose the termination date.");
            return;
        }
        if (!AlertUtil.confirm("Terminate agreement", "Terminate agreement " + agreementId + " effective "
                + DateFormats.format(date) + "? Later rent periods will be cancelled.")) {
            return;
        }
        UiTask.run(() -> ctx.agreements().terminate(agreementId, date), a -> {
            onChanged.run();
            load();
        }, terminateButton);
    }

    @FXML
    private void onLedger() {
        close();
        RentLedgerController ledger = nav.show("ledger.fxml");
        ledger.select(agreementId);
    }

    @FXML
    private void onClose() {
        close();
    }
}
