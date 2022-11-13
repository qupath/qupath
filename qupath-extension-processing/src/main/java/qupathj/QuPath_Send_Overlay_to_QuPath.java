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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
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
public class QuPath_Send_Overlay_to_QuPath implements PlugIn {
	
	private String typeChoice = "Annotation";
	private boolean includeMeasurements = false;
	private boolean selectObjects = true;

	@Override
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		
		List<Roi> rois = null;
		if ("manager".equalsIgnoreCase(arg)) {
			var rm = RoiManager.getInstance();
			if (rm == null) {
				IJ.showMessage("No RoiManager found!");
				return;				
			}
			rois = Arrays.asList(rm.getRoisAsArray());
			if (rois.isEmpty()) {
				IJ.showMessage("No ROIs found in the RoiManager!");
				return;								
			}
		} else {
			Overlay overlay = imp == null ? null : imp.getOverlay();
			if (overlay == null || overlay.size() == 0) {
				IJ.showMessage("No overlay found!");
				return;
			}
			rois = Arrays.asList(overlay.toArray());
		}
		// Shouldn't happen
		if (rois.isEmpty())
			return;

		var gui = QuPathGUI.getInstance();
		var viewer = gui == null ? null : gui.getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		if (imageData == null) {
			IJ.showMessage("No image selected in QuPath!");
			return;
		}
		
		promptToImportRois(imageData, imp, rois, viewer.getImagePlane());
	}

	private void promptToImportRois(ImageData<?> imageData, ImagePlus imp, Collection<? extends Roi> rois, ImagePlane currentPlane) {
		
		GenericDialog gd = new GenericDialog("Send overlay to QuPath");
		gd.addChoice("Choose_object_type", new String[]{"Annotation", "Detection"}, typeChoice);
		if (imp != null)
			gd.addCheckbox("Include_measurements", false);
		gd.addCheckbox("Select_objects", selectObjects);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		
		typeChoice = gd.getNextChoice();
		boolean asDetection = "Detection".equals(typeChoice);
		includeMeasurements = imp == null ? false : gd.getNextBoolean();
		selectObjects = gd.getNextBoolean();

		var server = imageData.getServer();
		double downsample = imp == null ? 1.0 : IJTools.estimateDownsampleFactor(imp, server);
		var hierarchy = imageData.getHierarchy();
		
		ImagePlane plane = currentPlane;
		if (imp == null)
			plane = null;
		else if (imp != null && server.nZSlices() * server.nTimepoints() > 1) {
			if (imp.getNSlices() == server.nZSlices() && imp.getNFrames() == server.nTimepoints())
				plane = null;
		}
		
		List<PathObject> pathObjects = createObjectsFromROIs(imp, rois, downsample, asDetection, includeMeasurements, plane);
		if (!pathObjects.isEmpty()) {
			Platform.runLater(() -> {
				hierarchy.addObjects(pathObjects);
				// Select the objects, e.g. so they can be classified or otherwise updated easily
				if (selectObjects)
					hierarchy.getSelectionModel().selectObjects(pathObjects);
			});
		}
	}

	/**
	 * Legacy method to turn an array of ImageJ ROIs into a list of QuPath PathObjects, optionally adding measurements as well.
	 * @param imp
	 * @param rois
	 * @param server
	 * @param downsample
	 * @param asDetection
	 * @param includeMeasurements
	 * @param plane
	 * @return
	 * @deprecated use instead {@link #createObjectsFromROIs(ImagePlus, Collection, double, boolean, boolean, ImagePlane)}
	 */
	@Deprecated
	public static List<PathObject> createPathObjectsFromROIs(final ImagePlus imp, final Roi[] rois, final ImageServer<?> server, 
			final double downsample, final boolean asDetection, final boolean includeMeasurements, final ImagePlane plane) {
		return createObjectsFromROIs(imp, Arrays.asList(rois), downsample, asDetection, includeMeasurements, plane);
	}
	
	/**
	 * Turn an array of ImageJ ROIs into a list of QuPath PathObjects, optionally adding measurements as well.
	 * 
	 * @param imp
	 * @param rois
	 * @param downsample
	 * @param asDetection
	 * @param includeMeasurements
	 * @param plane 
	 * @return
	 * @since v0.4.0
	 */
	public static List<PathObject> createObjectsFromROIs(final ImagePlus imp, final Collection<? extends Roi> rois, 
			final double downsample, final boolean asDetection, final boolean includeMeasurements, final ImagePlane plane) {
		List<PathObject> pathObjects = new ArrayList<>();
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = imp == null ? null : new Analyzer(imp, Analyzer.getMeasurements(), rt);
		String[] headings = null;
		Calibration cal = imp == null ? null : imp.getCalibration();
		var xOrigin = cal == null ? 0 : cal.xOrigin;
		var yOrigin = cal == null ? 0 : cal.yOrigin;
		for (Roi roi : rois) {
			PathObject pathObject;
			if (asDetection && !(roi instanceof PointRoi))
				pathObject = IJTools.convertToDetection(roi, xOrigin, yOrigin, downsample, plane);
			else
				pathObject = IJTools.convertToAnnotation(roi, xOrigin, yOrigin, downsample, plane);
			if (pathObject == null)
				IJ.log("Sorry, I couldn't convert " + roi + " to a valid QuPath object");
			else {
				// Make measurements
				if (includeMeasurements && imp != null) {
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
						ml.put(h, rt.getValue(h, row));
					}
					ml.close();
				}
				pathObjects.add(pathObject);
			}
		}
		return pathObjects;
	}
	
	
	
}
