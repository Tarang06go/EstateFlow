package com.estateflow.controller;
import com.estateflow.config.AppContext;import com.estateflow.model.Vendor;import com.estateflow.util.*;import javafx.fxml.FXML;import javafx.scene.control.*;import java.util.List;
public final class VendorListController extends AbstractController{
 @FXML private TextField query;@FXML private CheckBox inactive;@FXML private TableView<Vendor> table;@FXML private Label notice;
 public VendorListController(AppContext c,SceneNavigator n){super(c,n);}
 @FXML private void initialize(){TableUtil.column(table,"No.",60,Vendor::id);TableUtil.column(table,"Vendor",210,Vendor::name);TableUtil.column(table,"Service",150,v->v.category().name().replace('_',' '));TableUtil.column(table,"Phone",130,Vendor::phone);TableUtil.column(table,"Email",190,Vendor::email);TableUtil.column(table,"Status",90,v->v.active()?"Active":"Inactive");reload();}
 @FXML private void reload(){String q=query.getText();boolean show=inactive.isSelected();UiTask.run(()->ctx.vendors().isAvailable()?new Loaded(true,ctx.vendors().search(q,show)):new Loaded(false,List.of()),loaded->{table.getItems().setAll(loaded.rows());notice.setText(loaded.ready()?"":"Vendor records require the manual migration db/migrations/v1.7_vendors_work_orders.sql.");});}
 private record Loaded(boolean ready,List<Vendor> rows){}
 @FXML private void onAdd(){nav.<VendorFormController>showDialog("vendor-form.fxml","Add vendor",c->c.init(null,this::reload));}
 @FXML private void onEdit(){Vendor v=selected();if(v!=null)nav.<VendorFormController>showDialog("vendor-form.fxml","Edit vendor",c->c.init(v,this::reload));}
 private Vendor selected(){Vendor v=table.getSelectionModel().getSelectedItem();if(v==null)AlertUtil.info("Vendors","Select a vendor first.");return v;}
}
