package qupath.process.gui.ml;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import qupath.lib.color.ColorToolsAwt;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageDataServer;
import qupath.opencv.ops.ImageOps;

import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * Helper class for training a pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierTraining implements PathObjectHierarchyListener, AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierTraining.class);
	
	private BoundaryStrategy boundaryStrategy = BoundaryStrategy.getSkipBoundaryStrategy();

	private PixelCalibration resolution = PixelCalibration.getDefaultInstance();
    private ImageData<BufferedImage> imageData;
    private ImageDataOp featureCalculator;
    
    // TODO: Replace with an ImageDataOp only
	private ImageDataServer<BufferedImage> featureServer;
    private boolean changes = true;

    private Mat matTraining;
    private Mat matTargets;
    
    
    /**
     * Create a new pixel classifier helper, to support generating training data.
     * 
     * @param imageData
     * @param featureCalculator
     */
    public PixelClassifierTraining(ImageData<BufferedImage> imageData, ImageDataOp featureCalculator) {
        setImageData(imageData);
        this.featureCalculator = featureCalculator;
    }

    
    synchronized ImageDataServer<BufferedImage> getFeatureServer() {
    	if (featureServer == null) {
    		if (featureCalculator != null && imageData != null) {
    			if (featureCalculator.supportsImage(imageData)) {
	    			this.featureServer = ImageOps.buildServer(imageData, featureCalculator, resolution);
    			}
    		}
    	}
    	return featureServer;
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
    	if (Objects.equal(this.resolution, cal))
    		return;
    	this.resolution = cal;
    	this.featureServer = null;
    }

    /**
     * Set the {@link ImageDataOp} used to calculate features.
     * @param featureOp
     */
    public synchronized void setFeatureOp(ImageDataOp featureOp) {
        if (Objects.equal(this.featureCalculator, featureOp))
            return;
        this.featureCalculator = featureOp;
        this.featureServer = null;
        resetTrainingData();
    }

    /**
     * Set the current {@link ImageData} being used for interactive training.
     * This listens to changes in the object hierarchy, and therefore {@link #close()} should be 
     * called when this {@link PixelClassifierTraining} is no longer required to ensure that these 
     * are stopped appropriately.
     * @param imageData
     */
    public void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return;
        if (this.imageData != null) {
            this.imageData.getHierarchy().removePathObjectListener(this);
        }
        this.imageData = imageData;
        this.featureServer = null;
        if (this.imageData != null) {
            if (featureCalculator != null && !featureCalculator.supportsImage(imageData)) {
            	logger.warn("Feature calculator is not compatible with {}", imageData);
            }
            this.imageData.getHierarchy().addPathObjectListener(this);
        }
        changes = true;
    }

    /**
     * Get the current {@link ImageData} being used for interactive training.
     * @return
     */
    public ImageData<BufferedImage> getImageData() {
    	return imageData;
    }

    private synchronized ClassifierTrainingData updateTrainingData(Map<PathClass, Integer> labelMap) throws IOException {
        if (imageData == null) {
            resetTrainingData();
            return null;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        
        Map<PathClass, Integer> labels = new LinkedHashMap<>();
        if (labelMap == null) {
        	// Get labels for all annotations
            Collection<PathObject> annotations = hierarchy.getAnnotationObjects();
            Set<PathClass> pathClasses = new TreeSet<>((p1, p2) -> p1.toString().compareTo(p2.toString()));
            for (var annotation : annotations) {
            	if (isTrainableAnnotation(annotation)) {
            		var pathClass = annotation.getPathClass();
            		pathClasses.add(pathClass);
            		var boundaryClass = boundaryStrategy.getBoundaryClass(pathClass);
            		if (boundaryClass != null)
            			pathClasses.add(boundaryClass);
            	}
            }
            int lab = 0;
            for (PathClass pathClass : pathClasses) {
            	Integer temp = Integer.valueOf(lab);
            	labels.put(pathClass, temp);
            	lab++;
            }
        } else {
        	labels.putAll(labelMap);
        }
        
                
        // Get features & targets for all the tiles that we need
        var featureServer = getFeatureServer();
        var tiles = featureServer.getTileRequestManager().getAllTileRequests();
        List<Mat> allFeatures = new ArrayList<>();
        List<Mat> allTargets = new ArrayList<>();
        for (var tile : tiles) {
            var tileFeatures = getTileFeatures(tile.getRegionRequest(), featureServer, boundaryStrategy, labels);
        	if (tileFeatures != null) {
        		allFeatures.add(tileFeatures.getFeatures());
        		allTargets.add(tileFeatures.getTargets());
        	}
        }
        
        // We need at least two classes for anything very meaningful to happen
        int nTargets = labels.size();
        if (nTargets <= 1) {
        	logger.warn("Annotations for at least two classes are required to train a classifier");
            resetTrainingData();
            return null;
        }
         
        if (matTraining == null)
            matTraining = new Mat();
        if (matTargets == null)
            matTargets = new Mat();
        opencv_core.vconcat(new MatVector(allFeatures.toArray(Mat[]::new)), matTraining);
        opencv_core.vconcat(new MatVector(allTargets.toArray(Mat[]::new)), matTargets);

        logger.info("Training data: {} x {}, Target data: {} x {}", matTraining.rows(), matTraining.cols(), matTargets.rows(), matTargets.cols());
        
        changes = false;
        return new ClassifierTrainingData(labels, matTraining, matTargets);
    }
    
    

    
	private static PathClass REGION_CLASS = PathClassFactory.getPathClass(StandardPathClasses.REGION);

    /**
     * Test is a PathObject can be used as a classifier training annotation.
     * @param pathObject
     * @return
     */
    static boolean isTrainableAnnotation(PathObject pathObject) {
    	return pathObject != null &&
    			pathObject.hasROI() &&
    			!pathObject.getROI().isEmpty() &&
    			pathObject.isAnnotation() &&
    			!pathObject.isLocked() &&
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
    	resetTrainingData();
    }
    
    /**
     * Get the strategy for handling the boundaries of area annotations.
     * 
     * @return
     */    
    public BoundaryStrategy getBoundaryStrategy() {
    	return boundaryStrategy;
    }
    
    
    private synchronized void resetTrainingData() {
        if (matTraining != null)
            matTraining.release();
        matTraining = null;
        if (matTargets != null)
            matTargets.release();
        matTargets = null;
        changes = false;
    }

    
    /**
     * Wrapper for training data.
     */
    public static class ClassifierTrainingData {

    	private Mat matTraining;
    	private Mat matTargets;

    	private Map<PathClass, Integer> pathClassesLabels;

    	private ClassifierTrainingData(Map<PathClass, Integer> pathClassesLabels, Mat matTraining, Mat matTargets) {
    		this.pathClassesLabels = Collections.unmodifiableMap(new LinkedHashMap<>(pathClassesLabels));
    		this.matTraining = matTraining;
    		this.matTargets = matTargets;
    	}

    	/**
    	 * Get the map of classifications to labels.
    	 * @return
    	 */
    	public synchronized Map<PathClass, Integer> getLabelMap() {
    		return pathClassesLabels;
    	}

    	/**
    	 * Get training data.
    	 * @return
    	 */
    	public TrainData getTrainData() {
    		return TrainData.create(matTraining.clone(), opencv_ml.ROW_SAMPLE, matTargets.clone());
    	}

    }
    

    /**
     * Create training data, using a label map automatically generated from the available classifications.
     * @return
     * @throws IOException
     */
    public ClassifierTrainingData createTrainingData() throws IOException {
    	return createTrainingDataForLabelMap(null);
    }

    /**
     * Get a classifier training map, using a predefined label map (which determines which classifications to use).
     * @param labels
     * @return
     * @throws IOException
     */
    public ClassifierTrainingData createTrainingDataForLabelMap(Map<PathClass, Integer> labels) throws IOException {
        return updateTrainingData(labels);
    }


    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return;
        changes = true;
    }
    
    
	private static Map<RegionRequest, TileFeatures> cache = Collections.synchronizedMap(new WeakHashMap<>());
    
    private static TileFeatures getTileFeatures(RegionRequest request, ImageDataServer<BufferedImage> featureServer, BoundaryStrategy strategy, Map<PathClass, Integer> labels) {
		TileFeatures features = cache.get(request);
		Map<ROI, PathClass> rois = null;
		
		var annotations = featureServer.getImageData().getHierarchy().getObjectsForRegion(PathAnnotationObject.class, request, null);
		if (annotations != null && !annotations.isEmpty()) {
    		rois = new HashMap<>();
    		for (var annotation : annotations) {
    			// Don't train from locked annotations
    			if (!isTrainableAnnotation(annotation))
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
    		if (features != null)
				cache.remove(request);
			return null;
		}

		// Check if we can return cached features
		if (features != null) {
			if (features.featureServer.equals(featureServer) &&
					features.labels.equals(labels) &&
					features.strategy.equals(strategy) &&
					features.rois.equals(rois) &&
					features.request.equals(request))
				return features;
		}
		
		// Calculate new features
		try {
//			System.err.println("Calculating " + request);
    		features = new TileFeatures(request, featureServer, strategy, rois, labels);
    		cache.put(request, features);
		} catch (IOException e) {
			cache.remove(request);
			logger.error("Error requesting features for " + request, e);
		}
		
		return features;
	}
    
    
    private static class TileFeatures {
    	    	    	
    	private Map<PathClass, Integer> labels;
    	private ImageDataServer<BufferedImage> featureServer;
    	private RegionRequest request;
    	private Map<ROI, PathClass> rois;
    	private BoundaryStrategy strategy;
    	private Mat matFeatures;
    	private Mat matTargets;
    	
    	private TileFeatures(RegionRequest request, ImageDataServer<BufferedImage> featureServer, BoundaryStrategy strategy, Map<ROI, PathClass> rois, Map<PathClass, Integer> labels) throws IOException {
    		this.request = request;
    		this.strategy = strategy;
    		this.featureServer = featureServer;
    		this.rois = rois;
    		this.labels = labels;
    		ensureFeaturesCalculated();
    	};
    	
    	/**
    	 * Note that this implementation supports only one target per pixel, i.e. not multi-class classifications.
    	 * @throws IOException
    	 */
    	private void ensureFeaturesCalculated() throws IOException {
    		if (matFeatures != null && matTargets != null)
    			return;
    		
    		var features = featureServer.readBufferedImage(request);
    		
    		// TODO: Handle differing boundary thicknesses
    		double downsample = request.getDownsample();
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
    			
    			if (roi.isPoint()) {
    				for (var p : roi.getAllPoints()) {
        				int x = (int)Math.round((p.getX() - request.getX()) / downsample);    					
        				int y = (int)Math.round((p.getY() - request.getY()) / downsample); 
        				if (x >= 0 && y >= 0 && x < width && y < height)
        					raster.setSample(x, y, 0, lab);
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
    		var extracted = FloatBuffer.allocate(capacity * nFeatures);    		
    		var targets = IntBuffer.allocate(capacity);
    		var rasterFeatures = features.getRaster();
    		for (int y = 0; y < height; y++) {
        		for (int x = 0; x < width; x++) {
        			int label = raster.getSample(x, y, 0);
        			if (label != 0) {
        				buf = rasterFeatures.getPixel(x, y, buf);
        				extracted.put(buf);
        				targets.put(label-1);
        			}
        		}    			
    		}
    		
    		// Create Mats
    		int n = targets.position();
    		matFeatures = new Mat(n, nFeatures, opencv_core.CV_32FC1);
    		matTargets = new Mat(n, 1, opencv_core.CV_32SC1);

    		if (n == 0) {
    			logger.warn("I thought I'd have features but I don't! " + rois.size() + " - " + request);
    			return;
    		}

    		IntIndexer idxTargets = matTargets.createIndexer();
    		FloatIndexer idxFeatures = matFeatures.createIndexer();
    		int t = 0;
    		for (int i = 0; i < n; i++) {
    			for (int j = 0; j < nFeatures; j++) {
    				idxFeatures.put(t, extracted.get(t));
    				t++;
    			}
    			idxTargets.put(i, targets.get(i));
    		}
    		idxTargets.release();
    		idxFeatures.release();
    	}
    	
    	public Mat getFeatures() {
    		return matFeatures;
    	}

    	public Mat getTargets() {
    		return matTargets;
    	}

    }

    /**
     * Clean up when done.
     * In practice, this sets the {@link ImageData} to null, to ensure that listeners are no longer... well, listening.
     */
	@Override
	public void close() {
		setImageData(null);
	}
    

}
