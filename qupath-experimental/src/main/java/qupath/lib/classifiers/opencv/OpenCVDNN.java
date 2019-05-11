package qupath.lib.classifiers.opencv;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.opencv.opencv_core.StringVector;
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
	private double scale;
	private boolean swapRB;
	private boolean crop = false;
	
	private transient Net net;
	
	private OpenCVDNN() {}

	public Net getNet() {
		if (net == null) {
			net = opencv_dnn.readNet(pathModel, pathConfig, framework);
		}
		return net;
	}
	
	public String summarize(int width, int height, int nChannels) {
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
	
	public String getName() {
		return name;
	}
	
	public String getOutputLayerName() {
		return outputLayerName;
	}
	
	public double getScale() {
		return scale;
	}
	
	public double[] getMeans() {
		return means.clone();
	}
	
	public boolean doSwapRB() {
		return swapRB;
	}
	
	public boolean doCrop() {
		return crop;
	}
	
	public String getModelPath() {
		return pathModel;
	}

	public String getConfigPath() {
		return pathConfig;
	}
	
	public String getFramework() {
		return framework;
	}
	
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

	
	public static List<String> parseStrings(StringVector vector) {
		List<String> list = new ArrayList<>();
		int n = (int)vector.size();
		for (int i = 0; i < n; i++)
			list.add(vector.get(i).getString());
		return list;
	}
	
	public static int[] parseShape(MatShapeVector vector) {
		IntPointer pointer = vector.get(0L);
		int[] shape = new int[(int)pointer.limit()];
		for (int i = 0; i < shape.length; i++)
			shape[i] = pointer.get(i);
		return shape;
	}
	
	
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
		
		public String getName() {
			return name;
		}
		
		public int getID() {
			return id;
		}
		
		public int[] getInputShapes() {
			return inputShapes.clone();
		}
		
		public int[] getOutputShapes() {
			return outputShapes.clone();
		}
		
		@Override
		public String toString() {
			return String.format("%s \t%s -> %s" ,
					name, Arrays.toString(inputShapes), Arrays.toString(outputShapes));
		}
		
	}
	
	
	public static class Builder {
		
		private String name;
		private String outputLayerName;
		
		private String pathModel;
		private String pathConfig;
		private String framework;
		
		private double[] means = new double[] {0};
		private double scale = 1.0;
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
		
		public Builder scale(double scale) {
			this.scale = scale;
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
			
			dnn.means = means;
			dnn.scale = scale;
			dnn.swapRB = swapRB;
			dnn.crop = crop;
			return dnn;
		}
		
	}

}
