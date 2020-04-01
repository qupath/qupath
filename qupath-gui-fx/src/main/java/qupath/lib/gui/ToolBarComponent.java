package qupath.lib.gui;

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
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

class ToolBarComponent {
		
		private QuPathGUI qupath;
		
		private double lastMagnification = Double.NaN;
		
		private Label labelMag = new Label("1x");
		private Tooltip tooltipMag = new Tooltip("Current magnification - double-click to set");
		private ToolBar toolbar = new ToolBar();
		
		ToolBarComponent(final QuPathGUI qupath) {
			this.qupath = qupath;
			
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
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_ANALYSIS_PANEL, true, null, true));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			ToggleGroup groupTools = new ToggleGroup();
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.MOVE_TOOL, true, groupTools, true));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.RECTANGLE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.ELLIPSE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.LINE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.POLYGON_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.POLYLINE_TOOL, true, groupTools, false));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			ToggleButton btnBrush = qupath.getActionToggleButton(GUIActions.BRUSH_TOOL, true, groupTools, false);
			toolbar.getItems().add(btnBrush);
			btnBrush.setOnMouseClicked(e -> {
				if (e.isPopupTrigger() || e.getClickCount() < 2)
					return;

				final ParameterList params = new ParameterList()
						.addDoubleParameter("brushSize", "Brush diameter", PathPrefs.getBrushDiameter(), "pixels", "Enter the default brush diameter, in pixels")
						.addBooleanParameter("brushScaleMag", "Scale brush size by magnification", PathPrefs.getBrushScaleByMag())
						.addBooleanParameter("brushCreateNew", "Create new objects when painting", PathPrefs.getBrushCreateNewObjects());
				final ParameterPanelFX panel = new ParameterPanelFX(params);
				panel.addParameterChangeListener(new ParameterChangeListener() {

					@Override
					public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
						if ("brushSize".equals(key)) {
							double radius = params.getDoubleParameterValue("brushSize");
							if (!Double.isNaN(radius)) {
								PathPrefs.setBrushDiameter((int)Math.round(Math.max(1, radius)));
							}
						} else if ("brushCreateNew".equals(key))
							PathPrefs.setBrushCreateNewObjects(params.getBooleanParameterValue("brushCreateNew"));
						else if ("brushScaleMag".equals(key))
							PathPrefs.setBrushScaleByMag(params.getBooleanParameterValue("brushScaleMag"));
					}

				});
				Dialogs.showConfirmDialog("Brush tool options", panel.getPane());
			});
			ToggleButton toggleWand = qupath.getActionToggleButton(GUIActions.WAND_TOOL, true, groupTools, false);
			toolbar.getItems().add(toggleWand);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));

			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.POINTS_TOOL, true, groupTools, false));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SELECTION_MODE, true, null, PathPrefs.isSelectionMode()));			
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));

			toolbar.getItems().add(qupath.getActionButton(GUIActions.BRIGHTNESS_CONTRAST, true));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			toolbar.getItems().add(labelMag);
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.ZOOM_TO_FIT, true, false));

			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			OverlayOptions overlayOptions = qupath.getOverlayOptions();
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_ANNOTATIONS, true, overlayOptions.getShowAnnotations()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_TMA_GRID, true, overlayOptions.getShowTMAGrid()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_DETECTIONS, true, overlayOptions.getShowDetections()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.FILL_DETECTIONS, true, overlayOptions.getFillDetections()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_PIXEL_CLASSIFICATION, true, overlayOptions.getShowPixelClassification()));

			final Slider sliderOpacity = new Slider(0, 1, 1);
			sliderOpacity.valueProperty().bindBidirectional(overlayOptions.opacityProperty());
			sliderOpacity.setTooltip(new Tooltip("Overlay opacity"));
			toolbar.getItems().add(sliderOpacity);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			
			Button btnMeasure = new Button();
			btnMeasure.setGraphic(PathIconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.TABLE));
			btnMeasure.setTooltip(new Tooltip("Show measurements table"));
			ContextMenu popupMeasurements = new ContextMenu();
			popupMeasurements.getItems().addAll(
					qupath.getActionMenuItem(GUIActions.SUMMARY_TMA),
					qupath.getActionMenuItem(GUIActions.SUMMARY_ANNOTATIONS),
					qupath.getActionMenuItem(GUIActions.SUMMARY_DETECTIONS),
					qupath.getActionMenuItem(GUIActions.EXPORT_MEASUREMENTS)
					);
			btnMeasure.setOnMouseClicked(e -> {
				popupMeasurements.show(btnMeasure, e.getScreenX(), e.getScreenY());
			});
			
			toolbar.getItems().add(btnMeasure);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			// TODO: Check if viewer really needed...
			QuPathViewerPlus viewer = qupath.getViewer();
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_OVERVIEW, true, viewer.isOverviewVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_LOCATION, true, viewer.isLocationVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_SCALEBAR, true, viewer.isScalebarVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_GRID, true, overlayOptions.getShowGrid()));
			
			// Add preferences button
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			toolbar.getItems().add(qupath.getActionButton(GUIActions.PREFERENCES, true));
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