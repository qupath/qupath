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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Plugin for calculating Delaunay clustering, and associated features.
 * 
 * Note: This currently has an unfortunate dependency on the GUI, because it displays its own custom overlay.
 * Consequently it isn't in the 'correct' qupath-opencv-processing package... yet.
 * 
 * Warning! Because the implementation will have to change in the future, it is best not to rely on this class!
 * 
 * TODO: Handle overlay elsewhere in a more general way - probably by storing Delaunay data along with PathObjectHierarchy (for example).
 * 
 * @author Pete Bankhead
 *
 */
public class DelaunayClusteringPlugin extends AbstractInteractivePlugin<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(DelaunayClusteringPlugin.class);
	
	private QuPathViewer viewer;
	private DelaunayOverlay overlay;
	
	public DelaunayClusteringPlugin() {
		super();
	}	
	
	@Override
	public void preprocess(final PluginRunner<BufferedImage> runner) {
		
		ImageData<?> imageData = runner.getImageData();
		if (imageData == null)
			return;
		
		// Get a reference to any viewer (for possible overlay)
		viewer = null;
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null) {
			for (QuPathViewer viewer2 : qupath.getViewers()) {
				if (viewer2.getImageData() == runner.getImageData()) {
					viewer = viewer2;
				}
			}
			logger.trace("Found viewer: {}", viewer);
		}
		
		// Handle overlays
		if (viewer != null) {
			// Ensure any existing DelaunayOverlays are removed from the viewer
			overlay = (DelaunayOverlay)viewer.getOverlayLayer(DelaunayOverlay.class);
			while (overlay != null) {
				viewer.removeOverlay(overlay);
				overlay = (DelaunayOverlay)viewer.getOverlayLayer(DelaunayOverlay.class);
			}
			
			// Create an overlay, if needed
			if (params.getBooleanParameterValue("showOverlay"))
				overlay = new DelaunayOverlay(viewer.getOverlayOptions(), runner.getImageData());
			else
				overlay = null;
		}

	}
	
	
	@Override
	protected void postprocess(PluginRunner<BufferedImage> pluginRunner) {
		if (viewer != null && overlay != null) {
			viewer.addOverlay(overlay);
			viewer = null;
			overlay = null;
		}
	}
	

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(PathAnnotationObject.class, TMACoreObject.class, PathRootObject.class);
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
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList()
				.addDoubleParameter("distanceThreshold", "Distance threshold", 0, "pixels", "Distance threshold - edges longer than this will be omitted")
				.addDoubleParameter("distanceThresholdMicrons", "Distance threshold", 0, GeneralTools.micrometerSymbol(), "Distance threshold - edges longer than this will be omitted")
				.addBooleanParameter("limitByClass", "Limit edges to same class", false, "Prevent edges linking objects with different base classifications")
				.addBooleanParameter("addClusterMeasurements", "Add cluster measurements", false, "Add measurements derived from clustering connected objects")
				.addBooleanParameter("showOverlay", "Show overlay", true);
		
		ImageServer<?> server = imageData.getServer();
		boolean hasMicrons = server != null && server.hasPixelSizeMicrons();
		params.setHiddenParameters(hasMicrons, "distanceThreshold");
		params.setHiddenParameters(!hasMicrons, "distanceThresholdMicrons");
		return params;
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<BufferedImage> runner) {
		if (runner.getHierarchy() == null)
			return Collections.emptyList();
		
		List<PathObject> selected = new ArrayList<>(runner.getHierarchy().getSelectionModel().getSelectedObjects());
		if (selected.isEmpty())
			return Collections.singletonList(runner.getHierarchy().getRootObject());
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
	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
		
		// Get pixel sizes, if possible
		ImageServer<?> server = imageData.getServer();
		double pixelWidth = 1, pixelHeight = 1;
		boolean hasMicrons = server != null && server.hasPixelSizeMicrons();
		if (hasMicrons) {
			pixelWidth = server.getPixelWidthMicrons();
			pixelHeight = server.getPixelHeightMicrons();
		}
		double distanceThresholdPixels;
		if (server.hasPixelSizeMicrons())
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThresholdMicrons") / server.getAveragedPixelSizeMicrons();
		else
			distanceThresholdPixels = params.getDoubleParameterValue("distanceThreshold");


		tasks.add(new DelaunayRunnable(
				parentObject,
				params.getBooleanParameterValue("addClusterMeasurements"),
				pixelWidth,
				pixelHeight,
				distanceThresholdPixels,
				params.getBooleanParameterValue("limitByClass"),
				overlay
				));
	}
	
	
	
	
	static class DelaunayRunnable implements PathTask {
		
		private DelaunayOverlay overlay;
		
		private PathObject parentObject;
		private double pixelWidth;
		private double pixelHeight;
		private double distanceThresholdPixels;
		
		private boolean addClusterMeasurements;
		private boolean limitByClass;
		
		private DelaunayTriangulation dt;
		
		private String lastResult = null;
		
		DelaunayRunnable(final PathObject parentObject, final boolean addClusterMeasurements, final double pixelWidth, final double pixelHeight, final double distanceThresholdPixels, final boolean limitByClass, final DelaunayOverlay overlay) {
			this.parentObject = parentObject;
			this.pixelWidth = pixelWidth;
			this.pixelHeight = pixelHeight;
			this.addClusterMeasurements = addClusterMeasurements;
			this.distanceThresholdPixels = distanceThresholdPixels;
			this.limitByClass = limitByClass;
			this.overlay = overlay;
		}

		@Override
		public void run() {
			
			List<PathObject> pathObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false);
			pathObjects = pathObjects.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
			if (pathObjects.isEmpty()) {
				lastResult = "No detection descendant objects for " + parentObject;
				return;
			}
			
			dt = new DelaunayTriangulation(pathObjects, pixelWidth, pixelHeight, distanceThresholdPixels, limitByClass);
			dt.addNodeMeasurements();
			if (addClusterMeasurements)
				dt.addClusterMeasurements();
			
			lastResult = "Delaunay triangulation calculated for " + parentObject;
		}

		@Override
		public void taskComplete() {
			if (dt != null && overlay != null)
				overlay.addDelaunay(dt);
		}

		@Override
		public String getLastResultsDescription() {
			return lastResult;
		}
		
		
	}
	
	
	
//	@Override
//	public void actionPerformed(ActionEvent e) {
//		PathObjectHierarchy hierarchy = viewer.getHierarchy();
//		if (hierarchy == null)
//			return;
//
//		DelaunayOverlay overlay = (DelaunayOverlay)viewer.getOverlayLayer(DelaunayOverlay.class);
//		if (overlay != null)
//			viewer.removeOverlay(overlay);
//		overlay = new DelaunayOverlay(viewer.getOverlayOptions(), viewer.getImageData());
//		
//		DelaunayTriangulation dt;
//		TMAGrid tmaGrid = hierarchy.getTMAGrid();
//		if (tmaGrid != null) {
//			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
//				dt = new DelaunayTriangulation(hierarchy.getDescendantObjects(core, null, PathDetectionObject.class));
//				dt.updateOverlay(overlay);
//				dt.addNodeMeasurements();
//				System.out.println("Completed " + core);
//			}
//		} else {
//			dt = new DelaunayTriangulation(hierarchy.getObjects(null, PathDetectionObject.class));
//			dt.addNodeMeasurements();
//			dt.updateOverlay(overlay);
//		}
//		viewer.addOverlay(overlay);
//	}

}
