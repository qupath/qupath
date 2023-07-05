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

import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.OverlayActions;
import qupath.lib.gui.actions.ViewerActions;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.tools.ExtendedPathTool;
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
	
	private int toolIdx;
	
	@SuppressWarnings("unused")
	private ObservableValue<? extends QuPathViewer> viewerProperty; // Keep to prevent garbage collection

	private ToolBar toolbar = new ToolBar();

	ToolBarComponent(ToolManager toolManager, ViewerActions viewerManagerActions, CommonActions commonActions, OverlayActions overlayActions) {
		this.toolManager = toolManager;
		this.viewerProperty = viewerManagerActions.getViewerManager().activeViewerProperty();

		logger.trace("Initializing toolbar");
		
		var magLabel = new ViewerMagnificationLabel();
		viewerProperty.addListener((v, o, n) -> magLabel.setViewer(n));
		magLabel.setViewer(viewerProperty.getValue());

		availableTools = toolManager.getTools();
		availableTools.addListener((Change<? extends PathTool> v) -> updateToolbar());

		// Show analysis panel
		List<Node> nodes = new ArrayList<>();
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(commonActions.SHOW_ANALYSIS_PANE));
		nodes.add(new Separator(Orientation.VERTICAL));

		// Record index where tools start
		toolIdx = nodes.size();

		addToolButtons(nodes, availableTools);

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(toolManager.getSelectionModeAction()));			

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createButtonWithGraphicOnly(commonActions.BRIGHTNESS_CONTRAST));

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(magLabel);
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(viewerManagerActions.ZOOM_TO_FIT));

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_ANNOTATIONS));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_NAMES));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_TMA_GRID));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_DETECTIONS));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.FILL_DETECTIONS));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_PIXEL_CLASSIFICATION));

		final Slider sliderOpacity = new Slider(0, 1, 1);
		var overlayOptions = overlayActions.getOverlayOptions();
		sliderOpacity.valueProperty().bindBidirectional(overlayOptions.opacityProperty());
		sliderOpacity.setTooltip(new Tooltip(getDescription("overlayOpacity")));
		nodes.add(sliderOpacity);

		nodes.add(new Separator(Orientation.VERTICAL));

		var btnMeasure = new MenuButton();
		btnMeasure.setGraphic(IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.TABLE));
		btnMeasure.setTooltip(new Tooltip(getDescription("showMeasurementsTable")));
		btnMeasure.getItems().addAll(
				ActionTools.createMenuItem(commonActions.MEASURE_TMA),
				ActionTools.createMenuItem(commonActions.MEASURE_ANNOTATIONS),
				ActionTools.createMenuItem(commonActions.MEASURE_DETECTIONS)
				);
		nodes.add(btnMeasure);

		nodes.add(new Separator(Orientation.VERTICAL));

		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(viewerManagerActions.SHOW_OVERVIEW));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(viewerManagerActions.SHOW_LOCATION));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(viewerManagerActions.SHOW_SCALEBAR));
		nodes.add(ActionTools.createToggleButtonWithGraphicOnly(overlayActions.SHOW_GRID));

		nodes.add(new Separator(Orientation.VERTICAL));
		nodes.add(ActionTools.createButtonWithGraphicOnly(commonActions.PREFERENCES));
		nodes.add(ActionTools.createButtonWithGraphicOnly(commonActions.HELP_VIEWER));

		toolbar.getItems().setAll(nodes);
	}
	
	
	private static String getDescription(String key) {
		return QuPathResources.getString("Toolbar." + key + ".description");
	}
	
	private static String getName(String key) {
		return QuPathResources.getString("Toolbar." + key);
	}
	
	private static String getMessage(String key) {
		return QuPathResources.getString("Toolbar.message." + key);
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

	private ToggleGroup toolGroup;
	
	private ToggleGroup getToolToggleGroup() {
		if (toolGroup == null) {
			toolGroup = new ToggleGroup();
			toolGroup.selectedToggleProperty().addListener((v, o, n) -> {
				if (n == null)
					o.setSelected(true);
			});
		}
		return toolGroup;
	}

	private void addToolButtons(List<Node> nodes, List<PathTool> tools) {
		int ind = toolIdx;
		var group = getToolToggleGroup();
		for (var tool : tools) {
			var action = toolManager.getToolAction(tool);
			var btnTool = toolMap.get(tool);
			if (btnTool == null) {
				if (action.getGraphic() == null)
					btnTool = ActionTools.createToggleButton(action);
				else
					btnTool = ActionTools.createToggleButtonWithGraphicOnly(action);
				var toggleButton = (ToggleButton)btnTool;
				toggleButton.setToggleGroup(group);
				if (tool instanceof ExtendedPathTool extendedTool) {
					var popup = createContextMenu(extendedTool, toggleButton);
					btnTool.setOnContextMenuRequested(e -> {
						popup.show(toggleButton, e.getScreenX(), e.getScreenY());
					});
					addContextMenuDecoration(toggleButton, popup);
				}
				toolMap.put(tool, btnTool);
			}
			nodes.add(ind++, btnTool);
		}
	}
	
	
	private static void addContextMenuDecoration(ToggleButton btn, ContextMenu popup) {
		// It's horribly complicated to get the decoration to remain properly, 
		// since it appears to need a scene - and can disappear then the graphic changes
		var triangle = new Path();
		double width = 6;
		triangle.getElements().setAll(
				new MoveTo(0, 0),
				new LineTo(width, 0),
				new LineTo(width/2.0, Math.sqrt(width*width/2.0)),
				new ClosePath()
				);
		triangle.setTranslateX(-width);
		triangle.setTranslateY(-width);
		triangle.setRotate(-90);
		triangle.fillProperty().bind(btn.textFillProperty());
		triangle.setStroke(null);
		triangle.setOpacity(0.5);
		var decoration = new GraphicDecoration(triangle, Pos.BOTTOM_RIGHT);
		btn.sceneProperty().addListener((v, o, n) -> {
			Platform.runLater(() -> {
				if (n != null)
					Decorator.addDecoration(btn, decoration);
				else
					Decorator.removeDecoration(btn, decoration);
			});
		});
		Platform.runLater(() -> Decorator.addDecoration(btn, decoration));
		btn.graphicProperty().addListener((v, o, n) -> {
			Decorator.removeAllDecorations(btn);
			Platform.runLater(() -> Decorator.addDecoration(btn, decoration));
		});
		triangle.setOnMouseClicked(e -> {
			popup.show(btn, e.getScreenX(), e.getScreenY());
		});
	}
	
	
	private ContextMenu createContextMenu(ExtendedPathTool tool, Toggle toolToggle) {
		var menu = new ContextMenu();
		var toggle = new ToggleGroup();
		for (var subtool : tool.getAvailableTools()) {
			var mi = new RadioMenuItem();
			mi.textProperty().bind(subtool.nameProperty());
			mi.graphicProperty().bind(subtool.iconProperty());
			mi.setToggleGroup(toggle);
			menu.getItems().add(mi);
			mi.selectedProperty().addListener((v, o, n) -> {
				if (n) {
					tool.selectedTool().set(subtool);
					toolToggle.setSelected(true);
				}
			});
		}
		return menu;
	}

	
	ToolBar getToolBar() {
		return toolbar;
	}

	
	private static class ViewerMagnificationLabel extends Label implements QuPathViewerListener {
		
		private QuPathViewer viewer;
		
		private static String defaultText = "1x";
		
		private Tooltip tooltipMag = new Tooltip(getDescription("magnification"));
		
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
				tooltipMag.setText(getName("magnification"));
			else if (mag != null && !Double.isNaN(mag))
				tooltipMag.setText(getDescription("magnification"));
			else
				tooltipMag.setText(getDescription("magnificationScale"));
		}

		
		private void promptToUpdateMagnification() {
			if (viewer == null || !viewer.hasServer())
				return;
			double fullMagnification = viewer.getServer().getMetadata().getMagnification();
			boolean hasMagnification = !Double.isNaN(fullMagnification);
			if (hasMagnification) {
				double defaultValue = Math.rint(viewer.getMagnification() * 1000) / 1000;
				Double value = Dialogs.showInputDialog(getName("setMagnification"), getMessage("promptMagnification"), defaultValue);
				if (value == null)
					return;
				if (Double.isFinite(value) && value > 0)
					viewer.setMagnification(value.doubleValue());
				else
					Dialogs.showErrorMessage(getName("setMagnification"), String.format(getMessage("invalidMagnification"), value));
			} else {
				double defaultValue = Math.rint(viewer.getDownsampleFactor() * 1000) / 1000;
				Double value = Dialogs.showInputDialog(getName("setDownsample"), getMessage("promptDownsample"), defaultValue);
				if (value == null)
					return;
				if (Double.isFinite(value) && value > 0)
					viewer.setDownsampleFactor(value.doubleValue());
				else
					Dialogs.showErrorMessage(getName("setDownsample"), String.format(getMessage("invalidDownsample"), value));
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