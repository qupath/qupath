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

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.IOException;
import java.util.Arrays;
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
import qupath.lib.awt.color.model.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
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
	
	private ImagePlus imp;
	
	private static List<String> micronList = Arrays.asList("micron", "microns", "um", GeneralTools.micrometerSymbol());
	
	private ColorModel colorModel;
	
	public ImageJServer(final String path) throws IOException {
		if (path.toLowerCase().endsWith(".tif") || path.toLowerCase().endsWith(".tiff")) {
			imp = IJ.openVirtual(path);
		}
		if (imp == null)
			imp = IJ.openImage(path);
		if (imp == null)
			throw new IOException("Could not open " + path + " with ImageJ");
		
		Calibration cal = imp.getCalibration();
		double xMicrons = tryToParseMicrons(cal.pixelWidth, cal.getXUnit());
		double yMicrons = tryToParseMicrons(cal.pixelHeight, cal.getYUnit());
		double zMicrons = tryToParseMicrons(cal.pixelDepth, cal.getZUnit());
		TimeUnit timeUnit = null;
		for (TimeUnit temp : TimeUnit.values()) {
			if (temp.toString().toLowerCase().equals(cal.getTimeUnit())) {
				timeUnit = temp;
				break;
			}
		}
		
		boolean isRGB = imp.getType() == ImagePlus.COLOR_RGB;
		originalMetadata = new ImageServerMetadata.Builder(path,
				imp.getWidth(),
				imp.getHeight()).
				setPixelSizeMicrons(xMicrons, yMicrons).
				setSizeC(imp.getNChannels()).
				setSizeZ(imp.getNSlices()).
				setSizeT(imp.getNFrames()).
				setTimeUnit(timeUnit).
				setRGB(isRGB).
				setBitDepth(isRGB ? 8 : imp.getBitDepth()).
				setZSpacingMicrons(zMicrons).
				setPreferredDownsamples(1.0). // TODO: Consider an in-memory image pyramid for large images
				setPreferredTileSize(imp.getWidth(), imp.getHeight()).
//				setMagnification(pxlInfo.mag). // Don't know magnification...?
				build();
		
//		if ((!isRGB() && nChannels() > 1) || getBitsPerPixel() == 32)
//			throw new IOException("Sorry, currently only RGB & single-channel 8 & 16-bit images supported using ImageJ server");
	}
	
	
	/**
	 * Based on a value and its units, try to get something suitable in microns.
	 * (In other words, see if the units are 'microns' in some sense, and if not check if 
	 * they are something else that can easily be converted).
	 * 
	 * @param value
	 * @param unit
	 * @return the parsed value in microns, or NaN if the unit couldn't be parsed
	 */
	private static double tryToParseMicrons(final double value, final String unit) {
		if (unit == null)
			return Double.NaN;
		
		String u = unit.toLowerCase();
		boolean microns = micronList.contains(u);
		if (microns)
			return value;
		if ("nm".equals(u))
			return value * 1000;
		return Double.NaN;
	}
	
	@Override
	public double getTimePoint(int ind) {
		return imp.getCalibration().frameInterval * ind;
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
			z = 1;
			t = 1;
//			imp = imp.duplicate();
			imp2.killRoi();
		} else
			imp2 = this.imp;
		
		// Deal with any downsampling
		if (request.getDownsample() != 1) {
			ImageStack stackNew = null;
			for (int i = 1; i <= nChannels; i++) {
				int ind = imp2.getStackIndex(i, z, t);
				ImageProcessor ip = imp2.getStack().getProcessor(ind);
				ip = ip.resize((int)(ip.getWidth() / request.getDownsample() + 0.5));
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

		// Extract processor
		int ind = imp2.getStackIndex(1, z, t);
		ImageProcessor ip = imp2.getStack().getProcessor(ind);

		BufferedImage img = null;
		int w = ip.getWidth();
		int h = ip.getHeight();
		if (ip instanceof ColorProcessor) {
			img = ip.getBufferedImage();
		} else if (nChannels == 1 && !(ip instanceof FloatProcessor)) {
			// Take the easy way out for 8 and 16-bit images
			if (ip instanceof ByteProcessor)
				img = ip.getBufferedImage();
			else if (ip instanceof ShortProcessor)
				img = ((ShortProcessor)ip).get16BitBufferedImage();
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
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), true, null);
			} else if (ip instanceof ShortProcessor) {
				model = new BandedSampleModel(DataBuffer.TYPE_USHORT, w, h, nChannels);
				short[][] bytes = new short[nChannels][w*h];
				for (int i = 0; i < nChannels; i++) {
					int sliceInd = imp2.getStackIndex(i+1, z, t);
					bytes[i] = (short[])imp2.getStack().getPixels(sliceInd);
				}
				DataBufferUShort buffer = new DataBufferUShort(bytes, w*h);
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), true, null);
			} else if (ip instanceof FloatProcessor){
				model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nChannels);
				float[][] bytes = new float[nChannels][w*h];
				for (int i = 0; i < nChannels; i++) {
					int sliceInd = imp2.getStackIndex(i+1, z, t);
					bytes[i] = (float[])imp2.getStack().getPixels(sliceInd);
				}
				DataBufferFloat buffer = new DataBufferFloat(bytes, w*h);
				return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), true, null);
			}
			logger.error("Sorry, currently only RGB & single-channel images supported with ImageJ");
			return null;
		}
//		if (request.getX() == 0 && request.getY() == 0 && request.getWidth() == imp.getWidth() && request.getHeight() == imp.getHeight())
		return img;
//		return img.getSubimage(request.getX(), request.getY(), request.getWidth(), request.getHeight());
	}

	@Override
	public String getServerType() {
		return "ImageJ server";
	}

	@Override
	public String getDisplayedImageName() {
		return imp.getTitle();
	}

	@Override
	public Integer getDefaultChannelColor(int channel) {
		if (isRGB()) {
			return getDefaultRGBChannelColors(channel);
		}
		// Grayscale
		if (nChannels() == 1)
			return ColorTools.makeRGB(255, 255, 255);
		
		if (imp instanceof CompositeImage) {
			CompositeImage impComp = (CompositeImage)imp;
			LUT lut = impComp.getChannelLut(channel+1);
			int ind = lut.getMapSize()-1;
			return lut.getRGB(ind);
		}
		return super.getDefaultChannelColor(channel);
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
}