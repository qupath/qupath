/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.opencv.ml;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point2fVector;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.StringVector;
import org.bytedeco.opencv.opencv_dnn.ClassificationModel;
import org.bytedeco.opencv.opencv_dnn.DetectionModel;
import org.bytedeco.opencv.opencv_dnn.IntFloatPair;
import org.bytedeco.opencv.opencv_dnn.KeypointsModel;
import org.bytedeco.opencv.opencv_dnn.MatShapeVector;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.bytedeco.opencv.opencv_dnn.SegmentationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

/**
 * Tools for working with OpenCV's DNN module.
 * 
 * @author Pete Bankhead
 */
public class DnnTools {
	
	private final static Logger logger = LoggerFactory.getLogger(DnnTools.class);
	
	
	
	/**
	 * Get the names of all unconnected output layers.
	 * @param net 
	 * @return
	 * @throws IOException 
	 */
	public static List<String> getOutputLayerNames(Net net) {
		var names = new ArrayList<String>();
		for (var bp : net.getUnconnectedOutLayersNames().get()) {
			names.add(bp.getString());
		}
		return names;
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
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i).getString();
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
	public static int[] parseShape(MatShapeVector vector) {
		IntPointer pointer = vector.get(0L);
		int[] shape = new int[(int)pointer.limit()];
		for (int i = 0; i < shape.length; i++)
			shape[i] = pointer.get(i);
		return shape;
	}
	
	
	/**
	 * Helper class to summarize a DNN layer.
	 */
	public static class DNNLayer {
		
		private String name;
		private int id;
		private int[] inputShapes;
		private int[] outputShapes;
		
		private DNNLayer(String name, int id, int[] inputShapes, int[] outputShapes) {
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
		public int[] getInputShapes() {
			return inputShapes.clone();
		}
		
		/**
		 * Layer output shape. This may depend on the input shape provided when summarizing the model
		 * @return
		 */
		public int[] getOutputShapes() {
			return outputShapes.clone();
		}
		
		@Override
		public String toString() {
			return String.format("%s \t%s -> %s" ,
					name, Arrays.toString(inputShapes), Arrays.toString(outputShapes));
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
		var img = server.readBufferedImage(request);
		return OpenCVTools.imageToMat(img);
	}
	
	
	private static double calculateFirstPadProportion(double v1, double v2, double minVal, double maxVal) {
		// No left padding
		if (v1 >= minVal)
			return 0;
		// No right padding
		if (v2 <= maxVal)
			return 1;
		// Combination of left and right padding
		return (minVal - v1) / (v2 - maxVal);
	}
	
	
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
	
	
	public static boolean classify(ClassificationModel model, PathObject pathObject, ImageServer<BufferedImage> server, double downsample,  int width, int height, IntFunction<PathClass> classifier, String predictionMeasurement) throws IOException {
		var roi = pathObject.getROI();
		if (roi == null) {
			logger.warn("Cannot classify an object without a ROI!");
			return false;
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Width and height must be > 0");
		}
		double scaledWidth = width * downsample;
		double scaledHeight = height * downsample;
		int xi = (int)Math.floor(roi.getBoundsX() - scaledWidth/2.0);
		int yi = (int)Math.floor(roi.getBoundsY() - scaledHeight/2.0);
		int xi2 = (int)Math.ceil(roi.getBoundsX() + scaledWidth/2.0);
		int yi2 = (int)Math.ceil(roi.getBoundsY() + scaledWidth/2.0);
		
		int x = GeneralTools.clipValue(xi, 0, server.getWidth());
		int x2 = GeneralTools.clipValue(xi2, 0, server.getWidth());
		int y = GeneralTools.clipValue(yi, 0, server.getHeight());
		int y2 = GeneralTools.clipValue(yi2, 0, server.getHeight());
		
		var request = RegionRequest.createInstance(server.getPath(), downsample, x, y, x2-x, y2-y, roi.getZ(), roi.getT());
		
		// Ensure image is the correct size
		var input = readMat(server, request);
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
			if (height < matHeight || width < matWidth) {
				// Calculate relative amount of padding for left and top
				double xProp = calculateFirstPadProportion(x, x2, 0, server.getWidth());
				double yProp = calculateFirstPadProportion(y, y2, 0, server.getHeight());
				int padX = (int)Math.round((matWidth - width) * xProp);
				int padY = (int)Math.round((matHeight - height) * yProp);
				opencv_core.copyMakeBorder(input, input,
						padX,
						padY,
						width - matWidth - padX,
						height - matHeight - padY,
						opencv_core.BORDER_REPLICATE);
			}
			OpenCVTools.ensureContinuous(input, true);
		}
		
		return classify(model, pathObject, input, classifier, predictionMeasurement);
	}
	
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
			pathObject.getMeasurementList().putMeasurement(predictionMeasurement, result.second());
			pathObject.getMeasurementList().close();
		}
		result.close();
		result.deallocate();
		return changed;
	}
	

	public static Mat segment(SegmentationModel model, ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		var output = new Mat();
		try (var scope = new PointerScope()) {
			var input = readMat(server, request);
			segment(model, input, output);
			return output;
		}
	}

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
	

	@SuppressWarnings("unchecked")
	public static List<PathObject> detect(DetectionModel model, ImageServer<BufferedImage> server, RegionRequest request, IntFunction<PathClass> classifier, Function<ROI, PathObject> creator) throws IOException {
		try (var scope = new PointerScope()) {
			var mat = readMat(server, request);
			return detect(model, mat, request, classifier, creator);
		}
	}

	
	@SuppressWarnings("unchecked")
	public static List<PathObject> detect(DetectionModel model, Mat mat, RegionRequest request, IntFunction<PathClass> classifier, Function<ROI, PathObject> creator) {
		
		try (var scope = new PointerScope()) {
			var ids = new IntPointer();
			var preds = new FloatPointer();
			var rects = new RectVector();
			
			synchronized (model) {
				model.detect(mat, ids, preds, rects);
			}
			
			long n = rects.size();
			double downsample = request.getDownsample();
			List<PathObject> pathObjects = new ArrayList<>();
			for (long i = 0; i < n; i++) {
				var rect = rects.get(i);
				var roi = ROIs.createRectangleROI(
						request.getX() + rect.x() * downsample,
						request.getY() + rect.y() * downsample,
						rect.width() * downsample,
						rect.height() * downsample,
						request.getPlane());
				
				var pathClass = classifier == null ? null : classifier.apply(ids.get(i));
				double pred = preds.get(i);
				var pathObject = creator.apply(roi);
				pathObject.setPathClass(pathClass);
				try (var ml = pathObject.getMeasurementList()) {
					ml.putMeasurement("Probability", pred);
				}
				pathObjects.add(pathObject);
			}
			
			scope.deallocate();
			return pathObjects;
		}
		
	}
	
	

	public static PathObject detectKeypoints(KeypointsModel model, Mat mat, RegionRequest request, ROI mask, double threshold, Function<ROI, PathObject> creator) {
		var roi = detectKeypointsROI(model, mat, request, mask, threshold);
		return creator.apply(roi);
	}
	
	public static ROI detectKeypointsROI(KeypointsModel model, Mat mat, RegionRequest request, ROI mask, double threshold) {
		
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
		return ROIs.createPointsROI(points, request.getPlane());
	}

}
