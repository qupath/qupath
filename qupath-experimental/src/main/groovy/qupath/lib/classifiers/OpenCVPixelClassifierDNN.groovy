package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_core.MatVector
import org.bytedeco.javacpp.opencv_core.Scalar
import org.bytedeco.javacpp.opencv_dnn
import org.bytedeco.javacpp.opencv_imgproc
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.awt.image.ColorModel

class OpenCVPixelClassifierDNN extends AbstractOpenCVPixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class)

    private opencv_dnn.Net model
    private ColorModel colorModel
    private boolean doSoftMax
    private boolean do8Bit

    private Scalar means
    private Scalar scales
    private boolean scalesMatch

    OpenCVPixelClassifierDNN(String path, PixelClassifierMetadata metadata, boolean do8Bit=false) {
        super(metadata, do8Bit)
        logger.info("Reading model from {}", path)


        if (metadata.inputChannelMeans != null)
            means = toScalar(metadata.inputChannelMeans)
        else
            means = Scalar.ZERO
        if (metadata.inputChannelMeans != null)
            scales = toScalar(metadata.inputChannelScales)
        else
            scales = Scalar.ONE

        scalesMatch = true
        double firstScale = scales.get(0L)
        for (int i = 1; i < metadata.inputNumChannels; i++) {
            if (firstScale != scales.get(i)) {
                scalesMatch = false
                break
            }
        }

        this.model = opencv_dnn.readNetFromTensorflow(path)
    }


    /**
     * Default padding request
     *
     * @return
     */
    public int requestedPadding() {
        return 32
    }


    protected Mat doClassification(Mat mat, int pad=0, boolean doSoftmax=false) {
//        System.err.println("Mean start: " + opencv_core.mean(mat))
        opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGB2BGR)
        mat.convertTo(mat, opencv_core.CV_32F, 1.0/255.0, 0.0)
        mat.put(opencv_core.subtract(Scalar.ONE, mat))

//        System.err.println("Mean before: " + opencv_core.mean(mat))


        doSoftmax = true
        // Handle scales & offsets
        if (means != null)
            opencv_core.subtractPut(mat, means)
        if (scales != null) {
            if (scalesMatch)
                opencv_core.dividePut(mat, scales.get(0L))
            else {
                def matvec = new opencv_core.MatVector()
                opencv_core.split(mat, matvec)
                for (int i = 0; i < matvec.size(); i++)
                    opencv_core.multiplyPut(matvec.get(i), scales.get(i))
                opencv_core.merge(matvec, mat)
            }
        }



        def prob
        synchronized(model) {
            def blob = opencv_dnn.blobFromImage(mat)
            model.setInput(blob)
            prob = model.forward()
        }
        int nOutputChannels = metadata.nOutputChannels()
        def matOutput = []
        for (int i = 0; i < nOutputChannels; i++) {
            def plane = opencv_dnn.getPlane(prob, 0, i)
            matOutput << plane
        }
        def matvec = new MatVector(matOutput as Mat[])
        def matResult = new Mat()
        opencv_core.merge(matvec, matResult)

        // Remove padding, if necessary
        pad /= 2
        if (pad > 0) {
            matResult.put(matResult.apply(new opencv_core.Rect(pad, pad, matResult.cols()-pad*2, matResult.rows()-pad*2)).clone())
        }

        return matResult
    }

}