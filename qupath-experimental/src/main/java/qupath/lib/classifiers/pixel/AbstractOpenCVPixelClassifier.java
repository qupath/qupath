package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.color.model.ColorModelFactory;
import qupath.opencv.processing.OpenCVTools;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractOpenCVPixelClassifier implements PixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private ColorModel colorModelProbabilities;
    private IndexColorModel colorModelClassifications;
    private boolean doSoftMax;
    private boolean do8Bit;

    private PixelClassifierMetadata metadata;
    
    AbstractOpenCVPixelClassifier(PixelClassifierMetadata metadata, boolean do8Bit) {
        this.doSoftMax = metadata.getOutputType() == PixelClassifierMetadata.OutputType.Logit;
        this.metadata = metadata;
        this.do8Bit = do8Bit;
        int[] colors = metadata.getChannels().stream().mapToInt(c -> c.getColor()).toArray();
        int bpp = do8Bit ? 8 : 32;
        // TODO: Check residualBackground!
        this.colorModelProbabilities = ColorModelFactory.createProbabilityColorModel(bpp, metadata.nOutputChannels(), false, colors);
        int[] cmap = metadata.getChannels().stream().mapToInt(c -> c.getColor()).toArray();
        if (cmap.length > 256)
        	throw new IllegalArgumentException("Only 256 possible classifications supported!");
        this.colorModelClassifications = new IndexColorModel(8, metadata.nOutputChannels(), cmap, 0, true, -1, DataBuffer.TYPE_BYTE);
    }

    public PixelClassifierMetadata getMetadata() {
        return metadata;
    }

    @Override
    public BufferedImage applyClassification(BufferedImage img, int pad) {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
        Mat matInput = OpenCVTools.imageToMat(img);

        // Do the classification, optionally with softmax
        Mat matResult = doClassification(matInput, pad);
        
        // If we have a floating point or multi-channel result, we have probabilities
        ColorModel colorModelLocal;
        if (matResult.channels() > 1) {
        	// Do softmax if needed
            if (doSoftMax)
                applySoftmax(matResult);

            // Convert to 8-bit if needed
            if (do8Bit)
                matResult.convertTo(matResult, opencv_core.CV_8U, 255.0, 0.0);        	
            colorModelLocal = colorModelProbabilities;
        } else {
            matResult.convertTo(matResult, opencv_core.CV_8U);
            colorModelLocal = colorModelClassifications;
        }

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrices
        if (matInput != null)
            matInput.release();
        if (matResult != null && matResult != matInput)
            matResult.release();

        return imgResult;
    }


    protected abstract Mat doClassification(Mat mat, int padding);


    void applySoftmax(Mat mat) {
    	MatVector matvec = new MatVector();
        opencv_core.split(mat, matvec);
        applySoftmax(matvec);
        opencv_core.merge(matvec, mat);
    }


    void applySoftmax(MatVector matvec) {
        Mat matSum = null;
        for (int i = 0; i < matvec.size(); i++) {
        	Mat mat = matvec.get(i);
            opencv_core.exp(mat, mat);
            if (matSum == null)
                matSum = mat.clone();
            else
                opencv_core.addPut(matSum, mat);
        }
        for (int i = 0; i < matvec.size(); i++) {
        	Mat mat = matvec.get(i);
            opencv_core.dividePut(mat, matSum);
        }
        if (matSum != null)
        	matSum.release();
    }

    /**
     * Create a Scalar from between 1 and 4 double values.
     *
     * @param values
     * @return
     */
    opencv_core.Scalar toScalar(double... values) {
        if (values.length == 1)
            return opencv_core.Scalar.all(values[0]);
        else if (values.length == 2)
            return new opencv_core.Scalar(values[0], values[1]);
        else if (values.length == 3)
            return new opencv_core.Scalar(values[0], values[1], values[2], 0);
        else if (values.length == 4)
            return new opencv_core.Scalar(values[0], values[1], values[2], values[3]);
        throw new IllegalArgumentException("Invalid number of entries - need between 1 & 4 entries to create an OpenCV scalar, not " + values.length);
    }


    public List<String> getChannelNames() {
        return metadata.getChannels().stream().map(c -> c.getName()).collect(Collectors.toList());
    }

    public double getRequestedPixelSizeMicrons() {
        return metadata.getInputPixelSizeMicrons();
    }

}