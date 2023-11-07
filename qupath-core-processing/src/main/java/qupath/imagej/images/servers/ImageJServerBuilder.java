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

package qupath.imagej.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ij.IJ;
import ij.io.Opener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
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

	/**
	 * Check if image is a type that ImageJ won't be able to read
	 */
	private static Set<String> UNSUPPORTED_FORMATS = IntStream.of(
		Opener.UNKNOWN, Opener.JAVA_OR_TEXT, Opener.ROI, Opener.TABLE, Opener.TEXT
				).mapToObj(i -> Opener.types[i]).collect(Collectors.toSet());

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
		
		// Check if the image is too large
		long width = type.getLargestImageWidth();
		long height = type.getLargestImageHeight();
		if (width > 0 && height > 0 && width * height >= Integer.MAX_VALUE)
			return 0;

		var path = GeneralTools.toPath(uri);
		if (path == null)
			return 0;
		else {
			// We can't use ImageJ to read ROIs, tables, etc.
			String format = Opener.getFileFormat(path.toAbsolutePath().toString());
			var unsupportedFormats = IntStream.of(
					Opener.UNKNOWN, Opener.JAVA_OR_TEXT, Opener.ROI, Opener.TABLE, Opener.TEXT
			).mapToObj(i -> Opener.types[i]).collect(Collectors.toSet());
			if (unsupportedFormats.contains(format))
				return 0f;
			else
				return 1f;
		}
	}
	
	@Override
	public String getName() {
		return "ImageJ builder";
	}

	@Override
	public String getDescription() {
		return "Read images using ImageJ's default methods - best for TIFF files originally written by ImageJ, with calibration data available";
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}
	
	@Override
	public boolean matchClassName(String... classNames) {
		for (var className : classNames) {
			if (this.getClass().getName().equals(className) ||
					this.getClass().getSimpleName().equals(className) ||
					ImageJServer.class.getName().equals(className) ||
					ImageJServer.class.getSimpleName().equals(className) ||
					"imagej".equalsIgnoreCase(className))
				return true;			
		}
		return false;
	}

}