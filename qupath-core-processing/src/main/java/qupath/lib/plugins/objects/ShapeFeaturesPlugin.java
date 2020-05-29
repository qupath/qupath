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

package qupath.lib.plugins.objects;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.RoiTools;
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
	
	/**
	 * Constructor.
	 */
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
		return Arrays.asList(PathCellObject.class, PathDetectionObject.class);
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {

		PixelCalibration cal = imageData == null ? null : imageData.getServer().getPixelCalibration();
		boolean useMicrons = params.getBooleanParameterValue("useMicrons") && cal != null && cal.hasPixelSizeMicrons();
		
		double pixelWidth = useMicrons ? cal.getPixelWidthMicrons() : 1;
		double pixelHeight = useMicrons ? cal.getPixelHeightMicrons() : 1;
		String unit = useMicrons ? GeneralTools.micrometerSymbol() : "px";
		
		boolean doArea = params.getBooleanParameterValue("area");
		boolean doPerimeter = params.getBooleanParameterValue("perimeter");
		boolean doCircularity = params.getBooleanParameterValue("circularity");
		
		ROI roi = (parentObject.hasROI() && parentObject.getROI().isArea()) ? parentObject.getROI() : null;
		if (roi != null) {
			tasks.add(new Runnable() {
	
				@Override
				public void run() {
					try {
						MeasurementList measurementList = parentObject.getMeasurementList();
						
						ROI roi;
						if (parentObject instanceof PathCellObject) {
							
							roi = ((PathCellObject)parentObject).getNucleusROI();
							if (roi != null && roi.isArea())
								addMeasurements(measurementList, roi, "Nucleus Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
							
							roi = parentObject.getROI();
							if (roi != null && roi.isArea())
								addMeasurements(measurementList, roi, "Cell Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
							
						} else {
							roi = parentObject.getROI();
							if (roi != null && roi.isArea())
								addMeasurements(measurementList, roi, "ROI Shape: ", pixelWidth, pixelHeight, unit, doArea, doPerimeter, doCircularity);
						}
						
						measurementList.close();
					} catch (Exception e) {
						e.printStackTrace();
						throw(e);
					}
				}
			});
		}
	}
	

	private static void addMeasurements(final MeasurementList measurementList, final ROI roi, final String prefix, final double pixelWidth, final double pixelHeight, final String unit,
			final boolean doArea, final boolean doPerimeter, final boolean doCircularity) {
		if (doArea)
			measurementList.putMeasurement(prefix + "Area " + unit + "^2", roi.getScaledArea(pixelWidth, pixelHeight));
		if (doPerimeter)
			measurementList.putMeasurement(prefix + "Perimeter " + unit, roi.getScaledLength(pixelWidth, pixelHeight));
		if (doCircularity)
			measurementList.putMeasurement(prefix + "Circularity", RoiTools.getCircularity(roi));		
	}
	
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		ImageServer<? extends T> server = imageData.getServer();
		boolean pixelSizeMicrons = server != null && server.getPixelCalibration().hasPixelSizeMicrons();
		params.getParameters().get("useMicrons").setHidden(!pixelSizeMicrons);
		return params;
	}



	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		return runner.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
	}
	

}
