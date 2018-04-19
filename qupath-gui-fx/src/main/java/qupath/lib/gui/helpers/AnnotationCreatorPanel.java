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

package qupath.lib.gui.helpers;

import javafx.scene.control.ColorPicker;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;


/**
 * Panel used when specifying an annotation based upon its coordinates - 
 * useful when exactly the same size of annotation is needed (for some reason).
 * 
 * @author Pete Bankhead
 *
 */
public class AnnotationCreatorPanel {
	
	private ImageServer<?> server;
	private BorderPane pane = new BorderPane();
	
	private ColorPicker colorPicker = new ColorPicker(ColorToolsFX.getCachedColor(PathPrefs.getColorDefaultAnnotations()));
	
	private ParameterList params = new ParameterList()
//			.addEmptyParameter("t1", "Properties")
			.addChoiceParameter("type", "Type", "Rectangle", new String[]{"Rectangle", "Ellipse"})
			.addStringParameter("name", "Name", "")
//			.addEmptyParameter("t2", "Location")
			.addDoubleParameter("xOrigin", "X origin", -1, null, "X-coordinate of top left of annotation bounding box (if < 0, annotation will be centered)")
			.addDoubleParameter("yOrigin", "Y origin", -1, null, "Y-coordinate of top left of annotation bounding box (if < 0, annotation will be centered)")
			.addDoubleParameter("width", "Width", 100, null, "Width of annotation bounding box")
			.addDoubleParameter("height", "Height", 100, null, "Height of annotation bounding box")
			.addBooleanParameter("useMicrons", "Use microns", false, "Specify X origin, Y origin, Width & Height in " + GeneralTools.micrometerSymbol())
			;
	
	public AnnotationCreatorPanel(final ImageServer<?> server) {
		this.server = server;
		if (server == null || !server.hasPixelSizeMicrons())
			params.setHiddenParameters(true, "useMicrons");
		pane.setCenter(new ParameterPanelFX(params).getPane());
		pane.setBottom(colorPicker);
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	/**
	 * Create &amp; return a new annotation object according to the input.
	 * @return
	 */
	public PathObject createNewObject() {
		
		double scaleX = 1;
		double scaleY = 1;
		if (server != null && params.getBooleanParameterValue("useMicrons")) {
			scaleX = 1.0 / server.getPixelWidthMicrons();
			scaleY = 1.0 / server.getPixelHeightMicrons();
		}
		
		// Extract coordinates
		double x = params.getDoubleParameterValue("xOrigin") * scaleX;
		double y = params.getDoubleParameterValue("yOrigin") * scaleY;
		double width = params.getDoubleParameterValue("width") * scaleX;
		double height = params.getDoubleParameterValue("height") * scaleY;
		
		if (width == 0 || height == 0)
			return null;
		
		if (x < 0 && server != null)
			x = 0.5 * (server.getWidth() - width);
		if (y < 0 && server != null)
			y = 0.5 * (server.getHeight() - height);
		
		// Create ROI
		ROI pathROI;
		if ("Rectangle".equals(params.getChoiceParameterValue("type")))
			pathROI = new RectangleROI(x, y, width, height);
		else
			pathROI = new EllipseROI(x, y, width, height);			
		
		// Create & return annotation
		PathAnnotationObject pathObject = new PathAnnotationObject(pathROI);
		String name = params.getStringParameterValue("name");
		if (name.length() > 0)
			pathObject.setName(name);
		pathObject.setColorRGB(ColorToolsFX.getRGBA(colorPicker.getValue()));
		
		return pathObject;
	}

}