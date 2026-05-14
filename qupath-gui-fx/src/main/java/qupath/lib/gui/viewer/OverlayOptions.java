/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MeasurementMapper;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Default class for storing overlay display options.
 * 
 * @author Pete Bankhead
 * 
 */
public class OverlayOptions {

	/**
	 * Visibility modes for classes.
	 * This refers to how {@link #selectedClassesProperty()} and {@link #useExactSelectedClassesProperty()}
	 * are interpreted.
	 */
	public enum ClassVisibilityMode {
		/**
		 * Hide classes that are included in the selected class collection.
		 * Show all other classes.
		 */
		HIDE_SELECTED,
		/**
		 * Show classes that are included in the selected class collection.
		 * Hide all other classes.
		 */
		SHOW_SELECTED
	}
	
	/**
	 * Display modes for cells and other detections.
	 */
	public enum DetectionDisplayMode {
		/**
		 * Show only cell boundaries.
		 */
		BOUNDARIES_ONLY,
		/**
		 * Show only cell nuclei.
		 */
		NUCLEI_ONLY,
		/**
		 * Show both cell boundaries and cell nuclei, where available.
		 */
		NUCLEI_AND_BOUNDARIES,
		/**
		 * Show only detection centroids, not boundaries.
		 */
		CENTROIDS
		};
	
	private final ObjectProperty<MeasurementMapper> measurementMapper = new SimpleObjectProperty<>();
	private final BooleanProperty showAnnotations = new SimpleBooleanProperty(null, "showAnnotations", true);
	private final BooleanProperty showNames = new SimpleBooleanProperty(null, "showAnnotationNames", true);
	private final BooleanProperty showTMAGrid = new SimpleBooleanProperty(null, "showTMAGrid", true);
	private final BooleanProperty showDetections = new SimpleBooleanProperty(null, "showDetections", true);
	private final BooleanProperty showConnections = new SimpleBooleanProperty(null, "showConnections", false);
	private final BooleanProperty fillDetections = new SimpleBooleanProperty(null, "fillDetections", false);
	private final BooleanProperty fillAnnotations = new SimpleBooleanProperty(null, "fillAnnotations", false);
	private final BooleanProperty showTMACoreLabels = new SimpleBooleanProperty(null, "showTMACoreLabels", false);
	private final BooleanProperty showGrid = new SimpleBooleanProperty(null, "showGrid", false);
	private final ObjectProperty<GridLines> gridLines = new SimpleObjectProperty<>(null, "showGridLines", new GridLines());

	private final BooleanProperty showPixelClassification = new SimpleBooleanProperty(null, "showPixelClassification", true);
	private final ObjectProperty<RegionFilter> pixelClassificationFilter = new SimpleObjectProperty<>(null, "pixelClassificationFilter", RegionFilter.StandardRegionFilters.EVERYWHERE);

	private final FloatProperty fontSize = new SimpleFloatProperty();

	private final ObservableSet<PathClass> selectedClasses = FXCollections.observableSet();
	private final ObjectProperty<ClassVisibilityMode> selectedClassVisibilityMode = new SimpleObjectProperty<>(ClassVisibilityMode.HIDE_SELECTED);
	private final BooleanProperty useExactSelectedClasses = new SimpleBooleanProperty(null, "useExactSelectedClasses", false);

	private final ObjectProperty<DetectionDisplayMode> cellDisplayMode = new SimpleObjectProperty<>(null, "cellDisplayMode", DetectionDisplayMode.NUCLEI_AND_BOUNDARIES);

	private final ObjectProperty<Predicate<PathObject>> showObjectPredicate = new SimpleObjectProperty<>(null, "showObjectPredicate");

	private final FloatProperty opacity = new SimpleFloatProperty(1.0f);
	
	private final LongProperty timestamp = new SimpleLongProperty(System.nanoTime());

	private static final OverlayOptions SHARED_INSTANCE = createSharedInstance();

	/**
	 * Create a shared instance that makes <i>some</i> properties persistent.
	 * Not all can/should be persistent, e.g. because a confused user who has hidden annotations will be relieved
	 * to find them back when they restart QuPath.
	 * @return
	 */
	private static OverlayOptions createSharedInstance() {
		var options = new OverlayOptions();
		for (var prop : Arrays.asList(options.showNames, options.showConnections, options.fillDetections,
				options.fillAnnotations, options.showTMACoreLabels,
				options.showGrid, options.showAnnotations, options.showDetections,
				options.showPixelClassification, options.showTMAGrid,
				options.useExactSelectedClasses)) {
			prop.bindBidirectional(PathPrefs.createPersistentPreference("overlayOptions_" + prop.getName(), prop.get()));
		}
		options.selectedClassVisibilityMode.bindBidirectional(PathPrefs.createPersistentPreference("overlayOptions_selectedClassVisibilityMode", options.selectedClassVisibilityMode.get(), ClassVisibilityMode.class));
		options.cellDisplayMode.bindBidirectional(PathPrefs.createPersistentPreference("overlayOptions_cellDisplayMode", options.cellDisplayMode.get(), DetectionDisplayMode.class));
		options.fontSize.bindBidirectional(PathPrefs.createPersistentPreference("overlayOptions_fontSize", options.fontSize.get()));
		return options;
	}

	/**
	 * Get a shared OverlayOptions instance that makes some properties persistent.
	 * @return
	 */
	public static OverlayOptions getSharedInstance() {
		return SHARED_INSTANCE;
	}
    
	/**
	 * Constructor, using default values.
	 */
	public OverlayOptions() {
		InvalidationListener timestamper = (var e) -> updateTimestamp();
		showAnnotations.addListener(timestamper);
		showNames.addListener(timestamper);
		showTMAGrid.addListener(timestamper);
		showPixelClassification.addListener(timestamper);
		showDetections.addListener(timestamper);
		showConnections.addListener(timestamper);
		fillDetections.addListener(timestamper);
		fillAnnotations.addListener(timestamper);
		showTMACoreLabels.addListener(timestamper);
		showGrid.addListener(timestamper);
		gridLines.addListener(timestamper);
		selectedClasses.addListener(timestamper);
		cellDisplayMode.addListener(timestamper);
		opacity.addListener(timestamper);
		fontSize.addListener(timestamper);
		useExactSelectedClasses.addListener(timestamper);
		selectedClassVisibilityMode.addListener(timestamper);
	}
	
	/**
	 * Constructor, initializing values based on an existing {@link OverlayOptions} object.
	 * @param options 
	 */
	public OverlayOptions(OverlayOptions options) {
		this();
		this.cellDisplayMode.set(options.cellDisplayMode.get());
		this.fillAnnotations.set(options.fillAnnotations.get());
		this.fillDetections.set(options.fillDetections.get());
		this.gridLines.set(options.gridLines.get());
		this.selectedClasses.addAll(options.selectedClasses);
		this.showObjectPredicateProperty().set(options.showObjectPredicateProperty().get());
		this.selectedClassVisibilityMode.set(options.selectedClassVisibilityModeProperty().get());
		this.useExactSelectedClasses.set(options.useExactSelectedClasses.get());
		this.measurementMapper.set(options.measurementMapper.get());
		this.opacity.set(options.opacity.get());
		this.showAnnotations.set(options.showAnnotations.get());
		this.showNames.set(options.showNames.get());
		this.showConnections.set(options.showConnections.get());
		this.showDetections.set(options.showDetections.get());
		this.showGrid.set(options.showGrid.get());
		this.showPixelClassification.set(options.showPixelClassification.get());
		this.showTMACoreLabels.set(options.showTMACoreLabels.get());
		this.showTMAGrid.set(options.showTMAGrid.get());
		this.pixelClassificationFilter.set(options.pixelClassificationFilter.get());
		this.fontSize.set(options.fontSize.get());
		this.timestamp.set(options.timestamp.get());
	}
	
	private void updateTimestamp() {
		this.timestamp.set(System.nanoTime());
	}
	
	/**
	 * Get a property representing the timestamp of the last recorded change for any property.
	 * @return
	 */
	public ReadOnlyLongProperty lastChangeTimestamp() {
		return timestamp;
	}
    
	/**
	 * Set global opacity for overlay drawing.
	 * Individual overlays may have their own opacity settings, but these should be concatenated with the global opacity.
	 * @param opacity opacity value between 0 (completely transparent) and 1 (completely opaque).
	 */
    public void setOpacity(float opacity) {
    	opacity = opacity < 0 ? 0 : (opacity > 1 ? 1 : opacity);
    	this.opacity.set(opacity);
    }
    
    /**
     * Get the global opacity for overlay drawing.
     * @return opacity value between 0 (completely transparent) and 1 (completely opaque).
     */
    public float getOpacity() {
    	return opacity.get();
    }
    
    /**
	 * Property representing the global opacity for overlay drawing.
     * @return opacity property, which should accept values between 0 and 1.
	 */
    public FloatProperty opacityProperty() {
    	return opacity;
    }
    
    /**
     * Get the current {@link DetectionDisplayMode}.
     * @return the current display mode
     */
    public DetectionDisplayMode getDetectionDisplayMode() {
    	return cellDisplayMode.get();
    }
    
    /**
     * Query the current {@link DetectionDisplayMode} to see if nuclei ROIs should be drawn.
     * @return true if nuclei should be drawn, false otherwise
     */
    public boolean getShowCellNuclei() {
    	return cellDisplayMode.get() == DetectionDisplayMode.NUCLEI_AND_BOUNDARIES || cellDisplayMode.get() == DetectionDisplayMode.NUCLEI_ONLY;
    }

    /**
     * Query the current {@link DetectionDisplayMode} to see if cell boundary ROIs should be drawn.
     * @return true if nuclei should be drawn, false otherwise
     */
    public boolean getShowCellBoundaries() {
    	return cellDisplayMode.get() == DetectionDisplayMode.NUCLEI_AND_BOUNDARIES || cellDisplayMode.get() == DetectionDisplayMode.BOUNDARIES_ONLY;
    }
    
    
    /**
     * Set the current {@link DetectionDisplayMode}.
     * @param mode the requested mode to set
     */
    public void setDetectionDisplayMode(DetectionDisplayMode mode) {
    	this.cellDisplayMode.set(mode);
    }
    
    /**
     * Property representing the current {@link DetectionDisplayMode}.
     * @return
     */
    public ObjectProperty<DetectionDisplayMode> detectionDisplayModeProperty() {
    	return cellDisplayMode;
    }
    
    /**
     * Get the requested stroke thickness to use when drawing ROIs that should be represented with 'thick' lines (annotations, TMA cores).
     * 
     * @param downsample downsample at which the annotations should be drawn
     * @return preferred stroke thickness to use
     */
    public float getThickStrokeWidth(double downsample) {
    	return (float)(PathPrefs.annotationStrokeThicknessProperty().get() * Math.max(1, downsample));
    }
    
    
    
    /**
	 * Show the TMA grid on the image, if present.
	 * 
	 * @param show
	 */
	public void setShowTMAGrid(boolean show) {
		this.showTMAGrid.set(show);
	}
	
	/**
	 * Show the annotations on the image.
	 * 
	 * @param show
	 */
	public void setShowAnnotations(boolean show) {
		this.showAnnotations.set(show);
	}
	
	/**
	 * Show the object names on the image.
	 * 
	 * @param show
	 */
	public void setShowNames(boolean show) {
		this.showNames.set(show);
	}
	
	/**
	 * Set the requested font size for the 'Show names' option
	 * @param size 
	 */
	public void setFontSize(float size) {
		fontSize.set(size);
	}
	
	/**
	 * Show the objects as an overlay on the image.
	 * 
	 * @param show
	 */
	public void setShowDetections(boolean show) {
		this.showDetections.set(show);
	}
	
	/**
	 * Show pixel classification overlays.
	 * 
	 * @param show
	 */
	public void setShowPixelClassification(boolean show) {
		this.showPixelClassification.set(show);
	}
	
	/**
	 * Show detection objects 'filled' in viewers.
	 * 
	 * @param fill
	 */
	public void setFillDetections(boolean fill) {
		this.fillDetections.set(fill);
	}
	
	/**
	 * Show annotation objects 'filled' in viewers.
	 * 
	 * @param fill
	 */
	public void setFillAnnotations(boolean fill) {
		fillAnnotations.set(fill);
	}
	
	/**
	 * Show connections between objects, if available.
	 * 
	 * @param show
	 */
	public void setShowConnections(boolean show) {
		showConnections.set(show);
	}
	
	/**
	 * Show TMA core names on top of the image.
	 * 
	 * @param showTMALabels
	 */
	public void setShowTMACoreLabels(boolean showTMALabels) {
		showTMACoreLabels.set(showTMALabels);
	}
	
	/**
	 * @return true if TMA core labels should be shown in viewers, false otherwise
	 */
	public boolean getShowTMACoreLabels() {
		return showTMACoreLabels.get();
	}
	
	/**
	 * @return boolean property indicating whether TMA core labels should be shown in the viewer
	 */
	public BooleanProperty showTMACoreLabelsProperty() {
		return showTMACoreLabels;
	}
	
	/**
	 * @return boolean property indicating whether collections between objects should be shown (e.g. after Delaunay triangulation)
	 */
	public BooleanProperty showConnectionsProperty() {
		return showConnections;
	}
	
	/**
	 * @return true if the current active pixel classification should be shown, false otherwise
	 */
	public boolean getShowPixelClassification() {
		return showPixelClassification.get();
	}

	/**
	 * @return true if annotations should be displayed in viewers, false otherwise
	 */
	public boolean getShowAnnotations() {
		return showAnnotations.get();
	}
	
	/**
	 * @return true if annotation names should be displayed in viewers, false otherwise
	 */
	public boolean getShowNames() {
		return showNames.get();
	}
	
	/**
	 * @return the requested font size for showing annotation names on the viewer
	 */
	public float getFontSize() {
		return fontSize.get();
	}
	
	/**
	 * @return true if any TMA grids should be displayed in viewers, false otherwise
	 */
	public boolean getShowTMAGrid() {
		return showTMAGrid.get();
	}
	
	/**
	 * @return true if detections should be displayed in viewers, false otherwise
	 */
	public boolean getShowDetections() {
		return showDetections.get();
	}
	
	/**
	 * @return true if any calculated connections between objects should be displayed in viewers, false otherwise
	 */
	public boolean getShowConnections() {
		return showConnections.get();
	}
	
	/**
	 * @return true if detections should be displayed 'filled' in viewers, false otherwise
	 */
	public boolean getFillDetections() {
		return fillDetections.get();
	}
	
	/**
	 * @return true if annotations should be displayed 'filled' in viewers, false otherwise
	 */
	public boolean getFillAnnotations() {
		return fillAnnotations.get();
	}
	
	/**
	 * Set whether a counting grid should be shown in viewers
	 * @param showGrid
	 * @see #setGridLines(GridLines)
	 */
	public void setShowGrid(boolean showGrid) {
		this.showGrid.set(showGrid);
	}
	
	/**
	 * @return true if a counting grid should be displayed in viewers, false otherwise
	 * @see #getGridLines()
	 */
	public boolean getShowGrid() {
		return showGrid.get();
	}

	/**
	 * @return the {@link GridLines} object that defines how a counting grid may be show in viewers
	 * @see #getShowGrid()
	 */
	public GridLines getGridLines() {
		return gridLines.get();
	}
	
	/**
	 * Set the {@link GridLines} object that defines how a counting grid may be show in viewers
	 * @param gridLines
	 */
	public void setGridLines(final GridLines gridLines) {
		this.gridLines.set(gridLines);
	}
	
	/**
	 * @return an object property containing a {@link GridLines} object that defines how a counting grid may be show in viewers
	 * @see #getShowGrid()
	 */
	public ObjectProperty<GridLines> gridLinesProperty() {
		return gridLines;
	}

	/**
	 * Set the {@link MeasurementMapper} that defines how detections should be color coded according to their measurement values in viewers
	 * @param mapper
	 */
	public void setMeasurementMapper(MeasurementMapper mapper) {
		this.measurementMapper.set(mapper);
	}
	
	/**
	 * @return the {@link MeasurementMapper} object that defines how detections should be color coded according to their measurement values in viewers
	 */
	public MeasurementMapper getMeasurementMapper() {
		return measurementMapper.get();
	}
	
	/**
	 * @return object property containing the current {@link MeasurementMapper}, if one has been set, or null otherwise
	 */
	public ObjectProperty<MeasurementMapper> measurementMapperProperty() {
		return measurementMapper;
	}
	
	/**
	 * Reset any {@link MeasurementMapper}, so that measurements are not used to determine object colors.
	 */
	public void resetMeasurementMapper() {
		setMeasurementMapper(null);
	}

	/**
	 * @return true if objects should be displayed regardless of classification (i.e. no classifications are 'hidden')
	 * @see #selectedClassesProperty()
	 */
	public boolean getAllPathClassesVisible() {
		return selectedClasses.isEmpty();
	}

	/**
	 * Query whether an object should be hidden.
	 * This could be for one of 3 reasons:
	 * <ol>
	 *     <li>It is of a type that should be hidden (e.g. detections, annotations, TMA cores)</li>
	 *     <li>It is rejected by {@link #showObjectPredicateProperty()}</li>
	 *     <li>It has a classification that should be hidden</li>
	 * </ol>
	 * This can be based on the predicate or the classification.
	 * @param pathObject the object to test
	 * @return true if the object should be hidden, false if it can be shown
	 */
	public boolean isHidden(PathObject pathObject) {
		return isHiddenByType(pathObject) || isHiddenByPredicate(pathObject) || isPathClassHidden(pathObject.getPathClass());
	}

	/**
	 * Get the predicate used to determine whether an object should be displayed or hidden.
	 * Note that this is applied in <i>addition</i> to any values in {@link #selectedClassesProperty()} and the individual
	 * show/hide options for different object types.
	 * <p>
	 * Usually the value should be {@code null}, but it exists to allow more complex filtering of objects that cannot
	 * be achieved using any of the other methods.
	 * <p>
	 * @return the predicate used to determine whether an object should be displayed or hidden
	 * @see #isHidden(PathObject)
	 * @since v0.6.0
	 */
	public ObjectProperty<Predicate<PathObject>> showObjectPredicateProperty() {
		return showObjectPredicate;
	}

	/**
	 * Reset the value of {@link #showObjectPredicateProperty()}.
	 * @since v0.6.0
	 */
	public void resetShowObjectPredicate() {
		setShowObjectPredicate(null);
	}

	/**
	 * Set the value of {@link #showObjectPredicateProperty()}.
	 * <p>
	 * If the intention is to show all objects, either pass {@code null} or call {@link #resetShowObjectPredicate()} -
	 * do not pass a predicate that always returns {@code true}, because this will be less efficient.
	 *
	 * @param pathObjectPredicate the predicate to use, or {@code null} null if no predicate should be used
	 * @see #isHidden(PathObject)
	 * @since v0.6.0
	 */
	public void setShowObjectPredicate(Predicate<PathObject> pathObjectPredicate) {
		showObjectPredicate.set(pathObjectPredicate);
	}

	/**
	 * Get the value of {@link #showObjectPredicateProperty()}.
	 * @return the value of the predicate
	 * @since v0.6.0
	 */
	public Predicate<PathObject> getShowObjectPredicate() {
		return showObjectPredicate.get();
	}

	private boolean isHiddenByType(PathObject pathObject) {
		if (pathObject.isDetection())
			return !getShowDetections();
		if (pathObject.isAnnotation())
			return !getShowAnnotations();
		if (pathObject.isTMACore())
			return !getShowTMAGrid();
		return false;
	}

	private boolean isHiddenByPredicate(PathObject pathObject) {
		var predicate = getShowObjectPredicate();
		return predicate != null && !predicate.test(pathObject);
	}

	/**
	 * Query whether objects with a specified classification should be displayed or hidden.
	 * @param pathClass the classification to query
	 * @return true if objects with the classification should be displayed, false if they should be hidden
	 */
	public boolean isPathClassHidden(final PathClass pathClass) {
		var mode = getSelectedClassVisibilityMode();
		boolean showByDefault = mode == ClassVisibilityMode.HIDE_SELECTED;
		boolean checkContains = !getUseExactSelectedClasses();

		// If the class is selected,
		if (isSelectedClass(pathClass) || (checkContains && containsSelectedClass(pathClass))) {
			return showByDefault;
		} else {
			return !showByDefault;
		}
	}

	private boolean isSelectedClass(final PathClass pathClass) {
		if (selectedClasses.isEmpty())
			return false;

		if (pathClass == null || pathClass == PathClass.NULL_CLASS)
			return selectedClasses.contains(null) || selectedClasses.contains(PathClass.NULL_CLASS);

		return selectedClasses.contains(pathClass);
	}

	/**
	 * Check if a classification matches all the parts of any selected classification.
	 * This is a 'generous' criterion, e.g. hiding "CD3" will hide "CD3", "CD3: CD8", "CD8: CD3"...
	 * @param pathClass
	 * @return
	 */
	private boolean containsSelectedClass(final PathClass pathClass) {
		if (selectedClasses.isEmpty())
			return false;

		if (pathClass == null || pathClass == PathClass.NULL_CLASS)
			return false;

		var set = pathClass.toSet();
		for (var selected : selectedClasses) {
			if (selected != null && selected != PathClass.NULL_CLASS) {
				if (pathClass.isDerivedClass() || selected.isDerivedClass()) {
					if (set.containsAll(selected.toSet()))
						return true;
				}
			}
		}
		return false;
	}


	/**
	 * Request that objects with a particular PathClass not be displayed.
	 * This may be null, indicating that all unclassified objects should be hidden.
	 * 
	 * @param pathClass
	 * @param hidden
	 */
	public void setPathClassHidden(final PathClass pathClass, final boolean hidden) {
		if (hidden)
			selectedClasses.add(pathClass);
		else
			selectedClasses.remove(pathClass);
	}
	
	/**
	 * @return an observable set containing classifications for which the corresponding objects should not be displayed
	 */
	public ObservableSet<PathClass> selectedClassesProperty() {
		return selectedClasses;
	}

	/**
	 * Property to control whether entries in {@link #selectedClassesProperty()} should be used exactly,
	 * or should also include all related classes (i.e. classes that are supersets of the selected class).
	 * @return
	 */
	public BooleanProperty useExactSelectedClassesProperty() {
		return useExactSelectedClasses;
	}

	/**
	 * Set the value of {@link #useExactSelectedClassesProperty()}
	 * @param useExact
	 */
	public void setUseExactSelectedClasses(boolean useExact) {
		useExactSelectedClasses.set(useExact);
	}

	/**
	 * @return the value of {@link #useExactSelectedClassesProperty()}
	 */
	public boolean getUseExactSelectedClasses() {
		return useExactSelectedClasses.get();
	}

	/**
	 * Request that only exact matches to classes in {@link #selectedClassesProperty()} should be hidden.
	 * This influences the result of {@link #isPathClassHidden(PathClass)}.
	 * <p>
	 * If false, then any object with a classification that is a superset of a hidden classification will also be hidden.
	 * For example, if {@code "CD3"} is hidden then {@code "CD3: CD8"} will also be hidden.
	 * @return
	 * @since v0.6.0
	 */
	public ObjectProperty<ClassVisibilityMode> selectedClassVisibilityModeProperty() {
		return selectedClassVisibilityMode;
	}

	/**
	 * Get the value of {@link #selectedClassVisibilityModeProperty()}.
	 * @return
	 * @since v0.6.0
	 */
	public ClassVisibilityMode getSelectedClassVisibilityMode() {
		return selectedClassVisibilityMode.get();
	}

	/**
	 * Set the value of {@link #selectedClassVisibilityModeProperty()}.
	 * @param value the new value
	 * @since v0.6.0
	 */
	public void setSelectedClassVisibilityMode(ClassVisibilityMode value) {
		selectedClassVisibilityMode.set(value);
	}
	
	
	/**
	 * @return the boolean property indicating whether annotations should be displayed
	 */
	public BooleanProperty showAnnotationsProperty() {
		return showAnnotations;
	}
	
	/**
	 * @return the boolean property indicating whether object labels should be displayed
	 */
	public BooleanProperty showNamesProperty() {
		return showNames;
	}
	
	/**
	 * @return the float property indicating the font size that should be used for displaying names
	 */
	public FloatProperty fontSizeProperty() {
		return fontSize;
	}
	
	/**
	 * @return the boolean property indicating whether any current TMA grid should be displayed
	 */
	public BooleanProperty showTMAGridProperty() {
		return showTMAGrid;
	}
	
	/**
	 * @return the boolean property indicating whether any active pixel classification should be displayed
	 */
	public BooleanProperty showPixelClassificationProperty() {
		return showPixelClassification;
	}
	
	/**
	 * @return the filter used to determine whether a pixel classification should be computed for a specified region
	 */
	public ObjectProperty<RegionFilter> pixelClassificationFilterRegionProperty() {
		return pixelClassificationFilter;
	}
	
	/**
	 * Control where pixel classifications should be calculated during live prediction
	 * @param region
	 */
	public void setPixelClassificationRegionFilter(RegionFilter region) {
		pixelClassificationFilter.set(region);
	}
	
	/**
	 * @return a filter used to determine whether a pixel classification should be computed for a specified region
	 */
	public RegionFilter getPixelClassificationRegionFilter() {
		return pixelClassificationFilter.get();
	}

	/**
	 * @return the boolean property indicating whether detections should be displayed
	 */
	public BooleanProperty showDetectionsProperty() {
		return showDetections;
	}

	/**
	 * @return the boolean property indicating whether detections should be displayed 'filled'
	 */
	public BooleanProperty fillDetectionsProperty() {
		return fillDetections;
	}

	/**
	 * @return the boolean property indicating whether annotations should be displayed 'filled'
	 */
	public BooleanProperty fillAnnotationsProperty() {
		return fillAnnotations;
	}

	/**
	 * @return the boolean property indicating whether a counting grid should be shown over the viewer
	 */
	public BooleanProperty showGridProperty() {
		return showGrid;
	}	
	
	
}