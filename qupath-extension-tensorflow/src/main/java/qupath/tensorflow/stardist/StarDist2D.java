package qupath.tensorflow.stardist;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.CellTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.tensorflow.TensorFlowTools;

/**
 * Cell detection based on the following method:
 * <pre>
 *   Uwe Schmidt, Martin Weigert, Coleman Broaddus, and Gene Myers.
 *     "Cell Detection with Star-convex Polygons."
 *   <i>International Conference on Medical Image Computing and Computer-Assisted Intervention (MICCAI)</i>, Granada, Spain, September 2018.
 * </pre>
 * See the main repo at https://github.com/mpicbg-csbd/stardist
 * <p>
 * Very much inspired by stardist-imagej at https://github.com/mpicbg-csbd/stardist-imagej but re-written from scratch to use OpenCV and 
 * adapt the method of converting predictions to contours (very slightly) to be more QuPath-friendly.
 * <p>
 * Models are expected in the same format as required by the Fiji plugin.
 * 
 * @author Pete Bankhead (this implementation, but based on the others)
 */
public class StarDist2D {

	private final static Logger logger = LoggerFactory.getLogger(StarDist2D.class);
	
	/**
	 * Builder to help create a {@link StarDist2D} with custom parameters.
	 */
	public static class Builder {
		
		private String modelPath = null;
		private ColorTransform[] channels = new ColorTransform[0];
		private double threshold = 0.5;
		
		private double cellExpansion = Double.NaN;
		private double cellConstrainScale = Double.NaN;
		private double pixelSize = Double.NaN;
		
		private int tileWidth = 1024;
		private int tileHeight = 1024;
		
		private List<ImageOp> ops = new ArrayList<>();
		
		private boolean includeProbability = false;
		
		private Builder(String modelPath) {
			this.modelPath = modelPath;
			this.ops.add(ImageOps.Core.ensureType(PixelType.FLOAT32));
		}
		
		/**
		 * Probability threshold to apply for detection, between 0 and 1.
		 * @param threshold
		 * @return this builder
		 * @see #includeProbability(boolean)
		 */
		public Builder threshold(double threshold) {
			this.threshold = threshold;
			return this;
		}
		
		/**
		 * Add preprocessing operations, if required.
		 * @param ops
		 * @return this builder
		 */
		public Builder preprocess(ImageOp... ops) {
			for (var op : ops)
				this.ops.add(op);
			return this;
		}
		
		/**
		 * Specify channels. Useful for detecting nuclei for one channel 
		 * within a multi-channel image, or potentially for trained models that 
		 * support multi-channel input.
		 * @param channels 0-based indices of the channels to use
		 * @return this builder
		 */
		public Builder channels(int... channels) {
			return channels(Arrays.stream(channels)
					.mapToObj(c -> ColorTransforms.createChannelExtractor(c))
					.toArray(ColorTransform[]::new));
		}
		
		/**
		 * Specify channels by name. Useful for detecting nuclei for one channel 
		 * within a multi-channel image, or potentially for trained models that 
		 * support multi-channel input.
		 * @param channels 0-based indices of the channels to use
		 * @return this builder
		 */
		public Builder channels(String... channels) {
			return channels(Arrays.stream(channels)
					.map(c -> ColorTransforms.createChannelExtractor(c))
					.toArray(ColorTransform[]::new));
		}
		
		/**
		 * Define the channels (or color transformers) to apply to the input image.
		 * <p>
		 * This makes it possible to supply color deconvolved channels, for example.
		 * @param channels
		 * @return this builder
		 */
		public Builder channels(ColorTransform... channels) {
			this.channels = channels.clone();
			return this;
		}
		
		/**
		 * Amount by which to expand detected nuclei to approximate the cell area.
		 * Units are the same as for the {@link PixelCalibration} of the input image.
		 * <p>
		 * Warning! This is rather experimental, relying heavily on JTS and a convoluted method of 
		 * resolving overlaps using a Voronoi tessellation.
		 * <p>
		 * In short, be wary.
		 * @param distance
		 * @return this builder
		 */
		public Builder cellExpansion(double distance) {
			this.cellExpansion = distance;
			return this;
		}
		
		/**
		 * Constrain any cell expansion defined using {@link #cellExpansion(double)} based upon 
		 * the nucleus size. Only meaningful for values &gt; 1; the nucleus is expanded according 
		 * to the scale factor, and used to define the maximum permitted cell expansion.
		 * 
		 * @param scale
		 * @return this builder
		 */
		public Builder cellConstrainScale(double scale) {
			this.cellConstrainScale = scale;
			return this;
		}
		
		/**
		 * Optionally include the prediction probability as a measurement for the object.
		 * This can be helpful if detection is applied with a low (generous) probability threshold, 
		 * with the intention of filtering out less likely detections later.
		 * 
		 * @param include true if the probability should be included, false otherwise
		 * @return this builder
		 * @see #threshold(double)
		 */
		public Builder includeProbability(boolean include) {
			this.includeProbability = include;
			return this;
		}
		
		/**
		 * Resolution at which the cell detection should be run.
		 * The units depend upon the {@link PixelCalibration} of the input image.
		 * <p>
		 * The default is to use the full resolution of the input image.
		 * <p>
		 * For an image calibrated in microns, the recommended default is approximately 0.5.
		 * 
		 * @param pixelSize
		 * @return this builder
		 */
		public Builder pixelSize(double pixelSize) {
			this.pixelSize = pixelSize;
			return this;
		}
		
		/**
		 * Size in pixels of a tile used for detection.
		 * Note that tiles are independently normalized, and therefore tiling can impact 
		 * the results. Default is 1024.
		 * @param tileSize
		 * @return this builder
		 */
		public Builder tileSize(int tileSize) {
			this.tileWidth = tileSize;
			this.tileHeight = tileSize;
			return this;
		}
				
		/**
		 * Apply percentile normalization to the input image channels.
		 * <p>
		 * Note that this can be used in combination with {@link #preprocess(ImageOp...)}, 
		 * in which case the order in which the operations are applied depends upon the order 
		 * in which the methods of the builder are called.
		 * 
		 * @param min minimum percentile
		 * @param max maximum percentile
		 * @return this builder
		 */
		public Builder normalizePercentiles(double min, double max) {
			this.ops.add(ImageOps.Normalize.percentile(min, max));
			return this;
		}
		
		/**
		 * Create a {@link StarDist2D}, all ready for detection.
		 * @return
		 */
		public StarDist2D build() {
			var stardist = new StarDist2D();
			
			var mergedOps = new ArrayList<>(ops);
			mergedOps.add(TensorFlowTools.createOp(modelPath, tileWidth, tileHeight));
			mergedOps.add(ImageOps.Core.ensureType(PixelType.FLOAT32));
			
			stardist.op = ImageOps.buildImageDataOp(channels)
					.appendOps(mergedOps.toArray(ImageOp[]::new));
			stardist.threshold = threshold;
			stardist.pixelSize = pixelSize;
			stardist.cellConstrainScale = cellConstrainScale;
			stardist.cellExpansion = cellExpansion;
			stardist.tileWidth = tileWidth;
			stardist.tileHeight = tileHeight;
			stardist.includeProbability = includeProbability;
			return stardist;
		}
		
	}
	
	private double threshold;
	private ImageDataOp op;
	private double pixelSize;
	private double cellExpansion;
	private double cellConstrainScale;
	
	private boolean includeProbability = false;
	
	private int tileWidth = 1024;
	private int tileHeight = 1024;
	
	
	/**
	 * Detect cells within a parent object.
	 * 
	 * @param imageData the image data containing the object
	 * @param parent the parent object; existing child objects will be removed, and replaced by the detected cells
	 * @param fireUpdate if true, a hierarchy update will be fired on completion
	 */
	public void detectObjects(ImageData<BufferedImage> imageData, PathObject parent, boolean fireUpdate) {
		Objects.nonNull(parent);
		var detections = detectObjects(imageData, parent.getROI());
		
		if (cellExpansion > 0 || cellConstrainScale > 0) {
			double expansion = cellExpansion / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
			detections = CellTools.detectionsToCells(detections, expansion, cellConstrainScale);			
		}
		
		parent.clearPathObjects();
		parent.addPathObjects(detections);
		parent.setLocked(true);
		if (fireUpdate)
			imageData.getHierarchy().fireHierarchyChangedEvent(imageData.getHierarchy(), parent);
	}
	
	/**
	 * Detect cells within a {@link ROI}.
	 * @param imageData image to which the ROI belongs
	 * @param roi region of interest which which to detect cells. If null, the entire image will be used.
	 * @return the detected objects. Note that these will not automatically be added to the object hierarchy.
	 */
	public List<PathObject> detectObjects(ImageData<BufferedImage> imageData, ROI roi) {

		var resolution = imageData.getServer().getPixelCalibration();
		if (Double.isFinite(pixelSize) && pixelSize > 0) {
			double downsample = pixelSize / resolution.getAveragedPixelSize().doubleValue();
			resolution = resolution.createScaledInstance(downsample, downsample);
		}
		var server = ImageOps.buildServer(imageData, op, resolution, tileWidth, tileHeight);
		
		RegionRequest request;
		if (roi == null)
			request = RegionRequest.createInstance(server);
		else
			request = RegionRequest.createInstance(
				server.getPath(),
				server.getDownsampleForResolution(0),
				roi);

		var tiles = server.getTileRequestManager().getTileRequests(request);
		var mask = roi == null ? null : roi.getGeometry();
				
		return tiles.parallelStream()
			.flatMap(t -> detectObjectsForTile(op, imageData, t.getRegionRequest(), threshold, includeProbability, mask).stream())
			.collect(Collectors.toList());
	}
		
	private static List<PathObject> detectObjectsForTile(ImageDataOp op, ImageData<BufferedImage> imageData, RegionRequest request, double threshold, boolean includeProbability, Geometry mask) {
		
		Mat mat;
		try {
			mat = op.apply(imageData, request);
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage(), e);
			return Collections.emptyList();
		}
		
		FloatIndexer indexer = mat.createIndexer();
		var nuclei = createNuclei(indexer, threshold, request, mask);
		return createDetections(nuclei, ImagePlane.getDefaultPlane(), includeProbability);
	}
	
	/**
	 * Create a builder to customize detection parameters.
	 * @param modelPath path to the StarDist/TensorFlow model to use for prediction.
	 * @return
	 */
	public static Builder builder(String modelPath) {
		return new Builder(modelPath);
	}
	
	
	
	private static List<PotentialNucleus> createNuclei(FloatIndexer indexer, double threshold, RegionRequest request, Geometry mask) {
	    long[] sizes = indexer.sizes();
	    long[] inds = new long[3];
	    int h = (int)sizes[0];
	    int w = (int)sizes[1];
	    int nRays = (int)sizes[2] - 1;
	    double[][] rays = sinCosAngles(nRays);
	    double[] raySine = rays[0];
	    double[] rayCosine = rays[1];

	    var nuclei = new ArrayList<PotentialNucleus>();
	    
	    var locator = mask == null ? null : new SimplePointInAreaLocator(mask);

	    var downsample = request.getDownsample();
	    var factory = GeometryTools.getDefaultFactory();
	    var precisionModel = factory.getPrecisionModel();
	    for (int y = 0; y < h; y++) {
	        inds[0] = y;
	        for (int x = 0; x < w; x++) {
	            inds[1] = x;
	            inds[2] = 0;
	            double prob = indexer.get(inds);
	            if (prob < threshold)
	                continue;
	            var coords = new ArrayList<Coordinate>();
	            for (int a = 1; a <= nRays; a++) {
	                inds[2] = a;
	                double val = indexer.get(inds);
	                double xx = precisionModel.makePrecise(request.getX() + (x + val * rayCosine[a-1]) * downsample);
	                double yy = precisionModel.makePrecise(request.getY() + (y + val * raySine[a-1]) * downsample);
	                coords.add(new Coordinate(xx, yy));
	            }
	            coords.add(coords.get(0));
	            var polygon = factory.createPolygon(coords.toArray(Coordinate[]::new));
	            if (locator == null || locator.locate(new Centroid(polygon).getCentroid()) != Location.EXTERIOR)
	            	nuclei.add(new PotentialNucleus(polygon, prob));
	        }
	    }
	    Collections.sort(nuclei, Comparator.comparingDouble((PotentialNucleus n) -> n.getProbability()).reversed());
	    return nuclei;
	}


	private static List<PathObject> createDetections(List<PotentialNucleus> potentialNuclei, ImagePlane plane, boolean includeProbability) {
	    var detections = new ArrayList<PathObject>();
	    int n = potentialNuclei.size();
	    boolean[] skipped = new boolean[n];
	    int skipCount = 0;
	    int skipErrorCount = 0;
	    for (int i = 0; i < n; i++) {
	        if (skipped[i])
	            continue;
	        var nucleus = potentialNuclei.get(i);
	        var roi = GeometryTools.geometryToROI(nucleus.geometry, plane);
	        var detection = PathObjects.createDetectionObject(roi);
	        if (includeProbability) {
	        	try (var ml = detection.getMeasurementList()) {
	        		ml.putMeasurement("Detection probability", nucleus.getProbability());
	        	}
	        }
	        detections.add(detection);
	        for (int j = i+1; j < n; j++) {
	            if (skipped[j])
	                continue;
	            var nucleus2 = potentialNuclei.get(j);
	            // TODO: Consider simply subtracting the other area, rather than entirely removing the nucleus
	            try {
		            if (nucleus.geometry.intersects(nucleus2.geometry)) {
		                var difference = nucleus2.geometry.difference(nucleus.geometry);
		                if (difference instanceof Polygon && difference.getArea() > nucleus2.fullArea / 2.0)
		                    nucleus2.geometry = difference;
		                else {
		                    skipped[j] = true;
		                    skipCount++;
		                }
		            }
	            } catch (Exception e) {
                    skipCount++;
                    skipErrorCount++;	            	
	            }
	        }
	    }
	    if (skipErrorCount > 0)
	    	logger.warn("Skipped {} nucleus detection(s) due to error ({}% of all skipped)", 
	    			skipErrorCount, GeneralTools.formatNumber(skipErrorCount*100.0/skipCount, 1));
	    return detections;
	}
	
	
	private static double[][] sinCosAngles(int n) {
	    double[][] angles = new double[2][n];
	    for (int i = 0; i < n; i++) {
	        double theta = 2 * Math.PI / n * i;
	        angles[0][i] = Math.sin(theta);
	        angles[1][i] = Math.cos(theta);
	    }
	    return angles;
	}
	
	
	private static class PotentialNucleus {
		
		private Geometry geometry;
	    private double fullArea;
	    private double probability;

	    PotentialNucleus(Geometry geom, double prob) {
	        this.geometry = geom;
	        this.probability = prob;
	        this.fullArea = geom.getArea();
	    }

	    double getProbability() {
	        return probability;
	    };
		
	}
	
}
