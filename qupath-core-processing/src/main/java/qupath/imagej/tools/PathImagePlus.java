/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.imagej.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.measure.Calibration;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * PathImage implementation backed by an ImageJ ImagePlus object.
 * 
 * @author Pete Bankhead
 *
 */
class PathImagePlus implements PathImage<ImagePlus> {
	
	final private static Logger logger = LoggerFactory.getLogger(PathImagePlus.class);
	
	transient private ImagePlus imp = null;
	private RegionRequest request;
	
	private PixelCalibration calibration;

	
	/**
	 * Create a PathImage for which the image will be read lazily.
	 * 
	 * @param request
	 * @param imp
	 */
	PathImagePlus(RegionRequest request, ImagePlus imp) {
		this.request = request;		
		this.imp = imp;
		logger.trace("Creating PathImage for {} and request {}", imp, request);
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
	public double getDownsampleFactor() {
		return request.getDownsample();
	}
	
	@Override
	public ImagePlus getImage() {
		return imp;
	}
	
	@Override
	public PixelCalibration getPixelCalibration() {
		if (calibration == null) {
			var builder =  new PixelCalibration.Builder();
			Calibration cal = getImage().getCalibration();
			if (isMicrons(cal.getXUnit()) && isMicrons(cal.getYUnit())) {
				builder.pixelSizeMicrons(cal.pixelWidth, cal.pixelHeight);
			}
			if (isMicrons(cal.getZUnit())) {
				builder.zSpacingMicrons(cal.pixelDepth);				
			}
			calibration = builder.build();
		}
		return calibration;
	}
	
	private static boolean isMicrons(String unit) {
		if (unit == null)
			return false;
		unit = unit.toLowerCase();
		if (unit.startsWith(GeneralTools.micrometerSymbol()) || unit.equals("um") || unit.startsWith("micron"))
			return true;
		return false;
	}

	@Override
	public ImageRegion getImageRegion() {
		return request;
	}
	
}
