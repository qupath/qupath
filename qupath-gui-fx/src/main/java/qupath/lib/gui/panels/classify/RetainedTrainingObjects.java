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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Class for storing retained objects for multiple images.
 * 
 * Internally, the objects are stored in a map where the image server path is the key.
 * 
 * Within this map, objects are further separated by 'ground truth' PathClass.
 * 
 * @author Pete Bankhead
 *
 */
class RetainedTrainingObjects implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(RetainedTrainingObjects.class);
	
	private Map<String, Map<PathClass, List<PathObject>>> retainedObjectsMap = new TreeMap<>();
	
	public RetainedTrainingObjects() {}
	
	public boolean isEmpty() {
		return retainedObjectsMap.isEmpty();
	}
	
	public void clear() {
		retainedObjectsMap.clear();
	}
	
	public List<PathObject> getAllObjects() {
		List<PathObject> list = new ArrayList<>();
    	for (Map<PathClass, List<PathObject>> value : retainedObjectsMap.values()) {
        	for (Entry<PathClass, List<PathObject>> entry2 : value.entrySet()) {
        		list.addAll(entry2.getValue());
        	}            		
    	}
    	return list;
	}
	
	
	public int countRetainedObjects() {
		int n = 0;
		if (retainedObjectsMap != null) {
			for (Map<PathClass, List<PathObject>> map : retainedObjectsMap.values()) {
				for (List<PathObject> list : map.values()) {
					n += list.size();					
				}
			}
		}
		return n;
	}
	
	
	/**
	 * Get the size of the stored map, i.e. the number of represented images.
	 * 
	 * @return
	 */
	public int size() {
		return retainedObjectsMap.size();
	}
	
	
	/**
	 * Get an unmodifiable version of the map stored internally.
	 * 
	 * Note: the mapped entres are returned as-is.  In other words, they could still be modified
	 * (but almost certainly shouldn't be).
	 * 
	 * @return
	 */
	Map<String, Map<PathClass, List<PathObject>>> getMap() {
		return Collections.unmodifiableMap(retainedObjectsMap);
	}
	
	
	public boolean containsValue(final Map<PathClass, List<PathObject>> value) {
		return retainedObjectsMap.containsValue(value);
	}
	
	
	Map<PathClass, List<PathObject>> put(final String key, final Map<PathClass, List<PathObject>> value) {
		// For detections, all we really care about is storing the measurement lists.
		// The ROIs take up unnecessary space, and the parent/child lists can end up surreptitiously brining 
		// in the whole hierarchy... causing a horrendous memory leak.
		// For that reason, we create new objects and don't their their parents.
		// We *do* retain the unnecessary ROIs in the expectation this won't occupy too much memory... and to give a potential
		// backup way to recover the locations of the objects in an emergency.
		// (We don't assume detections here just in case... although currently all objects that end up here will be detections.
		//  The slightly more conservative code here will still have a memory leak if there are non-detections...)
		
		if (key == null)
			throw new IllegalArgumentException("Cannot retain objects without a key! Do you have a project open?");
		
		// Also, we create a new map to ensure it's sorted
		Map<PathClass, List<PathObject>> newMap = new TreeMap<>();
		for (PathClass pathClass : value.keySet()) {
			List<PathObject> originalList = value.get(pathClass);
			List<PathObject> newList = 
					originalList.stream().map(p -> {
						if (p.isDetection())
							return PathObjects.createDetectionObject(p.getROI(), p.getPathClass(), p.getMeasurementList());
						else {
							logger.debug("Adding non-detection object to retained map: {}", p);
							return p;
						}
						}).collect(Collectors.toList());
			newMap.put(pathClass,  newList);
			value.put(pathClass, newList);
		}
		return retainedObjectsMap.put(key, value);
	}
	
	
	/**
	 * Remove entry from the map.
	 * 
	 * @param key
	 * @return
	 */
	Map<PathClass, List<PathObject>> remove(final String key) {
		return retainedObjectsMap.remove(key);
	}
	
	
	/**
	 * Append retained objects, grouped by classification, to an existing training map.
	 * 
	 * @param map
	 * @return
	 */
	public int addToTrainingMap(final Map<PathClass, List<PathObject>> map) {
		int retainedImageCount = 0;
		for (Entry<String, Map<PathClass, List<PathObject>>> entry : retainedObjectsMap.entrySet()) {
			// Put any retained objects into the map
			Map<PathClass, List<PathObject>> map2 = entry.getValue();
			for (Entry<PathClass, List<PathObject>> entry2 : map2.entrySet()) {
				PathClass pathClassTemp = entry2.getKey();
				List<PathObject> listMain = map.get(pathClassTemp);
				if (listMain == null) {
					listMain = new ArrayList<>();
					map.put(pathClassTemp, listMain);
				}
				listMain.addAll(entry2.getValue());
			}
			retainedImageCount++;
		}
		return retainedImageCount;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject("Training objects v0.1"); // Version
		out.writeObject(retainedObjectsMap);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		Object version = in.readObject(); // Version
		if (!("Training objects v0.1".equals(version)))
			throw new IOException("Version not supported!");
		Object data = in.readObject();
		Map<String, Map<PathClass, List<PathObject>>> mapRead = (Map<String, Map<PathClass, List<PathObject>>>)data;
		
		// Loop through and ensure that we are using the 'current' PathClasses - not the deserialized ones
		for (String key : mapRead.keySet()) {
			Map<PathClass, List<PathObject>> oldMap = mapRead.get(key);
			Map<PathClass, List<PathObject>> newMap = new HashMap<>();
			for (Entry<PathClass, List<PathObject>> entry : oldMap.entrySet()) {
				PathClass pathClass = entry.getKey();
				newMap.put(PathClassFactory.getPathClass(pathClass.getName(), pathClass.getColor()), entry.getValue());
			}
			retainedObjectsMap.put(key, newMap);
		}
	}
	
}