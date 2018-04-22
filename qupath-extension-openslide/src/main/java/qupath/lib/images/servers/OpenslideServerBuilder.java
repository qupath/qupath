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

package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.regions.RegionRequest;

/**
 * Builder for Openslide ImageServer.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenslideServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(OpenslideServerBuilder.class);
	private static boolean openslideUnavailable = false;

	@Override
	public ImageServer<BufferedImage> buildServer(String path, Map<RegionRequest, BufferedImage> cache) {
		if (openslideUnavailable) {
			logger.debug("OpenSlide is unavailable - will be skipped");
			return null;
		}
		try {
			return new OpenslideImageServer(cache, path);
		} catch (UnsatisfiedLinkError e) {
			logger.error("Could not load OpenSlide native library", e);
			// Log that we couldn't create the link
			openslideUnavailable = true;
		} catch (Exception e) {
			logger.warn("Unable to open {} with OpenSlide: {}", path, e.getLocalizedMessage());
		}
		return null;
	}

	@Override
	public float supportLevel(String path, ImageCheckType type, Class<?> cls) {
		if (cls != BufferedImage.class)
			return 0;
		switch (type) {
		case TIFF_2D_RGB:
			return 3.5f;
		case TIFF_IMAGEJ:
			return 1;
		case TIFF_OTHER:
			return 1;
		case UNKNOWN:
			return 3;
		case URL:
			return 0;
		default:
			return 2;
		}
	}
	
	@Override
	public String getName() {
		return "OpenSlide Builder";
	}

	@Override
	public String getDescription() {
		return "Provides basic access to whole slide image formats supported by OpenSlide - see http://openslide.org";
	}
	
}