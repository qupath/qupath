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
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

/**
 * Default class for storing overlay display options.
 * 
 * @author Pete Bankhead
 * 
 */
public class OverlayOptions {
	
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
	
	private ObjectProperty<MeasurementMapper> measurementMapper = new SimpleObjectProperty<>();
	private BooleanProperty showAnnotations = new SimpleBooleanProperty(true);
	private BooleanProperty showNames = new SimpleBooleanProperty(true);
	private BooleanProperty showTMAGrid = new SimpleBooleanProperty(true);
	private BooleanProperty showDetections = new SimpleBooleanProperty(true);
	private BooleanProperty showConnections = new SimpleBooleanProperty(true);
	private BooleanProperty fillDetections = new SimpleBooleanProperty(false);
	private BooleanProperty fillAnnotations = new SimpleBooleanProperty(false);
	private BooleanProperty showTMACoreLabels = new SimpleBooleanProperty(false);
	private BooleanProperty showGrid = new SimpleBooleanProperty(false);
	private ObjectProperty<GridLines> gridLines = new SimpleObjectProperty<>(new GridLines());

	private BooleanProperty showPixelClassification = new SimpleBooleanProperty(true);
	private ObjectProperty<RegionFilter> pixelClassificationFilter = new SimpleObjectProperty<>(RegionFilter.StandardRegionFilters.EVERYWHERE);

	private FloatProperty fontSize = new SimpleFloatProperty();

	private ObservableSet<PathClass> hiddenClasses = FXCollections.observableSet();

	private ObjectProperty<DetectionDisplayMode> cellDisplayMode = new SimpleObjectProperty<>(DetectionDisplayMode.NUCLEI_AND_BOUNDARIES);

	private FloatProperty opacity = new SimpleFloatProperty(1.0f);
	
	private LongProperty timestamp = new SimpleLongProperty(System.currentTimeMillis());
	
//    public void addPropertyChangeListener(PropertyChangeListener listener) {
//        this.pcs.addPropertyChangeListener(listener);
//    }
//
//    public void removePropertyChangeListener(PropertyChangeListener listener) {
//        this.pcs.removePropertyChangeListener(listener);
//    }
    
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
		hiddenClasses.addListener(timestamper);
		cellDisplayMode.addListener(timestamper);
		opacity.addListener(timestamper);
		fontSize.addListener(timestamper);
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
		this.hiddenClasses.addAll(options.hiddenClasses);
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
		this.timestamp.set(System.currentTimeMillis());
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
	 * @see #hiddenClassesProperty()
	 */
	public boolean getAllPathClassesVisible() {
		return hiddenClasses.isEmpty();
	}
	
	/**
	 * Query whether objects with a specified classification should be displayed or hidden.
	 * @param pathClass the classification to query
	 * @return true if objects with the classification should be displayed, false if they should be hidden
	 */
	public boolean isPathClassHidden(final PathClass pathClass) {
		if (hiddenClasses.isEmpty())
			return false;
		if (pathClass == null || pathClass == PathClass.NULL_CLASS)
			return hiddenClasses.contains(null) || hiddenClasses.contains(PathClass.NULL_CLASS);
		return hiddenClasses.contains(pathClass) || 
				((PathClassTools.isPositiveOrGradedIntensityClass(pathClass) || PathClassTools.isNegativeClass(pathClass)) && pathClass.isDerivedClass() && isPathClassHidden(pathClass.getParentClass()));
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
			hiddenClasses.add(pathClass);
		else
			hiddenClasses.remove(pathClass);
	}
	
	/**
	 * @return an observable set containing classifications for which the corresponding objects should not be displayed
	 */
	public ObservableSet<PathClass> hiddenClassesProperty() {
		return hiddenClasses;
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