package legacy;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;

@Deprecated
public class OpenCVClassifierSelectorPane {
	
	private ObservableList<OpenCVStatModel> models;
	
	private ReadOnlyObjectProperty<OpenCVStatModel> selectedModel;
	
	private FlowPane pane;

	public OpenCVClassifierSelectorPane() {
		this(FXCollections.emptyObservableList());
	}
	
	public OpenCVClassifierSelectorPane(final ObservableList<OpenCVStatModel> models) {
		this.models = models;
		initialize();
	}
	
	private void initialize() {		
		var combo = new ComboBox<>(models);
		selectedModel = combo.getSelectionModel().selectedItemProperty();
		if (models.size() > 0)
			combo.getSelectionModel().select(0);
		
		var btnEdit = new Button("Edit");
		btnEdit.disableProperty().bind(selectedModel.isNull());
		btnEdit.setOnAction(e -> edit());

		var btnLoad = new Button("Load...");
		btnLoad.setDisable(true);
		
		var label = new Label("Model");
		
		pane = new FlowPane(label, combo, btnEdit, btnLoad);
		pane.setHgap(5);
	}
	
	
	public ObservableList<OpenCVStatModel> getItems() {
		return models;
	}
	
	public ReadOnlyObjectProperty<OpenCVStatModel> selectedModel() {
		return selectedModel;
	}
	
	public OpenCVStatModel getSelectedModel() {
		return selectedModel.getValue();
	}

	public Pane getPane() {
		return pane;
	}
	
	
	boolean edit() {
		var model = getSelectedModel();
		var params = model.getParameterList();
		// TODO: Export not changing model if user presses cancel
		return DisplayHelpers.showParameterDialog(model.getName(), params);
	}
	

}
