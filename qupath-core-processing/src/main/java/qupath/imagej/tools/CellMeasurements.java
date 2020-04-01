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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;

/**
 * Experimental class to generate cell measurements from labelled images.
 * May or may not survive into a future release...
 * 
 * @author Pete Bankhead
 *
 */
public class CellMeasurements {
	
	final private static Logger logger = LoggerFactory.getLogger(CellMeasurements.class);
	
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
	 * Measure all channels of an image for one individual cell.
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * 
	 * @param server the server containing the pixels (and channels) to be measured
	 * @param cell the cell to measure (the {@link MeasurementList} will be updated)
	 * @param downsample resolution at which to request pixels
	 * @param measurements requested measurements to make
	 * @throws IOException
	 */
	public static void measureCell(
			ImageServer<BufferedImage> server,
			PathCellObject cell,
			double downsample,
			Collection<Measurements> measurements) throws IOException {
		
		var cellROI = cell.getROI();
		int x = (int)GeneralTools.clipValue(cellROI.getBoundsX()-downsample*2, 0, server.getWidth());
		int y = (int)GeneralTools.clipValue(cellROI.getBoundsY()-downsample*2, 0, server.getHeight());
		int x2 = (int)GeneralTools.clipValue(cellROI.getBoundsX()+cellROI.getBoundsWidth()+downsample*2, 0, server.getWidth());
		int y2 = (int)GeneralTools.clipValue(cellROI.getBoundsY()+cellROI.getBoundsHeight()+downsample*2, 0, server.getHeight());
		var request = RegionRequest.createInstance(server.getPath(), downsample,
				x, y, x2-x, y2-y,
				cellROI.getZ(), cellROI.getT());
		
		var pathImage = IJTools.convertToImagePlus(server, request);
		var imp = pathImage.getImage();
		
		Map<String, ImageProcessor> channels = new LinkedHashMap<>();
		var serverChannels = server.getMetadata().getChannels();
		for (int i = 0; i < imp.getStackSize(); i++) {
			channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
		}
		
		ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
		ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
		if (cell.getNucleusROI() != null) {
			bpNucleus.setValue(1.0);
			var roi = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
			bpNucleus.fill(roi);
		}
		if (cell.getROI() != null) {
			bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
			bpCell.setValue(1.0);
			var roi = IJTools.convertToIJRoi(cell.getROI(), pathImage);
			bpCell.fill(roi);
		}
		measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, measurements);
	}
	
	/**
	 * Make cell measurements based on labelled images.
	 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
	 * 
	 * @param ipNuclei labelled image representing nuclei
	 * @param ipCells labelled image representing cells
	 * @param pathObjects cell objects mapped to integer values in the labelled images
	 * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
	 * @param measurements requested measurements to make
	 */
	public static void measureCells(
			ImageProcessor ipNuclei, ImageProcessor ipCells,
			Map<? extends Number, PathObject> pathObjects,
			Map<String, ImageProcessor> channels,
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
			
			measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
			measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
			measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
			measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
			
		}
		
	}
	
	
	private static PathObject[] mapToArray(Map<? extends Number, PathObject> pathObjects) {
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
	 * @param pathObjects map between label values and objects
	 * @param baseName base name to include when adding measurements (e.g. the channel name)
	 * @param measurements requested measurements
	 */
	public static void measureObjects(
			SimpleImage img, SimpleImage imgLabels,
			Map<? extends Number, PathObject> pathObjects,
			String baseName, Collection<Measurements> measurements) {
		
		measureObjects(img, imgLabels, mapToArray(pathObjects), baseName, measurements);
	}
	
	/**
	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
	 * @param img intensity values to measure
	 * @param imgLabels labels corresponding to objects
	 * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
	 * @param baseName base name to include when adding measurements (e.g. the channel name)
	 * @param measurements requested measurements
	 */
	public static void measureObjects(
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
