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

package qupath.lib.analysis.features;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.algorithm.Length;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Experimental class to generate object measurements.
 * <p>
 * May very well be moved, removed or refactored in a future release...
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectMeasurements {
	
	private static final Logger logger = LoggerFactory.getLogger(ObjectMeasurements.class);
	
	/**
	 * Cell compartments.
	 */
	public enum Compartments {
		/**
		 * Nucleus only
		 */
		NUCLEUS,
		/**
		 * Full cell region, with nucleus removed
		 */
		CYTOPLASM,
		/**
		 * Full cell region
		 */
		CELL,
		/**
		 * Cell boundary, with interior removed
		 */
		MEMBRANE;
		
	}
	
	/**
	 * Requested intensity measurements.
	 */
	public enum Measurements {
		/**
		 * Arithmetic mean
		 */
		MEAN,
		/**
		 * Median value
		 */
		MEDIAN,
		/**
		 * Minimum value
		 */
		MIN,
		/**
		 * Maximum value
		 */
		MAX,
		/**
		 * Standard deviation value
		 */
		STD_DEV,
		/**
		 * Variance value
		 */
		VARIANCE;
		
		private String getMeasurementName() {
            return switch (this) {
                case MAX -> "Max";
                case MEAN -> "Mean";
                case MEDIAN -> "Median";
                case MIN -> "Min";
                case STD_DEV -> "Std.Dev.";
                case VARIANCE -> "Variance";
                default -> throw new IllegalArgumentException("Unknown measurement " + this);
            };
		}
		
		private double getMeasurement(StatisticalSummary stats) {
			switch (this) {
			case MAX:
				return stats.getMax();
			case MEAN:
				return stats.getMean();
			case MEDIAN:
				if (stats instanceof DescriptiveStatistics)
					return ((DescriptiveStatistics)stats).getPercentile(50.0);
				else
					return Double.NaN;
			case MIN:
				return stats.getMin();
			case STD_DEV:
				return stats.getStandardDeviation();
			case VARIANCE:
				return stats.getVariance();
			default:
				throw new IllegalArgumentException("Unknown measurement " + this);
			}
		}
	}

	public static final Collection<Measurements> ALL_MEASUREMENTS = Set.of(Measurements.values());

	public static final Collection<Compartments> ALL_COMPARTMENTS = Set.of(Compartments.values());
	
	public static final Collection<ShapeFeatures> ALL_SHAPE_FEATURES = Set.of(ShapeFeatures.values());
	
	/**
	 * Add shape measurements for one object. If this is a cell, measurements will be made for both the 
	 * nucleus and cell boundary where possible.
	 * 
	 * @param pathObject the object for which measurements should be added
	 * @param cal pixel calibration, used to determine units and scaling
	 * @param features specific features to add; if empty, all available shape features will be added
	 */
	public static void addShapeMeasurements(PathObject pathObject, PixelCalibration cal, ShapeFeatures... features) {
		addShapeMeasurements(Collections.singleton(pathObject), cal, features);
	}
	
	/**
	 * Add shape measurements for multiple objects. If any of these objects is a cell, measurements will be made for both the 
	 * nucleus and cell boundary where possible.
	 * 
	 * @param pathObjects the objects for which measurements should be added
	 * @param cal pixel calibration, used to determine units and scaling
	 * @param features specific features to add; if empty, all available shape features will be added
	 */
	public static void addShapeMeasurements(Collection<? extends PathObject> pathObjects, PixelCalibration cal, ShapeFeatures... features) {
		
		PixelCalibration calibration = cal == null || !cal.unitsMatch2D() ? PixelCalibration.getDefaultInstance() : cal;
		Collection<ShapeFeatures> featureCollection = features.length == 0 ? ALL_SHAPE_FEATURES : Arrays.asList(features);
		
		pathObjects.parallelStream().filter(PathObject::hasROI).forEach(pathObject -> {
			if (pathObject instanceof PathCellObject cell) {
				addCellShapeMeasurements(cell, calibration, featureCollection);
			} else {
				try (var ml = pathObject.getMeasurementList()) {
					addShapeMeasurements(ml, pathObject.getROI(), calibration, "", featureCollection);
				}
			}
		});
	}
	
	
	private static Geometry getScaledGeometry(ROI roi, PixelCalibration cal, ShapeFeatures...features) {
		if (roi == null)
			return null;
		var geom = roi.getGeometry();
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		if (pixelWidth == 1 && pixelHeight == 1) 
			return geom;
		return AffineTransformation.scaleInstance(pixelWidth, pixelHeight).transform(geom);
	}
	
	private static void addCellShapeMeasurements(PathCellObject cell, PixelCalibration cal, Collection<ShapeFeatures> features) {
		
		var roiNucleus = cell.getNucleusROI();
		var roiCell = cell.getROI();
		
		try (MeasurementList ml = cell.getMeasurementList()) {
			if (roiNucleus != null) {
				addShapeMeasurements(ml, roiNucleus, cal, "Nucleus: ", features);
			}
			if (roiCell != null) {
				addShapeMeasurements(ml, roiCell, cal, "Cell: ", features);
			}
			
			if (roiNucleus != null && roiCell != null && features.contains(ShapeFeatures.NUCLEUS_CELL_RATIO)) {
				double pixelWidth = cal.getPixelWidth().doubleValue();
				double pixelHeight = cal.getPixelHeight().doubleValue();
				double nucleusCellAreaRatio = GeneralTools.clipValue(roiNucleus.getScaledArea(pixelWidth, pixelHeight) / roiCell.getScaledArea(pixelWidth, pixelHeight), 0, 1);
				ml.put("Nucleus/Cell area ratio", nucleusCellAreaRatio);
			}
		}
		
	}
	
	/**
	 * Standard measurements that may be computed from shapes.
	 */
	public static enum ShapeFeatures {
		/**
		 * Area of the shape.
		 */
		AREA,
		/**
		 * Length of the shape; for area geometries, this provides the perimeter.
		 */
		LENGTH,
		/**
		 * Circularity. This is available only for single-part polygonal shapes; holes are ignored.
		 */
		CIRCULARITY,
		/**
		 * Ratio of the area to the convex area.
		 */
		SOLIDITY,
		/**
		 * Maximum diameter; this is equivalent to the diameter of the minimum bounding circle.
		 */
		MAX_DIAMETER,
		/**
		 * Minimum diameter.
		 */
		MIN_DIAMETER,
		/**
		 * Nucleus/cell area ratio (only relevant to cell objects).
		 */
		NUCLEUS_CELL_RATIO;
		
		@Override
		public String toString() {
            return switch (this) {
                case AREA -> "Area";
                case CIRCULARITY -> "Circularity";
                case LENGTH -> "Length";
                case MAX_DIAMETER -> "Maximum diameter";
                case MIN_DIAMETER -> "Minimum diameter";
                case SOLIDITY -> "Solidity";
                case NUCLEUS_CELL_RATIO -> "Nucleus/Cell area ratio";
                default -> throw new IllegalArgumentException("Unknown feature " + this);
            };
		}
		
	}
	
	private static void addShapeMeasurements(MeasurementList ml, ROI roi, PixelCalibration cal, String baseName, Collection<ShapeFeatures> features) {
		if (roi == null)
			return;
		if (roi instanceof EllipseROI)
			addShapeMeasurements(ml, (EllipseROI)roi, cal, baseName, features);
		else {
			var geom = getScaledGeometry(roi, cal);			
			String units = cal.getPixelWidthUnit();
			addShapeMeasurements(ml, geom, baseName, units, features);
		}
	}
	
	
	private static void addShapeMeasurements(MeasurementList ml, EllipseROI ellipse, PixelCalibration cal, String baseName, Collection<ShapeFeatures> features) {
		String units = cal.getPixelWidthUnit();
		var units2 = units + "^2";
		if (!baseName.isEmpty() && !baseName.endsWith(" "))
			baseName += " ";
		
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		
		if (features.contains(ShapeFeatures.AREA))
			ml.put(baseName + "Area " + units2, ellipse.getScaledArea(pixelWidth, pixelHeight));
		if (features.contains(ShapeFeatures.LENGTH))
			ml.put(baseName + "Length " + units, ellipse.getLength());
		
		if (features.contains(ShapeFeatures.CIRCULARITY)) {
			ml.put(baseName + "Circularity", 1.0);
		}
		
		if (features.contains(ShapeFeatures.SOLIDITY)) {
			ml.put(baseName + "Solidity", 1.0);
		}
		
		if (features.contains(ShapeFeatures.MAX_DIAMETER)) {
			double maxDiameter = Math.max(ellipse.getBoundsWidth() * pixelWidth, ellipse.getBoundsHeight() * pixelHeight);
			ml.put(baseName + "Max diameter " + units, maxDiameter);
		}

		if (features.contains(ShapeFeatures.MIN_DIAMETER)) {
			double minDiameter = Math.min(ellipse.getBoundsWidth() * pixelWidth, ellipse.getBoundsHeight() * pixelHeight);
			ml.put(baseName + "Min diameter " + units, minDiameter);
		}
	}
	
	private static void addShapeMeasurements(MeasurementList ml, Geometry geom, String baseName, String units, Collection<ShapeFeatures> features) {
		boolean isArea = geom instanceof Polygonal;
		boolean isLine = geom instanceof Lineal;
		
		var units2 = units + "^2";
		if (!baseName.isEmpty() && !baseName.endsWith(" "))
			baseName += " ";
		
		double area = geom.getArea();
		double length = geom.getLength();
		
		if (isArea && features.contains(ShapeFeatures.AREA))
			ml.put(baseName + "Area " + units2, area);
		if ((isArea || isLine) && features.contains(ShapeFeatures.LENGTH))
			ml.put(baseName + "Length " + units, length);
		
		if (isArea && features.contains(ShapeFeatures.CIRCULARITY)) {
			if (geom instanceof Polygon polygon) {
                double ringArea, ringLength;
				if (polygon.getNumInteriorRing() == 0) {
					ringArea = area;
					ringLength = length;
				} else {
					var ring = polygon.getExteriorRing().getCoordinateSequence();
					ringArea = Area.ofRing(ring);
					ringLength = Length.ofLine(ring);
				}
				double circularity = Math.PI * 4 * ringArea / (ringLength * ringLength);
				ml.put(baseName + "Circularity", circularity);
			} else {
				logger.debug("Cannot compute circularity for {}", geom.getClass());
			}
		}
		
		if (isArea && features.contains(ShapeFeatures.SOLIDITY)) {
			double solidity = area / geom.convexHull().getArea();
			ml.put(baseName + "Solidity", solidity);
		}
		
		if (features.contains(ShapeFeatures.MAX_DIAMETER)) {
			double minCircleRadius = new MinimumBoundingCircle(geom).getRadius();
			ml.put(baseName + "Max diameter " + units, minCircleRadius*2);
		}

		if (features.contains(ShapeFeatures.MIN_DIAMETER)) {
			double minDiameter = new MinimumDiameter(geom).getLength();
			ml.put(baseName + "Min diameter " + units, minDiameter);
		}

	}

	/**
	 * Measure all channels of an image for multiple objects or cells.
	 * <p>
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * <p>
	 * Note: This can batch neighboring objects, so may be much faster than iteratively calling
	 * {@link #addIntensityMeasurements(ImageServer, PathObject, double, Collection, Collection)}
	 * for individuals objects.
	 * However, it is important that no individual object is very large (which could result in requesting
	 * too many pixels from the image in one go).
	 *
	 * @param server the server containing the pixels (and channels) to be measured
	 * @param pathObjects the objects to measure (the {@link MeasurementList} will be updated)
	 * @param downsample resolution at which to request pixels
	 * @param measurements requested measurements to make
	 * @param compartments the cell compartments to measure; ignored if the object is not a cell
	 * @throws IOException if there is a problem reading from the image
	 * @since v0.8.0
	 * @see #addIntensityMeasurements(ImageServer, Collection, double, Collection, Collection, Executor)
	 */
	public static void addIntensityMeasurements(
			ImageServer<BufferedImage> server,
			Collection<? extends PathObject> pathObjects,
			double downsample,
			Collection<Measurements> measurements,
			Collection<Compartments> compartments) throws IOException {
		addIntensityMeasurements(server, pathObjects, downsample, measurements, compartments, null);
	}

	/**
	 * Measure all channels of an image for multiple objects or cells, optionally using a specific executor.
	 * <p>
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * <p>
	 * Note: This can batch neighboring objects, so may be much faster than iteratively calling
	 * {@link #addIntensityMeasurements(ImageServer, PathObject, double, Collection, Collection)}
	 * for individuals objects.
	 * However, it is important that no individual object is very large (which could result in requesting
	 * too many pixels from the image in one go).
	 *
	 * @param server the server containing the pixels (and channels) to be measured
	 * @param pathObjects the objects to measure (the {@link MeasurementList} will be updated)
	 * @param downsample resolution at which to request pixels
	 * @param measurements requested measurements to make
	 * @param compartments the cell compartments to measure; ignored if the object is not a cell
	 * @param executor if not null, the measurement tasks are submitted to the executor rather than called directly
	 * @throws IOException if there is a problem reading from the image
	 * @since v0.8.0
	 * @see #addIntensityMeasurements(ImageServer, Collection, double, Collection, Collection)
	 */
	public static void addIntensityMeasurements(
			ImageServer<BufferedImage> server,
			Collection<? extends PathObject> pathObjects,
			double downsample,
			Collection<Measurements> measurements,
			Collection<Compartments> compartments,
			Executor executor) throws IOException {

		if (pathObjects.isEmpty()) {
			return;
		}

		if (measurements.isEmpty()) {
			logger.warn("No measurements selected");
			return;
		}

		var batches = createBatches(server, pathObjects, downsample);
		if (batches.isEmpty()) {
			return;
		}

		// Loop through batches
		for (var entry : batches.entrySet()) {
			var batchRequest = entry.getKey();
			var batch = entry.getValue();
			if (executor == null) {
				addIntensityMeasurementsBatch(server, batch, batchRequest, measurements, compartments);
			} else {
				executor.execute(() -> {
					try {
						if (Thread.currentThread().isInterrupted()) {
							logger.warn("Thread interrupted, skipping {} objects", pathObjects.size());
							return;
						}
						addIntensityMeasurementsBatch(server, batch, batchRequest, measurements, compartments);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
	}

	private static Map<RegionRequest, List<PathObject>> createBatches(ImageServer<BufferedImage> server,
																	  Collection<? extends PathObject> pathObjects,
																	  double downsample) {
		Map<ImagePlane, List<PathObject>> objectsByPlane = pathObjects.stream()
				.filter(PathObject::hasROI)
				.collect(Collectors.groupingBy(p -> p.getROI().getImagePlane()));

		if (objectsByPlane.isEmpty()) {
			return Map.of();
		} else if (objectsByPlane.size() == 1) {
			return createBatchesSinglePlane(server, pathObjects, downsample);
		} else {
			Map<RegionRequest, List<PathObject>> batches = new LinkedHashMap<>();
			for (var objectsOnPlane : objectsByPlane.values()) {
				batches.putAll(createBatchesSinglePlane(server, objectsOnPlane, downsample));
			}
			return batches;
		}
	}

	/**
	 * Create batches for objects that have ROIs all on a single plane.
	 * Note that it is up to the caller to verify that the input meets these criteria - this method does not check.
	 */
	private static Map<RegionRequest, List<PathObject>> createBatchesSinglePlane(ImageServer<BufferedImage> server,
																Collection<? extends PathObject> pathObjects,
																double downsample) {

		// Create a set of objects that have ROIs (probably all of them, but good to be sure)
		Set<PathObject> objectsToMeasure = pathObjects.stream().filter(PathObject::hasROI).collect(Collectors.toCollection(HashSet::new));

		// See if a single request will do the job
		int pad = (int)Math.ceil(downsample * 2);
		var globalRequest = createRequest(server, downsample, getAllROIs(objectsToMeasure), pad);
		if (globalRequest == null) {
			logger.warn("Cannot create region request for {} objects - no measurements will be made", objectsToMeasure.size());
			return Collections.emptyMap();
		}

		// Use 1 batch if we can: 1 object, or a single batch of objects on the same plane
		if (objectsToMeasure.size() == 1 || isSingleBatch(globalRequest)) {
			return Map.of(globalRequest, List.copyOf(objectsToMeasure));
		}

		// Create a spatial cache to identify objects near one another
		Map<RegionRequest, List<PathObject>> batches = new LinkedHashMap<>();
		var tree = new Quadtree();
		for (var pathObject : objectsToMeasure) {
			tree.insert(createEnvelope(pathObject), pathObject);
		}

		// Batch together nearby objects... we could try to optimize this cleverly, but it's probably not worth it
		int batchTileSize = (int)Math.round(1024 * downsample);
		for (int y = globalRequest.getMinY(); y < globalRequest.getMaxY(); y += batchTileSize) {
			for (int x = globalRequest.getMinX(); x < globalRequest.getMaxX(); x += batchTileSize) {
				var tempRequest = RegionRequest.createInstance(
						server.getPath(), downsample, x, y, batchTileSize, batchTileSize, globalRequest.getImagePlane()
				);
				@SuppressWarnings("unchecked") // Quadtree isn't typed - but we know what we put in
				var objects = (List<PathObject>)tree.query(GeometryTools.regionToEnvelope(tempRequest));
				// Only keep those that aren't already assigned
				objects.retainAll(objectsToMeasure);
				if (objects.isEmpty())
					continue;
				// We need to generate a new region to make sure it includes padding (and isn't bigger than necessary)
				var batchRequest = createRequest(server, downsample, getAllROIs(objects), pad);
				batches.put(batchRequest, List.copyOf(objects));
				objects.forEach(objectsToMeasure::remove);
				if (objectsToMeasure.isEmpty())
					break;
			}
		}

		// Check we did everything
		if (!objectsToMeasure.isEmpty()) {
			logger.warn("{} object(s) remain! This may be a bug, but I will create batches for them each.", objectsToMeasure.size());
			for (var pathObject : objectsToMeasure) {
				var list = List.of(pathObject);
				var batchRequest = createRequest(server, downsample, getAllROIs(list), pad);
				batches.put(batchRequest, list);
			}
		}

		return batches;
	}

	private static List<ROI> getAllROIs(Collection<? extends PathObject> pathObjects) {
		List<ROI> allROIs = new ArrayList<>();
		for (var pathObject : pathObjects) {
			var roi = pathObject.getROI();
			allROIs.add(roi);
			if (pathObject instanceof PathCellObject cell) {
				var nucleus = cell.getNucleusROI();
				if (nucleus != null) {
					if (!nucleus.getImagePlane().equals(roi.getImagePlane()))
						throw new IllegalArgumentException("Nucleus ROI must be on the same plane as cell ROI!");
					allROIs.add(nucleus);
				}
			}
		}
		return allROIs;
	}

	private static Envelope createEnvelope(PathObject pathObject) {
		var roi = pathObject.getROI();
		if (roi == null)
			return null;
		var envelope = GeometryTools.roiToEnvelope(roi);
		if (pathObject instanceof PathCellObject cell) {
			// The nucleus *should* be completely contained within the cell... but we are cautious here
			var nucleus = cell.getNucleusROI();
			if (nucleus != null) {
				envelope.expandToInclude(GeometryTools.roiToEnvelope(nucleus));
			}
		}
		return envelope;
	}


	private static boolean isSingleBatch(RegionRequest request) {
		double estimatedPixels = Math.ceil(request.getWidth() / request.getDownsample()) *
				Math.ceil(request.getHeight() / request.getDownsample());
		return estimatedPixels <= 2048 * 2048;
	}
	
	
	/**
	 * Measure all channels of an image for one individual object or cell.
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * <p>
	 * Note: This since v0.8.0
	 * {@link #addIntensityMeasurements(ImageServer, PathObject, double, Collection, Collection)}
	 * is more efficient to add measurements to large numbers of objects at once.
	 * 
	 * @param server the server containing the pixels (and channels) to be measured
	 * @param pathObject the cell to measure (the {@link MeasurementList} will be updated)
	 * @param downsample resolution at which to request pixels
	 * @param measurements requested measurements to make
	 * @param compartments the cell compartments to measure; ignored if the object is not a cell
	 * @throws IOException if there is a problem reading from the image
	 * @see #addIntensityMeasurements(ImageServer, Collection, double, Collection, Collection, Executor)
	 */
	public static void addIntensityMeasurements(
			ImageServer<BufferedImage> server,
			PathObject pathObject,
			double downsample,
			Collection<Measurements> measurements,
			Collection<Compartments> compartments) throws IOException {
		addIntensityMeasurements(server, List.of(pathObject), downsample, measurements, compartments, null);
	}


	/**
	 * Add intensity measurements for all cells in a batch, using a single image request.
	 * Important! The calling code needs to ensure that the bounding box of all the object ROIs is sensible.
	 */
	private static void addIntensityMeasurementsBatch(
			ImageServer<BufferedImage> server,
			Collection<? extends PathObject> pathObjects,
			RegionRequest request,
			Collection<Measurements> measurements,
			Collection<Compartments> compartments) throws IOException {

		var pathImage = IJTools.convertToImagePlus(server, request);
		var imp = pathImage.getImage();

		Map<String, ImageProcessor> channels = createChannels(server, imp);
		
		ByteProcessor bpCell = null;

		for (var pathObject : pathObjects) {
			var roi = pathObject.getROI();
			if (roi == null)
				continue;

			// Create or reset mask
			if (bpCell == null) {
				bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
				bpCell.setValue(1.0);
			} else {
				Arrays.fill((byte[])bpCell.getPixels(), (byte)0);
			}

			var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
			bpCell.fill(roiIJ);

			if (pathObject instanceof PathCellObject cell) {
				ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
				if (cell.getNucleusROI() != null) {
					bpNucleus.setValue(1.0);
					var roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
					bpNucleus.fill(roiNucleusIJ);
				}
				measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, compartments, measurements);
			} else {
				var imgLabels = new PixelImageIJ(bpCell);
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey(), measurements);
				}
			}
		}
	}

	private static RegionRequest createRequest(ImageServer<?> server, double downsample, Collection<? extends ROI> rois, int pad) {
		ImagePlane plane = null;
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (var roi : rois) {
			if (roi == null)
				continue;
			if (plane == null) {
				plane = roi.getImagePlane();
			} else if (!plane.equals(roi.getImagePlane())) {
				throw new IllegalArgumentException("ROIs are not on the same image plane!");
			}
			minX = Math.min(minX, roi.getBoundsX());
			minY = Math.min(minY, roi.getBoundsY());
			maxX = Math.max(maxX, roi.getBoundsX() + roi.getBoundsWidth());
			maxY = Math.max(maxY, roi.getBoundsY() + roi.getBoundsHeight());
		}
		// No ROIs
		if (plane == null) {
			return null;
		}
		int x = Math.max(0, (int)Math.floor(minX) - pad);
		int y = Math.max(0, (int)Math.floor(minY) - pad);
		int w = (int)Math.min(Math.ceil(maxX + pad), server.getWidth()) - x;
		int h = (int)Math.min(Math.ceil(maxY + pad), server.getHeight()) - y;
		return RegionRequest.createInstance(server.getPath(), downsample, x, y, w, h, plane);
	}


	private static Map<String, ImageProcessor> createChannels(ImageServer<BufferedImage> server, ImagePlus imp) {
		Map<String, ImageProcessor> channels = new LinkedHashMap<>();
		var serverChannels = server.getMetadata().getChannels();
		if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor cp) {
			for (int i = 0; i < serverChannels.size(); i++) {
				channels.put(serverChannels.get(i).getName(), cp.getChannel(i+1, null));
			}
		} else {
			assert imp.getStackSize() == serverChannels.size();
			for (int i = 0; i < imp.getStackSize(); i++) {
				channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
			}
		}
		return channels;
	}

	
	/**
	 * Make cell measurements based on labelled images.
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * 
	 * @param ipNuclei labelled image representing nuclei
	 * @param ipCells labelled image representing cells
	 * @param pathObjects cell objects mapped to integer values in the labelled images
	 * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
	 * @param compartments the cell compartments to measure
	 * @param measurements requested measurements to make
	 */
	private static void measureCells(
			ImageProcessor ipNuclei, ImageProcessor ipCells,
			Map<? extends Number, ? extends PathObject> pathObjects,
			Map<String, ImageProcessor> channels,
			Collection<Compartments> compartments,
			Collection<Measurements> measurements) {
		
		var array = mapToArray(pathObjects);
//		PathObjectTools.constrainCellByScaledNucleus(cell, nucleusScaleFactor, keepMeasurements)
		int width = ipNuclei.getWidth();
		int height = ipNuclei.getHeight();
		ImageProcessor ipMembrane = new FloatProcessor(width, height);
		ImageProcessor ipCytoplasm = ipCells.duplicate();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float cell = ipCells.getf(x, y);
				float nuc = ipNuclei.getf(x, y);
				if (nuc != 0f)
					ipCytoplasm.setf(x, y, 0f);
				if (cell == 0f)
					continue;
				// Check 4-neighbours to decide if we're at the membrane
				if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
						(y < height-1 && ipCells.getf(x, y+1) != cell) ||
						(x >= 1 && ipCells.getf(x-1, y) != cell) ||
						(x < width-1 && ipCells.getf(x+1, y) != cell))
					ipMembrane.setf(x, y, cell);
			}			
		}
		
		var imgNuclei = new PixelImageIJ(ipNuclei);
		var imgCells = new PixelImageIJ(ipCells);
		var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
		var imgMembrane = new PixelImageIJ(ipMembrane);

		// Use legacy names, from before QuPath v0.6.0
		// These encoded the channel name first, rather than after the cell compartment, but this made them less
		boolean useLegacyNames = Boolean.parseBoolean(System.getProperty("OBJECT_MEASUREMENTS_USE_LEGACY_NAMES", "false").strip());

		if (useLegacyNames) {
			for (var entry : channels.entrySet()) {
				var img = new PixelImageIJ(entry.getValue());
				if (compartments.contains(Compartments.NUCLEUS))
					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
				if (compartments.contains(Compartments.CYTOPLASM))
					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
				if (compartments.contains(Compartments.MEMBRANE))
					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
				if (compartments.contains(Compartments.CELL))
					measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
			}
		} else {
			// 'New' names group measurements by compartment first, then channel
			if (compartments.contains(Compartments.NUCLEUS)) {
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					String channelName = entry.getKey().trim();
					measureObjects(img, imgNuclei, array, "Nucleus: " + channelName, measurements);
				}
			}
			if (compartments.contains(Compartments.CYTOPLASM)) {
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					String channelName = entry.getKey().trim();
					measureObjects(img, imgCytoplasm, array, "Cytoplasm: " + channelName, measurements);
				}
			}
			if (compartments.contains(Compartments.MEMBRANE)) {
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					String channelName = entry.getKey().trim();
					measureObjects(img, imgMembrane, array, "Membrane: " + channelName, measurements);
				}
			}
			if (compartments.contains(Compartments.CELL)) {
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					String channelName = entry.getKey().trim();
					measureObjects(img, imgCells, array, "Cell: " + channelName, measurements);
				}
			}
		}
		
	}
	
	
	private static PathObject[] mapToArray(Map<? extends Number, ? extends PathObject> pathObjects) {
		Number[] labels = new Number[pathObjects.size()];
		int n = 0;
		long maxLabel = 0L;
		int invalidLabels = 0;
		for (var label : pathObjects.keySet()) {
			long lab = label.longValue();
			if (lab < 0 || lab != label.doubleValue() || lab >= Integer.MAX_VALUE) {
				invalidLabels++;
			} else {
				labels[n] = label;
				maxLabel = Math.max(lab, maxLabel);
				n++;
			}
		}
		
		if (invalidLabels > 0) {
			logger.warn("Only {}/{} labels are integer values >= 0 and < Integer.MAX_VALUE, the rest will be discarded!",
					n, pathObjects.size());
		}
		
		PathObject[] array = new PathObject[n];
		for (var label : labels) {
			array[label.intValue()-1] = pathObjects.get(label);
		}
		return array;
	}
	
	/**
	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
	 * @param img intensity values to measure
	 * @param imgLabels labels corresponding to objects
	 * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
	 * @param baseName base name to include when adding measurements (e.g. the channel name)
	 * @param measurements requested measurements
	 */
	private static void measureObjects(
			SimpleImage img, SimpleImage imgLabels,
			PathObject[] pathObjects,
			String baseName, Collection<Measurements> measurements) {
		
		// Initialize stats
		int n = pathObjects.length;
		DescriptiveStatistics[] allStats = new DescriptiveStatistics[n];
		for (int i = 0; i < n; i++)
			allStats[i] = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);
		
		// Compute statistics
		int width = img.getWidth();
		int height = img.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int label = (int)imgLabels.getValue(x, y);
				if (label <= 0 || label > n)
					continue;
				float val = img.getValue(x, y);
				allStats[label-1].addValue(val);
			}
		}
		
		// Add measurements
		if (!(measurements instanceof Set))
			measurements = new LinkedHashSet<>(measurements);
		for (int i = 0; i < n; i++) {
			var pathObject = pathObjects[i];
			if (pathObject == null)
				continue;
			var stats = allStats[i];
			try (var ml = pathObject.getMeasurementList()) {
				for (var m : measurements) {
					ml.put(baseName + ": " + m.getMeasurementName(), m.getMeasurement(stats));
				}
			}
		}
	}

}