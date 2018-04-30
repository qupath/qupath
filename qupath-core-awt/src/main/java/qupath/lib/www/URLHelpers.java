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

package qupath.lib.www;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;

/**
 * Collection of static methods that help with reading images using HTTP requests.
 * 
 * @author Pete Bankhead
 *
 */
public class URLHelpers {
	
	static int counter = 0;
	
	private final static Logger logger = LoggerFactory.getLogger(URLHelpers.class);
	
	private static final int DEFAULT_TIMEOUT = 10000;
	
	/**
	 *  A directory used to cache images read from URLs.
	 *  This doesn't do anything very smart (such as deleting old entries), so could grow considerably...
	 */
	private static FileSystem cacheFileSystem = null;
	private static String cacheRootPath = null;
	
	
	public static FileSystem getCacheFileSystem() {
		return cacheFileSystem;
	}

	public static void setCacheFileSystem(final FileSystem dir, final String cacheRoot) {
		cacheFileSystem = dir;
		cacheRootPath = cacheRoot;
	}

	
//	public static BufferedImage readJPEGfromURL(String url) {
//		return readImageFromURL(url);
////		BufferedImage img = null;
////		try	{
////			//			long startTime = System.currentTimeMillis();
////			URL pxlURL = new URL(url);
////			ImageReader reader = ImageIO.getImageReadersByFormatName("jpeg").next();
////			ImageReadParam param = reader.getDefaultReadParam();
////			//			 ImageInputStream stream = ImageIO.createImageInputStream(new BufferedInputStream(pxlURL.openStream()));
////			ImageInputStream stream = ImageIO.createImageInputStream(pxlURL.openStream());
////			reader.setInput(stream, true, true);
////			try {
////				img = reader.read(0, param);
////			} finally {
////				reader.dispose();
////				stream.close();
////			}
////			//			long endTime = System.currentTimeMillis();
////			//			System.out.println((endTime - startTime) + "ms for " + url);
////			return img;
////		}
////		catch(Exception e) {
////			e.printStackTrace();
////		}
////		return img;
//	}
	
	
	/**
	 * Try to find a cached version of an image to read from a URL.
	 * 
	 * If a cache directory is set, then use it to look for where the image relating to the URL might be.
	 * If found, read the cached image and return it.
	 * If not found, proceed with the HTTP request and cache any image that comes back.
	 * 
	 * If no cache directory is set, or it is set but doesn't exist on the file system, then quickly return null.
	 * 
	 * @param url
	 * @return
	 */
	static BufferedImage readCached(final String url) {
		FileSystem fileSystem = cacheFileSystem;
		if (fileSystem == null || !fileSystem.isOpen())
			return null;
		
//		File file = new File(dirCache, url.replace("/", "_"));
		String questionChar = GeneralTools.isWindows() ? "" : "?";
		Path cachePath = fileSystem.getPath(cacheRootPath, url.replace("://", File.separator).replace(":/", File.separator).replace("?", questionChar+File.separator));
//		File file = new File(dirCache, url.replace("://", File.separator).replace(":/", File.separator).replace("?", "?"+File.separator));
		// First, try reading from existing cached file
		if (Files.exists(cachePath)) {
			try (InputStream input = Files.newInputStream(cachePath)){
				BufferedImage img = ImageIO.read(input);
				if (img != null) {
//					logger.debug("Read from cache: {}", file);
					return ensureIntRGB(img);
				}
			} catch (IOException e) {
				logger.error("Error reading cached image from file", e);
				return null;
			}
		}
		
//		System.err.println(url);

		Path dirParent = cachePath.getParent();
		try {
			if (!Files.isDirectory(dirParent) && Files.createDirectories(dirParent) == null)
				return null;
		} catch (Exception e) {
			logger.trace("Unable to create directory in file system", e);
		}
		
		// Try reading bytes from URL
		BufferedInputStream stream = null;
		byte[] bytes = null;
		try {
			URL pxlURL = new URL(url);
			URLConnection connection = pxlURL.openConnection();
//			System.out.println("Content length: " + connection.getContentLength());
//			System.out.println("Encoding: " + connection.getContentType());
//			long startTime = System.currentTimeMillis();
			String type = connection.getContentType();
//			long endTime = System.currentTimeMillis();
//			System.err.println("Connection time: " + (endTime - startTime));
//			System.out.println(type);
			if (!type.startsWith("image")) {
				if (connection instanceof HttpURLConnection)
					((HttpURLConnection)connection).disconnect();
				return null;
			}
			long length = connection.getContentLengthLong();
			if (length < 0)
				length = 4096;
			stream = new BufferedInputStream(connection.getInputStream());
			ByteArrayOutputStream outBytes = new ByteArrayOutputStream((int)length);
			byte[] buffer = new byte[4096];
			int len = 0;
			while ((len = stream.read(buffer)) > 0) {
				outBytes.write(buffer, 0, len);
			}
			bytes = outBytes.toByteArray();
//			bytes = new byte[length];
//			stream.read(bytes);			
//			System.out.println("Lengths: " + (length - bytes.length));
//			System.out.println("Lengths 2: " + (length - outBytes.size()));
		} catch (Exception e) {
			logger.error("Error reading image bytes to cache", e);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException e) {
				logger.error("Error closing stream", e);
			}
		}
		
		if (bytes == null || bytes.length == 0)
			return null;
		
		// Create image & cache results
		BufferedImage img = null;
		synchronized(fileSystem) {
			if (fileSystem.isOpen()) {
					try (OutputStream output = Files.newOutputStream(cachePath)){
						output.write(bytes);
						img = ImageIO.read(new ByteArrayInputStream(bytes));
						img = ensureIntRGB(img);
					} catch (Exception e) {
						logger.error("Error closing stream from byte array", e);
					}
			} else {
				try (ByteArrayInputStream imgStream = new ByteArrayInputStream(bytes)) {
					img = ImageIO.read(imgStream);
					img = ensureIntRGB(img);
				} catch (IOException e) {
					logger.error("Error reading image from stream", e);
				}
			}
		}
		
		return img;
	}
	
	
	/**
	 * Ensure that an RGB image is the same kind of RGB, so that the int arrays can be treated as 
	 * storing the pixels as packed RGB values.
	 * 
	 * Running this command results in byte array variations, or BGR images are converted to have BufferedImage.TYPE_INT_RGB.
	 * 
	 * Images that are already RGB, or RGBA are returned unchanged.
	 * 
	 * @param img
	 * @return
	 */
	public static BufferedImage ensureIntRGB(final BufferedImage img) {
		if (img == null)
			return null;
		switch (img.getType()) {
		case BufferedImage.TYPE_3BYTE_BGR:
		case BufferedImage.TYPE_4BYTE_ABGR:
		case BufferedImage.TYPE_4BYTE_ABGR_PRE:
		case BufferedImage.TYPE_INT_BGR:
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
//			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			return img2;
		}
		return img;
	}
	
	

	/**
	 * Read an image from a URL, using the default timeout (here, 1 second).
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static BufferedImage readImageFromURL(String url) throws IOException {
		return readImageFromURL(url, DEFAULT_TIMEOUT);
	}

	
	/**
	 * Read an image from a URL, using a specified timeout.
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static BufferedImage readImageFromURL(final String url, final int timeout) throws IOException {
		BufferedImage img = readCached(url);
		if (img != null)
			return img;
		logger.trace("Requesting image from {}", url);
		URL pxlURL = new URL(url);
		URLConnection connection = pxlURL.openConnection();
//		connection.setConnectTimeout(timeout * 60);
		connection.setReadTimeout(timeout);
		try (InputStream in = connection.getInputStream()) {
			img = ImageIO.read(in);
			if (img == null) {
//				System.err.println("Unable to read image from " + url);
//				return null;
//				System.out.println("TIMEOUT: " + connection.getReadTimeout());
				throw new IOException("Unable to read image from " + url);
			}
			img = ensureIntRGB(img);
			return img;
		}
//			long startTime = System.currentTimeMillis();
//			if (Thread.currentThread().getId() == 29)
//				System.out.println(Thread.currentThread().getId() + " - Starting " + (counter++));
//			URL pxlURL = new URL(url);
//			img = ImageIO.read(pxlURL);
//			long endTime = System.currentTimeMillis();
//			if (Thread.currentThread().getId() == 29)
//				System.out.println((endTime - startTime) + "ms for " + url);
//			return img;
//		}
//		catch(Exception e) {
//			logger.error("Error reading url: {}", url);
//			e.printStackTrace();
//		}
//		return img;
	}
	
	
//	public static BufferedImage readImageFromURL(String url) {
//		BufferedImage img = null;
//		try	{
//			long startTime = System.currentTimeMillis();
//			URL pxlURL = new URL(url);
//			BufferedInputStream in = new BufferedInputStream(pxlURL.openStream());
//			ByteBuffer buf = new ByteBuffer(in);
//			img = ImageIO.read(in);
//			in.close();
//			long endTime = System.currentTimeMillis();
//			System.out.println((endTime - startTime) + "ms for " + url);
//			return img;
//		}
//		catch(Exception e) {
//			e.printStackTrace();
//		}
//		return img;
//	}

	
//	public static String readURLAsString(URL url) {
//		Scanner scanner = null;
//		String s = null;
//		try {
//			IJ.log("Making stream");
//			InputStream stream = url.openStream();
//			IJ.log("Got stream");
//			scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A");
//			IJ.log("Made scanner");
//			s = scanner.next();
//			IJ.log("Scanned!");
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		if (scanner != null)
//			scanner.close();
//		return s;
//	}
		
	
}
