package qupath.lib.classifiers

import ij.CompositeImage
import ij.ImagePlus
import ij.ImageStack
import ij.measure.Calibration
import ij.process.ByteProcessor
import ij.process.FloatProcessor
import ij.process.ShortProcessor
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.GridPane
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import javafx.collections.ObservableList
import org.bytedeco.javacpp.indexer.ByteIndexer
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.UShortIndexer
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_dnn
import org.bytedeco.javacpp.opencv_ml
import org.bytedeco.javacpp.opencv_ml.StatModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.imagej.helpers.IJTools
import qupath.imagej.images.servers.ImagePlusServerBuilder
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.commands.interfaces.PathCommand
import qupath.lib.gui.helpers.ColorToolsFX
import qupath.lib.gui.helpers.DisplayHelpers
import qupath.lib.gui.viewer.QuPathViewer
import qupath.lib.gui.viewer.QuPathViewerListener
import qupath.lib.images.ImageData
import qupath.lib.objects.PathObject
import qupath.lib.objects.classes.PathClass
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.interfaces.ROI
import qupath.opencv.processing.OpenCVTools

import java.awt.Shape
import java.awt.image.BufferedImage

class PixelClassifierGUI implements PathCommand, QuPathViewerListener, PathObjectHierarchyListener {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierGUI.class)

    final static enum ClassificationResolution {
        VERY_HIGH, HIGH, MODERATE, LOW, VERY_LOW;

        public double getMicronsPerPixel() {
            switch(this) {
                case VERY_HIGH:
                    return 1.0
                case HIGH:
                    return 2.0
                case MODERATE:
                    return 4.0
                case LOW:
                    return 8.0
                case VERY_LOW:
                    return 16.0
            }
        }

        public String toString() {
            switch(this) {
                case VERY_HIGH:
                    return String.format('High (%.0f mpp)', getMicronsPerPixel())
                case HIGH:
                    return String.format('High (%.0f mpp)', getMicronsPerPixel())
                case MODERATE:
                    return String.format('Moderate (%.0f mpp)', getMicronsPerPixel())
                case LOW:
                    return String.format('Low (%.0f mpp)', getMicronsPerPixel())
                case VERY_LOW:
                    return String.format('Very low (%.0f mpp)', getMicronsPerPixel())
            }
        }
    }

    private static PixelClassifierGUI classifierGUI;

    private PixelClassifierHelper helper
    private Stage stage
    private StatModel model

    private QuPathViewer viewer

    private PixelClassificationOverlay overlay
    private PixelClassifier classifier

    private SimpleObjectProperty<ClassificationResolution> selectedResolution = new SimpleObjectProperty<>()
    private SimpleObjectProperty<OpenCVFeatureCalculator> selectedFeatureCalculator = new SimpleObjectProperty<>()
    private BooleanProperty autoUpdate = new SimpleBooleanProperty()

    private ObservableList<PathClass> classificationList = FXCollections.observableArrayList()

    private PixelClassifierGUI(final QuPathViewer viewer) {
        this.viewer = viewer
    }

    public synchronized static PixelClassifierGUI getInstance() {
        if (classifierGUI == null) {
            classifierGUI = new PixelClassifierGUI(QuPathGUI.getInstance().getViewer());

        }
        return classifierGUI
    }

    @Override
    public void run() {
        getInstance().show()
    }


    private void createGUI() {
        stage = new Stage()
        stage.setTitle("Pixel classifier")
        stage.initOwner(QuPathGUI.getInstance().getStage())
        stage.setOnCloseRequest( { e ->
            destroy()
        })


        def listClassifications = new ListView<PathClass>(classificationList)
        listClassifications.setCellFactory {new ClassificationCell()};
        listClassifications.setPrefHeight(200)


        viewer.addViewerListener(this)
        def imageData = viewer.getImageData()
        if (imageData != null)
            imageData.getHierarchy().addPathObjectListener(this)

        // Make it possible to choose OpenCVFeatureCalculator
        ComboBox<OpenCVFeatureCalculator> comboFeatures = new ComboBox<>()
        comboFeatures.getItems().addAll(
                new SmoothedOpenCVFeatureCalculator(),
                new SmoothedOpenCVFeatureCalculator(2.0),
                new SmoothedOpenCVFeatureCalculator(4.0),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 1),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 2),
                new BasicMultiscaleOpenCVFeatureCalculator(1.0, 3),
                new BasicMultiscaleOpenCVFeatureCalculator(2.0, 3)
        )

        // Load more models if we can find any...
        def project = QuPathGUI.getInstance().getProject()
        if (project != null) {
            def dirModels = new File(project.getBaseDirectory(), 'models')
            if (dirModels.exists()) {
                for (File f : dirModels.listFiles()) {
                    if (f.isFile() && !f.isHidden()) {
                        try {
                            def model = opencv_dnn.readNetFromTensorflow(f.getAbsolutePath())
                            def featuresDNN = new OpenCVFeatureCalculatorDNN(model, f.getName())
                            logger.info("Loaded model from {}", f.getAbsolutePath())
                            comboFeatures.getItems().add(featuresDNN)
                        } catch (Exception e) {
                            logger.warn("Unable to load model from {}", f.getAbsolutePath())
                        }
                    }
                }
            }
        }


        comboFeatures.getSelectionModel().select(0)
        comboFeatures.setMaxWidth(Double.MAX_VALUE)
        selectedFeatureCalculator.bind(comboFeatures.getSelectionModel().selectedItemProperty())
        selectedFeatureCalculator.addListener({v, o, n ->
            if (autoUpdate.get())
                updateClassification()
        } as ChangeListener)
        Label labelFeatures = new Label("Features")
        labelFeatures.setLabelFor(comboFeatures)

        // Show features for current selection
        Button btnShowFeatures = new Button("Show")
        btnShowFeatures.setOnAction({e -> showFeatures()})

        // Make it possible to choose resolution
        ComboBox<ClassificationResolution> comboResolution = new ComboBox<>()
        comboResolution.getItems().addAll(ClassificationResolution.values())
        comboResolution.getSelectionModel().select(ClassificationResolution.MODERATE)
        comboResolution.setMaxWidth(Double.MAX_VALUE)
        selectedResolution.bind(comboResolution.getSelectionModel().selectedItemProperty())
        selectedResolution.addListener({v, o, n ->
            if (autoUpdate.get())
            updateClassification()
        } as ChangeListener)
        Label labelResolution = new Label("Resolution")
        labelResolution.setLabelFor(comboResolution)

        // Possibly auto-update
        def cbAutoUpdate = new CheckBox("Auto update")
        autoUpdate.bindBidirectional(cbAutoUpdate.selectedProperty())
        autoUpdate.addListener({v, o, n ->
            if (n)
                updateClassification()
        } as ChangeListener)

        // Make it possible to choose requested pixel size, if auto-update is not selected
        def btnUpdate = new Button("Update")
        btnUpdate.disableProperty().bind(autoUpdate)
        btnUpdate.setMaxWidth(Double.MAX_VALUE)
        btnUpdate.setOnAction { e -> updateClassification() }


        def pane = new GridPane()
        pane.setMaxWidth(Double.MAX_VALUE)
        pane.setMaxHeight(Double.MAX_VALUE)
        pane.setHgap(5.0)
        pane.setVgap(5.0)
        pane.setPadding(new Insets(5))
        int row = 0
        pane.add(labelFeatures, 0, row, 1, 1)
        pane.add(comboFeatures, 1, row, 1, 1)
        pane.add(btnShowFeatures, 2, row++, 1, 1)
        pane.add(labelResolution, 0, row, 1, 1)
        pane.add(comboResolution, 1, row++, 2, 1)
        pane.add(cbAutoUpdate, 0, row++, 3, 1)
        pane.add(listClassifications, 0, row++, 3, 1)
        pane.add(btnUpdate, 0, row++, 3, 1)


        stage.setScene(new Scene(pane))
    }

    def void showFeatures() {
        def imageData = viewer.getImageData()
        if (imageData == null) {
            DisplayHelpers.showErrorMessage("Show features", "No image available!")
            return
        }
        def roi = viewer.getCurrentROI()
        if (roi == null) {
            DisplayHelpers.showErrorMessage("Show features", "No ROI selected!")
            return
        }
        def calculator = selectedFeatureCalculator.get()
        if (calculator == null) {
            DisplayHelpers.showErrorMessage("Show features", "No feature calculator available!")
            return
        }
        def server = imageData.getServer()
        def downsample = selectedResolution.get().getMicronsPerPixel() / server.getAveragedPixelSizeMicrons()
        def request = RegionRequest.createInstance(server.getPath(), downsample, roi)
        def img = imageData.getServer().readBufferedImage(request)
        def mat = OpenCVTools.imageToMat(img)
        def matFeatures = calculator.calculateFeatures(mat)
        def names = calculator.getLastFeatureNames()
        def imp = matToImagePlus(matFeatures,
                String.format("Features: (%.2f, %d, %d, %d, %d)",
                request.getDownsample(), request.getX(), request.getY(), request.getWidth(), request.getHeight()))
        def impComp = new CompositeImage(imp, CompositeImage.GRAYSCALE)
        impComp.setDimensions(impComp.getStackSize(), 1, 1)
        for (int c = 1; c <= impComp.getStackSize(); c++) {
            impComp.setC(c)
            if (names)
                impComp.getStack().setSliceLabel(names[c-1], c)
            impComp.resetDisplayRange()
        }
        IJTools.calibrateImagePlus(impComp, request, server);

        impComp.setC(1)
        impComp.show()
    }

    public void show() {
        if (stage == null) {
            createGUI()
        }
        stage.show()
    }


    void updateClassification() {
        
        double requestedPixelSizeMicrons = selectedResolution.get().getMicronsPerPixel()

        if (helper == null)
            helper = new PixelClassifierHelper(viewer.getImageData(), selectedFeatureCalculator.get(), requestedPixelSizeMicrons)
        else {
            helper.setImageData(viewer.getImageData())
            helper.setFeatureCalculator(selectedFeatureCalculator.get())
            helper.setRequestedPixelSizeMicrons(requestedPixelSizeMicrons)
        }

        helper.updateTrainingData()
        def trainData = helper.getTrainData()
        double[] means = helper.getLastTrainingMeans()
        double[] scales = helper.getLastTrainingScales()
        if (trainData == null) {
            logger.error("Not enough annotations to train a classifier!")
            classificationList.clear()
            return
        }
        def channels = helper.getChannels()
        def nClasses = channels.size()
        def nFeatures = trainData.getNVars()

        model = opencv_ml.ANN_MLP.create() as opencv_ml.ANN_MLP

        def layersArray = [
                nFeatures,
                nClasses * 16.0,
                nClasses * 8.0,
                nClasses * 4.0,
                nClasses
        ] as double[]

        def layers = new opencv_core.Mat(layersArray.length, 1, opencv_core.CV_64F)
        def indexer = layers.createIndexer() as DoubleIndexer
        for (int i = 0; i < layersArray.length; i++) {
            indexer.put(i, layersArray[i])
        }
        indexer.release()
        model.setLayerSizes(layers)
        model.setActivationFunction(opencv_ml.ANN_MLP.SIGMOID_SYM, 1, 1)

        trainData.shuffleTrainTest()
        model.train(trainData, opencv_ml.VAR_NUMERICAL)

        def metadata = new PixelClassifierMetadata(
                inputPixelSizeMicrons: helper.getRequestedPixelSizeMicrons(),
                inputChannelMeans: means,
                inputChannelScales: scales,
                channels: channels
        )
        System.err.println(channels.name)
        System.err.println(channels.color)
        System.err.println(channels)

        classifier = new OpenCVPixelClassifier(model, helper.getFeatureCalculator(), metadata)

        classificationList.setAll(helper.getLastTrainingROIs().keySet())

        replaceOverlay(new PixelClassificationOverlay(viewer, classifier))
    }

    /**
     * Replace the overlay - making sure to do this on the application thread
     *
     * @param newOverlay
     */
    void replaceOverlay(PixelClassificationOverlay newOverlay) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater {replaceOverlay(newOverlay)}
            return
        }
        if (overlay != null)
            viewer.getCustomOverlayLayers().remove(overlay)
        overlay = newOverlay
        viewer.getCustomOverlayLayers().add(overlay)
    }



    public void destroy() {
        helper.setImageData(null)
        classifierGUI = null
        if (overlay != null) {
            viewer.getCustomOverlayLayers().remove(overlay)
            overlay = null
        }
        if (stage != null && stage.isShowing())
            stage.close()
    }


    @Override
    void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging() || !autoUpdate.get())
            return
        // Only update the classification if necessary
        Map<PathClass, Collection<ROI>> map = PixelClassifierHelper.getAnnotatedROIs(event.getHierarchy())
        if (helper == null || map != helper.getLastTrainingROIs()) {
            updateClassification()
        }
    }

    @Override
    void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        if (helper != null)
            helper.setImageData(imageDataNew)
        if (imageDataOld != null)
            imageDataOld.getHierarchy().removePathObjectListener(this)
        if (imageDataNew != null)
            imageDataNew.getHierarchy().addPathObjectListener(this)
    }

    @Override
    void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

    @Override
    void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

    @Override
    void viewerClosed(QuPathViewer viewer) {
        if (viewer.getImageData() != null)
            viewer.getImageData().getHierarchy().removePathObjectListener(this)
        destroy()
    }



    static matToImagePlus(opencv_core.Mat mat, String title="Results") {
        if (mat.channels() == 1) {
            return new ImagePlus(title, matToImageProcessor(mat))
        }
        def matvec = new opencv_core.MatVector()
        opencv_core.split(mat, matvec)
        ImageStack stack = new ImageStack(mat.cols(), mat.rows())
        for (int s = 0; s < matvec.size(); s++) {
            stack.addSlice(matToImageProcessor(matvec.get(s)))
        }
        return new ImagePlus(title, stack)
    }

    static matToImageProcessor(opencv_core.Mat mat) {
        assert mat.channels() == 1
        int w = mat.cols()
        int h = mat.rows()
        if (mat.depth() == opencv_core.CV_32F) {
            FloatIndexer indexer = mat.createIndexer()
            def pixels = new float[w*h]
            indexer.get(0L, pixels)
            return new FloatProcessor(w, h, pixels)
        } else if (mat.depth() == opencv_core.CV_8U) {
            ByteIndexer indexer = mat.createIndexer()
            def pixels = new byte[w*h]
            indexer.get(0L, pixels)
            return new ByteProcessor(w, h, pixels)
        } else if (mat.depth() == opencv_core.CV_16U) {
            UShortIndexer indexer = mat.createIndexer()
            def pixels = new int[w*h]
            indexer.get(0L, pixels)
            return new ShortProcessor(w, h, pixels as short[]) // TODO: Test!
        } else {
            def mat2 = new opencv_core.Mat()
            mat.convertTo(mat2, opencv_core.CV_32F)
            def ip = matToImageProcessor(mat2)
            mat2.release()
            return ip
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

                def map = helper?.getLastTrainingROIs()
                Integer n
                if (map != null) {
                    n = map.get(value)?.size()
                }
                if (n != null)
                    setText(value.getName() + ' (' + n + ')')
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


}
