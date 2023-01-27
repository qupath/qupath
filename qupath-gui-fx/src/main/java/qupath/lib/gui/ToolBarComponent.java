/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.QuPathGUI.DefaultActions;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.MultiviewManager;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

class ToolBarComponent {

	private static final Logger logger = LoggerFactory.getLogger(ToolBarComponent.class);

	/**
	 * The toolbar consists of distinct sections
	 */
	private ObservableList<PathTool> availableTools;
	private Map<PathTool, Node> toolMap = new WeakHashMap<>();

	private ToolManager toolManager;
	private MultiviewManager viewerManager;
	private DefaultActions defaultActions;
	
	private int toolIdx;


	private ToolBar toolbar = new ToolBar();

	ToolBarComponent(ToolManager toolManager, MultiviewManager viewerManager, DefaultActions defaultActions) {
		this.toolManager = toolManager;
		this.viewerManager = viewerManager;
		this.defaultActions = defaultActions;

		logger.trace("Initializing toolbar");
		
		var magLabel = new ViewerMagnificationLabel();
		viewerManager.activeViewerProperty().addListener((v, o, n) -> magLabel.setViewer(n));
		magLabel.setViewer(viewerManager.getActiveViewer());

		availableTools = toolManager.getTools();
		availableTools.addListener((Change<? extends PathTool> v) -> updateToolbar());

		// Show analysis panel
		List<Node> nodes = new ArrayList<>();
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_ANALYSIS_PANE, true, null, true));
		nodes.add(new Separator(Orientation.VERTICAL));

		// Record index where tools start
		toolIdx = nodes.size();

		addToolButtons(nodes, availableTools);

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createToggleButton(defaultActions.SELECTION_MODE, true, null, PathPrefs.selectionModeProperty().get()));			

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createButton(defaultActions.BRIGHTNESS_CONTRAST, true));

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(magLabel);
		nodes.add(ActionTools.createToggleButton(defaultActions.ZOOM_TO_FIT, true, false));

		nodes.add(new Separator(Orientation.VERTICAL));

		OverlayOptions overlayOptions = viewerManager.getOverlayOptions();
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_ANNOTATIONS, true, overlayOptions.getShowAnnotations()));
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_NAMES, true, overlayOptions.getShowNames()));
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_TMA_GRID, true, overlayOptions.getShowTMAGrid()));
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_DETECTIONS, true, overlayOptions.getShowDetections()));
		nodes.add(ActionTools.createToggleButton(defaultActions.FILL_DETECTIONS, true, overlayOptions.getFillDetections()));
		nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_PIXEL_CLASSIFICATION, true, overlayOptions.getShowPixelClassification()));

		final Slider sliderOpacity = new Slider(0, 1, 1);
		sliderOpacity.valueProperty().bindBidirectional(overlayOptions.opacityProperty());
		sliderOpacity.setTooltip(new Tooltip("Overlay opacity"));
		nodes.add(sliderOpacity);

		nodes.add(new Separator(Orientation.VERTICAL));


		Button btnMeasure = new Button();
		btnMeasure.setGraphic(IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.TABLE));
		btnMeasure.setTooltip(new Tooltip("Show measurements table"));
		ContextMenu popupMeasurements = new ContextMenu();

		popupMeasurements.getItems().addAll(
				ActionTools.createMenuItem(defaultActions.MEASURE_TMA),
				ActionTools.createMenuItem(defaultActions.MEASURE_ANNOTATIONS),
				ActionTools.createMenuItem(defaultActions.MEASURE_DETECTIONS)
				);
		btnMeasure.setOnMouseClicked(e -> {
			popupMeasurements.show(btnMeasure, e.getScreenX(), e.getScreenY());
		});

		nodes.add(btnMeasure);

		nodes.add(new Separator(Orientation.VERTICAL));

		// TODO: Check if viewer really needed...
		QuPathViewer viewer = viewerManager.getActiveViewer();
		if (viewer instanceof QuPathViewerPlus) {
			QuPathViewerPlus viewerPlus = (QuPathViewerPlus)viewer;
			nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_OVERVIEW, true, viewerPlus.isOverviewVisible()));
			nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_LOCATION, true, viewerPlus.isLocationVisible()));
			nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_SCALEBAR, true, viewerPlus.isScalebarVisible()));
			nodes.add(ActionTools.createToggleButton(defaultActions.SHOW_GRID, true, overlayOptions.getShowGrid()));
		}

		// Add preferences button
		nodes.add(new Separator(Orientation.VERTICAL));
		nodes.add(ActionTools.createButton(defaultActions.PREFERENCES, true));

		toolbar.getItems().setAll(nodes);
	}


	void updateToolbar() {
		// Snapshot all existing nodes
		var nodes = new ArrayList<>(toolbar.getItems());
		// Remove all the tools
		nodes.removeAll(toolMap.values());
		// Add all the tools as they currently are
		addToolButtons(nodes, availableTools);
		// Update the items
		toolbar.getItems().setAll(nodes);
	}


	private void addToolButtons(List<Node> nodes, List<PathTool> tools) {

		int ind = toolIdx;

		for (var tool : tools) {
			var action = toolManager.getToolAction(tool);
			var btnTool = toolMap.get(tool);
			if (btnTool == null) {
				btnTool = ActionTools.createToggleButton(action, action.getGraphic() != null);
				toolMap.put(tool, btnTool);
			}
			nodes.add(ind++, btnTool);
		}

	}

	
	ToolBar getToolBar() {
		return toolbar;
	}

	
	private static class ViewerMagnificationLabel extends Label implements QuPathViewerListener {
		
		private QuPathViewer viewer;
		
		private static String defaultText = "1x";
		
		private Tooltip tooltipMag = new Tooltip("Current magnification - double-click to set");
		
		private ViewerMagnificationLabel() {
			setTooltip(tooltipMag);
			setPrefWidth(60);
			setMinWidth(60);
			setMaxWidth(60);
			setTextAlignment(TextAlignment.CENTER);
			setOnMouseEntered(e -> refreshMagnificationTooltip());
			setOnMouseClicked(e -> {
				if (e.getClickCount() == 2)
					promptToUpdateMagnification();
			});
		}
		
		private void setViewer(QuPathViewer viewer) {
			if (this.viewer == viewer)
				return;
			if (this.viewer != null)
				this.viewer.removeViewerListener(this);
			this.viewer = viewer;
			if (this.viewer != null)
				this.viewer.addViewerListener(this);
			updateMagnificationString();
		}
		
		private void updateMagnificationString() {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> updateMagnificationString());
				return;
			}
			if (viewer == null || viewer.getImageData() == null) {
				setText(defaultText);
				return;
			}
			// Update magnification info
			setText(GuiTools.getMagnificationString(viewer));
		}
		
		
		private void refreshMagnificationTooltip() {
			// Ensure we have the right tooltip for magnification
			if (tooltipMag == null || viewer == null)
				return;
			var imageData = viewer.getImageData();
			var mag = imageData == null ? null : imageData.getServer().getMetadata().getMagnification();
			if (imageData == null)
				tooltipMag.setText("Magnification");
			else if (mag != null && !Double.isNaN(mag))
				tooltipMag.setText("Display magnification - double-click to edit");
			else
				tooltipMag.setText("Display scale value - double-click to edit");
		}

		
		private void promptToUpdateMagnification() {
			if (viewer == null || !viewer.hasServer())
				return;
			double fullMagnification = viewer.getServer().getMetadata().getMagnification();
			boolean hasMagnification = !Double.isNaN(fullMagnification);
			if (hasMagnification) {
				double defaultValue = Math.rint(viewer.getMagnification() * 1000) / 1000;
				Double value = Dialogs.showInputDialog("Set magnification", "Enter magnification", defaultValue);
				if (value == null)
					return;
				if (Double.isFinite(value) && value > 0)
					viewer.setMagnification(value.doubleValue());
				else
					Dialogs.showErrorMessage("Set downsample factor", "Invalid magnification " + value + ". \nPlease use a value greater than 0.");
			} else {
				double defaultValue = Math.rint(viewer.getDownsampleFactor() * 1000) / 1000;
				Double value = Dialogs.showInputDialog("Set downsample factor", "Enter downsample factor", defaultValue);
				if (value == null)
					return;
				if (Double.isFinite(value) && value > 0)
					viewer.setDownsampleFactor(value.doubleValue());
				else
					Dialogs.showErrorMessage("Set downsample factor", "Invalid downsample " + value + ". \nPlease use a value greater than 0.");
			}
		}
		

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {
			updateMagnificationString();
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
			updateMagnificationString();
		}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			updateMagnificationString();
		}
		
	}



}