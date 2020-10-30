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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.function.DoubleToIntFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.MaximumFinder;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
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
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.algorithms.ContourTracing;
import qupath.lib.analysis.heatmaps.DensityMap;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;


/**
 * Command for generating density maps from detections on an image.
 * 
 * @author Pete Bankhead
 */
public class DensityMapCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapCommand.class);
	
	private static final PathClass DEFAULT_HOTSPOT_CLASS = PathClassFactory.getPathClass("Hotspot", ColorTools.makeRGB(200, 120, 20));
	
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
		}
		
		var stage = dialog.getStage();
		stage.setOnCloseRequest(e -> dialog.deregister());
		stage.show();
	}
	
	
	private static enum DensityMapType {
		
		DETECTIONS_CLASSIFIED,
		POSITIVE_PERCENTAGE,
		POINT_ANNOTATIONS;
		
		@Override
		public String toString() {
			switch(this) {
			case DETECTIONS_CLASSIFIED:
				return "Detections";
			case POSITIVE_PERCENTAGE:
				return "Positive %";
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
		private ObservableValue<DensityMapType> densityType = comboType.getSelectionModel().selectedItemProperty();
		
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

		private DoubleProperty minDisplay = new SimpleDoubleProperty(0);
		private DoubleProperty maxDisplay = new SimpleDoubleProperty(100);
		private BooleanProperty autoUpdateDisplayRange = new SimpleBooleanProperty(true);
		
		/**
		 * Automatically update the density maps and overlays.
		 */
		private BooleanProperty autoUpdate = new SimpleBooleanProperty(true);
		
		private Future<?> currentTask;
		
		private Map<QuPathViewer, DensityMap> densityMapMap = new WeakHashMap<>();
		private Map<QuPathViewer, BufferedImageOverlay> overlayMap = new WeakHashMap<>();
		
		public DensityMapDialog() {
			
			comboType.getItems().setAll(DensityMapType.values());
			comboType.getSelectionModel().select(DensityMapType.DETECTIONS_CLASSIFIED);
			
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
					+ "Use 'Detections' to look for the density of all detections with any classication.\n"
					+ "Use 'Positive %' specifically if your detections are classified as positive & negative and you want to find a high density that is positive.\n"
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
			
			
			var sliderMinCount = new Slider(0, 1000, minCount.get());
			sliderMinCount.valueProperty().bindBidirectional(minCount);
			initializeSliderSnapping(sliderMinCount, 50, 1, 1);
			var tfMinCount = new TextField();
			GuiTools.bindSliderAndTextField(sliderMinCount, tfMinCount, expandSliderLimits, 0);
			GuiTools.installRangePrompt(sliderMinCount);
			PaneTools.addGridRow(pane, row++, 0, "Select minimum count of objects required for display in the density map.\n"
					+ "This is used to avoid isolated detections dominating the map (i.e. lower density regions can be shown as transparent).",
					new Label("Minimum count"), sliderMinCount, tfMinCount);
			
			
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
			slideMin.disableProperty().bind(autoUpdateDisplayRange);
			var tfMin = new TextField();
			GuiTools.bindSliderAndTextField(slideMin, tfMin, expandSliderLimits);
			GuiTools.installRangePrompt(slideMin);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the first entry in the colormap.", new Label("Min display"), slideMin, tfMin);

			var slideMax = new Slider(0, maxDisplay.get(), maxDisplay.get());
			slideMax.valueProperty().bindBidirectional(maxDisplay);
			initializeSliderSnapping(slideMax, 1, 1, 0.1);
			slideMax.disableProperty().bind(autoUpdateDisplayRange);
			var tfMax = new TextField();
			GuiTools.bindSliderAndTextField(slideMax, tfMax, expandSliderLimits);
			GuiTools.installRangePrompt(slideMax);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the last entry in the colormap.", new Label("Max display"), slideMax, tfMax);

			var sliderGamma = new Slider(0, 4, gamma.get());
			sliderGamma.valueProperty().bindBidirectional(gamma);
			initializeSliderSnapping(sliderGamma, 0.1, 1, 0.1);
			var tfGamma = new TextField();
			GuiTools.bindSliderAndTextField(sliderGamma, tfGamma, false, 1);
			PaneTools.addGridRow(pane, row++, 0, "Control how the opacity of the density map changes between low & high values.\n"
					+ "Choose zero for an opaque map.", new Label("Gamma"), sliderGamma, tfGamma);
			
			var cbAutoUpdateDisplayRange = new CheckBox("Use full display range");
			cbAutoUpdateDisplayRange.selectedProperty().bindBidirectional(autoUpdateDisplayRange);
			PaneTools.addGridRow(pane, row++, 0, "Automatically set the minimum & maximum display range for the colormap.", 
					cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange);
			cbAutoUpdateDisplayRange.setPadding(new Insets(0, 0, 10, 0));
			
			
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
			
			stage = new Stage();
			stage.setScene(new Scene(pane));
			stage.initOwner(qupath.getStage());
			stage.setTitle("Density map");
			stage.setOnCloseRequest(e -> deregister());
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
			if (fullUpdate)
				densityMapMap.clear();
			
			var executor = qupath.createSingleThreadExecutor(this);
			currentTask = executor.submit(() -> {
				for (var viewer : qupath.getViewers()) {
					if (Thread.currentThread().isInterrupted())
						return;
					updateHeatmap(viewer);
				}
			});
		}
		
		public void promptToFindHotspots() {
			
			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var densityMap = densityMapMap.get(viewer);
			if (imageData == null || densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}
			
			var response = Dialogs.showInputDialog(title, "How many hotspots do you want to find?", 1.0);
			boolean allowOverlapping = false;
			if (response == null)
				return;
			
			int n = response.intValue();
			
			PathClass hotspotClass = DEFAULT_HOTSPOT_CLASS;
			PathClass baseClass = selectedClass.getValue();
			if (PathClassTools.isValidClass(baseClass)) {
				hotspotClass = PathClassTools.mergeClasses(baseClass, hotspotClass);
			}
			
			double minCount = this.minCount.get();
			SimpleImage mask = null;
			if (minCount > 0) {
				if (densityMap.getAlpha() == null)
					mask = threshold(densityMap.getValues(), minCount, 0, 1, 1);
				else
					mask = threshold(densityMap.getAlpha(), minCount, 0, 1, 1);
			}
			
			var finder = new PeakFinder(densityMap.getValues())
					.region(densityMap.getRegion())
					.calibration(imageData.getServer().getPixelCalibration())
					.peakClass(hotspotClass)
					.minimumSeparation(allowOverlapping ? -1 : radius.get() * 2)
					.withinROI(true)
					.radius(radius.get());
			
			if (mask != null)
				finder.mask(mask);

			var hierarchy = imageData.getHierarchy();
			
			// Remove existing hotspots with the same classification
			var existing = hierarchy.getAnnotationObjects().stream().filter(a -> a.getPathClass() == finder.pathClass).collect(Collectors.toList());
			hierarchy.removeObjects(existing, true);

			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());

			for (var parent : selected) {
				var hotspots = finder.createObjects(parent.getROI(), n);
				parent.addPathObjects(hotspots);
			}
			
			hierarchy.fireHierarchyChangedEvent(finder);
		}
		
		
		
		private void updateInterpolation() {
			for (var viewer : qupath.getViewers()) {
				var overlay = overlayMap.getOrDefault(viewer, null);
				if (overlay != null) {
					overlay.setInterpolation(interpolation.getValue());
					viewer.repaint();
				}
			}
		}
		
		
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
			
			var params = new ParameterList()
					.addDoubleParameter("threshold", "Density threshold", maxDisplay.get(), null, "Define the density threshold to detection regions")
					.addBooleanParameter("split", "Split regions", false, "Split disconnected regions into separate annotations")
//					.addBooleanParameter("erode", "Erode by radius", false, "Erode ROIs by the density radius")
					;
			
			if (!Dialogs.showParameterDialog("Trace contours from density map", params))
				return;
			
			var image = densityMap.getValues();
			var threshold = params.getDoubleParameterValue("threshold");
			boolean doSplit = params.getBooleanParameterValue("split");
			var region = densityMap.getRegion();
//			boolean doErode = params.getBooleanParameterValue("erode");
			var geometry = ContourTracing.createTracedGeometry(image, threshold, Double.POSITIVE_INFINITY, region);
			if (geometry == null || geometry.isEmpty()) {
				Dialogs.showWarningNotification(title, "No regions found!");
				return;
			}
			
			// Get the selected objects
			var hierarchy = imageData.getHierarchy();
			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());
			
			// Get the class for hotspot
			// TODO: Change hotspot class name, and generate class with another method
			var baseClass = selectedClass.getValue();
			PathClass hotspotClass = DEFAULT_HOTSPOT_CLASS;
			if (PathClassTools.isValidClass(baseClass)) {
				hotspotClass = PathClassTools.mergeClasses(baseClass, hotspotClass);
			}
			
			boolean changes = false;
			for (var parent : selected) {
				var annotations = new ArrayList<PathObject>();
				var roiParent = parent.getROI();
				Geometry geomNew = null;
				if (roiParent == null)
					geomNew = geometry;
				else
					geomNew = GeometryTools.ensurePolygonal(geometry.intersection(roiParent.getGeometry()));
				if (geomNew.isEmpty())
					continue;
				
				var roi = GeometryTools.geometryToROI(geomNew, region == null ? ImagePlane.getDefaultPlane() : region.getPlane());
					
				if (doSplit) {
					for (var r : RoiTools.splitROI(roi))
						annotations.add(PathObjects.createAnnotationObject(r, hotspotClass));
				} else
					annotations.add(PathObjects.createAnnotationObject(roi, hotspotClass));
				parent.addPathObjects(annotations);
				changes = true;
			}
			
			if (changes)
				hierarchy.fireHierarchyChangedEvent(this);
			else
				logger.warn("No thresholded hotspots found!");
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
			if (btOrig.equals(response)) {
				promptToSaveRawImage(densityMap, imageData.getServer());
			} else if (btColor.equals(response)) {
				promptToSaveColorImage(overlay);
			} else if (btImageJ.equals(response)) {
				sendToImageJ(densityMap, imageData.getServer());
			}
		}
		
		public void promptToSaveRawImage(DensityMap densityMap, ImageServer<BufferedImage> server) {
			var imp = convertToImagePlus(densityMap, server);
			var file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
			if (file != null)
				IJ.save(imp, file.getAbsolutePath());
		}

		public void promptToSaveColorImage(BufferedImageOverlay overlay) {
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

		public void sendToImageJ(DensityMap densityMap, ImageServer<BufferedImage> server) {
			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			convertToImagePlus(densityMap, server).show();
		}

		
		private void updateHeatmap(QuPathViewer viewer) {
			BufferedImageOverlay overlay;
			DensityMap densityMap = densityMapMap.getOrDefault(viewer, null);
			try {
				if (densityMap == null) {
					densityMap = calculateDensityMap(viewer);
				}
				densityMapMap.put(viewer, densityMap);
				overlay = createOverlay(viewer, densityMap);
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
		
				
		
		private DensityMap calculateDensityMap(QuPathViewer viewer) {
//			requestFullUpdate = false;
			
			var imageData = viewer.getImageData();
			if (imageData == null) {
//				Dialogs.showErrorMessage("Density map", "No image available!");
				return null;
			}
			var builder = new DensityMap.HeatmapBuilder();
			
			if (pixelSize.get() > 0)
				builder.pixelSize(pixelSize.get());
			
			var mapType = densityType.getValue();
			var pathClass = comboPrimary.getSelectionModel().getSelectedItem();
			if (PathClassTools.isValidClass(pathClass)) {
				switch (mapType) {
				case DETECTIONS_CLASSIFIED:
					builder.density(pathClass, !pathClass.isDerivedClass());
					break;
				case POINT_ANNOTATIONS:
					builder.pointAnnotations(pathClass, !pathClass.isDerivedClass());
					break;
				case POSITIVE_PERCENTAGE:
					builder.positivePercentage(pathClass);
					break;
				default:
					throw new IllegalArgumentException("Unknown density map type " + mapType);
				}
			} else {
				switch (mapType) {
				case DETECTIONS_CLASSIFIED:
					builder.density(PathObjectFilter.DETECTIONS_ALL);
					break;
				case POINT_ANNOTATIONS:
					builder.density(PathObjectFilter.ANNOTATIONS.and(PathObjectFilter.ROI_POINT));
					break;
				case POSITIVE_PERCENTAGE:
					builder.positivePercentage();
					break;
				default:
					throw new IllegalArgumentException("Unknown density map type " + mapType);
				}
			}
			builder.pixelSize(10);
			
			builder.radius(radius.get());
			
			var map = builder.build(viewer.getImageData());
			
			
			return map;
		}
		
		private BufferedImageOverlay createOverlay(QuPathViewer viewer, DensityMap map) {
//			requestQuickUpdate = false;
			
			if (map == null)
				return null;
			
			var colorMap = comboColorMap.getSelectionModel().getSelectedItem();
			var image = map.getValues();
			var counts = map.getAlpha();
			if (counts == null)
				counts = image;
			
			var min = minDisplay.get();
			var max = maxDisplay.get();
			var minCount = this.minCount.get();

			if (autoUpdateDisplayRange.get()) {
				float[] pixels = null;
				if (minCount <= 0) {
					pixels = SimpleImages.getPixels(image, true);
				} else {
					int w = counts.getWidth();
					int h = counts.getHeight();
					pixels = new float[w * h];
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							if (counts.getValue(x, y) >= minCount)
								pixels[y*w+x] = image.getValue(x, y);
							else
								pixels[y*w+x] = Float.NaN;
						}
					}
					SimpleImages.getPixels(image, true);					
				}
				var minMax = findMinAndMax(pixels);
				min = minMax.minVal;
				max = minMax.maxVal;
				if (minMax.minInd >= 0) {
					Platform.runLater(() -> {
						minDisplay.set(minMax.minVal);
						maxDisplay.set(minMax.maxVal);
					});
				}
			}
			
			var g = gamma.get();
			DoubleToIntFunction alphaFun = null;
			int alphaChannel = 1;
			var alpha = map.getAlpha();
			
			// Everything <= minCount should be transparent
			// Everything else should derive opacity from the alpha value
			
			if (minCount > 0) {
				if (alpha == null) {
					if (g <= 0) {
						alpha = threshold(image, minCount, 0, 1, 1);
						alphaFun = ColorModelFactory.createLinearFunction(0, 1);
					} else {
//						alphaFun = ColorModelFactory.createLinearFunction(minCount+1, max);
						alpha = image;
						alphaFun = ColorModelFactory.createGammaFunction(g, minCount, max);
						alphaChannel = 0;
					}
				}
			}

			if (alpha == null) {
				if (g <= 0) {
					alphaFun = null;
					alphaChannel = -1;
				} else {
					alphaFun = ColorModelFactory.createGammaFunction(g, 0, max);
					alphaChannel = 0;
				}
			} else if (alphaFun == null) {
				var alphaMinMax = findMinAndMax(SimpleImages.getPixels(alpha, true));
				if (g <= 0)
					alphaFun = ColorModelFactory.createLinearFunction(Math.max(0, minCount), Math.max(0, minCount)+1);
				else if (minCount >= alphaMinMax.maxVal)
					alphaFun = v -> 0;
				else
					alphaFun = ColorModelFactory.createGammaFunction(g, Math.max(0, minCount), alphaMinMax.maxVal);
			}
			
			var cm = ColorModelFactory.createColorModel(PixelType.FLOAT32, colorMap, min, max, alphaChannel, alphaFun);
			
			var raster = cm.createCompatibleWritableRaster(image.getWidth(), image.getHeight());
			raster.setSamples(0, 0, image.getWidth(), image.getHeight(), 0, SimpleImages.getPixels(map.getValues(), true));
			if (alpha != null && alphaChannel > 0)
				raster.setSamples(0, 0, image.getWidth(), image.getHeight(), 1, SimpleImages.getPixels(alpha, true));
			var img = new BufferedImage(cm, raster, false, null);
			
			img = BufferedImageTools.ensureBufferedImageType(img, BufferedImage.TYPE_INT_ARGB);
			
			var overlay = new BufferedImageOverlay(viewer, img);
			overlay.setInterpolation(comboInterpolation.getSelectionModel().getSelectedItem());
			return overlay;
		}

		/**
		 * Listen for changes to the 
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
	
	
	private static ImagePlus convertToImagePlus(DensityMap densityMap, ImageServer<BufferedImage> server) {
		var imgValues = densityMap.getValues();
		var fp = IJTools.convertToFloatProcessor(imgValues);
		var stack = new ImageStack(fp.getWidth(), fp.getHeight());
		stack.addSlice(fp);
		stack.setSliceLabel("Density", 1);
		var alpha = densityMap.getAlpha();
		if (alpha != null) {
			stack.addSlice(IJTools.convertToFloatProcessor(alpha));
			stack.setSliceLabel("Counts", 2);
		}
		var imp = new ImagePlus("Density map", stack);
		if (stack.getSize() > 1) {
			imp.setDimensions(stack.getSize(), 1, 1);
			imp = new CompositeImage(imp, CompositeImage.COLOR);
		}
		// Calibrate the image if we can
		if (server != null && densityMap.getRegion() != null)
			IJTools.calibrateImagePlus(imp, densityMap.getRegion(), server);
		return imp;
	}
	
	
	static SimpleImage threshold(SimpleImage image, double threshold, float below, float equals, float above) {
		int w = image.getWidth();
		int h = image.getHeight();
		float[] pixels = new float[w * h];
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				var v = image.getValue(x, y);
				if (v > threshold)
					pixels[i] = above;
				else if (v < threshold)
					pixels[i] = below;
				else
					pixels[i] = equals;
				i++;
			}
		}
		return SimpleImages.createFloatImage(pixels, w, h);
	}

	
	static class PointWithValue {
		
		public final double x;
		public final double y;
		public final double value;
		
		public PointWithValue(double x, double y, double value) {
			this.x = x;
			this.y = y;
			this.value = value;
		}
		
		public double distance(PointWithValue p) {
			double dx = x - p.x;
			double dy = y - p.y;
			return Math.sqrt(dx*dx + dy*dy);
		}
					
	}
	
	
	static class MinMaxLoc {
		
		public final int minInd;
		public final double minVal;
		
		public final int maxInd;
		public final double maxVal;
		
		public MinMaxLoc(int minInd, double minVal, int maxInd, double maxVal) {
			this.minInd = minInd;
			this.minVal = minVal;
			this.maxInd = maxInd;
			this.maxVal = maxVal;
		}
		
	}
	
	static MinMaxLoc findMinAndMax(float[] values) {
		int minInd = -1;
		int maxInd = -1;
		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		int i = 0;
		for (var f : values) {
			if (!Float.isFinite(f))
				continue;
			if (f < minVal) {
				minVal = f;
				minInd = i;
			}
			if (f > maxVal) {
				maxVal = f;
				maxInd = i;
			}
			i++;
		}
		return new MinMaxLoc(minInd, minVal, maxInd, maxVal);
	}
	
	static MinMaxLoc findMinAndMax(int[] values) {
		int minInd = -1;
		int maxInd = -1;
		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		int i = 0;
		for (var f : values) {
			if (f < minVal) {
				minVal = f;
				minInd = i;
			}
			if (f > maxVal) {
				maxVal = f;
				maxInd = i;
			}
			i++;
		}
		return new MinMaxLoc(minInd, minVal, maxInd, maxVal);
	}
	
	static MinMaxLoc findMinAndMax(double[] values) {
		int minInd = -1;
		int maxInd = -1;
		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		int i = 0;
		for (var f : values) {
			if (f < minVal) {
				minVal = f;
				minInd = i;
			}
			if (f > maxVal) {
				maxVal = f;
				maxInd = i;
			}
			i++;
		}
		return new MinMaxLoc(minInd, minVal, maxInd, maxVal);
	}
	
	
	/**
	 * Find peaks in 2D images.
	 * 
	 * @author Pete Bankhead
	 */
	static class PeakFinder {
		
		private final static Logger logger = LoggerFactory.getLogger(PeakFinder.class);
				
		private PixelCalibration cal = PixelCalibration.getDefaultInstance();
		
		private RegionRequest region;
		private SimpleImage imgValues;
		private SimpleImage imgMask;
		
		private boolean mergeROIs = false;
		private double minimumSeparation = -1;
		
		private boolean constrainWithinROI = true;
		
		private Function<ROI, PathObject> objectFun = r -> PathObjects.createAnnotationObject(r);
		
		private double radius;
		
		private PathClass pathClass = null;
		
		/**
		 * Create a new {@link PeakFinder} to identify peaks in the provided image.
		 * @param imgValues
		 */
		public PeakFinder(SimpleImage imgValues) {
			this.imgValues = imgValues;
		}
		
		/**
		 * Define region for the image. This can be used to adjust the coordinates of any 
		 * identified ROIs to match the full image space.
		 * @param region
		 * @return this finder
		 */
		public PeakFinder region(RegionRequest region) {
			this.region = region;
			return this;
		}
		
//		public HotspotFinder values(SimpleImage imgValues) {
//			this.imgValues = imgValues;
//			return this;
//		}
		
		/**
		 * Define a mask; hotspots will not be found where pixel values are 0.
		 * @param imgMask
		 * @return this finder
		 */
		public PeakFinder mask(SimpleImage imgMask) {
			this.imgMask = imgMask;
			return this;
		}
		
		/**
		 * Optionally merge detected ROIs. This is most useful when radius == 0, to control whether 
		 * a multipoint ROI is created or multiple single-point ROIs.
		 * @param doMerge
		 * @return this finder
		 */
		public PeakFinder mergeROIs(boolean doMerge) {
			this.mergeROIs = doMerge;
			return this;
		}
		
		/**
		 * Define the function that creates objects for {@link #createObjects(ROI, int)}.
		 * Default is to create annotation objects; this method may be used to create detections (or other objects) instead.
		 * @param objectFun
		 * @return this finder
		 */
		public PeakFinder objectCreator(Function<ROI, PathObject> objectFun) {
			this.objectFun = objectFun;
			return this;
		}
		
		/**
		 * Define the pixel width and height in (unspecified) calibrated units.
		 * This relates to the full-resolution image; if a region with downsample is defined, the pixel size 
		 * will be scaled accordingly.
		 * @param pixelSize
		 * @return this finder
		 */
		public PeakFinder pixelSize(double pixelSize) {
			return calibration(PixelCalibration.getDefaultInstance().createScaledInstance(pixelSize, pixelSize));
		}
		
		/**
		 * Define the hotspot radius in calibrated units. If &le; 0, point ROIs will be created. Otherwise, ellipse ROIs will be created.
		 * @param radius
		 * @return this finder
		 */
		public PeakFinder radius(double radius) {
			if (!Double.isFinite(radius) || radius < 0) {
				logger.warn("Invalid radius {}, will use 0 instead", radius);
				this.radius = 0;
			} else
				this.radius = radius;
			return this;
		}
		
		/**
		 * Define the pixel calibration for the corresponding full-resolution image.
		 * This means that if a region with downsample is defined, the pixel size will be scaled accordingly.
		 * @param cal
		 * @return this finder
		 */
		public PeakFinder calibration(PixelCalibration cal) {
			this.cal = cal;
			return this;
		}
		
		/**
		 * Define the minimum separation between hotspot centroids. In general, this should be If &le; 0 (i.e. hotspots may overlap) or 
		 * radius x 2 (hotspots should not overlap).
		 * @param minimumSeparation
		 * @return this finder
		 */
		public PeakFinder minimumSeparation(double minimumSeparation) {
			this.minimumSeparation = minimumSeparation;
			return this;
		}
		
		/**
		 * Optionally constrain hotspots to be fully-contained within any provided ROI.
		 * This is relevant if the radius &gt; 0.
		 * @param constrainToROI
		 * @return this finder
		 */
		public PeakFinder withinROI(boolean constrainToROI) {
			this.constrainWithinROI = constrainToROI;
			return this;
		}
		
		/**
		 * Classification to apply to hotspots when using {@link #createObjects(ROI, int)}.
		 * @param hotspotClass
		 * @return this finder
		 */
		public PeakFinder peakClass(PathClass hotspotClass) {
			this.pathClass = hotspotClass;
			return this;
		}
		
		/**
		 * Find peaks as ROIs.
		 * @param roi optional ROI within which peaks should be found
		 * @param nPeaks total number of requested peaks (usually 1)
		 * @return a list of peak ROIs (ellipses or points, depending upon radius)
		 */
		public List<ROI> createROIs(ROI roi, int nPeaks) {
			
			// TODO: Consider using morphological reconstruction and H-maxima instead
			var fp = IJTools.convertToFloatProcessor(imgValues);
			int w = fp.getWidth();
			int h = fp.getHeight();
			for (int i = 0; i < w*h; i++) {
				if (Float.isNaN(fp.getf(i)))
					fp.setf(i, 0f);
			}
			var maxima = new MaximumFinder().getMaxima(fp, 1e-6, false);
			
			double downsample = 1;
			double offsetX = 0;
			double offsetY = 0;
			double maxX = imgValues.getWidth() * downsample;
			double maxY = imgValues.getHeight() * downsample;
			ImagePlane plane = ImagePlane.getDefaultPlane();
			if (region != null) {
				downsample = region.getDownsample();
				offsetX = region.getX();
				offsetY = region.getY();
				maxX = region.getMaxX();
				maxY = region.getMaxY();
				plane = region.getPlane();
			}
			
			double pixelSize = cal == null ? 1 : cal.getAveragedPixelSize().doubleValue();
			double radiusPixels = radius / pixelSize;
			double minimumSeparationPixels = minimumSeparation > 0 ? minimumSeparation / pixelSize : -1; 
			
			ROI roiMask = roi;
			if (constrainWithinROI) {
				if (roiMask == null) {
					roiMask = ROIs.createRectangleROI(offsetX+radiusPixels, offsetY+radiusPixels, maxX-radiusPixels, maxY-radiusPixels, plane);
				} else if (radiusPixels != 0) {
					roiMask = RoiTools.buffer(roiMask, -radiusPixels);
				}
			}
			if (roiMask.isEmpty()) {
				logger.error("ROI is too small - no hotspots can be found with radius " + radius);
				return Collections.emptyList();
			}
			
			List<PointWithValue> points = new ArrayList<>();
			for (int i = 0; i < maxima.npoints; i++) {
				if (imgMask != null && imgMask.getValue(maxima.xpoints[i], maxima.ypoints[i]) == 0f)
					continue;
				
				double val = fp.getf(maxima.xpoints[i], maxima.ypoints[i]);
				double x = (maxima.xpoints[i]+0.5) * downsample + offsetX;
				double y = (maxima.ypoints[i]+0.5) * downsample + offsetY;
				
				if (roiMask == null || roiMask.contains(x, y))
					points.add(new PointWithValue(x, y, val));
			}
			points.sort(Comparator.comparingDouble((PointWithValue p) -> p.value).reversed().thenComparingDouble(p -> p.y).thenComparingDouble(p -> p.x));
			
			var accepted = new ArrayList<PointWithValue>();
			int i = 0;
			while (accepted.size() < nPeaks && i < points.size()) {
				var p = points.get(i);
				boolean tooClose = false;
				if (minimumSeparationPixels > 0) {
					for (var p2 : accepted) {
						if (p.distance(p2) <= radiusPixels * 2) {
							tooClose = true;
							break;
						}
					}
				}
				if (!tooClose)
					accepted.add(p);
				i++;
			}
			
			if (accepted.isEmpty()) {
				logger.warn("No hotspots found matching the search criteria!");
				return Collections.emptyList();
			}
			if (accepted.size() < nPeaks) {
				logger.warn("I could only find {}/{} hotspots", accepted.size(), nPeaks);
			}
			
			var plane2 = plane;
			if (radiusPixels > 0) {
				var rois = accepted.stream().map(p -> ROIs.createEllipseROI(p.x-radiusPixels, p.y-radiusPixels, radiusPixels*2, radiusPixels*2, plane2)).collect(Collectors.toList());
				if (mergeROIs)
					return Collections.singletonList(RoiTools.union(rois));
				else
					return rois;
			} else {
				if (mergeROIs) {
					return Collections.singletonList(
							ROIs.createPointsROI(accepted.stream().map(p -> new Point2(p.x, p.y)).collect(Collectors.toList()), plane2)
							);
				} else
					return accepted.stream().map(p -> ROIs.createPointsROI(p.x, p.y, plane2)).collect(Collectors.toList());
			}
		}
		
		private PathObject createObject(ROI roi) {
			var pathObject = objectFun.apply(roi);
			pathObject.setPathClass(pathClass);
			return pathObject;
		}
		
		
		/**
		 * Create peaks as objects.
		 * @param roi optional ROI within which peaks should be found
		 * @param nPeaks total number of requested peaks (usually 1)
		 * @return a list of peak objects
		 * 
		 * @see #createROIs(ROI, int)
		 * @see #objectCreator(Function)
		 */
		public List<PathObject> createObjects(ROI roi, int nPeaks) {
			return createROIs(roi, nPeaks).stream().map(r -> createObject(r)).collect(Collectors.toList());
			
		}
		
		
	}
	
	

}
