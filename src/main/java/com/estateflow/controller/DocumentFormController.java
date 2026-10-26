package com.estateflow.controller;
import com.estateflow.config.AppContext; import com.estateflow.exception.EstateFlowException; import com.estateflow.model.*; import com.estateflow.util.*; import javafx.fxml.FXML; import javafx.scene.control.*; import javafx.stage.FileChooser; import java.nio.file.Path; import java.util.function.Consumer;
public final class DocumentFormController extends AbstractDialogController {
 @FXML private ComboBox<DocumentCategory> category; @FXML private ComboBox<String> linkType; @FXML private TextField linkId,fileName; @FXML private Button saveButton; private Path file; private Consumer<Void> saved=x->{};
 public DocumentFormController(AppContext c,SceneNavigator n){super(c,n);}
 @FXML private void initialize(){category.getItems().setAll(DocumentCategory.values());category.setValue(DocumentCategory.OTHER);linkType.getItems().setAll("Property","Lease agreement","Tenant","Maintenance request","Work order");linkType.setValue("Property");linkType.valueProperty().addListener((o,a,b)->linkId.setPromptText(b.equals("Property")?"Property ID":"Related record ID"));}
 void init(Runnable done){saved=x->done.run();}
 @FXML private void onCancel(){close();}
 @FXML private void onBrowse(){FileChooser fc=new FileChooser();fc.setTitle("Choose document");fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF, PNG, JPEG","*.pdf","*.png","*.jpg","*.jpeg"));var f=fc.showOpenDialog(fileName.getScene().getWindow());if(f!=null){file=f.toPath();fileName.setText(f.getName());}}
 @FXML private void onSave(){try{long id=Long.parseLong(linkId.getText().trim());String type=linkType.getValue();Long p=type.equals("Property")?id:null,a=type.equals("Lease agreement")?id:null,t=type.equals("Tenant")?id:null,m=type.equals("Maintenance request")?id:null,w=type.equals("Work order")?id:null;DocumentCategory chosen=category.getValue();UiTask.run(()->ctx.documents().add(file,p,a,t,m,w,chosen),r->{close();saved.accept(null);},saveButton);}catch(NumberFormatException e){AlertUtil.info("Documents","Enter a valid related record ID.");}}
}
