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

package qupath.lib.gui.panels.classify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PointsROI;

/**
 * Collection of static methods to help with object classification.
 * 
 * @author Pete Bankhead
 *
 */
class PathClassificationLabellingHelper {
	
	final private static Logger logger = LoggerFactory.getLogger(PathClassificationLabellingHelper.class);
	
	/**
	 * Enum representing different ways of extracting a subset of elements from a collection.
	 */
	public enum SplitType {
		/**
		 * Extract the first samples in the collection
		 */
		FIRST_SAMPLES,
		
		/**
		 * Extract the last samples in the collection
		 */
		LAST_SAMPLES,
		
		/**
		 * Extract samples randomly, without replacement
		 */
		RANDOM_NO_REPLACEMENT,
		
		/**
		 * Extract samples randomly, with replacement
		 */
		RANDOM_WITH_REPLACEMENT,
		
		/**
		 * Extract samples with (approximately) equal separation between them
		 */
		EQUIDISTANT;
		
		@Override
		public String toString() {
			switch (this) {
			case EQUIDISTANT:
				return "Equally-spaced";
			case FIRST_SAMPLES:
				return "First samples";
			case LAST_SAMPLES:
				return "Last samples";
			case RANDOM_NO_REPLACEMENT:
				return "Random (no replacement)";
			case RANDOM_WITH_REPLACEMENT:
				return "Random (with replacement)";
			default:
				throw new IllegalArgumentException("Unknown SplitType!");
			}
		}
	};
	
	
	private static Collection<PathObject> getAnnotations(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return Collections.emptyList();
		else
			return hierarchy.getAnnotationObjects();
	}
	

	/**
	 * Get a map of training data, based on the child objects of some classified annotations.
	 * 
	 * @param hierarchy the hierarchy containing all the objects and annotations.
	 * @param pointsOnly if true, only Point annotations will be used for training.

	 * @return
	 */
	public static Map<PathClass, List<PathObject>> getClassificationMap(final PathObjectHierarchy hierarchy, final boolean pointsOnly) {
		Map<PathClass, List<PathObject>> classifications = new TreeMap<>();
		
		// Get the annotations & filter out those that are useful
		List<PathObject> annotations = new ArrayList<>(getAnnotations(hierarchy));
		Iterator<PathObject> iter = annotations.iterator();
		while (iter.hasNext()) {
			PathObject pathObject = iter.next();
			// We need a PathClass, and may need to only include points
			if (pathObject.getPathClass() == null || pathObject.getPathClass() == PathClassFactory.getPathClass(StandardPathClasses.REGION) ||
					(pointsOnly && !PathObjectTools.hasPointROI(pathObject)) || (!PathObjectTools.hasPointROI(pathObject) && !pathObject.hasChildren()))
				iter.remove();
			else
				classifications.put(pathObject.getPathClass(), new ArrayList<>());
		}

		
		// Slightly strange move, admittedly, but sort the annotations somehow... anyhow
		// The reason is that training that involves randomness should always receive the training
		// data in a reproducible order, otherwise it can produce different results each time
		// even when the only difference is that completely irrelevant objects have been added/removed
		// from the hierarchy
		if (annotations.size() > 1) {
			annotations.sort(new Comparator<PathObject>() {
				@Override
				public int compare(PathObject o1, PathObject o2) {
					PathAnnotationObject p1 = (PathAnnotationObject)o1;
					PathAnnotationObject p2 = (PathAnnotationObject)o2;
					int comp = 0;
					if (p1.hasROI()) {
						if (p2.hasROI()) {
							comp = Double.compare(p1.getROI().getCentroidY(), p2.getROI().getCentroidY());
							if (comp == 0)
								comp = Double.compare(p1.getROI().getCentroidX(), p2.getROI().getCentroidX());
							if (comp == 0)
								comp = p1.getROI().toString().compareTo(p2.getROI().toString());
						}
					}
					if (comp == 0)
						return Integer.compare(o1.hashCode(), o2.hashCode());
					else
						return comp;
				}
			});
		}
		
		
		
//		StringBuilder sb = new StringBuilder("DETECTIONS:\t");
		for (PathObject pathObject : annotations) {
			PathClass pathClass = pathObject.getPathClass();
			List<PathObject> list = classifications.get(pathClass);
			// TODO: Consider using overlaps, rather than direct child objects
			list.addAll(pathObject.getChildObjects());
//			sb.append(list.size() + ", ");
			if (PathObjectTools.hasPointROI(pathObject)) {
				for (Point2 p : ((PointsROI)pathObject.getROI()).getAllPoints()) {
					// TODO: Pay attention to z & t position!
					Collection<PathObject> pathObjectsTemp = PathObjectTools.getObjectsForLocation(hierarchy, p.getX(), p.getY(), 0, 0, -1);
					pathObjectsTemp = PathObjectTools.getObjectsOfClass(pathObjectsTemp, PathDetectionObject.class);
					list.removeAll(pathObjectsTemp); // Clumsy way to avoid duplicates...
					list.addAll(pathObjectsTemp);
				}
			}
		}
		
		for (Entry<PathClass, List<PathObject>> entry : classifications.entrySet()) {
			logger.info(entry.getKey() + ": " + entry.getValue().size());
		}
		return classifications;
	}

	
	
	
	/**
	 * Resample a training map (PathClass label &amp; lists of PathObjects that should have the specified classifications) so that
	 * it contains only a specified proportion of entries.
	 * 
	 * @param map
	 * @param splitType
	 * @param proportion between 0 (empty map) and 1
	 * @return
	 */
	public static Map<PathClass, List<PathObject>> resampleClassificationMap(final Map<PathClass, List<PathObject>> map, final SplitType splitType, final double proportion, final long seed) {
		if (proportion > 1 || proportion == 0)
			throw new IllegalArgumentException("Proportion of samples to use for training cannot be < 0 or > 1!");
		if (proportion == 0)
			return Collections.emptyMap();
		if (proportion == 1 && splitType != SplitType.RANDOM_WITH_REPLACEMENT)
			return map;
		
		int n = 0;
		for (List<PathObject> temp : map.values())
			n += temp.size();
		int maxTrainingInstances = (int)(n * proportion + .5);
		
		List<TrainingEntry> entries = new ArrayList<>();
		for (Entry<PathClass, List<PathObject>> entry : map.entrySet()) {
			
			List<PathObject> list = entry.getValue();
			if (splitType == SplitType.LAST_SAMPLES)
				Collections.reverse(list);
			
			int maxCount = Integer.MAX_VALUE;
			if (splitType == SplitType.LAST_SAMPLES || splitType == SplitType.FIRST_SAMPLES)
				maxCount = (int)(list.size() * proportion + .9);
			
			PathClass pathClass = entry.getKey();
			int count = 0;
			for (PathObject temp : list) {
				entries.add(new TrainingEntry(pathClass, temp));
				count++;
				if (count >= maxCount)
					break;
			}
		}
		
		Map<PathClass, List<PathObject>> map2 = new TreeMap<>();
		
		Random random = new Random(seed);
		
		switch (splitType) {
		case EQUIDISTANT:
			double increment = 1.0 / proportion;
			for (double i = 0; i < entries.size(); i += increment) {
				int ind = (int)i;
				TrainingEntry entry = entries.get(ind);
				addToMap(map2, entry.pathClass, entry.pathObject);
			};
			return map2;
//			return resampleClassificationMapEquidistantly(map, proportion);
		case RANDOM_NO_REPLACEMENT:
			Collections.shuffle(entries, random);
			// Fall through...
		case FIRST_SAMPLES:
			// Fall through...
		case LAST_SAMPLES:
			for (int i = 0; i < maxTrainingInstances; i++) {
				TrainingEntry entry = entries.get(i);
				addToMap(map2, entry.pathClass, entry.pathObject);
			};
			return map2;
		case RANDOM_WITH_REPLACEMENT:
			for (int i = 0; i < maxTrainingInstances; i++) {
				int ind = random.nextInt(n);
				TrainingEntry entry = entries.get(ind);
				addToMap(map2, entry.pathClass, entry.pathObject);
			};
			return map2;
		default:
			break;
		}
		return map;
	}
	
	
	private static void addToMap(final Map<PathClass, List<PathObject>> map, final PathClass pathClass, final PathObject pathObject) {
		List<PathObject> list = map.get(pathClass);
		if (list == null) {
			list = new ArrayList<>();
			map.put(pathClass, list);
		}
		list.add(pathObject);
	}
	
	
	
	static class TrainingEntry {
		
		final PathClass pathClass;
		final PathObject pathObject;
		
		TrainingEntry(final PathClass pathClass, final PathObject pathObject) {
			this.pathClass = pathClass;
			this.pathObject = pathObject;
		}
		
	}
	
	
	
	/**
	 * In a map where values are lists of PathObjects, count the total number of entries in all the stored lists (directly; child objects ignored).
	 * 
	 * @param map
	 * @return
	 */
	public static int countObjectsInMap(final Map<?, ? extends Collection<? extends PathObject>> map) {
		return map.values().stream().mapToInt(v -> v.size()).sum();
//		int n = 0;
//		for (Collection<?> list : map.values())
//			n += list.size();
//		return n;
	}
	
}
