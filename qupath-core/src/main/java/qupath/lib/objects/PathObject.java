/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.common.LogTools;
import qupath.lib.io.PathIO;
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
		
	private static final Logger logger = LoggerFactory.getLogger(PathObject.class);
	
	private static final String METADATA_KEY_ID = "Object ID";
	
	private UUID id = UUID.randomUUID();
	
	private PathObject parent = null;
	private Collection<PathObject> childList = null; // Collections.synchronizedList(new ArrayList<>(0));
	private MeasurementList measurements = null;
	
	private MetadataMap metadata = null;
	
	private String name = null;
	private Integer color;

	private transient Collection<PathObject> cachedUnmodifiableChildren = null;
	

	/**
	 * Create a PathObject with a specific measurement list.
	 * <p>
	 * This can be used e.g. to create an object with a more memory-efficient list,
	 * at the cost of generality/mutability.
	 * 
	 * @param measurements
	 */
	public PathObject(MeasurementList measurements) {
		this();
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
	 * Default implementation throws an {@link UnsupportedOperationException} if an 
	 * attempt is made to unlock the object.
	 * 
	 * @param locked
	 */
	public void setLocked(boolean locked) {
		if (!locked)
			throw new UnsupportedOperationException("Locked status cannot be unset!");
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
	
	private transient Map<String, Double> measurementsMap;
	
	/**
	 * Get a map-based view on {@link #getMeasurementList()}.
	 * This is likely to be less efficient (because it does not support primitives), but has several advantages 
	 * <ul>
	 * <li>it uses a familiar and standard API</li>
	 * <li>it is much more amenable for scripting, especially in Groovy</li>
	 * <li>it is possible to return {@code null} for missing values, rather than only {@code Double.NaN}</li>
	 * </ul>
	 * The {@link MeasurementList} is retained for backwards-compatibility, particularly the ability to 
	 * read older data files.
	 * Changes made to the map are propagated through to the {@link MeasurementList}, so it should be possible to 
	 * use them interchangeably - however note that there may be some loss of precision if the backing measurement 
	 * list uses floats rather than doubles.
	 * <p>
	 * It is possible that a map implementation becomes the standard in the future and {@link #getMeasurementList()} 
	 * <i>may</i> be deprecated; this is an experimental feature introduced in v0.4.0 for testing.
	 * 
	 * @return
	 * @since v0.4.0
	 */
	public Map<String, Double> getMeasurements() {
		if (measurementsMap == null) {
			synchronized(this) {
				if (measurementsMap == null)
					measurementsMap = getMeasurementList().asMap();
			}
		}
		return measurementsMap;
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
		if (!hasChildObjects())
			return "";
		int nChildren = nChildObjects();
		int nDescendants = PathObjectTools.countDescendants(this);
		String objString = nDescendants == 1 ? " object)" : " objects)";
		if (nChildren == nDescendants)
			return " (" + nChildren + objString;
		return " (" + (nChildren) + "/" + nDescendants + objString;
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		
		// Name or class
		if (getName() != null)
			sb.append(getName());
		else
			sb.append(PathObjectTools.getSuitableName(getClass(), false));
		
		// Classification
		if (getPathClass() != null)
			sb.append(" (").append(getPathClass().toString()).append(")");

		// ROI
		if (hasROI()) {
			var roi = getROI();
			if (roi.getZ() > 0 || roi.getT() > 0) {
				if (roi.getZ() > 0) {
					sb.append(" (z=").append(roi.getZ());
					if (roi.getT() > 0) {
						sb.append(", t=").append(roi.getT());
					}
					sb.append(")");
				} else {
					sb.append("(t=").append(roi.getT()).append(")");
				}
			}
		}
		
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
	 * @since v0.4.0
	 */
	public synchronized void addChildObject(PathObject pathObject) {
		if (pathObject instanceof PathRootObject) //J
			throw new IllegalArgumentException("PathRootObject cannot be added as child to another PathObject"); //J 
		addChildObjectImpl(pathObject);
	}
	
	/**
	 * Legacy method to add a single child object.
	 * @param pathObject
	 * @deprecated since v0.4.0, replaced by {@link #addChildObject(PathObject)}
	 */
	@Deprecated
	public void addPathObject(PathObject pathObject) {
		LogTools.warnOnce(logger, "addPathObject(Collection) is deprecated - use addChildObject(Collection) instead");
		addChildObject(pathObject);
	}
	
	private synchronized void addChildObjectImpl(PathObject pathObject) {
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

	
	private synchronized void addChildObjectsImpl(Collection<? extends PathObject> pathObjects) {
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
	
	/**
	 * Add a collection of objects to the child list of this object.
	 * @param pathObjects
	 * @since v0.4.0
	 */
	public synchronized void addChildObjects(Collection<? extends PathObject> pathObjects) {
		addChildObjectsImpl(pathObjects);
	}
	
	/**
	 * Legacy method to add child objects.
	 * @param pathObjects
	 * @deprecated since v0.4.0, replaced by {@link #addChildObjects(Collection)}
	 */
	@Deprecated
	public void addPathObjects(Collection<? extends PathObject> pathObjects) {
		LogTools.warnOnce(logger, "addPathObjects(Collection) is deprecated - use addChildObjects(Collection) instead");
		addChildObjects(pathObjects);
	}

	/**
	 * Remove a single object from the child list of this object.
	 * @param pathObject
	 * @since v0.4.0
	 */
	public void removeChildObject(PathObject pathObject) {
		if (!hasChildObjects())
			return;
		if (pathObject.parent == this)
			pathObject.parent = null; //.setParent(null);
		childList.remove(pathObject);
	}
	
	/**
	 * Legacy method to remove a single child object.
	 * @param pathObject
	 * @deprecated since v0.4.0, replaced by {@link #removeChildObject(PathObject)}
	 */
	@Deprecated
	public void removePathObject(PathObject pathObject) {
		LogTools.warnOnce(logger, "removePathObject(PathObject) is deprecated - use removeChildObject(PathObject) instead");
		removeChildObject(pathObject);
	}
	
	/**
	 * Remove multiple objects from the child list of this object.
	 * @param pathObjects
	 * @since v0.4.0
	 */
	public synchronized void removeChildObjects(Collection<PathObject> pathObjects) {
		if (!hasChildObjects())
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
	 * Legacy method to remove specified child objects.
	 * @param pathObjects 
	 * @deprecated since v0.4.0, replaced by {@link #removeChildObjects(Collection)}
	 */
	@Deprecated
	public void removePathObjects(Collection<PathObject> pathObjects) {
		LogTools.warnOnce(logger, "removePathObjects(Collection) is deprecated - use removeChildObjects(Collection) instead");
		removeChildObjects(pathObjects);
	}
	
	/**
	 * Remove all child objects.
	 * @since v0.4.0
	 */
	public void clearChildObjects() {
		if (!hasChildObjects())
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
	 * Legacy method to remove all child objects.
	 * @deprecated since v0.4.0, replaced by {@link #clearChildObjects()}
	 */
	@Deprecated
	public void clearPathObjects() {
		LogTools.warnOnce(logger, "clearPathObjects() is deprecated, use clearChildObjects() instead");
		clearChildObjects();
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
		if (!hasChildObjects())
			return 0;
		int total = 0;
		synchronized (childList) {
			for (var child : childList) {
				total += 1 + child.nDescendants();
			}
		}
		return total;
	}
	
	/**
	 * Check if this object has children, or if its child object list is empty.
	 * @return
	 * @since v0.4.0, replaces {@link #hasChildren()} for more consistent naming
	 */
	public boolean hasChildObjects() {
		return childList != null && !childList.isEmpty();
	}
	
	/**
	 * Legacy method to check for child objects.
	 * @return
	 * @deprecated since v0.4.0, replaced by {@link #hasChildObjects()}
	 */
	@Deprecated
	public boolean hasChildren() {
		LogTools.warnOnce(logger, "hasChildren() is deprecated - use hasChildObjects() instead");
		return hasChildObjects();
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
		if (!hasChildObjects())
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
		if (!hasChildObjects())
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
		if (!hasChildObjects())
			return Collections.emptyList();
		if (descendants == null)
			descendants = new ArrayList<>();
//		descendants.addAll(childList);
		synchronized (childList) {
			for (var child : childList) {
				descendants.add(child);
				if (child.hasChildObjects())
					child.getDescendantObjects(descendants);
			}
		}
		return descendants;
	}
	
	/**
	 * Get a defensive copy of child objects as an array.
	 * <p>
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
	 * Reset the classification (i.e. set it to null).
	 * @return true if the classification has changed, false otherwise (i.e. it was already null)
	 */
	public boolean resetPathClass() {
		var previous = getPathClass();
		if (previous == null)
			return false;
		setPathClass((PathClass)null);
		return true;
	}
	
	
	/**
	 * Set the {@link PathClass} from a collection of names according to the rules:
	 * <ul>
	 * <li>If the collection is empty, reset the PathClass</li>
	 * <li>If the collection has one element, set it to be the name of the PathClass</li>
	 * <li>If the collection has multiple element, create and set a derived PathClass with each 
	 * <b>unique</b> element the name of a PathClass component</li>
	 * </ul>
	 * The uniqueness is equivalent to copying the elements into a set; if a set is provided 
	 * as input then a defensive copy will be made..
	 * <p>
	 * Ultimately, a single {@link PathClass} object is created to encapsulate the classification 
	 * and the color used for display - but {@link #setClassifications(Collection)} and 
	 * {@link #getClassifications()} provides a different (complementary) way to think of 
	 * classifications within QuPath.
	 * <p>
	 * <b>Important: </b> This is an experimental feature introduced in QuPath v0.4.0 to 
	 * provide an alternative way to interact with classifications and to add support for 
	 * multiple classifications. It is possible that this becomes the 'standard' approach 
	 * in future versions, with {@link PathClass} being deprecated.
	 * <p>
	 * Feedback or discussion on the approach is welcome on the forum at 
	 * <a href="https://forum.image.sc/tag/qupath">image.sc</a>.
	 * 
	 * @param classifications
	 * @since v0.4.0
	 * @see #getClassifications()
	 */
	public void setClassifications(Collection<String> classifications) {
		if (classifications.isEmpty())
			resetPathClass();
		else if (classifications instanceof Set) {
			setPathClass(PathClass.fromCollection((Set<String>)classifications));
		} else {
			// Use LinkedHashSet to maintain ordering
			var set = new LinkedHashSet<>(classifications);
			if (set.size() < classifications.size())
				logger.warn("Input to setClassifications() contains duplicate elements - {} will be replaced by {}", classifications, set);
			setPathClass(PathClass.fromCollection(set));
		}
	}
	
	/**
	 * Get the components of the {@link PathClass} as an unmodifiable set.
	 * 
	 * <b>Important: </b> This is an experimental feature introduced in QuPath v0.4.0 to 
	 * provide an alternative way to interact with classifications and to add support for 
	 * multiple classifications. It is possible that this becomes the 'standard' approach 
	 * in future versions, with {@link PathClass} being deprecated.
	 * <p>
	 * Feedback or discussion on the approach is welcome on the forum at 
	 * <a href="https://forum.image.sc/tag/qupath">image.sc</a>.
	 * 
	 * @return an empty collection is the PathClass is null, otherwise a collection of strings 
	 *         where each string gives the name of one component of the PathClass
	 * @since v0.4.0
	 * @see #setClassifications(Collection)
	 */
	public Set<String> getClassifications() {
		var pc = getPathClass();
		if (pc == null)
			return Collections.emptySet();
		else
			return pc.toSet();
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
	 * This may be null if no color has been set.
	 * @return
	 * @see #setColorRGB(Integer)
	 * @see ColorTools#red(int)
	 * @see ColorTools#green(int)
	 * @see ColorTools#blue(int)
	 * @since v0.4.0
	 */
	public Integer getColor() {
		return color;
	}
	
	/**
	 * Return any stored color as a packed RGB value.
	 * <p>
	 * This may be null if no color has been set
	 * @return
	 * @deprecated since v0.4.0, use {@link #getColor()} instead.
	 */
	@Deprecated
	public Integer getColorRGB() {
		LogTools.warnOnce(logger, "PathObject.getColorRGB() is deprecated since v0.4.0 - use getColor() instead");
		return getColor();
	}
	
	/**
	 * Set the display color.
	 * @param color
	 * @deprecated since v0.4.0, use {@link #setColor(Integer)} instead.
	 */
	@Deprecated
	public void setColorRGB(Integer color) {
		LogTools.warnOnce(logger, "PathObject.setColorRGB(Integer) is deprecated since v0.4.0 - use setColor(Integer) instead");
		setColor(color);
	}
	
	/**
	 * Set the display color as a packed (A)RGB integer (alpha may not be used 
	 * by viewing code).
	 * @param color packed (A)RGB value, or null if a color should not stored
	 * @since v0.4.0
	 * @see #setColor(int, int, int)
	 * @see ColorTools#packRGB(int, int, int)
	 * @implNote any alpha value is retained, but may not be used; it is recommended to 
	 *           use only RGB values with alpha 255.
	 */
	public void setColor(Integer color) {
		this.color = color;
	}
	
	/**
	 * Set the display color as 8-bit RGB values
	 * @param red 
	 * @param green 
	 * @param blue 
	 * @since v0.4.0
	 */
	public void setColor(int red, int green, int blue) {
		setColor(ColorTools.packRGB(red, green, blue));
	}
	
	/**
	 * Get the ID for this object.
	 * @return
	 * @see #setID(UUID)
	 * @see #refreshID()
	 */
	public UUID getID() {
		// Make extra sure we always have an ID when requested
		if (id == null) {
			synchronized (this) {
				if (id == null) {
					logger.debug("Generating a new UUID on request");
					id = UUID.randomUUID();
				}
			}
		}
		return id;
	}
	
	/**
	 * Set the ID for this object.
	 * <p>
	 * <b>Important!</b> Use this with caution or, even better, not at all!
	 * <p>
	 * In general, the ID for an object should be unique.
	 * This is best achieved by allowing the ID to be generated randomly when the object 
	 * is created, and never changing it to anything else.
	 * <p>
	 * However, there are times when it is necessary to transfer the ID from an existing object, 
	 * such as whenever the ROI of an object is being transformed and the original object deleted.
	 * <p>
	 * For that reason, it is possible (although discouraged) to set an ID explicitly.
	 * 
	 * @param id the ID to use
	 * @throws IllegalArgumentException if the specified ID is null
	 */
	public void setID(UUID id) throws IllegalArgumentException {
		if (id == null)
			throw new IllegalArgumentException("ID of an object cannot be null!");
		this.id = id;
	}
	
	/**
	 * Regenerate a new random ID.
	 * @see #setID(UUID)
	 */
	public void refreshID() {
		setID(UUID.randomUUID());
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
		
		// Write the ID, and an int representing the number of fields to include in the file
		// This is not currently used, but exists in case future QuPath versions need 
		// improved flexibility while wanting v0.4.0 to still be able to open the data files.
		if (PathIO.getRequestedDataFileVersion() >= 4) {
			out.writeObject(id);
			// Number of additional fields to write as objects
			int nFields = 1;
			if (metadata != null) {
				nFields++;
			}
			out.writeInt(nFields);
			if (metadata != null)
				out.writeObject(metadata);
			out.writeObject(measurements);
		} else {
			// v3 method way of writing output - enhanced to write the ID into the metadata map
			// This isn't as efficient as it could be, but means files can be reopened with earlier QuPath versions
			var tempMetadata = new MetadataMap();
			if (metadata != null) {
				tempMetadata.putAll(metadata);
			}
			if (id != null && !tempMetadata.containsKey(METADATA_KEY_ID))
				tempMetadata.put(METADATA_KEY_ID, id.toString());
			
			// We always have metadata now
			out.writeObject(tempMetadata);
			
			// Write measurements as well
			out.writeObject(measurements);		
						
//			// Previous way of writing output
//			if (metadata != null)
//				out.writeObject(metadata);
//			out.writeObject(measurements);			
		}

		// Write number of child objects, followed by those objects
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
		
		// If we have a UUID, then we're working with a data file version of at least 4
		if (nextObject instanceof UUID) {
			id = (UUID)nextObject;
			// Here we've stored the number of object fields (for future expansion)
			int nFields = in.readInt();
			for (int i = 0; i < nFields; i++) {
				nextObject = in.readObject();
				if (nextObject instanceof MetadataMap) {
					// Read metadata, if we have it
					metadata = (MetadataMap)nextObject;
				} else if (nextObject instanceof MeasurementList) {
					// Read a measurement list, if we have one
					// This is rather hack-ish... but re-closing a list can prompt it to be stored more efficiently
					measurements = (MeasurementList)nextObject;
					measurements.close();
				} else if (nextObject != null) {
					logger.debug("Unsupported field during deserialization {}", nextObject);
				} else {
					logger.debug("Null field during deserialization");					
				}
			}
		} else {
			// Handle legacy data file version
			// Here, we maybe have a metadata map and maybe have a measurement list - in that order - but nothing else
			// before reaching child objects
		
			// Read metadata, if we have it
			if (nextObject instanceof MetadataMap) {
				metadata = (MetadataMap)nextObject;
				String idString = metadata.getOrDefault(METADATA_KEY_ID, null);
				
				// Try to parse UUID from metadata map if we can
				if (idString != null && idString.length() <= 36 && idString.contains("-")) {
					try {
						id = UUID.fromString(idString);
						if (metadata.size() == 1)
							metadata = null;
						else
							metadata.remove(METADATA_KEY_ID);
					} catch (Exception e) {
						logger.debug("Unable to parse UUID from metadata ID");
					}
				}			
				nextObject = in.readObject();
			}
			
			// Read a measurement list, if we have one
			// This is rather hack-ish... but re-closing a list can prompt it to be stored more efficiently
			if (nextObject instanceof MeasurementList) {
				measurements = (MeasurementList)nextObject;
				measurements.close();
			}
		}
		
		// Ensure we have an ID
		if (id == null)
			id = UUID.randomUUID();
		
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