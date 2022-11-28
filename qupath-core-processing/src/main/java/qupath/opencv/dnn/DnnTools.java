/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021-2022 QuPath developers, The University of Edinburgh
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

package qupath.opencv.dnn;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point2fVector;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.StringVector;
import org.bytedeco.opencv.opencv_dnn.ClassificationModel;
import org.bytedeco.opencv.opencv_dnn.DetectionModel;
import org.bytedeco.opencv.opencv_dnn.IntFloatPair;
import org.bytedeco.opencv.opencv_dnn.KeypointsModel;
import org.bytedeco.opencv.opencv_dnn.MatShapeVector;
import org.bytedeco.opencv.opencv_dnn.Model;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.bytedeco.opencv.opencv_dnn.SegmentationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.common.LogTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

/**
 * Tools for working with OpenCV's DNN module.
 * 
 * @author Pete Bankhead
 * @since 0.3.0
 */
public class DnnTools {
	
	private static final Logger logger = LoggerFactory.getLogger(DnnTools.class);
	
	/**
	 * Register a new {@link DnnModel} class for JSON serialization/deserialization.
	 * @param <T>
	 * @param subtype
	 * @param name
	 * @deprecated since v0.4.0; use {@link DnnModels#registerDnnModel(Class, String)} instead.
	 */
	@SuppressWarnings("rawtypes")
	@Deprecated
	public static <T extends DnnModel> void registerDnnModel(Class<T> subtype, String name) {
		LogTools.warnOnce(logger, "DnnTools.registerDnnModel is deprecated - use DnnModels.registerDnnModel instead");
		DnnModels.registerDnnModel(subtype, name);
	}
	
	
	/**
	 * Initiative building and configuring an {@link OpenCVDnn}.
	 * <p>
	 * Note that {@link DnnModels#buildModel(DnnModelParams)} should generally be used instead 
	 * to create an arbitrary {@link DnnModel}, since it can potentially use different libraries 
	 * or frameworks.
	 * 
	 * @param modelPath
	 * @return
	 * @apiNote since {@link OpenCVDnn} is used to build other things (e.g. a {@link Net}, a {@link Model}), 
	 *          this is, rather inelegantly, a builder for a builder. However the difference is that, once you have 
	 *          an {@link OpenCVDnn}, configuration is fixed.
	 * @see DnnModels#buildModel(DnnModelParams)
	 */
	public static OpenCVDnn.Builder builder(String modelPath) {
		return OpenCVDnn.builder(modelPath);
	}
	
	
	// Default to using CUDA if available (user can override)
	private static boolean cudaAvailable = false;
	private static boolean useCuda = false;


	static {
		// Check if CUDA available
		int cudaDeviceCount = opencv_core.getCudaEnabledDeviceCount();
		if (cudaDeviceCount > 0) {
			var backends = opencv_dnn.getAvailableBackends();
			long n = backends.size();
			for (int i = 0; i < n; i++) {
				var bkend = backends.first(i);
				var target = backends.second(i);
				logger.trace("Available backend {}, target {}", bkend, target);
				if (bkend == opencv_dnn.DNN_BACKEND_CUDA && target == opencv_dnn.DNN_TARGET_CUDA) {
					logger.info("CUDA detected and will be used if possible. Use DnnTools.setUseCuda(false) to turn this off.");
					cudaAvailable = true;
					useCuda = true;
				}
			}
			if (!cudaAvailable)
				logger.warn("CUDA is not available - no compatible backend found with OpenCV DNN");
		} else if (cudaDeviceCount < 0) {
			// Warn if compiled with CUDA support but unable to actually use it
			logger.warn("CUDA is not available - device count returns {}, which may mean a driver is missing or incompatible",
					cudaDeviceCount);
		} else {
			// Log more quietly since this will usually be the case
			logger.debug("CUDA is not available (OpenCV not compiled with CUDA support)");			
		}
	}
	
	
	/**
	 * Temp method to help debugging through scripts.
	 * Likely to be replaced in the future with a proper public API to allow the backend to be set, 
	 * however, this may require new enums (rather than OpenCV's ints) for ease of use 
	 * and better compatibility checks.
	 */
	static void logAvailableBackends() {
		var backends = opencv_dnn.getAvailableBackends();
		long n = backends.size();
		for (int i = 0; i < n; i++) {
			var bkend = backends.first(i);
			var target = backends.second(i);
			logger.info("Available backend {}, target {}", bkend, target);
		}
	}
	

	/**
	 * Query whether CUDA is reported as available by OpenCV.
	 * If it is, it will be used by default until {@link #setUseCuda(boolean)} is used to turn if off.
	 * @return
	 */
	public static boolean isCudaAvailable() {
		return cudaAvailable;
	}

	/**
	 * Request that CUDA is used.
	 * This will be ignored if {@link #isCudaAvailable()} returns false, therefore the main purpose of 
	 * this method is to disable the use of CUDA if it would otherwise be employed.
	 * 
	 * @param requestUseCuda
	 */
	public static void setUseCuda(boolean requestUseCuda) {
		if (requestUseCuda && !cudaAvailable) {
			logger.warn("CUDA is not available - request will be ignored");
			return;
		}
		useCuda = requestUseCuda;
	}
	
	/**
	 * Returns true if CUDA is available and requested.
	 * Classes that could potentially use CUDA should query this request before attempting to use it.
	 * @return true if CUDA should be used, false otherwise
	 */
	public static boolean useCuda() {
		return useCuda;
	}
	
	
	/**
	 * Get the names of all unconnected output layers.
	 * @param net 
	 * @return
	 */
	public static List<String> getOutputLayerNames(Net net) {
		var names = new ArrayList<String>();
		for (var bp : net.getUnconnectedOutLayersNames().get()) {
			names.add(bp.getString());
		}
		return names;
	}
	
	/**
	 * Get the names of all unconnected output layers.
	 * @param net 
	 * @param inputShape 
	 * @return
	 */
	public static Map<String, DnnShape> getOutputLayers(Net net, DnnShape... inputShape) {
//		var fixedInput = inputShape.isFixed();
		
		// TODO: Handle where inputShape is empty or unknown
		
		var output = new LinkedHashMap<String, DnnShape>();
		var layerIds = net.getUnconnectedOutLayers();
		int[] ids = new int[(int)layerIds.limit()];
		layerIds.get(ids);
		
		// If we don't provide an input shape, OpenCV can bring down the VM...
		var names = getOutputLayerNames(net);
		if (inputShape.length == 0)
			return names.stream().collect(Collectors.toMap(n -> n, n -> DnnShape.UNKNOWN_SHAPE));
		
		var inputShapes = new MatShapeVector(Arrays.stream(inputShape).map(s -> toIntPointer(s)).toArray(IntPointer[]::new));
		
		var inLayerShapes = new MatShapeVector();
		var outLayerShapes = new MatShapeVector();
		for (var name : names) {
			var id = net.getLayerId(name);
			net.getLayerShapes(inputShapes, id, inLayerShapes, outLayerShapes);
			var shapes = parseShape(outLayerShapes);
			if (shapes.size() > 1)
				logger.warn("Multiple output shapes for layer {}, will use the first only", name);
			output.put(name, shapes.get(0));
		}
		
		
		inputShapes.close();
		inLayerShapes.close();
		outLayerShapes.close();
		
		return output;
	}
	
	private static IntPointer toIntPointer(DnnShape shape) {
		return new IntPointer(Arrays.stream(shape.getShape()).mapToInt(l -> (int)l).toArray());
	}
	
    /**
     * Parse the layers for a Net, which allows inspection of names and sizes.
     * @param net the Net to parse
     * @param width input width
     * @param height input height
     * @param channels input channels
     * @param batchSize input batch size
     * @return
     */
	public static List<DNNLayer> parseLayers(Net net, int width, int height, int channels, int batchSize) {
		MatShapeVector netInputShape = getShapeVector(width, height, channels, batchSize);
		return parseLayers(net, netInputShape);
	}
	
	private static MatShapeVector getShapeVector(int width, int height, int channels, int batchSize) {
		int[] shapeInput = new int[] {batchSize, channels, height, width};
		return new MatShapeVector(new IntPointer(shapeInput));
	}
	
	@SuppressWarnings("unchecked")
	private static List<DNNLayer> parseLayers(Net net, MatShapeVector netInputShape) {
		List<DNNLayer> list = new ArrayList<>();
		try (PointerScope scope = new PointerScope()) {
			StringVector names = net.getLayerNames();
			MatShapeVector inputShape = new MatShapeVector();
			MatShapeVector outputShape = new MatShapeVector();
			for (var nameBytes : names.get()) {
				String name = nameBytes.getString();
				int id = net.getLayerId(name);
				net.getLayerShapes(netInputShape, id, inputShape, outputShape);
				list.add(new DNNLayer(name, id, parseShape(inputShape), parseShape(outputShape)));
			}
		}
		return list;
	}

	/**
	 * Extract Strings from a {@link StringVector}.
	 * @param vector
	 * @return
	 */
	public static List<String> parseStrings(StringVector vector) {
		List<String> list = new ArrayList<>();
		int n = (int)vector.size();
		for (int i = 0; i < n; i++)
			list.add(vector.get(i).getString());
		return list;
	}
	
	/**
	 * Extract {@link Mat} dimensions from a {@link MatShapeVector}.
	 * @param vector
	 * @return
	 */
	public static List<DnnShape> parseShape(MatShapeVector vector) {
		var shapes = new ArrayList<DnnShape>();
		for (var pointer : vector.get()) {
			long[] shape = new long[(int)pointer.limit()];
			for (int i = 0; i < shape.length; i++)
				shape[i] = pointer.get(i);
			shapes.add(DnnShape.of(shape));
		}
		return shapes;
	}
	
	
	/**
	 * Helper class to summarize a DNN layer.
	 */
	public static class DNNLayer {
		
		private String name;
		private int id;
		private List<DnnShape> inputShapes;
		private List<DnnShape> outputShapes;
		
		private DNNLayer(String name, int id, List<DnnShape> inputShapes, List<DnnShape> outputShapes) {
			this.name = name;
			this.id = id;
			this.inputShapes = inputShapes;
			this.outputShapes = outputShapes;
		}
		
		/**
		 * Layer name.
		 * @return
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Layer ID.
		 * @return
		 */
		public int getID() {
			return id;
		}
		
		/**
		 * Layer input shape. This may depend on the input shape provided when summarizing the model
		 * @return
		 */
		public List<DnnShape> getInputShapes() {
			return Collections.unmodifiableList(inputShapes);
		}
		
		/**
		 * Layer output shape. This may depend on the input shape provided when summarizing the model
		 * @return
		 */
		public List<DnnShape> getOutputShapes() {
			return Collections.unmodifiableList(outputShapes);
		}
		
		@Override
		public String toString() {
			return String.format("%s \t%s -> %s" ,
					name, inputShapes, outputShapes);
		}
		
	}
	
	
	
	/**
	 * Create a (multiline) summary String for a {@link Net}, given the specified image input dimensions.
	 * @param net the Net to summarize
	 * @param width input width
	 * @param height input height
	 * @param nChannels input channel count
	 * @return
	 * @throws IOException if an error occurs when loading the model
	 */
	public static String summarize(Net net, int width, int height, int nChannels) throws IOException {
		StringBuilder sb = new StringBuilder();
		
		MatShapeVector netInputShape = getShapeVector(width, height, nChannels, 1);
		
		StringVector types = new StringVector();
		net.getLayerTypes(types);
		sb.append("Layer types:");
		for (String type : parseStrings(types))
			sb.append("\n\t").append(type);
		
		sb.append("\nLayers:");
		for (var layer : parseLayers(net, netInputShape)) {
			sb.append("\n\t").append(layer.toString());
		}
		
		long flops = net.getFLOPS(netInputShape);
		sb.append("\nFLOPS: ").append(flops);

		SizeTPointer weights = new SizeTPointer(1L);
		SizeTPointer blobs = new SizeTPointer(1L);
		net.getMemoryConsumption(netInputShape, weights, blobs);
		sb.append("\nMemory (weights): ").append(weights.get());
		sb.append("\nMemory (blobs): ").append(blobs.get());

		return sb.toString();
	}
	
	
	
	private static Mat readMat(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		var img = server.readRegion(request);
		return OpenCVTools.imageToMat(img);
	}
	
	/**
	 * Read an image patch, optionally with a fixed size.
	 * If the patch width and height are specified, these relate to the output (downsampled) image 
	 * and are centered on the ROI centroid. Otherwise the ROI bounds are used.
	 * 
	 * @param server the image server
	 * @param roi the ROI for which the patch should be extracted
	 * @param downsample the downsample value
	 * @param width the patch width, or -1 if the ROI bounds should be used
	 * @param height the patch height, or -1 if the ROI bounds should be used
	 * @return
	 * @throws IOException
	 */
	public static Mat readPatch(ImageServer<BufferedImage> server, ROI roi, double downsample, int width, int height) throws IOException {
		Mat input;
		if (width < 0 && height < 0) {
			var request = RegionRequest.createInstance(server.getPath(), downsample, roi);
			input = readMat(server, request);
		} else if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Width and height must both be > 0, or < 0 if the full ROI is used");
		} else {
			double scaledWidth = width * downsample;
			double scaledHeight = height * downsample;
			int xi = (int)Math.round(roi.getCentroidX() - scaledWidth/2.0);
			int yi = (int)Math.round(roi.getCentroidY() - scaledHeight/2.0);
			int xi2 = (int)Math.round(xi + scaledWidth);
			int yi2 = (int)Math.round(yi + scaledHeight);
			
			int x = GeneralTools.clipValue(xi, 0, server.getWidth());
			int x2 = GeneralTools.clipValue(xi2, 0, server.getWidth());
			int y = GeneralTools.clipValue(yi, 0, server.getHeight());
			int y2 = GeneralTools.clipValue(yi2, 0, server.getHeight());
			
			var request = RegionRequest.createInstance(server.getPath(), downsample, x, y, x2-x, y2-y, roi.getZ(), roi.getT());
			
			input = readMat(server, request);
			
			// Ensure image is the correct size if needed
			int matWidth = input.cols();
			int matHeight = input.rows();
			if (matWidth != width || matHeight != height) {
				if (matWidth > width) {
					input.put(input.colRange(0, width));
					matWidth = width;
				}
				if (matHeight > height) {
					input.put(input.rowRange(0, height));
					matHeight = height;
				}
				if (height > matHeight || width > matWidth) {
					// Calculate relative amount of padding for left and top
					double xProp = calculateFirstPadProportion(xi, xi2, 0, server.getWidth());
					double yProp = calculateFirstPadProportion(yi, yi2, 0, server.getHeight());
					
					int padX = (int)Math.round((width - matWidth) * xProp);
					int padY = (int)Math.round((height - matHeight) * yProp);
					
					// TODO: Consider most appropriate boundary padding
					opencv_core.copyMakeBorder(input, input,
							padY,
							height - matHeight - padY,
							padX,
							width - matWidth - padX,
							opencv_core.BORDER_CONSTANT);
//							opencv_core.BORDER_REPLICATE);
				}
			}
		}
		
		OpenCVTools.ensureContinuous(input, true);
		return input;
	}
	
	
	private static double calculateFirstPadProportion(double v1, double v2, double minVal, double maxVal) {
		// No left padding
		if (v1 >= minVal)
			return 0;
		// No right padding
		if (v2 <= maxVal)
			return 1;
		// Combination of left and right padding
		double d1 = minVal - v1;
		double d2 = v2 - maxVal;
		return d1 / (d1 + d2);
	}
	
	/**
	 * Apply a classification model to an image patch to classify an object.
	 * 
	 * @param model the model for prediction
	 * @param pathObject the object to classify
	 * @param server the image supplying the patch
	 * @param downsample the requested downsample at which classification should be applied
	 * @param classifier function to convert the classification label into a {@link PathClass}
	 * @param predictionMeasurement optional measurement name for storing the prediction value (often treated as a probability) 
	 *                              in the measurement list of the object.
	 * @return true if the classification of the object has changed, false otherwise
	 * @throws IOException if the patch cannot be read
	 */
	public static boolean classify(ClassificationModel model, PathObject pathObject, ImageServer<BufferedImage> server, double downsample,  IntFunction<PathClass> classifier, String predictionMeasurement) throws IOException {
		var roi = pathObject.getROI();
		if (roi == null) {
			logger.warn("Cannot classify an object without a ROI!");
			return false;
		}
		var request = RegionRequest.createInstance(server, downsample);
		var input = readMat(server, request);
		boolean changes = classify(model, pathObject, input, classifier, predictionMeasurement);
		input.close();
		return changes;
	}
	
	/**
	 * Apply a classification model to an image patch to classify an object.
	 * If a patch width and height both &gt; 0, the patch is cropped around the ROI centroid and padded if necessary.
	 * If a patch width and height both &lt; 0, the bounding box of hte ROI is used directly and the model is assumed able to 
	 * resize if needed.
	 * If the patch width and height are anything else, an {@link IllegalArgumentException} is thrown.
	 * 
	 * @param model the model for prediction
	 * @param pathObject the object to classify
	 * @param server the image supplying the patch
	 * @param downsample the requested downsample at which classification should be applied
	 * @param width the fixed input size
	 * @param height the fixed input size
	 * @param classifier function to convert the classification label into a {@link PathClass}
	 * @param predictionMeasurement optional measurement name for storing the prediction value (often treated as a probability) 
	 *                              in the measurement list of the object.
	 * @return true if the classification of the object has changed, false otherwise
	 * @throws IOException if the patch cannot be read
	 * @throws IllegalArgumentException if the patch width or height are invalid
	 */
	public static boolean classify(ClassificationModel model, PathObject pathObject, ImageServer<BufferedImage> server, double downsample,  int width, int height, IntFunction<PathClass> classifier, String predictionMeasurement) throws IOException, IllegalArgumentException {
		var roi = pathObject.getROI();
		if (roi == null) {
			logger.warn("Cannot classify an object without a ROI!");
			return false;
		}
		boolean preferNucleus = true;
		Mat input = readPatch(server, PathObjectTools.getROI(pathObject, preferNucleus), downsample, width, height);
		return classify(model, pathObject, input, classifier, predictionMeasurement);
	}
	
	/**
	 * Apply a classification model to an existing image patch to classify an object.
	 * @param model the model for prediction
	 * @param pathObject the object to classify
	 * @param input image patch
	 * @param classifier function to convert the classification label into a {@link PathClass}
	 * @param predictionMeasurement optional measurement name for storing the prediction value (often treated as a probability) 
	 *                              in the measurement list of the object.
	 * @return true if the classification of the object has changed, false otherwise
	 */
	public static boolean classify(ClassificationModel model, PathObject pathObject, Mat input, IntFunction<PathClass> classifier, String predictionMeasurement) {
		IntFloatPair result;
		synchronized (model) {
			result = model.classify(input);
		}
		int ind = result.first();
		var pathClass = classifier == null ? null : classifier.apply(ind);
		boolean changed = pathClass != pathObject.getPathClass();
		pathObject.setPathClass(pathClass);
		if (predictionMeasurement != null) {
			pathObject.getMeasurementList().put(predictionMeasurement, result.second());
			pathObject.getMeasurementList().close();
		}
		result.close();
		result.deallocate();
		return changed;
	}
	
	/**
	 * Apply a segmentation model to an image region.
	 * @param model the segmentation model
	 * @param server the image
	 * @param request the region
	 * @return a {@link Mat} containing the segmentation results
	 * @throws IOException if the input image could not be read
	 */
	@SuppressWarnings("unchecked")
	public static Mat segment(SegmentationModel model, ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		var output = new Mat();
		try (var scope = new PointerScope()) {
			var input = readMat(server, request);
			segment(model, input, output);
			return output;
		}
	}

	/**
	 * Apply a segmentation model to an image region.
	 * @param model the segmentation model
	 * @param input the input image
	 * @param output the output image
	 * @return a {@link Mat} containing the segmentation results (the same as output, if provided)
	 */
	@SuppressWarnings("unchecked")
	public static Mat segment(SegmentationModel model, Mat input, Mat output) {
		if (output == null)
			output = new Mat();
		try (var scope = new PointerScope()) {
			synchronized (model) {
				model.segment(input, output);
			}
			return output;
		}
	}
	
	/**
	 * Apply a detection model to generate rectangles surrounding distinct structures.
	 * @param model the detection model
	 * @param server the image
	 * @param request the region within which detection should be applied
	 * @param classifier function to convert the classification label into a {@link PathClass}
 	 * @param creator function to create an object (e.g. detection, annotation) from a ROI
	 * @return a list of created objects
	 * @throws IOException if the image could not be read
	 */
	@SuppressWarnings("unchecked")
	public static List<PathObject> detect(DetectionModel model, ImageServer<BufferedImage> server, RegionRequest request, IntFunction<PathClass> classifier, Function<ROI, PathObject> creator) throws IOException {
		try (var scope = new PointerScope()) {
			var mat = readMat(server, request);
			return detect(model, mat, request, classifier, creator);
		}
	}

	
	/**
	 * Apply a detection model to generate rectangles surrounding distinct structures.
	 * @param model the detection model
	 * @param mat the image
	 * @param request the region corresponding to the Mat; if provided, this is used to scale and translate detected regions
	 * @param classifier function to convert the classification label into a {@link PathClass}
 	 * @param creator function to create an object (e.g. detection, annotation) from a ROI
	 * @return a list of created objects
	 */
	@SuppressWarnings("unchecked")
	public static List<PathObject> detect(DetectionModel model, Mat mat, RegionRequest request, IntFunction<PathClass> classifier, Function<ROI, PathObject> creator) {
		
		try (var scope = new PointerScope()) {
			var ids = new IntPointer();
			var preds = new FloatPointer();
			var rects = new RectVector();
			
			synchronized (model) {
				model.detect(mat, ids, preds, rects);
			}

			double downsample = request == null ? 1.0 : request.getDownsample();
			ImagePlane plane = request == null ? ImagePlane.getDefaultPlane() : request.getImagePlane();
			double xOrigin = request == null ? 0 : request.getX();
			double yOrigin = request == null ? 0 : request.getY();

			long n = rects.size();
			List<PathObject> pathObjects = new ArrayList<>();
			for (long i = 0; i < n; i++) {
				var rect = rects.get(i);
				var roi = ROIs.createRectangleROI(
						xOrigin + rect.x() * downsample,
						yOrigin + rect.y() * downsample,
						rect.width() * downsample,
						rect.height() * downsample,
						plane);
				
				var pathClass = classifier == null ? null : classifier.apply(ids.get(i));
				double pred = preds.get(i);
				var pathObject = creator.apply(roi);
				pathObject.setPathClass(pathClass);
				try (var ml = pathObject.getMeasurementList()) {
					ml.put("Probability", pred);
				}
				pathObjects.add(pathObject);
			}
			
			return pathObjects;
		}
		
	}
	
	

	/**
	 * TODO: Non-public placeholder until better designed/validated - subject to change
	 * @param model
	 * @param mat
	 * @param request
	 * @param mask
	 * @param threshold
	 * @param creator
	 * @return
	 */
	static PathObject detectKeypoints(KeypointsModel model, Mat mat, RegionRequest request, ROI mask, double threshold, Function<ROI, PathObject> creator) {
		var roi = detectKeypointsROI(model, mat, request, mask, threshold);
		return creator.apply(roi);
	}
	
	/**
	 * TODO: Non-public placeholder until better designed/validated - subject to change
	 * @param model
	 * @param mat
	 * @param request
	 * @param mask
	 * @param threshold
	 * @return
	 */
	static ROI detectKeypointsROI(KeypointsModel model, Mat mat, RegionRequest request, ROI mask, double threshold) {
		
		float thresh = (float)threshold;
		Point2fVector output;
		synchronized (model) {
			output = model.estimate(mat, thresh);
		}
		
		double downsample = request.getDownsample();
		double xOrigin = request.getX();
		double yOrigin = request.getY();
		
		var pointsArray = output.get();
		var points = new ArrayList<Point2>();
		for (var p : pointsArray) {
			double x = xOrigin + p.x() * downsample;
			double y = yOrigin + p.y() * downsample;
			// TODO: Consider IndexedPointInAreaLocator if needed for performance
			if (mask == null || mask.contains(x, y))
				points.add(new Point2(x, y));
		}
		return ROIs.createPointsROI(points, request.getImagePlane());
	}
	
	
	
	/**
	 * Create an OpenCV blob from one or more mats.
	 * @param mats
	 * @return
	 */
	public static Mat blobFromImages(Mat... mats) {
		// Empty
		if (mats.length == 0)
			return new Mat();
		
		if (mats.length == 1)
			return blobFromImage(mats[0]);
		
		int nChannels = mats[0].channels();
		if (nChannels == 1 || nChannels == 3 || nChannels == 4) {
			var matvec = new MatVector(mats);
			return opencv_dnn.blobFromImages(matvec);
		} else
			throw new UnsupportedOperationException("Converting multiple images to a blob is only supported for 1, 3, or 4 channels, sorry!");
	}
	
	/**
	 * Create an OpenCV blob from a single mat.
	 * @param mat
	 * @return
	 */
	public static Mat blobFromImage(Mat mat) {
		int nChannels = mat.channels();
		if (nChannels == 1 || nChannels == 3 || nChannels == 4)
			return opencv_dnn.blobFromImage(mat);
		return blobFromImages(Collections.singletonList(mat), 1.0, new Size(), new Scalar(), false, false);
	}
	
	/**
	 * Create an OpenCV blob from a Mat with optional scaling, resizing and cropping.
	 * @param mat input image
	 * @param scaleFactor scale factor
	 * @param size input width and height
	 * @param mean mean values for subtraction
	 * @param swapRB swap red and blue of the mean values
	 * @param crop center crop after resizing if needed
	 * @return a blob with axis order NCHW
	 */
	public static Mat blobFromImages(Mat mat, double scaleFactor, Size size, Scalar mean, boolean swapRB, boolean crop) {
		int nChannels = mat.channels();
		if (nChannels == 1 || nChannels == 3 || nChannels == 4)
			return opencv_dnn.blobFromImage(mat, scaleFactor, size, mean, swapRB, crop, opencv_core.CV_32F);
		return blobFromImages(Collections.singletonList(mat), scaleFactor, size, mean, swapRB, crop);			
	}
	
	/**
	 * Create an OpenCV blob from a batch of Mats with optional scaling, resizing and cropping.
	 * @param mats input images
	 * @param scaleFactor scale factor
	 * @param size input width and height
	 * @param mean mean values for subtraction
	 * @param swapRB swap red and blue of the mean values
	 * @param crop center crop after resizing if needed
	 * @return a blob with axis order NCHW
	 */
	public static Mat blobFromImages(Collection<Mat> mats, double scaleFactor, Size size, Scalar mean, boolean swapRB, boolean crop) {
		
//		if (mat.depth() != opencv_core.CV_32F) {
//			var mat2 = new Mat();
//			mat.convertTo(mat2, opencv_core.CV_32F);
//			mat2 = mat;
//		}
		
		Mat blob = null;
		Mat first = mats.iterator().next();
		int nChannels = first.channels();
		if (nChannels == 1 || nChannels == 3 || nChannels == 4) {
			if (mats.size() == 1)
				blob = opencv_dnn.blobFromImage(first, scaleFactor, size, mean, swapRB, crop, opencv_core.CV_32F);
			else
				blob = opencv_dnn.blobFromImages(new MatVector(mats.toArray(Mat[]::new)), scaleFactor, size, mean, swapRB, crop, opencv_core.CV_32F);
		} else {
			// TODO: Don't have any net to test this with currently...
			logger.warn("Attempting to reshape an image with " + nChannels + " channels - this may not work! "
					+ "Only 1, 3 and 4 full supported, preprocessing will be ignored.");
			// Blob is a 4D Tensor [NCHW]
			int[] shape = new int[4];
			Arrays.fill(shape, 1);
			int nRows = first.size(0);
			int nCols = first.size(1);
			shape[0] = mats.size();
			shape[1] = nChannels;
			shape[2] = nRows;
			shape[3] = nCols;
			//    		for (int s = 1; s <= Math.min(nDims, 3); s++) {
			//    			shape[s] = mat.size(s-1);
			//    		}
			blob = new Mat(shape, opencv_core.CV_32F);
			var idxBlob = blob.createIndexer();
			long[] indsBlob = new long[4];
			int n = 0;
			for (var mat : mats) {
				indsBlob[0] = n++;
				long[] indsMat = new long[4];
				var idxMat = mat.createIndexer();
				for (int r = 0; r < nRows; r++) {
					indsMat[0] = r;
					indsBlob[2] = r;
					for (int c = 0; c < nCols; c++) {
						indsMat[1] = c;
						indsBlob[3] = c;
						for (int channel = 0; channel < nChannels; channel++) {
							indsMat[2] = channel;
							indsBlob[1] = channel;
							double val = idxMat.getDouble(indsMat);
							idxBlob.putDouble(indsBlob, val);
						}    			        			
					}    			
				}
				idxMat.close();
			}
			idxBlob.close();
		}
		
		return blob;
	}
	
	/**
	 * Extract images from an OpenCV blob.
	 * @param blob
	 * @return a list of of images, with length depending upon batch size
	 */
	public static List<Mat> imagesFromBlob(Mat blob) {
		var vec = new MatVector();
		opencv_dnn.imagesFromBlob(blob, vec);
		return Arrays.asList(vec.get());
	}

}
