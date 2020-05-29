/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.images.writers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * Static methods to access {@link ImageWriter} objects and write images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageWriterTools {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageWriterTools.class);
	
	@SuppressWarnings("rawtypes")
	private static ServiceLoader<ImageWriter> serviceLoader = ServiceLoader.load(ImageWriter.class);
	
	/**
	 * Get a list of compatible ImageWriters compatible with a specific server.
	 * 
	 * @param <T>
	 * @param server
	 * @param ext
	 * @return
	 * {@link #getCompatibleWriters(Class, String)}
	 */
	public static <T> List<ImageWriter<T>> getCompatibleWriters(final ImageServer<T> server, final String ext) {
		if (server == null)
			return getCompatibleWriters((Class<T>)null, ext);
		var writers = getCompatibleWriters(server.getImageClass(), ext);
		return writers.stream().filter(w -> w.supportsImageType(server)).collect(Collectors.toList());
	}
	
	/**
	 * Get a list of compatible ImageWriters for a specific image class.
	 * <p>
	 * The returned list is sorted, with the 'preferred' server coming first. 
	 * This decision is made based upon whether the writer supports pixel calibration information and 
	 * 
	 * @param <T>
	 * @param imageClass the image class (e.g. BufferedImage.class)
	 * @param ext the desired output file extension (e.g. ".jpg", ".tif").
	 * @return
	 * {@link #getCompatibleWriters(ImageServer, String)}
	 */
	public static <T> List<ImageWriter<T>> getCompatibleWriters(final Class<T> imageClass, final String ext) {
		String ext2;
		if (ext == null)
			ext2 = null;
		else {
			ext2 = ext.trim();
			ext2 = ext2.startsWith(".") ? ext2.substring(1) : ext2;
		}
		List<ImageWriter<T>> writers = new ArrayList<>();
		synchronized(serviceLoader) {
			for (ImageWriter<T> writer : serviceLoader) {
				if (imageClass == null || imageClass.equals(writer.getImageClass())) {
					if (ext2 == null || writer.getExtensions().contains(ext2))
						writers.add(writer);				
				}
			}
		}
		Collections.sort(writers, COMPARATOR);
		return writers;
	}
	
	
	/**
	 * Write a 2D image region using the default writer based on the file path.
	 * @param server the image to write
	 * @param request region to write; if null, the default plane of the entire image will be written
	 * @param path the file path; the extension will be used to identify an appropriate writer
	 * @return
	 * @throws IOException
	 */
	public static boolean writeImageRegion(final ImageServer<BufferedImage> server, final RegionRequest request, final String path) throws IOException {
		String ext = GeneralTools.getExtension(new File(path)).orElse(null);
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(server, ext);
		
		// If we have a path, use the 'best' writer we have, i.e. the first one that supports pixel sizes
		for (ImageWriter<BufferedImage> writer : compatibleWriters) {
			try {
				writer.writeImage(server, request, path);
				return true;
			} catch (Exception e) {
				logger.warn("Unable to write image", e);
			}
		}
		throw new IOException("Unable to write " + path + "!  No compatible writer found.");
	}
	
	/**
	 * Write a 2D image using the default writer based on the file path.
	 * @param img
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static boolean writeImage(final BufferedImage img, final String path) throws IOException {
		String ext = GeneralTools.getExtension(new File(path)).orElse(null);
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(
				new WrappedBufferedImageServer(UUID.randomUUID().toString(), img), ext);
		
		// If we have a path, use the 'best' writer we have, i.e. the first one that supports pixel sizes
		for (ImageWriter<BufferedImage> writer : compatibleWriters) {
			try {
				writer.writeImage(img, path);
				return true;
			} catch (Exception e) {
				logger.warn("Unable to write image", e);
			}
		}
		throw new IOException("Unable to write " + path + "!  No compatible writer found.");
	}

	/**
	 * Write a (possibly multidimensional) image region using the default writer based on the file path.
	 * @param server the image to write
	 * @param path the file path; the extension will be used to identify an appropriate writer
	 * @return
	 * @throws IOException
	 */
	public static boolean writeImage(final ImageServer<BufferedImage> server, final String path) throws IOException {
		String ext = GeneralTools.getExtension(new File(path)).orElse(null);
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(server, ext);
		
		// If we have a path, use the 'best' writer we have, i.e. the first one that supports pixel sizes
		for (ImageWriter<BufferedImage> writer : compatibleWriters) {
			try {
				writer.writeImage(server, path);
				return true;
			} catch (Exception e) {
				logger.warn("Unable to write image", e);
			}
		}
		throw new IOException("Unable to write " + path + "!  No compatible writer found.");
	}
	
	
	/**
	 * Comparator that prefers the most 'comprehensive/flexible' ImageWriter.
	 */
	private static Comparator<ImageWriter<?>> COMPARATOR = 
			Comparator.comparing(ImageWriter<?>::supportsPixelSize)
				.thenComparing(Comparator.comparing(ImageWriter::supportsZ))
				.thenComparing(Comparator.comparing(ImageWriter::supportsT))
				.thenComparing(Comparator.comparing(ImageWriter::supportsPyramidal))
				.thenComparing(Comparator.comparing(ImageWriter::supportsRGB))
				.thenComparing(Comparator.comparing(ImageWriter::getName));	

}