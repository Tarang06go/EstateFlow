package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.model.RentalAgreement;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;

public class AgreementDetailController extends AbstractDialogController {

    @FXML private Label summaryLabel, renewalLabel, depositSummaryLabel;
    @FXML private javafx.scene.control.ListView<String> historyList;
    @FXML private Button extendButton, renewButton;
    private RentalAgreement current;
    private com.estateflow.dto.DepositRow depositRow;
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
            // Phase 4 extras (renewal, deposit, history). Never allowed to break the screen, e.g. before the v1.5 migration.
            Object[] extras = new Object[]{null, null, null, java.util.List.of()};
            try {
                extras[0] = ctx.leases().whyNotExtendable(agreementId).orElse("");
                extras[1] = ctx.leases().whyNotRenewable(agreementId).orElse("");
                extras[2] = ctx.deposits().findPosition(agreementId).orElse(null);
                extras[3] = ctx.leases().history(agreementId);
            } catch (RuntimeException | com.estateflow.exception.EstateFlowException ex) {
                extras = new Object[]{null, null, null, java.util.List.of()};
            }
            return new Object[]{a, ctx.agreements().effectiveStatus(a), extras};
        }, data -> {
            RentalAgreement a = (RentalAgreement) data[0];
            current = a;
            Object[] extras = (Object[]) data[2];
            applyExtras(a, extras);
            summaryLabel.setText("Agreement " + a.getAgreementId() + " - " + data[1] + "\n"
                    + fmt.date(a.getStartDate()) + " to " + fmt.date(a.getEffectiveEndDate())
                    + (a.getTerminationDate() == null ? "" : " (terminated early; original end " + fmt.date(a.getEndDate()) + ")")
                    + "\nRent " + fmt.money(a.getMonthlyRent()) + " per month, deposit " + fmt.money(a.getSecurityDeposit()));
            notesArea.setText(a.getNotes());
            boolean terminable = a.getTerminationDate() == null && !LocalDate.now(ctx.clock()).isAfter(a.getEndDate());
            terminateButton.setDisable(!terminable);
            terminationPicker.setDisable(!terminable);
        });
    }

    @SuppressWarnings("unchecked")
    private void applyExtras(RentalAgreement a, Object[] extras) {
        boolean available = extras[0] != null;
        String noExtend = available ? (String) extras[0] : "Lease renewal needs the v1.5 migration.";
        String noRenew = available ? (String) extras[1] : "Lease renewal needs the v1.5 migration.";
        extendButton.setDisable(!noExtend.isEmpty());
        renewButton.setDisable(!noRenew.isEmpty());
        extendButton.setTooltip(noExtend.isEmpty() ? null : new javafx.scene.control.Tooltip(noExtend));
        renewButton.setTooltip(noRenew.isEmpty() ? null : new javafx.scene.control.Tooltip(noRenew));
        StringBuilder link = new StringBuilder();
        if (a.getRenewedFromAgreementId() != null) {
            link.append("Renewal of agreement ").append(a.getRenewedFromAgreementId()).append(". ");
        }
        if (a.getRenewedByAgreementId() != null) {
            link.append("Renewed by agreement ").append(a.getRenewedByAgreementId()).append(".");
        } else if (!noRenew.isEmpty() && available) {
            link.append(noRenew);
        }
        renewalLabel.setText(link.toString());
        depositRow = (com.estateflow.dto.DepositRow) extras[2];
        depositSummaryLabel.setText(depositRow == null ? (available ? "Security deposit: none collected yet (lease requires "
                + fmt.money(a.getSecurityDeposit()) + ")." : "")
                : "Security deposit held: " + fmt.money(depositRow.totals().balance()) + " of " + fmt.money(depositRow.totals().original())
                + " (" + depositRow.status().getDisplayLabel() + ")");
        java.util.List<com.estateflow.model.LeaseChange> history = (java.util.List<com.estateflow.model.LeaseChange>) extras[3];
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (com.estateflow.model.LeaseChange c : history) {
            String who = c.actorUsername() == null ? "" : " by " + c.actorUsername();
            String when = c.createdAt() == null ? "" : fmt.date(c.createdAt().toLocalDate());
            if ("EXTENDED".equals(c.changeType())) {
                lines.add(when + "  Extended: end " + fmt.date(c.oldEndDate()) + " to " + fmt.date(c.newEndDate()) + who);
            } else if (c.agreementId() == a.getAgreementId()) {
                lines.add(when + "  Renewed from #" + c.relatedAgreementId() + ": " + fmt.date(c.oldStartDate()) + "-" + fmt.date(c.oldEndDate())
                        + " at " + fmt.money(c.oldMonthlyRent()) + " to " + fmt.date(c.newStartDate()) + "-" + fmt.date(c.newEndDate())
                        + " at " + fmt.money(c.newMonthlyRent()) + who);
            } else {
                lines.add(when + "  Renewed by #" + c.agreementId() + ": new term " + fmt.date(c.newStartDate()) + "-" + fmt.date(c.newEndDate())
                        + " at " + fmt.money(c.newMonthlyRent()) + who);
            }
        }
        historyList.getItems().setAll(lines);
    }

    @FXML
    private void onExtend() {
        if (current != null) {
            nav.<LeaseRenewController>showDialog("lease-renew.fxml", "Extend lease", c -> c.init(current, depositRow, LeaseRenewController.Mode.EXTEND, () -> {
                onChanged.run();
                load();
            }));
        }
    }

    @FXML
    private void onRenew() {
        if (current != null) {
            nav.<LeaseRenewController>showDialog("lease-renew.fxml", "Renew lease", c -> c.init(current, depositRow, LeaseRenewController.Mode.RENEW, () -> {
                onChanged.run();
                load();
            }));
        }
    }

    @FXML
    private void onDeposit() {
        nav.<DepositDetailController>showDialog("deposit-detail.fxml", "Deposit - agreement " + agreementId, c -> c.init(agreementId, () -> {
            onChanged.run();
            load();
        }));
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
                + fmt.date(date) + "? Later rent periods will be cancelled.")) {
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
