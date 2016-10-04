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
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import qupath.imagej.helpers.IJTools;
import qupath.imagej.objects.PathImagePlus;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;

/**
 * Simple ImageServer that wraps around an existing ImageServer<BufferedImage> 
 * to convert the BufferedImages into ImagePlus objects, with the Calibration set suitably.
 * <p>
 * Note that changes to the calibration of this image 'filter through' the the ImageServer<BufferedImage> used for pixel access.
 * 
 * @author Pete Bankhead
 *
 */
public class BufferedImagePlusServer extends AbstractImageServer<BufferedImage> implements ImagePlusServer {

	private ImageServer<BufferedImage> server;
	
	public BufferedImagePlusServer(ImageServer<BufferedImage> server) {
		this.server = server;
	}
	
	protected ImageServer<BufferedImage> getWrappedServer() {
		return server;
	}
	
	@Override
	public String getPath() {
		return server.getPath();
	}

	@Override
	public String getShortServerName() {
		return server.getShortServerName();
	}

	@Override
	public double[] getPreferredDownsamples() {
		return server.getPreferredDownsamples();
	}

	@Override
	public double getMagnification() {
		return server.getMagnification();
	}

	@Override
	public int getWidth() {
		return server.getWidth();
	}

	@Override
	public int getHeight() {
		return server.getHeight();
	}

	@Override
	public int nChannels() {
		return server.nChannels();
	}

	@Override
	public double getPixelWidthMicrons() {
		return server.getPixelWidthMicrons();
	}

	@Override
	public double getPixelHeightMicrons() {
		return server.getPixelHeightMicrons();
	}

	
	protected Calibration getCalibration() {
		return null;
	}
	
	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		return server.readRegion(request);
	}
	
	@Override
	public void close() {
		// TODO: Consider whether or not to close the parent server
//		server.close();
	}

	
	@Override
	public String getServerType() {
		return String.format("%s (ImagePlus wrapper)", server.getServerType());
	}

	@Override
	public int getPreferredTileWidth() {
		return server.getPreferredTileWidth();
	}

	@Override
	public int getPreferredTileHeight() {
		return server.getPreferredTileHeight();
	}

	@Override
	public boolean isRGB() {
		return server.isRGB();
	}

	@Override
	public int nZSlices() {
		return server.nZSlices();
	}

	@Override
	public double getZSpacingMicrons() {
		return server.getZSpacingMicrons();
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		return server.readBufferedImage(request);
	}

	@Override
	public int nTimepoints() {
		return server.nTimepoints();
	}

	@Override
	public double getTimePoint(int ind) {
		return server.getTimePoint(ind);
	}

	@Override
	public TimeUnit getTimeUnit() {
		return server.getTimeUnit();
	}

	@Override
	public List<String> getSubImageList() {
		return server.getSubImageList();
	}

	@Override
	public String getDisplayedImageName() {
		return server.getDisplayedImageName();
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this.server.usesBaseServer(server);
	}
	
	@Override
	public int getBitsPerPixel() {
		return server.getBitsPerPixel();
	}
	
	@Override
	public Integer getDefaultChannelColor(int channel) {
		return server.getDefaultChannelColor(channel);
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
				sampleModel.getSamples(0, 0, w, h, b, pixels, img.getRaster().getDataBuffer());
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
	
	@Override
	public List<String> getAssociatedImageList() {
		return server.getAssociatedImageList();
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		return server.getAssociatedImage(name);
	}

	@Override
	public boolean containsSubImages() {
		return server.containsSubImages();
	}
	
	@Override
	public File getFile() {
		return server.getFile();
	}

	@Override
	public String getSubImagePath(String imageName) {
		return server.getSubImagePath(imageName);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return server.getMetadata();
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		server.setMetadata(metadata);
	}
	
}
