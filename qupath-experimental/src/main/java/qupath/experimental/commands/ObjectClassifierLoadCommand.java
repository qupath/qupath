package qupath.experimental.commands;

import java.io.IOException;
import java.util.Collection;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

/**
 * Command to apply a pre-trained pixel classifier to an image.
 * <p>
 * TODO: This command is unfinished!
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectClassifierLoadCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	private String title = "Load Object Classifier";
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public ObjectClassifierLoadCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		var project = qupath.getProject();
		if (project == null) {
			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
			return;
		}
		
		Collection<String> names;
		try {
			names = project.getObjectClassifiers().getNames();
			if (names.isEmpty()) {
				Dialogs.showErrorMessage(title, "No object classifiers were found in the current project!");
				return;
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
			return;
		}
			
		var comboClassifiers = new ComboBox<String>();
		comboClassifiers.getItems().setAll(names);
		var selectedClassifier = Bindings.createObjectBinding(() -> {
			String name = comboClassifiers.getSelectionModel().getSelectedItem();
			if (name != null) {
				try {
					return project.getObjectClassifiers().get(name);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Load object model", e);
				}
			}
			return null;
		}, comboClassifiers.getSelectionModel().selectedItemProperty());
		

		var label = new Label("Choose model");
		label.setLabelFor(comboClassifiers);
		
		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedClassifier.isNotNull());
		var btnApplyClassifier = new Button("Apply classifier");
		
		btnApplyClassifier.setOnAction(e -> {
			for (var viewer : qupath.getViewers()) {
				var imageData = viewer.getImageData();
				if (imageData != null) {
					if (selectedClassifier.get().classifyObjects(imageData, true) > 0)
						imageData.getHierarchy().fireHierarchyChangedEvent(selectedClassifier.get());
				}
			}
		});

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Choose object classification model to apply to the current image", label, comboClassifiers);
		PaneTools.addGridRow(pane, row++, 0, "Apply object classification to all open images", btnApplyClassifier, btnApplyClassifier);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, comboClassifiers, btnApplyClassifier);
				
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
	}
	

}
