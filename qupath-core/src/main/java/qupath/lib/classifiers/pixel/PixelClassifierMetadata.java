package qupath.lib.classifiers.pixel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import qupath.lib.images.servers.ImageChannel;

/**
 * Metadata to control the behavior of a pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierMetadata {

	public static enum PixelType { UInt8, UInt16, Float32, Float64 }
	public static enum OutputType { Features, Classification, Probability }

	private double inputPixelSize;
	private String inputPixelSizeUnits;
	private double[] inputChannelMeans;
	private double[] inputChannelScales;
	private boolean strictInputSize = false;
	private int inputWidth = -1;
	private int inputHeight = -1;
	private int inputNumChannels = 3;
	private PixelType inputDataType = PixelType.UInt8;
	private int outputWidth = -1;
	private int outputHeight = -1;
	private OutputType outputType = OutputType.Classification;
	private List<ImageChannel> channels;
	
    /**
     * Requested pixel size
     */
	public double getInputPixelSize() {
    	return inputPixelSize;
    };
    
    /**
     * Units for input pixel size.
     * 
     * @return
     * 
     * @see qupath.lib.images.servers.PixelCalibration#PIXEL
     * @see qupath.lib.images.servers.PixelCalibration#MICROMETER
     */
    public String getInputPixelSizeUnits() {
    	return inputPixelSizeUnits;
    }
    
    /**
     * Returns {@code true} if the input size must be strictly applied, {@code false} if 
     * different input image sizes can be handled.
     */
    public boolean strictInputSize() {
    	return strictInputSize;
    }

    /**
     * Mean value to subtract from a specified input channel
     * Returns 0 if no scaling is specified.
     */
    private double getInputChannelMean(int channel) {
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
    private PixelType getInputDataType() {
    	return inputDataType;
    }

    /**
     * Output image width for a specified inputWidth, or -1 if the inputWidth is not specified
     */
    private int getOutputWidth() {
    	return outputWidth;
    }

    /**
     * Output image height for a specified inputHeight, or -1 if the inputHeight is not specified
     */
    private int getOutputHeight() {
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
    public List<ImageChannel> getChannels() {
    	return Collections.unmodifiableList(channels);
    }


    private int nOutputChannels() {
        return channels == null ? 0 : channels.size();
    }
    
    
    private PixelClassifierMetadata(Builder builder) {
    	this.inputPixelSizeUnits = builder.inputPixelSizeUnits;
    	this.inputChannelMeans = builder.inputChannelMeans;
    	this.inputPixelSize = builder.inputPixelSize;
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
    	this.strictInputSize = builder.strictInputSize;
    }
    
    
    public static class Builder {
    	
    	private String inputPixelSizeUnits;
    	private double inputPixelSize;
    	private double[] inputChannelMeans;
    	private double[] inputChannelScales;
    	private boolean strictInputSize = false;
    	private int inputWidth = -1;
    	private int inputHeight = -1;
    	private int inputNumChannels = 3;
    	private PixelType inputDataType = PixelType.UInt8;
    	private int outputWidth = -1;
    	private int outputHeight = -1;
    	private OutputType outputType = OutputType.Probability;
    	private List<ImageChannel> channels = new ArrayList<>();
    	
    	public PixelClassifierMetadata build() {
    		return new PixelClassifierMetadata(this);
    	}
    	
    	public Builder setOutputType(OutputType type) {
    		this.outputType = type;
    		return this;
    	}
    	
    	public Builder inputPixelSize(double pixelSizeMicrons) {
    		this.inputPixelSize = pixelSizeMicrons;
    		return this;
    	}
    	
    	public Builder inputPixelSizeUnits(String inputPixelSizeUnits) {
    		this.inputPixelSizeUnits = inputPixelSizeUnits;
    		return this;
    	}
    	
    	public Builder inputShape(int width, int height) {
    		this.inputWidth = width;
    		this.inputHeight = height;
    		return this;
    	}
    	
    	private Builder strictInputSize() {
    		this.strictInputSize = true;
    		return this;
    	}
    	
    	private Builder inputChannels(int nChannels) {
    		this.inputNumChannels = nChannels;
    		return this;
    	}
    	
    	private Builder inputChannelMeans(double... means) {
    		this.inputChannelMeans = means.clone();
    		return this;
    	}
    	
    	private Builder inputChannelScales(double... scales) {
    		this.inputChannelScales = scales.clone();
    		return this;
    	}
    	
    	public Builder channels(ImageChannel...channels) {
    		return channels(Arrays.asList(channels));
    	}
    	
    	public Builder channels(List<ImageChannel> channels) {
    		this.channels.addAll(channels);
    		return this;
    	}
    	
    	    	
    }
    
}