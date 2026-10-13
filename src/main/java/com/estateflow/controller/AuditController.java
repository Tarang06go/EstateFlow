package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.AuditEntry;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Read-only audit trail (newest first, latest 500 entries). */
public class AuditController extends AbstractController {

    private static final String ALL = "All";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private record Data(boolean enabled, List<AuditEntry> entries) {
    }

    @FXML private Label statusLabel;
    @FXML private ComboBox<String> typeBox;
    @FXML private TextField idField;
    @FXML private TableView<AuditEntry> table;

    public AuditController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        typeBox.getItems().setAll(ALL, "AGREEMENT", "PAYMENT", "OWNER", "TENANT", "PROPERTY", "MAINTENANCE");
        typeBox.setValue(ALL);
        typeBox.valueProperty().addListener((o, a, b) -> reload());
        TableUtil.column(table, "When (UTC)", 150, e -> STAMP.format(e.createdAt()));
        TableUtil.column(table, "Record", 110, AuditEntry::entityType);
        TableUtil.column(table, "No.", 60, AuditEntry::entityId);
        TableUtil.column(table, "Action", 130, AuditEntry::action);
        TableUtil.column(table, "Before", 230, AuditEntry::oldValue);
        TableUtil.column(table, "After", 360, AuditEntry::newValue);
        reload();
    }

    @FXML
    void reload() {
        String type = ALL.equals(typeBox.getValue()) ? null : typeBox.getValue();
        try {
            Long id = InputParser.optionalInt("recordNo", "Record number", idField.getText()) == null ? null
                    : Long.valueOf(idField.getText().trim());
            UiTask.run(() -> new Data(ctx.audit().isEnabled(), ctx.audit().find(type, id, 500)), this::render);
        } catch (EstateFlowException e) {
            ErrorHandler.handle(e);
        }
    }

    private void render(Data data) {
        if (!data.enabled()) {
            statusLabel.setText("Audit history is not enabled in this database. Apply db/migrations/v1.1_audit_log.sql "
                    + "(see PATCH_README.md); until then the application works normally but records no audit entries.");
            table.setPlaceholder(new Label("Audit history is not enabled."));
        } else {
            statusLabel.setText("Important operations (agreements, payments, voids, activation changes, maintenance status) "
                    + "are recorded with the previous and new values. Entries cannot be edited or deleted.");
            table.setPlaceholder(new Label("No audit entries match."));
        }
        table.getItems().setAll(data.entries());
    }

    @FXML
    private void onClear() {
        typeBox.setValue(ALL);
        idField.clear();
        reload();
    }
}
