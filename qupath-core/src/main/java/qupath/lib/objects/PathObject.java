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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;

/**
 * Fundamental object of interest in QuPath.
 * <p>
 * Used as a base class for annotations, detections, cells, TMA cores, tiles...
 * 
 * @author Pete Bankhead
 *
 */
public abstract class PathObject implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private PathObject parent = null;
	private Collection<PathObject> childList = null; // Collections.synchronizedList(new ArrayList<>(0));
	private MeasurementList measurements = null;
	
	private MetadataMap metadata = null;
	
	private String name = null;
	private Integer color;

	transient private Collection<PathObject> cachedUnmodifiableChildren = null;
	

	/**
	 * Create a PathObject with a specific measurement list.
	 * <p>
	 * This can be used e.g. to create an object with a more memory-efficient list,
	 * at the cost of generality/mutability.
	 * 
	 * @param measurements
	 */
	public PathObject(MeasurementList measurements) {
		this.measurements = measurements;
	}

	/**
	 * Default constructor. Used for Externalizable support, not intended to be used by other consumers.
	 */
	public PathObject() {}
	
	/**
	 * Request the parent object. Each PathObject may have only one parent.
	 * 
	 * @return
	 */
	public PathObject getParent() {
		return parent;
	}
	
	/**
	 * Set locked status, if possible.
	 * <p>
	 * Subclasses should override this method to support locking or unlocking. 
	 * Default implementation throws an {@link UnsupportedOperationException}.
	 * 
	 * @param locked
	 */
	public void setLocked(boolean locked) {
		throw new UnsupportedOperationException("Locked status cannot be set!");
	}

	/**
	 * Query the locked status.
	 * <p>
	 * Subclasses should override this method to support locking or unlocking. 
	 * Default implementation always returns true.
	 * 
	 * @return true if the object is locked and should not be modified.
	 */
	public boolean isLocked() {
		return true;
	}

	
//	public abstract String getType();
	
	/**
	 * The level of the object in a hierarchy.
	 * <p>
	 * If the object has no parent, this is 0. Otherwise, it is equal to parent.getLevel() + 1.
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
	
	/**
	 * Returns true if the object is the 'root' of an object hierarchy.
	 * @return
	 * 
	 * @see PathObjectHierarchy
	 */
	public boolean isRootObject() {
		return this instanceof PathRootObject;
	}
	
	/**
	 * Retrieve the list stored measurements for the object.
	 * <p>
	 * This can be used to query or add specific numeric measurements.
	 * @return
	 */
	public synchronized MeasurementList getMeasurementList() {
		if (measurements == null)
			measurements = createEmptyMeasurementList();
		return measurements;
	}
	
	/**
	 * Create a new MeasurementList of the preferred type for this object.
	 * <p>
	 * This will be called whenever a MeasurementList is requested, if one is not already stored.
	 * <p>
	 * Subclasses can use this method to create more efficient MeasurementList implementations if required.
	 * 
	 * @return
	 */
	protected MeasurementList createEmptyMeasurementList() {
		MeasurementList list = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		return list;
	}
	
	protected synchronized String objectCountPostfix() {
		ROI pathROI = getROI();
		if (pathROI != null && pathROI.isPoint()) {
			int nPoints = pathROI.getNumPoints();
			if (nPoints == 1)
				return " (1 point)";
			else
				return String.format(" (%d points)", nPoints);
		}
		if (!hasChildren())
			return "";
		int nChildren = nChildObjects();
		int nDescendants = PathObjectTools.countDescendants(this);
		if (nChildren == nDescendants)
			return " (" + nChildren + " objects)";
		return " (" + (nChildren) + "/" + nDescendants + " objects)";
//		if (nDescendants == 1)
//			return " - 1 descendant";
//		else
//			return " - " + nDescendants + " descendant";
//		
//		if (childList.size() == 1)
//			return " - 1 object";
//		else
//			return " - " + childList.size() + " objects";
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		
		// Name or class
		if (getName() != null)
			sb.append(getName());
		else
			sb.append(PathObjectTools.getSuitableName(getClass(), false));
			
		// ROI
		if (!isCell() && hasROI())
			sb.append(" (").append(getROI().getRoiName()).append(")");
		
		// Classification
		if (getPathClass() != null)
			sb.append(" (").append(getPathClass().toString()).append(")");
		
		// Number of descendants
		sb.append(objectCountPostfix());
		return sb.toString();
		
//		String postfix;
//		if (getPathClass() == null)
//			postfix = objectCountPostfix();
//		else
//			postfix = " (" + getPathClass().toString() + ")";
//		if (getName() != null)
//			return getName() + postfix;
//		var roi = getROI();
//		if (!isCell() && roi != null)
//			return roi + postfix;
//		String prefix = PathObjectTools.getSuitableName(getClass(), false);
//		return prefix + postfix; // Entire image
	}
	
	/**
	 * Add an object to the child list of this object.
	 * @param pathObject
	 */
	public synchronized void addPathObject(PathObject pathObject) {
		if (pathObject instanceof PathRootObject) //J
			throw new IllegalArgumentException("PathRootObject cannot be added as child to another PathObject"); //J 
		addPathObjectImpl(pathObject);
	}
	
	private synchronized void addPathObjectImpl(PathObject pathObject) {
		ensureChildList(nChildObjects() + 1);
		// Make sure the object is removed from any other parent
		if (pathObject.parent != this) {
			if (pathObject.parent != null && pathObject.parent.childList != null)
				pathObject.parent.childList.remove(pathObject);
			pathObject.parent = this;
		}
		childList.add(pathObject);
		
//		if (!isRootObject())
//			Collections.sort(childList, DefaultPathObjectComparator.getInstance());
	}

	
	private synchronized void addPathObjectsImpl(Collection<? extends PathObject> pathObjects) {
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
//		// Sort if we aren't the root object
//		if (!this.isRootObject())
//			Collections.sort(childList, DefaultPathObjectComparator.getInstance());
	}
	
	
	/**
	 * When using an ArrayList previously, this method could (somewhat) improve object removal performance.
	 * 
	 * @param list
	 * @param toRemove
	 */
	private static <T> void removeAllQuickly(Collection<T> list, Collection<T> toRemove) {
		int size = 10;
		if (!(toRemove instanceof Set)  && toRemove.size() > size) {
			toRemove = new HashSet<>(toRemove);
		}
		list.removeAll(toRemove);
	}
	
//	/**
//	 * Remove all items from a list, but optionally using a (temporary) set 
//	 * to improve performance.
//	 * 
//	 * @param list
//	 * @param toRemove
//	 */
//	private static <T> void removeAllQuickly(List<T> list, Collection<T> toRemove) {
//		// This is rather implementation-specific, based on how ArrayLists do their object removal.
//		// In some implementations it might be better to switch the list to a set temporarily?
//		int size = 10;
//		if (list.size() > size || toRemove.size() > size) {
//			var tempSet = new LinkedHashSet<>(list);
//			tempSet.removeAll(toRemove);
//			list.clear();
//			list.addAll(tempSet);
//		} else {
//			if (!(toRemove instanceof Set)  && toRemove.size() > size) {
//				toRemove = new HashSet<>(toRemove);
//			}
//			list.removeAll(toRemove);
//		}
////		list.removeAll(toRemove);
//	}
	
	
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
	
	/**
	 * Add a collection of objects to the child list of this object.
	 * @param pathObjects
	 */
	public synchronized void addPathObjects(Collection<? extends PathObject> pathObjects) {
		addPathObjectsImpl(pathObjects);
	}

	/**
	 * Remove a single object from the child list of this object.
	 * @param pathObject
	 */
	public void removePathObject(PathObject pathObject) {
		if (!hasChildren())
			return;
		if (pathObject.parent == this)
			pathObject.parent = null; //.setParent(null);
		childList.remove(pathObject);
	}
	
	/**
	 * Remove multiple objects from the child list of this object.
	 * @param pathObjects
	 */
	public synchronized void removePathObjects(Collection<PathObject> pathObjects) {
		if (!hasChildren())
			return;
		for (PathObject pathObject : pathObjects) {
			if (pathObject.parent == this)
				pathObject.parent = null;
		}
		synchronized (childList) {
			removeAllQuickly(childList, pathObjects);
		}
	}
	
	/**
	 * Remove all child objects.
	 */
	public void clearPathObjects() {
		if (!hasChildren())
			return;
		synchronized (childList) {
			for (PathObject pathObject : childList) {
				if (pathObject.parent == this)
					pathObject.parent = null;
			}
			childList.clear();
		}
	}
	
	/**
	 * Total number of child objects.
	 * Note that this is the size of the child object list - it does not check descendants recursively.
	 * 
	 * @return the number of direct child objects
	 * @see #nDescendants()
	 */
	public int nChildObjects() {
		return childList == null ? 0 : childList.size();
	}
	
	/**
	 * Total number of descendant objects.
	 * This involves counting objects recursively; to get the number of direct child object only 
	 * see {@link #nChildObjects()}.
	 * 
	 * @return the number of child objects, plus the number of each child object's descendants
	 * @see #nChildObjects()
	 */
	public int nDescendants() {
		if (!hasChildren())
			return 0;
		// This could be used if needed - but with childList being synchronized I think it isn't necessary
//		var childArray = getChildObjectsAsArray();
		int total = 0;
		for (var child : childList) {
			total += 1 + child.nDescendants();
		}
		return total;
	}
	
	/**
	 * Check if this object has children, or if its child object list is empty.
	 * @return
	 */
	public boolean hasChildren() {
		return childList != null && !childList.isEmpty();
	}
	
	/**
	 * Returns true if this object has a ROI.
	 * <p>
	 * In general, objects are expected to have ROIs unless they are root objects.
	 * @return
	 */
	public boolean hasROI() {
		return getROI() != null;
	}
	
	/**
	 * Returns true if the object is an annotation.
	 * @return
	 * @see PathAnnotationObject
	 */
	public boolean isAnnotation() {
		return this instanceof PathAnnotationObject;
	}
	
	/**
	 * Returns true if the object is a detection.
	 * <p>
	 * Note that this returns true also if the object is a subclass of a detection, 
	 * e.g. a tile or cell.
	 * 
	 * @return
	 * @see #isCell()
	 * @see PathDetectionObject
	 * @see PathCellObject
	 * @see PathTileObject
	 */
	public boolean isDetection() {
		return this instanceof PathDetectionObject;
	}
	
	/**
	 * Returns true if the object is a cell object (a special type of detection, which can contain second ROI for the nucleus).
	 * 
	 * @return
	 * @see #isDetection()
	 * @see PathDetectionObject
	 * @see PathCellObject
	 * @see PathTileObject
	 */
	public boolean isCell() {
		return this instanceof PathCellObject;
	}
	
	/**
	 * Returns true if the measurement list for this object is not empty.
	 * @return
	 */
	public boolean hasMeasurements() {
		return measurements != null && !measurements.isEmpty();
	}
	
//	public boolean containsAllMeasurements(Collection<String> measurementNames) {
//		if (measurements == null && !measurementNames.isEmpty())
//			return false;
//		return measurements.containsAllMeasurements(measurementNames);
//	}
	
	/**
	 * Returns true if this object represents a TMA core.
	 * @return
	 * @see TMACoreObject
	 */
	public boolean isTMACore() {
		return this instanceof TMACoreObject;
	}
	
	/**
	 * Returns true if this object represents an image tile.
	 * 
	 * @return
	 * @see PathTileObject
	 */
	public boolean isTile() {
		return this instanceof PathTileObject;
	}
	
	/**
	 * Flag indicating that the object is editable.
	 * <p>
	 * If this returns false, this indicates the object has a ROI this should not be moved or resized 
	 * (e.g. because child objects depend upon it).
	 * @return
	 */
	public abstract boolean isEditable();
	
	/**
	 * Get a collection of child objects.
	 * <p>
	 * In the current implementation, this is immutable - it cannot be modified directly.
	 * @return
	 */
	public Collection<PathObject> getChildObjects() {
		if (!hasChildren())
			return Collections.emptyList();
		return cachedUnmodifiableChildren;
	}
	
	/**
	 * Get a collection containing all child objects.
	 * 
	 * @param children optional collection to which the children should be added
	 * @return collection containing all child object (the same as {@code children} if provided)
	 */
	public Collection<PathObject> getChildObjects(Collection<PathObject> children) {
		if (children == null)
			return getChildObjects();
		if (!hasChildren())
			return children;
		children.addAll(childList);
		return children;
	}
	
	/**
	 * Get a collection containing all descendant objects.
	 * 
	 * @param descendants optional collection to which the descendants should be added
	 * @return collection containing all descendant object (the same as {@code descendants} if provided)
	 */
	public Collection<PathObject> getDescendantObjects(Collection<PathObject> descendants) {
		if (!hasChildren())
			return Collections.emptyList();
		if (descendants == null)
			descendants = new ArrayList<>();
//		descendants.addAll(childList);
		for (var child : childList) {
			descendants.add(child);
			if (child.hasChildren())
				child.getDescendantObjects(descendants);
		}
		return descendants;
	}
	
	/**
	 * Get a defensive copy of child objects as an array.
	 * Why? Well perhaps you want to iterate through it and {@link #getChildObjects()} may result in synchronization problems if 
	 * the list is modified by another thread. In such a case a defensive copy may already be required, and it is more efficient to request 
	 * it here.
	 * @return
	 */
	public PathObject[] getChildObjectsAsArray() {
		return childList == null ? new PathObject[0] : childList.toArray(PathObject[]::new);
	}
	
	/**
	 * Get the classification of the object.
	 * @return
	 */
	public abstract PathClass getPathClass();
		
	/**
	 * Set the classification of the object, without specifying any classification probability.
	 * @param pc
	 */
	public void setPathClass(PathClass pc) {
		setPathClass(pc, Double.NaN);
	}

	/**
	 * Set the classification of the object, specifying a classification probability.
	 * <p>
	 * The probability is expected to be between 0 and 1, or Double.NaN if no probability should be set.
	 * @param pathClass
	 * @param classProbability
	 */
	public abstract void setPathClass(PathClass pathClass, double classProbability);
	
	/**
	 * Request the classification probability, or Double.NaN if no probability is available.
	 * @return
	 */
	public abstract double getClassProbability();
	
	/**
	 * Request an object name in a form suitable for displaying.
	 * <p>
	 * This may combine various properties of the object.
	 * @return
	 */
	public String getDisplayedName() {
		String nameDisplayed = name;
		if (nameDisplayed == null) {
			PathClass pathClass = getPathClass();
			if (pathClass != null)
				nameDisplayed = pathClass.toString();
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
	
	/**
	 * Request the stored object name.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Set the stored object name.
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Get the region of interest (ROI) for the object.
	 * @return
	 */
	public abstract ROI getROI();
	
	/**
	 * Return any stored color as a packed RGB value.
	 * <p>
	 * This may be null if no color has been set
	 * @return
	 */
	public Integer getColorRGB() {
		return color;
	}
	
	/**
	 * Set the display color.
	 * @param color
	 */
	public void setColorRGB(Integer color) {
		this.color = color;
	}
	
	
	/**
	 * Ensure that we have a child list with a minimum capacity.
	 * 
	 * @param capacity
	 */
	void ensureChildList(int capacity) {
		if (childList == null) { 
			synchronized (this) {
				if (childList != null)
					return;
				// Set default capacity to something reasonable, taking into consideration 
				// the load factor (we don't want to expand immediately)
				int n = 8;
				float loadFactor = 0.75f;
				if (capacity > 0)
					n = (int)Math.max(n, Math.ceil(capacity / loadFactor));
				childList = Collections.synchronizedSet(new LinkedHashSet<PathObject>(n, loadFactor));
				cachedUnmodifiableChildren = Collections.unmodifiableCollection(childList);
			}
		}
//			childList = new TreeSet<PathObject>(DefaultPathObjectComparator.getInstance());
//			childList = new ArrayList<PathObject>();
	}
	
	/**
	 * Store a metadata value.
	 * <p>
	 * Note: This should be used with caution; for objects that could be plentiful (e.g. detections) it is likely
	 * to be unwise to store any metadata values, since these can't be stored particularly efficiently - and
	 * therefore this could lead to far too high memory requirements.
	 * <p>
	 * If metadata is never stored for an object, no storage object is created - only a null reference.
	 * <p>
	 * Therefore the intention is that some newly-defined PathObject classes may take advantage of this mechanism and expose their
	 * own API for getting/setting values, backed-up by this store (which takes care of serialization/deserialization).
	 * However class definitions can also avoid making any use of this whatsoever if it's expected that it could lead to too much 
	 * memory being required.
	 * 
	 * @param key
	 * @param value
	 * @return 
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
	
	/**
	 * Remove all stored metadata values.
	 */
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
			measurements.close();
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