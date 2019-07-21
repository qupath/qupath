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
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;

/**
 * Builder for ImageServers that use ImageJ to read images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageJServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(ImageJServerBuilder.class);

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		try {
			return new ImageJServer(uri, args);
		} catch (IOException e) {
			logger.debug("Unable to open {} with ImageJ: {}", uri, e.getLocalizedMessage());
		}
		return null;
	}

	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		return UriImageSupport.createInstance(this.getClass(), supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), null, uri, args));
	}
	
	private float supportLevel(URI uri, String...args) {
		
		ImageCheckType type = FileFormatInfo.checkType(uri);
		String description = type.getDescription();
		if (description != null && description.toLowerCase().contains("imagej="))
			return 4;
		
		if (type.isURL())
			return 0;
		
		return 1;
	}
	
	@Override
	public String getName() {
		return "ImageJ Builder";
	}

	@Override
	public String getDescription() {
		return "Read images using ImageJ's default methods - best for TIFF files originally written by ImageJ, with calibration data available";
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}

}