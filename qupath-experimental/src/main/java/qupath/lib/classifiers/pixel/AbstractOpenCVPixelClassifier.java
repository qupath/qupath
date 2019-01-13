package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.gui.ClassificationColorModelFactory;

import java.awt.image.ColorModel;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractOpenCVPixelClassifier implements PixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(AbstractOpenCVPixelClassifier.class);

    private transient ColorModel colorModelProbabilities;
    private transient ColorModel colorModelClassifications;
    
    private boolean doSoftMax;
    private boolean do8Bit;

    private PixelClassifierMetadata metadata;
    
    AbstractOpenCVPixelClassifier(PixelClassifierMetadata metadata, boolean do8Bit) {
        this.doSoftMax = metadata.getOutputType() == PixelClassifierMetadata.OutputType.Logit;
        this.metadata = metadata;
        this.do8Bit = do8Bit;
    }
    
    boolean do8Bit() {
    	return this.do8Bit;
    }
    
    boolean doSoftMax() {
    	return this.doSoftMax;
    }
    
    protected synchronized ColorModel getClassificationsColorModel() {
    	if (colorModelClassifications == null) {
            colorModelClassifications = ClassificationColorModelFactory.geClassificationColorModel(metadata.getChannels());
    	}
    	return colorModelClassifications;
    }
    
    
    protected synchronized ColorModel getProbabilityColorModel() {
    	if (colorModelProbabilities == null) {
    		if (do8Bit())
    			colorModelProbabilities = ClassificationColorModelFactory.geProbabilityColorModel8Bit(metadata.getChannels());
    		else
    			colorModelProbabilities = ClassificationColorModelFactory.geProbabilityColorModel32Bit(metadata.getChannels());
    	}
    	return colorModelProbabilities;
    }
    

    public PixelClassifierMetadata getMetadata() {
        return metadata;
    }



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