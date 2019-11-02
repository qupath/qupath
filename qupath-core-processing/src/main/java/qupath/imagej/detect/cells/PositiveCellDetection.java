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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import qupath.lib.color.ColorDeconvolutionStains;
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
	}
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		if (parametersInitialized)
			return super.getDefaultParameterList(imageData);
		else {
			super.getDefaultParameterList(imageData);
			params.addTitleParameter("Intensity threshold parameters");
			var stains = imageData.getColorDeconvolutionStains();
			Set<String> channels = new LinkedHashSet<>();
			if (stains != null) {
				for (int i = 1; i <= 3; i++) {
					var stain = stains.getStain(i);
					if (!ColorDeconvolutionStains.isHematoxylin(stain) && !stain.isResidual())
						channels.add(stain.getName() + " OD");
				}
			} else {
				var server = imageData.getServer();
				for (var channel : server.getMetadata().getChannels())
					channels.add(channel.getName());
			}
			List<String> choices = new ArrayList<>();
			for (var channel : channels) {
				choices.add("Nucleus: " + channel + " mean");
				choices.add("Nucleus: " + channel + " max");
				choices.add("Cytoplasm: " + channel + " mean");
				choices.add("Cytoplasm: " + channel + " max");
				choices.add("Cell: " + channel + " mean");
				choices.add("Cell: " + channel + " max");
			}
			var server = imageData.getServer();
			var type = server.getMetadata().getPixelType();
			// Determine appropriate starting thresholds & maxima
			double t1 = 0.2;
			double tMax = 1.5;
			if (stains == null) {
				if (!type.isFloatingPoint()) {
					if (type.getBytesPerPixel() <= 1)
						t1 = 10;
					else
						t1 = 100;
					tMax = Math.min(10000, Math.pow(2, type.getBitsPerPixel()) - 1);
				}
			}
			params.addChoiceParameter("thresholdCompartment", "Score compartment", choices.get(0), choices, "Select the intensity measurement to threshold");
//			params.addChoiceParameter("thresholdCompartment", "Score compartment", "Nucleus: DAB OD mean",
//					Arrays.asList("Nucleus: DAB OD mean", "Nucleus: DAB OD max",
//							"Cytoplasm: DAB OD mean", "Cytoplasm: DAB OD max",
//							"Cell: DAB OD mean", "Cell: DAB OD max"));
			params.addDoubleParameter("thresholdPositive1", "Threshold 1+", t1, null, 0, tMax, "Low positive intensity threshold");
			params.addDoubleParameter("thresholdPositive2", "Threshold 2+", t1*2, null, 0, tMax, "Moderate positive intensity threshold");
			params.addDoubleParameter("thresholdPositive3", "Threshold 3+", t1*3, null, 0, tMax, "High positive intensity threshold");
			params.addBooleanParameter("singleThreshold", "Single threshold", true);
		}
		return params;
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