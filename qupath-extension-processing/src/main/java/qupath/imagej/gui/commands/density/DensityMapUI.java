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

package qupath.imagej.gui.commands.density;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.StringProperty;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.Pane;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.gui.commands.ui.SaveResourcePaneBuilder;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;

/**
 * UI elements associated with density maps.
 * 
 * @author Pete Bankhead
 */
public class DensityMapUI {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapUI.class);
	
	private final static String title = "Density maps";
	
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
				.labelText("Density map name")
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
				var imgTile = server.readBufferedImage(region);
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
	public static final PathClass ANY_CLASS = PathClassFactory.getPathClass(UUID.randomUUID().toString());

	/**
	 * Accept any positive classification, including 1+, 2+, 3+.
	 * Generated with a UUID for uniqueness, and because it should not be serialized.
	 */
	public static final PathClass ANY_POSITIVE_CLASS = PathClassFactory.getPathClass(UUID.randomUUID().toString());




	static class HotspotFinder implements BiConsumer<ImageData<BufferedImage>, DensityMapBuilder> {

		private ParameterList paramsHotspots = new ParameterList()
				.addIntParameter("nHotspots", "Number of hotspots to find", 1, null, "Specify the number of hotspots to identify; hotspots are peaks in the density map")
				.addDoubleParameter("minDensity", "Min object count", 1, null, "Specify the minimum density of objects to accept within a hotspot")
				.addBooleanParameter("allowOverlaps", "Allow overlapping hotspots", false, "Allow hotspots to overlap; if false, peaks are discarded if the hotspot radius overlaps with a 'hotter' hotspot")
				.addBooleanParameter("deletePrevious", "Delete existing hotspots", true, "Delete existing hotspot annotations with the same classification")
				;

		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}

			if (!Dialogs.showParameterDialog(title, paramsHotspots))
				return;

			double radius = builder.buildParameters().getRadius();

			int n = paramsHotspots.getIntParameterValue("nHotspots");
			double minDensity = paramsHotspots.getDoubleParameterValue("minDensity");
			boolean allowOverlapping = paramsHotspots.getBooleanParameterValue("allowOverlaps");
			boolean deleteExisting = paramsHotspots.getBooleanParameterValue("deletePrevious");

			int channel = 0; // TODO: Allow changing channel (if multiple channels available)

			var hierarchy = imageData.getHierarchy();
			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());

			try {
				var server = builder.buildServer(imageData);

				// Remove existing hotspots with the same classification
				PathClass hotspotClass = getHotpotClass(server.getChannel(channel).getName());
				if (deleteExisting) {
					var hotspotClass2 = hotspotClass;
					var existing = hierarchy.getAnnotationObjects().stream()
							.filter(a -> a.getPathClass() == hotspotClass2)
							.collect(Collectors.toList());
					hierarchy.removeObjects(existing, true);
				}

				DensityMaps.findHotspots(hierarchy, server, channel, selected, n, radius, minDensity, allowOverlapping, hotspotClass);
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}


	}



	static Action createDensityMapAction(String text, ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder,
			BiConsumer<ImageData<BufferedImage>, DensityMapBuilder> consumer, String tooltip) {
		var action = new Action(text, e -> consumer.accept(imageData.get(), builder.get()));
		if (tooltip != null)
			action.setLongText(tooltip);
		action.disabledProperty().bind(builder.isNull().or(imageData.isNull()));
		return action;
	}



	static class ContourTracer implements BiConsumer<ImageData<BufferedImage>, DensityMapBuilder> {

		private ParameterList paramsTracing = new ParameterList()
				.addDoubleParameter("threshold", "Density threshold", 0.5, null, "Define the density threshold to detection regions")
				.addBooleanParameter("split", "Split regions", false, "Split disconnected regions into separate annotations");

		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder) {

			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image available!");
				return;
			}

			if (builder == null) {
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
			var server = builder.buildServer(imageData);
			PathClass hotspotClass = getHotpotClass(server.getMetadata().getChannels().get(channel).getName());
			DensityMaps.traceContours(hierarchy, server, channel, selected, threshold, doSplit, hotspotClass);
		}

	}

	static class DensityMapExporter implements BiConsumer<ImageData<BufferedImage>, DensityMapBuilder> {

		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder) {

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
					promptToSaveRawImage(densityMapServer);
				} else if (btColor.equals(response)) {
					promptToSaveColorImage(densityMapServer, null); // Counting on color model being set!
				} else if (btImageJ.equals(response)) {
					sendToImageJ(densityMapServer);
				}
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}

		private void promptToSaveRawImage(ImageServer<BufferedImage> densityMap) throws IOException {
			var file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
			if (file != null)
				QP.writeImage(densityMap, file.getAbsolutePath());
		}

		private void promptToSaveColorImage(ImageServer<BufferedImage> densityMap, ColorModel colorModel) throws IOException {
			var server = RenderedImageServer.createRenderedServer(densityMap, new ColorModelRenderer(colorModel));
			File file;
			if (server.nResolutions() == 1 && server.nTimepoints() == 1 && server.nZSlices() == 1)
				file = Dialogs.promptToSaveFile(title, null, null, "PNG", ".png");
			else
				file = Dialogs.promptToSaveFile(title, null, null, "ImageJ tif", ".tif");
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
	 * Get a classification to use for hotspots based upon an image channel / classification name.
	 * @param channelName
	 * @return
	 */
	static PathClass getHotpotClass(String channelName) {		
		PathClass baseClass = channelName == null || channelName.isBlank() || DensityMaps.CHANNEL_ALL_OBJECTS.equals(channelName) ? null : PathClassFactory.getPathClass(channelName);
		return DensityMaps.getHotspotClass(baseClass);

	}

	
	/**
	 * Create a pane containing standardized buttons associated with processing a density map (find hotspots, threshold, export map).
	 * 
	 * Note that because density maps need to reflect the current hierarchy, but should be relatively fast to compute (at low resolution), 
	 * the full density map is generated upon request.
	 * 
	 * @param imageData expression returning the {@link ImageData} to use
	 * @param builder expression returning the {@link DensityMapBuilder} to use
	 * @return a pane that may be added to a stage
	 */
	public static Pane createButtonPane(ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder) {
		logger.trace("Creating button pane");
		
		var actionHotspots = createDensityMapAction("Find hotspots", imageData, builder, new HotspotFinder(),
				"Find the hotspots in the density map with highest values");
		var btnHotspots = ActionTools.createButton(actionHotspots, false);

		var actionThreshold = createDensityMapAction("Threshold", imageData, builder, new ContourTracer(),
				"Threshold to identify high-density regions");
		var btnThreshold = ActionTools.createButton(actionThreshold, false);

		var actionExport = createDensityMapAction("Export map", imageData, builder, new DensityMapExporter(),
				"Export the density map as an image");
		var btnExport = ActionTools.createButton(actionExport, false);

		var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnThreshold, btnExport);
//		buttonPane.setHgap(hGap);
		PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport, btnThreshold);
		return buttonPane;
	}
	


}
