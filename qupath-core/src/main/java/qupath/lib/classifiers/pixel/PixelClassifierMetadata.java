/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.classifiers.pixel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Metadata to control the behavior of a pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierMetadata {
	
	private int inputPadding = 0;
	
	private PixelCalibration inputResolution;
	
	private int inputWidth = -1;
	private int inputHeight = -1;
	private int inputNumChannels = 3;
	
	private ImageServerMetadata.ChannelType outputType = ImageServerMetadata.ChannelType.CLASSIFICATION;
	private List<ImageChannel> outputChannels;
	private Map<Integer, PathClass> classificationLabels;
	
	/**
     * Requested pixel size for input.
	 * 
	 * @return
	 */
	public PixelCalibration getInputResolution() {
    	return inputResolution;
    };
    
    /**
     * Requested input padding (above, below, left and right).
     * @return
     */
    public int getInputPadding() {
    	return inputPadding;
    }
    
//    /**
//     * Returns {@code true} if the input size must be strictly applied, {@code false} if 
//     * different input image sizes can be handled.
//     */
//    public boolean strictInputSize() {
//    	return strictInputSize;
//    }

    /**
     * Requested width of input image, or -1 if the classifier is not fussy
     * @return 
     */
    public int getInputWidth() {
    	return inputWidth;
    }

    /**
     * Requested height of input image, or -1 if the classifier is not fussy
     * @return 
     */
    public int getInputHeight() {
    	return inputHeight;
    }

    /**
     * Requested number of channels in input image; default is 3 (consistent with assuming RGB)
     * @return 
     */
    public int getInputNumChannels() {
    	return inputNumChannels;
    }

    /**
     * Type of output; default is OutputType.Probability
     * @return 
     */
    public ImageServerMetadata.ChannelType getOutputType() {
    	return outputType;
    }

    /**
     * List representing the names &amp; display colors for each output channel,
     * or for the output classifications if <code>outputType == OutputType.Classification</code>
     * @return 
     */
    public synchronized List<ImageChannel> getOutputChannels() {
    	if (outputChannels == null && classificationLabels != null) {
    		outputChannels = PathClassifierTools.classificationLabelsToChannels(classificationLabels, true);
    	}
    	return outputChannels == null ? Collections.emptyList() : Collections.unmodifiableList(outputChannels);
    }
    
    /**
     * Map between integer labels and classifications. For a labelled image (output type is CLASSIFICATION) then 
     * the labels correspond to pixel values. Otherwise they correspond to channel numbers.
     * @return
     */
    public synchronized Map<Integer, PathClass> getClassificationLabels() {
    	if (classificationLabels == null && outputChannels != null) {
    		classificationLabels = new LinkedHashMap<>();
    		for (int i = 0; i < outputChannels.size(); i++) {
    			var channel = outputChannels.get(i);
    			classificationLabels.put(i, PathClassFactory.getPathClass(channel.getName(), channel.getColor()));
    		}
    	}
    	return classificationLabels == null ? Collections.emptyMap() : Collections.unmodifiableMap(classificationLabels);
    }
    
    
    private PixelClassifierMetadata(Builder builder) {
    	this.inputResolution = builder.inputResolution;
    	this.inputPadding = builder.inputPadding;
    	this.inputWidth = builder.inputWidth;
    	this.inputHeight = builder.inputHeight;
    	this.inputNumChannels = builder.inputNumChannels;
    	this.classificationLabels = builder.classificationLabels;
    	this.outputType = builder.outputType;
    	this.outputChannels = builder.outputChannels;
//    	this.strictInputSize = builder.strictInputSize;
    }
    
    
    /**
     * Builder to create {@link PixelClassifierMetadata} objects.
     */
    public static class Builder {
    	
    	private int inputPadding = 0;
    	
    	private PixelCalibration inputResolution;
    	
    	private int inputWidth = -1;
    	private int inputHeight = -1;
    	private int inputNumChannels = 3;
    	
    	private ImageServerMetadata.ChannelType outputType = ImageServerMetadata.ChannelType.CLASSIFICATION;
    	private List<ImageChannel> outputChannels = new ArrayList<>();
    	
    	private Map<Integer, PathClass> classificationLabels;
    	
    	/**
    	 * Build a new PixelClassifierMetadata object.
    	 * @return
    	 */
    	public PixelClassifierMetadata build() {
    		return new PixelClassifierMetadata(this);
    	}
    	
    	/**
    	 * Amount of padding requested for the left, right, top and bottom of the image tile being classified.
    	 * This can be used to reduce boundary artifacts.
    	 * @param inputPadding
    	 * @return
    	 */
    	public Builder inputPadding(int inputPadding) {
    		this.inputPadding = inputPadding;
    		return this;
    	}
    	
    	/**
    	 * Specify the output channel type.
    	 * @param type
    	 * @return
    	 */
    	public Builder setChannelType(ImageServerMetadata.ChannelType type) {
    		this.outputType = type;
    		return this;
    	}
    	
    	/**
    	 * Pixel size defining the resolution at which the classifier should operate.
    	 * @param inputResolution
    	 * @return
    	 */
    	public Builder inputResolution(PixelCalibration inputResolution) {
    		this.inputResolution = inputResolution;
    		return this;
    	}
    	
    	/**
    	 * Preferred input image width and height. This may either be a hint or strictly enforced.
    	 * @param width
    	 * @param height
    	 * @return
    	 */
    	public Builder inputShape(int width, int height) {
    		this.inputWidth = width;
    		this.inputHeight = height;
    		return this;
    	}
    	
//    	/**
//    	 * Strictly enforce the input shape. Otherwise it is simply a request, but the pixel classifier may use a different size.
//    	 * @return
//    	 */
//    	public Builder strictInputSize() {
//    		this.strictInputSize = true;
//    		return this;
//    	}
    	
    	/**
    	 * Specify channels for output.
    	 * @param channels
    	 * @return
    	 */
    	public Builder outputChannels(ImageChannel... channels) {
    		this.outputChannels.addAll(Arrays.asList(channels));
    		return this;
    	}
    	
    	/**
    	 * Specify channels for output.
    	 * @param channels
    	 * @return
    	 */
    	public Builder outputChannels(Collection<ImageChannel> channels) {
    		this.outputChannels.addAll(channels);
    		return this;
    	}
    	
    	/**
    	 * Specify classification labels. This may be used instead of outputChannels.
    	 * @param labels
    	 * @return
    	 */
    	public Builder classificationLabels(Map<Integer, PathClass> labels) {
    		this.classificationLabels = new LinkedHashMap<>(labels);
    		return this;
    	}
    	    	
    }
    
}