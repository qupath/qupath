package qupath.imagej.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Experimental class to generate cell measurements from labelled images.
 * May or may not survive into a future release...
 * 
 * @author Pete Bankhead
 *
 */
public class CellMeasurements {
	
	private final static Logger logger = LoggerFactory.getLogger(CellMeasurements.class);
	
	/**
	 * Requested intensity measurements.
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
			switch (this) {
			case MAX:
				return "Max";
			case MEAN:
				return "Mean";
			case MEDIAN:
				return "Median";
			case MIN:
				return "Min";
			case STD_DEV:
				return "Std.Dev.";
			case VARIANCE:
				return "Variance";
			default:
				throw new IllegalArgumentException("Unknown measurement " + this);
			}
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
	
	/**
	 * Add shape measurements for the object. If this is a cell, measurements will be made for both the 
	 * nucleus and cell boundary where possible.
	 * <p>
	 * Note: This implementation is likely to change in the future, to enable specific shape measurements to be requested.
	 * 
	 * @param pathObject the object for which measurements should be added
	 * @param cal pixel calibration, used to determine units and scaling
	 */
	public static void addShapeMeasurements(PathObject pathObject, PixelCalibration cal) {
		if (cal == null || !cal.unitsMatch2D())
			cal = PixelCalibration.getDefaultInstance();
		var units = cal.getPixelWidthUnit();
		if (pathObject instanceof PathCellObject) {
			addCellShapeMeasurements((PathCellObject)pathObject, cal, units);
		} else {
			var geom = getScaledGeometry(pathObject.getROI(), cal);
			try (var ml = pathObject.getMeasurementList()) {
				addShapeMeasurements(ml, geom, "", units);
			}
		}
	}
	
	private static Geometry getScaledGeometry(ROI roi, PixelCalibration cal) {
		if (roi == null)
			return null;
		var geom = roi.getGeometry();
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		if (pixelWidth == 1 && pixelHeight == 1) 
			return geom;
		return AffineTransformation.scaleInstance(pixelWidth, pixelHeight).transform(geom);
	}
	
	private static void addCellShapeMeasurements(PathCellObject cell, PixelCalibration cal, String units) {
		var geom = getScaledGeometry(cell.getROI(), cal);
		var geomNucleus = getScaledGeometry(cell.getNucleusROI(), cal);
		try (MeasurementList ml = cell.getMeasurementList()) {
			if (geomNucleus != null) {
				addShapeMeasurements(ml, geomNucleus, "Nucleus: ", units);
			}
			addShapeMeasurements(ml, geom, "Cell: ", units);
			if (geomNucleus != null) {
				double nucleusCellAreaRatio = GeneralTools.clipValue(geomNucleus.getArea() / geom.getArea(), 0, 1);
				ml.putMeasurement("Nucleus/Cell area ratio", nucleusCellAreaRatio);
			}
		}
		
	}
	
	private static void addShapeMeasurements(MeasurementList ml, Geometry geom, String baseName, String units) {
		if (geom instanceof Puntal)
			return;
		
		if (geom instanceof Lineal) {
			ml.putMeasurement(baseName + "Length " + units, geom.getLength());
			return;
		}
		
		if (!(geom instanceof Polygonal))
			return;
			
		var units2 = units + "^2";
		if (!baseName.isEmpty() && !baseName.endsWith(" "))
			baseName += " ";
		
		double area = geom.getArea();
		double length = geom.getLength();
		
		ml.putMeasurement(baseName + "Area " + units2, area);
		ml.putMeasurement(baseName + "Length " + units, length);
		
		if (geom instanceof Polygon) {
			double circularity = Math.PI * 4 * area / (length * length);
			ml.putMeasurement(baseName + "Circularity", circularity);
		}
		
		double solidity = area / geom.convexHull().getArea();
		ml.putMeasurement(baseName + "Solidity", solidity);
		
		double minCircleRadius = new MinimumBoundingCircle(geom).getRadius();
		ml.putMeasurement(baseName + "Max diameter", minCircleRadius*2);

		double minDiameter = new MinimumDiameter(geom).getLength();
		ml.putMeasurement(baseName + "Min diameter", minDiameter);

	}
	
	
	
	/**
	 * Measure all channels of an image for one individual object or cell.
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * <p>
	 * Note: This implementation is likely to change in the future, to enable neighboring cells to be 
	 * measured more efficiently.
	 * 
	 * @param server the server containing the pixels (and channels) to be measured
	 * @param pathObject the cell to measure (the {@link MeasurementList} will be updated)
	 * @param downsample resolution at which to request pixels
	 * @param measurements requested measurements to make
	 * @param compartments the cell compartments to measure; ignored if the object is not a cell
	 * @throws IOException
	 */
	public static void addIntensityMeasurements(
			ImageServer<BufferedImage> server,
			PathObject pathObject,
			double downsample,
			Collection<Measurements> measurements,
			Collection<Compartments> compartments) throws IOException {
		
		var roi = pathObject.getROI();
		
		int pad = (int)Math.ceil(downsample * 2);
		var request = RegionRequest.createInstance(server.getPath(), downsample, roi)
				.pad2D(pad, pad)
				.intersect2D(0, 0, server.getWidth(), server.getHeight());
		
		var pathImage = IJTools.convertToImagePlus(server, request);
		var imp = pathImage.getImage();
		
		Map<String, ImageProcessor> channels = new LinkedHashMap<>();
		var serverChannels = server.getMetadata().getChannels();
		for (int i = 0; i < imp.getStackSize(); i++) {
			channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
		}
		
		ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
		bpCell.setValue(1.0);
		var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
		bpCell.fill(roiIJ);
			
		if (pathObject instanceof PathCellObject) {
			var cell = (PathCellObject)pathObject;
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
				measureObjects(img, imgLabels, new PathObject[] {pathObject}, entry.getKey(), measurements);
			}
		}
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
	
//	/**
//	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
//	 * @param img intensity values to measure
//	 * @param imgLabels labels corresponding to objects
//	 * @param pathObjects map between label values and objects
//	 * @param baseName base name to include when adding measurements (e.g. the channel name)
//	 * @param measurements requested measurements
//	 */
//	private static void measureObjects(
//			SimpleImage img, SimpleImage imgLabels,
//			Map<? extends Number, ? extends PathObject> pathObjects,
//			String baseName, Collection<Measurements> measurements) {
//		
//		measureObjects(img, imgLabels, mapToArray(pathObjects), baseName, measurements);
//	}
	
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
					ml.putMeasurement(baseName + ": " + m.getMeasurementName(), m.getMeasurement(stats));
				}
			}
		}
	}

}
