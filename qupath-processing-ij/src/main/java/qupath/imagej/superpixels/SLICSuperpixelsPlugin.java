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

package qupath.imagej.superpixels;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.filter.MaximumFinder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.imagej.color.ColorDeconvolutionIJ;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.processing.ROILabeling;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * A simple superpixel-generating command based upon applying ImageJ's watershed transform to the
 * absolute values of a Difference-of-Gaussians filtered image.
 * 
 * This provides tile objects that generally correspond to regions containing reasonably similar 
 * intensities or textures, which might then be classified.
 * 
 * @author Pete Bankhead
 *
 */
public class SLICSuperpixelsPlugin extends AbstractTileableDetectionPlugin<BufferedImage> {

	@Override
	public String getName() {
		return "SLIC superpixel creator";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		double pixelSize = params.getDoubleParameterValue("downsampleFactor");
		if (imageData != null && imageData.getServer().hasPixelSizeMicrons())
			pixelSize *= imageData.getServer().getAveragedPixelSizeMicrons();
		return pixelSize;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(final ImageData<BufferedImage> imageData, final ParameterList params) {
		return new DoGSuperpixelDetector();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList().
				addDoubleParameter("downsampleFactor", "Downsample factor", 8, null, "Downsample factor, used to determine the resolution of the image being processed").
				addDoubleParameter("sigmaPixels", "Gaussian sigma", 10, "px", "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("sigmaMicrons", "Gaussian sigma", 10, GeneralTools.micrometerSymbol(), "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("minThreshold", "Minimum intensity threshold", 10, null, "Regions with average values below this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("maxThreshold", "Maximum intensity threshold", 230, null, "Regions with average values above this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("noiseThreshold", "Noise threshold", 1, null, "Local threshold used to determine the number of regions created")
				;
		
		boolean hasMicrons = imageData != null && imageData.getServer().hasPixelSizeMicrons();
		params.getParameters().get("sigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("sigmaMicrons").setHidden(!hasMicrons);
		
		return params;
	}
	
	
	
	static class DoGSuperpixelDetector implements ObjectDetector<BufferedImage> {
		
		private PathImage<ImagePlus> pathImage = null;
		private ROI pathROI = null;
		
		private String lastResultSummary = null;

		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) {
			
			// TODO: Give a sensible error
			if (pathROI == null) {
				lastResultSummary = "No ROI selected!";
				return null;
			}
			// Get a PathImage if we have a new ROI
			if (!pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				this.pathImage = PathImagePlus.createPathImage(server, pathROI, params.getDoubleParameterValue("downsampleFactor"));
				this.pathROI = pathROI;
			}
			
			// Get a float processor
			ImageProcessor ipOrig = this.pathImage.getImage().getProcessor();
			
			FloatProcessor[] fpDeconvolved = ColorDeconvolutionIJ.colorDeconvolve((ColorProcessor)ipOrig, imageData.getColorDeconvolutionStains());
			
			
			FloatProcessor fpH = fpDeconvolved[0];
			fpH.blurGaussian(2.5);
//			fpH.setThreshold(0.2, ImageProcessor.NO_THRESHOLD, ImageProcessor.NO_LUT_UPDATE);
			ByteProcessor bpPoints = new MaximumFinder().findMaxima(fpH, 0, 0.1, MaximumFinder.SINGLE_POINTS, false, false);
			
			
			FloatProcessor fpPoints = bpPoints.convertToFloatProcessor();
			fpPoints.multiply(1.0/255.0);
			fpPoints.blurGaussian(5);
			bpPoints = new MaximumFinder().findMaxima(fpPoints, 0, 0.001, MaximumFinder.SEGMENTED, false, false);
			bpPoints.setThreshold(128, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			ImageProcessor ipLabels = ROILabeling.labelImage(bpPoints, false);
			
//			new ImagePlus("Points", bpPoints).show();
//			
//			
//			double m = 0.25;
//			
////			ImageStack stack = ((ColorProcessor)ipOrig).getHSBStack();
////			for (int i = 0; i < 3; i++) {
////				fpDeconvolved[i] = stack.getProcessor(i+1).convertToFloatProcessor();
////			}
////			m = 100;
//
//			
//			
//			for (FloatProcessor fp : fpDeconvolved)
//				fp.blurGaussian(5);
//
////			new ImagePlus("De", fpDeconvolved[1]).show();
//			
//			int w = ipOrig.getWidth();
//			int h = ipOrig.getHeight();
//			short[] labels = new short[w*h];
//			Arrays.fill(labels, (short)-1);
//			double[] distances = new double[w*h];
//			Arrays.fill(distances, Double.POSITIVE_INFINITY);
//			
//			int s = 60;
//			List<ClusterCenter> centers = new ArrayList<>();
//			for (int y = s/2; y < h; y += s) {
//			    for (int x = s/2; x < w; x += s) {
//			    	ClusterCenter center = new ClusterCenter(fpDeconvolved, s, m, w, h);
//			    	center.addLabel(y*w+x);
//			        labels[y*w+x] = (short)centers.size();
//			        centers.add(center);
//			    }
//			}
//			
//			for (int i = 0; i < 20; i++) {
//			    System.out.println("Iteration " + (i + 1));
//			    int centerLabel = 0;
//			    for (ClusterCenter center : centers) {
//			        center.updateFeatures();
////			        println("Center " + c + " of " + centers.size() + " - " + center.getObjects().size() + ", " + center.getNearbyClusters(grid).size())
//			        for (int label : center.getNearbyClusters()) {
//			            double distance = center.distanceSquared(label);
//			            if (distance < distances[label]) {
//			            	int currentLabel = labels[label];
//			            	if (currentLabel == centerLabel)
//			            		continue;
//			            	if (currentLabel != (short)-1)
//			            		centers.get(currentLabel).removeLabel(label);
//			            	center.addLabel(label);
//			            	distances[label] = distance;
//			            	labels[label] = (short)centerLabel;
//			            }
//			        }
//			        centerLabel++;
//			    }
//			}
//			
//			// Convert to ROIs
//			ShortProcessor ipLabels = new ShortProcessor(w, h, labels, null);
//			new ImagePlus("Labels", ipLabels.duplicate()).show();
////			new RankFilters().rank(ipLabels, 1, RankFilters.MAX);
			
			// Remove everything outside the ROI, if required
			if (pathROI != null) {
				Roi roi = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
				ROILabeling.clearOutside(ipLabels, roi);
				// It's important to move away from the containing ROI, to help with brush selections ending up
				// having the correct parent (i.e. don't want to risk moving slightly outside the parent object's ROI)
				ipLabels.setValue(0);
				ipLabels.setLineWidth(2);
				ipLabels.draw(roi);
			}
			
			
			
			
			// Convert to tiles & create a labelled image for later
			PolygonRoi[] polygons = ROILabeling.labelsToFilledROIs(ipLabels, (int)ipLabels.getMax());
			List<PathObject> pathObjects = new ArrayList<>(polygons.length);
			int label = 0;
			// Set thresholds - regions means must be within specified range
			double minThreshold = params.getDoubleParameterValue("minThreshold");
			double maxThreshold = params.getDoubleParameterValue("maxThreshold");
			if (!Double.isFinite(minThreshold))
				minThreshold = Double.NEGATIVE_INFINITY;
			if (!Double.isFinite(maxThreshold))
				maxThreshold = Double.POSITIVE_INFINITY;
			boolean hasThreshold = (minThreshold != maxThreshold) && (Double.isFinite(minThreshold) || Double.isFinite(maxThreshold));
			try {
				for (PolygonRoi roi : polygons) {
					if (roi == null)
						continue;
//					if (hasThreshold) {
//						ipOrig.setRoi(roi);
//						double meanValue = ipOrig.getStatistics().mean;
//						if (meanValue < minThreshold || meanValue > maxThreshold)
//							continue;
//					}
					PathArea superpixelROI = (PathArea)ROIConverterIJ.convertToPathROI(roi, pathImage);
					if (pathROI == null)
						continue;
					PathObject tile = new PathTileObject(superpixelROI);
					pathObjects.add(tile);
					label++;
					ipLabels.setValue(label);
					ipLabels.fill(roi);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
//			fpOrig.resetRoi();

//			// Compute Haralick textures
////			ipLabels.resetMinAndMax();
////			new ImagePlus("Labels", ipLabels.duplicate()).show();
////			fpOrig.resetMinAndMax();
////			new ImagePlus("Orig", fpOrig.duplicate()).show();
//			if ("OD sum".equals(imageName)) {
//				HaralickFeaturesIJ.measureHaralick(fpOrig, ipLabels, pathObjects, 32, 0, 3, 1, imageName);
//				ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
//				if (stains != null) {
//					ColorProcessor cp = (ColorProcessor)ipOrig;
//					FloatProcessor[] fpDeconv = ColorDeconvolutionIJ.colorDeconvolve(cp, stains, false);
//					for (int i = 0; i < fpDeconv.length; i++) {
//						StainVector stain = stains.getStain(i+1);
//						if (stain.isResidual())
//							continue;
//						HaralickFeaturesIJ.measureHaralick(fpDeconv[i], ipLabels, pathObjects, 32, 0, 2.5, 1, stain.getName());
//					}
//				}
//			} else {
//				HaralickFeaturesIJ.measureHaralick(fpOrig, ipLabels, pathObjects, 32, Double.NaN, Double.NaN, 1, imageName);
//				if (ipOrig instanceof ColorProcessor) {
//					ColorProcessor cp = (ColorProcessor)ipOrig;
//					FloatProcessor fpChannel = cp.toFloat(0, null);
//					HaralickFeaturesIJ.measureHaralick(fpChannel, ipLabels, pathObjects, 32, Double.NaN, Double.NaN, 1, "Red");					
//					fpChannel = cp.toFloat(1, fpChannel);
//					HaralickFeaturesIJ.measureHaralick(fpChannel, ipLabels, pathObjects, 32, Double.NaN, Double.NaN, 1, "Green");					
//					fpChannel = cp.toFloat(2, fpChannel);
//					HaralickFeaturesIJ.measureHaralick(fpChannel, ipLabels, pathObjects, 32, Double.NaN, Double.NaN, 1, "Blue");					
//				}
//			}
			
			
			lastResultSummary = pathObjects.size() + " tiles created";
			
			return pathObjects;
		}
		
		
		static double getSigma(final PathImage<?> pathImage, final ParameterList params) {
			double pixelSizeMicrons = .5 * (pathImage.getPixelWidthMicrons() + pathImage.getPixelHeightMicrons());
			if (Double.isNaN(pixelSizeMicrons)) {
				return params.getDoubleParameterValue("sigmaPixels") * params.getDoubleParameterValue("downsampleFactor");				
			} else
				return params.getDoubleParameterValue("sigmaMicrons") / pixelSizeMicrons;
		}
		

		@Override
		public String getLastResultsDescription() {
			return lastResultSummary;
		}
		
	}
	
	
	
	@Override
	public String getDescription() {
		return "Partition image into tiled regions of irregular shapes, using intensity & boundary information";
	}
	
	
	@Override
	protected synchronized Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		Collection<? extends PathObject> parents = super.getParentObjects(runner);
		return parents;
		
		// Exploring the use of hidden objects...
//		PathObject pathObjectHidden = new PathTileObject();
//		for (PathObject parent : parents) {
//			pathObjectHidden.addPathObject(new PathTileObject(parent.getROI()));
//		}
//		imageData.getHierarchy().getRootObject().addPathObject(pathObjectHidden);
//		return pathObjectHidden.getPathObjectList();
	}
	

	static class ClusterCenter {
		
		private FloatProcessor[] featuresImages;

	    private List<Integer> labels = new ArrayList<>();
	    private double[] features = null;
	    private double x;
	    private double y;
	    
	    private double s, m;
	    
	    private int width;
	    private int height;
	    
	    ClusterCenter(final FloatProcessor[] featuresImages, final double s, final double m, final int width, final int height) {
	    	this.featuresImages = featuresImages;
	    	this.s = s;
	    	this.m = m;
	    	this.width = width;
	    	this.height = height;
	    }

	    public List<Integer> getLabels() {
	        return Collections.unmodifiableList(labels);
	    }

	    public void addLabel(final Integer label) {
	        this.labels.add(label);
	    }


	    public void removeLabel(final Integer label) {
	        this.labels.remove(label);
	    }


	    public List<Integer> getNearbyClusters() {
	        if (labels.isEmpty())
	            return Collections.emptyList();

	        List<Integer> list = new ArrayList<>();
	        for (int yy = (int)Math.max(0, y-s); yy < (int)Math.min(height, y+s); yy++) {
	            for (int xx = (int)Math.max(0, x-s); xx < (int)Math.min(width, x+s); xx++) {
	                list.add(yy*width + xx);
	            }
	        }
	        return list;
	    }


	    public void updateFeatures() {
	        if (labels.isEmpty()) {
	            x = Double.NaN;
	            y = Double.NaN;
	            features = null;
	        }
	        int n = labels.size();
	        x = 0;
	        y = 0;
	        features = new double[featuresImages.length];
	        for (int label : labels) {
	        	double xx = label % width;
	        	double yy = label / width;
	            x += xx/n;
	            y += yy/n;
	            for (int i = 0; i < features.length; i++)
	                features[i] += featuresImages[i].getf(label)/n;
	        }
	    }

	    public double distanceSquared(final int label) {
	        if (features == null)
	            return Double.POSITIVE_INFINITY;

	        // Get coordinates from label
	        double xx = label % width;
        	double yy = label / width;
        	
	        // Calculate spatial distance squared
	        double dx = x - xx;
	        double dy = y - yy;

	        double DS2 = dx*dx + dy*dy;

	        // Calculate feature distance squared
	        double DC2 = 0;
	        for (int i = 0; i < featuresImages.length; i++) {
	            double d = featuresImages[i].getf(label) - features[i];
	            if (Double.isFinite(d))
	                DC2 += d*d;
	        }

	        // Compute distance
	        double distanceSquared = DC2 + DS2/(s*s) * m*m;

	        return distanceSquared;
	    }

	}
	
	
}