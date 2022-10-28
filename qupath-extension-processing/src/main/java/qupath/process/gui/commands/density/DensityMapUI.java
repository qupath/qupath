/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands.density;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableStringValue;
import javafx.event.ActionEvent;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;
import qupath.process.gui.commands.ui.SaveResourcePaneBuilder;

/**
 * UI elements associated with density maps.
 * 
 * @author Pete Bankhead
 */
public class DensityMapUI {
	
	private static final Logger logger = LoggerFactory.getLogger(DensityMapUI.class);
	
	private static final String title = "Density maps";
	
	/**
	 * Create a pane that can be used to save a {@link DensityMapBuilder}, with standardized display and prompts.
	 * @param project
	 * @param densityMap
	 * @param savedName
	 * @return
	 */
	public static Pane createSaveDensityMapPane(ObjectExpression<Project<BufferedImage>> project, ObjectExpression<DensityMapBuilder> densityMap, StringProperty savedName) {
		logger.trace("Creating 'Save density map' pane");

		var tooltipTextYes = "Save density map in the current project - this is required to use the density map later (e.g. to create objects, measurements)";
		var tooltipTextNo = "Cannot save a density map outside a project. Please create a project to save the classifier.";
		var tooltipText = Bindings
				.when(project.isNull())
				.then(Bindings.createStringBinding(() -> tooltipTextNo, project))
				.otherwise(Bindings.createStringBinding(() -> tooltipTextYes, project));

		return new SaveResourcePaneBuilder<>(DensityMapBuilder.class, densityMap)
				.project(project)
				.labelText("Save map")
				.textFieldPrompt("Enter name")
				.savedName(savedName)
				.tooltip(tooltipText)
				.title("Density maps")
				.build();
	}


	/**
	 * Supported input objects.
	 */
	static enum DensityMapObjects {

		DETECTIONS(PathObjectFilter.DETECTIONS_ALL),
		CELLS(PathObjectFilter.CELLS),
		POINT_ANNOTATIONS(
				PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS)
				.and(PathObjectPredicates.filter(PathObjectFilter.ROI_POINT)));

		private final PathObjectPredicate predicate;

		private DensityMapObjects(PathObjectFilter filter) {
			this(PathObjectPredicates.filter(filter));
		}

		private DensityMapObjects(PathObjectPredicate predicate) {
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

	static class MinMax {

		private float minValue = Float.POSITIVE_INFINITY;
		private float maxValue = Float.NEGATIVE_INFINITY;

		private void update(float val) {
			if (Float.isNaN(val))
				return;
			if (val < minValue)
				minValue = val;
			if (val > maxValue)
				maxValue = val;
		}
		
		public double getMinValue() {
			return minValue;
		}
		
		public double getMaxValue() {
			return maxValue;
		}

	}
	
	
	/**
	 * Get the min and max values for an {@link ImageServer}.
	 * Since this involves requesting all tiles at the highest resolution, it should be used with caution.
	 * @param server
	 * @return
	 * @throws IOException
	 */
	static List<MinMax> getMinMax(ImageServer<BufferedImage> server) throws IOException {
		return MinMaxFinder.getMinMax(server, -1, 0);
	}
	
	

	static class MinMaxFinder {

		private static Map<String, List<MinMax>> cache = Collections.synchronizedMap(new HashMap<>());

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
		static List<MinMax> getMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
			String key = server.getPath() + "?count=" + countBand + "&minCount=" + minCount;
			var minMax = cache.get(key);
			if (minMax == null) {
				logger.trace("Calculating min & max for {}", server);
				minMax = calculateMinMax(server, countBand, minCount);
				cache.put(key, minMax);
			} else
				logger.trace("Using cached min & max for {}", server);
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
							if (pixels[i] > minCount)
								minMax.update(pixels[i]);
						}					
					} else {
						for (int i = 0; i < w*h; i++) {
							if (countPixels[i] > minCount)
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
				var imgTile = server.readRegion(region);
				if (imgTile != null)
					map.put(region, imgTile);
			}
			return map;
		}

	}

	/**
	 * Ignore classification (accept all objects).
	 * Generated with a UUID for uniqueness, and because it should not be serialized.
	 */
	public static final PathClass ANY_CLASS = PathClass.fromString(UUID.randomUUID().toString());

	/**
	 * Accept any positive classification, including 1+, 2+, 3+.
	 * Generated with a UUID for uniqueness, and because it should not be serialized.
	 */
	public static final PathClass ANY_POSITIVE_CLASS = PathClass.fromString(UUID.randomUUID().toString());

	
	
	static Action createDensityMapAction(String text, ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder, ObservableStringValue densityMapName,
			ObservableBooleanValue disableButtons, DensityMapButtonCommand consumer, String tooltip) {
		var action = new Action(text, e -> consumer.fire(e, imageData.get(), builder.get(), densityMapName.get()));
		if (tooltip != null)
			action.setLongText(tooltip);
		if (disableButtons != null)
			action.disabledProperty().bind(disableButtons);
		return action;
	}

	
	/**
	 * Abstract base class for an action that operates on a density map.
	 * Only intended for internal use.
	 */
	private abstract static class DensityMapButtonCommand {
		
		protected QuPathGUI qupath;
		protected ObjectExpression<PixelClassificationOverlay> overlay;
		
		private BooleanProperty previewThreshold = new SimpleBooleanProperty(true);
		private ColorModel previousColorModel;
		
		public DensityMapButtonCommand(QuPathGUI qupath, ObjectExpression<PixelClassificationOverlay> overlay) {
			this.qupath = qupath;
			this.overlay = overlay;
			previewThreshold.addListener((v, o, n) -> updateRenderer());
		}
				
		protected ColorModelRenderer getRenderer() {
			var o = overlay == null ? null : overlay.get();
			if (o == null)
				return null;
			var renderer = o == null ? null : o.getRenderer();
			if (renderer == null && !o.rendererProperty().isBound()) {
				renderer = new ColorModelRenderer(null);
				o.setRenderer(renderer);
			}
			return renderer instanceof ColorModelRenderer ? (ColorModelRenderer)renderer : null;
		}
		
		/**
		 * Attempt to get the owner window for the button firing the event.
		 * @param event
		 * @return
		 */
		protected Window getOwner(ActionEvent event) {
			var source = event.getSource();
			if (source instanceof Node) {
				var scene = ((Node)source).getScene();
				return scene == null ? null : scene.getWindow();
			}
			return null;
		}
		
		/**
		 * Custom event handling for the command.
		 * @param event
		 * @param imageData
		 * @param builder
		 * @param densityMapName
		 */
		public abstract void fire(ActionEvent event, ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName);
		
		/**
		 * Save the current colormodel for {@link #getRenderer()}, if available.
		 */
		protected void saveColorModel() {
			var renderer = getRenderer();
			if (renderer != null)
				previousColorModel = renderer.getColorModel();
		}
		
		/**
		 * Restore the last saved colormodel for {@link #getRenderer()}, if available.
		 */
		protected void restoreColorModel() {
			var renderer = getRenderer();
			if (renderer != null) {
				renderer.setColorModel(previousColorModel);
				qupath.repaintViewers();
			}
		}
		
		protected void updateRenderer() {
			if (!previewThreshold.get()) {
				restoreColorModel();
				return;
			}
			var renderer = getRenderer();
			if (renderer != null)
				customUpdateRenderer(renderer);
		}
		
		/**
		 * Provide custom updates to the colormodel.
		 * @param renderer 
		 */
		protected abstract void customUpdateRenderer(ColorModelRenderer renderer);
		
		
		/**
		 * Update the renderer to display a threshold.
		 * @param renderer
		 * @param aboveThreshold
		 * @param threshold
		 */
		protected void updateRenderer(ColorModelRenderer renderer, PathClass aboveThreshold, ThresholdColorModels.ColorModelThreshold threshold) {
			var transparent = ColorToolsAwt.getCachedColor(Integer.valueOf(0), true);
			var above = ColorToolsAwt.getCachedColor(aboveThreshold.getColor());
			var colorModel = new ThresholdColorModels.ThresholdColorModel(threshold, transparent, transparent, above);
			
			renderer.setColorModel(colorModel);
			qupath.repaintViewers();
		}
		
		
		/**
		 * Create a standard pane for adjusting the overlay display.
		 * @return
		 */
		protected TitledPane createOverlayPane() {
			var paneOverlay = new GridPane();

			int row2 = 0;

			var cbLayer = new CheckBox("Show overlay");
			cbLayer.selectedProperty().bindBidirectional(qupath.getOverlayOptions().showPixelClassificationProperty());
			PaneTools.addGridRow(paneOverlay, row2++, 0, "Show or hide the overlay", cbLayer, cbLayer);	

			var cbPreviewThreshold = new CheckBox("Preview threshold");
			cbPreviewThreshold.selectedProperty().bindBidirectional(previewThreshold);
			PaneTools.addGridRow(paneOverlay, row2++, 0, "Override the main density map overlay to preview the current thresholds", cbPreviewThreshold, cbPreviewThreshold);	

			var cbDetections = new CheckBox("Show detections");
			cbDetections.selectedProperty().bindBidirectional(qupath.getOverlayOptions().showDetectionsProperty());
			PaneTools.addGridRow(paneOverlay, row2++, 0, "Show or hide detections on the image", cbDetections, cbDetections);	

			var sliderOpacity = new Slider(0.0, 1.0, 0.5);
			sliderOpacity.valueProperty().bindBidirectional(qupath.getOverlayOptions().opacityProperty());
			var labelOpacity = new Label("Opacity");
			PaneTools.addGridRow(paneOverlay, row2++, 0, "Control the overlay opacity", labelOpacity, sliderOpacity);	

			
			PaneTools.setToExpandGridPaneWidth(paneOverlay, sliderOpacity, cbLayer, cbDetections, cbPreviewThreshold);
			paneOverlay.setHgap(5);
			paneOverlay.setVgap(5);
			
			var titledOverlay = new TitledPane("Overlay", paneOverlay);
			titledOverlay.setExpanded(false);
			PaneTools.simplifyTitledPane(titledOverlay, true);
			
			titledOverlay.heightProperty().addListener((v, o, n) -> titledOverlay.getScene().getWindow().sizeToScene());
			return titledOverlay;
		}
		
	}


	/**
	 * Find (circular) hotspots within a density map.
	 */
	static class HotspotFinder extends DensityMapButtonCommand {
		
		private IntegerProperty nHotspots = new SimpleIntegerProperty(1);
		private DoubleProperty thresholdCounts = new SimpleDoubleProperty(1);
		private BooleanProperty deletePrevious = new SimpleBooleanProperty(false);
		private BooleanProperty strictPeaks = new SimpleBooleanProperty(true);
		
		private int band = 0;
		private int bandCounts = -1;
		private PathClass aboveThreshold;
		
		public HotspotFinder(QuPathGUI qupath, ObjectExpression<PixelClassificationOverlay> overlay) {
			super(qupath, overlay);
			thresholdCounts.addListener((v, o, n) -> updateRenderer());
		}
		
		boolean showDialog(ImageServer<BufferedImage> densityServer, ColorModelRenderer renderer, Window owner) throws IOException {
						
			int tfWidth = 6;
			
			// Slider for the number of hotspots
			var labelNum = new Label("Num hotspots");
			var sliderNum = new Slider(1, 10, 1);
			sliderNum.setMajorTickUnit(1);
			sliderNum.setMinorTickCount(0);
			sliderNum.setSnapToTicks(true);
			sliderNum.valueProperty().bindBidirectional(nHotspots);
			var tfNum = new TextField();
			tfNum.setPrefColumnCount(tfWidth);
			GuiTools.bindSliderAndTextField(sliderNum, tfNum, false, 0);
			labelNum.setLabelFor(sliderNum);
			
			// Slider for the minimum counts
			var labelCounts = new Label("Min object count");
			var minMaxAll = getMinMax(densityServer);
			int max = (int)Math.ceil(minMaxAll.get(bandCounts).maxValue);
			var sliderCounts = new Slider(0, max, 1);
			sliderCounts.setMajorTickUnit(10);
			sliderCounts.setMinorTickCount(9);
			sliderCounts.setSnapToTicks(true);
			sliderCounts.valueProperty().bindBidirectional(thresholdCounts);
			var tfCounts = new TextField();
			tfCounts.setPrefColumnCount(tfWidth);
			GuiTools.bindSliderAndTextField(sliderCounts, tfCounts, false, 0);
			labelCounts.setLabelFor(sliderCounts);
			
			// Other options
			var cbPeaks = new CheckBox("Density peaks only");
			cbPeaks.selectedProperty().bindBidirectional(strictPeaks);
			
			var cbDeletePrevious = new CheckBox("Delete existing hotspots");
			cbDeletePrevious.selectedProperty().bindBidirectional(deletePrevious);
			
			// Create pane
			var pane = new GridPane();
			int row = 0;

			PaneTools.addGridRow(pane, row++, 0, "Maximum number of hotspots to create", labelNum, sliderNum, tfNum);
			
			PaneTools.addGridRow(pane, row++, 0, "The minimum number of objects required.\n"
					+ "This can eliminate hotspots based on just 1 or 2 objects.", labelCounts, sliderCounts, tfCounts);

			PaneTools.addGridRow(pane, row++, 0, "Limit hotspots to peaks in the density map only.\n"
					+ "This is a stricter criteria that can result in fewer hotspots being found, however those that *are* found are more distinct.", cbPeaks, cbPeaks, cbPeaks);

			PaneTools.addGridRow(pane, row++, 0, "Delete existing hotspots similar to those being created.", cbDeletePrevious, cbDeletePrevious, cbDeletePrevious);
			
			PaneTools.setToExpandGridPaneWidth(sliderNum, sliderCounts, cbDeletePrevious, cbPeaks);
			
						
			var titledPane = new TitledPane("Hotspot parameters", pane);
			titledPane.setExpanded(true);
			titledPane.setCollapsible(false);
			PaneTools.simplifyTitledPane(titledPane, true);
			
			// Opacity slider
			var paneMain = new BorderPane(titledPane);
			if (renderer != null) {
				paneMain.setBottom(createOverlayPane());
				updateRenderer();
			}

			pane.setVgap(5);
			pane.setHgap(5);
			
			if (Dialogs.builder()
				.modality(Modality.WINDOW_MODAL)
				.content(paneMain)
				.title(title)
				.owner(owner)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.build()
				.showAndWait()
				.orElse(ButtonType.CANCEL) != ButtonType.APPLY)
				return false;
						
			return true;
		}
		
		@Override
		protected void customUpdateRenderer(ColorModelRenderer renderer) {
			if (renderer == null || aboveThreshold == null || bandCounts < 0)
				return;
			ThresholdColorModels.ColorModelThreshold threshold = ThresholdColorModels.ColorModelThreshold.create(DataBuffer.TYPE_FLOAT, bandCounts, thresholdCounts.get());
			this.updateRenderer(renderer, aboveThreshold, threshold);
		}
				
		@Override
		public void fire(ActionEvent event, ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}
			
			var densityServer = builder.buildServer(imageData);

			ColorModelRenderer renderer = getRenderer();
			saveColorModel();

			try {
				bandCounts = densityServer.nChannels() - 1;
				var channel = densityServer.getChannel(band);
				aboveThreshold = PathClass.fromString(channel.getName(), channel.getColor());
				
				if (!showDialog(densityServer, renderer, getOwner(event)))
					return;
				
				
				double radius = builder.buildParameters().getRadius();

				int numHotspots = nHotspots.get();
				double minDensity = thresholdCounts.get();
				boolean peaksOnly = strictPeaks.get();
				boolean deleteExisting = deletePrevious.get();

				try {
					var hierarchy = imageData.getHierarchy();
					DensityMaps.findHotspots(hierarchy, densityServer, band, numHotspots, radius, minDensity, aboveThreshold, deleteExisting, peaksOnly);
					
					if (densityMapName != null) {
						imageData.getHistoryWorkflow().addStep(
								new DefaultScriptableWorkflowStep("Density map find hotspots",
										String.format("findDensityMapHotspots(\"%s\", %d, %d, %f, %s, %s)",
												densityMapName,
												band, numHotspots,
												minDensity, deleteExisting, peaksOnly)
										)
								);
					} else
						logger.warn("Density map not saved - cannot log step to workflow");
					
				} catch (IOException e) {
					Dialogs.showErrorNotification(title, e);
				}
				
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
				return;
			} finally {
				restoreColorModel();
				aboveThreshold = null;
			}
			
		}


	}	


	/**
	 * Threshold a density map to identify high-density regions, optionally eliminating areas containing low object counts.
	 */
	static class ContourTracer extends DensityMapButtonCommand {

		private DoubleProperty threshold = new SimpleDoubleProperty(Double.NaN);
		private DoubleProperty thresholdCounts = new SimpleDoubleProperty(1);
		
		private BooleanProperty deleteExisting = new SimpleBooleanProperty(false);
		private BooleanProperty split = new SimpleBooleanProperty(false);
		private BooleanProperty select = new SimpleBooleanProperty(false);
		
		private PathClass aboveThreshold;
		private int bandThreshold = 0;
		private int bandCounts = -1;
		
		ContourTracer(QuPathGUI qupath, ObjectExpression<PixelClassificationOverlay> overlay) {
			super(qupath, overlay);
			threshold.addListener((v, o, n) -> updateRenderer());
			thresholdCounts.addListener((v, o, n) -> updateRenderer());
		}
		
		boolean showDialog(ImageServer<BufferedImage> densityServer, ColorModelRenderer renderer, Window owner) throws IOException {
					
			var minMaxAll = getMinMax(densityServer);
			
			var minMax = minMaxAll.get(bandThreshold);
			var slider = new Slider(0, (int)Math.ceil(minMax.getMaxValue()), (int)(minMax.getMaxValue()/2.0));
			slider.setMinorTickCount((int)(slider.getMax() + 1));
			var tfThreshold = new TextField();
			tfThreshold.setPrefColumnCount(6);
			GuiTools.bindSliderAndTextField(slider, tfThreshold, false, 2);
			double t = threshold.get();
			if (!Double.isFinite(t) || t > slider.getMax() || t < slider.getMin())
				threshold.set(slider.getValue());
			slider.valueProperty().bindBidirectional(threshold);
			
			int row = 0;
			var pane = new GridPane();
			
			var labelThreshold = new Label("Density threshold");
			PaneTools.addGridRow(pane, row++, 0, "Threshold to identify high-density regions.", labelThreshold, slider, tfThreshold);

			boolean includeCounts = densityServer.nChannels() > 1;
			if (includeCounts) {
				bandCounts = densityServer.nChannels()-1;
				var minMaxCounts = minMaxAll.get(bandCounts);
				int max = (int)Math.ceil(minMaxCounts.getMaxValue());
				var sliderCounts = new Slider(0, max, (int)(minMaxCounts.getMaxValue()/2.0));
				sliderCounts.setMajorTickUnit(10);
				sliderCounts.setMinorTickCount(9);
				sliderCounts.setSnapToTicks(true);
				
				var tfThresholdCounts = new TextField();
				tfThresholdCounts.setPrefColumnCount(6);
				GuiTools.bindSliderAndTextField(sliderCounts, tfThresholdCounts, false, 0);
				double tc = thresholdCounts.get();
				if (tc > sliderCounts.getMax())
					thresholdCounts.set(1);
				sliderCounts.valueProperty().bindBidirectional(thresholdCounts);				
				
				var labelCounts = new Label("Min object count");
				PaneTools.addGridRow(pane, row++, 0, "The minimum number of objects required.\n"
						+ "Used in combination with the density threshold to remove outliers (i.e. high density based on just 1 or 2 objects).", labelCounts, sliderCounts, tfThresholdCounts);
			}
			
			var cbDeleteExisting = new CheckBox("Delete existing similar annotations");
			cbDeleteExisting.selectedProperty().bindBidirectional(deleteExisting);
			PaneTools.addGridRow(pane, row++, 0, "Delete existing annotations that share the same classification as the new annotations", cbDeleteExisting, cbDeleteExisting, cbDeleteExisting);
			
			var cbSplit = new CheckBox("Split new annotations");
			cbSplit.selectedProperty().bindBidirectional(split);
			PaneTools.addGridRow(pane, row++, 0, "Split new, multi-part annotations into separate polygons", cbSplit, cbSplit, cbSplit);

			var cbSelect = new CheckBox("Select new annotations");
			cbSelect.selectedProperty().bindBidirectional(select);
			PaneTools.addGridRow(pane, row++, 0, "Automatically set new annotations to be selected."
					+ "\nThis is useful if the next step involves manipulating the annotations (e.g. setting another classification).", cbSelect, cbSelect, cbSelect);
			
			PaneTools.setToExpandGridPaneWidth(slider, cbDeleteExisting, cbSplit, cbSelect);
			
			var titledPane = new TitledPane("Threshold parameters", pane);
			titledPane.setExpanded(true);
			titledPane.setCollapsible(false);
			PaneTools.simplifyTitledPane(titledPane, true);

			
			// Opacity slider
			var paneMain = new BorderPane(titledPane);
			if (renderer != null) {
				paneMain.setBottom(createOverlayPane());
				updateRenderer();
			}

			pane.setVgap(5);
			pane.setHgap(5);
			
			if (Dialogs.builder()
				.modality(Modality.WINDOW_MODAL)
				.content(paneMain)
				.title(title)
				.owner(owner)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.build()
				.showAndWait()
				.orElse(ButtonType.CANCEL) != ButtonType.APPLY)
				return false;
			
			return true;
		}
		
		
		@Override
		protected void customUpdateRenderer(ColorModelRenderer renderer) {
			if (renderer == null || aboveThreshold == null)
				return;
			
			// Create a translucent overlay showing thresholded regions
			ThresholdColorModels.ColorModelThreshold colorModelThreshold;
			if (bandCounts >= 0) {
				colorModelThreshold = ThresholdColorModels.ColorModelThreshold.create(DataBuffer.TYPE_FLOAT, Map.of(bandThreshold, threshold.get(), bandCounts, thresholdCounts.get()));
			} else {
				colorModelThreshold = ThresholdColorModels.ColorModelThreshold.create(DataBuffer.TYPE_FLOAT, bandThreshold, threshold.get());
			}
			updateRenderer(renderer, aboveThreshold, colorModelThreshold);
		}
		
		
		@Override
		public void fire(ActionEvent event, ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No image available!");
				return;
			}
			
			var densityServer = builder.buildServer(imageData);

			ColorModelRenderer renderer = getRenderer();
			saveColorModel();

			try {
				bandThreshold = 0;
				bandCounts = -1;
						
				var channel = densityServer.getChannel(bandThreshold);
				aboveThreshold = PathClass.fromString(channel.getName(), channel.getColor());

				if (!showDialog(densityServer, renderer, getOwner(event)))
					return;
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
				return;
			} finally {
				restoreColorModel();
				aboveThreshold = null;
			}
			
			double countThreshold = this.thresholdCounts.get();
			double threshold = this.threshold.get();
			boolean doDelete = deleteExisting.get();
			boolean doSplit = split.get();
			boolean doSelect = select.get();
			
			List<CreateObjectOptions> options = new ArrayList<>();
			if (doDelete)
				options.add(CreateObjectOptions.DELETE_EXISTING);
			if (doSplit)
				options.add(CreateObjectOptions.SPLIT);
			if (doSelect)
				options.add(CreateObjectOptions.SELECT_NEW);

			Map<Integer, Double> thresholds;
			if (bandCounts > 0)
				thresholds = Map.of(bandThreshold, threshold, bandCounts, countThreshold);
			else
				thresholds = Map.of(bandThreshold, threshold);
			
			var pathClassName = densityServer.getChannel(0).getName();
			
			try {
				DensityMaps.threshold(imageData.getHierarchy(), densityServer, thresholds, pathClassName, options.toArray(CreateObjectOptions[]::new));
			} catch (IOException e1) {
				Dialogs.showErrorMessage(title, e1);
				return;
			}
			
			if (densityMapName != null) {
				String optionsString = "";
				if (!options.isEmpty())
					optionsString = ", " + options.stream().map(o -> "\"" + o.name() + "\"").collect(Collectors.joining(", "));
				
				// Groovy-friendly map
				var thresholdString = "[" + thresholds.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")) + "]";
	
				imageData.getHistoryWorkflow().addStep(
						new DefaultScriptableWorkflowStep("Density map create annotations",
								String.format("createAnnotationsFromDensityMap(\"%s\", %s, \"%s\"%s)",
										densityMapName,
										thresholdString.toString(),
										pathClassName,
										optionsString)
								)
						);
			}
			
		}

	}
	
	
	/**
	 * Export the raw values or rendered image of a density map.
	 */
	static class DensityMapExporter extends DensityMapButtonCommand {

		public DensityMapExporter(QuPathGUI qupath, ObjectExpression<PixelClassificationOverlay> overlay) {
			super(qupath, overlay);
		}

		@Override
		public void fire(ActionEvent event, ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}

			var densityMapServer = builder.buildServer(imageData);

			var dialog = new Dialog<ButtonType>();
			dialog.setTitle(title);
			dialog.setHeaderText("How do you want to export the density map?");
			dialog.setContentText("Choose 'Raw values' of 'Send to ImageJ' if you need the original counts, or 'Color overlay' if you want to keep the same visual appearance.");
			var btOrig = new ButtonType("Raw values");
			var btColor = new ButtonType("Color overlay");
			var btImageJ = new ButtonType("Send to ImageJ");
			dialog.getDialogPane().getButtonTypes().setAll(btOrig, btColor, btImageJ, ButtonType.CANCEL);

			var response = dialog.showAndWait().orElse(ButtonType.CANCEL);
			try {
				if (btOrig.equals(response)) {
					promptToSaveRawImage(imageData, densityMapServer, densityMapName);
				} else if (btColor.equals(response)) {
					promptToSaveColorImage(densityMapServer, null); // Counting on color model being set!
				} else if (btImageJ.equals(response)) {
					sendToImageJ(densityMapServer);
				}
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		@Override
		protected void customUpdateRenderer(ColorModelRenderer renderer) {
			// Does nothing
		}

		private void promptToSaveRawImage(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> densityMap, String densityMapName) throws IOException {
			var file = Dialogs.promptToSaveFile(title, null, densityMapName, "ImageJ tif", ".tif");
			if (file != null) {
				try {
					QP.writeImage(densityMap, file.getAbsolutePath());
					// Log to workflow
					if (densityMapName != null && !densityMapName.isBlank()) {
						var path = file.getAbsolutePath();
						imageData.getHistoryWorkflow().addStep(
								new DefaultScriptableWorkflowStep("Write density map image",
										String.format("writeDensityMapImage(\"%s\", \"%s\")", densityMapName, path)
										)
								);
					}
				} catch (IOException e) {
					Dialogs.showErrorMessage("Save prediction", e);
				}
			}
		}

		private void promptToSaveColorImage(ImageServer<BufferedImage> densityMap, ColorModel colorModel) throws IOException {
			var server = RenderedImageServer.createRenderedServer(densityMap, new ColorModelRenderer(colorModel));
			File file;
			String fmt, ext;
			if (server.nResolutions() == 1 && server.nTimepoints() == 1 && server.nZSlices() == 1) {
				fmt = "PNG";
				ext = ".png";				
			} else {
				fmt = "ImageJ tif";
				ext = ".tif";
			}
			file = Dialogs.promptToSaveFile(title, null, null, fmt, ext);
			if (file != null) {
				QP.writeImage(server, file.getAbsolutePath());
			}
		}

		private void sendToImageJ(ImageServer<BufferedImage> densityMap) throws IOException {
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
	 * Create a pane containing standardized buttons associated with processing a density map (find hotspots, threshold, export map).
	 * 
	 * Note that because density maps need to reflect the current hierarchy, but should be relatively fast to compute (at low resolution), 
	 * the full density map is generated upon request.
	 * 
	 * @param qupath QuPathGUI instance, used to identify viewers
	 * @param imageData expression returning the {@link ImageData} to use
	 * @param builder expression returning the {@link DensityMapBuilder} to use
	 * @param densityMapName name of the density map, if it has been saved (otherwise null). This is used for writing workflow steps.
	 * @param overlay optional overlay; if present, this could be used by commands to update display (e.g. by temporarily setting a color model).
	 * @param enableUnsavedButton add button to enable buttons even if no name is available (indicating the map has not been saved).
	 * @return a pane that may be added to a stage
	 */
	public static Pane createButtonPane(QuPathGUI qupath, ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder, 
			StringExpression densityMapName, ObjectExpression<PixelClassificationOverlay> overlay, boolean enableUnsavedButton) {
		logger.trace("Creating button pane");
		
		BooleanProperty allowWithoutSaving = new SimpleBooleanProperty(!enableUnsavedButton);
		
		BooleanBinding disableButtons = imageData.isNull()
				.or(builder.isNull())
				.or(densityMapName.isEmpty().and(allowWithoutSaving.not()));
		
		var actionHotspots = createDensityMapAction("Find hotspots", imageData, builder, densityMapName, disableButtons, new HotspotFinder(qupath, overlay),
				"Find the hotspots in the density map with highest values");
		var btnHotspots = ActionTools.createButton(actionHotspots, false);

		// TODO: Don't provide QuPath in this way...
		var actionThreshold = createDensityMapAction("Threshold", imageData, builder, densityMapName, disableButtons, new ContourTracer(qupath, overlay),
				"Threshold to identify high-density regions");
		var btnThreshold = ActionTools.createButton(actionThreshold, false);

		var actionExport = createDensityMapAction("Export map", imageData, builder, densityMapName, disableButtons, new DensityMapExporter(qupath, overlay),
				"Export the density map as an image");
		var btnExport = ActionTools.createButton(actionExport, false);

		var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnThreshold, btnExport);
//		buttonPane.setHgap(hGap);
		PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport, btnThreshold);
		
		var pane = new BorderPane(buttonPane);
		
		// Add some more options
		if (enableUnsavedButton) {
			var menu = new ContextMenu();
			var miWithoutSaving = new CheckMenuItem("Enable buttons for unsaved density maps");
			miWithoutSaving.selectedProperty().bindBidirectional(allowWithoutSaving);
			
			menu.getItems().addAll(
					miWithoutSaving
					);
			var btnAdvanced = GuiTools.createMoreButton(menu, Side.RIGHT);
			pane.setRight(btnAdvanced);
		}
		return pane;
	}
	

	/**
	 * Color models to quickly display the result of thresholding.
	 * 
	 * TODO: Generalize these classes for use elsewhere and move to ColorModels or ColorModelFactory if they prove useful.
	 */
	static class ThresholdColorModels {
	
		abstract static class ColorModelThreshold {
			
			private int transferType;
						
			static ColorModelThreshold create(int transferType, int band, double threshold) {
				return new SingleBandThreshold(transferType, band, threshold);
			}
			
			static ColorModelThreshold create(int transferType, Map<Integer, ? extends Number> thresholds) {
				if (thresholds.size() == 1) {
					var entry = thresholds.entrySet().iterator().next();
					return create(transferType, entry.getKey(), entry.getValue().doubleValue());
				}
				return new MultiBandThreshold(transferType, thresholds);
			}
			
			ColorModelThreshold(int transferType) {
				this.transferType = transferType;
			}
			
			
			protected int getTransferType() {
				return transferType;
			}
			
			protected int getBits() {
				return DataBuffer.getDataTypeSize(transferType);
			}
			
			protected double getValue(Object input, int band) {
					
				if (input instanceof float[])
					return ((float[])input)[band];
				
				if (input instanceof double[])
					return ((double[])input)[band];
				
				if (input instanceof int[])
					return ((int[])input)[band];
	
				if (input instanceof byte[])
					return ((byte[])input)[band] & 0xFF;
	
				if (input instanceof short[]) {
					int val = ((short[])input)[band];
					if (transferType == DataBuffer.TYPE_SHORT)
						return val;
					return val & 0xFFFF;
				}
				
				return Double.NaN;
			}
			
			protected abstract int threshold(Object input);
			
		}
		
		static class SingleBandThreshold extends ColorModelThreshold {
			
			private int band;
			private double threshold;
			
			SingleBandThreshold(int transferType, int band, double threshold) {
				super(transferType);
				this.band = band;
				this.threshold = threshold;
			}
			
			@Override
			protected int threshold(Object input) {
				return Double.compare(getValue(input, band), threshold);
			}			
			
		}
		
		static class MultiBandThreshold  extends ColorModelThreshold {
			
			private int n;
			private int[] bands;
			private double[] thresholds;
			
			MultiBandThreshold(int transferType, Map<Integer, ? extends Number> thresholds) {
				super(transferType);
				this.n = thresholds.size();
				this.bands = new int[n];
				this.thresholds = new double[n];
				int i = 0;
				for (var entry : thresholds.entrySet()) {
					bands[i] = entry.getKey();
					this.thresholds[i] = entry.getValue().doubleValue();
					i++;
				}
			}
			
			@Override
			protected int threshold(Object input) {
				int sum = 0;
				for (int i = 0; i < n; i++) {
					double val = getValue(input, bands[i]);
					int cmp = Double.compare(val, thresholds[i]);
					if (cmp < 0)
						return -1;
					sum += cmp;
				}
				return sum;
			}	
			
		}
		
		
		static class ThresholdColorModel extends ColorModel {
	
			private ColorModelThreshold threshold;
			
			protected Color above;
			protected Color equals;
			protected Color below;
	
			public ThresholdColorModel(ColorModelThreshold threshold, Color below, Color equals, Color above) {
				super(threshold.getBits());
				this.threshold = threshold;
				this.below = below;
				this.equals = equals;
				this.above = above;
			}
	
			@Override
			public int getRed(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getGreen(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getBlue(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getAlpha(int pixel) {
				throw new IllegalArgumentException();
			}
			
			@Override
			public int getRed(Object pixel) {
				return getColor(pixel).getRed();
			}
	
			@Override
			public int getGreen(Object pixel) {
				return getColor(pixel).getGreen();
			}
	
			@Override
			public int getBlue(Object pixel) {
				return getColor(pixel).getBlue();
			}
	
			@Override
			public int getAlpha(Object pixel) {
				return getColor(pixel).getAlpha();
			}
			
			public Color getColor(Object input) {
				int cmp = threshold.threshold(input);
				if (cmp > 0)
					return above;
				else if (cmp < 0)
					return below;
				return equals;
			}
			
			@Override
			public boolean isCompatibleRaster(Raster raster) {
				return raster.getTransferType() == threshold.getTransferType();
			}
			
			
		}
		
		
	}
	

}
