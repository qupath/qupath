package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_ml
import org.bytedeco.javacpp.opencv_ml.TrainData
import qupath.lib.images.ImageData

import org.bytedeco.javacpp.opencv_core.Mat
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.classes.PathClass
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.objects.hierarchy.PathObjectHierarchy
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.PathROIToolsAwt
import qupath.lib.roi.interfaces.ROI
import qupath.opencv.processing.OpenCVTools

import java.awt.Color
import java.awt.image.BufferedImage


class PixelClassifierHelper implements PathObjectHierarchyListener {

    private ImageData<BufferedImage> imageData
    private OpenCVFeatureCalculator calculator
    private boolean changes = true

    private List<PixelClassifierOutputChannel> channels
    private double requestedPixelSizeMicrons

    private Mat matTraining
    private Mat matTargets
    private double[] means
    private double[] scales

    private Map<ROI, Mat> cacheFeatures = new WeakHashMap<>()

    PixelClassifierHelper(ImageData<BufferedImage> imageData, OpenCVFeatureCalculator calculator, double requestedPixelSizeMicrons) {
        setImageData(imageData)
        this.calculator = calculator
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons
    }

    public double getRequestedPixelSizeMicrons() {
        return requestedPixelSizeMicrons
    }

    public void setFeatureCalculator(OpenCVFeatureCalculator calculator) {
        if (this.calculator == calculator)
            return
        this.calculator = calculator
        resetTrainingData()
    }

    public void setRequestedPixelSizeMicrons(double requestedPixelSizeMicrons) {
        if (this.requestedPixelSizeMicrons == requestedPixelSizeMicrons)
            return
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons
        resetTrainingData()
    }

    public OpenCVFeatureCalculator getFeatureCalculator() {
        return calculator
    }

    public void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return
        if (this.imageData != null) {
            this.imageData.getHierarchy().removePathObjectListener(this)
        }
        this.imageData = imageData
        cacheFeatures.values().each {it.release()}
        cacheFeatures.clear()
        if (this.imageData != null) {
            this.imageData.getHierarchy().addPathObjectListener(this)
        }
        changes = true
    }


    public static Map<PathClass, Collection<ROI>> getAnnotatedROIs(PathObjectHierarchy hierarchy) {
        def annotations = hierarchy.getObjects(null, PathAnnotationObject.class).findAll {
            it.getPathClass() != null && it.getPathClass() != PathClassFactory.getRegionClass() && it.hasROI()
        }

        Map<PathClass, List<ROI>> map = new TreeMap<>()
        annotations.each {
            def pathClass = it.getPathClass()
            if (map.containsKey(pathClass))
                map.get(pathClass).add(it.getROI())
            else {
                map.put(pathClass, [it.getROI()] as Set)
            }
        }
        return map
    }


    private Map<PathClass, Collection<ROI>> lastAnnotatedROIs


    public Map<PathClass, Collection<ROI>> getLastTrainingROIs() {
        return lastAnnotatedROIs
    }


    public boolean updateTrainingData() {
        if (imageData == null) {
            resetTrainingData()
            return false
        }
        def hierarchy = imageData.getHierarchy()

        Map<PathClass, Collection<ROI>> map = getAnnotatedROIs(hierarchy)

        // We need at least two classes for anything very meaningful to happen
        int nTargets = map.size()
        if (nTargets <= 1) {
            resetTrainingData()
            return false
        }

        // Training is the same - so nothing else to do
        if (map.equals(lastAnnotatedROIs))
            return true

        // Get the current image
        def server = imageData.getServer()
        double downsample = requestedPixelSizeMicrons / server.getAveragedPixelSizeMicrons()

        double padding = calculator.requestedPadding() * downsample

        List<PathClass> pathClasses = new ArrayList<>(map.keySet())
        List<PixelClassifierOutputChannel> newChannels = new ArrayList<>()
        def path = imageData.getServerPath()
        def allFeatures = []
        def allTargets = []
        int label = 0
        for (PathClass pathClass : pathClasses) {
            // Create a suitable channel
            PixelClassifierOutputChannel channel = new PixelClassifierOutputChannel(name: pathClass.getName(), color: pathClass.getColor())
            newChannels.add(channel)
            // Loop through the object & get masks
            for (ROI roi : map.get(pathClass)) {
                // Check if we've cached features
                // Here, we use the ROI regardless of classification - because we can quickly create a classification matrix
                def matFeatures = cacheFeatures.get(roi)
                if (matFeatures == null) {
                    def request = RegionRequest.createInstance(path, downsample,
                        roi.getBoundsX()-padding as int, roi.getBoundsY()-padding as int, roi.getBoundsWidth()+padding*2 as int, roi.getBoundsHeight()+padding*2 as int)
                    def shape = PathROIToolsAwt.getShape(roi)
                    def img = server.readBufferedImage(request)
                    def imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY)
                    def g2d = imgMask.createGraphics()
                    g2d.scale(1.0/downsample, 1.0/downsample)
                    g2d.translate(-request.getX(), -request.getY())
                    g2d.setColor(Color.WHITE)
                    g2d.fill(shape)
                    g2d.dispose()

                    def matImage = OpenCVTools.imageToMat(img)
                    matImage.convertTo(matImage, opencv_core.CV_32F)
                    def matMask = OpenCVTools.imageToMat(imgMask)
                    // Reshape mask to a column matrix
                    matMask.put(matMask.reshape(1, matMask.rows()*matMask.cols()))
                    // Get features & reshape so that each row has features for specific pixel
                    def matFeaturesFull = calculator.calculateFeatures(matImage)
//                    matFeaturesFull.convertTo(matFeaturesFull, opencv_core.CV_32F)
                    matFeaturesFull.put(matFeaturesFull.reshape(1, matMask.rows()*matMask.cols()))
                    // Extract the pixels
                    def indexerMask = matMask.createIndexer()
                    List<Mat> rows = new ArrayList<>()
                    for (int r = 0; r < indexerMask.rows(); r++) {
                        if (indexerMask.get(r) == 0)
                            continue
                        rows.add(matFeaturesFull.row(r))
                    }
                    indexerMask.release()
                    matFeatures = new Mat()
                    opencv_core.vconcat(new opencv_core.MatVector(rows as Mat[]), matFeatures)
                    cacheFeatures.put(roi, matFeatures)
                }
                allFeatures << matFeatures.clone() // Clone to be careful... not sure if normalization could impact this under adverse conditions
                def targets = new Mat(matFeatures.rows(), nTargets, opencv_core.CV_32FC1, opencv_core.Scalar.ZERO)
                targets.col(label).put(opencv_core.Scalar.ONE)
                allTargets << targets
            }
            label++
        }
        if (matTraining == null)
            matTraining = new Mat()
        if (matTargets == null)
            matTargets = new Mat()
        opencv_core.vconcat(new opencv_core.MatVector(allFeatures as Mat[]), matTraining)
        opencv_core.vconcat(new opencv_core.MatVector(allTargets as Mat[]), matTargets)

        int nFeatures = matTraining.cols()
        def matMean = new Mat(1, nFeatures, opencv_core.CV_64F)
        def matStdDev = new Mat(1, nFeatures, opencv_core.CV_64F)
        for (int i = 0; i < nFeatures; i++) {
            opencv_core.meanStdDev(matTraining.col(i), matMean.col(i), matStdDev.col(i))
            // Apply normalization while we're here
            opencv_core.subtractPut(matTraining.col(i), matMean.col(i))
            opencv_core.dividePut(matTraining.col(i), matStdDev.col(i))
        }
        means = OpenCVPixelClassifier.toDoubleArray(matMean)
        scales = OpenCVPixelClassifier.toDoubleArray(matStdDev)

        if (channels == null)
            channels = []
        else
            channels.clear()
        channels.addAll(newChannels)

        lastAnnotatedROIs = Collections.unmodifiableMap(map)
        changes = false
        return true
    }


    public double[] getLastTrainingMeans() {
        return means
    }

    public double[] getLastTrainingScales() {
        return scales
    }


    private void resetTrainingData() {
        if (matTraining != null)
            matTraining.release()
        matTraining = null
        if (matTargets != null)
            matTargets.release()
        cacheFeatures.values().each {it.release()}
        cacheFeatures.clear()
        lastAnnotatedROIs = null
        matTargets = null
        changes = false
    }


    public TrainData getTrainData() {
        if (changes)
            updateTrainingData()
        if (matTraining == null || matTargets == null)
            return null
        return TrainData.create(matTraining, opencv_ml.ROW_SAMPLE, matTargets)
    }

    public List<PixelClassifierOutputChannel> getChannels() {
        return new ArrayList<>(channels)
    }

    @Override
    void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return
        changes = true
    }

}
