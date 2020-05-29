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

package qupath.lib.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;


/**
 * Abstract plugin aimed towards cases where new objects will be detected inside existing objects (normally TMA cores or annotations).
 * <p>
 * TODO: Note this isn't a very stable API (because it's quite awkward), and it's therefore liable to change...
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractDetectionPlugin<T> extends AbstractInteractivePlugin<T> {

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(
				PathAnnotationObject.class,
				TMACoreObject.class
				);
//		List<Class<? extends PathObject>> list = new ArrayList<>(3);
//		list.add(PathAnnotationObject.class);
//		list.add(TMACoreObject.class);
//		list.add(PathRootObject.class);
//		return list;
	}

	/**
	 * Get all selected objects that are instances of a supported class.
	 */
	@Override
	protected Collection<? extends PathObject> getParentObjects(final PluginRunner<T> runner) {
		Collection<Class<? extends PathObject>> supported = getSupportedParentObjectClasses();
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		Collection<PathObject> selectedObjects = hierarchy
				.getSelectionModel()
				.getSelectedObjects();
		Collection<? extends PathObject> objects = PathObjectTools.getSupportedObjects(selectedObjects, supported);
		// In the event that not all selected objects were chosen, we need to deselect the others
		if (!objects.isEmpty() && selectedObjects.size() > objects.size()) {
			Set<PathObject> objectsToDeselect = new HashSet<>(selectedObjects);
			objectsToDeselect.removeAll(objects);
			hierarchy.getSelectionModel().deselectObjects(objectsToDeselect);
		}
		return objects;
//		return InteractivePluginTools.getObjectsForChoice(runner.getHierarchy(), getSupportedParentObjectClasses(), getParameterList(runner.getImageData()));
	}


}