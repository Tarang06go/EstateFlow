package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.config.UserPreferences;
import com.estateflow.util.AlertUtil;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.util.StringConverter;
import javafx.concurrent.Task;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Display preferences (persisted per user) and a read-only view of the effective system configuration. */
public class SettingsController extends AbstractController {

    @FXML private ComboBox<String> currencyBox, dateBox;
    @FXML private ComboBox<Integer> monthsBox;
    @FXML private javafx.scene.control.CheckBox rentRemindersBox, leaseRemindersBox, workOrderRemindersBox;
    @FXML private Label previewLabel, fileLabel, databaseValue, warningValue, reminderValue, auditValue, facialVerificationValue;
    @FXML private Button saveButton;
    @FXML private Label facialStatusLabel;
    @FXML private CheckBox facialConsentBox;
    @FXML private PasswordField facialPasswordField;
    @FXML private Button facialEnrollButton, facialEnableButton, facialDisableButton, facialRevokeButton;
    @FXML private Button facialCancelButton;
    private Task<Boolean> facialTask;
    private EnrollmentCancellation facialEnrollmentCancellation;

    public SettingsController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        currencyBox.getItems().setAll(UserPreferences.CURRENCY_SYMBOLS);
        currencyBox.setConverter(new StringConverter<>() {
            public String toString(String s) { return s == null ? "" : s.isEmpty() ? "None (numbers only)" : s.equals("\u20B9") ? "\u20B9  Indian rupee" : s.equals("Rs.") ? "Rs.  Rupees" : s + "  Dollar"; }
            public String fromString(String s) { return null; }
        });
        dateBox.getItems().setAll(UserPreferences.DATE_PATTERNS);
        monthsBox.getItems().setAll(UserPreferences.DASHBOARD_MONTHS);
        monthsBox.setConverter(new StringConverter<>() {
            public String toString(Integer m) { return m == null ? "" : "Last " + m + " months"; }
            public Integer fromString(String s) { return null; }
        });
        load(ctx.preferences().currencySymbol(), ctx.preferences().datePattern(), ctx.preferences().dashboardMonths());
        rentRemindersBox.setSelected(ctx.preferences().rentReminders());
        leaseRemindersBox.setSelected(ctx.preferences().leaseReminders());
        workOrderRemindersBox.setSelected(ctx.preferences().workOrderReminders());
        currencyBox.valueProperty().addListener((o, a, b) -> preview());
        dateBox.valueProperty().addListener((o, a, b) -> preview());
        fileLabel.setText("Saved to " + UserPreferences.defaultFile());
        String url = ctx.config().dbUrl();
        databaseValue.setText(url.contains("?") ? url.substring(0, url.indexOf('?')) : url);
        warningValue.setText(ctx.config().leaseExpiryWarningDays() + " days");
        reminderValue.setText(ctx.config().rentReminderDays() + " days");
        auditValue.setText("Checking...");
        refreshFacialStatus();
        UiTask.run(() -> ctx.audit().isEnabled(), enabled -> auditValue.setText(enabled ? "Enabled" : "Not enabled (apply db/migrations/v1.1_audit_log.sql)"));
    }

    private void refreshFacialStatus() {
        UiTask.run(() -> ctx.facialVerification().status(), status -> {
            facialVerificationValue.setText(status.message());
            facialStatusLabel.setText(status.providerAvailable() ? "Model and camera support are available. The camera opens only after you choose a face action."
                    : "Face models or Windows camera support are unavailable. Password sign-in remains available.");
            facialEnrollButton.setDisable(!status.providerAvailable());
            facialEnableButton.setDisable(!status.enrolled() || status.enabled());
            facialDisableButton.setDisable(!status.enrolled() || !status.enabled());
            facialRevokeButton.setDisable(!status.enrolled());
        }, failure -> {
            facialVerificationValue.setText("Face status could not be read. Apply the manual v1.8 migration if it has not been applied.");
            facialStatusLabel.setText("No face action is available. Password sign-in remains available.");
            facialEnrollButton.setDisable(true); facialEnableButton.setDisable(true); facialDisableButton.setDisable(true); facialRevokeButton.setDisable(true);
        });
    }

    @FXML private void onFaceEnroll() {
        String password=facialPasswordField.getText();
        if(password==null || password.isEmpty()) { facialStatusLabel.setText("Enter your current password to enroll."); return; }
        if(!facialConsentBox.isSelected()) { facialStatusLabel.setText("Consent is required before the camera opens."); return; }
        facialStatusLabel.setText("Capturing five face samples. Keep one face centered and steady. You can cancel.");
        EnrollmentCancellation cancellation=new EnrollmentCancellation();
        facialEnrollmentCancellation=cancellation;
        facialTask=UiTask.runCancellable(() -> { ctx.facialVerification().enroll(password,true,cancellation); return Boolean.TRUE; }, ok -> {
            facialTask=null; facialEnrollmentCancellation=null; clearFacialPassword(); facialConsentBox.setSelected(false); facialStatusLabel.setText("Enrollment completed on this Windows profile."); refreshFacialStatus();
        }, failure -> {
            facialTask=null; facialEnrollmentCancellation=null; clearFacialPassword(); facialConsentBox.setSelected(false);
            if(cancellation.cancelled()) facialStatusLabel.setText("Enrollment cancelled. No enrollment change was saved.");
            else if(failure instanceof com.estateflow.service.FaceEnrollmentException e) facialStatusLabel.setText(e.userMessage()+" Reference: "+e.diagnosticId());
            else facialStatusLabel.setText("Enrollment could not be completed. Password sign-in remains available.");
                    com.estateflow.util.ErrorHandler.handle(failure);
        }, () -> { facialTask=null; facialEnrollmentCancellation=null; clearFacialPassword(); facialConsentBox.setSelected(false); facialStatusLabel.setText("Enrollment cancelled. No enrollment change was saved."); },
                facialEnrollButton, facialPasswordField, facialConsentBox);
    }

    @FXML private void onFaceCancelEnrollment() {
        if(facialTask==null || facialEnrollmentCancellation==null) return;
        if(facialEnrollmentCancellation.requestCancel()) {
            facialStatusLabel.setText("Cancelling enrollment and releasing camera resources...");
            facialTask.cancel(true);
        } else {
            facialStatusLabel.setText("Secure enrollment save is finishing. You can disable or revoke it after completion.");
        }
    }

    private static final class EnrollmentCancellation implements com.estateflow.service.biometric.CancellationSignal {
        private boolean cancelled;
        private boolean commitStarted;
        @Override public synchronized boolean cancelled() { return cancelled; }
        synchronized boolean requestCancel() {
            if(commitStarted) return false;
            cancelled=true;
            return true;
        }
        @Override public synchronized boolean beginCommit() {
            if(cancelled) return false;
            commitStarted=true;
            return true;
        }
    }

    @FXML private void onFaceEnable() {
        if(!facialConsentBox.isSelected()) { facialStatusLabel.setText("Confirm the consent checkbox before enabling face sign-in."); return; }
        changeFaceLogin(true);
    }
    @FXML private void onFaceDisable() { changeFaceLogin(false); }
    private void changeFaceLogin(boolean enabled) {
        String password=facialPasswordField.getText();
        if(password==null || password.isEmpty()) { facialStatusLabel.setText("Enter your current password to change face sign-in."); return; }
        UiTask.runVoid(() -> ctx.facialVerification().setEnabled(enabled,password), () -> {
            clearFacialPassword(); facialConsentBox.setSelected(false); facialStatusLabel.setText(enabled?"Face sign-in enabled on this Windows profile.":"Face sign-in disabled on this Windows profile."); refreshFacialStatus();
        }, facialEnableButton, facialDisableButton, facialPasswordField, facialConsentBox);
    }
    @FXML private void onFaceRevoke() {
        if(!AlertUtil.confirm("Revoke facial consent", "Delete all facial enrollments for your account from the shared database? You will need to enroll again on each computer.")) return;
        String password=facialPasswordField.getText();
        if(password==null || password.isEmpty()) { facialStatusLabel.setText("Enter your current password to revoke consent."); return; }
        UiTask.runVoid(() -> ctx.facialVerification().revokeAndDelete(password), () -> {
            clearFacialPassword(); facialConsentBox.setSelected(false); facialStatusLabel.setText("Consent revoked and all account enrollments deleted."); refreshFacialStatus();
        }, facialRevokeButton, facialPasswordField);
    }
    private void clearFacialPassword() { facialPasswordField.clear(); }

    private void load(String currency, String date, int months) {
        currencyBox.setValue(currency);
        dateBox.setValue(date);
        monthsBox.setValue(months);
        preview();
    }

    private void preview() {
        if (currencyBox.getValue() == null || dateBox.getValue() == null) {
            return;
        }
        UserPreferences sample = UserPreferences.defaults();
        sample.update(currencyBox.getValue(), dateBox.getValue(), 6);
        com.estateflow.util.UiFormat f = new com.estateflow.util.UiFormat(sample);
        previewLabel.setText(f.money(new BigDecimal("18500.50")) + "    |    " + f.date(LocalDate.now(ctx.clock())));
    }

    @FXML
    private void onSave() {
        String currency = currencyBox.getValue();
        String date = dateBox.getValue();
        int months = monthsBox.getValue();
        boolean rentReminders=rentRemindersBox.isSelected(),leaseReminders=leaseRemindersBox.isSelected(),workOrderReminders=workOrderRemindersBox.isSelected();
        UiTask.runVoid(() -> {
            ctx.preferences().update(currency, date, months);
            ctx.preferences().updateReminderPreferences(rentReminders,leaseReminders,workOrderReminders);
            ctx.preferences().save();
        }, () -> nav.toast("Settings saved. Screens use them when they next open."), saveButton);
    }

    @FXML
    private void onDefaults() {
        if (AlertUtil.confirm("Restore defaults", "Reset currency, date format and dashboard period to the defaults?")) {
            load("\u20B9", "dd MMM yyyy", 6);
            rentRemindersBox.setSelected(true);leaseRemindersBox.setSelected(true);workOrderRemindersBox.setSelected(true);
        }
    }
}
