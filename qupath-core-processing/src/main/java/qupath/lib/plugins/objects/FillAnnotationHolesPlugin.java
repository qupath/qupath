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

package qupath.lib.plugins.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to create new annotations by expanding the size of existing annotations.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class FillAnnotationHolesPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private String resultString = null;
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Collections.singleton(PathAnnotationObject.class);
	}

	@Override
	public String getName() {
		return "Fill annotation holes";
	}

	@Override
	public String getDescription() {
		return "Fill holes occurring within selected annotations (AreaROIs or some PolygonROIs)";
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
		return getHierarchy(runner).getSelectionModel().getSelectedObjects().stream().filter(p -> p.isAnnotation()).collect(Collectors.toList());
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {}
	
	@Override
	protected Collection<Runnable> getTasks(final PluginRunner<T> runner) {
		Collection<? extends PathObject> parentObjects = getParentObjects(runner);
		if (parentObjects == null || parentObjects.isEmpty())
			return Collections.emptyList();
		
		// Add a single task, to avoid multithreading - which may complicate setting parents
		List<Runnable> tasks = new ArrayList<>(1);
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		
		// Want to reset selection
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		Collection<PathObject> previousSelection = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		
		tasks.add(() -> {
			Map<PathROIObject, ROI> toUpdate = new HashMap<>();
			for (PathObject pathObject : parentObjects) {
				ROI roiOrig = pathObject.getROI();
				if (roiOrig == null || !roiOrig.isArea())
					continue;
				ROI roiUpdated = roiOrig;
				roiUpdated = RoiTools.fillHoles(roiUpdated);
				if (roiOrig != roiUpdated && pathObject instanceof PathROIObject) {
					toUpdate.put((PathROIObject)pathObject, roiUpdated);
				}
			}
			if (toUpdate.isEmpty())
				return;
			hierarchy.getSelectionModel().clearSelection();
			if (!toUpdate.isEmpty()) {
				hierarchy.removeObjects(toUpdate.keySet(), true);
				toUpdate.forEach((p, r) -> p.setROI(r));
				hierarchy.addPathObjects(toUpdate.keySet());
			}
			hierarchy.getSelectionModel().selectObjects(previousSelection);
			hierarchy.getSelectionModel().setSelectedObject(selected, true);
		});
		return tasks;
	}
	

}
