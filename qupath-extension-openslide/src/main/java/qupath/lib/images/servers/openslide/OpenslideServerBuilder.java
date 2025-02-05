/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.LogTools;
import qupath.lib.images.servers.FileFormatInfo;
import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.openslide.jna.OpenSlideLoader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

/**
 * Builder for Openslide ImageServer.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenslideServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenslideServerBuilder.class);

	/**
	 * Flag that we failed to load OpenSlide before, and should not continue trying.
	 */
	private boolean failedToLoad = false;

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		if (!OpenSlideLoader.isOpenSlideAvailable() && !OpenSlideLoader.tryToLoadQuietly()) {
			logger.debug("OpenSlide is unavailable - will be skipped");
			return null;
		}
		try {
			return new OpenslideImageServer(uri, args);
		} catch (Exception e) {
			logger.error("Unable to open {} with OpenSlide: {}", uri, e.getMessage(), e);
		} catch (NoClassDefFoundError e) {
			logger.warn("OpenSlide library not found!");
			logger.debug(e.getMessage(), e);
		}
		return null;
	}
	
	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		return UriImageSupport.createInstance(this.getClass(), supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
	}

	private float supportLevel(URI uri, String...args) {
		if (!OpenSlideLoader.isOpenSlideAvailable() && !failedToLoad && !OpenSlideLoader.tryToLoadQuietly()) {
			failedToLoad = true;
			return 0;
		}
		
		// Don't handle queries or fragments with OpenSlide
		ImageCheckType type = FileFormatInfo.checkType(uri);
		if (type.isURL() || type.getFile() == null)
			return 0;
		
		try {
			File file = Paths.get(uri).toFile().getCanonicalFile();
			String vendor = OpenSlideLoader.detectVendor(file.toString());
			if (vendor == null)
				return 0;
		} catch (Exception e) {
			logger.debug("Unable to read with OpenSlide: {}", e.getLocalizedMessage());
		} catch (UnsatisfiedLinkError e) {
			LogTools.warnOnce(logger, "OpenSlide is not available (" + e.getLocalizedMessage() + ")");
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
		return "Provides basic access to whole slide image formats supported by OpenSlide - see https://openslide.org";
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
