package qupath.experimental.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.projects.Project;

/**
 * Command to apply a pre-trained object classifier to an image.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectClassifierLoadCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	private String title = "Object Classifiers";
	
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
//		if (project == null) {
//			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
//			return;
//		}
		
		var listClassifiers = new ListView<String>();
		
		listClassifiers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		var labelPlaceholder = new Label("Object classifiers in the\n" + "current project will appear here");
		labelPlaceholder.setAlignment(Pos.CENTER);
		labelPlaceholder.setTextAlignment(TextAlignment.CENTER);
		listClassifiers.setPlaceholder(labelPlaceholder);
		
		refreshNames(listClassifiers.getItems());
//		if (comboClassifiers.getItems().isEmpty()) {
//			Dialogs.showErrorMessage(title, "No object classifiers were found in the current project!");
//			return;
//		}

		
		// Provide an option to remove a classifier
		var popup = new ContextMenu();
		var miRemove = new MenuItem("Delete selected");
		popup.getItems().add(miRemove);
		miRemove.disableProperty().bind(listClassifiers.getSelectionModel().selectedItemProperty().isNull());
		listClassifiers.setContextMenu(popup);
		miRemove.setOnAction(e -> {
			var selectedItems = new ArrayList<>(listClassifiers.getSelectionModel().getSelectedItems());
			if (selectedItems.isEmpty() || project == null)
				return;
			try {
				String message = selectedItems.size() == 1 ? "'" + selectedItems.get(0) + "'" : selectedItems.size() + " classifiers";
				if (!Dialogs.showConfirmDialog(title, "Are you sure you want to delete " + message + "?"))
					return;
				for (var selected : selectedItems) {
					if (!project.getObjectClassifiers().getNames().contains(selected)) {
						Dialogs.showErrorMessage(title, "Unable to delete " + selected + " - not found in the current project");
						return;
					}
					project.getObjectClassifiers().remove(selected);
					listClassifiers.getItems().remove(selected);
				}
			} catch (Exception ex) {
				Dialogs.showErrorMessage("Error deleting classifier", ex);
			}
		});
		

		var label = new Label("Choose classifier");
		label.setLabelFor(listClassifiers);
		
//		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedClassifier.isNotNull());
		var btnApplyClassifier = new Button("Apply classifier");
		btnApplyClassifier.textProperty().bind(Bindings.createStringBinding(() -> {
			if (listClassifiers.getSelectionModel().getSelectedItems().size() > 1)
				return "Apply classifiers sequentially";
			return "Apply classifier";
		}, listClassifiers.getSelectionModel().getSelectedItems()));
		btnApplyClassifier.disableProperty().bind(listClassifiers.getSelectionModel().selectedItemProperty().isNull());
		
		btnApplyClassifier.setOnAction(e -> {
			var imageData = qupath.getImageData();
			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image open!");
				return;
			}
			ObjectClassifier<BufferedImage> classifier = null;
			try {
				classifier = getClassifier(project, listClassifiers.getSelectionModel().getSelectedItems());
			} catch (IOException ex) {
				Dialogs.showErrorMessage(title, ex);
				return;
			}
			if (classifier.classifyObjects(imageData, true) > 0)
				imageData.getHierarchy().fireHierarchyChangedEvent(classifier);
		});
		
//		var pane = new BorderPane();
//		pane.setPadding(new Insets(10.0));
//		pane.setTop(label);
//		pane.setCenter(comboClassifiers);
//		pane.setBottom(btnApplyClassifier);

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		int row = 0;
		PaneTools.setFillWidth(Boolean.TRUE, label, listClassifiers, btnApplyClassifier);
		PaneTools.setVGrowPriority(Priority.ALWAYS, listClassifiers);
		PaneTools.setHGrowPriority(Priority.ALWAYS, label, listClassifiers, btnApplyClassifier);
		PaneTools.setMaxWidth(Double.MAX_VALUE, label, listClassifiers, btnApplyClassifier);
		PaneTools.addGridRow(pane, row++, 0, "Choose object classification model to apply to the current image", label);
		PaneTools.addGridRow(pane, row++, 0, "Choose object classification model to apply to the current image", listClassifiers);
		PaneTools.addGridRow(pane, row++, 0, "Apply object classification to all open images", btnApplyClassifier);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, listClassifiers, btnApplyClassifier);
				
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
//		stage.sizeToScene();
		stage.setWidth(300);
		stage.setHeight(400);
		
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n)
				refreshNames(listClassifiers.getItems());
		});
		
//		stage.setResizable(false);
		stage.show();
		
	}
	
	
	/**
	 * Refresh names from the current project.
	 * @param availableClassifiers list to which names should be added
	 */
	void refreshNames(ObservableList<String> availableClassifiers) {
		var project = qupath.getProject();
		if (project == null) {
			availableClassifiers.clear();
			return;
		}
		Collection<String> names;
		try {
			names = project.getObjectClassifiers().getNames();
			availableClassifiers.setAll(names);
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
			return;
		}
	}
	
	
	ObjectClassifier<BufferedImage> getClassifier(Project<BufferedImage> project, List<String> names) throws IOException {
		if (project == null)
			return null;
		if (names.isEmpty())
			return null;
		if (names.size() == 1)
			return project.getObjectClassifiers().get(names.get(0));
		List<ObjectClassifier<BufferedImage>> classifiers = new ArrayList<>();
		for (var s : names) {
			classifiers.add(project.getObjectClassifiers().get(s));
		}
		return ObjectClassifiers.createCompositeClassifier(classifiers);
	}
	

}
