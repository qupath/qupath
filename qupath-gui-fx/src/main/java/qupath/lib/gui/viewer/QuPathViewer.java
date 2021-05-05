/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.LookupOp;
import java.awt.image.ByteLookupTable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.servers.PathHierarchyImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreHelpers;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.images.stores.TileListener;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.GridOverlay;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.overlays.TMAGridOverlay;
import qupath.lib.gui.viewer.tools.MoveTool;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;


/**
 * JavaFX component for viewing a (possibly large) image, along with overlays.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathViewer implements TileListener<BufferedImage>, PathObjectHierarchyListener, PathObjectSelectionListener {

	private final static Logger logger = LoggerFactory.getLogger(QuPathViewer.class);

	private static final double MIN_ROTATION = 0;
	private static final double MAX_ROTATION = 360 * Math.PI / 180;

	private List<QuPathViewerListener> listeners = new ArrayList<>();

	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

	private DefaultImageRegionStore regionStore;

	// Overlay (ROI/object) display variables
	private OverlayOptions overlayOptions;

	// An overlay used to display an ImageServer wrapping a PathObjectHierarchy, for faster painting when there are a lot of objects
	private HierarchyOverlay hierarchyOverlay = null;
	// An overlay to show a TMA grid
	private TMAGridOverlay tmaGridOverlay;
	// An overlay to show a regular grid (e.g. for counting)
	private GridOverlay gridOverlay;
//	// A default overlay to show a pixel layer on top of an image
//	private PixelLayerOverlay pixelLayerOverlay = null;
	// A custom pixel overlay to use instead of the default
	private PathOverlay customPixelLayerOverlay = null;
	
	// Overlay layers that can be edited
	private ObservableList<PathOverlay> customOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
	
	// Core overlay layers - these are always retained, and painted on top of any custom layers
	private ObservableList<PathOverlay> coreOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
	
	// List that concatenates the custom & core overlay layers in painting order
	private ObservableList<PathOverlay> allOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

	// Current we have two images - one transformed & one not - because the untransformed
	// image is needed to determine pixel values as the mouse moves over the image
	private BufferedImage imgBuffer = null;
	//	private BufferedImage imgTemp = null;

	// Keep a reference to a thumbnail image here, and apply color transforms to it
	//	private BufferedImage imgThumbnail;
	private BufferedImage imgThumbnailRGB; // An RGB thumbnail, which may have been transformed (or null if imgThumbnail is already RGB)
	private boolean thumbnailIsFullImage = false;

	// Flag used to indicates that the image was updated for a repaint (otherwise it's assumed only the overlay may have changed)
	protected boolean imageUpdated = false;
	protected boolean locationUpdated = false;
	
	// Flag that is temporarily set to true while the ImageData is being set
	private BooleanProperty imageDataChanging = new SimpleBooleanProperty(false);
	
	// Create tooltip to use!
	// Currently disabled because JavaFX tooltips seem to behave quite erratically -
	// remaining too long in the same place
	private Tooltip tooltip = null; //new Tooltip();

	// Location & magnification variables
	// x & y coordinates - in the image space - of the center of the displayed region
	private double xCenter = 0;
	private double yCenter = 0;
	private DoubleProperty downsampleFactor = new SimpleDoubleProperty(1.0);
	private DoubleProperty rotationProperty = new SimpleDoubleProperty(0);
	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
	
	// Affine transform used to apply rotation
	private AffineTransform transform = new AffineTransform();
	private AffineTransform transformInverse = new AffineTransform();

	// Flag to indicate that repainting should occur faster if possible (less detail required)
	// This can be useful when rapidly changing view, for example
	private boolean doFasterRepaint = false;
	
	private Color background = ColorToolsAwt.getCachedColor(PathPrefs.viewerBackgroundColorProperty().get());

	// Keep a record of when the spacebar is pressed, to help with dragging to pan
	private boolean spaceDown = false;

	protected Color colorOverlaySuggested = null;
	
	// Requested cursor - but this may be overridden temporarily
	private Cursor requestedCursor = Cursor.DEFAULT;

	// The shape (coordinates in the image domain) last painted
	// Used to determine whether the visible part of the image has been changed
	private Shape lastVisibleShape = null;

	private RoiEditor roiEditor = RoiEditor.createInstance();

	private ObjectProperty<PathTool> currentTool = new SimpleObjectProperty<>(PathTools.MOVE);
	private ImageDisplay imageDisplay;
	transient private long lastDisplayChangeTimestamp = 0; // Used to indicate imageDisplay changes

	private LongProperty lastRepaintTimestamp = new SimpleLongProperty(0L); // Used for debugging repaint times
	
	private boolean repaintRequested = false;
	
	private double mouseX, mouseY;
	
	private StackPane pane;
	private Canvas canvas;
	private BufferedImage imgCache;
	private WritableImage imgCacheFX;
	
	private double borderLineWidth = 5;
	private javafx.scene.paint.Color borderColor;
	
	/**
	 * Get the main JavaFX component representing this viewer.
	 * This is what should be added to a scene.
	 * @return
	 */
	public Pane getView() {
		if (canvas == null) {
			setupCanvas();
		}
		return pane;
	}
	
	private void setupCanvas() {
		canvas = new Canvas();
		addViewerListener(new QuPathViewerListener() {

			@Override
			public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
					ImageData<BufferedImage> imageDataNew) {
				paintCanvas();
			}

			@Override
			public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
//				paintCanvas();
			}

			@Override
			public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
//				paintCanvas();
			}

			@Override
			public void viewerClosed(QuPathViewer viewer) {
				removeViewerListener(this);
				canvas = null;
			}
			
		});
		
		canvas.widthProperty().addListener((e, f, g) -> {
			if (getZoomToFit()) {
				centerImage();
				// Make sure the downsample factor is being continually updated
				downsampleFactor.set(getZoomToFitDownsampleFactor());
			} else {
				updateAffineTransform();
				repaint();
			}
		});
		canvas.heightProperty().addListener((e, f, g) -> {
			if (getZoomToFit()) {
				centerImage();
				// Make sure the downsample factor is being continually updated
				downsampleFactor.set(getZoomToFitDownsampleFactor());
			} else {
				updateAffineTransform();
				repaint();
			}
		});
		
		pane = new StackPane();
//		pane.setStyle("fx-background-color: black;");
		pane.getChildren().add(canvas);
		canvas.widthProperty().bind(pane.widthProperty());
		canvas.heightProperty().bind(pane.heightProperty());
		
		// Resize to anything
		pane.setMinWidth(1);
		pane.setMinHeight(1);
		pane.setMaxWidth(Double.MAX_VALUE);
		pane.setMaxHeight(Double.MAX_VALUE);
		
		pane.addEventFilter(MouseEvent.ANY, e -> {
			mouseX = e.getX();
			mouseY = e.getY();
			
			if (tooltip != null && tooltip.isShowing())
				updateTooltip(tooltip);
		});
		
		pane.addEventFilter(KeyEvent.ANY, new KeyEventFilter());
		pane.addEventHandler(KeyEvent.ANY, new KeyEventHandler());

	}
	
	
	/**
	 * Update allOverlayLayers to make sure it contains all the required PathOverlays.
	 */
	private synchronized void refreshAllOverlayLayers() {
		List<PathOverlay> temp = new ArrayList<>();
		temp.addAll(customOverlayLayers);
		temp.addAll(coreOverlayLayers);
		allOverlayLayers.setAll(temp);
	}
	
	
	private long lastPaint = 0;
	private long minimumRepaintSpacingMillis = -1; // This can be used (temporarily) to prevent repaints happening too frequently
	
	/**
	 * Prevent frequent repaints (temporarily) by setting a minimum time that must have elapsed
	 * after the previous repaint for a new one to be triggered.
	 * (Repaint requests that come in between are simply disregarded for performance.)
	 * <p>
	 * When finished, it's necessary to call resetMinimumRepaintSpacingMillis() to make sure that 
	 * normal service is resumed.
	 * 
	 * @param repaintSpacingMillis
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
		repaintRequested = false;
		repaint();
	}

	
	void paintCanvas() {
		// Ensure there's always a repaint requested whenever the image is updated
		// (Should be the case anyway)
		if (imageUpdated) {
			repaintRequested = true;
		}
		
		if (!repaintRequested || canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
			repaintRequested = false;
			return;
		}
//		if (canvas == null || !canvas.isVisible())
//			return;
		
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> paintCanvas());
			return;
		}
		
		if (imgCache == null || imgCache.getWidth() < canvas.getWidth() || imgCache.getHeight() < canvas.getHeight()) {
			int w = (int)(canvas.getWidth() + 1);
			int h = (int)(canvas.getHeight() + 1);
			imgCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
			imgCacheFX = new WritableImage(w, h);
//			imgCacheFX = SwingFXUtils.toFXImage(imgCache, imgCacheFX);
		}
		
		// Reset repaint flag
		repaintRequested = false;
		
		GraphicsContext context = canvas.getGraphicsContext2D();
		
		Graphics2D g = imgCache.createGraphics();
		paintViewer(g, getWidth(), getHeight());
		g.dispose();
		
		imgCacheFX = SwingFXUtils.toFXImage(imgCache, imgCacheFX);
		context.drawImage(imgCacheFX, 0, 0);
		
		if (borderColor != null) {
			context.setStroke(borderColor);
			context.setLineWidth(borderLineWidth);
			context.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
		}
		
		
		
//		// Basic code for including the TMA core name
//		if (getHierarchy().getTMAGrid() != null) {
//			Point2D pSource = new Point2D.Double();
//			Point2D pDest = new Point2D.Double();
//			context.setTextAlign(TextAlignment.CENTER);
//			context.setTextBaseline(VPos.CENTER);
//			for (TMACoreObject core : getHierarchy().getTMAGrid().getTMACoreList()) {
//				if (core.getName() == null)
//					continue;
//				double x = core.getROI().getBoundsX() + core.getROI().getBoundsWidth()/2;
//				double y = core.getROI().getBoundsY() + core.getROI().getBoundsHeight()/2;
//				pSource.setLocation(x, y);
//				transform.transform(pSource, pDest);
//				context.setFill(getSuggestedOverlayColorFX());
//				context.setStroke(javafx.scene.paint.Color.WHITE);
//				double xf = pDest.getX();
//				double yf = pDest.getY();
//				context.fillText(core.getName(), xf, yf, core.getROI().getBoundsWidth()/getDownsampleFactor()*0.5);
//			}
//		}
		
		
//		if (getServer() == null) {
//			context.setStroke(javafx.scene.paint.Color.GREENYELLOW);
//			context.setLineWidth(borderLineWidth);
//			context.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
//		}
		
		long time = System.currentTimeMillis();
		logger.trace("Time since last repaint: {} ms", (time - lastPaint));
		lastPaint = System.currentTimeMillis();
		
		imageDataChanging.set(false);
//		repaintRequested = false;
	}
	
	/**
	 * Set the border color for this viewer.
	 * This can be used to indicate (for example) that a particular viewer is active.
	 * @param color
	 */
	public void setBorderColor(final javafx.scene.paint.Color color) {
		this.borderColor = color;
		if (Platform.isFxApplicationThread()) {
			repaintRequested = true;
			paintCanvas();
		} else
			repaint();
	}

	/**
	 * Get the border color set for this viewer.
	 * @return
	 */
	public javafx.scene.paint.Color getBorderColor() {
		return borderColor;
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
		if (repaintRequested && minimumRepaintSpacingMillis <= 0)
			return;
		
		// We need to repaint everything if the display changed
		if (imageDisplay != null && (lastDisplayChangeTimestamp != imageDisplay.getLastChangeTimestamp())) {
			repaintEntireImage();
			return;
		}
		
		logger.trace("Repaint requested!");
		repaintRequested = true;
		
		// Skip repaint if the minimum time hasn't elapsed
		if ((System.currentTimeMillis() - lastPaint) < minimumRepaintSpacingMillis)
			return;
		Platform.runLater(() -> paintCanvas());
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

	
	private InvalidationListener repainter = new InvalidationListener() {
		@Override
		public void invalidated(Observable observable) {
			repaint();
		}
	};
	
	// We need a more extensive repaint for changes to the image pixel display
	private InvalidationListener repainterEntire = new InvalidationListener() {
		@Override
		public void invalidated(Observable observable) {
			background = ColorToolsAwt.getCachedColor(PathPrefs.viewerBackgroundColorProperty().get());
			repaintEntireImage();
		}
	};
	
	// We need a more extensive repaint for changes to the image pixel display
	private InvalidationListener repainterOverlay = new InvalidationListener() {
		@Override
		public void invalidated(Observable observable) {
			forceOverlayUpdate();
			background = ColorToolsAwt.getCachedColor(PathPrefs.viewerBackgroundColorProperty().get());
			repaint();
		}
	};
	
	
	
	static class ListenerManager {
		
		private List<ListenerHandler> handlers = new ArrayList<>();
		
		public ListenerHandler attachListener(Observable observable, InvalidationListener listener) {
			ListenerHandler handler = new ObservableListenerHandler(observable, listener);
			handler.attach();
			handlers.add(handler);
			return handler;
		}
		
		public <T> ListenerHandler attachListener(ObservableValue<T> observable, ChangeListener<T> listener) {
			ListenerHandler handler = new ObservableValueListenerHandler<>(observable, listener);
			handlers.add(handler);
			handler.attach();
			return handler;
		}

		public <T> ListenerHandler attachListener(ObservableList<T> observable, ListChangeListener<T> listener) {
			ListenerHandler handler = new ObservableListListenerHandler<>(observable, listener);
			handlers.add(handler);
			handler.attach();
			return handler;
		}

		public void detachAll() {
			handlers.stream().forEach(h -> h.detach());
		}
		
		public void clear() {
			this.handlers.clear();
		}
		
	}
	
	static interface ListenerHandler {
		void attach();
		void detach();
	}
	
	static class ObservableListenerHandler implements ListenerHandler {
		
		private Observable observable;
		private InvalidationListener listener;
		
		private ObservableListenerHandler(Observable observable, InvalidationListener listener) {
			this.observable = observable;
			this.listener = listener;
		}
		
		@Override
		public void attach() {
			this.observable.addListener(listener);
		}
		
		@Override
		public void detach() {
			this.observable.removeListener(listener);
		}
		
	}
	
	static class ObservableListListenerHandler<T> implements ListenerHandler {
		
		private ObservableList<T> observable;
		private ListChangeListener<T> listener;
		
		private ObservableListListenerHandler(ObservableList<T> observable, ListChangeListener<T> listener) {
			this.observable = observable;
			this.listener = listener;
		}

		@Override
		public void attach() {
			observable.addListener(listener);
		}

		@Override
		public void detach() {
			observable.removeListener(listener);
		}
		
	}

	static class ObservableValueListenerHandler<T> implements ListenerHandler {
		
		private ObservableValue<T> observable;
		private ChangeListener<T> listener;
		
		private ObservableValueListenerHandler(ObservableValue<T> observable, ChangeListener<T> listener) {
			this.observable = observable;
			this.listener = listener;
		}

		@Override
		public void attach() {
			observable.addListener(listener);
		}

		@Override
		public void detach() {
			observable.removeListener(listener);
		}
		
	}
	
//	protected void finalize() throws Throwable {
//		System.err.println("Viewer being removed!");
//		super.finalize();
//	}
	
	
	private ListenerManager manager = new ListenerManager();
	private ListenerManager overlayOptionsManager = new ListenerManager();
	

	/**
	 * Create a new viewer.
	 * @param imageData image data to show within the viewer
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 */
	public QuPathViewer(final ImageData<BufferedImage> imageData, DefaultImageRegionStore regionStore, OverlayOptions overlayOptions) {
		this(imageData, regionStore, overlayOptions, new ImageDisplay(null));
	}
	
	/**
	 * Create a new viewer.
	 * @param imageData image data to show within the viewer
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 * @param imageDisplay image display used to control the image display (conversion to RGB)
	 */
	public QuPathViewer(final ImageData<BufferedImage> imageData, DefaultImageRegionStore regionStore, OverlayOptions overlayOptions, ImageDisplay imageDisplay) {
		super();

		this.regionStore = regionStore;

		setOverlayOptions(overlayOptions);
		
		// We need a simple repaint for color changes & simple (thick) line changes
		manager.attachListener(PathPrefs.annotationStrokeThicknessProperty(), repainter);
		
		manager.attachListener(PathPrefs.viewerGammaProperty(), repainterEntire);
		manager.attachListener(PathPrefs.viewerInterpolateBilinearProperty(), repainterEntire);
		manager.attachListener(PathPrefs.viewerBackgroundColorProperty(), repainterEntire);
		
		manager.attachListener(PathPrefs.showPointHullsProperty(), repainter);
		manager.attachListener(PathPrefs.useSelectedColorProperty(), repainter);
		manager.attachListener(PathPrefs.colorDefaultObjectsProperty(), repainterOverlay);
		manager.attachListener(PathPrefs.colorSelectedObjectProperty(), repainter);
		manager.attachListener(PathPrefs.colorTileProperty(), repainter);
		manager.attachListener(PathPrefs.colorTMAProperty(), repainter);
		manager.attachListener(PathPrefs.colorTMAMissingProperty(), repainter);
		manager.attachListener(PathPrefs.alwaysPaintSelectedObjectsProperty(), repainter);
		manager.attachListener(PathPrefs.viewerFontSizeProperty(), repainter);

		manager.attachListener(PathPrefs.gridSpacingXProperty(), repainter);
		manager.attachListener(PathPrefs.gridSpacingYProperty(), repainter);
		manager.attachListener(PathPrefs.gridStartXProperty(), repainter);
		manager.attachListener(PathPrefs.gridStartYProperty(), repainter);
		manager.attachListener(PathPrefs.gridScaleMicronsProperty(), repainter);

		// We need to repaint everything if detection line thickness changes - including any cached regions
		manager.attachListener(PathPrefs.detectionStrokeThicknessProperty(), repainterOverlay);		

		// Can be used to debug graphics
		//		setDoubleBuffered(false);
		//		RepaintManager.currentManager(this).setDoubleBufferingEnabled(false);
		//		setDebugGraphicsOptions(DebugGraphics.LOG_OPTION);

		this.imageDisplay = imageDisplay;

		// Prepare overlay layers
		customOverlayLayers.addListener((Change<? extends PathOverlay> e) -> refreshAllOverlayLayers());
		coreOverlayLayers.addListener((Change<? extends PathOverlay> e) -> refreshAllOverlayLayers());
		allOverlayLayers.addListener((Change<? extends PathOverlay> e) -> repaint());
		
		hierarchyOverlay = new HierarchyOverlay(this.regionStore, overlayOptions, imageData);
		tmaGridOverlay = new TMAGridOverlay(overlayOptions);
		gridOverlay = new GridOverlay(overlayOptions);
//		pixelLayerOverlay = new PixelLayerOverlay(this);
		// Set up the overlay layers
		coreOverlayLayers.setAll(
//				pixelLayerOverlay,
				tmaGridOverlay,
				hierarchyOverlay,
				gridOverlay
		);

		setImageData(imageData);

		this.regionStore.addTileListener(this);

		//		updateCursor();
		imageUpdated = true;

		if (tooltip != null) {
			tooltip.setTextAlignment(TextAlignment.CENTER);
			tooltip.activatedProperty().addListener((v, o, n) -> {
				if (n) 
					updateTooltip(tooltip);
			});
			tooltip.setAutoHide(true);
			Tooltip.install(getView(), tooltip);
		}
		
		
		zPosition.addListener((v, o, n) -> {
//			if (zPosition.get() == n)
//				return;
			imageUpdated = true;
			updateThumbnail(false);
			repaint();
			fireVisibleRegionChangedEvent(getDisplayedRegionShape());
		});
		
		
		tPosition.addListener((v, o, n) -> {
//			if (zPosition.get() == n)
//				return;
			imageUpdated = true;
			updateThumbnail(false);
			repaint();
			fireVisibleRegionChangedEvent(getDisplayedRegionShape());
		});
		
		zoomToFit.addListener((v, o, n) -> {
			if (zoomToFit.get()) {
				setDownsampleFactorImpl(getZoomToFitDownsampleFactor(), -1, -1);
				centerImage();
			}
			imageUpdated = true;
			repaint();
		});
		
		rotationProperty.addListener((v, o, n) -> {
			imageUpdated = true;
			updateAffineTransform();
			repaint();
		});
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
		if (mouseX >= 0 && mouseX <= canvas.getWidth() && mouseY >= 0 && mouseY <= canvas.getWidth())
			return new Point2D.Double(mouseX, mouseY);
		return null;
	}
	

	private void setOverlayOptions(OverlayOptions overlayOptions) {
		if (this.overlayOptions == overlayOptions)
			return;
		if (this.overlayOptions != null) {
			overlayOptionsManager.detachAll();
//			this.overlayOptions.removePropertyChangeListener(this);
		}
		this.overlayOptions = overlayOptions;
		if (overlayOptions != null) {
			
			overlayOptionsManager.attachListener(overlayOptions.fillDetectionsProperty(), repainterOverlay);
			overlayOptionsManager.attachListener(overlayOptions.hiddenClassesProperty(), repainterOverlay);
			overlayOptionsManager.attachListener(overlayOptions.measurementMapperProperty(), repainterOverlay);
			overlayOptionsManager.attachListener(overlayOptions.detectionDisplayModeProperty(), repainterOverlay);
			overlayOptionsManager.attachListener(overlayOptions.showConnectionsProperty(), repainterOverlay);

			overlayOptionsManager.attachListener(overlayOptions.showAnnotationsProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showNamesProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.fillAnnotationsProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showDetectionsProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showPixelClassificationProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.pixelClassificationFilterRegionProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.gridLinesProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showTMACoreLabelsProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showGridProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.showTMAGridProperty(), repainter);
			overlayOptionsManager.attachListener(overlayOptions.opacityProperty(), repainter);

		}
		if (isShowing())
			repaint();
	}

	/**
	 * Returns true if the viewer is visible, and attached to a scene.
	 * @return
	 */
	public boolean isShowing() {
		return canvas != null && canvas.isVisible() && canvas.getScene() != null;
	}
	


	protected void initializeForServer(ImageServer<BufferedImage> server) {
		// Note that the image has updated
		imageUpdated = true;

		if (server == null) {
			zPosition.set(0);
			tPosition.set(0);
			return;
		}

		zPosition.set(server.nZSlices() / 2);
		tPosition.set(0);
		updateThumbnail();

//		if (thumbnailIsFullImage)
//			overlayOptions.setThinStrokeThickness(1f);
//		else
//			overlayOptions.setThinStrokeThickness(2f);

		// Reset the suggested color for the scalebar & grid
		colorOverlaySuggested = null;
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
				if (activeTool != null)
					activeTool.deregisterTool(this);
				activeTool = PathTools.MOVE;
				if (activeTool != null)
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


	private static int getMeanBrightnessRGB(final BufferedImage img, int x, int y, int w, int h) {
		if (img == null)
			return 0;
		double sum = 0;
		if (w < 0)
			w = img.getWidth();
		if (h < 0)
			h = img.getHeight();
		int[] pixels = new int[w * h];
		img.getRGB(x, y, w, h, pixels, 0, w);
		double scale = 1. / (3. * w * h); // To convert to mean
		for (int c : pixels) {
			int r = (c & ColorTools.MASK_RED) >> 16;
		int g = (c & ColorTools.MASK_GREEN) >> 8;
		int b = c & ColorTools.MASK_BLUE;
		sum += (r + g + b) * scale;
		}
		// Convert to mean brightness
		return (int)(sum + .5);
	}

	/**
	 * Update colorOverlaySuggested from the entire (RGB, i.e. color-transformed) image thumbnail
	 */
	void updateSuggestedOverlayColorFromThumbnail() {
		if (getMeanBrightnessRGB(imgThumbnailRGB, 0, 0, imgThumbnailRGB.getWidth(), imgThumbnailRGB.getHeight()) > 127)
			colorOverlaySuggested = ColorToolsAwt.TRANSLUCENT_BLACK;
		else
			colorOverlaySuggested = ColorToolsAwt.TRANSLUCENT_WHITE;
	}


	Color getSuggestedOverlayColor() {
		if (colorOverlaySuggested == null)
			updateSuggestedOverlayColorFromThumbnail();
		return colorOverlaySuggested;
	}
	
	javafx.scene.paint.Color getSuggestedOverlayColorFX() {
		Color c = getSuggestedOverlayColor();
		if (c == ColorToolsAwt.TRANSLUCENT_BLACK)
			return ColorToolsFX.TRANSLUCENT_BLACK_FX;
//		else if (c == DisplayHelpers.TRANSLUCENT_WHITE):
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

	
	protected void updateCursor() {
//		logger.debug("Requested cursor {} for {}", requestedCursor, getMode());
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
		if (this.customPixelLayerOverlay == pathOverlay)
			return;
		// Get existing custom overlay
		var previousOverlay = getCurrentPixelLayerOverlay();
		int ind = coreOverlayLayers.indexOf(previousOverlay);
		this.customPixelLayerOverlay = pathOverlay;
		if (this.customPixelLayerOverlay == null) {
			if (ind >= 0)
				coreOverlayLayers.remove(ind);
		} else if (ind < 0) {
			coreOverlayLayers.add(0, this.customPixelLayerOverlay);
		} else {
			coreOverlayLayers.set(ind, this.customPixelLayerOverlay);
		}
				
//		// Get existing custom overlay
//		var previousOverlay = getCurrentPixelLayerOverlay();
//		int ind = coreOverlayLayers.indexOf(previousOverlay);
//		this.customPixelLayerOverlay = pathOverlay;
//		if (ind < 0) {
//			logger.warn("Pixel layer overlay not found! Will try to recover...");
//			coreOverlayLayers.removeAll(pixelLayerOverlay, customPixelLayerOverlay);
//			coreOverlayLayers.add(0, getCurrentPixelLayerOverlay());
//		} else {
//			coreOverlayLayers.set(ind, getCurrentPixelLayerOverlay());
//		}
	}
	
	/**
	 * Reset the custom pixel layer overlay to null.
	 */
	public void resetCustomPixelLayerOverlay() {
		setCustomPixelLayerOverlay(null);
	}

	
	private PathOverlay getCurrentPixelLayerOverlay() {
		return customPixelLayerOverlay;
//		return customPixelLayerOverlay == null ? pixelLayerOverlay : customPixelLayerOverlay;
	}
	

	/**
	 * Get the custom pixel layer overlay, or null if it has not be set.
	 * 
	 * @return
	 */
	public PathOverlay getCustomPixelLayerOverlay() {
		return customPixelLayerOverlay;
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


	// TODO: Consider making thumbnail update private
	void updateThumbnail() {
		updateThumbnail(true);
	}



	// TODO: Consider making thumbnail update private
	void updateThumbnail(final boolean updateOverlayColor) {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return;

		// Read a thumbnail image
		try {
			int z = GeneralTools.clipValue(getZPosition(), 0, server.nZSlices()-1);
			int t = GeneralTools.clipValue(getTPosition(), 0, server.nTimepoints()-1);
			BufferedImage imgThumbnail = regionStore.getThumbnail(server, z, t, true);
//			BufferedImage imgThumbnail = regionStore.getThumbnail(server, getZPosition(), getTPosition(), true);
			imgThumbnailRGB = createThumbnailRGB(imgThumbnail);
			thumbnailIsFullImage = imgThumbnailRGB.getWidth() == server.getWidth() && imgThumbnailRGB.getHeight() == server.getHeight();
			if (updateOverlayColor)
				colorOverlaySuggested = null;
		} catch (IOException e) {
			imgThumbnailRGB = null;
			colorOverlaySuggested = null;
			logger.warn("Error requesting thumbnail {}", e.getLocalizedMessage());
		}
	}
	
	/**
	 * Create an RGB thumbnail image using the current rendering settings.
	 * <p>
	 * Subclasses may choose to override this if a suitable image has been cached already.
	 * 
	 * @return
	 */
	BufferedImage createThumbnailRGB(BufferedImage imgThumbnail) throws IOException {
		ImageRenderer renderer = getRenderer();
		if (renderer != null) // && !server.isRGB()) // Transforms will be applied quickly to RGB images, so no need to cache transformed part now
			return renderer.applyTransforms(imgThumbnail, null);
		else
			return imgThumbnail;
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
	protected ImageRenderer getRenderer() {
		return getImageDisplay();
	}
	


	/**
	 * Get a shape corresponding to the region of the image currently visible in this viewer.
	 * Coordinates are in the image space.
	 * 
	 * If no rotation is applied, the result will be an instance of java.awt.Rectangle.
	 * Otherwise it will be a Path2D with the rotated rectangle vertices.
	 * 
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
		if (clip2 instanceof Rectangle2D && getRotation() == 0) {
			Rectangle2D rect = (Rectangle2D)clip2;
			double[] coords = new double[]{rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY()};
			transformInverse.transform(coords, 0, coords, 0, 2);
			// Create a new rectangle if we need to - otherwise reuse one we just created (because clip == null)
			if (rect == clip)
				rect = new Rectangle2D.Double();
			rect.setFrameFromDiagonal(coords[0], coords[1], coords[2], coords[3]);
			return rect;
		}
		return transformInverse.createTransformedShape(clip2);
	}

	/**
	 * Returns the value of {@link #zoomToFitProperty()}.
	 * @return
	 */
	public boolean getZoomToFit() {
		return zoomToFit.get();
	}
	
	/**
	 * Property to request that the downsample and location properties are adjusted automatically to fit the 
	 * current image within the available viewer component.
	 * @return
	 */
	public BooleanProperty zoomToFitProperty() {
		return zoomToFit;
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


	private IntegerProperty tPosition = new SimpleIntegerProperty(0);
	private IntegerProperty zPosition = new SimpleIntegerProperty(0);

	/**
	 * Set the requested z-slice to be visible.
	 * @param zPos
	 */
	public void setZPosition(int zPos) {
		zPosition.set(zPos);
	}

	/**
	 * Get the currently-visible time point.
	 * @return
	 */
	public int getTPosition() {
		return tPosition.get();
	}

	/**
	 * Set the requested time point to be visible.
	 * @param tPosition
	 */
	public void setTPosition(int tPosition) {
		this.tPosition.set(tPosition);
	}

	/**
	 * Get the currently-visible z-slice.
	 * @return
	 */
	public int getZPosition() {
		return zPosition.get();
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
	public void setImageData(ImageData<BufferedImage> imageDataNew) {
		if (this.imageDataProperty.get() == imageDataNew)
			return;
		
		imageDataChanging.set(true);
		
		// Remove listeners for previous hierarchy
		ImageData<BufferedImage> imageDataOld = this.imageDataProperty.get();
		if (imageDataOld != null) {
			imageDataOld.getHierarchy().removePathObjectListener(this);
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
				if (imageDisplay.getImageData() != null && serversCompatible(imageDataNew.getServer(), imageDisplay.getImageData().getServer())) {
					imageDisplay.setImageData(imageDataNew, keepDisplay);
					displaySet = true;
				} else {
					for (var viewer : QuPathGUI.getInstance().getViewers()) {
						if (this == viewer || viewer.getImageData() == null)
							continue;
						var tempServer = viewer.getServer();
						var currentServer = imageDataNew.getServer();
						if (serversCompatible(tempServer, currentServer)) {
							var json = viewer.getImageDisplay().toJSON(false);
							imageDataNew.setProperty(ImageDisplay.class.getName(), json);
							imageDisplay.setImageData(imageDataNew, false);
							displaySet = true;
							break;
						}
					}
				}
			}
			if (!displaySet)
				imageDisplay.setImageData(imageDataNew, keepDisplay);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Setting ImageData time: {} ms", endTime - startTime);

		initializeForServer(server);
		
		if (!sameServer) {
			setDownsampleFactorImpl(getZoomToFitDownsampleFactor(), -1, -1);
			centerImage();
		}

		fireImageDataChanged(imageDataOld, imageDataNew);

		//		featureMapWrapper = new TiledFeatureMapImageWrapper(server.getWidth(), server.getHeight());

//		// Notify overlays of change to ImageData
//		Iterator<PathOverlay> iter = allOverlayLayers.iterator();
//		while (iter.hasNext()) {
//			PathOverlay overlay = iter.next();
//			if (overlay instanceof ImageDataOverlay) {
//				ImageDataOverlay overlay2 = (ImageDataOverlay)overlay;
//				if (!overlay2.supportsImageDataChange()) {
//					// Remove any non-core overlay layers that don't support an ImageData change
//					if (!coreOverlayLayers.contains(overlay2))
//						iter.remove();
//					continue;
//				} else
//					overlay2.setImageData(imageDataNew);
//			}
//		}
		//		overlay.setImageData(imageData);

		if (imageDataNew != null) {
			//			hierarchyPainter = new PathHierarchyPainter(hierarchy);
			hierarchy.addPathObjectListener(this);
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
		}

		setSelectedObject(null);
		
		// TODO: Consider shifting, fixing magnification, repainting etc.
		if (isShowing())
			repaint();
		
		logger.info("Image data set to {}", imageDataNew);
	}
	
	
	/**
	 * Check if two ImageServers are compatible in terms of display settings, i.e. having the same number, type and names for channels.
	 * @param currentServer
	 * @param tempServer
	 * @return true if the servers are compatible, false otherwise
	 */
	private static boolean serversCompatible(ImageServer<BufferedImage> currentServer, ImageServer<BufferedImage> tempServer) {
		if (Objects.equals(currentServer, tempServer))
			return true;
		if (currentServer == null || tempServer == null)
			return false;
		if (tempServer.nChannels() == currentServer.nChannels() && tempServer.getPixelType() == currentServer.getPixelType()) {
			var tempNames = tempServer.getMetadata().getChannels().stream().map(c -> c.getName()).collect(Collectors.toList());
			var currentNames = currentServer.getMetadata().getChannels().stream().map(c -> c.getName()).collect(Collectors.toList());
			return tempNames.equals(currentNames);
		}
		return false;
	}

	
	protected void fireImageDataChanged(ImageData<BufferedImage> imageDataPrevious, ImageData<BufferedImage> imageDataNew) {
		for (QuPathViewerListener listener : listeners.toArray(new QuPathViewerListener[0]))
			listener.imageDataChanged(this, imageDataPrevious, imageDataNew);		
	}

	protected void fireVisibleRegionChangedEvent(Shape shape) {
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

		//		// Clip bounds are overestimated a bit to avoid trouble due to antialiasing
		//		Rectangle clipBounds = new Rectangle((int)((region.getX()-x)/downsampleFactor)-10,
		//        		(int)((region.getY()-y)/downsampleFactor)-10,
		//        		(int)(region.getWidth() / downsampleFactor + 20),
		//        		(int)(region.getHeight() / downsampleFactor + 20));
		if (clipBounds.intersects(0, 0, getWidth(), getHeight())) {
			if (updateImage)
				imageUpdated = true;
//			repaint(clipBounds);
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
		if (imageDisplay != null)
			lastDisplayChangeTimestamp = imageDisplay.getLastChangeTimestamp();
		ensureGammaUpdated();
		updateThumbnail();
		repaint();		
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
		//		painter.close();
		overlayOptionsManager.detachAll();
		overlayOptionsManager.clear();
		manager.detachAll();
		manager.clear();
		regionStore.removeTileListener(this);
//		// Set the server to null
//		setImageData(null);
		// Notify listeners
		for (QuPathViewerListener listener : listeners.toArray(new QuPathViewerListener[0]))
			listener.viewerClosed(this);
	}


	/*
	 * Lessons from trying VolatileImages:
	 * 	- Repainting using BufferedImages is (at least on OSX) frustratingly slow; depending on window size, ~25-30 ms *just* for copying
	 * 		imgBuffer to screen (ignoring time taken to draw to imgBuffer in the first place, and to draw everything else)
	 *  - The trouble is that imgBuffer loses isAccelerated() when it is drawn to; when it remains static, drawing is extremely fast as it is accelerated in the background
	 *  - Avoiding imgBuffer and using a VolatileImage leads to improved performance, about ~9ms to draw to the image, and then copying to display seems instantaneous. But...
	 *  - Can't access pixels of VolatileImage directly, therefore any image display transforms can kill performance horribly
	 *  
	 *  In short, current performance is worse than it needs to be due to the cost of blitting the BufferedImage when panning/zooming.
	 *  It can easily be improved by switching to using a VolatileImage, but then color transforms become unacceptably terrible.
	 *  It may be worthwhile to use a compromise solution of a VolatileImage so long as no color transforms are required.
	 *  
	 *  But for now this has not been implemented as the code is not stable enough to warrant introducing yet more complexity.
	 * 
	 */
	//	VolatileImage imgVolatile;

	
	protected void paintComponent(Graphics g) {
		paintViewer(g, getWidth(), getHeight());
	}

	
	void updateRepaintTimestamp() {
		long timestamp = System.currentTimeMillis();
		lastRepaintTimestamp.set(timestamp);
	}
	

	protected void paintViewer(Graphics g, int w, int h) {
		
		ImageServer<BufferedImage> server = getServer();
		if (server == null) {
			g.setColor(background);
			g.fillRect(0, 0, w, h);
			updateRepaintTimestamp();
			return;
		}

//		// Get dimensions
//		int w = getWidth();
//		int h = getHeight();

		Rectangle clip = g.getClipBounds();
		boolean clipFull;
		if (clip == null) {
			clip = new Rectangle(0, 0, w, h);
			g.setClip(0, 0, w, h);
			clipFull = true;
		} else
			clipFull = clip.x == 0 && clip.y == 0 && clip.width == w && clip.height == h;

		// Ensure we have a sufficiently-large buffer
		if (imgBuffer == null || imgBuffer.getWidth() != w || imgBuffer.getHeight() != h) {
			// Create buffered images & buffers for RGB pixel values
			imgBuffer = createBufferedImage(w, h);
			imgBuffer.setAccelerationPriority(1f);
			logger.trace("New buffered image created: {}", imgBuffer);
			//			imgVolatile = createVolatileImage(w, h);
			imageUpdated = true;
			// If the size changed, ensure the AffineTransform is up-to-date
			updateAffineTransform();
		}

		// Get the displayed region
		Shape shapeRegion = getDisplayedRegionShape();

		// The visible shape must have changed if there wasn't one previously...
		// Otherwise check if it has changed & update accordingly
		// This will be used to notify listeners soon
		boolean shapeChanged = lastVisibleShape == null || !lastVisibleShape.equals(shapeRegion);

		long t1 = System.currentTimeMillis();

		// Only repaint the image if this is requested, otherwise only overlays need to be repainted
		if (imageUpdated || locationUpdated) {// || imgVolatile.contentsLost()) {
			// Set flags that image no longer requiring an update
			// By setting them early, they might still be reset during this run... in which case we don't want to thwart the re-run
			imageUpdated = false;
			locationUpdated = false;

			//			updateBufferedImage(imgVolatile, shapeRegion, w, h);
			updateBufferedImage(imgBuffer, shapeRegion, w, h);
		}

		//		if (imageUpdated || locationUpdated) {
		//			updateBufferedImage(imgVolatile, shapeRegion, w, h);
		////			updateBufferedImage(imgBuffer, shapeRegion, w, h);
		////			logger.info("INITIAL Image drawing time: " + (System.currentTimeMillis() - t1));			
		//			imgVolatile.createGraphics().drawImage(imgBuffer, 0, 0, this);
		//		}


		//		while (imgVolatile.contentsLost()) {
		//			imgVolatile.createGraphics().drawImage(imgBuffer, 0, 0, this);
		//		}

		// Store the last shape visible
		lastVisibleShape = shapeRegion;

		// Draw the image from the buffer
		// The call to super.paintComponent is delayed until here to try to stop occasional flickering on Apple's Java 6
		g.setColor(background);
		// Somehow, painting the thumbnail helps for Java 8 on a MacBook Pro/iMac... I have no idea why... but it kills performance for Java 6
		//		if (imgThumbnailRGB != null)
		//			g2d.drawImage(imgThumbnailRGB, 0, 0, getWidth(), getHeight(), this);

		if (clipFull)
			paintFinalImage(g, imgBuffer, this);
		//			g2d.drawImage(imgBuffer, 0, 0, getWidth(), getHeight(), this);
		else
			g.drawImage(imgBuffer, clip.x, clip.y, clip.x+clip.width, clip.y+clip.height, clip.x, clip.y, clip.x+clip.width, clip.y+clip.height, null);

		if (logger.isTraceEnabled()) {
			long t2 = System.currentTimeMillis();
			logger.trace("Final image drawing time: {}", (t2 - t1));
		}

		// Really useful only for debugging graphics
		if (!(g instanceof Graphics2D)) {
			imageUpdated = false;
			// Notify any listeners of shape changes
			if (shapeChanged)
				fireVisibleRegionChangedEvent(lastVisibleShape);
			return;
		}
		
		double downsample = getDownsampleFactor();

		float opacity = overlayOptions.getOpacity();
		Graphics2D g2d = (Graphics2D)g.create();
		// Apply required transform to the graphics object (rotation, scaling, shifting...)
		g2d.transform(transform);
		Composite previousComposite = g2d.getComposite();
		boolean paintCompletely = thumbnailIsFullImage || !doFasterRepaint;
//		var regionBounds = AwtTools.getImageRegion(clip, getZPosition(), getTPosition());
		if (opacity > 0 || PathPrefs.alwaysPaintSelectedObjectsProperty().get()) {
			if (opacity < 1) {
				AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
				g2d.setComposite(composite);			
			}

			Color color = getSuggestedOverlayColor();
			// Paint the overlay layers
			var imageData = this.imageDataProperty.get();
			for (PathOverlay overlay : allOverlayLayers.toArray(PathOverlay[]::new)) {
				logger.trace("Painting overlay: {}", overlay);
				if (overlay instanceof AbstractOverlay)
					((AbstractOverlay)overlay).setPreferredOverlayColor(color);
//				overlay.paintOverlay(g2d, regionBounds, downsample, null, paintCompletely);
				overlay.paintOverlay(g2d, getServerBounds(), downsample, imageData, paintCompletely);
			}
//			if (hierarchyOverlay != null) {
//				hierarchyOverlay.setPreferredOverlayColor(color);
//				hierarchyOverlay.paintOverlay(g2d, getServerBounds(), downsampleFactor, null, paintCompletely);
//			}
		}
		
		// Paint the selected object
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
						g2d.setComposite(previousComposite);
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				}
				
				Rectangle boundsDisplayed = shapeRegion.getBounds();
				
				ROI pathROI = selectedObject.getROI();
//				if ((PathPrefs.getPaintSelectedBounds() || (selectedObject.isDetection() && !PathPrefs.getUseSelectedColor())) && !(pathROI instanceof RectangleROI)) {
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
					// Tried to match to pixel boundaries... but resulted in too much jiggling
//					boundsShape.setFrame(
//							Math.round(boundsShape.getX()/downsampleFactor)*downsampleFactor-downsampleFactor,
//							Math.round(boundsShape.getY()/downsampleFactor)*downsampleFactor-downsampleFactor,
//							Math.round(boundsShape.getWidth()/downsampleFactor)*downsampleFactor+2*downsampleFactor,
//							Math.round(boundsShape.getHeight()/downsampleFactor)*downsampleFactor+2*downsampleFactor);
					
//					boundsShape.setFrame(boundsShape.getX()-downsampleFactor, boundsShape.getY()-downsampleFactor, boundsShape.getWidth()+2*downsampleFactor, boundsShape.getHeight()+2*downsampleFactor);
					PathHierarchyPaintingHelper.paintShape(boundsShape, g2d, getSuggestedOverlayColor(), PathHierarchyPaintingHelper.getCachedStroke(Math.max(downsample, 1)*2), null);
//					boundsShape.setFrame(boundsShape.getX()+downsampleFactor, boundsShape.getY()-downsampleFactor, boundsShape.getWidth(), boundsShape.getHeight());
//					PathHierarchyPaintingHelper.paintShape(boundsShape, g2d, new Color(1f, 1f, 1f, 0.75f), PathHierarchyPaintingHelper.getCachedStroke(Math.max(downsampleFactor, 1)*2), null, downsampleFactor);
				}
				
				// Avoid double-painting of annotations (which looks odd if they are filled in)
				// However do always paint detections, since they are otherwise painted (unselected) 
				// in a cached way
				if ((selectedObject.isDetection() && PathPrefs.useSelectedColorProperty().get()) || !PathObjectTools.hierarchyContainsObject(hierarchy, selectedObject))
					PathHierarchyPaintingHelper.paintObject(selectedObject, false, g2d, boundsDisplayed, overlayOptions, getHierarchy().getSelectionModel(), downsample);
				// Paint ROI handles, if required
				if (selectedObject == mainSelectedObject && roiEditor.hasROI()) {
					Stroke strokeThick = PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.annotationStrokeThicknessProperty().get() * downsample);
					Color color = useSelectedColor ? ColorToolsAwt.getCachedColor(PathPrefs.colorSelectedObjectProperty().get()) : null;
					if (color == null)
						color = ColorToolsAwt.getCachedColor(ColorToolsFX.getDisplayedColorARGB(selectedObject));
					g2d.setStroke(strokeThick);
					// Draw ROI handles using adaptive size
					double maxHandleSize = getMaxROIHandleSize();
					double minHandleSize = downsample;
					PathHierarchyPaintingHelper.paintHandles(roiEditor, g2d, minHandleSize, maxHandleSize, color, ColorToolsAwt.getTranslucentColor(color));
				}
			}
		}

		// Notify any listeners of shape changes
		if (shapeChanged)
			fireVisibleRegionChangedEvent(lastVisibleShape);
		
		
		updateRepaintTimestamp();
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
	
	
	/**
	 * Create an RGB BufferedImage suitable for caching the image used for painting.
	 * @param w
	 * @param h
	 * @return
	 */
	static BufferedImage createBufferedImage(final int w, final int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
	}


	//	private void updateBufferedImage(final VolatileImage imgBuffer, final Shape shapeRegion, final int w, final int h) {
	//		Graphics2D gBuffered = imgBuffer.createGraphics();
	//		updateBufferedImage(gBuffered, shapeRegion, w, h);
	//		gBuffered.dispose();
	//	}

	private void updateBufferedImage(final BufferedImage imgBuffer, final Shape shapeRegion, final int w, final int h) {
		Graphics2D gBuffered = imgBuffer.createGraphics();
		updateBufferedImage(gBuffered, shapeRegion, w, h);
		gBuffered.dispose();
		// Apply color transforms, if required
		if (iccTransformOp != null) {
			iccTransformOp.filter(this.imgBuffer.getRaster(), this.imgBuffer.getRaster());
		}
		ensureGammaUpdated();
		if (gammaOp != null) {
			gammaOp.filter(this.imgBuffer.getRaster(), this.imgBuffer.getRaster());
		}
	}

	//	private void updateBufferedImage(final BufferedImage imgBuffer, final Shape shapeRegion) {
	private void updateBufferedImage(final Graphics2D gBuffered, final Shape shapeRegion, final int w, final int h) {
		// Check if we are doing a simple shift (scroll) - if so, we can reuse some previous painting
		// TODO: Verify that the 'scroll only' test is sufficiently reliable
		Shape shapeToUpdate = shapeRegion;
		// Set all image pixels to be the background color
		gBuffered.setColor(background);
		gBuffered.fillRect(0, 0, w, h);

		// Apply the transform so we don't need to worry about converting coordinates so much
		gBuffered.transform(transform);
		gBuffered.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Get the server width & height
		ImageServer<BufferedImage> server = getServer();
		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();

		// Check if we require tiling the image, or if the low-resolution version does all we need
		BufferedImage imgThumbnail = regionStore.getThumbnail(server, getZPosition(), getTPosition(), true);
		double lowResolutionDownsample = 0.5 * ((double)serverWidth / imgThumbnail.getWidth() + (double)serverHeight / imgThumbnail.getHeight());
		boolean requiresTiling = !thumbnailIsFullImage && lowResolutionDownsample > Math.max(downsampleFactor.get(), 1);

		// Check if we will be painting some background beyond the image edge
		Rectangle shapeBounds = shapeToUpdate.getBounds();
		boolean overBoundary = shapeBounds.x < 0 || shapeBounds.y < 0 || shapeBounds.x + shapeBounds.width >= serverWidth || shapeBounds.y + shapeBounds.height >= serverHeight;

		// Reset interpolation - this roughly halves repaint times
		if (!doFasterRepaint && PathPrefs.viewerInterpolateBilinearProperty().get())
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		if (requiresTiling) {

			// TODO: Consider removing faster repaint?
			double downsample = getDownsampleFactor();
//			if (doFasterRepaint)
//				downsample = downsampleFactor * 1.5;


			// Try to repaint higher resolution tiles for only the requested region
			// A small optimization (that can make a difference in repaint speed...) is that for an RGB image we don't need to transform
			// the image as we go along (tile by tile), but we can apply a single transform afterwards.
			// *However* this shouldn't be applied if the region we are viewing extends beyond the image boundary, as it means we would be color-transforming the background color.
			// For a non-RGB image, or if the viewed region is over the image boundary, the transform should be applied in advance to the thumbnail, and then tile-by-tile during painting.
			if (server.isRGB() && !overBoundary) {
				regionStore.paintRegion(server, gBuffered, shapeToUpdate, getZPosition(), getTPosition(), downsample, imgThumbnail, null, null);
				gBuffered.dispose();
				if (imageDisplay != null)
//					imgBuffer = imageDisplay.applyTransforms(imgBuffer, imgBuffer);
//					 More benchmarking required... but reusing imgBuffer was killing performance for RGB transform on Java 8 (JavaFX)... possibly
					imgBuffer = getRenderer().applyTransforms(imgBuffer, null);
			} else {
				regionStore.paintRegion(server, gBuffered, shapeToUpdate, getZPosition(), getTPosition(), downsample, imgThumbnail, null, getRenderer());
			}
		} else {
			// Just paint the 'thumbnail' version, which has already (potentially) been color-transformed
			paintThumbnail(gBuffered, imgThumbnailRGB, serverWidth, serverHeight, this);
		}
	}


	/**
	 * Get an unmodifiable list containing the overlay layers, in order.
	 * @return
	 */
	public List<PathOverlay> getOverlayLayers() {
		return FXCollections.unmodifiableObservableList(allOverlayLayers);
	}
	
	/**
	 * Get direct access to the custom overlay list.
	 * @return
	 */
	public ObservableList<PathOverlay> getCustomOverlayLayers() {
		return customOverlayLayers;
	}



	static void paintThumbnail(Graphics g, Image img, int width, int height, QuPathViewer viewer) {
		g.drawImage(img, 0, 0, width, height, null);
	}

	
	/**
	 * Create a <code>LookupOp</code> that applies a gamma transform to an 8-bit image.
	 * 
	 * @param gamma
	 * @return
	 */
	static LookupOp createGammaOp(double gamma) {
		byte[] lut = new byte[256];
		for (int i = 0; i < 256; i++) {
			double val = Math.pow(i/255.0, gamma) * 255;
			lut[i] = (byte)ColorTools.do8BitRangeCheck(val);
		}
		return new LookupOp(new ByteLookupTable(0, lut), null);
	}

	/**
	 * Attempt to read an ICC profile from a TIFF image or stream.
	 * This depends on ImageIO; in general, it should work with Java 9 
	 * (if an ICC profile is included in the TIFF) but not earlier versions.
	 * 
	 * @param input an input of the kind that <code>ImageIO.createImageInputStream</code> can handle (e.g. a <code>File</code>)
	 * @return an ICC profile if one is found, otherwise null.
	 */
	static ICC_Profile readICC(Object input) {
		try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (readers == null) {
				logger.debug("No readers found to extract ICC profile from {}", input);
				return null;
			}
			Class<?> clsTiffDir = Class.forName("javax.imageio.plugins.tiff.TIFFDirectory");
			Class<?> clsTiffField = Class.forName("javax.imageio.plugins.tiff.TIFFField");
			Method mCreateFromMetadata = clsTiffDir.getMethod("createFromMetadata", IIOMetadata.class);
			Method mGetTiffField = clsTiffDir.getMethod("getTIFFField", int.class);
			Method mGetAsBytes = clsTiffField.getMethod("getAsBytes");
			while (readers.hasNext()) {
				ImageReader reader = readers.next();
				stream.reset();
				reader.setInput(stream);
				Object tiffDir = mCreateFromMetadata.invoke(null, reader.getImageMetadata(0));
				Object tiffField = mGetTiffField.invoke(tiffDir, 34675);
				byte[] bytes = (byte[])mGetAsBytes.invoke(tiffField);
				return ICC_Profile.getInstance(bytes);
			}
		} catch (Exception e) {
			logger.warn("Unable to read ICC profile: {}", e.getLocalizedMessage());
		}
		return null;
	}

	/**
	 * Try to create a <code>ColorConvertOp</code> that can be applied to transform using the color space of 
	 * the source image (read from TIFF tags, if possible) to sRGB.
	 * 
	 * @return the <code>ColorConvertOp</code> if an appropriate conversion could be found, or <code>null</code> otherwise.
	 */
	ColorConvertOp createICCConvertOp() {
		ICC_Profile iccSource = readICC(new File(getServerPath()));
		if (iccSource == null)
			return null;
		return new ColorConvertOp(new ICC_Profile[]{
				iccSource,
				ICC_Profile.getInstance(ColorSpace.CS_sRGB)}, null);
	}
	
	
	private double gamma = 1.0;
	private LookupOp gammaOp = null;
	
	private ColorConvertOp iccTransformOp = null;
	private boolean doICCTransform = false;
	
	void setGamma(final double gamma) {
		if (this.gamma == gamma)
			return;
		if (gamma == 1 || gamma <= 0 || !Double.isFinite(gamma))
			gammaOp = null;
		else
			gammaOp = createGammaOp(gamma);
		this.gamma = gamma;
	}
	
	void ensureGammaUpdated() {
		var gammaProperty = PathPrefs.viewerGammaProperty().get();
		if (gamma != gammaProperty) {
			setGamma(gammaProperty);
			imageUpdated = true;
		}
	}
	
	void updateICCTransform() {
		if (getServerPath() != null && getDoICCTransform())
			iccTransformOp = createICCConvertOp();
		else
			iccTransformOp = null;
	}
	
	void setDoICCTransform(final boolean doTransform) {
		this.doICCTransform = doTransform;
		updateICCTransform();
	}

	boolean getDoICCTransform() {
		return doICCTransform;
	}
	
	static void paintFinalImage(Graphics g, Image img, QuPathViewer viewer) {
		g.drawImage(img, 0, 0, null);
	}
	
	/**
	 * Get the {@link RoiEditor} used by this viewer.
	 * @return
	 */
	public RoiEditor getROIEditor() {
		return roiEditor;
	}


	private void updateTooltip(final Tooltip tooltip) {
		String text = getTooltipText(mouseX, mouseY);
		tooltip.setText(text);
		if (text == null)
			tooltip.setOpacity(0);
		else
			tooltip.setOpacity(1);
	}

	private String getTooltipText(final double x, final double y) {
		// Try to show which TMA core is selected - if we have a TMA image
		PathObjectHierarchy hierarchy = getHierarchy();
		TMAGrid tmaGrid = hierarchy == null ? null : hierarchy.getTMAGrid();
		if (tmaGrid != null) {
			Point2D p = componentPointToImagePoint(x, y, null, false);
			TMACoreObject core = PathObjectTools.getTMACoreForPixel(tmaGrid, p.getX(), p.getY());
			if (core != null) {
				if (core.isMissing())
					return String.format("TMA Core %s\n(missing)", core.getName());
				else
					return String.format("TMA Core %s", core.getName());
			}
		}
		return null;
	}

	/**
	 * Get the {@link ImageDisplay} object used to determine how the image is converted to RGB for display.
	 * @return
	 */
	public ImageDisplay getImageDisplay() {
		return imageDisplay;
	}
	
	protected boolean componentContains(double x, double y) {
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
	 * Get a thumbnail representing the image as displayed by this viewer.
	 * 
	 * @return
	 */
	public BufferedImage getThumbnail() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? null : regionStore.getThumbnail(server, getZPosition(), getTPosition(), true);
	}

	/**
	 * Get thumbnails for all z-slices &amp; time points
	 * @return
	 */
	public List<BufferedImage> getAllThumbnails() {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return Collections.emptyList();
		int nImages = server.nTimepoints() * server.nZSlices();
		if (nImages == 1)
			return Collections.singletonList(regionStore.getThumbnail(server, 0, 0, true));
		List<BufferedImage> thumbnails = new ArrayList<BufferedImage>(nImages);
		for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				thumbnails.add(regionStore.getThumbnail(server, getZPosition(), getTPosition(), true));
			}
		}
		return thumbnails;
	}



	/**
	 * Get a thumbnail representing the image as displayed by this viewer.
	 * 
	 * Note: This will be a color (aRGB) image, with any color transforms applied -
	 * therefore should not be used to extract 'original' pixel values
	 * @return
	 */
	public BufferedImage getRGBThumbnail() {
		return imgThumbnailRGB;
	}

	/**
	 * Set downsample factor, so that the specified coordinate in the image space is not shifted in the viewer afterwards.
	 * The purpose is to make it possible to zoom in/out while keeping the cursor focussed on a particular location.
	 * 
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
		
		// Don't allow setting if we have 'zoom to fit'
		if (getZoomToFit())
			return;
		
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

		this.downsampleFactor.set(downsampleFactor);
		updateAffineTransform();

		imageUpdated = true;
		repaint();
	}

	protected double getZoomToFitDownsampleFactor() {
		if (!hasServer())
			return Double.NaN;
		double maxDownsample = (double)getServerWidth() / getWidth();
		maxDownsample = Math.max(maxDownsample, (double)getServerHeight() / getHeight());
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


//	public Rectangle getServerBounds() {
//		ImageServer server = getServer();
//		return server == null ? null : new Rectangle(0, 0, server.getWidth(), server.getHeight());
//	}
	
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
//		pointDest.setLocation(Math.floor(pointDest.getX()), Math.floor(pointDest.getY()));
//		pointDest.setLocation(Math.round(pointDest.getX()), Math.round(pointDest.getY()));
		// Constrain, if necessary
		ImageServer<BufferedImage> server = getServer();
		if (constrainToBounds && server != null) {
			pointDest.setLocation(
					Math.min(Math.max(pointDest.getX(), 0), server.getWidth()),
					Math.min(Math.max(pointDest.getY(), 0), server.getHeight())
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
					Math.min(Math.max(pointDest.getX(), 0), getWidth()),
					Math.min(Math.max(pointDest.getY(), 0), getHeight())
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
		var pathObjects = PathObjectTools.getObjectsForLocation(hierarchy,
				p2.getX(), p2.getY(),
				getZPosition(),
				getTPosition(),
				0);
		if (!pathObjects.isEmpty()) {
			return pathObjects.stream()
					.filter(pathObject -> pathObject.isDetection())
					.map(pathObject -> {
				var pathClass = pathObject.getPathClass();
				return pathClass == null ? "Unclassified" : pathClass.toString();
			}).collect(Collectors.joining(", "));
		}
		return "";
	}
	
	/**
	 * Get a string representing the image coordinates for a particular x &amp; y location in the viewer component.
	 * 
	 * @param x x-coordinate in the component space (not image space)
	 * @param y y-coordinate in the component space (not image space)
	 * @param useCalibratedUnits 
	 * @return a String to display representing the cursor location
	 */
	public String getLocationString(double x, double y, boolean useCalibratedUnits) {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return "";
		String units;
		Point2D p = componentPointToImagePoint(x, y, null, false);
		//		double xx = (int)(p.getX() + .5);
		//		double yy = (int)(p.getY() + .5);
		double xx = p.getX();
		double yy = p.getY();

		if (xx < 0 || yy < 0 || xx > server.getWidth()-1 || yy > server.getHeight()-1)
			return "";

		double xDisplay = xx;
		double yDisplay = yy;
		PixelCalibration cal = server.getPixelCalibration();
		if (useCalibratedUnits && cal.hasPixelSizeMicrons()) {
			units = GeneralTools.micrometerSymbol();
			xDisplay *= cal.getPixelWidthMicrons();
			yDisplay *= cal.getPixelHeightMicrons();
		} else {
			units = "px";
		}
		
		// See if we're on top of a TMA core
		String prefix = "";
		TMAGrid tmaGrid = getHierarchy().getTMAGrid();
		if (tmaGrid != null) {
			TMACoreObject core = PathObjectTools.getTMACoreForPixel(tmaGrid, xx, yy);
			if (core != null && core.getName() != null)
				prefix = "Core: " + core.getName() + "\n";
		}

		String s = null;
		RegionRequest request = ImageRegionStoreHelpers.getTileRequest(server, xx, yy, downsampleFactor.get(), getZPosition(), getTPosition());
		if (request != null) {
			BufferedImage img = regionStore.getCachedTile(server, request);
			int xi = 0, yi = 0;
			if (img == null) {
				// Try getting a value from the thumbnail for the whole image
				BufferedImage imgThumbnail = regionStore.getCachedThumbnail(server, getZPosition(), getTPosition());
				if (imgThumbnail != null) {
					img = imgThumbnail;
					double downsample = (double)server.getWidth() / imgThumbnail.getWidth();
					xi = (int)(xx / downsample + .5);
					yi = (int)(yy / downsample + .5);
				}
			} else {
				xi = (int)((xx - request.getX())/request.getDownsample());
				yi = (int)((yy - request.getY())/request.getDownsample());
			}
			if (img != null) {
				// Make sure we are within range
				xi = Math.min(xi, img.getWidth()-1);
				yi = Math.min(yi, img.getHeight()-1);
				// Get the value, having applied any required color transforms
				if (imageDisplay != null)
					s = imageDisplay.getTransformedValueAsString(img, xi, yi);
			}
		}
		
		// Append z, t position if required
		String zString = null;
		if (server.nZSlices() > 1) {
			double zSpacing = server.getPixelCalibration().getZSpacingMicrons();
			if (!useCalibratedUnits || Double.isNaN(zSpacing))
				zString = "z = " + getZPosition();
			else
				zString = String.format("z = %.2f %s", getZPosition()*zSpacing, GeneralTools.micrometerSymbol());
		}
		String tString = null;
		if (server.nTimepoints() > 1) {
			// TODO: Consider use of TimeUnit
//			TimeUnit timeUnit = server.getTimeUnit();
//			if (!useMicrons || timeUnit == null)
				tString = "t = " + getTPosition();
//			else
//				tString = String.format("z = %.2f %s", getTPosition(), timeUnit.toString());
		}

		String dimensionString;
		if (tString == null && zString == null)
			dimensionString = "";
		else {
			dimensionString = "\n";
			if (zString != null) {
				dimensionString += zString;
				if (tString != null)
					dimensionString += ", " + tString;
			} else
				dimensionString += tString;
		}

		if (s != null)
			return String.format("%s%.2f, %.2f %s\n%s%s", prefix, xDisplay, yDisplay, units, s, dimensionString);
		else
			return String.format("%s%.2f, %.2f %s%s", prefix, xDisplay, yDisplay, units, dimensionString);
		
		
//		if (s != null)
//			return String.format("<html><center>%.2f, %.2f %s<br>%s", xDisplay, yDisplay, units, s);
//		else
//			return String.format("<html><center>%.2f, %.2f %s", xDisplay, yDisplay, units);
	}

	/**
	 * Get a string to summarize the pixel found below the most recent known mouse location, 
	 * or "" if the mouse is outside this viewer.
	 * 
	 * @param useCalibratedUnits If true, microns will be used rather than pixels (if known).
	 * @return
	 */
	protected String getFullLocationString(boolean useCalibratedUnits) {
		if (componentContains(mouseX, mouseY)) {
			String classString = getObjectClassificationString(mouseX, mouseY).trim();
			String locationString = getLocationString(mouseX, mouseY, useCalibratedUnits);
			if (locationString == null || locationString.isBlank())
				return "";
			if (classString != null && !classString.isBlank())
				classString = classString + "\n";
			return classString + locationString;
		} else
			return "";
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
		
//		double dx = xCenter - x;
//		double dy = yCenter - y;
//		if (dx*dx + dy*dy > 1000) {
//			System.err.println("Moving a lot");
//		}

		this.xCenter = x;
		this.yCenter = y;
		updateAffineTransform();

		// Flag that the location has been updated
		locationUpdated = true;
		repaint();
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
	}
	
//	
//	public AffineTransform getTransform() {
//		return new AffineTransform(transform);
//	}
//
//	public AffineTransform getInverseTransform() {
//		return new AffineTransform(transformInverse);
//	}


	/**
	 * Set the rotation; angle in radians.
	 * 
	 * @param theta
	 */
	public void setRotation(double theta) {
		if (rotationProperty.get() == theta)
			return;
		while (theta < MIN_ROTATION)
			theta += MAX_ROTATION;
		theta = (theta % MAX_ROTATION) + MIN_ROTATION;

		rotationProperty.set(theta);
	}

	/**
	 * Returns true if {@code viewer.getRotation() != 0}.
	 * @return isRotated
	 */
	public boolean isRotated() {
		return getRotation() != 0;
	}

	/**
	 * Get the current rotation; angle in radians.
	 * @return rotation in radians
	 */
	public double getRotation() {
		return rotationProperty.get();
	}
	
	/**
	 * Return the rotation property of this viewer.
	 * @return rotation property
	 */
	public DoubleProperty rotationProperty() {
		return rotationProperty;
	}

	@Override
	public void tileAvailable(String serverPath, ImageRegion region, BufferedImage tile) {
		//		if (serverPath == null || serverPath.equals(getServerPath()))
//		System.out.println(region);
		
		if (!hasServer())
			return;
		
		// Check contains rather than equals to all for derived servers (e.g. for painting hierarchies)
		if (serverPath == null || serverPath.contains(getServerPath()))
			repaintImageRegion(AwtTools.getBounds(region), true);//!serverPath.startsWith(PathHierarchyImageServer.DEFAULT_PREFIX));
		
		//		imageUpdated = true;
		//		repaint();
	}


	/**
	 * Force the overlay displaying detections and annotations to be repainted.  Any cached versions will be thrown away, so this is useful when
	 * some aspect of the display has changed, e.g. objects colors or fill/outline status.  Due to the usefulness of caching for performance, it should
	 * not be called too often.
	 */
	public void forceOverlayUpdate() {
		if (Platform.isFxApplicationThread())
			hierarchyOverlay.clearCachedOverlay();
		else
			Platform.runLater(() -> hierarchyOverlay.clearCachedOverlay());
		repaint();
	}



	@Override
	public void hierarchyChanged(final PathObjectHierarchyEvent event) {
		// Measurement changes don't modify the hierarchy
		if (event.isObjectMeasurementEvent())
			return;

		if (Platform.isFxApplicationThread())
			handleHierarchyChange(event);
		else
			Platform.runLater(() -> handleHierarchyChange(event));
	}


	private void handleHierarchyChange(final PathObjectHierarchyEvent event) {
		if (event != null)
			logger.trace(event.toString());
		
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> handleHierarchyChange(event));
			return;
		}
		
		// Clear any cached regions of the overlay, if necessary
		// TODO: Make this update a bit less conservative - it isn't really needed if we don't modify detections?
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
//		hierarchyOverlay.clearCachedOverlay();

		// Just in case, make sure the handles are updated in any ROIEditor
		if (event != null && !event.isChanging())
			updateRoiEditor();
		// Request repaint
		repaint();
	}



	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {

//		// We only want to shift the object ROI to the center under certain conditions, otherwise the screen jerks annoyingly
//		if (!settingSelectedObject && pathObjectSelected != previousObject && !getZoomToFit() && pathObjectSelected != null && RoiTools.isShapeROI(pathObjectSelected.getROI())) {
//
//			// We want to center a TMA core if more than half of it is outside the window
//			boolean centerCore = false;
//			Shape shapeDisplayed = getDisplayedRegionShape();
//			ROI pathROI = pathObjectSelected.getROI();
//			if (centerCore || !shapeDisplayed.intersects(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight())) {
//				//			if (!getDisplayedRegionShape().intersects(pathObjectSelected.getROI().getBounds2D())) {
//				//			(!(pathObjectSelected instanceof PathDetectionObject && getDisplayedRegionShape().intersects(pathObjectSelected.getROI().getBounds2D())))) {
//				double cx = pathObjectSelected.getROI().getCentroidX();
//				double cy = pathObjectSelected.getROI().getCentroidY();
//				setCenterPixelLocation(cx, cy);
//				//		logger.info("Centered to " + cx + ", " + cy);
//			}
//		}
		updateRoiEditor();
		for (QuPathViewerListener listener : new ArrayList<QuPathViewerListener>(listeners)) {
			listener.selectedObjectChanged(this, pathObjectSelected);
		}

		if (Platform.isFxApplicationThread())
			hierarchyOverlay.resetBuffer();
		else
			Platform.runLater(() -> hierarchyOverlay.resetBuffer());
		logger.trace("Selected path object changed from {} to {}", previousObject, pathObjectSelected);

		repaint();
//		repaintEntireImage();
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

	@Override
	public synchronized boolean requiresTileRegion(final String serverPath, final ImageRegion region) {
		//		String path = getServerPath();
		//		return path.equals(serverPath) && (getDisplayedClipShape(null).intersects(region));
		//		if (region instanceof RegionRequest && getDownsampleFactor() < ((RegionRequest)region).getDownsample())
		//			return false;
		if (serverPath.startsWith(PathHierarchyImageServer.DEFAULT_PREFIX) || serverPath.equals(getServerPath())) {
			return Math.abs(region.getZ() - getZPosition()) <= 3 && region.getT() == getTPosition() && getDisplayedClipShape(null).intersects(AwtTools.getBounds(region));
		}
		return false;
	}
	
	
	@Override
	public String toString() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		if (temp != null)
			return getClass().getSimpleName() + " - " + temp.getServerPath();
		return getClass().getSimpleName() + " - no server";
	}


	/**
	 * Current z-position for the z-slice currently visible in the viewer.
	 * @return
	 */
	public IntegerProperty zPositionProperty() {
		return zPosition;
	}
	
	/**
	 * Current t-position for the timepoint currently visible in the viewer.
	 * @return
	 */
	public IntegerProperty tPositionProperty() {
		return tPosition;
	}
	
	
	
	
	
	/**
	 * Watch for spacebar pressing as an event filter, because we don't wanna miss a thing.
	 */
	class KeyEventFilter implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			KeyCode code = event.getCode();

			// Handle spacebar pressed (log state for later)
			if (code == KeyCode.SPACE) {
				if (event.getEventType() == KeyEvent.KEY_PRESSED)
					setSpaceDown(true);
				else if (event.getEventType() == KeyEvent.KEY_RELEASED)
					setSpaceDown(false);
				return;
			}
		}
		
	}
	
	
	/**
	 * Handle key press events that control viewer directly.
	 */
	class KeyEventHandler implements EventHandler<KeyEvent> {

		private KeyCode lastPressed = null;
		private Set<KeyCode> keysPressed = new HashSet<>();
		private long keyDownTime = Long.MIN_VALUE;
		private double scale = 1.0;

		@Override
		public void handle(KeyEvent event) {
			KeyCode code = event.getCode();
			
			// Handle backspace/delete to remove selected object
			if (event.getEventType() == KeyEvent.KEY_RELEASED && (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE)) {
				if (getROIEditor().hasActiveHandle() || getROIEditor().isTranslating()) {
					logger.debug("Cannot delete object - ROI being edited");
					return;
				}
				var hierarchy = getHierarchy();
				if (hierarchy != null) {
					if (hierarchy.getSelectionModel().singleSelection()) {
						GuiTools.promptToRemoveSelectedObject(hierarchy.getSelectionModel().getSelectedObject(), hierarchy);
					} else {
						GuiTools.promptToClearAllSelectedObjects(getImageData());
					}
//					setSelectedObject(null);
				}
				event.consume();
				return;
			}

			PathObjectHierarchy hierarchy = getHierarchy();
			if (hierarchy == null)
				return;

//			// Center selected object if Enter pressed ('center on enter')
//			if (event.getEventType() == KeyEvent.KEY_RELEASED && code == KeyCode.ENTER) {
//				PathObject selectedObject = getSelectedObject();
//				if (selectedObject != null && selectedObject.hasROI())
//					setCenterPixelLocation(selectedObject.getROI().getCentroidX(), selectedObject.getROI().getCentroidY());
//				event.consume();
//				return;
//			}


			if (!(code == KeyCode.LEFT || code == KeyCode.UP || code == KeyCode.RIGHT || code == KeyCode.DOWN))
				return;

			// Use arrow keys to navigate, either or directly or using a TMA grid
			boolean skipMissingTMACores = PathPrefs.getSkipMissingCoresProperty();
			TMAGrid tmaGrid = hierarchy.getTMAGrid();
			List<TMACoreObject> cores = tmaGrid == null ? Collections.emptyList() : new ArrayList<>(tmaGrid.getTMACoreList());
			if (!event.isShiftDown() && tmaGrid != null && tmaGrid.nCores() > 0) {
				if (event.getEventType() != KeyEvent.KEY_PRESSED)
					return;
				PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
				// Look up the hierarchy for a TMA core
				while (selected != null && !selected.isTMACore()) {
					selected = selected.getParent();
				}
				int ind = tmaGrid.getTMACoreList().indexOf(selected);
				int w = tmaGrid.getGridWidth();
				int h = tmaGrid.getGridHeight();
				if (ind < 0) {
					// Find the closest TMA core to the current position
					double minDisplacementSq = Double.POSITIVE_INFINITY;
					int i = -1;
					for (TMACoreObject core : cores) {
						i++;
						if (core.isMissing() && skipMissingTMACores)
							continue;
						
						ROI coreROI = core.getROI();
						double dx = coreROI.getCentroidX() - getCenterPixelX();
						double dy = coreROI.getCentroidY() - getCenterPixelY();
						double displacementSq = dx*dx + dy*dy;
						if (displacementSq < minDisplacementSq) {
							ind = i;
							minDisplacementSq = displacementSq;
						}
						
					}
				}

				int temp;
				switch (code) {
				case LEFT:
					temp = ind-1 < 0 ? 0 : ind-1;
					while (skipMissingTMACores && cores.get(temp).isMissing() && temp > 0)
						temp = temp-1 < 0 ? 0 : temp-1;
					break;
				case UP:
					temp = ind == 0 ? ind : ind-w < 0 ? (w*h)-(w-ind+1) : ind-w;
					while (skipMissingTMACores && cores.get(temp).isMissing() && temp != 0) 
						temp = ind == 0 ? ind : temp-w <= 0 ? (w*h)-(w-temp+1) : temp-w;
					break;
				case RIGHT:
					temp = ind+1 >= w*h ? (w*h)-1 : ind+1;
					while (skipMissingTMACores && cores.get(temp).isMissing() && temp < (w*h)-1)
						temp = temp+1 >= w*h ? (w*h)-1 : temp+1;
					break;
				case DOWN:
					temp = ind == (w*h)-1 ? ind : ind+w >= (w*h) ? ind%w + 1 : ind+w;
					while (skipMissingTMACores && cores.get(temp).isMissing() && temp != (w*h)-1) 
						temp = temp+w >= (w*h) ? temp%w + 1 : temp+w;
					break;
				default:
					return;
				}
				ind = !skipMissingTMACores ? temp : cores.get(temp).isMissing() ? ind : temp;
				// Set the selected object & center the viewer
				if (ind >= 0 && ind < w*h) {
					PathObject selectedObject = cores.get(ind);
					hierarchy.getSelectionModel().setSelectedObject(selectedObject);
					if (selectedObject != null && selectedObject.hasROI())
						centerROI(selectedObject.getROI());
				}
				
				event.consume();

				
			} else if (event.getEventType() == KeyEvent.KEY_PRESSED) {
				
				if (keysPressed.isEmpty()) {
					keysPressed.add(code);
					lastPressed = code;
				} else if (!keysPressed.contains(code)) {
					keysPressed.add(code);
					if (keysPressed.size() == 3)
						keysPressed.remove(lastPressed);
				}

				if (event.isShiftDown()) {
					switch (code) {
					case UP:
						zoomIn(10);
						event.consume();
						return;
					case DOWN:
						zoomOut(10);
						event.consume();
						return;
					default:
						break;
					}
				}


				long currentTime = System.currentTimeMillis();
				if (keyDownTime == Long.MIN_VALUE)
					keyDownTime = currentTime;
				// Take care of acceleration
				//				double dt = 0.1*currentTime - 0.1*keyDownTime;
				//				double scale = 5 * Math.pow(20 + dt, 0.5);

				// Apply acceleration effects if required
				if (PathPrefs.getNavigationAccelerationProperty())
					scale = scale * 1.05;
				
				double d = getDownsampleFactor() * scale * 20 * PathPrefs.getScaledNavigationSpeed();
				double dx = 0;
				double dy = 0;
				int nZSlices = hasServer() ? getServer().nZSlices() : 1;
				int nTimepoints = hasServer() ? getServer().nTimepoints() : 1;
				switch (code) {
				case LEFT:
					if (nTimepoints > 1) {
						setTPosition(Math.max(nTimepoints-1, 0));
						event.consume();
						return;
					}
					dx = d;
					if (lastPressed != code) {
						if (lastPressed == KeyCode.RIGHT)
							dx = 0;
						else
							dy = lastPressed == KeyCode.UP ? d : -d;
					}
					break;
				case UP:
					if (nZSlices > 1) {
						setZPosition(Math.max(0, getZPosition() - 1));	
						event.consume();
						return;
					}
					dy = d;
					if (lastPressed != code) {
						if (lastPressed == KeyCode.DOWN)
							dy = 0;
						else
							dx = lastPressed == KeyCode.LEFT ? d : -d;
					}
					break;
				case RIGHT:
					if (nTimepoints > 1) {
						setTPosition(Math.min(nTimepoints-1, getTPosition() + 1));						
						event.consume();
						return;
					}
					dx = -d;
					if (lastPressed != code) {
						if (lastPressed == KeyCode.LEFT)
							dx = 0;
						else
							dy = lastPressed == KeyCode.UP ? d : -d;							
					}
					break;
				case DOWN:
					if (nZSlices > 1) {
						setZPosition(Math.min(nZSlices-1, getZPosition() + 1));						
						event.consume();
						return;
					}
					dy = -d;
					if (lastPressed != code) {
						if (lastPressed == KeyCode.UP)
							dy = 0;
						else
							dx = lastPressed == KeyCode.LEFT ? d : -d;
					}
					break;
				default:
					return;
				}

				requestStartMoving(dx, dy);
				event.consume();


			} else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
				keysPressed.remove(code);
				if (lastPressed == code) {
					if (keysPressed.size() == 1)
						lastPressed = keysPressed.iterator().next();
					else
						lastPressed = null;
				}
				
				if (keysPressed.size() == 1)
					requestCancelDirection(code == KeyCode.LEFT || code == KeyCode.RIGHT);
				
				switch (code) {
				case LEFT:
				case UP:
				case RIGHT:
				case DOWN:
					if (lastPressed == null) {
						if (!PathPrefs.getNavigationAccelerationProperty())
							mover.stopMoving();
						else 
							mover.decelerate();
						setDoFasterRepaint(false);
						keyDownTime = Long.MIN_VALUE;
						scale = 1;
					}
					event.consume();
					break;
				default:
					return;
				}	
			}
		}
	}
	
	
	private MoveTool.ViewerMover mover = new MoveTool.ViewerMover(this);
	
	
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
	 * 
	 * <p>This can be used in combination with {@code requestDecelerate} to end a panning event more smoothly.
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
	 * Requests that the viewer cancels either the x- or y-axis direction.
	 * @param xAxis 
	 */
	public void requestCancelDirection(final boolean xAxis) {
		mover.cancelDirection(xAxis);
	}

}