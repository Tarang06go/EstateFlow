package com.estateflow.controller;
import com.estateflow.config.AppContext;import com.estateflow.model.*;import com.estateflow.util.*;import javafx.fxml.FXML;import javafx.scene.control.*;import java.util.function.Consumer;
public final class VendorFormController extends AbstractDialogController{
 @FXML private TextField name,phone,email,address;@FXML private TextArea notes;@FXML private ComboBox<VendorCategory> category;@FXML private CheckBox active;@FXML private Button saveButton;private Vendor existing;private Consumer<Void> saved=x->{};
 public VendorFormController(AppContext c,SceneNavigator n){super(c,n);}
 @FXML private void initialize(){category.getItems().setAll(VendorCategory.values());category.setValue(VendorCategory.GENERAL_REPAIRS);active.setSelected(true);}
 void init(Vendor vendor,Runnable done){existing=vendor;saved=x->done.run();if(vendor!=null){name.setText(vendor.name());category.setValue(vendor.category());phone.setText(vendor.phone());email.setText(vendor.email());address.setText(vendor.address());notes.setText(vendor.notes());active.setSelected(vendor.active());}}
 @FXML private void onCancel(){close();}
 @FXML private void onSave(){String n=name.getText(),p=InputParser.optionalText(phone.getText()),e=InputParser.optionalText(email.getText()),a=InputParser.optionalText(address.getText()),note=InputParser.optionalText(notes.getText());VendorCategory c=category.getValue();boolean enabled=active.isSelected();Long id=existing==null?null:existing.id();UiTask.run(()->ctx.vendors().save(new Vendor(id,n,c,p,e,a,note,enabled)),v->{close();saved.accept(null);nav.toast("Vendor saved");},saveButton);}
}
