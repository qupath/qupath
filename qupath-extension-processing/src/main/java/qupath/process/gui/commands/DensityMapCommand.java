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

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ij.CompositeImage;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapNormalization;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.common.ThreadTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.process.gui.commands.DensityMapCommand.MinMaxFinder.MinMax;
import qupath.process.gui.ml.PixelClassificationOverlay;


/**
 * Command for generating density maps from detections on an image.
 * 
 * @author Pete Bankhead
 */
public class DensityMapCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapCommand.class);
	
	private final static String title = "Density map";
	
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
			dialog = new DensityMapDialog(qupath);
			if (qupath.getImageData() != null)
				dialog.updateDefaults(qupath.getImageData());
		}
		
		var stage = dialog.getStage();
		stage.setOnCloseRequest(e -> closeDialog());
		stage.show();
	}
	
	void closeDialog() {
		if (dialog != null) {
			dialog.deregister();
			dialog = null;
		}
	}
	
	
	/**
	 * Supported input objects.
	 */
	private static enum DensityMapType {
		
		DETECTIONS(PathObjectFilter.DETECTIONS_ALL),
		CELLS(PathObjectFilter.CELLS),
		POINT_ANNOTATIONS(
				PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS)
				.and(PathObjectPredicates.filter(PathObjectFilter.ROI_POINT)));
		
		private final PathObjectPredicate predicate;
		
		private DensityMapType(PathObjectFilter filter) {
			this(PathObjectPredicates.filter(filter));
		}
		
		private DensityMapType(PathObjectPredicate predicate) {
			this.predicate = predicate;
		}
		
		/**
		 * Get predicate to select objects of the desired type.
		 * @return
		 */
		public PathObjectPredicate getPredicate() {
			return predicate;
		}
		
		@Override
		public String toString() {
			switch(this) {
			case DETECTIONS:
				return "All detections";
			case CELLS:
				return "All cells";
			case POINT_ANNOTATIONS:
				return "Point annotations";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
		
	}
	
	
	static class DensityMapDialog {
		
		private QuPathGUI qupath;
				
		private final Stage stage;
		
		private final ObservableDensityMapBuilder densityBuilder;
		private final ObservableColorMapBuilder displayBuilder;
				
		/**
		 * Automatically update the density maps and overlays.
		 */
		private final BooleanProperty autoUpdate = new SimpleBooleanProperty(true);
		
		private HierarchyPixelClassifierManager manager;
				
		private final double textFieldWidth = 80;
		private final double hGap = 5;
		private final double vGap = 5;
				
		public DensityMapDialog(QuPathGUI qupath) {
			this.qupath = qupath;
			
			densityBuilder = new ObservableDensityMapBuilder(qupath.imageDataProperty());
			displayBuilder = new ObservableColorMapBuilder(qupath);
			
			var paneParams = buildAllObjectsPane(densityBuilder);
			var titledPaneParams = new TitledPane("Define density map", paneParams);
			titledPaneParams.setExpanded(true);
			titledPaneParams.setCollapsible(false);
			PaneTools.simplifyTitledPane(titledPaneParams, true);
			
//			var labelAppearance = new Label("These options change the appearance of the density map only.");
//			labelAppearance.setAlignment(Pos.CENTER);
//			PaneTools.addGridRow(pane, row++, 0, null, labelAppearance, labelAppearance, labelAppearance);
			
			var paneDisplay = buildDisplayPane(displayBuilder);
			
			var titledPaneDisplay = new TitledPane("Customize appearance", paneDisplay);
			titledPaneDisplay.setExpanded(false);
			PaneTools.simplifyTitledPane(titledPaneDisplay, true);
			
			var pane = createGridPane();
			int row = 0;
			PaneTools.addGridRow(pane, row++, 0, "Options to customize the density map content", titledPaneParams, titledPaneParams, titledPaneParams);			
			PaneTools.addGridRow(pane, row++, 0, "Options to customize how the density map is displayed", titledPaneDisplay, titledPaneDisplay, titledPaneDisplay);

			
			var btnAutoUpdate = new ToggleButton("Auto-update");
			btnAutoUpdate.setSelected(autoUpdate.get());
			btnAutoUpdate.selectedProperty().bindBidirectional(autoUpdate);
			autoUpdate.addListener((v, o, n) -> {
				if (n)
					displayBuilder.updateDisplayRanges(manager.currentDensityMap.get());
			});
			
			PaneTools.addGridRow(pane, row++, 0, "Automatically update the heatmap. Turn this off if changing parameters and heatmap generation is slow.", btnAutoUpdate, btnAutoUpdate, btnAutoUpdate);
			
			displayBuilder.colorMap.addListener((v, o, n) -> refreshViewers());
			displayBuilder.gamma.addListener((v, o, n) -> refreshViewers());
			displayBuilder.minDisplay.addListener((v, o, n) -> refreshViewers());
			displayBuilder.maxDisplay.addListener((v, o, n) -> refreshViewers());
			// TODO: Add autoupdate control
//			autoUpdate.addListener((v, o, n) -> {
//				if (n)
//					requestAutoUpdate(n);
//			});
			displayBuilder.autoUpdateDisplayRange.addListener((v, o, n) -> refreshViewers());
			
			var buttonPane = buildButtonPane();
			PaneTools.addGridRow(pane, row++, 0, null, buttonPane, buttonPane, buttonPane);
			PaneTools.setToExpandGridPaneWidth(btnAutoUpdate, buttonPane);
						
			pane.setPadding(new Insets(10));

			stage = new Stage();
			stage.setScene(new Scene(pane));
			stage.setResizable(false);
			stage.initOwner(qupath.getStage());
			stage.setTitle("Density map");
			
			// Update stage height when display options expanded/collapsed
			titledPaneDisplay.heightProperty().addListener((v, o, n) -> stage.sizeToScene());
			
			// Create new overlays for the viewers
			manager = new HierarchyPixelClassifierManager(qupath, densityBuilder.classifier, displayBuilder.interpolation, displayBuilder.renderer);
			manager.currentDensityMap.addListener((v, o, n) -> displayBuilder.updateDisplayRanges(n));
		}
		
		
		private Pane buildButtonPane() {
			var hotspotFinder = new HotspotFinder();			
			var btnHotspots = new Button("Find hotspots");
			btnHotspots.setTooltip(new Tooltip("Find the hotspots in the density map with highest values"));
			btnHotspots.setOnAction(e -> promptToFindHotspots(hotspotFinder));
			
			var contourTracer = new ContourTracer();			
			var btnContours = new Button("Threshold");
			btnContours.setTooltip(new Tooltip("Threshold to identify high-density regions"));
			btnContours.setOnAction(e -> promptToTraceContours(contourTracer));
			
			DensityMapExporter exporter = new DensityMapExporter();
			var btnExport = new Button("Export map");
			btnExport.setTooltip(new Tooltip("Export the density map as an image"));
			btnExport.setOnAction(e -> promptToSaveImage(exporter));
			
			var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnContours, btnExport);
			buttonPane.setHgap(hGap);
			PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport, btnContours);
			return buttonPane;
		}
		
		private void promptToFindHotspots(HotspotFinder finder) {
			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var classifier = densityBuilder.classifier.get();
			double radius = densityBuilder.radius.get();
			if (imageData != null && classifier != null) {
				finder.promptToFindHotspots(imageData, classifier, radius);
			}
		}
		
		private void promptToTraceContours(ContourTracer tracer) {
			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var classifier = densityBuilder.classifier.get();
			if (imageData != null && classifier != null) {
				tracer.promptToTraceContours(imageData, classifier);
			}
		}
		
		
		private void promptToSaveImage(DensityMapExporter exporter) {
			var viewer = qupath.getViewer();
			var imageData = viewer.getImageData();
			var classifier = densityBuilder.classifier.get();
			if (imageData != null && classifier != null) {
				exporter.promptToSaveImage(imageData, classifier, manager.overlay);
			}
		}
		
		
		private Pane buildAllObjectsPane(ObservableDensityMapBuilder params) {
			ComboBox<DensityMapType> comboType = new ComboBox<>();
			comboType.getItems().setAll(DensityMapType.values());
			comboType.getSelectionModel().select(DensityMapType.DETECTIONS);
			params.allObjectTypes.bind(comboType.getSelectionModel().selectedItemProperty());

			ComboBox<PathClass> comboAllObjects = new ComboBox<>();
			comboAllObjects.getItems().setAll(qupath.getAvailablePathClasses());
			comboAllObjects.getSelectionModel().select(PathClassFactory.getPathClassUnclassified());
			params.allObjectClass.bind(comboAllObjects.getSelectionModel().selectedItemProperty());
			
			ComboBox<PathClass> comboPrimary = new ComboBox<>();
			comboPrimary.getItems().setAll(qupath.getAvailablePathClasses());
			comboPrimary.getSelectionModel().select(PathClassFactory.getPathClassUnclassified());
			params.densityObjectClass.bind(comboPrimary.getSelectionModel().selectedItemProperty());
			
			comboPrimary.setButtonCell(GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.setCellFactory(c -> GuiTools.createCustomListCell(p -> classificationText(p)));
			comboPrimary.getSelectionModel().selectFirst();
			
			ComboBox<DensityMapNormalization> comboNormalization = new ComboBox<>();
			comboNormalization.getItems().setAll(DensityMapNormalization.values());
			comboNormalization.getSelectionModel().select(DensityMapNormalization.NONE);
			params.normalization.bind(comboNormalization.getSelectionModel().selectedItemProperty());
			
			var pane = createGridPane();
			int row = 0;
			
//			var labelDescription = new Label("These options change how the density map is calculated.");
//			labelDescription.setAlignment(Pos.CENTER);
//			PaneTools.addGridRow(pane, row++, 0, null, labelDescription, labelDescription, labelDescription);

			PaneTools.addGridRow(pane, row++, 0, "Select type of density map.\n"
					+ "Use 'All detections' to look for the density of all detections (regardless of any intensity classification, e.g. Positive/Negative).\n"
					+ "Use 'Positive detections' specifically if your detections are classified as Positive & Negative and you want to find a high density that is positive.\n"
					+ "Use 'Point annotations' to use annotated points rather than detections.",
					new Label("Input objects"), comboType, comboType);
			
			PaneTools.addGridRow(pane, row++, 0, "Select object classifications to include.\n"
					+ "Use this to filter out detections that should not contribute to the density map.",
					new Label("Input object class"), comboAllObjects, comboAllObjects);

			
			PaneTools.addGridRow(pane, row++, 0, "Select object classifications to include.\n"
					+ "Use this to filter out detections that should not contribute to the density map.",
					new Label("Density class"), comboPrimary, comboPrimary);
			
			PaneTools.addGridRow(pane, row++, 0, "Select method of normalizing densities.\n"
					+ "Choose whether to show raw counts, or normalize densities by area or the number of objects locally.\n"
					+ "This can be used to distinguish between the total number of objects in an area with a given classification, "
					+ "and the proportion of objects within the area with that classification.\n"
					+ "Gaussian weighting gives a smoother result, but it can be harder to interpret.",
					new Label("Density type"), comboNormalization, comboNormalization);
			
			
			var sliderRadius = new Slider(0, 1000, params.radius.get());
			sliderRadius.valueProperty().bindBidirectional(params.radius);
			initializeSliderSnapping(sliderRadius, 50, 1, 0.1);
			var tfRadius = createTextField();
			
			boolean expandSliderLimits = true;
			
			GuiTools.bindSliderAndTextField(sliderRadius, tfRadius, expandSliderLimits, 2);
			GuiTools.installRangePrompt(sliderRadius);
//			tfRadius.setOnMouseClicked(e -> {
//				if (e.getClickCount() > 1)
//					GuiTools.promptForSliderRange(slider)
//			});
			PaneTools.addGridRow(pane, row++, 0, "Select smoothing radius used to calculate densities.\n"
					+ "This is defined in calibrated pixel units (e.g. µm if available).", new Label("Radius"), sliderRadius, tfRadius);
			
			PaneTools.setToExpandGridPaneWidth(comboType, comboPrimary, comboAllObjects, comboNormalization, sliderRadius);

			return pane;
		}
		
		
		
		private Pane buildDisplayPane(ObservableColorMapBuilder displayParams) {
			
			var comboColorMap = new ComboBox<ColorMap>();
			comboColorMap.getItems().setAll(ColorMaps.getColorMaps().values());
			if (comboColorMap.getSelectionModel().getSelectedItem() == null)
				comboColorMap.getSelectionModel().selectFirst();
			displayParams.colorMap.bind(comboColorMap.getSelectionModel().selectedItemProperty());
			
			var comboInterpolation = new ComboBox<ImageInterpolation>();
			
			boolean expandSliderLimits = true;

			var paneDisplay = createGridPane();
			
			int rowDisplay = 0;
			PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Choose the colormap to use for display", new Label("Colormap"), comboColorMap, comboColorMap);
			
			PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Choose how the density map should be interpolated.\n"
					+ "This impacts the visual smoothness, especially if the density radius is small and the image is viewed while zoomed in.", new Label("Interpolation"), comboInterpolation, comboInterpolation);
			
			comboInterpolation.getItems().setAll(ImageInterpolation.values());
			comboInterpolation.getSelectionModel().select(ImageInterpolation.NEAREST);
			displayParams.interpolation.bind(comboInterpolation.getSelectionModel().selectedItemProperty());

			var slideMin = new Slider(0, displayParams.maxDisplay.get(), displayParams.minDisplay.get());
			slideMin.valueProperty().bindBidirectional(displayParams.minDisplay);
			initializeSliderSnapping(slideMin, 1, 1, 0.1);
			slideMin.disableProperty().bind(displayParams.autoUpdateDisplayRange);
			var tfMin = createTextField();
			GuiTools.bindSliderAndTextField(slideMin, tfMin, expandSliderLimits);
			GuiTools.installRangePrompt(slideMin);
			PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Set the density value corresponding to the first entry in the colormap.", new Label("Min display"), slideMin, tfMin);

			var slideMax = new Slider(0, displayParams.maxDisplay.get(), displayParams.maxDisplay.get());
			slideMax.valueProperty().bindBidirectional(displayParams.maxDisplay);
			initializeSliderSnapping(slideMax, 1, 1, 0.1);
			slideMax.disableProperty().bind(displayParams.autoUpdateDisplayRange);
			var tfMax = createTextField();
			GuiTools.bindSliderAndTextField(slideMax, tfMax, expandSliderLimits);
			GuiTools.installRangePrompt(slideMax);
			PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Set the density value corresponding to the last entry in the colormap.", new Label("Max display"), slideMax, tfMax);

			var sliderGamma = new Slider(0, 4, displayParams.gamma.get());
			sliderGamma.valueProperty().bindBidirectional(displayParams.gamma);
			initializeSliderSnapping(sliderGamma, 0.1, 1, 0.1);
			var tfGamma = createTextField();
			GuiTools.bindSliderAndTextField(sliderGamma, tfGamma, false, 1);
			PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Control how the opacity of the density map changes between low & high values.\n"
					+ "Choose zero for an opaque map.", new Label("Gamma"), sliderGamma, tfGamma);

//			var cbAutoUpdateDisplayRange = new CheckBox("Use full display range");
//			cbAutoUpdateDisplayRange.selectedProperty().bindBidirectional(autoUpdateDisplayRange);
//			PaneTools.addGridRow(pane, row++, 0, "Automatically set the minimum & maximum display range for the colormap.", 
//					cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange, cbAutoUpdateDisplayRange);
//			cbAutoUpdateDisplayRange.disableProperty().bind(displayAdjustable.not());
//			cbAutoUpdateDisplayRange.setPadding(new Insets(0, 0, 10, 0));
			
			PaneTools.setToExpandGridPaneWidth(comboColorMap, comboInterpolation, sliderGamma);
			
			return paneDisplay;
		}
		
		
		/**
		 * Create a {@link GridPane} with standard gaps.
		 * @return
		 */
		GridPane createGridPane() {
			var pane = new GridPane();
			pane.setVgap(vGap);
			pane.setHgap(hGap);
			return pane;
		}
		
		
		/**
		 * Create a {@link TextField} with a standard width;
		 * @return
		 */
		TextField createTextField() {
			var textField = new TextField();
			textField.setMaxWidth(textFieldWidth);
			return textField;
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
			densityBuilder.radius.set(pixelSize);
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

		
		/**
		 * Deregister listeners. This should be called when the stage is closed if it will not be used again.
		 */
		public void deregister() {
			manager.shutdown();
		}
		
		public Stage getStage() {
			return stage;
		}
		
		
		private void refreshViewers() {
			qupath.repaintViewers();
		}
		
		
	}
	
	
	static class MinMaxFinder {
		
		private static Map<String, List<MinMax>> cache = Collections.synchronizedMap(new HashMap<>());
		
	
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
		 * Note that this will use a cached value, therefore it is assumed that the server cannot change.
		 * 
		 * @param server server containing pixels
		 * @param countBand optional band that can be thresholded and used for masking; if -1, then the same band is used for counts
		 * @param minCount minimum value for pixels to be included
		 * @return
		 * @throws IOException 
		 */
		private static List<MinMax> getMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
			String key = server.getPath() + "?count=" + countBand + "&minCount=" + minCount;
			var minMax = cache.get(key);
			if (minMax == null) {
				minMax = calculateMinMax(server, countBand, minCount);
				cache.put(key, minMax);
			}
			return minMax;
		}
		
		private static List<MinMax> calculateMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
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
		
	
	/**
	 * Encapsulate the parameters needed to generate a density map in a JavaFX-friendly way.
	 */
	static class ObservableDensityMapBuilder {
		
		private ObjectProperty<DensityMapType> allObjectTypes = new SimpleObjectProperty<>(DensityMapType.DETECTIONS);
		private ObjectProperty<PathClass> allObjectClass = new SimpleObjectProperty<>(null);
		private ObjectProperty<PathClass> densityObjectClass = new SimpleObjectProperty<>(null);
		private ObjectProperty<DensityMapNormalization> normalization = new SimpleObjectProperty<>(DensityMapNormalization.NONE);
		
		private DoubleProperty pixelSize = new SimpleDoubleProperty(-1);
		private DoubleProperty radius = new SimpleDoubleProperty(10.0);
		
		private ObservableValue<ImageData<BufferedImage>> imageData;
		private ObjectProperty<PixelClassifier> classifier = new SimpleObjectProperty<>();
		
		private Gson gson = GsonTools.getInstance();
				
		ObservableDensityMapBuilder(ObservableValue<ImageData<BufferedImage>> imageData) {
			this.imageData = imageData;
			allObjectTypes.addListener((v, o, n) -> updateClassifier());
			allObjectClass.addListener((v, o, n) -> updateClassifier());
			densityObjectClass.addListener((v, o, n) -> updateClassifier());
			normalization.addListener((v, o, n) -> updateClassifier());
			pixelSize.addListener((v, o, n) -> updateClassifier());
			radius.addListener((v, o, n) -> updateClassifier());
		}
		
		/**
		 * Update the classifier. Note that this can only be done if there is an active {@link ImageData}, 
		 * which is used to get pixel calibration information.
		 */
		private void updateClassifier() {
			var imageData = this.imageData.getValue();
			if (imageData == null)
				return;
			// Only update the classifier if it is different from the current classifier
			// To test this, we rely upon the JSON representation
			setClassifier(buildDensityMap(imageData));
		}
		
		private void setClassifier(PixelClassifier newClassifier) {
			var currentClassifier = classifier.get();
			if (newClassifier != null && !Objects.equals(newClassifier, currentClassifier)) {
				if (classifier.get() == null || !Objects.equals(gson.toJson(currentClassifier), gson.toJson(newClassifier)))
					classifier.set(newClassifier);
			}
		}
		
		private PathObjectPredicate updatePredicate(PathObjectPredicate predicate, PathClass pathClass) {
			if (pathClass == null || pathClass.getName() == null)
				return predicate;
			PathObjectPredicate pathClassPredicate;
			if (pathClass.isDerivedClass())
				pathClassPredicate = PathObjectPredicates.exactClassification(pathClass);
			else
				pathClassPredicate = PathObjectPredicates.containsClassification(pathClass.getName());
			return predicate == null ? pathClassPredicate : predicate.and(pathClassPredicate);
		}
		
		private PixelClassifier buildDensityMap(ImageData<BufferedImage> imageData) {
			
			// Determine all objects filter
			PathObjectPredicate allObjectsFilter = allObjectTypes.get().getPredicate();
			PathClass primaryClass = allObjectClass.get();
			allObjectsFilter = updatePredicate(allObjectsFilter, primaryClass);
			
			// Determine density objects filter
			var densityClass = densityObjectClass.get();
			PathObjectPredicate densityObjectsFilter = updatePredicate(null, densityClass);
			
			// Create map
			var builder = DensityMaps.builder(allObjectsFilter);
			if (pixelSize.get() > 0)
				builder.pixelSize(pixelSize.get());
			
			builder.normalization(normalization.get());

			if (densityObjectsFilter != null) {
				String filterName;
				if (primaryClass == null)
					filterName = densityClass.toString();
				else
					filterName = primaryClass.toString() + ": " + densityClass.toString();
				builder.addDensities(filterName, densityObjectsFilter);
			}
			
			builder.radius(radius.get());
			return builder.buildClassifier(imageData);
			
		}
		
	}
	
	
	
	
	static class ObservableColorMapBuilder {
		
		private QuPathGUI qupath;
				
		private final ObjectProperty<ColorMap> colorMap = new SimpleObjectProperty<>();
		private final ObjectProperty<ImageInterpolation> interpolation = new SimpleObjectProperty<>(ImageInterpolation.NEAREST);
		
		// Not observable, since the user can't adjust them (and we don't want unnecessary events fired)
		private int alphaCountBand = -1;
		private double minAlpha = 0;
		private double maxAlpha = 1;
		
		// Observable, so we can update them in the UI
		private final DoubleProperty gamma = new SimpleDoubleProperty(1.0);

		private final DoubleProperty minDisplay = new SimpleDoubleProperty(0);
		private final DoubleProperty maxDisplay = new SimpleDoubleProperty(1);
		private final BooleanProperty autoUpdateDisplayRange = new SimpleBooleanProperty(true);
		
		private final ColorModelRenderer renderer = new ColorModelRenderer(null);
		
		ObservableColorMapBuilder(QuPathGUI qupath) {
			this.qupath = qupath;
			colorMap.addListener((v, o, n) -> updateRenderer());
			gamma.addListener((v, o, n) -> updateRenderer());
			minDisplay.addListener((v, o, n) -> updateRenderer());
			maxDisplay.addListener((v, o, n) -> updateRenderer());
			// TODO: Ensure interpolation works with overlay
			interpolation.addListener((v, o, n) -> refreshViewers());
		}
		
		private void updateDisplayRanges(ImageServer<BufferedImage> densityMapServer) {
			if (densityMapServer == null || !autoUpdateDisplayRange.get())
				return;
			
			boolean autoUpdate = autoUpdateDisplayRange.get();
			
			// If the last channel is 'counts', then it is used for normalization
			int alphaCountBand = -1;
			if (densityMapServer.getChannel(densityMapServer.nChannels()-1).getName().equals(DensityMaps.CHANNEL_ALL_OBJECTS))
				alphaCountBand = densityMapServer.nChannels()-1;
			
			// Compute min/max values if we need them
			List<MinMax> minMax = null;
			if (alphaCountBand > 0 || autoUpdate) {
				try {
					minMax = MinMaxFinder.getMinMax(densityMapServer, alphaCountBand, 1);
				} catch (IOException e) {
					logger.warn("Error setting display ranges: " + e.getLocalizedMessage(), e);
				}
			}
			
			// Determine min/max values for alpha in count channel, if needed
			minAlpha = minDisplay.get();
			if (alphaCountBand <= 0) {
				maxAlpha = maxDisplay.get();
			} else {
				maxAlpha = minMax.get(alphaCountBand).maxValue;
			}
			this.alphaCountBand = alphaCountBand;
			
			double maxDisplayValue = minMax == null ? maxDisplay.get() : minMax.get(0).maxValue;
//			GuiTools.runOnApplicationThread(() -> {
				if (autoUpdate) {
					maxDisplay.set(maxDisplayValue);
				}
				updateRenderer();
//			});
		}
		
		
		private void updateRenderer() {
			var colorMap = this.colorMap.get();
			
			double min = this.minDisplay.get();
			double max = this.maxDisplay.get();

			var g = gamma.get();
			DoubleToIntFunction alphaFun = null;
			
			if (alphaCountBand < 0) {
				if (g <= 0) {
					alphaFun = null;
					alphaCountBand = -1;
				} else {
					alphaFun = ColorModelFactory.createGammaFunction(g, min, max);
					alphaCountBand = 0;
				}
			} else if (alphaFun == null) {
				if (g <= 0)
					alphaFun = ColorModelFactory.createLinearFunction(minAlpha, minAlpha+1);
				else
					alphaFun = ColorModelFactory.createGammaFunction(g, minAlpha, maxAlpha);
			}
			
			var cm = ColorModelFactory.createColorModel(PixelType.FLOAT32, colorMap, 0, min, max, alphaCountBand, alphaFun);
			renderer.setColorModel(cm);
			
			refreshViewers();
		}
		
		private void refreshViewers() {
			for (var viewer : qupath.getViewers())
				viewer.repaint();
		}
		
		
	}
	
	
	
	static class HotspotFinder {
		
		private ParameterList paramsHotspots = new ParameterList()
				.addIntParameter("nHotspots", "Number of hotspots to find", 1, null, "Specify the number of hotspots to identify; hotspots are peaks in the density map")
				.addDoubleParameter("minDensity", "Min object count", 1, null, "Specify the minimum density of objects to accept within a hotspot")
				.addBooleanParameter("allowOverlaps", "Allow overlapping hotspots", false, "Allow hotspots to overlap; if false, peaks are discarded if the hotspot radius overlaps with a 'hotter' hotspot")
				.addBooleanParameter("deletePrevious", "Delete existing hotspots", true, "Delete existing hotspot annotations with the same classification")
				;
		
		
		public void promptToFindHotspots(ImageData<BufferedImage> imageData, PixelClassifier densityMap, double radius) {
			
			if (imageData == null || densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}
			
			if (!Dialogs.showParameterDialog(title, paramsHotspots))
				return;
			
			int n = paramsHotspots.getIntParameterValue("nHotspots");
			double minDensity = paramsHotspots.getDoubleParameterValue("minDensity");
			boolean allowOverlapping = paramsHotspots.getBooleanParameterValue("allowOverlaps");
			boolean deleteExisting = paramsHotspots.getBooleanParameterValue("deletePrevious");
						
//			var response = Dialogs.showInputDialog(title, "How many hotspots do you want to find?", (double)lastHotspotCount);
//			boolean allowOverlapping = false;
//			if (response == null)
//				return;
//			int n = response.intValue();
//			lastHotspotCount = n;
			
			int channel = 0; // TODO: Allow changing channel (if multiple channels available)
			PathClass hotspotClass = getHotpotClass(densityMap.getMetadata().getOutputChannels().get(channel).getName());
			
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
				var server = PixelClassifierTools.createPixelClassificationServer(imageData, densityMap);
				DensityMaps.findHotspots(hierarchy, server, channel, selected, n, radius, minDensity, allowOverlapping, hotspotClass);
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		
	}
	
	static class ContourTracer {
		
		private ParameterList paramsTracing = new ParameterList()
				.addDoubleParameter("threshold", "Density threshold", 0.5, null, "Define the density threshold to detection regions")
				.addBooleanParameter("split", "Split regions", false, "Split disconnected regions into separate annotations");
		
		public void promptToTraceContours(ImageData<BufferedImage> imageData, PixelClassifier densityMap) {
			
			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image available!");
				return;
			}

			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
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
			
			int channel = 0;
			PathClass hotspotClass = getHotpotClass(densityMap.getMetadata().getOutputChannels().get(channel).getName());

			var server = PixelClassifierTools.createPixelClassificationServer(imageData, densityMap);
			DensityMaps.traceContours(hierarchy, server, channel, selected, threshold, doSplit, hotspotClass);
		}
		
	}
	
	static class DensityMapExporter {
		
		public void promptToSaveImage(ImageData<BufferedImage> imageData, PixelClassifier densityMap, PixelClassificationOverlay overlay) {
			
			if (imageData == null || densityMap == null || overlay == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			
			var densityMapServer = PixelClassifierTools.createPixelClassificationServer(imageData, densityMap);
			
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
					promptToSaveRawImage(densityMapServer);
				} else if (btColor.equals(response)) {
					// TODO: SUPPORT WRITING COLOR IMAGES
//					if (overlay instanceof BufferedImageOverlay)
//						promptToSaveColorImage((BufferedImageOverlay)overlay);
//					else
						Dialogs.showErrorMessage(title, "Not implemented yet!");
				} else if (btImageJ.equals(response)) {
					sendToImageJ(densityMapServer);
				}
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		public void promptToSaveRawImage(ImageServer<BufferedImage> densityMap) throws IOException {
			var file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
			if (file != null)
				QP.writeImage(densityMap, file.getAbsolutePath());
		}

		public void promptToSaveColorImage(QuPathViewer viewer, PixelClassificationOverlay overlay) {
			Dialogs.showErrorMessage(title, "Not implemented yet!");
//			var img = overlay.getRegionMap().values().iterator().next();
//			var file = Dialogs.promptToSaveFile(title, null, null, "PNG", ".png");
			
			/*
			 * TODO: RenderedImageServer to write the overlay only.
			 * Need to add:
			 * - Optionally skip base server
			 * - Background (white, black, transparent)
			 */
//			var imageData = viewer.getImageData();
//			var renderedServer = new RenderedImageServer.Builder(imageData)
//					.layers(overlay)
//					.store(viewer.getImageRegionStore())
//					.build();
				
//			if (file != null) {
//				try {
//					ImageIO.write(img, "PNG", file);
//				} catch (IOException e) {
//					Dialogs.showErrorMessage(title, "Unable to write file: " + e.getLocalizedMessage());
//					logger.error(e.getLocalizedMessage(), e);
//				}
//			}
		}

		public void sendToImageJ(ImageServer<BufferedImage> densityMap) throws IOException {
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
		
	}
	
	/**
	 * Get a classification to use for hotspots based upon an image channel / classification name.
	 * @param channelName
	 * @return
	 */
	static PathClass getHotpotClass(String channelName) {
		
		PathClass baseClass = channelName == null || channelName.isBlank() || DensityMaps.CHANNEL_ALL_OBJECTS.equals(channelName) ? null : PathClassFactory.getPathClass(channelName);
		return DensityMaps.getHotspotClass(baseClass);
		
	}
	
		
	/**
	 * Manage the density map associated with the image in a viewer.
	 * This is written to potentially support different kinds of classifier that require updates on a hierarchy change.
	 */
	static class HierarchyPixelClassifierManager implements PathObjectHierarchyListener, QuPathViewerListener {
		
		private final QuPathGUI qupath;
		
		private final PixelClassificationOverlay overlay;
		private final ObservableValue<PixelClassifier> classifier;
		// Cache a server
		private Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> classifierServerMap = Collections.synchronizedMap(new HashMap<>());
		
		private ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("density-maps", true));;
		private Map<QuPathViewer, Future<?>> tasks = Collections.synchronizedMap(new HashMap<>());
		
		private ObjectProperty<ImageServer<BufferedImage>> currentDensityMap = new SimpleObjectProperty<>();
		
		HierarchyPixelClassifierManager(QuPathGUI qupath, ObservableValue<PixelClassifier> classifier, ObservableValue<ImageInterpolation> interpolation, ImageRenderer renderer) {
			this.qupath = qupath;
			this.classifier = classifier;
			var options = qupath.getOverlayOptions();
			overlay = PixelClassificationOverlay.create(options, classifierServerMap, renderer);
			for (var viewer : qupath.getViewers()) {
				viewer.addViewerListener(this);
				viewer.setCustomPixelLayerOverlay(overlay);
				var hierarchy = viewer.getHierarchy();
				if (hierarchy != null)
					hierarchy.addPathObjectListener(this);
			}
			overlay.interpolationProperty().bind(interpolation);
			overlay.interpolationProperty().addListener((v, o, n) -> qupath.repaintViewers());
			overlay.setLivePrediction(true);
			classifier.addListener((v, o, n) -> updateDensityServers());
			updateDensityServers();
		}

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (event.isChanging())
				return;
			qupath.getViewers().stream().filter(v -> v.getHierarchy() == event.getHierarchy()).forEach(v -> updateDensityServer(v));
		}

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {
			
			if (imageDataOld != null)
				imageDataOld.getHierarchy().removePathObjectListener(this);
			
			if (imageDataNew != null) {
				imageDataNew.getHierarchy().addPathObjectListener(this);
			}
			updateDensityServer(viewer);
		}
		
		private void updateDensityServers() {
			classifierServerMap.clear(); // TODO: Check if this causes any flickering
			for (var viewer : qupath.getViewers())
				updateDensityServer(viewer);
		}
		
		private void updateDensityServer(QuPathViewer viewer) {
			if (Platform.isFxApplicationThread()) {
				synchronized (tasks) {
					var task = tasks.get(viewer);
					if (task != null && !task.isDone())
						task.cancel(true);
					if (!pool.isShutdown())
						task = pool.submit(() -> updateDensityServer(viewer));
					tasks.put(viewer, task);
				}
				return;
			}
			var imageData = viewer.getImageData();
			var classifier = this.classifier.getValue();
			if (imageData == null || classifier == null) {
				classifierServerMap.remove(imageData);
			} else {
				if (Thread.interrupted())
					return;
				// Create server with a unique ID, because it may change with the object hierarchy & we don't want caching to mask this
				var id = UUID.randomUUID().toString();
				var tempServer = PixelClassifierTools.createPixelClassificationServer(imageData, classifier, id, null, true);
				if (Thread.interrupted())
					return;
				if (viewer == qupath.getViewer())
					Platform.runLater(() -> currentDensityMap.set(tempServer));
				classifierServerMap.put(imageData, tempServer);
				if (viewer == qupath.getViewer())
					currentDensityMap.set(tempServer);
				viewer.repaint();
			}
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			imageDataChanged(viewer, viewer.getImageData(), null);
			viewer.removeViewerListener(this);
		}

		public void shutdown() {
			tasks.values().stream().forEach(t -> t.cancel(true));
			pool.shutdown();
			for (var viewer : qupath.getViewers()) {
				imageDataChanged(viewer, viewer.getImageData(), null);
				viewer.removeViewerListener(this);
				if (viewer.getCustomPixelLayerOverlay() == overlay)
					viewer.resetCustomPixelLayerOverlay();				
			}
			if (overlay != null) {
				overlay.stop();
			}
		}
		
		
	}
	
	

}
