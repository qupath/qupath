package qupath.lib.classifiers.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;

import org.bytedeco.javacpp.opencv_imgproc;

/**
 * Just apply a Gaussian filter (if even that) to image pixels.
 */
public class SmoothedOpenCVFeatureCalculator implements OpenCVFeatureCalculator {

	private PixelClassifierMetadata metadata;
	private int DEFAULT_WIDTH = 512;
    private int DEFAULT_HEIGHT = DEFAULT_WIDTH;
    
    private int nChannels;
	
    private Size size;
    private double sigma = 0;
    private int padding = 0;

    
    public SmoothedOpenCVFeatureCalculator(final int nChannels, final double sigma) {
    	this.nChannels = nChannels;
        this.sigma = sigma;
        if (sigma > 0) {
            int s = (int)Math.ceil(sigma * 4) * 2 + 1;
            size = new Size(s, s);
            padding = (int)Math.ceil(s * 3);
        }
        
        List<PixelClassifierOutputChannel> channels = new ArrayList<>();
        int color = ColorTools.makeRGB(255, 255, 255);
        for (int c = 1; c <= nChannels; c++) {
            if (sigma > 0)
            	channels.add(new PixelClassifierOutputChannel(String.format("Channel %d: Gaussian sigma = %.2f", c, sigma), color));
            else
            	channels.add(new PixelClassifierOutputChannel(String.format("Channel %d", c), color));
        }
        
        this.metadata = new PixelClassifierMetadata.Builder()
        		.inputShape(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        		.channels(channels)
        		.setOutputType(OutputType.Features)
        		.build();
    }

    public Mat calculateFeatures(Mat input) {
    	if (nChannels != input.channels()) {
        	throw new IllegalArgumentException("Required " + nChannels + " input channels for feature calculations, but received " + input.channels());
        }
    	
        Mat matOutput = new Mat();
        input.convertTo(matOutput, opencv_core.CV_32F);
        if (sigma > 0) {
            opencv_imgproc.GaussianBlur(matOutput, matOutput, size, sigma);
        }
        return matOutput;
    }
    
    
    @Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		BufferedImage img = BasicMultiscaleOpenCVFeatureCalculator.getPaddedRequest(server, request, padding);
		Mat mat = OpenCVTools.imageToMat(img);
		Mat matFeatures = calculateFeatures(mat);
		if (padding > 0)
			matFeatures.put(matFeatures.apply(new opencv_core.Rect(padding, padding, mat.cols()-padding*2, mat.rows()-padding*2)).clone());
		mat.release();
		return matFeatures;
	}
    

    @Override
    public String toString() {
        if (sigma == 0)
            return "Original pixel values";
        return String.format("Smoothed original pixel values (sigma = %.2f)", sigma);
    }

	@Override
	public PixelClassifierMetadata getMetadata() {
		return metadata;
	}


}