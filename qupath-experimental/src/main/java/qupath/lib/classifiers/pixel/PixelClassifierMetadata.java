package qupath.lib.classifiers.pixel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Metadata to control the behavior of a pixel classifier.
 */
public class PixelClassifierMetadata {

    public static enum PixelType { UInt8, UInt16, Float32, Float64 }
    public static enum OutputType { Classification, Probability, Logit }

	private double inputPixelSizeMicrons;
	private double[] inputChannelMeans;
	private double[] inputChannelScales;
	private int inputWidth = -1;
	private int inputHeight = -1;
	private int inputNumChannels = 3;
	private PixelType inputDataType = PixelType.UInt8;
	private int outputWidth = -1;
	private int outputHeight = -1;
	private OutputType outputType = OutputType.Probability;
	private List<PixelClassifierOutputChannel> channels;
	
    /**
     * Requested pixel size
     */
    public double getInputPixelSizeMicrons() {
    	return inputPixelSizeMicrons;
    };

    /**
     * Mean value to subtract from a specified input channel
     * Returns 0 if no scaling is specified.
     */
    public double getInputChannelMean(int channel) {
    	return inputChannelMeans == null ? 0 : inputChannelMeans[channel];
    }

    /**
     * Scale value used to divide a specified input channel (after possible mean subtraction), if required.
     * Returns 1 if no scaling is specified;
     */
    public double getInputChannelScale(int channel) {
    	return inputChannelScales == null ? 0 : inputChannelScales[channel];
    }
    
    /**
     * Mean values to subtract from each input channel, if required.
     * May return null if no mean subtraction is required.
     */
    public double[] getInputChannelMeans() {
    	return inputChannelMeans == null ? null : inputChannelMeans.clone();
    }

    /**
     * Scale values used to divide each input channel (after possible mean subtraction), if required
     * May return null if no scaling is required.
     */
    public double[] getInputChannelScales() {
    	return inputChannelScales == null ? null : inputChannelScales.clone();
    }

    /**
     * Requested width of input image, or -1 if the classifier is not fussy
     */
    public int getInputWidth() {
    	return inputWidth;
    }

    /**
     * Requested height of input image, or -1 if the classifier is not fussy
     */
    public int getInputHeight() {
    	return inputHeight;
    }

    /**
     * Requested number of channels in input image; default is 3 (consistent with assuming RGB)
     */
    public int getInputNumChannels() {
    	return inputNumChannels;
    }

    /**
     * Data type of input image; default is UInt8 (consistent with assuming RGB)
     */
    public PixelType getInputDataType() {
    	return inputDataType;
    }

    /**
     * Output image width for a specified inputWidth, or -1 if the inputWidth is not specified
     */
    public int getOutputWidth() {
    	return outputWidth;
    }

    /**
     * Output image height for a specified inputHeight, or -1 if the inputHeight is not specified
     */
    public int getOutputHeight() {
    	return outputHeight;
    }

    /**
     * Type of output; default is OutputType.Probability
     */
    public OutputType getOutputType() {
    	return outputType;
    }

    /**
     * List representing the names &amp; display colors for each output channel,
     * or for the output classifications if <code>outputType == OutputType.Classification</code>
     */
    public List<PixelClassifierOutputChannel> getChannels() {
    	return Collections.unmodifiableList(channels);
    }


    public int nOutputChannels() {
        return channels == null ? 0 : channels.size();
    }
    
    
    private PixelClassifierMetadata(Builder builder) {
    	this.inputChannelMeans = builder.inputChannelMeans;
    	this.inputPixelSizeMicrons = builder.inputPixelSizeMicrons;
    	this.inputChannelMeans = builder.inputChannelMeans;
    	this.inputChannelScales = builder.inputChannelScales;
    	this.inputWidth = builder.inputWidth;
    	this.inputHeight = builder.inputHeight;
    	this.inputNumChannels = builder.inputNumChannels;
    	this.inputDataType = builder.inputDataType;
    	this.outputWidth = builder.outputWidth;
    	this.outputHeight = builder.outputHeight;
    	this.outputType = builder.outputType;
    	this.channels = builder.channels;
    }
    
    
    public static class Builder {
    	
    	private double inputPixelSizeMicrons;
    	private double[] inputChannelMeans;
    	private double[] inputChannelScales;
    	private int inputWidth = -1;
    	private int inputHeight = -1;
    	private int inputNumChannels = 3;
    	private PixelType inputDataType = PixelType.UInt8;
    	private int outputWidth = -1;
    	private int outputHeight = -1;
    	private OutputType outputType = OutputType.Probability;
    	private List<PixelClassifierOutputChannel> channels = new ArrayList<>();
    	
    	public PixelClassifierMetadata build() {
    		return new PixelClassifierMetadata(this);
    	}
    	
    	public Builder inputPixelSizeMicrons(double pixelSizeMicrons) {
    		this.inputPixelSizeMicrons = pixelSizeMicrons;
    		return this;
    	}
    	
    	public Builder inputShape(int width, int height) {
    		this.inputWidth = width;
    		this.inputHeight = height;
    		return this;
    	}
    	
    	public Builder inputChannels(int nChannels) {
    		this.inputNumChannels = nChannels;
    		return this;
    	}
    	
    	public Builder inputChannelMeans(double... means) {
    		this.inputChannelMeans = means.clone();
    		return this;
    	}
    	
    	public Builder inputChannelScales(double... scales) {
    		this.inputChannelScales = scales.clone();
    		return this;
    	}
    	
    	public Builder channels(PixelClassifierOutputChannel...channels) {
    		return channels(Arrays.asList(channels));
    	}
    	
    	public Builder channels(List<PixelClassifierOutputChannel> channels) {
    		this.channels.addAll(channels);
    		return this;
    	}
    	
    	    	
    }
    
}