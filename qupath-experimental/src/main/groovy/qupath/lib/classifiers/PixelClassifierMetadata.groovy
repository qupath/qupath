package qupath.lib.classifiers

/**
 * Metadata to control the behavior of a pixel classifier.
 */
class PixelClassifierMetadata {

    static enum PixelType { UInt8, UInt16, Float32, Float64 }
    static enum OutputType { Classification, Probability, Logit }

    /**
     * Requested pixel size
     */
    double inputPixelSizeMicrons

    /**
     * Mean values to subtract from each input channel, if required
     */
    double[] inputChannelMeans

    /**
     * Scale values used to divide each input channel (after possible mean subtraction), if required
     */
    double[] inputChannelScales

    /**
     * Requested width of input image, or -1 if the classifier is not fussy
     */
    int inputWidth = -1

    /**
     * Requested height of input image, or -1 if the classifier is not fussy
     */
    int inputHeight = -1

    /**
     * Requested number of channels in input image; default is 3 (consistent with assuming RGB)
     */
    int inputNumChannels = 3

    /**
     * Data type of input image; default is UInt8 (consistent with assuming RGB)
     */
    PixelType inputDataType = PixelType.UInt8

    /**
     * Output image width for a specified inputWidth, or -1 if the inputWidth is not specified
     */
    int outputWidth = -1

    /**
     * Output image height for a specified inputHeight, or -1 if the inputHeight is not specified
     */
    int outputHeight = -1

    /**
     * Type of output; default is OutputType.Probability
     */
    OutputType outputType = OutputType.Probability

    /**
     * List representing the names & display colors for each output channel,
     * or for the output classifications if <code>outputType == OutputType.Classification</code>
     */
    List<PixelClassifierOutputChannel> channels


    public int nOutputChannels() {
        return channels == null ? 0 : channels.size()
    }

}


class PixelClassifierOutputChannel {

    /**
     * Name of the output channel
     */
    String name

    /**
     * Color used to display the output channel
     */
    Integer color

}