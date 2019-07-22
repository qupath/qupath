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

package qupathj;

import java.awt.image.BufferedImage;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import javafx.application.Platform;

/**
 * ImageJ plugin for sending back all the active ROI from ImageJ to QuPath.
 * 
 * @author Pete Bankhead
 *
 */
public class QUPath_Send_ROI_to_QuPath implements PlugIn {

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
		
		QuPathGUI gui = QuPathGUI.getInstance();
		if (gui == null) {
			IJ.showMessage("QuPath viewer not found!");
			return;
		}
		
		QuPathViewer viewer = gui.getViewer();
		ImageServer<BufferedImage> server = viewer.getServer();
		double downsample = IJTools.estimateDownsampleFactor(imp, server);
		PathObject pathObject = IJTools.convertToPathObject(imp, server, roi, downsample, false, viewer.getImagePlane());
		if (pathObject == null) {
			IJ.error("Sorry, I could not convert " + roi + " to a value QuPath object");
			return;
		}
		
		Platform.runLater(() -> gui.getViewer().getHierarchy().addPathObject(pathObject));		
	}

}
