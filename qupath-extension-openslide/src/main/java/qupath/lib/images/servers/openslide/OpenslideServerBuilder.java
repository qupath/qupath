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

package qupath.lib.images.servers.openslide;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.openslide.OpenSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;

/**
 * Builder for Openslide ImageServer.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenslideServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(OpenslideServerBuilder.class);
	private static boolean openslideUnavailable = false;
	
	private static List<String> WIN_LIBRARIES = Arrays.asList(
			"iconv",
			"libjpeg-62",
			"libsqlite3-0",
			"libpixman-1-0",
			"libffi-6",
			"zlib1",
			"libxml2-2",
			"libopenjp2",
			"libpng16-16",
			"libtiff-5",
			"libintl-8",
			"libglib-2.0-0",
			"libgobject-2.0-0",
			"libcairo-2",
			"libgmodule-2.0-0",
			"libgio-2.0-0",
			"libgthread-2.0-0",
			"libgdk_pixbuf-2.0-0",
			"libopenslide-0"
			);
	
	static {
		try {
			// Try loading OpenSlide-JNI - hopefully there is a version of OpenSlide on the PATH we should use
			System.loadLibrary("openslide-jni");
		} catch (UnsatisfiedLinkError e) {
			try {
				// If we didn't succeed, try loading dependencies in reverse order
				logger.debug("Couldn't load OpenSlide directly, attempting to load dependencies first...");
				for (var lib : WIN_LIBRARIES) {
					System.loadLibrary(lib);
				}
			} catch (UnsatisfiedLinkError e2) {}
		}
		try {
			// Finally try to get the library version
			logger.info("OpenSlide version {}", OpenSlide.getLibraryVersion());
		}
		catch (UnsatisfiedLinkError e) {
			logger.error("Could not load OpenSlide native libraries", e);
			logger.info("If you want to use OpenSlide, you'll need to get the native libraries (either building from source or with a packager manager)\n" +
			"and add them to your system PATH, including openslide-jni.");
			openslideUnavailable = true;
		}
	}
	
	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		if (openslideUnavailable) {
			logger.debug("OpenSlide is unavailable - will be skipped");
			return null;
		}
		try {
			return new OpenslideImageServer(uri, args);
		} catch (Exception e) {
			logger.warn("Unable to open {} with OpenSlide: {}", uri, e.getLocalizedMessage());
		}
		return null;
	}
	
	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		return UriImageSupport.createInstance(this.getClass(), supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
	}

	private static float supportLevel(URI uri, String...args) {
		if (openslideUnavailable)
			return 0;
		
		// Don't handle queries or fragments with OpenSlide
		ImageCheckType type = FileFormatInfo.checkType(uri);
		if (type.isURL() || type.getFile() == null)
			return 0;
		
		try {
			File file = new File(uri);
			String vendor = OpenSlide.detectVendor(file);
			if (vendor == null)
				return 0;
		} catch (Exception e) {
			logger.debug("Unable to read with OpenSlide: {}", e.getLocalizedMessage());
		}
		
		// We can only handle RGB images with OpenSlide... so if we don't think it's RGB, use only as a last resort
		if (type.isNotRGB())
			return 1f;
		
		// We're pretty good on 2D RGB, not great if we have more images
		if (type.nImagesLargest() == 1)
			return 3.5f;
		else if (type.nImagesLargest() == -1)
			return 2.5f;
		else
			return 1f;
	}
	
	@Override
	public String getName() {
		return "OpenSlide builder";
	}

	@Override
	public String getDescription() {
		return "Provides basic access to whole slide image formats supported by OpenSlide - see http://openslide.org";
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
					OpenslideImageServer.class.getName().equals(className) ||
					OpenslideImageServer.class.getSimpleName().equals(className) ||
					"openslide".equalsIgnoreCase(className))
				return true;			
		}
		return false;
	}
	
}