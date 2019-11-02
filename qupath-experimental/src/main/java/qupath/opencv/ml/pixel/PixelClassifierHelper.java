package qupath.opencv.ml.pixel;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import qupath.lib.classifiers.Normalization;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.ml.BoundaryStrategy;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
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
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.FeaturePreprocessor;
import qupath.opencv.ml.pixel.features.FeatureCalculator;

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
import java.util.stream.Collectors;

/**
 * Helper class for training a pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierHelper implements PathObjectHierarchyListener {
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierHelper.class);
	
	private BoundaryStrategy boundaryStrategy = BoundaryStrategy.getSkipBoundaryStrategy();

	private PixelCalibration resolution = PixelCalibration.getDefaultInstance();
    private ImageData<BufferedImage> imageData;
    private FeatureCalculator<BufferedImage> featureCalculator;
    private FeatureImageServer featureServer;
    private boolean changes = true;

    private Mat matTraining;
    private Mat matTargets;
    
    private FeaturePreprocessor preprocessor;

    /**
     * Create a new pixel classifier helper, to support generating training data.
     * 
     * @param imageData
     * @param featureCalculator
     */
    public PixelClassifierHelper(ImageData<BufferedImage> imageData, FeatureCalculator<BufferedImage> featureCalculator) {
        setImageData(imageData);
        this.featureCalculator = featureCalculator;
    }
    
    public synchronized ImageServer<BufferedImage> getFeatureServer() {
    	if (featureServer == null) {
    		if (featureCalculator != null && imageData != null) {
    			if (featureCalculator.supportsImage(imageData)) {
	    			try {
	    				this.featureServer = new FeatureImageServer(imageData, featureCalculator, resolution);
	    			} catch (IOException e) {
	    				logger.error("Error initializing FeatureImageServer", e);
	    				this.featureServer = null;
	    			}
    			}
    		}
    	}
    	return featureServer;
    }
    
    public synchronized FeatureCalculator<BufferedImage> getFeatureCalculator() {
    	return featureCalculator;
    }
    
    public synchronized PixelCalibration getResolution() {
    	return resolution;
    }

    public synchronized void setResolution(PixelCalibration cal) {
    	if (Objects.equal(this.resolution, cal))
    		return;
    	this.resolution = cal;
    	this.featureServer = null;
    }

    public synchronized void setFeatureCalculator(FeatureCalculator<BufferedImage> featureCalculator) {
        if (Objects.equal(this.featureCalculator, featureCalculator))
            return;
        this.featureCalculator = featureCalculator;
        this.featureServer = null;
//        if (imageData != null) {
//        	try {
//                var temp = new FeatureImageServer(imageData, calculator, downsample);
//                var tiles = temp.getTileRequestManager().getAllTileRequests();
//                int i = 0;
//                for (var tile : tiles) {
//                	IJTools.convertToImagePlus(temp, tile.getRegionRequest()).getImage().show();;
//                	i++;
//                	if (i >= 5)
//                		break;
//                }
//                
//        	} catch (Exception e) {
//        		e.printStackTrace();
//        	}
//        }
        resetTrainingData();
    }

    public void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return;
        if (this.imageData != null) {
            this.imageData.getHierarchy().removePathObjectListener(this);
        }
        this.imageData = imageData;
        this.featureServer = null;
        resetCaches();
        if (this.imageData != null) {
            if (featureCalculator != null && !featureCalculator.supportsImage(imageData)) {
            	logger.warn("Feature calculator is not compatible with {}", imageData);
            }
            this.imageData.getHierarchy().addPathObjectListener(this);
        }
        changes = true;
    }

    
    public ImageData<BufferedImage> getImageData() {
    	return imageData;
    }
    

    private Map<Integer, PathClass> pathClassesLabels = new LinkedHashMap<>();
    
    public synchronized Map<Integer, PathClass> getPathClassLabels() {
    	return Collections.unmodifiableMap(pathClassesLabels);
    }
    

    public synchronized boolean updateTrainingData() throws IOException {
        if (imageData == null) {
            resetTrainingData();
            return false;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        
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
        pathClassesLabels.clear();
        int lab = 0;
        Map<PathClass, Integer> labels = new HashMap<>();
        for (PathClass pathClass : pathClasses) {
        	Integer temp = Integer.valueOf(lab);
        	pathClassesLabels.put(temp, pathClass);
        	labels.put(pathClass, temp);
        	lab++;
        }
                
        // Get features & targets for all the tiles that we need
        var tiles = featureServer.getTileRequestManager().getAllTileRequests();
        List<Mat> allFeatures = new ArrayList<>();
        List<Mat> allTargets = new ArrayList<>();
        for (var tile : tiles) {
            var tileFeatures = TileFeatures.getFeatures(tile.getRegionRequest(), featureServer, boundaryStrategy, labels);
        	if (tileFeatures != null) {
        		allFeatures.add(tileFeatures.getFeatures());
        		allTargets.add(tileFeatures.getTargets());
        	}
        }
        
//        PathClass boundaryClass = null;
//        if (boundaryStrategy == BoundaryStrategy.CLASSIFY_BOUNDARY)
//        	boundaryClass = PathClassFactory.getPathClass("Boundary", 0);
//        else if (boundaryStrategy == BoundaryStrategy.CLASSIFY_IGNORE) {
//        	boundaryClass = PathClassFactory.getPathClass(StandardPathClasses.IGNORE);        	
//        }
//        boolean trainBoundaries = boundaryClass != null;
//        
//        if (trainBoundaries) {
//	        List<ROI> boundaryROIs = new ArrayList<>();
//	        for (var entry : map.entrySet()) {
//	        	if (entry.getKey() == null || PathClassTools.isIgnoredClass(entry.getKey()))
//	        		continue;
//	        	for (ROI roi : entry.getValue()) {
//	        		if (roi.isArea())
//	        			boundaryROIs.add(roi);
//	        	}
//	        }
//	        if (!boundaryROIs.isEmpty()) {
//	        	if (map.containsKey(boundaryClass))
//	        		map.get(boundaryClass).addAll(boundaryROIs);
//	        	else
//	        		map.put(boundaryClass, boundaryROIs);
//	        }
//        }
        
        // We need at least two classes for anything very meaningful to happen
        int nTargets = labels.size();
        if (nTargets <= 1) {
        	logger.warn("Annotations for at least two classes are required to train a classifier");
            resetTrainingData();
            return false;
        }
         
        if (matTraining == null)
            matTraining = new Mat();
        if (matTargets == null)
            matTargets = new Mat();
        opencv_core.vconcat(new MatVector(allFeatures.toArray(Mat[]::new)), matTraining);
        opencv_core.vconcat(new MatVector(allTargets.toArray(Mat[]::new)), matTargets);

        
        this.preprocessor = new OpenCVClassifiers.FeaturePreprocessor.Builder()
        	.normalize(normalization)
        	.pca(pcaRetained, pcaNormalize)
        	.missingValue(missingValue)
        	.buildAndApply(matTraining);
        

        logger.info("Training data: {} x {}, Target data: {} x {}", matTraining.rows(), matTraining.cols(), matTargets.rows(), matTargets.cols());
        
        changes = false;
        return true;
    }
    
    /**
     * Feature normalization method
     */
    private Normalization normalization = Normalization.NONE;
    
    /**
     * Feature normalization method. Applied before any dimensionality reduction.
     * @param normalization
     * @see #setPCARetainedVariance(double)
     * @see #setPCANormalize(boolean)
     */
    public void setNormalization(Normalization normalization) {
    	if (this.normalization == normalization)
    		return;
    	this.normalization = normalization;
    	resetTrainingData();
    }
    
    /**
     * Get the current feature normalization method for preprocessing.
     * @return
     */
    public Normalization getNormalization() {
    	return normalization;
    }
    
    /**
     * Missing value for training data
     */
    private double missingValue = 0;
    
    /**
     * Retained variance for PCA
     */
    private double pcaRetained = 0;
    
    /**
     * Normalize features after dimensionality reduction using PCA
     */
    private boolean pcaNormalize = true;
       
    /**
     * Apply Principal Component Analysis for feature dimensionality reduction.
     * @param retained retained variance after dimensionality reduction; set &le; 0 if PCA should not be applied
     * @see #setNormalization(Normalization)
     */
    public void setPCARetainedVariance(double retained) {
    	if (pcaRetained == retained)
    		return;
    	pcaRetained = retained;
    	resetTrainingData();
    }
    
    /**
     * Get the retained variance after applying PCA for dimensionality reduction
     * @return
     * @see #setPCARetainedVariance(double)
     */
    public double getPCARetainedVariance() {
    	return pcaRetained;
    }
    
    /**
     * Request features to be normalized <i>after</i> dimensionality reduction using PCA.
     * @param normalize
     * @see #setPCARetainedVariance(double)
     */
    public void setPCANormalize(boolean normalize) {
    	if (pcaNormalize == normalize)
    		return;
    	pcaNormalize = normalize;
    	resetTrainingData();
    }

    /**
     * Query whether reduced features are normalized <i>after</i> PCA.
     * @return
     * @see #setPCANormalize(boolean)
     * @see #setPCARetainedVariance(double)
     */
    public boolean doPCANormalize() {
    	return pcaNormalize;
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
    
    
    public FeaturePreprocessor getLastFeaturePreprocessor() {
        return preprocessor;
    }
    
    
    private synchronized void resetTrainingData() {
        if (matTraining != null)
            matTraining.release();
        matTraining = null;
        if (matTargets != null)
            matTargets.release();
        resetCaches();
//        lastAnnotatedROIs = null;
        matTargets = null;
        changes = false;
    }
    
    private synchronized void resetCaches() {
//    	for (Mat matTemp : cacheAreaFeatures.values())
//        	matTemp.release();
//        for (Mat matTemp : cacheLineFeatures.values())
//        	matTemp.release();
//        cacheAreaFeatures.clear();
//        cacheLineFeatures.clear();
    }


    public TrainData getTrainData() throws IOException {
        if (changes)
            updateTrainingData();
        if (matTraining == null || matTargets == null)
            return null;
        return TrainData.create(matTraining, opencv_ml.ROW_SAMPLE, matTargets);
    }

    public List<ImageChannel> getChannels() {
        return pathClassesLabels == null ? Collections.emptyList() :
        	pathClassesLabels.values()
        			.stream()
        			.map(l -> {
        				String name = l.getName();
        				Integer color = l.getColor();
        				if (name.equals("Ignore*"))
        					color = null;
        				return ImageChannel.getInstance(name, color);
        			})
        			.collect(Collectors.toList());
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return;
        changes = true;
    }
    
    
    static class TileFeatures {
    	
    	private static Map<RegionRequest, TileFeatures> cache = Collections.synchronizedMap(new WeakHashMap<>());
    	    	
    	private Map<PathClass, Integer> labels;
    	private FeatureImageServer featureServer;
    	private RegionRequest request;
    	private Map<ROI, PathClass> rois;
    	private BoundaryStrategy strategy;
    	private Mat matFeatures;
    	private Mat matTargets;
    	
    	static TileFeatures getFeatures(RegionRequest request, FeatureImageServer featureServer, BoundaryStrategy strategy, Map<PathClass, Integer> labels) {
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
	    		features = new TileFeatures(request, featureServer, strategy, rois, labels);
	    		cache.put(request, features);
    		} catch (IOException e) {
    			cache.remove(request);
    			logger.error("Error requesting features for " + request, e);
    		}
    		
    		return features;
    	}
    	
    	private TileFeatures(RegionRequest request, FeatureImageServer featureServer, BoundaryStrategy strategy, Map<ROI, PathClass> rois, Map<PathClass, Integer> labels) throws IOException {
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
    	
//    	private void ensureFeaturesCalculated() throws IOException {
//    		if (matFeatures != null && matTargets != null)
//    			return;
//    		
//    		matFeatures = new Mat();
//    		matTargets = new Mat();
//    		
//    		var features = featureServer.readBufferedImage(request);
//    		
//    		var map = new HashMap<PathClass, BufferedImage>();
//    		
//    		// TODO: Handle differing boundary thicknesses
//    		double downsample = request.getDownsample();
//    		double boundaryThickness = 1.0;
//    		
//    		int width = features.getWidth();
//    		int height = features.getHeight();
//    		
//    		// Create labels one annotation at a time - we want to permit overlapping annotations for multiclass classification
//    		for (var entry : rois.entrySet()) {
//    			var roi = entry.getKey();
//    			var pathClass = entry.getValue();
//    			
//    			// Get (or create) as mask
//    			var imgMask = map.get(pathClass);
//    			if (imgMask == null) {
//    				imgMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
//    				map.put(pathClass, imgMask);
//    	    	}
//    			
//    			boolean isArea = roi.isArea();
//    			boolean isLine = roi.isLine();
//    			boolean trainBoundaries = false;
//    			
//    			if (roi.isPoint()) {
//    				for (var p : roi.getPolygonPoints()) {
//        				int x = (int)Math.round((p.getX() - request.getX()) / downsample);    					
//        				int y = (int)Math.round((p.getY() - request.getY()) / downsample); 
//        				if (x >= 0 && y >= 0 && x < width && y < height)
//        					imgMask.getRaster().setSample(x, y, 0, 255);
//    				}
//    			} else {
//	    			var g2d = imgMask.createGraphics();
//	    			g2d.scale(1.0/downsample, 1.0/downsample);
//	                g2d.translate(-request.getX(), -request.getY());
//	                g2d.setColor(Color.WHITE);
//	                
//	                var shape = entry.getKey().getShape();
//	                if (isArea) {
//	                	g2d.fill(shape);
//	                	// Do not train on boundaries if these should be classified some other way
//	                	if (trainBoundaries) {
//	                        g2d.setColor(Color.BLACK);
//	                    	g2d.setStroke(new BasicStroke((float)(downsample * boundaryThickness)));
//	                    	g2d.draw(shape);                        		
//	                	}
//	                } else if (isLine) {
//	                	g2d.setStroke(new BasicStroke((float)(downsample * boundaryThickness)));
//	                	g2d.draw(shape);
//	                }
//	        		g2d.dispose();
//    			}
//    		}
//    		
//    		// Allocate buffers
//    		int capacity = width * height * map.size();
//    		int nFeatures = features.getRaster().getNumBands();
//    		var extracted = FloatBuffer.allocate(capacity * nFeatures);    		
//    		var targets = IntBuffer.allocate(capacity);
//    		
//    		
//    	}
    	
    	public Mat getFeatures() {
    		return matFeatures;
    	}

    	public Mat getTargets() {
    		return matTargets;
    	}

    }
    

}
