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

package qupath.lib.classifiers;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.PathObject;

/**
 * A (sub)classifier that specializes in assigning intensity classifications.
 * <p>
 * This is generally applied to distinguish positive from negative cells, often
 * after applying another classifier to distinguish cell types.
 * 
 * @author Pete Bankhead
 *
 */
class PathIntensityClassifier implements Serializable, PathObjectClassifier {
	
	private static final long serialVersionUID = 1L;
	
	private long lastModifiedTimestamp;
	
	transient private boolean updateCalled = false;
	
	private PathClass classSelected;
	private String intensityMeasurement;
	private double t1, t2, t3;
	private boolean singleThreshold = false;
	
	static DecimalFormat df = new DecimalFormat("#.###");
	
	PathIntensityClassifier(final PathClass classSelected, final String intensityMeasurement, final double t1, final double t2, final double t3) {
		this.classSelected = classSelected;
		this.intensityMeasurement = intensityMeasurement;
		this.t1 = t1;
		this.t2 = t2;
		this.t3 = t3;
		singleThreshold = Double.isNaN(t2 + t3);
		lastModifiedTimestamp = System.currentTimeMillis();
	}
	
	PathIntensityClassifier(final PathClass classSelected, final String intensityMeasurement, final double threshold) {
		this(classSelected, intensityMeasurement, threshold, Double.NaN, Double.NaN);
	}
	
	/**
	 * Classify the intensity of a collection of objects, based on the current thresholds.
	 * 
	 * @param pathObjects
	 */
	@Override
	public int classifyPathObjects(Collection<PathObject> pathObjects) {
		int counter = 0;
		
//		PathClass classPositive = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.POSITIVE);
//		PathClass classNegative = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.NEGATIVE);
//		PathClass classOnePlus = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.ONE_PLUS);
//		PathClass classTwoPlus = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.TWO_PLUS);
//		PathClass classThreePlus = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.THREE_PLUS);
		
		// If there is no class specified, apply to all
		if (classSelected == null) {
			if (singleThreshold) {
				PathClassifierTools.setIntensityClassifications(pathObjects, intensityMeasurement, t1);
			} else {
				PathClassifierTools.setIntensityClassifications(pathObjects, intensityMeasurement, t1, t2, t3);
			}
			return pathObjects.size();
		}
		
		// Ensure we have the correct singleton class so we can do an equality check
		classSelected = PathClassFactory.getSingletonPathClass(classSelected);
		
		PathClass classPositive = PathClassFactory.getPositive(classSelected);
		PathClass classNegative = PathClassFactory.getNegative(classSelected);
		PathClass classOnePlus = PathClassFactory.getOnePlus(classSelected);
		PathClass classTwoPlus = PathClassFactory.getTwoPlus(classSelected);
		PathClass classThreePlus = PathClassFactory.getThreePlus(classSelected);

		// Because the classifications are really sub-classifications, retain the same probability
		for (PathObject pathObjectTemp : pathObjects) {
			if (classSelected == null || pathObjectTemp.getPathClass() == null || !(pathObjectTemp.getPathClass().isDerivedFrom(classSelected) || pathObjectTemp.getPathClass().getName().equals(classSelected.getName())))
//			if (classSelected == null || pathObjectTemp.getPathClass() == null || !pathObjectTemp.getPathClass().getName().equals(classSelected.getName()))
				continue;
			Object value = pathObjectTemp.getMeasurementList().getMeasurementValue(intensityMeasurement);
			if (!(value instanceof Number))
				continue;
			double val = ((Number)value).doubleValue();
			if (singleThreshold) {
				if (val > t1)
					pathObjectTemp.setPathClass(classPositive, pathObjectTemp.getClassProbability());
				else
					pathObjectTemp.setPathClass(classNegative, pathObjectTemp.getClassProbability());				
			} else {
				if (val > t3)
					pathObjectTemp.setPathClass(classThreePlus, pathObjectTemp.getClassProbability());
				else if (val > t2)
					pathObjectTemp.setPathClass(classTwoPlus, pathObjectTemp.getClassProbability());
				else if (val > t1)
					pathObjectTemp.setPathClass(classOnePlus, pathObjectTemp.getClassProbability());
				else
					pathObjectTemp.setPathClass(classNegative, pathObjectTemp.getClassProbability());
			}
			counter++;
		}
		return counter;
	}
	
	
	@Override
	public String toString() {
		return String.format("Intensity classifier: %s, %s, Thresholds [%.2f, %.2f, %.2f]", classSelected, intensityMeasurement, t1, t2, t3);
	}

	
	@Override
	public List<String> getRequiredMeasurements() {
		if (intensityMeasurement == null)
			return null;
		return Collections.singletonList(intensityMeasurement);
	}

	
	@Override
	public Collection<PathClass> getPathClasses() {
		if (singleThreshold) {
			return Arrays.asList(
					PathClassFactory.getPositive(classSelected),
					PathClassFactory.getNegative(classSelected)
					);
		}
		return Arrays.asList(
				PathClassFactory.getNegative(classSelected),
				PathClassFactory.getOnePlus(classSelected),
				PathClassFactory.getTwoPlus(classSelected),
				PathClassFactory.getThreePlus(classSelected)
				);
	}

	
	@Override
	public boolean isValid() {
		return !GeneralTools.blankString(intensityMeasurement, true) && !Double.isNaN(t1);
	}

	
	@Override
	public String getDescription() {
		if (!isValid())
			return "Invalid intensity classifier";
		StringBuilder sb = new StringBuilder();
		sb.append("Intensity classifier").append("\n\n");
		sb.append("Input class:\t").append(classSelected == null ? "All" : classSelected.getName()).append("\n\n");
		sb.append("Thresholded measurement:\t").append(intensityMeasurement).append("\n\n");
		if (singleThreshold)
			sb.append("Thresholded value:\t").append(df.format(t1));
		else
			sb.append("Thresholded values:\t").append(df.format(t1)).append(", ").append(df.format(t2)).append(", ").append(df.format(t3));			
//			sb.append(String.format("Thresholded value:\t%.3f, %.3f, %.3f", t1, t2, t3));
//			sb.append("Thresholded values:\t").append(t1).append(", ").append(t2).append(", ").append(t3);			
		return sb.toString();
	}
	
	
	@Override
	public long getLastModifiedTimestamp() {
		return lastModifiedTimestamp;
	}
	
	
	@Override
	public String getName() {
		return "Intensity classifier";
	}

//	/**
//	 * Does nothing - intensity classifiers require no training.
//	 */
//	@Override
//	public void updateClassifier(ImageData<?> imageData, List<String> measurements, int maxTrainingInstances) {}

	@Override
	public boolean supportsAutoUpdate() {
		return true;
	}

	/**
	 * Does nothing - intensity classifiers require no training.
	 * <p>
	 * Returns true if this is the first time updateClassifier was called, false otherwise.
	 * 
	 */
	@Override
	public boolean updateClassifier(Map<PathClass, List<PathObject>> map, List<String> measurements, Normalization normalization) {
		boolean previouslyUpdated = updateCalled;
		updateCalled = true;
		return !previouslyUpdated;
	}
	

}
