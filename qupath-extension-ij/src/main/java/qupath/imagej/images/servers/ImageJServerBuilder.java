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
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.regions.RegionRequest;

/**
 * Builder for ImageServers that use ImageJ to read images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageJServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(ImageJServerBuilder.class);

	@Override
	public ImageServer<BufferedImage> buildServer(String path, Map<RegionRequest, BufferedImage> cache) {
		try {
			return new ImageJServer(path);
		} catch (IOException e) {
			logger.warn("Error opening {} with ImageJ: {}", path, e.getLocalizedMessage());
		}
		return null;
	}

	@Override
	public float supportLevel(String path, ImageCheckType type, Class<?> cls) {
		if (cls != BufferedImage.class)
			return 0;
		switch (type) {
		case TIFF_2D_RGB:
			return 1;
		case TIFF_IMAGEJ:
			return 4;
		case TIFF_OTHER:
			return 1;
		case UNKNOWN:
			return 2;
		case URL:
			return 0;
		default:
			return 2;
		}
	}
	
	
	@Override
	public String getName() {
		return "ImageJ Builder";
	}

	@Override
	public String getDescription() {
		return "Reads images using ImageJ's default methods - best for TIFF files originally written by ImageJ, with calibration data available";
	}

}