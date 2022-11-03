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

package qupathj;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.regions.ImagePlane;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import javafx.application.Platform;

/**
 * ImageJ plugin for sending back the active ROI from ImageJ to QuPath.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPath_Send_ROI_to_QuPath implements PlugIn {

	@Override
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp == null)
			return;
		
		Roi roi = imp.getRoi();
		if (roi == null) {
			IJ.showMessage("No Roi found!");
			return;
		}
		
		var gui = QuPathGUI.getInstance();
		var viewer = gui == null ? null : gui.getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		if (gui == null) {
			IJ.showMessage("No active image found in QuPath!");
			return;
		}
		
		var server = imageData.getServer();
		double downsample = IJTools.estimateDownsampleFactor(imp, server);
		var cal = imp.getCalibration();
		
		// We always use the current viewer plane for ROIs
		// This could be changed in the future
		var plane = viewer.getImagePlane();
		if (server.nZSlices() * server.nTimepoints() > 1) {
			if (imp.getNSlices() == server.nZSlices() && imp.getNFrames() == server.nTimepoints()) {
				plane = ImagePlane.getPlane(imp.getZ()-1, imp.getT()-1);
			}
		}
		
		var pathObject = IJTools.convertToAnnotation(roi, cal.xOrigin, cal.yOrigin, downsample, plane);
//		PathObject pathObject = IJTools.convertToAnnotation(imp, server, roi, downsample, viewer.getImagePlane());
		if (pathObject == null) {
			IJ.error("Sorry, I couldn't convert " + roi + " to a valid QuPath object");
			return;
		}
		
		Platform.runLater(() -> {
			imageData.getHierarchy().addObject(pathObject);
			imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
			viewer.setZPosition(pathObject.getROI().getZ());
			viewer.setTPosition(pathObject.getROI().getT());
		});		
	}

}
