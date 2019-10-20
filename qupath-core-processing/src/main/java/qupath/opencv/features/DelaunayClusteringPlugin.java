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

package qupath.opencv.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Plugin for calculating Delaunay clustering, and associated features.
 * <p>
 * Warning! Because the implementation will have to change in the future, it is best not to rely on this class!
 * 
 * @author Pete Bankhead
 *
 */
public class DelaunayClusteringPlugin<T> extends AbstractInteractivePlugin<T> {

	final private static Logger logger = LoggerFactory.getLogger(DelaunayClusteringPlugin.class);
	
	/**
	 * Constructor.
	 */
	public DelaunayClusteringPlugin() {
		super();
	}	
	
	@Override
	protected void preprocess(PluginRunner<T> pluginRunner) {
		super.preprocess(pluginRunner);
		// Reset any previous connections
		pluginRunner.getImageData().removeProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
	}
	
	@Override
	protected void postprocess(PluginRunner<T> pluginRunner) {
		super.postprocess(pluginRunner);
		getHierarchy(pluginRunner).fireHierarchyChangedEvent(this);
	}
	

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(TMACoreObject.class, PathAnnotationObject.class, PathRootObject.class);
	}

	@Override
	public String getName() {
		return "Delaunay clustering";
	}

	@Override
	public String getDescription() {
		return "Cluster neighboring objects, optionally limited by classification and/or distance";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
		ParameterList params = new ParameterList()
				.addDoubleParameter("distanceThreshold", "Distance threshold", 0, "pixels", "Distance threshold - edges longer than this will be omitted")
				.addDoubleParameter("distanceThresholdMicrons", "Distance threshold", 0, GeneralTools.micrometerSymbol(), "Distance threshold - edges longer than this will be omitted")
				.addBooleanParameter("limitByClass", "Limit edges to same class", false, "Prevent edges linking objects with different base classifications")
				.addBooleanParameter("addClusterMeasurements", "Add cluster measurements", false, "Add measurements derived from clustering connected objects")
				;
		
		ImageServer<?> server = imageData.getServer();
		boolean hasMicrons = server != null && server.getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(hasMicrons, "distanceThreshold");
		params.setHiddenParameters(!hasMicrons, "distanceThresholdMicrons");
		return params;
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		if (hierarchy == null)
			return Collections.emptyList();
		
		List<PathObject> selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		if (selected.isEmpty())
			return Collections.singletonList(hierarchy.getRootObject());
		else
			return selected;
//		List<PathObject> selected = runner.getHierarchy().getSelectionModel().getSelectedObjects().stream().filter(p -> p.isTMACore()).collect(Collectors.toList());
//		if (selected.isEmpty()) {
//			if (runner.getHierarchy().getTMAGrid() != null)
//				return PathObjectTools.getTMACoreObjects(runner.getHierarchy(), false);
//			return Collections.singletonList(runner.getHierarchy().getRootObject());
//		} else
//			return selected;
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
		
		// Get pixel sizes, if possible
		ImageServer<?> server = imageData.getServer();
		double pixelWidth = 1, pixelHeight = 1;
		PixelCalibration cal = server.getPixelCalibration();
		boolean hasMicrons = server != null && cal.hasPixelSizeMicrons();
		if (hasMicrons) {
			pixelWidth = cal.getPixelWidthMicrons();
			pixelHeight = cal.getPixelHeightMicrons();
		}
		double distanceThresholdPixels;
		if (cal.hasPixelSizeMicrons())
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThresholdMicrons") / cal.getAveragedPixelSizeMicrons();
		else
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThreshold");


		tasks.add(new DelaunayRunnable(
				imageData,
				parentObject,
				params.getBooleanParameterValue("addClusterMeasurements"),
				pixelWidth,
				pixelHeight,
				distanceThresholdPixels,
				params.getBooleanParameterValue("limitByClass")
				));
	}
	
	
	
	
	private static class DelaunayRunnable implements PathTask {
		
		private ImageData<?> imageData;
		private PathObject parentObject;
		private double pixelWidth;
		private double pixelHeight;
		private double distanceThresholdPixels;
		
		private boolean addClusterMeasurements;
		private boolean limitByClass;
		
		private PathObjectConnectionGroup result;
		
		private String lastResult = null;
		
		DelaunayRunnable(final ImageData<?> imageData, final PathObject parentObject, final boolean addClusterMeasurements, final double pixelWidth, final double pixelHeight, final double distanceThresholdPixels, final boolean limitByClass) {
			this.imageData = imageData;
			this.parentObject = parentObject;
			this.pixelWidth = pixelWidth;
			this.pixelHeight = pixelHeight;
			this.addClusterMeasurements = addClusterMeasurements;
			this.distanceThresholdPixels = distanceThresholdPixels;
			this.limitByClass = limitByClass;
		}

		
		@Override
		public void run() {
			
			List<PathObject> pathObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false);
			pathObjects = pathObjects.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
			if (pathObjects.isEmpty()) {
				lastResult = "No detection descendant objects for " + parentObject;
				return;
			}
			
			DelaunayTriangulation dt = new DelaunayTriangulation(pathObjects, pixelWidth, pixelHeight, distanceThresholdPixels, limitByClass);
			
			DefaultPathObjectConnectionGroup result = new DefaultPathObjectConnectionGroup(dt);
			pathObjects = new ArrayList<>(result.getPathObjects());
			
			dt.addNodeMeasurements();
			if (addClusterMeasurements)
				dt.addClusterMeasurements();
			
			
			this.result = result;
			
			lastResult = "Delaunay triangulation calculated for " + parentObject;
		}
		

		@Override
		public void taskComplete(boolean wasCancelled) {
			if (wasCancelled)
				return;
			
			if (result != null && imageData != null) {
				synchronized(imageData) {
					Object o = imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
					PathObjectConnections connections = null;
					if (o instanceof PathObjectConnections)
						connections = (PathObjectConnections)o;
					else {
						connections = new PathObjectConnections();
						imageData.setProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS, connections);
					}
					connections.addGroup(result);
				}
			}
		}

		@Override
		public String getLastResultsDescription() {
			return lastResult;
		}
		
		
	}

}
