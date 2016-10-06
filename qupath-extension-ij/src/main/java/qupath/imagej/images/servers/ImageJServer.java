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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import qupath.lib.awt.images.PathBufferedImage;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
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
	
	private String path;
	private ImagePlus imp;
	
	private static List<String> micronList = Arrays.asList("micron", "microns", "um", GeneralTools.micrometerSymbol());
	
	private ImageServerMetadata originalMetadata;
	private ImageServerMetadata userMetadata;
	
	public ImageJServer(final String path) throws IOException {
		this.path = path;
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
		
		originalMetadata = new ImageServerMetadata.Builder(path,
				imp.getWidth(),
				imp.getHeight()).
				setPixelSizeMicrons(xMicrons, yMicrons).
				setSizeC(imp.getNChannels()).
				setSizeZ(imp.getNSlices()).
				setSizeT(imp.getNFrames()).
				setTimeUnit(timeUnit).
				setZSpacingMicrons(zMicrons).
				setPreferredTileSize(imp.getWidth(), imp.getHeight()).
//				setMagnification(pxlInfo.mag). // Don't know magnification...?
				build();
		
		if ((!isRGB() && nChannels() > 1) || getBitsPerPixel() == 32)
			throw new IOException("Sorry, currently only RGB & single-channel 8 & 16-bit images supported using ImageJ server");
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
	public double[] getPreferredDownsamples() {
		return new double[]{1};
	}

	@Override
	public boolean isRGB() {
		return imp.getType() == ImagePlus.COLOR_RGB;
	}

	@Override
	public double getTimePoint(int ind) {
		return imp.getCalibration().frameInterval * ind;
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		BufferedImage img = readBufferedImage(request);
		if (img == null)
			return null;
		return new PathBufferedImage(this, request, img);
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		int ind = imp.getStackIndex(1, request.getZ(), request.getT());
		ImageProcessor ip = imp.getStack().getProcessor(ind);
		
		// Deal with any cropping
		if (!(request.getX() == 0 && request.getY() == 0 && request.getWidth() == imp.getWidth() && request.getHeight() == imp.getHeight())) {
			ip.setRoi(request.getX(), request.getY(), request.getWidth(), request.getHeight());
			ImageProcessor ip2 = ip.duplicate();
			ip.resetRoi();
			ip = ip2;
		}
		// Deal with any downsampling
		if (request.getDownsample() != 1) {
			ip = ip.resize((int)(ip.getWidth() / request.getDownsample() + 0.5));
		}
		
		BufferedImage img = null;
		if (ip instanceof ColorProcessor) {
			img = ip.getBufferedImage();
		} else if (nChannels() == 1) {
			if (ip instanceof ByteProcessor)
				img = ip.getBufferedImage();
			else if (ip instanceof ShortProcessor)
				img = ((ShortProcessor)ip).get16BitBufferedImage();
			else if (ip instanceof FloatProcessor){
				img = ip.getBufferedImage(); // TODO: 32-bit... will end up being converted to 8-bit, sadly
			}
		} else {
			logger.error("Sorry, currently only RGB & single-channel images supported with ImageJ");
			return null;
		}
		if (request.getX() == 0 && request.getY() == 0 && request.getWidth() == imp.getWidth() && request.getHeight() == imp.getHeight())
			return img;
		return img.getSubimage(request.getX(), request.getY(), request.getWidth(), request.getHeight());
	}

	@Override
	public String getServerType() {
		return "ImageJ server";
	}

	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getAssociatedImageList() {
		return Collections.emptyList();
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		return null;
	}

	@Override
	public String getDisplayedImageName() {
		return imp.getTitle();
	}

	@Override
	public boolean containsSubImages() {
		return false;
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}

	@Override
	public File getFile() {
		return new File(path);
	}

	@Override
	public int getBitsPerPixel() {
		return isRGB() ? 8 : imp.getBitDepth();
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
			int r = lut.getRed(lut.getMapSize());
			int g = lut.getRed(lut.getMapSize());
			int b = lut.getRed(lut.getMapSize());
			return ColorTools.makeRGB(r, g, b);
		}
		return getDefaultChannelColor(channel);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return userMetadata == null ? originalMetadata : userMetadata;
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		if (!originalMetadata.isCompatibleMetadata(metadata))
			throw new RuntimeException("Specified metadata is incompatible with original metadata for " + this);
		userMetadata = metadata;
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

}
