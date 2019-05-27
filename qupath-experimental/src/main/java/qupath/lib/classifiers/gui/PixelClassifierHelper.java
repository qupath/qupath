package qupath.lib.classifiers.gui;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.FeaturePreprocessor;
import qupath.lib.classifiers.opencv.OpenCVClassifiers;
import qupath.lib.classifiers.opencv.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.DefaultROIComparator;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
	
	/**
	 * Strategy for handling the boundary pixels for area annotations.
	 * <p>
	 * This can be to do nothing, assign them to the 'ignore' class, or assign them 
	 * to a specific 'boundary' class.
	 * <p>
	 * The purpose is to facilitate learning separations between densely-packed objects.
	 */
	public static enum BoundaryStrategy {NONE, CLASSIFY_IGNORE, CLASSIFY_BOUNDARY};
	
	private BoundaryStrategy boundaryStrategy = BoundaryStrategy.NONE;
	private double boundaryThickness = 1.0;

    private ImageData<BufferedImage> imageData;
    private OpenCVFeatureCalculator calculator;
    private boolean changes = true;

    private List<ImageChannel> channels;
    private double downsample;
    
    private Mat matTraining;
    private Mat matTargets;
    
    private FeaturePreprocessor preprocessor;

    private Map<ROI, Mat> cacheAreaFeatures = new WeakHashMap<>();
    private Map<ROI, Mat> cacheLineFeatures = new WeakHashMap<>();

    /**
     * Create a new pixel classifier helper, to support generating training data.
     * 
     * @param imageData
     * @param calculator
     * @param downsample
     */
    public PixelClassifierHelper(ImageData<BufferedImage> imageData, OpenCVFeatureCalculator calculator, 
    		double downsample) {
        setImageData(imageData);
        this.calculator = calculator;
        this.downsample = downsample;
    }

    public void setFeatureCalculator(OpenCVFeatureCalculator calculator) {
        if (this.calculator == calculator)
            return;
        this.calculator = calculator;
        resetTrainingData();
    }

    public void setDownsample(double downsample) {
        if (this.downsample == downsample)
            return;
        this.downsample = downsample;
        resetTrainingData();
    }

    public OpenCVFeatureCalculator getFeatureCalculator() {
        return calculator;
    }

    public void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return;
        if (this.imageData != null) {
            this.imageData.getHierarchy().removePathObjectListener(this);
        }
        this.imageData = imageData;
        resetCaches();
        if (this.imageData != null) {
            this.imageData.getHierarchy().addPathObjectListener(this);
        }
        changes = true;
    }

    
    public ImageData<BufferedImage> getImageData() {
    	return imageData;
    }
    

    private static Map<PathClass, Collection<ROI>> getAnnotatedROIs(PathObjectHierarchy hierarchy) {
        List<PathObject> annotations = hierarchy.getAnnotationObjects().stream().filter((it) -> {
            return !it.isLocked() && it.getPathClass() != null && it.getPathClass() != PathClassFactory.getPathClass(StandardPathClasses.REGION) && it.hasROI();
        }).collect(Collectors.toList());

        Map<PathClass, Collection<ROI>> map = new TreeMap<>();
        for (PathObject it : annotations) {
            PathClass pathClass = it.getPathClass();
            if (map.containsKey(pathClass))
                map.get(pathClass).add(it.getROI());
            else {
            	// TODO: Check if this needs to be a set at all
//            	Set<ROI> list = new LinkedHashSet<>();
            	Set<ROI> list = new TreeSet<ROI>(DefaultROIComparator.getInstance());
            	list.add(it.getROI());
                map.put(pathClass, list);
            }
        }
        return map;
    }


    private Map<PathClass, Collection<ROI>> lastAnnotatedROIs;

    private Map<Integer, PathClass> pathClassesLabels = new LinkedHashMap<>();
    
    public synchronized Map<Integer, PathClass> getPathClassLabels() {
    	return Collections.unmodifiableMap(pathClassesLabels);
    }
    
    
//    private static Map<String, List<RegionRequest>> regionMap = Collections.synchronizedMap(new LinkedHashMap<>());
//    
//    static List<ImageRegion> getAllRegions(ImageServer<?> server) {
//    	var list = regionMap.get(server.getPath());
//    	
//    	double downsample = 1.0;
//    	int tileWidth = 256;
//    	int tileHeight = 256;
//    	int w = (int)(server.getWidth() / downsample);
//    	int h = (int)(server.getHeight() / downsample);
//    	
//    	if (list == null) {
//    		list = new ArrayList<>();
//    		for (int t = 0; t < server.nTimepoints(); t++) {
//    			for (int z = 0; z < server.nZSlices(); z++) {
//    	    		for (int y = 0; y < h; y += tileHeight) {
//    	            	for (int x = 0; x < w; x += tileWidth) {
//    	            		tempObjects.clear();
//    	            		hierarchy.getObjectsForRegion(PathAnnotationObject.class,
//    	            				ImageRegion.createInstance(x, y, width, height, z, t),
//    	            				tempObjects);
//    	            		
//    	            	}
//    	        	}    				
//    			}
//    		}
//    	}
//    }
//    
//    private static void getTrainingData(ImageData<BufferedImage> imageData) {
//    	
//    	var server = imageData.getServer();
//    	var hierarchy = imageData.getHierarchy();
//    	var map = getAnnotatedROIs(hierarchy);
//    	
//    	var annotations = hierarchy.getObjects(null,  PathAnnotationObject.class);
//    	
//    	int w = (int)(server.getWidth() / downsample);
//    	int h = (int)(server.getHeight() / downsample);
//    	
//    	var tempObjects = new ArrayList<PathObject>();
//    	for (int y = 0; y < h; y += tileHeight) {
//        	for (int x = 0; x < w; x += tileWidth) {
//        		tempObjects.clear();
//        		hierarchy.getObjectsForRegion(PathAnnotationObject.class,
//        				ImageRegion.createInstance(x, y, width, height, z, t),
//        				tempObjects);
//        		
//        	}    		
//    	}
//    	
//    	for (int x = 0; x < )
//    	
//    }
    
    

    public synchronized boolean updateTrainingData() {
        if (imageData == null) {
            resetTrainingData();
            return false;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        
        Set<PathClass> backgroundClasses = new HashSet<>(
        		Arrays.asList(
        				PathClassFactory.getPathClass(StandardPathClasses.IGNORE)				
        				)
        		);

        Map<PathClass, Collection<ROI>> map = getAnnotatedROIs(hierarchy);
        
        PathClass boundaryClass = null;
        if (boundaryStrategy == BoundaryStrategy.CLASSIFY_BOUNDARY)
        	boundaryClass = PathClassFactory.getPathClass("Boundary", 0);
        else if (boundaryStrategy == BoundaryStrategy.CLASSIFY_IGNORE) {
        	boundaryClass = PathClassFactory.getPathClass(StandardPathClasses.IGNORE);        	
        }
        boolean trainBoundaries = boundaryClass != null;
        
        if (trainBoundaries) {
	        List<ROI> boundaryROIs = new ArrayList<>();
	        for (var entry : map.entrySet()) {
	        	if (entry.getKey() == null || backgroundClasses.contains(entry.getKey()))
	        		continue;
	        	for (ROI roi : entry.getValue()) {
	        		if (roi.isArea())
	        			boundaryROIs.add(roi.duplicate());
	        	}
	        }
	        if (!boundaryROIs.isEmpty()) {
	        	if (map.containsKey(boundaryClass))
	        		map.get(boundaryClass).addAll(boundaryROIs);
	        	else
	        		map.put(boundaryClass, boundaryROIs);
	        }
        }
        
        
        
        // We need at least two classes for anything very meaningful to happen
        int nTargets = map.size();
        if (nTargets <= 1) {
            resetTrainingData();
            return false;
        }

        // Training is the same - so nothing else to do unless the varType changed
        if (map.equals(lastAnnotatedROIs)) {
       		return true;
        }

        // Get the current image
        ImageServer<BufferedImage> server = imageData.getServer();
         
        List<PathClass> pathClasses = new ArrayList<>(map.keySet());
        pathClassesLabels.clear();
        
        List<ImageChannel> newChannels = new ArrayList<>();
        List<Mat> allFeatures = new ArrayList<>();
        List<Mat> allTargets = new ArrayList<>();
        int label = 0;
        for (PathClass pathClass : pathClasses) {
            // Create a suitable channel
        	// For background classes, make the color (mostly?) transparent
        	Integer color = pathClass.getColor();
        	if (backgroundClasses.contains(pathClass.getBaseClass()))
        		color = null;
//        	if (backgroundClasses.contains(pathClass.getBaseClass()))
//        		color = ColorTools.makeRGBA(ColorTools.red(color),
//        				ColorTools.green(color),
//        				ColorTools.blue(color),
//        				32);
        	
            ImageChannel channel = ImageChannel.getInstance(
                    pathClass.getName(), color);
            
            newChannels.add(channel);
            pathClassesLabels.put(label, pathClass);
            // Loop through the object & get masks
            for (ROI roi : map.get(pathClass)) {
            	boolean isArea = roi instanceof PathArea;
            	boolean isLine = roi instanceof PathLine;
            	if (!isArea && !isLine) {
            		logger.warn("{} is neither an instance of PathArea nor PathLine! Will be skipped...", roi);
            		continue;
            	}
            	if (pathClass == boundaryClass) {
            		isLine = true;
            		isArea = false;
            	}
            	
            	// Check if we've cached features
                // Here, we use the ROI regardless of classification - because we can quickly create a classification matrix
                Mat matFeatures = isLine ? cacheLineFeatures.get(roi) : cacheAreaFeatures.get(roi);
                if (matFeatures == null) {
                	                	
                    Shape shape = RoiTools.getShape(roi);
                    
                    List<RegionRequest> requests = new ArrayList<>();
                    int tw = (int)Math.round(calculator.getMetadata().getInputWidth() * downsample);
                    int th = (int)Math.round(calculator.getMetadata().getInputHeight() * downsample);
                    for (int y = (int)roi.getBoundsY(); y < (int)Math.ceil(roi.getBoundsY() + roi.getBoundsHeight()); y += th) {
                        for (int x = (int)roi.getBoundsX(); x < (int)Math.ceil(roi.getBoundsX() + roi.getBoundsWidth()); x += tw) {
                        	requests.add(RegionRequest.createInstance(
                        			server.getPath(), downsample, x, y, tw, th, roi.getZ(), roi.getT()));
                        }                    	
                    }
                    
//                    List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(
//                			server, shape, downsample, roi.getZ(), roi.getT(),
//                			calculator.getMetadata().getInputWidth(),
//                			calculator.getMetadata().getInputHeight(), null);

                    matFeatures = new Mat();
                    List<Mat> rows = new ArrayList<>();
                    for (RegionRequest request : requests) {
                        // Get features & reshape so that each row has features for specific pixel
                        Mat matFeaturesFull;
						try {
							// TODO: FIX THE DOWNSAMPLE - IT IS LIKELY TO BE WRONG!
							matFeaturesFull = calculator.calculateFeatures(server, request);
						} catch (IOException e) {
							logger.warn("Unable to calculate features for " + request + " - will be skipped", e);
							continue;
						}

                        // Create a mask based on the output size after feature classification
                        // Note that the feature classification can incorporate additional resampling (e.g. with max pooling steps)
                        int resultWidth = matFeaturesFull.cols();
                        int resultHeight = matFeaturesFull.rows();
                        BufferedImage imgMask = new BufferedImage(resultWidth, resultHeight, BufferedImage.TYPE_BYTE_GRAY);
                        double downsampleMask = 0.5 * ((double)request.getWidth() / resultWidth) + 0.5 * ((double)request.getHeight() / resultHeight);
                        Graphics2D g2d = imgMask.createGraphics();
                        g2d.scale(1.0/downsampleMask, 1.0/downsampleMask);
                        g2d.translate(-request.getX(), -request.getY());
                        g2d.setColor(Color.WHITE);
                        if (isArea) {
                        	g2d.fill(shape);
                        	if (trainBoundaries) {
                                g2d.setColor(Color.BLACK);
                            	g2d.setStroke(new BasicStroke((float)(downsampleMask * boundaryThickness)));
                            	g2d.draw(shape);                        		
                        	}
                        } else if (isLine) {
                        	g2d.setStroke(new BasicStroke((float)(downsampleMask * boundaryThickness)));
                        	g2d.draw(shape);
                        }
                        g2d.dispose();
                        
//                        if (pathClass == boundaryClass) {
//                            try {
//								new ImagePlus("Orig " + request.toString(), server.readBufferedImage(request)).show();
//							} catch (IOException e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//                            new ImagePlus("Mask " + request.toString(), imgMask).show();
//                        }

//                        Mat matImage = OpenCVTools.imageToMat(img);
//                        matImage.convertTo(matImage, opencv_core.CV_32F);
                        Mat matMask = OpenCVTools.imageToMat(imgMask);
                        
                        int heightFeatures = matFeaturesFull.rows();
                        int widthFeatures = matFeaturesFull.cols();
                        if (heightFeatures != matMask.rows() || widthFeatures != matMask.cols()) {
                            opencv_imgproc.resize(matMask, matMask, new Size(widthFeatures, heightFeatures));
                        }
                        // Reshape mask to a column matrix
                        matMask.put(matMask.reshape(1, matMask.rows()*matMask.cols()));
//                        System.err.println('SIZE: ' + widthFeatures + ' x ' + heightFeatures)
//                        matFeaturesFull.convertTo(matFeaturesFull, opencv_core.CV_32F)
                        matFeaturesFull.put(matFeaturesFull.reshape(1, matMask.rows()*matMask.cols()));
                        // Extract the pixels
                        UByteIndexer indexerMask = matMask.createIndexer();
                        for (int r = 0; r < indexerMask.rows(); r++) {
                            if (indexerMask.get(r) == 0)
                                continue;
                            rows.add(matFeaturesFull.row(r));
                        }
                        indexerMask.release();
                    }
                    opencv_core.vconcat(new MatVector(rows.toArray(new Mat[0])), matFeatures);
                    
                    
                    if (isLine)
                    	cacheLineFeatures.put(roi, matFeatures);
                    else if (isArea)
                    	cacheAreaFeatures.put(roi, matFeatures);
                }
                if (matFeatures != null && !matFeatures.empty()) {
                    allFeatures.add(matFeatures.clone()); // Clone to be careful... not sure if normalization could impact this under adverse conditions
                    Mat targets = new Mat(matFeatures.rows(), 1, opencv_core.CV_32SC1, Scalar.all(label));
                    allTargets.add(targets);
                }
            }
            label++;
        }
        if (matTraining == null)
            matTraining = new Mat();
        if (matTargets == null)
            matTargets = new Mat();
        opencv_core.vconcat(new MatVector(allFeatures.toArray(Mat[]::new)), matTraining);
        opencv_core.vconcat(new MatVector(allTargets.toArray(Mat[]::new)), matTargets);

        
//        opencv_core.patchNaNs(matTraining, 0.0);
        
        this.preprocessor = new OpenCVClassifiers.FeaturePreprocessor.Builder()
        	.normalize(Normalization.MEAN_VARIANCE)
//        	.pca(0.99, true)
        	.missingValue(0)
        	.buildAndApply(matTraining);
        

        logger.info("Training data: {} x {}, Target data: {} x {}", matTraining.rows(), matTraining.cols(), matTargets.rows(), matTargets.cols());
        
        if (channels == null)
            channels = new ArrayList<>();
        else
            channels.clear();
        channels.addAll(newChannels);
        
        lastAnnotatedROIs = Collections.unmodifiableMap(map);
        changes = false;
        return true;
    }
    
    /**
     * Set the strategy for handling the boundaries of area annotations.
     * 
     * @param strategy
     */
    public void setBoundaryStrategy(BoundaryStrategy strategy) {
    	if (this.boundaryStrategy == strategy)
    		return;
    	this.boundaryStrategy = strategy;
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
    
    /**
     * Get the thickness of annotation boundaries, whenever {@code getBoundaryStrategy() != BoundaryStrategy.NONE}.
     * 
     * @return
     */
    public double getBoundaryThickness() {
    	return boundaryThickness;
    }
    
    /**
     * Set the thickness of annotation boundaries, whenever {@code getBoundaryStrategy() != BoundaryStrategy.NONE}.
     * <p>
     * This is defined in pixel units at the classification resolution level (i.e. after any downsampling has been applied).
     * 
     * @param boundaryThickness
     */
    public void setBoundaryThickness(double boundaryThickness) {
    	if (this.boundaryThickness == boundaryThickness)
    		return;
    	this.boundaryThickness = boundaryThickness;
    	resetTrainingData();
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
        lastAnnotatedROIs = null;
        matTargets = null;
        changes = false;
    }
    
    private synchronized void resetCaches() {
    	for (Mat matTemp : cacheAreaFeatures.values())
        	matTemp.release();
        for (Mat matTemp : cacheLineFeatures.values())
        	matTemp.release();
        cacheAreaFeatures.clear();
        cacheLineFeatures.clear();
    }


    public TrainData getTrainData() {
        if (changes)
            updateTrainingData();
        if (matTraining == null || matTargets == null)
            return null;
        
//        // Calculate weights
//        Mat matWeights = null;
//        int[] counts = new int[channels.size()];
//        try (IntIndexer indexer = matTargets.createIndexer()) {
//	        for (long i = 0; i < indexer.size(0); i++) {
//	        	counts[indexer.get(i)]++;
//	        }
//	        int minCount = Integer.MAX_VALUE;
//	        for (int c : counts) {
//	        	if (c > 0)
//	        		minCount = Math.min(c, minCount);
//	        }
//	        
//	        float[] weights = new float[counts.length];
//	        for (int i = 0; i < counts.length; i++) {
//	        	int c = counts[i];
//	        	if (c == 0)
//	        		weights[i] = 0f;
//	        	else
//	        		weights[i] = minCount / (float)c;
//	        }
//	        
//	        matWeights = new Mat(matTargets.size(), opencv_core.CV_32FC1);
//	        try (FloatIndexer idxWeights = matWeights.createIndexer()) {
//		        for (long i = 0; i < indexer.size(0); i++) {
//		        	idxWeights.put(i, weights[indexer.get(i)]);
//		        }
//	        }
//        } catch (Exception e) {
//        	logger.error("Error calculating weights", e);
//        }
//        
//        return TrainData.create(matTraining, opencv_ml.ROW_SAMPLE, matTargets, null, null, matWeights, null);
        
        return TrainData.create(matTraining, opencv_ml.ROW_SAMPLE, matTargets);
    }

    public List<ImageChannel> getChannels() {
        return new ArrayList<>(channels);
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return;
        changes = true;
    }

}
