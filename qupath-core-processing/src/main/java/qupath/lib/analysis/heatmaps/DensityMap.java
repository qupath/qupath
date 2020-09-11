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


package qupath.lib.analysis.heatmaps;

import java.awt.image.BufferedImage;
import java.util.function.DoubleFunction;
import java.util.function.Predicate;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.OpenCVTools;

/**
 * Class to represent density maps, used to visualize and annotate hotspots within images.
 * <p>
 * Note that density maps are always expected to be 'small' (i.e. fitting easily into RAM), since they can summarize 
 * the content of a large image. This makes them much easier to generate and use.
 * 
 * @author Pete Bankhead
 * 
 */
public interface DensityMap {
	
	/**
	 * Get the region to which the density map corresponds.
	 * @return
	 */
	public RegionRequest getRegion();
	
	/**
	 * Get the main density values. The interpretation of these varies according to the type of the map.
	 * @return
	 */
	public SimpleImage getValues();
	
	/**
	 * Get optional alpha values that can control how the densities are displayed.
	 * <p>
	 * For example, for a density map that gives the percentage of positive cells the alpha values could 
	 * provide the total cell counts - thereby enabling regions with low and high cell counts to be displayed 
	 * differently.
	 * 
	 * @return an alpha image if available, or else null
	 */
	public default SimpleImage getAlpha() {
		return null;
	}
	
	/**
	 * Get text corresponding to a pixel location, defined in the full image space.
	 * @param x
	 * @param y
	 * @return
	 */
	public default String getText(double x, double y) {
		return null;
	}
		
	
	static class HeatmapBuilder {
		
		private double downsample = Double.NaN;
		private double pixelSize = -1;
		private int maxSize = 1024;
		
		private double radius = 0;
		private boolean roundDownsample = false;
		
		private Predicate<PathObject> primaryObjects;
		private Predicate<PathObject> allObjects;
		
		private DoubleFunction<String> stringFun;
		
		
		public HeatmapBuilder pixelSize(double requestedPixelSize) {
			this.pixelSize = requestedPixelSize;
			return this;
		}
		
		public HeatmapBuilder maxSize(int maxSize) {
			this.maxSize = maxSize;
			return this;
		}
		
		public HeatmapBuilder downsample(double downsample) {
			this.downsample = downsample;
			return this;
		}
		
		public HeatmapBuilder radius(double radius) {
			this.radius = radius;
			return this;
		}
		
		public HeatmapBuilder density(Predicate<PathObject> primaryObjects) {
			this.primaryObjects = primaryObjects;
			return this;
		}
		
		public HeatmapBuilder density(PathClass pathClass) {
			return density(pathClass, false);
		}
		
		public HeatmapBuilder density(PathClass pathClass, boolean baseClass) {
			if (baseClass)
				this.primaryObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> getBaseClass(p.getPathClass()) == pathClass);
			else
				this.primaryObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> p.getPathClass() == pathClass);
			return this;
		}
		
		public HeatmapBuilder percentage(Predicate<PathObject> primaryObjects, Predicate<PathObject> allObjects) {
			this.primaryObjects = primaryObjects;
			this.allObjects = allObjects;
			stringFun = d -> GeneralTools.formatNumber(d, 1) + " %";
			return this;
		}
		
		
		public HeatmapBuilder positivePercentage() {
			this.allObjects = PathObjectFilter.DETECTIONS_ALL;
			this.primaryObjects = allObjects.and(p -> p.hasROI() && PathClassTools.isPositiveClass(p.getPathClass()));
			stringFun = d -> "Positive " + GeneralTools.formatNumber(d, 1) + " %";
			return this;
		}
		
		public HeatmapBuilder positivePercentage(PathClass baseClass) {
			this.allObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> p.hasROI() && getBaseClass(p.getPathClass()) == baseClass);
			this.primaryObjects = allObjects.and(p -> PathClassTools.isPositiveClass(p.getPathClass()));
			stringFun = d -> baseClass.toString() + " positive " + GeneralTools.formatNumber(d, 1) + " %";
			return this;
		}
		
		/**
		 * Set the string function, which can be used to convert a density value into a readable string representation.
		 * This should be called after any method that sets the density map type, e.g. {@link #positivePercentage()} to override the default.
		 * @param fun
		 * @return
		 */
		public HeatmapBuilder stringFunction(DoubleFunction<String> fun) {
			this.stringFun = fun;
			return this;
		}
		
		private static PathClass getBaseClass(PathClass pathClass) {
			return pathClass == null ? null : pathClass.getBaseClass();
		}
		
		public DensityMap build(ImageData<BufferedImage> imageData) {
			
			if (primaryObjects == null) {
				throw new IllegalArgumentException("Primary object filter is not defined - no densities to calculate!");
			}
			
			var server = imageData.getServer();
			
			double downsample = this.downsample;
			if (!Double.isFinite(downsample)) {
				if (pixelSize > 0) {
					downsample = pixelSize / server.getPixelCalibration().getAveragedPixelSize().doubleValue();
				} else {
					downsample = Math.max(server.getWidth(), server.getHeight()) / maxSize;
				}
			}
			if (roundDownsample)
				downsample = Math.round(downsample);
			
			int width = (int)Math.round(server.getWidth() / downsample);
			int height = (int)Math.round(server.getHeight() / downsample);
			
			int nChannels = allObjects == null ? 1 : 2;
			
			// Create density maps
			var mat = new Mat(height, width, opencv_core.CV_32FC(nChannels), Scalar.ZERO);
			var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
			try (FloatIndexer idx = mat.createIndexer()) {
				for (var pathObject : pathObjects) {
					if (primaryObjects.test(pathObject)) {
						int x = (int)(pathObject.getROI().getCentroidX() / downsample);
						int y = (int)(pathObject.getROI().getCentroidY() / downsample);
						idx.put(y, x, 0, idx.get(y, x, 0) + 1);
					}
					if (allObjects != null && allObjects.test(pathObject)) {
						int x = (int)(pathObject.getROI().getCentroidX() / downsample);
						int y = (int)(pathObject.getROI().getCentroidY() / downsample);
						idx.put(y, x, 1, idx.get(y, x, 1) + 1);
					}
				}
			}
			
			if (radius > 0) {
				double serverPixelSize = imageData.getServer().getMetadata().getPixelCalibration().getAveragedPixelSize().doubleValue();
				int kernelRadius = (int)Math.round(radius / (downsample * serverPixelSize));
				var kernel = new Mat(kernelRadius*2+1, kernelRadius*2+1, opencv_core.CV_32FC1, Scalar.ZERO);
				opencv_imgproc.circle(kernel, new Point(kernelRadius, kernelRadius), kernelRadius, Scalar.ONE, opencv_imgproc.FILLED, opencv_imgproc.FILLED, 0);
				// TODO: Note that I'm assuming the constant is 0... but can this be confirmed?!
//				opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, opencv_core.BORDER_REPLICATE);
				opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, opencv_core.BORDER_CONSTANT);
			}
			
			// Compute ratios (if required) & apply rounding
			// The rounding is needed because floating point errors can be introduced during the filtering 
			// (probably connected to FFT?) - and int filtering won't necessarily work for all kernel sizes
			boolean twoChannels = nChannels > 1;
			try (FloatIndexer idx = mat.createIndexer()) {
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						float primary = Math.round(idx.get(y, x, 0));
						idx.put(y, x, 0, primary);
						if (twoChannels) {
							float total = Math.round(idx.get(y, x, 1));
							idx.put(y, x, 0, primary/total*100f);
						}
					}						
				}
			}
			
			SimpleImage values = OpenCVTools.matToSimpleImage(mat, 0);
			SimpleImage alpha = nChannels > 1 ? OpenCVTools.matToSimpleImage(mat, 1) : null;
			
			mat.close();
			
			var region = RegionRequest.createInstance(imageData.getServer(), downsample);
			
			return new DefaultDensityMap(region, values, alpha, stringFun);
		}
		
	}
	
	
	static class DefaultDensityMap implements DensityMap {
		
		private SimpleImage values;
		private SimpleImage alpha;
		
		private RegionRequest region;
		private DoubleFunction<String> fun;
		
		private DefaultDensityMap(RegionRequest region, SimpleImage values, SimpleImage alpha, DoubleFunction<String> fun) {
			this.region = region;
			this.values = values;
			this.alpha = alpha;
			this.fun = fun;
		}

		@Override
		public SimpleImage getValues() {
			return values;
		}

		@Override
		public SimpleImage getAlpha() {
			return alpha;
		}
		
		@Override
		public String getText(double x, double y) {
			if (fun == null)
				return null;
			int xi = (int)Math.floor((x - region.getMinX()) / region.getDownsample());
			int yi = (int)Math.floor((y - region.getMinY()) / region.getDownsample());
			if (xi >= 0 && xi < values.getWidth() && yi >= 0 && yi <= values.getHeight()) {
				float val = values.getValue(xi, yi);
				return fun.apply(val);
			}
			return null;
		}

		@Override
		public RegionRequest getRegion() {
			return region;
		}
		
	}
	

}
