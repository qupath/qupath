/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.plugins.parameters.ParameterList;

class ToolBarComponent {
	
	private final static Logger logger = LoggerFactory.getLogger(ToolBarComponent.class);
		
		private QuPathGUI qupath;
		
		private double lastMagnification = Double.NaN;
		
		private Label labelMag = new Label("1x");
		private Tooltip tooltipMag = new Tooltip("Current magnification - double-click to set");
		
		/**
		 * The toolbar consists of distinct sections
		 */
		private ObservableList<PathTool> availableTools;
		private Map<PathTool, Node> toolMap = new WeakHashMap<>();
		
		private int toolIdx;
		
		
		private ToolBar toolbar = new ToolBar();
		
		ToolBarComponent(final QuPathGUI qupath) {
			this.qupath = qupath;
			
			logger.trace("Initializing toolbar");
			
			availableTools = qupath.getAvailableTools();
			availableTools.addListener((Change<? extends PathTool> v) -> updateToolbar());
			
			var actionManager = qupath.getDefaultActions();
			
			labelMag.setTooltip(tooltipMag);
			labelMag.setPrefWidth(60);
			labelMag.setMinWidth(60);
			labelMag.setMaxWidth(60);
			labelMag.setTextAlignment(TextAlignment.CENTER);
			
			labelMag.setOnMouseEntered(e -> refreshMagnificationTooltip());
			
			labelMag.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2)
					promptToUpdateMagnification();
			});
			
			// Show analysis panel
			List<Node> nodes = new ArrayList<>();
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_ANALYSIS_PANE, true, null, true));
			nodes.add(new Separator(Orientation.VERTICAL));
			
			// Record index where tools start
			toolIdx = nodes.size();
			
			addToolButtons(nodes, availableTools);
						
			nodes.add(new Separator(Orientation.VERTICAL));
			
			nodes.add(ActionTools.createToggleButton(actionManager.SELECTION_MODE, true, null, PathPrefs.selectionModeProperty().get()));			
			
			nodes.add(new Separator(Orientation.VERTICAL));

			nodes.add(ActionTools.createButton(actionManager.BRIGHTNESS_CONTRAST, true));
			
			nodes.add(new Separator(Orientation.VERTICAL));
			
			nodes.add(labelMag);
			nodes.add(ActionTools.createToggleButton(actionManager.ZOOM_TO_FIT, true, false));

			nodes.add(new Separator(Orientation.VERTICAL));
			
			OverlayOptions overlayOptions = qupath.getOverlayOptions();
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_ANNOTATIONS, true, overlayOptions.getShowAnnotations()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_TMA_GRID, true, overlayOptions.getShowTMAGrid()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_DETECTIONS, true, overlayOptions.getShowDetections()));
			nodes.add(ActionTools.createToggleButton(actionManager.FILL_DETECTIONS, true, overlayOptions.getFillDetections()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_PIXEL_CLASSIFICATION, true, overlayOptions.getShowPixelClassification()));

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
					ActionTools.createMenuItem(qupath.getDefaultActions().MEASURE_TMA),
					ActionTools.createMenuItem(qupath.getDefaultActions().MEASURE_ANNOTATIONS),
					ActionTools.createMenuItem(qupath.getDefaultActions().MEASURE_DETECTIONS)
					);
			btnMeasure.setOnMouseClicked(e -> {
				popupMeasurements.show(btnMeasure, e.getScreenX(), e.getScreenY());
			});
			
			nodes.add(btnMeasure);
			
			nodes.add(new Separator(Orientation.VERTICAL));
			
			// TODO: Check if viewer really needed...
			QuPathViewerPlus viewer = qupath.getViewer();
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_OVERVIEW, true, viewer.isOverviewVisible()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_LOCATION, true, viewer.isLocationVisible()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_SCALEBAR, true, viewer.isScalebarVisible()));
			nodes.add(ActionTools.createToggleButton(actionManager.SHOW_GRID, true, overlayOptions.getShowGrid()));
			
			// Add preferences button
			nodes.add(new Separator(Orientation.VERTICAL));
			nodes.add(ActionTools.createButton(qupath.lookupActionByText("Preferences..."), true));
			
			toolbar.getItems().setAll(nodes);
		}
		
		
		void promptToUpdateMagnification() {
			QuPathViewer viewer = qupath.getViewer();
			if (viewer == null || !viewer.hasServer())
				return;
			double fullMagnification = viewer.getServer().getMetadata().getMagnification();
			boolean hasMagnification = !Double.isNaN(fullMagnification);
			ParameterList params = new ParameterList();
			if (hasMagnification) {
				double defaultValue = Math.rint(viewer.getMagnification() * 1000) / 1000;
				params.addDoubleParameter("magnification", "Enter magnification", defaultValue);
			} else {
				double defaultValue = Math.rint(viewer.getDownsampleFactor() * 1000) / 1000;
				params.addDoubleParameter("downsample", "Enter downsample factor", defaultValue);			
			}
			
			if (!Dialogs.showParameterDialog("Set magnification", params))
				return;
			
			if (hasMagnification) {
				double mag = params.getDoubleParameterValue("magnification");
				if (!Double.isNaN(mag))
					viewer.setMagnification(mag);
			} else {
				double downsample = params.getDoubleParameterValue("downsample");
				if (!Double.isNaN(downsample))
					viewer.setDownsampleFactor(downsample);
			}
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
				var action = qupath.getToolAction(tool);
				var btnTool = toolMap.get(tool);
				if (btnTool == null) {
					btnTool = ActionTools.createToggleButton(action, action.getGraphic() != null);
					toolMap.put(tool, btnTool);
				}
				nodes.add(ind++, btnTool);
			}
			
		}
		
		
		void refreshMagnificationTooltip() {
			// Ensure we have the right tooltip for magnification
			if (tooltipMag == null)
				return;
			var imageData = qupath.getImageData();
			var mag = imageData == null ? null : imageData.getServer().getMetadata().getMagnification();
			if (imageData == null)
				tooltipMag.setText("Magnification");
			else if (mag != null && !Double.isNaN(mag))
				tooltipMag.setText("Display magnification - double-click to edit");
			else
				tooltipMag.setText("Display scale value - double-click to edit");
		}
		
		void updateMagnificationDisplay(final QuPathViewer viewer) {
			if (viewer == null || labelMag == null)
				return;
			// Update magnification info
			double mag = viewer.getMagnification();
			if (Math.abs(mag - lastMagnification) / mag < 0.0001)
				return;
			lastMagnification = mag;
			Platform.runLater(() -> {
				labelMag.setText(GuiTools.getMagnificationString(viewer));
//				labelMag.setTextAlignment(TextAlignment.CENTER);
			});
		}
		
		ToolBar getToolBar() {
			return toolbar;
		}
		
		
	}