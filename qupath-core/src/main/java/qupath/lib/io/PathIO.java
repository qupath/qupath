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

package qupath.lib.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Locale.Category;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.Workflow;

/**
 * Primary class for loading/saving ImageData objects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathIO {
	
	final private static Logger logger = LoggerFactory.getLogger(PathIO.class);
		
	private PathIO() {}
	
	
	
	/**
	 * Read the server path from a serialized file, if present.  This is assumed to be the first line within the file.
	 * @param file
	 * @return The server path that is stored within the file, or null if no path could be found.
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws ClassNotFoundException 
	 */
	public static String readSerializedServerPath(final File file) throws FileNotFoundException, IOException, ClassNotFoundException {
		String serverPath = null;
		try (FileInputStream fileIn = new FileInputStream(file)) {
			ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(fileIn));
			// Check the first line, then read the server path if it is valid
			String firstLine = inStream.readUTF();
			if (firstLine.startsWith("Data file version")) {
				serverPath = (String)inStream.readObject();
				serverPath = serverPath.substring("Image path: ".length()).trim();
			}
		}
		return serverPath;
	}
	
	private static <T> ImageData<T> readImageDataSerialized(final File file, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws FileNotFoundException, IOException {
		if (file == null)
			return null;
		logger.info("Reading data from {}...", file.getName());
		try (FileInputStream stream = new FileInputStream(file)) {
			imageData = readImageDataSerialized(stream, imageData, server, cls);	
			// Set the last saved path (actually the path from which this was opened)
			if (imageData != null)
				imageData.setLastSavedPath(file.getAbsolutePath(), true);
			return imageData;
//		} catch (IOException e) {
//			logger.error("Error reading ImageData from file", e);
//			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <T> ImageData<T> readImageDataSerialized(final InputStream stream, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		
		long startTime = System.currentTimeMillis();
		Locale locale = Locale.getDefault(Category.FORMAT);
		boolean localeChanged = false;

		try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(stream))) {
			String serverPath = null;
			PathObjectHierarchy hierarchy = null;
			ImageData.ImageType imageType = null;
			ColorDeconvolutionStains stains = null;
			Workflow workflow = null;
			Map<String, Object> propertyMap = null;

			String firstLine = inStream.readUTF();
			//				int versionNumber = -1;
			if (!firstLine.startsWith("Data file version")) {
				logger.error("Input stream does not contain valid QuPath data!");
			}
			//				else {
			//					// Could try to parse version number... although frankly, at this time, we don't really care...
			//					try {
			//						versionNumber = NumberFormat.getInstance(Locale.US).parse(firstLine.substring("Data file version".length()).trim()).intValue();
			//					} catch (Exception e) {
			//						logger.warn("Unable to parse version number from {}", firstLine);
			//					}
			//				}

			serverPath = (String)inStream.readObject();
			serverPath = serverPath.substring("Image path: ".length()).trim();


			while (true) {
				//					logger.debug("Starting read: " + inStream.available());
				try {
					// Try to read a relevant object from the stream
					Object input = inStream.readObject();
					logger.debug("Read: {}", input);

					// If we have a Locale, then set it
					if (input instanceof Locale) {
						if (input != locale) {
							Locale.setDefault(Category.FORMAT, (Locale)input);
							localeChanged = true;
						}
					} else if (input instanceof PathObjectHierarchy)
						hierarchy = (PathObjectHierarchy)input;
					else if (input instanceof ImageData.ImageType)
						imageType = (ImageData.ImageType)input;
					else if (input instanceof String && "EOF".equals(input))  {
						//							if (serverPath == null) // serverPath should be first string
						//								serverPath = (String)input;
						//							else if ("EOF".equals(input)) {
						break;
						//							}
					}
					else if (input instanceof ColorDeconvolutionStains)
						stains = (ColorDeconvolutionStains)input;
					else if (input instanceof Workflow)
						workflow = (Workflow)input;
					else if (input instanceof Map)
						propertyMap = (Map<String, Object>)input;
					else if (input == null) {
						logger.debug("Null object will be skipped");
					} else
						logger.warn("Unsupported object of class {} will be skipped: {}", input.getClass().getName(), input);

				} catch (ClassNotFoundException e) {
					logger.error("Unable to find class", e);
				} catch (EOFException e) {
					// Try to recover from EOFExceptions - we may already have enough info
					logger.error("Reached end of file...");
					if (hierarchy == null)
						e.printStackTrace();
					break;
				}
			}

			// Create an entirely new ImageData if necessary
			if (imageData == null || !(imageData.getServer().equals(server) || imageData.getServerPath().equals(serverPath))) {
				// Create a new server if we need to
				if (server == null) {
					try {
						server = ImageServerProvider.buildServer(serverPath, cls);
					} catch (Exception e) {
						logger.error(e.getLocalizedMessage());
					};
					if (server == null) {
						logger.error("Warning: Unable to create server for path " + serverPath);
						//							throw new RuntimeException("Warning: Unable to create server for path " + serverPath);
					}
				}
				// TODO: Make this less clumsy... but for now we need to ensure we have a fully-initialized hierarchy (which deserialization alone doesn't achieve)
				PathObjectHierarchy hierarchy2 = new PathObjectHierarchy();
				hierarchy2.setHierarchy(hierarchy);
				hierarchy = hierarchy2;

				imageData = new ImageData<>(server, hierarchy, imageType);
			} else {
				if (imageType != null)
					imageData.setImageType(imageType);
				// Set the new hierarchy
				imageData.getHierarchy().setHierarchy(hierarchy);
			}
			// Set the other properties we have just read
			if (workflow != null) {
				imageData.getHistoryWorkflow().clear();
				imageData.getHistoryWorkflow().addSteps(workflow.getSteps());
			}
			if (stains != null) {
				imageData.setColorDeconvolutionStains(stains);
			}
			if (propertyMap != null) {
				for (Entry<String, Object> entry : propertyMap.entrySet())
					imageData.setProperty(entry.getKey(), entry.getValue());
			}

			long endTime = System.currentTimeMillis();

			//				if (hierarchy == null) {
			//					logger.error(String.format("%s does not contain a valid QUPath object hierarchy!", file.getAbsolutePath()));
			//					return null;
			//				}
			logger.debug(String.format("Hierarchy with %d object(s) read in %.2f seconds", hierarchy.nObjects(), (endTime - startTime)/1000.));

		} catch (ClassNotFoundException e1) {
			logger.warn("Class not found reading image data", e1);
		} finally {
			if (localeChanged)
				Locale.setDefault(Category.FORMAT, locale);
		}
		return imageData;
	}
	
	
//	/**
//	 * Test if a specified file can be identified as a zip file.
//	 * 
//	 * Zip 'magic number' contents are tested rather than file extension.
//	 * 
//	 * @param file
//	 * @return
//	 */
//	public static boolean isZipFile(final File file) {
//		if (!file.canRead() || file.length() < 4)
//			return false;
//		
//		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
//			int zipTest = in.readInt();
//			in.close();
//			return zipTest == 0x504b0304;
//		} catch (IOException e) {
//			return false;
//		}
//	}
	
	/**
	 * Read ImageData from an InputStream into an existing ImageData object, or creating a new one if required.
	 * 
	 * @param stream
	 * @param imageData
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @param cls
	 * @return
	 * @throws IOException
	 */
	public static <T> ImageData<T> readImageData(final InputStream stream, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		return readImageDataSerialized(stream, imageData, server, cls);
	}

	
	/**
	 * Read ImageData from a File into an existing ImageData object, or create a new one if required.
	 * @param <T> 
	 * 
	 * @param file
	 * @param imageData
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @param cls 
	 * @return
	 * @throws IOException 
	 */
	public static <T> ImageData<T> readImageData(final File file, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		return readImageDataSerialized(file, imageData, server, cls);
	}
	
	
	/**
	 * Write (binary) file containing ImageData for later use.
	 * 
	 * @param file
	 * @param imageData
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public static void writeImageData(final File file, final ImageData<?> imageData) throws FileNotFoundException, IOException {
		File backup = null;
		
		// Backup any existing file... just in case of disaster
		if (file.exists()) {
			File fileCopy = new File(file.toURI());
			backup = new File(fileCopy.getAbsolutePath() + ".backup");
			fileCopy.renameTo(backup);
		}
		
		// Write the data
		try (var stream = new FileOutputStream(file)) {
			writeImageDataSerialized(stream, imageData);
			
			// Remember the saved path
			imageData.setLastSavedPath(file.getAbsolutePath(), true);
		}
		
		// Delete the backup file
		if (backup != null && !backup.equals(file))
			backup.delete();

	}
	
	/**
	 * Serialize an ImageData object to an output stream.
	 * @param stream
	 * @param imageData
	 * @throws IOException
	 */
	public static void writeImageData(final OutputStream stream, final ImageData<?> imageData) throws IOException {
		writeImageDataSerialized(stream, imageData);
	}
	

	private static void writeImageDataSerialized(final OutputStream stream, final ImageData<?> imageData) throws IOException {
				
		try (OutputStream outputStream = new BufferedOutputStream(stream)) {
			long startTime = System.currentTimeMillis();
			
			ObjectOutputStream outStream = new ObjectOutputStream(outputStream);
			
			// Write the identifier
			// Version 1.0 was the first...
			// Version 2 switched to integers, and includes Locale information
			outStream.writeUTF("Data file version 2");
			
			// Try to write a backwards-compatible image path
			var server = imageData.getServer();
			var uris = server.getURIs();
			String path;
			if (uris.size() == 1) {
				var uri = uris.iterator().next();
				var serverPath = GeneralTools.toPath(uri);
				if (serverPath != null && Files.exists(serverPath))
					path = serverPath.toFile().getAbsolutePath();
				else
					path = uri.toString();
			} else
				path = server.getPath();
			outStream.writeObject("Image path: " + path);
			
			// Write the current locale
			outStream.writeObject(Locale.getDefault(Category.FORMAT));
			
			// Write the rest of the main image metadata
			outStream.writeObject(imageData.getImageType());
			outStream.writeObject(imageData.getColorDeconvolutionStains());
			outStream.writeObject(imageData.getHistoryWorkflow());
			
			// Write the rest of the main image metadata
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			logger.info(String.format("Writing object hierarchy with %d object(s)...", hierarchy.nObjects()));
			outStream.writeObject(hierarchy);
			
			// Write any remaining (serializable) properties
			Map<String, Object> map = new HashMap<>();
			for (Entry<String, Object> entry : imageData.getProperties().entrySet()) {
				if (entry.getValue() instanceof Serializable)
					map.put(entry.getKey(), entry.getValue());
				else
					logger.error("Property not serializable and will not be saved!  Key: " + entry.getKey() + ", Value: " + entry.getValue());
			}
			if (map != null)
				outStream.writeObject(map);
			
			// Write EOF marker
			outStream.writeObject("EOF");
			
			long endTime = System.currentTimeMillis();
			logger.info(String.format("Image data written in %.2f seconds", (endTime - startTime)/1000.));
		}
	}
	
	/**
	 * Read a hierarchy from a .qpdata file.
	 * 
	 * @param file
	 * @return
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public static PathObjectHierarchy readHierarchy(final File file) throws FileNotFoundException, IOException {
		logger.info("Reading hierarchy from {}...", file.getName());
		try (FileInputStream stream = new FileInputStream(file)) {
			var hierarchy = readHierarchy(stream);			
			if (hierarchy == null)
				logger.error("Unable to find object hierarchy in " + file);
			return hierarchy;
		}
	}
	
	/**
	 * Read a PathObjectHierarchy from a saved data file (omitting all other contents).
	 * 
	 * @param fileIn
	 * @return
	 * @throws IOException 
	 */
	public static PathObjectHierarchy readHierarchy(final InputStream fileIn) throws IOException {

		Locale locale = Locale.getDefault(Category.FORMAT);
		boolean localeChanged = false;

		try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(fileIn))) {
			if (!inStream.readUTF().startsWith("Data file version")) {
				logger.error("Input stream is not from a valid QuPath data file!");
			}
			while (true) {
				//					logger.debug("Starting read: " + inStream.available());
				try {
					// Try to read a relevant object from the stream
					Object input = inStream.readObject();
					logger.debug("Read: {}", input);

					// Set locale - may be needed (although probably isn't...)
					if (input instanceof Locale) {
						if (input != locale) {
							Locale.setDefault(Category.FORMAT, (Locale)input);
							localeChanged = true;
						}
					} else if (input instanceof PathObjectHierarchy) {
						/* This would ideally be unnecessary, but it's needed to ensure that the PathObjectHierarchy
						 * has been property initialized.  We can't count on the deserialized hierarchy being immediately functional.
						 */
						PathObjectHierarchy hierarchy = new PathObjectHierarchy();
						hierarchy.setHierarchy((PathObjectHierarchy)input);
						return hierarchy;
					}

				} catch (ClassNotFoundException e) {
					logger.error("Unable to find class", e);
				} catch (EOFException e) {
					logger.error("Reached end of file unexpectedly...");
				}
			}
		} finally {
			if (localeChanged)
				Locale.setDefault(Category.FORMAT, locale);
		}
	}

	
	
//	private static boolean serializePathObject(File file, PathObject pathObject) {
//		boolean success = false;
//		if (file == null)
//			return false;
//		BufferedOutputStream outputStream = null;
//		try {
//			logger.info("Writing {}", pathObject);
//			FileOutputStream fileOutMain = new FileOutputStream(file);
//			outputStream = new BufferedOutputStream(fileOutMain);
//			ObjectOutputStream outStream = new ObjectOutputStream(outputStream);
//			outStream.writeObject(pathObject);
//			outStream.close();
//			success = true;
//		} catch (IOException e) {
//			e.printStackTrace();
//		} finally {
//			if (outputStream != null)
//				try {
//					outputStream.close();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//		}
//		return success;
//	}
	
	
}
