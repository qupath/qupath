package legacy;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.Net;

import qupath.lib.images.servers.ImageServer;
import qupath.opencv.tools.OpenCVTools;

@Deprecated
class PixelFeatureExtractorDNN extends PixelFeatureExtractor {
	
	private List<String> measurements = new ArrayList<>();
	
	private Net net;
	private String outputName;
	private int nFeatures;
	
	PixelFeatureExtractorDNN(final ImageServer<BufferedImage> server, final Net net, String outputLayer, final int width, final int height, final double requestedPixelSizeMicrons) {
		super(server, width, height, requestedPixelSizeMicrons);
		
		// Store net and output layer name
		this.net = net;
		this.outputName = outputLayer;
		
		// Determine the number of features that will be computed here
		var mat = new Mat(width, height, opencv_core.CV_32FC(server.nChannels()));
		var blob = opencv_dnn.blobFromImage(mat);
		net.setInput(blob);
		var output = net.forward(outputLayer);
		long nElements = 1;
		for (var l : output.createIndexer().sizes())
			nElements *= l;
		this.nFeatures = (int)nElements;
		
		// Initialize feature names
		this.measurements.clear();
		for (int i = 1; i <= nFeatures; i++)
			this.measurements.add("Feature " + i);
	}
	
	@Override
	public List<String> getFeatureNames() {
		return Collections.unmodifiableList(measurements);
	}
	
	@Override
	public int nFeatures() {
		return nFeatures;
	}
	
	@Override
	protected void extractFeatures(final List<BufferedImage> images, final FloatBuffer buffer) {
		try (PointerScope scope = new PointerScope()) {
			
			var mats = images.stream().map(img -> OpenCVTools.imageToMatRGB(img, false)).toArray(i -> new Mat[i]);
			var matvec = new MatVector(mats);
			
			var size = mats[0].size();
			
			// TODO: Must request normalization values elsewhere
			var blob = opencv_dnn.blobFromImages(matvec, 1.0/255.0, size, Scalar.all(0.5), false, false, opencv_core.CV_32F);			
			net.setInput(blob);
			var output = net.forward(outputName);
			
			var indexer = output.createIndexer();
			
			var sizes = indexer.sizes();
			var inds = new long[4];
			for (int i = 0; i < sizes[0]; i++) {
				inds[0] = i;
				for (int j = 0; j < sizes[1]; j++) {
					inds[1] = j;
					for (int k = 0; k < sizes[2]; k++) {
						inds[2] = k;
						for (int l = 0; l < sizes[3]; l++) {
							inds[3] = l;
							double val = indexer.getDouble(inds);
							buffer.put((float)val);
						}
					}
				}
			}	
		}
	}
	
}