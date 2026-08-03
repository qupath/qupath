package qupath.process.gui.commands.ml;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Subscription;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.imagej.gui.IJExtension;
import qupath.lib.gui.commands.MiniViewers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

/**
 * A mini viewer and associated controls to view overlays for pixel classifier output and features.
 */
class TrainingViewerPane extends Control implements Skinnable {

    private final ObjectProperty<QuPathViewer> viewer = new SimpleObjectProperty<>();
    private final ObjectProperty<ImageData<BufferedImage>> imageData = new SimpleObjectProperty<>();
    private final PixelClassifierOverlayManager overlayManager;

    /**
     * Resolution at which the classifier is being trained.
     */
    private final ObjectProperty<ClassificationResolution> resolution = new SimpleObjectProperty<>();

    /**
     * List of all available features.
     */
    private final ObservableList<String> availableFeatures = FXCollections.observableArrayList();

    TrainingViewerPane(ObservableValue<QuPathViewer> viewer, PixelClassifierOverlayManager overlayManager) {
        super();
        this.viewer.bind(viewer);
        this.imageData.bind(viewer.flatMap(QuPathViewer::imageDataProperty));
        this.overlayManager = overlayManager;
    }

    public ObjectProperty<ClassificationResolution> resolutionProperty() {
        return resolution;
    }

    public ClassificationResolution getResolution() {
        return resolutionProperty().get();
    }

    public void setResolution(ClassificationResolution resolution) {
        resolutionProperty().set(resolution);
    }

    public ObservableList<String> getAvailableFeatures() {
        return availableFeatures;
    }

    protected Skin<?> createDefaultSkin() {
        return new TrainingViewerPaneSkin(this);
    }

    private static class TrainingViewerPaneSkin implements Skin<TrainingViewerPane> {

        private final TrainingViewerPane skinnable;

        private final BorderPane pane = new BorderPane();

        private final ObjectProperty<MiniViewers.MiniViewerManager> miniViewer = new SimpleObjectProperty<>();
        private final ComboBox<String> comboDisplayFeatures = PixelClassifierUtils.createHGrowComboBox();
        private final Slider sliderFeatureOpacity = new Slider(0.0, 1.0, 1.0);
        private final Spinner<Double> spinFeatureMin = FXUtils.createDynamicStepSpinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0, 0.1, 1);
        private final Spinner<Double> spinFeatureMax = FXUtils.createDynamicStepSpinner(-Double.MAX_VALUE, Double.MAX_VALUE, 1, 0.1, 1);

        private final Tooltip resolutionTooltip = new Tooltip();

        // Store for convenience
        private final PixelClassifierOverlayManager overlayManager;

        // Store to avoid garbage collection
        private final ObjectProperty<Double> minDisplay;
        private final ObjectProperty<Double> maxDisplay;

        private final ListChangeListener<String> featureChangeListener = this::handleAvailableFeaturesChange;
        private Subscription subscription;

        private TrainingViewerPaneSkin(TrainingViewerPane skinnable) {
            this.skinnable = skinnable;
            this.overlayManager = skinnable.overlayManager;
            this.minDisplay = overlayManager.featureMinDisplayProperty().asObject();
            this.maxDisplay = overlayManager.featureMaxDisplayProperty().asObject();
        }

        private MiniViewers.MiniViewerManager createMiniViewer(QuPathViewer viewer) {
            if (viewer == null) {
                return miniViewer.get();
            } else {
                var manager = MiniViewers.createManager(viewer);
                var pane = manager.getPane();
                Tooltip.install(pane, resolutionTooltip);
                resolutionTooltip.setText("View image at classification resolution");
                return manager;
            }
        }

        private void initialize() {
            initializeViewer();
            initializeControls();
        }

        private void initializeViewer() {
            pane.centerProperty().bind(miniViewer.map(MiniViewers.MiniViewerManager::getPane));
        }

        private void initializeControls() {

            comboDisplayFeatures.getSelectionModel().selectedItemProperty().subscribe(overlayManager::ensureOverlaySet);
            comboDisplayFeatures.setMaxWidth(Double.MAX_VALUE);
            spinFeatureMin.setPrefWidth(100);
            spinFeatureMax.setPrefWidth(100);

            var btnFeatureAuto = new Button("Auto");
            btnFeatureAuto.setMaxWidth(Double.MAX_VALUE);
            btnFeatureAuto.setMinWidth(Button.USE_PREF_SIZE);
            btnFeatureAuto.setOnAction(e -> overlayManager.autoFeatureContrast());

            comboDisplayFeatures.getItems().setAll(PixelClassifierOverlayManager.DEFAULT_CLASSIFICATION_OVERLAY);
            comboDisplayFeatures.getSelectionModel().select(PixelClassifierOverlayManager.DEFAULT_CLASSIFICATION_OVERLAY);

            var featureDisableBinding = comboDisplayFeatures.valueProperty().isEqualTo(PixelClassifierOverlayManager.DEFAULT_CLASSIFICATION_OVERLAY).or(comboDisplayFeatures.valueProperty().isNull());
            btnFeatureAuto.disableProperty().bind(featureDisableBinding);
            btnFeatureAuto.setMaxHeight(Double.MAX_VALUE);
            spinFeatureMin.disableProperty().bind(featureDisableBinding);
            spinFeatureMin.setEditable(true);
            FXUtils.restrictTextFieldInputToNumber(spinFeatureMin.getEditor(), true);
            FXUtils.resetSpinnerNullToPrevious(spinFeatureMin);

            spinFeatureMax.disableProperty().bind(featureDisableBinding);
            spinFeatureMax.setEditable(true);
            FXUtils.restrictTextFieldInputToNumber(spinFeatureMax.getEditor(), true);
            FXUtils.resetSpinnerNullToPrevious(spinFeatureMax);

            var paneFeatures = new GridPane();
            spinFeatureMax.setTooltip(new Tooltip("Choose classification result or feature overlay to display (Warning: This requires a lot of memory & computation!)"));
            spinFeatureMin.setTooltip(new Tooltip("Min display value for feature overlay"));
            spinFeatureMax.setTooltip(new Tooltip("Max display value for feature overlay"));
            sliderFeatureOpacity.setTooltip(new Tooltip("Adjust classification/feature overlay opacity"));

            Button btnShow = new Button("To IJ");
            btnShow.setMinWidth(Button.USE_PREF_SIZE);
            btnShow.setMaxWidth(Double.MAX_VALUE);
            btnShow.setGraphic(new ImageView(IJExtension.getImageJIcon(16, 16)));
            btnShow.setContentDisplay(ContentDisplay.RIGHT);
            btnShow.setTooltip(new Tooltip("Send classification or features to ImageJ"));
            btnShow.setOnAction(this::handleSendToImageJ);
            btnShow.disableProperty().bind(skinnable.imageData.isNull()
                    .or(overlayManager.classifierProperty().isNull()));

            GridPaneUtils.addGridRow(paneFeatures, 0, 0, null,
                    comboDisplayFeatures, comboDisplayFeatures, comboDisplayFeatures, btnShow);
            GridPaneUtils.addGridRow(paneFeatures, 1, 0, null,
                    sliderFeatureOpacity, spinFeatureMin, spinFeatureMax, btnFeatureAuto);

            GridPaneUtils.setMaxWidth(Double.MAX_VALUE, comboDisplayFeatures, sliderFeatureOpacity);
            GridPaneUtils.setFillWidth(Boolean.TRUE, comboDisplayFeatures, sliderFeatureOpacity);
            GridPaneUtils.setHGrowPriority(Priority.ALWAYS, comboDisplayFeatures, sliderFeatureOpacity);
            paneFeatures.setHgap(5);
            paneFeatures.setVgap(5);
            paneFeatures.setPadding(new Insets(5));
            paneFeatures.prefWidthProperty().bind(pane.prefWidthProperty());

            pane.setBottom(paneFeatures);
        }

        private void handleSendToImageJ(ActionEvent e) {
            var overlay = PixelClassifierOverlayManager.DEFAULT_CLASSIFICATION_OVERLAY.equals(overlayManager.selectedNameProperty().get()) ?
                    overlayManager.getOverlay() :
                    overlayManager.getFeatureOverlay();
            var viewer = skinnable.viewer.get();
            if (viewer != null) {
                PixelClassifierUtils.showImageJClassifierOutput(
                        viewer,
                        overlay
                );
            }
        }

        @Override
        public TrainingViewerPane getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return pane;
        }

        @Override
        public void install() {
            Skin.super.install();
            bindControls();
        }

        private void bindControls() {
            initialize();
            minDisplay.bindBidirectional(spinFeatureMin.getValueFactory().valueProperty());
            maxDisplay.bindBidirectional(spinFeatureMax.getValueFactory().valueProperty());
            overlayManager.selectedNameProperty().bind(comboDisplayFeatures.getSelectionModel().selectedItemProperty());
            overlayManager.overlayOpacityProperty().bindBidirectional(sliderFeatureOpacity.valueProperty());
            skinnable.getAvailableFeatures().addListener(featureChangeListener);
            subscription = skinnable.resolutionProperty()
                    .subscribe(this::updateResolution)
                    .and(miniViewer.subscribe(this::updateResolution))
                    .and(skinnable.viewer.subscribe(this::updateMiniViewer));
            updateMiniViewer(skinnable.viewer.get());
            updateAvailableFeatures();
        }

        private void updateMiniViewer(QuPathViewer viewer) {
            if (viewer == null)
                return;
            var previous = miniViewer.get();
            miniViewer.setValue(createMiniViewer(viewer));
            if (previous != null) {
                previous.close();
            }
        }

        private void updateResolution() {
            var resolution = skinnable.resolution.get();
            var imageData = skinnable.imageData.get();
            var server = imageData == null ? null : imageData.getServer();
            if (resolution != null && server != null) {
                resolutionTooltip.setText("Classification resolution: \n" + resolution);
                var mini = miniViewer.get();
                if (mini != null) {
                    mini.setDownsample(resolution.cal.getAveragedPixelSize().doubleValue() /
                            server.getPixelCalibration().getAveragedPixelSize().doubleValue());
                }
            } else {
                resolutionTooltip.setText("No image available");
            }
        }

        private void handleAvailableFeaturesChange(ListChangeListener.Change<? extends String> change) {
            updateAvailableFeatures();
        }

        private void updateAvailableFeatures() {
            String currentSelection = comboDisplayFeatures.getSelectionModel().getSelectedItem();
            List<String> featureNames = new ArrayList<>();
            featureNames.add(PixelClassifierOverlayManager.DEFAULT_CLASSIFICATION_OVERLAY);
            featureNames.addAll(skinnable.getAvailableFeatures());
            if (comboDisplayFeatures.getItems().equals(featureNames))
                return;
            comboDisplayFeatures.getItems().setAll(featureNames);
            if (featureNames.contains(currentSelection))
                comboDisplayFeatures.getSelectionModel().select(currentSelection);
            else
                comboDisplayFeatures.getSelectionModel().selectFirst();
        }

        @Override
        public void dispose() {
            unbindControls();
        }

        private void unbindControls() {
            pane.centerProperty().unbind();
            pane.setCenter(null);
            minDisplay.unbindBidirectional(spinFeatureMin.getValueFactory().valueProperty());
            maxDisplay.unbindBidirectional(spinFeatureMax.getValueFactory().valueProperty());
            overlayManager.selectedNameProperty().unbind();
            overlayManager.overlayOpacityProperty().unbindBidirectional(sliderFeatureOpacity.valueProperty());
            skinnable.getAvailableFeatures().removeListener(featureChangeListener);
            if (subscription != null)
                subscription.unsubscribe();
            var mini = miniViewer.get();
            if (mini != null) {
                mini.close();
            }
        }

    }

}
