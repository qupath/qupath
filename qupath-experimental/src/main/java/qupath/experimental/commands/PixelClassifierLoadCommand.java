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
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.ml.PixelClassificationOverlay;
import qupath.lib.gui.ml.PixelClassifierPane;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.gui.tools.PaneTools;

/**
 * Command to apply a pre-trained pixel classifier to an image.
 * <p>
 * TODO: This command is unfinished!
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierLoadCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	private String title = "Load Pixel Classifier";
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public PixelClassifierLoadCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}		
		
		var project = qupath.getProject();
		if (project == null) {
			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
			return;
		}
		
		Collection<String> names;
		try {
			names = project.getPixelClassifiers().getNames();
			if (names.isEmpty()) {
				Dialogs.showErrorMessage(title, "No pixel classifiers were found in the current project!");
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
					return project.getPixelClassifiers().get(name);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Load pixel model", e);
				}
			}
			return null;
		}, comboClassifiers.getSelectionModel().selectedItemProperty());
		
		var selectedOverlay = Bindings.createObjectBinding(() -> {
			return selectedClassifier.get() == null ? null : PixelClassificationOverlay.createPixelClassificationOverlay(viewer, selectedClassifier.get());
		}, selectedClassifier);
		
		selectedOverlay.addListener((v, o, n) -> {
			if (o != null)
				o.stop();
			if (n == null) {
				viewer.resetCustomPixelLayerOverlay();
			} else {
				n.setLivePrediction(true);
				viewer.setCustomPixelLayerOverlay(n);
			}
		});
		
		var label = new Label("Choose model");
		label.setLabelFor(comboClassifiers);
		
		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedOverlay.isNotNull());
		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(enableButtons.not());
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.disableProperty().bind(enableButtons.not());
		var tilePane = PaneTools.createColumnGrid(btnCreateObjects, btnClassifyObjects);
//		btnCreateObjects.prefWidthProperty().bind(btnClassifyObjects.widthProperty());
		
		btnCreateObjects.setOnAction(e -> {
			PixelClassifierPane.promptToCreateObjects(viewer.getImageData(), 
					(PixelClassificationImageServer)selectedOverlay.get().getPixelClassificationServer());
		});
		btnClassifyObjects.setOnAction(e -> {
			PixelClassifierTools.classifyDetectionsByCentroid(viewer.getImageData(), selectedClassifier.get(), true);
		});

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Choose pixel classification model to apply to the current image", label, comboClassifiers);
		PaneTools.addGridRow(pane, row++, 0, "Apply pixel classification", tilePane, tilePane);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, comboClassifiers, tilePane, btnCreateObjects, btnClassifyObjects);
				
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.setOnHiding(e -> {
			var current = selectedOverlay.get();
			if (current != null && viewer.getCustomPixelLayerOverlay() == current) {
				current.stop();
				viewer.resetCustomPixelLayerOverlay();
			}
		});
		
	}
	

}
