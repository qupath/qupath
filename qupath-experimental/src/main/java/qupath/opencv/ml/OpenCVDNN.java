package qupath.opencv.ml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.StringVector;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.MatShapeVector;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for an OpenCV Net, including essential metadata about how it should be used.
 * <p>
 * The main purpose of this is to support serializing models to JSON... kind of. In truth currently the paths 
 * to the original model files are serialized, since (to my knowledge) there is no way to save and reload a Net directly.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVDNN {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVDNN.class);
	
	private String name;
	
	private String pathModel;
	private String pathConfig;
	private String framework;
	
	private String outputLayerName;
	
	private double[] means;
	private double[] scales;
	private boolean swapRB;
	private boolean crop = false;
	
	private transient Net net;
	private transient Boolean doMeanSubtraction;
	private transient Boolean doScaling;
		
	private OpenCVDNN() {}

	/**
	 * Get the actual OpenCV Net directly.
	 * @return
	 * @throws IOException 
	 */
	public Net getNet() throws IOException {
		if (net == null) {
			try {
				net = opencv_dnn.readNet(pathModel, pathConfig, framework);
			} catch (RuntimeException e) {
				throw new IOException("Unable to load moxel from " + pathModel, e);
			}
		}
		return net;
	}
	
	/**
	 * Create a (multiline) summary String for the Net, given the specified image input dimensions.
	 * @param width input width
	 * @param height input height
	 * @param nChannels input channel count
	 * @return
	 * @throws IOException if an error occurs when loading the model
	 */
	public String summarize(int width, int height, int nChannels) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(name).append("\n");
		
		MatShapeVector netInputShape = getShapeVector(width, height, nChannels, 1);
		
		StringVector types = new StringVector();
		Net net = getNet();
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
	
	/**
	 * Get a user-readable name for this model, or null if no name is specified.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the name of the requested output layer, or null if no output layer is required (that is, the last should be chosen).
	 * @return
	 */
	public String getOutputLayerName() {
		return outputLayerName;
	}
	
	/**
	 * Get scale factors to be applied for preprocessing. This can either be a single value to multiply 
	 * all channels, or a different value per input channel. The calculation is {@code (mat - means) * scale}.
	 * @return
	 * @see #getMeans()
	 */
	public double[] getScales() {
		return scales.clone();
	}
	
	/**
	 * Get means which should be subtracted for preprocessing. This can either be a single value to subtract 
	 * from all channels, or a different value per input channel. The calculation is {@code (mat - means) * scale}.
	 * @return
	 * @see #getScales()
	 */
	public double[] getMeans() {
		return means.clone();
	}
	
	/**
	 * If true, preprocessing should involve swapping red and blue channels.
	 * @return
	 */
	public boolean doSwapRB() {
		return swapRB;
	}
	
	/**
	 * If true, preprocessing should involve cropping the input to the requested size.
	 * @return
	 */
	public boolean doCrop() {
		return crop;
	}
	
	/**
	 * Get the path to the model.
	 * @return
	 */
	public String getModelPath() {
		return pathModel;
	}

	/**
	 * Get the path to the model configuration, if required.
	 * @return
	 */
	public String getConfigPath() {
		return pathConfig;
	}
	
	/**
	 * Get the framework used to create the model.
	 * @return
	 */
	public String getFramework() {
		return framework;
	}
	
	/**
	 * Returns true if mean subtraction is required as preprocessing.
	 * @return
	 */
	public boolean doMeanSubtraction() {
		if (doMeanSubtraction == null) {
			doMeanSubtraction = means != null && means.length > 0 && !Arrays.stream(means).allMatch(d -> d == 0.0);
		}
		return doMeanSubtraction;
	}
	
	/**
	 * Returns true if scaling is required as preprocessing.
	 * @return
	 */
	public boolean doScaling() {
		if (doScaling == null) {
			doScaling = scales != null && scales.length > 0 && !Arrays.stream(scales).allMatch(d -> d == 1.0);
		}
		return doScaling;
	}
	
	/**
     * Apply mean subtraction and multiplication by a scaling factor to a Mat (in-place).
     * @param mat
     * @param model
     */
    public static void preprocessMat(Mat mat, OpenCVDNN model) {
    	if (model.doMeanSubtraction()) {
	    	var means = model.getMeans();
	    	// If we have 1 value, subtract from all channels
    		if (means.length == 1) {
    			if (means[0] != 0)
    				opencv_core.subtractPut(mat, Scalar.all(means[0]));
    		} else if (means.length != mat.channels()) {
    	    	// If we have more than 1 value, but it doesn't match the channel count, throw an exception
    			throw new IllegalArgumentException("Means array of length " + means.length + " cannot be applied to image with " + mat.channels() + " channels");
    		} else if (means.length < 4) {
    	    	// If we can subtract a scalar that's easier
    			var m = new Scalar();
    			m.put(means);
    			opencv_core.subtractPut(mat, m); 
    			m.close();
    		} else {
    	    	// Subtract one channel at a time if necessary
    			Mat temp = new Mat();
    			Scalar s = new Scalar();
    			for (int i = 0; i < means.length; i++) {
    				s.put(means[i]);
    				opencv_core.extractChannel(mat, temp, i);
    				opencv_core.subtractPut(temp, s);
    			}
    			s.close();
    			temp.close();
    		}
    	}
    	
    	if (model.doScaling()) {
    		var scales = model.getScales();
	    	// If we have 1 value, scale all channels
    		if (scales.length == 1) {
    			if (scales[0] != 0)
    				opencv_core.multiplyPut(mat, scales[0]);
    		} else if (scales.length != mat.channels()) {
    	    	// If we have more than 1 value, but it doesn't match the channel count, throw an exception
    			throw new IllegalArgumentException("Scales array of length " + scales.length + " cannot be applied to image with " + mat.channels() + " channels");
    		} else {
    	    	// Scale one channel at a time if necessary
    			Mat temp = new Mat();
    			for (int i = 0; i < scales.length; i++) {
    				opencv_core.extractChannel(mat, temp, i);
    				opencv_core.multiplyPut(temp, scales[i]);
    			}
    			temp.close();
    		}
        }
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
	
	static MatShapeVector getShapeVector(int width, int height, int channels, int batchSize) {
		int[] shapeInput = new int[] {batchSize, channels, height, width};
		return new MatShapeVector(new IntPointer(shapeInput));
	}
	
	static List<DNNLayer> parseLayers(Net net, MatShapeVector netInputShape) {
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
	 * Helper class to build an {@link OpenCVDNN}.
	 */
	public static class Builder {
		
		private String name;
		private String outputLayerName;
		
		private String pathModel;
		private String pathConfig;
		private String framework;
		
		private double[] means = new double[] {0};
		private double[] scales = new double[] {1.0};
		private boolean swapRB = false;;
		private boolean crop = false;
		
		public Builder(String pathModel) {
			this.pathModel = pathModel;
			try {
				this.name = new File(pathModel).getName();
			} catch (Exception e) {
				logger.debug("Unable to set default Net name from {} ({})", pathModel, e.getLocalizedMessage());
			}
		}
				
		public Builder framework(String name) {
			this.framework = name;
			return this;
		}
		
		public Builder config(String pathConfig) {
			this.pathConfig = pathConfig;
			return this;
		}
		
		public Builder name(String name) {
			this.name = name;
			return this;
		}
		
		public Builder outputLayerName(String outputLayerName) {
			this.outputLayerName = outputLayerName;
			return this;
		}
		
		public Builder swapRB() {
			this.swapRB = true;
			return this;
		}
		
		public Builder means(double...means) {
			this.means = means;
			return this;
		}
		
		public Builder scales(double... scales) {
			this.scales = scales;
			return this;
		}
		
		public Builder crop() {
			this.crop = true;
			return this;
		}
		
		public OpenCVDNN build() {
			OpenCVDNN dnn = new OpenCVDNN();
			dnn.pathModel = pathModel;
			dnn.pathConfig = pathConfig;
			dnn.framework = framework;
			dnn.name = name;
			dnn.outputLayerName = outputLayerName;
			
			dnn.means = means.clone();
			dnn.scales = scales.clone();
			dnn.swapRB = swapRB;
			dnn.crop = crop;
			return dnn;
		}
		
	}

}
