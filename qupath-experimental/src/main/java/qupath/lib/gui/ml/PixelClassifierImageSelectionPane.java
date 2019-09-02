package qupath.lib.gui.ml;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.IntBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.Opener;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import jfxtras.scene.layout.HBox;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.images.servers.ImageJServer;
import qupath.imagej.tools.IJTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
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
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
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
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.pixel.OpenCVPixelClassifier;
import qupath.opencv.ml.pixel.PixelClassifierHelper;
import qupath.opencv.ml.pixel.PixelClassifierHelper.BoundaryStrategy;
import qupath.opencv.ml.pixel.features.ExtractNeighborsFeatureCalculator;
import qupath.opencv.ml.pixel.features.Feature;
import qupath.opencv.ml.pixel.features.MultiscaleFeatureCalculator;
import qupath.opencv.ml.pixel.features.OpenCVFeatureCalculator;
import qupath.opencv.tools.OpenCVTools;
import qupath.opencv.tools.HessianCalculator.MultiscaleFeature;


public class PixelClassifierImageSelectionPane {
	
	final static Logger logger = LoggerFactory.getLogger(PixelClassifierImageSelectionPane.class);
	
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
	
	private ObjectProperty<BoundaryStrategy> boundaryStrategy = new SimpleObjectProperty<>(BoundaryStrategy.NONE);
	
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

	
	private void initialize(final QuPathViewer viewer) {
		
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
		
		// Boundary strategy
		boundaryStrategy.addListener((v, o, n) -> {
			updateClassifier();
		});
		
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
		comboFeatures.getItems().add(new ExtractNeighborsFeatureCalculatorBuilder());
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
				OpenCVClassifiers.createStatModel(RTrees.class),
				OpenCVClassifiers.createStatModel(KNearest.class),
				OpenCVClassifiers.createStatModel(ANN_MLP.class),
				OpenCVClassifiers.createStatModel(LogisticRegression.class)
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
	 * Add to the list of default feature calculator builders that will be available when 
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
	
	private void updateAvailableResolutions() {
		var imageData = viewer.getImageData();
		if (imageData == null) {
			resolutions.clear();
			return;
		}
		var temp = new ArrayList<ImageResolution>();
		double originalDownsample = 1;
		String units = null;
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			originalDownsample = cal.getAveragedPixelSizeMicrons();
			units = PixelCalibration.MICROMETER;
		}
		int scale = 1;
		
		for (String name : resolutionNames) {
			temp.add(SimpleImageResolution.getInstance(name, originalDownsample * scale, units));
			scale *= 2;
		}
		resolutions.setAll(temp);
	}
	
	
	private void updateFeatureCalculator() {
		featureCalculator = selectedFeatureCalculatorBuilder.get().build(viewer.getImageData(), getRequestedPixelSizeMicrons());
		updateClassifier();
	}
	
	private void updateClassifier() {
		updateClassifier(livePrediction.get());
	}
	
	private void updateClassifier(boolean doClassification) {
		
		double downsample = getRequestedDownsample();
				
		if (helper == null) {
			helper = new PixelClassifierHelper(
	        		viewer.getImageData(), featureCalculator, downsample);
		} else {
			helper.setImageData(viewer.getImageData());
			helper.setFeatureCalculator(featureCalculator);
			helper.setDownsample(downsample);
		}
		helper.setBoundaryStrategy(boundaryStrategy.get());
		helper.setBoundaryThickness(1.0);
		
		if (doClassification)
			doClassification();
		else
			replaceOverlay(null);
	}
	
	
	private double getRequestedDownsample() {
		if (selectedResolution == null || selectedResolution.get() == null)
			return 1;
		double downsample = selectedResolution.get().getDownsampleFactor(1);
		var server = viewer.getServer();
		PixelCalibration cal = server == null ? null : server.getPixelCalibration();
		if (cal != null && cal.hasPixelSizeMicrons())
			downsample = selectedResolution.get().getDownsampleFactor(cal.getAveragedPixelSizeMicrons());
		return downsample;
	}
	
	private double getRequestedPixelSizeMicrons() {
		double downsample = getRequestedDownsample();
		var server = viewer.getServer();
		PixelCalibration cal = server == null ? null : server.getPixelCalibration();
		if (cal != null && cal.hasPixelSizeMicrons())
			return downsample * cal.getAveragedPixelSizeMicrons();
		return downsample;
	}
	
	
	
	private boolean showAdvancedOptions() {
		
		var params = new ParameterList()
				.addChoiceParameter("boundaryStrategy", "Boundary strategy", boundaryStrategy.get(),
						Arrays.asList(BoundaryStrategy.values()),
						"Choose how annotation boundaries should influence classifier training");
//				.addDoubleParameter("boundaryThickness", "Boundary thickness", );
		if (!DisplayHelpers.showParameterDialog("Advanced options", params))
			return false;
		
		var strategy = params.getChoiceParameterValue("boundaryStrategy");
		if (strategy instanceof BoundaryStrategy) {
			boundaryStrategy.set((BoundaryStrategy)strategy);
		}
		
		return true;
	}
	
	
	
	private void doClassification() {
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

		TrainData trainData = null;
		try {
			helper.updateTrainingData();
			trainData = helper.getTrainData();
		} catch (Exception e) {
			logger.error("Error when updating training data", e);
			return;
		}
		 if (trainData == null) {
			 pieChart.setData(Collections.emptyMap(), false);
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
		 var rawCounts = new int[labels.size()];
		 for (int i = 0; i < n; i++) {
			 rawCounts[buffer.get(i)] += 1;
		 }
		 Map<PathClass, Integer> counts = new LinkedHashMap<>();
		 for (var entry : labels.entrySet()) {
			 counts.put(entry.getValue(), rawCounts[entry.getKey()]);
		 }
		 pieChart.setData(counts, true);
		 
		 trainData = model.createTrainData(trainData.getTrainSamples(), trainData.getTrainResponses());
		 model.train(trainData);
		 
		 trainData.close();

		 int inputWidth = helper.getFeatureCalculator().getInputSize().getWidth();
		 int inputHeight = helper.getFeatureCalculator().getInputSize().getHeight();
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
	private void replaceOverlay(PixelClassificationOverlay newOverlay) {
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



	private void destroy() {
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
	
	
	
	private boolean saveAndApply() {
		logger.debug("Saving & applying classifier");
		updateClassifier(true);
		
		PixelClassificationImageServer server = null;
		if (overlay != null)
			server = getClassificationServerOrShowError();
		if (server == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "Nothing to save - please train a classifier first!");
			return false;
		}
		
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
	
	
	private static String promptToSaveClassifier(Project<BufferedImage> project, PixelClassifier classifier) throws IOException {
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
	
	
	private static void saveClassifier(Project<BufferedImage> project, PixelClassifier classifier, String classifierName) throws IOException {
		project.getPixelClassifiers().putResource(classifierName, classifier);
	}
	
	private static PixelClassificationImageServer saveAndApply(Project<BufferedImage> project, ImageData<BufferedImage> imageData, PixelClassifier classifier) throws IOException {
		String name = promptToSaveClassifier(project, classifier);
		if (name == null)
			return null;
		return PixelClassifierStatic.applyClassifier(project, imageData, classifier, name);
	}
	
	private PixelClassificationImageServer getClassificationServerOrShowError() {
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return null;
		var server = overlay == null ? null : overlay.getPixelClassificationServer();
		if (server == null || !(server instanceof PixelClassificationImageServer)) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "No classifier available yet!");
			return null;
		}
		return (PixelClassificationImageServer)server;
	}
	
	
	private boolean classifyObjects() {
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
	
	
	private boolean createObjects() {
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
		
		PixelCalibration cal = server.getPixelCalibration();
		params.setHiddenParameters(!cal.hasPixelSizeMicrons(), "minSizeUnits");
		
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
		if (cal.hasPixelSizeMicrons() && !params.getChoiceParameterValue("minSizeUnits").equals("Pixels"))
			minSizePixels /= (cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons());
		
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
	
	
	private boolean editClassifierParameters() {
		var model = selectedClassifier.get();
		if (model == null) {
			DisplayHelpers.showErrorMessage("Edit parameters", "No classifier selected!");
			return false;
		}
		DisplayHelpers.showParameterDialog("Edit parameters", model.getParameterList());
		updateClassifier();
		return true;
	}
	
	
	private boolean showOutput() {
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
		double estimatedMB = (estimatedPixels * server.nChannels() * (server.getPixelType().getBytesPerPixel())) / (1024.0 * 1024.0);
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
					server,
					request);
			var imp = pathImage.getImage();
			if (imp instanceof CompositeImage && server.getMetadata().getChannelType() != ChannelType.CLASSIFICATION)
				((CompositeImage)imp).setDisplayMode(CompositeImage.GRAYSCALE);
			if (roi != null && !(roi instanceof RectangleROI)) {
				imp.setRoi(IJTools.convertToIJRoi(roi, pathImage));
			}
			IJExtension.getImageJInstance();
			imp.show();
			return true;
		} catch (IOException e) {
			logger.error("Error showing output", e);
		}
		return false;
	}
	
	
	private boolean showFeatures() {
		ImageData<BufferedImage> imageData = viewer.getImageData();
		double cx = viewer.getCenterPixelX();
		double cy = viewer.getCenterPixelY();
		if (imageData == null || featureCalculator == null)
			return false;
		ImageServer<BufferedImage> server = imageData.getServer();
		PixelCalibration cal = server.getPixelCalibration();
		double pixelSize = cal.getAveragedPixelSizeMicrons();
		if (!Double.isFinite(pixelSize))
			pixelSize = 1;
		double downsample = selectedResolution.get().getDownsampleFactor(pixelSize);
		double width = featureCalculator.getInputSize().getWidth() * downsample;
		double height = featureCalculator.getInputSize().getHeight() * downsample;
		var request = RegionRequest.createInstance(
				server.getPath(), downsample, 
				(int)(cx - width/2), (int)(cy - height/2), (int)width, (int)height, viewer.getZPosition(), viewer.getTPosition());
		
		try {
			List<Feature<Mat>> features = featureCalculator.calculateFeatures(imageData, request);
			if (features.isEmpty()) {
				DisplayHelpers.showErrorMessage("Show features", "No features selected!");
				return false;
			}
			ImagePlus imp = OpenCVTools.matToImagePlus("Features", features.stream().map(f -> f.getFeature()).toArray(Mat[]::new));
			int s = 1;
			IJTools.calibrateImagePlus(imp, request, server);
			CompositeImage impComp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
			impComp.setDimensions(imp.getStackSize(), 1, 1);
			for (Feature<?> feature : features) {
				impComp.setPosition(s);
				impComp.resetDisplayRange();
				impComp.getStack().setSliceLabel(feature.getName(), s++);
			}
			impComp.setPosition(1);
			IJExtension.getImageJInstance();
			impComp.show();
			return true;
		} catch (IOException e) {
			logger.error("Error calculating features", e);
		}
		return false;
	}
	
	private boolean addResolution() {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null) {
			DisplayHelpers.showErrorMessage("Add resolution", "No image available!");
			return false;
		}
		String units = null;
		Double pixelSize = null;
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			pixelSize = DisplayHelpers.showInputDialog("Add resolution", "Enter requested pixel size in " + GeneralTools.micrometerSymbol(), 1.0);
			units = PixelCalibration.MICROMETER;
		} else {
			pixelSize = DisplayHelpers.showInputDialog("Add resolution", "Enter requested downsample factor", 1.0);
		}
		
		if (pixelSize == null)
			return false;
		
		ImageResolution res = SimpleImageResolution.getInstance("Custom", pixelSize, units);
		List<ImageResolution> temp = new ArrayList<>(resolutions);
		temp.add(res);
		Collections.sort(temp, (r1, r2) -> Double.compare(r1.getDownsampleFactor(1), r2.getDownsampleFactor(1)));
		resolutions.setAll(temp);
		comboResolutions.getSelectionModel().select(res);
		
		return true;
	}	
	
	
	public PixelClassifierImageSelectionPane(final QuPathViewer viewer) {
		this.viewer = viewer;
		initialize(viewer);
	}
	
	
	public ImageResolution getSelectedResolution() {
		return selectedResolution.get();
	}
	
	
	private void updateResolution(ImageResolution resolution) {
		ImageServer<BufferedImage> server = null;
		if (viewer != null)
			server = viewer.getServer();
		if (server == null || miniViewer == null || resolution == null)
			return;
		Tooltip.install(miniViewer.getPane(), new Tooltip("Classification resolution: \n" + resolution));
		miniViewer.setDownsample(resolution.getDownsampleFactor(server.getPixelCalibration().getAveragedPixelSizeMicrons()));
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
	private static String getResultsString(ImageServer<BufferedImage> classifierServer, double x, double y, int z, int t) {
    	if (classifierServer == null)
    		return null;
    	
    	int level = 0;
    	var tile = classifierServer.getTileRequestManager().getTileRequest(level, (int)Math.round(x), (int)Math.round(y), z, t);
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
    	
    	var channels = classifierServer.getMetadata().getChannels();
    	if (classifierServer.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION) {
        	int sample = img.getRaster().getSample(xx, yy, 0); 		
        	return String.format("Classification: %s", channels.get(sample).getName());
//        	return String.format("Classification (%s):\n%s", coords, channels.get(sample).getName());
    	} else {
    		String[] array = new String[channels.size()];
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
			if (!event.isChanging() && !event.isObjectMeasurementEvent() && (event.isStructureChangeEvent() || event.isObjectClassificationEvent() || !event.getChangedObjects().isEmpty())) {
				if (event.isObjectClassificationEvent()  || event.getChangedObjects().stream().anyMatch(p -> p.getPathClass() != null)) {
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
				var channels = server.getMetadata().getChannels();
				if (imp.getNChannels() == 1)
					colorModel = ColorModelFactory.getIndexedColorModel(channels);
				else
					colorModel = ColorModelFactory.geProbabilityColorModel8Bit(channels);
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
		
		public abstract OpenCVFeatureCalculator build(ImageData<BufferedImage> imageData, double requestedPixelSize);
		
		public boolean canCustomize() {
			return false;
		}
		
		public boolean doCustomize() {
			throw new UnsupportedOperationException("Cannot customize this feature calculator!");
		}
		
//		public String getName() {
//			OpenCVFeatureCalculator calculator = build(1);
//			if (calculator == null)
//				return "No feature calculator";
//			return calculator.toString();
//		}
		
	}
	
	
	static class ExtractNeighborsFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
		
		private GridPane pane;
		
		private ObservableList<Integer> availableChannels;
		private ObservableList<Integer> selectedChannels;
		private ObservableValue<Integer> selectedRadius;
		
		ExtractNeighborsFeatureCalculatorBuilder() {
			
			int row = 0;
			
			pane = new GridPane();
			
			// Selected channels
			
			var labelChannels = new Label("Channels");
			var comboChannels = new CheckComboBox<Integer>();
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
			comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = comboChannels.getCheckModel().getCheckedItems().size();
				if (n == 0)
					return "No channels selected!";
				if (n == 1)
					return "1 channel selected";
				return n + " channels selected";
			}, comboChannels.getCheckModel().getCheckedItems()));
			
			
			var comboScales = new ComboBox<Integer>();
			var labelScales = new Label("Radius");
			comboScales.getItems().addAll(1, 2, 3, 4, 5);
			comboScales.getSelectionModel().selectFirst();
			selectedRadius = comboScales.getSelectionModel().selectedItemProperty();
			
			availableChannels = comboChannels.getItems();
			selectedChannels = comboChannels.getCheckModel().getCheckedItems();
			
			GridPaneTools.setMaxWidth(Double.MAX_VALUE, comboChannels, comboScales);
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the feature scales",
					labelScales, comboScales);					
			
			pane.setHgap(5);
			pane.setVgap(6);
			
		}

		@Override
		public OpenCVFeatureCalculator build(ImageData<BufferedImage> imageData, double requestedPixelSize) {
			return new ExtractNeighborsFeatureCalculator("Extract neighbors", requestedPixelSize, 
					selectedRadius.getValue(), selectedChannels.stream().mapToInt(i -> i).toArray());
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
			}
			return success;
			
		}
		
		@Override
		public String toString() {
			return "Extract neighbors";
		}
		
	}
	
	
	static class DefaultFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
		
		private GridPane pane;
		
		private ObservableList<Integer> availableChannels;
		private ObservableList<Integer> selectedChannels;
		private ObservableList<Double> selectedSigmas;
		private ObservableList<MultiscaleFeature> selectedFeatures;
		
		private ObservableBooleanValue doNormalize;
		private ObservableBooleanValue do3D;
		
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
			comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = comboChannels.getCheckModel().getCheckedItems().size();
				if (n == 0)
					return "No channels selected!";
				if (n == 1)
					return "1 channel selected";
				return n + " channels selected";
			}, comboChannels.getCheckModel().getCheckedItems()));
			
			
			var comboScales = new CheckComboBox<Double>();
			var labelScales = new Label("Scales");
			comboScales.getItems().addAll(0.5, 1.0, 2.0, 4.0, 8.0);
			comboScales.getCheckModel().check(1);
			selectedSigmas = comboScales.getCheckModel().getCheckedItems();
//			comboScales.getCheckModel().check(1.0);
			
			availableChannels = comboChannels.getItems();
			selectedChannels = comboChannels.getCheckModel().getCheckedItems();
			
			
			var comboFeatures = new CheckComboBox<MultiscaleFeature>();
			var labelFeatures = new Label("Features");
			comboFeatures.getItems().addAll(MultiscaleFeature.values());
			comboFeatures.getCheckModel().check(MultiscaleFeature.GAUSSIAN);
			selectedFeatures = comboFeatures.getCheckModel().getCheckedItems();
//			comboFeatures.getCheckModel().check(MultiscaleFeature.GAUSSIAN);
//			selectedChannels.addListener((Change<? extends Integer> c) -> updateFeatureCalculator());
			comboFeatures.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = selectedFeatures.size();
				if (n == 0)
					return "No features selected!";
				if (n == 1)
					return "1 feature selected";
				return n + " features selected";
			},
					selectedFeatures));
			
			
			var cbNormalize = new CheckBox("Do local normalization");
			doNormalize = cbNormalize.selectedProperty();
			
			var cb3D = new CheckBox("Use 3D filters");
			do3D = cb3D.selectedProperty();
			
			
			GridPaneTools.setMaxWidth(Double.MAX_VALUE, comboChannels, comboFeatures, comboScales,
					cbNormalize, cb3D);
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the feature scales",
					labelScales, comboScales);		

			GridPaneTools.addGridRow(pane, row++, 0,
					"Choose the features",
					labelFeatures, comboFeatures);		
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Apply local intensity normalization before calculating features",
					cbNormalize, cbNormalize);		
			
			GridPaneTools.addGridRow(pane, row++, 0,
					"Use 3D filters (rather than 2D)",
					cb3D, cb3D);	

//			GridPaneTools.addGridRow(pane, row++, 0,
//					"Choose the image channels used to calculate features",
//					labelChannels, comboChannels, btnChannels);
			
			
			
			
			pane.setHgap(5);
			pane.setVgap(6);
			
		}

		@Override
		public OpenCVFeatureCalculator build(ImageData<BufferedImage> imageData, double requestedPixelSize) {
			// Extract features, removing any that are incompatible
			MultiscaleFeature[] features;
			if (do3D.get())
				features = selectedFeatures.stream().filter(f -> f.is3D()).toArray(MultiscaleFeature[]::new);
			else
				features = selectedFeatures.stream().filter(f -> f.is2D()).toArray(MultiscaleFeature[]::new);
			
			double[] sigmas = selectedSigmas.stream().mapToDouble(d -> d).toArray();
			return new MultiscaleFeatureCalculator(
					imageData,
					selectedChannels.stream().mapToInt(i -> i).toArray(),
					sigmas,
					doNormalize.get() && sigmas.length > 1 ? sigmas[sigmas.length-1] * 4.0 : 0,
					do3D.get() ? true : false,
					features
					);
//			return new MultiscaleFeatureCalculator(
//					selectedChannels, 
//					requestedPixelSize);
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
			
		}
		
		@Override
		public String toString() {
			return "Default multiscale features";
		}
		
	}
	
	
}
