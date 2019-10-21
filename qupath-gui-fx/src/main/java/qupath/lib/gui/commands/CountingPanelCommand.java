/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.DefaultMode;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.QuPathGUI.Mode;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.panels.CountingPanel;
import qupath.lib.gui.panels.PathAnnotationPanel;
import qupath.lib.gui.prefs.PathPrefs;
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
public class CountingPanelCommand implements PathCommand, ImageDataChangeListener<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(CountingPanelCommand.class);
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private CountingPanel countingPanel = null;
	
	private Stage dialog;
	
	private Slider sliderRadius;
	private Button btnLoad, btnSave;
	
	public CountingPanelCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.addImageDataChangeListener(this);
//		viewer.addViewerListener(this);
		imageDataChanged(null, null, qupath.getImageData());
	}
	
	private ToolBar makeToolbarButtons() {
		if (qupath == null)
			return null;
		
		ToolBar toolbar = new ToolBar();
		toolbar.getItems().addAll(
				qupath.getActionToggleButton(GUIActions.MOVE_TOOL, true),
				qupath.getActionToggleButton(GUIActions.POINTS_TOOL, true),
				new Separator(Orientation.VERTICAL),
				qupath.getActionToggleButton(GUIActions.SHOW_ANNOTATIONS, true),
				qupath.getActionToggleButton(GUIActions.FILL_DETECTIONS, true),
				qupath.getActionToggleButton(GUIActions.SHOW_GRID, true));
		return toolbar;
	}
	
	
	private Pane makeButtonPanel() {
		if (qupath == null)
			return null;
		
		VBox panel = new VBox();
		panel.setSpacing(10);
//		TilePane panel = new TilePane(Orientation.VERTICAL);
//		panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
		
		sliderRadius = new Slider(1, 100, PathPrefs.getDefaultPointRadius());
		sliderRadius.valueProperty().addListener(event -> {
			PathPrefs.setDefaultPointRadius((int)sliderRadius.getValue());
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
				File file = qupath.getDialogHelper().promptForFile(null, null, "zip files", new String[]{"zip"});
				if (file == null)
					return;
				try {
					List<PathObject> pointsList = PointIO.readPointsObjectList(file);
					if (pointsList != null) {
						for (PathObject points : pointsList)
							hierarchy.addPathObject(points);
					}
				} catch (IOException e) {
					DisplayHelpers.showErrorMessage("Load points error", e);
				}
			}
		);
		btnSave = new Button("Save points");
		btnSave.setOnAction(event -> {
				if (countingPanel == null)
					return;
				List<PathObject> pointsList = countingPanel.getPathObjects();
				if (pointsList.isEmpty()) {
					DisplayHelpers.showErrorMessage("Save points", "No points available!");
					return;
				}
				String defaultName = null;
				try {
					defaultName = ServerTools.getDisplayableImageName(qupath.getViewer().getServer()) + "-points.zip"; // Sorry, this is lazy...
				} catch (Exception e) {
					// Ignore...
				};
				File file = QuPathGUI.getSharedDialogHelper().promptToSaveFile(null, null, defaultName, "zip files", "zip");
				if (file == null)
					return;
				try {
					PointIO.writePointsObjectsList(file, pointsList, PathPrefs.getColorDefaultAnnotations());
				} catch (IOException e) {
					DisplayHelpers.showErrorMessage("Save points error", e);
				}
			}
		);
		
		GridPane panelLoadSave = PathAnnotationPanel.createColumnGridControls(
				btnLoad,
				btnSave
				);
		
////		GridPane panelLoadSave = new GridPane();
//		HBox panelLoadSave = new HBox();
////		TilePane panelLoadSave = new TilePane(Orientation.HORIZONTAL);
////		btnLoad.setMaxWidth(Double.MAX_VALUE);
////		btnSave.setMaxWidth(Double.MAX_VALUE);
//		panelLoadSave.getChildren().addAll(btnLoad, btnSave);
		
//		panelLoadSave.addRow(0, btnLoad, btnSave);
//		ColumnConstraints constraints = new ColumnConstraints();
//		constraints.setPercentWidth(50);
//		panelLoadSave.getColumnConstraints().addAll(constraints);
		
		
		Button btnConvert = qupath.getActionButton(GUIActions.DETECTIONS_TO_POINTS, false);
		Pane convertPane = new Pane(btnConvert);
		btnConvert.prefWidthProperty().bind(convertPane.widthProperty());
		
//		panel.setSpacing(5);
		panel.getChildren().addAll(
				qupath.getActionCheckBox(GUIActions.CONVEX_POINTS, false),
				qupath.getActionCheckBox(GUIActions.USE_SELECTED_COLOR, false),
				sliderPane,
				convertPane,
				panelLoadSave
				);
		
		return panel;
	}
	
	
	private void attemptToSelectPoints() {
		if (hierarchy == null || countingPanel == null)
			return;
		logger.trace("Attempting to select");
		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedObject();
		// Try to set selected object to be a Point object
		if (pathObjectSelected == null || !PathObjectTools.hasPointROI(pathObjectSelected)) {
			List<PathObject> pointObjects = countingPanel.getPathObjects();
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
		
		if (dialog != null) {
			if (qupath.getMode() != DefaultMode.POINTS)
				qupath.setMode(DefaultMode.POINTS);
			attemptToSelectPoints();
			if (!dialog.isShowing())
				dialog.show();
			return;
		}
		
		dialog = new Stage();
		dialog.setTitle("Counting");
		
		countingPanel = new CountingPanel(hierarchy);
//		countingPanel.setSize(countingPanel.getPreferredSize());
		BorderPane pane = new BorderPane();
		
		ToolBar toolbar = makeToolbarButtons();
		if (toolbar != null)
			pane.setTop(toolbar);
		pane.setCenter(countingPanel.getPane());
		Pane panelButtons = makeButtonPanel();
		if (panelButtons != null)
			pane.setBottom(panelButtons);
		
//		dialog.getDialogPane().setContent(pane);
		pane.setPadding(new Insets(10, 10, 10, 10));
		Scene scene = new Scene(pane, 300, 450);
		dialog.setScene(scene);
		dialog.setOnCloseRequest(e -> qupath.setMode(DefaultMode.MOVE));
		
//		dialog.getDialogPane().setMinSize(220, 350);
//		dialog.getDialogPane().setPrefSize(300, 450);
//		dialog.getDialogPane().setMaxSize(400, 800);
		
//		dialog.setAlwaysOnTop(true);
		if (qupath.getMode() != DefaultMode.POINTS)
			qupath.setMode(DefaultMode.POINTS);
		attemptToSelectPoints();
		
		dialog.initModality(Modality.NONE);
		dialog.setResizable(true);
		dialog.initOwner(qupath.getStage());

		dialog.show();
	}

	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> manager, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		this.hierarchy = imageDataNew == null ? null : imageDataNew.getHierarchy();
		if (countingPanel != null) {
			countingPanel.setHierarchy(this.hierarchy);
			// If the dialog is showing, try to select points
			if (dialog != null && dialog.isShowing())
				attemptToSelectPoints();
		}
	}

}
