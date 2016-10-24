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
import qupath.lib.objects.DefaultPathObjectConnections;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.DefaultPathObjectConnections.MeasurementNormalizer;
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
public class DelaunayClusteringPlugin<T> extends AbstractInteractivePlugin<T> {

	final private static Logger logger = LoggerFactory.getLogger(DelaunayClusteringPlugin.class);
	
	public DelaunayClusteringPlugin() {
		super();
	}	
	
	@Override
	protected void preprocess(PluginRunner<T> pluginRunner) {
		super.preprocess(pluginRunner);
		ImageData<T> imageData = pluginRunner.getImageData();
		if (imageData != null) {
			if (params.getBooleanParameterValue("showOverlay"))
				imageData.setProperty(DefaultPathObjectConnections.KEY_OBJECT_CONNECTIONS, new ArrayList<PathObjectConnections>());
			else
				imageData.setProperty(DefaultPathObjectConnections.KEY_OBJECT_CONNECTIONS, null);
		}
	}

	
	@Override
	protected void postprocess(PluginRunner<T> pluginRunner) {
		super.postprocess(pluginRunner);
		pluginRunner.getHierarchy().fireHierarchyChangedEvent(this);
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
	public ParameterList getDefaultParameterList(ImageData<T> imageData) {
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
	protected Collection<? extends PathObject> getParentObjects(PluginRunner<T> runner) {
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
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
		
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
				imageData,
				parentObject,
				params.getBooleanParameterValue("addClusterMeasurements"),
				pixelWidth,
				pixelHeight,
				distanceThresholdPixels,
				params.getBooleanParameterValue("limitByClass")
				));
	}
	
	
	
	
	static class DelaunayRunnable implements PathTask {
		
		private ImageData<?> imageData;
		private PathObject parentObject;
		private double pixelWidth;
		private double pixelHeight;
		private double distanceThresholdPixels;
		
		private boolean addClusterMeasurements;
		private boolean limitByClass;
		
		private PathObjectConnections result;
		
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
			
			MeasurementNormalizer normalizer = new MeasurementNormalizer(pathObjects);
			List<String> measurements = new ArrayList<>(normalizer.getAvailableMeasurements());
			
			measurements = measurements.stream().filter(p -> {
				return (p.toLowerCase().contains("haralick") || p.toLowerCase().contains("smooth")) && !p.toLowerCase().contains("cluster") && !p.toLowerCase().startsWith("pca");
//				return !p.toLowerCase().contains("cluster");
			}).collect(Collectors.toList());
//			measurements = measurements.stream().filter(p -> {
//				return p.toLowerCase().contains("haralick") && !p.toLowerCase().contains("cluster");
//			}).collect(Collectors.toList());
			
//			double correlationThreshold = 0.8;
//			List<String> measurementsToRemove = new ArrayList<>();
//			PearsonsCorrelation corr = new PearsonsCorrelation();
//			for (int i = 0; i < measurements.size(); i++) {
//				String namei = measurements.get(i);
//				double[] xArray = pathObjects.stream().mapToDouble(p -> p.getMeasurementList().getMeasurementValue(namei)).toArray();
//				for (int j = i+1; j < measurements.size(); j++) {
//					String namej = measurements.get(j);
//					double[] yArray = pathObjects.stream().mapToDouble(p -> p.getMeasurementList().getMeasurementValue(namej)).toArray();
//					double pcc = Math.abs(corr.correlation(xArray, yArray));
//					if (pcc > correlationThreshold) {
//						measurementsToRemove.add(namej);
//						break;
//					}
//				}
//			}
//			System.err.println("Remove: " + measurementsToRemove.size());
//			measurements.removeAll(measurementsToRemove);
			
			
			
//			int k = 4;
//			int attempts = 1;
//			Mat data = new Mat(pathObjects.size(), measurements.size(), CvType.CV_32F);
//			
//			double[] values = new double[measurements.size()];
//			for (int i = 0; i < pathObjects.size(); i++) {
//				values = normalizer.normalizeMeanStdDev(pathObjects.get(i), measurements, 0, values);
//				data.put(i, 0, values);
//			}
//			
//			Mat eigenvectors = new Mat();
//			Mat mean = new Mat();
//			Core.PCACompute(data, mean, eigenvectors);
//			Mat pca = new Mat();
//			Core.PCAProject(data, mean, eigenvectors, pca);
//			for (int i = 0; i < pathObjects.size(); i++) {
//				MeasurementList list = pathObjects.get(i).getMeasurementList();
//				float[] output = new float[1];
//				for (int p = 0; p < 5; p++) {
//					pca.get(i, p, output);
//					list.putMeasurement("PCA " + (p+1), output[0]);
//				}
//			}
//			eigenvectors.release();
//			mean.release();
//			pca.release();
//			data.release();
//			data = pca.colRange(0, 1);
//			
//			
//			Mat bestLabels = new Mat(pathObjects.size(), 1, CvType.CV_32S);
//			TermCriteria termCriteria = new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 100, 0.001);
//			Core.kmeans(data, k, bestLabels, termCriteria, attempts, Core.KMEANS_PP_CENTERS);
//			
////			Mat centers = new Mat(k, measurements.size(), CvType.CV_32F);
////			Core.kmeans(data, k, bestLabels, termCriteria, attempts, Core.KMEANS_PP_CENTERS, centers);
////			centers.release();
//			
//			Map<PathObject, Integer> mapLabels = new HashMap<>();
//			int[] label = new int[1];
//			for (int i = 0; i < pathObjects.size(); i++) {
//				bestLabels.get(i, 0, label);
//				mapLabels.put(pathObjects.get(i), label[0]);
//			}
//			
//			data.release();
//			bestLabels.release();
//			
//			
//			// Move through and break connections
//			for (PathObject pathObject : pathObjects) {
//				int currentLabel = mapLabels.get(pathObject);
//				// Set PathClass
//				pathObject.setPathClass(
//						PathClassFactory.getPathClass("Cluster " + currentLabel,
//								ColorTools.makeRGB((int)(Math.random() * 256), (int)(Math.random() * 256), (int)(Math.random() * 256))
//								)
//						);
//			}
			
			DelaunayTriangulation dt = new DelaunayTriangulation(pathObjects, pixelWidth, pixelHeight, distanceThresholdPixels, limitByClass);
			
			DefaultPathObjectConnections result = new DefaultPathObjectConnections(dt);
			pathObjects = new ArrayList<>(result.getPathObjects());

			
			
//			result.sortByDistance();
//			RunningStatistics stats = new RunningStatistics();
//			RunningStatistics statsRandom = new RunningStatistics();
//			for (PathObject pathObject : pathObjects) {
//				List<PathObject> connections = result.getConnectedObjects(pathObject);
//				if (connections.isEmpty())
//					continue;
//				normalizer.
//				double distance = normalizer.getMeasurementDistance(pathObject, connections.get(0), measurements);				
////				double distance = normalizer.getMeasurementDistance(pathObject, connections.get(connections.size()/2), measurements);				
//				stats.addValue(distance);
//				
//				distance = normalizer.getMeasurementDistance(pathObject, pathObjects.get((int)(Math.random() * pathObjects.size())), measurements);				
//				statsRandom.addValue(distance);
//			}
//			
//			System.err.println("Mean: " + stats.getMean());
//			System.err.println("Std dev: " + stats.getStdDev());
//
//			System.err.println("Random Mean: " + statsRandom.getMean());
//			System.err.println("Random Std dev: " + statsRandom.getStdDev());
//
//			// Determine connection-breaking threshold
//			double threshold = stats.getMean();// + stats.getStdDev();
//			
//			// Work with squared values now
//			threshold = threshold*threshold;
//
//			// Move through and break connections
//			for (PathObject pathObject : pathObjects) {
//				List<PathObject> connections = result.getConnectedObjects(pathObject);
//				if (connections.isEmpty())
//					continue;
//				for (PathObject connection : connections.toArray(new PathObject[0])) {
//					double distanceSq = normalizer.getMeasurementDistanceSquared(pathObject, connections.get(connections.size()/2), measurements);
//					if (distanceSq > threshold)
//						result.breakConnection(pathObject, connection);
//				}
//			}

			
			dt.addNodeMeasurements();
			if (addClusterMeasurements)
				dt.addClusterMeasurements();
			
			
			this.result = result;
			
			lastResult = "Delaunay triangulation calculated for " + parentObject;
		}
		
		
//		@Override
//		public void run() {
//			
//			List<PathObject> pathObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false);
//			pathObjects = pathObjects.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
//			if (pathObjects.isEmpty()) {
//				lastResult = "No detection descendant objects for " + parentObject;
//				return;
//			}
//			
//			DelaunayTriangulation dt = new DelaunayTriangulation(pathObjects, pixelWidth, pixelHeight, distanceThresholdPixels, limitByClass);
//			
//			DefaultPathObjectConnections result = new DefaultPathObjectConnections(dt);
//			pathObjects = new ArrayList<>(result.getPathObjects());
//			MeasurementNormalizer normalizer = new MeasurementNormalizer(pathObjects);
//			List<String> measurements = new ArrayList<>(normalizer.getAvailableMeasurements());
//			
//			measurements = measurements.stream().filter(p -> p.toLowerCase().contains("haralick")).collect(Collectors.toList());
//			
//			int k = 3;
//			int attempts = 5;
//			Mat data = new Mat(pathObjects.size(), measurements.size(), CvType.CV_32F);
//			
//			double[] values = new double[measurements.size()];
//			for (int i = 0; i < pathObjects.size(); i++) {
//				values = normalizer.normalizeMeanStdDev(pathObjects.get(i), measurements, 0, values);
//				data.put(i, 0, values);
//			}
//			
//			Mat bestLabels = new Mat(pathObjects.size(), 1, CvType.CV_32S);
//			TermCriteria termCriteria = new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 100, 0.001);
//			Core.kmeans(data, k, bestLabels, termCriteria, attempts, Core.KMEANS_PP_CENTERS);
//			
//			Map<PathObject, Integer> mapLabels = new HashMap<>();
//			int[] label = new int[1];
//			for (int i = 0; i < pathObjects.size(); i++) {
//				bestLabels.get(i, 0, label);
//				mapLabels.put(pathObjects.get(i), label[0]);
//			}
//			
//			data.release();
//			bestLabels.release();
//			
//			
//			// Move through and break connections
//			for (PathObject pathObject : pathObjects) {
//				List<PathObject> connections = result.getConnectedObjects(pathObject);
//				if (connections.isEmpty())
//					continue;
//				int currentLabel = mapLabels.get(pathObject);
//				for (PathObject connection : connections.toArray(new PathObject[0])) {
//					int connectionLabel = mapLabels.get(connection);
//					if (currentLabel != connectionLabel)
//						result.breakConnection(pathObject, connection);
//				}
//				
//				
//				// Set PathClass while we're here (why not...?)
//				pathObject.setPathClass(
//						PathClassFactory.getPathClass("Cluster " + currentLabel,
//								ColorTools.makeRGB((int)(Math.random() * 256), (int)(Math.random() * 256), (int)(Math.random() * 256))
//								)
//						);
//			}
//			
//			
//			
////			result.sortByDistance();
////			RunningStatistics stats = new RunningStatistics();
////			RunningStatistics statsRandom = new RunningStatistics();
////			for (PathObject pathObject : pathObjects) {
////				List<PathObject> connections = result.getConnectedObjects(pathObject);
////				if (connections.isEmpty())
////					continue;
////				normalizer.
////				double distance = normalizer.getMeasurementDistance(pathObject, connections.get(0), measurements);				
//////				double distance = normalizer.getMeasurementDistance(pathObject, connections.get(connections.size()/2), measurements);				
////				stats.addValue(distance);
////				
////				distance = normalizer.getMeasurementDistance(pathObject, pathObjects.get((int)(Math.random() * pathObjects.size())), measurements);				
////				statsRandom.addValue(distance);
////			}
////			
////			System.err.println("Mean: " + stats.getMean());
////			System.err.println("Std dev: " + stats.getStdDev());
////
////			System.err.println("Random Mean: " + statsRandom.getMean());
////			System.err.println("Random Std dev: " + statsRandom.getStdDev());
////
////			// Determine connection-breaking threshold
////			double threshold = stats.getMean();// + stats.getStdDev();
////			
////			// Work with squared values now
////			threshold = threshold*threshold;
////
////			// Move through and break connections
////			for (PathObject pathObject : pathObjects) {
////				List<PathObject> connections = result.getConnectedObjects(pathObject);
////				if (connections.isEmpty())
////					continue;
////				for (PathObject connection : connections.toArray(new PathObject[0])) {
////					double distanceSq = normalizer.getMeasurementDistanceSquared(pathObject, connections.get(connections.size()/2), measurements);
////					if (distanceSq > threshold)
////						result.breakConnection(pathObject, connection);
////				}
////			}
//
//			
//			dt.addNodeMeasurements();
//			if (addClusterMeasurements)
//				dt.addClusterMeasurements();
//			
//			
//			this.result = result;
//			
//			lastResult = "Delaunay triangulation calculated for " + parentObject;
//		}

		@Override
		public void taskComplete() {
			if (result != null && imageData != null) {
				Object o = imageData.getProperty("OBJECT_CONNECTIONS");
				Collection<PathObjectConnections> connections = null;
				if (o != null) {
					try {
						connections = (Collection<PathObjectConnections>)o;
					} catch (ClassCastException e) {
						logger.error("Invalid contents of OBJECT_CONNECTIONS property: {}", o);
					}
				}
				if (connections == null)
					connections = new ArrayList<>();
				connections.add(result);
			}
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
