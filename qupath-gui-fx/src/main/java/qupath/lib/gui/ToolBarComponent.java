package qupath.lib.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.plugins.parameters.ParameterList;

class ToolBarComponent {
	
	private final static Logger logger = LoggerFactory.getLogger(ToolBarComponent.class);
		
		private QuPathGUI qupath;
		
		private double lastMagnification = Double.NaN;
		
		private Label labelMag = new Label("1x");
		private Tooltip tooltipMag = new Tooltip("Current magnification - double-click to set");
		private ToolBar toolbar = new ToolBar();
		
		ToolBarComponent(final QuPathGUI qupath) {
			this.qupath = qupath;
			
			var actionManager = qupath.getActionManager();
			
			labelMag.setTooltip(tooltipMag);
			labelMag.setPrefWidth(60);
			labelMag.setMinWidth(60);
			labelMag.setMaxWidth(60);
			labelMag.setTextAlignment(TextAlignment.CENTER);
			
			labelMag.setOnMouseEntered(e -> refreshMagnificationTooltip());
			
			labelMag.setOnMouseClicked(e -> {

					QuPathViewer viewer = qupath.getViewer();
					if (viewer == null || e.getClickCount() != 2 || !viewer.hasServer())
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
				
			});
			
			// Show analysis panel
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_ANALYSIS_PANEL, true, null, true));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			ToggleGroup groupTools = new ToggleGroup();
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.MOVE_TOOL, true, groupTools, true));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.RECTANGLE_TOOL, true, groupTools, false));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.ELLIPSE_TOOL, true, groupTools, false));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.LINE_TOOL, true, groupTools, false));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.POLYGON_TOOL, true, groupTools, false));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.POLYLINE_TOOL, true, groupTools, false));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			ToggleButton btnBrush = ActionTools.getActionToggleButton(actionManager.BRUSH_TOOL, true, groupTools, false);
			toolbar.getItems().add(btnBrush);
//			ToggleButton toggleWand = ActionTools.getActionToggleButton(qupath.WAND_TOOL, true, groupTools, false);
//			toolbar.getItems().add(toggleWand);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));

			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.POINTS_TOOL, true, groupTools, false));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SELECTION_MODE, true, null, PathPrefs.isSelectionMode()));			
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));

			toolbar.getItems().add(ActionTools.getActionButton(actionManager.BRIGHTNESS_CONTRAST, true));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			toolbar.getItems().add(labelMag);
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.ZOOM_TO_FIT, true, false));

			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			OverlayOptions overlayOptions = qupath.getOverlayOptions();
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_ANNOTATIONS, true, overlayOptions.getShowAnnotations()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_TMA_GRID, true, overlayOptions.getShowTMAGrid()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_DETECTIONS, true, overlayOptions.getShowDetections()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.FILL_DETECTIONS, true, overlayOptions.getFillDetections()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_PIXEL_CLASSIFICATION, true, overlayOptions.getShowPixelClassification()));

			final Slider sliderOpacity = new Slider(0, 1, 1);
			sliderOpacity.valueProperty().bindBidirectional(overlayOptions.opacityProperty());
			sliderOpacity.setTooltip(new Tooltip("Overlay opacity"));
			toolbar.getItems().add(sliderOpacity);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			
			Button btnMeasure = new Button();
			btnMeasure.setGraphic(PathIconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.TABLE));
			btnMeasure.setTooltip(new Tooltip("Show measurements table"));
			ContextMenu popupMeasurements = new ContextMenu();
			
			// TODO: ADD SUMMARY MEASUREMENTS AGAIN!
			logger.warn("REMEMBER TO REINSTATE SUMMARY MEASUREMENT COMMANDS PROPERLY");
//			popupMeasurements.getItems().addAll(
//					ActionTools.getActionMenuItem(qupath.measureManager.TMA),
//					ActionTools.getActionMenuItem(qupath.measureManager.ANNOTATIONS),
//					ActionTools.getActionMenuItem(qupath.measureManager.DETECTIONS),
//					ActionTools.getActionMenuItem(qupath.measureManager.EXPORT)
//					);
			btnMeasure.setOnMouseClicked(e -> {
				popupMeasurements.show(btnMeasure, e.getScreenX(), e.getScreenY());
			});
			
			toolbar.getItems().add(btnMeasure);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			// TODO: Check if viewer really needed...
			QuPathViewerPlus viewer = qupath.getViewer();
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_OVERVIEW, true, viewer.isOverviewVisible()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_LOCATION, true, viewer.isLocationVisible()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_SCALEBAR, true, viewer.isScalebarVisible()));
			toolbar.getItems().add(ActionTools.getActionToggleButton(actionManager.SHOW_GRID, true, overlayOptions.getShowGrid()));
			
			// Add preferences button
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			toolbar.getItems().add(ActionTools.getActionButton(qupath.lookupActionByText("Preferences..."), true));
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
		
		public void updateMagnificationDisplay(final QuPathViewer viewer) {
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