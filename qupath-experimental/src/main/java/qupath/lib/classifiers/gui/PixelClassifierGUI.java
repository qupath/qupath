package qupath.lib.classifiers.gui;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
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
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UShortIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_dnn;
import org.bytedeco.javacpp.opencv_ml;
import org.bytedeco.javacpp.opencv_ml.ANN_MLP;
import org.bytedeco.javacpp.opencv_ml.StatModel;
import org.bytedeco.javacpp.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.helpers.IJTools;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.classifiers.pixel.features.BasicMultiscaleOpenCVFeatureCalculator;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculatorDNN;
import qupath.lib.classifiers.pixel.features.SmoothedOpenCVFeatureCalculator;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User interface for interacting with pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierGUI implements PathCommand, QuPathViewerListener, PathObjectHierarchyListener {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierGUI.class);

    public static enum ClassificationResolution {
    	MAX_RESOLUTION, VERY_HIGH, HIGH, MODERATE, LOW, VERY_LOW;

        public double getMicronsPerPixel() {
            switch(this) {
	            case MAX_RESOLUTION:
	                return -1;
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
    private StatModel model;

    private QuPathViewer viewer;

    private PixelClassificationOverlay overlay;
    private PixelClassifier classifier;

    private SimpleObjectProperty<ClassificationResolution> selectedResolution = new SimpleObjectProperty<>();
    private SimpleObjectProperty<OpenCVFeatureCalculator> selectedFeatureCalculator = new SimpleObjectProperty<>();
    private BooleanProperty autoUpdate = new SimpleBooleanProperty();
    
    private ObservableList<ClassifierModelBuilder> availableClassifierBuilders = FXCollections.observableArrayList(
    		new RTreesClassifierBuilder(),
//    		new NormalBayesClassifierBuilder(),
    		new ANNClassifierBuilder()
    		);
    private SimpleObjectProperty<ClassifierModelBuilder> classifierBuilder = new SimpleObjectProperty<>(availableClassifierBuilders.get(0));

    private ObservableList<PathClass> classificationList = FXCollections.observableArrayList();

    private PixelClassifierGUI(final QuPathViewer viewer) {
        this.viewer = viewer;
    }

    public synchronized static PixelClassifierGUI getInstance() {
        if (classifierGUI == null) {
            classifierGUI = new PixelClassifierGUI(QuPathGUI.getInstance().getViewer());

        }
        return classifierGUI;
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

        // Make it possible to choose OpenCVFeatureCalculator
        ComboBox<OpenCVFeatureCalculator> comboFeatures = new ComboBox<>();
        comboFeatures.getItems().addAll(
                new SmoothedOpenCVFeatureCalculator(1.0),
                new SmoothedOpenCVFeatureCalculator(2.0),
                new SmoothedOpenCVFeatureCalculator(4.0),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 1, false),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 2, false),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 3, false),
                new BasicMultiscaleOpenCVFeatureCalculator(2.0, 3, false)
        );

        // Load more models if we can find any...
        Project<BufferedImage> project = QuPathGUI.getInstance().getProject();
        if (project != null) {
        	File dirModels = new File(project.getBaseDirectory(), "models");
            if (dirModels.exists()) {
                for (File f : dirModels.listFiles()) {
                    if (f.isFile() && !f.isHidden()) {
                        try {
                        	opencv_dnn.Net model = opencv_dnn.readNetFromTensorflow(f.getAbsolutePath());
                        	// TODO: HANDLE UNKNOWN MEAN AND SCALE!
                        	logger.warn("Creating DNN feature calculator - but don't know mean or scale to apply!");
                        	OpenCVFeatureCalculatorDNN featuresDNN = new OpenCVFeatureCalculatorDNN(model, f.getName(), 0, 1.0/255.0);
                            logger.info("Loaded model from {}", f.getAbsolutePath());
                            comboFeatures.getItems().add(featuresDNN);
                        } catch (Exception e) {
                            logger.warn("Unable to load model from {}", f.getAbsolutePath());
                        }
                    }
                }
            }
        }


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
        ComboBox<ClassifierModelBuilder> comboClassifierType = new ComboBox<>(availableClassifierBuilders);
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
        btnUpdate.setOnAction( e -> updateClassification());


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
        pane.add(comboResolution, 1, row++, 2, 1);
        pane.add(labelClassifierType, 0, row, 1, 1);
        pane.add(comboClassifierType, 1, row++, 2, 1);
        pane.add(cbAutoUpdate, 0, row++, 3, 1);
        pane.add(listClassifications, 0, row++, 3, 1);
        pane.add(btnUpdate, 0, row++, 3, 1);


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
        RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi);
        
//        downsample = 0.5 / server.getAveragedPixelSizeMicrons();
//        request = RegionRequest.createInstance(server.getPath(), downsample,
//        		(int)roi.getBoundsX(),
//        		(int)roi.getBoundsY(),
//        		(int)Math.round(256*downsample),
//  				(int)Math.round(256*downsample));
        
        BufferedImage img = imageData.getServer().readBufferedImage(request);        
//        new ImagePlus("Extracted", img).show();
        Mat mat = OpenCVTools.imageToMat(img);
        Mat matFeatures = calculator.calculateFeatures(mat);
        List<String> names = calculator.getLastFeatureNames();
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
        
        double requestedPixelSizeMicrons = selectedResolution.get().getMicronsPerPixel();
        if (requestedPixelSizeMicrons < 0 && viewer.getServer() != null)
        	requestedPixelSizeMicrons = viewer.getServer().getAveragedPixelSizeMicrons();
        
        
        ClassifierModelBuilder modelBuilder = classifierBuilder.get();
        
        if (helper == null)
            helper = new PixelClassifierHelper(
            		viewer.getImageData(), selectedFeatureCalculator.get(), requestedPixelSizeMicrons, opencv_ml.VAR_CATEGORICAL);
        else {
            helper.setImageData(viewer.getImageData());
            helper.setFeatureCalculator(selectedFeatureCalculator.get());
            helper.setRequestedPixelSizeMicrons(requestedPixelSizeMicrons);
        }
        // Set the var type according to the kind of model we have
        helper.setVarType(modelBuilder.getVarType());

        helper.updateTrainingData();
        TrainData trainData = helper.getTrainData();
        double[] means = helper.getLastTrainingMeans();
        double[] scales = helper.getLastTrainingScales();
        if (trainData == null) {
            logger.error("Not enough annotations to train a classifier!");
            classificationList.clear();
            return;
        }
        List<PixelClassifierOutputChannel> channels = helper.getChannels();
        int nClasses = channels.size();
        int nFeatures = trainData.getNVars();
        
        model = modelBuilder.createNewClassifier(nFeatures, nClasses);
        
        trainData.shuffleTrainTest();
        model.train(trainData, modelBuilder.getVarType());

        PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
        		.inputPixelSizeMicrons(helper.getRequestedPixelSizeMicrons())
        		.inputChannelMeans(means)
        		.inputChannelScales(scales)
        		.channels(channels)
        		.build();

        classifier = new OpenCVPixelClassifier(model, helper.getFeatureCalculator(), metadata);

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
        if (overlay != null)
            viewer.getCustomOverlayLayers().remove(overlay);
        overlay = newOverlay;
        if (overlay != null)
        	viewer.getCustomOverlayLayers().add(overlay);
    }



    public void destroy() {
    	if (helper != null)
    		helper.setImageData(null);
        classifierGUI = null;
        if (overlay != null) {
            viewer.getCustomOverlayLayers().remove(overlay);
            overlay = null;
        }
        if (stage != null && stage.isShowing())
            stage.close();
    }


    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging() || !autoUpdate.get())
            return;
        // Only update the classification if necessary
        Map<PathClass, Collection<ROI>> map = PixelClassifierHelper.getAnnotatedROIs(event.getHierarchy());
        if (helper == null || map != helper.getLastTrainingROIs()) {
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



    static ImagePlus matToImagePlus(opencv_core.Mat mat, String title) {
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

    static ImageProcessor matToImageProcessor(Mat mat) {
        assert mat.channels() == 1;
        int w = mat.cols();
        int h = mat.rows();
        if (mat.depth() == opencv_core.CV_32F) {
            FloatIndexer indexer = mat.createIndexer();
            float[] pixels = new float[w*h];
            indexer.get(0L, pixels);
            return new FloatProcessor(w, h, pixels);
        } else if (mat.depth() == opencv_core.CV_8U) {
            ByteIndexer indexer = mat.createIndexer();
            byte[] pixels = new byte[w*h];
            indexer.get(0L, pixels);
            return new ByteProcessor(w, h, pixels);
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
    
    
    static interface ClassifierModelBuilder {
    	
    	/**
    	 * Create a new {@code StatModel} to use as a classifier.
    	 * <p>
    	 * This may optionally use the number of features and number of output classes to 
    	 * initialize itself suitably.
    	 * 
    	 * @param nFeatures
    	 * @param nClasses
    	 * @return
    	 */
    	public StatModel createNewClassifier(final int nFeatures, final int nClasses);
    	
    	/**
    	 * Either opencv_ml.VAR_CATEGORICAL or opencv_ml.VAR_NUMERIC, depending on whether
    	 * this classifier outputs classifications directly or numerical predictions (here, assumed to be probabilities).
    	 * 
    	 * @return
    	 */
    	public int getVarType();
    	
    }
    
    static class RTreesClassifierBuilder implements ClassifierModelBuilder {
    	
    	public StatModel createNewClassifier(final int nFeatures, final int nClasses) {
    		return opencv_ml.RTrees.create();
    	}
    	
    	public int getVarType() {
    		return opencv_ml.VAR_CATEGORICAL;
    	}
    	
    	public String toString() {
    		return "Random Trees";
    	}
    	
    }
    
//    static class NormalBayesClassifierBuilder implements ClassifierModelBuilder {
//    	
//    	public StatModel createNewClassifier(final int nFeatures, final int nClasses) {
//    		return opencv_ml.NormalBayesClassifier.create();
//    	}
//    	
//    	public int getVarType() {
//    		return opencv_ml.VAR_CATEGORICAL;
//    	}
//    	
//    	@Override
//    	public String toString() {
//    		return "Normal Bayes";
//    	}
//    	
//    }
    
    static class ANNClassifierBuilder implements ClassifierModelBuilder {
    	
    	public StatModel createNewClassifier(final int nFeatures, final int nClasses) {
    		ANN_MLP model = opencv_ml.ANN_MLP.create();
    		
    		double[] layersArray = new double[] {
                    nFeatures,
                    nFeatures * 2.0,
                    nClasses * 8.0,
                    nClasses * 4.0,
                    nClasses
            };

            Mat layers = new Mat(layersArray.length, 1, opencv_core.CV_64F);
            DoubleIndexer indexer = layers.createIndexer();
            for (int i = 0; i < layersArray.length; i++) {
                indexer.put(i, layersArray[i]);
            }
            indexer.release();
        	((opencv_ml.ANN_MLP)model).setLayerSizes(layers);
        	((opencv_ml.ANN_MLP)model).setActivationFunction(opencv_ml.ANN_MLP.SIGMOID_SYM, 1, 1);
    		
    		return model;
    	}
    	
    	public int getVarType() {
    		return opencv_ml.VAR_NUMERICAL;
    	}
    	
    	@Override
    	public String toString() {
    		return "Artificial Neural Network (ANN)";
    	}

    	
    }


}