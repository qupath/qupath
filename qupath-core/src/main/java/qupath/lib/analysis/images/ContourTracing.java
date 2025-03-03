/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2020-2024 QuPath developers, The University of Edinburgh
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


package qupath.lib.analysis.images;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Class to convert labelled images to Geometry objects, ROIs and PathObjects.
 * 
 * @author Pete Bankhead
 */
public class ContourTracing {
	
	private static final Logger logger = LoggerFactory.getLogger(ContourTracing.class);


	/**
	 * Convert labeled images to detection objects, determining the region from the filename if possible.
	 * @param paths paths to image files (e.g. PNGs)
	 * @param mergeByLabel if true, merge objects with the same ROI label
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToDetections(Collection<Path> paths, boolean mergeByLabel) throws IOException {
		var list = paths.parallelStream().flatMap(p -> labelsToDetectionsStream(p)).toList();
		if (mergeByLabel)
			return mergeObjectsByName(list);
		return list;
	}
	
	/**
	 * Convert 2-channel labeled images to cell objects, determining the region from the filename if possible.
	 * @param paths paths to image files (e.g. PNGs)
	 * @param mergeByLabel if true, merge objects with the same ROI label
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToCells(Collection<Path> paths, boolean mergeByLabel) throws IOException {
		var list = paths.parallelStream().flatMap(ContourTracing::labelsToCellsStream).toList();
		if (mergeByLabel)
			return mergeObjectsByName(list);
		return list;
	}
	
	private static <K> List<PathObject> mergeObjectsByName(Collection<? extends PathObject> pathObjects) {
		return PathObjectTools.mergeObjects(pathObjects, PathObject::getName);
	}
	
	private static Stream<PathObject> labelsToDetectionsStream(Path path) {
		try {
			return labelsToDetections(path, null).stream();
		} catch (IOException e) {
			logger.error("Error parsing detections from " + path + ": " + e.getLocalizedMessage(), e);
			return Stream.empty();
		}
	}
	
	private static Stream<PathObject> labelsToCellsStream(Path path) {
		try {
			return labelsToCells(path, null).stream();
		} catch (IOException e) {
			logger.error("Error parsing cells from " + path + ": " + e.getLocalizedMessage(), e);
			return Stream.empty();
		}
	}
	
	/**
	 * Convert a labeled image to detection objects.
	 * @param path path to labeled image file (e.g. PNGs)
	 * @param request a {@link RegionRequest} representing the region or the full image, used to reposition and rescale ROIs.
	 *        If not provided, this will be extracted from the filename, if possible.
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToDetections(Path path, RegionRequest request) throws IOException {
		var requestImage = readImage(path, request);
		var image = ContourTracing.extractBand(requestImage.getImage().getRaster(), 0);
		return ContourTracing.createDetections(image, requestImage.getRequest(), 1, -1);
	}
	
	
	/**
	 * Convert a 2-channel labeled image to cell objects.
	 * @param path path to labeled image file (e.g. PNGs)
	 * @param request a {@link RegionRequest} representing the region or the full image, used to reposition and rescale ROIs.
	 *        If not provided, this will be extracted from the filename, if possible.
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToCells(Path path, RegionRequest request) throws IOException {
		var requestImage = readImage(path, request);
		var img = requestImage.getImage();
		if (img.getRaster().getNumBands() < 2)
			throw new IllegalArgumentException("labelsToCells requires an image with at least 2 channels, cannot convert " + path);
		var imageNuclei = ContourTracing.extractBand(img.getRaster(), 0);
		var imageCells = ContourTracing.extractBand(img.getRaster(), 1);
		return ContourTracing.createCells(imageNuclei, imageCells, requestImage.getRequest(), 1, -1);
	}

	
	/**
	 * Convert labeled images to annotation objects, determining the region from the filename if possible.
	 * @param paths paths to image files (e.g. PNGs)
	 * @param mergeByLabel if true, merge objects with the same ROI label
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToAnnotations(Collection<Path> paths, boolean mergeByLabel) throws IOException {
		var list = paths.parallelStream().flatMap(p -> labelsToAnnotationsStream(p)).toList();
		if (mergeByLabel)
			return mergeObjectsByName(list);
		return list;
	}
	
	private static Stream<PathObject> labelsToAnnotationsStream(Path path) {
		try {
			return labelsToAnnotations(path, null).stream();
		} catch (IOException e) {
			logger.error("Error parsing annotations from " + path + ": " + e.getLocalizedMessage(), e);
			return Stream.empty();
		}
	}
	/**
	 * Convert a labeled image to annotation objects.
	 * @param path path to labeled image file (e.g. PNGs)
	 * @param request a {@link RegionRequest} representing the region or the full image, used to reposition and rescale ROIs.
	 *        If not provided, this will be extracted from the filename, if possible.
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToAnnotations(Path path, RegionRequest request) throws IOException {
		var requestImage = readImage(path, request);
		var image = ContourTracing.extractBand(requestImage.getImage().getRaster(), 0);
		return ContourTracing.createAnnotations(image, requestImage.getRequest(), 1, -1);
	}
	
	/**
	 * Convert a labeled image to objects.
	 * @param path path to labeled image file (e.g. PNGs)
	 * @param request a {@link RegionRequest} representing the region or the full image, used to reposition and rescale ROIs.
	 *        If not provided, this will be extracted from the filename, if possible.
	 * @param creator function used to convert a ROI and numeric label to an object
	 * @return a list of objects generated from the labels
	 * @throws IOException if there is an error reading the images
	 */
	public static List<PathObject> labelsToObjects(Path path, RegionRequest request, BiFunction<ROI, Number, PathObject> creator) throws IOException {
		var requestImage = readImage(path, request);
		var image = ContourTracing.extractBand(requestImage.getImage().getRaster(), 0);
		return ContourTracing.createObjects(image, request, 1, -1, creator);
	}
	
	
	/**
	 * Try to read {@link BufferedImage} from a file.
	 * @param path path to the file
	 * @return image, if it could be read
	 * @throws IOException if the image could not be read
	 */
	private static RequestImage readImage(Path path, RegionRequest request) throws IOException {
		var img = ImageIO.read(path.toFile());
		if (request == null) {
			String name = path.getFileName().toString();
			request = parseRegion(name, img.getWidth(), img.getHeight());
		}
		if (img == null) {
			var server = ImageServers.buildServer(path.toUri());
			img = server.readRegion(request);
		}
		return new RequestImage(request, img);
	}
	
	private static class RequestImage {
		
		private final RegionRequest request;
		private final BufferedImage img;
		
		public RequestImage(RegionRequest request, BufferedImage img) {
			this.request = request;
			this.img = img;
		}
		
		public RegionRequest getRequest() {
			return request;
		}
		
		public BufferedImage getImage() {
			return img;
		}
		
	}
	
	
	
	/**
	 * Attempt to parse a {@link RegionRequest} corresponding to an image region from the file name.
	 * <p>
	 * This is used whenever a tile has been extracted from a larger image for further processing, 
	 * and then there is a need to get the information back to the full-resolution image later.
	 * 
	 * @param name file name
	 * @param width labeled image width, used to calculate the downsample if required; use -1 to ignore this
	 * @param height labeled image height, used to calculate the downsample if required; use -1 to ignore this
	 * @return a {@link RegionRequest} that may be used to transform ROIs
	 */
	public static RegionRequest parseRegion(String name, int width, int height) {
		int x = 0;
		int y = 0;
		int w = 0;
		int h = 0;
		int z = 0;
		int t = 0;
		double downsample = Double.NaN;
		
		// Region components are a letter=number, where x, y, w
		String patternString = "\\[([a-zA-Z]=[\\d\\.]+,?)*\\]";

		
//		String patternString = "\\[x=(?<x>\\d+),y=(?<y>\\d+),w=(?<w>\\d+),h=(?<h>\\d+)[,z=(?<z>\\d+)]?[,t=(?<t>\\d+)]?\\]";
		var pattern = Pattern.compile(patternString);
		var matcher = pattern.matcher(name);
		
		if (matcher.find()) {
			String group = matcher.group();
			String[] parts = group.substring(1, group.length()-1).split(",");
			for (String part : parts) {
				var split = part.split("=");
				switch (split[0]) {
				case "x":
					x = Integer.parseInt(split[1]);
					break;
				case "y":
					y = Integer.parseInt(split[1]);
					break;
				case "w":
					w = Integer.parseInt(split[1]);
					break;
				case "h":
					h = Integer.parseInt(split[1]);
					break;
				case "z":
					z = Integer.parseInt(split[1]);
					break;
				case "t":
					t = Integer.parseInt(split[1]);
					break;
				case "d":
					downsample = Double.parseDouble(split[1]);
					break;
				default:
					logger.warn("Unknown region component '{}'", part);
				}
			}
		} else
			return null;
		
		// If we don't have a finite downsample, try to figure it out from the image size
		if (!Double.isFinite(downsample)) {
			if (w > 0 && h > 0 && width > 0 && height > 0) {
				double downsampleX = (double)w / width;
				double downsampleY = (double)h / height;
				downsample = (downsampleX + downsampleY) / 2.0;
				if (!GeneralTools.almostTheSame(downsampleX, downsampleY, 0.01))
					logger.warn("Estimated downsample x={} and y={}, will use average {}", downsampleX, downsampleY, downsample);
				else if (downsampleX != downsampleY)
					logger.debug("Estimated downsample x={} and y={}, will use average {}", downsampleX, downsampleY, downsample);
			} else {
				logger.debug("Using default downsample of 1");
				downsample = 1.0;
			}
		}
		
		return RegionRequest.createInstance(name, downsample, x, y, w, h, z, t);		
	}
	
	
	
	/**
	 * Create objects from one band of a raster containing integer labels.
	 * 
	 * @param raster the raster containing integer label values
	 * @param band the band of interest (usually 0)
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @param creator function to convert the traced ROI and associated numeric label into a {@link PathObject}
	 * @return a list of all created objects
	 */
	public static List<PathObject> createObjects(Raster raster, int band, RegionRequest region, int minLabel, int maxLabel, BiFunction<ROI, Number, PathObject> creator) {
		var image = extractBand(raster, band);
		return createObjects(image, region, minLabel, maxLabel, creator);
	}
	
	/**
	 * Create objects from a labelled image.
	 * 
	 * @param image the labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @param creator function to convert the traced ROI and associated numeric label into a {@link PathObject}
	 * @return a list of all created objects
	 */
	public static List<PathObject> createObjects(SimpleImage image, RegionRequest region, int minLabel, int maxLabel, BiFunction<ROI, Number, PathObject> creator) {
		var rois = createROIs(image, region, minLabel, maxLabel);
		List<PathObject> pathObjects = new ArrayList<>();
		for (var entry : rois.entrySet()) {
			var pathObject = creator.apply(entry.getValue(), entry.getKey());
			pathObjects.add(pathObject);
		}
		return pathObjects;
	}
	
	/**
	 * Create detection objects from a labelled image.
	 * 
	 * @param image the labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return a list of all created objects
	 */
	public static List<PathObject> createDetections(SimpleImage image, RegionRequest region, int minLabel, int maxLabel) {
		return createObjects(image, region, minLabel, maxLabel, createNumberedObjectFunction(r -> PathObjects.createDetectionObject(r)));
	}
	
	/**
	 * Create annotation objects from a labelled image.
	 * 
	 * @param image the labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return a list of all created objects
	 */
	public static List<PathObject> createAnnotations(SimpleImage image, RegionRequest region, int minLabel, int maxLabel) {
		return createObjects(image, region, minLabel, maxLabel, createNumberedObjectFunction(r -> PathObjects.createAnnotationObject(r)));
	}
	
	/**
	 * Convert a number to a String, providing an integer if possible.
	 * @param n
	 * @return
	 */
	private static String numberToString(Number n) {
		if (n == null)
			return null;
		double value = n.doubleValue();
		if (value == Math.rint(value))
			return Long.toString((long)value);
		return Double.toString(value);
	}
	
	/**
	 * Create a (bi)function to generate an object from a ROI and a number, setting the name of the object to match the number.
	 * @param creator
	 * @return a function compatible with {@link #createObjects(SimpleImage, RegionRequest, int, int, BiFunction)}
	 * @see #createObjectFunction(Function, BiConsumer)
	 * @see #createObjects(SimpleImage, RegionRequest, int, int, BiFunction)
	 */
	public static BiFunction<ROI, Number, PathObject> createNumberedObjectFunction(Function<ROI, PathObject> creator) {
		return createObjectFunction(creator, (p, n) -> p.setName(numberToString(n)));
	}
	
	
	/**
	 * Create a (bi)function to generate an object from a ROI and a number.
	 * @param creator the function to create the object from the ROI
	 * @param numberer the function to manipulate the object based on the number (e.g. to set the name, classification or measurements)
	 * @return a function compatible with {@link #createObjects(SimpleImage, RegionRequest, int, int, BiFunction)}
	 * @see #createNumberedObjectFunction(Function)
	 * @see #createObjects(SimpleImage, RegionRequest, int, int, BiFunction)
	 */
	public static BiFunction<ROI, Number, PathObject> createObjectFunction(Function<ROI, PathObject> creator, BiConsumer<PathObject, Number> numberer) {
		return (ROI r, Number n) -> {
			var pathObject = creator.apply(r);
			if (numberer != null && n != null)
				numberer.accept(pathObject, n);
			return pathObject;
		};
	}
	
	
	/**
	 * Create cell objects from two bands of a raster representing a labelled image.
	 * 
	 * @param raster the raster containing the labelled pixels
	 * @param bandNuclei the band containing the labelled nucleus pixels
	 * @param bandCells the band containing the labelled cell pixels
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return a list of all created cells
	 */
	public static List<PathObject> createCells(Raster raster, int bandNuclei, int bandCells, RegionRequest region, int minLabel, int maxLabel) {
		var imageNuclei = extractBand(raster, bandNuclei);
		var imageCells = extractBand(raster, bandCells);
		return createCells(imageNuclei, imageCells, region, minLabel, maxLabel);
	}
	
	/**
	 * Create cell objects from a pair of labelled images.
	 * 
	 * @param imageNuclei the labelled image containing cell nuclei
	 * @param imageCells the labelled image containing full cell regions; labels must match with imageNuclei
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return a list of all created cells
	 */
	public static List<PathObject> createCells(SimpleImage imageNuclei, SimpleImage imageCells, RegionRequest region, int minLabel, int maxLabel) {
		// Sanity check our images; we *should* have all non-zero nucleus pixels with the same labels in the cell image.
		// Otherwise, we end up with nuclei outside of the cell.
		// Conceivably, both images could be the same (although generally they shouldn't be)
		if (imageNuclei != imageCells) {
			if (imageNuclei.getWidth() != imageCells.getWidth() || imageNuclei.getHeight() != imageCells.getHeight()) {
				throw new IllegalArgumentException(
						String.format("Labelled images for nuclei and cells don't match! Image dimensions are different (%d x %d, %d x %d).", 
								imageNuclei.getWidth(), imageNuclei.getHeight(), imageCells.getWidth(), imageCells.getHeight()));
			}
			if (!maybeCellLabels(imageNuclei, imageCells, minLabel))
				throw new IllegalArgumentException("Nucleus and cell labelled images don't match! All labels >= " + minLabel + " in imageNuclei must be the same as labels in imageCells.");
		}
		var nucleusROIs = createROIs(imageNuclei, region, minLabel, maxLabel);
		var cellROIs = createROIs(imageCells, region, minLabel, maxLabel);
		List<PathObject> cells = new ArrayList<>();
		for (var entry : cellROIs.entrySet()) {
			var roiCell = entry.getValue();
			var roiNucleus = nucleusROIs.getOrDefault(entry.getKey(), null);
			var cell = PathObjects.createCellObject(roiCell, roiNucleus, null, null);
			cell.setName(numberToString(entry.getKey()));
			cells.add(cell);
		}
		return cells;
	}
	
	/**
	 * Check whether a raster could be used to generate cell objects, by providing a nucleus and corresponding cell labels.
	 * @param raster the image raster containing labelled images
	 * @param bandNuclei band corresponding to the potential labeled image for nuclei
	 * @param bandCells  band corresponding to the potential labeled image for cells
	 * @param minLabel the minimum positive label (usually 1). All pixels in {@code imageNuclei} with a value &geq; minLabel must 
	 *                 have the same value in {@code imageCells}.
	 * @return true if the images could provide nuclei and cell regions, false otherwise
	 * @implSpec this returns true if {@code bandNuclei == bandCells}, since this could potentially provide cell objects (with no cytoplasm).
	 */
	public static boolean maybeCellLabels(Raster raster, int bandNuclei, int bandCells, int minLabel) {
		if (bandNuclei == bandCells)
			return true;
		var imageNuclei = extractBand(raster, bandNuclei);
		var imageCells = extractBand(raster, bandCells);
		return maybeCellLabels(imageNuclei, imageCells, minLabel);
	}
	
	
	/**
	 * Check whether two images could be used to generate cell objects, by providing a nucleus and corresponding cell labels.
	 * @param imageNuclei potential labeled image for nuclei
	 * @param imageCells  potential labeled image for cells
	 * @param minLabel the minimum positive label (usually 1). All pixels in {@code imageNuclei} with a value &geq; minLabel must 
	 *                 have the same value in {@code imageCells}.
	 * @return true if the images could provide nuclei and cell regions, false otherwise
	 */
	public static boolean maybeCellLabels(SimpleImage imageNuclei, SimpleImage imageCells, int minLabel) {
		if (imageNuclei.getWidth() != imageCells.getWidth() || imageNuclei.getHeight() != imageCells.getHeight())
			return false;
		for (int y = 0; y < imageNuclei.getHeight(); y++) {
			for (int x = 0; x < imageNuclei.getWidth(); x++) {
				float val = imageNuclei.getValue(x, y);
				if (val >= minLabel) {
					if (val != imageCells.getValue(x, y)) {
						return false;
					}
				}
			}
		}
		return true;
	}
	
	

	/**
	 * Extract a band from a a raster as a {@link SimpleImage}.
	 * @param raster the raster
	 * @param band the band (0-based index)
	 * @return a {@link SimpleImage} containing a duplicate copy of the pixels in raster
	 */
	public static SimpleImage extractBand(Raster raster, int band) {
		var pixels = raster.getSamples(0, 0, raster.getWidth(), raster.getHeight(), band, (float[])null);
		return SimpleImages.createFloatImage(pixels, raster.getWidth(), raster.getHeight());
	}
	
	/**
	 * Create ROIs from one band of a raster containing integer label values.
	 * 
	 * @param raster the raster containing integer label values
	 * @param band the band of interest (usually 0)
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return an ordered map containing all the ROIs that could be found; corresponding labels are keys in the map
	 */
	public static Map<Number, ROI> createROIs(Raster raster, int band, RegionRequest region, int minLabel, int maxLabel) {
		var image = extractBand(raster, band);
		return createROIs(image, region, minLabel, maxLabel);
	}
	
	/**
	 * Create Geometries from a labelled image containing integer labels.
	 * 
	 * @param image the labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return an ordered map containing all the ROIs that could be found; corresponding labels are keys in the map
	 */
	public static Map<Number, Geometry> createGeometries(SimpleImage image, RegionRequest region, int minLabel, int maxLabel) {
		var envelopes = new HashMap<Number, Envelope>();
		if (minLabel == maxLabel) {
			// Don't bother storing an envelope here - we'll iterate the full image when tracing
			// But do store the label so that we can use the map for iterating
			envelopes.put(minLabel, null);
		} else {
			// Check if we need to identify the max label because it hasn't been provided
			boolean searchingMaxLabel = maxLabel < minLabel;
			int maxLabelFound = Integer.MIN_VALUE;
			// If we want ROIs for more than one label (or an unknown number of labels,
			// do a first pass to find envelopes (i.e. bounding boxes)
			// so that we don't need to visit all pixels every time we trace a contour later
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					float val = Math.round(image.getValue(x, y));
					int label = Math.round(val);
					if (val != label)
						continue;
					// Update our max label if required
					if (label > maxLabel) {
						maxLabelFound = label;
						if (searchingMaxLabel)
							maxLabel = maxLabelFound;
					}
					// Update envelope if required
					if (selected(label, minLabel, maxLabel)) {
						envelopes.computeIfAbsent(label, k -> new Envelope()).expandToInclude(x, y);
					}
				}
			}
			// If no label exceeds the min label, return an empty map
			if (maxLabelFound < minLabel)
				return Collections.emptyMap();
		}

		// Trace contours for all requested labels
		var map = envelopes.entrySet()
				.parallelStream()
				.collect(
						Collectors.toMap(
								Map.Entry::getKey,
								e -> createTracedGeometry(image, e.getKey().doubleValue(), e.getKey().doubleValue(), region, e.getValue())
						)
				);

		// Return a sorted map with all non-empty ROIs
		Map<Number, Geometry> rois = new TreeMap<>();
		for (var entry : map.entrySet()) {
			if (entry.getValue() != null && !entry.getValue().isEmpty())
				rois.put(entry.getKey(), entry.getValue());
		}
		return rois;
	}

	/**
	 * Create ROIs from a labelled image containing integer labels.
	 *
	 * @param image the labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return an ordered map containing all the ROIs that could be found; corresponding labels are keys in the map
	 */
	public static Map<Number, ROI> createROIs(SimpleImage image, RegionRequest region, int minLabel, int maxLabel) {
		var map = createGeometries(image, region, minLabel, maxLabel);
		return map.entrySet().stream()
				.collect(
						Collectors.toMap(
								Map.Entry::getKey,
								es -> {
									var geom = es.getValue();
									return geom == null ? null : GeometryTools.geometryToROI(geom, region == null ? ImagePlane.getDefaultPlane() : region.getImagePlane());
								}
						)
				);
	}


	private static ROI labelToROI(SimpleImage image, double label, RegionRequest region, Envelope envelope) {
		return createTracedROI(image, label, label, region, envelope);
	}
	
	/**
	 * Create a traced ROI from a raster.
	 * 
	 * @param raster input raster
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param band band (channel) to threshold
	 * @param request region request used to translate and rescale to the image space, and determine the image plane
	 * @return a ROI created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 * 
	 * @see #createTracedGeometry(Raster, double, double, int, RegionRequest)
	 */
	public static ROI createTracedROI(Raster raster, double minThresholdInclusive, double maxThresholdInclusive, int band, RegionRequest request) {
		var geom = createTracedGeometry(raster, minThresholdInclusive, maxThresholdInclusive, band, request);
		return GeometryTools.geometryToROI(geom, request == null ? ImagePlane.getDefaultPlane() : request.getImagePlane());
	}
	
	/**
	 * Create a traced ROI from a {@link SimpleImage}.
	 * 
	 * @param image input image
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param request region request used to translate and rescale to the image space, and determine the image plane
	 * @return a ROI created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 * 
	 * @see #createTracedGeometry(SimpleImage, double, double, RegionRequest)
	 */
	public static ROI createTracedROI(SimpleImage image, double minThresholdInclusive, double maxThresholdInclusive, RegionRequest request) {
		return createTracedROI(image, minThresholdInclusive, maxThresholdInclusive, request, null);
	}


	private static ROI createTracedROI(SimpleImage image, double minThresholdInclusive, double maxThresholdInclusive, RegionRequest request, Envelope envelope) {
		var geom = createTracedGeometry(image, minThresholdInclusive, maxThresholdInclusive, request, envelope);
		return geom == null ? null : GeometryTools.geometryToROI(geom, request == null ? ImagePlane.getDefaultPlane() : request.getImagePlane());
	}
	
	
	/**
	 * Create traced geometry from tile.
	 * Note that it is important to use this version with tiles, rather than {@link #createTracedGeometry(Raster, double, double, int, RegionRequest)},
	 * to avoid accumulating rounding errors.
	 * 
	 * @param raster
	 * @param minThresholdInclusive
	 * @param maxThresholdInclusive
	 * @param band
	 * @param request
	 * @param envelope
	 * @return
	 */
	private static List<CoordinatePair> createCoordinatePairs(Raster raster, double minThresholdInclusive, double maxThresholdInclusive,
															  int band, TileRequest request, Envelope envelope) {
		var image = extractBand(raster, band);
		return createCoordinatePairs(image, minThresholdInclusive, maxThresholdInclusive, request, envelope);
	}
	
	
	/**
	 * Create traced geometry from tile.
	 * Note that it is important to use this version with tiles, rather than {@link #createTracedGeometry(SimpleImage, double, double, RegionRequest)},
	 * to avoid accumulating rounding errors.
	 * 
	 * @param image
	 * @param minThresholdInclusive
	 * @param maxThresholdInclusive
	 * @param tile
	 * @param envelope
	 * @return
	 */
	private static List<CoordinatePair> createCoordinatePairs(SimpleImage image, double minThresholdInclusive, double maxThresholdInclusive, TileRequest tile, Envelope envelope) {
		// If we are translating but not rescaling, we can do this during tracing
		int xOffset = 0;
		int yOffset = 0;
		if (tile != null) {
			xOffset = tile.getTileX();
			yOffset = tile.getTileY();
		}
		return traceCoordinates(image, minThresholdInclusive, maxThresholdInclusive, xOffset, yOffset, envelope);
	}

	private static Geometry createGeometry(GeometryFactory factory, Collection<CoordinatePair> lines, RegionRequest request) {
		if (request == null)
			return createGeometry(factory, lines);
		double scale = request.getDownsample();
		return createGeometry(factory, lines,
				request.getX(),
				request.getY(),
				scale);
	}

	private static Geometry createGeometry(GeometryFactory factory, Collection<CoordinatePair> lines, TileRequest tile) {
		if (tile == null)
			return createGeometry(factory, lines);
		double scale = tile.getDownsample();
		return createGeometry(factory, lines,
				tile.getTileX() * scale,
				tile.getTileY() * scale,
				scale);
	}

	private static Geometry createGeometry(GeometryFactory factory, Collection<CoordinatePair> lines) {
		return createGeometry(factory, lines, 0, 0, 1);
	}



	private static Geometry createGeometry(GeometryFactory factory, Collection<CoordinatePair> lines,
										   double xOrigin, double yOrigin, double scale) {

		if (lines.isEmpty())
			return factory.createEmpty(2);

		var pairs = ContourTracingUtils.removeDuplicatesCompletely(lines);

		try {
			long startTime = System.currentTimeMillis();
			int nPairs = pairs.size();
			if (nPairs > 10_000_000) {
				// About 7 million pairs has been relatively fast in tests... whereas 33 million proved too much
				logger.warn("Attempting to trace {} coordinate pairs (consider using a smaller region if this fails)",
						nPairs);
			} else {
				logger.debug("Attempting to trace {} coordinate pairs", nPairs);
			}
			var lineStrings = ContourTracingUtils.linesFromPairsFast(factory, pairs, xOrigin, yOrigin, scale);
			long endTime = System.currentTimeMillis();
			logger.debug("Created {} lines from {} coordinate pairs in {} ms", lineStrings.getNumGeometries(), nPairs, endTime - startTime);

			var polygonizer = new Polygonizer(true);
			polygonizer.add(lineStrings);
			var geometry = polygonizer.getGeometry();
			geometry.normalize();

			logger.debug("Created {} with {} coordinates", geometry.getGeometryType(), geometry.getNumPoints());
			return geometry;
		} catch (Throwable e) {
			logger.error("Error in polygonization: {}", e.getMessage(), e);
			return factory.createEmpty(2);
		}
	}
	
	/**
	 * Create a traced geometry from a {@link SimpleImage}.
	 * 
	 * @param image input image
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param request optional region request; if provided, the geometry will be translated and rescaled to the image space
	 * @return a polygonal geometry created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 */
	public static Geometry createTracedGeometry(SimpleImage image, double minThresholdInclusive, double maxThresholdInclusive, RegionRequest request) {
		return createTracedGeometry(image, minThresholdInclusive, maxThresholdInclusive, request, null);
	}

	private static Geometry createTracedGeometry(SimpleImage image, double minThresholdInclusive, double maxThresholdInclusive, RegionRequest request, Envelope envelope) {

		// If we are translating but not rescaling, we can do this during tracing
		double xOffset = 0;
		double yOffset = 0;
		double scale = 1.0;
		if (request != null) {
			scale = request.getDownsample();
			xOffset = request.getX();
			yOffset = request.getY();
		}

		var lines = traceCoordinates(image, minThresholdInclusive, maxThresholdInclusive, 0, 0, envelope);

		return createGeometry(GeometryTools.getDefaultFactory(), lines, xOffset, yOffset, scale);
	}
	
	
	/**
	 * Create a traced geometry from a raster.
	 * 
	 * @param raster input raster
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param band band (channel) to threshold
	 * @param request optional region request; if provided, the geometry will be translated and rescaled to the image space
	 * @return a polygonal geometry created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 */
	public static Geometry createTracedGeometry(Raster raster, double minThresholdInclusive, double maxThresholdInclusive, int band, RegionRequest request) {
		var image = extractBand(raster, band);
		return createTracedGeometry(image, minThresholdInclusive, maxThresholdInclusive, request);
	}
	
	
	
	
	
	/**
	 * Helper class defining global thresholds to apply to a single image channel.
	 * 
	 * @author Pete Bankhead
	 */
	public static class ChannelThreshold {
		
		private final int channel;
		private final double minThreshold;
		private final double maxThreshold;
		
		private ChannelThreshold(int channel, double minThreshold, double maxThreshold) {
			this.channel = channel;
			this.minThreshold = minThreshold;
			this.maxThreshold = maxThreshold;
		}
		
		/**
		 * Create a simple channel threshold. This contains no intensity values (min/max thresholds are infinity) 
		 * but it is useful for thresholding classification images. In this case, the channel refers to the classification 
		 * label.
		 * @param channel
		 * @return
		 */
		public static ChannelThreshold create(int channel) {
			return new ChannelThreshold(channel, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		}

		/**
		 * Create a threshold to select values between a minimum and maximum (inclusive).
		 * @param channel channel to threshold
		 * @param minThreshold minimum value (inclusive)
		 * @param maxThreshold maximum value (inclusive)
		 * @return
		 */
		public static ChannelThreshold create(int channel, double minThreshold, double maxThreshold) {
			return new ChannelThreshold(channel, minThreshold, maxThreshold);
		}

		/**
		 * Create a threshold to select values greater than or equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param minThreshold the minimum threshold to apply
		 * @return
		 */
		public static ChannelThreshold createAbove(int channel, double minThreshold) {
			return new ChannelThreshold(channel, minThreshold, Double.POSITIVE_INFINITY);
		}

		/**
		 * Create a threshold to select values less than or equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param maxThreshold the maximum threshold to apply
		 * @return
		 */
		public static ChannelThreshold createBelow(int channel, double maxThreshold) {
			return new ChannelThreshold(channel, Double.NEGATIVE_INFINITY, maxThreshold);
		}
		
		/**
		 * Create a threshold to select values that are exactly equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param threshold the threshold value
		 * @return
		 */
		public static ChannelThreshold createExactly(int channel, double threshold) {
			return new ChannelThreshold(channel, threshold, threshold);
		}

		/**
		 * Get the minimum threshold value. This may be {@link Float#NEGATIVE_INFINITY} if no minimum threshold is required.
		 * @return
		 */
		public double getMinThreshold() {
			return minThreshold;
		}
		
		/**
		 * Get the maximum threshold value. This may be {@link Float#POSITIVE_INFINITY} if no minimum threshold is required.
		 * @return
		 */
		public double getMaxThreshold() {
			return maxThreshold;
		}
		
		/**
		 * Get the channel to threshold.
		 * @return
		 */
		public int getChannel() {
			return channel;
		}
		
		@Override
		public String toString() {
			return String.format("Threshold %d (%s, %s)", channel, GeneralTools.formatNumber(minThreshold, 3), GeneralTools.formatNumber(maxThreshold, 3));
		}
		
	}
	

	/**
	 * Trace a geometry from a single channel of a single image.
	 * @param server
	 * @param regionRequest
	 * @param clipArea
	 * @param channel
	 * @param minThreshold
	 * @param maxThreshold
	 * @return
	 * @throws IOException 
	 */
	public static Geometry traceGeometry(ImageServer<BufferedImage> server, RegionRequest regionRequest, Geometry clipArea, int channel, double minThreshold, double maxThreshold) throws IOException {
		var map = traceGeometries(server, regionRequest, clipArea, ChannelThreshold.create(channel, minThreshold, maxThreshold));
		if (map == null || map.isEmpty())
			return GeometryTools.getDefaultFactory().createPolygon();
		assert map.size() == 1;
		return map.values().iterator().next();
	}
	
	
	/**
	 * Trace one or more geometries in an image.
	 * @param server
	 * @param regionRequest optional region defining the area within which geometries should be traced
	 * @param clipArea optional clip region, intersected with the created geometries (may be null)
	 * @param thresholds min/max thresholds (inclusive) to apply to each channel to generate objects
	 * @return
	 * @throws IOException 
	 */
	public static Map<Integer, Geometry> traceGeometries(ImageServer<BufferedImage> server, RegionRequest regionRequest, Geometry clipArea, ChannelThreshold... thresholds) throws IOException {
		
		RegionRequest region = regionRequest;
		if (region == null) {
			if (clipArea == null) {
				region = RegionRequest.createInstance(server, server.getDownsampleForResolution(0));
			} else {
				var env = clipArea.getEnvelopeInternal();
				region = RegionRequest.createInstance(server.getPath(), server.getDownsampleForResolution(0), GeometryTools.envelopToRegion(env, 0, 0));
			}
		} else if (clipArea != null) {
			// Ensure we don't compute more than we need to
			var env = clipArea.getEnvelopeInternal();
			region = region.intersect2D(GeometryTools.envelopToRegion(env, region.getZ(), region.getT()));
		}
		
		Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(region);

		if (thresholds.length == 0 || tiles.isEmpty())
			return Collections.emptyMap();

		// If the region downsample doesn't match the tile requests, the scaling may be off
		// One way to resolve that (without requiring the region to be read in one go) is to generate new tile requests for a pyramidalized server at the correct resolution
		double downsample = region.getDownsample();
		if (Math.abs(tiles.iterator().next().getDownsample() - downsample) > 1e-3) {
			server = ImageServers.pyramidalize(server, downsample);
			tiles = server.getTileRequestManager().getTileRequests(region);
		}

		// If we have a clip area, make sure we skip as many tiles as we can
		if (clipArea != null && !clipArea.isRectangle()) {
			tiles = tiles.stream().filter(
					t -> clipArea.intersects(GeometryTools.regionToGeometry(t.getRegionRequest())))
					.toList();
		}
		
		return traceGeometriesImpl(server, tiles, clipArea, thresholds);
	}
	
	
	private static <T,S> List<S> invokeAll(ExecutorService pool, Collection<T> items, Function<T, S> fun) throws InterruptedException, ExecutionException {
		List<Future<S>> futures = new ArrayList<>();
		for (var item : items)
			futures.add(pool.submit(() -> fun.apply(item)));
		
		List<S> results = new ArrayList<>();
		for (var future : futures)
			results.add(future.get());
		
		return results;
	}
	
	
	@SuppressWarnings("unchecked")
	private static Map<Integer, Geometry> traceGeometriesImpl(ImageServer<BufferedImage> server, Collection<TileRequest> tiles, Geometry clipArea, ChannelThreshold... thresholds) throws IOException {
		
		if (thresholds.length == 0)
			return Collections.emptyMap();
				
		Map<Integer, Geometry> output = new LinkedHashMap<>();

		var pool = Executors.newFixedThreadPool(ThreadTools.getParallelism());
		try {
			List<List<LabeledCoordinatePairs>> labeledCoords = invokeAll(pool, tiles, t -> traceGeometries(server, t, clipArea, thresholds));
			var coordMap =  labeledCoords.stream()
					.flatMap(List::stream)
					.collect(Collectors.groupingBy(LabeledCoordinatePairs::getLabel));
			
			var futures = new LinkedHashMap<Integer, Future<Geometry>>();
			
			// Merge objects with the same classification
			for (var entry : coordMap.entrySet()) {
				var list = entry.getValue();
				if (list.isEmpty())
					continue;
				// At this point, we have traced using tile offsets but without scaling to the full image coordinates.
				// We need to apply the downsampling to correct for this.
				double scale = tiles.iterator().next().getDownsample();
				if (clipArea == null)
					futures.put(entry.getKey(), pool.submit(() -> coordsToGeometry(list, 0, 0, scale)));
				else
					futures.put(entry.getKey(), pool.submit(() -> areaIntersection(clipArea, coordsToGeometry(list, 0, 0, scale))));
			}
			
			for (var entry : futures.entrySet())
				output.put(entry.getKey(), entry.getValue().get());			
			
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			pool.shutdown();
		}
		return output;
	}

	private static Geometry areaIntersection(Geometry a, Geometry b) {
		Geometry result;
		if (a.getNumGeometries() < b.getNumGeometries()) {
			result = b.intersection(a);
		} else {
			result = a.intersection(b);
		}
		// Strip out any non-polygonal pieces
		return GeometryTools.ensurePolygonal(result);
	}
	
	
	/**
	 * Merge labeled coordinate pairs together to create geometry objects.c
	 * @param list the coordinate pairs to merge
	 * @param xOrigin offset to add to all x coordinates
	 * @param yOrigin offset to add to all y coordinates
	 * @param scale multiplication factor for coordinates
	 * @return
	 */
	private static Geometry coordsToGeometry(List<LabeledCoordinatePairs> list, double xOrigin, double yOrigin, double scale) {

		var factory = GeometryTools.getDefaultFactory();

		// Shouldn't happen (since we should have filtered out empty lists before calling this)
		if (list.isEmpty())
			return factory.createEmpty(2);
		
		// If we just have one tile, that's what we need
		var coords = list.stream().flatMap(g -> g.coordinates.stream()).toList();

		return createGeometry(factory, coords, xOrigin, yOrigin, scale);
	}



	private static List<LabeledCoordinatePairs> traceGeometries(ImageServer<BufferedImage> server, TileRequest tile, Geometry clipArea, ChannelThreshold... thresholds) {
		try {
			return traceGeometriesImpl(server, tile, clipArea, thresholds);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	
	private static List<LabeledCoordinatePairs> traceGeometriesImpl(ImageServer<BufferedImage> server, TileRequest tile, Geometry clipArea, ChannelThreshold... thresholds) throws IOException {
		if (thresholds.length == 0)
			return Collections.emptyList();
		
		var request = tile.getRegionRequest();
		var list = new ArrayList<LabeledCoordinatePairs>();

		var img = server.readRegion(request);
		// Get an image to threshold
		var channelType = server.getMetadata().getChannelType();
		int h = img.getHeight();
		int w = img.getWidth();
		var nChannels = server.nChannels();

		// If we have probabilities, then the 'true' classification is the one with the highest values.
		// If we have classifications, then the 'true' classification is the value of the pixel (which is expected to have a single band).
		boolean doClassification = (channelType == ImageServerMetadata.ChannelType.PROBABILITY && nChannels > 1) || channelType == ImageServerMetadata.ChannelType.CLASSIFICATION;
		if (doClassification) {
			SimpleImage image;
			// If we have probability & more than one channel, we take the channel with the highest probability (i.e. softmax)
			if (channelType == ImageServerMetadata.ChannelType.PROBABILITY) {
				// Convert probabilities to classifications
				var raster = img.getRaster();
				float[] output = new float[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int maxInd = 0;
						float maxVal = raster.getSampleFloat(x, y, 0);
						for (int c = 1; c < nChannels; c++) {
							float val = raster.getSampleFloat(x, y, c);						
							if (val > maxVal) {
								maxInd = c;
								maxVal = val;
							}
							output[y*w+x] = (float)maxInd;
						}
					}
				}
				image = SimpleImages.createFloatImage(output, w, h);
			} else {
				// Handle classifications
				var raster = img.getRaster();
				var pixels = raster.getSamples(0, 0, w, h, 0, (float[])null);
				image = SimpleImages.createFloatImage(pixels, w, h);
			}
			for (var threshold : thresholds) {
				int c = threshold.getChannel();
				var coords = ContourTracing.createCoordinatePairs(image, c, c, tile, null);
				list.add(new LabeledCoordinatePairs(coords, c));
			}
		} else {
			// Apply the provided threshold to all channels
			var raster = img.getRaster();

			// Precompute envelopes if we have multiple thresholds, to avoid needing to iterate all pixels many times
			Map<ChannelThreshold, Envelope> envelopes = new HashMap<>();
			if (thresholds.length > 1) {
				logger.info("Populating envelopes!");
				populateEnvelopes(raster, envelopes, thresholds);
			}

			for (var threshold : thresholds) {
				var coords = ContourTracing.createCoordinatePairs(
						raster, threshold.getMinThreshold(), threshold.getMaxThreshold(), threshold.getChannel(), tile, envelopes.getOrDefault(threshold, null));
				if (!coords.isEmpty()) {
					// Exclude lines/points that can sometimes arise
					list.add(new LabeledCoordinatePairs(coords, threshold.getChannel()));
				}
			}
		}
		return list;
	}

	/**
	 * Populate an existing map of envelopes with the bounding boxes of pixels that fall within the specified thresholds.
	 * @param raster
	 * @param envelopes
	 * @param thresholds
	 */
	private static void populateEnvelopes(WritableRaster raster, Map<ChannelThreshold, Envelope> envelopes, ChannelThreshold... thresholds) {
		var groups = Arrays.stream(thresholds).collect(Collectors.groupingBy(ChannelThreshold::getChannel));
		for (var entry : groups.entrySet()) {
			int channel = entry.getKey().intValue();
			var channelThresholds = entry.getValue();
			var image = extractBand(raster, channel);
			populateEnvelopes(image, envelopes, channelThresholds.toArray(ChannelThreshold[]::new));
		}
	}

	/**
	 * Populate an existing map of envelopes with the bounding boxes of pixels that fall within the specified thresholds.
	 * @param image
	 * @param envelopes
	 * @param thresholds
	 */
	private static void populateEnvelopes(SimpleImage image, Map<ChannelThreshold, Envelope> envelopes, ChannelThreshold... thresholds) {
		int w = image.getWidth();
		int h = image.getHeight();
		for (var t : thresholds) {
			envelopes.computeIfAbsent(t, k -> new Envelope());
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float val = image.getValue(x, y);
				for (var t : thresholds) {
					if (selected(val, t.getMinThreshold(), t.getMaxThreshold()))
						envelopes.get(t).expandToInclude(x, y);
				}
			}
		}
	}
	

	/**
	 * Simple wrapper for a list of coordinate pairs and a label (usually a channel number or classification).
	 */
	private static class LabeledCoordinatePairs {
		
		private final List<CoordinatePair> coordinates;
		private final int label;
		
		private LabeledCoordinatePairs(List<CoordinatePair> coordinates, int label) {
			this.coordinates = List.copyOf(coordinates);
			this.label = label;
		}

		/**
		 * Get an unmodifiable list of the coordinate pairs.
		 * @return
		 */
		List<CoordinatePair> getCoordinates() {
			return coordinates;
		}

		/**
		 * Get the label.
		 * @return
		 */
		int getLabel() {
			return label;
		}
		
	}


	private static boolean selected(int v, int min, int max) {
		return v >= min && v <= max;
	}

	private static boolean selected(float v, float min, float max) {
		return v >= min && v <= max;
	}
	
	private static boolean selected(double v, double min, double max) {
		return v >= min && v <= max;
	}
	
	
	
	/**
	 * This was rewritten for QuPath v0.6.0 to use JTS Polygonizer instead of the previous approach.
	 * It should be considerably faster.
	 * 
	 * @param image the image containing the contour
	 * @param min the minimum value to consider as part of the contour (inclusive)
	 * @param max the maximum value to consider as part of the contour (inclusive)
	 * @param xOffset the x offset to add to the contour
	 * @param yOffset the y offset to add to the contour
	 * @param envelope optional bounding box, in the image space, to restrict the contour search (may be null).
	 *                 This is useful to avoid searching the entire image when only a small region is needed.
	 * @return
	 */
	private static List<CoordinatePair> traceCoordinates(SimpleImage image, double min, double max, int xOffset,
														 int yOffset, Envelope envelope) {

		var factory = GeometryTools.getDefaultFactory();
		var pm = factory.getPrecisionModel();

		int xStart = 0;
		int yStart = 0;
		int xEnd = image.getWidth();
		int yEnd = image.getHeight();
		// Clip searched pixels using the envelope if provided
		if (envelope != null) {
			xStart = Math.max(xStart, (int)Math.floor(envelope.getMinX()-1));
			yStart = Math.max(yStart, (int)Math.floor(envelope.getMinY()-1));
			xEnd = Math.min(xEnd, (int)Math.ceil(envelope.getMaxX())+1);
			yEnd = Math.min(yEnd, (int)Math.ceil(envelope.getMaxY())+1);
		}

		List<CoordinatePair> lines = new ArrayList<>();
		IntPoint lastHorizontalEdgeCoord = null;
		IntPoint[] lastVerticalEdgeCoords = new IntPoint[xEnd-xStart+1];
		Map<IntPoint, IntPoint> pointCache = new HashMap<>();
		for (int y = yStart; y <= yEnd; y++) {
			for (int x = xStart; x <= xEnd; x++) {
				boolean isOn = inRange(image, x, y, min, max);
				boolean onHorizontalEdge = isOn != inRange(image, x, y-1, min, max);
				boolean onVerticalEdge = isOn != inRange(image, x-1, y, min, max);
				// Check if on a horizontal edge with the previous row
				if (onHorizontalEdge) {
					var nextEdgeCoord = createCoordinate( xOffset + x, yOffset + y, pointCache);
					if (lastHorizontalEdgeCoord != null) {
						lines.add(new CoordinatePair(lastHorizontalEdgeCoord, nextEdgeCoord));
					}
					lastHorizontalEdgeCoord = nextEdgeCoord;
				} else {
					if (lastHorizontalEdgeCoord != null) {
						var nextEdgeCoord = createCoordinate(xOffset + x, yOffset + y, pointCache);
						lines.add(new CoordinatePair(lastHorizontalEdgeCoord, nextEdgeCoord));
						lastHorizontalEdgeCoord = null;
					}
				}
				// Check if on a vertical edge with the previous column
				var lastVerticalEdgeCoord = lastVerticalEdgeCoords[x - xStart];
				if (onVerticalEdge) {
					var nextEdgeCoord = createCoordinate( xOffset + x, yOffset + y, pointCache);
					if (lastVerticalEdgeCoord != null) {
						lines.add(new CoordinatePair(lastVerticalEdgeCoord, nextEdgeCoord));
					}
					lastVerticalEdgeCoords[x - xStart] = nextEdgeCoord;
				} else {
					if (lastVerticalEdgeCoord != null) {
						var nextEdgeCoord = createCoordinate( xOffset + x, yOffset + y, pointCache);
						lines.add(new CoordinatePair(lastVerticalEdgeCoord, nextEdgeCoord));
						lastVerticalEdgeCoords[x - xStart] = null;
					}
				}
			}
		}
		return lines;
	}

	/**
	 * Get a point with the specified precision model.
	 * It pointCache is provided, then the point will be cached and reused if it already exists.
	 * This is intended to slightly reduce overhead by effectively making points singletons (at least where the cache
	 * is shared).
	 */
	private static IntPoint createCoordinate(int x, int y, Map<IntPoint, IntPoint> pointCache) {
		// We don't avoid the overhead of *creating* the point, but at least it can be immediately garbage collected
		var point = new IntPoint(x, y);
		return pointCache == null ? point : pointCache.computeIfAbsent(point, p -> p);
	}



	private static boolean inRange(SimpleImage image, int x, int y, double min, double max) {
		if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight())
			return false;
		double val = image.getValue(x, y);
		return val >= min && val <= max;
	}


}
