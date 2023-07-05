/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.SplitPane.Divider;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import jfxtras.scene.menu.CirclePopupMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.ToolManager;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.TMACommands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Class to manage multiple {@link QuPathViewer} instances in a UI region.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class ViewerManager implements QuPathViewerListener {

	private static final Logger logger = LoggerFactory.getLogger(ViewerManager.class);

	private QuPathGUI qupath;

	/**
	 * The current ImageData in the current QuPathViewer
	 */
	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	
	private ObservableList<QuPathViewer> viewers = FXCollections.observableArrayList();
	private ObservableList<QuPathViewer> viewersUnmodifiable = FXCollections.unmodifiableObservableList(viewers);
	
	private SimpleObjectProperty<QuPathViewer> activeViewerProperty = new SimpleObjectProperty<>();

	private SplitPaneGrid splitPaneGrid;

	private ViewerPlusDisplayOptions viewerDisplayOptions = new ViewerPlusDisplayOptions();
	private OverlayOptions overlayOptions = new OverlayOptions();
	
	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);

	private PathObject lastAnnotationObject = null;

	private final Color colorBorder = Color.rgb(180, 0, 0, 0.5);

	private BooleanProperty synchronizeViewers = new SimpleBooleanProperty(true);
	private double lastX = Double.NaN;
	private double lastY = Double.NaN;
	private double lastDownsample = Double.NaN;
	private double lastRotation = Double.NaN;

	private ViewerManager(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	/**
	 * Create a new instance
	 * @param qupath
	 * @return
	 */
	public static ViewerManager create(final QuPathGUI qupath) {
		return new ViewerManager(qupath);
	}
	
	
	/**
	 * Get an observable list of viewers.
	 * Note that the list is unmodifiable; viewers should be added or removed through other 
	 * methods in thie class.
	 * @return
	 */
	public ObservableList<QuPathViewer> getAllViewers() {
		return viewersUnmodifiable;
	}

	/**
	 * Get the overlay options shared by all viewers created by this manager.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}
	

	/**
	 * Show the overview image.
	 * @return
	 */
	public BooleanProperty showOverviewProperty() {
		return viewerDisplayOptions.showOverviewProperty();
	}

	/**
	 * Show the cursor location.
	 * @return
	 */
	public BooleanProperty showLocationProperty() {
		return viewerDisplayOptions.showLocationProperty();
	}

	/**
	 * Show the scalebar.
	 * @return
	 */
	public BooleanProperty showScalebarProperty() {
		return viewerDisplayOptions.showScalebarProperty();
	}


	/**
	 * Match the display resolutions (downsamples) of all viewers to match the current viewer.
	 * This uses calibrated pixel size information if available.
	 */
	public void matchResolutions() {
		var viewer = getActiveViewer();
		var activeViewers = getAllViewers().stream().filter(v -> v.hasServer()).toList();
		if (activeViewers.size() <= 1 || !viewer.hasServer())
			return;
		var cal = viewer.getServer().getPixelCalibration();
		double pixelSize = cal.getAveragedPixelSize().doubleValue();
		double downsample = viewer.getDownsampleFactor();
		for (var temp : activeViewers) {
			if (temp == viewer)
				continue;
			var cal2 = temp.getServer().getPixelCalibration();
			double newDownsample;
			double tempPixelSize = cal2.getAveragedPixelSize().doubleValue();
			if (Double.isFinite(tempPixelSize) && Double.isFinite(pixelSize) && cal2.getPixelWidthUnit().equals(cal.getPixelWidthUnit()) && cal2.getPixelHeightUnit().equals(cal.getPixelHeightUnit())) {
				newDownsample = (pixelSize / tempPixelSize) * downsample;
			} else {
				newDownsample = downsample;
			}
			temp.setDownsampleFactor(newDownsample);
		}
	}

	public void setActiveViewer(final QuPathViewer viewer) {
		QuPathViewer previousActiveViewer = getActiveViewer();
		if (previousActiveViewer == viewer)
			return;

		ImageData<BufferedImage> imageDataNew = viewer == null ? null : viewer.getImageData();
		if (previousActiveViewer != null) {
			previousActiveViewer.setBorderColor(null);
			//				activeViewer.setBorder(BorderFactory.createLineBorder(colorTransparent, borderWidth));
			//				activeViewer.setBorder(null);
			deactivateTools(previousActiveViewer);

			// Grab reference to the current annotation, if there is one
			PathObject pathObjectSelected = previousActiveViewer.getSelectedObject();
			if (pathObjectSelected instanceof PathAnnotationObject) {
				lastAnnotationObject = pathObjectSelected;					
			}
		}
		this.activeViewerProperty.set(viewer);
		lastX = Double.NaN;
		lastY = Double.NaN;
		lastDownsample = Double.NaN;
		lastRotation = Double.NaN;
		if (viewer != null) {
			viewer.setBorderColor(colorBorder);
			if (viewer.getServer() != null) {
				lastX = viewer.getCenterPixelX();
				lastY = viewer.getCenterPixelY();
				lastDownsample = viewer.getDownsampleFactor();
				lastRotation = viewer.getRotation();
			}
		}
		logger.debug("Active viewer set to {}", viewer);
		imageDataProperty.set(imageDataNew);
	}
	
	
	/**
	 * Read-only property containing the image open within the currently-active viewer.
	 * To change the open image data, you should do so directly within the viewer.
	 * @return
	 */
	public ReadOnlyObjectProperty<ImageData<BufferedImage>> imageDataProperty() {
		return imageDataProperty;
	}
	
	
	private void deactivateTools(final QuPathViewer viewer) {
		viewer.setActiveTool(null);
	}
	
	/**
	 * Get the value of {@link #activeViewerProperty()}.
	 * @return
	 */
	public QuPathViewer getActiveViewer() {
		return activeViewerProperty.get();
	}
	
	public BooleanProperty zoomToFitProperty() {
		return zoomToFit;
	}

	/**
	 * Get a read-only property representing the currently active viewer.
	 * Only one viewer can be active, and this should not be null (i.e. the list of {@link #getAllViewers()} 
	 * should never be empty).
	 * @return
	 */
	public ReadOnlyObjectProperty<QuPathViewer> activeViewerProperty() {
		return activeViewerProperty;
	}

	/**
	 * Get the region node that can be added to a scene graph to display the viewers.
	 * @return
	 */
	public Region getRegion() {
		if (splitPaneGrid == null) {
			// Create a reasonably-sized viewer
			QuPathViewerPlus defaultViewer = createViewer();
			this.viewers.add(defaultViewer);
			if (defaultViewer != null)
				defaultViewer.addViewerListener(this);
			setActiveViewer(defaultViewer);
			splitPaneGrid = new SplitPaneGrid(defaultViewer.getView());
		}
		return splitPaneGrid.getMainSplitPane();
	}
	
	
	/**
	 * Request that all viewers are repainted as soon as possible.
	 */
	public void repaintAllViewers() {
		for (QuPathViewer v : getAllViewers()) {
			v.repaint();
		}
	}
	

	/**
	 * Create a viewer, adding it to the stored array but not adding it to any component (which is left up to the calling code to handle)
	 * @return
	 */
	protected QuPathViewerPlus createViewer() {
		QuPathViewerPlus viewerNew = new QuPathViewerPlus(null, qupath.getImageRegionStore(), overlayOptions, viewerDisplayOptions);
		setupViewer(viewerNew);
		viewerNew.addViewerListener(this);
		viewers.add(viewerNew);
		return viewerNew;
	}


	SplitPane getAncestorSplitPane(Node node) {
		while (node != null && !(node instanceof SplitPane))
			node = node.getParent();
		return (SplitPane)node;
	}

	/**
	 * Try to remove the row containing the specified viewer, notifying the user if this isn't possible.
	 * @param viewer
	 * @return true if the row was removed, false otherwise
	 */
	public boolean removeRow(final QuPathViewer viewer) {
		//			if (viewer.getServer() != null)
		//				System.err.println(viewer.getServer().getShortServerName());
		// Note: These are the internal row numbers... these don't necessarily match with the displayed row (?)
		int row = splitPaneGrid.getRow(viewer.getView());
		if (row < 0) {
			// Shouldn't occur...
			Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
			return false;
		}
		if (splitPaneGrid.nRows() == 1) {
			Dialogs.showErrorMessage("Close row error", "The last row can't be removed!");
			return false;
		}

		int nOpen = splitPaneGrid.countOpenViewersForRow(row);
		if (nOpen > 0) {
			Dialogs.showErrorMessage("Close row error", "Please close all open viewers in the selected row, then try again");
			return false;
		}
		splitPaneGrid.removeRow(row);
		splitPaneGrid.resetGridSize();
		// Make sure the viewer list is up-to-date
		refreshViewerList();
		return true;
	}


	/**
	 * Check all viewers to see if they are associated with a scene, and remove them from the list if not.
	 */
	private void refreshViewerList() {
		// Remove viewers from the list if they aren't associated with anything
		// Easiest way is to check for a scene
		Iterator<? extends QuPathViewer> iter = viewers.iterator();
		while (iter.hasNext()) {
			if (iter.next().getView().getScene() == null)
				iter.remove();
		}
	}


	/**
	 * Try to remove the column containing the specified viewer, notifying the user if this isn't possible.
	 * @param viewer
	 * @return true if the column was removed, false otherwise
	 */
	public boolean removeColumn(final QuPathViewer viewer) {
		int col = splitPaneGrid.getColumn(viewer.getView());
		if (col < 0) {
			// Shouldn't occur...
			Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
			return false;
		}

		if (splitPaneGrid.nCols() == 1) {
			Dialogs.showErrorMessage("Close row error", "The last row can't be removed!");
			return false;
		}

		int nOpen = splitPaneGrid.countOpenViewersForColumn(col);
		if (nOpen > 0) {
			Dialogs.showErrorMessage("Close column error", "Please close all open viewers in selected column, then try again");
			//				DisplayHelpers.showErrorMessage("Close column error", "Please close all open viewers in column " + col + ", then try again");
			return false;
		}
		splitPaneGrid.removeColumn(col);
		splitPaneGrid.resetGridSize();
		// Make sure the viewer list is up-to-date
		refreshViewerList();
		return true;
	}


	public void addRow(final QuPathViewer viewer) {
		splitViewer(viewer, false);
		splitPaneGrid.resetGridSize();
	}

	public void addColumn(final QuPathViewer viewer) {
		splitViewer(viewer, true);
		splitPaneGrid.resetGridSize();
	}


	public void splitViewer(final QuPathViewer viewer, final boolean splitVertical) {
		if (!viewers.contains(viewer))
			return;

		if (splitVertical) {
			splitPaneGrid.addColumn(splitPaneGrid.getColumn(viewer.getView()));
		} else {
			splitPaneGrid.addRow(splitPaneGrid.getRow(viewer.getView()));
		}
	}

	public void resetGridSize() {
		splitPaneGrid.resetGridSize();
	}


	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		if (viewer != null && viewer == getActiveViewer()) {
			if (viewer.getServer() != null) {
				// Setting these to NaN prevents unexpected jumping when a new image is opened
				lastX = Double.NaN;
				lastY = Double.NaN;
				lastDownsample = Double.NaN;
				lastRotation = Double.NaN;
			}
			imageDataProperty.set(viewer.getImageData());
		}
	}
	
	
	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
		if (viewer == null)
			return;
		if (viewer != getActiveViewer() || viewer.isImageDataChanging() || zoomToFit.get()) {
			return;
		}

		QuPathViewer activeViewer = getActiveViewer();
		double x = activeViewer.getCenterPixelX();
		double y = activeViewer.getCenterPixelY();
		double rotation = activeViewer.getRotation();
		double dx = Double.NaN, dy = Double.NaN, dr = Double.NaN;

		double downsample = viewer.getDownsampleFactor();
		double relativeDownsample = viewer.getDownsampleFactor() / lastDownsample;

		// Shift as required, assuming we aren't aligning cores
		//			if (!aligningCores) {
		//			synchronizeViewers = true;
		if (synchronizeViewers.get()) {
			if (!Double.isNaN(lastX + lastY)) {
				dx = x - lastX;
				dy = y - lastY;
				dr = rotation - lastRotation;
			}

			for (QuPathViewer v : viewers) {
				if (v == viewer)
					continue;
				if (!Double.isNaN(relativeDownsample))
					v.setDownsampleFactor(v.getDownsampleFactor() * relativeDownsample, -1, -1, false);
				if (!Double.isNaN(dr) && dr != 0)
					v.setRotation(v.getRotation() + dr);

				// Shift as required - correcting for rotation
				double downsampleRatio = v.getDownsampleFactor() / downsample;
				if (!Double.isNaN(dx) && !Double.isNaN(downsampleRatio)) {

					double rot = rotation - v.getRotation();
					double sin = Math.sin(rot);
					double cos = Math.cos(rot);

					double dx2 = dx * downsampleRatio;
					double dy2 = dy * downsampleRatio;

					double dx3 = cos * dx2 - sin * dy2;
					double dy3 = sin * dx2 + cos * dy2;

					v.setCenterPixelLocation(v.getCenterPixelX() + dx3, v.getCenterPixelY() + dy3);
				}
			}
		}

		lastX = x;
		lastY = y;
		lastDownsample = downsample;
		lastRotation = rotation;
	}


	public boolean getSynchronizeViewers() {
		return synchronizeViewers.get();
	}

	public void setSynchronizeViewers(final boolean synchronizeViewers) {
		this.synchronizeViewers.set(synchronizeViewers);
	}

	public ReadOnlyBooleanProperty synchronizeViewersProperty() {
		return synchronizeViewers;
	}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
		// Store any annotation ROIs, which might need to be transferred
		if (pathObjectSelected instanceof PathAnnotationObject) {
			lastAnnotationObject = pathObjectSelected;
			return;
		}

		// Don't handle unselected viewers
		if (viewer != getActiveViewer()) {
			return;
		}

		// Synchronize TMA cores
		if (!(pathObjectSelected instanceof TMACoreObject))
			return;

		// Thwart the upcoming region shift
		lastX = Double.NaN;
		lastY = Double.NaN;
		lastDownsample = Double.NaN;
		lastRotation = Double.NaN;

		//			aligningCores = true;
		String coreName = ((TMACoreObject)pathObjectSelected).getName();
		for (QuPathViewer v : viewers) {
			if (v == viewer)
				continue;
			PathObjectHierarchy hierarchy = v.getHierarchy();
			if (hierarchy == null || hierarchy.getTMAGrid() == null)
				continue;

			TMAGrid tmaGrid = hierarchy.getTMAGrid();
			TMACoreObject core = tmaGrid.getTMACore(coreName);
			if (core != null) {
				v.setSelectedObject(core);
				double cx = core.getROI().getCentroidX();
				double cy = core.getROI().getCentroidY();
				v.setCenterPixelLocation(cx, cy);
			}
		}
	}



	public boolean applyLastAnnotationToActiveViewer() {
		if (lastAnnotationObject == null) {
			logger.info("No annotation object to copy");
			return false;
		}

		QuPathViewer activeViewer = getActiveViewer();
		if (activeViewer == null || activeViewer.getHierarchy() == null) {
			logger.info("No active viewer available");
			return false;
		}

		PathObjectHierarchy hierarchy = activeViewer.getHierarchy();
		if (PathObjectTools.hierarchyContainsObject(hierarchy, lastAnnotationObject)) {
			logger.info("Hierarchy already contains annotation object!");
			return false;
		}

		ROI roi = lastAnnotationObject.getROI().duplicate();

		// If we are within a TMA core, try to apply any required translations
		TMACoreObject coreNewParent = null;
		if (hierarchy.getTMAGrid() != null) {
			TMACoreObject coreParent = null;
			PathObject parent = lastAnnotationObject.getParent();
			while (parent != null) {
				if (parent instanceof TMACoreObject) {
					coreParent = (TMACoreObject)parent;
					break;
				} else
					parent = parent.getParent();
			}
			if (coreParent != null) {
				coreNewParent = hierarchy.getTMAGrid().getTMACore(coreParent.getName());
				if (coreNewParent != null) {
					double rotation = activeViewer.getRotation();
					//						if (rotation == 0) {
					double dx = coreNewParent.getROI().getCentroidX() - coreParent.getROI().getCentroidX();
					double dy = coreNewParent.getROI().getCentroidY() - coreParent.getROI().getCentroidY();
					roi = roi.translate(dx, dy);
					// TODO: Deal with rotations... it's a bit tricky...
					//						} else {
					// TODO: Check how best to handle transferring ROIs with rotation involved
					if (rotation != 0) {
						AffineTransform transform = new AffineTransform();
						transform.rotate(-rotation, coreNewParent.getROI().getCentroidX(), coreNewParent.getROI().getCentroidY());
						logger.info("ROTATING: " + transform);
						Area area = RoiTools.getArea(roi);
						area.transform(transform);
						roi = RoiTools.getShapeROI(area, roi.getImagePlane());
					}
				}
			}
		}


		PathObject annotation = PathObjects.createAnnotationObject(roi, lastAnnotationObject.getPathClass());
		//			hierarchy.addPathObject(annotation, false);

		//			// Make sure any core parent is set
		hierarchy.addObjectBelowParent(coreNewParent, annotation, true);

		activeViewer.setSelectedObject(annotation);
		return true;
	}

	@Override
	public void viewerClosed(QuPathViewer viewer) {}

	
	
	private void setupViewer(final QuPathViewerPlus viewer) {
		
		viewer.getView().setFocusTraversable(true);
		
		// Update active viewer as required
		viewer.getView().focusedProperty().addListener((e, f, nowFocussed) -> {
			if (nowFocussed) {
				setActiveViewer(viewer);
			}
		});
		
		viewer.getView().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> viewer.getView().requestFocus());

		viewer.zoomToFitProperty().bind(zoomToFit);
		
		// Create popup menu
		setViewerPopupMenu(viewer);
		
		
		// Enable drag and drop
		qupath.getDefaultDragDropListener().setupTarget(viewer.getView());
		
		
		// Listen to the scroll wheel
		viewer.getView().setOnScroll(e -> {
			if (viewer == getActiveViewer() || !getSynchronizeViewers()) {
				double scrollUnits = e.getDeltaY() * PathPrefs.getScaledScrollSpeed();
				
				// Use shift down to adjust opacity
				if (e.isShortcutDown()) {
					OverlayOptions options = viewer.getOverlayOptions();
					options.setOpacity((float)(options.getOpacity() + scrollUnits * 0.001));
					return;
				}
				
				// Avoid zooming at the end of a gesture when using touchscreens
				if (e.isInertia())
					return;
				
				if (PathPrefs.invertScrollingProperty().get())
					scrollUnits = -scrollUnits;
				double newDownsampleFactor = viewer.getDownsampleFactor() * Math.pow(viewer.getDefaultZoomFactor(), scrollUnits);
				newDownsampleFactor = Math.min(viewer.getMaxDownsample(), Math.max(newDownsampleFactor, viewer.getMinDownsample()));
				viewer.setDownsampleFactor(newDownsampleFactor, e.getX(), e.getY());
			}
		});
		
		
		viewer.getView().addEventFilter(RotateEvent.ANY, e -> {
			if (!PathPrefs.useRotateGesturesProperty().get())
				return;
//			logger.debug("Rotating: " + e.getAngle());
			viewer.setRotation(viewer.getRotation() + Math.toRadians(e.getAngle()));
			e.consume();
		});

		viewer.getView().addEventFilter(ZoomEvent.ANY, e -> {
			if (!PathPrefs.useZoomGesturesProperty().get())
				return;
			double zoomFactor = e.getZoomFactor();
			if (Double.isNaN(zoomFactor))
				return;
			
			logger.debug("Zooming: " + e.getZoomFactor() + " (" + e.getTotalZoomFactor() + ")");
			viewer.setDownsampleFactor(viewer.getDownsampleFactor() / zoomFactor, e.getX(), e.getY());
			e.consume();
		});
		
		viewer.getView().addEventFilter(ScrollEvent.ANY, new ScrollEventPanningFilter(viewer));
		
		
		viewer.getView().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			PathObject pathObject = viewer.getSelectedObject();
			if (!e.isConsumed() && pathObject != null) {
				if (pathObject.isTMACore()) {
					TMACoreObject core = (TMACoreObject)pathObject;
					if (e.getCode() == KeyCode.ENTER) {
						qupath.getCommonActions().TMA_ADD_NOTE.handle(new ActionEvent(e.getSource(), e.getTarget()));
						e.consume();
					} else if (e.getCode() == KeyCode.BACK_SPACE) {
						core.setMissing(!core.isMissing());
						viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));
						e.consume();
					}
				} else if (pathObject.isAnnotation()) {
					if (e.getCode() == KeyCode.ENTER) {
						GuiTools.promptToSetActiveAnnotationProperties(viewer.getHierarchy());
						e.consume();
					}
				}
			}
		});
		

	}






	class SplitPaneGrid {

		private SplitPane splitPaneMain = new SplitPane();
		private List<SplitPane> splitPaneRows = new ArrayList<>();

		SplitPaneGrid(final Node node) {
			splitPaneMain.setOrientation(Orientation.VERTICAL);
			SplitPane splitRow = new SplitPane();
			splitRow.setOrientation(Orientation.HORIZONTAL);
			splitRow.getItems().add(node);
			splitPaneRows.add(splitRow);
			splitPaneMain.getItems().add(splitRow);
		}

		SplitPane getMainSplitPane() {
			return splitPaneMain;
		}


		void addRow(final int position) {
			SplitPane splitRow = new SplitPane();
			splitRow.setOrientation(Orientation.HORIZONTAL);

			// For now, we create a row with the same number of columns in every row
			// Create viewers & bind dividers
			splitRow.getItems().clear();
			SplitPane firstRow = splitPaneRows.get(0);
			splitRow.getItems().add(createViewer().getView());
			for (int i = 0; i < firstRow.getDividers().size(); i++) {
				splitRow.getItems().add(createViewer().getView());
			}

			// Ensure the new divider takes up half the space
			double lastDividerPosition = position == 0 ? 0 : splitPaneMain.getDividers().get(position-1).getPosition();
			double nextDividerPosition = position >= splitPaneRows.size()-1 ? 1 : splitPaneMain.getDividers().get(position).getPosition();
			splitPaneRows.add(position, splitRow);
			splitPaneMain.getItems().add(position+1, splitRow);
			splitPaneMain.setDividerPosition(position, (lastDividerPosition + nextDividerPosition)/2);

			refreshDividerBindings();
		}



		boolean removeRow(final int row) {
			if (row < 0 || row >= splitPaneRows.size() || splitPaneRows.size() == 1) {
				logger.error("Cannot remove row {} from grid with {} rows", row, splitPaneRows.size());
				return false;
			}
			SplitPane splitPane = splitPaneRows.remove(row);
			splitPaneMain.getItems().remove(splitPane);
			refreshDividerBindings();
			return true;
		}


		/**
		 * Restore all grid panels to be the same size
		 */
		public void resetGridSize() {
			resetDividers(splitPaneRows.get(0)); // Because of property binding, this should be enough
			resetDividers(splitPaneMain);
		}


		void resetDividers(final SplitPane splitPane) {
			int n = splitPane.getItems().size();
			if (n <= 1)
				return;
			if (n == 2) {
				splitPane.setDividerPosition(0, 0.5);
				return;
			}
			double[] positions = new double[n-1];
			for (int i = 0; i < positions.length; i++)
				positions[i] = (i + 1.0) / (double)n;
			splitPane.setDividerPositions(positions);
		}


		boolean removeColumn(final int col) {
			if (col < 0 || col >= nCols() || nCols() == 1) {
				logger.error("Cannot remove column {} from grid with {} columns", col, nCols());
				return false;
			}
			for (SplitPane splitRow : splitPaneRows) {
				splitRow.getItems().remove(col);
			}
			refreshDividerBindings();
			return true;
		}


		int countOpenViewersForRow(final int row) {
			int count = 0;
			for (QuPathViewer viewer : getAllViewers()) {
				if (row == getRow(viewer.getView()) && viewer.hasServer())
					count++;
			}
			return count;
		}


		int countOpenViewersForColumn(final int col) {
			int count = 0;
			for (QuPathViewer viewer : getAllViewers()) {
				if (col == getColumn(viewer.getView()) && viewer.hasServer())
					count++;
			}
			return count;				
		}


		/**
		 * Update all divider bindings so they match the first row
		 */
		void refreshDividerBindings() {
			SplitPane firstRow = splitPaneRows.get(0);
			for (int r = 1; r < splitPaneRows.size(); r++) {
				SplitPane splitRow = splitPaneRows.get(r);
				for (int c = 0; c < splitRow.getDividers().size(); c++) {
					splitRow.getDividers().get(c).positionProperty().bindBidirectional(firstRow.getDividers().get(c).positionProperty());
				}
			}
		}


		void addColumn(final int position) {
			SplitPane firstRow = splitPaneRows.get(0);
			double lastDividerPosition = position == 0 ? 0 : firstRow.getDividers().get(position-1).getPosition();
			double nextDividerPosition = position >= firstRow.getItems().size()-1 ? 1 : firstRow.getDividers().get(position).getPosition();

			firstRow.getItems().add(position+1, createViewer().getView());
			Divider firstDivider = firstRow.getDividers().get(position);
			firstDivider.setPosition((lastDividerPosition + nextDividerPosition)/2);
			for (int i = 1; i < splitPaneRows.size(); i++) {
				SplitPane splitRow = splitPaneRows.get(i);
				splitRow.getItems().add(position+1, createViewer().getView());
			}

			refreshDividerBindings();
		}


		public int getRow(final Node node) {
			int count = 0;
			for (SplitPane row : splitPaneRows) {
				int ind = row.getItems().indexOf(node);
				if (ind >= 0)
					return count;
				count++;
			}
			return -1;
		}

		public int getColumn(final Node node) {
			for (SplitPane row : splitPaneRows) {
				int ind = row.getItems().indexOf(node);
				if (ind >= 0)
					return ind;
			}
			return -1;
		}

		public int nRows() {
			return splitPaneRows.size();
		}

		public int nCols() {
			return splitPaneRows.get(0).getDividers().size() + 1;
		}

	}

	
	
	private void setViewerPopupMenu(final QuPathViewerPlus viewer) {
		
		final ContextMenu popup = new ContextMenu();
		
		var commonActions = qupath.getCommonActions();
		var viewerManagerActions = qupath.getViewerActions();
		
		MenuItem miAddRow = new MenuItem(QuPathResources.getString("Action.View.Multiview.addRow"));
		miAddRow.setOnAction(e -> addRow(viewer));
		MenuItem miAddColumn = new MenuItem(QuPathResources.getString("Action.View.Multiview.addColumn"));
		miAddColumn.setOnAction(e -> addColumn(viewer));
		
		MenuItem miRemoveRow = new MenuItem(QuPathResources.getString("Action.View.Multiview.removeRow"));
		miRemoveRow.setOnAction(e -> removeRow(viewer));
		MenuItem miRemoveColumn = new MenuItem(QuPathResources.getString("Action.View.Multiview.removeColumn"));
		miRemoveColumn.setOnAction(e -> removeColumn(viewer));

		MenuItem miCloseViewer = new MenuItem(QuPathResources.getString("Action.View.Multiview.closeViewer"));
		miCloseViewer.setOnAction(e -> qupath.closeViewer(viewer));
		MenuItem miResizeGrid = new MenuItem(QuPathResources.getString("Action.View.Multiview.resetGridSize"));
		miResizeGrid.setOnAction(e -> resetGridSize());
		
		MenuItem miToggleSync = ActionTools.createCheckMenuItem(viewerManagerActions.TOGGLE_SYNCHRONIZE_VIEWERS, null);
		MenuItem miMatchResolutions = ActionTools.createMenuItem(viewerManagerActions.MATCH_VIEWER_RESOLUTIONS);
		Menu menuMultiview = MenuTools.createMenu(
				"Menu.View.Multiview",
				miToggleSync,
				miMatchResolutions,
				miCloseViewer,
				null,
				miResizeGrid,
				null,
				miAddRow,
				miAddColumn,
				null,
				miRemoveRow,
				miRemoveColumn
				);
		
		Menu menuView = MenuTools.createMenu(
				"Display",
				ActionTools.createCheckMenuItem(commonActions.SHOW_ANALYSIS_PANE, null),
				commonActions.BRIGHTNESS_CONTRAST,
				null,
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 0.25), "400%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 1), "100%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 2), "50%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 10), "10%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 100), "1%")
				);
		
		// Hack to update the tools when we show this for the first time
		// This should catch tools added via extensions (even if it doesn't respond to tool list being changed later)
		Menu menuTools = MenuTools.createMenu("Set tool");

		
		// Handle awkward 'TMA core missing' option
		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		Menu menuTMA = MenuTools.createMenu(
				"Menu.TMA",
				miTMAValid,
				miTMAMissing,
				null,
				commonActions.TMA_ADD_NOTE,
				null,
				MenuTools.createMenu(
						"General.add",
					qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowBeforeSelected(imageData), "Add TMA row before"),
					qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowAfterSelected(imageData), "Add TMA row after"),
					qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnBeforeSelected(imageData), "Add TMA column before"),
					qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnAfterSelected(imageData), "Add TMA column after")
					),
				MenuTools.createMenu(
						"General.remove",
						qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridRow(imageData), "Remove TMA row"),
						qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridColumn(imageData), "column")
					)
				);
		
		
		// Create an empty placeholder menu
		Menu menuSetClass = MenuTools.createMenu("Set class");
		
		var overlayActions = qupath.getOverlayActions();
		Menu menuCells = MenuTools.createMenu(
				"General.objects.cells",
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_NUCLEI),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_BOUNDARIES),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_CENTROIDS)
				);

		
		
		MenuItem miClearSelectedObjects = new MenuItem(QuPathResources.getString("General.deleteObjects"));
		miClearSelectedObjects.setOnAction(e -> {
			PathObjectHierarchy hierarchy = viewer.getHierarchy();
			if (hierarchy == null)
				return;
			if (hierarchy.getSelectionModel().singleSelection()) {
				GuiTools.promptToRemoveSelectedObject(hierarchy.getSelectionModel().getSelectedObject(), hierarchy);
			} else {
				GuiTools.promptToClearAllSelectedObjects(viewer.getImageData());
			}
		});
		
		// Create a standard annotations menu
		Menu menuAnnotations = GuiTools.populateAnnotationsMenu(qupath, MenuTools.createMenu("General.objects.annotations"));
		
		SeparatorMenuItem topSeparator = new SeparatorMenuItem();
		popup.setOnShowing(e -> {
			// Check if we have any cells
			ImageData<?> imageData = viewer.getImageData();
			if (imageData == null)
				menuCells.setVisible(false);
			else
				menuCells.setVisible(!imageData.getHierarchy().getDetectionObjects().isEmpty());
			
			
			// Check what to show for TMA cores or annotations
			Collection<PathObject> selectedObjects = viewer.getAllSelectedObjects();
			PathObject pathObject = viewer.getSelectedObject();
			menuTMA.setVisible(false);
			if (pathObject instanceof TMACoreObject) {
				boolean isMissing = ((TMACoreObject)pathObject).isMissing();
				miTMAValid.setSelected(!isMissing);
				miTMAMissing.setSelected(isMissing);
				menuTMA.setVisible(true);
			}
			
			// Add clear objects option if we have more than one non-TMA object
			if (imageData == null || imageData.getHierarchy().getSelectionModel().noSelection() || imageData.getHierarchy().getSelectionModel().getSelectedObject() instanceof TMACoreObject)
				miClearSelectedObjects.setVisible(false);
			else {
				if (imageData.getHierarchy().getSelectionModel().singleSelection()) {
					miClearSelectedObjects.setText(QuPathResources.getString("General.deleteObject"));
					miClearSelectedObjects.setVisible(true);
				} else {
					miClearSelectedObjects.setText(QuPathResources.getString("General.deleteObjects"));
					miClearSelectedObjects.setVisible(true);					
				}
			}
			
			if (menuTools.getItems().isEmpty()) {
				menuTools.getItems().addAll(createToolMenu(qupath.getToolManager()));
			}
			
			boolean hasAnnotations = pathObject instanceof PathAnnotationObject || (!selectedObjects.isEmpty() && selectedObjects.stream().allMatch(p -> p.isAnnotation()));
			
			updateSetAnnotationPathClassMenu(menuSetClass, viewer);
			menuAnnotations.setVisible(hasAnnotations);
			topSeparator.setVisible(hasAnnotations || pathObject instanceof TMACoreObject);
			// Occasionally, the newly-visible top part of a popup menu can have the wrong size?
			popup.setWidth(popup.getPrefWidth());
		});
		
		popup.getItems().addAll(
				miClearSelectedObjects,
				menuTMA,
				menuSetClass,
				menuAnnotations,
				topSeparator,
				menuMultiview,
				menuCells,
				menuView,
				menuTools
				);
		
		popup.setAutoHide(true);
		
		// Enable circle pop-up for quick classification on right-click
		CirclePopupMenu circlePopup = new CirclePopupMenu(viewer.getView(), null);
		viewer.getView().addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
			if ((e.isPopupTrigger() || e.isSecondaryButtonDown()) && e.isShiftDown() && !qupath.getAvailablePathClasses().isEmpty()) {
				circlePopup.setAnimationDuration(Duration.millis(200));
				updateSetAnnotationPathClassMenu(circlePopup, viewer);
				circlePopup.show(e.getScreenX(), e.getScreenY());
				e.consume();
				return;
			} else if (circlePopup.isShown())
				circlePopup.hide();
				
			if (e.isPopupTrigger() || e.isSecondaryButtonDown()) {
				popup.show(viewer.getView().getScene().getWindow(), e.getScreenX(), e.getScreenY());				
				e.consume();
			}
		});
	}
	
	
	
	static List<MenuItem> createToolMenu(ToolManager toolManager) {
//		ToggleGroup groupTools = new ToggleGroup();
		List<MenuItem> items = new ArrayList<>();
		for (var tool : toolManager.getTools()) {
			var action = toolManager.getToolAction(tool);
			var mi = ActionTools.createCheckMenuItem(action);
			items.add(mi);
		}
		if (!items.isEmpty())
			items.add(new SeparatorMenuItem());
		items.add(ActionTools.createCheckMenuItem(toolManager.getSelectionModeAction()));
		return items;
	}
	
	
	
	
	/**
	 * Set selected TMA cores to have the specified 'missing' status.
	 * 
	 * @param hierarchy
	 * @param setToMissing
	 */
	private void setTMACoreMissing(final PathObjectHierarchy hierarchy, final boolean setToMissing) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		List<PathObject> changed = new ArrayList<>();
		if (pathObject instanceof TMACoreObject) {
			TMACoreObject core = (TMACoreObject)pathObject;
			core.setMissing(setToMissing);
			changed.add(core);
			// Update any other selected cores to have the same status
			for (PathObject pathObject2 : hierarchy.getSelectionModel().getSelectedObjects()) {
				if (pathObject2 instanceof TMACoreObject) {
					core = (TMACoreObject)pathObject2;
					if (core.isMissing() != setToMissing) {
						core.setMissing(setToMissing);
						changed.add(core);
					}
				}
			}
		}
		if (!changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(qupath, changed);
	}
	
	
	
	/**
	 * Update a 'set annotation class' menu for a viewer immediately prior to display
	 * 
	 * @param menuSetClass
	 * @param viewer
	 */
	private void updateSetAnnotationPathClassMenu(final Menu menuSetClass, final QuPathViewer viewer) {
		updateSetAnnotationPathClassMenu(menuSetClass.getItems(), viewer, false);
		menuSetClass.setVisible(!menuSetClass.getItems().isEmpty());
	}
	
	
	private void updateSetAnnotationPathClassMenu(final CirclePopupMenu menuSetClass, final QuPathViewer viewer) {
		updateSetAnnotationPathClassMenu(menuSetClass.getItems(), viewer, true);
	}

		
	private void updateSetAnnotationPathClassMenu(final ObservableList<MenuItem> menuSetClassItems, final QuPathViewer viewer, final boolean useFancyIcons) {
		// We need a viewer and an annotation, as well as some PathClasses, otherwise we just need to ensure the menu isn't visible
		var availablePathClasses = qupath.getAvailablePathClasses();
		if (viewer == null || !(viewer.getSelectedObject() instanceof PathAnnotationObject) || availablePathClasses.isEmpty()) {
			menuSetClassItems.clear();
			return;
		}
		
		int iconSize = QuPathGUI.TOOLBAR_ICON_SIZE;
		
		PathObject mainPathObject = viewer.getSelectedObject();
		PathClass currentClass = mainPathObject.getPathClass();
		
		ToggleGroup group = new ToggleGroup();
		List<MenuItem> itemList = new ArrayList<>();
		RadioMenuItem selected = null;
		for (PathClass pathClass : availablePathClasses) {
			PathClass pathClassToSet = pathClass.getName() == null ? null : pathClass;
			String name = pathClass.getName() == null ? "None" : pathClass.toString();
			Action actionSetClass = new Action(name, e -> {
				List<PathObject> changed = new ArrayList<>();
				for (PathObject pathObject : viewer.getAllSelectedObjects()) {
					if (!pathObject.isAnnotation() || pathObject.getPathClass() == pathClassToSet)
						continue;
					pathObject.setPathClass(pathClassToSet);
					changed.add(pathObject);
				}
				if (!changed.isEmpty())
					viewer.getHierarchy().fireObjectClassificationsChangedEvent(this, changed);				
			});
			Node shape;
			if (useFancyIcons) {
				Ellipse r = new Ellipse(iconSize/2.0, iconSize/2.0, iconSize, iconSize);
				if ("None".equals(name)) {
					r.setFill(Color.rgb(255, 255, 255, 0.75));
					
				}
				else
					r.setFill(ColorToolsFX.getCachedColor(pathClass.getColor()));
				r.setOpacity(0.8);
				DropShadow effect = new DropShadow(6, -3, 3, Color.GRAY);
				r.setEffect(effect);
				shape = r;
			} else {
				Rectangle r = new Rectangle(0, 0, 8, 8);
				r.setFill("None".equals(name) ? Color.TRANSPARENT : ColorToolsFX.getCachedColor(pathClass.getColor()));
				shape = r;
			}
//			actionSetClass.setGraphic(r);
			RadioMenuItem item = ActionUtils.createRadioMenuItem(actionSetClass);
			item.graphicProperty().unbind();
			item.setGraphic(shape);
			item.setToggleGroup(group);
			itemList.add(item);
			if (pathClassToSet == currentClass)
				selected = item;
		}
		group.selectToggle(selected);
		menuSetClassItems.setAll(itemList);
	}

}