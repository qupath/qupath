package qupath.lib.classifiers.pixel.features;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgproc;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;

public class BasicMultiscaleOpenCVFeatureCalculator implements OpenCVFeatureCalculator {

    private double[] sigmaValues;
    private int padding = 0;

    private boolean includeEdgesFeatures;
    
    private int DEFAULT_WIDTH = 512;
    private int DEFAULT_HEIGHT = DEFAULT_WIDTH;
    
    private int nChannels;
    private PixelClassifierMetadata metadata;
    
    public BasicMultiscaleOpenCVFeatureCalculator(int nChannels, boolean includeEdgeFeatures) {
    	this(nChannels, 1, 1, includeEdgeFeatures);
    }

    public BasicMultiscaleOpenCVFeatureCalculator(int nChannels, double sigmaStart, int nScales, boolean includeEdgeFeatures) {
    	this.nChannels = nChannels;
        sigmaValues = new double[nScales];
        for (int i = 0; i < nScales; i++) {
        	sigmaValues[i] = sigmaStart;
            sigmaStart *= 2;
        }
        padding = (int)Math.ceil(sigmaValues[sigmaValues.length-1] * 3);
        this.includeEdgesFeatures = includeEdgeFeatures;
        
        List<PixelClassifierOutputChannel> channels = new ArrayList<>();
        // Compute smoothing
        int color = ColorTools.makeRGB(255, 255, 255);
        for (double sigma : sigmaValues) {
        	// Apply Gaussian filter
            for (int c = 1; c <= nChannels; c++)
            	channels.add(
            			new PixelClassifierOutputChannel(String.format("Channel %d: Gaussian sigma = %.2f", c, sigma), 
            					color));

            // Apply Laplacian filter
            for (int c = 1; c <= nChannels; c++)
            	channels.add(
            			new PixelClassifierOutputChannel(String.format("Channel %d: Laplacian sigma = %.2f", c, sigma), 
            					color));

            // Calculate local standard deviation
            int window = (int)Math.round(sigma)*2+1;
            for (int c = 1; c <= nChannels; c++)
            	channels.add(
            			new PixelClassifierOutputChannel(String.format("Channel %d: Std dev window = %d", c, window), 
            					color));

            // Apply Sobel filter, if required
            if (includeEdgesFeatures) {
                for (int c = 1; c <= nChannels; c++)
                	channels.add(
                			new PixelClassifierOutputChannel(String.format("Channel %d: Gradient mag sigma = %.2f", c, sigma), 
                					color));
            }
        }
        
        this.metadata = new PixelClassifierMetadata.Builder()
        		.inputShape(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        		.channels(channels)
        		.build();
    }

    public opencv_core.Mat calculateFeatures(opencv_core.Mat input) {
        opencv_core.Mat matDest = new Mat();
        input.convertTo(matDest, opencv_core.CV_32F);

        if (nChannels != input.channels()) {
        	throw new IllegalArgumentException("Required " + nChannels + " input channels for feature calculations, but received " + input.channels());
        }

        // Compute smoothing
        List<Mat> mats = new ArrayList<>();
        for (double sigma : sigmaValues) {
        	// Apply Gaussian filter
            int s = (int)Math.ceil(sigma * 3) * 2 + 1;
            Size size = new Size(s, s);
            Mat matTemp = new Mat();
            opencv_imgproc.GaussianBlur(matDest, matTemp, size, sigma);
            mats.add(matTemp);

            // Apply Laplacian filter
            Mat matTemp2 = new Mat();
            opencv_imgproc.Laplacian(matTemp, matTemp2, -1);
            mats.add(matTemp2);

            // Calculate local standard deviation
            int window = (int)Math.round(sigma)*2+1;
            Size sizeStdDev = new Size(window, window);
            Mat matStdDev = localStandardDeviation(matDest, sizeStdDev);
            mats.add(matStdDev);

//            // Must be 8-bit for median filter...?
//            Mat matTemp3 = new Mat();
//            int window = (int)Math.round(sigma)*2+1;
//            opencv_imgproc.medianBlur(matDest, matTemp3, window);
//            mats.add(matTemp3);
//            for (int c = 1; c <= nChannels; c++)
//                featureNames.add(String.format("Channel %d: Median window = %d", c, window));

//            def matTemp3 = new opencv_core.Mat()
//            opencv_imgproc.cvtColor(matTemp, matTemp3, opencv_imgproc.COLOR_RGB2HSV)
//            mats << matTemp3

            // Apply Sobel filter, if required
            if (includeEdgesFeatures) {
            	Mat matDx = new Mat();
                Mat matDy = new Mat();
                opencv_imgproc.Sobel(matTemp, matDx, -1, 1, 0);
                opencv_imgproc.Sobel(matTemp, matDy, -1, 0, 1);
                opencv_core.magnitude(matDx, matDy, matDx);
                mats.add(matDx);
            }

        }
        opencv_core.merge(new opencv_core.MatVector(mats.toArray(new Mat[0])), matDest);

        return matDest;
    }
    
    /**
     * Compute local standard deviation
     * 
     * @param mat
     * @param size
     * @return
     */
    private static Mat localStandardDeviation(final Mat mat, final Size size) {
    	// Create a normalized kernel
    	Mat strel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, size);
    	strel.convertTo(strel, opencv_core.CV_64F);
    	Scalar sum = opencv_core.sumElems(strel);
    	opencv_core.multiplyPut(strel, 1.0/sum.get());
    	sum = opencv_core.sumElems(strel);
    	
    	Mat matESquared = new Mat();
    	mat.convertTo(matESquared, opencv_core.CV_64F);
    	opencv_imgproc.filter2D(matESquared, matESquared, opencv_core.CV_64F, strel);
    	opencv_core.multiply(matESquared, matESquared, matESquared);
    	
    	Mat matSquaredE = mat.clone();
    	matSquaredE.convertTo(matSquaredE, opencv_core.CV_64F);
    	opencv_core.multiply(matSquaredE, matSquaredE, matSquaredE);
    	opencv_imgproc.filter2D(matSquaredE, matSquaredE, opencv_core.CV_64F, strel);
    	
    	opencv_core.subtract(matSquaredE, matESquared, matESquared);
    	opencv_core.sqrt(matESquared, matESquared);
    	
    	matSquaredE.release();
    	matESquared.convertTo(matESquared, mat.depth());
    	return matESquared;
    	
//    	Mat matESquared = new Mat();
//    	opencv_imgproc.blur(mat, matESquared, size);
//    	opencv_core.multiply(matESquared, matESquared, matESquared);
//    	
//    	Mat matSquaredE = new Mat();
//    	opencv_core.multiply(mat, mat, matSquaredE);
//    	opencv_imgproc.blur(matSquaredE, matSquaredE, size);
//    	
//    	opencv_core.subtract(matESquared, matSquaredE, matESquared);
//    	
//    	matSquaredE.release();
//    	return matESquared;
    }
    
    @Override
    public String toString() {
        String edgesOrNot = includeEdgesFeatures ? " with edges" : "";
        if (sigmaValues.length == 1)
            return String.format("Basic texture features%s (sigma = %.2f)", edgesOrNot, sigmaValues[0]);
        return String.format("Basic multiscale texture features%s (sigma = %.2f-%.2f)", edgesOrNot, sigmaValues[0], sigmaValues[sigmaValues.length-1]);
    }

	@Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) {
		BufferedImage img = getPaddedRequest(server, request, padding);
		Mat mat = OpenCVTools.imageToMat(img);
		Mat matFeatures = calculateFeatures(mat);
		if (padding > 0)
			matFeatures.put(matFeatures.apply(new opencv_core.Rect(padding, padding, mat.cols()-padding*2, mat.rows()-padding*2)).clone());
		mat.release();
		return matFeatures;
	}
	
	/**
	 * Get a raster, padded by the specified amount, to the left, right, above and below.
	 * <p>
	 * Note that the padding is defined in terms of the <i>destination</i> pixels.
	 * <p>
	 * In other words, a specified padding of 5 should actually result in 20 pixels being added in each dimension 
	 * if the {@code request.getDownsample() == 4}.
	 * <p>
	 * Currently, zero-padding is used.
	 * 
	 * @param server
	 * @param request
	 * @param padding
	 * @return
	 */
	public static BufferedImage getPaddedRequest(ImageServer<BufferedImage> server, RegionRequest request, int padding) {
		// If we don't have any padding, just return directly
		if (padding == 0)
			return server.readBufferedImage(request);
		// If padding < 0, throw an exception
		if (padding < 0)
			new IllegalArgumentException("Padding must be >= 0, but here it is " + padding);
		// Get the expected bounds
		double downsample = request.getDownsample();
		int x = (int)(request.getX() - padding * downsample);
		int y = (int)(request.getY() - padding * downsample);
		int x2 = (int)((request.getX() + request.getWidth()) + padding * downsample);
		int y2 = (int)((request.getY() + request.getHeight()) + padding * downsample);
		// If we're out of range, we'll need to work a bit harder
		int padLeft = 0, padRight = 0, padUp = 0, padDown = 0;
		boolean outOfRange = false;
		if (x < 0) {
			padLeft = (int)Math.round(-x/downsample);
			x = 0;
			outOfRange = true;
		}
		if (y < 0) {
			padUp = (int)Math.round(-y/downsample);
			y = 0;
			outOfRange = true;
		}
		if (x2 > server.getWidth()) {
			padRight  = (int)Math.round((x2 - server.getWidth() - 1)/downsample);
			x2 = server.getWidth();
			outOfRange = true;
		}
		if (y2 > server.getHeight()) {
			padDown  = (int)Math.round((y2 - server.getHeight() - 1)/downsample);
			y2 = server.getHeight();
			outOfRange = true;
		}
		// If everything is within range, this should be relatively straightforward
		RegionRequest request2 = RegionRequest.createInstance(request.getPath(), downsample, x, y, x2-x, y2-y);
		BufferedImage img = server.readBufferedImage(request2);
		if (outOfRange) {
			WritableRaster raster = img.getRaster();
			WritableRaster rasterPadded = raster.createCompatibleWritableRaster(
					raster.getWidth() + padLeft + padRight,
					raster.getHeight() + padUp + padDown);
			rasterPadded.setRect(padLeft, padUp, raster);
			// Add padding above
			if (padUp > 0) {
				WritableRaster row = raster.createWritableChild(0, 0, raster.getWidth(), 1, 0, 0, null);
				for (int r = 0; r < padUp; r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding below
			if (padDown > 0) {
				WritableRaster row = raster.createWritableChild(0, raster.getHeight()-1, raster.getWidth(), 1, 0, 0, null);
				for (int r = padUp + raster.getHeight(); r < rasterPadded.getHeight(); r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding to the left
			if (padLeft > 0) {
				WritableRaster col = rasterPadded.createWritableChild(padLeft, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = 0; c < padLeft; c++)
					rasterPadded.setRect(c, 0, col);
			}
			// Add padding to the right
			if (padRight > 0) {
				WritableRaster col = rasterPadded.createWritableChild(rasterPadded.getWidth()-padRight-1, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = padLeft + raster.getWidth(); c < rasterPadded.getWidth(); c++)
					rasterPadded.setRect(c, 0, col);
			}
			// TODO: The padding seems to work - but something to be cautious with...
//			System.err.println(String.format("Applied padding (%d, %d, %d, %d) - check this!", padUp, padDown, padLeft, padRight));
			img = new BufferedImage(img.getColorModel(), rasterPadded, img.isAlphaPremultiplied(), null);
		}
		return img;
	}
	

	@Override
	public PixelClassifierMetadata getMetadata() {
		return metadata;
	}


}