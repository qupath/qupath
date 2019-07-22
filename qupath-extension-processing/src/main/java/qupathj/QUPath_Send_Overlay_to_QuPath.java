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
import java.util.ArrayList;
import java.util.List;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import javafx.application.Platform;

/**
 * ImageJ plugin for sending back all the ROIs on an ImageJ overlay to QuPath.
 * 
 * Optionally measure the ROIs first, and include the measurements as features.
 * 
 * @author Pete Bankhead
 *
 */
public class QUPath_Send_Overlay_to_QuPath implements PlugIn {
	
	private String typeChoice = "Detection";
	private boolean includeMeasurements = false;

	@Override
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp == null)
			return;

		Overlay overlay = imp.getOverlay();
		if (overlay == null || overlay.size() == 0) {
			IJ.showMessage("No overlay found!");
			return;
		}

		QuPathGUI gui = QuPathGUI.getInstance();
		if (gui == null) {
			IJ.showMessage("QuPath viewer not found!");
			return;
		}

		GenericDialog gd = new GenericDialog("Send overlay to QuPath");
		gd.addChoice("Choose_object_type", new String[]{"Annotation", "Detection"}, "Detection");
		gd.addCheckbox("Include_measurements", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		typeChoice = gd.getNextChoice();
		boolean asDetection = "Detection".equals(typeChoice);
		includeMeasurements = gd.getNextBoolean();

		QuPathViewer viewer = gui.getViewer();
		ImageServer<BufferedImage> server = viewer.getServer();
		double downsample = IJTools.estimateDownsampleFactor(imp, server);
		PathObjectHierarchy hierarchy = gui.getViewer().getHierarchy();
		
		List<PathObject> pathObjects = createPathObjectsFromROIs(imp, overlay.toArray(), server, downsample, asDetection, includeMeasurements, viewer.getImagePlane());
		if (!pathObjects.isEmpty()) {
			Platform.runLater(() -> hierarchy.addPathObjects(pathObjects));
		}
	}

	
	
	/**
	 * Turn an array of ImageJ ROIs into a list of QuPath PathObjects, optionally adding measurements as well.
	 * 
	 * @param imp
	 * @param rois
	 * @param server
	 * @param downsample
	 * @param asDetection
	 * @param includeMeasurements
	 * @return
	 */
	public static List<PathObject> createPathObjectsFromROIs(final ImagePlus imp, final Roi[] rois, final ImageServer<?> server, final double downsample, final boolean asDetection, final boolean includeMeasurements, final ImagePlane plane) {
		List<PathObject> pathObjects = new ArrayList<>();
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = new Analyzer(imp, Analyzer.getMeasurements(), rt);
		String[] headings = null;
		for (Roi roi : rois) {
			PathObject pathObject = IJTools.convertToPathObject(imp, server, roi, downsample, asDetection, plane);
			if (pathObject == null)
				IJ.log("Sorry, I could not convert " + roi + " to a value QuPath object");
			else {
				// Make measurements
				if (includeMeasurements) {
					ImageProcessor ip = imp.getProcessor();
					ip.setRoi(roi);
					ImageStatistics stats = ImageStatistics.getStatistics(ip, Analyzer.getMeasurements(), imp.getCalibration());
					analyzer.saveResults(stats, roi);
					// Get measurements from table and append
					if (headings == null)
						headings = rt.getHeadings();
					int row = rt.getCounter()-1;
					MeasurementList ml = pathObject.getMeasurementList();
					for (String h : headings) {
						if ("Label".equals(h))
							continue;
						ml.putMeasurement(h, rt.getValue(h, row));
					}
					ml.close();
				}
				pathObjects.add(pathObject);
			}
		}
		return pathObjects;
	}
	
	
	
}
