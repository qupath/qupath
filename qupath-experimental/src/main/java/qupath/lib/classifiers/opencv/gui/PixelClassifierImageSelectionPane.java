package qupath.lib.classifiers.opencv.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.opencv_ml.ANN_MLP;
import org.bytedeco.javacpp.opencv_ml.DTrees;
import org.bytedeco.javacpp.opencv_ml.LogisticRegression;
import org.bytedeco.javacpp.opencv_ml.NormalBayesClassifier;
import org.bytedeco.javacpp.opencv_ml.RTrees;
import org.bytedeco.javacpp.opencv_ml.TrainData;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import impl.org.controlsfx.skin.CheckComboBoxSkin;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.PieChart.Data;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import jfxtras.scene.layout.HBox;
import qupath.lib.classifiers.gui.PixelClassificationOverlay;
import qupath.lib.classifiers.gui.PixelClassifierGUI;
import qupath.lib.classifiers.gui.PixelClassifierHelper;
import qupath.lib.classifiers.gui.PixelClassifierGUI.FeatureFilter;
import qupath.lib.classifiers.opencv.OpenCVClassifiers;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.OpenCVStatModel;
import qupath.lib.classifiers.opencv.Reclassifier;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;

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
	
	
	private static final ImageResolution RESOLUTION_FULL = DownsampledImageResolution.getInstance("Full", 1.0);
	
	private static final ImageResolution RESOLUTION_VERY_HIGH_CAL = SimpleImageResolution.getInstance("Very high", 1.0);
	private static final ImageResolution RESOLUTION_HIGH_CAL      = SimpleImageResolution.getInstance("High", 2.0);
	private static final ImageResolution RESOLUTION_MODERATE_CAL  = SimpleImageResolution.getInstance("Moderate", 4.0);
	private static final ImageResolution RESOLUTION_LOW_CAL 	  = SimpleImageResolution.getInstance("Low", 8.0);
	private static final ImageResolution RESOLUTION_VERY_LOW_CAL  = SimpleImageResolution.getInstance("Very low", 16.0);

	private static final ImageResolution RESOLUTION_VERY_HIGH = DownsampledImageResolution.getInstance("Very high", 2.0);
	private static final ImageResolution RESOLUTION_HIGH      = DownsampledImageResolution.getInstance("High", 4.0);
	private static final ImageResolution RESOLUTION_MODERATE  = DownsampledImageResolution.getInstance("Moderate", 8.0);
	private static final ImageResolution RESOLUTION_LOW 	  = DownsampledImageResolution.getInstance("Low", 16.0);
	private static final ImageResolution RESOLUTION_VERY_LOW  = DownsampledImageResolution.getInstance("Very low", 32.0);

	private List<ImageResolution> DEFAULT_CALIBRATED_RESOLUTIONS = Collections.unmodifiableList(Arrays.asList(
			RESOLUTION_FULL,
			RESOLUTION_VERY_HIGH_CAL,
			RESOLUTION_HIGH_CAL,
			RESOLUTION_MODERATE_CAL,
			RESOLUTION_LOW_CAL,
			RESOLUTION_VERY_LOW_CAL
			));
	
	private List<ImageResolution> DEFAULT_UNCALIBRATED_RESOLUTIONS = Collections.unmodifiableList(Arrays.asList(
			RESOLUTION_FULL,
			RESOLUTION_VERY_HIGH,
			RESOLUTION_HIGH,
			RESOLUTION_MODERATE,
			RESOLUTION_LOW,
			RESOLUTION_VERY_LOW
			));
	
	
	private QuPathViewer viewer;
	private GridPane pane;
	
	private ObservableList<ImageResolution> resolutions = FXCollections.observableArrayList();
	private ReadOnlyObjectProperty<ImageResolution> selectedResolution;
	
	private MiniViewerCommand.MiniViewerManager miniViewer;
	
	private BooleanProperty livePrediction = new SimpleBooleanProperty(false);
	
	private ObservableBooleanValue classificationComplete = new SimpleBooleanProperty(false);
	private ReadOnlyObjectProperty<OpenCVStatModel> selectedClassifier;

	private ReadOnlyObjectProperty<ClassificationRegion> selectedRegion;
	
	private ObservableList<FeatureFilter> selectedFeatures;

	private ObservableList<Integer> availableChannels;
	private ObservableList<Integer> selectedChannels;
	
	private ClassificationPieChart pieChart;

	private HierarchyListener hierarchyListener = new HierarchyListener();

	private ChangeListener<ImageData<BufferedImage>> imageDataListener = new ChangeListener<ImageData<BufferedImage>>() {

		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
				ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
			if (oldValue != null)
				oldValue.getHierarchy().removePathObjectListener(hierarchyListener);
			if (newValue != null)
				newValue.getHierarchy().addPathObjectListener(hierarchyListener);
		}
		
	};
	
	private Stage stage;
	
	/**
	 * Add a row of nodes.  The rowspan is always 1.  The colspan is 1 by default, 
	 * unless a Node is added multiple times consecutively in which case it is the sum 
	 * of the number of times the node is added.
	 * 
	 * @param pane
	 * @param row
	 * @param col
	 * @param nodes
	 */
	static void addGridRow(GridPane pane, int row, int col, String tooltipText, Node... nodes) {
		Node lastNode = null;
		var tooltip = tooltipText == null ? null : new Tooltip(tooltipText);
		
		for (var n : nodes) {
			if (lastNode == n) {
				Integer span = GridPane.getColumnSpan(n);
				if (span == null)
					GridPane.setColumnSpan(n, 2);
				else
					GridPane.setColumnSpan(n, span + 1);
			} else {
				pane.add(n, col, row);
				if (tooltip != null)
					Tooltip.install(n, tooltip);
			}
			lastNode = n;
			col++;
		}
	}
	
	static void setGrowAlwaysPriority(Node... nodes) {
		for (var n : nodes) {
//			GridPane.setVgrow(n, Priority.ALWAYS);
			GridPane.setHgrow(n, Priority.ALWAYS);
		}
	}
	
	static void setMaxWidth(double width, Region...regions) {
		for (var r : regions)
			r.setMaxWidth(width);
	}

	static void setMaxHeight(double height, Region...regions) {
		for (var r : regions)
			r.setMaxWidth(height);
	}
	
	static void setMaxSize(double width, double height, Region...regions) {
		for (var r : regions)
			r.setMaxSize(width, height);
	}

	static void setFillWidth(Node...nodes) {
		for (var n : nodes)
			GridPane.setFillWidth(n, Boolean.TRUE);
	}

	static void setFillHeight(Node...nodes) {
		for (var n : nodes)
			GridPane.setFillHeight(n, Boolean.TRUE);
	}

	
	public void initialize(final QuPathViewer viewer) {
		
		int row = 0;
		int col = 0;
		
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
		
		addGridRow(pane, row++, 0, 
				"Choose classifier type (RTrees is generally a good default)",
				labelClassifier, comboClassifier, btnEditClassifier);
		
		
		// Image resolution
		var labelResolution = new Label("Resolution");
		var comboResolution = new ComboBox<ImageResolution>();
		labelResolution.setLabelFor(comboResolution);
		var btnResolution = new Button("Add");
		btnResolution.setOnAction(e -> addResolution());
		selectedResolution = comboResolution.getSelectionModel().selectedItemProperty();
		
		addGridRow(pane, row++, 0, 
				"Choose the base image resolution based upon required detail in the classification (see preview on the right)",
				labelResolution, comboResolution, btnResolution);
		
		
		// Selected channels
		
		var labelChannels = new Label("Channels");
		var comboChannels = new CheckComboBox<Integer>();
		var btnChannels = new Button("Select");
		btnChannels.setOnAction(e -> selectChannels());
		var server = QuPathGUI.getInstance().getViewer().getServer();
		if (server != null) {
			for (int c = 0; c < server.nChannels(); c++)
				comboChannels.getItems().add(c);			
			comboChannels.getCheckModel().checkAll();
		}
		comboChannels.setConverter(new StringConverter<Integer>() {
			
			@Override
			public String toString(Integer object) {
				return server.getChannelName(object);
			}
			
			@Override
			public Integer fromString(String string) {
				for (int i = 0; i < server.nChannels(); i++) {
					if (string.equals(server.getChannelName(i)))
						return i;
				}
				return null;
			}
		});
		availableChannels = comboChannels.getItems();
		selectedChannels = comboChannels.getCheckModel().getCheckedItems();
		selectedChannels.addListener((Change<? extends Integer> c) -> updateFeatureCalculator());
		
		labelResolution.setLabelFor(comboChannels);
		
		addGridRow(pane, row++, 0,
				"Choose the image channels used to calculate features",
				labelChannels, comboChannels, btnChannels);
		
		// Features
		
		var labelFeatures = new Label("Features");
		var comboFeatures = new CheckComboBox<FeatureFilter>();
		selectedFeatures = comboFeatures.getCheckModel().getCheckedItems();
		selectedFeatures.addListener((Change<? extends FeatureFilter> c) -> updateFeatureCalculator());

		
		var sigmas = new double[] {1, 2, 4, 8};
		for (var s : sigmas)
			comboFeatures.getItems().add(new PixelClassifierGUI.GaussianFeatureFilter(s));
		
		for (var s : sigmas)
			comboFeatures.getItems().add(new PixelClassifierGUI.LoGFeatureFilter(s));
		
		for (var s : sigmas)
			comboFeatures.getItems().add(new PixelClassifierGUI.SobelFeatureFilter(s));

		for (var s : sigmas)
			comboFeatures.getItems().add(new PixelClassifierGUI.CoherenceFeatureFilter(s));

		// Select the simple Gaussian features by default
		comboFeatures.getCheckModel().checkIndices(1, 2, 3);
		
		// I'd like more informative text to be displayed by default
		comboFeatures.setSkin(new CheckComboBoxSkin<FeatureFilter>(comboFeatures) {
			
			protected String buildString() {
				int n = comboFeatures.getCheckModel().getCheckedItems().size();
				if (n == 0)
					return "No features selected!";
				if (n == 1)
					return "1 feature selected";
				return n + " features selected";
			}
			
		});

		
		var labelFeaturesSummary = new Label("No features selected");
		var btnEditFeatures = new Button("Select");
		btnEditFeatures.setOnAction(e -> selectFeatures());
		
		addGridRow(pane, row++, 0, 
				"Choose the features that are available to the classifier (e.g. smoothed pixels, edges, other textures)",
				labelFeatures, comboFeatures, btnEditFeatures);
//		addGridRow(pane, row++, 0, labelFeatures, labelFeaturesSummary, btnEditFeatures);
		
		
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
		
		addGridRow(pane, row++, 0, 
				"Choose whether to apply the classifier to the whole image, or only regions containing annotations",
				labelRegion, comboRegion, comboRegion);

		
		// Live predict
		var btnLive = new ToggleButton("Live prediction");
		btnLive.selectedProperty().bindBidirectional(livePrediction);
		livePrediction.addListener((v, o, n) -> {
			if (n)
				updateClassifier(n);
		});
		
		var btnSave = new Button("Save & Apply");
		btnSave.setOnAction(e -> saveAndApply());
		
		var panePredict = new HBox(btnLive, btnSave);
		setMaxWidth(Double.MAX_VALUE, btnLive, btnSave);
		HBox.setHgrow(btnLive, Priority.ALWAYS);
		HBox.setHgrow(btnSave, Priority.ALWAYS);
		
		
		pane.add(panePredict, 0, row++, pane.getColumnCount(), 1);

		
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
		
		addGridRow(pane, row++, 0, 
				null,
//				"View information about the current classifier training",
				chart, chart, chart);
		
		comboClassifier.getItems().addAll(
				OpenCVClassifiers.wrapStatModel(RTrees.create()),
				OpenCVClassifiers.wrapStatModel(DTrees.create()),
				OpenCVClassifiers.wrapStatModel(ANN_MLP.create()),
				OpenCVClassifiers.wrapStatModel(LogisticRegression.create()),
				OpenCVClassifiers.wrapStatModel(NormalBayesClassifier.create())
				);
		comboClassifier.getSelectionModel().clearAndSelect(0);
		
//		comboResolution.setMaxWidth(Double.MAX_VALUE);
//		labelFeaturesSummary.setMaxWidth(Double.MAX_VALUE);
		
		setGrowAlwaysPriority(comboResolution, comboChannels, comboClassifier, comboFeatures, labelFeaturesSummary);
		setFillWidth(comboResolution, comboChannels, comboClassifier, comboFeatures, labelFeaturesSummary);
		
		
		
		miniViewer = new MiniViewerCommand.MiniViewerManager(viewer, 0);
		var viewerPane = miniViewer.getPane();
		GridPane.setFillWidth(viewerPane, Boolean.TRUE);
		GridPane.setFillHeight(viewerPane, Boolean.TRUE);
		GridPane.setHgrow(viewerPane, Priority.ALWAYS);
		GridPane.setVgrow(viewerPane, Priority.ALWAYS);
		
		if (viewer.getImageData().getServer().hasPixelSizeMicrons())
			resolutions.setAll(DEFAULT_CALIBRATED_RESOLUTIONS);
		else
			resolutions.setAll(DEFAULT_UNCALIBRATED_RESOLUTIONS);			
		comboResolution.setItems(resolutions);
		selectedResolution.addListener((v, o, n) -> {
			updateResolution(n);
			updateClassifier();
		});
		if (!comboResolution.getItems().isEmpty())
			comboResolution.getSelectionModel().clearAndSelect(comboResolution.getItems().size()/2);
		
		pane.setHgap(5);
		pane.setVgap(6);

		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(classificationComplete);
		btnCreateObjects.setOnAction(e -> createObjects());
		
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.disableProperty().bind(classificationComplete);
		btnClassifyObjects.setOnAction(e -> classifyObjects());
		
		setMaxWidth(Double.MAX_VALUE, btnCreateObjects, btnClassifyObjects);
		HBox.setHgrow(btnCreateObjects, Priority.ALWAYS);
		HBox.setHgrow(btnClassifyObjects, Priority.ALWAYS);
		var panePostProcess = new HBox(btnCreateObjects, btnClassifyObjects);
		
		pane.add(panePostProcess, 0, row++, pane.getColumnCount(), 1);

		setMaxWidth(Double.MAX_VALUE, pane.getChildren().stream().filter(p -> p instanceof Region).toArray(Region[]::new));
		
		var splitPane = new SplitPane(pane, viewerPane);
		viewerPane.setPrefSize(400, 400);
		splitPane.setDividerPositions(0.5);
		
		pane.setPadding(new Insets(5));
		
		stage = new Stage();
		stage.setTitle("Pixel classifier (demo)");
		stage.setScene(new Scene(splitPane));
		
		stage.initOwner(QuPathGUI.getInstance().getStage());
		
		stage.getScene().getRoot().disableProperty().bind(
				QuPathGUI.getInstance().viewerProperty().isNotEqualTo(viewer)
				);
		
		stage.show();
		stage.setOnCloseRequest(e -> destroy());
		
		
		viewer.getImageDataProperty().addListener(imageDataListener);
		if (viewer.getImageData() != null)
			viewer.getImageData().getHierarchy().addPathObjectListener(hierarchyListener);
		
	}
	
	private OpenCVFeatureCalculator featureCalculator;
	private PixelClassifierHelper helper;

	
	void updateFeatureCalculator() {
		featureCalculator = new PixelClassifierGUI.BasicFeatureCalculator("Basic features", selectedChannels, selectedFeatures, 
				getRequestedPixelSizeMicrons());
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
		
		if (selectedChannels == null || selectedChannels.isEmpty()) {
			DisplayHelpers.showErrorNotification("Pixel classifier", "No channels selected!");
			return;
		}

		if (selectedFeatures == null || selectedFeatures.isEmpty()) {
			DisplayHelpers.showErrorNotification("Pixel classifier", "No features selected!");
			return;
		}

		 helper.updateTrainingData();
		 
		 TrainData trainData = helper.getTrainData();
		 if (trainData == null) {
			 pieChart.setData(Collections.emptyList(), false);
			 return;
		 }

		 List<PixelClassifierOutputChannel> channels = helper.getChannels();

		 // TODO: Optionally limit the number of training samples we use
		 //	     		var trainData = classifier.createTrainData(matFeatures, matTargets);
		 int maxSamples = 100_000;
		 if (maxSamples > 0 && trainData.getNTrainSamples() > maxSamples)
			 trainData.setTrainTestSplit(maxSamples, true);
		 else
			 trainData.shuffleTrainTest();
		 
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
				 .inputPixelSizeMicrons(getRequestedPixelSizeMicrons())
				 .inputShape(inputWidth, inputHeight)
				 .channels(channels)
				 .build();

		 var classifier = new OpenCVPixelClassifier(model, helper.getFeatureCalculator(), helper.getLastFeaturePreprocessor(), metadata);

		 replaceOverlay(new PixelClassificationOverlay(viewer, classifier));
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
		if (overlay != null) {
			overlay.stop();
			viewer.getCustomOverlayLayers().remove(overlay);
		}
		overlay = newOverlay;
		if (overlay != null) {
			overlay.setUseAnnotationMask(selectedRegion.get() == ClassificationRegion.ANNOTATIONS_ONLY);
			viewer.getCustomOverlayLayers().add(overlay);
		}
	}



	public void destroy() {
		if (overlay != null) {
			overlay.stop();
			viewer.getCustomOverlayLayers().remove(overlay);
			overlay = null;
		}
		viewer.getImageDataProperty().removeListener(imageDataListener);
//		setImageData(viewer, viewer.getImageData(), null);
		if (helper != null)
			helper.setImageData(null);
		if (stage != null && stage.isShowing())
			stage.close();
	}
	
	
	
	boolean saveAndApply() {
		logger.info("Only applying, not saving...");
		updateClassifier(true);
//		DisplayHelpers.showErrorMessage("Save & Apply", "Not implemented yet!");
		return false;
	}
	
	
	
	boolean classifyObjects() {
		
		var hierarchy = viewer.getHierarchy();
		var pathObjects = hierarchy.getObjects(null, PathDetectionObject.class);
		var server = overlay.getPixelClassificationServer();
		var reclassifiers = pathObjects.parallelStream().map(p -> {
				try {
					var roi = PathObjectTools.getROI(p, true);
					int x = (int)Math.round(roi.getCentroidX());
					int y = (int)Math.round(roi.getCentroidY());
					int ind = server.getClassification(x, y, roi.getZ(), roi.getT());
					return new Reclassifier(p, PathClassFactory.getPathClass(overlay.getPixelClassificationServer().getChannelName(ind)));
				} catch (Exception e) {
					return new Reclassifier(p, null);
				}
			}).collect(Collectors.toList());
		reclassifiers.parallelStream().forEach(r -> r.apply());
		hierarchy.fireObjectClassificationsChangedEvent(this, pathObjects);
		return true;
	}
	
	
	boolean createObjects() {
		DisplayHelpers.showErrorMessage("Create objects", "Not implemented yet!");
		return false;
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
	
	boolean selectFeatures() {
		DisplayHelpers.showErrorMessage("Select features", "Not implemented yet!");
		return false;
	}

	boolean selectChannels() {
		DisplayHelpers.showErrorMessage("Select channels", "Not implemented yet!");
		return false;
	}
	
	boolean addResolution() {
		DisplayHelpers.showErrorMessage("Add resolution", "Not implemented yet!");
		return false;
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
		var server = viewer == null ? null : viewer.getServer();
		if (server == null || miniViewer == null || resolution == null)
			return;
		miniViewer.setDownsample(resolution.getDownsampleFactor(server.getAveragedPixelSizeMicrons()));
	}
	
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	
	
	

	static interface ImageResolution {
		
		double getDownsampleFactor(double pixelSizeMicrons);
		
	}
	
	

	static class DownsampledImageResolution implements ImageResolution {
		
		private static Map<String, DownsampledImageResolution> map = new TreeMap<>();
		
		public synchronized static DownsampledImageResolution getInstance(final String name, final double downsample) {
			var key = name + "::" + downsample;
			var resolution = map.get(key);
			if (resolution == null) {
				resolution = new DownsampledImageResolution(name, downsample);
				map.put(key, resolution);
			}
			return resolution;
		}
		

		private final String name;
		private final double downsample;
		
		private DownsampledImageResolution(String name, double downsample) {
			this.name = name;
			this.downsample = downsample;
		}
		
		@Override
		public double getDownsampleFactor(double pixelSizeMicrons) {
			return downsample;
		}
		
		@Override
		public String toString() {
			String text = "Downsample " + GeneralTools.formatNumber(downsample, 5);
			if (name == null)
				return text;
			else if (downsample == 1)
				return name;
			else
				return String.format("%s (%s)", name, text);
		}
		
	}
	
	static class SimpleImageResolution implements ImageResolution {
		
		private static Map<String, SimpleImageResolution> map = new TreeMap<>();
		
		private final String name;
		private final double requestedPixelSizeMicrons;
		
		public synchronized static SimpleImageResolution getInstance(final String name, final double requestedPixelSizeMicrons) {
			var key = name + "::" + requestedPixelSizeMicrons;
			var resolution = map.get(key);
			if (resolution == null) {
				resolution = new SimpleImageResolution(name, requestedPixelSizeMicrons);
				map.put(key, resolution);
			}
			return resolution;
		}
		
		private SimpleImageResolution(String name, double requestedPixelSizeMicrons) {
			this.name = name;
			this.requestedPixelSizeMicrons = requestedPixelSizeMicrons;
		}
		
		public double getDownsampleFactor(double pixelSizeMicrons) {
			return requestedPixelSizeMicrons / pixelSizeMicrons;
		}
		
		@Override
		public String toString() {
			String text = GeneralTools.formatNumber(requestedPixelSizeMicrons, 5) + " " + GeneralTools.micrometerSymbol() + " per pixel";
			if (name == null)
				return text;
			else
				return String.format("%s (%s)", name, text);
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
	
	
	class HierarchyListener implements PathObjectHierarchyListener {

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (livePrediction.get() && !event.isChanging() && (event.isStructureChangeEvent() || event.isObjectClassificationEvent())) {
				if (event.isObjectClassificationEvent() || event.getChangedObjects().stream().anyMatch(p -> p.getPathClass() != null)) {
					if (event.getChangedObjects().stream().anyMatch(p -> p.isAnnotation()))
						updateClassifier();
				}
			}
		}
		
	}
	

}
