package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.color.model.ColorModelFactory;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractOpenCVPixelClassifier implements PixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    ColorModel colorModelProbabilities;
    IndexColorModel colorModelClassifications;
    boolean doSoftMax;
    boolean do8Bit;

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