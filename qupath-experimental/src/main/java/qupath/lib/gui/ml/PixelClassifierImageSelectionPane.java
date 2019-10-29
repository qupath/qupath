package qupath.lib.gui.ml;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.openblas.global.openblas;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
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
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJTools;
import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
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
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.pixel.FeatureImageServer;
import qupath.opencv.ml.pixel.OpenCVPixelClassifiers;
import qupath.opencv.ml.pixel.PixelClassifierHelper;


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
	
	private ObservableList<ClassificationResolution> resolutions = FXCollections.observableArrayList();
	private ComboBox<ClassificationResolution> comboResolutions = new ComboBox<>(resolutions);
	private ReadOnlyObjectProperty<ClassificationResolution> selectedResolution;
	
	// To display features as overlays across the image
	private ComboBox<String> comboDisplayFeatures = new ComboBox<>();
	private Slider sliderFeatureOpacity = new Slider(0.0, 1.0, 1.0);
	private Spinner<Double> spinFeatureMin = new Spinner<>(-Double.MAX_VALUE, Double.MAX_VALUE, 0);
	private Spinner<Double> spinFeatureMax = new Spinner<>(-Double.MAX_VALUE, Double.MAX_VALUE, 1.0);
	private String DEFAULT_CLASSIFICATION_OVERLAY = "Show classification";

	
	private MiniViewerCommand.MiniViewerManager miniViewer;
	
	private BooleanProperty livePrediction = new SimpleBooleanProperty(false);
	
	private ObservableBooleanValue classificationComplete = new SimpleBooleanProperty(false);
	private ReadOnlyObjectProperty<OpenCVStatModel> selectedClassifier;

	private ReadOnlyObjectProperty<ClassificationRegion> selectedRegion;
	
	private ReadOnlyObjectProperty<FeatureCalculatorBuilder> selectedFeatureCalculatorBuilder;

	private ReadOnlyObjectProperty<ImageServerMetadata.ChannelType> selectedOutputType;
	
	private StringProperty cursorLocation = new SimpleStringProperty();
	
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
			updateTitle();
			updateAvailableResolutions();
		}
		
	};
	
	private Stage stage;
	
	
	public PixelClassifierImageSelectionPane(final QuPathViewer viewer) {
		this.viewer = viewer;
		helper = new PixelClassifierHelper(viewer.getImageData(), null);
		featureRenderer = new FeatureRenderer(viewer.getImageRegionStore());
		initialize(viewer);
	}

	
	private void initialize(final QuPathViewer viewer) {
		
		int row = 0;
		
		// Classifier
		pane = new GridPane();
		
		// TODO: Check if openblas multithreading continues to have trouble with Mac/Linux
		if (!GeneralTools.isWindows())
			openblas.blas_set_num_threads(1);

		var labelClassifier = new Label("Classifier");
		var comboClassifier = new ComboBox<OpenCVStatModel>();
		labelClassifier.setLabelFor(comboClassifier);
		
		selectedClassifier = comboClassifier.getSelectionModel().selectedItemProperty();
		selectedClassifier.addListener((v, o, n) -> updateClassifier());
		var btnEditClassifier = new Button("Edit");
		btnEditClassifier.setOnAction(e -> editClassifierParameters());
		btnEditClassifier.disableProperty().bind(selectedClassifier.isNull());
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				"Choose classifier type (RTrees or ANN_MLP are generally good choices)",
				labelClassifier, comboClassifier, comboClassifier, btnEditClassifier);
		
//		// Boundary strategy
//		boundaryStrategy.addListener((v, o, n) -> {
//			updateClassifier();
//		});
		
		// Image resolution
		var labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		var btnResolution = new Button("Add");
		btnResolution.setOnAction(e -> addResolution());
		selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				"Choose the base image resolution based upon required detail in the classification (see preview on the right)",
				labelResolution, comboResolutions, comboResolutions, btnResolution);
		
		
		// Features
		var labelFeatures = new Label("Features");
		var comboFeatures = new ComboBox<FeatureCalculatorBuilder>();
		comboFeatures.getItems().add(new FeatureCalculatorBuilder.DefaultFeatureCalculatorBuilder(viewer.getImageData()));
		comboFeatures.getItems().add(new FeatureCalculatorBuilder.ExtractNeighborsFeatureCalculatorBuilder(viewer.getImageData()));
		labelFeatures.setLabelFor(comboFeatures);
		selectedFeatureCalculatorBuilder = comboFeatures.getSelectionModel().selectedItemProperty();
		
//		var labelFeaturesSummary = new Label("No features selected");
		var btnShowFeatures = new Button("Show");
		btnShowFeatures.setOnAction(e -> showFeatures());
		
		var btnCustomizeFeatures = new Button("Edit");
		btnCustomizeFeatures.disableProperty().bind(Bindings.createBooleanBinding(() -> {
			var calc = selectedFeatureCalculatorBuilder.get();
			return calc == null || !calc.canCustomize(viewer.getImageData());
		},
				selectedFeatureCalculatorBuilder));
		btnCustomizeFeatures.setOnAction(e -> {
			if (selectedFeatureCalculatorBuilder.get().doCustomize(viewer.getImageData())) {
				updateFeatureCalculator();
			}
		});
		comboFeatures.getItems().addAll(defaultFeatureCalculatorBuilders);
		
		comboFeatures.getSelectionModel().select(0);
		comboFeatures.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateFeatureCalculator());
//		btnCustomizeFeatures.setOnAction(e -> showFeatures());
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
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
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				"Choose whether to output classifications only, or estimated probabilities per class (not all classifiers support probabilities, which also require more memory)",
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
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				"Choose whether to apply the classifier to the whole image, or only regions containing annotations",
				labelRegion, comboRegion, comboRegion, comboRegion);

		
		// Live predict
		var btnAdvancedOptions = new Button("Advanced options");
		btnAdvancedOptions.setTooltip(new Tooltip("Advanced options to customize preprocessing and classifier behavior"));
		btnAdvancedOptions.setOnAction(e -> {
			if (showAdvancedOptions())
				updateClassifier();
		});
		
		var btnLive = new ToggleButton("Live prediction");
		btnLive.selectedProperty().bindBidirectional(livePrediction);
		btnLive.setTooltip(new Tooltip("Toggle whether to calculate classification 'live' while viewing the image"));
		livePrediction.addListener((v, o, n) -> {
			if (overlay == null) {
				if (n) {
					updateClassifier(n);				
					return;
				}
			} else {
				overlay.setLivePrediction(n);
			}
			if (featureOverlay != null)
				featureOverlay.setLivePrediction(n);
		});
				
		var panePredict = PaneToolsFX.createColumnGridControls(btnAdvancedOptions, btnLive);
		pane.add(panePredict, 0, row++, pane.getColumnCount(), 1);
		
//		addGridRow(pane, row++, 0, btnPredict, btnPredict, btnPredict);

		var btnSave = new Button("Save");
		btnSave.setMaxWidth(Double.MAX_VALUE);
		btnSave.setOnAction(e -> saveAndApply());
		pane.add(btnSave, 0, row++, pane.getColumnCount(), 1);
		
		
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
		Tooltip.install(chart, new Tooltip("View training classes by proportion"));
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				null,
//				"View information about the current classifier training",
				chart, chart, chart);
		
		
		// Label showing cursor location
		var labelCursor = new Label();
		labelCursor.textProperty().bindBidirectional(cursorLocation);
		labelCursor.setMaxWidth(Double.MAX_VALUE);
		labelCursor.setAlignment(Pos.CENTER);
		
		PaneToolsFX.addGridRow(pane, row++, 0, 
				"Prediction for current cursor location",
				labelCursor, labelCursor, labelCursor);
		
		comboClassifier.getItems().addAll(
				OpenCVClassifiers.createStatModel(RTrees.class),
				OpenCVClassifiers.createStatModel(ANN_MLP.class),
				OpenCVClassifiers.createStatModel(LogisticRegression.class),
				OpenCVClassifiers.createStatModel(KNearest.class)
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
		
		PaneToolsFX.setHGrowPriority(Priority.ALWAYS, comboResolutions, comboClassifier, comboFeatures);
		PaneToolsFX.setFillWidth(Boolean.TRUE, comboResolutions, comboClassifier, comboFeatures);
		
		
		
		miniViewer = new MiniViewerCommand.MiniViewerManager(viewer, 0);
		var viewerPane = miniViewer.getPane();
//		GridPane.setFillWidth(viewerPane, Boolean.TRUE);
//		GridPane.setFillHeight(viewerPane, Boolean.TRUE);
//		GridPane.setHgrow(viewerPane, Priority.ALWAYS);
//		GridPane.setVgrow(viewerPane, Priority.ALWAYS);
		Tooltip.install(viewerPane, new Tooltip("View image at classification resolution"));
		
		updateAvailableResolutions();	
		selectedResolution.addListener((v, o, n) -> {
			updateResolution(n);
			updateClassifier();
			updateFeatureOverlay();
		});
		if (!comboResolutions.getItems().isEmpty())
			comboResolutions.getSelectionModel().clearAndSelect(resolutions.size()/2);
		
		pane.setHgap(5);
		pane.setVgap(6);
		
//		var btnSavePrediction = new Button("Save prediction image");
//		btnSavePrediction.setMaxWidth(Double.MAX_VALUE);
//		btnSavePrediction.setOnAction(e -> saveAndApply());
//		pane.add(btnSavePrediction, 0, row++, pane.getColumnCount(), 1);

		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.setTooltip(new Tooltip("Create annotations or detections from pixel classification"));
		btnCreateObjects.disableProperty().bind(classificationComplete);
		btnCreateObjects.setOnAction(e -> {
			var server = getClassificationServerOrShowError();
			var imageData = viewer.getImageData();
			if (imageData != null && server != null)
				promptToCreateObjects(imageData, server);
		});
		
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.setTooltip(new Tooltip("Assign classifications to detection objects based on the corresponding pixel classification"));
		btnClassifyObjects.disableProperty().bind(classificationComplete);
		btnClassifyObjects.setOnAction(e -> classifyObjects());
		
		var panePostProcess = PaneToolsFX.createColumnGridControls(btnCreateObjects, btnClassifyObjects);
				
		pane.add(panePostProcess, 0, row++, pane.getColumnCount(), 1);

		PaneToolsFX.setMaxWidth(Double.MAX_VALUE, pane.getChildren().stream().filter(p -> p instanceof Region).toArray(Region[]::new));
		
		var viewerBorderPane = new BorderPane(viewerPane);
		
		comboDisplayFeatures.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateFeatureOverlay());
		comboDisplayFeatures.setMaxWidth(Double.MAX_VALUE);
		spinFeatureMin.setPrefWidth(100);
		spinFeatureMax.setPrefWidth(100);
		spinFeatureMin.valueProperty().addListener((v, o, n) -> updateFeatureDisplayRange());
		spinFeatureMax.valueProperty().addListener((v, o, n) -> updateFeatureDisplayRange());
		sliderFeatureOpacity.valueProperty().addListener((v, o, n) -> {
			if (featureOverlay != null) {
				featureOverlay.setOpacity(n.doubleValue());
			}
			if (overlay != null)
				overlay.setOpacity(n.doubleValue());
			viewer.repaint();
		});

		var btnFeatureAuto = new Button("Auto");
		btnFeatureAuto.setOnAction(e -> autoFeatureContrast());
		comboDisplayFeatures.getItems().setAll(DEFAULT_CLASSIFICATION_OVERLAY);
		comboDisplayFeatures.getSelectionModel().select(DEFAULT_CLASSIFICATION_OVERLAY);
		var featureDisableBinding = comboDisplayFeatures.valueProperty().isEqualTo(DEFAULT_CLASSIFICATION_OVERLAY).or(comboDisplayFeatures.valueProperty().isNull());
		btnFeatureAuto.disableProperty().bind(featureDisableBinding);
		btnFeatureAuto.setMaxHeight(Double.MAX_VALUE);
		spinFeatureMin.disableProperty().bind(featureDisableBinding);
		spinFeatureMin.setEditable(true);
		spinFeatureMax.disableProperty().bind(featureDisableBinding);
		spinFeatureMax.setEditable(true);
		var paneFeatures = new GridPane();
		comboDisplayFeatures.setTooltip(new Tooltip("Choose classification result or feature overlay to display (Warning: This requires a lot of memory & computation!)"));
		spinFeatureMin.setTooltip(new Tooltip("Min display value for feature overlay"));
		spinFeatureMax.setTooltip(new Tooltip("Max display value for feature overlay"));
		sliderFeatureOpacity.setTooltip(new Tooltip("Adjust classification/feature overlay opacity"));
		
		PaneToolsFX.addGridRow(paneFeatures, 0, 0, null,
				comboDisplayFeatures, comboDisplayFeatures, comboDisplayFeatures, comboDisplayFeatures);
		PaneToolsFX.addGridRow(paneFeatures, 1, 0, null,
				sliderFeatureOpacity, spinFeatureMin, spinFeatureMax, btnFeatureAuto);
//		paneFeatures.add(btnFeatureAuto, 3, 0, 1, 2);
		
//		PaneToolsFX.addGridRow(paneFeatures, 0, 0, null,
//				comboDisplayFeatures, spinFeatureMin);
//		PaneToolsFX.addGridRow(paneFeatures, 1, 0, null,
//				sliderFeatureOpacity, spinFeatureMax);
//		paneFeatures.add(btnFeatureAuto, 2, 0, 1, 2);
		
		var factory = new Callback<ListView<String>, ListCell<String>>() {

			@Override
			public ListCell<String> call(ListView<String> param) {
				var listCell = new ListCell<String>() {
					@Override
					public void updateItem(String value, boolean empty) {
						super.updateItem(value, empty);
						if (value == null || empty)
							setText(null);
						else
							setText(value);
					}
				};
				listCell.setTextOverrun(OverrunStyle.ELLIPSIS);
				return listCell;
			}
		};
		comboDisplayFeatures.setCellFactory(factory);
		comboDisplayFeatures.setButtonCell(factory.call(null));
		
		PaneToolsFX.setMaxWidth(Double.MAX_VALUE, comboDisplayFeatures, sliderFeatureOpacity);
		PaneToolsFX.setFillWidth(Boolean.TRUE, comboDisplayFeatures, sliderFeatureOpacity);
		PaneToolsFX.setHGrowPriority(Priority.ALWAYS, comboDisplayFeatures, sliderFeatureOpacity);
		paneFeatures.setHgap(5);
		paneFeatures.setVgap(5);
		paneFeatures.setPadding(new Insets(5));
		paneFeatures.prefWidthProperty().bind(viewerBorderPane.prefWidthProperty());
		viewerBorderPane.setBottom(paneFeatures);
		
//		var splitPane = new SplitPane(new ScrollPane(pane), viewerBorderPane);
		var splitPane = new BorderPane(viewerBorderPane);
		splitPane.setLeft(pane);
		pane.setPrefWidth(400);
		pane.setMinHeight(GridPane.USE_PREF_SIZE);
//		viewerBorderPane.setMinWidth(0);
//		viewerPane.setPrefSize(400, 400);
//		splitPane.setDividerPositions(0.5);
		
//		SplitPane.setResizableWithParent(pane, Boolean.FALSE);
//		SplitPane.setResizableWithParent(viewerBorderPane, Boolean.FALSE);
		
		var fullPane = new StackPane(splitPane);
		
		pane.setPadding(new Insets(5));
		
		stage = new Stage();
		stage.setScene(new Scene(fullPane));

		stage.initOwner(QuPathGUI.getInstance().getStage());
		
		stage.getScene().getRoot().disableProperty().bind(
				QuPathGUI.getInstance().viewerProperty().isNotEqualTo(viewer)
				);
		
		updateTitle();
		
		updateFeatureCalculator();
		
//		pane.getChildren().stream().forEach(c -> {
//			if (c instanceof Control)
//				((Control)c).setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
//		});
		PaneToolsFX.setMinWidth(
				Region.USE_PREF_SIZE,
				PaneToolsFX.getContentsOfType(stage.getScene().getRoot(), Region.class, true).toArray(Region[]::new));
		
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
	
	private PixelClassifierHelper helper;

	
	private static List<String> resolutionNames = Arrays.asList("Full", "Very high", "High", "Moderate", "Low", "Very low", "Extremely low");
	
	/**
	 * Get a list of default resolutions to show, derived from PixelCalibration objects.
	 * @param imageData
	 * @param selected
	 * @return
	 */
	public static List<ClassificationResolution> getDefaultResolutions(ImageData<?> imageData, ClassificationResolution selected) {
		var temp = new ArrayList<ClassificationResolution>();
		PixelCalibration cal = imageData.getServer().getPixelCalibration();

		int scale = 1;
		for (String name : resolutionNames) {
			var newResolution = new ClassificationResolution(name, cal.createScaledInstance(scale, scale, 1));
			if (Objects.equals(selected, newResolution))
				temp.add(selected);
			else
				temp.add(newResolution);
			scale *= 2;
		}
		if (selected == null)
			selected = temp.get(0);
		else if (!temp.contains(selected))
			temp.add(selected);
		
		return temp;
	}
	
	private void updateAvailableResolutions() {
		var imageData = viewer.getImageData();
		if (imageData == null) {
			resolutions.clear();
			return;
		}
		var selected = selectedResolution.get();
		resolutions.setAll(getDefaultResolutions(imageData, selected));
		comboResolutions.getSelectionModel().select(selected);
	}
	
	
	private void updateFeatureCalculator() {
		var cal = getSelectedResolution();
		helper.setFeatureCalculator(selectedFeatureCalculatorBuilder.get().build(viewer.getImageData(), cal));
		var featureServer = helper.getFeatureServer();
		if (featureServer == null) {
			comboDisplayFeatures.getItems().setAll(DEFAULT_CLASSIFICATION_OVERLAY);
		} else {
			List<String> featureNames = new ArrayList<>();
			featureNames.add(DEFAULT_CLASSIFICATION_OVERLAY);
			for (var channel : featureServer.getMetadata().getChannels())
				featureNames.add(channel.getName());
			comboDisplayFeatures.getItems().setAll(featureNames);
		}
		comboDisplayFeatures.getSelectionModel().select(DEFAULT_CLASSIFICATION_OVERLAY);
		updateClassifier();
	}
	
	private PixelClassificationOverlay featureOverlay = null;
	private FeatureRenderer featureRenderer;
	
	private void autoFeatureContrast() {
		if (featureRenderer != null && featureRenderer.selectedChannel != null) {
			featureRenderer.autoSetDisplayRange();
			double min = (double)featureRenderer.selectedChannel.getMinDisplay();
			double max = (double)featureRenderer.selectedChannel.getMaxDisplay();
			spinFeatureMin.getValueFactory().setValue(min);
			spinFeatureMax.getValueFactory().setValue(max);
//			viewer.repaint();
		}
	}
	
	private void updateFeatureOverlay() {
		if (featureOverlay != null) {
			featureOverlay.setVisible(false);
			featureOverlay.stop();
			if (viewer.getCustomPixelLayerOverlay() == featureOverlay)
				viewer.resetCustomPixelLayerOverlay();
			featureOverlay = null;
		}
		var featureServer = helper.getFeatureServer();
		String featureName = comboDisplayFeatures.getSelectionModel().getSelectedItem();
		if (DEFAULT_CLASSIFICATION_OVERLAY.equals(featureName)) {
			if (overlay != null)
				overlay.setImageData(viewer.getImageData());
			viewer.setCustomPixelLayerOverlay(overlay);
			return;
		}
		int channel = -1;
		if (featureServer != null && featureName != null) {
			for (int c = 0; c < featureServer.nChannels(); c++) {
				if (featureName.equals(featureServer.getChannel(c).getName())) {
					channel = c;
					break;
				}
			}
			if (channel >= 0) {
				featureRenderer.setChannel(featureServer, channel, spinFeatureMin.getValue(), spinFeatureMax.getValue());
				featureOverlay = PixelClassificationOverlay.createFeatureDisplayOverlay(viewer, featureServer, featureRenderer);
				((PixelClassificationOverlay)featureOverlay).setLivePrediction(true);
//				featureOverlay = new ImageServerOverlay(viewer, featureServer);
//				featureOverlay.setRenderer(featureRenderer);
				featureOverlay.setOpacity(sliderFeatureOpacity.getValue());
				featureOverlay.setLivePrediction(livePrediction.get());
				autoFeatureContrast();
			}
		}
		if (featureOverlay != null)
			viewer.setCustomPixelLayerOverlay(featureOverlay);
	}
	
	
	private void ensureOverlaySet() {
		updateFeatureOverlay();
	}
	
	
	private void updateFeatureDisplayRange() {
		if (featureRenderer == null)
			return;
		featureRenderer.setRange(spinFeatureMin.getValue(), spinFeatureMax.getValue());
		viewer.repaint();
	}
	
	static class FeatureRenderer extends AbstractImageRenderer {
		
		private DefaultImageRegionStore store;
		private ChannelDisplayInfo.DirectServerChannelInfo selectedChannel = null;
		private WeakReference<ImageData<BufferedImage>> currentData;
		
		FeatureRenderer(DefaultImageRegionStore store) {
			this.store = store;
		}
				
		public void setChannel(ImageServer<BufferedImage> server, int channel, double min, double max) {
			var temp = currentData == null ? null : currentData.get();
			if (temp == null || temp.getServer() != server) {
				temp = new ImageData<>(server);
				currentData = new WeakReference<ImageData<BufferedImage>>(temp);
			}
			selectedChannel = new ChannelDisplayInfo.DirectServerChannelInfo(temp, channel);
			selectedChannel.setLUTColor(255, 255, 255);
//			autoSetDisplayRange();
			setRange(min, max);
			this.timestamp = System.currentTimeMillis();
		}
		
		public void setRange(double min, double max) {
			if (selectedChannel != null) {
				selectedChannel.setMinDisplay((float)min);
				selectedChannel.setMaxDisplay((float)max);			
				this.timestamp = System.currentTimeMillis();
			}
		}
		
		void autoSetDisplayRange() {
			if (selectedChannel == null)
				return;
			var imageData = currentData.get();
			Map<RegionRequest, BufferedImage> tiles = store == null || imageData == null ? Collections.emptyMap() : store.getCachedTilesForServer(imageData.getServer());
			
			float maxVal = Float.NEGATIVE_INFINITY;
			float minVal = Float.POSITIVE_INFINITY;
			float[] pixels = null;
			for (var tile : tiles.values()) {
				int n = tile.getWidth() * tile.getHeight();
				if (pixels != null && pixels.length < n)
					pixels = null;
				pixels = tile.getRaster().getSamples(0, 0, tile.getWidth(), tile.getHeight(), selectedChannel.getChannel(), pixels);
				for (float v : pixels) {
					if (!Float.isFinite(v))
						continue;
					if (v > maxVal)
						maxVal = v;
					if (v < minVal)
						minVal = v;
				}
			}
			if (Float.isFinite(maxVal))
				selectedChannel.setMaxDisplay(maxVal);
			else
				selectedChannel.setMaxDisplay(1.0f);
			
			if (Float.isFinite(minVal))
				selectedChannel.setMinDisplay(minVal);
			else
				selectedChannel.setMinDisplay(0.0f);
			this.timestamp = System.currentTimeMillis();
		}

		@Override
		public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
			return ImageDisplay.applyTransforms(imgInput, imgOutput, Collections.singletonList(selectedChannel), true);
		}
				
	}
	
	
	private void updateClassifier() {
		updateClassifier(livePrediction.get());
	}
	
	
	
	private void updateClassifier(boolean doClassification) {
				
		helper.setImageData(viewer.getImageData());
		
		if (doClassification)
			doClassification();
		else
			replaceOverlay(null);
	}
	
	private boolean reweightSamples = false;
	private int maxSamples = 100_000;
	private int rngSeed = 100;
	
	private boolean showAdvancedOptions() {
		
		var existingStrategy = helper.getBoundaryStrategy();
		
		List<BoundaryStrategy> boundaryStrategies = new ArrayList<>();
		boundaryStrategies.add(BoundaryStrategy.getSkipBoundaryStrategy());
		boundaryStrategies.add(BoundaryStrategy.getDerivedBoundaryStrategy(1));
		for (var pathClass : QuPathGUI.getInstance().getAvailablePathClasses())
			boundaryStrategies.add(BoundaryStrategy.getClassifyBoundaryStrategy(pathClass, 1));
		
		String PCA_NONE = "No feature reduction";
		String PCA_BASIC = "Do PCA projection";
		String PCA_NORM = "Do PCA projection + normalize output";
		
		String pcaChoice = PCA_NONE;
		if (helper.getPCARetainedVariance() > 0) {
			if (helper.doPCANormalize())
				pcaChoice = PCA_NORM;
			else
				pcaChoice = PCA_BASIC;
		}
		
		
		var params = new ParameterList()
				.addTitleParameter("Training data")
				.addIntParameter("maxSamples", "Maximum samples", maxSamples, null, "Maximum number of training samples")
				.addIntParameter("rngSeed", "RNG seed", rngSeed, null, "Seed for the random number generator used when training (not relevant to all classifiers)")
				.addBooleanParameter("reweightSamples", "Reweight samples", reweightSamples, "Weight training samples according to frequency")
				.addTitleParameter("Preprocessing")
				.addChoiceParameter("normalization", "Feature normalization", helper.getNormalization(),
						Arrays.asList(Normalization.values()), "Method to normalize features")
				.addChoiceParameter("featureReduction", "Feature reduction", pcaChoice, List.of(PCA_NONE, PCA_BASIC, PCA_NORM), 
						"Use Principal Component Analysis for feature reduction (must also specify retained variance)")
				.addDoubleParameter("pcaRetainedVariance", "PCA retained variance", helper.getPCARetainedVariance(), "",
						"Retained variance if applying Principal Component Analysis for dimensionality reduction. Should be between 0 and 1; if <= 0 PCA will not be applied.")
				.addTitleParameter("Annotation boundaries")
				.addChoiceParameter("boundaryStrategy", "Boundary strategy", helper.getBoundaryStrategy(),
						boundaryStrategies,
						"Choose how annotation boundaries should influence classifier training")
				.addDoubleParameter("boundaryThickness", "Boundary thickness", existingStrategy.getBoundaryThickness(), "pixels",
						"Set the boundary thickness whenever annotation boundaries are trained separately");
		
		if (!DisplayHelpers.showParameterDialog("Advanced options", params))
			return false;
		
		reweightSamples = params.getBooleanParameterValue("reweightSamples");
		maxSamples = params.getIntParameterValue("maxSamples");
		rngSeed = params.getIntParameterValue("rngSeed");
		
		pcaChoice = (String)params.getChoiceParameterValue("featureReduction");
		boolean pcaNormalize = PCA_NORM.equals(pcaChoice);
		double pcaRetainedVariance = PCA_NONE.equals(pcaChoice) ? 0 : params.getDoubleParameterValue("pcaRetainedVariance");
		
		helper.setNormalization((Normalization)params.getChoiceParameterValue("normalization"));
		helper.setPCARetainedVariance(pcaRetainedVariance);
		helper.setPCANormalize(pcaNormalize);
		
		var strategy = (BoundaryStrategy)params.getChoiceParameterValue("boundaryStrategy");
		strategy = BoundaryStrategy.setThickness(strategy, params.getDoubleParameterValue("boundaryThickness"));
		helper.setBoundaryStrategy(strategy);
		
		return true;
	}
	
	
	
	private void doClassification() {
//		if (helper == null || helper.getFeatureServer() == null) {
////			updateFeatureCalculator();
////			updateClassifier();
//			if (helper == null) {
//				logger.error("No pixel classifier helper available!");
//				return;
//			}
//		}
		if (helper.getFeatureServer() == null) {
			DisplayHelpers.showErrorNotification("Pixel classifier", "No feature calculator available!");
			return;			
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

		 // Ensure we seed the RNG for reproducibility
		 opencv_core.setRNGSeed(rngSeed);
		 
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
		 
		 Mat weights = null;
		 if (reweightSamples) {
			 weights = new Mat(n, 1, opencv_core.CV_32FC1);
			 FloatIndexer bufferWeights = weights.createIndexer();
			 float[] weightArray = new float[rawCounts.length];
			 for (int i = 0; i < weightArray.length; i++) {
				 int c = rawCounts[i];
//				 weightArray[i] = c == 0 ? 1 : (float)1.f/c;
				 weightArray[i] = c == 0 ? 1 : (float)n/c;
			 }
			 for (int i = 0; i < n; i++) {
				 int label = buffer.get(i);
				 bufferWeights.put(i, weightArray[label]);
			 }
			 bufferWeights.release();
		 }
		 
		 trainData = model.createTrainData(trainData.getTrainSamples(), trainData.getTrainResponses(), weights);
		 model.train(trainData);
		 
		 // Calculate accuracy using whatever we can, as a rough guide to progress
		 var test = trainData.getTestSamples();
		 String testSet = "HELD-OUT TRAINING SET";
		 if (test.empty()) {
			 test = trainData.getTrainSamples();
			 testSet = "TRAINING SET";
		 } else {
			 buffer = trainData.getTestNormCatResponses().createBuffer();
		 }
		 var testResults = new Mat();
		 model.predict(test, testResults, null);
		 IntBuffer bufferResults = testResults.createBuffer();
		 int nTest = (int)testResults.rows();
		 int nCorrect = 0;
		 for (int i = 0; i < nTest; i++) {
			 if (bufferResults.get(i) == buffer.get(i))
				 nCorrect++;
		 }
		 logger.info("Current accuracy on the {}: {} %", testSet, GeneralTools.formatNumber(nCorrect*100.0/n, 1));

		 
		 trainData.close();

		 var featureCalculator = helper.getFeatureCalculator();
		 int inputWidth = featureCalculator.getInputSize().getWidth();
		 int inputHeight = featureCalculator.getInputSize().getHeight();
		 var cal = helper.getResolution();
		 PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
				 .inputResolution(cal)
				 .inputShape(inputWidth, inputHeight)
				 .setChannelType(model.supportsProbabilities() ? selectedOutputType.get() : ImageServerMetadata.ChannelType.CLASSIFICATION)
				 .outputChannels(channels)
				 .build();

		 var classifier = OpenCVPixelClassifiers.createPixelClassifier(model, featureCalculator, helper.getLastFeaturePreprocessor(), metadata, true);

		 var overlay = PixelClassificationOverlay.createPixelClassificationOverlay(viewer, classifier);
		 replaceOverlay(overlay);
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
		if (overlay != null) {
			overlay.stop();
		}
		overlay = newOverlay;
		if (overlay != null) {
			overlay.setUseAnnotationMask(selectedRegion.get() == ClassificationRegion.ANNOTATIONS_ONLY);
			overlay.setLivePrediction(livePrediction.get());
			overlay.setOpacity(sliderFeatureOpacity.getValue());
		}
		ensureOverlaySet();
	}
		
	



	private void destroy() {
		if (overlay != null) {
			var imageData = viewer.getImageData();
			if (imageData != null && PixelClassificationImageServer.getPixelLayer(imageData) == overlay.getPixelClassificationServer())
				PixelClassificationImageServer.setPixelLayer(imageData, null);
			overlay.stop();
			viewer.resetCustomPixelLayerOverlay();
			overlay = null;
		}
		if (featureOverlay != null) {
			viewer.getCustomOverlayLayers().remove(featureOverlay);
			featureOverlay = null;
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
			return saveAndApply(project, imageData, server.getClassifier());
//			var resultServer = saveAndApply(project, imageData, server.getClassifier());
//			if (resultServer != null) {
//				PixelClassificationImageServer.setPixelLayer(imageData, resultServer);
//				wasApplied = true;
//				replaceOverlay(null);
//				return true;
//			}
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage("Pixel classifier", e);
		}
		return false;
	}
	
	
	/**
	 * Get a suitable (unique) name for a pixel classifier.
	 * 
	 * @param project
	 * @param classifier
	 * @return
	 */
	static String getDefaultClassifierName(Project<BufferedImage> project, PixelClassifier classifier) {
		String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
//		String simpleName = classifier.toString();
		String simpleName = "Pixel Model";
		String name = String.format("%s %s", date, simpleName);
		Collection<String> names = null;
		try {
			names = project.getPixelClassifiers().getNames();
		} catch (Exception e) {}
		if (names == null || names.isEmpty() || !names.contains(name))
			return name;
		int i = 1;
		while (names.contains(name)) {
			name = String.format("%s %s (%d)", date, simpleName, i);
			i++;
		}
		return GeneralTools.stripInvalidFilenameChars(name);
	}
	
	
	public static String promptToSaveClassifier(Project<BufferedImage> project, PixelClassifier classifier) throws IOException {
		
		String name = getDefaultClassifierName(project, classifier);
		
		String classifierName = DisplayHelpers.promptForFilename("Save model", "Model name", name);
		if (classifierName == null)
			return null;
		
//		var pane = new GridPane();
//		pane.setHgap(5);
//		pane.setVgap(5);
//		pane.setPadding(new Insets(10));
//		pane.setMaxWidth(Double.MAX_VALUE);
//		
//		var labelGeneral = new Label("Click 'Apply' to save the prediction model & predictions in the current project.\n" +
//				"Click 'File' if you want to save either of these elsewhere.");
//		labelGeneral.setContentDisplay(ContentDisplay.CENTER);
//		
//		var label = new Label("Name");
//		var tfName = new TextField(name);
//		label.setLabelFor(tfName);
//		
//		var cbModel = new CheckBox("Save prediction model");
//		var cbImage = new CheckBox("Save prediction image");
//		var btnModel = new Button("File");
//		btnModel.setTooltip(new Tooltip("Save prediction model to a file"));
//		btnModel.setOnAction(e -> {
//			var file = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Save model", null, tfName.getText(), "Prediction model", ".json");
//			if (file != null) {
//				try (var writer = Files.newWriter(file, StandardCharsets.UTF_8)) {
//					GsonTools.getInstance(true).toJson(classifier, writer);
//				} catch (IOException e1) {
//					DisplayHelpers.showErrorMessage("Save model", e1);
//				}
//			}
//		});
//		
//		var btnImage = new Button("File");
//		btnImage.setTooltip(new Tooltip("Save prediction image to a file"));
//		btnImage.setOnAction(e -> {
//			var file = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Save image", null, tfName.getText(), "Prediction image", ".ome.tif");
//			if (file != null) {
//				try {
//					ImageWriterTools.writeImageRegion(new PixelClassificationImageServer(QuPathGUI.getInstance().getImageData(), classifier), null, file.getAbsolutePath());
//				} catch (IOException e1) {
//					DisplayHelpers.showErrorMessage("Save image", e1);
//				}
//			}
//		});
//		
//		int row = 0;
//		int col = 0;
//		GridPaneTools.addGridRow(pane, row++, col, "Input a unique classifier name", label, tfName);
//		GridPaneTools.addGridRow(pane, row++, col, "Save the classification model (can be applied to similar images)", cbModel, cbModel, btnModel);
//		GridPaneTools.addGridRow(pane, row++, col, "Save the prediction image", cbImage, cbImage, btnImage);
//		GridPaneTools.addGridRow(pane, row++, col, labelGeneral.getText(), labelGeneral, labelGeneral);
//		
//		GridPaneTools.setHGrowPriority(Priority.ALWAYS, labelGeneral, cbModel, cbImage, tfName);
//		GridPaneTools.setFillWidth(Boolean.TRUE, labelGeneral, cbModel, cbImage, tfName);
//		GridPaneTools.setMaxWidth(Double.MAX_VALUE, labelGeneral, cbModel, cbImage, tfName);
//		
//		var dialog = new Dialog<ButtonType>();
//		dialog.setTitle("Save");
//		dialog.getDialogPane().setContent(pane);
//		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
//		if (dialog.showAndWait().orElseGet(() -> ButtonType.CANCEL) == ButtonType.CANCEL)
//			return null;
////		if (!DisplayHelpers.showMessageDialog("Save & Apply", pane)) {
////			return null;
////		}
//		String classifierName = tfName.getText();	
//		
////		var classifierName = DisplayHelpers.showInputDialog("Pixel classifier", "Pixel classifier name", name);
//		if (classifierName == null || classifierName.isBlank())
//			return null;
//		classifierName = classifierName.strip();
//		if (classifierName.isBlank() || classifierName.contains("\n")) {
//			DisplayHelpers.showErrorMessage("Pixel classifier", "Classifier name must be unique, non-empty, and not contain invalid characters");
//			return null;
//		}
//		
//		// Save the classifier in the project
//		if (cbModel.isSelected()) {
			try {
				saveClassifier(project, classifier, classifierName);
			} catch (IOException e) {
				DisplayHelpers.showWarningNotification("Pixel classifier", "Unable to write classifier to JSON - classifier can't be reloaded later");
				logger.error("Error saving classifier", e);
				throw e;
			}
//		}
//		// Save the image
//		if (cbImage.isSelected()) {
//			var server = new PixelClassificationImageServer(QuPathGUI.getInstance().getImageData(), classifier);
//			var imageData = QuPathGUI.getInstance().getImageData();
//			var entry = project.getEntry(imageData);
//			var path = entry.getEntryPath();
//			ImageWriterTools.writeImageRegion(new PixelClassificationImageServer(imageData, classifier), null, file.getAbsolutePath());
//			logger.warn("Saving image now yet supported!");
//		}
		
		return classifierName;
	}
	
	
	private static void saveClassifier(Project<BufferedImage> project, PixelClassifier classifier, String classifierName) throws IOException {
		project.getPixelClassifiers().put(classifierName, classifier);
	}
	
	private static boolean saveAndApply(Project<BufferedImage> project, ImageData<BufferedImage> imageData, PixelClassifier classifier) throws IOException {
		String name = promptToSaveClassifier(project, classifier);
		if (name == null)
			return false;
		return true;
//		return PixelClassifierTools.applyClassifier(project, imageData, classifier, name);
	}
	
	private PixelClassificationImageServer getClassificationServerOrShowError() {
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return null;
		var server = overlay == null ? null : overlay.getPixelClassificationServer();
		if (server == null || !(server instanceof PixelClassificationImageServer)) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "No classifier available!");
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
		PixelClassifierTools.classifyObjectsByCentroid(server, hierarchy.getDetectionObjects(), true);
		return true;
	}
	
	
	
	
	
	public static boolean promptToCreateObjects(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> server) {
		Objects.requireNonNull(imageData);
		Objects.requireNonNull(server);
		
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
				.addDoubleParameter("minSize", "Minimum object size", 0, null, "Minimum size of a region to keep (smaller regions will be dropped)")
				.addDoubleParameter("minHoleSize", "Minimum hole size", 0, null, "Minimum size of a hole to keep (smaller holes will be filled)")
				.addChoiceParameter("sizeUnits", "Minimum object/hole size units", "Pixels", sizeUnits)
				.addBooleanParameter("doSplit", "Split objects", true,
						"Split multi-part regions into separate objects")
				.addBooleanParameter("clearExisting", "Delete existing objects", false,
						"Delete any existing objects within the selected object before adding new objects (or entire image if no object is selected)");
		
		PixelCalibration cal = server.getPixelCalibration();
		params.setHiddenParameters(!cal.hasPixelSizeMicrons(), "sizeUnits");
		
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
		double minHoleSizePixels = params.getDoubleParameterValue("minHoleSize");
		if (cal.hasPixelSizeMicrons() && !params.getChoiceParameterValue("sizeUnits").equals("Pixels")) {
			minSizePixels /= (cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons());
			minHoleSizePixels /= (cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons());
		}
		boolean clearExisting = params.getBooleanParameterValue("clearExisting");
		
		Collection<PathObject> allSelected = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
		List<PathObject> selected = allSelected.stream().filter(p -> p.hasROI() && p.getROI().isArea() && 
				(p.isAnnotation() || p.isTMACore())).collect(Collectors.toList());
		boolean hasSelection = true;
		if (allSelected.isEmpty()) {
			hasSelection = false;
			selected = Collections.singletonList(imageData.getHierarchy().getRootObject());
		} else if (selected.size() != allSelected.size()) {
			DisplayHelpers.showErrorMessage("Create objects", "All selected objects should be annotations with area ROIs or TMA cores!");
			return false;
		}
		if (hasSelection && selected.size() == 1 && selected.get(0).getPathClass() != null && selected.get(0).getPathClass() != PathClassFactory.getPathClass(StandardPathClasses.REGION)) {
			var btn = DisplayHelpers.showYesNoCancelDialog("Create objects", "Create objects for selected annotation(s)?\nChoose 'no' to use the entire image.");
			if (btn == DialogButton.CANCEL)
				return false;
			if (btn == DialogButton.NO)
				selected = Collections.singletonList(imageData.getHierarchy().getRootObject());
		}
		
//		int nChildObjects = 0;
//		if (selected == null)
//			nChildObjects = hierarchy.nObjects();
//		else
//			nChildObjects = PathObjectTools.countDescendants(selected);
//		if (nChildObjects > 0) {
//			String message = "Existing child object will be deleted - is that ok?";
//			if (nChildObjects > 1)
//				message = nChildObjects + " existing descendant object will be deleted - is that ok?";
//			if (!DisplayHelpers.showConfirmDialog("Create objects", message))
//				return false;
//		}
//		// Need to turn off live prediction so we don't start training on the results...
//		livePrediction.set(false);
		
		return PixelClassifierTools.createObjectsFromPixelClassifier(
				server, imageData.getHierarchy(), selected, creator,
				minSizePixels, minHoleSizePixels, doSplit, clearExisting);
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
		if (server == null)
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
		if (imageData == null)
			return false;

		try {
			// Create a new FeatureServer if we need one
			ImageServer<BufferedImage> featureServer ;
			boolean tempFeatureServer = false;
			if (helper.getImageData() == imageData) {
				featureServer = helper.getFeatureServer();
			} else {
				tempFeatureServer = true;
				featureServer = new FeatureImageServer(imageData, helper.getFeatureCalculator(), helper.getResolution());
			}
			double downsample = featureServer.getDownsampleForResolution(0);
			int tw = (int)(featureServer.getMetadata().getPreferredTileWidth() * downsample);
			int th = (int)(featureServer.getMetadata().getPreferredTileHeight() * downsample);
			int x = (int)GeneralTools.clipValue(cx - tw/2, 0, featureServer.getWidth() - tw);
			int y = (int)GeneralTools.clipValue(cy - th/2, 0, featureServer.getHeight() - th);
			var request = RegionRequest.createInstance(
					featureServer.getPath(),
					downsample,
					x, y, tw, th, viewer.getZPosition(), viewer.getTPosition());
//			var tile = featureServer.getTileRequestManager().getTileRequest(
//					0,
//					(int)cx,
//					(int)cy,
//					viewer.getZPosition(), viewer.getTPosition());
//			if (tile == null) {
//				DisplayHelpers.showErrorMessage("Show features", "To file found - center the image within the viewer, then try again");
//				return false;
//			}
			
			var imp = IJTools.convertToImagePlus(featureServer, request).getImage();

			CompositeImage impComp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
			impComp.setDimensions(imp.getStackSize(), 1, 1);
			for (int s = 1; s <= imp.getStackSize(); s++) {
				impComp.setPosition(s);
				impComp.resetDisplayRange();
//				impComp.getStack().setSliceLabel(feature.getName(), s++);
			}
			impComp.setPosition(1);
			IJExtension.getImageJInstance();
			impComp.show();
			
			if (tempFeatureServer)
				featureServer.close();
			return true;
		} catch (Exception e) {
			logger.error("Error calculating features", e);
		}
		return false;
	}
	
	private boolean addResolution() {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null) {
			DisplayHelpers.showNoImageError("Add resolution");
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
		
		ClassificationResolution res;
		if (PixelCalibration.MICROMETER.equals(units)) {
			double scale = pixelSize / cal.getAveragedPixelSizeMicrons();
			res = new ClassificationResolution("Custom", cal.createScaledInstance(scale, scale, 1));
		} else
			res = new ClassificationResolution("Custom", cal.createScaledInstance(pixelSize, pixelSize, 1));

		List<ClassificationResolution> temp = new ArrayList<>(resolutions);
		temp.add(res);
		Collections.sort(temp, Comparator.comparingDouble((ClassificationResolution w) -> w.cal.getAveragedPixelSize().doubleValue()));
		resolutions.setAll(temp);
		comboResolutions.getSelectionModel().select(res);
		
		return true;
	}	
	
	
	private PixelCalibration getSelectedResolution() {
		return selectedResolution.get().cal;
	}
	
	
	private void updateResolution(ClassificationResolution resolution) {
		ImageServer<BufferedImage> server = null;
		if (viewer != null)
			server = viewer.getServer();
		if (server == null || miniViewer == null || resolution == null)
			return;
		Tooltip.install(miniViewer.getPane(), new Tooltip("Classification resolution: \n" + resolution));
		helper.setResolution(resolution.cal);
		miniViewer.setDownsample(resolution.cal.getAveragedPixelSize().doubleValue()  / server.getPixelCalibration().getAveragedPixelSize().doubleValue());
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
    	
    	if (classifierServer.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION) {
        	var classificationLabels = classifierServer.getMetadata().getClassificationLabels();
        	int sample = img.getRaster().getSample(xx, yy, 0); 		
        	return String.format("Classification: %s", classificationLabels.get(sample).getName());
//        	return String.format("Classification (%s):\n%s", coords, channels.get(sample).getName());
    	} else {
        	var channels = classifierServer.getMetadata().getChannels();
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
	
	
	
	/**
	 * Wrapper for a PixelCalibration to be used to define classifier resolution.
	 * This makes it possible to override {@link #toString()} and return a more readable representation of 
	 * the resolution.
	 */
	public static class ClassificationResolution {
		
		final private String name;
		final private PixelCalibration cal;
		
		ClassificationResolution(String name, PixelCalibration cal) {
			this.name = name;
			this.cal = cal;
		}
		
		public String getName() {
			return name;
		}
		
		public PixelCalibration getPixelCalibration() {
			return cal;
		}
		
		@Override
		public String toString() {
			if (cal.hasPixelSizeMicrons())
				return String.format("%s (%.1f %s/px)", name, cal.getAveragedPixelSizeMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s (downsample = %.1f)", name, cal.getAveragedPixelSize().doubleValue());
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cal == null) ? 0 : cal.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ClassificationResolution other = (ClassificationResolution) obj;
			if (cal == null) {
				if (other.cal != null)
					return false;
			} else if (!cal.equals(other.cal))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
		
	}


}
