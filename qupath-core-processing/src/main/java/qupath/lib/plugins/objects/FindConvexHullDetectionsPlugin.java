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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.ConvexHull;

/**
 * Plugin to identify/remove detections from the convex hull of all detections.
 * <p>
 * Currently works only for TMA cores.
 * <p>
 * Purpose is to remove edge detections, where the tissue quality tends to be lower.
 * 
 * @author Pete Bankhead
 *
 */
public class FindConvexHullDetectionsPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private final static String commandName = "Find convex hull detections (TMA)";
	
	private transient AtomicInteger nObjectsRemoved = new AtomicInteger();
	
	static List<PathObject> getConvexHullDetections(final PathObjectHierarchy hierarchy, final PathObject parent, final int nIterations) {
		
		Map<Point2, PathObject> pointsMap = new HashMap<>();
		List<PathObject> convexDetections = new ArrayList<>();

		Collection<PathObject> pathObjects = PathObjectTools.getDescendantObjects(parent, null, PathDetectionObject.class);
		if (pathObjects.isEmpty())
			return Collections.emptyList();

		// Populate the points map
		pointsMap.clear();
		for (PathObject child : pathObjects) {
			if (!child.hasROI())
				continue;
			pointsMap.put(new Point2(child.getROI().getCentroidX(), child.getROI().getCentroidY()), child);
		}

		// Determine what to remove
		List<Point2> points = new ArrayList<>(pointsMap.keySet());
		for (int i = 0; i < nIterations; i++) {
			List<Point2> convexPoints = ConvexHull.getConvexHull(points);
			if (convexPoints != null) {
				for (Point2 p : convexPoints)
					convexDetections.add(pointsMap.get(p));
				points.removeAll(convexPoints);
			}
		}

		return convexDetections;
	}

	@Override
	public String getName() {
		return commandName;
	}

	@Override
	public String getDescription() {
		return "Iteratively remove detections at the boundary of a TMA core";
	}

	@Override
	public String getLastResultsDescription() {
		boolean deleteImmediately = Boolean.TRUE.equals(params.getBooleanParameterValue("deleteImmediately"));
		String process = deleteImmediately ? "Removed " : "Selected ";
		if (Boolean.TRUE.equals(params.getBooleanParameterValue("deleteImmediately")))
		if (nObjectsRemoved.get() == 1)
			return process + " 1 object";
		return process + nObjectsRemoved.get() + " objects";
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		if (selected instanceof TMACoreObject)
			return Collections.singleton(selected);
		if (hierarchy.getTMAGrid() != null)
			return hierarchy.getTMAGrid().getTMACoreList();
		else
			return Collections.emptyList();
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Collections.singleton(TMACoreObject.class);
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		return new ParameterList()
				.addIntParameter("nIterations", "Number of iterations", 10, null, "Number of times to iteratively identify detections from the convex hull of the detection centroids")
				.addBooleanParameter("deleteImmediately", "Delete immediately", false, "Immediately delete detections, rather than selecting them only");
	}
	
	
	@Override
	public boolean runPlugin(final PluginRunner<T> pluginRunner, final String arg) {
		nObjectsRemoved.set(0);
		return super.runPlugin(pluginRunner, arg);
	}
	

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
		boolean deleteImmediately = Boolean.TRUE.equals(params.getBooleanParameterValue("deleteImmediately"));
		tasks.add(new PathTask() {
			
			private List<PathObject> toRemove;
			private int nRemoved;

			@Override
			public void run() {
				toRemove = getConvexHullDetections(imageData.getHierarchy(), parentObject, params.getIntParameterValue("nIterations"));
				nRemoved = toRemove.size();
			}

			@Override
			public void taskComplete(boolean wasCancelled) {
				if (wasCancelled)
					return;
				
				if (toRemove != null && !toRemove.isEmpty()) {
					if (deleteImmediately)
						imageData.getHierarchy().removeObjects(toRemove, false);
					else {
						imageData.getHierarchy().getSelectionModel().deselectObject(parentObject);
						imageData.getHierarchy().getSelectionModel().selectObjects(toRemove);
					}
					toRemove = null;
					nObjectsRemoved.addAndGet(nRemoved);
				}
			}

			@Override
			public String getLastResultsDescription() {
				String process = deleteImmediately ? "Removed " : "Selected ";
				if (Boolean.TRUE.equals(params.getBooleanParameterValue("deleteImmediately")))
				if (nRemoved == 1)
					return process + " 1 object";
				return process + nRemoved + " objects";
			}
			
		});
	}

}
