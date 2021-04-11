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

package qupath.process.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.function.DoubleToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMapImageServer;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;


/**
 * Command for generating density maps from detections on an image.
 * 
 * @author Pete Bankhead
 */
public class DensityMapCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapCommand.class);
		
	private QuPathGUI qupath;
	private DensityMapDialog dialog;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public DensityMapCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		if (dialog == null) {
			dialog = new DensityMapDialog();
			if (qupath.getImageData() != null)
				dialog.updateDefaults(qupath.getImageData());
		}
		
		var stage = dialog.getStage();
		stage.setOnCloseRequest(e -> dialog.deregister());
		stage.show();
	}
	
	
	private static enum DensityMapType {
		
		DETECTIONS,
		POSITIVE_DETECTIONS,
		POINT_ANNOTATIONS;
		
		@Override
		public String toString() {
			switch(this) {
			case DETECTIONS:
				return "All detections";
			case POSITIVE_DETECTIONS:
				return "Positive detections";
			case POINT_ANNOTATIONS:
				return "Point annotations";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
		
	}
	
	private class DensityMapDialog implements ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {
		
		private final static String title = "Density map";
		
		private Stage stage;
		
		private ComboBox<DensityMapType> comboType = new ComboBox<>();
		private ReadOnlyObjectProperty<DensityMapType> densityType = comboType.getSelectionModel().selectedItemProperty();
		
		private ComboBox<PathClass> comboPrimary = new ComboBox<>(qupath.getAvailablePathClasses());
		private ObservableValue<PathClass> selectedClass = comboPrimary.getSelectionModel().selectedItemProperty();

		private ComboBox<ColorMap> comboColorMap = new ComboBox<>();
		private ObservableValue<ColorMap> colorMap = comboColorMap.getSelectionModel().selectedItemProperty();
		
		private ComboBox<ImageInterpolation> comboInterpolation = new ComboBox<>();
		private ObservableValue<ImageInterpolation> interpolation = comboInterpolation.getSelectionModel().selectedItemProperty();

		private DoubleProperty pixelSize = new SimpleDoubleProperty(-1);
		private DoubleProperty radius = new SimpleDoubleProperty(10.0);

		private DoubleProperty gamma = new SimpleDoubleProperty(1.0);
		private DoubleProperty minCount = new SimpleDoubleProperty(0);
		private BooleanProperty gaussianFilter = new SimpleBooleanProperty(false);

		private DoubleProperty minDisplay = new SimpleDoubleProperty(0);
		private DoubleProperty maxDisplay = new SimpleDoubleProperty(1);
		private BooleanProperty autoUpdateDisplayRange = new SimpleBooleanProperty(true);
		
		// Property that controls whether display options can be adjusted
		private BooleanBinding displayAdjustable = densityType.isEqualTo(DensityMapType.POSITIVE_DETECTIONS);
		
		/**
		 * Automatically update the density maps and overlays.
		 */
		private BooleanProperty autoUpdate = new SimpleBooleanProperty(true);
		
		private Future<?> currentTask;
		
		private Map<QuPathViewer, DensityMapImageServer> densityMapMap = new WeakHashMap<>();
		private Map<QuPathViewer, PathOverlay> overlayMap = new WeakHashMap<>();
		private Map<String, List<MinMax>> densityMapRanges = new HashMap<>();
		
		public DensityMapDialog() {
			
			comboType.getItems().setAll(DensityMapType.values());
			comboType.getSelectionModel().select(DensityMapType.DETECTIONS);
			
			comboInterpolation.getItems().setAll(ImageInterpolation.values());
			comboInterpolation.getSelectionModel().select(ImageInterpolation.NEAREST);
			
			var pane = new GridPane();
			int row = 0;
			
			var labelTitle = new Label("Define density map");
			labelTitle.setStyle("-fx-font-weight: bold;");
			labelTitle.setPadding(new Insets(0, 0, 5, 0));
			PaneTools.addGridRow(pane, row++, 0,
					"Options to specify how the density map is calculated",
					labelTitle, labelTitle, labelTitle);

//			var labelDescription = new Label("These options change how the density map is calculated.");
//			labelDescription.setAlignment(Pos.CENTER);
//			PaneTools.addGridRow(pane, row++, 0, null, labelDescription, labelDescription, labelDescription);

			PaneTools.addGridRow(pane, row++, 0, "Select type of density map.\n"
					+ "Use 'All detections' to look for the density of all detections with any classication.\n"
					+ "Use 'Positive detections' specifically if your detections are classified as positive & negative and you want to find a high density that is positive.\n"
					+ "Use 'Point annotations' to use annotated points rather than detections.",
					new Label("Map type"), comboType, comboType);
			PaneTools.addGridRow(pane, row++, 0, "Select object classifications to include.\n"
					+ "Use this to filter out detections that should not contribute to the density map.", new Label("Classifications"), comboPrimary, comboPrimary);
			
			var sliderRadius = new Slider(0, 1000, radius.get());
			sliderRadius.valueProperty().bindBidirectional(radius);
			initializeSliderSnapping(sliderRadius, 50, 1, 0.1);
			var tfRadius = new TextField();
			boolean expandSliderLimits = true;
			GuiTools.bindSliderAndTextField(sliderRadius, tfRadius, expandSliderLimits, 2);
			GuiTools.installRangePrompt(sliderRadius);
//			tfRadius.setOnMouseClicked(e -> {
//				if (e.getClickCount() > 1)
//					GuiTools.promptForSliderRange(slider)
//			});
			PaneTools.addGridRow(pane, row++, 0, "Select smoothing radius used to calculate densities.\n"
					+ "This is defined in calibrated pixel units (e.g. µm if available).", new Label("Radius"), sliderRadius, tfRadius);
			
			
			var cbGaussianFilter = new CheckBox("Use Gaussian filter");
			cbGaussianFilter.selectedProperty().bindBidirectional(gaussianFilter);
			PaneTools.addGridRow(pane, row++, 0, "Use a Gaussian filter to estimate densities (weighted sum rather than local mean).\n"
					+ "This gives a smoother result, but the output is less intuitive.", 
					cbGaussianFilter, cbGaussianFilter, cbGaussianFilter);
			cbGaussianFilter.setPadding(new Insets(0, 0, 10, 0));
			
			
			var labelColor = new Label("Customize appearance");
			labelColor.setStyle("-fx-font-weight: bold;");
			labelColor.setPadding(new Insets(5, 0, 5, 0));
			labelColor.setPadding(new Insets(10, 0, 0, 0));
			PaneTools.addGridRow(pane, row++, 0,
					"Options to customize how the density map is displayed",
					labelColor, labelColor, labelColor);
			
//			var labelAppearance = new Label("These options change the appearance of the density map only.");
//			labelAppearance.setAlignment(Pos.CENTER);
//			PaneTools.addGridRow(pane, row++, 0, null, labelAppearance, labelAppearance, labelAppearance);
			
			PaneTools.addGridRow(pane, row++, 0, "Choose the colormap to use for display", new Label("Colormap"), comboColorMap, comboColorMap);
			
			PaneTools.addGridRow(pane, row++, 0, "Choose how the density map should be interpolated (this impacts the visual smoothness, especially if the density radius is small)", new Label("Interpolation"), comboInterpolation, comboInterpolation);

			var slideMin = new Slider(0, maxDisplay.get(), minDisplay.get());
			slideMin.valueProperty().bindBidirectional(minDisplay);
			initializeSliderSnapping(slideMin, 1, 1, 0.1);
			slideMin.disableProperty().bind(autoUpdateDisplayRange.or(displayAdjustable.not()));
			var tfMin = new TextField();
			GuiTools.bindSliderAndTextField(slideMin, tfMin, expandSliderLimits);
			GuiTools.installRangePrompt(slideMin);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the first entry in the colormap.", new Label("Min display"), slideMin, tfMin);

			var slideMax = new Slider(0, maxDisplay.get(), maxDisplay.get());
			slideMax.valueProperty().bindBidirectional(maxDisplay);
			initializeSliderSnapping(slideMax, 1, 1, 0.1);
			slideMax.disableProperty().bind(autoUpdateDisplayRange.or(displayAdjustable.not()));
			var tfMax = new TextField();
			GuiTools.bindSliderAndTextField(slideMax, tfMax, expandSliderLimits);
			GuiTools.installRangePrompt(slideMax);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the last entry in the colormap.", new Label("Max display"), slideMax, tfMax);

			var sliderMinCount = new Slider(0, 1000, minCount.get());
			sliderMinCount.valueProperty().bindBidirectional(minCount);
			initializeSliderSnapping(sliderMinCount, 50, 1, 1);
			var tfMinCount = new TextField();
			GuiTools.bindSliderAndTextField(sliderMinCount, tfMinCount, expandSliderLimits, 0);
			GuiTools.installRangePrompt(sliderMinCount);
			PaneTools.addGridRow(pane, row++, 0, "Select minimum density of objects required for display in the density map.\n"
					+ "This is used to avoid isolated detections dominating the map (i.e. lower density regions can be shown as transparent).",
					new Label("Min density"), sliderMinCount, tfMinCount);

			var sliderGamma = new Slider(0, 4, gamma.get());
			sliderGamma.valueProperty().bindBidirectional(gamma);
			initializeSliderSnapping(sliderGamma, 0.1, 1, 0.1);
			var tfGamma = new TextField();
			GuiTools.bindSliderAndTextField(sliderGamma, tfGamma, false, 1);
			PaneTools.addGridRow(pane, row++, 0, "Control how the opacity of the density map changes between low & high values.\n"
					+ "Choose zero for an opaque map.", new Label("Gamma"), sliderGamma, tfGamma);
			
//			var cbAutoUpdateDisplayRange = new CheckBox("Use full display range");
//			cbAutoUpdateDisplayRange.selectedProperty().bindBidirectional(autoUpdateDisplayRange);
//			PaneTools.addGridRow(pane, row++, 0, "Automatically set the minimum & maximum display range for the colormap.", 
//					cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange);
//			cbAutoUpdateDisplayRange.disableProperty().bind(displayAdjustable.not());
//			cbAutoUpdateDisplayRange.setPadding(new Insets(0, 0, 10, 0));
			
			
			var btnAutoUpdate = new ToggleButton("Auto-update");
			btnAutoUpdate.setSelected(autoUpdate.get());
			btnAutoUpdate.selectedProperty().bindBidirectional(autoUpdate);
			
			PaneTools.addGridRow(pane, row++, 0, "Automatically update the heatmap. Turn this off if changing parameters and heatmap generation is slow.", btnAutoUpdate, btnAutoUpdate, btnAutoUpdate);
			
//			var btnRefresh = new Button("Refresh density maps");
//			btnRefresh.setOnAction(e -> requestUpdate(true));
//			PaneTools.addGridRow(pane, row++, 0,
//					"Synchronize the density maps with the current object data. \n"
//					+ "Press this button when something changes (e.g. objects added/removed), or you just want to ensure everything is"
//					+ "up-to-date.",
//					btnRefresh, btnRefresh, btnRefresh);
			
			double tfw = 80;
			tfRadius.setMaxWidth(tfw);
			tfGamma.setMaxWidth(tfw);
			tfMin.setMaxWidth(tfw);
			tfMax.setMaxWidth(tfw);
			tfMinCount.setMaxWidth(tfw);
			
			comboPrimary.setButtonCell(GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.setCellFactory(c -> GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.getSelectionModel().selectFirst();

			refresh();
						
			densityType.addListener((v, o, n) -> requestAutoUpdate(true));
			selectedClass.addListener((v, o, n) -> requestAutoUpdate(true));
			colorMap.addListener((v, o, n) -> requestAutoUpdate(false));
			radius.addListener((v, o, n) -> requestAutoUpdate(true));
			gaussianFilter.addListener((v, o, n) -> requestAutoUpdate(true));
			minCount.addListener((v, o, n) -> requestAutoUpdate(false));
			gamma.addListener((v, o, n) -> requestAutoUpdate(false));
			minDisplay.addListener((v, o, n) -> requestAutoUpdate(false));
			maxDisplay.addListener((v, o, n) -> requestAutoUpdate(false));
			interpolation.addListener((v, o, n) -> updateInterpolation());
			autoUpdate.addListener((v, o, n) -> autoUpdateChanged(n));
			autoUpdateDisplayRange.addListener((v, o, n) -> requestAutoUpdate(false));
			
			var btnHotspots = new Button("Find hotspots");
			btnHotspots.setTooltip(new Tooltip("Find the hotspots in the density map with highest values"));
			btnHotspots.setOnAction(e -> promptToFindHotspots());
			
			var btnContours = new Button("Threshold regions");
			btnContours.setTooltip(new Tooltip("Threshold to identify high-density regions"));
			btnContours.setOnAction(e -> promptToTraceContours());
			
			var btnExport = new Button("Export map");
			btnExport.setTooltip(new Tooltip("Export the density map as an image"));
			btnExport.setOnAction(e -> promptToSaveImage());
			
			var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnContours, btnExport);
			buttonPane.setHgap(5);
			PaneTools.addGridRow(pane, row++, 0, null, buttonPane, buttonPane, buttonPane);
			PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport, btnContours);
			
			PaneTools.setToExpandGridPaneWidth(comboType, comboPrimary, comboColorMap, comboInterpolation, btnAutoUpdate, sliderRadius, sliderGamma, buttonPane);
			
			pane.setHgap(5);
			pane.setVgap(5);
			pane.setPadding(new Insets(10));

			qupath.imageDataProperty().addListener(this);
			var imageData = qupath.getImageData();
			if (imageData != null)
				imageData.getHierarchy().addPathObjectListener(this);
			
			stage = new Stage();
			stage.setScene(new Scene(pane));
			stage.initOwner(qupath.getStage());
			stage.setTitle("Density map");
			stage.setOnCloseRequest(e -> deregister());
		}
		
		
		/**
		 * Update default parameters with a specified ImageData.
		 * This gives better starting values.
		 * @param imageData
		 */
		boolean updateDefaults(ImageData<BufferedImage> imageData) {
			if (imageData == null)
				return false;
			var server = imageData.getServer();
			double pixelSize = Math.round(server.getPixelCalibration().getAveragedPixelSize().doubleValue() * 10);
			pixelSize *= 100;
//			if (server.nResolutions() > 1)
//				pixelSize *= 10;
			pixelSize = Math.min(pixelSize, Math.min(server.getHeight(), server.getWidth())/20.0);
			radius.set(pixelSize);
			return true;
		}
		
		
		private void initializeSliderSnapping(Slider slider, double blockIncrement, double majorTicks, double minorTicks) {
			slider.setBlockIncrement(blockIncrement);
			slider.setMajorTickUnit(majorTicks);
			slider.setMinorTickCount((int)Math.round(majorTicks / minorTicks) - 1);
			slider.setSnapToTicks(true);
		}
		
		
		private String classificationText(PathClass pathClass) {
			if (PathClassTools.isValidClass(pathClass))
				return pathClass.toString();
			else
				return "Any";
		}

		
		private void deregister() {
			dialog = null;
			var currentImageData = qupath.getImageData();
			if (currentImageData != null)
				currentImageData.getHierarchy().removePathObjectListener(this);
			qupath.imageDataProperty().removeListener(this);
			for (var viewer : qupath.getViewers()) {
				var hierarchy = viewer.getHierarchy();
				if (hierarchy != null)
					hierarchy.removePathObjectListener(this);
//				viewer.imageDataProperty().removeListener(this);
				if (viewer.getCustomPixelLayerOverlay() == overlayMap.getOrDefault(viewer, null))
					viewer.resetCustomPixelLayerOverlay();
			}
		}
		
		public void refresh() {
			comboColorMap.getItems().setAll(ColorMaps.getColorMaps().values());
			if (comboColorMap.getSelectionModel().getSelectedItem() == null)
				comboColorMap.getSelectionModel().selectFirst();
			requestUpdate(true);
		}
		
		public Stage getStage() {
			return stage;
		}
		
		private void autoUpdateChanged(boolean doAutoUpdate) {
			if (doAutoUpdate) {
				requestAutoUpdate(true);
			}
		}
		
		/**
		 * Request that the density maps and/or overlays are updated.
		 * Note that this only has an effect if autoUpdate is turned on.
		 * @param fullUpdate if true, recalculate the density map entirely
		 */
		private void requestAutoUpdate(boolean fullUpdate) {
			if (!autoUpdate.get())
				return;
			requestUpdate(fullUpdate);
		}
		
		/**
		 * Request that the density maps and/or overlays are updated.
		 * @param fullUpdate if true, recalculate the density map entirely
		 */
		private void requestUpdate(boolean fullUpdate) {
			if (currentTask != null)
				currentTask.cancel(true);
			if (fullUpdate) {
				densityMapMap.clear();
				densityMapRanges.clear();
			}
			
			var executor = qupath.createSingleThreadExecutor(this);
			currentTask = executor.submit(() -> {
				for (var viewer : qupath.getViewers()) {
					if (Thread.currentThread().isInterrupted())
						return;
					updateHeatmap(viewer);
				}
			});
		}
		
		
		
		private ParameterList paramsHotspots = new ParameterList()
				.addIntParameter("nHotspots", "Number of hotspots to find", 1, null, "Specify the number of hotspots to identify; hotspots are peaks in the density map")
				.addBooleanParameter("allowOverlaps", "Allow overlapping hotspots", false, "Allow hotspots to overlap; if false, peaks are discarded if the hotspot radius overlaps with a 'hotter' hotspot")
				.addBooleanParameter("deletePrevious", "Delete existing hotspots", true, "Delete existing hotspot annotations with the same classification")
				;
		
		
		public void promptToFindHotspots() {
			
			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var densityMap = densityMapMap.get(viewer);
			if (imageData == null || densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}
			
			if (!Dialogs.showParameterDialog(title, paramsHotspots))
				return;
			
			int n = paramsHotspots.getIntParameterValue("nHotspots");
			boolean allowOverlapping = paramsHotspots.getBooleanParameterValue("allowOverlaps");
			boolean deleteExisting = paramsHotspots.getBooleanParameterValue("deletePrevious");
			
//			var response = Dialogs.showInputDialog(title, "How many hotspots do you want to find?", (double)lastHotspotCount);
//			boolean allowOverlapping = false;
//			if (response == null)
//				return;
//			int n = response.intValue();
//			lastHotspotCount = n;
			
			PathClass hotspotClass = DensityMaps.getHotspotClass(selectedClass.getValue());
			
			var hierarchy = imageData.getHierarchy();
			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());
			
			// Remove existing hotspots with the same classification
			if (deleteExisting) {
				var hotspotClass2 = hotspotClass;
				var existing = hierarchy.getAnnotationObjects().stream()
						.filter(a -> a.getPathClass() == hotspotClass2)
						.collect(Collectors.toList());
				hierarchy.removeObjects(existing, true);
			}

			
			try {
				DensityMaps.findHotspots(hierarchy, densityMap, 0, selected, n, radius.get(), minCount.get(), allowOverlapping, hotspotClass);
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		
		
		private void updateInterpolation() {
			for (var viewer : qupath.getViewers()) {
				var overlay = overlayMap.getOrDefault(viewer, null);
				if (overlay != null) {
					if (overlay instanceof BufferedImageOverlay)
						((BufferedImageOverlay)overlay).setInterpolation(interpolation.getValue());
					viewer.repaint();
				}
			}
		}
		
		
		private ParameterList paramsTracing = new ParameterList()
				.addDoubleParameter("threshold", "Density threshold", 0.5, null, "Define the density threshold to detection regions")
				.addBooleanParameter("split", "Split regions", false, "Split disconnected regions into separate annotations");

		
		public void promptToTraceContours() {

			var viewer = qupath.getViewer();
			var densityMap = densityMapMap.getOrDefault(viewer, null);

			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			
			var imageData = viewer.getImageData();
			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image available!");
				return;
			}
			
			if (!Dialogs.showParameterDialog("Trace contours from density map", paramsTracing))
				return;
			
			var threshold = paramsTracing.getDoubleParameterValue("threshold");
			boolean doSplit = paramsTracing.getBooleanParameterValue("split");
			
			var hierarchy = imageData.getHierarchy();
			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());
			
			DensityMaps.traceContours(hierarchy, densityMap, 0, selected, threshold, doSplit, DensityMaps.getHotspotClass(selectedClass.getValue()));
		}
		
		

		
		public void promptToSaveImage() {

			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var densityMap = densityMapMap.getOrDefault(viewer, null);
			var overlay = overlayMap.getOrDefault(viewer, null);
			
			if (imageData == null || densityMap == null || overlay == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			
			var dialog = new Dialog<ButtonType>();
			dialog.setTitle(title);
			dialog.setHeaderText("How do you want to export the density map?");
			dialog.setContentText("Choose 'Raw values' of 'Send to ImageJ' if you need the original counts, or 'Color image' it you want to keep the same visual appearance.");
			var btOrig = new ButtonType("Raw values");
			var btColor = new ButtonType("Color image");
			var btImageJ = new ButtonType("Send to ImageJ");
			dialog.getDialogPane().getButtonTypes().setAll(btOrig, btColor, btImageJ, ButtonType.CANCEL);
			
			var response = dialog.showAndWait().orElse(ButtonType.CANCEL);
			try {
				if (btOrig.equals(response)) {
					promptToSaveRawImage(densityMap);
				} else if (btColor.equals(response)) {
					if (overlay instanceof BufferedImageOverlay)
						promptToSaveColorImage((BufferedImageOverlay)overlay);
					else
						Dialogs.showErrorMessage(title, "Not implemented yet!");
				} else if (btImageJ.equals(response)) {
					sendToImageJ(densityMap);
				}
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		public void promptToSaveRawImage(DensityMapImageServer densityMap) throws IOException {
			var file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
			if (file != null)
				QP.writeImage(densityMap, file.getAbsolutePath());
		}

		public void promptToSaveColorImage(BufferedImageOverlay overlay) {
			Dialogs.showErrorMessage(title, "Not implemented yet!");
			var img = overlay.getRegionMap().values().iterator().next();
			var file = Dialogs.promptToSaveFile(title, null, null, "PNG", ".png");
			if (file != null) {
				try {
					ImageIO.write(img, "PNG", file);
				} catch (IOException e) {
					Dialogs.showErrorMessage(title, "Unable to write file: " + e.getLocalizedMessage());
					logger.error(e.getLocalizedMessage(), e);
				}
			}
		}

		public void sendToImageJ(DensityMapImageServer densityMap) throws IOException {
			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			IJExtension.getImageJInstance();
			var imp = IJTools.extractHyperstack(densityMap, null);
			if (imp instanceof CompositeImage)
				((CompositeImage)imp).resetDisplayRanges();
			imp.show();
		}

		
		private void updateHeatmap(QuPathViewer viewer) {
			PathOverlay overlay;
			// Get a cached density map if we can
			var densityMap = densityMapMap.getOrDefault(viewer, null);
			try {
				// Generate a new density map if we need to
				var imageData = viewer.getImageData();
				if (densityMap == null) {
					if (imageData == null)
						densityMap = null;
					else
						densityMap = calculateDensityMap(viewer.getImageData());
				}
				// Don't update anything if we are interrupted - that can cause flickering of the display
				if (Thread.currentThread().isInterrupted())
					return;
				// Create a new overlay (or null if we have no density map)
				overlay = densityMap == null ? null : createOverlay(viewer, densityMap);
				if (Thread.currentThread().isInterrupted())
					return;
				densityMapMap.put(viewer, densityMap);
				overlayMap.put(viewer, overlay);
			} catch (Exception e) {
				Dialogs.showErrorNotification(title, "Error creating density map: " + e.getLocalizedMessage());
				logger.error(e.getLocalizedMessage(), e);
				densityMapMap.remove(viewer);
				overlayMap.remove(viewer);
				overlay = null;
				densityMap = null;
			}
			
			if (Platform.isFxApplicationThread()) {
				viewer.setCustomPixelLayerOverlay(overlay);
			} else {
				var overlay2 = overlay;
				Platform.runLater(() -> viewer.setCustomPixelLayerOverlay(overlay2));
			}
		}
		
		
		
		private DensityMapImageServer calculateDensityMap(ImageData<BufferedImage> imageData) {
			var builder = calculateDensityMapBuilder();
			return builder.buildMap(imageData);
		}
		
		
		private DensityMapBuilder calculateDensityMapBuilder() {
			var builder = DensityMaps.builder();
			
			if (pixelSize.get() > 0)
				builder.pixelSize(pixelSize.get());
			
			builder.gaussianFilter(gaussianFilter.get());
			
			var mapType = densityType.getValue();
			var pathClass = comboPrimary.getSelectionModel().getSelectedItem();
			if (PathClassTools.isValidClass(pathClass)) {
				switch (mapType) {
				case DETECTIONS:
					builder.density(pathClass, !pathClass.isDerivedClass());
					break;
				case POINT_ANNOTATIONS:
					builder.pointAnnotations(pathClass, !pathClass.isDerivedClass());
					break;
				case POSITIVE_DETECTIONS:
					builder.positiveDetections(pathClass);
					break;
				default:
					throw new IllegalArgumentException("Unknown density map type " + mapType);
				}
			} else {
				switch (mapType) {
				case DETECTIONS:
					builder.density(PathObjectFilter.DETECTIONS_ALL);
					break;
				case POINT_ANNOTATIONS:
					builder.density(PathObjectFilter.ANNOTATIONS.and(PathObjectFilter.ROI_POINT));
					break;
				case POSITIVE_DETECTIONS:
					builder.positiveDetections();
					break;
				default:
					throw new IllegalArgumentException("Unknown density map type " + mapType);
				}
			}
//			builder.pixelSize(10);
			
			builder.radius(radius.get());
			return builder;
		}
		
		
		private PathOverlay createOverlay(QuPathViewer viewer, DensityMapImageServer map) throws IOException {
//			requestQuickUpdate = false;
			
			if (map == null)
				return null;
			
			var colorMap = comboColorMap.getSelectionModel().getSelectedItem();
			
			var request = RegionRequest.createInstance(map);
			var img = map.readBufferedImage(request);
			if (img == null)
				return null;
			
			var min = minDisplay.get();
			var max = maxDisplay.get();
			var minCount = this.minCount.get();

			// If the last channel is 'counts', then it is used for normalization
			int alphaCountBand = -1;
			if (map.getChannel(map.nChannels()-1).getName().equals("Counts"))
				alphaCountBand = map.nChannels()-1;
			
			String key = map.getPath() + "?countBand=" + alphaCountBand + "&minCount=" + minCount;
			
			var minMaxList = densityMapRanges.get(key);
			if (minMaxList == null) {
				minMaxList = getMinMax(map, alphaCountBand, (float)minCount);
				if (minMaxList == null)
					return null;
				densityMapRanges.put(key, minMaxList);
			}
			var alphaCountMinMax = alphaCountBand > 0 ? minMaxList.get(alphaCountBand) : null;
			
			if (Thread.currentThread().isInterrupted())
				return null;
			
			// Calculate the min/max values if needed; these define the bounds for the colormap
			// Use all channels, unless one is being used for masking
			if (autoUpdateDisplayRange.get()) {
				// TODO: Check whether or not to always drop the count band
				min = 0;//minMaxList.stream().filter(m -> m != alphaCountMinMax).mapToDouble(m -> m.minValue).min().orElse(Double.NaN);
				max = minMaxList.stream().filter(m -> m != alphaCountMinMax).mapToDouble(m -> m.maxValue).max().orElse(Double.NaN);
				if (Double.isFinite(min) && Double.isFinite(max)) {
					double min2 = min;
					double max2 = max;
					Platform.runLater(() -> {
						minDisplay.set(min2);
						maxDisplay.set(max2);
					});
				}
			}
			
			var g = gamma.get();
			DoubleToIntFunction alphaFun = null;
			
			// Everything <= minCount should be transparent
			// Everything else should derive opacity from the alpha value
			if (minCount > 0) {
				if (alphaCountBand < 0) {
					if (g <= 0) {
						alphaFun = ColorModelFactory.createLinearFunction(minCount, minCount+1);
					} else {
						alphaFun = ColorModelFactory.createGammaFunction(g, minCount, max);
					}
				}
			}

			if (alphaCountMinMax == null) {
				if (g <= 0) {
					alphaFun = null;
					alphaCountBand = -1;
				} else {
					alphaFun = ColorModelFactory.createGammaFunction(g, Math.max(0, minCount), max);
					alphaCountBand = 0;
				}
			} else if (alphaFun == null) {
				if (g <= 0)
					alphaFun = ColorModelFactory.createLinearFunction(Math.max(0, minCount), Math.max(0, minCount)+1);
				else if (minCount >= alphaCountMinMax.maxValue)
					alphaFun = v -> 0;
				else
					alphaFun = ColorModelFactory.createGammaFunction(g, Math.max(0, minCount), alphaCountMinMax.maxValue);
			}
			
			var cm = ColorModelFactory.createColorModel(PixelType.FLOAT32, colorMap, 0, min, max, alphaCountBand, alphaFun);
			
			var tiles = getAllTiles(map, 0, false);
			if (tiles == null)
				return null;
			
			var overlay = new BufferedImageOverlay(viewer, viewer.getOverlayOptions(), tiles);
			overlay.setInterpolation(comboInterpolation.getSelectionModel().getSelectedItem());
			overlay.setColorModel(cm);
			return overlay;
		}
		
		

		/**
		 * Listen for changes to the currently open image
		 */
		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
				ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
			if (oldValue != null)
				oldValue.getHierarchy().removePathObjectListener(this);
			if (newValue != null) {
				newValue.getHierarchy().addPathObjectListener(this);
				requestAutoUpdate(true);
			}
		}


		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (event.isChanging())
				return;
			requestAutoUpdate(true);
		}
		
		
	}
	
	static class MinMax {
		
		private float minValue = Float.POSITIVE_INFINITY;
		private float maxValue = Float.NEGATIVE_INFINITY;
		
		void update(float val) {
			if (Float.isNaN(val))
				return;
			if (val < minValue)
				minValue = val;
			if (val > maxValue)
				maxValue = val;
		}
		
	}
	
	/**
	 * Get the minimum and maximum values for all pixels across all channels of an image.
	 * 
	 * @param server server containing pixels
	 * @param countBand optional band that can be thresholded and used for masking; if -1, then the same band is used for counts
	 * @param minCount minimum value for pixels to be included
	 * @return
	 * @throws IOException 
	 */
	private static List<MinMax> getMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
		var tiles = getAllTiles(server, 0, false);
		if (tiles == null)
			return null;
		// Sometimes we use the
		boolean countsFromSameBand = countBand < 0;
		int nBands = server.nChannels();
		List<MinMax> results = IntStream.range(0, nBands).mapToObj(i -> new MinMax()).collect(Collectors.toList());
		float[] pixels = null;
		float[] countPixels = null;
		for (var img : tiles.values()) {
			var raster = img.getRaster();
			int w = raster.getWidth();
			int h = raster.getHeight();
			if (pixels == null || pixels.length < w*h) {
				pixels = new float[w*h];
				if (!countsFromSameBand)
					countPixels = new float[w*h];
			}
			countPixels = !countsFromSameBand ? raster.getSamples(0, 0, w, h, countBand, countPixels) : null;
			for (int band = 0; band < nBands; band++) {
				var minMax = results.get(band);
				pixels = raster.getSamples(0, 0, w, h, band, pixels);
				if (countsFromSameBand) {
					for (int i = 0; i < w*h; i++) {
						if (pixels[i] >= minCount)
							minMax.update(pixels[i]);
					}					
				} else {
					for (int i = 0; i < w*h; i++) {
						if (countPixels[i] >= minCount)
							minMax.update(pixels[i]);
					}
				}
			}
		}
		return Collections.unmodifiableList(results);
	}
	
	private static Map<RegionRequest, BufferedImage> getAllTiles(ImageServer<BufferedImage> server, int level, boolean ignoreInterrupts) throws IOException {
		Map<RegionRequest, BufferedImage> map = new LinkedHashMap<>();
		var tiles = server.getTileRequestManager().getTileRequestsForLevel(level);
    	for (var tile : tiles) {
    		if (!ignoreInterrupts && Thread.currentThread().isInterrupted())
    			return null;
    		var region = tile.getRegionRequest();
    		if (server.isEmptyRegion(region))
    			continue;
    		var imgTile = server.readBufferedImage(region);
    		if (imgTile != null)
    			map.put(region, imgTile);
    	}
    	return map;
	}
	

}
