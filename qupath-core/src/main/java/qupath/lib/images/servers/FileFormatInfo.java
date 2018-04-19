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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.URLTools;


/**
 * Helper class that, given a path, determines some basic file format information without enlisting
 * the help of an external library or performing extensive parsing of the data.
 * <p>
 *  investigates whether a file is:
 * 	 - a TIFF file at all
 * 	 - an ImageJ TIFF
 *   - some other kind of 2D, RGB TIFF
 *   - some other kind of potentially multidimensional TIFF
 * <p>
 * To improve the likelihood of accurate metadata parsing &amp; all dimensions being present,
 * it is better to choose a the right ImageServer.  For example ImageJ should be used for
 * ImageJ TIFFs, while OpenSlide should only be used for TIFFs (or TIFF-based formats) that
 * are 2D and RGB.
 * <p>
 * The code is based on a (very much stripped down and adapted) version of ImageJ's TiffDecoder
 * (i.e. it was written while looking at that code, and the Tiff specifications at
 * https://partners.adobe.com/public/developer/en/tiff/TIFF6.pdf and
 * http://bigtiff.org )
 * 
 * @author Pete Bankhead
 *
 */
public class FileFormatInfo {
	
	public enum ImageCheckType {UNKNOWN, URL, TIFF_IMAGEJ, TIFF_2D_RGB, TIFF_OTHER};
	
	final private static Logger logger = LoggerFactory.getLogger(FileFormatInfo.class);
	
	static final int IMAGE_WIDTH = 256;
	static final int IMAGE_LENGTH = 257;
	static final int BITS_PER_SAMPLE = 258;
	static final int EXTRA_SAMPLES = 338;
	static final int COMPRESSION = 259;
	static final int PHOTO_INTERP = 262;
	static final int IMAGE_DESCRIPTION = 270;
	static final int SAMPLES_PER_PIXEL = 277;
	static final int SOFTWARE = 305;
	static final int SAMPLE_FORMAT = 339;

	
	public static ImageCheckType checkImageType(final String path) {
		
		if (URLTools.checkURL(path))
			return ImageCheckType.URL;
		
		File file = new File(path);
		if (!file.exists())
			return ImageCheckType.UNKNOWN;
		
		RandomAccessFile in = null;
		FileFormatInfo.ImageCheckType type = null;
		try {
			in = new RandomAccessFile(path, "r");
			
			boolean littleEndian;
			int byteOrder = in.readShort();
			if (byteOrder == 0x4949) // "II"
				littleEndian = true;
			else if (byteOrder == 0x4d4d) // "MM"
				littleEndian = false;
			else {
				in.close();
				return ImageCheckType.UNKNOWN;
			}
			
			// Check if standard (key: 42) or BigTiff (key: 43)
			int special = readShort(in, littleEndian);
			if (special == 42)
				type = checkStandardTiff(in, littleEndian);
			else if (special == 43)
				type = checkBigTiff(in, littleEndian); 
			else
				type = ImageCheckType.UNKNOWN;
			
		} catch (Exception e) {
			e.printStackTrace();
			return ImageCheckType.UNKNOWN;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return type;
	}
	
	
	static ImageCheckType checkStandardTiff(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		long offset = readUnsignedInt(in, littleEndian);
		if (offset < 0)
			return ImageCheckType.UNKNOWN;
		
		List<Long> nPixels = new ArrayList<>();
		while (offset > 0L && offset < in.length()) {
			in.seek(offset);
			
			long width = -1;
			long height = -1;
			long samplesPerPixel = -1;
			int[] bitsPerSample = null;
			int extraSamples = 0;

			
			int nEntries = readShort(in, littleEndian);
			for (int i = 0; i < nEntries; i++) {
				int tag = readShort(in, littleEndian);
				readShort(in, littleEndian);
				long count = readUnsignedInt(in, littleEndian);
				long valueOrOffset = readUnsignedInt(in, littleEndian);
				
				switch (tag) {
				case IMAGE_DESCRIPTION:
					String s = getString(in, (int)count, valueOrOffset);
					if (s.toLowerCase().contains("imagej"))
						return ImageCheckType.TIFF_IMAGEJ;
					break;
				case IMAGE_WIDTH:
					width = valueOrOffset;
					break;
				case IMAGE_LENGTH:
					height = valueOrOffset;
					break;
				case SAMPLES_PER_PIXEL:
					samplesPerPixel = valueOrOffset;
					break;
				case BITS_PER_SAMPLE:
					if (count==1) {
						// TODO: Check this... could be quite questionable...
						int val = littleEndian ? (int)((valueOrOffset & 0x00ff0000) >> 16) : (int)((valueOrOffset & 0xff000000) >> 24);
						if (val > 64 || val <= 0)
							logger.warn("Strange 'bits per sample' of {}", val);
						bitsPerSample = new int[]{val};
//						bitsPerSample = new int[]{readShort(in, littleEndian)};
					} else if (count > 1) {
						long loc = in.getFilePointer();
						in.seek(valueOrOffset);
						bitsPerSample = new int[(int)count];
						for (int b = 0; b < count; b++)
							bitsPerSample[b] = readShort(in, littleEndian);
						in.seek(loc);
					}
					break;
				case EXTRA_SAMPLES:
					extraSamples = readShort(in, littleEndian);
					break;
				}
			}
			
			// If we have read a valid image, check if it could be RGB
			if (samplesPerPixel > 0) {
				if (!(samplesPerPixel - extraSamples == 3 && (bitsPerSample != null && bitsPerSample[0] == 8)))
					return ImageCheckType.TIFF_OTHER;
				
				// Store the number of pixels
				nPixels.add(width * height);
			}
			
			// Read the next offset
			offset = readUnsignedInt(in, littleEndian);
		}
		
		// Sort by the number of pixels
		Collections.sort(nPixels);
		
		// If we got this far and only one image has the maximum number of pixels, then we can assume 2D RGB
		if (nPixels.size() == 1 || !nPixels.get(nPixels.size()-1).equals(nPixels.get(nPixels.size()-2)))
			return ImageCheckType.TIFF_2D_RGB;
		return ImageCheckType.TIFF_OTHER;
	}
	
	static ImageCheckType checkBigTiff(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		
		logger.error("Checking Big TIFF images currently not supported!!!");
		
		return ImageCheckType.TIFF_OTHER;			
	}
	
	
	static int readShort(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (littleEndian)
			return ((b2<<8) + b1);
		else
			return ((b1<<8) + b2);
	}
	
	static int readInt(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		int b3 = in.read();
		int b4 = in.read();
		if (littleEndian)
			return ((b4 << 24) + (b3 << 16) + (b2 << 8) + (b1 << 0));
		else
			return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
	}
	
	static long readUnsignedInt(final RandomAccessFile in, final boolean littleEndian) throws IOException {
		return ((long)readInt(in, littleEndian)) & 0xffffffffL;
	}
	
	
	
	static String getString(final RandomAccessFile in, final int count, final long offset) throws IOException {
		byte[] bytes = new byte[count - 1]; // Skip null byte
		long saveLoc = in.getFilePointer();
		in.seek(offset);
		in.readFully(bytes);
		in.seek(saveLoc);
		return new String(bytes);
	}
	
	
	
}