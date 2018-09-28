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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.regions.RegionRequest;

/**
 * Buidler for ImageServer using Java's ImageIO.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageIoImageServerBuilder implements ImageServerBuilder<BufferedImage> {

	@Override
	public float supportLevel(String path, ImageCheckType info, Class<?> cls) {
		if (cls != BufferedImage.class)
			return 0;
		switch(info) {
		case TIFF_2D_RGB:
			return 1;
		case TIFF_IMAGEJ:
			return 1;
		case TIFF_OTHER:
			return 1;
		case UNKNOWN:
			return 1;
		case URL:
			return 1;
		default:
			break;
		}
		return 1;
	}

	@Override
	public ImageServer<BufferedImage> buildServer(String path, Map<RegionRequest, BufferedImage> cache) throws MalformedURLException, IOException {
		return new ImageIoImageServer(path);
	}

	@Override
	public String getName() {
		return "ImageIO Builder";
	}

	@Override
	public String getDescription() {
		return "Provides basic access to file formats supported by Java's ImageIO";
	}
	
	
}
