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

package qupath.lib.plugins.objects;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to create new annotations by expanding the size of existing annotations.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class DilateAnnotationPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private static Logger logger = LoggerFactory.getLogger(DilateAnnotationPlugin.class);
	
	/**
	 * Line cap to use for annotation expansion.
	 * This can be important when expanding line or polyline annotations to 
	 * determine how the ends are handled.
	 */
	public static enum LineCap {
		/**
		 * Round cap
		 */
		ROUND,
		/**
		 * Flat cap
		 */
		FLAT,
		/**
		 * Square cap
		 */
		SQUARE;
		
		@Override
		public String toString() {
			switch(this) {
			case FLAT:
				return "Flat";
			case ROUND:
				return "Round";
			case SQUARE:
				return "Square";
			default:
				throw new IllegalArgumentException();
			}
		}
	};
	
	private ParameterList params = new ParameterList()
			.addDoubleParameter("radiusMicrons", "Expansion radius", 100, GeneralTools.micrometerSymbol(), "Distance to expand ROI")
			.addDoubleParameter("radiusPixels", "Expansion radius", 100, "px", "Distance to expand ROI")
			.addChoiceParameter("lineCap", "Line cap", LineCap.ROUND, Arrays.asList(LineCap.values()), "Method to handle end points when expanding lines (not important when expanding areas)")
			.addBooleanParameter("removeInterior", "Remove interior", false, "Create annotation containing only the expanded region, with the original ROI removed")
			.addBooleanParameter("constrainToParent", "Constrain to parent", true, "Constrain ROI to fit inside the ROI of the parent object")
			;
	
	private String resultString = null;
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Collections.singleton(PathAnnotationObject.class);
	}

	@Override
	public String getName() {
		return "Expand annotations";
	}

	@Override
	public String getDescription() {
		return "Expand annotation object ROIs";
	}

	@Override
	public String getLastResultsDescription() {
		return resultString;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(hasMicrons, "radiusPixels");
		params.setHiddenParameters(!hasMicrons, "radiusMicrons");
		return params;
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		return getHierarchy(runner).getSelectionModel().getSelectedObjects().stream().filter(p -> p.isAnnotation()).collect(Collectors.toList());
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {}
	
	@Override
	protected Collection<Runnable> getTasks(final PluginRunner<T> runner) {
		Collection<? extends PathObject> parentObjects = getParentObjects(runner);
		if (parentObjects == null || parentObjects.isEmpty())
			return Collections.emptyList();
		
		// Add a single task, to avoid multithreading - which may complicate setting parents
		List<Runnable> tasks = new ArrayList<>(parentObjects.size());
		ImageServer<T> server = getServer(runner);
		Rectangle bounds = new Rectangle(0, 0, server.getWidth(), server.getHeight());
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		
		double radiusPixels;
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons())
			radiusPixels = params.getDoubleParameterValue("radiusMicrons") / cal.getAveragedPixelSizeMicrons();
		else
			radiusPixels = params.getDoubleParameterValue("radiusPixels");
		
		boolean constrainToParent = params.getBooleanParameterValue("constrainToParent");
		boolean removeInterior = params.getBooleanParameterValue("removeInterior");
		LineCap cap = params.containsKey("lineCap") ? (LineCap)params.getChoiceParameterValue("lineCap") : LineCap.ROUND;
		
		// Want to reset selection
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		Collection<PathObject> previousSelection = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		
		tasks.add(() -> {
			for (PathObject pathObject : parentObjects) {
				addExpandedAnnotation(bounds, hierarchy, pathObject, radiusPixels, constrainToParent, removeInterior, cap);
			}
			hierarchy.getSelectionModel().selectObjects(previousSelection);
			hierarchy.getSelectionModel().setSelectedObject(selected, true);
		});
		return tasks;
	}
	
	
	/**
	 * Create and add a new annotation by expanding the ROI of the specified PathObject.
	 * 
	 * 
	 * @param bounds
	 * @param hierarchy
	 * @param pathObject
	 * @param radiusPixels
	 * @param constrainToParent
	 * @param removeInterior
	 */
	private static void addExpandedAnnotation(final Rectangle bounds, final PathObjectHierarchy hierarchy, final PathObject pathObject, final double radiusPixels, final boolean constrainToParent, final boolean removeInterior, final LineCap cap) {
		
		ROI roi = pathObject.getROI();
		
		Geometry geometry = roi.getGeometry();
		
		int capVal = BufferParameters.CAP_ROUND;
		if (cap == LineCap.FLAT)
			capVal = BufferParameters.CAP_FLAT;
		else if (cap == LineCap.SQUARE)
			capVal = BufferParameters.CAP_SQUARE;
		
		Geometry geometry2  = BufferOp.bufferOp(geometry, radiusPixels, BufferParameters.DEFAULT_QUADRANT_SEGMENTS, capVal);
		
		// If the radius is negative (i.e. an erosion), then the parent will be the original object itself
		boolean isErosion = radiusPixels < 0;
	    PathObject parent = isErosion ? pathObject : pathObject.getParent();
		if (constrainToParent && !isErosion) {
		    Geometry parentShape;
		    if (parent == null || parent.getROI() == null)
		        parentShape = ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlane(roi)).getGeometry();
		    else
		        parentShape = parent.getROI().getGeometry();
		    geometry2 = geometry2.intersection(parentShape);
		}

		if (removeInterior) {
			if (isErosion)
				geometry2 = geometry.difference(geometry2);
			else {
				if (geometry.getArea() == 0.0)
					geometry = geometry.buffer(0.5);
				geometry2 = geometry2.difference(geometry);
			}
		}

		ROI roi2 = GeometryTools.geometryToROI(geometry2, ImagePlane.getPlane(roi));
		
		if (roi2.isEmpty()) {
			logger.debug("Updated ROI is empty after {} px expansion", radiusPixels);
			return;
		}
		
		// Create a new annotation, with properties based on the original
		PathObject annotation2 = PathObjects.createAnnotationObject(roi2, pathObject.getPathClass());
		annotation2.setName(pathObject.getName());
		annotation2.setColorRGB(pathObject.getColorRGB());

		if (constrainToParent || isErosion)
		    hierarchy.addPathObjectBelowParent(parent, annotation2, true);
		else
			hierarchy.addPathObject(annotation2);
		
	}
	

}
