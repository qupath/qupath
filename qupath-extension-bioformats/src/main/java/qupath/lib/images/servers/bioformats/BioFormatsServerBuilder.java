/*-
 * #%L
 * This file is part of a QuPath extension.
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

package qupath.lib.images.servers.bioformats;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.net.URI;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.images.servers.bioformats.BioFormatsServerOptions.UseBioformats;

/**
 * Builder for ImageServers that make use of the Bio-Formats library.
 * 
 * @author Pete Bankhead
 *
 */
public class BioFormatsServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(BioFormatsServerBuilder.class);
	
	final private Map<URI, Float> lastSupportLevel = new HashMap<>();
	
	@Override
	public ImageServer<BufferedImage> buildServer(URI uri) {
		try {
			BioFormatsImageServer server = new BioFormatsImageServer(uri);
			return server;
		} catch (Exception e) {
			lastSupportLevel.put(uri, Float.valueOf(0f));
			logger.error("Unable to open {}: {}", uri, e);
		}
		return null;
	}

	@Override
	public float supportLevel(URI uri, ImageCheckType type, Class<?> cls) {
		// We only support BufferedImages
		if (cls != BufferedImage.class)
			return 0;
		
		// We also can't do anything if Bio-Formats isn't installed
		if (getBioFormatsVersion() == null)
			return 0;
		
		if (!"file".equals(uri.getScheme()))
			return 0;
				
		String path = uri.getPath();
		
		// Check the options to see whether we really really do or don't want to read this
		BioFormatsServerOptions options = BioFormatsServerOptions.getInstance();
		switch (checkPath(options, path)) {
			case YES:
				return 5;
			case NO:
				return 0;
			default:
				break;
		}
		
		// Avoid calculated support again if we don't have to
		Float lastSupport = lastSupportLevel.getOrDefault(uri, null);
		if (lastSupport != null)
			return lastSupport.floatValue();
		
		// We don't want to handle zip files (which are very slow)
		float support;
		if (path.toLowerCase().endsWith(".zip"))
			support = 1f;
		else {
			// Default to our normal checks
			switch (type) {
			case TIFF_2D_RGB:
				// Good support for .qptiff
				if (path.toLowerCase().endsWith(".qptiff"))
					support = 4f;
				support = 3f;
			case TIFF_IMAGEJ:
				support = 3f;
			case TIFF_OTHER:
				support = 2f;
			case UNKNOWN:
				support = 2f;
			case URL:
				support = 0f;
			default:
				support = 2f;
			}
		}
		
		lastSupportLevel.put(uri, Float.valueOf(support));
		return support;
	}

	@Override
	public String getName() {
		return "Bio-Formats builder";
	}

	@Override
	public String getDescription() {
		String bfVersion = getBioFormatsVersion();
		if (bfVersion == null)
			return "Image server using the Bio-Formats library - but it won't work because 'bioformats_package.jar' is missing.";
		else
			return "Image server using the Bio-Formats library (" + bfVersion + ")";
	}
	
	/**
	 * Request the Bio-Formats version number from {@code loci.formats.FormatTools.VERSION}.
	 * 
	 * This uses reflection, returning {@code null} if Bio-Formats cannot be found.
	 * 
	 * @return
	 */
	static String getBioFormatsVersion() {
		try {
			Class<?> cls = Class.forName("loci.formats.FormatTools");
			return (String)cls.getField("VERSION").get(null);	
		} catch (Exception e) {
			logger.error("Error requesting version from Bio-Formats", e);
			return null;
		}
	}
	
	/**
	 * Check whether Bio-Formats definitely should/shouldn't be used to try opening an image, 
	 * based upon its path and the supplied BioFormatsServerOptions.
	 * 
	 * @param options
	 * @param path
	 * @return
	 */
	static UseBioformats checkPath(final BioFormatsServerOptions options, final String path) {
		if (!options.bioformatsEnabled())
			return UseBioformats.NO;
		// Check if the file extension is one that we've been explicitly told to skip
		String pathLower = path.toLowerCase();
		for (String ext : options.getSkipAlwaysExtensions()) {
			if (pathLower.endsWith(ext.toLowerCase()))
				return UseBioformats.NO;
		}
		// Check if the file extension is one that we've been explicitly told to include
		for (String ext : options.getUseAlwaysExtensions()) {
			if (pathLower.endsWith(ext.toLowerCase()))
				return UseBioformats.YES;
		}
		// If we get here, it could go either way... need to check support for format
		return UseBioformats.MAYBE;
	}
	
}
