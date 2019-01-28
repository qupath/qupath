package qupath.lib.classifiers.gui;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.indexer.UShortIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_ml.ANN_MLP;
import org.bytedeco.javacpp.opencv_ml.KNearest;
import org.bytedeco.javacpp.opencv_ml.RTrees;
import org.bytedeco.javacpp.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.imagej.helpers.IJTools;
import qupath.imagej.images.servers.BufferedImagePlusServer;
import qupath.imagej.images.servers.ImagePlusServer;
import qupath.imagej.images.servers.ImagePlusServerBuilder;
import qupath.lib.classifiers.opencv.OpenCVClassifiers;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.OpenCVStatModel;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.features.BasicMultiscaleOpenCVFeatureCalculator;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.classifiers.pixel.features.SmoothedOpenCVFeatureCalculator;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.TypeAdaptersCV;

import java.awt.Color;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * User interface for interacting with pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierGUI implements PathCommand, QuPathViewerListener, PathObjectHierarchyListener {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierGUI.class);

    public static enum ClassificationResolution {
    	MAX_RESOLUTION, ULTRA_HIGH, VERY_HIGH, HIGH, MODERATE, LOW, VERY_LOW;

        public double getMicronsPerPixel() {
            switch(this) {
	            case MAX_RESOLUTION:
	                return -1;
                case ULTRA_HIGH:
                    return 0.5;
                case VERY_HIGH:
                    return 1.0;
                case HIGH:
                    return 2.0;
                case MODERATE:
                    return 4.0;
                case LOW:
                    return 8.0;
                case VERY_LOW:
                    return 16.0;
            }
			return Double.NaN;
        }

        public String toString() {
            switch(this) {
	            case MAX_RESOLUTION:
	                return "Maximum resolution";
                case ULTRA_HIGH:
                    return String.format("Ultra high (%.1f mpp)", getMicronsPerPixel());
                case VERY_HIGH:
                    return String.format("Very high (%.0f mpp)", getMicronsPerPixel());
                case HIGH:
                    return String.format("High (%.0f mpp)", getMicronsPerPixel());
                case MODERATE:
                    return String.format("Moderate (%.0f mpp)", getMicronsPerPixel());
                case LOW:
                    return String.format("Low (%.0f mpp)", getMicronsPerPixel());
                case VERY_LOW:
                    return String.format("Very low (%.0f mpp)", getMicronsPerPixel());
            }
			return null;
        }
    }

    private static PixelClassifierGUI classifierGUI;

    private PixelClassifierHelper helper;
    private Stage stage;
    private OpenCVStatModel model;

    private QuPathViewer viewer;

    private PixelClassificationOverlay overlay;
    private PixelClassifier classifier;

    private SimpleObjectProperty<ClassificationResolution> selectedResolution = new SimpleObjectProperty<>();
    private SimpleObjectProperty<OpenCVFeatureCalculator> selectedFeatureCalculator = new SimpleObjectProperty<>();
    private BooleanProperty autoUpdate = new SimpleBooleanProperty();
    
    private ObservableList<OpenCVStatModel> availableClassifierBuilders = FXCollections.observableArrayList(
    		OpenCVClassifiers.wrapStatModel(RTrees.create()),
    		OpenCVClassifiers.wrapStatModel(ANN_MLP.create()),
    		OpenCVClassifiers.wrapStatModel(KNearest.create())
    		);
    
    private SimpleObjectProperty<OpenCVStatModel> classifierBuilder = new SimpleObjectProperty<>(availableClassifierBuilders.get(0));

    private ObservableList<PathClass> classificationList = FXCollections.observableArrayList();
    
    public final static int NUM_CHANNELS = 3;
    
    private ObservableList<OpenCVFeatureCalculator> featureCalculators = FXCollections.observableArrayList(
    		new SmoothedOpenCVFeatureCalculator(NUM_CHANNELS, 1.0),
            new SmoothedOpenCVFeatureCalculator(NUM_CHANNELS, 2.0),
            new SmoothedOpenCVFeatureCalculator(NUM_CHANNELS, 4.0),
            new BasicMultiscaleOpenCVFeatureCalculator(NUM_CHANNELS, 1.0, 1, false),
            new BasicMultiscaleOpenCVFeatureCalculator(NUM_CHANNELS, 1.0, 2, false),
            new BasicMultiscaleOpenCVFeatureCalculator(NUM_CHANNELS, 1.0, 3, false),
            new BasicMultiscaleOpenCVFeatureCalculator(NUM_CHANNELS, 2.0, 3, false)
    		);


    private PixelClassifierGUI(final QuPathViewer viewer) {
        this.viewer = viewer;
    }

    public synchronized static PixelClassifierGUI getInstance() {
        if (classifierGUI == null) {
            classifierGUI = new PixelClassifierGUI(QuPathGUI.getInstance().getViewer());

        }
        return classifierGUI;
    }
    
    
    /**
     * Access the observable list of available feature calculators.
     * 
     * @return
     */
    public ObservableList<OpenCVFeatureCalculator> getFeatureCalculators() {
    	return featureCalculators;
    }
    

    @Override
    public void run() {
        getInstance().show();
    }


    private void createGUI() {
        stage = new Stage();
        stage.setTitle("Pixel classifier");
        stage.initOwner(QuPathGUI.getInstance().getStage());
        stage.setOnCloseRequest(e -> destroy());


        ListView<PathClass> listClassifications = new ListView<>(classificationList);
        listClassifications.setCellFactory(c -> new ClassificationCell());
        listClassifications.setPrefHeight(200);


        viewer.addViewerListener(this);
        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData != null)
            imageData.getHierarchy().addPathObjectListener(this);
        
        var channelsList = IntStream.range(0, imageData.getServer().nChannels()).boxed().collect(Collectors.toList());
        
        var filters = new ArrayList<FeatureFilter>();
        var sigmas = new double[]{2.0, 4.0, 8.0};
        for (var sigma : sigmas) {
        	filters.add(new GaussianFeatureFilter(sigma));
        }
        featureCalculators.add(new BasicFeatureCalculator(
    				"Smoothed features",
    				channelsList, 
    				filters, 4.0));
        
        for (var sigma : sigmas) {
        	filters.add(new SobelFeatureFilter(sigma));
        }
        featureCalculators.add(new BasicFeatureCalculator(
    				"Smoothed with edges",
    				channelsList, 
    				filters, 4.0));

        for (var sigma : sigmas) {
        	filters.add(new LoGFeatureFilter(sigma));
        }
        featureCalculators.add(new BasicFeatureCalculator(
    				"Smoothed, edges, LoG",
    				channelsList, 
    				filters, 4.0));

        for (var sigma : sigmas) {
        	filters.add(new CoherenceFeatureFilter(sigma));
        }
        featureCalculators.add(new BasicFeatureCalculator(
    				"Smoothed, edges, LoG, coherence",
    				channelsList, 
    				filters, 4.0));

//        for (var channel : viewer.getImageDisplay().availableChannels()) {
//        	if (channel instanceof SingleChannelDisplayInfo) {
//        		var sigmas = new double[]{2.0, 4.0, 8.0};
//        		var filters = new ArrayList<FeatureFilter>();
//        		for (var s : sigmas) {
//        			filters.add(new GaussianFeatureFilter(s));
//        			filters.add(new LoGFeatureFilter(s));
////        			filters.add(new SobelFeatureFilter(s));
////        			filters.add(new CoherenceFeatureFilter(s));
//        		}
//        		featureCalculators.add(new BasicFeatureCalculator(
//        				channel.getName() + " - basic features",
//        				Collections.singletonList((SingleChannelDisplayInfo)channel), 
//        				filters, 4.0));
//        	}
//        }
        

        // Make it possible to choose OpenCVFeatureCalculator
        ComboBox<OpenCVFeatureCalculator> comboFeatures = new ComboBox<>(featureCalculators);

//        // Load more models if we can find any...
//        Project<BufferedImage> project = QuPathGUI.getInstance().getProject();
//        if (project != null) {
//        	File dirModels = new File(project.getBaseDirectory(), "models");
//            if (dirModels.exists()) {
//                for (File f : dirModels.listFiles()) {
//                    if (f.isFile() && !f.isHidden()) {
//                        try {
//                        	opencv_dnn.Net model = opencv_dnn.readNetFromTensorflow(f.getAbsolutePath());
//                        	// TODO: HANDLE UNKNOWN MEAN AND SCALE!
//                        	logger.warn("Creating DNN feature calculator - but don't know mean or scale to apply!");
//                        	OpenCVFeatureCalculatorDNN featuresDNN = new OpenCVFeatureCalculatorDNN(model, f.getName(), 0, 1.0/255.0);
//                            logger.info("Loaded model from {}", f.getAbsolutePath());
//                            featureCalculators.add(featuresDNN);
//                        } catch (Exception e) {
//                            logger.warn("Unable to load model from {}", f.getAbsolutePath());
//                        }
//                    }
//                }
//            }
//        }


        comboFeatures.getSelectionModel().select(0);
        comboFeatures.setMaxWidth(Double.MAX_VALUE);
        selectedFeatureCalculator.bind(comboFeatures.getSelectionModel().selectedItemProperty());
        selectedFeatureCalculator.addListener((v, o, n) -> {
            if (autoUpdate.get())
                updateClassification();
        });
        Label labelFeatures = new Label("Features");
        labelFeatures.setLabelFor(comboFeatures);

        // Show features for current selection
        Button btnShowFeatures = new Button("Show");
        btnShowFeatures.setOnAction(e -> showFeatures());
        btnShowFeatures.setMaxWidth(Double.MAX_VALUE);
        btnShowFeatures.setTooltip(new Tooltip("Show ImageJ stack illustrating the features being used"));

        // Make it possible to choose resolution
        ComboBox<ClassificationResolution> comboResolution = new ComboBox<>();
        comboResolution.getItems().addAll(ClassificationResolution.values());
        comboResolution.getSelectionModel().select(ClassificationResolution.MODERATE);
        comboResolution.setMaxWidth(Double.MAX_VALUE);
        selectedResolution.bind(comboResolution.getSelectionModel().selectedItemProperty());
        selectedResolution.addListener((v, o, n) -> {
            if (autoUpdate.get())
            	updateClassification();
        });
        Label labelResolution = new Label("Resolution");
        labelResolution.setLabelFor(comboResolution);
        
        // Make it possible to choose the type of classifier
        var comboClassifierType = new ComboBox<>(availableClassifierBuilders);
        comboClassifierType.getSelectionModel().select(classifierBuilder.get());
        comboClassifierType.setMaxWidth(Double.MAX_VALUE);
        classifierBuilder.bind(comboClassifierType.getSelectionModel().selectedItemProperty());
        classifierBuilder.addListener((v, o, n) -> {
            if (autoUpdate.get())
            	updateClassification();
        });
        Label labelClassifierType = new Label("Classifier");
        labelClassifierType.setLabelFor(comboClassifierType);

        // Possibly auto-update
        CheckBox cbAutoUpdate = new CheckBox("Auto update");
        autoUpdate.bindBidirectional(cbAutoUpdate.selectedProperty());
        autoUpdate.addListener((v, o, n) -> {
            if (n)
                updateClassification();
        });

        // Make it possible to choose requested pixel size, if auto-update is not selected
        Button btnUpdate = new Button("Update");
        btnUpdate.disableProperty().bind(autoUpdate);
        btnUpdate.setMaxWidth(Double.MAX_VALUE);
        btnUpdate.setMaxHeight(Double.MAX_VALUE);
        btnUpdate.setOnAction( e -> updateClassification());
        btnUpdate.setTooltip(new Tooltip("Update the classifier"));


        GridPane pane = new GridPane();
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMaxHeight(Double.MAX_VALUE);
        pane.setHgap(5.0);
        pane.setVgap(5.0);
        pane.setPadding(new Insets(5));
        int row = 0;
        pane.add(labelFeatures, 0, row, 1, 1);
        pane.add(comboFeatures, 1, row, 1, 1);
        pane.add(btnShowFeatures, 2, row++, 1, 1);
        pane.add(labelResolution, 0, row, 1, 1);
        pane.add(comboResolution, 1, row++, 1, 1);
        pane.add(labelClassifierType, 0, row, 1, 1);
        pane.add(comboClassifierType, 1, row++, 1, 1);
        pane.add(cbAutoUpdate, 0, row++, 3, 1);
        pane.add(listClassifications, 0, row++, 3, 1);
        // Add update button (across rows)
        pane.add(btnUpdate, 2, 1, 1, 2);
//        pane.add(btnUpdate, 0, row++, 3, 1);

        
        
        Button btnShowClassification = new Button("Apply current classifier");
        btnShowClassification.setMaxWidth(Double.MAX_VALUE);
        btnShowClassification.setOnAction(e -> {
        	if (classifier == null || imageData == null)
        		return;
        	ImageServer<BufferedImage> server = imageData.getServer();
        	PathObjectHierarchy hierarchy = imageData.getHierarchy();
        	Set<PathObject> selected = hierarchy.getSelectionModel().getSelectedObjects();
        	if (selected.isEmpty())
        		selected = Collections.singleton(hierarchy.getRootObject());
        	 double downsample = selectedResolution.get().getMicronsPerPixel() / server.getAveragedPixelSizeMicrons();
             if (downsample < 0)
             	downsample = 1;
        	for (PathObject parent : selected) {
            	RegionRequest request = parent.isRootObject() ?
            			RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight()) :
            				RegionRequest.createInstance(server.getPath(), downsample, parent.getROI());
      			ImagePlus imp;
            	try {
	            	BufferedImage imgClassified = classifier.applyClassification(viewer.getServer(), request);
	            	String title = String.format("Classified %s (%.2f, x=%d, y=%d, w=%d, h=%d, z=%d, t=%d)", server.getDisplayedImageName(),
	            			request.getDownsample(), request.getX(), request.getY(), request.getWidth(), request.getHeight(), request.getZ(), request.getT());
	            	imp = BufferedImagePlusServer.convertToImagePlus(title, server, imgClassified, request).getImage();
            	} catch (IOException e2) {
            		logger.error("Unable to apply classification for " + request, e2);
            		continue;
            	}
            	
        		List<ImageChannel> channels = classifier.getMetadata().getChannels();
            	if (imp.getNChannels() > 1 && imp.getNChannels() == channels.size()) {
            		CompositeImage impComp = imp instanceof CompositeImage ? (CompositeImage)imp : new CompositeImage(imp, CompositeImage.GRAYSCALE);
            		for (int c = 0; c < channels.size(); c++) {
                		impComp.getStack().setSliceLabel(channels.get(c).getName(), c+1);            			
                		impComp.setChannelLut(LUT.createLutFromColor(new Color(channels.get(c).getColor())), c+1);
            		}
            		impComp.resetDisplayRanges(); // TODO: Consider setting to full range
            	}
            	
            	imp.show();
        	}
        });
        pane.add(btnShowClassification, 0, row++, 3, 1);
        

        stage.setScene(new Scene(pane));
    }

    void showFeatures() {
        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData == null) {
            DisplayHelpers.showErrorMessage("Show features", "No image available!");
            return;
        }
        ROI roi = viewer.getCurrentROI();
        if (roi == null) {
            DisplayHelpers.showErrorMessage("Show features", "No ROI selected!");
            return;
        }
        OpenCVFeatureCalculator calculator = selectedFeatureCalculator.get();
        if (calculator == null) {
            DisplayHelpers.showErrorMessage("Show features", "No feature calculator available!");
            return;
        }
        ImageServer<BufferedImage> server = imageData.getServer();
        double downsample = selectedResolution.get().getMicronsPerPixel() / server.getAveragedPixelSizeMicrons();
        if (downsample < 0)
        	downsample = 1;
        
        // Always give at the requested pixel size
        int x = (int)Math.round(roi.getCentroidX() - calculator.getMetadata().getInputWidth() * downsample / 2.0);
        int y = (int)Math.round(roi.getCentroidY() - calculator.getMetadata().getInputHeight() * downsample / 2.0);
        int w = (int)Math.round(calculator.getMetadata().getInputWidth() * downsample);
        int h = (int)Math.round(calculator.getMetadata().getInputHeight() * downsample);
        RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, x, y, w, h);
        
        Mat matFeatures;
        List<String> names = calculator.getMetadata().getChannels().stream().map(c -> c.getName()).collect(Collectors.toList());
        try {
	        ImagePlusServer serverIJ = ImagePlusServerBuilder.ensureImagePlusWholeSlideServer(server);
	        serverIJ.readImagePlusRegion(request).getImage().show();
	        
	//        downsample = 0.5 / server.getAveragedPixelSizeMicrons();
	//        request = RegionRequest.createInstance(server.getPath(), downsample,
	//        		(int)roi.getBoundsX(),
	//        		(int)roi.getBoundsY(),
	//        		(int)Math.round(256*downsample),
	//  				(int)Math.round(256*downsample));
	        
	        matFeatures = calculator.calculateFeatures(imageData.getServer(), request);
	        var preprocessor = helper.getLastFeaturePreprocessor();
	        if (preprocessor != null) {
	        	preprocessor.apply(matFeatures);
	        	if (names.size() != matFeatures.channels()) {
	        		names.clear();
	        		for (int i = 1; i <= matFeatures.channels(); i++)
	        			names.add("Feature " + i + " (preprocessed)");
	        	}
	        }
        }
        catch (IOException e2) {
    		logger.error("Unable to calculate faetures for " + request, e2);
    		return;
    	}
        
        ImagePlus imp = matToImagePlus(matFeatures,
                String.format("Features: (%.2f, %d, %d, %d, %d)",
                request.getDownsample(), request.getX(), request.getY(), request.getWidth(), request.getHeight()));
        CompositeImage impComp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
        impComp.setDimensions(impComp.getStackSize(), 1, 1);
        for (int c = 1; c <= impComp.getStackSize(); c++) {
            impComp.setC(c);
            if (names != null && !names.isEmpty())
                impComp.getStack().setSliceLabel(names.get(c-1), c);
            impComp.resetDisplayRange();
        }
        IJTools.calibrateImagePlus(impComp, request, server);

        impComp.setC(1);
        impComp.show();
    }

    public void show() {
        if (stage == null) {
            createGUI();
        }
        stage.show();
    }


    void updateClassification() {
        
        double downsample = viewer.getServer() == null ? selectedResolution.get().getMicronsPerPixel() :
        	selectedResolution.get().getMicronsPerPixel() / viewer.getServer().getAveragedPixelSizeMicrons();
        
        
       model = classifierBuilder.get();
        
        if (helper == null)
            helper = new PixelClassifierHelper(
            		viewer.getImageData(), selectedFeatureCalculator.get(), downsample);
        else {
            helper.setImageData(viewer.getImageData());
            helper.setFeatureCalculator(selectedFeatureCalculator.get());
            helper.setDownsample(downsample);
        }
        
        helper.updateTrainingData();
        TrainData trainData = helper.getTrainData();
        if (trainData == null) {
            logger.error("Not enough annotations to train a classifier!");
            classificationList.clear();
            return;
        }
        List<ImageChannel> channels = helper.getChannels();
        
     // TODO: Optionally limit the number of training samples we use
//     		var trainData = classifier.createTrainData(matFeatures, matTargets);
     		int maxSamples = 10000;
     		if (maxSamples > 0 && trainData.getNTrainSamples() > maxSamples)
     			trainData.setTrainTestSplit(maxSamples, true);
     		else
     			trainData.shuffleTrainTest();
     	
        model.train(trainData);
//        model.train(trainData, modelBuilder.getVarType());
        
        int inputWidth = helper.getFeatureCalculator().getMetadata().getInputWidth();
        int inputHeight = helper.getFeatureCalculator().getMetadata().getInputHeight();
        PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
//        		.inputPixelSizeMicrons(helper.getRequestedPixelSizeMicrons())
        		.inputShape(inputWidth, inputHeight)
        		.channels(channels)
        		.build();

        classifier = new OpenCVPixelClassifier(model, helper.getFeatureCalculator(), helper.getLastFeaturePreprocessor(), metadata);

        classificationList.setAll(helper.getLastTrainingROIs().keySet());

        replaceOverlay(new PixelClassificationOverlay(viewer, classifier));
    }

    /**
     * Replace the overlay - making sure to do this on the application thread
     *
     * @param newOverlay
     */
    void replaceOverlay(PixelClassificationOverlay newOverlay) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> replaceOverlay(newOverlay));
            return;
        }
        if (overlay != null) {
            overlay.stop();
            viewer.getCustomOverlayLayers().remove(overlay);
        }
        overlay = newOverlay;
        if (overlay != null)
        	viewer.getCustomOverlayLayers().add(overlay);
    }



    public void destroy() {
        if (overlay != null) {
        	overlay.stop();
            viewer.getCustomOverlayLayers().remove(overlay);
            overlay = null;
        }
        imageDataChanged(viewer, viewer.getImageData(), null);
    	if (helper != null)
    		helper.setImageData(null);
        classifierGUI = null;
        if (stage != null && stage.isShowing())
            stage.close();
    }


    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging() || !autoUpdate.get()) {
            return;
        }
        // Only update the classification if necessary
        Map<PathClass, Collection<ROI>> map = PixelClassifierHelper.getAnnotatedROIs(event.getHierarchy());
        if (helper == null || !map.equals(helper.getLastTrainingROIs())) {
            updateClassification();
        }
    }

    @Override
    public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        if (helper != null)
            helper.setImageData(imageDataNew);
        if (imageDataOld != null)
            imageDataOld.getHierarchy().removePathObjectListener(this);
        if (imageDataNew != null)
            imageDataNew.getHierarchy().addPathObjectListener(this);
    }

    @Override
    public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

    @Override
    public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

    @Override
    public void viewerClosed(QuPathViewer viewer) {
        if (viewer.getImageData() != null)
            viewer.getImageData().getHierarchy().removePathObjectListener(this);
        destroy();
    }


    /**
     * Convert an OpenCV {@code Mat} into an ImageJ {@code ImagePlus}.
     * 
     * @param mat
     * @param title
     * @return
     */
    public static ImagePlus matToImagePlus(Mat mat, String title) {
        if (mat.channels() == 1) {
            return new ImagePlus(title, matToImageProcessor(mat));
        }
        MatVector matvec = new MatVector();
        opencv_core.split(mat, matvec);
        ImageStack stack = new ImageStack(mat.cols(), mat.rows());
        for (int s = 0; s < matvec.size(); s++) {
            stack.addSlice(matToImageProcessor(matvec.get(s)));
        }
        return new ImagePlus(title, stack);
    }

    /**
     * Convert a single-channel OpenCV {@code Mat} into an ImageJ {@code ImageProcessor}.
     * 
     * @param mat
     * @return
     */
    static ImageProcessor matToImageProcessor(Mat mat) {
    	if (mat.channels() != 1)
    		throw new IllegalArgumentException("Only a single-channel Mat can be converted to an ImageProcessor! Specified Mat has " + mat.channels() + " channels");
        int w = mat.cols();
        int h = mat.rows();
        if (mat.depth() == opencv_core.CV_32F) {
            FloatIndexer indexer = mat.createIndexer();
            float[] pixels = new float[w*h];
            indexer.get(0L, pixels);
            return new FloatProcessor(w, h, pixels);
        } else if (mat.depth() == opencv_core.CV_8U) {
            UByteIndexer indexer = mat.createIndexer();
            int[] pixels = new int[w*h];
            indexer.get(0L, pixels);
            ByteProcessor bp = new ByteProcessor(w, h);
            for (int i = 0; i < pixels.length; i++)
            	bp.set(i, pixels[i]);
            return bp;
        } else if (mat.depth() == opencv_core.CV_16U) {
            UShortIndexer indexer = mat.createIndexer();
            int[] pixels = new int[w*h];
            indexer.get(0L, pixels);
            short[] shortPixels = new short[pixels.length];
            for (int i = 0; i < pixels.length; i++)
            	shortPixels[i] = (short)pixels[i];
            return new ShortProcessor(w, h, shortPixels, null); // TODO: Test!
        } else {
        	Mat mat2 = new Mat();
            mat.convertTo(mat2, opencv_core.CV_32F);
            ImageProcessor ip = matToImageProcessor(mat2);
            mat2.release();
            return ip;
        }
    }
    
    
    
    
    


    class ClassificationCell extends ListCell<PathClass> {

        @Override
        protected void updateItem(PathClass value, boolean empty) {
            super.updateItem(value, empty);
            int size = 10;
            if (value == null || empty) {
                setText(null);
                setGraphic(null);
            } else if (value.getName() == null) {
                setText("None");
                setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
            } else {

                Map<PathClass, Collection<ROI>> map = helper == null ? null : helper.getLastTrainingROIs();
                Integer n = null;
                if (map != null) {
                    n = map.get(value) == null ? null : map.get(value).size();
                }
                if (n != null)
                    setText(value.getName() + " (" + n + ")");
                else
                    setText(value.getName());
                setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
            }
//            if (value != null && qupath.getViewer().getOverlayOptions().isPathClassHidden(value)) {
//                setStyle("-fx-font-family:arial; -fx-font-style:italic;");
//                setText(getText() + " (hidden)");
//            } else
//                setStyle("-fx-font-family:arial; -fx-font-style:normal;");
        }

    }
    
    @JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
    public static class BasicFeatureCalculator implements OpenCVFeatureCalculator {
    	
    	private String name;
    	private List<Integer> channels = new ArrayList<>();
    	private List<FeatureFilter> filters = new ArrayList<>();
    	private PixelClassifierMetadata metadata;
    	
    	private int nPyramidLevels = 1;
    	private int padding = 0;
    	
    	public BasicFeatureCalculator(String name, List<Integer> channels, List<FeatureFilter> filters, double pixelSizeMicrons) {
    		this.name = name;
    		this.channels.addAll(channels);
    		this.filters.addAll(filters);
    		
    		var outputChannels = new ArrayList<ImageChannel>();
    		for (var channel : channels) {
    			for (var filter : filters) {
    				outputChannels.add(ImageChannel.getInstance("Channel " + channel + ": " + filter.getName(), ColorTools.makeRGB(255, 255, 255)));
//    				outputChannels.add(new PixelClassifierOutputChannel(channel.getName() + ": " + filter.getName(), ColorTools.makeRGB(255, 255, 255)));
    			}
    		}
    		
    		padding = filters.stream().mapToInt(f -> f.getPadding()).max().orElseGet(() -> 0);
    		metadata = new PixelClassifierMetadata.Builder()
    				.channels(outputChannels)
    				.inputPixelSizeMicrons(pixelSizeMicrons)
    				.inputShape(512, 512)
    				.build();
    		
    		
    		for (int i = 1; i< nPyramidLevels; i++) {
    			padding *= 2;
    		}
    		
    	}
    	
    	public String toString() {
    		return name;
    	}
    	
		@Override
		public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
			
			BufferedImage img = BasicMultiscaleOpenCVFeatureCalculator.getPaddedRequest(server, request, padding);
			
			List<Mat> output = new ArrayList<opencv_core.Mat>();
			
			int w = img.getWidth();
			int h = img.getHeight();
			float[] pixels = new float[w * h];
			var mat = new Mat(h, w, opencv_core.CV_32FC1);
			FloatIndexer idx = mat.createIndexer();
			for (var channel : channels) {
				pixels = img.getRaster().getSamples(0, 0, w, h, channel, pixels);
//				channel.getValues(img, 0, 0, w, h, pixels);
				idx.put(0L, pixels);
				
				addFeatures(mat, output);
				
				if (nPyramidLevels > 1) {
					var matLastLevel = mat;
        			var size = mat.size();
	    			for (int i = 1; i < nPyramidLevels; i++) {
	    				// Downsample pyramid level
	    				var matPyramid = new Mat();
	    				opencv_imgproc.pyrDown(matLastLevel, matPyramid);
	    				// Add features to a temporary list (because we'll need to resize them
	    				var tempList = new ArrayList<Mat>();
	    				addFeatures(matPyramid, tempList);
	    				for (var temp : tempList) {
	    					// Upsample
	    					for (int k = i; k > 0; k--)
	    						opencv_imgproc.pyrUp(temp, temp);
	    					// Adjust size if necessary
	    					if (temp.rows() != size.height() || temp.cols() != size.width())
	    						opencv_imgproc.resize(temp, temp, size, 0, 0, opencv_imgproc.INTER_CUBIC);
	    					output.add(temp);
	    				}
	    				if (matLastLevel != mat)
	    					matLastLevel.release();
	    				matLastLevel = matPyramid;
	    			}
	    			matLastLevel.release();
				}
    			
			}
			
			opencv_core.merge(new MatVector(output.toArray(Mat[]::new)), mat);
			if (padding > 0)
				mat.put(mat.apply(new opencv_core.Rect(padding, padding, mat.cols()-padding*2, mat.rows()-padding*2)).clone());
			
			return mat;
		}
		
		
		void addFeatures(Mat mat, List<Mat> output) {
			Mat matGaussian = null;
			double sigma = Double.NaN;
			for (var filter : filters) {
				if (filter instanceof AbstractGaussianFeatureFilter) {
					double nextSigma = ((AbstractGaussianFeatureFilter) filter).getSigma();
					if (nextSigma != sigma) {
						if (matGaussian == null)
							matGaussian = new Mat(mat.rows(), mat.cols(), opencv_core.CV_32FC1);
						sigma = nextSigma;
	    				gaussianFilter(mat, sigma, matGaussian);
					}
					((AbstractGaussianFeatureFilter)filter).calculate(mat, matGaussian, output);
				} else {
					filter.calculate(mat, output);
				}
			}
			if (matGaussian != null)
				matGaussian.release();
	    }
		

		@Override
		public PixelClassifierMetadata getMetadata() {
			return metadata;
		}
    	
    }
    
        
    
    static void gaussianFilter(Mat matInput, double sigma, Mat matOutput) {
    	int s = (int)Math.ceil(sigma * 3) * 2 + 1;
    	opencv_imgproc.GaussianBlur(matInput, matOutput, new Size(s, s), sigma);
    }
    
    public static abstract class FeatureFilter {
    	    	
    	public abstract String getName();
    	
    	public abstract int getPadding();
    	
    	public abstract void calculate(Mat matInput, List<Mat> output);
    	
    	@Override
    	public String toString() {
    		return getName();
    	}
    	    	
    }
    
    /**
     * Clone the input image without further modification.
     */
    public static class OriginalPixels extends FeatureFilter {

		@Override
		public String getName() {
			return "Original pixels";
		}

		@Override
		public int getPadding() {
			return 0;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			output.add(matInput.clone());
		}
    	
    	
    	
    }
    
    
    static Mat getSumFilter(int radius) {
    	int s = radius*2 + 1;
		var kernel = opencv_imgproc.getStructuringElement(
				opencv_imgproc.MORPH_ELLIPSE, new Size(s, s));
		kernel.convertTo(kernel, opencv_core.CV_32F);
		return kernel;
    }
    
    static Mat getMeanFilter(int radius) {
		var kernel = getSumFilter(radius);
		opencv_core.dividePut(kernel, opencv_core.countNonZero(kernel));
		return kernel;
    }
    
    
    public static class StdDevFeatureFilter extends FeatureFilter {
    	
//    	private boolean includeMean = false;
    	private int radius;
    	
    	private transient Mat kernel;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public StdDevFeatureFilter(final int radius) {
//    		this.includeMean = includeMean;
    		this.radius = radius;
    	}

		@Override
		public String getName() {
			return "Std dev filter (sigma=" + radius + ")";
		}

		@Override
		public int getPadding() {
			return radius;
		}
		
		private synchronized Mat getKernel() {
			if (kernel == null) {
				kernel = getMeanFilter(radius);
			}
			return kernel;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
//			var matX = new Mat();
//			var kernel = getKernel();
//			gaussianFilter(matInput, radius, matX);
//			matX.put(matX.mul(matX));
//			
//			var matX2 = matInput.mul(matInput).asMat();
//			gaussianFilter(matX2, radius, matX2);
//			
//			opencv_core.subtractPut(matX2, matX);
//			opencv_core.sqrt(matX2, matX2);
//			matX.release();
//			output.add(matX2);
			
			var matX = new Mat();
			var kernel = getKernel();
			opencv_imgproc.filter2D(matInput, matX, opencv_core.CV_32F, kernel);
			matX.put(matX.mul(matX));
			
			var matX2 = matInput.mul(matInput).asMat();
			opencv_imgproc.filter2D(matX2, matX2, opencv_core.CV_32F, kernel);
			
			opencv_core.subtractPut(matX2, matX);
			opencv_core.sqrt(matX2, matX2);
			matX.release();
			
			// TODO: Consider applying Gaussian filter afterwards
			
			output.add(matX2);
		}
    	
    }
    
    
    
    public static class NormalizedIntensityFilter extends AbstractGaussianFeatureFilter {

		public NormalizedIntensityFilter(double sigma) {
			super(sigma);
		}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			
			var kernel = getMeanFilter((int)Math.round(getSigma() * 2));

			// Mean of X^2
			var matXSq = matInput.mul(matInput).asMat();
			opencv_imgproc.filter2D(matXSq, matXSq, -1, kernel);
			
			// Mean of 2X*y
			var matX = new Mat();
			opencv_imgproc.filter2D(matInput, matX, -1, kernel);
			var mat2XY = opencv_core.multiply(2.0, matX.mul(matGaussian));
			
			// Mean of y^2 (constant)
			var matYSq = matGaussian.mul(matGaussian);
//			var n = opencv_core.countNonZero(kernel);
//			matYSq = opencv_core.multiply(matYSq, n);
			
			// X^2 + y^2 - 2Xy
			var localStdDev = opencv_core.subtract(opencv_core.add(matXSq, matYSq), mat2XY).asMat();
			opencv_core.sqrt(localStdDev, localStdDev);
			// setTo doesn't appear to work?
//			var mask = opencv_core.lessThan(localStdDev, 1).asMat();
//			var one = new Mat(1, 1, localStdDev.type(), Scalar.ONE);
//			localStdDev.setTo(one, mask);
			localStdDev.put(opencv_core.max(localStdDev, 1.0));
			
			var localSubtracted = opencv_core.subtract(matInput, matGaussian);
			var localNormalized = opencv_core.divide(localSubtracted, localStdDev);
			
			matXSq.put(localNormalized);
			
//			matX.release();
//			mask.release();
//			localStdDev.release();
			
			output.add(matXSq);
		}

		@Override
		public String getName() {
			return "Normalized intensity" + sigmaString();
		}
    	
    }
    
    
    
    public static class PeakDensityFilter extends AbstractGaussianFeatureFilter {

    	private transient Mat kernel = opencv_imgproc.getStructuringElement(
    			opencv_imgproc.MORPH_RECT, new Size(3, 3));
    	
    	private boolean highPeaks;
    	private int radius;
    	private transient Mat sumFilter;
    	
		public PeakDensityFilter(double sigma, int radius, boolean highPeaks) {
			super(sigma);
			this.radius = radius;
			this.sumFilter = getSumFilter(radius);
			this.highPeaks = highPeaks;
		}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matTemp = new Mat();
			var matGaussian2 = new Mat();
			gaussianFilter(matInput, getSigma(), matGaussian2);
			if (highPeaks)
				opencv_imgproc.dilate(matGaussian2, matTemp, kernel);
			else
				opencv_imgproc.erode(matGaussian2, matTemp, kernel);
			
			opencv_core.subtractPut(matTemp, matGaussian2);
			matTemp.put(opencv_core.abs(matTemp));
			matTemp.put(opencv_core.lessThan(matTemp, 1e-6));
//			matTemp.put(opencv_core.equals(matTemp, matGaussian));
			
			opencv_imgproc.filter2D(matTemp, matTemp, opencv_core.CV_32F, sumFilter);
			
			matGaussian2.release();
			output.add(matTemp);
		}

		@Override
		public String getName() {
			if (highPeaks)
				return "High peak density" + sigmaString() + " (radius=" + radius + ")";
			else
				return "Low peak density" + sigmaString() + " (radius=" + radius + ")";
		}
    	
    }
    
    
    
    public static class MedianFeatureFilter extends FeatureFilter {
    	
    	private int size;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public MedianFeatureFilter(final int size) {
    		this.size = size;
    	}

		@Override
		public String getName() {
			return "Median filter (" + size + "x" + size + ")";
		}

		@Override
		public int getPadding() {
			return size;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.medianBlur(matInput, matOutput, size);
			output.add(matOutput);
		}
    	
    }
    
    
    public static class MorphFilter extends FeatureFilter {
    	
    	private final int radius;
    	private final int op;
    	
    	private transient String opName;
    	private transient Mat kernel;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public MorphFilter(final int op, final int radius) {
    		this.op = op;
    		this.radius = radius;
    	}
    	
    	String getOpName() {
    		if (opName == null) {
    			switch (op) {
    			case opencv_imgproc.MORPH_BLACKHAT:
    				return "Morphological blackhat";
    			case opencv_imgproc.MORPH_CLOSE:
    				return "Morphological closing";
    			case opencv_imgproc.MORPH_DILATE:
    				return "Morphological dilation";
    			case opencv_imgproc.MORPH_ERODE:
    				return "Morphological erosion";
    			case opencv_imgproc.MORPH_GRADIENT:
    				return "Morphological gradient";
    			case opencv_imgproc.MORPH_HITMISS:
    				return "Morphological hit-miss";
    			case opencv_imgproc.MORPH_OPEN:
    				return "Morphological opening";
    			case opencv_imgproc.MORPH_TOPHAT:
    				return "Morphological tophat";
    			default:
    				return "Unknown morphological filter (" + op + ")";
    			}
    		}
    		return opName;
    	}

		@Override
		public String getName() {
			return getOpName() + " (radius=" + radius + ")";
		}

		@Override
		public int getPadding() {
			return radius + 1;
		}
		
		Mat getKernel() {
			if (kernel == null) {
				kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(radius*2+1, radius*2+1));
			}
			return kernel;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.morphologyEx(matInput, matOutput, op, getKernel());
			output.add(matOutput);
		}
    	
    }
    

    public static abstract class AbstractGaussianFeatureFilter extends FeatureFilter {
    	
    	private double sigma;
    	
    	AbstractGaussianFeatureFilter(final double sigma) {
    		this.sigma = sigma;
    	}
    	
        String sigmaString() {
        	return " (\u03C3="+ GeneralTools.formatNumber(sigma, 1) + ")";
//        	return " (sigma=" + GeneralTools.formatNumber(sigma, 1) + ")";
        }
    	
    	public double getSigma() {
    		return sigma;
    	}
    	
    	public int getPadding() {
    		return (int)Math.ceil(sigma * 4);
    	}
    	
    	public void calculate(Mat matInput, List<Mat> output) {
    		var matGaussian = new Mat();
    		gaussianFilter(matInput, sigma, matGaussian);
    		calculate(matInput, matGaussian, output);
    		matGaussian.release();
    	}
    	
    	/**
    	 * Alternative calculate method, suitable whenever the Gaussian filtering has already been 
    	 * precomputed (so that it is not necessary to do this again).
    	 * 
    	 * @param matInput
    	 * @param matGaussian
    	 * @param output
    	 */
    	public abstract void calculate(Mat matInput, Mat matGaussian, List<Mat> output);
    	
    	@Override
    	public String toString() {
    		return getName();
    	}
    	
    }
    
    public static class GaussianFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public GaussianFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	public String getName() {
    		return "Gaussian" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			output.add(matGaussian.clone());
		}    	
    	
    }
    
    public static class SobelFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public SobelFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	public String getName() {
    		return "Gradient magnitude" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matOutput = new Mat();
			var matTemp = new Mat();
			opencv_imgproc.Sobel(matGaussian, matOutput, -1, 1, 0);
			opencv_imgproc.Sobel(matGaussian, matTemp, -1, 0, 1);
			opencv_core.magnitude(matOutput, matTemp, matOutput);
			output.add(matOutput);
			matTemp.release();
		}    	
    	
    }
    
    public static class LoGFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public LoGFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	public String getName() {
    		return "LoG" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.Laplacian(matGaussian, matOutput, -1);
			output.add(matOutput);
		}    	
    	
    }
    
    /**
     * See http://bigwww.epfl.ch/publications/puespoeki1603.html
     * 
     */
    public static class CoherenceFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public CoherenceFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	public String getName() {
    		return "Coherence" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matDX = new Mat();
			var matDY = new Mat();
			opencv_imgproc.Sobel(matInput, matDX, -1, 1, 0);
			opencv_imgproc.Sobel(matInput, matDY, -1, 0, 1);
			
			var matDXY = new Mat();
			opencv_core.multiply(matDX, matDY, matDXY);
			opencv_core.multiply(matDX, matDX, matDX);
			opencv_core.multiply(matDY, matDY, matDY);
			
			double sigma = getSigma();
			gaussianFilter(matDX, sigma, matDX);
			gaussianFilter(matDY, sigma, matDY);
			gaussianFilter(matDXY, sigma, matDXY);
			
			FloatIndexer idxDX = matDX.createIndexer();
			FloatIndexer idxDY = matDY.createIndexer();
			FloatIndexer idxDXY = matDXY.createIndexer();

			// Reuse one mat for the output
			var matOutput = matDXY;
			FloatIndexer idxOutput = idxDXY;

			long cols = matOutput.cols();
			long rows = matOutput.rows();
			for (long y = 0; y < rows; y++) {
				for (long x = 0; x < cols; x++) {
					float fxx = idxDX.get(y, x);
					float fyy = idxDY.get(y, x);
					float fxy = idxDXY.get(y, x);
					double coherence = Math.sqrt(
							(fxx - fyy) * (fxx - fyy) + 4 * fxy * fxy
							) / (fxx + fyy);
					idxOutput.put(y, x, (float)coherence);
				}
			}
			output.add(matOutput);
			
			idxDX.release();
			idxDY.release();
			idxDXY.release();
			
			matDX.release();		
			matDY.release();		
		}
		
    	
    }
    
    
    


}