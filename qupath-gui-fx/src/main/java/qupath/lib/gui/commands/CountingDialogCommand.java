/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.scene.control.ContentDisplay;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PointIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Command to open up a counting panel to aid with creating Point annotations.
 * 
 * @author Pete Bankhead
 *
 */
public class CountingDialogCommand implements Runnable, ChangeListener<ImageData<BufferedImage>> {

	private static final Logger logger = LoggerFactory.getLogger(CountingDialogCommand.class);
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private CountingPane countingPane = null;
	
	private Stage dialog;
	
	private Slider sliderRadius;
	private Button btnLoad, btnSave;
	
	private String savingOption = "Selected points";
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance.
	 */
	public CountingDialogCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.imageDataProperty().addListener(this);
//		viewer.addViewerListener(this);
		changed(qupath.imageDataProperty(), null, qupath.getImageData());
	}
	
	private ToolBar makeToolbarButtons() {
		if (qupath == null)
			return null;
		
		var overlayActions = qupath.getOverlayActions();
		ToolBar toolbar = new ToolBar();
		toolbar.getItems().addAll(
				ActionTools.createToggleButtonWithGraphicOnly(qupath.getToolManager().getToolAction(PathTools.MOVE)),
				ActionTools.createToggleButtonWithGraphicOnly(qupath.getToolManager().getToolAction(PathTools.POINTS)),
				new Separator(Orientation.VERTICAL),
				ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_ANNOTATIONS),
				ActionTools.createToggleButtonWithGraphicOnly(overlayActions.FILL_DETECTIONS),
				ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_GRID));
		return toolbar;
	}
	
	
	@SuppressWarnings("deprecation")
	private Pane makeButtonPanel() {
		if (qupath == null)
			return null;
		
		VBox panel = new VBox();
		panel.setSpacing(10);
//		TilePane panel = new TilePane(Orientation.VERTICAL);
//		panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
		
		sliderRadius = new Slider(1, 100, PathPrefs.pointRadiusProperty().get());
		sliderRadius.valueProperty().addListener(event -> {
			PathPrefs.pointRadiusProperty().set((int)sliderRadius.getValue());
//			PathPrefs.setMinPointSeparation(sliderRadius.getValue());
			qupath.getViewer().repaint();
			}
		);
		
//		panel.setSpacing(10);
		
		BorderPane sliderPane = new BorderPane();
		sliderPane.setLeft(new Label("Point size"));
		sliderPane.setCenter(sliderRadius);
		
		
		
		// Add load/save buttons
		btnLoad = new Button("Load points");
		btnLoad.setOnAction(event -> {
				if (hierarchy == null)
					return;
				File file = FileChoosers.promptForFile(FileChoosers.createExtensionFilter("TSV (Tab delimited)", "*.tsv"));
				if (file == null)
					return;
				try {
					List<PathObject> pointsList = null;
					if (file.toPath().toString().endsWith(".zip"))
						pointsList = PointIO.readPointsObjectList(file);
					
					else if (file.toPath().toString().endsWith(".tsv"))
						pointsList = PointIO.readPoints(file);
					
					if (pointsList != null) {
						for (PathObject points : pointsList)
							hierarchy.addObject(points);
					}
				} catch (IOException e) {
					Dialogs.showErrorMessage("Load points error", e);
					logger.error(e.getMessage(), e);
				}
			}
		);
		btnSave = new Button("Save points");
		btnSave.setOnAction(event -> {
				if (countingPane == null)
					return;
				
				// Prompt the user with choice over which annotations to save
				ListView<PathObject> listView = countingPane.getListView();
				var selection = listView.getSelectionModel().getSelectedItems();
				List<PathObject> pointsList = countingPane.getPathObjects();
				if (!selection.isEmpty()) {
					ArrayList<String> choiceList = new ArrayList<>();
					choiceList.addAll(Arrays.asList("All point annotations", "Selected objects"));
					
					var choice = Dialogs.showChoiceDialog("Save points", "Choose point annotations to save", Arrays.asList("All points", "Selected points"), savingOption);
					if (choice == null)
						return;
					if (choice.equals("Selected points"))
						pointsList = selection;
					savingOption = choice;
				}

				if (pointsList.isEmpty()) {
					Dialogs.showErrorMessage("Save points", "No points available!");
					return;
				}
				File defaultName = null;
				try {
					String name = ServerTools.getDisplayableImageName(qupath.getViewer().getServer()) + "-points.tsv"; // Sorry, this is lazy...
					defaultName = new File(name);
				} catch (Exception e) {
					// Ignore...
				};
				File file = FileChoosers.promptToSaveFile(null, defaultName,
						FileChoosers.createExtensionFilter("TSV (Tab delimited)", "tsv"));
				if (file == null)
					return;
				try {
					PointIO.writePoints(file, pointsList);
				} catch (IOException e) {
					Dialogs.showErrorMessage("Save points error", e);
					logger.error(e.getMessage(), e);
				}
			}
		);
		
		GridPane panelLoadSave = GridPaneUtils.createColumnGridControls(
				btnLoad,
				btnSave
				);

		var labelDelete = new Label("Delete individual points by clicking on them with the 'Alt' key pressed");
		labelDelete.setWrapText(true);
		labelDelete.setMaxHeight(Double.MAX_VALUE);
		labelDelete.prefWidthProperty().bind(panel.widthProperty().subtract(10));
		labelDelete.setContentDisplay(ContentDisplay.CENTER);
		labelDelete.setStyle("-fx-font-style: italic; -fx-opacity: 0.8;");
		labelDelete.setTextAlignment(TextAlignment.CENTER);
		
		var actionConvexPoints = ActionTools.createSelectableAction(PathPrefs.showPointHullsProperty(), "Show point convex hull");
		var actionSelectedColor = ActionTools.createSelectableAction(PathPrefs.useSelectedColorProperty(), "Highlight selected objects by color");
		var actionDetectionsToPoints = qupath.createImageDataAction(imageData -> Commands.convertDetectionsToPoints(imageData, true));
		actionDetectionsToPoints.setText("Convert detections to points");
		
		var btnConvert = ActionTools.createButton(actionDetectionsToPoints);
		var convertPane = new Pane(btnConvert);
		btnConvert.prefWidthProperty().bind(convertPane.widthProperty());
		
		var cbConvex = ActionTools.createCheckBox(actionConvexPoints);
		var cbSelected = ActionTools.createCheckBox(actionSelectedColor);
//		panel.setSpacing(5);
		panel.getChildren().addAll(
				cbConvex,
				cbSelected,
				sliderPane,
				convertPane,
				panelLoadSave,
				labelDelete
				);
		
		return panel;
	}
	
	
	private void attemptToSelectPoints() {
		if (hierarchy == null || countingPane == null)
			return;
		logger.trace("Attempting to select");
		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedObject();
		// Try to set selected object to be a Point object
		if (pathObjectSelected == null || !PathObjectTools.hasPointROI(pathObjectSelected)) {
			List<PathObject> pointObjects = countingPane.getPathObjects();
			if (pointObjects.isEmpty())
				hierarchy.getSelectionModel().setSelectedObject(null);
			else
				// Use the first point object
				hierarchy.getSelectionModel().setSelectedObject(pointObjects.get(0));
		}
	}
	

	@Override
	public void run() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> run());
			return;
		}
		
		var toolManager = qupath.getToolManager();
		if (dialog != null) {
			if (toolManager.getSelectedTool() != PathTools.POINTS)
				toolManager.setSelectedTool(PathTools.POINTS);
			attemptToSelectPoints();
			if (!dialog.isShowing())
				dialog.show();
			return;
		}
		
		dialog = new Stage();
		dialog.setTitle("Points");
		FXUtils.addCloseWindowShortcuts(dialog);

		countingPane = new CountingPane(qupath, hierarchy);
		BorderPane pane = new BorderPane();
		
		ToolBar toolbar = makeToolbarButtons();
		if (toolbar != null)
			pane.setTop(toolbar);
		pane.setCenter(countingPane.getPane());
		Pane panelButtons = makeButtonPanel();
		panelButtons.setPadding(new Insets(5, 0, 0, 0));
		if (panelButtons != null)
			pane.setBottom(panelButtons);
		
		pane.setPadding(new Insets(10, 10, 10, 10));
		Scene scene = new Scene(pane, 300, 450);
		dialog.setScene(scene);
		dialog.setOnCloseRequest(e -> toolManager.setSelectedTool(PathTools.MOVE));
		
		if (toolManager.getSelectedTool() != PathTools.POINTS)
			toolManager.setSelectedTool(PathTools.POINTS);
		attemptToSelectPoints();
		
		dialog.initModality(Modality.NONE);
		dialog.setResizable(true);
		dialog.initOwner(qupath.getStage());

		dialog.show();
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> manager, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		this.hierarchy = imageDataNew == null ? null : imageDataNew.getHierarchy();
		if (countingPane != null) {
			countingPane.setHierarchy(this.hierarchy);
			// If the dialog is showing, try to select points
			if (dialog != null && dialog.isShowing())
				attemptToSelectPoints();
		}
	}

}