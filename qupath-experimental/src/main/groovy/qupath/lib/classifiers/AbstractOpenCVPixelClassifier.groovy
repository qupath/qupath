package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_core.Scalar
import org.bytedeco.javacpp.opencv_dnn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.opencv.processing.OpenCVTools
import qupath.opencv.processing.ProbabilityColorModel

import java.awt.image.BufferedImage
import java.awt.image.ColorModel

abstract class AbstractOpenCVPixelClassifier implements PixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class)

    private ColorModel colorModel
    private boolean doSoftMax
    private boolean do8Bit

    private PixelClassifierMetadata metadata

    AbstractOpenCVPixelClassifier(PixelClassifierMetadata metadata, boolean do8Bit=false) {
        this.doSoftMax = metadata.outputType == PixelClassifierMetadata.OutputType.Logit
        this.metadata = metadata
        this.do8Bit = do8Bit
        int[] colors = metadata.channels.collect {it.getColor()} as int[]
        int bpp = do8Bit ? 8 : 32
        // TODO: Check residualBackground!
        this.colorModel = new ProbabilityColorModel(bpp, metadata.nOutputChannels(), false, colors)
    }

    public PixelClassifierMetadata getMetadata() {
        return metadata
    }

    @Override
    BufferedImage applyClassification(BufferedImage img, int pad) {
        // Get the pixels into a friendly format
        opencv_core.Mat matInput = OpenCVTools.imageToMatRGB(img, false);

        // Do the classification, optimally with softmax
        def matResult = doClassification(matInput, pad)
        if (doSoftMax)
            applySoftmax(matResult)

        // Convert to 8-bit if needed
        if (do8Bit)
            matResult.convertTo(matResult, opencv_core.CV_8U, 255.0, 0.0)

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModel);

        // Free matrices
        if (matInput != null)
            matInput.release()
        if (matResult != null && matResult != matInput)
            matResult.release()

        return imgResult;
    }


    protected abstract Mat doClassification(Mat mat, int padding=0);


    def void applySoftmax(Mat mat) {
        def matvec = new opencv_core.MatVector()
        opencv_core.split(mat, matvec)
        applySoftmax(matvec)
        opencv_core.merge(matvec, mat)
    }


    def void applySoftmax(opencv_core.MatVector matvec) {
        def matSum = null
        for (int i = 0; i < matvec.size(); i++) {
            def mat = matvec.get(i)
            opencv_core.exp(mat, mat)
            if (matSum == null)
                matSum = mat.clone()
            else
                opencv_core.addPut(matSum, mat)
        }
        for (int i = 0; i < matvec.size(); i++) {
            def mat = matvec.get(i)
            opencv_core.dividePut(mat, matSum)
        }
    }

    /**
     * Create a Scalar from between 1 and 4 double values.
     *
     * @param values
     * @return
     */
    opencv_core.Scalar toScalar(double... values) {
        if (values.size() == 1)
            return opencv_core.Scalar.all(values[0])
        else if (values.size() == 2)
            return new opencv_core.Scalar(values[0], values[1])
        else if (values.size() == 3)
            return new opencv_core.Scalar(values[0], values[1], values[2])
        else if (values.size() == 4)
            return new opencv_core.Scalar(values[0], values[1], values[2], values[3])
        throw new IllegalArgumentException('Invalid number of entries - need between 1 & 4 entries to create an OpenCV scalar, not ' + values.length)
    }


    List<String> getChannelNames() {
        return metadata.channels.collect {it.getName()}
    }

    double getRequestedPixelSizeMicrons() {
        return metadata.inputPixelSizeMicrons
    }

}