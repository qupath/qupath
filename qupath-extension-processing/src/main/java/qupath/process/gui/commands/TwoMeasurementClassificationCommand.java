package qupath.process.gui.commands;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.chart.ScatterChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementBuilder;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.ChartThresholdPane;
import qupath.lib.gui.charts.Charts;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.process.gui.commands.ml.ProjectClassifierBindings;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Command to (sub)classify objects based on two measurements.
 *
 * @author Pete Bankhead
 */
public class TwoMeasurementClassificationCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TwoMeasurementClassificationCommand.class);
    private static final String title = "Two measurement classifier";
    private final QuPathGUI qupath;
    private final Map<QuPathViewer, TwoMeasurementPane> paneMap = new WeakHashMap<>();

    /**
     * Constructor.
     * @param qupath The GUI instance
     */
    public TwoMeasurementClassificationCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        var viewer = qupath.getViewer();
        var pane = paneMap.get(viewer);
        if (pane == null) {
            pane = new TwoMeasurementPane(qupath, viewer);
            paneMap.put(viewer, pane);
        }
        if (pane.dialog != null) {
            pane.dialog.getDialogPane().requestFocus();
        } else {
            pane.show();
        }
    }



    static class TwoMeasurementPane implements ChangeListener<ImageData<BufferedImage>> {

        private final QuPathGUI qupath;
        private final QuPathViewer viewer;

        private final GridPane pane;
        private final BorderPane borderPane = new BorderPane();


        private final Predicate<String> ALWAYS_TRUE = m -> true;
        private final String NO_CHANNEL_FILTER = "No filter (allow all channels)";
        private final ComboBox<String> comboChannels = new ComboBox<>();

        private final ComboBox<PathObjectFilter> comboFilter = new ComboBox<>();

        /**
         * Map storing previous threshold values used for specific measurements.
         */
        private final Map<String, Double> previousThresholds = new HashMap<>();

        private final ObservableList<String> measurements = FXCollections.observableArrayList();
        private final FilteredList<String> measurementsFiltered = measurements.filtered(ALWAYS_TRUE);

        private final ComboBox<String> comboMeasurementsX = new ComboBox<>(measurementsFiltered);
        private final ComboBox<String> comboMeasurementsY = new ComboBox<>(measurementsFiltered);

        private final Slider sliderThresholdX = new Slider();
        private final Slider sliderThresholdY = new Slider();
        private ChartThresholdPane chartPane;
        private final ComboBox<PathClass> comboAbove;
        private final ComboBox<PathClass> comboBelow;

        private final CheckBox cbLivePreview = new CheckBox("Live preview");

        private ScatterChart<Number, Number> scatterChart;

        private ClassificationRequest<BufferedImage> nextRequest;

        private final TextField tfSaveName = new TextField();

        private Dialog<ButtonType> dialog;

        private final StringProperty titleProperty = new SimpleStringProperty(title);

        private ExecutorService pool;

        TwoMeasurementPane(QuPathGUI qupath, QuPathViewer viewer) {
            this.qupath = qupath;
            this.viewer = viewer;

            // Object selection filter
            this.comboFilter.getItems().setAll(
                    PathObjectFilter.DETECTIONS_ALL,
                    PathObjectFilter.DETECTIONS,
                    PathObjectFilter.CELLS,
                    PathObjectFilter.TILES
            );
            comboFilter.getSelectionModel().select(PathObjectFilter.DETECTIONS_ALL);

            // Set up text fields
            var tfx = new TextField();
            tfx.setPrefColumnCount(6);
            var tfy = new TextField();
            tfy.setPrefColumnCount(6);
            FXUtils.bindSliderAndTextField(sliderThresholdX, tfx, true);
            GuiTools.installRangePrompt(sliderThresholdX);

            FXUtils.bindSliderAndTextField(sliderThresholdY, tfy, true);
            GuiTools.installRangePrompt(sliderThresholdY);

            // Initialize pane
            pane = new GridPane();
            borderPane.setCenter(pane);

            pane.setHgap(5.0);
            pane.setVgap(5.0);


            comboAbove = new ComboBox<>(qupath.getAvailablePathClasses());
            comboBelow = new ComboBox<>(qupath.getAvailablePathClasses());

            int row = 0;


            var labelFilter = new Label("Object filter");
            GridPaneUtils.addGridRow(pane, row++, 0, "Select objects to classify", labelFilter, comboFilter, comboFilter);

            var labelChannels = new Label("Channel filter");
            GridPaneUtils.addGridRow(pane, row++, 0, "Optionally filter measurement lists & classifications by channel name", labelChannels, comboChannels, comboChannels);

            var labelMeasurementsX = new Label("Measurement X");
            GridPaneUtils.addGridRow(pane, row++, 0, "Select measurement to threshold (X)", labelMeasurementsX, comboMeasurementsX, comboMeasurementsX);

            var labelMeasurementsY = new Label("Measurement Y");
            GridPaneUtils.addGridRow(pane, row++, 0, "Select measurement to threshold (Y)", labelMeasurementsY, comboMeasurementsY, comboMeasurementsY);

            var labelThresholdX = new Label("Threshold X");
            GridPaneUtils.addGridRow(pane, row++, 0, "Select threshold value", labelThresholdX, sliderThresholdX, tfx);

            var labelThresholdY = new Label("Threshold Y");
            GridPaneUtils.addGridRow(pane, row++, 0, "Select threshold value", labelThresholdY, sliderThresholdY, tfy);

            var labelAbove = new Label("Above threshold");
            GridPaneUtils.addGridRow(pane, row++, 0, "Specify the classification for objects above (or equal to) the threshold", labelAbove, comboAbove, comboAbove);

            var labelBelow = new Label("Below threshold");
            GridPaneUtils.addGridRow(pane, row++, 0, "Specify the classification for objects below the threshold", labelBelow, comboBelow, comboBelow);

            GridPaneUtils.addGridRow(pane, row++, 0, "Turn on/off live preview while changing settings", cbLivePreview, cbLivePreview, cbLivePreview);

            var btnSave = new Button("Save");
            btnSave.setOnAction(e -> {
                tryToSave();
                tfSaveName.requestFocus();
                btnSave.requestFocus();
            });
            var labelSave = new Label("Classifier name");
            tfSaveName.setMaxWidth(Double.MAX_VALUE);
            tfSaveName.setPromptText("Enter object classifier name");
            ProjectClassifierBindings.bindObjectClassifierNameInput(tfSaveName, qupath.projectProperty());
            btnSave.setMaxWidth(Double.MAX_VALUE);
            btnSave.disableProperty().bind(comboMeasurementsX.valueProperty().isNull().or(comboMeasurementsX.valueProperty().isNull()).or(tfSaveName.textProperty().isEmpty()));
            GridPaneUtils.addGridRow(pane, row++, 0, "Specify name of the classifier - this will be used to save to "
                    + "save the classifier in the current project, so it may be used for scripting later", labelSave, tfSaveName, btnSave);

            scatterChart = Charts.scatterChart().build();
            scatterChart.setAnimated(false);
            chartPane = new ChartThresholdPane(scatterChart);
            chartPane.setIsInteractive(true);

            GridPaneUtils.setToExpandGridPaneHeight(chartPane);
            GridPaneUtils.setToExpandGridPaneWidth(chartPane);
            chartPane.setPrefSize(200, 200);
            borderPane.setTop(chartPane);

            GridPaneUtils.setToExpandGridPaneWidth(comboFilter, comboChannels, comboMeasurementsX, comboMeasurementsY, sliderThresholdX, sliderThresholdY,
                    comboAbove, comboBelow, tfSaveName, cbLivePreview);


            // Add listeners
            comboChannels.valueProperty().addListener((v, o, n) -> updateChannelFilter());
            comboMeasurementsX.valueProperty().addListener((v, o, n) -> {
                if (o != null)
                    previousThresholds.put(o, getThresholdX());
                updateThresholdSlider(sliderThresholdX, comboMeasurementsX);
                maybePreview();
            });
            comboMeasurementsY.valueProperty().addListener((v, o, n) -> {
                if (o != null)
                    previousThresholds.put(o, getThresholdY());
                updateThresholdSlider(sliderThresholdY, comboMeasurementsY);
                maybePreview();
            });
            sliderThresholdX.valueProperty().addListener((v, o, n) -> maybePreview());
            sliderThresholdY.valueProperty().addListener((v, o, n) -> maybePreview());
            comboAbove.valueProperty().addListener((v, o, n) -> maybePreview());
            comboBelow.valueProperty().addListener((v, o, n) -> maybePreview());
            cbLivePreview.selectedProperty().addListener((v, o, n) -> maybePreview());
        }


        void updateChannelFilter() {
            var selected = comboChannels.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isBlank() || NO_CHANNEL_FILTER.equals(selected)) {
                measurementsFiltered.setPredicate(ALWAYS_TRUE);
            } else {
                var lowerSelected = selected.trim().toLowerCase();
                Predicate<String> predicate = m -> m.toLowerCase().contains(lowerSelected);
                if (measurements.stream().anyMatch(predicate))
                    measurementsFiltered.setPredicate(predicate);
                else
                    measurementsFiltered.setPredicate(ALWAYS_TRUE);

                if (comboMeasurementsX.getSelectionModel().getSelectedItem() == null && !comboMeasurementsX.getItems().isEmpty())
                    comboMeasurementsX.getSelectionModel().selectFirst();
                if (comboMeasurementsY.getSelectionModel().getSelectedItem() == null && !comboMeasurementsY.getItems().isEmpty())
                    comboMeasurementsY.getSelectionModel().selectFirst();

                var imageData = getImageData();
                var pathClass = qupath.getAvailablePathClasses().stream()
                        .filter(p -> p.toString().toLowerCase().contains(lowerSelected))
                        .findFirst().orElse(null);
                if (imageData != null && pathClass != null) {
//					if (imageData.isBrightfield()) {
                    comboAbove.getSelectionModel().select(pathClass);
                    comboBelow.getSelectionModel().select(null);
//					}
                }
                tfSaveName.setText(selected.trim());
            }
        }


        private final Map<PathObjectHierarchy, Map<PathObject, PathClass>> mapPrevious = new WeakHashMap<>();

        /**
         * Store the classifications for the current hierarchy, so these may be reset if the user cancels.
         * @param hierarchy The object hierarchy
         */
        void storeClassificationMap(PathObjectHierarchy hierarchy) {
            if (hierarchy == null)
                return;
            var pathObjects = hierarchy.getFlattenedObjectList(null);
            mapPrevious.put(
                    hierarchy,
                    PathObjectTools.createClassificationMap(pathObjects)
            );
        }

        public void show() {
            var imageData = viewer.getImageData();
            if (imageData == null) {
                GuiTools.showNoImageError(title);
                return;
            }

            viewer.imageDataProperty().addListener(this);

            storeClassificationMap(imageData.getHierarchy());
            refreshOptions();

            pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("single-measurement-classifier", true));

            dialog = new Dialog<>();
            dialog.initOwner(qupath.getStage());
            dialog.titleProperty().bind(titleProperty);
            dialog.getDialogPane().setContent(borderPane);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
            dialog.initModality(Modality.NONE);

            dialog.getDialogPane().focusedProperty().addListener((v, o, n) -> {
                if (n)
                    refreshTitle();
            });

            dialog.setOnCloseRequest(e -> {
                var applyClassifier = ButtonType.APPLY.equals(dialog.getResult());
                cleanup(applyClassifier);
            });

            dialog.show();
            maybePreview();
        }


        /**
         * Cleanup after the dialog is closed.
         * @param applyLastClassifier Should we apply the last classifier before closing?
         */
        void cleanup(boolean applyLastClassifier) {
            pool.shutdown();
            try {
                pool.awaitTermination(5000L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.debug("Exception waiting for classification to complete: " + e.getLocalizedMessage(), e);
            }
            if (applyLastClassifier) {
                // Make sure we ran the last command, then log it in the workflow
                var nextRequest = getUpdatedRequest();
                if (nextRequest != null) {
                    nextRequest.doClassification();
                    String name = null;
                    if (tfSaveName.getText() != null && !tfSaveName.getText().isEmpty())
                        name = tryToSave();
                    if (name == null) {
                        Dialogs.showWarningNotification("Object classifier", "Classifier was not saved, so will not appear in the command history");
                    } else {
                        nextRequest.imageData.getHistoryWorkflow().addStep(ObjectClassifierLoadCommand.createObjectClassifierStep(name));
                    }
                }
                // TODO: Log classification in the workflow?
            } else {
                // Restore classifications if the user cancelled
                for (var entry : mapPrevious.entrySet())
                    resetClassifications(entry.getKey(), entry.getValue());
            }
            viewer.imageDataProperty().removeListener(this);
            dialog = null;
        }



        ImageData<BufferedImage> getImageData() {
            return viewer.getImageData();
        }

        PathObjectHierarchy getHierarchy() {
            var imageData = getImageData();
            return imageData == null ? null : imageData.getHierarchy();
        }

        /**
         * Get objects for which the classification should be applied (depending upon image and filter).
         * @return The PathObjects that we're classifying.
         */
        Collection<? extends PathObject> getCurrentObjects() {
            var hierarchy = getHierarchy();
            if (hierarchy == null)
                return Collections.emptyList();
            var filter = comboFilter.getValue();
            var pathObjects = hierarchy.getFlattenedObjectList(new ArrayList<>());
            if (filter != null)
                pathObjects.removeIf(filter.negate());
            return pathObjects;
        }


        double getThresholdX() {
            return sliderThresholdX.getValue();
        }

        double getThresholdY() {
            return sliderThresholdY.getValue();
        }

        void refreshTitle() {
            var imageData = getImageData();
            var project = qupath.getProject();
            if (imageData == null)
                titleProperty.set(title);
            else {
                String imageName = null;
                if (project != null) {
                    var entry = project.getEntry(imageData);
                    if (entry != null)
                        imageName = entry.getImageName();
                }
                if (imageName == null)
                    imageName = imageData.getServer().getMetadata().getName();
                titleProperty.set(title + " (" + imageName + ")");
            }
        }

        /**
         * Refresh all displayed options to match the current image
         */
        void refreshOptions() {
            refreshTitle();
            refreshChannels();
//			updateAvailableClasses();
            updateAvailableMeasurements();
            updateThresholdSlider(sliderThresholdX, comboMeasurementsX);
            updateThresholdSlider(sliderThresholdY, comboMeasurementsY);
        }

        void refreshChannels() {
            var list = new ArrayList<String>();
            list.add(NO_CHANNEL_FILTER);
            var imageData = getImageData();
            if (imageData != null) {
                var stains = imageData.getColorDeconvolutionStains();
                if (stains != null) {
                    for (int s = 1; s <= 3; s++) {
                        var stain = stains.getStain(s);
                        if (!stain.isResidual())
                            list.add(stain.getName());
                    }
                }
                for (var channel : imageData.getServer().getMetadata().getChannels()) {
                    list.add(channel.getName());
                }
            }
            comboChannels.getItems().setAll(list);
            if (comboChannels.getSelectionModel().getSelectedItem() == null)
                comboChannels.getSelectionModel().selectFirst();
        }

//		void updateAvailableClasses() {
//			comboAbove.getItems().setAll(qupath.getAvailablePathClasses());
//			comboBelow.getItems().setAll(qupath.getAvailablePathClasses());
//		}

        void resetClassifications(PathObjectHierarchy hierarchy, Map<PathObject, PathClass> mapPrevious) {
            // Restore classifications if the user cancelled
            var changed = PathObjectTools.restoreClassificationsFromMap(mapPrevious);
            if (hierarchy != null && !changed.isEmpty())
                hierarchy.fireObjectClassificationsChangedEvent(this, changed);
        }

        void updateThresholdSlider(Slider sliderThreshold, ComboBox<String> comboMeasurements) {
            var measurement = comboMeasurements.getSelectionModel().getSelectedItem();
            var pathObjects = getCurrentObjects();
            if (measurement == null || pathObjects.isEmpty()) {
                sliderThreshold.setMin(0);
                sliderThreshold.setMax(1);
                sliderThreshold.setValue(0);
                return;
            }
            double[] allValues = pathObjects.stream().mapToDouble(p -> p.getMeasurementList().get(measurement))
                    .filter(Double::isFinite).toArray();
            var stats = new DescriptiveStatistics(allValues);
            updateChart();

            double value = previousThresholds.getOrDefault(measurement, stats.getMean());
            sliderThreshold.setMin(stats.getMin());
            sliderThreshold.setMax(stats.getMax());
            sliderThreshold.setValue(value);
        }

        private void updateChart() {
            scatterChart = Charts.scatterChart()
                    .viewer(QuPathGUI.getInstance().getViewer())
                    .measurements(getCurrentObjects(), comboMeasurementsX.getValue(), comboMeasurementsY.getValue())
                    .build();
            scatterChart.setAnimated(false);
            chartPane = new ChartThresholdPane(scatterChart);
            chartPane.setIsInteractive(true);
            chartPane.addThreshold(sliderThresholdX.valueProperty(), Color.BLACK, ChartThresholdPane.ThresholdAxis.X);
            chartPane.addThreshold(sliderThresholdY.valueProperty(), Color.BLACK, ChartThresholdPane.ThresholdAxis.Y);

            GridPaneUtils.setToExpandGridPaneHeight(chartPane);
            GridPaneUtils.setToExpandGridPaneWidth(chartPane);
            chartPane.setPrefSize(200, 200);
            borderPane.setTop(chartPane);
        }

        /**
         * Update measurements according to current image and filter.
         */
        void updateAvailableMeasurements() {
            var measurements = PathObjectTools.getAvailableFeatures(getCurrentObjects());
            this.measurements.setAll(measurements);
        }

        /**
         * Try to save the classifier & return the name of the saved classifier if successful
         * @return
         */
        String tryToSave() {
            var project = qupath.getProject();
            if (project == null) {
                Dialogs.showErrorMessage(title, "You need a project to save this classifier!");
                return null;
            }
            var name = GeneralTools.stripInvalidFilenameChars(tfSaveName.getText());
            if (name.isBlank()) {
                Dialogs.showErrorMessage(title, "Please enter a name for the classifier!");
                return null;
            }
            var classifier = updateClassifier();
            if (classifier == null) {
                Dialogs.showErrorMessage(title, "Not enough information to create a classifier!");
                return null;
            }
            try {
                if (project.getObjectClassifiers().contains(name)) {
                    if (!Dialogs.showConfirmDialog(title, "Do you want to overwrite the existing classifier '" + name + "'?"))
                        return null;
                }
                project.getObjectClassifiers().put(name, classifier);
                Dialogs.showInfoNotification(title, "Saved classifier as '" + name + "'");
                return name;
            } catch (Exception e) {
                Dialogs.showErrorNotification(title, e);
                logger.error(e.getMessage(), e);
                return null;
            }
        }

        /**
         * Request a preview of the classification update, asynchronously.
         * This will only have an effect if there is an {@link ExecutorService} ready (and thereby may be suppressed during initialization
         * by delaying the creation of the service), and also if 'live preview' is selected.
         */
        void maybePreview() {
            if (!cbLivePreview.isSelected() || pool == null || pool.isShutdown())
                return;

            nextRequest = getUpdatedRequest();
            pool.execute(() -> processRequest());
        }

        ClassificationRequest<BufferedImage> getUpdatedRequest() {
            var imageData = getImageData();
            if (imageData == null) {
                return null;
            }
            var classifier = updateClassifier();
            if (classifier == null)
                return null;
            return new ClassificationRequest<>(imageData, classifier);
        }

        ObjectClassifier<BufferedImage> updateClassifier() {
            var filter = comboFilter.getValue();
            var measurementX = comboMeasurementsX.getValue();
            var measurementY = comboMeasurementsY.getValue();
            var thresholdX = getThresholdX();
            var thresholdY = getThresholdY();
            var classAbove = comboAbove.getValue();
            var classBelow = comboBelow.getValue();
            var classEquals = classAbove; // We use >= and if this changes the tooltip must change too!

            if (measurementX == null || Double.isNaN(thresholdX) || measurementY == null || Double.isNaN(thresholdY))
                return null;

            var classX = new ClassifyByMeasurementBuilder<BufferedImage>(measurementX)
                    .threshold(thresholdX)
                    .filter(filter)
                    .above(classAbove)
                    .equalTo(classEquals)
                    .below(classBelow)
                    .build();
            var classY = new ClassifyByMeasurementBuilder<BufferedImage>(measurementY)
                    .threshold(thresholdY)
                    .filter(filter)
                    .above(classAbove)
                    .equalTo(classEquals)
                    .below(classBelow)
                    .build();
            return ObjectClassifiers.createCompositeClassifier(classX, classY);
        }

        synchronized void processRequest() {
            if (nextRequest == null || nextRequest.isComplete())
                return;
            nextRequest.doClassification();
        }

        @Override
        public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
                            ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {

            if (newValue != null && ! mapPrevious.containsKey(newValue.getHierarchy()))
                storeClassificationMap(newValue.getHierarchy());

            refreshOptions();
        }


    }


    /**
     * Encapsulate the requirements for a intensity classification into a single object.
     * @param <T>
     */
    static class ClassificationRequest<T> {

        private ImageData<T> imageData;
        private ObjectClassifier<T> classifier;

        private boolean isComplete = false;

        ClassificationRequest(ImageData<T> imageData, ObjectClassifier<T> classifier) {
            this.imageData = imageData;
            this.classifier = classifier;
        }

        public synchronized void doClassification() {
            var pathObjects = classifier.getCompatibleObjects(imageData);
            classifier.classifyObjects(imageData, pathObjects, true);
            imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
            isComplete = true;
        }

        public synchronized boolean isComplete() {
            return isComplete;
        }

    }


}
