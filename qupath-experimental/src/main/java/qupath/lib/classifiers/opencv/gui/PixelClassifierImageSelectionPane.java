package qupath.lib.classifiers.opencv.gui;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.IntBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import ij.CompositeImage;
import ij.io.FileSaver;
import ij.io.Opener;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.PieChart.Data;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import jfxtras.scene.layout.HBox;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.images.servers.ImageJServer;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.lib.classifiers.gui.PixelClassificationOverlay;
import qupath.lib.classifiers.gui.PixelClassifierStatic;
import qupath.lib.classifiers.gui.PixelClassifierHelper;
import qupath.lib.classifiers.opencv.OpenCVClassifiers;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.OpenCVStatModel;
import qupath.lib.classifiers.opencv.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.opencv.pixel.features.BasicFeatureCalculator;
import qupath.lib.classifiers.opencv.pixel.features.FeatureFilter;
import qupath.lib.classifiers.opencv.pixel.features.FeatureFilters;
import qupath.lib.classifiers.opencv.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.GridPaneTools;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;


public class PixelClassifierImageSelectionPane {
	
	private final static Logger logger = LoggerFactory.getLogger(PixelClassifierImageSelectionPane.class);
	
	static enum ClassificationRegion {
		ENTIRE_IMAGE,
		ANNOTATIONS_ONLY;
		
		@Override
		public String toString() {
			switch(this) {
			case ANNOTATIONS_ONLY:
				return "Annotations only";
			case ENTIRE_IMAGE:
				return "Entire image";
			default:
				return "Unknown";
			}
		}
	}
	
	
	private static ObservableList<FeatureCalculatorBuilder> defaultFeatureCalculatorBuilders = FXCollections.observableArrayList();
	
	
	private QuPathViewer viewer;
	private GridPane pane;
	
	private ObservableList<ImageResolution> resolutions = FXCollections.observableArrayList();
	private ComboBox<ImageResolution> comboResolutions = new ComboBox<>(resolutions);
	private ReadOnlyObjectProperty<ImageResolution> selectedResolution;
	
	private MiniViewerCommand.MiniViewerManager miniViewer;
	
	private BooleanProperty livePrediction = new SimpleBooleanProperty(false);
	
	private ObservableBooleanValue classificationComplete = new SimpleBooleanProperty(false);
	private ReadOnlyObjectProperty<OpenCVStatModel> selectedClassifier;

	private ReadOnlyObjectProperty<ClassificationRegion> selectedRegion;
	
	private ReadOnlyObjectProperty<FeatureCalculatorBuilder> selectedFeatureCalculatorBuilder;

	private ReadOnlyObjectProperty<ImageServerMetadata.ChannelType> selectedOutputType;
	
	private StringProperty cursorLocation = new SimpleStringProperty();
	
	private ClassificationPieChart pieChart;
	
	private boolean wasApplied = false;

	private HierarchyListener hierarchyListener = new HierarchyListener();

	private ChangeListener<ImageData<BufferedImage>> imageDataListener = new ChangeListener<ImageData<BufferedImage>>() {

		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
				ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
			if (oldValue != null)
				oldValue.getHierarchy().removePathObjectListener(hierarchyListener);
			if (newValue != null)
				newValue.getHierarchy().addPathObjectListener(hierarchyListener);
			updateTitle();
			updateAvailableResolutions();
		}
		
	};
	
	private Stage stage;

	
	public void initialize(final QuPathViewer viewer) {
		
		int row = 0;
		
		// Classifier
		pane = new GridPane();

		var labelClassifier = new Label("Classifier");
		var comboClassifier = new ComboBox<OpenCVStatModel>();
		labelClassifier.setLabelFor(comboClassifier);
		
		selectedClassifier = comboClassifier.getSelectionModel().selectedItemProperty();
		selectedClassifier.addListener((v, o, n) -> updateClassifier());
		var btnEditClassifier = new Button("Edit");
		btnEditClassifier.setOnAction(e -> editClassifierParameters());
		btnEditClassifier.disableProperty().bind(selectedClassifier.isNull());
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Choose classifier type (RTrees is generally a good default)",
				labelClassifier, comboClassifier, comboClassifier, btnEditClassifier);
		
		
		// Image resolution
		var labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		var btnResolution = new Button("Add");
		btnResolution.setOnAction(e -> addResolution());
		selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Choose the base image resolution based upon required detail in the classification (see preview on the right)",
				labelResolution, comboResolutions, comboResolutions, btnResolution);
		
		
		// Features
		var labelFeatures = new Label("Features");
		var comboFeatures = new ComboBox<FeatureCalculatorBuilder>();
		comboFeatures.getItems().add(new DefaultFeatureCalculatorBuilder());
		labelFeatures.setLabelFor(comboFeatures);
		selectedFeatureCalculatorBuilder = comboFeatures.getSelectionModel().selectedItemProperty();
		
//		var labelFeaturesSummary = new Label("No features selected");
		var btnShowFeatures = new Button("Show");
		btnShowFeatures.setOnAction(e -> showFeatures());
		
		var btnCustomizeFeatures = new Button("Edit");
		btnCustomizeFeatures.disableProperty().bind(Bindings.createBooleanBinding(() -> {
			var calc = selectedFeatureCalculatorBuilder.get();
			return calc == null || !calc.canCustomize();
		},
				selectedFeatureCalculatorBuilder));
		btnCustomizeFeatures.setOnAction(e -> {
			if (selectedFeatureCalculatorBuilder.get().doCustomize()) {
				updateFeatureCalculator();
			}
		});
		comboFeatures.getItems().addAll(defaultFeatureCalculatorBuilders);
		
		comboFeatures.getSelectionModel().select(0);
		comboFeatures.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateFeatureCalculator());
//		btnCustomizeFeatures.setOnAction(e -> showFeatures());
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Select features for the classifier",
				labelFeatures, comboFeatures, btnCustomizeFeatures, btnShowFeatures);

		
		// Output
		var labelOutput = new Label("Output");
		var comboOutput = new ComboBox<ImageServerMetadata.ChannelType>();
		comboOutput.getItems().addAll(ImageServerMetadata.ChannelType.CLASSIFICATION, ImageServerMetadata.ChannelType.PROBABILITY);
		selectedOutputType = comboOutput.getSelectionModel().selectedItemProperty();
		selectedOutputType.addListener((v, o, n) -> {
			updateClassifier();
		});
		comboOutput.getSelectionModel().clearAndSelect(0);
		var btnShowOutput = new Button("Show");
		btnShowOutput.setOnAction(e -> showOutput());
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Choose whether to output classifications only, or estimated probabilities per class (classifications only takes much less memory)",
				labelOutput, comboOutput, comboOutput, btnShowOutput);
		
		
		// Region
		var labelRegion = new Label("Region");
		var comboRegion = new ComboBox<ClassificationRegion>();
		comboRegion.getItems().addAll(ClassificationRegion.values());
		selectedRegion = comboRegion.getSelectionModel().selectedItemProperty();
		selectedRegion.addListener((v, o, n) -> {
			if (overlay != null)
				overlay.setUseAnnotationMask(n == ClassificationRegion.ANNOTATIONS_ONLY);
		});
		comboRegion.getSelectionModel().clearAndSelect(0);
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Choose whether to apply the classifier to the whole image, or only regions containing annotations",
				labelRegion, comboRegion, comboRegion, comboRegion);

		
		// Live predict
		var btnAdvancedOptions = new Button("Advanced options");
		btnAdvancedOptions.setOnAction(e -> showAdvancedOptions());
		
		var btnLive = new ToggleButton("Live prediction");
		btnLive.selectedProperty().bindBidirectional(livePrediction);
		livePrediction.addListener((v, o, n) -> {
			if (overlay == null) {
				if (n) {
					updateClassifier(n);				
//					viewer.setSuppressPixelLayer(true);
					return;
				}
			} else {
				overlay.setLivePrediction(n);
			}
//			viewer.setSuppressPixelLayer(false);
		});
				
		var panePredict = new HBox(btnAdvancedOptions, btnLive);
		GridPaneTools.setMaxWidth(Double.MAX_VALUE, btnLive, btnAdvancedOptions);
		HBox.setHgrow(btnAdvancedOptions, Priority.ALWAYS);
		HBox.setHgrow(btnLive, Priority.ALWAYS);
		
		
		pane.add(panePredict, 0, row++, pane.getColumnCount(), 1);

		var btnSave = new Button("Save & Apply");
		btnSave.setMaxWidth(Double.MAX_VALUE);
		btnSave.setOnAction(e -> saveAndApply());
		pane.add(btnSave, 0, row++, pane.getColumnCount(), 1);

		
//		addGridRow(pane, row++, 0, btnPredict, btnPredict, btnPredict);

		
		
		pieChart = new ClassificationPieChart();
		
//		var hierarchy = viewer.getHierarchy();
//		Map<PathClass, List<PathObject>> map = hierarchy == null ? Collections.emptyMap() : PathClassificationLabellingHelper.getClassificationMap(hierarchy, false);
		
		var chart = pieChart.getChart();
		chart.setLabelsVisible(false);
		chart.setLegendVisible(true);
		chart.setPrefSize(40, 40);
		chart.setMaxSize(100, 100);
		chart.setLegendSide(Side.RIGHT);
		GridPane.setVgrow(chart, Priority.ALWAYS);
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				null,
//				"View information about the current classifier training",
				chart, chart, chart);
		
		
		// Label showing cursor location
		var labelCursor = new Label();
		labelCursor.textProperty().bindBidirectional(cursorLocation);
		labelCursor.setMaxWidth(Double.MAX_VALUE);
		labelCursor.setAlignment(Pos.CENTER);
		
		GridPaneTools.addGridRow(pane, row++, 0, 
				"Prediction for current cursor location",
				labelCursor, labelCursor, labelCursor);
		
		comboClassifier.getItems().addAll(
				OpenCVClassifiers.wrapStatModel(RTrees.class),
				OpenCVClassifiers.wrapStatModel(KNearest.class),
				OpenCVClassifiers.wrapStatModel(ANN_MLP.class)
				);
		
//		comboClassifier.getItems().addAll(
//				OpenCVClassifiers.wrapStatModel(RTrees.create()),
//				OpenCVClassifiers.wrapStatModel(DTrees.create()),
//				OpenCVClassifiers.wrapStatModel(KNearest.create()),
//				OpenCVClassifiers.wrapStatModel(ANN_MLP.create()),
//				OpenCVClassifiers.wrapStatModel(LogisticRegression.create()),
//				OpenCVClassifiers.wrapStatModel(NormalBayesClassifier.create())
//				);
		comboClassifier.getSelectionModel().clearAndSelect(0);
		
//		comboResolution.setMaxWidth(Double.MAX_VALUE);
//		labelFeaturesSummary.setMaxWidth(Double.MAX_VALUE);
		
		GridPaneTools.setHGrowPriority(Priority.ALWAYS, comboResolutions, comboClassifier, comboFeatures);
		GridPaneTools.setFillWidth(Boolean.TRUE, comboResolutions, comboClassifier, comboFeatures);
		
		
		
		miniViewer = new MiniViewerCommand.MiniViewerManager(viewer, 0);
		var viewerPane = miniViewer.getPane();
		GridPane.setFillWidth(viewerPane, Boolean.TRUE);
		GridPane.setFillHeight(viewerPane, Boolean.TRUE);
		GridPane.setHgrow(viewerPane, Priority.ALWAYS);
		GridPane.setVgrow(viewerPane, Priority.ALWAYS);
		
		updateAvailableResolutions();	
		selectedResolution.addListener((v, o, n) -> {
			updateResolution(n);
			updateClassifier();
		});
		if (!comboResolutions.getItems().isEmpty())
			comboResolutions.getSelectionModel().clearAndSelect(resolutions.size()/2);
		
		pane.setHgap(5);
		pane.setVgap(6);

		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(classificationComplete);
		btnCreateObjects.setOnAction(e -> createObjects());
		
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.disableProperty().bind(classificationComplete);
		btnClassifyObjects.setOnAction(e -> classifyObjects());
		
		GridPaneTools.setMaxWidth(Double.MAX_VALUE, btnCreateObjects, btnClassifyObjects);
		HBox.setHgrow(btnCreateObjects, Priority.ALWAYS);
		HBox.setHgrow(btnClassifyObjects, Priority.ALWAYS);
		var panePostProcess = new HBox(btnCreateObjects, btnClassifyObjects);
				
		pane.add(panePostProcess, 0, row++, pane.getColumnCount(), 1);

		GridPaneTools.setMaxWidth(Double.MAX_VALUE, pane.getChildren().stream().filter(p -> p instanceof Region).toArray(Region[]::new));
		
		var splitPane = new SplitPane(pane, viewerPane);
		pane.setPrefWidth(400);
		pane.setMinHeight(GridPane.USE_PREF_SIZE);
		viewerPane.setPrefSize(400, 400);
		splitPane.setDividerPositions(0.5);
		
		pane.setPadding(new Insets(5));
		
		stage = new Stage();
		stage.setScene(new Scene(splitPane));
		
		stage.initOwner(QuPathGUI.getInstance().getStage());
		
		stage.getScene().getRoot().disableProperty().bind(
				QuPathGUI.getInstance().viewerProperty().isNotEqualTo(viewer)
				);
		
		updateTitle();
		
		updateFeatureCalculator();
		
		stage.show();
		stage.setOnCloseRequest(e -> destroy());
		
		viewer.getView().addEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
		
		viewer.getImageDataProperty().addListener(imageDataListener);
		if (viewer.getImageData() != null)
			viewer.getImageData().getHierarchy().addPathObjectListener(hierarchyListener);
		
	}
	
	/**
	 * Get a list of default feature calculator builders that will be available when 
	 * this pane is opened.
	 * <p>
	 * This provides a mechanism to install additional feature calculators.
	 * <p>
	 * Note that the builder will only be added if it is not already present.
	 * 
	 * @return true if the builder was added, false otherwise.
	 */
	public synchronized static boolean installDefaultFeatureClassificationBuilder(FeatureCalculatorBuilder builder) {
		if (!defaultFeatureCalculatorBuilders.contains(builder)) {
			defaultFeatureCalculatorBuilders.add(builder);
			return true;
		}
		return false;
	}
	
	
	private void updateTitle() {
		if (stage == null)
			return;
//		var imageData = viewer.getImageData();
//		if (imageData == null)
			stage.setTitle("Pixel classifier");
//		else
//			stage.setTitle("Pixel classifier (" + imageData.getServer().getDisplayedImageName() + ")");
	}
	
	private MouseListener mouseListener = new MouseListener();
	
	private OpenCVFeatureCalculator featureCalculator;
	private PixelClassifierHelper helper;

	
	private List<String> resolutionNames = Arrays.asList("Full", "Very high", "High", "Moderate", "Low", "Very low", "Extremely low");
	
	void updateAvailableResolutions() {
		var imageData = viewer.getImageData();
		if (imageData == null) {
			resolutions.clear();
			return;
		}
		var temp = new ArrayList<ImageResolution>();
		double originalDownsample = 1;
		String units = null;
		if (imageData.getServer().hasPixelSizeMicrons()) {
			originalDownsample = imageData.getServer().getAveragedPixelSizeMicrons();
			units = PixelCalibration.MICROMETER;
		}
		int scale = 1;
		
		for (String name : resolutionNames) {
			temp.add(SimpleImageResolution.getInstance(name, originalDownsample * scale, units));
			scale *= 2;
		}
		resolutions.setAll(temp);
	}
	
	
	void updateFeatureCalculator() {
		featureCalculator = selectedFeatureCalculatorBuilder.get().build(getRequestedPixelSizeMicrons());
		updateClassifier();
	}
	
	void updateClassifier() {
		updateClassifier(livePrediction.get());
	}
	
	void updateClassifier(boolean doClassification) {
		
		double downsample = getRequestedDownsample();
				
		if (helper == null) {
			helper = new PixelClassifierHelper(
	        		viewer.getImageData(), featureCalculator, downsample);
		} else {
			helper.setImageData(viewer.getImageData());
			helper.setFeatureCalculator(featureCalculator);
			helper.setDownsample(downsample);
		}
		
		if (doClassification)
			doClassification();
		else
			replaceOverlay(null);
	}
	
	
	double getRequestedDownsample() {
		if (selectedResolution == null || selectedResolution.get() == null)
			return 1;
		double downsample = selectedResolution.get().getDownsampleFactor(1);
		var server = viewer.getServer();
		if (server != null && server.hasPixelSizeMicrons())
			downsample = selectedResolution.get().getDownsampleFactor(server.getAveragedPixelSizeMicrons());
		return downsample;
	}
	
	double getRequestedPixelSizeMicrons() {
		double downsample = getRequestedDownsample();
		var server = viewer.getServer();
		if (server != null && server.hasPixelSizeMicrons())
			return downsample * server.getAveragedPixelSizeMicrons();
		return downsample;
	}
	
	
	
	boolean showAdvancedOptions() {
		DisplayHelpers.showMessageDialog("Pixel classifier", "Advanced options not available yet, sorry!");
		return false;
	}
	
	
	
	void doClassification() {
		if (helper == null) {
			updateFeatureCalculator();
			updateClassifier();
			if (helper == null) {
				logger.error("No pixel classifier helper available!");
				return;
			}
		}
		
		var model = selectedClassifier.get();
		if (model == null) {
			DisplayHelpers.showErrorNotification("Pixel classifier", "No classifier selected!");
			return;
		}

		 helper.updateTrainingData();
		 
		 TrainData trainData = helper.getTrainData();
		 if (trainData == null) {
			 pieChart.setData(Collections.emptyList(), false);
			 return;
		 }

		 List<ImageChannel> channels = helper.getChannels();

		 // TODO: Optionally limit the number of training samples we use
		 //	     		var trainData = classifier.createTrainData(matFeatures, matTargets);

		 int maxSamples = 100_000;
		 
		 // Ensure we seed the RNG for reproducibility
		 opencv_core.setRNGSeed(100);
		 
		 if (maxSamples > 0 && trainData.getNTrainSamples() > maxSamples)
			 trainData.setTrainTestSplit(maxSamples, true);
		 else
			 trainData.shuffleTrainTest();

//		 System.err.println("Train: " + trainData.getTrainResponses());
//		 System.err.println("Test: " + trainData.getTestResponses());
		 
		 //	        model.train(trainData, modelBuilder.getVarType());
		 
		 var labels = helper.getPathClassLabels();
		 var targets = trainData.getTrainNormCatResponses();
		 IntBuffer buffer = targets.createBuffer();
		 int n = (int)targets.total();
		 var counts = new int[labels.size()];
		 for (int i = 0; i < n; i++)
			 counts[buffer.get(i)] += 1;
		 
		 List<ClassificationPieChart.PathClassAndValue> data = new ArrayList<>();
		 for (var entry : labels.entrySet()) {
			 data.add(ClassificationPieChart.PathClassAndValue.create(entry.getValue(), counts[entry.getKey()]));
		 }
		 pieChart.setData(data, true);
		 
		 trainData = model.createTrainData(trainData.getTrainSamples(), trainData.getTrainResponses());
		 model.train(trainData);
		 
		 trainData.close();

		 int inputWidth = helper.getFeatureCalculator().getMetadata().getInputWidth();
		 int inputHeight = helper.getFeatureCalculator().getMetadata().getInputHeight();
		 PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
				 .inputPixelSize(getRequestedPixelSizeMicrons())
				 .inputShape(inputWidth, inputHeight)
				 .setChannelType(model.supportsProbabilities() ? selectedOutputType.get() : ImageServerMetadata.ChannelType.CLASSIFICATION)
				 .channels(channels)
				 .build();

		 var classifier = new OpenCVPixelClassifier(model, helper.getFeatureCalculator(), helper.getLastFeaturePreprocessor(), metadata, true);

		 var classifierServer = new PixelClassificationImageServer(helper.getImageData(), classifier);
		 replaceOverlay(new PixelClassificationOverlay(viewer, classifierServer));
//		 replaceOverlay(new PixelClassificationOverlay(viewer, classifier));
	}
	
	private PixelClassificationOverlay overlay;

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
		var imageData = viewer.getImageData();
		if (overlay != null) {
			overlay.stop(!wasApplied);
			if (imageData != null && imageData.getProperty("PIXEL_LAYER") == overlay.getPixelClassificationServer())
				imageData.removeProperty("PIXEL_LAYER");
//			viewer.getCustomOverlayLayers().remove(overlay);
		}
		overlay = newOverlay;
		if (overlay != null) {
			overlay.setUseAnnotationMask(selectedRegion.get() == ClassificationRegion.ANNOTATIONS_ONLY);
//			viewer.getCustomOverlayLayers().add(overlay);
			overlay.setLivePrediction(livePrediction.get());
			if (imageData != null)
				imageData.setProperty("PIXEL_LAYER", overlay.getPixelClassificationServer());
			wasApplied = false;
		}
		viewer.setCustomPixelLayerOverlay(overlay);
	}



	public void destroy() {
		if (overlay != null) {
			var imageData = viewer.getImageData();
			if (imageData != null && imageData.getProperty("PIXEL_LAYER") == overlay.getPixelClassificationServer())
				imageData.removeProperty("PIXEL_LAYER");
			overlay.stop(!wasApplied);
			viewer.resetCustomPixelLayerOverlay();
			overlay = null;
		}
		viewer.getView().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
		viewer.getImageDataProperty().removeListener(imageDataListener);
		var hierarchy = viewer.getHierarchy();
		if (hierarchy != null)
			hierarchy.removePathObjectListener(hierarchyListener);
//		setImageData(viewer, viewer.getImageData(), null);
		if (helper != null)
			helper.setImageData(null);
		if (stage != null && stage.isShowing())
			stage.close();
	}
	
	
	
	boolean saveAndApply() {
		logger.debug("Saving & applying classifier");
		updateClassifier(true);
		
		var server = overlay.getPixelClassificationServer();
		
		var project = QuPathGUI.getInstance().getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "Saving pixel classification requires a project!");
			return false;
		}
			
		try {
			var imageData = viewer.getImageData();
			var resultServer = saveAndApply(project, imageData, server.getClassifier());
			if (resultServer != null) {
				imageData.setProperty("PIXEL_LAYER", resultServer);
				wasApplied = true;
				replaceOverlay(null);
				return true;
			}
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage("Pixel classifier", e);
		}
		return false;
	}
	
	
	static String promptToSaveClassifier(Project<BufferedImage> project, PixelClassifier classifier) throws IOException {
		var classifierName = DisplayHelpers.showInputDialog("Pixel classifier", "Pixel classifier name", "");
		if (classifierName == null)
			return null;
		classifierName = classifierName.strip();
		if (classifierName.isBlank() || classifierName.contains("\n")) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "Classifier name must be unique, non-empty, and not contain invalid characters");
			return null;
		}
		
		// Save the classifier in the project
		try {
			saveClassifier(project, classifier, classifierName);
		} catch (IOException e) {
			DisplayHelpers.showWarningNotification("Pixel classifier", "Unable to write classifier to JSON - classifier can't be reloaded later");
			logger.error("Error saving classifier", e);
			throw e;
		}
		
		return classifierName;
	}
	
	
	static void saveClassifier(Project<BufferedImage> project, PixelClassifier classifier, String classifierName) throws IOException {
		project.getPixelClassifiers().putResource(classifierName, classifier);
	}
	
	static PixelClassificationImageServer saveAndApply(Project<BufferedImage> project, ImageData<BufferedImage> imageData, PixelClassifier classifier) throws IOException {
		String name = promptToSaveClassifier(project, classifier);
		if (name == null)
			return null;
		return applyClassifier(project, imageData, classifier, name);
	}
	
	public static PixelClassificationImageServer applyClassifier(Project<BufferedImage> project, ImageData<BufferedImage> imageData, PixelClassifier classifier, String classifierName) throws IOException {
		
		var server = new PixelClassificationImageServer(imageData, classifier);
		
		var entry = project.getEntry(imageData);
		if (entry == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "Unable to find current image in the current project!");
			return null;
		}
		
		var pathEntry = entry.getEntryPath();
		if (pathEntry == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "Sorry, a new-style project is needed to save pixel classifier results");
			return null;
		}
		
//		var dataFileName = QuPathGUI.getImageDataFile(project, entry).getName();
//		var dataFileName = entry.getUniqueName();
//		int ind = dataFileName.indexOf(PathPrefs.getSerializationExtension());
//		if (ind >= 0)
//			dataFileName = dataFileName.substring(0, ind);
		
		var pathOutput = Paths.get(pathEntry.toString(), "layers", classifierName, classifierName + ".zip");
		try {
			if (!Files.exists(pathOutput.getParent()) || !Files.isDirectory(pathOutput.getParent()))
				Files.createDirectories(pathOutput.getParent());
			else if (Files.exists(pathOutput)) {
				logger.warn("Classification already exists at {}", pathOutput);
				return new PixelClassificationImageServer(
						imageData,
						new ReadFromStorePixelClassifier(
								new FileSystemPersistentTileCache(pathOutput, server), server.getClassifier()));
			}
			
			// TODO: Write through the project entry instead
			var task = new Task<PixelClassificationImageServer>() {

				@Override
				protected PixelClassificationImageServer call() throws Exception {
					var tiles = server.getAllTileRequests();
					int n = tiles.size();
					boolean success = false;
					try (var persistentTileCache = new FileSystemPersistentTileCache(pathOutput, server)) {
						persistentTileCache.writeJSON("metadata.json", server.getMetadata());
						persistentTileCache.writeJSON("classifier.json", server.getClassifier());
						int i = 0;
						for (var tile : tiles) {
							updateProgress(i++, n);
							var request = tile.getRegionRequest();
							persistentTileCache.saveToCache(request, server.readBufferedImage(request));			
						}
						success = true;
					} catch (IOException e) {
						DisplayHelpers.showErrorMessage("Pixel classification", e);
					} finally {
						updateProgress(n, n);
//						replaceOverlay(
//								new PixelClassificationOverlay(viewer, newServer));
					}
					if (success)
						return new PixelClassificationImageServer(
							imageData,
							new ReadFromStorePixelClassifier(
									new FileSystemPersistentTileCache(pathOutput, server), server.getClassifier()));
					else
						return null;
				}
			};
			
			var progress = new ProgressDialog(task);
			progress.setTitle("Pixel classification");
			progress.setContentText("Applying classifier: " + classifierName);
			
			var t = new Thread(task);
			t.setDaemon(true);
			t.start();
			
			return task.get();
			
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}

		/*
		 * - Prompt for classifier name.
		 * - Get output directory for the image.
		 * - Write the classifier
		 * - Write the tiles (while showing progress dialog - remember to trim to annotations if needed)
		 */
	}
	
	
	
	
	
	
	
	static String getCachedName(RegionRequest request) {
		return String.format(
				"tile(x=%d,y=%d,w=%d,h=%d,z=%d,t=%d).tif",
				request.getX(),
				request.getY(),
				request.getWidth(),
				request.getHeight(),
				request.getZ(),
				request.getT());
	}
	
	
	PixelClassificationImageServer getClassificationServerOrShowError() {
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return null;
		var server = overlay == null ? null : overlay.getPixelClassificationServer();
		if (overlay == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "No classifier available yet!");
			return null;
		}
		return server;
	}
	
	
	boolean classifyObjects() {
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return false;
		var server = getClassificationServerOrShowError();
		if (server == null) {
			return false;
		}
		PixelClassifierStatic.classifyObjects(server, hierarchy.getDetectionObjects());
		return true;
	}
	
	
	boolean createObjects() {
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return false;
		
		var server = getClassificationServerOrShowError();
		if (server == null) {
			return false;
		}
		
		var objectTypes = Arrays.asList(
				"Annotation", "Detection"
		);
//		var availableChannels = new String[] {
//			server.getOriginalMetadata().getC
//		};
		var sizeUnits = Arrays.asList(
				"Pixels",
				GeneralTools.micrometerSymbol()
		);
		
		var params = new ParameterList()
				.addChoiceParameter("objectType", "Object type", "Annotation", objectTypes)
				.addDoubleParameter("minSize", "Minimum object/hole size", 0)
				.addChoiceParameter("minSizeUnits", "Minimum object/hole size units", "Pixels", sizeUnits)
				.addBooleanParameter("doSplit", "Split objects", false);
		
		params.setHiddenParameters(!server.hasPixelSizeMicrons(), "minSizeUnits");
		
		if (!DisplayHelpers.showParameterDialog("Create objects", params))
			return false;
		
		Function<ROI, PathObject> creator;
		if (params.getChoiceParameterValue("objectType").equals("Detection"))
			creator = r -> PathObjects.createDetectionObject(r);
		else
			creator = r -> {
				var annotation = PathObjects.createAnnotationObject(r);
				((PathAnnotationObject)annotation).setLocked(true);
				return annotation;
			};
		boolean doSplit = params.getBooleanParameterValue("doSplit");
		double minSizePixels = params.getDoubleParameterValue("minSize");
		if (server.hasPixelSizeMicrons() && !params.getChoiceParameterValue("minSizeUnits").equals("Pixels"))
			minSizePixels /= (server.getPixelWidthMicrons() * server.getPixelHeightMicrons());
		
		var selected = viewer.getSelectedObject();
		if (selected != null && selected.isDetection())
			selected = null;
		if (selected != null && !selected.getROI().isArea()) {
			DisplayHelpers.showErrorMessage("Create objects", "You either need an area selection or no selected object");
			return false;
		}
		if (selected != null && selected.getPathClass() != null && selected.getPathClass() != PathClassFactory.getPathClass(StandardPathClasses.REGION)) {
			var btn = DisplayHelpers.showYesNoCancelDialog("Create objects", "Create objects for selected annotation?\nChoose 'no' to use the entire image.");
			if (btn == DialogButton.CANCEL)
				return false;
			if (btn == DialogButton.NO)
				selected = null;
		}
		
		int nChildObjects = 0;
		if (selected == null)
			nChildObjects = hierarchy.nObjects();
		else
			nChildObjects = PathObjectTools.countDescendants(selected);
		if (nChildObjects > 0) {
			String message = "Existing child object will be deleted - is that ok?";
			if (nChildObjects > 1)
				message = nChildObjects + " existing descendant object will be deleted - is that ok?";
			if (!DisplayHelpers.showConfirmDialog("Create objects", message))
				return false;
		}
		// Need to turn off live prediction so we don't start training on the results...
		livePrediction.set(false);
		
		return PixelClassifierStatic.createObjectsFromPixelClassifier(server, selected, creator, minSizePixels, doSplit);
	}
	
	static interface PathObjectCreator {
		
		public PathObject createObject(ROI roi);
		
	}
	
	
	boolean editClassifierParameters() {
		var model = selectedClassifier.get();
		if (model == null) {
			DisplayHelpers.showErrorMessage("Edit parameters", "No classifier selected!");
			return false;
		}
		DisplayHelpers.showParameterDialog("Edit parameters", model.getParameterList());
		updateClassifier();
		return true;
	}
	
	
	boolean showOutput() {
		if (overlay == null) {
			DisplayHelpers.showErrorMessage("Show output", "No pixel classifier has been trained yet!");
			return false;
		}
		var server = overlay.getPixelClassificationServer();
		if (server == null || featureCalculator == null)
			return false;
		var selected = viewer.getSelectedObject();
		var roi = selected == null ? null : selected.getROI();
		double downsample = server.getDownsampleForResolution(0);
		RegionRequest request;
		if (roi == null) {
			request = RegionRequest.createInstance(
					server.getPath(), downsample, 
					0, 0, server.getWidth(), server.getHeight(), viewer.getZPosition(), viewer.getTPosition());			
		} else {
			request = RegionRequest.createInstance(server.getPath(), downsample, selected.getROI());
		}
		long estimatedPixels = (long)Math.ceil(request.getWidth()/request.getDownsample()) * (long)Math.ceil(request.getHeight()/request.getDownsample());
		double estimatedMB = (estimatedPixels * server.nChannels() * (server.getBitsPerPixel()/8)) / (1024.0 * 1024.0);
		if (estimatedPixels >= Integer.MAX_VALUE - 16) {
			DisplayHelpers.showErrorMessage("Extract output", "Requested region is too big! Try selecting a smaller region.");
			return false;
		} else if (estimatedMB >= 200.0) {
			if (!DisplayHelpers.showConfirmDialog("Extract output",
					String.format("Extracting this region will require approximately %.1f MB - are you sure you want to try this?", estimatedMB)))
				return false;
		}
		
		try {
//			var imp = IJExtension.extractROI(server, selected, request, true, null).getImage();
			var pathImage = IJTools.convertToImagePlus(
					overlay.getPixelClassificationServer(),
					request);
			var imp = pathImage.getImage();
			if (roi != null && !(roi instanceof RectangleROI)) {
				imp.setRoi(ROIConverterIJ.convertToIJRoi(roi, pathImage));
			}
			imp.show();
			return true;
		} catch (IOException e) {
			logger.error("Error showing output", e);
		}
		return false;
	}
	
	
	boolean showFeatures() {
		double cx = viewer.getCenterPixelX();
		double cy = viewer.getCenterPixelY();
		var server = viewer.getServer();
		if (server == null || featureCalculator == null)
			return false;
		double pixelSize = server.getAveragedPixelSizeMicrons();
		if (!Double.isFinite(pixelSize))
			pixelSize = 1;
		double downsample = selectedResolution.get().getDownsampleFactor(pixelSize);
		double width = featureCalculator.getMetadata().getInputWidth() * downsample;
		double height = featureCalculator.getMetadata().getInputHeight() * downsample;
		var request = RegionRequest.createInstance(
				server.getPath(), downsample, 
				(int)(cx - width/2), (int)(cy - height/2), (int)width, (int)height, viewer.getZPosition(), viewer.getTPosition());
		
		try {
			var features = featureCalculator.calculateFeatures(viewer.getServer(), request);
			var imp = OpenCVTools.matToImagePlus(features, "Features");
			int s = 1;
			IJTools.calibrateImagePlus(imp, request, server);
			var impComp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
			impComp.setDimensions(imp.getStackSize(), 1, 1);
			for (var c : featureCalculator.getMetadata().getChannels()) {
				impComp.setPosition(s);
				impComp.resetDisplayRange();
				impComp.getStack().setSliceLabel(c.getName(), s++);
			}
			impComp.setPosition(1);
			impComp.show();
			return true;
		} catch (IOException e) {
			logger.error("Error calculating features", e);
		}
		return false;
	}

	boolean selectChannels() {
		DisplayHelpers.showErrorMessage("Select channels", "Not implemented yet!");
		return false;
	}
	
	boolean addResolution() {
		var server = viewer.getServer();
		if (server == null) {
			DisplayHelpers.showErrorMessage("Add resolution", "No image available!");
			return false;
		}
		String units = null;
		Double pixelSize = null;
		if (server.hasPixelSizeMicrons()) {
			pixelSize = DisplayHelpers.showInputDialog("Add resolution", "Enter requested pixel size in " + GeneralTools.micrometerSymbol(), 1.0);
			units = PixelCalibration.MICROMETER;
		} else {
			pixelSize = DisplayHelpers.showInputDialog("Add resolution", "Enter requested downsample factor", 1.0);
		}
		
		if (pixelSize == null)
			return false;
		
		var res = SimpleImageResolution.getInstance("Custom", pixelSize, units);
		var temp = new ArrayList<>(resolutions);
		temp.add(res);
		Collections.sort(temp, (r1, r2) -> Double.compare(r1.getDownsampleFactor(1), r2.getDownsampleFactor(1)));
		resolutions.setAll(temp);
		comboResolutions.getSelectionModel().select(res);
		
		return true;
	}
	
	
	
	private static Map<String, String> piechartStyleSheets = new HashMap<>();
	
	
	
	public PixelClassifierImageSelectionPane(final QuPathViewer viewer) {
		this.viewer = viewer;
		initialize(viewer);
	}
	
	
	public ImageResolution getSelectedResolution() {
		return selectedResolution.get();
	}
	
	
	void updateResolution(ImageResolution resolution) {
		ImageServer<BufferedImage> server = null;
		if (viewer != null)
			server = viewer.getServer();
		if (server == null || miniViewer == null || resolution == null)
			return;
		Tooltip.install(miniViewer.getPane(), new Tooltip("Classification resolution: \n" + resolution));
		miniViewer.setDownsample(resolution.getDownsampleFactor(server.getAveragedPixelSizeMicrons()));
	}
	
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	
	
	
	
	static interface ImageResolution {
		
		double getDownsampleFactor(double pixelSizeMicrons);
		
	}
	
	static class SimpleImageResolution implements ImageResolution {
		
		private static Map<String, SimpleImageResolution> map = new TreeMap<>();
		
		private final String name;
		private final String units;
		private final double requestedPixelSize;
		
		public synchronized static SimpleImageResolution getInstance(final String name, final double requestedPixelSize, final String units) {
			var key = name + "::" + requestedPixelSize + "::" + units;
			var resolution = map.get(key);
			if (resolution == null) {
				resolution = new SimpleImageResolution(name, requestedPixelSize, units);
				map.put(key, resolution);
			}
			return resolution;
		}
		
		private SimpleImageResolution(String name, double requestedPixelSize, String units) {
			this.name = name;
			this.requestedPixelSize = requestedPixelSize;
			this.units = units;
		}
		
		@Override
		public double getDownsampleFactor(double pixelSize) {
			if (Double.isFinite(pixelSize))
				return requestedPixelSize / pixelSize;
			else
				return requestedPixelSize;
		}
		
		@Override
		public String toString() {
			if (units != null) {
				String text = GeneralTools.formatNumber(requestedPixelSize, 2) + " " + GeneralTools.micrometerSymbol() + " per pixel";
				if (name == null)
					return text;
				else
					return String.format("%s (%s)", name, text);
			} else {
				return "Downsample " + GeneralTools.formatNumber(requestedPixelSize, 5);
			}
		}
		
	}
	
	
	
	static class ClassificationPieChart {
		
		private PieChart chart = new PieChart();
		
		public void setData(List<PathClassAndValue> pathClassData, boolean convertToPercentages) {
			
			var style = new StringBuilder();

			double sum = pathClassData.stream().mapToDouble(p -> p.value).sum();
			var newData = new ArrayList<Data>();
			int ind = 0;
			var tooltips = new HashMap<Data, Tooltip>();
			for (var d : pathClassData) {
				var name = d.pathClass.getName();
				double value = d.value;
				if (convertToPercentages)
					value = value / sum * 100.0;
				var datum = new Data(name, value);
				newData.add(datum);
				
				var color = d.pathClass.getColor();
				style.append(String.format(".default-color%d.chart-pie { -fx-pie-color: rgb(%d, %d, %d); }", ind++, 
						ColorTools.red(color),
						ColorTools.green(color),
						ColorTools.blue(color))).append("\n");
				
				var text = String.format("%s: %.1f%%", name, value);
				tooltips.put(datum, new Tooltip(text));
			}
			
			var styleString = style.toString();
			var sheet = piechartStyleSheets.get(styleString);
			sheet = null;
			if (sheet == null) {
				try {
					var file = File.createTempFile("chart", ".css");
					file.deleteOnExit();
					var writer = new PrintWriter(file);
					writer.println(styleString);
					writer.close();
					sheet = file.toURI().toURL().toString();
					piechartStyleSheets.put(styleString, sheet);
				} catch (IOException e) {
					logger.error("Error creating temporary piechart stylesheet", e);
				}			
			}
			if (sheet != null)
				chart.getStylesheets().setAll(sheet);
			
			chart.setAnimated(false);
			chart.getData().setAll(newData);
			
			for (var entry : tooltips.entrySet()) {
				Tooltip.install(entry.getKey().getNode(), entry.getValue());
			}

		}
		
		public PieChart getChart() {
			return chart;
		}
		
		
		static class PathClassAndValue {
			
			private final PathClass pathClass;
			private final double value;
			
			PathClassAndValue(PathClass pathClass, double value) {
				this.pathClass = pathClass;
				this.value = value;
			}
			
			static PathClassAndValue create(final PathClass pathClass, final double value) {
				return new PathClassAndValue(pathClass, value);
			}
			
		}
		
		
	}
	
	
	class MouseListener implements EventHandler<MouseEvent> {
		

		@Override
		public void handle(MouseEvent event) {
			if (overlay == null)
				return;
			var p = viewer.componentPointToImagePoint(event.getX(), event.getY(), null, false);
			var server = overlay.getPixelClassificationServer();
			String results = null;
			if (server != null)
				results = getResultsString(server, p.getX(), p.getY(), viewer.getZPosition(), viewer.getTPosition());
			if (results == null)
				cursorLocation.set("");
			else
				cursorLocation.set(results);
		}
		
	}
	
	
	/**
	 * Get a String summarizing the pixel classification values at a specified pixel location.
	 * 
	 * @param classifierServer
	 * @param x
	 * @param y
	 * @param z
	 * @param t
	 * @return
	 */
    static String getResultsString(PixelClassificationImageServer classifierServer, double x, double y, int z, int t) {
    	if (classifierServer == null)
    		return null;
    	
    	int level = 0;
    	var tile = classifierServer.getTile(level, (int)Math.round(x), (int)Math.round(y), z, t);
    	if (tile == null)
    		return null;
    	var img = classifierServer.getCachedTile(tile);
    	if (img == null)
    		return null;

    	int xx = (int)Math.floor((x - tile.getImageX()) / tile.getDownsample());
    	int yy = (int)Math.floor((y - tile.getImageY()) / tile.getDownsample());
    	if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight())
    		return null;
    	
//    	String coords = GeneralTools.formatNumber(x, 1) + "," + GeneralTools.formatNumber(y, 1);
    	
    	var channels = classifierServer.getChannels();
    	if (classifierServer.getOutputType() == ImageServerMetadata.ChannelType.CLASSIFICATION) {
        	int sample = img.getRaster().getSample(xx, yy, 0); 		
        	return String.format("Classification: %s", channels.get(sample).getName());
//        	return String.format("Classification (%s):\n%s", coords, channels.get(sample).getName());
    	} else {
    		var array = new String[channels.size()];
    		for (int c = 0; c < channels.size(); c++) {
    			float sample = img.getRaster().getSampleFloat(xx, yy, c);
    			if (img.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_BYTE)
    				sample /= 255f;
    			array[c] = channels.get(c).getName() + ": " + GeneralTools.formatNumber(sample, 2);
    		}
        	return String.format("Prediction: %s", String.join(", ", array));
    	}
    }
	
	
	class HierarchyListener implements PathObjectHierarchyListener {

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (!event.isChanging() && (event.isStructureChangeEvent() || event.isObjectClassificationEvent())) {
				if (event.isObjectClassificationEvent() || event.getChangedObjects().stream().anyMatch(p -> p.getPathClass() != null)) {
					if (event.getChangedObjects().stream().anyMatch(p -> p.isAnnotation()))
						updateClassifier();
				}
			}
		}
		
	}
	
	
	
	
	
	
	
	public interface PersistentTileCache extends AutoCloseable {
		
		default public String getCachedName(RegionRequest request) {
			return String.format(
					"tile(x=%d,y=%d,w=%d,h=%d,z=%d,t=%d).tif",
					request.getX(),
					request.getY(),
					request.getWidth(),
					request.getHeight(),
					request.getZ(),
					request.getT());
		}
		
		public void writeJSON(String name, Object o) throws IOException;
		
		public BufferedImage readFromCache(RegionRequest request) throws IOException;
		
		public void saveToCache(RegionRequest request, BufferedImage img) throws IOException;
		
	}

	
	static class FileSystemPersistentTileCache implements PersistentTileCache {
		
		private Path path;
		private transient ImageServer<BufferedImage> server;
		private transient FileSystem fileSystem;
		private transient String root;
		
		FileSystemPersistentTileCache(Path path, ImageServer<BufferedImage> server) throws IOException, URISyntaxException {
			this.path = path;
			this.server = server;
		}
		
		private synchronized void ensureFileSystemOpen() throws IOException {
			if (fileSystem != null && fileSystem.isOpen())
				return;
			fileSystem = null;
			if (path.toString().toLowerCase().endsWith(".zip")) {
				var fileURI = path.toUri();
				try {
					var uri = new URI("jar:" + fileURI.getScheme(), fileURI.getPath(), null);
					this.fileSystem = FileSystems.newFileSystem(uri, Collections.singletonMap("create", String.valueOf(Files.notExists(path))));
					this.root = "/";
				} catch (URISyntaxException e) {
					logger.error("Problem constructing file system", e);
				}
			}
			if (this.fileSystem == null) {
				this.fileSystem = FileSystems.getDefault();
				this.root = path.toString();
			}
		}
		
		@Override
		public synchronized void close() throws Exception {
			if (this.fileSystem != FileSystems.getDefault())
				this.fileSystem.close();
		}
		
		
		@Override
		public synchronized void writeJSON(String name, Object o) throws IOException {
			var gson = new GsonBuilder()
					.setLenient()
					.serializeSpecialFloatingPointValues()
					.setPrettyPrinting()
					.create();
			var json = gson.toJson(o);
			ensureFileSystemOpen();
			var path = fileSystem.getPath(root, name);
			Files.writeString(path, json);
		}
		
		@Override
		public synchronized BufferedImage readFromCache(RegionRequest request) throws IOException {
			ensureFileSystemOpen();
			var path = fileSystem.getPath(root, getCachedName(request));
			try (var stream = Files.newInputStream(path)) {
				var imp = new Opener().openTiff(stream, "Anything");
				ColorModel colorModel;
				if (imp.getNChannels() == 1)
					colorModel = ColorModelFactory.getIndexedColorModel(server.getChannels());
				else
					colorModel = ColorModelFactory.geProbabilityColorModel8Bit(server.getChannels());
				return ImageJServer.convertToBufferedImage(imp, 1, 1, colorModel);
			}
		}
		
		@Override
		public void saveToCache(RegionRequest request, BufferedImage img) throws IOException {
			var imp = IJTools.convertToImagePlus("Tile", server, img, request).getImage();
			var bytes = new FileSaver(imp).serialize();
			saveToCache(request, bytes);
		}
		
		public synchronized void saveToCache(RegionRequest request, byte[] bytes) throws IOException {
			ensureFileSystemOpen();
			var path = fileSystem.getPath(root, getCachedName(request));
			Files.write(path, bytes);
		}
		
	}

	/**
	 * A (@link PixelClassifier} that doesn't actually compute classifications itself, 
	 * but rather reads them from storage.
	 */
	static class ReadFromStorePixelClassifier implements PixelClassifier {

		private PixelClassifier classifier;
		private PersistentTileCache store;

		ReadFromStorePixelClassifier(PersistentTileCache store, PixelClassifier classifier) {
			this.store = store;
			this.classifier = classifier;
		}

		@Override
		public BufferedImage applyClassification(ImageData<BufferedImage> server, RegionRequest request)
				throws IOException {
			BufferedImage img = store.readFromCache(request);
			if (img == null)
				return classifier.applyClassification(server, request);
			else
				return img;
		}

		@Override
		public PixelClassifierMetadata getMetadata() {
			return classifier.getMetadata();
		}

	}
	
	
//	static interface PixelLayerManager<T> {
//		
//		public ImageServer<T> buildPixelServer(String id) throws IOException;
//		
//		public List<String> listPixelServers() throws IOException;
//
//		public void writePixelServer(String id, PixelClassificationImageServer server, Predicate<TileRequest> predicate) throws IOException;
//		
//		public void removePixelServer(String id) throws IOException;
//		
//	}
//	
//	
//	static class DefaultPixelLayerManager implements PixelLayerManager<BufferedImage> {
//		
//		private ProjectImageEntry<BufferedImage> entry;
//		private Path baseDir;
//		
//		public DefaultPixelLayerManager(ProjectImageEntry<BufferedImage> entry, Path baseDir) {
//			this.entry = entry;
//			baseDir = Paths.get(entry.getEntryPath().toString(), "layers");
//		}
//		
//		
//
//		@Override
//		public ImageServer<BufferedImage> buildPixelServer(String id) throws IOException {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		public List<String> listPixelServers() throws IOException {
//			return Files.list(baseDir).filter(p -> {
//				try {
//					return Files.isDirectory(p) && !Files.isHidden(p);
//				} catch (IOException e) {
//					return false;
//				}
//					}).map(p -> p.getFileName().toString())
//					.collect(Collectors.toList());
//		}
//
//		@Override
//		public void writePixelServer(String id, PixelClassificationImageServer server, Predicate<TileRequest> predicate) throws IOException {
//			var tiles = server.getAllTileRequests();
//			if (predicate != null)
//				tiles = tiles.stream().filter(predicate).collect(Collectors.toList());
//			for (var tile : tiles) {
//				var img = server.readBufferedImage(tile.getRegionRequest());
//			}
//			
//		}
//
//		@Override
//		public void removePixelServer(String id) throws IOException {
//			
//		}
//		
//	}
	
	
	/**
	 * Helper class capable of building (or returning) a FeatureCalculator.
	 * 
	 * @author Pete Bankhead
	 */
	public static abstract class FeatureCalculatorBuilder {
		
		public abstract OpenCVFeatureCalculator build(double requestedPixelSize);
		
		public boolean canCustomize() {
			return false;
		}
		
		public boolean doCustomize() {
			throw new UnsupportedOperationException("Cannot customize this feature calculator!");
		}
		
		public String getName() {
			OpenCVFeatureCalculator calculator = build(1);
			if (calculator == null)
				return "No feature calculator";
			return calculator.toString();
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	
	
	class DefaultFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
		
		private GridPane pane;
		
		private ObservableList<FeatureFilter> selectedFeatures;
		
		private ObservableList<Integer> availableChannels;
		private ObservableList<Integer> selectedChannels;
		
		DefaultFeatureCalculatorBuilder() {
			
			int row = 0;
			
			pane = new GridPane();
			
			// Selected channels
			
			var labelChannels = new Label("Channels");
			var comboChannels = new CheckComboBox<Integer>();
//			var btnChannels = new Button("Select");
//			btnChannels.setOnAction(e -> selectChannels());
			var server = QuPathGUI.getInstance().getViewer().getServer();
			if (server != null) {
				for (int c = 0; c < server.nChannels(); c++)
					comboChannels.getItems().add(c);			
				comboChannels.getCheckModel().checkAll();
			}
			comboChannels.setConverter(new StringConverter<Integer>() {
				
				@Override
				public String toString(Integer object) {
					return server.getChannel(object).getName();
				}
				
				@Override
				public Integer fromString(String string) {
					for (int i = 0; i < server.nChannels(); i++) {
						if (string.equals(server.getChannel(i).getName()))
							return i;
					}
					return null;
				}
			});
			availableChannels = comboChannels.getItems();
			selectedChannels = comboChannels.getCheckModel().getCheckedItems();
//			selectedChannels.addListener((Change<? extends Integer> c) -> updateFeatureCalculator());
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		
//			GridPaneTools.addGridRow(pane, row++, 0,
//					"Choose the image channels used to calculate features",
//					labelChannels, comboChannels, btnChannels);
			
			
			
			var labelFeatures = new Label("Features");
			var comboFeatures = new CheckComboBox<FeatureFilter>();
			selectedFeatures = comboFeatures.getCheckModel().getCheckedItems();
//			selectedFeatures.addListener((Change<? extends FeatureFilter> c) -> updateFeatureCalculator());

			comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.ORIGINAL_PIXELS, -1));
			
			var sigmas = new double[] {1, 2, 4, 8};
			for (var s : sigmas)
				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.GAUSSIAN_FILTER, s));
			
			for (var s : sigmas)
				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.LAPLACIAN_OF_GAUSSIAN_FILTER, s));
			
			for (var s : sigmas)
				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.SOBEL_FILTER, s));

			for (var s : sigmas)
				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.HESSIAN_EIGENVALUES_AND_DETERMINANT_FILTER, s));

//			for (var s : sigmas)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.NORMALIZED_INTENSITY_FILTER, s));
	//
//			for (var s : sigmas)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.COHERENCE_FILTER, s));
//			
//			int nAngles = 4;
//			for (double lamda : new double[] {5, 10}) {
//				for (double gamma : new double[] {0.5, 1.0}) {
//					for (var s : sigmas)
//						comboFeatures.getItems().add(new FeatureFilters.GaborFeatureFilter(s, gamma, lamda, nAngles));
//				}
//			}
			
			comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MEDIAN_FILTER, 3));
			comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MEDIAN_FILTER, 5));
			
			var radii = new int[] {1, 2, 4, 8};
			
			for (var r : radii)
				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.STANDARD_DEVIATION_FILTER, r));
			
////			for (var r : radii)
////				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.PEAK_DENSITY_FILTER, r));
	//
////			for (var r : radii)
////				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.VALLEY_DENSITY_FILTER, r));
	//
//			for (var r : radii)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MORPHOLOGICAL_OPEN_FILTER, r));
//			
//			for (var r : radii)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MORPHOLOGICAL_CLOSE_FILTER, r));
//			
//			for (var r : radii)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MORPHOLOGICAL_ERODE_FILTER, r));
	//
//			for (var r : radii)
//				comboFeatures.getItems().add(FeatureFilters.getFeatureFilter(FeatureFilters.MORPHOLOGICAL_DILATE_FILTER, r));


			// Select the simple Gaussian features by default
			comboFeatures.getCheckModel().checkIndices(1);
			
			// I'd like more informative text to be displayed by default
			comboFeatures.setTitle("Selected");
			comboFeatures.setShowCheckedCount(true);
						
			GridPaneTools.addGridRow(pane, row++, 0, 
					"Choose the features that are available to the classifier (e.g. smoothed pixels, edges, other textures)",
					labelFeatures, comboFeatures);
			
			comboChannels.setMaxWidth(Double.MAX_VALUE);
			comboFeatures.setMaxWidth(Double.MAX_VALUE);
			GridPaneTools.setHGrowPriority(Priority.ALWAYS, comboChannels, comboFeatures);
			GridPaneTools.setFillWidth(Boolean.TRUE, comboChannels, comboFeatures);
			
			pane.setHgap(5);
			pane.setVgap(6);
			
		}

		@Override
		public OpenCVFeatureCalculator build(double requestedPixelSize) {
			return new BasicFeatureCalculator(
					"Custom features", selectedChannels, selectedFeatures, 
					requestedPixelSize);
		}
		
		@Override
		public boolean canCustomize() {
			return true;
		}
		
		@Override
		public boolean doCustomize() {
			
			boolean success = DisplayHelpers.showMessageDialog("Select features", pane);
			if (success) {
				if (selectedChannels == null || selectedChannels.isEmpty()) {
					DisplayHelpers.showErrorNotification("Pixel classifier", "No channels selected!");
					return false;
				}
	
				if (selectedFeatures == null || selectedFeatures.isEmpty()) {
					DisplayHelpers.showErrorNotification("Pixel classifier", "No features selected!");
					return false;
				}
			}
			return success;
//			
//			throw new UnsupportedOperationException("Cannot customize this feature calculator!");
		}
		
	}
	
	
}
