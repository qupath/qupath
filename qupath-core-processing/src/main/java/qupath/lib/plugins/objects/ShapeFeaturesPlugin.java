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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Add shape measurements
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class ShapeFeaturesPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private ParameterList params;
	
	final private static Logger logger = LoggerFactory.getLogger(ShapeFeaturesPlugin.class);
	
	public ShapeFeaturesPlugin() {
		
		params = new ParameterList()
				.addTitleParameter("Measurements")
				.addBooleanParameter("area", "Area", true, "Compute area of ROI")
				.addBooleanParameter("perimeter", "Perimeter", false, "Compute perimeter of ROI")
				.addBooleanParameter("circularity", "Circularity", false, "Compute circularity of ROI, between 0 (a line) and 1 (a circle)")
				.addTitleParameter("Units")
				.addBooleanParameter("useMicrons", "Use microns", true, "Compute measurements using " + GeneralTools.micrometerSymbol() + ", where possible");
	}
	
	

	@Override
	public String getName() {
		return "Add shape measurements";
	}

	@Override
	public String getDescription() {
		return "Add shape measurements to the measurement lists for objects";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(PathCellObject.class, PathDetectionObject.class, TMACoreObject.class);
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {

		boolean useMicrons = params.getBooleanParameterValue("useMicrons") && imageData != null && imageData.getServer().hasPixelSizeMicrons();
		
		double pixelWidth = useMicrons ? imageData.getServer().getPixelWidthMicrons() : 1;
		double pixelHeight = useMicrons ? imageData.getServer().getPixelHeightMicrons() : 1;
		String unit = useMicrons ? GeneralTools.micrometerSymbol() : "px";
		
		boolean doArea = params.getBooleanParameterValue("area");
		boolean doPerimeter = params.getBooleanParameterValue("perimeter");
		boolean doCircularity = params.getBooleanParameterValue("circularity");
		
		PathArea roi = (parentObject.getROI() instanceof PathArea) ? (PathArea)parentObject.getROI() : null;
		if (roi instanceof PathArea) {
			tasks.add(new Runnable() {
	
				@Override
				public void run() {
					try {
						MeasurementList measurementList = parentObject.getMeasurementList();
						
						ROI roi;
						if (parentObject instanceof PathCellObject) {
							
							roi = ((PathCellObject)parentObject).getNucleusROI();
							if (roi instanceof PathArea)
								addMeasurements(measurementList, (PathArea)roi, "Nucleus Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
							
							roi = parentObject.getROI();
							if (roi instanceof PathArea)
								addMeasurements(measurementList, (PathArea)roi, "Cell Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
							
						} else {
							roi = parentObject.getROI();
							if (roi instanceof PathArea)
								addMeasurements(measurementList, (PathArea)roi, "ROI Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
						}
						
						measurementList.closeList();
					} catch (Exception e) {
						e.printStackTrace();
						throw(e);
					}
				}
			});
		}
	}
	

	private static void addMeasurements(final MeasurementList measurementList, final PathArea roi, final String prefix, final double pixelWidth, final double pixelHeight, final String unit,
			final boolean doArea, final boolean doPerimeter, final boolean doCircularity) {
		if (doArea)
			measurementList.putMeasurement(prefix + "Area " + unit + "^2", roi.getScaledArea(pixelWidth, pixelHeight));
		if (doPerimeter)
			measurementList.putMeasurement(prefix + "Perimeter " + unit, roi.getScaledPerimeter(pixelWidth, pixelHeight));
		if (doCircularity)
			measurementList.putMeasurement(prefix + "Circularity", roi.getCircularity());		
	}
	
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		ImageServer<? extends T> server = imageData.getServer();
		boolean pixelSizeMicrons = server != null && server.hasPixelSizeMicrons();
		params.getParameters().get("useMicrons").setHidden(!pixelSizeMicrons);
		return params;
	}



	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		return runner.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
	}
	

}
