package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.OwnerDetails;
import com.estateflow.dto.PropertyOccupancyRow;
import com.estateflow.model.Owner;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.TableUtil;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner profile dialog: contact details, owned properties with live occupancy, and outstanding rent. */
public class OwnerDetailController extends AbstractDialogController {

    @FXML private Label nameLabel, contactLabel, statusBadge;
    @FXML private HBox kpiRow;
    @FXML private TableView<PropertyOccupancyRow> table;

    public OwnerDetailController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        table.setPlaceholder(new Label("This owner has no properties yet."));
        TableUtil.column(table, "Property", 190, PropertyOccupancyRow::propertyName);
        TableUtil.column(table, "City", 110, PropertyOccupancyRow::city);
        TableUtil.column(table, "Occupancy", 110, r -> r.occupied() ? "Occupied" : "Vacant");
        TableUtil.column(table, "Tenant today", 160, PropertyOccupancyRow::tenantName);
        TableUtil.column(table, "Listed rent", 120, r -> fmt.money(r.listedRent()));
        TableUtil.column(table, "Status", 90, r -> r.active() ? "Active" : "Inactive");
    }

    void init(long ownerId) {
        UiTask.run(() -> ctx.owners().details(ownerId), this::render);
    }

    private void render(OwnerDetails d) {
        Owner o = d.owner();
        nameLabel.setText(o.getFullName());
        contactLabel.setText(o.getPhone() + (o.getEmail() == null ? "" : "   |   " + o.getEmail())
                + (o.getAddress() == null ? "" : "   |   " + o.getAddress()));
        statusBadge.setText(o.isActive() ? "Active" : "Inactive");
        statusBadge.getStyleClass().setAll("badge", o.isActive() ? "badge-success" : "badge-danger");
        long occupied = d.properties().stream().filter(p -> p.active() && p.occupied()).count();
        kpiRow.getChildren().setAll(kpi("Properties", String.valueOf(d.properties().size()), occupied + " occupied today", ""),
                kpi("Outstanding rent", fmt.money(d.outstandingBalance()), "On this owner's properties",
                        d.outstandingBalance().signum() > 0 ? "danger" : "success"));
        table.getItems().setAll(d.properties());
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
