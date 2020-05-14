package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.tools.OpenCVTools;

import org.bytedeco.opencv.global.opencv_core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenCVPixelClassifier implements PixelClassifier {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private transient ColorModel colorModelProbabilities;
    private transient ColorModel colorModelClassifications;
    
    private PixelClassifierMetadata metadata;
    private ImageDataOp op;
    
    private OpenCVPixelClassifier() {}

    OpenCVPixelClassifier(ImageDataOp op, PixelClassifierMetadata metadata) {
    	this();
        this.op = op;
        this.metadata = metadata;
        logger.debug("Creating OpenCV pixel classifier");
    }
    
    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
    	return op.supportsImage(imageData);
    }
    
    protected synchronized ColorModel getClassificationsColorModel() {
    	if (colorModelClassifications == null) {
            colorModelClassifications = ColorModelFactory.getIndexedClassificationColorModel(metadata.getClassificationLabels());
    	}
    	return colorModelClassifications;
    }
    
    protected synchronized ColorModel getProbabilityColorModel(boolean do8Bit) {
    	if (colorModelProbabilities == null) {
    		if (do8Bit)
    			colorModelProbabilities = ColorModelFactory.getProbabilityColorModel8Bit(metadata.getOutputChannels());
    		else
    			colorModelProbabilities = ColorModelFactory.getProbabilityColorModel32Bit(metadata.getOutputChannels());
    	}
    	return colorModelProbabilities;
    }
    
    protected ImageDataOp getOp() {
    	return op;
    }
    
    @Override
	public PixelClassifierMetadata getMetadata() {
        return metadata;
    }

    
    @Override
    public BufferedImage applyClassification(final ImageData<BufferedImage> imageData, final RegionRequest request) throws IOException {
    	
    	var matResult = getOp().apply(imageData, request);
    	
    	var type = getMetadata().getOutputType();
    	ColorModel colorModelLocal = null;
    	if (type == ImageServerMetadata.ChannelType.PROBABILITY) {
    		colorModelLocal = getProbabilityColorModel(matResult.depth() == opencv_core.CV_8U);
    	} else if (type == ImageServerMetadata.ChannelType.CLASSIFICATION) {
    		colorModelLocal = getClassificationsColorModel();
    	}

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrix
        matResult.release();

        return imgResult;
    }
    
    
}