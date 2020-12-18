package qupath.lib.analysis.heatmaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.plugin.filter.MaximumFinder;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.geom.Point2;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

public class SimpleProcessing {


	public static SimpleImage threshold(SimpleImage image, double threshold, float below, float equals, float above) {
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

	
	private static class PointWithValue {
		
		public final double x;
		public final double y;
		public final double value;
		
		public PointWithValue(double x, double y, double value) {
			this.x = x;
			this.y = y;
			this.value = value;
		}
		
		public double distance(SimpleProcessing.PointWithValue p) {
			double dx = x - p.x;
			double dy = y - p.y;
			return Math.sqrt(dx*dx + dy*dy);
		}
					
	}
	
	
	public static class MinMaxLoc {
		
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
	
	public static SimpleProcessing.MinMaxLoc findMinAndMax(float[] values) {
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
	
	public static SimpleProcessing.MinMaxLoc findMinAndMax(int[] values) {
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
	
	public static SimpleProcessing.MinMaxLoc findMinAndMax(double[] values) {
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
	public static class PeakFinder {
		
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
			
			List<SimpleProcessing.PointWithValue> points = new ArrayList<>();
			for (int i = 0; i < maxima.npoints; i++) {
				if (imgMask != null && imgMask.getValue(maxima.xpoints[i], maxima.ypoints[i]) == 0f)
					continue;
				
				double val = fp.getf(maxima.xpoints[i], maxima.ypoints[i]);
				double x = (maxima.xpoints[i]+0.5) * downsample + offsetX;
				double y = (maxima.ypoints[i]+0.5) * downsample + offsetY;
				
				if (roiMask == null || roiMask.contains(x, y))
					points.add(new PointWithValue(x, y, val));
			}
			points.sort(Comparator.comparingDouble((SimpleProcessing.PointWithValue p) -> p.value).reversed().thenComparingDouble(p -> p.y).thenComparingDouble(p -> p.x));
			
			var accepted = new ArrayList<SimpleProcessing.PointWithValue>();
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