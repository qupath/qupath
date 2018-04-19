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

package qupath.imagej.objects;

import java.awt.image.BufferedImage;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.measure.Calibration;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.images.servers.ImagePlusServer;
import qupath.imagej.images.servers.ImagePlusServerBuilder;

/**
 * PathImage implementation backed by an ImageJ ImagePlus object.
 * 
 * @author Pete Bankhead
 *
 */
public class PathImagePlus implements PathImage<ImagePlus>, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(PathImagePlus.class);
	
	transient private ImagePlusServer server;
	transient private ImagePlus imp = null;
	private RegionRequest request;
	private double pixelWidthMicrons = Double.NaN, pixelHeightMicrons = Double.NaN;

	
	public static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final ROI pathROI, final double downsample) {
		return createPathImage(server, RegionRequest.createInstance(server.getPath(), downsample, pathROI));
	}

	public static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final double downsample) {
		return createPathImage(server, RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight()));
	}

	public static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final RegionRequest request) {
		return new PathImagePlus(server, request, null);
	}
	
	public static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final RegionRequest request, final ImagePlus imp) {
		return new PathImagePlus(server, request, imp);
	}
	
	/**
	 * Create a PathImage for which the image will be read lazily.
	 * 
	 * @param server
	 * @param request
	 * @param imp
	 */
	PathImagePlus(ImageServer<BufferedImage> server, RegionRequest request, ImagePlus imp) {
		this.request = request;
		// Store the server if we don't have an ImagePlus
		if (imp == null)
			this.server = ImagePlusServerBuilder.ensureImagePlusWholeSlideServer(server);
		this.pixelWidthMicrons = server.getPixelWidthMicrons() * request.getDownsample();
		this.pixelHeightMicrons = server.getPixelHeightMicrons() * request.getDownsample();
		this.imp = imp;
		if (imp != null && hasPixelSizeMicrons() && 
				!(GeneralTools.almostTheSame(pixelWidthMicrons, imp.getCalibration().pixelWidth, 0.0001) &&
						GeneralTools.almostTheSame(pixelHeightMicrons, imp.getCalibration().pixelHeight, 0.0001))) {
				logger.warn("Warning!  The pixel widths & heights calculated from the server & region request do not match the ImagePlus calibration - ImagePlus will be recalibrated");
				IJTools.calibrateImagePlus(imp, request, server);
		}
	}
	
	public void removeCachedImagePlus() {
		this.imp = null;
	}
	
//	protected void setImagePlusOrigin(ImagePlus imp) {
//		if (imp == null || request == null)
//			return;
//		double downsampleFactor = getDownsampleFactor();
////		IJ.log("Computed downsample factor: " + downsampleFactor);
//		Calibration cal = imp.getCalibration();
//		cal.xOrigin = -bounds.x / downsampleFactor;
//		cal.yOrigin = -bounds.y / downsampleFactor;
//	}
	
	@Override
	public String getImageTitle() {
		if (hasCachedImage())
			return imp.getTitle();
		else
			return "Untitled"; // TODO: RETURN TITLE BASED ON CACHED IMAGE OR PATH
	}
	
	@Override
	public ImagePlus getImage() {
		return getImage(true);
	}
	
	
	@Override
	public double getDownsampleFactor() {
		return request.getDownsample();
	}


	@Override
	public boolean hasCachedImage() {
		return imp != null;
	}
	
	@Override
	public ImagePlus getImage(boolean cache) {
//		System.out.println("IMAGE IS REQUESTED NOW");
		if (imp != null)
			return imp;
		else if (request != null) {
			ImagePlus impTemp;
			// Create a server if necessary, or use one if possible
			if (server == null) {
				ImagePlusServer server = new ImagePlusServerBuilder().buildServer(request.getPath());
				impTemp = server.readImagePlusRegion(request).getImage();
				server.close();
			} else
				impTemp = server.readImagePlusRegion(request).getImage();
			if (cache)
				imp = impTemp;
			return impTemp;
		}
		return null;
	}
	
	public Calibration getCalibration() {
		return getImage().getCalibration();
	}
	
	@Override
	public boolean validateSquarePixels() {
		Calibration cal = getCalibration();
		return Math.abs(cal.pixelWidth - cal.pixelHeight) / cal.pixelWidth < 0.001;
	}
	
	public static boolean calibrationMicrons(Calibration cal) {
		if (cal == null)
			return false;
		String unit = cal.getUnit().toLowerCase();
		if (unit.startsWith(GeneralTools.micrometerSymbol()) || unit.equals("um") || unit.startsWith("micron"))
			return true;
		return false;
	}
	
	@Override
	public double getPixelWidthMicrons() {
		if (Double.isNaN(pixelWidthMicrons))
			if (calibrationMicrons(getCalibration()))
				return getCalibration().pixelWidth;
			else
				return Double.NaN;
		else
			return pixelWidthMicrons;
	}

	@Override
	public double getPixelHeightMicrons() {
		if (Double.isNaN(pixelHeightMicrons)) {
			if (calibrationMicrons(getCalibration()))
				return getCalibration().pixelHeight;
			else
				return Double.NaN;
		}
		else
			return pixelHeightMicrons;
	}

	@Override
	public boolean hasPixelSizeMicrons() {
		return !Double.isNaN(getPixelWidthMicrons() + getPixelHeightMicrons());
	}

	@Override
	public ImageRegion getImageRegion() {
		return request;
	}
	
}
