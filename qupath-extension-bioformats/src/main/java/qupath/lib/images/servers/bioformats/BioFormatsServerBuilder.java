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
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.FileFormatInfo;
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
	
	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		try {
			BioFormatsImageServer server = new BioFormatsImageServer(uri, args);
			return server;
		} catch (Exception e) {
			logger.error("Unable to open {}: {}", uri, e);
		}
		return null;
	}

	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) throws IOException {
		float supportLevel = supportLevel(uri, args);
		if (supportLevel > 0) {
			try (BioFormatsImageServer server = new BioFormatsImageServer(uri, BioFormatsServerOptions.getInstance(), args)) {
				// If we requested a specified series, just allow one builder
				Map<String, ServerBuilder<BufferedImage>> builders;
				if (args.length > 0 && Arrays.asList(args).contains("--series"))
					builders = Collections.singletonMap(server.getMetadata().getName(), server.getBuilder());
				else
					// If we didn't specify a series, return all of them
					builders = server.getImageBuilders();
				if ("OME-TIFF".equals(server.getFormat()))
					supportLevel = 5f;
				// If the image is large but not pyramidal, decrease support - maybe another server can find a pyramid
				if (server.nResolutions() == 1) {
					long nPixels = (long)server.getWidth() * (long)server.getHeight();
					long nBytes = nPixels * server.nChannels() * server.getMetadata().getPixelType().getBytesPerPixel();
					if (nPixels >= Integer.MAX_VALUE || nBytes > Runtime.getRuntime().maxMemory()/2)
						supportLevel = 1;
				}
				return UriImageSupport.createInstance(this.getClass(), supportLevel, builders.values());
			} catch (Exception e) {
				logger.debug("Unable to create server using Bio-Formats", e);
			}
		}
		return null;
	}
	
	private static float supportLevel(URI uri, String... args) {
		
		// We also can't do anything if Bio-Formats isn't installed
		if (getBioFormatsVersion() == null)
			return 0;
		
		ImageCheckType type = FileFormatInfo.checkType(uri);
		if (type.isURL())
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
		
		path = path.toLowerCase();
		
		// We don't want to handle zip files (which are very slow)
		float support = 3f;
				
		String description = type.getDescription();
		if (path.endsWith(".zip"))
			support = 1f;
		else if (type.isTiff()) {
			// Some nasty files seem to be larger than they think they are - which can be troublesome
			if (!type.isBigTiff() && type.getFile().length() >= 1024L * 1024L * 1024L * 4L) {
				support = 2f;
			}
			if (description != null) {
				if (description.contains("<OME "))
					support = 5f;
				if (description.contains("imagej"))
					support = 3.5f;
				if (path.endsWith(".qptiff"))
					support = 3.5f;
			}
			// Handle ome.tif extensions... necessary because micromanager can include an ImageJ ImageDescription
			// despite also containing OME metadata
			if (path.endsWith(".ome.tif") || path.endsWith(".ome.tiff"))
				support = 5f;
		} else {
			// Check if we know the file type
			File file = type.getFile();
			if (file != null) {
				String supportedReader = null;
				try {
					supportedReader = BioFormatsImageServer.getSupportedReaderClass(file.getAbsolutePath());
				} catch (Exception e) {
					logger.warn("Error checking file " + file.getAbsolutePath(), e);
				}
				if (supportedReader == null) {
					logger.debug("No supported reader found for {}", file.getAbsolutePath());
					return 1f;
				} 
				logger.debug("Potential Bio-Formats reader: {}", supportedReader);
			}
		}
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
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}
	
	/**
	 * Request the Bio-Formats version number from {@code loci.formats.FormatTools.VERSION}.
	 * <p>
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
