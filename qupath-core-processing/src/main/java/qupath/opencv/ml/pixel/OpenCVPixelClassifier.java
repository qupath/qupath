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

package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.io.UriResource;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.tools.OpenCVTools;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenCVPixelClassifier implements PixelClassifier, UriResource {
	
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
    	
//    	logger.debug("Before: \n" + OpenCVTools.memoryReport(", "));
    	
    	try (@SuppressWarnings("unchecked")
		var scope = new PointerScope()) {
	    	var matResult = getOp().apply(imageData, request);

	    	var type = getMetadata().getOutputType();
	    	ColorModel colorModelLocal = null;
	    	if (type == null)
	    		type = ChannelType.DEFAULT;
	    	switch(type) {
			case CLASSIFICATION:
	    		colorModelLocal = getClassificationsColorModel();
				break;
			case DEFAULT:
			case DENSITY:
			case FEATURE:
			case MULTICLASS_PROBABILITY:
			case PROBABILITY:
			default:
	    		colorModelLocal = getProbabilityColorModel(matResult.depth() == opencv_core.CV_8U);
				break;
	    	}
	
	        // Create & return BufferedImage
	        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);
	
//	        // Return memory as quickly as we can
//	        scope.deallocate();
	        return imgResult;
    	} finally {
//        	System.gc(); // Shouldn't be needed if deallocate is used?
//    		logger.debug("After: \n" + OpenCVTools.memoryReport(", "));
    	}
    }

	@Override
	public Collection<URI> getURIs() throws IOException {
		return op.getURIs();
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		return op.updateURIs(replacements);
	}
    
    
}