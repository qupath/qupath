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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import qupath.lib.gui.helpers.MeasurementMapper;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.classes.PathClass;

/**
 * Default class for storing overlay display options.
 * 
 * @author Pete Bankhead
 * 
 */
public class OverlayOptions {
	
	public enum CellDisplayMode {BOUNDARIES_ONLY, NUCLEI_ONLY, NUCLEI_AND_BOUNDARIES};
	
	private ObjectProperty<MeasurementMapper> measurementMapper = new SimpleObjectProperty<>();
	private BooleanProperty showAnnotations = new SimpleBooleanProperty(true);
	private BooleanProperty showTMAGrid = new SimpleBooleanProperty(true);
	private BooleanProperty showPixelClassification = new SimpleBooleanProperty(true);
	private BooleanProperty showDetections = new SimpleBooleanProperty(true);
	private BooleanProperty showConnections = new SimpleBooleanProperty(true);
	private BooleanProperty fillDetections = new SimpleBooleanProperty(false);
	private BooleanProperty fillAnnotations = new SimpleBooleanProperty(false);
	private BooleanProperty showTMACoreLabels = new SimpleBooleanProperty(false);
	private BooleanProperty showGrid = new SimpleBooleanProperty(false);
	private ObjectProperty<GridLines> gridLines = new SimpleObjectProperty<>(new GridLines());
	
	private ObservableSet<PathClass> hiddenClasses = FXCollections.observableSet();

	private ObjectProperty<CellDisplayMode> cellDisplayMode = new SimpleObjectProperty<>(CellDisplayMode.NUCLEI_AND_BOUNDARIES);

	private FloatProperty opacity = new SimpleFloatProperty(1.0f);
	
	
//    public void addPropertyChangeListener(PropertyChangeListener listener) {
//        this.pcs.addPropertyChangeListener(listener);
//    }
//
//    public void removePropertyChangeListener(PropertyChangeListener listener) {
//        this.pcs.removePropertyChangeListener(listener);
//    }
    
    
    public void setOpacity(float opacity) {
    	opacity = opacity < 0 ? 0 : (opacity > 1 ? 1 : opacity);
    	this.opacity.set(opacity);
    }
    
    
    public CellDisplayMode getCellDisplayMode() {
    	return cellDisplayMode.get();
    }
    
    public boolean getShowCellNuclei() {
    	return cellDisplayMode.get() != CellDisplayMode.BOUNDARIES_ONLY;
    }

    public boolean getShowCellBoundaries() {
    	return cellDisplayMode.get() != CellDisplayMode.NUCLEI_ONLY;
    }
    
    
    public void setCellDisplayMode(CellDisplayMode mode) {
    	this.cellDisplayMode.set(mode);
    }
    
    public ObjectProperty<CellDisplayMode> cellDisplayModeProperty() {
    	return cellDisplayMode;
    }
    
    public float getOpacity() {
    	return opacity.get();
    }
    
    public FloatProperty opacityProperty() {
    	return opacity;
    }

    public float getThickStrokeWidth(double downsample) {
    	return (float)(PathPrefs.getThickStrokeThickness() * Math.max(1, downsample));
    }
    
    
    
    /**
	 * Show the annotations on the image, including the TMA grid.
	 * 
	 * @param show
	 */
	public void setShowTMAGrid(boolean show) {
		this.showTMAGrid.set(show);
	}
	
	/**
	 * Show the annotations on the image, including the TMA grid.
	 * 
	 * @param show
	 */
	public void setShowAnnotations(boolean show) {
		this.showAnnotations.set(show);
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
	 * Fill the objects on the image.
	 * 
	 * @param fill
	 */
	public void setFillObjects(boolean fill) {
		this.fillDetections.set(fill);
	}
	
	/**
	 * Fill the annotations on the image.
	 * 
	 * @param fill
	 */
	public void setFillAnnotations(boolean fill) {
		fillAnnotations.set(fill);
	}
	
	/**
	 * Show connections, if available.
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
	
	public boolean getShowTMACoreLabels() {
		return showTMACoreLabels.get();
	}
	
	public BooleanProperty showTMACoreLabelsProperty() {
		return showTMACoreLabels;
	}
	
	public BooleanProperty showConnectionsProperty() {
		return showConnections;
	}
	
	public boolean getShowPixelClassification() {
		return showPixelClassification.get();
	}

	public boolean getShowAnnotations() {
		return showAnnotations.get();
	}
	
	public boolean getShowTMAGrid() {
		return showTMAGrid.get();
	}
	
	public boolean getShowDetections() {
		return showDetections.get();
	}
	
	public boolean getShowConnections() {
		return showConnections.get();
	}
	
	public boolean getFillObjects() {
		return fillDetections.get();
	}
	
	public boolean getFillAnnotations() {
		return fillAnnotations.get();
	}
	
	public void setShowGrid(boolean showGrid) {
		this.showGrid.set(showGrid);
	}
	
	public boolean getShowGrid() {
		return showGrid.get();
	}

	
	public GridLines getGridLines() {
		return gridLines.get();
	}
	
	public void setGridLines(final GridLines gridLines) {
		this.gridLines.set(gridLines);
	}
	
	public ObjectProperty<GridLines> gridLinesProperty() {
		return gridLines;
	}

	
	public void setMeasurementMapper(MeasurementMapper mapper) {
		this.measurementMapper.set(mapper);
	}
	
	public MeasurementMapper getMeasurementMapper() {
		return measurementMapper.get();
	}
	
	public ObjectProperty<MeasurementMapper> measurementMapperProperty() {
		return measurementMapper;
	}
	
	public void resetMeasurementMapper() {
		setMeasurementMapper(null);
	}

	public boolean getAllPathClassesVisible() {
		return hiddenClasses.isEmpty();
	}
	
	public boolean isPathClassHidden(final PathClass pathClass) {
		if (hiddenClasses.isEmpty())
			return false;
		return hiddenClasses.contains(pathClass) || (pathClass != null && pathClass.isDerivedClass() && isPathClassHidden(pathClass.getParentClass()));
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
	
	public ObservableSet<PathClass> hiddenClassesProperty() {
		return hiddenClasses;
	}
	
	
	public BooleanProperty showAnnotationsProperty() {
		return showAnnotations;
	}
	
	public BooleanProperty showTMAGridProperty() {
		return showTMAGrid;
	}
	
	public BooleanProperty showPixelClassificationProperty() {
		return showPixelClassification;
	}

	public BooleanProperty showDetectionsProperty() {
		return showDetections;
	}

	public BooleanProperty fillDetectionsProperty() {
		return fillDetections;
	}

	public BooleanProperty fillAnnotationsProperty() {
		return fillAnnotations;
	}

	public BooleanProperty showGridProperty() {
		return showGrid;
	}	
	
	
}