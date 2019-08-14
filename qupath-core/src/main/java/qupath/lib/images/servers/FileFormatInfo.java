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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;


/**
 * Helper class that, given a path, determines some basic file format information without enlisting
 * the help of an external library or performing extensive parsing of the data.
 * <p>
 * In particular, it attempts to extract some usable information from TIFF images using ImageIO to help 
 * a reader determine whether or not it should attempt to read the image.
 * 
 * @author Pete Bankhead
 *
 */
public class FileFormatInfo {
	
	final private static Logger logger = LoggerFactory.getLogger(FileFormatInfo.class);
	
	private static Map<URI, ImageCheckType> cache = new HashMap<>();
	
	/**
	 * Check the type of an image for a specified URI.
	 * <p>
	 * This will return a cached value if possible, to avoid applying (potentially costly) checks too often.
	 * @param uri
	 * @return
	 */
	public static synchronized ImageCheckType checkType(URI uri) {
		ImageCheckType check = cache.get(uri);
		if (check == null) {
			check = checkImageType(uri);
			cache.put(uri, check);
		}
		return check;
	}
	
	
	
	/**
	 * Interface defining some basic image metadata that may be extracted from an image file 
	 * to help ascertain whether an {@link ImageServerBuilder} can handle it.
	 */
	public static interface ImageCheckType {
		
		/**
		 * Return true if URI has a scheme beginning with http.
		 * @return
		 */
		public boolean isURL();
		
		/**
		 * Returns true if URI is for a local file known/expected to be a TIFF image.
		 * @return
		 */
		public boolean isTiff();
		
		/**
		 * Returns true if URI is for a local file known/expected to be a BigTIFF image.
		 * @return
		 */
		public boolean isBigTiff();
		
		/**
		 * Get the image description, as stored in a TIFF, or null if no description is available.
		 * @return
		 */
		public String getDescription();
		
		/**
		 * Get a File object representing the local image file - or null if no file could be found.
		 * @return
		 */
		public File getFile();
		
		/**
		 * Return an estimate of the number of images if known, or -1 if unknown.
		 * <p>
		 * Note that the maximum value returned may be less than the total number of images, if scanning the full 
		 * file risks being prohibitively expensive (this has been found to be the case with some non-standard TIFF 
		 * images).
		 * @return
		 */
		public int nImages();
				
		/**
		 * Return the number of images with the largest image size, if known, or -1 if unknown.
		 * <p>
		 * This can be used to help distinguish images that contain multiple images where each image is really 
		 * another z-slice, channel or time point - rather than a macro or label, for example.
		 * @return
		 */
		public int nImagesLargest();
		
		/**
		 * Returns true if we can say with reasonable confidence that the image is not RGB.
		 * <p>
		 * This information can be used to prevent readers that only support RGB images from trying to read this one.
		 * However, note that it is permissible to return false in cases where the RGB status of the image is unclear.
		 * @return
		 */
		public boolean isNotRGB();

	}
	
	static class DefaultFormatInfo implements ImageCheckType {
		
		private static int MAX_IMAGES = 100;
		
		private boolean isURL = false;
		private File file = null;
		
		private boolean isTiff = false;
		private boolean isBigTiff = false;
		
		private boolean notRGB = false;
		
		private int nImages = -1;
		private int nImagesLargest = -1;
		private String description;
		
		DefaultFormatInfo(URI uri) {
			String scheme = uri.getScheme();
			if (scheme != null && scheme.startsWith("http")) {
				isURL = true;
				return;
			}
			Path path = GeneralTools.toPath(uri);
			if (!Files.exists(path)) {
				return;
			}
			File file = path.toFile();
			if (!file.isFile()) {
				return;
			}
			this.file = file;
			
			// Check if we have a TIFF
			try {
				try (RandomAccessFile in = new RandomAccessFile(file, "r")) {				
					boolean littleEndian;
					int byteOrder = in.readShort();
					if (byteOrder == 0x4949) // "II"
						littleEndian = true;
					else if (byteOrder == 0x4d4d) // "MM"
						littleEndian = false;
					else {
						in.close();
						return;
					}
					
					// Check if standard (key: 42) or BigTiff (key: 43)
					int special = readShort(in, littleEndian);
					if (special == 42)
						isTiff = true;
					else if (special == 43) {
						isTiff = true;
						isBigTiff = true;
					} else
						return;
				}
				

				// If we do have a TIFF, try to get more from the metadata
				
				try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
					ImageReader reader = ImageIO.getImageReaders(stream).next();
					reader.setInput(stream);

					TIFFDirectory tiffDir = TIFFDirectory.createFromMetadata(reader.getImageMetadata(0));
					TIFFField tempDescription = tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_DESCRIPTION);
					description = tempDescription == null ? null : tempDescription.getAsString(0);

					nImages = reader.getNumImages(false);
					int nTestImages = nImages >= 0 ? nImages : MAX_IMAGES;

					if (nTestImages > 0) {
						int i = 0;
						try {
							int largestCount = 0;
							long maxNumPixels = 0L;
							boolean largestMaybeRGB = false;
							while (i < nTestImages) {
								long nPixels = (long)reader.getWidth(i) * (long)reader.getHeight(i);
								ImageTypeSpecifier specifier = reader.getRawImageType(i);
								if (nPixels > maxNumPixels) {
									maxNumPixels = nPixels;
									largestCount = 1;
									largestMaybeRGB = maybeRGB(specifier);
								} else if (nPixels == maxNumPixels) {
									largestCount++;
									largestMaybeRGB = largestMaybeRGB && maybeRGB(specifier);
								}
								i++;
								nImagesLargest = largestCount;
								notRGB = !largestMaybeRGB;
							}
						} catch (IndexOutOfBoundsException e) {
							nImages = i - 1;
							logger.debug("Checked first {} images only of {}", i-1, uri);
						}
					}
				}
				
			} catch (Exception e) {
				logger.warn("Unable to obtain full image format info for {} ({})", uri, e.getLocalizedMessage() == null ? e.getClass() : e.getLocalizedMessage());
				logger.debug("Format info error", e);
			}
		}

		@Override
		public boolean isURL() {
			return isURL;
		}
		
		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public boolean isTiff() {
			return isTiff;
		}

		@Override
		public boolean isBigTiff() {
			return isBigTiff;
		}

		@Override
		public File getFile() {
			return file;
		}

		@Override
		public int nImages() {
			return nImages;
		}
		
		@Override
		public int nImagesLargest() {
			return nImagesLargest;
		}

		@Override
		public boolean isNotRGB() {
			return notRGB;
		}
		
		@Override
		public String toString() {
			return "DefaultFormatInfo [isURL=" + isURL + ", file=" + file + ", isTiff=" + isTiff + ", isBigTiff="
					+ isBigTiff + ", notRGB=" + notRGB + ", nImages=" + nImages + ", nImagesLargest=" + nImagesLargest
					+ ", description=" + Boolean.toString(description != null) + "]";
		}
		
	}
	
	/**
	 * Try to determine from a specifier whether it might refer to an RGB image or not.
	 * @param specifier
	 * @return
	 */
	static boolean maybeRGB(ImageTypeSpecifier specifier) {
		int nBands = specifier.getNumBands();
		if (nBands < 3 || nBands > 4)
			return false;
		for (int i = 0; i < nBands; i++) {
			if (specifier.getBitsPerBand(i) != 8)
				return false;
		}
		return true;
	}
	
	static ImageCheckType checkImageType(final URI uri) {
		return new DefaultFormatInfo(uri);
	}
	
	static int readShort(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (littleEndian)
			return ((b2<<8) + b1);
		else
			return ((b1<<8) + b2);
	}
	
}