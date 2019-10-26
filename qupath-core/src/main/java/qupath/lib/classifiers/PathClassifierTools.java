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

package qupath.lib.classifiers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;

/**
 * Static methods to load &amp; run a detection object classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PathClassifierTools {
	
	final private static Logger logger = LoggerFactory.getLogger(PathClassifierTools.class);

	/**
	 * Apply a classifier to the detection objects in a hierarchy.
	 * @param hierarchy
	 * @param classifier
	 */
	public static void runClassifier(final PathObjectHierarchy hierarchy, final PathObjectClassifier classifier) {
		// Apply classifier to everything
		// If we have a TMA grid, do one core at a time
		long startTime = System.currentTimeMillis();
		TMAGrid tmaGrid = hierarchy.getTMAGrid();
		Collection<PathObject> pathObjects = new ArrayList<>();
		int nClassified = 0;
		//			tmaGrid = null;
		if (tmaGrid != null) {
			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
				pathObjects = PathObjectTools.getDescendantObjects(core, pathObjects, PathDetectionObject.class);
				nClassified += classifier.classifyPathObjects(pathObjects);
				pathObjects.clear();
			}
		} else {
			hierarchy.getObjects(pathObjects, PathDetectionObject.class);
			nClassified = classifier.classifyPathObjects(pathObjects);
		}
		long endTime = System.currentTimeMillis();
		logger.info(String.format("Classification time: %.2f seconds", (endTime-startTime)/1000.));
	
		// Fire a change event for all detection objects
		if (nClassified > 0)
			hierarchy.fireObjectClassificationsChangedEvent(classifier, hierarchy.getObjects(null, PathDetectionObject.class));
		else
			logger.warn("No objects classified!");
	}

	/**
	 * Load a classifier that has previously been serialized to a file.
	 * @param file
	 * @return
	 */
	public static PathObjectClassifier loadClassifier(File file) {
		// TODO: Put this into another method
		PathObjectClassifier classifier = null;
		ObjectInputStream inStream = null;
		try {
			inStream = new ObjectInputStream(new FileInputStream(file));
			classifier = (PathObjectClassifier)inStream.readObject();
			inStream.close();
			logger.info(String.format("Reading classifier %s complete!", classifier.toString()));
		} catch (IOException e) {
			logger.error("Error reading classifier", e);
		} catch (ClassNotFoundException e) {
			logger.error("Class missing when reading classifier", e);
		} finally {
			try {
				if (inStream != null)
					inStream.close();
			} catch (IOException e) {
				logger.error("Error closing classifier stream", e);
			}
		}
		return classifier;
	}

	/**
	 * Create a {@link PathObjectClassifier} that applies an array of classifiers, sequentially in order.
	 * @param classifiers
	 */
	public static PathObjectClassifier createCompositeClassifier(final PathObjectClassifier... classifiers) {
		return new CompositeClassifier(Arrays.asList(classifiers));
	}

	/**
	 * Create a {@link PathObjectClassifier} that applies a collection of classifiers, sequentially in order.
	 * @param classifiers
	 */
	public static PathObjectClassifier createCompositeClassifier(final Collection<PathObjectClassifier> classifiers) {
		return new CompositeClassifier(classifiers);
	}
	
	/**
	 * Create an {@link PathObjectClassifier} that (sub)classifies objects by a single intensity measurement.
	 * <p>
	 * Three thresholds may be provided, resulting in objects being classified as Negative, 1+, 2+ or 3+.
	 * Alternatively, if either t2 or t3 is Double.NaN, only the first threshold will be applied and objects will be classified as Negative/Positive only.
	 * <p>
	 * If the objects already have a (non-intensity-based) base classification, this will be retained and a sub-classification applied.
	 * 
	 * @param classSelected if not null, apply sub-classification only to objects with the specified initial base classification
	 * @param intensityMeasurement the object measurement used for thresholding
	 * @param t1 low threshold
	 * @param t2 moderate threshold
	 * @param t3 high threshold
	 * @return
	 */
	public static PathObjectClassifier createIntensityClassifier(final PathClass classSelected, final String intensityMeasurement, final double t1, final double t2, final double t3) {
		return new PathIntensityClassifier(classSelected, intensityMeasurement, t1, t2, t3);
	}

	/**
	 * Create an {@link PathObjectClassifier} that (sub)classifies objects by a single intensity measurement.
	 * <p>
	 * Objects are finally classified as either Positive or Negative. If the objects already have a (non-intensity-based) base classification, 
	 * this will be retained.
	 * 
	 * @param classSelected if not null, apply sub-classification only to objects with the specified initial base classification
	 * @param intensityMeasurement the object measurement used for thresholding
	 * @param threshold objects will be classified as Positive if their corresponding measurement has a value above threshold,
	 * 					otherwise they will be classified as Negative.
	 * @return
	 */
	public static PathObjectClassifier createIntensityClassifier(final PathClass classSelected, final String intensityMeasurement, final double threshold) {
		return new PathIntensityClassifier(classSelected, intensityMeasurement, threshold, Double.NaN, Double.NaN);
	}

	/**
	 * Get a set containing the names of all measurements found in the measurement lists of a specified object collection.
	 * 
	 * @param pathObjects
	 * @return
	 */
	public static Set<String> getAvailableFeatures(final Collection<PathObject> pathObjects) {
		Set<String> featureSet = new LinkedHashSet<>();
		// This has a small optimization that takes into consideration the fact that many objects share references to exactly the same MeasurementLists -
		// so by checking the last list that was added, there is no need to bother the set to add the same thing again.
		List<String> lastNames = null;
		for (PathObject pathObject : pathObjects) {
			if (!pathObject.hasMeasurements())
				continue;
			List<String> list = pathObject.getMeasurementList().getMeasurementNames();
			if (lastNames == null || !lastNames.equals(list))
				featureSet.addAll(list);
			lastNames = list;
		}
		return featureSet;
	}

	/**
	 * Get a set of the represented path classes, i.e. those with at least 1 manually-labelled object.
	 * 
	 * @return
	 */
	public static Set<PathClass> getRepresentedPathClasses(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls) {
		Set<PathClass> pathClassSet = new LinkedHashSet<>();
		for (PathObject pathObject : hierarchy.getObjects(null, cls)) {
			if (pathObject.getPathClass() != null)
				pathClassSet.add(pathObject.getPathClass());
		}
		return pathClassSet;
	}
	
	
	
	/**
	 * Assign cell classifications as positive or negative based upon a specified measurement, using up to 3 intensity bins.
	 * 
	 * An IllegalArgumentException is thrown if &lt; 1 or &gt; 3 intensity thresholds are provided.
	 * 
	 * @param pathObject 		the object to classify.
	 * @param measurementName 	the name of the measurement to use for thresholding.
	 * @param thresholds 		between 1 and 3 intensity thresholds, used to indicate negative/positive, or negative/1+/2+/3+
	 * @return 					the PathClass of the object after running this method.
	 */
	public static PathClass setIntensityClassification(final PathObject pathObject, final String measurementName, final double... thresholds) {
		if (thresholds.length == 0 || thresholds.length > 3)
			throw new IllegalArgumentException("Between 1 and 3 intensity thresholds required!");
		
		PathClass baseClass = PathClassTools.getNonIntensityAncestorClass(pathObject.getPathClass());
		
		// Don't do anything with the 'ignore' class
		if (baseClass == PathClassFactory.getPathClass(StandardPathClasses.IGNORE))
			return pathObject.getPathClass();
		
		double intensityValue = pathObject.getMeasurementList().getMeasurementValue(measurementName);
		
		boolean singleThreshold = thresholds.length == 1;
		
		if (intensityValue < thresholds[0]) {
			pathObject.setPathClass(PathClassFactory.getNegative(baseClass));
		} else {
			if (singleThreshold)
				pathObject.setPathClass(PathClassFactory.getPositive(baseClass));
			else if (thresholds.length >= 3 && intensityValue >= thresholds[2])
				pathObject.setPathClass(PathClassFactory.getThreePlus(baseClass));				
			else if (thresholds.length >= 2 && intensityValue >= thresholds[1])
				pathObject.setPathClass(PathClassFactory.getTwoPlus(baseClass));				
			else if (intensityValue >= thresholds[0])
				pathObject.setPathClass(PathClassFactory.getOnePlus(baseClass));				
		}
		return pathObject.getPathClass();
	}
	
	
	/**
	 * Set the intensity classifications for the specified objects.
	 * 
	 * @param pathObjects
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassifications(final Collection<PathObject> pathObjects, final String measurementName, final double... thresholds) {
		for (PathObject pathObject : pathObjects)
			setIntensityClassification(pathObject, measurementName, thresholds);
	}

	/**
	 * Create a list of channels from classification labels.
	 * Note that the labels must be &geq; 0 or else an {@link IllegalArgumentException} will be thrown.
	 * It is also expected that the labels densely represent integer values from 0 up to some maximum, although this is 
	 * not strictly enforced and channels will be generated from any missing labels.
	 * 
	 * @param classificationLabels
	 * @param ignoredTransparent
	 * @return
	 */
	public static List<ImageChannel> classificationLabelsToChannels(Map<Integer, PathClass> classificationLabels, boolean ignoredTransparent) {
		var labels = classificationLabels.keySet().stream().mapToInt(i -> i).summaryStatistics();
		if (labels.getMin() < 0)
			throw new IllegalArgumentException("Classification labels must be >= 0!");
		int nChannels = labels.getMax() + 1;
		List<ImageChannel> channels = new ArrayList<>();
		int unclassifiedCount = 0;
		for (int i = 0; i < nChannels; i++) {
			var pathClass = classificationLabels.getOrDefault(i, null);
			if (pathClass == null) {
				unclassifiedCount++;
				channels.add(ImageChannel.getInstance("Unclassified " + unclassifiedCount, null));
			} else {
				Integer color = ignoredTransparent && PathClassTools.isIgnoredClass(pathClass) ? null : pathClass.getColor();
				channels.add(ImageChannel.getInstance(pathClass.toString(), color));
			}
		}
		return channels;
	}
	
	
}
