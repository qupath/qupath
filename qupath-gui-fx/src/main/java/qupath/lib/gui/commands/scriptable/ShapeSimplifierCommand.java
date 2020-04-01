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

package qupath.lib.gui.commands.scriptable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ObservableValue;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;


/**
 * Simplify a shape by reducing the number of vertices it contains.
 * 
 * @author Pete Bankhead
 * 
 * @param <T> generic parameter for {@link ImageData}
 *
 */
public class ShapeSimplifierCommand<T> implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(ShapeSimplifierCommand.class);
	
	private ObservableValue<ImageData<T>> manager;
	private double altitudeThreshold = 1;
	
	public ShapeSimplifierCommand(final ObservableValue<ImageData<T>> manager) {
		this.manager = manager;
	}
	

	@Override
	public void run() {
		ImageData<?> imageData = manager.getValue();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		if (!(pathObject instanceof PathAnnotationObject) || pathObject.hasChildren() || !RoiTools.isShapeROI(pathObject.getROI())) {
			logger.error("Only annotations without child objects can be simplified");
			return;
		}

		String input = Dialogs.showInputDialog("Simplify shape", 
				"Set altitude threshold in pixels (> 0; higher values give simpler shapes)", 
				Double.toString(altitudeThreshold));
		if (input == null || !(input instanceof String) || ((String)input).trim().length() == 0)
			return;
		try {
			altitudeThreshold = Double.parseDouble(((String)input).trim());
		} catch (NumberFormatException e) {
			logger.error("Could not parse altitude threshold from {}", input);
			return;
		}
		
		long startTime = System.currentTimeMillis();
		ROI pathROI = pathObject.getROI();
		PathObject pathObjectNew = null;
		if (pathROI instanceof PolygonROI) {
			PolygonROI polygonROI = (PolygonROI)pathROI;
			polygonROI = ShapeSimplifier.simplifyPolygon(polygonROI, altitudeThreshold);
			pathObjectNew = PathObjects.createAnnotationObject(polygonROI, pathObject.getPathClass(), pathObject.getMeasurementList());
		} else {
			pathROI = ShapeSimplifier.simplifyShape(pathROI, altitudeThreshold);
			pathObjectNew = PathObjects.createAnnotationObject(pathROI, pathObject.getPathClass(), pathObject.getMeasurementList());			
		}
		long endTime = System.currentTimeMillis();
//		logger.debug("Polygon simplified in " + (endTime - startTime)/1000. + " seconds");
		logger.debug("Shape simplified in " + (endTime - startTime) + " ms");
		hierarchy.removeObject(pathObject, true);
		hierarchy.addPathObject(pathObjectNew);
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
//		viewer.setSelectedObject(pathObjectNew);
	}
	

}