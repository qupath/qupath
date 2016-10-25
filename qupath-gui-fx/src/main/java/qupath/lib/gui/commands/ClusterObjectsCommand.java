package qupath.lib.gui.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QPEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ClusterObjectsCommand implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(ClusterObjectsCommand.class);
	
	private QuPathGUI qupath;
	
	public ClusterObjectsCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null)
			return;
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		int n = 1;
		
		for (TMACoreObject core : hierarchy.getTMAGrid().getTMACoreList()) {
			int nClusters = 5;
			
			if (core.isMissing())
				continue;
	
			PathObject selectedObject = core;//QPEx.getSelectedObject();
	
			List<PathObject> detections = selectedObject == null ? hierarchy.getObjects(null, PathDetectionObject.class) : hierarchy.getDescendantObjects(selectedObject, null, PathDetectionObject.class);
	
			Collection<String> measurements = PathClassificationLabellingHelper.getAvailableFeatures(detections);
			// Remove DAB + hematoxylin features
			//measurements = measurements.stream().filter({m -> !(m.startsWith("DAB") || m.startsWith("Hematoxylin"))}).collect(Collectors.toList())
			//measurements = measurements.stream().filter({m -> m.contains("Smoothed")}).collect(Collectors.toList())
			//measurements = measurements.stream().filter({m -> m.contains("OD Sum")}).collect(Collectors.toList())
	
			if (measurements.size() > detections.size() || detections.size() < nClusters) {
				logger.error("Not enough features!");
			    return;
			}
	
			ClusterManager clusterManager = new ClusterManager(detections, measurements);
	
			//def clusterer = new DBSCANClusterer<>(1, (int)(Math.max(3, (int)(detections.size()/10000.0))))
			KMeansPlusPlusClusterer<ClusterableDetection> clusterer = new KMeansPlusPlusClusterer<>(nClusters);
	
			logger.info("Starting to cluster " + detections.size() + " objects into " + nClusters + " clusters " + " using " + measurements.size() + " features");
			List<CentroidCluster<ClusterableDetection>> centroids = clusterer.cluster(clusterManager.getClusterableDetections());
			logger.info("Clustering complete! " + centroids.size() + " clusters created");
	
			for (Cluster<ClusterableDetection> centroid : centroids) {
				logger.info(centroid.toString());
			    PathClass pathClass = PathClassFactory.getPathClass("Cluster " + n);
			    int color = (int)(Math.random() * Integer.MAX_VALUE) & 0x00FFFFFF;
			    pathClass.setColor(color);
			    for (ClusterableDetection centroidObject : centroid.getPoints()) {
			        centroidObject.getPathObject().setPathClass(pathClass);
	//		        println(centroidObject.toString())
			    }
			    n++;
			}
		}

		hierarchy.fireHierarchyChangedEvent(this);
	}

	
	static class ClusterManager {

	    private List<PathObject> detections = new ArrayList<>();

	    private List<String> measurementNames = new ArrayList<>();
	    private Map<String, RunningStatistics> stats = new HashMap<>();


//	    public ClusterManager(final Collection<PathObject> pathObjects) {
//	        this(pathObjects, PathClassificationLabellingHelper.getAvailableFeatures(pathObjects));
//	    }

	    public ClusterManager(final Collection<PathObject> pathObjects, final Collection<String> measurements) {
	        super();



	        this.detections.addAll(pathObjects);

	        // Get all measurements
	        measurementNames.addAll(measurements);

	        // Get means & standard deviations
	        for (String m : measurementNames) {
	            RunningStatistics statistics = new RunningStatistics();
	            for (PathObject detection : detections) {
	                double value = detection.getMeasurementList().getMeasurementValue(m);
	                if (!Double.isNaN(value))
	                    statistics.addValue(value);
	            }
	            stats.put(m, statistics);
//	            println(m + ":  " + statistics.getMean());
	        }

	    }

	    public List<PathObject> getDetections() {
	        return Collections.unmodifiableList(detections);
	    }

	    public List<ClusterableDetection> getClusterableDetections() {
	        // Create clusterable detections
	        List<ClusterableDetection> clusterableDetections = new ArrayList<>();
	        for (PathObject detection : detections) {
	            clusterableDetections.add(new ClusterableDetection(this, detection));
	        }
	        return clusterableDetections;
	    }


	    public double normalizeMeasurement(final String measurement, final double value) {
	        RunningStatistics stat = stats.get(measurement);
	        if (Double.isNaN(value))
	            return stat.getMean();
	        return (value - stat.getMean()) / stat.getStdDev();
	    }


	    public List<String> getMeasurementNames() {
	        return measurementNames;
	    }

	    public int nMeasurements() {
	        return measurementNames.size();
	    }

	    public String toString() {
	        return "Cluster manager with " + detections.size() + " detections and " + measurementNames.size() + " measurements";
	    }

	}


	static class ClusterableDetection implements Clusterable {

	    private PathObject pathObject;
	    private ClusterManager manager;
	    private double[] point;

	    public ClusterableDetection(final ClusterManager manager, final PathObject pathObject) {
	        this.manager = manager;
	        this.pathObject = pathObject;
	    }

	    public double[] getPoint() {
	        if (point == null)
	            createPoint();
	        return point;
	    }

	    public PathObject getPathObject() {
	        return pathObject;
	    }

	    private void createPoint() {
//	        def[] point = [manager.nMeasurements()]
//	        int i = 0;
	        MeasurementList ml = pathObject.getMeasurementList();

	        point = manager.getMeasurementNames().stream().mapToDouble(m ->
	            manager.normalizeMeasurement(m, ml.getMeasurementValue(m))).toArray();

//	        for (String m : manager.getMeasurementNames()) {
//	            point[i] = manager.normalizeMeasurement(ml.getMeasurementValue(m));
//	        }


	    }

	}
	
}
