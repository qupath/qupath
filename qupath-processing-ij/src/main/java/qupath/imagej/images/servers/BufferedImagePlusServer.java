/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.images.servers;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.objects.PathImagePlus;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.WrappedImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * Simple ImageServer that wraps around an existing {@code ImageServer<BufferedImage>}
 * to convert the BufferedImages into ImagePlus objects, with the Calibration set suitably.
 * <p>
 * Note that changes to the calibration of this image 'filter through' the the {@code ImageServer<BufferedImage>} used for pixel access.
 * 
 * @author Pete Bankhead
 *
 */
public class BufferedImagePlusServer extends WrappedImageServer<BufferedImage> implements ImagePlusServer {

	public BufferedImagePlusServer(ImageServer<BufferedImage> server) {
		super(server);
	}
	
	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		return getWrappedServer().readRegion(request);
	}
	
	@Override
	public String getServerType() {
		return String.format("%s (ImagePlus wrapper)", getWrappedServer().getServerType());
	}

	@Override
	public PathImage<ImagePlus> readImagePlusRegion(RegionRequest request) {
		// Create an ImagePlus from a BufferedImage
		BufferedImage img = readBufferedImage(request);
		ImagePlus imp = null;
		SampleModel sampleModel = img.getSampleModel();
		int dataType = sampleModel.getDataType();
		int w = img.getWidth();
		int h = img.getHeight();
		if ((dataType == DataBuffer.TYPE_BYTE && sampleModel.getNumBands() != 1) ||
				dataType == DataBuffer.TYPE_USHORT || dataType == DataBuffer.TYPE_SHORT || dataType == DataBuffer.TYPE_FLOAT || dataType == DataBuffer.TYPE_DOUBLE) {
			// Handle non-8-bit images
			ImageStack stack = new ImageStack(w, h);
			for (int b = 0; b < sampleModel.getNumBands(); b++) {
				// Read data as float (no matter what it is)
				FloatProcessor fp = new FloatProcessor(w, h);
				float[] pixels = (float[])fp.getPixels();
				img.getRaster().getSamples(0, 0, w, h, b, pixels);
//				sampleModel.getSamples(0, 0, w, h, b, pixels, img.getRaster().getDataBuffer());
				// Convert to 8 or 16-bit, if appropriate
				if (dataType == DataBuffer.TYPE_BYTE) {
					ByteProcessor bp = new ByteProcessor(w, h);
					bp.setPixels(0, fp);
					stack.addSlice(bp);
				} else if (dataType == DataBuffer.TYPE_USHORT) {
					ShortProcessor sp = new ShortProcessor(w, h);
					sp.setPixels(0, fp);
					stack.addSlice(sp);
				} else
					stack.addSlice(fp);
			}
			imp = new ImagePlus(getShortServerName(), stack);
		} else
			// Create whatever image ImageJ will give us
			imp = new ImagePlus(getShortServerName(), img);
		imp.setDimensions(imp.getNSlices(), 1, 1);
		IJTools.calibrateImagePlus(imp, request, this);
		return PathImagePlus.createPathImage(this, request, imp);
	}
	
	
}
