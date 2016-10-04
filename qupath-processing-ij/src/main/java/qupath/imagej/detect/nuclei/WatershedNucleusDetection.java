/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.detect.nuclei;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.filter.EDM;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.color.ColorDeconvolutionIJ;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.objects.measure.ObjectMeasurements;
import qupath.imagej.processing.MorphologicalReconstruction;
import qupath.imagej.processing.ROILabeling;
import qupath.imagej.processing.RegionalExtrema;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.processing.Watershed;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;

/**
 * Original nucleus detection plugin.
 * 
 * How now largely been replaced with WatershedCellDetection.
 * 
 * @author Pete Bankhead
 *
 */
public class WatershedNucleusDetection extends AbstractTileableDetectionPlugin<BufferedImage> {

	private ParameterList params;
	
//	transient protected WatershedNucleusDetector detector;

	private String lastResults = null;
	
	
	public WatershedNucleusDetection() {
		params = new ParameterList();
		// TODO: Use a better way to determining if pixel size is available in microns
		//		params.addEmptyParameter("detectionParameters", "Detection parameters", true);

		String microns = GeneralTools.micrometerSymbol();
		params.addDoubleParameter("requestedPixelSizeMicrons", "Requested pixel size", .5, microns, 
				"Choose pixel size at which detection will be performed - higher values are likely to be faster, but may be less accurate; set <= 0 to use the full image resolution");
//		
		params.addDoubleParameter("backgroundRadiusMicrons", "Background radius", 8, microns);
		params.addDoubleParameter("holesRadiusMicrons", "Fill holes radius", 0, microns);
		params.addDoubleParameter("medianRadiusMicrons", "Median filter radius", 0, microns);
		params.addDoubleParameter("sigmaMicrons", "Sigma", 1.5, microns);
		params.addDoubleParameter("minAreaMicrons", "Minimum area", 10, microns+"^2");
		params.addDoubleParameter("maxAreaMicrons", "Maximum area", 500, microns+"^2");

		params.addDoubleParameter("backgroundRadius", "Background radius", 15, "px");
		params.addDoubleParameter("holesRadius", "Fill holes radius", 0, "px");
		params.addDoubleParameter("medianRadius", "Median filter radius", 0, "px");
		params.addDoubleParameter("sigma", "Sigma", 3, "px");
		params.addDoubleParameter("minArea", "Minimum area", 10, "px^2");
		params.addDoubleParameter("maxArea", "Maximum area", 1000, "px^2");


		params.addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 2.5);
		params.addDoubleParameter("maxBackground", "Max background intensity", 2, null);

		params.addBooleanParameter("subtractDABBackground", "Subtract DAB background staining", false);

		params.addBooleanParameter("mergeAll", "Merge all", true);
		params.addBooleanParameter("watershedPostProcess", "Split by shape", true);
		params.addBooleanParameter("excludeDAB", "Exclude DAB", false);
		params.addBooleanParameter("smoothBoundaries", "Smooth boundaries", true);

		//		params.addEmptyParameter("classification", "Classification", true);
		params.addDoubleParameter("positiveThreshold", "Positive threshold", 0.2, null, 0, 2.5);
		params.addBooleanParameter("positiveOnly", "Keep positive only", false);
	}
	
	
	
	@Override
	public String getDescription() {
		return "Nucleus detection for brightfield IHC images, with built-in threshold to distinguish positive and negative nuclei to create a percentage score";
	}

	
	
	synchronized void detectorComplete(final WatershedNucleusDetector detector) {
		if (detector != null)
			this.lastResults = detector.getLastResultsDescription();
	}
	
	

	static class WatershedNucleusDetector implements ObjectDetector<BufferedImage> {

		private String lastResultsMessage = null;

//		private String lastServerPath = null;
//		private PathImage<ImagePlus> pathImage;
//		private ROI pathROI;
//		private FloatProcessor fpH, fpDAB;

//		private WatershedNucleusDetector2 detector2;
//		transient private ColorDeconvolutionStains lastStains = null;
		
		transient private WatershedNucleusDetection plugin;
		
		WatershedNucleusDetector(final WatershedNucleusDetection plugin) {
			this.plugin = plugin;
		}
		
		
		public double getPreferredPixelSizeMicrons() {
			return plugin == null || plugin.params == null ? 0.5 : plugin.params.getDoubleParameterValue("requestedPixelSizeMicrons");
		}

		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			if (stains == null) {
				lastResultsMessage = "Currently nucleus detection only works with H&E or H-DAB images!";
//				throw new RuntimeException("Currently nucleus detection only works with H&E or H-DAB images!");
				return null;
			}

			lastResultsMessage = "";

			// TODO: Give a sensible error
			if (pathROI == null)
				return null;
			// Get a PathImage if we have a new ROI
			PathImage<ImagePlus> pathImage = null;
//			boolean imageChanged = stains != lastStains || !imageData.getServer().getPath().equals(lastServerPath);
//			if (lastServerPath == null || !imageData.getServerPath().equals(lastServerPath) || !pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				pathImage = PathImagePlus.createPathImage(server, pathROI, ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(), true));
//				this.pathROI = pathROI;
//				imageChanged = true;
//			}
			// Create a detector if we don't already have one for this image
//			if (detector2 == null || imageChanged) {
			FloatProcessor fpH, fpDAB;
				ImageProcessor ip = pathImage.getImage().getProcessor();
				if (ip instanceof ColorProcessor) {
					FloatProcessor[] fps = ColorDeconvolutionIJ.colorDeconvolve((ColorProcessor)ip, stains.getStain(1), stains.getStain(2), stains.getStain(3));
					fpH = fps[0];
					if (stains.isH_DAB())
						fpDAB = fps[1];
					else
						fpDAB = null;
				} else {
					fpH = ip.convertToFloatProcessor();
					fpDAB = null;
				}
				Roi roi = null;
				if (pathROI != null)
					roi = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
				WatershedNucleusDetector2 detector2 = new WatershedNucleusDetector2(fpH, fpDAB, roi);
				
//				lastServerPath = imageData.getServer().getPath();
//			}

			// Create or reset the PathObjects list
			List<PathObject> pathObjects = new ArrayList<>();


			// Convert parameters where needed
			double sigma, holesRadius, medianRadius, backgroundRadius, minArea, maxArea;
			boolean subtractDABBackground = params.getBooleanParameterValue("subtractDABBackground");
			if (pathImage.hasPixelSizeMicrons()) {
				double pixelSize = 0.5 * (pathImage.getPixelHeightMicrons() + pathImage.getPixelWidthMicrons());
				backgroundRadius = params.getDoubleParameterValue("backgroundRadiusMicrons") / pixelSize;
				holesRadius = params.getDoubleParameterValue("holesRadiusMicrons") / pixelSize;
				medianRadius = params.getDoubleParameterValue("medianRadiusMicrons") / pixelSize;
				sigma = params.getDoubleParameterValue("sigmaMicrons") / pixelSize;
				minArea = params.getDoubleParameterValue("minAreaMicrons") / (pixelSize * pixelSize);
				maxArea = params.getDoubleParameterValue("maxAreaMicrons") / (pixelSize * pixelSize);
			} else {
				backgroundRadius = params.getDoubleParameterValue("backgroundRadius");
				holesRadius = params.getDoubleParameterValue("holesRadius");
				medianRadius = params.getDoubleParameterValue("medianRadius");
				sigma = params.getDoubleParameterValue("sigma");
				minArea = params.getDoubleParameterValue("minArea");
				maxArea = params.getDoubleParameterValue("maxArea");
			}

			detector2.runDetection(
					backgroundRadius,
					holesRadius,
					params.getDoubleParameterValue("maxBackground"),
					medianRadius,
					sigma,
					params.getDoubleParameterValue("threshold"),
					minArea,
					maxArea,
					params.getBooleanParameterValue("mergeAll"),
					params.getBooleanParameterValue("watershedPostProcess"),
					params.getBooleanParameterValue("excludeDAB"),
					params.getBooleanParameterValue("smoothBoundaries"));


			//		long startTime = System.currentTimeMillis();
			Calibration cal = pathImage.getImage().getCalibration();
			// Compute how many measurements we will need, to help with setting capacity from the start
			int nShape = 9;
			int nIntensity = 5;
			int nMeasurements = fpDAB == null ? nShape + nIntensity : nShape + nIntensity * 2;

			// Subtract DAB background, if required
			ImageProcessor ipDABToMeasure = null;
			if (fpDAB != null && subtractDABBackground && backgroundRadius > 0) {
				fpDAB.resetRoi();
				ipDABToMeasure = MorphologicalReconstruction.openingByReconstruction(fpDAB, backgroundRadius);
				for (int i = 0; i < ipDABToMeasure.getWidth()*ipDABToMeasure.getHeight(); i++)
					ipDABToMeasure.setf(i, fpDAB.getf(i) - ipDABToMeasure.getf(i));
				ipDABToMeasure.resetMinAndMax();
				//			new ImagePlus("Measure", ipDABToMeasure).show();
			} else
				ipDABToMeasure = fpDAB;

			for (Roi r : detector2.getOverlay().toArray()) {

				// TODO: It would be more efficient to measure the hematoxylin intensities along with the shapes

				// Create a DefaultMeasurementList if we need one, otherwise create a new instance from the previous list
				MeasurementList measurementList = MeasurementListFactory.createMeasurementList(nMeasurements, MeasurementList.TYPE.FLOAT);

				//				measurementList = new BasicFloatMeasurementList(nMeasurements);
				ObjectMeasurements.addShapeStatistics(measurementList, r, fpH, cal, "Nucleus: ");
				//			PathObject pathObject = new PathObjectIJ(pathImage, r, null);

				//			pathObject = new PathObject(new PathPolygonROI(PathPolygonROI.smoothPoints(((PathPolygonROI)pathObject.getROI()).getPolygonPoints())));
				//			r = PathROIConverterIJ.convertToIJRoi(pathObject.getROI(), pathImage);

				fpH.setRoi(r);
				ImageStatistics stats = fpH.getStatistics();
				if (stats.pixelCount < minArea)
					continue;
				measurementList.addMeasurement("Nucleus: Hematoxylin OD mean", stats.mean);
				measurementList.addMeasurement("Nucleus: Hematoxylin OD std dev", stats.stdDev);
				measurementList.addMeasurement("Nucleus: Hematoxylin OD max", stats.max);
				measurementList.addMeasurement("Nucleus: Hematoxylin OD min", stats.min);
				measurementList.addMeasurement("Nucleus: Hematoxylin OD range", stats.max - stats.min);

				if (ipDABToMeasure != null) {
					ipDABToMeasure.setRoi(r);
					stats = ipDABToMeasure.getStatistics();
					measurementList.addMeasurement("Nucleus: DAB OD mean", stats.mean);
					measurementList.addMeasurement("Nucleus: DAB OD std dev", stats.stdDev);
					measurementList.addMeasurement("Nucleus: DAB OD max", stats.max);
					measurementList.addMeasurement("Nucleus: DAB OD min", stats.min);
					measurementList.addMeasurement("Nucleus: DAB OD range", stats.max - stats.min);
				}

				//			measurementList.trimToSize();

				// Convert to a FixedMeasurementList if we don't already have one
				//			if (!(measurementList instanceof FixedMeasurementList))
				//				measurementList = FixedMeasurementListFactory.createFixedMeasurementList(measurementList, true);

				//			measurementList = MeasurementListFactory.createUnmodifiableMeasurementList(measurementList, true);
				measurementList.closeList();
				PathObject pathObject = new PathDetectionObject(ROIConverterIJ.convertToPathROI(r, pathImage), null, measurementList);
				pathObjects.add(pathObject);
			}
			//		long endTime = System.currentTimeMillis();
			//		System.out.println("Measurement time: " + (endTime-startTime)/1000.0);

			boolean nucleiClassified = false;
			double posThreshold = params.getDoubleParameterValue("positiveThreshold");
			if (posThreshold > 0 && fpDAB != null) {
				classifyObjectsByThreshold(pathObjects, "Nucleus: DAB OD mean", new double[]{Double.NEGATIVE_INFINITY, posThreshold},
						new PathClass[]{PathClassFactory.getPathClass(PathClassFactory.getNegativeClassName()), PathClassFactory.getPathClass(PathClassFactory.getPositiveClassName())});
				nucleiClassified = true;
			}
			else {
				classifyAllObjects(pathObjects, PathClassFactory.getPathClass("Nucleus"));
				nucleiClassified = false;
			}

			// Keep positive only, if required
			if (nucleiClassified && params.getBooleanParameterValue("positiveOnly")) {
				Iterator<PathObject> iter = pathObjects.iterator();
				while (iter.hasNext()) {
					PathClass positiveClass = PathClassFactory.getPathClass(PathClassFactory.getPositiveClassName());
					if (!positiveClass.equals(iter.next().getPathClass()))
						iter.remove();
				}
			}

			if (pathObjects != null) {
				int nDetections = pathObjects.size();
				if (nDetections == 0)
					lastResultsMessage = "No nuclei detected";
				else if (nDetections == 1)
					lastResultsMessage = "1 nucleus detected";
				String s = String.format("%d nuclei detected", nDetections);
				if (nucleiClassified) {
					int nPositive = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getPositiveClassName()), false);
					int nNegative = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getNegativeClassName()), false);
					lastResultsMessage = String.format("%s (%.3f%% positive)", s, ((double)nPositive * 100.0 / (nPositive + nNegative)));			
				} else
					lastResultsMessage = s;
			}

			// Notify plugin of results
			if (plugin != null)
				plugin.detectorComplete(this);

			return pathObjects;
		}

		@Override
		public String getLastResultsDescription() {
			return lastResultsMessage;
		}
		
		
		
		
		/**
		 * The PathClass of each object will be set to pathClasses[i] for the lowest value of i for which the following holds:
		 * 		pathObject.getMeasurement(measurementName).getValue() >= thresholds[i]
		 * 
		 * thresholds and pathClasses should be arrays of the same length.
		 * 
		 * @param pathObjects
		 * @param thresholds
		 * @param pathClasses
		 */
		private static void classifyObjectsByThreshold(List<? extends PathObject> pathObjects, String measurementName, double[] thresholds, PathClass[] pathClasses) {
			for (PathObject pathObject : pathObjects) {
				double measurement = pathObject.getMeasurementList().getMeasurementValue(measurementName);
				for (int i = 0; i < thresholds.length; i++) {
					if (measurement >= thresholds[i])
						pathObject.setPathClass(pathClasses[i]);
					else
						break;
				}
			}
		}
		
		
		
		private static void classifyAllObjects(List<? extends PathObject> pathObjects, PathClass pathClass) {
			for (PathObject pathObject : pathObjects)
				pathObject.setPathClass(pathClass);
		}

	}



	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		boolean micronsKnown = imageData.getServer().hasPixelSizeMicrons();
		Map<String, Parameter<?>> map = params.getParameters();

		map.get("backgroundRadiusMicrons").setHidden(!micronsKnown);
		map.get("holesRadiusMicrons").setHidden(!micronsKnown);
		map.get("medianRadiusMicrons").setHidden(!micronsKnown);
		map.get("sigmaMicrons").setHidden(!micronsKnown);
		map.get("minAreaMicrons").setHidden(!micronsKnown);
		map.get("maxAreaMicrons").setHidden(!micronsKnown);

		map.get("backgroundRadius").setHidden(micronsKnown);
		map.get("holesRadius").setHidden(micronsKnown);
		map.get("medianRadius").setHidden(micronsKnown);
		map.get("sigma").setHidden(micronsKnown);
		map.get("minArea").setHidden(micronsKnown);
		map.get("maxArea").setHidden(micronsKnown);

		return params;
	}

	@Override
	public String getName() {
		return "Watershed nucleus detection";
	}

	@Override
	public String getLastResultsDescription() {
		return lastResults;
//		return detector == null ? "" : detector.getLastResultsDescription();
	}

//	@Override
//	protected void addRunnableTasks(ImageData imageData,	PathObject parentObject, List<Runnable> tasks) {
//		if (detector == null || detector.pathROI != parentObject.getROI())
//			detector = new WatershedNucleusDetector();
//		tasks.add(createRunnableTask(detector, getCurrentParameterList(imageData), imageData, parentObject));
//	}

	@Override
	protected double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0.5;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		return new WatershedNucleusDetector(this);
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 25;
	}

}





class WatershedNucleusDetector2 {

	private double backgroundRadius = 15;
	private double maxBackground = 0.3;

	private double holesRadius = 0;
	private double medianRadius = 0;
	private double sigma = 2.5;
	private double threshold = 0.3;
	private boolean mergeAll = true;
	private boolean watershedPostProcess = true; // TODO: COMBINE WITH MERGEALL OPTION
	private boolean excludeDAB = false;
	private boolean smoothBoundaries = false;

	private double minArea = 0;
	private double maxArea = Double.POSITIVE_INFINITY;

	private Roi roi = null;
	private FloatProcessor fpH = null;
	private FloatProcessor fpDAB = null;
	private ImageProcessor ipToMeasure = null;
	private ImageProcessor ipBackground = null;
	private List<PolygonRoi> rois = null;
	private ByteProcessor bpLoG = null;
	private Overlay overlay = null;

	public WatershedNucleusDetector2(FloatProcessor fpH, FloatProcessor fpDAB, Roi roi) {
		Prefs.setThreads(1); // When parallelising a lot, this really helps... Maximum filters & Gaussian filters start up many threads
		this.fpH = fpH;
		this.fpDAB = fpDAB;
		this.roi = roi;
	}


	private void doDetection(boolean regenerateROIs) {
		overlay = null;
		ByteProcessor bp = null;
		fpH.setRoi(roi);
		if (regenerateROIs) {
			rois = null;
			bpLoG = null;

			RankFilters rf = new RankFilters();

			// Use Laplacian of Gaussian filtering followed by watershed transform to determine possible nucleus segments
			// Result will be a dramatic over-segmentation...
			FloatProcessor fpLoG = (FloatProcessor)fpH.duplicate();

			if (holesRadius > 0)
				fpLoG = (FloatProcessor)MorphologicalReconstruction.closingByReconstruction(fpLoG, holesRadius);

			if (medianRadius > 0)
				rf.rank(fpLoG, medianRadius, RankFilters.MEDIAN);

			//			if (holesRadius > 0)
			//				fpLoG = (FloatProcessor)MorphologicalReconstructionInteger.closingByReconstruction(fpLoG, holesRadius);

			// Subtract background first, if needed
			if (backgroundRadius > 0) {
				//				long startTime = System.currentTimeMillis();
				ipBackground = MorphologicalReconstruction.openingByReconstruction(fpLoG, backgroundRadius);
				//				long endTime = System.currentTimeMillis();
				//				System.out.println("Opening by reconstruction time: " + (endTime - startTime)/1000.);
				fpLoG.copyBits(ipBackground, 0, 0, Blitter.SUBTRACT);
				ipToMeasure = fpLoG.duplicate();
				//				new ImagePlus("Background", ipBackground.duplicate()).show();
			} else {
				ipToMeasure = fpH;
				ipBackground = null;
			}

			//--------NEW--------
			if (excludeDAB && fpDAB != null) {
				// If we are avoiding DAB, set pixels away from potential nuclei to zero
				fpDAB.setRoi(roi);
				ByteProcessor bpH = SimpleThresholding.greaterThanOrEqual(fpH, fpDAB);
				bpH.multiply(1.0/255.0);
				rf.rank(bpH, 2.5, RankFilters.MEDIAN);
				rf.rank(bpH, 2.5, RankFilters.MAX);
				fpLoG.copyBits(bpH, 0, 0, Blitter.MULTIPLY);
			}
			//			new ImagePlus("Log", fpLoG.duplicate()).show();
			//--------END_NEW--------

			// Apply (approximation of) Laplacian of Gaussian filter
			fpLoG.blurGaussian(sigma);
			fpLoG.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);

			// Threshold the main LoG image
			bpLoG = SimpleThresholding.thresholdAbove(fpLoG, 0f);
			// Need to set the threshold very slightly above zero for ImageJ
			// TODO: DECIDE ON USING MY WATERSHED OR IMAGEJ'S....
			fpLoG.setRoi(roi);
			//			long startTime = System.currentTimeMillis();
			ImageProcessor ipTemp = RegionalExtrema.findRegionalMaxima(fpLoG, 0.001f, false);
			//			long endTime = System.currentTimeMillis();

			//			System.out.println("Regional maxima: " + (endTime - startTime)/1000.0);
			ImageProcessor ipLabels = ROILabeling.labelImage(ipTemp, 0, false);
			ipLabels.resetMinAndMax();

			//			startTime = System.currentTimeMillis();
			Watershed.doWatershed(fpLoG, ipLabels, 0, false);
			//			endTime = System.currentTimeMillis();
			//			System.out.println("Watershed: " + (endTime - startTime)/1000.0);

			ipLabels.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			// TODO: Consider 4/8 connectivity for watershed nucleus ROIs
			rois = ROILabeling.getFilledPolygonROIs(ipLabels, Wand.FOUR_CONNECTED);			
			//			rois = ROILabeling.getFilledPolygonROIsExperimental(ipLabels);

			if (Thread.currentThread().isInterrupted())
				return;
			//			new ImagePlus("Maxima", ipTemp.duplicate()).show();
			//			new ImagePlus("Labels", ipLabels.duplicate()).show();
		} 



		//		System.out.println("ROIs: " + rois.size());


		if (bp == null)
			bp = new ByteProcessor(fpH.getWidth(), fpH.getHeight());			

		bp.setValue(255);
		for (Roi r : rois) {
			// Perform mean intensity check
			ipToMeasure.setRoi(r);
			double mean = ipToMeasure.getStatistics().mean;
			if (mean <= threshold) {
				//				IJ.log("Discarding mean: " + mean);
				continue;
			}
			// Perform background intensity check, if required
			if (ipBackground != null && !Double.isNaN(maxBackground)) {
				ipBackground.setRoi(r);
				if (ipBackground.getStatistics().mean > maxBackground)
					continue;
			}
			// Fill the ROI to keep it
			bp.fill(r);
		}
		//		new ImagePlus("Binary", bp.duplicate()).show();
		//		new ImagePlus("Measured", ipToMeasure.duplicate()).show();

		if (Thread.currentThread().isInterrupted())
			return;

		//		// Now create a new, updated binary image
		bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
		if (mergeAll) {
			// Grow the remaining (sufficiently-intense) regions & compute intersection with LoG-filtered
			// (which gives edge estimations)
			bp.filter(ImageProcessor.MAX);
			bp.copyBits(bpLoG, 0, 0, Blitter.AND);	
			//			if (IJ.debugMode)
			//				new ImagePlus("Binary during watershed", bp.duplicate()).show();
			if (watershedPostProcess) {
				// TODO: CHECK IF THIS IS A SENSIBLE APPROACH TO FILLING HOLES SELECTIVELY
				List<PolygonRoi> rois2 = ROILabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);
				bp.setValue(255);
				for (PolygonRoi r : rois2) {
					// Don't fill if the resulting area would be too much - do a (faster) bounding box area test first
					Rectangle bounds = r.getBounds();
					if (bounds.width * bounds.height > maxArea) {
						bp.setRoi(r);
						if (ImageStatistics.getStatistics(bp, Measurements.AREA, null).area > maxArea)
							continue;
					}
					bp.fill(r);
				}
				bp.resetRoi();
				new EDM().toWatershed(bp);
			}
		}
		// TODO: Look at the better boundary clearing implemented in Fast_nucleus_counts
		if (roi != null)
			ROILabeling.clearOutside(bp, roi);
		//			ROILabeling.clearBoundary(bp, roi, 0);

		bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
		// TODO: MAKE A DECISION ON WHICH TRACING ALGORITHM TO USE FOR WATERSHED DETECTION
		List<PolygonRoi> rois2 = ROILabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);
		//		List<PolygonRoi> rois2 = ROILabeling.getFilledPolygonROIsExperimental(bp);

		if (Thread.currentThread().isInterrupted())
			return;

		overlay = new Overlay();
		for (PolygonRoi r : rois2) {
			// Smooth boundaries with a 3-point moving averaging, while also downsampling to remove every second point
			if (smoothBoundaries) {
				FloatPolygon poly = r.getFloatPolygon();
				FloatPolygon poly2 = new FloatPolygon();
				int nPoints = poly.npoints;
				for (int i = 0; i < nPoints; i += 2) {
					int iMinus = (i + nPoints - 1) % nPoints;
					int iPlus = (i + 1) % nPoints;
					poly2.addPoint((poly.xpoints[iMinus] + poly.xpoints[iPlus] + poly.xpoints[i])/3, 
							(poly.ypoints[iMinus] + poly.ypoints[iPlus] + poly.ypoints[i])/3);
				}
				r = new PolygonRoi(poly2, PolygonRoi.POLYGON);
			}
			overlay.add(r);
		}
	}


	public Overlay getOverlay() {
		return overlay;
	}


	//	private Overlay doDetectionFromMembranes(FloatProcessor fpH, FloatProcessor fpDAB) {
	//		ByteProcessor bpH = SimpleThresholding.greaterThanOrEqual(fpH, fpDAB);
	//		bpH.multiply(1.0/255.0);
	//		new RankFilters().rank(bpH, 2.5, RankFilters.MEDIAN);
	//		new RankFilters().rank(bpH, 2.5, RankFilters.MAX);
	//		fpH.copyBits(bpH, 0, 0, Blitter.MULTIPLY);
	//		Overlay overlay = doDetection(fpH);
	//		Overlay overlay2 = new Overlay();
	//		for (Roi r : overlay.toArray()) {
	//			fpH.setRoi(r);
	//			double meanH = fpH.getStatistics().max;
	//			fpDAB.setRoi(r);
	//			double meanDAB = fpDAB.getStatistics().mean;
	//			if (meanH >= meanDAB)
	//				overlay2.add(r);
	//		}
	//		return overlay;
	//	}



	public void runDetection(double backgroundRadius, double holesRadius, double maxBackground, double medianRadius, double sigma, double threshold, double minArea, double maxArea, boolean mergeAll, boolean watershedPostProcess, boolean excludeDAB, boolean smoothBoundaries) {

		boolean updateROIs = rois == null || bpLoG == null;

		updateROIs = updateROIs ? updateROIs : this.holesRadius != holesRadius;
		this.holesRadius = holesRadius;

		updateROIs = updateROIs ? updateROIs : this.medianRadius != medianRadius;
		this.medianRadius = medianRadius;

		updateROIs = updateROIs ? updateROIs : this.backgroundRadius != backgroundRadius;
		this.backgroundRadius = backgroundRadius;

		updateROIs = updateROIs ? updateROIs : this.sigma != sigma;
		this.sigma = sigma;

		updateROIs = updateROIs ? updateROIs : this.excludeDAB != excludeDAB;
		this.excludeDAB = excludeDAB;

		boolean updateAnything = updateROIs || overlay == null;

		updateAnything = updateAnything ? updateAnything : this.maxBackground != maxBackground;
		this.maxBackground = maxBackground;

		updateAnything = updateAnything ? updateAnything : this.minArea != minArea;
		this.minArea = minArea;

		updateAnything = updateAnything ? updateAnything : this.maxArea != maxArea;
		this.maxArea = maxArea;

		updateAnything = updateAnything ? updateAnything : this.threshold != threshold;
		this.threshold = threshold;

		updateAnything = updateAnything ? updateAnything : this.mergeAll != mergeAll;
		this.mergeAll = mergeAll;

		updateAnything = updateAnything ? updateAnything : this.watershedPostProcess != watershedPostProcess;
		this.watershedPostProcess = watershedPostProcess;

		updateAnything = updateAnything ? updateAnything : this.smoothBoundaries != smoothBoundaries;
		this.smoothBoundaries = smoothBoundaries;

		if (!updateAnything)
			return;

		doDetection(updateROIs);

	}


}