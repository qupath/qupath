/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.opencv.ml.pixel.train;

import java.util.Comparator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.MemoryTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.progress.LogProgressMonitor;
import qupath.lib.progress.ProgressMonitor;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageDataServer;
import qupath.opencv.ops.ImageOps;

import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Helper class for training a pixel classifier.
 * @since v0.8.0
 */
public class PixelClassifierTraining {
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierTraining.class);

	private static final PathClass REGION_CLASS = PathClass.StandardPathClasses.REGION;

	private BoundaryStrategy boundaryStrategy = BoundaryStrategy.getSkipBoundaryStrategy();

	private PixelCalibration resolution = PixelCalibration.getDefaultInstance();
    private ImageDataOp featureCalculator;

//	private Predicate<PathObject> trainingObjectFilter

	private static final Cache<RegionRequest, TileFeatures> cache = Caffeine.newBuilder()
			.softValues()
			.weigher(PixelClassifierTraining::weighFeatures)
			.maximumWeight(MemoryTools.getMaxMemoryPercent(10.0).orElse(1024L * 1024L))
			.build();

	private static final ProgressMonitor DEFAULT_PROGRESS_MONITOR = LogProgressMonitor.debug(logger);


	private static int weighFeatures(RegionRequest regionRequest, TileFeatures tileFeatures) {
		var features = tileFeatures.getFeatures();
		if (features == null || features.isEmpty()) {
			return 1;
		}
		return Math.toIntExact(Math.max(1,
				Math.round((features.size() * features.getFirst().size() * 4) / 1024.0)
				));
	}

	/**
     * Create a new pixel classifier helper, to support generating training data.
     * 
     * @param featureCalculator
     */
    public PixelClassifierTraining(ImageDataOp featureCalculator) {
        this.featureCalculator = featureCalculator;
    }

    
    public synchronized ImageDataServer<BufferedImage> getFeatureServer(ImageData<BufferedImage> imageData) {
		if (featureCalculator != null && imageData != null) {
			if (featureCalculator.supportsImage(imageData)) {
    			return ImageOps.buildServer(imageData, featureCalculator, resolution);
			} else {
				logger.warn("Feature calculator does not support {}", imageData);
			}
		}
		return null;
    }
    
    /**
     * Get an {@link ImageDataOp} used for feature calculation.
     * @return
     */
    public synchronized ImageDataOp getFeatureOp() {
    	return featureCalculator;
    }
    
    /**
     * Get the resolution at which the training should occur.
     * @return
     */
    public synchronized PixelCalibration getResolution() {
    	return resolution;
    }

    /**
     * Set the resolution at which the training should occur.
     * @param cal
     */
    public synchronized void setResolution(PixelCalibration cal) {
    	if (Objects.equals(this.resolution, cal))
    		return;
    	this.resolution = cal;
    }

    /**
     * Set the {@link ImageDataOp} used to calculate features.
     * @param featureOp
     */
    public synchronized void setFeatureOp(ImageDataOp featureOp) {
        if (Objects.equals(this.featureCalculator, featureOp))
            return;
        this.featureCalculator = featureOp;
    }

    private synchronized List<ClassifierTrainingData> updateTrainingData(Map<PathClass, Integer> labelMap,
																		 Collection<ImageData<BufferedImage>> imageDataCollection,
																		 ProgressMonitor progressMonitor) throws InterruptedException, IOException {
        if (imageDataCollection.isEmpty()) {
            return List.of();
        }

		if (progressMonitor == null) {
			progressMonitor = ProgressMonitor.NULL;
		}

        Map<PathClass, Integer> labels = new LinkedHashMap<>();
        
        boolean hasLockedAnnotations = false;
        if (labelMap == null) {
			progressMonitor.updateProgress("Extracting labels", -1);
            Set<PathClass> pathClasses = new TreeSet<>(Comparator.comparing(PathClass::toString));
        	for (var imageData : imageDataCollection) {
	        	// Get labels for all annotations
	            Collection<PathObject> annotations = imageData.getHierarchy().getAnnotationObjects();
	            for (var annotation : annotations) {
	            	if (isTrainableAnnotation(annotation, true)) {
	            		var pathClass = annotation.getPathClass();
	            		pathClasses.add(pathClass);
	            		// We only use boundary classes for areas
	            		if (annotation.getROI().isArea()) {
		            		var boundaryClass = boundaryStrategy.getBoundaryClass(pathClass);
		            		if (boundaryClass != null)
		            			pathClasses.add(boundaryClass);
	            		}
	            	} else if (isTrainableAnnotation(annotation, false))
	            		hasLockedAnnotations = true;
	            }
        	}
            int lab = 0;
            for (PathClass pathClass : pathClasses) {
            	labels.put(pathClass, lab);
            	lab++;
            }
        } else {
        	labels.putAll(labelMap);
        }

		// We need at least two classes for anything very meaningful to happen
		int nTargets = labels.size();
		if (nTargets <= 1) {
			logger.warn("Unlocked annotations for at least two classes are required to train a classifier!");
			if (hasLockedAnnotations)
				logger.warn("Image contains annotations that *could* be used for training, except they are currently locked. Please unlock them if they should be used.");
			return List.of();
		}
        
        List<ClassifierTrainingData> allTraining = new ArrayList<>();

		String progressMessage = "Calculating features";
		double count = 0;
		progressMonitor.updateProgress(progressMessage, 0.0);

        for (var imageData : imageDataCollection) {
			if (Thread.interrupted()) {
				throw new InterruptedException("Interrupted during feature calculation");
			}
			count++;

			String imageName = imageData.getServer().getMetadata().getName();

			List<FeatureVector> allFeatures = new ArrayList<>();
			List<TargetVector> allTargets = new ArrayList<>();

			// Get features & targets for all the tiles that we need
	        var featureServer = getFeatureServer(imageData);
	        if (featureServer != null) {
		        var tiles = featureServer.getTileRequestManager().getAllTileRequests();
		        for (var tile : tiles) {
		            var tileFeatures = getTileFeatures(tile, featureServer, boundaryStrategy, labels);
		        	if (tileFeatures != null) {
		        		allFeatures.addAll(tileFeatures.getFeatures());
		        		allTargets.add(tileFeatures.getTargets());
		        	}
		        }
	        } else {
	        	logger.warn("Unable to generate features for {}", imageData);
	        }
			progressMonitor.updateProgress(progressMessage, count / imageDataCollection.size());

			if (allFeatures.isEmpty()) {
				logger.warn("No training data found for {} - if you have training annotations, check the features are compatible with the current image.", imageName);
				continue;
			}

			var targets = TargetVector.concat(allTargets);
			logger.debug("Training data: {} x {}, Target data: {} x {} ({})",
					allFeatures.size(), allFeatures.getFirst().size(), targets.size(), 1,
					imageName);

			allTraining.add(ClassifierTrainingData.create(imageData.getServer().getMetadata().getName(),
					allFeatures,
					targets,
					labels));
        }

		progressMonitor.updateProgress(progressMessage, 1.0);

		return allTraining;
    }

    /**
     * Test is a PathObject can be used as a classifier training annotation.
     * @param pathObject
     * @param checkLocked 
     * @return
     */
    private static boolean isTrainableAnnotation(PathObject pathObject, boolean checkLocked) {
    	return pathObject != null &&
    			pathObject.hasROI() &&
    			!pathObject.getROI().isEmpty() &&
    			pathObject.isAnnotation() &&
    			(!pathObject.isLocked() || !checkLocked) &&
    			pathObject.getPathClass() != null && 
    			pathObject.getPathClass() != REGION_CLASS;
    }
    
    
    /**
     * Set the strategy for handling the boundaries of area annotations.
     * 
     * @param strategy
     */
    public void setBoundaryStrategy(BoundaryStrategy strategy) {
    	if (this.boundaryStrategy == strategy)
    		return;
    	this.boundaryStrategy = strategy == null ? BoundaryStrategy.getSkipBoundaryStrategy() : strategy;
    }
    
    /**
     * Get the strategy for handling the boundaries of area annotations.
     * 
     * @return
     */    
    public BoundaryStrategy getBoundaryStrategy() {
    	return boundaryStrategy;
    }


	/**
     * Create training data, using a label map automatically generated from the available classifications.
	 * @param imageData image data to use for training
     * @return
     * @throws IOException
     */
    public ClassifierTrainingData createTrainingData(ImageData<BufferedImage> imageData) throws IOException, InterruptedException {
    	return createTrainingData(imageData, DEFAULT_PROGRESS_MONITOR);
    }

	/**
	 * Create training data, using a label map automatically generated from the available classifications.
	 * @param imageData image data to use for training
	 * @param progressMonitor progress monitor to notify of updates
	 * @return
	 * @throws IOException
	 */
	public ClassifierTrainingData createTrainingData(ImageData<BufferedImage> imageData, ProgressMonitor progressMonitor) throws IOException, InterruptedException {
		return createTrainingDataForLabelMap(Collections.singleton(imageData), null, progressMonitor).getFirst();
	}
    
    /**
     * Create training data, using a label map automatically generated from the available classifications.
	 * @param imageData collection of {@link ImageData} used for training
     * @return
     * @throws IOException
     */
    public List<ClassifierTrainingData> createTrainingData(Collection<ImageData<BufferedImage>> imageData) throws IOException, InterruptedException {
    	return createTrainingData(imageData, DEFAULT_PROGRESS_MONITOR);
    }

	/**
	 * Create training data, using a label map automatically generated from the available classifications.
	 * @param imageData collection of {@link ImageData} used for training
	 * @param progressMonitor progress monitor to notify of updates
	 * @return
	 * @throws IOException
	 */
	public List<ClassifierTrainingData> createTrainingData(Collection<ImageData<BufferedImage>> imageData, ProgressMonitor progressMonitor) throws IOException, InterruptedException {
		return createTrainingDataForLabelMap(imageData, null, progressMonitor);
	}

    /**
     * Get a classifier training map, using a predefined label map (which determines which classifications to use).
     * @param imageData collection of {@link ImageData} used for training
     * @param labels map linking classifications to labels in the output; may be null, in which case a label map will be generated from the data
     * @return a {@link ClassifierTrainingData} object representing training data
     * @throws IOException
     */
    public List<ClassifierTrainingData> createTrainingDataForLabelMap(Collection<ImageData<BufferedImage>> imageData, Map<PathClass, Integer> labels) throws IOException, InterruptedException {
        return createTrainingDataForLabelMap(imageData, labels, DEFAULT_PROGRESS_MONITOR);
    }

	/**
	 * Get a classifier training map, using a predefined label map (which determines which classifications to use).
	 * @param imageData collection of {@link ImageData} used for training
	 * @param labels map linking classifications to labels in the output; may be null, in which case a label map will be generated from the data
	 * @param progressMonitor progress monitor to notify of updates
	 * @return a {@link ClassifierTrainingData} object representing training data
	 * @throws IOException
	 */
	public List<ClassifierTrainingData> createTrainingDataForLabelMap(Collection<ImageData<BufferedImage>> imageData, Map<PathClass, Integer> labels, ProgressMonitor progressMonitor) throws InterruptedException, IOException {
		return updateTrainingData(labels, imageData, progressMonitor);
	}
    

    private static TileFeatures getTileFeatures(TileRequest tile, ImageDataServer<BufferedImage> featureServer, BoundaryStrategy strategy, Map<PathClass, Integer> labels) throws IOException {
		var request = tile.getRegionRequest();
		TileFeatures features = cache.getIfPresent(request);
		Map<ROI, PathClass> rois = null;
		
		var annotations = featureServer.getImageData().getHierarchy().getAllObjectsForRegion(request, null);
		if (annotations != null && !annotations.isEmpty()) {
    		rois = new HashMap<>();
    		for (var annotation : annotations) {
    			// Don't train from locked annotations
    			if (!isTrainableAnnotation(annotation, true))
    				continue;
    			
    			var roi = annotation.getROI();
    			// For points, make sure at least one point is in the region
    			if (roi != null && roi.isPoint()) {
        			boolean containsPoint = false;
    				for (var p : roi.getAllPoints()) {
    					if (request.contains((int)p.getX(), (int)p.getY(), roi.getZ(), roi.getT())) {
    						containsPoint = true;
    						break;
    					}
    				}
    				if (!containsPoint)
    					continue;
    			}
    			
    			var pathClass = annotation.getPathClass();
    			if (roi != null && labels.containsKey(pathClass)) {
    				rois.put(roi, pathClass);
    			}
    		}
		}
		
		// We don't have any features
		if (rois == null || rois.isEmpty()) {
    		if (features != null) {
				logger.debug("Removing features for {}", request);
				cache.invalidate(request);
			}
			return null;
		}

		// Check if we can return cached features
		if (features != null) {
			if (Objects.equals(features.featureServer.getPath(), featureServer.getPath()) &&
					features.labels.equals(labels) &&
					features.strategy.equals(strategy) &&
					features.rois.equals(rois) &&
					features.tile.equals(tile)) {
				logger.debug("Reusing features for {}", request);
				return features;
			}
		}
		logger.trace("Computing features for {}", request);

		// Calculate new features
		try {
    		features = new TileFeatures(tile, featureServer, strategy, rois, labels);
    		cache.put(request, features);
		} catch (IOException e) {
			cache.invalidate(request);
            logger.error("Error requesting features for {}", request, e);
			throw e;
		}
		
		return features;
	}
    
    
    private static class TileFeatures {
    	    	    	
    	private final Map<PathClass, Integer> labels;
    	private final ImageDataServer<BufferedImage> featureServer;
    	private final TileRequest tile;
    	private final Map<ROI, PathClass> rois;
    	private final BoundaryStrategy strategy;
    	private List<FeatureVector> features;
    	private TargetVector targets;
    	
    	private TileFeatures(TileRequest tile, ImageDataServer<BufferedImage> featureServer, BoundaryStrategy strategy, Map<ROI, PathClass> rois, Map<PathClass, Integer> labels) throws IOException {
    		this.tile = tile;
    		this.strategy = strategy;
    		this.featureServer = featureServer;
    		this.rois = rois;
    		this.labels = labels;
			calculateFeatures();
    	};
    	
    	/**
    	 * Note that this implementation supports only one target per pixel, i.e. not multi-class classifications.
    	 * @throws IOException
    	 */
    	private void calculateFeatures() throws IOException {

			// We don't change the features, so get the cached tile directly if possible.
			// This can substantially affect performance for large numbers of features,
			// because it avoids the need to make a defensive copy of the (possibly many) channels.
			BufferedImage features = featureServer.getCachedTile(tile);
			if (features == null) {
				long startTimeMillis = System.currentTimeMillis();
				features = featureServer.readRegion(tile.getRegionRequest());
				long endTimeMillis = System.currentTimeMillis();
				logger.debug("Calculated features in {} ms ({})", (endTimeMillis - startTimeMillis), tile);
			} else {
				logger.debug("Using cached features for {}", tile);
			}

    		double downsample = tile.getDownsample();
    		double boundaryThickness = strategy.getBoundaryThickness();
    		BasicStroke stroke = boundaryThickness > 0 ? new BasicStroke((float)(downsample * boundaryThickness)) : null;
    		BasicStroke singleStroke = new BasicStroke((float)downsample);
    		
    		int width = features.getWidth();
    		int height = features.getHeight();
    		var imgLabels = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    		var raster = imgLabels.getRaster();
    		
    		// Create labels one annotation at a time - we want to permit overlapping annotations for multiclass classification
    		for (var entry : rois.entrySet()) {
    			var roi = entry.getKey();
    			var pathClass = entry.getValue();
    			Integer label = labels.get(pathClass);
    			if (label == null)
    				continue;
    			// Need to add 1 because we want to ignore zeros as being unpainted
    			int lab = label.intValue() + 1;
    			
    			boolean isArea = roi.isArea();
    			boolean isLine = roi.isLine();
				var request = tile.getRegionRequest();
    			
    			if (roi.isPoint()) {
    				for (var p : roi.getAllPoints()) {
						// Change for v0.8: previously used Math.round, but this could cause out-of-bounds coordniates.
						// Also, it meant that a point at (0.5, 0.5) would move to (1, 1),
						// when it ought to be at (0, 0).
        				int x = (int)Math.floor((p.getX() - request.getX()) / downsample);
        				int y = (int)Math.floor((p.getY() - request.getY()) / downsample);
        				if (x >= 0 && y >= 0 && x < width && y < height) {
							raster.setSample(x, y, 0, lab);
						} else {
							logger.warn("Out of bounds {}, {}", x, y);
						}
    				}
    			} else {
	    			var g2d = imgLabels.createGraphics();
	    			g2d.scale(1.0/downsample, 1.0/downsample);
	                g2d.translate(-request.getX(), -request.getY());
	                g2d.setColor(ColorToolsAwt.getCachedColor(lab, lab, lab));
	                
	                var shape = entry.getKey().getShape();
	                if (isArea) {
	                	g2d.fill(shape);
	                	// Do not train on boundaries if these should be classified some other way
	                	var boundaryClass = strategy.getBoundaryClass(pathClass);
	                	Integer boundaryLabel = boundaryClass == null ? null : labels.get(boundaryClass);
	                	if (stroke != null && boundaryLabel != null) {
	                		int boundaryLab = boundaryLabel.intValue() + 1;
	                		g2d.setColor(ColorToolsAwt.getCachedColor(boundaryLab, boundaryLab, boundaryLab));
	                    	g2d.setStroke(stroke);
	                    	g2d.draw(shape);                        		
	                	}
	                } else if (isLine) {
	                	g2d.setStroke(stroke == null ? singleStroke : stroke);
	                	g2d.draw(shape);
	                }
	        		g2d.dispose();
    			}
    		}

    		// Allocate buffers
    		int capacity = width * height;
    		int nFeatures = features.getRaster().getNumBands();
    		float[] buf = new float[nFeatures];
    		var allFeatures = new ArrayList<FeatureVector>();
    		var targets = IntBuffer.allocate(capacity);
    		var rasterFeatures = features.getRaster();
    		for (int y = 0; y < height; y++) {
        		for (int x = 0; x < width; x++) {
        			int label = raster.getSample(x, y, 0);
        			if (label != 0) {
        				buf = rasterFeatures.getPixel(x, y, buf);
						allFeatures.add(FeatureVector.create(buf));
        				targets.put(label-1);
        			}
        		}    			
    		}
			this.features = List.copyOf(allFeatures);
			assert targets.position() == allFeatures.size();
			targets.rewind();
			int[] targetArray = new int[allFeatures.size()];
			targets.get(targetArray);
			this.targets = TargetVector.create(targetArray);
		}
    	
    	public List<FeatureVector> getFeatures() {
    		return features;
    	}

    	public TargetVector getTargets() {
    		return targets;
    	}

    }
    

}