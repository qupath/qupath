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

package qupath.lib.objects.hierarchy.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * An event class for passing on information about modifications to a PathObjectHierarchy.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectHierarchyEvent {
	
	/**
	 * Enum representing different ways in which the hierarchy may have been updated.
	 */
	public static enum HierarchyEventType {
		/**
		 * An object has been added
		 */
		ADDED,
		
		/**
		 * An object has been removed
		 */
		REMOVED,
		
		/**
		 * A more complex structural change was made than simply either adding or removing objects
		 */
		OTHER_STRUCTURE_CHANGE,
		
		/**
		 * A change was made to one or more object classifications
		 */
		CHANGE_CLASSIFICATION,
		
		/**
		 * A change was made to one or more object measurements
		 */
		CHANGE_MEASUREMENTS,
//		CHANGE_ROI, // A change was made to one or more object ROIs
		
		/**
		 * A change was made to one or more objects that is more complex than the other changes allow for
		 */
		CHANGE_OTHER
		};
	
	private Object source;
	private PathObjectHierarchy hierarchy;
	private PathObject parentObject;
	private HierarchyEventType type;
	private List<PathObject> pathObjects;
	private boolean isChanging;

	PathObjectHierarchyEvent(final Object source, final PathObjectHierarchy hierarchy, final HierarchyEventType type, final PathObject parentObject, final List<PathObject> pathObjects, final boolean isChanging) {
		this.source = source;
		this.hierarchy = hierarchy;
		this.type = type;
		this.parentObject = parentObject;
		this.pathObjects = Collections.unmodifiableList(pathObjects);
		this.isChanging = isChanging;
	}
	
	@Override
	public String toString() {
		return "Hierarchy change event: Source=" + source + ", Type="+type + ", Parent="+parentObject;
	}
	
	/**
	 * Create a hierarchy event indicating that the hierarchy structure has been changed.
	 * @param source
	 * @param hierarchy
	 * @param parentObject
	 * @return
	 */
	public static PathObjectHierarchyEvent createStructureChangeEvent(Object source, PathObjectHierarchy hierarchy, PathObject parentObject) {
		return new PathObjectHierarchyEvent(source, hierarchy, HierarchyEventType.OTHER_STRUCTURE_CHANGE, parentObject, new ArrayList<>(0), false);						
	}

	/**
	 * Create a hierarchy event indicated objects were added.
	 * @param source
	 * @param hierarchy
	 * @param parentObject
	 * @param pathObjectAdded
	 * @return
	 */
	public static PathObjectHierarchyEvent createObjectAddedEvent(Object source, PathObjectHierarchy hierarchy, PathObject parentObject, PathObject pathObjectAdded) {
		return new PathObjectHierarchyEvent(source, hierarchy, HierarchyEventType.ADDED, parentObject, Collections.singletonList(pathObjectAdded), false);				
	}

	/**
	 * Create a hierarchy event indicating objects were removed.
	 * @param source
	 * @param hierarchy
	 * @param parentObject
	 * @param pathObjectRemoved
	 * @return
	 */
	public static PathObjectHierarchyEvent createObjectRemovedEvent(Object source, PathObjectHierarchy hierarchy, PathObject parentObject, PathObject pathObjectRemoved) {
		return new PathObjectHierarchyEvent(source, hierarchy, HierarchyEventType.REMOVED, parentObject, Collections.singletonList(pathObjectRemoved), false);		
	}
	
	/**
	 * Create a hierarchy event indicating objects have changed in a way consistent with the specified event type.
	 * @param source
	 * @param hierarchy
	 * @param type
	 * @param pathObjects
	 * @param isChanging
	 * @return
	 */
	public static PathObjectHierarchyEvent createObjectsChangedEvent(Object source, PathObjectHierarchy hierarchy, HierarchyEventType type, Collection<? extends PathObject> pathObjects, boolean isChanging) {
		return new PathObjectHierarchyEvent(source, hierarchy, type, null, new ArrayList<>(pathObjects), isChanging);
	}

	/**
	 * Returns true if changes are still being made, so more events will be fired.
	 * This enables listeners to postpone expensive operations that could be called often until 
	 * this flag is false.
	 * 
	 * @return
	 */
	public boolean isChanging() {
		return isChanging;
	}
	
	/**
	 * The hierarchy to which this event refers.
	 * @return
	 */
	public PathObjectHierarchy getHierarchy() {
		return hierarchy;
	}
	
	/**
	 * The objects that were affected by whichever changes were made.
	 * @return
	 */
	public List<PathObject> getChangedObjects() {
		return pathObjects;
	}
	
	/**
	 * Get the hierarchy event type.
	 * @return
	 */
	public HierarchyEventType getEventType() {
		return type;
	}
	
	/**
	 * Get the source that triggered the event.
	 * @return
	 */
	public Object getSource() {
		return source;
	}
	
	/**
	 * Returns true if the hierarchy structure has changed, e.g. with objects added or removed
	 * @return
	 */
	public boolean isStructureChangeEvent() {
		return isAddedOrRemovedEvent() || type == HierarchyEventType.OTHER_STRUCTURE_CHANGE;
	}
	
	/**
	 * Returns true if objects have been added or removed from the hierarchy.
	 * @return
	 */
	public boolean isAddedOrRemovedEvent() {
		return type == HierarchyEventType.ADDED || type == HierarchyEventType.REMOVED;
	}
	
	/**
	 * Returns true if the event indicates that object classifications have changed.
	 * @return
	 */
	public boolean isObjectClassificationEvent() {
		return type == HierarchyEventType.CHANGE_CLASSIFICATION;
	}

	/**
	 * Returns true if the event indicates that object measurements have changed.
	 * @return
	 */
	public boolean isObjectMeasurementEvent() {
		return type == HierarchyEventType.CHANGE_MEASUREMENTS;
	}
	
	/**
	 * If this is a structure change event, return the base object, i.e. below which the structure differs from previously.
	 * In the case that an object was added or removed, this will return the parent of the added/removed object.  For more
	 * thorough changes, the root object is returned.
	 * <p>
	 * For events that do not correspond to structural changes, this returns null.
	 * @return
	 */
	public PathObject getStructureChangeBase() {
		if (isStructureChangeEvent())
			return parentObject;
		return null;
	}
	
	
	
//	/**
//	 * Returns true if none of the changed objects are within the hierarchy.
//	 * @return
//	 */
//	public boolean changedObjectsWithinHierarchy() {
//		for (PathObject temp : getChangedObjects())
//			if (temp.getParent() != null)
//				return false;
//		return true;
//	}
	
	
//	/**
//	 * Returns true if the base object of the event is the root of the hierarchy.
//	 * @return
//	 */
//	public boolean isBaseRootObject() {
//		return pathObjectBase == hierarchy.getRootObject();
//	}

//	public boolean singleObjectChanged() {
//		return !areDescendantObjectsChanged() && (pathObjects == null || pathObjects.isEmpty())
////		return type == HierarchyEventType.OBJECT_CHANGE && !areDescendantObjectsChanged() && !(pathObjectBase instanceof PathRootObject) &&
////				(pathObjects == null || pathObjects.isEmpty() || pathObjects.equals(Collections.singletonList(pathObjectBase)));
//	}

}