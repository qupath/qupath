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
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.io.FileInfo;
import qupath.lib.common.URLTools;
//import qupath.lib.images.servers.BioformatsImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;

/**
 * Builder for ImageServers that are capable of returning ImagePlus objects.
 * 
 * @author Pete Bankhead
 *
 */
public class ImagePlusServerBuilder {
	
	final private static Logger logger = LoggerFactory.getLogger(ImagePlusServerBuilder.class);
	
	public static ImagePlusServer ensureImagePlusWholeSlideServer(ImageServer<BufferedImage> server) {
		if (server instanceof ImagePlusServer)
			return (ImagePlusServer)server;
//		else if (server instanceof BioformatsImageServer)
//			return new ImagePlusBioformatsServer((BioformatsImageServer)server); // TODO: Consider bringing this back! Was useful to ensure BioFormats took care of its own ImagePlus creation
		else
			return new BufferedImagePlusServer(server);
	}
	

	public ImagePlusServer buildWholeSlideServer(ImagePlus imp) {
		return buildServer(getPathFromImagePlus(imp));
	}
	
	public ImagePlusServer buildServer(String path) {
		if (path == null)
			return null;
		// Try making a Bioformats server
		try {
			return ensureImagePlusWholeSlideServer(ImageServerProvider.buildServer(path, BufferedImage.class));
		} catch (Exception e) {
			logger.error("Unable to create Bioformats server for {}", path);
		}
		return null;
	}
		
	public static String getPathFromImagePlus(ImagePlus imp) {
		String path = getURLFromImagePlus(imp);
		if (path == null)
			return getFilePathFromImagePlus(imp);
		else
			return path;
	}
	
	private static String getFilePathFromImagePlus(ImagePlus imp) {
		// Try to get path first from image info property
		// (The info property should persist despite duplication, but the FileInfo probably doesn't)
		String info = imp.getInfoProperty();
		String path = null;
		if (info != null) {
			for (String s : info.split("\n")) {
				if (s.toLowerCase().startsWith("location")) {
					path = s.substring(s.indexOf('=')+1).trim();
					break;
				}
			}
		}// If we haven't got a path yet, try the FileInfo
		if (path == null) {
			// Check the file info
			FileInfo fi = imp.getOriginalFileInfo();
			if (fi == null)
				return null;
			path = fi.directory + fi.fileName;
		}
		File file = new File(path);
		if (file.exists())
			return file.getAbsolutePath();
		return null;
	}
	
	private static String getURLFromImagePlus(ImagePlus imp) {
		// Check the file info first
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi == null)
			return null;
		if (fi.url != null && URLTools.checkURL(fi.url))
			return fi.url;
		// Check the image info property
		// (The info property should persist despite duplication, but the FileInfo probably doesn't)
		String info = imp.getInfoProperty();
		if (info != null) {
			for (String s : info.split("\n")) {
				if (s.toLowerCase().startsWith("url")) {
					String url = s.substring(s.indexOf('=')+1).trim();
					if (URLTools.checkURL(url))
						return url;
				}
				if (s.toLowerCase().startsWith("location")) {
					String url = s.substring(s.indexOf('=')+1).trim();
					if (URLTools.checkURL(url))
						return url;
				}
			}
		}
		return null;
	}
	
}
