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
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
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
 * An implementation of SLICO superpixels, as described at http://ivrl.epfl.ch/research/superpixels
 * 
 * This largely follows the description at:
 *   Radhakrishna Achanta, Appu Shaji, Kevin Smith, Aurelien Lucchi, Pascal Fua, and Sabine Süsstrunk
 *   SLIC Superpixels Compared to State-of-the-art Superpixel Methods
 *   IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 34, num. 11, p. 2274 - 2282, May 2012.
 *   
 * It doesn't follow the code made available by the authors, and differs in some details. 
 * In particular, it currently uses color-deconvolved images (rather than CIELAB - although this may change).
 * 
 * Additionally, the 'spacing' parameter is also used to determine the resolution at which the superpixel computation 
 * is performed, and a Gaussian filter is used to help reduce textures in advance.
 * 
 * @author Pete Bankhead
 *
 */
public class SLICSuperpixelsPlugin extends AbstractTileableDetectionPlugin<BufferedImage> {

	/**
	 * The requested spacing is combined with the 'preferred' spacing to determine what 
	 * downsampling to apply to the image prior to computation of the superpixels.
	 */
	private static int PREFERRED_PIXEL_SPACING = 20;
	
	@Override
	public String getName() {
		return "SLIC superpixel creator";
	}

	@Override
	public String getLastResultsDescription() {
		return null;
	}
	
	
	private static double getPreferredDownsample(final ImageData<BufferedImage> imageData, final ParameterList params) {
		boolean hasPixelSizeMicrons = imageData.getServer().hasPixelSizeMicrons();
		double spacingPixels = hasPixelSizeMicrons ? params.getDoubleParameterValue("spacingMicrons") / imageData.getServer().getAveragedPixelSizeMicrons() : params.getDoubleParameterValue("spacingPixels");
		
		// We aim to have about PREFERRED_PIXEL_SPACING spacing, so need to downsample the image accordingly
		double downsample = Math.max(1, Math.round(spacingPixels / PREFERRED_PIXEL_SPACING));
		return downsample;
	}
	

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		if (imageData.getServer().hasPixelSizeMicrons())
			return imageData.getServer().getAveragedPixelSizeMicrons() * getPreferredDownsample(imageData, params);
		return getPreferredDownsample(imageData, params);
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(final ImageData<BufferedImage> imageData, final ParameterList params) {
		return new SLICSuperpixelDetector();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList()
				.addDoubleParameter("sigmaPixels", "Gaussian sigma", 5, "px", "Adjust the Gaussian smoothing applied to the image, to reduce textures and give a smoother result")
				.addDoubleParameter("sigmaMicrons", "Gaussian sigma", 5, GeneralTools.micrometerSymbol(), "Adjust the Gaussian smoothing applied to the image, to reduce textures and give a smoother result")
				.addDoubleParameter("spacingPixels", "Superpixel spacing", 50, "px", "Control the (approximate) size of individual superpixels")
				.addDoubleParameter("spacingMicrons", "Superpixel spacing", 50, GeneralTools.micrometerSymbol(), "Control the (approximate) size of individual superpixels")
//				addBooleanParameter("doMerge", "Merge similar", false, "Merge neighboring superpixels if they are similar to one another")
				;
		
		boolean hasMicrons = imageData != null && imageData.getServer().hasPixelSizeMicrons();
		params.getParameters().get("sigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("sigmaMicrons").setHidden(!hasMicrons);
		params.getParameters().get("spacingPixels").setHidden(hasMicrons);
		params.getParameters().get("spacingMicrons").setHidden(!hasMicrons);
		
		return params;
	}
	
	
	
	static class SLICSuperpixelDetector implements ObjectDetector<BufferedImage> {
		
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
				this.pathImage = PathImagePlus.createPathImage(server, pathROI, getPreferredDownsample(imageData, params));
				this.pathROI = pathROI;
			}
			
			// Get a float processor
			ImageProcessor ipOrig = this.pathImage.getImage().getProcessor();
			
			FloatProcessor[] fpDeconvolved = ColorDeconvolutionIJ.colorDeconvolve((ColorProcessor)ipOrig, imageData.getColorDeconvolutionStains());
			
//			fpDeconvolved = Arrays.copyOf(fpDeconvolved, 2);
			
			double m = 0.1;
			double sigma = getSigma(pathImage, params);
			
			for (FloatProcessor fp : fpDeconvolved)
				fp.blurGaussian(sigma);

			int w = ipOrig.getWidth();
			int h = ipOrig.getHeight();
			short[] labels = new short[w*h];
			Arrays.fill(labels, (short)-1);
			double[] distances = new double[w*h];
			Arrays.fill(distances, Double.POSITIVE_INFINITY);
			
			int s = PREFERRED_PIXEL_SPACING;
			List<ClusterCenter> centers = new ArrayList<>();
			for (int y = s/2; y < h; y += s) {
			    for (int x = s/2; x < w; x += s) {
			    	ClusterCenter center = new ClusterCenter(fpDeconvolved, s, m, w, h);
			    	center.addLabel(y*w+x);
			        labels[y*w+x] = (short)centers.size();
			        centers.add(center);
			    }
			}
			
			for (int i = 0; i < 20; i++) {
			    int centerLabel = 0;
			    for (ClusterCenter center : centers) {
			        center.updateFeatures();
//			        println("Center " + c + " of " + centers.size() + " - " + center.getObjects().size() + ", " + center.getNearbyClusters(grid).size())
			        for (int label : center.getNearbyClusters()) {
			            double distance = center.distanceSquared(label);
			            if (distance < distances[label]) {
			            	int currentLabel = labels[label];
			            	if (currentLabel == centerLabel)
			            		continue;
			            	if (currentLabel != (short)-1)
			            		centers.get(currentLabel).removeLabel(label);
			            	center.addLabel(label);
			            	distances[label] = distance;
			            	labels[label] = (short)centerLabel;
			            }
			        }
			        centerLabel++;
			    }
			}
			
			
			short[] newLabels = new short[labels.length];
			int[] xyCurrent = new int[w*h];
			short label = 1;
			int minSize = s*s/4;
			for (int y = 0; y < h; y++) {
				// Maintain a reference to the previous label
				short lastNewLabel = y > 0 ? labels[(y-1)*w] : 1;
				for (int x = 0; x < w; x++) {
					int i = y*w+x;
					// Check if we've already labelled this
					short currentNewLabel = newLabels[i];
					if (currentNewLabel != 0) {
						lastNewLabel = currentNewLabel;
						continue;
					}
					
					// Determine pixels for the current region
					short currentOldLabel = labels[i];
					int count = 1;
					xyCurrent[0] = i;
					newLabels[i] = label;
					int c = 0;
					while (c < count) {
						int ii = xyCurrent[c];
						int xx = ii % w;
						int yy = ii / w;
						c++;
						// Check 4-connected neighbors
						if (xx > 0) {
							int ind = ii-1;
							if (newLabels[ind] == 0 && labels[ind] == currentOldLabel) {
								xyCurrent[count] = ind;
								newLabels[ind] = label;
								count++;
							}
						}
						if (yy > 0) {
							int ind = ii-w;
							if (newLabels[ind] == 0 && labels[ind] == currentOldLabel) {
								xyCurrent[count] = ind;
								newLabels[ind] = label;
								count++;
							}
						}
						
						if (xx < w-1) {
							int ind = ii+1;
							if (newLabels[ind] == 0 && labels[ind] == currentOldLabel) {
								xyCurrent[count] = ind;
								newLabels[ind] = label;
								count++;
							}
						}
						
						if (yy < h-1) {
							int ind = ii+w;
							if (newLabels[ind] == 0 && labels[ind] == currentOldLabel) {
								xyCurrent[count] = ind;
								newLabels[ind] = label;
								count++;
							}
						}
					}
					// Check if small, relabelling if required
					if (count <= minSize) {
						for (c = 0; c < count; c++)
							newLabels[xyCurrent[c]] = lastNewLabel;
					} else {
						lastNewLabel = label;
						label++;
					}
				}
				
			}
			
			
			// Convert to ROIs
			ShortProcessor ipLabels = new ShortProcessor(w, h, newLabels, null);
//			new ImagePlus("Labels", ipLabels.duplicate()).show();
//			// Increment all labels (TODO: Consider setting numbers 'correctly' the first time)
//			ipLabels.add(1.0);
			
			// Remove everything outside the ROI, if required
			if (pathROI != null) {
				Roi roi = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
				ROILabeling.clearOutside(ipLabels, roi);
				// It's important to move away from the containing ROI, to help with brush selections ending up
				// having the correct parent (i.e. don't want to risk moving slightly outside the parent object's ROI)
				// (Or at least it previously seemed important... it has an unwanted effect when tiling though)
//				ipLabels.setValue(0);
//				ipLabels.setLineWidth(1);
//				ipLabels.draw(roi);
			}
			
			
			// Convert to tiles & create a labelled image for later
			List<PolygonRoi> polygons = ROILabeling.labelsToFilledRoiList(ipLabels, true);
//			PolygonRoi[] polygons = ROILabeling.labelsToFilledROIs(ipLabels, (int)ipLabels.getMax());
			List<PathObject> pathObjects = new ArrayList<>(polygons.size());
			label = 0;
//			// Set thresholds - regions means must be within specified range
//			double minThreshold = params.getDoubleParameterValue("minThreshold");
//			double maxThreshold = params.getDoubleParameterValue("maxThreshold");
//			if (!Double.isFinite(minThreshold))
//				minThreshold = Double.NEGATIVE_INFINITY;
//			if (!Double.isFinite(maxThreshold))
//				maxThreshold = Double.POSITIVE_INFINITY;
//			boolean hasThreshold = (minThreshold != maxThreshold) && (Double.isFinite(minThreshold) || Double.isFinite(maxThreshold));
			try {
				for (Roi roi : polygons) {
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
			
			
			lastResultSummary = pathObjects.size() + " tiles created";
			
			return pathObjects;
		}
		
		
		static double getSigma(final PathImage<?> pathImage, final ParameterList params) {
			double pixelSizeMicrons = .5 * (pathImage.getPixelWidthMicrons() + pathImage.getPixelHeightMicrons());
			if (Double.isNaN(pixelSizeMicrons)) {
				return params.getDoubleParameterValue("sigmaPixels") * pathImage.getDownsampleFactor();				
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
	}
	

	static class ClusterCenter {
		
		private FloatProcessor[] featuresImages;

	    private List<Integer> labels = new ArrayList<>();
	    private double[] features = null;
	    private double x;
	    private double y;
	    
	    private double s, m, mObserved;
	    
	    private int width;
	    private int height;
	    
	    ClusterCenter(final FloatProcessor[] featuresImages, final double s, final double m, final int width, final int height) {
	    	this.featuresImages = featuresImages;
	    	this.s = s;
	    	this.m = m;
	    	this.mObserved = m;
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
	        // Loop through labels again to get maxima color difference
        	double maxDistanceSquared = 0;
	        for (int label : labels) {
	        	double distanceSquared = 0;
	            for (int i = 0; i < features.length; i++) {
	                double dist = features[i] - featuresImages[i].getf(label);
	                distanceSquared += dist*dist;
	            }
	            if (distanceSquared > maxDistanceSquared)
	            	maxDistanceSquared = distanceSquared;
	        }
	        if (maxDistanceSquared == 0)
	        	mObserved = m;
	        else
	        	mObserved = Math.sqrt(maxDistanceSquared);
	        
//	        System.err.println(mObserved);
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
//	        double distanceSquared = DC2/(m*m) + DS2/(s*s);
	        double distanceSquared = DC2/(mObserved*mObserved) + DS2/(s*s);

	        // Compute distance
//	        double distanceSquared = DC2 + DS2/(s*s) * m*m;

	        return distanceSquared;
	    }

	}
	
	
}