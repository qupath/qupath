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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleToIntFunction;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMap;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.roi.ROIs;


/**
 * Command for generating density maps from detections on an image.
 * 
 * @author Pete Bankhead
 */
public class DensityMapCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapCommand.class);
	
	private QuPathGUI qupath;
	private Map<QuPathViewer, DensityMapDialog> dialogMap = new WeakHashMap<>();
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public DensityMapCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		var viewer = qupath.getViewer();
		
		var dialog = dialogMap.getOrDefault(viewer, null);
		if (dialog != null) {
			dialog.refresh();
			var stage = dialog.getStage();
			if (stage.isShowing())
				stage.requestFocus();
			else
				stage.show();
			return;
		}
		dialog = new DensityMapDialog(viewer);
		dialogMap.put(viewer, dialog);
		
		var stage = dialog.getStage();
//		stage.setOnCloseRequest(e -> dialogMap.remove(dialog.viewer));
		stage.show();
	}
	
	static enum DensityMapType {
		
		DETECTIONS_CLASSIFIED,
		POSITIVE_PERCENTAGE;
		
		@Override
		public String toString() {
			switch(this) {
			case DETECTIONS_CLASSIFIED:
				return "Detections";
			case POSITIVE_PERCENTAGE:
				return "Positive %";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
		
	}
	
	private class DensityMapDialog implements ChangeListener<ImageData<BufferedImage>> {
		
		private final static String title = "Density map";
		
		private QuPathViewer viewer;
		
		private Stage stage;
		
		private ComboBox<DensityMapType> comboType = new ComboBox<>();
		private ComboBox<PathClass> comboPrimary = new ComboBox<>(qupath.getAvailablePathClasses());

		private ComboBox<ColorMap> comboColorMap = new ComboBox<>();

		private DoubleProperty pixelSize = new SimpleDoubleProperty(-1);
		private DoubleProperty radius = new SimpleDoubleProperty(0.0);

		private DoubleProperty gamma = new SimpleDoubleProperty(1.0);

		private DoubleProperty minDisplay = new SimpleDoubleProperty(0);
		private DoubleProperty maxDisplay = new SimpleDoubleProperty(100);
		private BooleanProperty autoUpdateDisplayRange = new SimpleBooleanProperty(true);
		
		private DensityMap densityMap;
		private BufferedImageOverlay overlay;
		
		private boolean requestFullUpdate = false;
		private boolean requestQuickUpdate = false;
		
		public DensityMapDialog(QuPathViewer viewer) {
			this.viewer = viewer;
			
			comboType.getItems().setAll(DensityMapType.values());
			comboType.getSelectionModel().select(DensityMapType.DETECTIONS_CLASSIFIED);
			
			var pane = new GridPane();
			int row = 0;
			
			var labelTitle = new Label("Generate density map");
			labelTitle.setStyle("-fx-font-weight: bold;");
			labelTitle.setPadding(new Insets(0, 0, 5, 0));
			PaneTools.addGridRow(pane, row++, 0, null, labelTitle, labelTitle, labelTitle);
					
			PaneTools.addGridRow(pane, row++, 0, "Select type of density map.\n"
					+ "Use 'Detections' to look for the density of all detections with any classication.\n"
					+ "Use 'Positive %' specifically if your detections are classified as positive & negative and you want to find a high density that is positive.", new Label("Map type"), comboType, comboType);
			PaneTools.addGridRow(pane, row++, 0, "Select object classifications to include.\n"
					+ "Use this to filter out detections that should not contribute to the density map.", new Label("Classifications"), comboPrimary, comboPrimary);
			
			var sliderRadius = new Slider(0, 1000, radius.get());
			sliderRadius.valueProperty().bindBidirectional(radius);
			sliderRadius.setBlockIncrement(50);
			var tfRadius = new TextField();
			GuiTools.bindSliderAndTextField(sliderRadius, tfRadius);
			PaneTools.addGridRow(pane, row++, 0, "Select smoothing radius used to calculate densities."
					+ "This is defined in calibrated pixel units.", new Label("Radius"), sliderRadius, tfRadius);
			
			
			var labelColor = new Label("Customize appearance");
			labelColor.setStyle("-fx-font-weight: bold;");
			labelColor.setPadding(new Insets(5, 0, 5, 0));
			PaneTools.addGridRow(pane, row++, 0, null, labelColor, labelColor, labelColor);
			
			PaneTools.addGridRow(pane, row++, 0, "Choose the colormap to use for display", new Label("Colormap"), comboColorMap, comboColorMap);
			
			var slideMin = new Slider(0, maxDisplay.get(), minDisplay.get());
			slideMin.valueProperty().bindBidirectional(minDisplay);
			slideMin.setBlockIncrement(0.1);
			slideMin.disableProperty().bind(autoUpdateDisplayRange);
			var tfMin = new TextField();
			GuiTools.bindSliderAndTextField(slideMin, tfMin);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the first entry in the colormap.", new Label("Min display"), slideMin, tfMin);

			var slideMax = new Slider(0, maxDisplay.get(), maxDisplay.get());
			slideMax.valueProperty().bindBidirectional(maxDisplay);
			slideMax.setBlockIncrement(0.1);
			slideMax.disableProperty().bind(autoUpdateDisplayRange);
			var tfMax = new TextField();
			GuiTools.bindSliderAndTextField(slideMax, tfMax);
			PaneTools.addGridRow(pane, row++, 0, "Set the density value corresponding to the last entry in the colormap.", new Label("Max display"), slideMax, tfMax);

			var sliderGamma = new Slider(0, 4, gamma.get());
			sliderGamma.valueProperty().bindBidirectional(gamma);
			sliderGamma.setBlockIncrement(0.1);
			var tfGamma = new TextField();
			GuiTools.bindSliderAndTextField(sliderGamma, tfGamma);
			PaneTools.addGridRow(pane, row++, 0, "Control how the opacity of the density map changes between low & high values.\n"
					+ "Choose zero for an opaque map.", new Label("Gamma"), sliderGamma, tfGamma);
			
			var cbAutoUpdate = new CheckBox("Auto-update display range");
			cbAutoUpdate.selectedProperty().bindBidirectional(autoUpdateDisplayRange);
			PaneTools.addGridRow(pane, row++, 0, "Automatically set the minimum & maximum display range for the colormap.", 
					cbAutoUpdate, cbAutoUpdate, cbAutoUpdate);
			
			
			double tfw = 80;
			tfRadius.setMaxWidth(tfw);
			tfGamma.setMaxWidth(tfw);
			tfMin.setMaxWidth(tfw);
			tfMax.setMaxWidth(tfw);
			
			comboPrimary.setButtonCell(GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.setCellFactory(c -> GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.getSelectionModel().selectFirst();

			refresh();
						
			comboType.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> requestUpdate(true));
			comboPrimary.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> requestUpdate(true));
			comboColorMap.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> requestUpdate(false));
			radius.addListener((v, o, n) -> requestUpdate(true));
			gamma.addListener((v, o, n) -> requestUpdate(false));
			maxDisplay.addListener((v, o, n) -> requestUpdate(false));
			
			
			var btnHotspots = new Button("Find hotspots");
			btnHotspots.setTooltip(new Tooltip("Find the hotspots in the density map with highest values"));
			btnHotspots.setOnAction(e -> promptToFindHotspots());
			
			var btnExport = new Button("Export map");
			btnExport.setTooltip(new Tooltip("Export the density map as an image"));
			btnExport.setOnAction(e -> promptToSaveImage());
			
//			var btnSendToIJ = new Button("Send to ImageJ");
//			btnSendToIJ.setTooltip(new Tooltip("Send the density map to ImageJ"));
//			btnSendToIJ.setOnAction(e -> sendToImageJ());
			
			var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnExport);
			buttonPane.setHgap(5);
			PaneTools.addGridRow(pane, row++, 0, null, buttonPane, buttonPane, buttonPane);
			PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport);
			buttonPane.setPadding(new Insets(5, 0, 0, 0));
			
			PaneTools.setToExpandGridPaneWidth(comboType, comboPrimary, comboColorMap, sliderRadius, sliderGamma, buttonPane);
			
			pane.setVgap(5);
			pane.setPadding(new Insets(10));

			viewer.imageDataProperty().addListener(this);
			
			stage = new Stage();
			stage.setScene(new Scene(pane));
			stage.initOwner(qupath.getStage());
			stage.setTitle("Density map");
			stage.setOnCloseRequest(e -> deregister());
		}
		
		private String classificationText(PathClass pathClass) {
			if (PathClassTools.isValidClass(pathClass))
				return pathClass.toString();
			else
				return "Any";
		}
		
		private void deregister() {
			viewer.imageDataProperty().removeListener(this);
			dialogMap.remove(viewer);
			if (viewer.getCustomPixelLayerOverlay() == overlay)
				viewer.resetCustomPixelLayerOverlay();
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
		
		private void requestUpdate(boolean fullUpdate) {
			if (requestFullUpdate || (requestQuickUpdate && !fullUpdate))
				return;
			requestFullUpdate = fullUpdate;
			requestQuickUpdate = true;
			qupath.createSingleThreadExecutor(this).submit(() -> updateHeatmap());
		}
		
		public void promptToFindHotspots() {
			// TODO: Implement finding hotspots
			
			var imageData = viewer.getImageData();
			if (imageData == null || densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}
			
			var response = Dialogs.showInputDialog(title, "How many hotspots do you want to find?", 1.0);
			boolean allowOverlapping = false;
			if (response == null)
				return;
			int n = response.intValue();
			
			var imgValues = densityMap.getValues();
			var fp = IJTools.convertToFloatProcessor(imgValues);
			var maxima = new MaximumFinder().getMaxima(fp, 1e-6, false);
			
			var region = densityMap.getRegion();
			double downsample = region.getDownsample();
			List<PointWithValue> points = new ArrayList<>();
			for (int i = 0; i < maxima.npoints; i++) {
				double val = fp.getf(maxima.xpoints[i], maxima.ypoints[i]);
				double x = maxima.xpoints[i] * downsample + region.getX();
				double y = maxima.ypoints[i] * downsample + region.getY();
				points.add(new PointWithValue(x, y, val));
			}
			points.sort(Comparator.comparingDouble((PointWithValue p) -> p.value).reversed().thenComparingDouble(p -> p.y).thenComparingDouble(p -> p.x));
			
			var accepted = new ArrayList<PointWithValue>();
			double radiusPixels = radius.get() / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
			int i = 0;
			while (accepted.size() < n && i < points.size()) {
				var p = points.get(i);
				boolean tooClose = false;
				if (!allowOverlapping) {
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
			
			var annotations = new ArrayList<PathObject>();
			var plane = region.getPlane();
			var baseClass = comboPrimary.getSelectionModel().getSelectedItem();
			PathClass hotspotClass = PathClassFactory.getPathClass("Hotspot", ColorTools.makeRGB(200, 120, 20));
			if (PathClassTools.isValidClass(baseClass)) {
				hotspotClass = PathClassTools.mergeClasses(baseClass, hotspotClass);
			}
			for (var p : accepted) {
				var roi = ROIs.createEllipseROI(p.x-radiusPixels, p.y-radiusPixels, radiusPixels*2, radiusPixels*2, plane);
				annotations.add(PathObjects.createAnnotationObject(roi, hotspotClass));
			}
			
			if (annotations.size() < n) {
				if (annotations.size() == 1)
					Dialogs.showErrorNotification(title, "I could only find one hotspot!");
				else
					Dialogs.showErrorNotification(title, "I could only find " + annotations.size() + " hotspots!");
			}
			
			// Remove existing hotspots with the same classification
			var hierarchy = imageData.getHierarchy();
			var hotspotClassTemp = hotspotClass;
			var existing = hierarchy.getAnnotationObjects().stream().filter(a -> a.getPathClass() == hotspotClassTemp).collect(Collectors.toList());
			hierarchy.removeObjects(existing, true);
			hierarchy.addPathObjects(annotations);
		}
		
		
		public void promptToSaveImage() {
			
			if (densityMap == null) {
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
				promptToSaveRawImage();
			} else if (btColor.equals(response)) {
				promptToSaveColorImage();
			} else if (btImageJ.equals(response)) {
				sendToImageJ();
			}
		}
		
		public void promptToSaveRawImage() {
			var imp = convertToImagePlus(densityMap);
			var file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
			if (file != null)
				IJ.save(imp, file.getAbsolutePath());
		}

		public void promptToSaveColorImage() {
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

		public void sendToImageJ() {
			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			convertToImagePlus(densityMap).show();
		}

		public ImagePlus convertToImagePlus(DensityMap densityMap) {
			var imgValues = densityMap.getValues();
			var fp = IJTools.convertToFloatProcessor(imgValues);
			var stack = new ImageStack(fp.getWidth(), fp.getHeight());
			stack.addSlice(fp);
			var alpha = densityMap.getAlpha();
			if (alpha != null) {
				stack.addSlice(IJTools.convertToFloatProcessor(alpha));
			}
			var imp = new ImagePlus("Density map", stack);
			if (stack.getSize() > 1) {
				imp.setDimensions(stack.getSize(), 1, 1);
				imp = new CompositeImage(imp, CompositeImage.COLOR);
			}
			// TODO: Calibrate the ImagePlus
			// TODO: Handle multidimensional images
//			IJTools.calibrateImagePlus(imp, request, server);
			return imp;
		}
		
		
		
		public void updateHeatmap() {
			if (requestFullUpdate || densityMap == null)
				densityMap = calculateHeatmap();
			overlay = createOverlay(densityMap);
			if (Platform.isFxApplicationThread()) {
				viewer.setCustomPixelLayerOverlay(overlay);
			} else
				Platform.runLater(() -> viewer.setCustomPixelLayerOverlay(overlay));
		}
		
				
		
		public DensityMap calculateHeatmap() {
			requestFullUpdate = false;
			
			var imageData = viewer.getImageData();
			if (imageData == null) {
//				Dialogs.showErrorMessage("Density map", "No image available!");
				return null;
			}
			var builder = new DensityMap.HeatmapBuilder();
			
			if (pixelSize.get() > 0)
				builder.pixelSize(pixelSize.get());
			
			var mapType = comboType.getSelectionModel().getSelectedItem();
			var pathClass = comboPrimary.getSelectionModel().getSelectedItem();
			if (PathClassTools.isValidClass(pathClass)) {
				if (mapType == DensityMapType.POSITIVE_PERCENTAGE)
					builder.positivePercentage(pathClass);
				else
					builder.density(pathClass, !pathClass.isDerivedClass());
			} else {
				if (mapType == DensityMapType.POSITIVE_PERCENTAGE)
					builder.positivePercentage();
				else
					builder.density(PathObjectFilter.DETECTIONS_ALL);
			}
			
			builder.radius(radius.get());
			
			var map = builder.build(viewer.getImageData());
			
			
			return map;
		}
		
		public BufferedImageOverlay createOverlay(DensityMap map) {
			
			requestQuickUpdate = false;
			
			if (map == null)
				return null;
			
			var colorMap = comboColorMap.getSelectionModel().getSelectedItem();
			var image = map.getValues();
			
			var min = minDisplay.get();
			var max = maxDisplay.get();

			if (autoUpdateDisplayRange.get()) {
				var minMax = findMinAndMax(SimpleImages.getPixels(image, true));
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
			DoubleToIntFunction alphaFun;
			int alphaChannel;
			var alpha = map.getAlpha();
			if (g <= 0) {
				alphaFun = null;
				alphaChannel = -1;
			} else {
				if (alpha == null) {
					alphaFun = ColorModelFactory.createGammaFunction(g, min, max);
					alphaChannel = 0;
				} else {
					var alphaMinMax = findMinAndMax(SimpleImages.getPixels(alpha, true));
					alphaFun = ColorModelFactory.createGammaFunction(g, alphaMinMax.minVal, alphaMinMax.maxVal);
					alphaChannel = 1;
				}
			}
			
			var cm = ColorModelFactory.createColorModel(PixelType.FLOAT32, colorMap, min, max, alphaChannel, alphaFun);
			
			var raster = cm.createCompatibleWritableRaster(image.getWidth(), image.getHeight());
			raster.setSamples(0, 0, image.getWidth(), image.getHeight(), 0, SimpleImages.getPixels(map.getValues(), true));
			if (alpha != null && alphaChannel >= 0)
				raster.setSamples(0, 0, image.getWidth(), image.getHeight(), 1, SimpleImages.getPixels(alpha, true));
			var img = new BufferedImage(cm, raster, false, null);
			
			img = BufferedImageTools.ensureBufferedImageType(img, BufferedImage.TYPE_INT_ARGB);
			
			var overlay = new BufferedImageOverlay(viewer, img);
			overlay.setInterpolation(ImageInterpolation.BILINEAR);
			return overlay;
		}

		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
				ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
			if (newValue != null)
				requestUpdate(true);
		}
		
		
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
	
	

}
