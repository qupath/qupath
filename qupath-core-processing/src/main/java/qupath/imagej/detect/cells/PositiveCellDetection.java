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

package qupath.imagej.detect.cells;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;

/**
 * Alternative implementation of {@link WatershedCellDetection} that automatically applies 1 or 3 intensity thresholds to classify cells.
 * 
 * @author Pete Bankhead
 *
 */
public class PositiveCellDetection extends WatershedCellDetection {
	
	/**
	 * Default constructor.
	 */
	public PositiveCellDetection() {
		super();
		params.addTitleParameter("Intensity threshold parameters");
		params.addChoiceParameter("thresholdCompartment", "Score compartment", "Nucleus: DAB OD mean",
				Arrays.asList("Nucleus: DAB OD mean", "Nucleus: DAB OD max",
						"Cytoplasm: DAB OD mean", "Cytoplasm: DAB OD max",
						"Cell: DAB OD mean", "Cell: DAB OD max"));
		params.addDoubleParameter("thresholdPositive1", "Threshold 1+", 0.2, null, 0, 1.5, "Low positive intensity threshold");
		params.addDoubleParameter("thresholdPositive2", "Threshold 2+", 0.4, null, 0, 1.5, "Moderate positive intensity threshold");
		params.addDoubleParameter("thresholdPositive3", "Threshold 3+", 0.6, null, 0, 1.5, "High positive intensity threshold");
		params.addBooleanParameter("singleThreshold", "Single threshold", true);
	}
	
	
	@Override
	public String getName() {
		return "Positive cell detection";
	}
	
	
	/**
	 * Wrap the detector to apply any required classification.
	 */
	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		ObjectDetector<BufferedImage> detector = super.createDetector(imageData, params);
		return new DetectorWrapper<>(detector);
	}
	
	
	
	static class DetectorWrapper<T> implements ObjectDetector<T> {
		
		private ObjectDetector<T> detector;
			
		public DetectorWrapper(ObjectDetector<T> detector) {
			this.detector = detector;
		}

		@Override
		public Collection<PathObject> runDetection(ImageData<T> imageData, ParameterList params, ROI pathROI) throws IOException {
			Collection<PathObject> detections = detector.runDetection(imageData, params, pathROI);
			// Apply intensity classifications
			String measurement = (String)params.getChoiceParameterValue("thresholdCompartment");
			double threshold1 = params.getDoubleParameterValue("thresholdPositive1");
			double threshold2 = params.getDoubleParameterValue("thresholdPositive2");
			double threshold3 = params.getDoubleParameterValue("thresholdPositive3");
			boolean singleThreshold = params.getBooleanParameterValue("singleThreshold");
			for (PathObject pathObject : detections) {
				double val = pathObject.getMeasurementList().getMeasurementValue(measurement);
				if (singleThreshold) {
					if (val >= threshold1) {
						pathObject.setPathClass(PathClassFactory.getPositive(pathObject.getPathClass()));
					} else {
						pathObject.setPathClass(PathClassFactory.getNegative(pathObject.getPathClass()));
					}
				} else {
					if (val >= threshold3) {
						pathObject.setPathClass(PathClassFactory.getThreePlus(pathObject.getPathClass()));
					} else if (val >= threshold2){
						pathObject.setPathClass(PathClassFactory.getTwoPlus(pathObject.getPathClass()));
					} else if (val >= threshold1){
						pathObject.setPathClass(PathClassFactory.getOnePlus(pathObject.getPathClass()));
					} else
						pathObject.setPathClass(PathClassFactory.getNegative(pathObject.getPathClass()));
				}
			}
			return detections;
		}

		@Override
		public String getLastResultsDescription() {
			return detector.getLastResultsDescription();
		}
		
	}

}