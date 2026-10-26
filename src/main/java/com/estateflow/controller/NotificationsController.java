package com.estateflow.controller;
import com.estateflow.config.AppContext;import com.estateflow.model.*;import com.estateflow.util.*;import javafx.fxml.FXML;import javafx.scene.control.*;import java.util.List;
/** A read-only view of reminders generated from current records; no delivery is attempted. */
public final class NotificationsController extends AbstractController{
 @FXML private TableView<GeneratedNotification> table;@FXML private Label statusLabel;
 public NotificationsController(AppContext c,SceneNavigator n){super(c,n);}
 @FXML private void initialize(){table.setPlaceholder(new Label("No reminders are active for the current preferences."));TableUtil.column(table,"Level",100,n->n.severity().name());TableUtil.column(table,"Type",110,n->n.type().name().replace('_',' '));TableUtil.column(table,"Due",115,n->fmt.date(n.dueDate()));TableUtil.column(table,"Property",180,GeneratedNotification::propertyName);TableUtil.column(table,"Reminder",210,GeneratedNotification::title);TableUtil.column(table,"Details",260,GeneratedNotification::detail);TableUtil.column(table,"Delivery",100,GeneratedNotification::deliveryState);reload();}
 @FXML private void reload(){UiTask.run(()->ctx.notifications().generate(),rows->{table.getItems().setAll(rows);statusLabel.setText(rows.size()+" generated reminder"+(rows.size()==1?"":"s")+" · refreshed from current records · nothing has been sent");});}
 @FXML private void onOpen(){GeneratedNotification n=table.getSelectionModel().getSelectedItem();if(n==null){AlertUtil.info("Notifications","Select a reminder first.");return;}switch(n.type()){case RENT->nav.show("ledger.fxml");case LEASE->nav.<AgreementDetailController>showDialog("agreement-detail.fxml","Agreement "+n.relatedId(),c->c.init(n.relatedId(),this::reload));case WORK_ORDER->nav.show("work-orders.fxml");}}
}
