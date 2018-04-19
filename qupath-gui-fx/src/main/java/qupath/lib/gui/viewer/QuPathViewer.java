/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.viewer;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
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
import qupath.lib.awt.color.ColorToolsAwt;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI.Modes;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.overlays.GridOverlay;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.ImageDataOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.overlays.TMAGridOverlay;
import qupath.lib.gui.viewer.tools.MoveTool;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PathHierarchyImageServer;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.images.stores.ImageRegionStoreHelpers;
import qupath.lib.images.stores.TileListener;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.helpers.PathObjectColorToolsAwt;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.PathShape;


/**
 * JavaFX component for viewing a (possibly large) image, along with overlays.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathViewer implements TileListener<BufferedImage>, PathObjectHierarchyListener, PathObjectSelectionListener {

	static Logger logger = LoggerFactory.getLogger(QuPathViewer.class);

	private Vector<QuPathViewerListener> listeners = new Vector<>();

	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

	private DefaultImageRegionStore regionStore;

	// Overlay (ROI/object) display variables
	private OverlayOptions overlayOptions;

	// A PathImageServer used to wrap a PathObjectHierarchy, for faster painting when there are a lot of objects
	private HierarchyOverlay hierarchyOverlay = null;
	// An overlay to show a TMA grid
	private TMAGridOverlay tmaGridOverlay;
	// An overlay to show a regular grid (e.g. for counting)
	private GridOverlay gridOverlay;
	
	// Overlay layers that can be edited
	private ObservableList<PathOverlay> customOverlayLayers = FXCollections.observableArrayList();
	
	// Core overlay layers - these are always retained, and painted on top of any custom layers
	private ObservableList<PathOverlay> coreOverlayLayers = FXCollections.observableArrayList();
	
	// List that concatenates the custom & core overlay layers in painting order
	private ObservableList<PathOverlay> allOverlayLayers = FXCollections.observableArrayList();

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
	private double downsampleFactor = 1.0;
	private double rotation = 0;
	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
	// Affine transform used to apply rotation
	private AffineTransform transform = new AffineTransform();
	private AffineTransform transformInverse = new AffineTransform();

	// Flag to indicate that repainting should occur faster if possible (less detail required)
	// This can be useful when rapidly changing view, for example
	private boolean doFasterRepaint = false;

	// Keep a record of when the spacebar is pressed, to help with dragging to pan
	private boolean spaceDown = false;

	protected boolean autoRecolorGridAndScalebar = true;

	protected Color colorOverlaySuggested = null;
	
	// Requested cursor - but this may be overridden temporarily
	private Cursor requestedCursor = Cursor.DEFAULT;

	// Flag to know when a selected object event is arising from thsi viewer
	private boolean settingSelectedObject = false;

	// The shape (coordinates in the image domain) last painted
	// Used to determine whether the visible part of the image has been changed
	private Shape lastVisibleShape = null;

	private boolean showMainOverlay = true;

	private RoiEditor roiEditor = RoiEditor.createInstance();

	private Modes mode = Modes.MOVE;
	private ImageDisplay imageDisplay;

	private Color background = Color.BLACK;

	transient private long lastRepaintTimestamp = 0; // Used for debugging repaint times
	
	private boolean repaintRequested = false;
	
	private double mouseX, mouseY;
	
	private StackPane pane;
	private Canvas canvas;
	private BufferedImage imgCache;
	private WritableImage imgCacheFX;
	
	private double borderLineWidth = 5;
	private javafx.scene.paint.Color borderColor;
	
	public Pane getView() {
		if (canvas == null) {
			setupCanvas();
		}
		return pane;
	}
	
	public Canvas getCanvas() {
		return canvas;
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
				canvas = null;
			}
			
		});
		
		canvas.widthProperty().addListener((e, f, g) -> {
			if (getZoomToFit()) {
				centerImage();
				// Make sure the downsample factor is being continually updated
				downsampleFactor = getZoomToFitDownsampleFactor();
			} else {
				updateAffineTransform();
				repaint();
			}
		});
		canvas.heightProperty().addListener((e, f, g) -> {
			if (getZoomToFit()) {
				centerImage();
				// Make sure the downsample factor is being continually updated
				downsampleFactor = getZoomToFitDownsampleFactor();
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
	private void refreshAllOverlayLayers() {
		List<PathOverlay> temp = new ArrayList<>();
		temp.addAll(customOverlayLayers);
		temp.addAll(coreOverlayLayers);
		allOverlayLayers.setAll(temp);
	}
	
	
	long lastPaint = 0;
	private long minimumRepaintSpacingMillis = -1; // This can be used (temporarily) to prevent repaints happening too frequently
	
	/**
	 * Prevent frequent repaints (temporarily) by setting a minimum time that must have elapsed
	 * after the previous repaint for a new one to be triggered.
	 * (Repaint requests that come in between are simply disregarded for performance.)
	 * 
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
	 * (Note: calling this command triggers a repaint itself.)
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
	
	
	public void setBorderColor(final javafx.scene.paint.Color color) {
		this.borderColor = color;
		if (Platform.isFxApplicationThread()) {
			repaintRequested = true;
			paintCanvas();
		} else
			repaint();
	}

	public javafx.scene.paint.Color getBorderColor() {
		return borderColor;
	}
	
	public int getWidth() {
		return (int)Math.ceil(getView().getWidth());
	}
	
	public int getHeight() {
		return (int)Math.ceil(getView().getHeight());
	}
	
	public void repaint() {
		if (repaintRequested && minimumRepaintSpacingMillis <= 0)
			return;
		logger.trace("Repaint requested!");
		repaintRequested = true;
		
		// Skip repaint if the minimum time hasn't elapsed
		if ((System.currentTimeMillis() - lastPaint) < minimumRepaintSpacingMillis)
			return;
		Platform.runLater(() -> paintCanvas());
	}

	/**
	 * Default map without tools - need to call registerTools to change this
	 */
	private Map<Modes, PathTool> tools = Collections.emptyMap();
	
	private PathTool activeTool = null;


	public double getMinDownsample() {
		return 0.0625;
	}

	public double getMaxDownsample() {
		if (!hasServer())
			return 1;
		return Math.max(getServerWidth(), getServerHeight()) / 100.0;
	}

	public void zoomOut(int nSteps) {
		zoomIn(-nSteps);
	}

	public void zoomIn(int nSteps) {
		if (nSteps == 0)
			return;
		setDownsampleFactor(getDownsampleFactor() * Math.pow(getDefaultZoomFactor(), nSteps), -1, -1);
	}

	/**
	 * The amount by which the downsample factor is scaled for one increment of zoomIn() or zoomOut().  Controls zoom speed.
	 * @return
	 */
	public double getDefaultZoomFactor() {
		return 1.01;
	}

	public void zoomOut() {
		zoomOut(1);
	}

	public void zoomIn() {
		zoomIn(1);		
	}



	public void registerTools(Map<Modes, PathTool> tools) {
		this.tools = tools;
	}


	public QuPathViewer(final ImageData<BufferedImage> imageData, DefaultImageRegionStore regionStore, OverlayOptions overlayOptions) {
		this(imageData, regionStore, overlayOptions, new ImageDisplay(null, regionStore, PathPrefs.getShowAllRGBTransforms()));
	}


	public QuPathViewer(final ImageData<BufferedImage> imageData, DefaultImageRegionStore regionStore, OverlayOptions overlayOptions, ImageDisplay imageDisplay) {
		super();

		this.regionStore = regionStore;

		setOverlayOptions(overlayOptions);
		
		// We need a simple repaint for color changes & simple (thick) line changes
		PathPrefs.strokeThickThicknessProperty().addListener(v -> repaint());
		InvalidationListener repainter = new InvalidationListener() {
			@Override
			public void invalidated(Observable observable) {
				repaint();
			}
		};
		// We need a more extensive repaint for changes to the image pixel display
		InvalidationListener repainterEntire = new InvalidationListener() {
			@Override
			public void invalidated(Observable observable) {
				repaintEntireImage();
			}
		};
		
		PathPrefs.viewerGammaProperty().addListener(repainterEntire);
		
		PathPrefs.viewerInterpolateBilinearProperty().addListener(repainterEntire);
		PathPrefs.useSelectedColorProperty().addListener(repainter);
		PathPrefs.colorDefaultAnnotationsProperty().addListener(repainter);
		PathPrefs.colorSelectedObjectProperty().addListener(repainter);
		PathPrefs.colorTileProperty().addListener(repainter);
		PathPrefs.colorTMAProperty().addListener(repainter);
		PathPrefs.colorTMAMissingProperty().addListener(repainter);
		// We need to repaint everything if detection line thickness changes - including any cached regions
		PathPrefs.strokeThinThicknessProperty().addListener(v -> invalidateHierarchyOverlay());
		

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
		tmaGridOverlay = new TMAGridOverlay(overlayOptions, imageData);
		gridOverlay = new GridOverlay(overlayOptions, imageData);
		// Set up the overlay layers
		coreOverlayLayers.setAll(
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
		});
		
		
		tPosition.addListener((v, o, n) -> {
//			if (zPosition.get() == n)
//				return;
			imageUpdated = true;
			updateThumbnail(false);
			repaint();
		});
		
		zoomToFit.addListener(o -> {
			if (zoomToFit.get()) {
				downsampleFactor = getZoomToFitDownsampleFactor();
				centerImage();
			}
			imageUpdated = true;
			repaint();
		});
	}

	
	
	public ReadOnlyObjectProperty<ImageData<BufferedImage>> getImageDataProperty() {
		return imageDataProperty;
	}
	

	public ImageData<BufferedImage> getImageData() {
		return imageDataProperty.get();
	}


	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}

	public DefaultImageRegionStore getImageRegionStore() {
		return regionStore;
	}


	/**
	 * Set flag to indicate that repaints should prefer speed over accuracy.  This is useful when scrolling quickly, or rapidly changing
	 * the image zoom.
	 * <p>
	 * This only makes a difference for large (tiled) images.
	 * @param fasterRepaint
	 */
	public void setDoFasterRepaint(boolean fasterRepaint) {
		if (this.doFasterRepaint == fasterRepaint)
			return;
		this.imageUpdated = true;
		this.doFasterRepaint = fasterRepaint;
		repaint();
	}

	public boolean getDoFasterRepaint() {
		return doFasterRepaint;
	}

	
	public Point2D getMousePosition() {
		if (mouseX >= 0 && mouseX <= canvas.getWidth() && mouseY >= 0 && mouseY <= canvas.getWidth())
			return new Point2D.Double(mouseX, mouseY);
		return null;
	}
	

	private void setOverlayOptions(OverlayOptions overlayOptions) {
		if (this.overlayOptions == overlayOptions)
			return;
		if (this.overlayOptions != null) {
//			this.overlayOptions.removePropertyChangeListener(this);
		}
		this.overlayOptions = overlayOptions;
		if (overlayOptions != null) {
			InvalidationListener listener = new InvalidationListener() {
				@Override
				public void invalidated(Observable observable) {
					invalidateHierarchyOverlay();
				}
			};
			overlayOptions.fillObjectsProperty().addListener(listener);
			overlayOptions.hiddenClassesProperty().addListener(listener);
			overlayOptions.measurementMapperProperty().addListener(listener);
			overlayOptions.cellDisplayModeProperty().addListener(listener);
			overlayOptions.showConnectionsProperty().addListener(listener);
			
			overlayOptions.showAnnotationsProperty().addListener(e -> repaint());
			overlayOptions.fillAnnotationsProperty().addListener(e -> repaint());
			overlayOptions.showObjectsProperty().addListener(e -> repaint());
			overlayOptions.gridLinesProperty().addListener(e -> repaint());
			overlayOptions.showTMACoreLabelsProperty().addListener(e -> repaint());
			overlayOptions.showGridProperty().addListener(e -> repaint());
			overlayOptions.showTMAGridProperty().addListener(e -> repaint());
			overlayOptions.opacityProperty().addListener(e -> repaint());
		}
		if (isShowing())
			repaint();
	}

	
	public boolean isShowing() {
		return canvas != null && canvas.isVisible() && canvas.getScene() != null;
	}
	


	public boolean getAutoRecolorGridAndScalebar() {
		return autoRecolorGridAndScalebar;
	}


	//	public ROI getTempROI() {
	//		return tempROI;
	//	}
	//	
	//	public void setTempROI(ROI pathROI) {
	//		if (this.tempROI == pathROI)
	//			return;
	//		this.tempROI = pathROI;
	//		repaint();
	//	}



	protected void initializeForServer(ImageServer<BufferedImage> server) {
		// Note that the image has updated
		imageUpdated = true;

		if (server == null) {
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
	 * 
	 * This is useful whenever another component might have received the event,
	 * but the viewer needs to 'know' when it receives the focus.
	 * 
	 * @param spaceDown
	 */
	public void setSpaceDown(boolean spaceDown) {
		if (this.spaceDown == spaceDown)
			return;
		this.spaceDown = spaceDown;
		if (spaceDown) {
			// Temporarily switch to 'move' tool
			if (activeTool != null)
				activeTool.deregisterTool(this);
			activeTool = tools.get(Modes.MOVE);
			activeTool.registerTool(this);
		} else {
			// Reset tool, as required
			if (activeTool != tools.get(mode)) {
				activeTool.deregisterTool(this);
				activeTool = tools.get(mode);
				activeTool.registerTool(this);
			}
		}
		logger.trace("Setting space down to {} - active tool {}", spaceDown, activeTool);
		updateCursor();
	}


	public int getMeanBrightness(final BufferedImage img, int x, int y, int w, int h) {
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
		if (getMeanBrightness(imgThumbnailRGB, 0, 0, imgThumbnailRGB.getWidth(), imgThumbnailRGB.getHeight()) > 127)
			colorOverlaySuggested = ColorToolsAwt.TRANSLUCENT_BLACK;
		else
			colorOverlaySuggested = ColorToolsAwt.TRANSLUCENT_WHITE;
	}


	Color getSuggestedOverlayColor() {
		if (colorOverlaySuggested == null)
			updateSuggestedOverlayColorFromThumbnail();
		return colorOverlaySuggested;
	}
	
	public javafx.scene.paint.Color getSuggestedOverlayColorFX() {
		Color c = getSuggestedOverlayColor();
		if (c == ColorToolsAwt.TRANSLUCENT_BLACK)
			return ColorToolsFX.TRANSLUCENT_BLACK_FX;
//		else if (c == DisplayHelpers.TRANSLUCENT_WHITE):
			return ColorToolsFX.TRANSLUCENT_WHITE_FX;
	}


	//	/**
	//	 * Get a suggested overlay color based on a region of the image currently being displayed on screen.
	//	 * Coordinates are in the component space (not original image space).
	//   * (Removed due to Java 6 performance issue)
	//	 */
	//	Color getSuggestedOverlayColorFromDisplay(int x, int y, int w, int h) {
	//		if (getMeanBrightness(imgBuffer, x, y, w, h) > 127)
	//			return DisplayHelpers.TRANSLUCENT_BLACK;
	//		else
	//			return DisplayHelpers.TRANSLUCENT_WHITE;
	//	}



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


	//	public List<ROI> getPathROIs() {
	//		return pathROIs.;
	//	}

	//	public void setPathROIs(Collection<ROI> pathROIs) {
	//		this.pathROIs.clear();
	//		this.pathROIs.addAll(pathROIs);
	//		repaint();
	//	}


	//	public TMAGrid getTMAGrid() {
	//		return hierarchy.getTMAGrid();
	//	}
	//
	//	public void setTMAGrid(TMAGrid tmaGrid) {
	//		if (getTMAGrid() == tmaGrid)
	//			return;
	//		this.hierarchy.setTMAGrid(tmaGrid);
	//		repaint();
	//	}


	public void setMode(Modes mode) {
		logger.trace("Setting mode {} for {}", mode, this);
		if (mode == null && activeTool != null) {
			activeTool.deregisterTool(this);				
			activeTool = null;
			updateCursor();
		}
		this.mode = mode;
		PathTool tool = tools.get(mode);
		if (activeTool == tool || tool == null)
			return;
		if (activeTool != null)
			activeTool.deregisterTool(this);
		activeTool = tool;
		activeTool.registerTool(this);
		updateCursor();
		updateRoiEditor();
	}

	
	protected void updateCursor() {
//		logger.debug("Requested cursor {} for {}", requestedCursor, getMode());
		switch(getMode()) {
		case MOVE:
			getView().setCursor(Cursor.HAND);
			break;
		default:
			getView().setCursor(requestedCursor);
		}
	}
	
	
//	public void addKeyListener(KeyListener l) {
//		logger.warn("Add key listener not implemented!");
//	}
//
//	public void removeKeyListener(KeyListener l) {
//		logger.warn("Remove key listener not implemented!");
//	}
	

	public Cursor getCursor() {
		return getView().getCursor();
	}
	
	
	public void setCursor(Cursor cursor) {
		this.requestedCursor = cursor;
		updateCursor();
	}
	
	public void setFocusable(boolean focusable) {
		getView().setFocusTraversable(focusable);
//		getCanvas().setFocusTraversable(focusable);
	}

	public Modes getMode() {
		// Always navigate when the spacebar is down
		if (spaceDown)
			return Modes.MOVE;
		return mode;
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
	 * Get the current ROI, i.e. the ROI belonging to the currently-selected object - or null, if there is no object or if the selected object has no ROI.
	 * @return
	 */
	public ROI getCurrentROI() {
		PathObject selectedObject = getSelectedObject();
		return selectedObject == null ? null : selectedObject.getROI();
	}

	/**
	 * Get the bounding box of the ROI returned by getCurrentROI(), or null if no ROI is available.
	 * @return
	 */
	public Rectangle getSelectedImageBounds() {
		if (getCurrentROI() !=  null)
			return AwtTools.getBounds(getCurrentROI());
		//		if (selectedTMAROI != null)
		//			return selectedTMAROI.getBounds();
		return null;
	}

	//	/**
	//	 * Set the current object using x, y image coordinates.
	//	 * 
	//	 * @param x
	//	 * @param y
	//	 * @return true if a different object was selected than previously, false if no object was found at the location
	//	 * 			or the selection did not change
	//	 */
	//	public boolean selectCurrentObjectByLocation(double x, double y) {
	//		PathObject objectSelected = getPathObjectHierarchy().getObjectForLocation(x, y);
	//		if (objectSelected != null) {
	//			setSelectedObject(objectSelected);
	//			return true;
	//		}
	//		setSelectedObject(null);
	//		return false;
	//	}

	public void setSelectedObject(PathObject pathObject) {
		setSelectedObject(pathObject, false);
	}
	
	
	public void setSelectedObject(final PathObject pathObject, final boolean addToSelected) {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		settingSelectedObject = true;
		hierarchy.getSelectionModel().setSelectedObject(pathObject, addToSelected);
		settingSelectedObject = false;
	}


	/**
	 * Create a new annotation object from a ROI, and set it to the current object
	 */
	public void createAnnotationObject(ROI pathROI) {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		PathObject pathObject = new PathAnnotationObject(pathROI);
		hierarchy.addPathObject(pathObject, true);
		setSelectedObject(pathObject);
	}

	public boolean hasROI() {
		return getCurrentROI() != null;
	}


	// TODO: Consider making thumbnail update private
	public void updateThumbnail() {
		updateThumbnail(true);
	}



	// TODO: Consider making thumbnail update private
	private void updateThumbnail(final boolean updateOverlayColor) {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return;

		// Read a thumbnail image
		BufferedImage imgThumbnail = regionStore.getThumbnail(server, getZPosition(), getTPosition(), true);
		if (imageDisplay != null) // && !server.isRGB()) // Transforms will be applied quickly to RGB images, so no need to cache transformed part now
			imgThumbnailRGB = imageDisplay.applyTransforms(imgThumbnail, null);
		else
			imgThumbnailRGB = imgThumbnail;
		thumbnailIsFullImage = imgThumbnail.getWidth() == server.getWidth() && imgThumbnail.getHeight() == server.getHeight();
		if (updateOverlayColor)
			colorOverlaySuggested = null;
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


	public void setZoomToFit(boolean zoomToFit) {
		this.zoomToFit.set(zoomToFit);
	}

	public boolean getZoomToFit() {
		return zoomToFit.get();
	}
	
	public BooleanProperty zoomToFitProperty() {
		return zoomToFit;
	}


	public ImageServer<BufferedImage> getServer() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		return temp == null ? null : temp.getServer();
	}

	public boolean hasServer() {
		return getServer() != null;
	}


	private IntegerProperty tPosition = new SimpleIntegerProperty(0);
	private IntegerProperty zPosition = new SimpleIntegerProperty(0);

	public void setZPosition(int zPos) {
		zPosition.set(zPos);
	}

	public int getTPosition() {
		return tPosition.get();
	}

	public void setTPosition(int tPosition) {
		this.tPosition.set(tPosition);
	}

	public int getZPosition() {
		return zPosition.get();
	}
	
	/**
	 * Returns true between the time setImageData has been called, and before the first repaint has been completed.
	 * 
	 * This is useful to distinguish between view changes triggered by setting the ImageData, and those triggered 
	 * by panning/zooming.
	 * 
	 * @return
	 */
	public boolean isImageDataChanging() {
		return imageDataChanging.get();
	}

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

		this.imageDataProperty.set(imageDataNew);
		ImageServer<BufferedImage> server = imageDataNew == null ? null : imageDataNew.getServer();
		PathObjectHierarchy hierarchy = imageDataNew == null ? null : imageDataNew.getHierarchy();

		long startTime = System.currentTimeMillis();
		if (imageDisplay != null) {
			imageDisplay.setImageData(imageDataNew);
			//			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			//
			//				@Override
			//				protected Void doInBackground() throws Exception {
			//					imageDisplay.setImageData(imageData);
			//					return null;
			//				}
			//				
			//			};
			//			worker.execute();
			//			imageDisplay.setImageData(imageData);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Setting ImageData time: {} ms", endTime - startTime);

		initializeForServer(server);

		fireImageDataChanged(imageDataOld, imageDataNew);

		//		featureMapWrapper = new TiledFeatureMapImageWrapper(server.getWidth(), server.getHeight());

		// Notify overlays of change to ImageData
		Iterator<PathOverlay> iter = allOverlayLayers.iterator();
		while (iter.hasNext()) {
			PathOverlay overlay = iter.next();
			if (overlay instanceof ImageDataOverlay) {
				ImageDataOverlay overlay2 = (ImageDataOverlay)overlay;
				if (!overlay2.supportsImageDataChange()) {
					// Remove any non-core overlay layers that don't support an ImageData change
					if (!coreOverlayLayers.contains(overlay2))
						iter.remove();
					continue;
				} else
					overlay2.setImageData(imageDataNew);
			}
		}
		//		overlay.setImageData(imageData);

		if (imageDataNew != null) {
			//			hierarchyPainter = new PathHierarchyPainter(hierarchy);
			hierarchy.addPathObjectListener(this);
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
		}

		// TODO: Consider shifting, fixing magnification, repainting etc.
		repaint();
		
		setSelectedObject(null);
		
		logger.info("Image data set to {}", imageDataNew);
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
	 */
	public void repaintImageRegion(Rectangle2D region, boolean updateImage) {
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


	public void repaintEntireImage() {
		imageUpdated = true;
		ensureGammaUpdated();
		updateThumbnail();
		repaint();		
	}



	public double getMagnification() {
		if (!hasServer())
			return Double.NaN;
		return getFullMagnification() / getDownsampleFactor();
	}

	public double getFullMagnification() {
		if (!hasServer())
			return 1.0;
		double magnification = getServer().getMagnification();
		if (Double.isNaN(magnification))
			return 1.0;
		else
			return magnification;
	}

	public void setMagnification(final double magnification) {
		if (hasServer())
			setDownsampleFactor(getFullMagnification() / magnification);
	}
	
	
//	public void setMagnification(final double magnification, final double cx, final double cy) {
//		if (hasServer())
//			setDownsampleFactor(getFullMagnification() / magnification, cx, cy);
//	}
	

	//	public BufferedImage makeSnapshot() {
	//		BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
	//		this.paintAll(g);
	//	}



	public void closeViewer() {
		//		painter.close();
		regionStore.removeTileListener(this);
		// Set the server to null
		setImageData(null);
		// Notify listeners
		for (QuPathViewerListener listener : listeners)
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


	protected void paintViewer(Graphics g, int w, int h) {
		
//		if (!SwingUtilities.isEventDispatchThread()) {
//			logger.warn("Repainting called from the wrong thread!");
//			return;
//		}
//		
		if (logger.isTraceEnabled() && new Rectangle(0, 0, getWidth(), getHeight()).equals(g.getClipBounds())) {
			long timestamp = System.currentTimeMillis();
			logger.trace("Full repaint delay time: {} ms", (timestamp - lastRepaintTimestamp));
			lastRepaintTimestamp = timestamp;
		}
		
		ImageServer<BufferedImage> server = getServer();
		if (server == null) {
			g.setColor(background);
			g.fillRect(0, 0, w, h);
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

		float opacity = overlayOptions.getOpacity();
		Graphics2D g2d = (Graphics2D)g.create();
		// Apply required transform to the graphics object (rotation, scaling, shifting...)
		g2d.transform(transform);
		Composite previousComposite = g2d.getComposite();
		boolean paintCompletely = thumbnailIsFullImage || !doFasterRepaint;
		if (showMainOverlay && opacity > 0) {
			if (opacity < 1) {
				AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
				g2d.setComposite(composite);			
			}

			Color color = getSuggestedOverlayColor();
			// Paint the overlay layers
			for (PathOverlay overlay : allOverlayLayers.toArray(new PathOverlay[0])) {
				overlay.setPreferredOverlayColor(color);
				overlay.paintOverlay(g2d, getServerBounds(), downsampleFactor, null, paintCompletely);
			}
//			if (hierarchyOverlay != null) {
//				hierarchyOverlay.setPreferredOverlayColor(color);
//				hierarchyOverlay.paintOverlay(g2d, getServerBounds(), downsampleFactor, null, paintCompletely);
//			}
		}
		
		// Paint the selected object
		PathObjectHierarchy hierarchy = getHierarchy();
		PathObject mainSelectedObject = getSelectedObject();
		Rectangle2D boundsShape = null;
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
				if ((PathPrefs.getPaintSelectedBounds() || (!PathPrefs.getUseSelectedColor())) && !(pathROI instanceof RectangleROI)) {
					boundsShape = AwtTools.getBounds2D(pathROI, boundsShape);
					// Tried to match to pixel boundaries... but resulted in too much jiggling
//					boundsShape.setFrame(
//							Math.round(boundsShape.getX()/downsampleFactor)*downsampleFactor-downsampleFactor,
//							Math.round(boundsShape.getY()/downsampleFactor)*downsampleFactor-downsampleFactor,
//							Math.round(boundsShape.getWidth()/downsampleFactor)*downsampleFactor+2*downsampleFactor,
//							Math.round(boundsShape.getHeight()/downsampleFactor)*downsampleFactor+2*downsampleFactor);
					
//					boundsShape.setFrame(boundsShape.getX()-downsampleFactor, boundsShape.getY()-downsampleFactor, boundsShape.getWidth()+2*downsampleFactor, boundsShape.getHeight()+2*downsampleFactor);
					PathHierarchyPaintingHelper.paintShape(boundsShape, g2d, getSuggestedOverlayColor(), PathHierarchyPaintingHelper.getCachedStroke(Math.max(downsampleFactor, 1)*2), null, downsampleFactor);
//					boundsShape.setFrame(boundsShape.getX()+downsampleFactor, boundsShape.getY()-downsampleFactor, boundsShape.getWidth(), boundsShape.getHeight());
//					PathHierarchyPaintingHelper.paintShape(boundsShape, g2d, new Color(1f, 1f, 1f, 0.75f), PathHierarchyPaintingHelper.getCachedStroke(Math.max(downsampleFactor, 1)*2), null, downsampleFactor);
				}
				
				// Avoid double-painting of annotations (which looks odd if they are filled in)
				// However do always paint detections, since they are otherwise painted (unselected) 
				// in a cached way
				if ((selectedObject.isDetection() && PathPrefs.getUseSelectedColor()) || !PathObjectTools.hierarchyContainsObject(hierarchy, selectedObject))
					PathHierarchyPaintingHelper.paintObject(selectedObject, false, g2d, boundsDisplayed, overlayOptions, getHierarchy().getSelectionModel(), downsampleFactor);
				// Paint ROI handles, if required
				if (selectedObject == mainSelectedObject && roiEditor.hasROI()) {
					Stroke strokeThick = PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.getThickStrokeThickness() * downsampleFactor);
					Color color = PathPrefs.getUseSelectedColor() ? ColorToolsAwt.getCachedColor(PathPrefs.getSelectedObjectColor()) : null;
					if (color == null)
						color = PathObjectColorToolsAwt.getDisplayedColorAWT(selectedObject);
					g2d.setStroke(strokeThick);
					double size = getROIHandleSize();
					PathHierarchyPaintingHelper.paintHandles(roiEditor, g2d, size, color, ColorToolsAwt.getTranslucentColor(color));
				}
			}
		}

		// Notify any listeners of shape changes
		if (shapeChanged)
			fireVisibleRegionChangedEvent(lastVisibleShape);
	}

	
	/**
	 * Create an RGB BufferedImage suitable for caching the image used for painting.
	 * @param w
	 * @param h
	 * @return
	 */
	protected BufferedImage createBufferedImage(final int w, final int h) {
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
		boolean requiresTiling = !thumbnailIsFullImage && lowResolutionDownsample > Math.max(downsampleFactor, 1);

		// Check if we will be painting some background beyond the image edge
		Rectangle shapeBounds = shapeToUpdate.getBounds();
		boolean overBoundary = shapeBounds.x < 0 || shapeBounds.y < 0 || shapeBounds.x + shapeBounds.width >= serverWidth || shapeBounds.y + shapeBounds.height >= serverHeight;

		// Reset interpolation - this roughly halves repaint times
		if (PathPrefs.getViewerInterpolationBilinear())
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gBuffered.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		if (requiresTiling) {

			// TODO: Consider removing faster repaint?
			double downsample = downsampleFactor;
			if (doFasterRepaint)
				downsample = downsampleFactor * 1.5;


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
					imgBuffer = imageDisplay.applyTransforms(imgBuffer, null);
			} else {
				regionStore.paintRegion(server, gBuffered, shapeToUpdate, getZPosition(), getTPosition(), downsample, imgThumbnail, null, imageDisplay);
			}
		} else {
			// Just paint the 'thumbnail' version, which has already (potentially) been color-transformed
			paintThumbnail(gBuffered, imgThumbnailRGB, serverWidth, serverHeight, this);
		}
	}






	/**
	 * Flag to set if the main overlay is being shown (objects, TMA grid etc.)
	 */
	public void setShowMainOverlay(boolean showOverlay) {
		if (this.showMainOverlay == showOverlay)
			return;
		this.showMainOverlay = showOverlay;
		repaint();
	}


	/**
	 * Flag to test if the main overlay is being shown (objects, TMA grid etc.)
	 * @return
	 */
	public boolean getShowMainOverlay() {
		return showMainOverlay;
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
	

	/**
	 * Get the handle size used to draw a ROI.
	 * This is adapted to the downsample factor and any currently-selected ROI, to try to get a sensible
	 * size even when very large images are viewed at a low magnification.
	 * 
	 * @return
	 */
	public double getROIHandleSize() {
		double strokeWidth = overlayOptions.getThickStrokeWidth(downsampleFactor);
		double size = strokeWidth * 4;
		if (roiEditor.getROI() instanceof PathArea) {
			ROI pathROI = roiEditor.getROI();
			if (pathROI instanceof AreaROI || roiEditor.getROI() instanceof PolygonROI)
				size = Math.min(size, Math.min(pathROI.getBoundsWidth()/32, pathROI.getBoundsHeight()/32));
			else
				size = Math.min(size, Math.min(pathROI.getBoundsWidth()/8, pathROI.getBoundsHeight()/8));
		}
		return Math.max(strokeWidth * .5, size);
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
		if (gamma != PathPrefs.getViewerGamma()) {
			setGamma(PathPrefs.getViewerGamma());
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



	public Dimension getSize() {
		return new Dimension(getWidth(), getHeight());
	}
	
	

	public RoiEditor getROIEditor() {
		return roiEditor;
	}



	//	public boolean isDisplayingConvexHull() {
	//		return displayConvexHull;
	//	}

	public void updateTooltip(final Tooltip tooltip) {
		String text = getTooltipText(mouseX, mouseY);
		tooltip.setText(text);
		if (text == null)
			tooltip.setOpacity(0);
		else
			tooltip.setOpacity(1);
	}

	public String getTooltipText(final double x, final double y) {
		// Try to show which TMA core is selected - if we have a TMA image
		PathObjectHierarchy hierarchy = getHierarchy();
		if (PathPrefs.showTMAToolTips() && hierarchy != null && hierarchy.getTMAGrid() != null) {
			Point2D p = componentPointToImagePoint(x, y, null, false);
//						double xx = componentXtoImageX(x);
//						double yy = componentYtoImageY(y);
			TMACoreObject core = PathObjectTools.getTMACoreForLocation(hierarchy, p.getX(), p.getY());
			if (core != null) {
				if (core.isMissing())
					return String.format("TMA Core %s\n(missing)", core.getName());
				else
					return String.format("TMA Core %s", core.getName());
			}
		}
		return null;
	}


	public ImageDisplay getImageDisplay() {
		return imageDisplay;
	}


	protected static double getNextHigher(double[] array, double val) {
		for (double d: array)
			if (d > val)
				return d;
		return val;
	}

	protected static double getLastLower(double[] array, double val) {
		for (int i = array.length-1; i >= 0; i--)
			if (array[i] < val)
				return array[i];
		return val;
	}
	
	
	protected boolean componentContains(double x, double y) {
		return x >= 0 && x < getView().getWidth() && y >= 0 && y <= getView().getHeight();
	}
	
	
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
	 * The purpose is to make it possible to zoom in/out while keeping the cursor focussed on a particular location.
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
			downsampleFactor = Math.min(Math.max(downsampleFactor, getMinDownsample()), getMaxDownsample());
		if (this.downsampleFactor == downsampleFactor)
			return;

		// Don't allow setting if we have 'zoom to fit'
		if (getZoomToFit())
			return;

		// Refine the downsample factor as required
		//		double fullMag = getFullMagnification();
		//		downsampleFactor = fullMag / getClosestMagnification(fullMag / downsampleFactor);
		if (this.downsampleFactor == downsampleFactor)
			return;

		// Take care of centering according to the specified coordinates
		if (cx < 0)
			cx = getWidth() / 2;
		if (cy < 0)
			cy = getHeight() / 2;

		// Compute what the x, y coordinates should be to preserve the same image centering
		if (!isRotated()) {
			xCenter += (cx - getWidth()/2) * (this.downsampleFactor - downsampleFactor);
			yCenter += (cy - getHeight()/2) * (this.downsampleFactor - downsampleFactor);
		} else {
			// Get the image coordinate
			Point2D p2 = componentPointToImagePoint(cx, cy, null, false);
			double dx = (p2.getX() - xCenter) / this.downsampleFactor * downsampleFactor;
			double dy = (p2.getY() - yCenter) / this.downsampleFactor * downsampleFactor;
			xCenter = p2.getX() - dx;
			yCenter = p2.getY() - dy;
		}

		this.downsampleFactor = downsampleFactor;
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


	public int getServerWidth() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? 0 : server.getWidth();
	}

	public int getServerHeight() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? 0 : server.getHeight();
	}


//	public Rectangle getServerBounds() {
//		ImageServer server = getServer();
//		return server == null ? null : new Rectangle(0, 0, server.getWidth(), server.getHeight());
//	}
	
	
	public ImageRegion getServerBounds() {
		ImageServer<BufferedImage> server = getServer();
		return server == null ? null : ImageRegion.createInstance(0, 0, server.getWidth(), server.getHeight(), getZPosition(), getTPosition());
	}


	public double getDownsampleFactor() {
		return downsampleFactor;
	}

	/**
	 * Convert a coordinate from the viewer into the corresponding pixel coordinate in the full-resolution image - optionally constraining it to any server bounds.
	 * A point object can optionally be provided into which the location is written (may be the same as the component point object).
	 * 
	 * @param point
	 * @param pointDest
	 * @return
	 */
	public Point2D componentPointToImagePoint(Point2D point, Point2D pointDest, boolean constrainToBounds) {
		return componentPointToImagePoint(point.getX(), point.getY(), pointDest, constrainToBounds);
	}

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
	 * @return
	 */
	public Point2D imagePointToComponentPoint(Point2D point, Point2D pointDest, boolean constrainToBounds) {
		return imagePointToComponentPoint(point.getX(), point.getY(), pointDest, constrainToBounds);
	}

	public Point2D imagePointToComponentPoint(double x, double y, Point2D pointDest, boolean constrainToBounds) {
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


	public double getDisplayedPixelWidthMicrons() {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return Double.NaN;
		return server.getPixelWidthMicrons() * getDownsampleFactor();
	}

	public double getDisplayedPixelHeightMicrons() {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return Double.NaN;
		return server.getPixelHeightMicrons() * getDownsampleFactor();
	}


	public void centerImage() {
		ImageServer<BufferedImage> server = getServer();
		if (server == null)
			return;
		setCenterPixelLocation(0.5 * server.getWidth(), 0.5 * server.getHeight());
	}

	/**
	 * Get a string representing the image coordinates for a particular x &amp; y location in the viewer component.
	 * 
	 * @param x
	 * @param y
	 * @return
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
		if (useCalibratedUnits && server.hasPixelSizeMicrons()) {
			units = '\u00B5' + "m";
			xDisplay *= server.getPixelWidthMicrons();
			yDisplay *= server.getPixelHeightMicrons();
		} else {
			units = "px";
		}
		
		// See if we're on top of a TMA core
		String prefix = "";
		if (getHierarchy().getTMAGrid() != null) {
			TMACoreObject core = getHierarchy().getTMAGrid().getTMACoreForPixel(xx, yy);
			if (core != null && core.getName() != null)
				prefix = "Core: " + core.getName() + "\n";
		}

		String s = null;
		RegionRequest request = ImageRegionStoreHelpers.getTileRequest(server, xx, yy, downsampleFactor, getZPosition(), getTPosition());
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
			double zSpacing = server.getZSpacingMicrons();
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
	public String getLocationString(boolean useCalibratedUnits) {
		if (componentContains(mouseX, mouseY))
			return getLocationString(mouseX, mouseY, useCalibratedUnits);
		else
			return "";
	}


	public PathObjectHierarchy getHierarchy() {
		ImageData<BufferedImage> temp = imageDataProperty.get();
		return temp == null ? null : temp.getHierarchy();
	}

	public void addViewerListener(QuPathViewerListener listener) {
		listeners.add(listener);
	}

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

		// Flag that the location has been updated
		locationUpdated = true;
		repaint();
	}




	protected void updateAffineTransform() {
		if (!hasServer())
			return;

		transform.setToIdentity();
		transform.translate(getWidth()*.5, getHeight()*.5);
		transform.scale(1.0/downsampleFactor, 1.0/downsampleFactor);
		transform.translate(-xCenter, -yCenter);
		if (rotation != 0)
			transform.rotate(rotation, xCenter, yCenter);

		transformInverse.setTransform(transform);
		try {
			transformInverse.invert();
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set the rotation; angle in radians.
	 * 
	 * @param theta
	 */
	public void setRotation(double theta) {
		if (this.rotation == theta)
			return;
		this.rotation = theta;
		imageUpdated = true;
		updateAffineTransform();
		repaint();
	}

	public boolean isRotated() {
		return getRotation() != 0;
	}

	/**
	 * Get the current rotation; angle in radians.
	 * @return
	 */
	public double getRotation() {
		return rotation;
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



	public void invalidateHierarchyOverlay() {
		hierarchyOverlay.clearCachedOverlay();
		repaint();
	}


	/**
	 * Force the overlay displaying detections and annotations to be repainted.  Any cached versions will be thrown away, so this is useful when
	 * some aspect of the display has changed, e.g. objects colors or fill/outline status.  Due to the usefulness of caching for performance, it should
	 * not be called too often.
	 */
	public void forceOverlayUpdate() {
		hierarchyOverlay.clearCachedOverlay();
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
		// Clear any cached regions of the overlay, if necessary
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
		repaint();
	}





	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject) {

		// We only want to shift the object ROI to the center under certain conditions, otherwise the screen jerks annoyingly
		if (!settingSelectedObject && !getZoomToFit() && pathObjectSelected != null && pathObjectSelected.getROI() instanceof PathShape) {

			// We want to center a TMA core if more than half of it is outside the window
			boolean centerCore = false;
			Shape shapeDisplayed = getDisplayedRegionShape();
			ROI pathROI = pathObjectSelected.getROI();
			if (centerCore || !shapeDisplayed.intersects(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight())) {
				//			if (!getDisplayedRegionShape().intersects(pathObjectSelected.getROI().getBounds2D())) {
				//			(!(pathObjectSelected instanceof PathDetectionObject && getDisplayedRegionShape().intersects(pathObjectSelected.getROI().getBounds2D())))) {
				double cx = pathObjectSelected.getROI().getCentroidX();
				double cy = pathObjectSelected.getROI().getCentroidY();
				setCenterPixelLocation(cx, cy);
				//		logger.info("Centered to " + cx + ", " + cy);
			}
		}
		updateRoiEditor();
		for (QuPathViewerListener listener : new ArrayList<QuPathViewerListener>(listeners)) {
			listener.selectedObjectChanged(this, pathObjectSelected);
		}

		logger.trace("Selected path object changed from {} to {}", previousObject, pathObjectSelected);

		repaint();
//		repaintEntireImage();
	}

	/**
	 * Get the AffineTransform used to convert the image coordinates into the component coordinate space
	 * 
	 * @return
	 */
	protected AffineTransform getAffineTransform() {
		return transform;
	}




	private void updateRoiEditor() {
		PathObject pathObjectSelected = getSelectedObject();
		ROI previousROI = roiEditor.getROI();
		ROI newROI = pathObjectSelected != null && MoveTool.canAdjust(pathObjectSelected) ? pathObjectSelected.getROI() : null;

		if (previousROI == newROI)
			roiEditor.ensureHandlesUpdated();
		else
			roiEditor.setROI(newROI);

		repaint();		
	}


	public synchronized String getServerPath() {
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


	
	protected IntegerProperty zPositionProperty() {
		return zPosition;
	}
	
	protected IntegerProperty tPositionProperty() {
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

		private long keyDownTime = Long.MIN_VALUE;
		private double scale = 1.0;

		@Override
		public void handle(KeyEvent event) {
			KeyCode code = event.getCode();

			// Handle backspace/delete to remove selected object
			if (event.getEventType() == KeyEvent.KEY_PRESSED && (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE)) {
				if (getROIEditor().hasActiveHandle() || getROIEditor().isTranslating()) {
					logger.debug("Cannot delete object - ROI being edited");
					return;
				}
				if (getImageData() != null) {
					if (getHierarchy().getSelectionModel().singleSelection()) {
						DisplayHelpers.promptToRemoveSelectedObject(getHierarchy().getSelectionModel().getSelectedObject(), getHierarchy());
					} else {
						DisplayHelpers.promptToClearAllSelectedObjects(getImageData());
					}
//					setSelectedObject(null);
				}
				event.consume();
				return;
			}

			PathObjectHierarchy hierarchy = getHierarchy();
			if (hierarchy == null)
				return;

			// Center selected object if Enter pressed ('center on enter')
			if (event.getEventType() == KeyEvent.KEY_PRESSED && code == KeyCode.ENTER) {
				PathObject selectedObject = getSelectedObject();
				if (selectedObject != null && selectedObject.hasROI())
					setCenterPixelLocation(selectedObject.getROI().getCentroidX(), selectedObject.getROI().getCentroidY());
				event.consume();
				return;
			}




			if (!(code == KeyCode.LEFT || code == KeyCode.UP || code == KeyCode.RIGHT || code == KeyCode.DOWN))
				return;

			// Use arrow keys to navigate, either or directly or using a TMA grid
			TMAGrid tmaGrid = hierarchy.getTMAGrid();
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
					for (int i = 0; i < tmaGrid.nCores(); i++) {
						ROI coreROI = tmaGrid.getTMACore(i).getROI();
						double dx = coreROI.getCentroidX() - getCenterPixelX();
						double dy = coreROI.getCentroidY() - getCenterPixelY();
						double displacementSq = dx*dx + dy*dy;
						if (displacementSq < minDisplacementSq) {
							ind = i;
							minDisplacementSq = displacementSq;
						}
					}
				}

				switch (code) {
				case LEFT:
					if (ind >= 0)
						ind--;
					else
						ind = 0;
					break;
				case UP:
					if (ind >= 0)
						ind -= w;
					else
						ind = 0;
					break;
				case RIGHT:
					if (ind >= 0)
						ind++;
					else
						ind = 0;
					break;
				case DOWN:
					if (ind >= 0)
						ind += w;
					else
						ind = 0;
					break;
				default:
					return;
				}
				// Set the selected object & center the viewer
				if (ind >= 0 && ind < w*h) {
					PathObject selectedObject = tmaGrid.getTMACore(ind);
					hierarchy.getSelectionModel().setSelectedObject(selectedObject);
					if (selectedObject != null && selectedObject.hasROI())
						setCenterPixelLocation(selectedObject.getROI().getCentroidX(), selectedObject.getROI().getCentroidY());
					//					setSelectedObject(tmaGrid.getTMACore(ind));
				}
				
				event.consume();

				
			} else if (event.getEventType() == KeyEvent.KEY_PRESSED) {


				if (event.isShiftDown()) {

					//					// TODO: Handle changes in focus or time point
					//					if (event.isAltDown() || event.isMetaDown() || event.isControlDown()) {
					//						switch (code) {
					//						case UP:
					//							if (sliderZ != null)
					//								sliderZ.setValue(sliderZ.getValue() + 1);
					//							return;
					//						case DOWN:
					//							if (sliderZ != null)
					//								sliderZ.setValue(sliderZ.getValue() - 1);
					//							return;
					//						case LEFT:
					//							if (sliderT != null)
					//								sliderT.setValue(sliderT.getValue() - 1);
					//							return;
					//						case RIGHT:
					//							if (sliderT != null)
					//								sliderT.setValue(sliderT.getValue() + 1);
					//							return;
					//						default:
					//							break;
					//						}
					//					}


					switch (code) {
					case UP:
						zoomOut(10);
						event.consume();
						return;
					case DOWN:
						zoomIn(10);
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

				scale = scale * 1.05;
				double d = getDownsampleFactor() * scale * 20;
				double dx = 0;
				double dy = 0;
				switch (code) {
				case LEFT:
					dx = d;
					break;
				case UP:
					dy = d;
					break;
				case RIGHT:
					dx = -d;
					break;
				case DOWN:
					dy = -d;
					break;
				default:
					return;
				}

				startMoving(dx, dy);
				event.consume();


			} else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
				switch (code) {
				case LEFT:
				case UP:
				case RIGHT:
				case DOWN:
					mover.decelerate();
					setDoFasterRepaint(false);
					keyDownTime = Long.MIN_VALUE;
					scale = 1;
					event.consume();
					break;
				default:
					return;
				}	
			}
		}
	}
	
	
private MoveTool.ViewerMover mover = new MoveTool.ViewerMover(this);
	
	
	void startMoving(final double dx, final double dy) {
		mover.startMoving(dx, dy, true);
		this.setDoFasterRepaint(true);
	}

}