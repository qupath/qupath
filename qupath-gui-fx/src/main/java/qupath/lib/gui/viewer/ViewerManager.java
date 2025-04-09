/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
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
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
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

	private ViewerPlusDisplayOptions viewerDisplayOptions = ViewerPlusDisplayOptions.getSharedInstance();
	private OverlayOptions overlayOptions = OverlayOptions.getSharedInstance();
	
	/**
	 * Since v0.5.0, this uses a Reference so that we can potentially allow garbage collection is memory is scare
	 * (and the annotation might otherwise be dragging in a whole object hierarchy).
	 */
	private Reference<PathObject> lastAnnotationObject = null;

	private final Color colorBorder = Color.rgb(180, 0, 0, 0.5);

	private BooleanProperty synchronizeViewers = PathPrefs.createPersistentPreference("synchronizeViewers", false);

	private Map<QuPathViewer, ViewerPosition> lastViewerPosition = new WeakHashMap<>();

	// Hacky solution to needing a mechanism to refresh the titles of detached viewers
	private BooleanProperty refreshTitleProperty = new SimpleBooleanProperty();

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
	 * Request that viewers refresh their titles.
	 * This is only really needed for detached viewers, so that they are notified of any changes to the image name.
	 */
	public void refreshTitles() {
		refreshTitleProperty.set(!refreshTitleProperty.get());
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
	 * Show z-projection overlay options, where relevant
	 * @return
	 */
	public BooleanProperty showZProjectControlsProperty() {
		return viewerDisplayOptions.showZProjectControlsProperty();
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
		boolean spaceDown = false;
		if (previousActiveViewer != null) {
			spaceDown = previousActiveViewer.isSpaceDown();
			previousActiveViewer.setSpaceDown(false);
			previousActiveViewer.setBorderColor(null);
			deactivateTools(previousActiveViewer);

			// Grab reference to the current annotation, if there is one
			PathObject pathObjectSelected = previousActiveViewer.getSelectedObject();
			if (pathObjectSelected instanceof PathAnnotationObject annotation) {
				updateLastAnnotation(annotation);
			}
		}
		this.activeViewerProperty.set(viewer);
		getLastViewerPosition(viewer).reset();

		if (viewer != null) {
			viewer.setBorderColor(colorBorder);
			if (viewer.getServer() != null) {
				getLastViewerPosition(viewer).update(viewer);
				if (spaceDown)
					viewer.setSpaceDown(true);
			}
		}
		logger.debug("Active viewer set to {}", viewer);
		imageDataProperty.set(imageDataNew);
	}

	/**
	 * Here, we try to help achieve similar 'transfer last ROI' behavior as ImageJ - but while also attempting not
	 * to leak memory by retaining large object hierarchies after an image has been closed.
	 * @param pathObject
	 */
	private void updateLastAnnotation(PathAnnotationObject pathObject) {
		// Don't store the original annotation, because it will drag in the entire object hierarchy
		// Instead, create a new annotation object that shares similar properties
		var temp = PathObjects.createAnnotationObject(pathObject.getROI(), pathObject.getPathClass());
		temp.setID(pathObject.getID());

		// We also need to handle TMA cores, since we may need to position the annotation within a TMA core
		// rather than entirely separately
		var core = PathObjectTools.getAncestorTMACore(pathObject);
		if (core != null) {
			var coreTemp = PathObjects.createTMACoreObject(
					core.getROI().getBoundsX(), core.getROI().getBoundsY(),
					core.getROI().getBoundsWidth(), core.getROI().getBoundsHeight(),
					core.isMissing(), core.getROI().getImagePlane()
			);
			coreTemp.setID(core.getID());
			coreTemp.setName(core.getName());
			coreTemp.addChildObject(temp); // Also assigns itself as the parent
			assert temp.getParent() == coreTemp;
		}

		lastAnnotationObject = new SoftReference<>(temp);
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
//			this.viewers.add(defaultViewer);
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
		QuPathViewerPlus viewerNew = new QuPathViewerPlus(qupath.getImageRegionStore(), overlayOptions, viewerDisplayOptions);
		setupViewer(viewerNew);
		viewerNew.addViewerListener(this);
		viewers.add(viewerNew);
		return viewerNew;
	}

	/**
	 * Try to remove the row containing the specified viewer, notifying the user if this isn't possible.
	 * @param viewer
	 * @return true if the row was removed, false otherwise
	 */
	public boolean removeRow(final QuPathViewer viewer) {
		// Note: These are the internal row numbers... these don't necessarily match with the displayed row (?)
		int row = splitPaneGrid.getRow(viewer);
		if (row < 0) {
			// Shouldn't occur...
			Dialogs.showErrorMessage("Multiview", "Cannot find " + viewer + " in the grid!");
			return false;
		}
		if (splitPaneGrid.nRows() == 1) {
			Dialogs.showWarningNotification("Close row", "The last row can't be removed!");
			return false;
		}

		int nOpen = splitPaneGrid.countOpenViewersForRow(row);
		if (nOpen > 0) {
			Dialogs.showWarningNotification("Close row", "Please close all open viewers in the selected row, then try again");
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
			var view = iter.next().getView();
			if (view.getScene() == null)
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
			Dialogs.showWarningNotification("Close column", "The last columns can't be removed!");
			return false;
		}

		int nOpen = splitPaneGrid.countOpenViewersForColumn(col);
		if (nOpen > 0) {
			Dialogs.showWarningNotification("Close column", "Please close all open viewers in selected column, then try again");
			//				DisplayHelpers.showErrorMessage("Close column error", "Please close all open viewers in column " + col + ", then try again");
			return false;
		}
		splitPaneGrid.removeColumn(col);
		splitPaneGrid.resetGridSize();
		// Make sure the viewer list is up-to-date
		refreshViewerList();
		return true;
	}


	/**
	 * Set the grid to have a specific number of rows and columns.
	 * @param nRows
	 * @param nCols
	 * @return
	 */
	public boolean setGridSize(int nRows, int nCols) {
		if (nRows < 1 || nCols < 1) {
			Dialogs.showErrorMessage("Multiview grid", "There must be at least one viewer in the grid!");
			return false;
		}
		// Easiest case - no resizing to do
		if (nRows == splitPaneGrid.nRows() && nCols == splitPaneGrid.nCols()) {
			logger.warn("Viewer grid is already {} x {} - nothing to change!", nRows, nCols);
			return true;
		}
		// Get all the open viewers currently in the grid & check it fits with what we want
		refreshViewerList();

		var openViewers = getAllViewers().stream().filter(
				v -> !splitPaneGrid.isDetached(v) && v.hasServer()).toList();
		if (openViewers.size() > nRows * nCols) {
			Dialogs.showWarningNotification("Multiview grid", "There are too many viewers open! Please close some, then set the grid size.");
			return false;
		}
		// Adding is easy too
		while (splitPaneGrid.nRows() < nRows)
			splitPaneGrid.addRow(splitPaneGrid.nRows());
		while (splitPaneGrid.nCols() < nCols)
			splitPaneGrid.addColumn(splitPaneGrid.nCols());
		if (nRows == splitPaneGrid.nRows() && nCols == splitPaneGrid.nCols()) {
			return true;
		}
		// Removing is trickier - we first need to move any open viewers to the top-left,
		// replacing any closed viewers we find there
		var activeViewer = getActiveViewer();
		var allViewers = getAllViewers();
		var closedViewers = allViewers.stream().filter(
				v -> !splitPaneGrid.isDetached(v)
						&& !v.hasServer()
						&& splitPaneGrid.getRow(v) < nRows
						&& splitPaneGrid.getColumn(v) < nCols)
						.collect(Collectors.toCollection(ArrayList::new));
		for (var viewer : openViewers) {
			var view = viewer.getView();
			int r = splitPaneGrid.getRow(view);
			int c = splitPaneGrid.getColumn(view);
			if (r >= nRows || c >= nCols) {
				// If the open viewer is outside the new grid, we need to move it
				var nextClosedViewer = closedViewers.remove(0);
				int rClosed = splitPaneGrid.getRow(nextClosedViewer);
				int cClosed = splitPaneGrid.getColumn(nextClosedViewer);
				// Add a dummy to the open viewer's position, to detach it from the scene
				splitPaneGrid.splitPaneRows.get(r).getItems().set(c, new BorderPane());
				// Set the open viewer to the closed viewer's position, to reattach it to the scene
				splitPaneGrid.splitPaneRows.get(rClosed).getItems().set(cClosed, view);
				// Remove the closed viewer from the list of all viewers
				viewers.remove(nextClosedViewer);
			}
		}
		while (splitPaneGrid.nRows() > nRows) {
			splitPaneGrid.removeRow(splitPaneGrid.nRows()-1);
		}
		while (splitPaneGrid.nCols() > nCols) {
			splitPaneGrid.removeColumn(splitPaneGrid.nCols()-1);
		}
		refreshViewerList();
		if (activeViewer != null && viewers.contains(activeViewer))
			setActiveViewer(activeViewer);
		else if (!viewers.isEmpty())
			setActiveViewer(viewers.get(0));
		else
			logger.warn("No viewers remaining, cannot set active viewer");

		// Distribute the divider positions
		resetGridSize();
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
			splitPaneGrid.addColumn(splitPaneGrid.getColumn(viewer.getView())+1);
		} else {
			splitPaneGrid.addRow(splitPaneGrid.getRow(viewer.getView())+1);
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
				getLastViewerPosition(viewer).reset();
			}
			imageDataProperty.set(viewer.getImageData());
		}
	}

	private ViewerPosition getLastViewerPosition(QuPathViewer viewer) {
		return lastViewerPosition.computeIfAbsent(viewer, v -> new ViewerPosition());
	}
	
	
	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
		if (viewer == null)
			return;
		if (viewer != getActiveViewer() || viewer.isImageDataChanging()) {
			return;
		}

		QuPathViewer activeViewer = getActiveViewer();


		double x = activeViewer.getCenterPixelX();
		double y = activeViewer.getCenterPixelY();
		double rotation = activeViewer.getRotation();
		double dx = Double.NaN, dy = Double.NaN, dr = Double.NaN;
		int dt = 0, dz = 0;

		double downsample = viewer.getDownsampleFactor();
		var position = getLastViewerPosition(activeViewer);
		double relativeDownsample = viewer.getDownsampleFactor() / position.downsample;

		// Shift as required, assuming we aren't aligning cores
		//			if (!aligningCores) {
		//			synchronizeViewers = true;
		if (synchronizeViewers.get()) {
			if (!Double.isNaN( position.x + position.y)) {
				dx = x - position.x;
				dy = y - position.y;
				dr = rotation - position.rotation;
				dt = activeViewer.getTPosition() - position.t;
				dz = activeViewer.getZPosition() - position.z;
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

					// Handle z and t
					if (dz != 0) {
						v.setZPosition(GeneralTools.clipValue(v.getZPosition() + dz, 0, v.getServer().nZSlices()-1));
					}
					if (dt != 0) {
						v.setTPosition(GeneralTools.clipValue(v.getTPosition() + dt, 0, v.getServer().nTimepoints()-1));
					}

				}
			}
		}

		position.update(activeViewer);
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
		if (pathObjectSelected instanceof PathAnnotationObject annotation) {
			updateLastAnnotation(annotation);
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
		getLastViewerPosition(getActiveViewer()).reset();

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
		var lastAnnotation = this.lastAnnotationObject.get();
		if (lastAnnotation == null) {
			logger.info("No annotation object to copy");
			return false;
		}

		QuPathViewer activeViewer = getActiveViewer();
		if (activeViewer == null || activeViewer.getHierarchy() == null) {
			logger.info("No active viewer available");
			return false;
		}

		// We can't just use a simple 'contains' because we need work with duplicate annotations -
		// so we need to check the ID as well
		PathObjectHierarchy hierarchy = activeViewer.getHierarchy();
		if (PathObjectTools.hierarchyContainsObject(hierarchy, lastAnnotation) ||
				hierarchy.getAnnotationObjects().stream().anyMatch(a -> a.getID().equals(lastAnnotation.getID()))) {
			logger.info("Hierarchy already contains the annotation object!");
			return false;
		}

		ROI roi = lastAnnotation.getROI().duplicate();

		// If we are within a TMA core, try to apply any required translations
		TMACoreObject coreNewParent = null;
		if (hierarchy.getTMAGrid() != null) {
			TMACoreObject coreParent = PathObjectTools.getAncestorTMACore(lastAnnotation);
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


		PathObject annotation = PathObjects.createAnnotationObject(roi, lastAnnotation.getPathClass());
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

		// Create popup menu
		setViewerPopupMenu(viewer);
		
		
		// Enable drag and drop
		qupath.getDefaultDragDropListener().setupTarget(viewer.getView());
		
		
		// Listen to the scroll wheel
		viewer.getView().setOnScroll(e -> {
			if (viewer == getActiveViewer() || !getSynchronizeViewers()) {
				if (!viewer.hasServer())
					return;
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
			if (e.isConsumed())
				return;
			PathObject pathObject = viewer.getSelectedObject();
			if (pathObject != null) {
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
			// For temporarily setting selection mode, we want to grab any S key presses eagerly
			if (e.getCode() == KeyCode.S && e.getEventType() == KeyEvent.KEY_PRESSED) {
				PathPrefs.tempSelectionModeProperty().set(true);
				// Don't consume the event! Doing so can break the 'Save' accelerators
			}
		});
		viewer.getView().addEventHandler(KeyEvent.KEY_RELEASED, e -> {
			// For temporarily setting selection mode, we want to switch off the mode quickly for any key release events -
			// to reduce the risk of accidentally leaving selection mode 'stuck' on if the S key release is missed
			PathPrefs.tempSelectionModeProperty().set(false);
		});

	}

	/**
	 * Detach the currently active viewer from the viewer grid, if possible.
	 */
	public void detachActiveViewerFromGrid() {
		detachViewerFromGrid(getActiveViewer());
	}

	/**
	 * Insert the currently active viewer back into the viewer grid.
	 * @see #attachViewerToGrid(QuPathViewer)
	 */
	public void attachActiveViewerToGrid() {
		attachViewerToGrid(getActiveViewer());
	}


	/**
	 * Detach the specified viewer from the viewer grid, if possible.
	 * This will remove the viewer from the grid, and create a new window to contain it.
	 * @param viewer
	 * @see #detachViewerFromGrid(QuPathViewer)
	 */
	public void detachViewerFromGrid(QuPathViewer viewer) {
		if (viewer == null)
			Dialogs.showWarningNotification("Attach viewer", "Viewer is null - cannot detach from the viewer grid");
		else if (splitPaneGrid.isDetached(viewer))
			Dialogs.showWarningNotification("Attach viewer", "Viewer is already detached from the viewer grid");
		else
			splitPaneGrid.detachViewer(viewer);
	}

	/**
	 * Attach the specified viewer to the viewer grid, if possible.
	 * It will be inserted in place of the first available empty viewer slot.
	 * If no empty slots are available, an error will be shown.
	 * @param viewer
	 */
	public void attachViewerToGrid(QuPathViewer viewer) {
		if (viewer == null)
			Dialogs.showWarningNotification("Attach viewer", "Viewer is null - cannot attach to the viewer grid");
		else if (splitPaneGrid.isDetached(viewer))
			splitPaneGrid.attachViewer(viewer);
		else
			Dialogs.showWarningNotification("Attach viewer", "Viewer can't be added to the viewer grid");
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
			// The new row we will add
			SplitPane newRow = new SplitPane();
			newRow.setOrientation(Orientation.HORIZONTAL);

			// For now, we create a row with the same number of columns in every row
			// Create viewers & bind dividers
			newRow.getItems().clear();
			SplitPane firstRow = splitPaneRows.get(0);
			for (int i = 0; i < firstRow.getItems().size(); i++) {
				newRow.getItems().add(createViewer().getView());
			}

			// Insert the row
			splitPaneRows.add(position, newRow);
			splitPaneMain.getItems().add(position, newRow);

			// Redistribute the positions & ensure column dividers bind to the first row
			resetDividers(splitPaneMain);
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
			// Add a new column at the same position to each row
			for (var splitRow : splitPaneRows) {
				splitRow.getItems().add(position, createViewer().getView());
			}

			// Redistribute the positions & ensure column dividers bind to the first row
			resetDividers(splitPaneRows.get(0));
			refreshDividerBindings();
		}


		public int getRow(final QuPathViewer viewer) {
			return getRow(viewer.getView());
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

		public int getColumn(final QuPathViewer viewer) {
			return getColumn(viewer.getView());
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


		public boolean isDetached(QuPathViewer viewer) {
			return getRow(viewer) < 0;
		}

		public boolean attachViewer(QuPathViewer viewer) {
			var closedViewer = getAllViewers()
					.stream()
					.filter(v -> !v.hasServer() && !isDetached(v))
					.sorted(Comparator.comparingInt((QuPathViewer vv) -> getRow(vv)).thenComparing(vv -> getColumn(vv)))
					.findFirst()
					.orElse(null);
			if (closedViewer == null) {
				Dialogs.showErrorMessage("Attach viewer", "Cannot attach viewer - " +
						"please close an existing viewer in the grid first");
				return false;
			}
			double[] positions = splitPaneRows.get(0).getDividerPositions();
			var row = getRow(closedViewer);
			int col = getColumn(closedViewer);
			var stage = FXUtils.getWindow(viewer.getView());
			stage.hide();
			stage.getScene().setRoot(new BorderPane());
			stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
			splitPaneRows.get(row).getItems().set(col, viewer.getView());
			refreshViewerList();
			refreshDividerBindings();
			setActiveViewer(viewer);
			splitPaneRows.get(0).setDividerPositions(positions);
			return true;
		}



		public boolean detachViewer(QuPathViewer viewer) {
			int row = getRow(viewer.getView());
			int col = getColumn(viewer.getView());
			if (row >= 0 && col >= 0) {
				double[] positions = splitPaneRows.get(0).getDividerPositions();
				SplitPane splitRow = splitPaneRows.get(row);
				splitRow.getItems().set(col, createViewer().getView());
				refreshDividerBindings();
				var stage = new Stage();
				FXUtils.addCloseWindowShortcuts(stage);
				var pane = new BorderPane(viewer.getView());
				var scene = new Scene(pane);
				stage.setScene(scene);
				stage.initOwner(qupath.getStage());
				stage.titleProperty().bind(createDetachedViewerTitleBinding(viewer));
				// It's messy... but we need to propagate key presses to the main window somehow,
				// otherwise the viewer is non-responsive to key presses
				stage.addEventFilter(KeyEvent.ANY, this::keyEventFilter);
				stage.addEventFilter(MouseEvent.ANY, this::mouseEventFilter);
				stage.addEventHandler(KeyEvent.ANY, this::propagateKeyEventToMainWindow);
				stage.setOnCloseRequest(e -> {
					if (FXUtils.getWindow(viewer.getView()) == null) {
						logger.debug("Closing stage after viewer has been removed");
						return;
					}

					if (viewers.size() == 1 && viewers.contains(viewer)) {
						// This shouldn't occur if we always replace detached viewers
						logger.error("The last viewer can't be closed!");
						e.consume();
						return;
					}
					if (qupath.closeViewer(viewer)) {
						// Ensure we have an active viewer
						// (If there isn't one, something has gone badly wrong)
						var allOtherViewers = new ArrayList<>(getAllViewers());
						allOtherViewers.remove(viewer);
						if (!allOtherViewers.isEmpty())
							setActiveViewer(allOtherViewers.get(0));
						stage.close();
						pane.getChildren().clear();
						refreshViewerList();
					}
					e.consume();
				});
				stage.show();
				refreshViewerList();
				splitPaneRows.get(0).setDividerPositions(positions);
				return true;
			} else {
				logger.warn("Viewer is already detached!");
				return false;
			}
		}

		private StringBinding createDetachedViewerTitleBinding(QuPathViewer viewer) {
			return Bindings.createStringBinding(() -> {
				return qupath.getDisplayedImageName(viewer.getImageData());
			}, viewer.imageDataProperty(), PathPrefs.maskImageNamesProperty(), qupath.projectProperty(), refreshTitleProperty);
		}

		private void keyEventFilter(KeyEvent e) {
			if (qupath.uiBlocked().getValue())
				e.consume();
		}

		private void mouseEventFilter(MouseEvent e) {
			if (qupath.uiBlocked().getValue())
				e.consume();
		}

		private void propagateKeyEventToMainWindow(KeyEvent e) {
			if (!e.isConsumed()) {
				if (e.getEventType() == KeyEvent.KEY_RELEASED) {
					var handler = qupath.getStage().getScene().getOnKeyReleased();
					if (handler != null)
						handler.handle(e);
				}
			}
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

		var menuGrid = MenuTools.createMenu(
				"Action.View.Multiview.gridMenu",
				viewerManagerActions.VIEWER_GRID_1x1,
				viewerManagerActions.VIEWER_GRID_1x2,
				viewerManagerActions.VIEWER_GRID_2x1,
				viewerManagerActions.VIEWER_GRID_2x2,
				viewerManagerActions.VIEWER_GRID_3x3,
				null,
				miAddRow,
				miAddColumn,
				null,
				miRemoveRow,
				miRemoveColumn,
				null,
				miResizeGrid
		);

		var miDetachViewer = ActionTools.createMenuItem(viewerManagerActions.DETACH_VIEWER);
		var miAttachViewer = ActionTools.createMenuItem(viewerManagerActions.ATTACH_VIEWER);

		MenuItem miToggleSync = ActionTools.createCheckMenuItem(viewerManagerActions.TOGGLE_SYNCHRONIZE_VIEWERS, null);
		MenuItem miMatchResolutions = ActionTools.createMenuItem(viewerManagerActions.MATCH_VIEWER_RESOLUTIONS);
		Menu menuMultiview = MenuTools.createMenu(
				"Menu.View.Multiview",
				menuGrid,
				null,
				miToggleSync,
				miMatchResolutions,
				null,
				miCloseViewer,
				null,
				miDetachViewer,
				miAttachViewer
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
		Menu menuSetClass = MenuTools.createMenu("Set classification");
		
		var overlayActions = qupath.getOverlayActions();
		Menu menuCells = MenuTools.createMenu(
				"General.objects.cells",
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_NUCLEI),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_BOUNDARIES),
				ActionTools.createCheckMenuItem(overlayActions.SHOW_CELL_CENTROIDS)
				);

		
		
		MenuItem miRemoveSelectedObjects = new MenuItem(QuPathResources.getString("General.deleteObjects"));
		miRemoveSelectedObjects.setOnAction(e -> {
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
			if (pathObject instanceof TMACoreObject core) {
				boolean isMissing = core.isMissing();
				miTMAValid.setSelected(!isMissing);
				miTMAMissing.setSelected(isMissing);
				menuTMA.setVisible(true);
			}
			
			// Add clear objects option if we have more than one non-TMA object
			if (imageData == null || imageData.getHierarchy().getSelectionModel().noSelection() || imageData.getHierarchy().getSelectionModel().getSelectedObject() instanceof TMACoreObject)
				miRemoveSelectedObjects.setVisible(false);
			else {
				if (imageData.getHierarchy().getSelectionModel().singleSelection()) {
					miRemoveSelectedObjects.setText(QuPathResources.getString("General.deleteObject"));
					miRemoveSelectedObjects.setVisible(true);
				} else {
					miRemoveSelectedObjects.setText(QuPathResources.getString("General.deleteObjects"));
					miRemoveSelectedObjects.setVisible(true);
				}
			}
			
			if (menuTools.getItems().isEmpty()) {
				menuTools.getItems().addAll(createToolMenu(qupath.getToolManager()));
			}
			
			boolean hasAnnotations = pathObject instanceof PathAnnotationObject ||
					(!selectedObjects.isEmpty() && selectedObjects.stream().allMatch(PathObject::isAnnotation));
			
			updateSetAnnotationPathClassMenu(menuSetClass, viewer);
			menuAnnotations.setVisible(hasAnnotations);
			topSeparator.setVisible(hasAnnotations || pathObject instanceof TMACoreObject);
			// Occasionally, the newly-visible top part of a popup menu can have the wrong size?
			popup.setWidth(popup.getPrefWidth());

			if (viewer == null || splitPaneGrid == null) {
				miDetachViewer.setVisible(false);
				miAttachViewer.setVisible(false);
			} else if (splitPaneGrid.isDetached(viewer)) {
				miDetachViewer.setVisible(false);
				miAttachViewer.setVisible(true);
			} else {
				miDetachViewer.setVisible(true);
				miAttachViewer.setVisible(false);
			}
		});

		popup.getItems().addAll(
				miRemoveSelectedObjects,
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
		if (viewer == null || availablePathClasses.isEmpty() || viewer.getSelectedObject() == null ||
			!(viewer.getSelectedObject().isAnnotation() || viewer.getSelectedObject().isTMACore())) {
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
					if (pathObject.getPathClass() == pathClassToSet)
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
			item.setMnemonicParsing(false); // Fix display of underscores in classification names
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


	private static class ViewerPosition {

		private double x;
		private double y;
		private double downsample;
		private double rotation;
		private int z;
		private int t;

		private ViewerPosition() {
			reset();
		}

		private ViewerPosition(QuPathViewer viewer) {
			update(viewer);
		}

		private void update(QuPathViewer viewer) {
			if (viewer == null || viewer.getImageData() == null) {
				reset();
			} else {
				x = viewer.getCenterPixelX();
				y = viewer.getCenterPixelY();
				downsample = viewer.getDownsampleFactor();
				rotation = viewer.getRotation();
				z = viewer.getZPosition();
				t = viewer.getTPosition();
			}
		}

		private void reset() {
			x = Double.NaN;
			y = Double.NaN;
			downsample = Double.NaN;
			rotation = Double.NaN;
			z = -1;
			t = -1;
		}

	}


}
