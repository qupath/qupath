/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.imagej.superpixels;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ColorProcessor;
import ij.process.ColorSpaceConverter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * An implementation of SLIC superpixels, as described at http://ivrl.epfl.ch/research/superpixels
 * <p>
 * This largely follows the description at:
 * <blockquote>
 *   Radhakrishna Achanta, Appu Shaji, Kevin Smith, Aurelien Lucchi, Pascal Fua, and Sabine Süsstrunk <br>
 *   SLIC Superpixels Compared to State-of-the-art Superpixel Methods <br>
 *   IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 34, num. 11, p. 2274 - 2282, May 2012.
 * </blockquote>
 *   
 * It doesn't follow the code made available by the authors, and differs in some details. 
 * <p>
 * For example, the 'spacing' parameter is also used to determine the resolution at which the superpixel computation 
 * is performed, and a Gaussian filter is used to help reduce textures in advance.
 * It is also possible to use color deconvolved images, rather than transforming RGB to CIELAB.
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
	
	private static Logger logger = LoggerFactory.getLogger(SLICSuperpixelsPlugin.class);
	
	@Override
	public String getName() {
		return "SLIC superpixel plugin";
	}

	@Override
	public String getLastResultsDescription() {
		return null;
	}
	
	
	private static double getPreferredDownsample(final ImageData<BufferedImage> imageData, final ParameterList params) {
		
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
		boolean hasPixelSizeMicrons = cal.hasPixelSizeMicrons();
		double spacingPixels = hasPixelSizeMicrons ? params.getDoubleParameterValue("spacingMicrons") / cal.getAveragedPixelSizeMicrons() : params.getDoubleParameterValue("spacingPixels");
		
		// We aim to have about PREFERRED_PIXEL_SPACING spacing, so need to downsample the image accordingly
		double downsample = Math.max(1, Math.round(spacingPixels / PREFERRED_PIXEL_SPACING));
		return downsample;
	}
	

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
		if (cal.hasPixelSizeMicrons())
			return cal.getAveragedPixelSizeMicrons() * getPreferredDownsample(imageData, params);
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
				.addTitleParameter("Size parameters")
				.addDoubleParameter("sigmaPixels", "Gaussian sigma", 5, "px", "Adjust the Gaussian smoothing applied to the image, to reduce textures and give a smoother result")
				.addDoubleParameter("sigmaMicrons", "Gaussian sigma", 5, GeneralTools.micrometerSymbol(), "Adjust the Gaussian smoothing applied to the image, to reduce textures and give a smoother result")
				.addDoubleParameter("spacingPixels", "Superpixel spacing", 50, "px", "Control the (approximate) size of individual superpixels")
				.addDoubleParameter("spacingMicrons", "Superpixel spacing", 50, GeneralTools.micrometerSymbol(), "Control the (approximate) size of individual superpixels")
				.addTitleParameter("Algorithm parameters")
				.addIntParameter("maxIterations", "Number of iterations", 10, null, "Maximum number of iterations to use for superpixel generation")
				.addDoubleParameter("regularization", "Regularization", 0.25, null, "Control the 'squareness' of superpixels - higher values are more square")
				.addBooleanParameter("adaptRegularization", "Auto-adapt regularization", false, "Automatically adapt regularization parameter for different superpixels")
				.addBooleanParameter("useDeconvolved", "Use color deconvolved channels", false, "Use color-deconvolved values, rather than (standard) RGB->LAB colorspace transform")
//				.addBooleanParameter("doMerge", "Merge similar", false, "Merge neighboring superpixels if they are similar to one another")
				;
		
		boolean hasMicrons = imageData != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.getParameters().get("sigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("sigmaMicrons").setHidden(!hasMicrons);
		params.getParameters().get("spacingPixels").setHidden(hasMicrons);
		params.getParameters().get("spacingMicrons").setHidden(!hasMicrons);
		params.getParameters().get("useDeconvolved").setHidden(!(imageData.isBrightfield() && imageData.getColorDeconvolutionStains() != null && imageData.getServer().isRGB()));
		
		return params;
	}
	
	
	
	static class SLICSuperpixelDetector implements ObjectDetector<BufferedImage> {
		
		private PathImage<ImagePlus> pathImage = null;
		private ROI pathROI = null;
		
		private String lastResultSummary = null;

		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) throws IOException {
			
			// TODO: Give a sensible error
			if (pathROI == null) {
				lastResultSummary = "No ROI selected!";
				return null;
			}
			// Get a PathImage if we have a new ROI
			if (!pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				
				double downsample = getPreferredDownsample(imageData, params);
				
				// Create an expanded request (we will clip to the actual ROI later)
				var request = RegionRequest.createInstance(server.getPath(), downsample, pathROI)
						.pad2D((int)Math.ceil(downsample * 2), (int)Math.ceil(downsample * 2))
						.intersect2D(0, 0, server.getWidth(), server.getHeight());
				
				this.pathImage = IJTools.convertToImagePlus(server, request);
				this.pathROI = pathROI;
			}
			
			// Define maximum iterations
			int maxIterations = params.getIntParameterValue("maxIterations");
			double m = params.getDoubleParameterValue("regularization");
			boolean adaptRegularization = params.getBooleanParameterValue("adaptRegularization");
			boolean doDeconvolve = params.getBooleanParameterValue("useDeconvolved");
			double mergeThreshold = 0.05;
			
			// Get a float processor
			ImagePlus imp = pathImage.getImage();
			
			ImageProcessor[] ipColor;
			if (imp.getType() == ImagePlus.COLOR_RGB) {
				ColorProcessor cp = (ColorProcessor)imp.getProcessor();
				if (doDeconvolve && imageData.isBrightfield() && imageData.getColorDeconvolutionStains() != null) {
					ipColor = IJTools.colorDeconvolve(cp, imageData.getColorDeconvolutionStains());
//					fpDeconvolved = Arrays.copyOf(fpDeconvolved, 1);
//					for (ImageProcessor fp : fpDeconvolved)
//						System.err.println(fp.getStatistics().stdDev);
					m = m / 2;
					mergeThreshold = mergeThreshold / 2;
				} else {
					imp = new ColorSpaceConverter().RGBToLab(imp);
					ImageStack stack = imp.getStack();
					ipColor = new ImageProcessor[stack.getSize()];
					for (int i = 0; i < stack.getSize(); i++)
						ipColor[i] = stack.getProcessor(i+1).convertToFloatProcessor();
					// Rescale; original paper describes sensible values in range 1-40
					m = m * 40;
					mergeThreshold = mergeThreshold * 40;
				}
			} else {
				ImageStack stack = imp.getStack();
				ipColor = new ImageProcessor[stack.getSize()];
				for (int i = 0; i < stack.getSize(); i++)
					ipColor[i] = stack.getProcessor(i+1).convertToFloatProcessor();
				// Sensible fluorescence values are a bit harder to guess...
				double regularizationSuggestion = 0;
				for (ImageProcessor fp : ipColor)
					regularizationSuggestion += fp.getStatistics().stdDev;
				logger.info("Possible regularization value: {}", regularizationSuggestion/ipColor.length/100);
				// Scale by 100 for 'arbitary' fluorescence
				m = m * 100;
				mergeThreshold = mergeThreshold * 100;
			}
			
			double sigma = getSigma(pathImage, params);
			
			// Apply smoothing
			if (sigma > 0) {
				for (ImageProcessor fp : ipColor)
					fp.blurGaussian(sigma);
			}
			
			// Initialize centres and distances
			// TODO: Consider that the use of short here places a limit upon the maximum number of labels
			// (In practice should be ok because of enforced tiling for large regions?)
			int w = imp.getWidth();
			int h = imp.getHeight();
			short[] labels = new short[w*h];
			Arrays.fill(labels, (short)-1);
			double[] distances = new double[w*h];
			Arrays.fill(distances, Double.POSITIVE_INFINITY);
			
			int s = PREFERRED_PIXEL_SPACING;
			List<ClusterCenter> centers = new ArrayList<>();
			int widthClusters = 0; // Used to help figure out which clusters are side by side
			for (int y = s/2; y < h; y += s) {
				widthClusters = 0;
			    for (int x = s/2; x < w; x += s) {
			    	short label = (short)centers.size();
			    	ClusterCenter center = new ClusterCenter(ipColor, label, s, m, adaptRegularization, w, h);
			    	center.addLabel(y*w+x);
			        labels[y*w+x] = label;	
			        centers.add(center);
			        widthClusters++;
			    }
			}
			
			if (Thread.currentThread().isInterrupted())
				return Collections.emptyList();

			
			// Loop through and perform local k-means clustering
			for (int i = 0; i < maxIterations; i++) {
			    int centerLabel = 0;
				if (Thread.currentThread().isInterrupted())
					return Collections.emptyList();
			    for (ClusterCenter center : centers) {
			        center.updateFeatures();
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
			
			// Merge clusters if required
			if (params.containsKey("doMerge") && Boolean.TRUE.equals(params.getBooleanParameterValue("doMerge"))) {
				for (int i = 0; i < centers.size(); i++) {
					ClusterCenter center = centers.get(i);
					center.updateFeatures();
					
					int xc = i % widthClusters;
					// Check for horizontal link
					if (xc < widthClusters - 1) {
						ClusterCenter center2 = centers.get(i+1);
						maybeMergeClusters(center, center2, labels, mergeThreshold);
					}
					// Check for vertical link
					if (i < centers.size() - widthClusters) {
						ClusterCenter center2 = centers.get(i+widthClusters);
						maybeMergeClusters(center, center2, labels, mergeThreshold);
						
						// Check for diagonal forward link
						if (xc < widthClusters - 1) {
							center2 = centers.get(i+widthClusters+1);
							maybeMergeClusters(center, center2, labels, mergeThreshold);
						}
						
						// Check for backwards forward link
						if (xc > 0) {
							center2 = centers.get(i+widthClusters-1);
							maybeMergeClusters(center, center2, labels, mergeThreshold);
						}
					}
				}
			}
			
			
			// Enforce connectivity and merge small objects
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
			
			
			// Convert to tilez
			ShortProcessor ipLabels = new ShortProcessor(w, h, newLabels, null);
			List<PolygonRoi> polygons = RoiLabeling.labelsToFilledRoiList(ipLabels, true);
			List<PathObject> pathObjects;
			List<ROI> superpixelROIs = new ArrayList<>();
			try {
				for (Roi roi : polygons) {
					if (roi == null)
						continue;
					superpixelROIs.add(IJTools.convertToROI(roi, pathImage));
				}
				
				superpixelROIs = RoiTools.clipToROI(pathROI, superpixelROIs);
				
				pathObjects = superpixelROIs.stream()
						.map(r -> PathObjects.createTileObject(r))
						.collect(Collectors.toList());
			} catch (Exception e) {
				logger.error("Error created tiled ROIs", e);
				pathObjects = Collections.emptyList();
			}
			
			lastResultSummary = pathObjects.size() + " tiles created";
			return pathObjects;
		}
		
		
		
		static boolean maybeMergeClusters(final ClusterCenter center, final ClusterCenter center2, final short[] labels, final double mergeThreshold) {
			// Check merge based on euclidean distance
			double distanceThreshold = mergeThreshold;//0.02;
			double dist = 0;
			for (int i = 0; i < center.features.length; i++) {
				double f1 = center.features[i];
				double f2 = center2.features[i];
				dist += (f1-f2)*(f1-f2);
			}
			dist = Math.sqrt(dist);
//			System.err.println(dist);
			boolean doMerge = dist <= distanceThreshold;
						
						
//			// Check merge based on cosine distance
//			double distanceThreshold = 0.9999;
//			double dist = 0;
//			double mag1 = 0;
//			double mag2 = 0;
//			for (int i = 0; i < center.features.length; i++) {
//				double f1 = center.features[i];
//				mag1 += f1*f1;
//				double f2 = center2.features[i];
//				mag2 += f2*f2;				
//				dist += f1*f2;
//			}
//			dist = dist / (Math.sqrt(mag1) * Math.sqrt(mag2));
////			dist = 1-2*Math.acos(dist)/Math.PI;
//			boolean doMerge = dist >= distanceThreshold;
						
//			// Check merge based on distance standard deviations within each cluster
//			double distanceThreshold = Math.min(center.getColorDistanceStdDev(), center2.getColorDistanceStdDev());
//			double dist = center.colorDistance(center2.features);
//			boolean doMerge = dist < distanceThreshold;
			
			// Perform the merge
			if (doMerge) {
				for (int label : center2.getLabels())
					labels[label] = center.primaryLabel;
				center2.primaryLabel = center.primaryLabel;
//				center2.labels.addAll(center.labels);
				return true;
			}
			return false;
		}
		
		
		
		
		static double getSigma(final PathImage<?> pathImage, final ParameterList params) {
			double pixelSizeMicrons = pathImage.getPixelCalibration().getAveragedPixelSizeMicrons();
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
	
	
//	// This is somewhat complete... but fails to take sRGB into consideration
//	private static ImageProcessor[] convertToLAB(final ColorProcessor cp) {
//		
//		// Extract channels and FloatProcessors
//		// (These will be reused for the output)
//		FloatProcessor fpRed = cp.toFloat(0, null);
//		FloatProcessor fpGreen = cp.toFloat(1, null);
//		FloatProcessor fpBlue = cp.toFloat(2, null);
//
//		// Conversion values taken from http://docs.opencv.org/2.4/modules/imgproc/doc/miscellaneous_transformations.html
//
//		double epsilon = 0.008856;
//		for (int i = 0; i < cp.getWidth() * cp.getHeight(); i++) {
//			// Extract pixels
//			double r = fpRed.getf(i)   / 255.0;
//			double g = fpGreen.getf(i) / 255.0;
//			double b = fpBlue.getf(i)  / 255.0;
//			
//			// Convert to X, Y, Z
//			double X = r*0.412453 + g*0.357580 + b*0.180423;
//			double Y = r*0.212671 + g*0.715160 + b*0.072169;
//			double Z = r*0.019334 + g*0.119193 + b*0.950227;
//			
//			X /= 0.950456;
//			Z /= 1.088754;
//			
//			// Convert to LAB
//			double L = Y > epsilon ? 116*Math.cbrt(Y)-16 : 903.3*Y;
//		
//			double fx = X > epsilon ? Math.cbrt(X) : 7.787*X + 16.0/116.0;
//			double fy = Y > epsilon ? Math.cbrt(Y) : 7.787*Y + 16.0/116.0;
//			double fz = Z > epsilon ? Math.cbrt(Z) : 7.787*Z + 16.0/116.0;
//
//			double A = 500 * (fx - fy);
//			double B = 200 * (fy - fz);
//			
//			fpRed.setf(i, (float)L);
//			fpGreen.setf(i, (float)A);
//			fpBlue.setf(i, (float)B);
//		}
//		
//		return new ImageProcessor[]{fpRed, fpGreen, fpBlue};
//	}
	
	
	
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
		
		private ImageProcessor[] featuresImages;

	    private List<Integer> labels = new ArrayList<>();
	    private short primaryLabel;
	    private double[] features = null;
	    private double x;
	    private double y;
	    
	    private boolean adaptRegularization;
	    private double s, mSquared;
	    
	    private int width;
	    private int height;
	    
	    ClusterCenter(final ImageProcessor[] featuresImages, final short primaryLabel, final double s, final double m, final boolean adaptRegularization, final int width, final int height) {
	    	this.featuresImages = featuresImages;
	    	this.primaryLabel = primaryLabel;
	    	this.s = s;
	    	this.adaptRegularization = adaptRegularization;
	    	this.mSquared = m*m;
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
	        updateM();
	    }
	    
	    private void updateM() {
	    	if (!adaptRegularization)
	    		return;
	    	double maxDistanceSquared = 0;
	    	for (int label : labels) {
	    		double dist = colorDistanceSquared(label);
	    		if (dist > maxDistanceSquared)
	    			maxDistanceSquared = dist;
	    	}
	    	if (maxDistanceSquared > 0)
	    		this.mSquared = maxDistanceSquared;
	    }
	    
	    
//	    private double getColorDistanceStdDev() {
//	    	RunningStatistics stats = new RunningStatistics();
//	    	for (int label : getLabels()) {
//	    		stats.addValue(Math.sqrt(colorDistanceSquared(label)));
//	    	}
//	    	return stats.getStdDev();
//	    }
	    
	    
	    private double colorDistanceSquared(final int ind) {
	    	double DC2 = 0;
	        for (int i = 0; i < featuresImages.length; i++) {
	            double d = featuresImages[i].getf(ind) - features[i];
	            if (Double.isFinite(d))
	                DC2 += d*d;
	        }
	        return DC2;
	    }
	    
	    
	    public double colorDistance(final double[] features) {
	    	double distanceSquared = 0;
	    	for (int i = 0; i < features.length; i++) {
	    		double d = features[i] - this.features[i];
	    		distanceSquared += d*d;
	    	}
	    	return Math.sqrt(distanceSquared);
	    }
	    

	    public double distanceSquared(final int ind) {
	        if (features == null)
	            return Double.POSITIVE_INFINITY;

	        // Get coordinates from label
	        double xx = ind % width;
        	double yy = ind / width;
        	
	        // Calculate spatial distance squared
	        double dx = x - xx;
	        double dy = y - yy;

	        double DS2 = dx*dx + dy*dy;

	        // Calculate feature distance squared
	        double DC2 = colorDistanceSquared(ind);
	        
	        // Compute distance
	        double distanceSquared = DC2/mSquared + DS2/(s*s);
	        
	        return distanceSquared;
	    }

	}
	
	
}