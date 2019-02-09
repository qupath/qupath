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

package qupath.lib.plugins.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to create new annotations by expanding the size of existing annotations.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class SplitAnnotationsPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private String resultString = null;
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Collections.singleton(PathAnnotationObject.class);
	}

	@Override
	public String getName() {
		return "Split annotations";
	}

	@Override
	public String getDescription() {
		return "Split annotations with multi-part ROIs into separate annotation objects";
	}

	@Override
	public String getLastResultsDescription() {
		return resultString;
	}

	/**
	 * Returns an empty ParameterList.
	 */
	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		return new ParameterList();
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		return runner.getHierarchy().getSelectionModel().getSelectedObjects().stream().filter(p -> p.isAnnotation()).collect(Collectors.toList());
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {}
	
	protected Collection<Runnable> getTasks(final PluginRunner<T> runner) {
		Collection<? extends PathObject> parentObjects = getParentObjects(runner);
		if (parentObjects == null || parentObjects.isEmpty())
			return Collections.emptyList();
		
		// Add a single task, to avoid multithreading - which may complicate setting parents
		List<Runnable> tasks = new ArrayList<>(1);
		PathObjectHierarchy hierarchy = runner.getHierarchy();
		
		// Want to reset selection
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		
		tasks.add(() -> {
			List<PathObject> toAdd = new ArrayList<>();
			List<PathObject> toRemove = new ArrayList<>();
			Set<PathObject> toSelect = new HashSet<>();
			for (PathObject pathObject : parentObjects) {
				ROI roiOrig = pathObject.getROI();
				if (roiOrig == null || !roiOrig.isArea()) {
					toSelect.add(pathObject);
					continue;
				}
				var splitROIs = PathROIToolsAwt.splitROI(roiOrig);
				if (splitROIs.size() == 1)
					continue;
				toRemove.add(pathObject);
				for (var r : splitROIs)
					toAdd.add(PathObjects.createAnnotationObject(r, pathObject.getPathClass()));
			}
			if (toAdd.isEmpty() && toRemove.isEmpty())
				return;
			hierarchy.getSelectionModel().clearSelection();
			toSelect.addAll(toAdd);
			hierarchy.removeObjects(toRemove, true);
			hierarchy.addPathObjects(toAdd, false);
			
			hierarchy.getSelectionModel().selectObjects(toSelect);
			if (toSelect.contains(selected))
				hierarchy.getSelectionModel().setSelectedObject(selected, true);
		});
		return tasks;
	}
	

}
