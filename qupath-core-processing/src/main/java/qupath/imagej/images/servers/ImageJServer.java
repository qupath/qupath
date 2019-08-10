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

import java.awt.Rectangle;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that uses ImageJ's image-reading capabilities.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageJServer extends AbstractImageServer<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(ImageJServer.class);
	
	private ImageServerMetadata originalMetadata;
	
	private URI uri;
	private String[] args;
	
	private ImagePlus imp;
		
	private ColorModel colorModel;
	
	/**
	 * Constructor.
	 * @param uri URI representing the local file or an ImageJ-compatible URL
	 * @param args optional arguments (not currently used)
	 * @throws IOException
	 */
	public ImageJServer(final URI uri, final String...args) throws IOException {
		super(BufferedImage.class);
		this.uri = uri;
		File file = GeneralTools.toPath(uri).toFile();
		String path = file.getAbsolutePath();
		if (path.toLowerCase().endsWith(".tif") || path.toLowerCase().endsWith(".tiff")) {
			imp = IJ.openVirtual(path);
			// We only want a virtual stack if we have a large z-stack or time series
			long bpp = imp.getBitDepth() / 8;
			if (bpp == 3)
				bpp = 4; // ImageJ uses 4 bytes for an RGB image, but reports 24-bit
			long nBytes = (long)imp.getWidth() * imp.getHeight() * imp.getStackSize() * bpp;
			long maxMemory = Runtime.getRuntime().maxMemory();
			long allowedMemory;
			if (maxMemory == Long.MAX_VALUE)
				allowedMemory = 1024L * 1024L * 1024L;
			else
				allowedMemory = maxMemory / 8;
			if ((imp.getNFrames() == 1 && imp.getNSlices() == 1) || nBytes < allowedMemory) {
				logger.info("Opening {} fully, estimated {} MB (max memory {} MB)", uri, nBytes / (1024L * 1024L), maxMemory / (1024L * 1024L));
				imp = IJ.openImage(path);
			} else {
				logger.info("Opening {} as virtual stack, estimated {} MB (max memory {} MB)", uri, nBytes / (1024L * 1024L), maxMemory / (1024L * 1024L));
			}
		}
		if (imp == null)
			imp = IJ.openImage(path);
		if (imp == null)
			throw new IOException("Could not open " + path + " with ImageJ");
		
		Calibration cal = imp.getCalibration();
		double xMicrons = IJTools.tryToParseMicrons(cal.pixelWidth, cal.getXUnit());
		double yMicrons = IJTools.tryToParseMicrons(cal.pixelHeight, cal.getYUnit());
		double zMicrons = IJTools.tryToParseMicrons(cal.pixelDepth, cal.getZUnit());
		TimeUnit timeUnit = null;
		double[] timepoints = null;
		for (TimeUnit temp : TimeUnit.values()) {
			if (temp.toString().toLowerCase().equals(cal.getTimeUnit())) {
				timeUnit = temp;
				timepoints = new double[imp.getNFrames()];
				for (int i = 0; i < timepoints.length; i++) {
					timepoints[i] = i * cal.frameInterval;
				}
				break;
			}
		}
		
		PixelType pixelType;
		boolean isRGB = false;
		switch (imp.getType()) {
		case (ImagePlus.COLOR_RGB):
			isRGB = true;
		case (ImagePlus.COLOR_256):
		case (ImagePlus.GRAY8):
			pixelType = PixelType.UINT8;
			break;
		case (ImagePlus.GRAY16):
			pixelType = PixelType.UINT16;
			break;
		case (ImagePlus.GRAY32):
			pixelType = PixelType.FLOAT32;
			break;
		default:
			throw new IllegalArgumentException("Unknown ImagePlus type " + imp.getType());
		}

		List<ImageChannel> channels;
		if (isRGB)
			channels = ImageChannel.getDefaultRGBChannels();
		else if (imp instanceof CompositeImage) {
			CompositeImage impComp = (CompositeImage)imp;
			channels = new ArrayList<ImageChannel>();
			for (int channel = 0; channel < imp.getNChannels(); channel++) {
				LUT lut = impComp.getChannelLut(channel+1);
				int ind = lut.getMapSize()-1;
				String name = impComp.getStack().getSliceLabel(channel + 1);
				// Use slice label if it is a non-empty single line for a 2D image
				if (name == null || impComp.getNFrames() > 1 || impComp.getNSlices() > 1 || name.isBlank() || name.contains("\n"))
					name = "Channel " + (channel + 1);
				channels.add(
						ImageChannel.getInstance(name, lut.getRGB(ind))
						);
			}
		} else
			channels = ImageChannel.getDefaultChannelList(imp.getNChannels());
		
		this.args = args;
		var builder = new ImageServerMetadata.Builder() //, uri.normalize().toString())
				.width(imp.getWidth())
				.height(imp.getHeight())
				.name(imp.getTitle())
//				.args(args)
				.channels(channels)
				.sizeZ(imp.getNSlices())
				.sizeT(imp.getNFrames())
				.rgb(isRGB)
				.pixelType(pixelType)
				.zSpacingMicrons(zMicrons)
				.preferredTileSize(imp.getWidth(), imp.getHeight());
//				setMagnification(pxlInfo.mag). // Don't know magnification...?
		
		if (!Double.isNaN(xMicrons + yMicrons))
			builder = builder.pixelSizeMicrons(xMicrons, yMicrons);
		
		if (timeUnit != null)
			builder = builder.timepoints(timeUnit, timepoints);
		
		originalMetadata = builder.build();
		
//		if ((!isRGB() && nChannels() > 1) || getBitsPerPixel() == 32)
//			throw new IOException("Sorry, currently only RGB & single-channel 8 & 16-bit images supported using ImageJ server");
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}

	@Override
	protected String createID() {
		return getClass().getName() + ": " + uri.toString();
	}
	
	@Override
	public synchronized BufferedImage readBufferedImage(RegionRequest request) {
		// Deal with any cropping
//		ImagePlus imp2 = this.imp;
		
		int z = request.getZ()+1;
		int t = request.getT()+1;
		int nChannels = nChannels();
		
//		// There would be a possibility to intercept these calls and perform a z-projection...
//		if (nZSlices() > 1) {
//			imp.setT(t);
//			ZProjector zProjector = new ZProjector(imp);
//			zProjector.setMethod(ZProjector.MAX_METHOD);
//			zProjector.setStartSlice(1);
//			zProjector.setStopSlice(nZSlices());
//			zProjector.doHyperStackProjection(false);
//			imp = zProjector.getProjection();
//			z = 1;
//			t = 1;
//		}
		
		ImagePlus imp2;
		if (!(request.getX() == 0 && request.getY() == 0 && request.getWidth() == this.imp.getWidth() && request.getHeight() == this.imp.getHeight())) {
			// Synchronization introduced because of concurrency issues around here!
			this.imp.setRoi(request.getX(), request.getY(), request.getWidth(), request.getHeight());
			// Crop for required z and time
			Duplicator duplicator = new Duplicator();
			imp2 = duplicator.run(this.imp, 1, nChannels, z, z, t, t);
			this.imp.killRoi();
			if (imp2.getHeight() != request.getHeight()||
					imp2.getWidth() != request.getWidth())
				logger.warn("Unexpected image size {}x{} for request {}", imp.getWidth(), imp.getHeight(), request);
			z = 1;
			t = 1;
//			imp = imp.duplicate();
			imp2.killRoi();
		} else
			imp2 = this.imp;
		
		// Deal with any downsampling
		if (request.getDownsample() != 1) {
			ImageStack stackNew = null;
			Rectangle roi = imp2.getProcessor().getRoi();
			int w = (int)Math.max(1, Math.round(imp.getWidth() / request.getDownsample()));
			int h = (int)Math.max(1, Math.round(imp.getHeight() / request.getDownsample()));
			if (roi != null) {
				w = (int)Math.max(1, Math.round(roi.getWidth() / request.getDownsample()));
				h = (int)Math.max(1, Math.round(roi.getHeight() / request.getDownsample()));
			}
			for (int i = 1; i <= nChannels; i++) {
				int ind = imp2.getStackIndex(i, z, t);
				ImageProcessor ip = imp2.getStack().getProcessor(ind);
				ip.setInterpolationMethod(ImageProcessor.BILINEAR);
				ip = ip.resize(w, h, true);
				if (stackNew == null)
					stackNew = new ImageStack(ip.getWidth(), ip.getHeight());
				stackNew.addSlice("Channel " + i, ip);
			}
			imp2 = new ImagePlus(imp2.getTitle(), stackNew);
			imp2.setDimensions(nChannels, 1, 1);
			// Reset other indices
			z = 1;
			t = 1;
		}

		// If we don't have a color model yet, reuse this one
		var img = convertToBufferedImage(imp2, z, t, colorModel);
		if (colorModel == null)
			colorModel = img.getColorModel();
		return img;
	}
	
	
	/**
	 * Convert an ImagePlus to a BufferedImage, for a specific z-slice and timepoint.
	 * <p>
	 * Note that ImageJ uses 1-based indices for z and t! Therefore these should be &gt;= 1.
	 * <p>
	 * A {@link ColorModel} can optionally be provided; otherwise, a default ColorModel will be 
	 * created for the image (with may not be particularly suitable).
	 * 
	 * @param imp2
	 * @param z
	 * @param t
	 * @param colorModel
	 * @return
	 */
	public static BufferedImage convertToBufferedImage(ImagePlus imp2, int z, int t, ColorModel colorModel) {
		// Extract processor
		int nChannels = imp2.getNChannels();
		int ind = imp2.getStackIndex(1, z, t);
		ImageProcessor ip = imp2.getStack().getProcessor(ind);

		BufferedImage img = null;
		int w = ip.getWidth();
		int h = ip.getHeight();
		if (ip instanceof ColorProcessor) {
			img = ip.getBufferedImage();
//		} else if (nChannels == 1 && !(ip instanceof FloatProcessor)) {
//			// Take the easy way out for 8 and 16-bit images
//			if (ip instanceof ByteProcessor)
//				img = ip.getBufferedImage();
//			else if (ip instanceof ShortProcessor)
//				img = ((ShortProcessor)ip).get16BitBufferedImage();
		} else {
			// Try to create a suitable BufferedImage for whatever else we may need
			SampleModel model;
			if (colorModel == null) {
				if (ip instanceof ByteProcessor)
					colorModel = ColorModelFactory.getDummyColorModel(8);
				else if (ip instanceof ShortProcessor)
					colorModel = ColorModelFactory.getDummyColorModel(16);
				else
					colorModel = ColorModelFactory.getDummyColorModel(32);
			}

			if (ip instanceof ByteProcessor) {
				model = new BandedSampleModel(DataBuffer.TYPE_BYTE, w, h, nChannels);
				byte[][] bytes = new byte[nChannels][w*h];
				for (int i = 0; i < nChannels; i++) {
					int sliceInd = imp2.getStackIndex(i+1, z, t);
					bytes[i] = (byte[])imp2.getStack().getPixels(sliceInd);
				}
				DataBufferByte buffer = new DataBufferByte(bytes, w*h);
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
			} else if (ip instanceof ShortProcessor) {
				model = new BandedSampleModel(DataBuffer.TYPE_USHORT, w, h, nChannels);
				short[][] bytes = new short[nChannels][w*h];
				for (int i = 0; i < nChannels; i++) {
					int sliceInd = imp2.getStackIndex(i+1, z, t);
					bytes[i] = (short[])imp2.getStack().getPixels(sliceInd);
				}
				DataBufferUShort buffer = new DataBufferUShort(bytes, w*h);
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
			} else if (ip instanceof FloatProcessor){
				model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nChannels);
				float[][] bytes = new float[nChannels][w*h];
				for (int i = 0; i < nChannels; i++) {
					int sliceInd = imp2.getStackIndex(i+1, z, t);
					bytes[i] = (float[])imp2.getStack().getPixels(sliceInd);
				}
				DataBufferFloat buffer = new DataBufferFloat(bytes, w*h);
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
			}
			logger.error("Sorry, currently only RGB & single-channel images supported with ImageJ");
			return null;
		}
		//				if (request.getX() == 0 && request.getY() == 0 && request.getWidth() == imp.getWidth() && request.getHeight() == imp.getHeight())
		return img;
	}
	
	

	@Override
	public String getServerType() {
		return "ImageJ server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
				ImageJServerBuilder.class,
				getMetadata(),
				uri,
				args);
	}
	
}