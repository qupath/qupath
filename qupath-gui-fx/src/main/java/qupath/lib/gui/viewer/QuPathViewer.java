/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.viewer;

import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.images.stores.ImageRenderer;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.gui.viewer.tools.handlers.MoveToolEventHandler;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.LookupOp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * JavaFX component for viewing a (possibly large) image, along with overlays.
 * <p>
 * Internally, this uses 3 buffers:
 * <ol>
 *     <li><b>Image buffer:</b> for the main image in the viewer</li>
 *     <li><b>Overlay buffer:</b> for all the overlays drawn on top of the image</li>
 *     <li><b>Composite buffer:</b> simply the image buffer, with the overlay buffer drawn on top.</li>
 * </ol>
 * This means that overlay updates can be processed more quickly, because the image
 * buffer contents do not need to change.
 * <p>
 * Starting with v0.8.0, this class was substantially redesigned to process updates using an
 * {@link AnimationTimer}; previously, it responded to updates triggered elsewhere.
 */
public class QuPathViewer implements PathObjectHierarchyListener, PathObjectSelectionListener {

	private static final Logger logger = LoggerFactory.getLogger(QuPathViewer.class);

	private final AnimationTimer timer = new AnimationTimer() {
		@Override
		public void handle(long now) {
			handlePulse(now);
		}
	};

	private final List<QuPathViewerListener> listeners = new ArrayList<>();

	private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

	private final DefaultImageRegionStore regionStore;

	private OverlayOptions overlayOptions;

	// Create separate buffers for the image and overlay,
	// because often the overlay changes while the image remains static
	private ViewerBuffers buffers = ViewerBuffers.createEmpty();

	// Keep a reference to a thumbnail image here, and apply color transforms to it
	private final ObjectProperty<BufferedImage> imgThumbnail = new SimpleObjectProperty<>();
	private final ReadOnlyObjectWrapper<BufferedImage> imgThumbnailRGB = new ReadOnlyObjectWrapper<>(); // An RGB thumbnail, which may have been transformed (or null if imgThumbnail is already RGB)
	private final BooleanProperty thumbnailIsFullImage = new SimpleBooleanProperty();

	/**
	 * Flag used to indicate that the image was updated and a full repaint is required.
	 */
	protected boolean imageUpdated = false;

	/**
	 * Flag to indicate that one or more overlays should be repainted.
	 * If imageUpdated is false, then the image itself need not be updated.
	 */
	protected boolean overlayUpdated = false;

	/**
	 * Flag to indicate that the viewer content is still loading,
	 * and the last repaint pulse did not completely paint the visible image region and all overlays.
	 * Subsequent repaints will automatically be called.
	 */
	private final ReadOnlyBooleanWrapper isLoading = new ReadOnlyBooleanWrapper(false);


	// Flag that is temporarily set to true while the ImageData is being set
	private final BooleanProperty imageDataChanging = new SimpleBooleanProperty(false);
	
	// Location & magnification variables
	// x & y coordinates - in the image space - of the center of the displayed region
	private double xCenter = 0;
	private double yCenter = 0;
	private final DoubleProperty downsampleFactor = new SimpleDoubleProperty(1.0);

	// Affine transform used to apply rotation
	private final AffineTransform transform = new AffineTransform();
	private final AffineTransform transformInverse = new AffineTransform();

	// Flag to indicate that repainting should occur faster if possible (less detail required)
	// This can be useful when rapidly changing view, for example
	private boolean doFasterRepaint = false;
	
	private final ObservableValue<java.awt.Color> background = createBackgroundColorBinding();

	// Keep a record of when the spacebar is pressed, to help with dragging to pan
	private boolean spaceDown = false;

	// Suggested overlay color, based upon the local background
	private final ObjectProperty<java.awt.Color> colorOverlaySuggested = new SimpleObjectProperty<>(calculateOverlayColor());
	
	// Requested cursor - but this may be overridden temporarily
	private Cursor requestedCursor = Cursor.DEFAULT;
	private double mouseX, mouseY;

	// The shape (coordinates in the image domain) last painted
	// Used to determine whether the visible part of the image has been changed
	private Shape lastVisibleShape = null;

	private final RoiEditor roiEditor = RoiEditor.createInstance();

	private final ObjectProperty<PathTool> currentTool = new SimpleObjectProperty<>(PathTools.MOVE);

	private final ViewerPane pane = createPane();

	private final ViewerOverlays overlays;

	private final ImageDisplay imageDisplay;
	private long lastDisplayChangeTimestamp = 0; // Used to indicate imageDisplay changes
	private final LongProperty lastRepaintTimestamp = new SimpleLongProperty(0L); // Used for debugging repaint times
	private long lastPaint = 0;
	private long minimumRepaintSpacingMillis = -1; // This can be used (temporarily) to prevent repaints happening too frequently

    /**
	 * Get the main JavaFX component representing this viewer.
	 * This is what should be added to a scene.
	 * @return
	 */
	public Pane getView() {
		return pane;
	}

	private Subscription subscription = Subscription.EMPTY;
	private Subscription overlayOptionsManager = Subscription.EMPTY;
	

	/**
	 * Create a new viewer.
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 */
	public QuPathViewer(DefaultImageRegionStore regionStore, OverlayOptions overlayOptions) {
		this(regionStore, overlayOptions, new ImageDisplay());
	}
	
	/**
	 * Create a new viewer.
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 * @param imageDisplay image display used to control the image display (conversion to RGB)
	 */
	private QuPathViewer(DefaultImageRegionStore regionStore, OverlayOptions overlayOptions, ImageDisplay imageDisplay) {
		super();

		this.regionStore = regionStore;

		initOverlayOptions(overlayOptions);

		// We need a simple repaint for color changes and simple (thick) line changes
		subscription = QuPathViewerUtils.subscribeObservables(
				this::repaint,
				PathPrefs.annotationStrokeThicknessProperty(),
				PathPrefs.newDetectionRenderingProperty(),
				PathPrefs.pointRadiusProperty(),
				PathPrefs.showPointHullsProperty(),
				PathPrefs.useSelectedColorProperty(),
				PathPrefs.colorSelectedObjectProperty(),
				PathPrefs.colorTileProperty(),
				PathPrefs.colorTMAProperty(),
				PathPrefs.opacityTMAMissingProperty(),
				PathPrefs.alwaysPaintSelectedObjectsProperty(),
				PathPrefs.locationFontSizeProperty(),

				PathPrefs.scalebarFontSizeProperty(),
				PathPrefs.scalebarFontWeightProperty(),
				PathPrefs.scalebarLineWidthProperty(),

				PathPrefs.gridSpacingXProperty(),
				PathPrefs.gridSpacingYProperty(),
				PathPrefs.gridStartXProperty(),
				PathPrefs.gridStartYProperty(),
				PathPrefs.gridScaleMicronsProperty(),

				pane.borderLineWidthProperty(),
				borderColorProperty()
		).and(
				QuPathViewerUtils.subscribeObservables(
						this::updateOverlaysAndRepaint,
						PathPrefs.colorDefaultObjectsProperty(),
						// We need to repaint everything if detection line thickness changes - including any cached regions
						PathPrefs.detectionStrokeThicknessProperty(),
						colorOverlaySuggested
				)
		).and(
				QuPathViewerUtils.subscribeObservables(
						this::repaintEntireImage,
						gammaProperty(),
						PathPrefs.viewerInterpolateBilinearProperty(),
						PathPrefs.viewerBackgroundColorProperty()
				)
		).and(
				QuPathViewerUtils.subscribeObservables(
						this::repaintAfterPlaneUpdate,
						tPosition,
						zPosition
				)
		).and(
				QuPathViewerUtils.subscribeObservables(
						this::repaintAfterAffineUpdate,
						rotationProperty(),
						pane.widthProperty(),
						pane.heightProperty()
				)
		);

		gammaProperty.set(PathPrefs.viewerGammaProperty().get());
		gammaProperty.bind(PathPrefs.viewerGammaProperty());

		this.imageDisplay = imageDisplay;
		if (imageDisplay != null)
			subscription = subscription.and(imageDisplay.eventCountProperty().subscribe(this::repaintEntireImage));

		// Prepare overlay layers
		this.overlays = ViewerOverlays.create(overlayOptions, regionStore);
		this.overlays.getAllOverlayLayers().addListener((Change<? extends PathOverlay> _) -> repaint());

		initThumbnails();

		imageUpdated = true;
		timer.start();
	}

	private void initThumbnails() {
		// Create RGB thumbnail binding
		if (imageDisplay == null) {
			imgThumbnailRGB.bind(Bindings.createObjectBinding(this::createThumbnailRGB,
					imgThumbnail, imageDataChanging));
		} else {
			imgThumbnailRGB.bind(Bindings.createObjectBinding(this::createThumbnailRGB,
					imgThumbnail, imageDataChanging, imageDisplay.eventCountProperty()));
		}
		colorOverlaySuggested.bind(Bindings.createObjectBinding(this::calculateOverlayColor, this.imgThumbnailRGB));
		thumbnailIsFullImage.bind(Bindings.createBooleanBinding(() -> {
				var img = imgThumbnail.get();
				var server = getServer();
				return img != null && server != null && img.getWidth() == server.getWidth() && img.getHeight() == server.getHeight();
				}, imgThumbnail, imageDataProperty));
	}

	private BufferedImage createThumbnailRGB() {
		// Don't show anything when the image data is changing:
		// the image display and thumbnail image might be temporarily out of sync
		if (imageDataChanging.get())
			return null;
		var img = imgThumbnail.get();
		ImageRenderer renderer = getRenderer();
		if (img != null && renderer != null)
			return renderer.applyTransforms(img, null);
		else
			return img;
	}

	private void repaintAfterAffineUpdate() {
		imageUpdated = true;
		overlayUpdated = true;
		updateAffineTransform();
		repaint();
	}

	private void repaintAfterPlaneUpdate() {
		imageUpdated = true;
		overlayUpdated = true;
		lastVisibleShape = null; // This ensures an update to the visible region is fired on the next pulse
	}

	/**
	 * Property for the image data currently being displayed within this viewer.
	 * @return
	 */
	public ReadOnlyObjectProperty<ImageData<BufferedImage>> imageDataProperty() {
		return imageDataProperty;
	}
	
	/**
	 * Get the image data currently being displayed within thie viewer.
	 * @return
	 */
	public ImageData<BufferedImage> getImageData() {
		return imageDataProperty.get();
	}

	/**
	 * Get the overlay options that control the viewer display.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}

	/**
	 * Get the region store used by this viewer for tile caching and painting.
	 * @return
	 */
	public DefaultImageRegionStore getImageRegionStore() {
		return regionStore;
	}

	/**
	 * Read-only property to indicate whether the viewer contents are still loading.
	 * This is set to true when tiles are being requested, and should switch to false when a repaint has
	 * not identified that any tiles were missing for the current field of view.
	 * @return
	 */
	public ReadOnlyBooleanProperty isLoadingProperty() {
		return isLoading.getReadOnlyProperty();
	}

	public boolean isLoading() {
		return isLoadingProperty().get();
	}

	private ViewerPane createPane() {
		var pane = new ViewerPane();
		pane.spaceDownProperty().subscribe(this::setSpaceDown);
		pane.mouseLocationProperty().subscribe(p -> {
			if (p != null) {
				mouseX = p.getX();
				mouseY = p.getY();
			} else {
				mouseX = -1;
				mouseY = -1;
			}
		});
		pane.addEventHandler(KeyEvent.ANY, new QuPathViewerKeyEventHandler(this));
		pane.placeholderVisibleProperty().bind(imageDataProperty().isNull().and(pane.placeholderTextProperty().isNotEmpty()));
		return pane;
	}

	/**
	 * Text to display within the viewer, whenever no image is open.
	 * @return
	 */
	public StringProperty placeholderTextProperty() {
		return pane.placeholderTextProperty();
	}


	/**
	 * Prevent frequent repaints (temporarily) by setting a minimum time that must have elapsed
	 * after the previous repaint for a new one to be triggered.
	 * (Repaint requests that come in between are simply disregarded for performance.)
	 * <p>
	 * When finished, it's necessary to call resetMinimumRepaintSpacingMillis() to make sure that
	 * normal service is resumed.
	 *
	 * @param repaintSpacingMillis the new minimum time delay between repaints
	 *
	 * @see #resetMinimumRepaintSpacingMillis
	 */
	public void setMinimumRepaintSpacingMillis(final long repaintSpacingMillis) {
		this.minimumRepaintSpacingMillis = repaintSpacingMillis;
	}

	/**
	 * Return to processing all repainting requests.
	 * <p>
	 * Note: calling this command triggers a repaint itself.
	 */
	public void resetMinimumRepaintSpacingMillis() {
		this.minimumRepaintSpacingMillis = -1;
		imageUpdated = true;
	}


	private static ObservableValue<java.awt.Color> createBackgroundColorBinding() {
		return Bindings.createObjectBinding(() -> {
			var c = PathPrefs.viewerBackgroundColorProperty();
			return c == null ? java.awt.Color.BLACK : ColorToolsAwt.getCachedColor(c.intValue());
		}, PathPrefs.viewerBackgroundColorProperty());
	}

	/**
	 * Color of the border around the viewer.
	 * This can be set to indicate that a viewer is 'active'.
	 * @return
	 */
	public ObjectProperty<Color> borderColorProperty() {
		return pane.borderColorProperty();
	}

	public void setBorderColor(final Color color) {
		borderColorProperty().set(color);
	}

	public Color getBorderColor() {
		return borderColorProperty().get();
	}

	private int getWidth() {
		return (int)Math.ceil(getView().getWidth());
	}

	private int getHeight() {
		return (int)Math.ceil(getView().getHeight());
	}

	/**
	 * Request that the viewer is repainted.
	 * The repaint is not triggered immediately, but rather enqueued for future processing.
	 * <p>
	 * Note that this can be used for changes in the field of view or overlay, but <i>not</i> for
	 * large changes that require any cached thumbnail to also be updated (e.g. changing the
	 * brightness/contrast or lookup table). In such cases {@link #repaintEntireImage()} is required.
	 * @see #repaintEntireImage()
	 */
	public void repaint() {
		// We need to repaint everything if the display changed
		if (imageDisplay != null && (lastDisplayChangeTimestamp != imageDisplay.getLastChangeTimestamp())) {
			repaintEntireImage();
			return;
		}
		logger.trace("Repaint requested!");
		imageUpdated = true;
		overlayUpdated = true;
	}

	/**
	 * Get the minimum downsample value supported by this viewer.
	 * This prevents zooming in by an unreasonably large amount.
	 * @return
	 */
	public double getMinDownsample() {
		return 1.0/64.0;
	}

	/**
	 * Get the maximum downsample value supported by this viewer.
	 * This prevents zooming out by an unreasonably large amount.
	 * @return
	 */
	public double getMaxDownsample() {
		if (!hasServer())
			return 1;
		return Math.max(getServerWidth(), getServerHeight()) / 100.0;
	}

	/**
	 * Zoom out by a specified number of steps, where one 'step' indicates a minimal zoom increment.
	 * @param nSteps
	 */
	public void zoomOut(int nSteps) {
		zoomIn(-nSteps);
	}

	/**
	 * Zoom in by a specified number of steps, where one 'step' indicates a minimal zoom increment.
	 * @param nSteps
	 */
	public void zoomIn(int nSteps) {
		if (nSteps == 0)
			return;
		setDownsampleFactor(getDownsampleFactor() * Math.pow(getDefaultZoomFactor(), -nSteps), -1, -1);
	}

	/**
	 * The amount by which the downsample factor is scaled for one increment of {@link #zoomIn()} or
	 * {@link #zoomOut()}.  Controls zoom speed.
	 * @return
	 */
	public double getDefaultZoomFactor() {
		return 1.01;
	}

	/**
	 * Zoom out by one step.
	 *
	 * @see #zoomOut(int)
	 * @see #getDefaultZoomFactor()
	 */
	public void zoomOut() {
		zoomOut(1);
	}

	/**
	 * Zoom in by one step.
	 *
	 * @see #zoomIn(int)
	 * @see #getDefaultZoomFactor()
	 */
	public void zoomIn() {
		zoomIn(1);
	}


	// We need a more extensive repaint for changes to the image pixel display
	private void updateOverlaysAndRepaint() {
		overlays.getHierarchyOverlay().clearCachedOverlay();
		overlayUpdated = true;
	}

	/**
	 * Set flag to indicate that repaints should prefer speed over accuracy.  This is useful when scrolling quickly, or rapidly changing
	 * the image zoom.
	 * <p>
	 * Note: Previously, this would drop the downsample level - but this produced visual artifacts too often.  
	 * Currently it only impacts interpolation used.
	 * 
	 * @param fasterRepaint
	 */
	public void setDoFasterRepaint(boolean fasterRepaint) {
		if (this.doFasterRepaint == fasterRepaint)
			return;
		this.imageUpdated = true;
		this.doFasterRepaint = fasterRepaint;
		repaint();
	}

	/**
	 * Get the current cursor position within this viewer, or null if the cursor is outside the viewer.
	 * This is provided in the component space.
	 * @return
	 */
	public Point2D getMousePosition() {
		if (mouseX >= 0 && mouseX <= pane.getWidth() && mouseY >= 0 && mouseY <= pane.getHeight())
			return new Point2D.Double(mouseX, mouseY);
		return null;
	}
	

	private void initOverlayOptions(OverlayOptions overlayOptions) {
		if (this.overlayOptions == overlayOptions)
			return;
		if (this.overlayOptions != null && overlayOptionsManager != null) {
			overlayOptionsManager.unsubscribe();
		}
		this.overlayOptions = overlayOptions;
		if (overlayOptions != null) {
			
			overlayOptionsManager = QuPathViewerUtils.subscribeObservables(this::updateOverlaysAndRepaint,
					overlayOptions.fillDetectionsProperty(),
					overlayOptions.selectedClassesProperty(),
					overlayOptions.selectedClassVisibilityModeProperty(),
					overlayOptions.useExactSelectedClassesProperty(),
					overlayOptions.measurementMapperProperty(),
					overlayOptions.detectionDisplayModeProperty(),
					overlayOptions.showConnectionsProperty(),
					overlayOptions.showObjectPredicateProperty(),
					overlayOptions.curtainMinXProperty(),
					overlayOptions.curtainMinYProperty(),
					overlayOptions.curtainMaxXProperty(),
					overlayOptions.curtainMaxYProperty())
					.and(QuPathViewerUtils.subscribeObservables(
							this::repaint,
							overlayOptions.showAnnotationsProperty(),
							overlayOptions.showNamesProperty(),
							overlayOptions.fillAnnotationsProperty(),
							overlayOptions.showDetectionsProperty(),
							overlayOptions.showPixelClassificationProperty(),
							overlayOptions.pixelClassificationFilterRegionProperty(),
							overlayOptions.gridLinesProperty(),
							overlayOptions.showTMACoreLabelsProperty(),
							overlayOptions.showGridProperty(),
							overlayOptions.showTMAGridProperty(),
							overlayOptions.opacityProperty(),
							overlayOptions.fontSizeProperty()
					));
		}
		if (isShowing())
			repaint();
	}

    /**
	 * Returns true if the viewer is visible, and attached to a scene.
	 * @return
	 */
	public boolean isShowing() {
		return pane.isVisible() && pane.getScene() != null;
	}


	/**
	 * Initialize the viewer for a new image server.
	 * This is primarily to set the z and t positions; subclasses may make other adjustments.
	 * @param server
	 * @see #setImageData(ImageData)
	 */
	protected void initializeForServer(ImageServer<BufferedImage> server) {
		// Note that the image has updated
		imageUpdated = true;

		if (server == null) {
			setZPosition(0);
			setTPosition(0);
			imgThumbnail.set(null);
			return;
		}

		setZPosition(server.nZSlices() / 2);
		setTPosition(0);
	}


	/**
	 * Returns true if the spacebar was pressed when this component was focussed, and is still being held down.
	 * @return
	 */
	public boolean isSpaceDown() {
		return spaceDown;
	}
	
	
	/**
	 * Notify this viewer that the isSpaceDown status should be changed.
	 * <p>
	 * This is useful whenever another component might have received the event,
	 * but the viewer needs to 'know' when it receives the focus.
	 * 
	 * @param spaceDown
	 */
	public void setSpaceDown(boolean spaceDown) {
		if (this.spaceDown == spaceDown)
			return;
 		this.spaceDown = spaceDown;
		var activeTool = currentTool.get();
		if (activeTool != PathTools.MOVE && activeTool != null) {
			if (spaceDown) {
				// Temporarily switch to 'move' tool
				activeTool.deregisterTool(this);
				activeTool = PathTools.MOVE;
				activeTool.registerTool(this);
			} else {
				// Reset tool, as required
				PathTools.MOVE.deregisterTool(this);
				activeTool.registerTool(this);
			}
		}
		logger.trace("Setting space down to {} - active tool {}", spaceDown, activeTool);
		updateCursor();
	}


	/**
	 * Update colorOverlaySuggested from the entire (RGB, i.e. color-transformed) image thumbnail
	 */
	private java.awt.Color calculateOverlayColor() {
		var img = getRGBThumbnail();
		if (img == null || QuPathViewerUtils.getMeanBrightnessRGB(img, 0, 0, img.getWidth(), img.getHeight()) > 127)
			return ColorToolsAwt.TRANSLUCENT_BLACK;
		else
			return ColorToolsAwt.TRANSLUCENT_WHITE;
	}


	private java.awt.Color getSuggestedOverlayColor() {
		return colorOverlaySuggested.getValue();
	}

	/**
	 * Get a JavaFX color that is suggested for use in an overlay.
	 * This adapts to whether the image is considered 'dark' or 'light', based upon its thumbnail.
	 * @return
	 */
	protected Color getSuggestedOverlayColorFX() {
		java.awt.Color c = getSuggestedOverlayColor();
		if (c == ColorToolsAwt.TRANSLUCENT_BLACK)
			return ColorToolsFX.TRANSLUCENT_BLACK_FX;
		else
			return ColorToolsFX.TRANSLUCENT_WHITE_FX;
	}


	/**
	 * Get the x-coordinate of the pixel currently centered in the viewer, in the full size image space.
	 * @return
	 */
	public double getCenterPixelX() {
		return xCenter;
	}

	/**
	 * Get the y-coordinate of the pixel currently centered in the viewer, in the full size image space.
	 * @return
	 */
	public double getCenterPixelY() {
		return yCenter;
	}

	/**
	 * Set the active {@link PathTool} for input to this viewer.
	 * @param tool
	 */
	public void setActiveTool(PathTool tool) {
		logger.trace("Setting tool {} for {}", tool, this);
		var activeTool = currentTool.get();
		if (activeTool != null)
			activeTool.deregisterTool(this);
		this.currentTool.set(tool);
		if (tool != null)
			tool.registerTool(this);
		updateCursor();
		updateRoiEditor();
	}
	
	/**
	 * Get the active {@link PathTool} for this viewer.
	 * Note that this is not necessarily identical to the result of the last call to {@link #setActiveTool(PathTool)},
	 * because it may be modified by other behavior (e.g. pressing the spacebar to temporarily activate the Move tool).
	 * @return
	 */
	public PathTool getActiveTool() {
		// Always navigate when the spacebar is down
		if (spaceDown)
			return PathTools.MOVE;
		return currentTool.get();
	}

	
	private void updateCursor() {
		PathTool mode = getActiveTool();
		if (mode == PathTools.MOVE)
			getView().setCursor(Cursor.HAND);
		else
			getView().setCursor(requestedCursor);
	}
	
	/**
	 * Get the current cursor for this viewer
	 * @return
	 */
	public Cursor getCursor() {
		return getView().getCursor();
	}
	
	/**
	 * Set the requested cursor to display in this viewer
	 * @param cursor
	 */
	public void setCursor(Cursor cursor) {
		this.requestedCursor = cursor;
		updateCursor();
	}
	
	/**
	 * Get the currently-selected object from the hierarchy.
	 * @return
	 */
	public PathObject getSelectedObject() {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return null;
		else
			return hierarchy.getSelectionModel().getSelectedObject();
	}
	
	/**
	 * Get all currently-selected objects from the hierarchy.
	 * @return
	 */
	public Collection<PathObject> getAllSelectedObjects() {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		else
			return hierarchy.getSelectionModel().getSelectedObjects();
	}
		
	/**
	 * Optionally set a custom overlay to use for the pixel layer.
	 * <p>
	 * This is useful to support live prediction based on a specific field of view, for example.
	 * 
	 * @param pathOverlay
	 */
	public void setCustomPixelLayerOverlay(PathOverlay pathOverlay) {
		if (getCustomPixelLayerOverlay() == pathOverlay)
			return;

		overlays.setCustomPixelLayerOverlay(pathOverlay);
		
		var imageData = getImageData();
		if (imageData != null) {
			if (pathOverlay instanceof PixelClassificationOverlay classificationOverlay) {
				var server = classificationOverlay.getPixelClassificationServer(imageData);
				ObservableMeasurementTableData.setPixelLayer(imageData, server);
			} else
				ObservableMeasurementTableData.setPixelLayer(imageData, null);
		}
	}
	
	
	/**
	 * Reset the custom pixel layer overlay to null.
	 */
	public void resetCustomPixelLayerOverlay() {
		setCustomPixelLayerOverlay(null);
	}

	/**
	 * Get the custom pixel layer overlay, or null if it has not be set.
	 * 
	 * @return
	 */
	public PathOverlay getCustomPixelLayerOverlay() {
		return overlays.getCustomPixelLayerOverlay();
	}

	/**
	 * Get the current ROI, i.e. the ROI belonging to the currently-selected object - or null, if there is no object or if the selected object has no ROI.
	 * @return
	 */
	public ROI getCurrentROI() {
		PathObject selectedObject = getSelectedObject();
		return selectedObject == null ? null : selectedObject.getROI();
	}

	
	/**
	 * Set selected object in the current hierarchy, without centering the viewer.
	 * 
	 * @param pathObject
	 */
	public void setSelectedObject(PathObject pathObject) {
		setSelectedObject(pathObject, false);
	}
	
	/**
	 * Set selected object in the current hierarchy, without centering the viewer.
	 * 
	 * @param pathObject
	 * @param addToSelected
	 */
	public void setSelectedObject(final PathObject pathObject, final boolean addToSelected) {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.getSelectionModel().setSelectedObject(pathObject, addToSelected);
	}
	
	
	/**
	 * Request a renderer that converts image tiles into RGB images.
	 * <p>
	 * By default, this returns {@code getImageDisplay}.
	 * <p>
	 * Subclasses might override this, e.g. to use custom image viewers that select transforms some 
	 * other way.
	 * 
	 * @return
	 */
	private ImageRenderer getRenderer() {
		return getImageDisplay();
	}
	


	/**
	 * Get a shape corresponding to the region of the image currently visible in this viewer.
	 * Coordinates are in the image space.
	 * <p>
	 * If no rotation is applied, the result will be an instance of java.awt.Rectangle.
	 * Otherwise, it will be a Path2D with the rotated rectangle vertices.
	 * @return
	 */
	public Shape getDisplayedRegionShape() {
		return getDisplayedClipShape(null);
	}


	/**
	 * Transform a clip shape into image coordinates for this viewer.
	 * The resulting shape coordinates are in the image space.
	 * 
	 * @param clip The clip shape, or null if the entire width &amp; height of the component should be used.
	 * @return
	 */
	protected Shape getDisplayedClipShape(Shape clip) {
		Shape clip2;
		if (clip == null)
			clip2 = new Rectangle2D.Double(0, 0, getWidth(), getHeight());
		else
			clip2 = clip;

		// Ideally we'd return a rectangle if no rotations are applied, rather than some more complex shape
		if (clip2 instanceof Rectangle2D rect && getRotation() == 0) {
            double[] coords = new double[]{rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY()};
			transformInverse.transform(coords, 0, coords, 0, 2);
			// Create a new rectangle if we need to - otherwise reuse one we just created (because clip == null)
			if (rect == clip)
				rect = new Rectangle2D.Double();
			rect.setFrameFromDiagonal(coords[0], coords[1], coords[2], coords[3]);
			return rect;
		}
		return new Area(transformInverse.createTransformedShape(clip2));
	}

	/**
	 * Request that the downsample is set to contain the entire image, and the image is centered in the viewer.
	 */
	public void zoomToFit() {
		if (getServer() == null)
			return;
		setDownsampleFactorImpl(getZoomToFitDownsampleFactor(), -1, -1);
		centerImage();
	}

	/**
	 * Get the {@link ImageServer} for the current image displayed within the viewer, or null if 
	 * no image is displayed.
	 * @return
	 */
	public ImageServer<BufferedImage> getServer() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		return temp == null ? null : temp.getServer();
	}

	/**
	 * Returns true if there is currently an ImageServer being displayed in this viewer.
	 * @return
	 */
	public boolean hasServer() {
		return getServer() != null;
	}

	/**
	 * Index of the currently visible z-slice.
	 */
	private final IntegerProperty zPosition = new SimpleIntegerProperty(null, "zPosition", 0);

	public IntegerProperty zPositionProperty() {
		return zPosition;
	}

	public void setZPosition(int zPos) {
		zPositionProperty().set(zPos);
	}

	public int getZPosition() {
		return zPositionProperty().get();
	}

	/**
	 * Index of the currently visible time point.
	 */
	private final IntegerProperty tPosition = new SimpleIntegerProperty(null, "tPositon", 0);

	public IntegerProperty tPositionProperty() {
		return tPosition;
	}

	public int getTPosition() {
		return tPositionProperty().get();
	}

	public void setTPosition(int tPosition) {
		tPositionProperty().set(tPosition);
	}

	/**
	 * Get the {@link ImagePlane} currently being displayed, including z and t positions. Channels are ignored.
	 * 
	 * @return
	 */
	public ImagePlane getImagePlane() {
		return ImagePlane.getPlane(getZPosition(), getTPosition());
	}
	
	/**
	 * Returns true between the time setImageData has been called, and before the first repaint has been completed.
	 * <p>
	 * This is useful to distinguish between view changes triggered by setting the ImageData, and those triggered 
	 * by panning/zooming.
	 * 
	 * @return
	 */
	public boolean isImageDataChanging() {
		return imageDataChanging.get();
	}

	/**
	 * Set the current image for this viewer.
	 * @param imageDataNew
	 */
	public void setImageData(ImageData<BufferedImage> imageDataNew) throws IOException {
		if (this.imageDataProperty.get() == imageDataNew)
			return;

		isLoading.set(true);

		// Important to reset thumbnail, so there are no attempts to render it
		// before the ImageDisplay is updated
		imgThumbnail.set(null);

		// We want to stop caching the hierarchy
		overlays.getHierarchyOverlay().resetImageData();

		imageDataChanging.set(true);
		
		// Remove listeners for previous hierarchy
		ImageData<BufferedImage> imageDataOld = this.imageDataProperty.get();
		if (imageDataOld != null) {
			imageDataOld.getHierarchy().removeListener(this);
			imageDataOld.getHierarchy().getSelectionModel().removePathObjectSelectionListener(this);
		}
		
		// Determine if the server has remained the same, so we can avoid shifting the viewer
		boolean sameServer = false;
		if (imageDataOld != null && imageDataNew != null && imageDataOld.getServerPath().equals(imageDataNew.getServerPath()))
			sameServer = true;

		this.imageDataProperty.set(imageDataNew);
		ImageServer<BufferedImage> server = imageDataNew == null ? null : imageDataNew.getServer();
		PathObjectHierarchy hierarchy = imageDataNew == null ? null : imageDataNew.getHierarchy();

		long startTime = System.currentTimeMillis();
		if (imageDisplay != null) {
			boolean keepDisplay = PathPrefs.keepDisplaySettingsProperty().get();
			// This is a bit of a hack to avoid calling internal methods for ImageDisplay
			// See https://github.com/qupath/qupath/issues/601
			boolean displaySet = false;
			if (imageDataNew != null && keepDisplay) {
				if (imageDisplay.getImageData() != null && QuPathViewerUtils.serversCompatible(imageDataNew.getServer(), imageDisplay.getImageData().getServer())) {
					imageDisplay.setImageData(imageDataNew, keepDisplay);
					displaySet = true;
				} else {
					for (var viewer : QuPathGUI.getInstance().getAllViewers()) {
						if (this == viewer || viewer.getImageData() == null)
							continue;
						var tempServer = viewer.getServer();
						var currentServer = imageDataNew.getServer();
						if (QuPathViewerUtils.serversCompatible(tempServer, currentServer)) {
							var json = viewer.getImageDisplay().toJSON(false);
							imageDataNew.setProperty(ImageDisplay.class.getName(), json);
							imageDisplay.setImageData(imageDataNew, false);
							displaySet = true;
							break;
						}
					}
				}
			}
			if (!displaySet) {
				try {
					imageDisplay.setImageData(imageDataNew, keepDisplay);
				} catch (Exception | UnsatisfiedLinkError e2) {
					// This can fail if the image isn't actually open-able.
					// If we don't reset, then the viewer will be in a bad state and continually through exceptions.
					logger.warn("Caught exception setting ImageData - will reset to null ({})", e2.getMessage());
					setImageData(null);
					throw e2;
				}
			}
			
			// For non-RGB images, the channel colors in our server metadata might now be out of sync with the 
			// brightness/contrast, based upon whatever we extracted from the image properties or kept from the last image.
			// If this happens, we need to update the metadata.
			// See https://github.com/qupath/qupath/issues/843
			if (server != null && !server.isRGB()) {
				var colors = imageDisplay.availableChannels().stream()
						.filter(c -> c instanceof DirectServerChannelInfo)
						.map(ChannelDisplayInfo::getColor)
						.toList();
				if (server.nChannels() == colors.size())
					QuPathViewerUtils.updateServerChannels(server, colors);
			}
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Setting ImageData time: {} ms", endTime - startTime);

		initializeForServer(server);
		
		if (!sameServer) {
			setDownsampleFactorImpl(getZoomToFitDownsampleFactor(), -1, -1);
			centerImage();
		}

		fireImageDataChanged(imageDataOld, imageDataNew);

		if (imageDataNew != null) {
			hierarchy.addListener(this);
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
		}

		setSelectedObject(null);
		
		// TODO: Consider shifting, fixing magnification, repainting etc.
		repaintEntireImage();
		forceOverlayUpdate();

		if (imageDataNew == null)
			logger.info("Image data reset");
		else
			logger.info("Image data set to {}", imageDataNew);
	}


	/**
	 * Reset the image data to null.
	 */
	public void resetImageData() {
		try {
			setImageData(null);
		} catch (IOException e) {
			// Should not happen - exception only 'expected' when an image cannot be read
			logger.error("Error resetting image data", e);
		}
	}


	private void fireImageDataChanged(ImageData<BufferedImage> imageDataPrevious, ImageData<BufferedImage> imageDataNew) {
		for (QuPathViewerListener listener : listeners.toArray(new QuPathViewerListener[0]))
			listener.imageDataChanged(this, imageDataPrevious, imageDataNew);		
	}

	private void fireVisibleRegionChangedEvent(Shape shape) {
		for (QuPathViewerListener listener : listeners.toArray(new QuPathViewerListener[0]))
			listener.visibleRegionChanged(this, shape);		
	}


	/**
	 * Request a region to repaint using image coordinates (rather than component coordinates).
	 * 
	 * @param region
	 * @param updateImage 
	 */
	private void repaintImageRegion(Rectangle2D region, boolean updateImage) {
		Rectangle clipBounds = transform.createTransformedShape(region).getBounds();
		if (clipBounds.intersects(0, 0, getWidth(), getHeight())) {
			if (updateImage)
				imageUpdated = true;
			repaint();
		}
	}
	
	
	/**
	 * Request that the entire image is repainted, including the thumbnail.
	 * This should be called whenever a major change in display is triggered, such as 
	 * changing the brightness/contrast or lookup table.
	 * Otherwise, {@link #repaint()} is preferable.
	 * @see #repaint()
	 */
	public void repaintEntireImage() {
		imageUpdated = true;
	}

	/**
	 * Get the magnification for the image within this viewer, or Double.NaN if no image is present.
	 * This is mostly for display; {@link #getDownsampleFactor()} is more meaningful.
	 * The actual value of the magnification depends upon whether any magnification value is available 
	 * within the image metadata.
	 * @return
	 */
	public double getMagnification() {
		if (!hasServer())
			return Double.NaN;
		return getFullMagnification() / getDownsampleFactor();
	}

	/**
	 * Get the full magnification for the image.
	 * This is either the magnification value stored within the current image metadata, 
	 * or 1.0 if no suitable image or metadata is available.
	 * @return
	 */
	public double getFullMagnification() {
		if (!hasServer())
			return 1.0;
		double magnification = getServer().getMetadata().getMagnification();
		if (Double.isNaN(magnification))
			return 1.0;
		else
			return magnification;
	}

	/**
	 * Set the downsample factor based upon magnification values.
	 * In general, {@link #setDownsampleFactor(double)} should be used directly in preference to this method.
	 * @param magnification
	 */
	public void setMagnification(final double magnification) {
		if (hasServer())
			setDownsampleFactor(getFullMagnification() / magnification);
	}

	/**
	 * Request that this viewer is closed.
	 * This unbinds the viewer from any properties it may be observing,
	 * and also triggers {@link QuPathViewerListener#viewerClosed(QuPathViewer)} calls for 
	 * any viewer listeners.
	 */
	public void closeViewer() {
		timer.stop();
		overlayOptionsManager.unsubscribe();
		subscription.unsubscribe();
		// Notify listeners
		for (QuPathViewerListener listener : listeners.toArray(new QuPathViewerListener[0]))
			listener.viewerClosed(this);
	}


	private void updateRepaintTimestamp() {
		long timestamp = System.currentTimeMillis();
		lastRepaintTimestamp.set(timestamp);
	}

	protected void handlePulse(long now) {
		boolean empty = getWidth() <= 0 || getHeight() <= 0;
		if (empty) {
			imageUpdated = false;
			overlayUpdated = false;
			isLoading.set(false);
			return;
		}

		// Skip repaint if the minimum time hasn't elapsed
		if (minimumRepaintSpacingMillis > 0) {
			long timeSinceRepaint = System.currentTimeMillis() - lastPaint;
			if (timeSinceRepaint < minimumRepaintSpacingMillis)
				return;
		}

//		var timer = new Timeit();
//		timer.start("Pulse");

		// Avoid repaints if there's no reason for them
		boolean somethingUpdated = false;

		// Ensure we have sufficiently-large buffers.
		// If these need to be resized, we definitely need to up the the image and overlays.
		int w = getWidth();
		int h = getHeight();
		if (!buffers.matchesSize(w, h)) {
			buffers = buffers.ensureSize(w, h);
			imageUpdated = true;
			overlayUpdated = true;
			// If the size changed, ensure the AffineTransform is up-to-date
			updateAffineTransform();
		}

		// Get region and downsample now (lest they change)
		var shapeRegion = getDisplayedRegionShape();
		double downsample = getDownsampleFactor();

		// The visible shape must have changed if there wasn't one previously...
		// Otherwise, check if it has changed & update accordingly
		// This will be used to notify listeners soon
		boolean shapeChanged = lastVisibleShape == null || !shapesEqual(lastVisibleShape, shapeRegion);

		// Store the last shape visible
		lastVisibleShape = shapeRegion;

		imageUpdated = imageUpdated || shapeChanged;

		if (imageUpdated) {
//			timer.checkpoint("Image update");
			// Only change status if the image update could be made fully,
			// otherwise continue on next pulse
			imageUpdated = !updateImageBuffer(buffers.getImageBuffer(), shapeRegion);
			somethingUpdated = true;
		}
		if (overlayUpdated) {
//			timer.checkpoint("Overlay update");
			// Only change status if the overlay update could be made fully,
			// otherwise continue on next pulse
			overlayUpdated = !updateOverlayBuffer(buffers.getOverlayBuffer(), downsample, shapeRegion);
			somethingUpdated = true;
		}

		if (somethingUpdated) {
//			timer.checkpoint("Composite update");
			updateCompositeBuffer();
			pane.drawImage(buffers.getCompositeBuffer());
			updateRepaintTimestamp();
		}

		imageDataChanging.set(false);

		// Notify any listeners of shape changes
		if (shapeChanged) {
//			timer.checkpoint("Shape update");
			fireVisibleRegionChangedEvent(lastVisibleShape);
		}

		if (imageDisplay != null)
			lastDisplayChangeTimestamp = imageDisplay.getLastChangeTimestamp();
		lastPaint = System.currentTimeMillis();
		isLoading.set(imageUpdated || overlayUpdated);


//		timer.stop();
//		if (hasServer() && timer.getCheckpoints().size() > 2 && false) {
//			timer.milliseconds();
//			System.err.println(timer.toString());
//		}
	}

	private static boolean shapesEqual(Shape shape1, Shape shape2) {
		if (Objects.equals(shape1, shape2)) {
			return true;
		} else {
			// Work around shape.equals not being terribly reliable.
			// See https://github.com/qupath/qupath/pull/2207#discussion_r4259929598
			return ensureArea(shape1).equals(ensureArea(shape2));
		}
	}

	private static Area ensureArea(Shape shape) {
		if (shape instanceof Area area)
			return area;
		else
			return new Area(shape);
	}

	private boolean updateImageBuffer(BufferedImage img, Shape shapeRegion) {
		var g2d = img.createGraphics();
		try {
			ImageServer<BufferedImage> server = getServer();
			if (server == null) {
				g2d.setColor(background.getValue());
				g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
				return true;
			}
			// Get the displayed region
			return updateImageBuffer(img, shapeRegion, img.getWidth(), img.getHeight());
		} finally {
			g2d.dispose();
		}
	}

	private boolean updateOverlayBuffer(BufferedImage imgOverlay, double downsample, Shape shapeRegion) {

		// The buffer for the overlay
		Graphics2D gOverlay = imgOverlay.createGraphics();
		try {
			gOverlay.setBackground(new java.awt.Color(0, true));
			gOverlay.clearRect(0, 0, imgOverlay.getWidth(), imgOverlay.getHeight());
			if (!hasServer())
				return true;
			gOverlay.setClip(0, 0, imgOverlay.getWidth(), imgOverlay.getHeight());
			gOverlay.transform(transform);

			float opacity = overlayOptions.getOpacity();
			Composite previousComposite = gOverlay.getComposite();
			boolean paintCompletely = thumbnailIsFullImage.get() || !doFasterRepaint;
			if (opacity > 0 || PathPrefs.alwaysPaintSelectedObjectsProperty().get()) {
				if (opacity < 1) {
					AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
					gOverlay.setComposite(composite);
				}

				var color = getSuggestedOverlayColor();
				// Paint the overlay layers
				var imageData = this.imageDataProperty.get();
				for (PathOverlay overlay : overlays.getAllOverlayLayersSnapshot()) {
					logger.trace("Painting overlay: {}", overlay);
					if (overlay instanceof AbstractOverlay abstractOverlay)
						abstractOverlay.setPreferredOverlayColor(color);
					overlay.paintOverlay(gOverlay, getServerBounds(), downsample, imageData, paintCompletely);
				}
			}

			// Paint the selected objects
			PathObjectHierarchy hierarchy = getHierarchy();
			PathObject mainSelectedObject = getSelectedObject();
			Rectangle2D boundsRect = null;
			boolean useSelectedColor = PathPrefs.useSelectedColorProperty().get();
			boolean paintSelectedBounds = PathPrefs.paintSelectedBoundsProperty().get();
			for (PathObject selectedObject : hierarchy.getSelectionModel().getSelectedObjects().toArray(new PathObject[0])) {
				// TODO: Simplify this...
				if (selectedObject != null && selectedObject.hasROI() && selectedObject.getROI().getZ() == getZPosition() && selectedObject.getROI().getT() == getTPosition()) {

					if (!selectedObject.isDetection()) {
						// Ensure a selected ROI can be seen clearly
						if (previousComposite != null)
							gOverlay.setComposite(previousComposite);
						gOverlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					}

					ROI pathROI = selectedObject.getROI();
					if (pathROI != null && (paintSelectedBounds || (!useSelectedColor)) && !(pathROI instanceof RectangleROI) && !pathROI.isEmpty()) {
						Shape boundsShape = null;
						if (pathROI.isPoint()) {
							var hull = pathROI.getConvexHull();
							if (hull != null)
								boundsShape = hull.getShape();
						}
						if (boundsShape == null) {
							boundsRect = AwtTools.getBounds2D(pathROI, boundsRect);
							boundsShape = boundsRect;
						}
						PathObjectPainter.paintShape(boundsShape, gOverlay, getSuggestedOverlayColor(), PathObjectPainter.getCachedStroke(Math.max(downsample, 1) * 2), null);
					}

					// Avoid double-painting of annotations (which looks odd if they are filled in)
					// However do always paint detections, since they are otherwise painted (unselected)
					// in a cached way
					if ((selectedObject.isDetection() && PathPrefs.useSelectedColorProperty().get()) || !PathObjectTools.hierarchyContainsObject(hierarchy, selectedObject)) {
						gOverlay.setClip(shapeRegion);
						PathObjectPainter.paintObject(selectedObject, gOverlay, overlayOptions, getHierarchy().getSelectionModel(), downsample);
					}
					// Paint ROI handles, if required
					if (selectedObject == mainSelectedObject && roiEditor.hasROI()) {
						Stroke strokeThick = PathObjectPainter.getCachedStroke(PathPrefs.annotationStrokeThicknessProperty().get() * downsample);
						java.awt.Color color = useSelectedColor ? ColorToolsAwt.getCachedColor(PathPrefs.colorSelectedObjectProperty().get()) : null;
						if (color == null)
							color = ColorToolsAwt.getCachedColor(ColorToolsFX.getDisplayedColorARGB(selectedObject));
						gOverlay.setStroke(strokeThick);
						// Draw ROI handles using adaptive size
						double maxHandleSize = getMaxROIHandleSize();
						double minHandleSize = downsample;
						PathObjectPainter.paintHandles(roiEditor, gOverlay, minHandleSize, maxHandleSize, color, ColorToolsAwt.getTranslucentColor(color));
					}
				}
			}
		} finally {
			gOverlay.dispose();
		}
		return true;
	}

	private void updateCompositeBuffer() {
		// Update the (final) composite buffer
		var imgBuffer = buffers.getCompositeBuffer();
		int w = imgBuffer.getWidth();
		int h = imgBuffer.getHeight();
		var g2d = imgBuffer.createGraphics();
		try {
			g2d.drawImage(buffers.getImageBuffer(), 0, 0, null);
			// Draw overlay, applying curtain effect if needed
			int x = (int) GeneralTools.clipValue(overlayOptions.getCurtainMinX() * w, 0, w);
			int y = (int) GeneralTools.clipValue(overlayOptions.getCurtainMinY() * h, 0, h);
			int x2 = (int) GeneralTools.clipValue(overlayOptions.getCurtainMaxX() * w, 0, w);
			int y2 = (int) GeneralTools.clipValue(overlayOptions.getCurtainMaxY() * h, 0, h);
			if (x2 - x > 0 && y2 - y > 0) {
				g2d.drawImage(buffers.getOverlayBuffer(), x, y, x2, y2,
						x, y, x2, y2, null);
			}
		} finally {
			g2d.dispose();
		}
	}
	
	/**
	 * Get the maximum size for which ROI handles may be drawn.
	 * @return
	 */
	public double getMaxROIHandleSize() {
		return PathPrefs.annotationStrokeThicknessProperty().get() * getDownsampleFactor() * 4.0;
	}

	/**
	 * Get the timestamp referring to the last time this viewer was repainted.
	 * @return
	 */
	public ReadOnlyLongProperty repaintTimestamp() {
		return lastRepaintTimestamp;
	}


    private boolean updateImageBuffer(final BufferedImage imgBuffer, final Shape shapeRegion, final int w, final int h) {

		Graphics2D gBuffered = imgBuffer.createGraphics();

		// Set all image pixels to be the background color
		gBuffered.setColor(background.getValue());
		gBuffered.fillRect(0, 0, w, h);

		// Apply the transform so we don't need to worry about converting coordinates so much
		gBuffered.transform(transform);
		gBuffered.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Get the server width & height
		ImageServer<BufferedImage> server = getServer();
		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();

		// Check if we require tiling the image, or if the low-resolution version does all we need
		var plane = getImagePlane();
		BufferedImage imgThumbnail = regionStore.getThumbnailCache().getOrRequestThumbnail(server, plane);
		boolean thumbnailMissing = imgThumbnail == null;
		if (thumbnailMissing) {
			if (this.imgThumbnail.get() == null) {
				// If we have *no* thumbnail for any z-slice or time point, then we need to request one and wait
				imgThumbnail = regionStore.getThumbnailCache().getThumbnail(server, plane);
			} else {
				// If we have a thumbnail for another slice, use the closest one and return -
				// we'll only try the full repaint once the thumbnail for the current slice is available.
				// This helps avoid making requests for too many (non-thumbnail) tiles when we're quickly browsing
				// through a stack.
				var imgThumbnailClosest = regionStore.getThumbnailCache().getClosestCachedThumbnail(server, plane);
				if (imgThumbnailClosest != null) {
					if (imageDisplay != null) {
						imgThumbnailClosest = imageDisplay.applyTransforms(imgThumbnailClosest, null);
					}
					gBuffered.drawImage(imgThumbnailClosest, 0, 0, getServerWidth(), getServerHeight(), null);
					gBuffered.dispose();
					var gammaOp = getGammaOp();
					if (gammaOp != null) {
						gammaOp.filter(imgBuffer.getRaster(), imgBuffer.getRaster());
					}
					return false;
				}
			}
		}
		// Set the thumbnail to the best one we have (which enables the image overview to update)
		if (imgThumbnail != null) {
			this.imgThumbnail.set(imgThumbnail);
		}

		// Check if we will be painting some background beyond the image edge
		Rectangle shapeBounds = shapeRegion.getBounds();
		boolean overBoundary = shapeBounds.x < 0 || shapeBounds.y < 0 || shapeBounds.x + shapeBounds.width >= serverWidth || shapeBounds.y + shapeBounds.height >= serverHeight;

		// Reset interpolation - this roughly halves repaint times
		if (!doFasterRepaint && PathPrefs.viewerInterpolateBilinearProperty().get())
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		double downsample = getDownsampleFactor();

		// Try to repaint higher resolution tiles for only the requested region
		// A small optimization (that can make a difference in repaint speed...) is that for an RGB image we don't need to transform
		// the image as we go along (tile by tile), but we can apply a single in-place transform afterwards.
		// *However* this shouldn't be applied if the region we are viewing extends beyond the image boundary, as it means we would be color-transforming the background color.
		// For a non-RGB image, or if the viewed region is over the image boundary, the transform should be applied in advance to the thumbnail, and then tile-by-tile during painting.
		boolean repaintSuccess;
		if (server.isRGB() && !overBoundary) {
			repaintSuccess = regionStore.paintRegion(server, gBuffered, shapeRegion, getZPosition(), getTPosition(), downsample, imgThumbnail, null);
			gBuffered.dispose();
			if (imageDisplay != null) {
				imageDisplay.applyTransforms(imgBuffer, imgBuffer);
			}
		} else {
			repaintSuccess = regionStore.paintRegion(server, gBuffered, shapeRegion, getZPosition(), getTPosition(), downsample, imgThumbnail, getRenderer());
		}

		gBuffered.dispose();
		// Apply color transforms, if required
		var gammaOp = getGammaOp();
		if (gammaOp != null) {
			gammaOp.filter(imgBuffer.getRaster(), imgBuffer.getRaster());
		}

		// TODO: Consider indicating that the image is incomplete
//		if (thumbnailMissing) {
//			int n = 9;
//			float[] kernel = new float[n];
//			Arrays.fill(kernel, 1f/n);
//			var imgTemp = new ConvolveOp(new Kernel(n, 1, kernel)).filter(imgBuffer, null);
//			new ConvolveOp(new Kernel(1, n, kernel)).filter(imgTemp, imgBuffer);
//		}

		return repaintSuccess && !thumbnailMissing;
	}


	/**
	 * Get an unmodifiable list containing the overlay layers, in order.
	 * @return
	 */
	public List<PathOverlay> getOverlayLayers() {
		return overlays.getAllOverlayLayers();
	}
	
	/**
	 * Get direct access to the custom overlay list.
	 * @return
	 */
	public ObservableList<PathOverlay> getCustomOverlayLayers() {
		return overlays.getCustomOverlayLayers();
	}

	/**
	 * Gamma property affecting the display of all images in the viewer.
	 * By default, this is bound to {@link PathPrefs#viewerGammaProperty()}.
	 */
	private final DoubleProperty gammaProperty = new SimpleDoubleProperty(1.0);

	/**
	 * Property representing the gamma value for a gamma transform applied to the viewer contents.
	 * Note that the gamma is applied to the RGB pixels after any other adjustments (e.g., brightness and contrast,
	 * lookup tables) have been performed.
	 * @return
	 */
	public DoubleProperty gammaProperty() {
		return gammaProperty;
	}

	public double getGamma() {
		return gammaProperty().get();
	}
	
	public void setGamma(double gamma) {
		// If we're bound to the preferences value, we can't set the gamma
		if (gammaProperty().isBound()) {
			logger.warn("Unable to set gamma for viewer - property is bound.");
			logger.warn("Call viewer.gammaProperty().unbind() first.");
			return;
		}
		gammaProperty().set(gamma);
	}

	private final ObjectBinding<LookupOp> gammaOp = Bindings.createObjectBinding(() -> {
		double gamma = gammaProperty().get();
		if (gamma == 1.0 || gamma <= 0 || !Double.isFinite(gamma))
			return null;
		else
			return QuPathViewerUtils.createGammaOp(gamma);
	}, gammaProperty());

	/**
	 * Get a {@link LookupOp} that can perform any requested gamma correction in this viewer.
	 * Note that the gamma is applied to the RGB image (not the original data).
	 * @return a gamma op if specified, or null if no gamma adjustment is required (gamma is 1.0, or invalid)
	 */
	public LookupOp getGammaOp() {
		return gammaOp.get();
	}

	/**
	 * Get the {@link RoiEditor} used by this viewer.
	 * @return
	 */
	public RoiEditor getROIEditor() {
		return roiEditor;
	}


	/**
	 * Get the {@link ImageDisplay} object used to determine how the image is converted to RGB for display.
	 * @return
	 */
	public ImageDisplay getImageDisplay() {
		return imageDisplay;
	}

	boolean componentContains(double x, double y) {
		return x >= 0 && x < getView().getWidth() && y >= 0 && y <= getView().getHeight();
	}
	
	/**
	 * Set the downsample factor for this viewer.
	 * @param downsampleFactor
	 */
	public void setDownsampleFactor(double downsampleFactor) {
		if (componentContains(mouseX, mouseY))
			setDownsampleFactor(downsampleFactor, mouseX, mouseY);
		else {
			setDownsampleFactor(downsampleFactor, -1, -1);
		}
	}


	/**
	 * Get a thumbnail representing the image as displayed by this viewer, if available.
	 * Note that this may change as z-slices and time points and selected,
	 * and it is not guaranteed that the thumbnail has yet been loaded.
	 * @return
	 */
	public BufferedImage getThumbnail() {
		return imgThumbnail.get();
	}


	/**
	 * Get a thumbnail representing the image as displayed by this viewer.
	 * <p>
	 * Note: This will be a color (aRGB) image, with any color transforms applied -
	 * therefore should not be used to extract 'original' pixel values
	 * @return
	 */
	public BufferedImage getRGBThumbnail() {
		return imgThumbnailRGB.getValue();
	}

	/**
	 * Property showing an RGB thumbnail corresponding to the current image, with the current display settings.
	 * This can be used for an overview image and updates when the display settings change, or z or t index change.
	 * However, note that the changes may incur some delay, while the thumbnail is generated.
	 * @return
	 */
	protected ReadOnlyObjectProperty<BufferedImage> rgbThumbnailProperty() {
		return imgThumbnailRGB.getReadOnlyProperty();
	}

	/**
	 * Set downsample factor, so that the specified coordinate in the image space is not shifted in the viewer afterwards.
	 * The purpose is to make it possible to zoom in/out while keeping the cursor focussed on a particular location.
	 * <p>
	 * The specified downsample factor will automatically be clipped to the range <code>getMinDownsample</code> to <code>getMaxDownsample</code>.
	 *  
	 * @param downsampleFactor
	 * @param cx
	 * @param cy
	 */
	public void setDownsampleFactor(double downsampleFactor, double cx, double cy) {
		setDownsampleFactor(downsampleFactor, cx, cy, false);
	}
	
	/**
	 * Set downsample factor, so that the specified coordinate in the image space is not shifted in the viewer afterwards.
	 * The purpose is to make it possible to zoom in/out while keeping the cursor focused on a particular location.
	 * 
	 * @param downsampleFactor
	 * @param cx
	 * @param cy
	 * @param clipToMinMax If <code>true</code>, the specified downsample factor will be clipped 
	 * to the range <code>getMinDownsample</code> to <code>getMaxDownsample</code>.
	 */
	public void setDownsampleFactor(double downsampleFactor, double cx, double cy, boolean clipToMinMax) {
		
		// Ensure within range, if necessary
		if (clipToMinMax)
			downsampleFactor = GeneralTools.clipValue(downsampleFactor, getMinDownsample(), getMaxDownsample());
		else if (downsampleFactor <= 0 || !Double.isFinite(downsampleFactor)) {
			logger.warn("Invalid downsample factor {}, will use {} instead", downsampleFactor, getMinDownsample());
			downsampleFactor = getMinDownsample();
		}
		
		setDownsampleFactorImpl(downsampleFactor, cx, cy);
	}

	/**
	 * Set downsample factor, so that the specified coordinate in the image space is not shifted in the viewer afterwards.
	 * The purpose is to make it possible to zoom in/out while keeping the cursor focussed on a particular location.
	 * This avoids doing any additional checking (e.g. of zoom-to-fit).
	 * 
	 * @param downsampleFactor
	 * @param cx
	 * @param cy
	 */
	private void setDownsampleFactorImpl(double downsampleFactor, double cx, double cy) {
		
		double currentDownsample = getDownsampleFactor();
		if (currentDownsample == downsampleFactor)
			return;
		
		// Take care of centering according to the specified coordinates
		if (cx < 0)
			cx = getWidth() / 2.0;
		if (cy < 0)
			cy = getHeight() / 2.0;

		// Compute what the x, y coordinates should be to preserve the same image centering
		if (!isRotated()) {
			xCenter += (cx - getWidth()/2.0) * (currentDownsample - downsampleFactor);
			yCenter += (cy - getHeight()/2.0) * (currentDownsample - downsampleFactor);
		} else {
			// Get the image coordinate
			Point2D p2 = componentPointToImagePoint(cx, cy, null, false);
			double dx = (p2.getX() - xCenter) / currentDownsample * downsampleFactor;
			double dy = (p2.getY() - yCenter) / currentDownsample * downsampleFactor;
			xCenter = p2.getX() - dx;
			yCenter = p2.getY() - dy;
		}

		// Downsample might not be finite if the width and height are 0
		// (and this method has been called too early) - we need a finite
		// value to avoid the UI becoming unstable
		if (!Double.isFinite(downsampleFactor)) {
			logger.debug("Setting non-finite downsample {} to 1.0", downsampleFactor);
			downsampleFactor = 1.0;
		}
		this.downsampleFactor.set(downsampleFactor);
		updateAffineTransform();
	}

	private double getZoomToFitDownsampleFactor() {
		if (!hasServer())
			return Double.NaN;
		double fullWidth = getServerWidth();
		double fullHeight = getServerHeight();
		// TODO: Consider handling rotation
		double maxDownsample = fullWidth / getWidth();
		maxDownsample = Math.max(maxDownsample, fullHeight / getHeight());
		return maxDownsample;		
	}


	/**
	 * Get the width in pixels of the full resolution of the current image, or 0 if no image is currently open.
	 * @return
	 */
	public int getServerWidth() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? 0 : server.getWidth();
	}

	/**
	 * Get the height in pixels of the full resolution of the current image, or 0 if no image is currently open.
	 * @return
	 */
	public int getServerHeight() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? 0 : server.getHeight();
	}

	
	/**
	 * Get an {@link ImageRegion} representing the full width and height of the current image.
	 * The {@link ImagePlane} is set according to the z and t position of the viewer.
	 * @return
	 */
	public ImageRegion getServerBounds() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? null : ImageRegion.createInstance(0, 0, server.getWidth(), server.getHeight(), getZPosition(), getTPosition());
	}

	/**
	 * Get the current downsample factor.
	 * @return
	 */
	public double getDownsampleFactor() {
		return downsampleFactor.get();
	}

	/**
	 * Convert a coordinate from the viewer into the corresponding pixel coordinate in the full-resolution image - optionally constraining it to any server bounds.
	 * A point object can optionally be provided into which the location is written (may be the same as the component point object).
	 * 
	 * @param point
	 * @param pointDest
	 * @param constrainToBounds 
	 * @return
	 */
	public Point2D componentPointToImagePoint(Point2D point, Point2D pointDest, boolean constrainToBounds) {
		return componentPointToImagePoint(point.getX(), point.getY(), pointDest, constrainToBounds);
	}

	/**
	 * Convert x and y coordinates from the component space to the image space.
	 * @param x x coordinate, related to {@link #getView()}
	 * @param y y coordinate, related to {@link #getView()}
	 * @param pointDest object in which to store the corresponding image point (will be set and returned if non-null)
	 * @param constrainToBounds if true, clip the image coordinate computed from x and y to fit within the image bounds
	 * @return a {@link Point2D} referring to the pixel coordinate corresponding to the component coordinate defined by x and y; 
	 */
	public Point2D componentPointToImagePoint(double x, double y, Point2D pointDest, boolean constrainToBounds) {
		if (pointDest == null)
			pointDest = new Point2D.Double(x, y);
		else
			pointDest.setLocation(x, y);
		// Transform the point (in-place)
		transformInverse.transform(pointDest, pointDest);
		// Constrain, if necessary
		ImageServer<BufferedImage> server = getServer();
		if (constrainToBounds && server != null) {
			pointDest.setLocation(
                    Math.clamp(pointDest.getX(), 0, server.getWidth()),
                    Math.clamp(pointDest.getY(), 0, server.getHeight())
					);
		}
		return pointDest;
	}


	/**
	 * Convert a coordinate from the the full-resolution image into the corresponding pixel coordinate in the viewer - optionally constraining it to any viewer component bounds.
	 * A point object can optionally be provided into which the location is written (may be the same as the image point object).
	 * 
	 * @param point
	 * @param pointDest
	 * @param constrainToBounds 
	 * @return
	 */
	public Point2D imagePointToComponentPoint(Point2D point, Point2D pointDest, boolean constrainToBounds) {
		return imagePointToComponentPoint(point.getX(), point.getY(), pointDest, constrainToBounds);
	}

	private Point2D imagePointToComponentPoint(double x, double y, Point2D pointDest, boolean constrainToBounds) {
		if (pointDest == null)
			pointDest = new Point2D.Double(x, y);
		else
			pointDest.setLocation(x, y);
		// Transform the point (in-place)
		transform.transform(pointDest, pointDest);
		// Constrain, if necessary
		if (constrainToBounds) {
			pointDest.setLocation(
                    Math.clamp(pointDest.getX(), 0, getWidth()),
                    Math.clamp(pointDest.getY(), 0, getHeight())
					);
		}
		return pointDest;
	}

	/**
	 * Center the current image in the viewer, while keeping the same downsample factor.
	 * This does nothing if no image is currently open.
	 */
	public void centerImage() {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return;
		setCenterPixelLocation(0.5 * server.getWidth(), 0.5 * server.getHeight());
	}

	/**
	 * Get a string representing the object classification x &amp; y location in the viewer component,
	 * or an empty String if no object is found.
	 * 
	 * @param x x-coordinate in the component space (not image space)
	 * @param y y-coordinate in the component space (not image space)
	 * @return a String to display representing the object classification
	 */
	public String getObjectClassificationString(double x, double y) {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return "";
		var p2 = componentPointToImagePoint(x, y, null, false);
		return getImageObjectClassificationString(p2.getX(), p2.getY());
	}


	/**
	 * Get a string representing the object classification x &amp; y location in the image space,
	 * or an empty String if no object is found.
	 * 
	 * @param x x-coordinate in the image space (not the component/viewer space)
	 * @param y y-coordinate in the image space (not the component/viewer space)
	 * @return a String to display representing the object classification
	 */
	public String getImageObjectClassificationString(double x, double y) {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return "";
		return QuPathViewerUtils.getImageObjectClassificationString(hierarchy, x, y, getImagePlane());
	}



	/**
	 * Get a string to summarize the pixel found below the most recent known mouse location, 
	 * or "" if the mouse is outside this viewer.
	 * 
	 * @param useCalibratedUnits If true, microns will be used rather than pixels (if known).
	 * @return
	 */
	protected String getFullLocationString(boolean useCalibratedUnits) {
		return QuPathViewerUtils.getFullLocationString(this, mouseX, mouseY, useCalibratedUnits);
	}


	/**
	 * Get the object hierarchy for the current image data, or null if no image data is available.
	 * @return
	 */
	public PathObjectHierarchy getHierarchy() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		return temp == null ? null : temp.getHierarchy();
	}

	/**
	 * Add a viewer listener.
	 * @param listener
	 */
	public void addViewerListener(QuPathViewerListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a viewer listener.
	 * @param listener
	 */
	public void removeViewerListener(QuPathViewerListener listener) {
		listeners.remove(listener);
	}


	/**
	 * Set the image pixel to display in the center of the viewer (using image pixel coordinates at the full-resolution)
	 * @param x
	 * @param y
	 */
	public void setCenterPixelLocation(double x, double y) {
		if ((this.xCenter == x && this.yCenter == y) || Double.isNaN(x + y))
			return;
		this.xCenter = x;
		this.yCenter = y;
		updateAffineTransform();
		this.imageUpdated = true;
		this.overlayUpdated = true;
	}

	
	/**
	 * Center the specified ROI in the viewer
	 * @param roi
	 */
	public void centerROI(ROI roi) {
		if (roi == null)
			return;
		double x = roi.getCentroidX();
		double y = roi.getCentroidY();
		setZPosition(roi.getZ());
		setTPosition(roi.getT());
		setCenterPixelLocation(x, y);
	}


	/**
	 * Update the affine transform used to convert coordinates from the viewer space into the image space,
	 * and its inverse.
	 * This should be called on the JavaFX application thread after any relevant changes are made
	 * (e.g., rotation, translation, downsample).
	 */
	protected void updateAffineTransform() {
		if (!hasServer())
			return;

		transform.setToIdentity();
		transform.translate(getWidth()*.5, getHeight()*.5);
		double downsample = getDownsampleFactor();
		transform.scale(1.0/downsample, 1.0/downsample);
		transform.translate(-xCenter, -yCenter);
		if (rotationProperty.get() != 0)
			transform.rotate(rotationProperty.get(), xCenter, yCenter);

		transformInverse.setTransform(transform);
		try {
			transformInverse.invert();
		} catch (NoninvertibleTransformException e) {
			logger.warn("Transform not invertible!", e);
		}

		imageUpdated = true;
		overlayUpdated = true;
	}

	/**
	 * Rotation property for the viewer, defined in radians.
	 * Note that this automatically converts values to fall within 0 and 2 PI radians.
	 */
	private final DoubleProperty rotationProperty = new SimpleDoubleProperty(null, "rotation", 0) {

		private static final double MIN_ROTATION = 0;
		private static final double MAX_ROTATION = 2 * Math.PI;

		@Override
		public void set(double value) {
			double theta = value;
			while (theta < MIN_ROTATION)
				theta += MAX_ROTATION;
			theta = (theta % MAX_ROTATION) + MIN_ROTATION;
			if (value != theta) {
				logger.warn("Converting rotation {} to {}", value, theta);
			}
			super.set(theta);
		}

	};

	/**
	 * Rotation property for the viewer, defined in radians.
	 * Note that this automatically converts values to fall within 0 and 2 PI radians.
	 */
	public DoubleProperty rotationProperty() {
		return rotationProperty;
	}

	public void setRotation(double theta) {
		rotationProperty().set(theta);
	}

	/**
	 * Query if the image in the viewer is rotated for visualization.
	 * @return true if the rotation is not 0.
	 */
	public boolean isRotated() {
		return getRotation() != 0;
	}

	public double getRotation() {
		return rotationProperty().get();
	}


	/**
	 * Force the overlay displaying detections and annotations to be repainted.
	 * Any cached versions will be thrown away, so this is useful when
	 * some aspect of the display has changed, e.g. objects colors or fill/outline status.
	 * Due to the usefulness of caching for performance, it should not be called too often.
	 */
	public void forceOverlayUpdate() {
		overlays.getHierarchyOverlay().clearCachedOverlay();
		overlayUpdated = true;
	}



	@Override
	public void hierarchyChanged(final PathObjectHierarchyEvent event) {
		// Measurement changes don't modify the hierarchy
		if (event.isObjectMeasurementEvent())
			return;
		handleHierarchyChange(event);
	}


	private void handleHierarchyChange(final PathObjectHierarchyEvent event) {
		if (event != null)
			logger.trace(event.toString());

		// TODO: Be aware that this used to be on the application thread...
		//       and may need to be again, if this proves problematic
//		if (!Platform.isFxApplicationThread()) {
//			Platform.runLater(() -> handleHierarchyChange(event));
//			return;
//		}
		
		// Clear any cached regions of the overlay, if necessary
		// TODO: Make this update a bit less conservative - it isn't really needed if we don't modify detections?
		var hierarchyOverlay = overlays.getHierarchyOverlay();
		if (event == null || event.isStructureChangeEvent())
			hierarchyOverlay.clearCachedOverlay();
		else {
			List<PathObject> pathObjects = event.getChangedObjects();
			List<PathObject> pathDetectionObjects = PathObjectTools.getObjectsOfClass(pathObjects, PathDetectionObject.class);
			if (pathDetectionObjects.size() <= 50) {
				// TODO: PUT THIS LISTENER INTO THE HIERARCHY OVERLAY ITSELF?  But then the order of events is uncertain... hierarchy would need to be able to call repaint as well
				// (or possibly post an event?)
				for (PathObject temp : pathDetectionObjects) {
					if (temp.hasROI())
						hierarchyOverlay.clearCachedOverlayForRegion(ImageRegion.createInstance(temp.getROI()));
				}
			} else {
				hierarchyOverlay.clearCachedOverlay();
			}
		}

		// Just in case, make sure the handles are updated in any ROIEditor
		if (event != null && !event.isChanging())
			updateRoiEditor();
		// Request repaint
		overlayUpdated = true;
	}



	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
		updateRoiEditor();
		for (QuPathViewerListener listener : new ArrayList<>(listeners)) {
			listener.selectedObjectChanged(this, pathObjectSelected);
		}

		logger.trace("Selected path object changed from {} to {}", previousObject, pathObjectSelected);

		repaint();
	}

	private void updateRoiEditor() {
		PathObject pathObjectSelected = getSelectedObject();
		ROI previousROI = roiEditor.getROI();
		ROI newROI = pathObjectSelected != null && pathObjectSelected.isEditable() ? pathObjectSelected.getROI() : null;

		if (previousROI == newROI)
			roiEditor.ensureHandlesUpdated();
		else
			roiEditor.setROI(newROI);

		repaint();		
	}


	private synchronized String getServerPath() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? null : server.getPath();
	}


    private final MoveToolEventHandler.ViewerMover mover = new MoveToolEventHandler.ViewerMover(this);


	/**
	 * Request that the viewer stop any panning immediately.
	 *
	 * @see #requestDecelerate
	 * @see #requestStartMoving
	 */
	public void requestStopMoving() {
		mover.stopMoving();
	}

	/**
	 * Request that a viewer decelerate any existing panning smoothly.
	 *
	 * @see #requestStartMoving
	 * @see #requestStopMoving
	 */
	public void requestDecelerate() {
		mover.decelerate();
	}

	/**
	 * Request that the viewer start panning with a velocity determined by dx and dy.
	 * <p>
	 * This can be used in combination with {@code requestDecelerate} to end a panning event more smoothly.
	 *
	 * @param dx
	 * @param dy
	 *
	 * @see #requestDecelerate
	 * @see #requestStopMoving
	 */
	public void requestStartMoving(final double dx, final double dy) {
		mover.startMoving(dx, dy, true);
		this.setDoFasterRepaint(true);
	}

	/**
	 * Requests that the viewer cancels movement in the x-axis direction.
	 */
	void requestCancelMoveX() {
		mover.cancelDirection(true);
	}

	/**
	 * Requests that the viewer cancels movement in the y-axis direction.
	 */
	void requestCancelMoveY() {
		mover.cancelDirection(false);
	}


	@Override
	public String toString() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		if (temp != null)
			return getClass().getSimpleName() + " - " + temp.getServerPath();
		return getClass().getSimpleName() + " - no server";
	}

}
