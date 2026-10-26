package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.DepositRow;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.RentalAgreement;
import com.estateflow.service.rules.RentScheduleGenerator;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Extend (add months at the same rent) or renew (new linked agreement, optionally new rent and deposit carry-over). */
public class LeaseRenewController extends AbstractDialogController {

    public enum Mode { EXTEND, RENEW }

    @FXML private Label infoLabel, monthsLabel, rentLabel, depositLabel, previewLabel;
    @FXML private TextField monthsField, rentField, depositField, noteField;
    @FXML private CheckBox carryBox;
    @FXML private Button saveButton;

    private RentalAgreement agreement;
    private Mode mode;
    private BigDecimal heldBalance = BigDecimal.ZERO;
    private Runnable onDone = () -> { };

    public LeaseRenewController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    void init(RentalAgreement agreement, DepositRow deposit, Mode mode, Runnable onDone) {
        this.agreement = agreement;
        this.mode = mode;
        this.onDone = onDone;
        LocalDate end = agreement.getEffectiveEndDate();
        if (mode == Mode.EXTEND) {
            monthsLabel.setText("Months to add *");
            monthsField.setText("3");
            for (Node n : new Node[]{rentLabel, rentField, depositLabel, depositField, carryBox}) {
                n.setVisible(false);
                n.setManaged(false);
            }
            infoLabel.setText("Extend agreement #" + agreement.getAgreementId() + " (now ending " + fmt.date(end) + ", "
                    + agreement.getDurationMonths() + " months) at the same rent of " + fmt.money(agreement.getMonthlyRent())
                    + ". New rent periods are added; existing ones are not changed.");
        } else {
            monthsLabel.setText("New lease length (months) *");
            monthsField.setText("12");
            rentField.setText(agreement.getMonthlyRent().toPlainString());
            depositField.setText(agreement.getSecurityDeposit().toPlainString());
            heldBalance = deposit == null ? BigDecimal.ZERO : deposit.totals().balance();
            carryBox.setSelected(heldBalance.signum() > 0);
            carryBox.setDisable(heldBalance.signum() <= 0);
            carryBox.setText(heldBalance.signum() > 0 ? "Carry the " + fmt.money(heldBalance) + " held over to the new lease"
                    : "No deposit is held to carry over");
            infoLabel.setText("Renew agreement #" + agreement.getAgreementId() + " with a NEW linked agreement starting "
                    + fmt.date(end.plusDays(1)) + ". The current agreement stays exactly as it is.");
        }
        monthsField.textProperty().addListener((o, a, b) -> preview());
        preview();
    }

    private void preview() {
        try {
            int months = Integer.parseInt(monthsField.getText().trim());
            if (months < 1 || months > 120) {
                previewLabel.setText("Enter between 1 and 120 months.");
                return;
            }
            if (mode == Mode.EXTEND) {
                int total = agreement.getDurationMonths() + months;
                previewLabel.setText(total > 120 ? "The total length would exceed 120 months."
                        : "New end date: " + fmt.date(RentScheduleGenerator.endDate(agreement.getStartDate(), total)) + " (" + total + " months in total).");
            } else {
                LocalDate start = agreement.getEffectiveEndDate().plusDays(1);
                previewLabel.setText("New lease: " + fmt.date(start) + " to " + fmt.date(RentScheduleGenerator.endDate(start, months)) + ".");
            }
        } catch (NumberFormatException e) {
            previewLabel.setText("Enter the number of months.");
        }
    }

    @FXML
    private void onSave() {
        try {
            int months = parseMonths();
            String note = InputParser.optionalText(noteField.getText());
            long id = agreement.getAgreementId();
            if (mode == Mode.EXTEND) {
                if (!AlertUtil.confirm("Extend agreement", "Add " + months + " month(s) to agreement " + id + "? " + previewLabel.getText())) {
                    return;
                }
                UiTask.run(() -> ctx.leases().extend(id, months, note), a -> done("Agreement extended"), saveButton);
            } else {
                BigDecimal rent = InputParser.requiredMoney("monthlyRent", "Monthly rent", rentField.getText());
                BigDecimal deposit = depositField.getText() == null || depositField.getText().isBlank() ? BigDecimal.ZERO
                        : InputParser.requiredMoney("securityDeposit", "Required deposit", depositField.getText());
                boolean carry = carryBox.isSelected();
                if (!AlertUtil.confirm("Renew agreement", "Create a renewal of agreement " + id + " at " + fmt.money(rent) + " per month? "
                        + previewLabel.getText() + (carry ? " The deposit held will be carried over." : ""))) {
                    return;
                }
                UiTask.run(() -> ctx.leases().renew(id, months, rent, deposit, carry, note), a -> done("Agreement renewed"), saveButton);
            }
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
        }
    }

    private int parseMonths() throws ValidationException {
        try {
            return Integer.parseInt(monthsField.getText().trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter the number of months as a whole number.", "months");
        }
    }

    private void done(String message) {
        close();
        nav.toast(message);
        onDone.run();
    }

    @FXML
    private void onCancel() {
        close();
    }
}
