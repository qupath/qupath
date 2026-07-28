package qupath.process.gui.commands.ml;

import java.awt.image.BufferedImage;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import javafx.util.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.ViewerManager;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;

/**
 * Class to manage viewer overlays when training a pixel classifier,
 * both for the classifier output and for feature display.
 */
class PixelClassifierOverlayManager {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierOverlayManager.class);

    public static final String DEFAULT_CLASSIFICATION_OVERLAY = "Show classification";

    private final ViewerManager viewerManager;
    private final PixelClassifierTraining training;
    private final FeatureRenderer featureRenderer;

    private final ObjectProperty<PixelClassifier> classifier = new SimpleObjectProperty<>();

    private final IntegerProperty predictionThreads = new SimpleIntegerProperty(1);

    private final DoubleProperty overlayOpacity = new SimpleDoubleProperty(1.0);
    private final BooleanProperty livePrediction = new SimpleBooleanProperty(false);

    private final DoubleProperty featureMinDisplay = new SimpleDoubleProperty(0.0);
    private final DoubleProperty featureMaxDisplay = new SimpleDoubleProperty(1.0);

    private final StringProperty selectedName = new SimpleStringProperty(DEFAULT_CLASSIFICATION_OVERLAY);

    private final ReadOnlyStringWrapper cursorLocation = new ReadOnlyStringWrapper();

    private final EventHandler<MouseEvent> mouseListener = this::handleMouseMoved;

    private PixelClassificationOverlay overlay;
    private PixelClassificationOverlay featureOverlay;

    private Subscription subscription;

    PixelClassifierOverlayManager(ViewerManager viewerManager, DefaultImageRegionStore regionStore, PixelClassifierTraining training) {
        this.viewerManager = viewerManager;
        this.training = training;
        this.featureRenderer = new FeatureRenderer(regionStore);
    }

    private void removeMouseMovedFilters() {
        logger.debug("Removing mouse moved filters");
        for (var viewer : viewerManager.getAllViewers()) {
            logger.trace("Removing mouse moved filter from {}", viewer);
            viewer.getView().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
        }
    }

    private void addMouseMovedFilters() {
        logger.debug("Adding mouse moved filters");
        for (var viewer : viewerManager.getAllViewers()) {
            logger.trace("Adding mouse moved filter to {}", viewer);
            // Remove, then add, to avoid duplicates
            viewer.getView().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
            viewer.getView().addEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
        }
    }

    /**
     * Start the overlay manager.
     * This attached property and mouse listeners, so that viewers are updated.
     * It must be called for the overlay manager to do anything useful.
     * <p>
     * This should be called from the JavaFX application thread.
     */
    public void start() {
        logger.debug("Starting overlay manager");
        this.subscription = this.overlayOpacity.subscribe(this::updateOpacity)
                .and(this.livePrediction.subscribe(this::updateLivePrediction))
                .and(this.classifier.subscribe(this::updateClassifier))
                .and(this.featureMinDisplay.subscribe(this::updateFeatureDisplayRange))
                .and(this.featureMaxDisplay.subscribe(this::updateFeatureDisplayRange))
                .and(this.viewerManager.getAllViewers().subscribe(this::addMouseMovedFilters));

        addMouseMovedFilters();
    }

    /**
     * Query whether the overlay manager is running.
     * @return true if {@link #start()} has been called after any previous call to {@link #stop()}.
     */
    public boolean isRunning() {
        return subscription != null;
    }

    /**
     * Stop the overlay manager.
     * This removes property and mouse listeners, so that changes do not cause viewers to update.
     * <p>
     * This should be called from the JavaFX application thread.
     */
    public void stop() {
        if (!isRunning()) {
            logger.warn("Stop request for overlay manager, but it is not running");
            return;
        }
        logger.debug("Stopping overlay manager");
        if (overlay != null)
            overlay.stop();

        subscription.unsubscribe();

        for (var viewer : viewerManager.getAllViewers()) {
            viewer.resetCustomPixelLayerOverlay();
            if (featureOverlay != null) {
                viewer.getCustomOverlayLayers().remove(featureOverlay);
                featureOverlay.stop();
            }
        }

        featureOverlay = null;
        overlay = null;

        removeMouseMovedFilters();
    }


    private void updateClassifier(PixelClassifier classifier) {
        if (classifier == null) {
            resetOverlay();
        } else {
            replaceOverlay(
                    PixelClassificationOverlay.create(
                            viewerManager.getOverlayOptions(),
                            classifier,
                            predictionThreads.get())
            );
        }
    }

    public void resetOverlay() {
        replaceOverlay(null);
    }

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
            overlay.setLivePrediction(livePrediction.get());
            overlay.setOpacity(overlayOpacityProperty().get());
        }
        ensureOverlaySet();
    }


    private void updateLivePrediction(boolean live) {
        if (overlay != null)
            overlay.setLivePrediction(live);
        if (featureOverlay != null)
            featureOverlay.setLivePrediction(live);
    }

    private void updateOpacity(Number opacity) {
        if (opacity == null || !Double.isFinite(opacity.doubleValue()))
            return;
        if (featureOverlay != null) {
            featureOverlay.setOpacity(opacity.doubleValue());
        }
        if (overlay != null)
            overlay.setOpacity(opacity.doubleValue());
        viewerManager.repaintAllViewers();
    }


    void ensureOverlaySet() {
        if (featureOverlay != null) {
            featureOverlay.stop();
            featureOverlay = null;
        }

        var imageData = getCurrentImageData();
        if (imageData == null)
            return;
        String featureName = selectedName.get();
        if (DEFAULT_CLASSIFICATION_OVERLAY.equals(featureName)) {
            for (var viewer : viewerManager.getAllViewers())
                viewer.setCustomPixelLayerOverlay(overlay);
            return;
        }
        int channel = -1;
        var featureServer = training.getFeatureServer(imageData);
        if (featureServer != null && featureName != null) {
            for (int c = 0; c < featureServer.nChannels(); c++) {
                if (featureName.equals(featureServer.getChannel(c).getName())) {
                    channel = c;
                    break;
                }
            }
            if (channel >= 0) {
                var channelBefore = featureRenderer.getSelectedChannel();
                featureRenderer.setChannel(featureServer, channel, featureMinDisplay.get(), featureMaxDisplay.get());
                var channelAfter = featureRenderer.getSelectedChannel();
                featureOverlay = PixelClassificationOverlay.create(viewerManager.getOverlayOptions(), training::getFeatureServer, featureRenderer);
                featureOverlay.setMaxThreads(predictionThreads.get());
                featureOverlay.setLivePrediction(true);
                featureOverlay.setOpacity(overlayOpacity.get());
                featureOverlay.setLivePrediction(livePrediction.get());
                if (channelBefore == null || !Objects.equals(channelBefore.getName(), channelAfter.getName())) {
                    autoFeatureContrast();
                }
            }
        }
        if (featureOverlay != null) {
            for (var viewer : viewerManager.getAllViewers())
                viewer.setCustomPixelLayerOverlay(featureOverlay);
        }
    }

    private ImageData<BufferedImage> getCurrentImageData() {
        var viewer = viewerManager.getActiveViewer();
        return viewer == null ? null : viewer.getImageData();
    }

    public void autoFeatureContrast() {
        var selectedChannel = featureRenderer == null ? null : featureRenderer.getSelectedChannel();
        if (selectedChannel != null) {
            featureRenderer.autoSetDisplayRange();
            double min = selectedChannel.getMinDisplay();
            double max = selectedChannel.getMaxDisplay();
            featureMinDisplay.set(min);
            featureMaxDisplay.set(max);
        }
    }

    private void updateFeatureDisplayRange() {
        if (featureRenderer == null || featureMinDisplay.getValue() == null || featureMaxDisplay.getValue() == null)
            return;
        featureRenderer.setRange(featureMinDisplay.get(), featureMaxDisplay.get());
        viewerManager.repaintAllViewers();
    }

    public StringProperty selectedNameProperty() {
        return selectedName;
    }

    public BooleanProperty livePredictionProperty() {
        return livePrediction;
    }

    public DoubleProperty featureMinDisplayProperty() {
        return featureMinDisplay;
    }

    public DoubleProperty featureMaxDisplayProperty() {
        return featureMaxDisplay;
    }

    public DoubleProperty overlayOpacityProperty() {
        return overlayOpacity;
    }

    public IntegerProperty predictionThreadsProperty() {
        return predictionThreads;
    }

    public ObjectProperty<PixelClassifier> classifierProperty() {
        return classifier;
    }

    public ReadOnlyStringProperty cursorLocationProperty() {
        return cursorLocation.getReadOnlyProperty();
    }

    public PixelClassificationOverlay getOverlay() {
        return overlay;
    }

    public PixelClassificationOverlay getFeatureOverlay() {
        return featureOverlay;
    }

    private void handleMouseMoved(MouseEvent event) {
        if (overlay == null)
            return;
        for (var viewer : viewerManager.getAllViewers()) {
            var view = viewer.getView();
            var local = view.screenToLocal(event.getScreenX(), event.getScreenY());
            if (view.contains(local)) {
                updateCursorLocation(viewer, local);
                return;
            }
        }
    }

    private void updateCursorLocation(QuPathViewer viewer, Point2D localPoint) {
        var p = viewer.componentPointToImagePoint(localPoint.getX(), localPoint.getY(), null, false);
        var server = overlay.getPixelClassificationServer(viewer.getImageData());
        String results = null;
        if (server != null)
            results = PixelClassificationOverlay.getDefaultLocationString(server,
                    null, p.getX(), p.getY(), viewer.getZPosition(), viewer.getTPosition());
        if (results == null)
            cursorLocation.set("");
        else
            cursorLocation.set(results);
    }

}
