package qupath.lib.classifiers.gui;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_ml;
import org.bytedeco.javacpp.opencv_ml.TrainData;
import org.bytedeco.javacpp.indexer.UByteIndexer;

import qupath.lib.classifiers.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.stream.Collectors;


class PixelClassifierHelper implements PathObjectHierarchyListener {

    private ImageData<BufferedImage> imageData;
    private OpenCVFeatureCalculator calculator;
    private boolean changes = true;

    private List<PixelClassifierOutputChannel> channels;
    private double requestedPixelSizeMicrons;
    
    private int modelType = opencv_ml.VAR_NUMERICAL;

    private Mat matTraining;
    private Mat matTargets;
    private double[] means;
    private double[] scales;

    private Map<ROI, Mat> cacheFeatures = new WeakHashMap<>();

    /**
     * Create a new pixel classifier helper, to support generating training data.
     * 
     * @param imageData
     * @param calculator
     * @param requestedPixelSizeMicrons
     * @param varType  opencv_ml.VAR_CATEGORICAL or opencv_ml.VAR_NUMERICAL
     */
    PixelClassifierHelper(ImageData<BufferedImage> imageData, OpenCVFeatureCalculator calculator, 
    		double requestedPixelSizeMicrons, int varType) {
        setImageData(imageData);
        this.calculator = calculator;
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons;
        setVarType(varType);
    }

    public double getRequestedPixelSizeMicrons() {
        return requestedPixelSizeMicrons;
    }
    
    /**
     * Set the var type, which indicates how the training data should be created.
     * 
     * @param newVarType
     */
    public void setVarType(final int newVarType) {
    	if (newVarType == modelType)
    		return;
    	if (newVarType == opencv_ml.VAR_CATEGORICAL || newVarType == opencv_ml.VAR_NUMERICAL) {
	    	modelType = newVarType;
	    	changes = true;
    	} else
    		throw new IllegalArgumentException("Unsupported varType!  Must be opencv_ml.VAR_CATEGORICAL or opencv_ml.VAR_NUMERICAL");
    }

    public void setFeatureCalculator(OpenCVFeatureCalculator calculator) {
        if (this.calculator == calculator)
            return;
        this.calculator = calculator;
        resetTrainingData();
    }

    public void setRequestedPixelSizeMicrons(double requestedPixelSizeMicrons) {
        if (this.requestedPixelSizeMicrons == requestedPixelSizeMicrons)
            return;
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons;
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
        for (Mat temp : cacheFeatures.values())
        	temp.release();
        cacheFeatures.clear();
        if (this.imageData != null) {
            this.imageData.getHierarchy().addPathObjectListener(this);
        }
        changes = true;
    }


    public static Map<PathClass, Collection<ROI>> getAnnotatedROIs(PathObjectHierarchy hierarchy) {
        List<PathObject> annotations = hierarchy.getObjects(null, PathAnnotationObject.class).stream().filter((it) -> {
            return it.getPathClass() != null && it.getPathClass() != PathClassFactory.getRegionClass() && it.hasROI();
        }).collect(Collectors.toList());

        Map<PathClass, Collection<ROI>> map = new TreeMap<>();
        for (PathObject it : annotations) {
            PathClass pathClass = it.getPathClass();
            if (map.containsKey(pathClass))
                map.get(pathClass).add(it.getROI());
            else {
            	// TODO: Check if this needs to be a set at all
            	Set<ROI> list = new LinkedHashSet<>();
            	list.add(it.getROI());
                map.put(pathClass, list);
            }
        }
        return map;
    }


    private Map<PathClass, Collection<ROI>> lastAnnotatedROIs;


    public Map<PathClass, Collection<ROI>> getLastTrainingROIs() {
        return lastAnnotatedROIs;
    }


    public boolean updateTrainingData() {
        if (imageData == null) {
            resetTrainingData();
            return false;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();

        Map<PathClass, Collection<ROI>> map = getAnnotatedROIs(hierarchy);

        // We need at least two classes for anything very meaningful to happen
        int nTargets = map.size();
        if (nTargets <= 1) {
            resetTrainingData();
            return false;
        }

        // Training is the same - so nothing else to do unless the varType changed
        if (map.equals(lastAnnotatedROIs)) {
        	if ((modelType == opencv_ml.VAR_CATEGORICAL && matTargets != null && matTargets.cols() == 1) ||
        			(modelType == opencv_ml.VAR_NUMERICAL && matTargets != null && matTargets.cols() != 1))
        		return true;
        }

        // Get the current image
        ImageServer<BufferedImage> server = imageData.getServer();
        double downsample = requestedPixelSizeMicrons / server.getAveragedPixelSizeMicrons();

        double padding = calculator.requestedPadding() * downsample;

        List<PathClass> pathClasses = new ArrayList<>(map.keySet());
        List<PixelClassifierOutputChannel> newChannels = new ArrayList<>();
        String path = imageData.getServerPath();
        List<Mat> allFeatures = new ArrayList<>();
        List<Mat> allTargets = new ArrayList<>();
        int label = 0;
        Set<PathClass> backgroundClasses = new HashSet<>(
        		Arrays.asList(
        				PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.WHITESPACE),
        				PathClassFactory.getPathClass("Background")        				
        				)
        		);
        for (PathClass pathClass : pathClasses) {
            // Create a suitable channel
            Integer color = backgroundClasses.contains(pathClass) ?
            		PixelClassifierOutputChannel.TRANSPARENT : pathClass.getColor();
            PixelClassifierOutputChannel channel = new PixelClassifierOutputChannel(
                    pathClass.getName(), color);
            newChannels.add(channel);
            // Loop through the object & get masks
            for (ROI roi : map.get(pathClass)) {
                // Check if we've cached features
                // Here, we use the ROI regardless of classification - because we can quickly create a classification matrix
                Mat matFeatures = cacheFeatures.get(roi);
                if (matFeatures == null) {
                	RegionRequest request = RegionRequest.createInstance(path, downsample,
                        (int)(roi.getBoundsX()-padding),
                        (int)(roi.getBoundsY()-padding),
                  		(int)(roi.getBoundsWidth()+padding*2),
          				(int)(roi.getBoundsHeight()+padding*2));
                    Shape shape = PathROIToolsAwt.getShape(roi);
                    BufferedImage img = server.readBufferedImage(request);
                    BufferedImage imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                    Graphics2D g2d = imgMask.createGraphics();
                    g2d.scale(1.0/downsample, 1.0/downsample);
                    g2d.translate(-request.getX(), -request.getY());
                    g2d.setColor(Color.WHITE);
                    g2d.fill(shape);
                    g2d.dispose();

                    Mat matImage = OpenCVTools.imageToMat(img);
                    matImage.convertTo(matImage, opencv_core.CV_32F);
                    Mat matMask = OpenCVTools.imageToMat(imgMask);
                    // Get features & reshape so that each row has features for specific pixel
                    Mat matFeaturesFull = calculator.calculateFeatures(matImage);
                    int heightFeatures = matFeaturesFull.rows();
                    int widthFeatures = matFeaturesFull.cols();
                    if (heightFeatures != matMask.rows() || widthFeatures != matMask.cols()) {
                        opencv_imgproc.resize(matMask, matMask, new opencv_core.Size(widthFeatures, heightFeatures));
                    }
                    // Reshape mask to a column matrix
                    matMask.put(matMask.reshape(1, matMask.rows()*matMask.cols()));
//                    System.err.println('SIZE: ' + widthFeatures + ' x ' + heightFeatures)
//                    matFeaturesFull.convertTo(matFeaturesFull, opencv_core.CV_32F)
                    matFeaturesFull.put(matFeaturesFull.reshape(1, matMask.rows()*matMask.cols()));
                    // Extract the pixels
                    UByteIndexer indexerMask = matMask.createIndexer();
                    List<Mat> rows = new ArrayList<>();
                    for (int r = 0; r < indexerMask.rows(); r++) {
                        if (indexerMask.get(r) == 0)
                            continue;
                        rows.add(matFeaturesFull.row(r));
                    }
                    indexerMask.release();
                    matFeatures = new Mat();
                    opencv_core.vconcat(new MatVector(rows.toArray(new Mat[0])), matFeatures);
                    cacheFeatures.put(roi, matFeatures);
                }
                allFeatures.add(matFeatures.clone()); // Clone to be careful... not sure if normalization could impact this under adverse conditions
                Mat targets;
                if (modelType == opencv_ml.VAR_CATEGORICAL) {
                    targets = new Mat(matFeatures.rows(), 1, opencv_core.CV_32SC1, opencv_core.Scalar.all(label));
                } else {
                    targets = new Mat(matFeatures.rows(), nTargets, opencv_core.CV_32FC1, opencv_core.Scalar.ZERO);
                    targets.col(label).put(opencv_core.Scalar.ONE);                	
                }
                allTargets.add(targets);
            }
            label++;
        }
        if (matTraining == null)
            matTraining = new Mat();
        if (matTargets == null)
            matTargets = new Mat();
        opencv_core.vconcat(new MatVector(allFeatures.toArray(new Mat[0])), matTraining);
        opencv_core.vconcat(new MatVector(allTargets.toArray(new Mat[0])), matTargets);

        int nFeatures = matTraining.cols();
        Mat matMean = new Mat(1, nFeatures, opencv_core.CV_64F);
        Mat matStdDev = new Mat(1, nFeatures, opencv_core.CV_64F);
        for (int i = 0; i < nFeatures; i++) {
            opencv_core.meanStdDev(matTraining.col(i), matMean.col(i), matStdDev.col(i));
            // Apply normalization while we're here
            opencv_core.subtractPut(matTraining.col(i), matMean.col(i));
            opencv_core.dividePut(matTraining.col(i), matStdDev.col(i));
        }
        means = OpenCVPixelClassifier.toDoubleArray(matMean);
        scales = OpenCVPixelClassifier.toDoubleArray(matStdDev);

        if (channels == null)
            channels = new ArrayList<>();
        else
            channels.clear();
        channels.addAll(newChannels);

        lastAnnotatedROIs = Collections.unmodifiableMap(map);
        changes = false;
        return true;
    }


    public double[] getLastTrainingMeans() {
        return means;
    }

    public double[] getLastTrainingScales() {
        return scales;
    }


    private void resetTrainingData() {
        if (matTraining != null)
            matTraining.release();
        matTraining = null;
        if (matTargets != null)
            matTargets.release();
        for (Mat matTemp : cacheFeatures.values())
        	matTemp.release();
        cacheFeatures.clear();
        lastAnnotatedROIs = null;
        matTargets = null;
        changes = false;
    }


    public TrainData getTrainData() {
        if (changes)
            updateTrainingData();
        if (matTraining == null || matTargets == null)
            return null;
        return TrainData.create(matTraining, opencv_ml.ROW_SAMPLE, matTargets);
    }

    public List<PixelClassifierOutputChannel> getChannels() {
        return new ArrayList<>(channels);
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return;
        changes = true;
    }

}
