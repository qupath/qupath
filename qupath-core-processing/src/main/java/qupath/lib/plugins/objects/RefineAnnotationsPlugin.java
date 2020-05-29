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

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
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
public class RefineAnnotationsPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private ParameterList params = new ParameterList()
			.addDoubleParameter("minFragmentSizeMicrons", "Minimum fragment size", 0, GeneralTools.micrometerSymbol()+"^2", "Area of the smallest fragment to keep (set <= 0 to keep all fragments)")
			.addDoubleParameter("minFragmentSizePixels", "Minimum fragment size", 0, "px^2", "Area of the smallest fragment to keep (set <= 0 to keep all fragments)")
			.addDoubleParameter("maxHoleSizeMicrons", "Maximum hole size", 0, GeneralTools.micrometerSymbol()+"^2", "Area of the largest hole to keep (set < 0 to fill all holes, or 0 to fill no holes)")
			.addDoubleParameter("maxHoleSizePixels", "Maximum hole size", 0, "px^2", "Area of the largest hole to keep (set < 0 to fill all holes, or 0 to fill no holes)")
			;
	
	private String resultString = null;
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Collections.singleton(PathAnnotationObject.class);
	}

	@Override
	public String getName() {
		return "Remove fragments & holes";
	}

	@Override
	public String getDescription() {
		return "Refine annotation object ROIs by removing small fragments/annotations and/or filling holes";
	}

	@Override
	public String getLastResultsDescription() {
		return resultString;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(hasMicrons, "minFragmentSizePixels", "maxHoleSizePixels");
		params.setHiddenParameters(!hasMicrons, "minFragmentSizeMicrons", "maxHoleSizeMicrons");
		return params;
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
		
		double minFragmentSize;
		double maxHoleSize, maxHoleSizeTemp;
		ImageServer<T> server = getServer(runner);
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			double pixelAreaMicrons = cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons();
			minFragmentSize = params.getDoubleParameterValue("minFragmentSizeMicrons") / pixelAreaMicrons;
			maxHoleSizeTemp = params.getDoubleParameterValue("maxHoleSizeMicrons") / pixelAreaMicrons;
		} else {
			minFragmentSize = params.getDoubleParameterValue("minFragmentSizePixels");
			maxHoleSizeTemp = params.getDoubleParameterValue("maxHoleSizePixels");
		}
		// Handle negative values
		if (maxHoleSizeTemp < 0)
			maxHoleSize = Double.POSITIVE_INFINITY;
		else
			maxHoleSize = maxHoleSizeTemp;
		
		// Want to reset selection
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		Collection<PathObject> previousSelection = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		
		tasks.add(() -> {
			List<PathObject> toRemove = new ArrayList<>();
			Map<PathROIObject, ROI> toUpdate = new HashMap<>();
			for (PathObject pathObject : parentObjects) {
				ROI roiOrig = pathObject.getROI();
				if (roiOrig == null || !roiOrig.isArea())
					continue;
				ROI roiUpdated = RoiTools.removeSmallPieces(roiOrig, minFragmentSize, maxHoleSize);
				if (roiUpdated == null || roiUpdated.isEmpty())
					toRemove.add(pathObject);
				else if (roiOrig != roiUpdated && pathObject instanceof PathROIObject) {
					toUpdate.put((PathROIObject)pathObject, roiUpdated);
				}
			}
			if (toRemove.isEmpty() && toUpdate.isEmpty())
				return;
			hierarchy.getSelectionModel().clearSelection();
			if (!toRemove.isEmpty())
				hierarchy.removeObjects(toRemove, true);
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
