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

package qupath.lib.objects;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.interfaces.PathPoints;
import qupath.lib.roi.interfaces.ROI;

/**
 * Fundamental object of interest in QuPath.
 * 
 * Used as a base class for annotations, detections, cells, TMA cores, tiles...
 * 
 * @author Pete Bankhead
 *
 */
public abstract class PathObject implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	protected static int DEFAULT_MEASUREMENT_LIST_CAPACITY = 16;

	private PathObject parent = null;
	private List<PathObject> childList = null; // Collections.synchronizedList(new ArrayList<>(0));
	private MeasurementList measurements = null;
	
	private MetadataMap metadata = null;
	
	private String name = null;
	private Integer color;

	transient private Collection<PathObject> cachedUnmodifiableChildren = null;
	

	/**
	 * Create a PathObject with a specific measurement list.
	 * This can be used e.g. to create an object with a more memory-efficient list,
	 * at the cost of generality/mutability.
	 * 
	 * @param measurements
	 */
	public PathObject(MeasurementList measurements) {
		this.measurements = measurements;
	}

	public PathObject() {}
	
	
	public PathObject getParent() {
		return parent;
	}
	
//	public abstract String getType();
	
	/**
	 * The level of the object in a hierarchy.
	 * If the object has no parent, this is 0.
	 * Otherwise, it is equal to parent.getLevel() + 1.
	 * 
	 * @return
	 */
	public int getLevel() {
		if (parent == null)
			return 0;
		return parent.getLevel() + 1;
	}
	
//	public void setParent(PathObject pathObject) {
//		if (parent != null && parent != pathObject && parent.getPathObjectList().contains(this))
//			parent.getPathObjectList().remove(this);
//		this.parent = pathObject;
//	}	
	
	public boolean isRootObject() {
		return this instanceof PathRootObject;
	}
		
	public boolean isPoint() {
		return getROI() instanceof PointsROI; // TODO: Check the 'isPoint' method of PathObject
	}
	
	public MeasurementList getMeasurementList() {
		if (measurements == null)
			measurements = createEmptyMeasurementList();
		return measurements;
	}
	
	/**
	 * Create a new MeasurementList of the preferred type for this object.
	 * 
	 * This will be called whenever a MeasurementList is requested, if one is not already stored.
	 * 
	 * Subclasses can use this method to create more efficient MeasurementList implementations if required.
	 * 
	 * @return
	 */
	protected MeasurementList createEmptyMeasurementList() {
		MeasurementList list = MeasurementListFactory.createMeasurementList(16, MeasurementList.TYPE.GENERAL);
		return list;
	}
	
	public int nMeasurements() {
		if (measurements == null)
			return 0;
		return measurements.size();
	}
	
	protected String objectCountPostfix() {
		ROI pathROI = getROI();
		if (pathROI instanceof PathPoints) {
			int nPoints = ((PathPoints)pathROI).getNPoints();
			if (nPoints == 1)
				return " - 1 point";
			else
				return String.format(" - %d points", nPoints);
		}
		if (!hasChildren())
			return "";
		if (childList.size() == 1)
			return " - 1 object";
		else
			return " - " + childList.size() + " objects";
	}

	@Override
	public String toString() {
		String postfix;
		if (getPathClass() == null)
			postfix = objectCountPostfix();
		else
			postfix = " (" + getPathClass().getName() + ")";
		if (getName() != null)
			return getName() + postfix;
		if (getROI() != null)
			return getROI().toString() + postfix;
		return "Unassigned" + postfix; // Entire image
	}
	
	public void addPathObject(PathObject pathObject) {
		if (pathObject instanceof PathRootObject) //J
			throw new IllegalArgumentException("PathRootObject cannot be added as child to another PathObject"); //J 
		addPathObjectImpl(pathObject);
	}
	
	private void addPathObjectImpl(PathObject pathObject) {
		ensureChildList(nChildObjects() + 1);
		// Make sure the object is removed from any other parent
		if (pathObject.parent != this) {
			if (pathObject.parent != null && pathObject.parent.childList != null)
				pathObject.parent.childList.remove(pathObject);
			pathObject.parent = this;
		}
		childList.add(pathObject);
		
		if (!isRootObject())
			Collections.sort(childList, DefaultPathObjectComparator.getInstance());
	}

	
	private void addPathObjectsImpl(Collection<? extends PathObject> pathObjects) {
		if (pathObjects == null || pathObjects.isEmpty())
			return;
		ensureChildList(nChildObjects() + pathObjects.size());
		// Make sure the object is removed from any other parent
		Iterator<? extends PathObject> iter = pathObjects.iterator();
		PathObject lastBatchRemoveParent = null;
		List<PathObject> batchRemove = new ArrayList<>(pathObjects.size());
		boolean isChildList = false;
		while (iter.hasNext()) {
			PathObject pathObject = iter.next();
			PathObject previousParent = pathObject.parent;
			if (previousParent == this)
				continue;
			// Remove objects from previous parent
			if (previousParent != null && previousParent.childList != null) {
				// Check if we were provided with the full child list directly - if so, no need to keep iterating
				// Warning! It's crucial to check both for the Collection returned from getChildObjects(), since this may be 
				// an unmodifiable collection backed by something else.  It's troublesome to predict how equals/hashCode will deal with this
				// (e.g. passing through or not... Collections.unmodifiableList behaves differently from Collections.unmodifiableCollection)
				if (previousParent.getChildObjects().equals(pathObjects) || previousParent.childList.equals(pathObjects)) {
					isChildList = true;
					lastBatchRemoveParent = previousParent;
					break;
				}
				// Keep a record of the object to remove
				if (lastBatchRemoveParent != previousParent) {
					if (lastBatchRemoveParent != null && !batchRemove.isEmpty()) {
						removeAllQuickly(lastBatchRemoveParent.childList, batchRemove);
						batchRemove.clear();
					}
				}
				lastBatchRemoveParent = previousParent;
				batchRemove.add(pathObject);
			}
			// Set the parent for the new object
			pathObject.parent = this;
		}
		// Perform any final batch removal that are necessary
		if (isChildList) {
			for (PathObject child : pathObjects)
				child.parent = this;
		} else if (lastBatchRemoveParent != null && !batchRemove.isEmpty())
			removeAllQuickly(lastBatchRemoveParent.childList, batchRemove);
		// Add to the current child list
		childList.addAll(pathObjects);
		if (isChildList)
			lastBatchRemoveParent.childList.clear();
		
//		// Don't sort TMA cores!
//		for (PathObject pathObject : childList) {
//			if (pathObject instanceof TMACoreObject) {
//				return;
//			}
//		}
		// Sort if we aren't the root object
		if (!this.isRootObject())
			Collections.sort(childList, DefaultPathObjectComparator.getInstance());
	}
	
	
	/**
	 * Remove all items from a list, but optionally using a (temporary) set 
	 * to improve performance.
	 * 
	 * @param list
	 * @param toRemove
	 */
	private static <T> void removeAllQuickly(List<T> list, Collection<T> toRemove) {
		// This is rather implementation-specific, based on how ArrayLists do their object removal.
		// In some implementations it might be better to switch the list to a set temporarily?
		int size = 10;
		if (!(toRemove instanceof Set)  && toRemove.size() > size) {
			toRemove = new HashSet<>(toRemove);
		}
		list.removeAll(toRemove);
	}
	
	
//	public void addPathObjects(int index, Collection<? extends PathObject> pathObjects) {
//		if (pathObjects == null || pathObjects.isEmpty())
//			return;
//		ensureChildList(pathObjects.size());
//		// Make sure the object is removed from any other parent
//		Iterator<? extends PathObject> iter = pathObjects.iterator();
//		boolean isChildList = false;
//		PathObject previousParent = null;
//		while (iter.hasNext()) {
//			PathObject pathObject = iter.next();
//			previousParent = pathObject.parent;
//			if (pathObject.parent != this) {
//				// Remove objects from previous parent
//				if (previousParent != null && previousParent.childList != null) {
//					// Check if we were given the list directly... if so, remove using the iterator
////					if (pathObjects == previousParent.childList)
//					isChildList = pathObjects.equals(previousParent.childList);
//					if (!isChildList) {
//						// Should be able to remove from previous parent without a concurrent exception
////						logger.info("Calling remove " + pathObject);
//						previousParent.childList.remove(pathObject);
//					}
//				}
//				// Set the parent
//				pathObject.parent = this;
//			}
//		}
////		logger.info("Adding all " + pathObjects.size());
//		childList.addAll(index, pathObjects);
//		// If we have all the children of a pathObject, clear that objects children
//		if (isChildList)
//			previousParent.childList.clear();
//		
////		for (PathObject pathObject : pathObjects)
////			addPathObject(index++, pathObject);
//	}
	
	public void addPathObjects(Collection<? extends PathObject> pathObjects) {
		addPathObjectsImpl(pathObjects);
	}

	public void removePathObject(PathObject pathObject) {
		if (!hasChildren())
			return;
		if (pathObject.parent == this)
			pathObject.parent = null; //.setParent(null);
		childList.remove(pathObject);
	}
	
	public void removePathObjects(Collection<PathObject> pathObjects) {
		if (!hasChildren())
			return;
		for (PathObject pathObject : pathObjects) {
			if (pathObject.parent == this)
				pathObject.parent = null;
		}
		removeAllQuickly(childList, pathObjects);
	}
	
	public void clearPathObjects() {
		if (!hasChildren())
			return;
		for (PathObject pathObject : childList) {
			if (pathObject.parent == this)
				pathObject.parent = null;
		}
		childList.clear();
	}
	
	public int nChildObjects() {
		return childList == null ? 0 : childList.size();
	}
	
	public boolean hasChildren() {
		return childList != null && !childList.isEmpty();
	}
	
	public boolean hasROI() {
		return getROI() != null;
	}
	
	public boolean isAnnotation() {
		return this instanceof PathAnnotationObject;
	}
	
	public boolean isDetection() {
		return this instanceof PathDetectionObject;
	}
	
	public boolean hasMeasurements() {
		return measurements != null && !measurements.isEmpty();
	}
	
//	public boolean containsAllMeasurements(Collection<String> measurementNames) {
//		if (measurements == null && !measurementNames.isEmpty())
//			return false;
//		return measurements.containsAllMeasurements(measurementNames);
//	}
	
	public boolean isTMACore() {
		return this instanceof TMACoreObject;
	}
	
	public boolean isTile() {
		return this instanceof PathTileObject;
	}
	
	/**
	 * Flag indicating that the object is editable, and therefore if it has a ROI this should not be moved or resized 
	 * (e.g. because child objects depend upon it).
	 * @return
	 */
	public abstract boolean isEditable();
	
	/**
	 * Get a list of child objects.
	 * In the current implementation, this is immutable - it cannot be modified directly!
	 * @return
	 */
	public Collection<PathObject> getChildObjects() {
		if (childList == null)
			return Collections.emptyList();
		if (cachedUnmodifiableChildren == null)
			cachedUnmodifiableChildren = Collections.unmodifiableList(childList); // Could use collection (but be careful about hashcode & equals!)
		return cachedUnmodifiableChildren;
	}
	
	public abstract PathClass getPathClass();
		
	public void setPathClass(PathClass pc) {
		setPathClass(pc, Double.NaN);
	}

	public abstract void setPathClass(PathClass pathClass, double classProbability);
	
	public abstract double getClassProbability();
	
	public String getDisplayedName() {
		String nameDisplayed = name;
		if (nameDisplayed == null) {
			PathClass pathClass = getPathClass();
			if (pathClass != null)
				nameDisplayed = pathClass.getName();
			else
				nameDisplayed = getClass().getSimpleName();
		}
		if (getParent() != null && getParent().isTMACore())
			nameDisplayed = getParent().getDisplayedName() + " - " + nameDisplayed;
		return nameDisplayed;
	}
	
//	public String getDisplayedName() {
//		if (name == null) {
//			PathClass pathClass = getPathClass();
//			if (pathClass != null)
//				return pathClass.getName();
//			else
//				return getType();
//		}
//		return name;
//	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public abstract ROI getROI();
	
	/**
	 * Return any stored color as a packed RGB value - may be null if no color has been set
	 * @return
	 */
	public Integer getColorRGB() {
		return color;
	}
	
	public void setColorRGB(Integer color) {
		this.color = color;
	}
	
	
	/**
	 * Ensure that we have a child list with a minimum capacity.
	 * 
	 * @param capacity
	 */
	void ensureChildList(int capacity) {
		if (childList == null)
			childList = new ArrayList<>(capacity);
	}
	
	/**
	 * Store a metadata value.
	 * 
	 * Note: This should be used with caution; for objects that could be plentiful (e.g. detections) it is likely
	 * to be unwise to store any metadata values, since these can't be stored particularly efficiently - and
	 * therefore this could lead to far too high memory requirements.
	 * 
	 * If metadata is never stored for an object, no storage object is created - only a null reference.
	 * 
	 * Therefore the intention is that some newly-defined PathObject classes may take advantage of this mechanism and expose their
	 * own API for getting/setting values, backed-up by this store (which takes care of serialization/deserialization).
	 * However class definitions can also avoid making any use of this whatsoever if it's expected that it could lead to too much 
	 * memory being required.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @see #retrieveMetadataValue
	 */
	protected Object storeMetadataValue(final String key, final String value) {
		if (metadata == null)
			metadata = new MetadataMap();
		return metadata.put(key, value);
	}
	
	/**
	 * Get a metadata value.
	 * 
	 * @param key
	 * @return the metadata value if set, or null if not
	 * 
	 * @see #storeMetadataValue
	 */
	protected Object retrieveMetadataValue(final String key) {
		return metadata == null ? null : metadata.get(key);
	}
	
	/**
	 * Get the set of metadata keys.
	 * 
	 * @return
	 */
	protected Set<String> retrieveMetadataKeys() {
		return metadata == null ? Collections.emptySet() : metadata.keySet();
	}
	
	/**
	 * Get an unmodifiable map of the metadata.
	 * 
	 * @return
	 */
	protected Map<String, String> getUnmodifiableMetadataMap() {
		return metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
	}
	
	
	protected void clearMetadataMap() {
		if (metadata != null)
			metadata.clear();
	}
	
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeObject(name);
		out.writeObject(color);
		
		if (metadata != null)
			out.writeObject(metadata);

		out.writeObject(measurements);
		int n = nChildObjects();
		out.writeInt(n);
		if (n > 0) {
			for (PathObject pathObject : childList) {
				out.writeObject(pathObject);
			}
		}
		
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		name = (String)in.readObject();
		
		// Integer check so that legacy (pre-release) data that use Java's AWT Color objects doesn't immediately break
		// (Could try parsing from Java's AWT Color... but shouldn't need to - it hasn't been used for a long time)
		Object colorObject = in.readObject();
		if (colorObject instanceof Integer)
			color = (Integer)colorObject;
		
		// Read the next object carefully, to help deal with changes in object specifications
		Object nextObject = in.readObject();
		
		// Read metadata, if we have it
		if (nextObject instanceof MetadataMap) {
			metadata = (MetadataMap)nextObject;
			nextObject = in.readObject();
		}
		
		// Read a measurement list, if we have one
		// This is rather hack-ish... but re-closing a list can prompt it to be stored more efficiently
		if (nextObject instanceof MeasurementList) {
			measurements = (MeasurementList)nextObject;
			measurements.closeList();
		}
		
		// Read child objects
		int nChildObjects = in.readInt();
		if (nChildObjects > 0) {
			ensureChildList(nChildObjects);
			for (int i = 0; i < nChildObjects; i++) {
				PathObject child = (PathObject)in.readObject();
				child.parent = this;
				this.childList.add(child);
//				addPathObject((PathObject)in.readObject());
			}
//			Collections.sort(childList, DefaultPathObjectComparator.getInstance());
		}
		
	}
	
}