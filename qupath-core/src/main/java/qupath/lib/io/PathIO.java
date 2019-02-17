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

package qupath.lib.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Locale.Category;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.workflow.Workflow;

/**
 * Primary class for loading/saving ImageData objects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathIO {
	
	final private static Logger logger = LoggerFactory.getLogger(PathIO.class);
	
	// Temporary flag to switch default file format used for serialization of ImageData objects
	private static boolean USE_ZIPPED_SERIALIZATION = false;
	
	private PathIO() {}
	
	
	
	/**
	 * Read the server path from a serialized file, if present.  This is assumed to be the first line within the file.
	 * @param file
	 * @return The server path that is stored within the file, or null if no path could be found.
	 */
	public static String readSerializedServerPath(final File file) {
		String serverPath = null;
		try {
			FileInputStream fileIn = null;
			ObjectInputStream inStream = null;
			try {
				fileIn = new FileInputStream(file);
				inStream = new ObjectInputStream(new BufferedInputStream(fileIn));
				// Check the first line, then read the server path if it is valid
				String firstLine = inStream.readUTF();
				if (firstLine.startsWith("Data file version")) {
					serverPath = (String)inStream.readObject();
					serverPath = serverPath.substring("Image path: ".length()).trim();
				}
			} catch (Exception e) {
				// Log that the server path wasn't there
				logger.warn("Server path not stored within {}", file.getName());
			} finally {
				if (fileIn != null)
					fileIn.close();
				if (inStream != null)
					inStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return serverPath;
	}
	
	
	@SuppressWarnings("unchecked")
	private static <T> ImageData<T> readImageDataSerialized(final File file, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) {
		if (file == null)
			return null;
		
		Locale locale = Locale.getDefault(Category.FORMAT);
		boolean localeChanged = false;
		
		try {
			long startTime = System.currentTimeMillis();
			logger.info("Reading data from {}...", file.getName());
			
			FileInputStream fileIn = null;
			ObjectInputStream inStream = null;
			try {
				fileIn = new FileInputStream(file);
//				inStream = new ObjectInputStream(new InflaterInputStream(new BufferedInputStream(fileIn)));
				inStream = new ObjectInputStream(new BufferedInputStream(fileIn));
				
				
				String serverPath = null;
				PathObjectHierarchy hierarchy = null;
				ImageData.ImageType imageType = null;
				ColorDeconvolutionStains stains = null;
				Workflow workflow = null;
				Map<String, Object> propertyMap = null;
				
				String firstLine = inStream.readUTF();
//				int versionNumber = -1;
				if (!firstLine.startsWith("Data file version")) {
					logger.error(file.getPath() + " is not a valid QuPath data file!");
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
							logger.error("Null object will be skipped");
						} else
							logger.error("Unsupported object of class {} will be skipped: {}", input.getClass().getName(), input);
						
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
				
				// Set the last saved path (actually the path from which this was opened)
				imageData.setLastSavedPath(file.getAbsolutePath(), true);
					
				
				long endTime = System.currentTimeMillis();
				
//				if (hierarchy == null) {
//					logger.error(String.format("%s does not contain a valid QUPath object hierarchy!", file.getAbsolutePath()));
//					return null;
//				}
				logger.info(String.format("Hierarchy with %d object(s) read from %s in %.2f seconds", hierarchy.nObjects(), file.getAbsolutePath(), (endTime - startTime)/1000.));
				
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
			} finally {
				if (fileIn != null)
					fileIn.close();
				if (inStream != null)
					inStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (localeChanged)
				Locale.setDefault(Category.FORMAT, locale);
		}
		return imageData;
	}
	
	
	/**
	 * Test if a specified file can be identified as a zip file.
	 * 
	 * Zip 'magic number' contents are tested rather than file extension.
	 * 
	 * @param file
	 * @return
	 */
	public static boolean isZipFile(final File file) {
		if (!file.canRead() || file.length() < 4)
			return false;
		
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			int zipTest = in.readInt();
			in.close();
			return zipTest == 0x504b0304;
		} catch (IOException e) {
			return false;
		}
	}
	
	
	/**
	 * Strip the core number from the filename of a serialized TMA core object path.
	 * 
	 * @param path
	 * @return
	 */
	private static int stripCoreNumber(final Path path) {
		String name = path.getFileName().toString();
		return Integer.parseInt(name.replace("core_", "").replace(".qpobj", ""));
	}
	
	
	/**
	 * Read ImageData into an existing ImageData object, or creating a new one if required.
	 * 
	 * @param file
	 * @param imageData
	 * @param server - an ImageServer to use rather than any that might be stored within the serialized file.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> ImageData<T> readImageData(final File file, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) {
		if (!isZipFile(file))
			return readImageDataSerialized(file, imageData, server, cls);
		
		try (FileSystem fsZip = FileSystems.newFileSystem(file.toPath(), null)) {
			
			List<TMACoreObject> cores = null;
			
			List<Path> corePaths = StreamSupport.stream(Files.newDirectoryStream(fsZip.getPath("hierarchy"), "core_*").spliterator(), false).collect(Collectors.toCollection(() -> new ArrayList<>()));
			corePaths.sort((p1, p2) -> Integer.compare(stripCoreNumber(p1), stripCoreNumber(p2)));
			
			cores = 
						corePaths.parallelStream().map(p -> {
					try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
						return (TMACoreObject)inStream.readObject();
					} catch (IOException | ClassNotFoundException e) {
						e.printStackTrace();
					}
					return null;
				}).collect(Collectors.toList());
			
			
			
//			try (DirectoryStream<Path> stream = Files.newDirectoryStream(fsZip.getPath("hierarchy"), "core_*")) {
//				cores = 
//						StreamSupport.stream(stream.spliterator(), true).map(p -> {
//					try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
//						return (TMACoreObject)inStream.readObject();
//					} catch (IOException | ClassNotFoundException e) {
//						e.printStackTrace();
//					}
//					return null;
//				}).collect(Collectors.toList());
//			} catch (IOException e) {
//			    e.printStackTrace();
//			}
			
			PathObjectHierarchy hierarchy = new PathObjectHierarchy();
			if (cores != null) {
				Path gridPath = fsZip.getPath("hierarchy", "grid");
				int gridWidth = 1;
				try (BufferedReader reader = Files.newBufferedReader(gridPath)) {
					String line = reader.readLine();
					gridWidth = Integer.parseInt(line.replace("width=", ""));
				} catch (IOException e) {
					e.printStackTrace();
				}
				TMAGrid tmaGrid = new DefaultTMAGrid(cores, gridWidth);
				hierarchy.setTMAGrid(tmaGrid);
			}
			
			// Read all remaining objects
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(fsZip.getPath("hierarchy"), "object_*")) {
				List<PathObject> pathObjects = 
						StreamSupport.stream(stream.spliterator(), true).map(p -> {
					try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
						return (PathObject)inStream.readObject();
					} catch (IOException | ClassNotFoundException e) {
						e.printStackTrace();
					}
					return null;
				}).collect(Collectors.toList());
				hierarchy.getRootObject().addPathObjects(pathObjects);
			} catch (IOException e) {
			    e.printStackTrace();
			}
			
			
			// Read the remaining data that's needed
			Path dataPath = fsZip.getPath("data");
			ImageType imageType = null;
			Workflow workflow = null;
			String serverPath = null;
			ColorDeconvolutionStains stains = null;
			Map<String, Object> propertyMap = null;
			try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(dataPath)))) {
				while (true) {
					Object object = inStream.readObject();
					if ("EOF".equals(object))
						break;
					if (object instanceof Workflow)
						workflow = (Workflow)object;
					else if (object instanceof ImageType)
						imageType = (ImageType)object;
					else if (object instanceof String)
						serverPath = (String)object;
					else if (object instanceof Map)
						propertyMap = (Map<String, Object>)object;
					else if (object instanceof ColorDeconvolutionStains)
						stains = (ColorDeconvolutionStains)object;
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// Create a new server if we need to
			if (server == null || (serverPath != null && !server.getPath().equals(serverPath))) {
				try {
				server = ImageServerProvider.buildServer(serverPath, cls);
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage());
				};
				if (server == null) {
					logger.error("Warning: Unable to create server for path " + serverPath);
//					throw new RuntimeException("Warning: Unable to create server for path " + serverPath);
				}
			}
			
			imageData = new ImageData<>(server, hierarchy, imageType);
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
			
			
			return imageData;
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	
	/**
	 * Write (binary) file containing ImageData for later use.
	 * 
	 * @param file
	 * @param imageData
	 * @return
	 */
	public static boolean writeImageData(final File file, final ImageData<?> imageData) {
		if (USE_ZIPPED_SERIALIZATION)
			return writeImageDataZipped(file, imageData);
		else
			return writeImageDataSerialized(file, imageData);
	}
	

	private static boolean writeImageDataZipped(final File file, final ImageData<?> imageData) {
		
		if (imageData.getHierarchy().getTMAGrid() == null)
			return writeImageDataSerialized(file, imageData);
		
		
		logger.warn("NEW DATA SERIALIZATION WILL BE APPLIED!!!");
		
//		FileSystem fsZip = null;
		long startTime = System.currentTimeMillis();
		try {
			Map<String, String> env = new HashMap<>(); 
			env.put("create", "true");
//			Path path = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().indexOf(".")) + ".mrdata").toPath();
			Path path = file.toPath();
			URI rootURI = new URI("file:///");
            Path rootPath = Paths.get(rootURI);
            path = rootPath.resolve(path);
            Files.deleteIfExists(path); // TODO: Rename instead, to have a backup
			FileSystem fsZip = FileSystems.newFileSystem(URI.create("jar:" + path.toUri().toString()), env, null);
			// Write the TMA cores
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			Files.createDirectories(fsZip.getPath("hierarchy"));
			
			Path gridPath = fsZip.getPath("hierarchy", "grid");
			try (BufferedWriter writer = Files.newBufferedWriter(gridPath)) {
				writer.write("width="+hierarchy.getTMAGrid().getGridWidth());
				writer.newLine();
				writer.write("height="+hierarchy.getTMAGrid().getGridHeight());
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			hierarchy.getTMAGrid().getTMACoreList().parallelStream().forEach(core -> {
				int count = hierarchy.getTMAGrid().getTMACoreList().indexOf(core);
				Path corePath = fsZip.getPath("hierarchy", "core_" + count + ".qpobj");
				try (ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(corePath)))) {
					stream.writeObject(core);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			
			// Write everything else in the hierarchy - in case there are other annotations (for example)
			int count = 0;
			for (PathObject pathObject : hierarchy.getRootObject().getChildObjects()) {
				if (pathObject instanceof TMACoreObject)
					continue;
				Path objectPath = fsZip.getPath("object_" + count + ".qpobj");
				count++;
				try (ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(objectPath)))) {
					stream.writeObject(pathObject);
				} catch (IOException e) {
					e.printStackTrace();
				}				
			}
			
			Path dataPath = fsZip.getPath("data");
			try (ObjectOutputStream outStream = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(dataPath)))) {
				outStream.writeObject(imageData.getServerPath());
				// Write the rest of the main image metadata
				outStream.writeObject(imageData.getImageType());
				outStream.writeObject(imageData.getColorDeconvolutionStains());
				outStream.writeObject(imageData.getHistoryWorkflow());
	
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
			}
			
			fsZip.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		long endTime = System.currentTimeMillis();
		logger.info(String.format("TMA image data written to %s in %.2f seconds", file.getAbsolutePath(), (endTime - startTime)/1000.));
		
		return true;
	}
	
	

	private static boolean writeImageDataSerialized(final File file, final ImageData<?> imageData) {
		if (file == null)
			return false;
		File backup = null;
		
		try {
			long startTime = System.currentTimeMillis();
			
			// Backup any existing file... just in case of disaster
			if (file.exists()) {
				File fileCopy = new File(file.toURI());
				backup = new File(fileCopy.getAbsolutePath() + ".backup");
				fileCopy.renameTo(backup);
			}
			
			FileOutputStream fileOutMain = new FileOutputStream(file);
			OutputStream outputStream = new BufferedOutputStream(fileOutMain);
			// Could enable compression - however need to consider writing data file version number first
//			if (compress) {
//				Deflater deflater = new Deflater(Deflater.BEST_SPEED);
//				deflater.setStrategy(Deflater.HUFFMAN_ONLY); // More modest compression, but a bit faster
//				outputStream = new DeflaterOutputStream(fileOutMain, deflater, 1024*1024*8);
//			} else {
				outputStream = new BufferedOutputStream(fileOutMain);
//			}
			ObjectOutputStream outStream = new ObjectOutputStream(outputStream);
			
			// Write the identifier
			// Version 1.0 was the first...
			// Version 2 switched to integers, and includes Locale information
			outStream.writeUTF("Data file version 2");
			
			// Write the image path
			outStream.writeObject("Image path: " + imageData.getServerPath());
			
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
			
			// Remember the saved path
			imageData.setLastSavedPath(file.getAbsolutePath(), true);

			outputStream.close();
			
			// Delete the backup file
			if (backup != null && !backup.equals(file))
				backup.delete();
			
			long endTime = System.currentTimeMillis();
			logger.info(String.format("Image data written to %s in %.2f seconds", file.getAbsolutePath(), (endTime - startTime)/1000.));
		} catch (IOException e) {
			logger.error("Error writing Image data to " + file.getAbsolutePath(), e);
			return false;
		}
		return true;
	}
	
	/**
	 * Read a hierarchy from a .qpdata file.
	 * <p>
	 * Deprecated in favor of alternative that reads from an InputStream.
	 * 
	 * @param file
	 * @return
	 */
	@Deprecated
	public static PathObjectHierarchy readHierarchy(final File file) {
		logger.info("Reading hierarchy from {}...", file.getName());
		try (FileInputStream stream = new FileInputStream(file)) {
			var hierarchy = readHierarchy(stream);			
			if (hierarchy == null)
				logger.error("Unable to find object hierarchy in " + file);
			return hierarchy;
		} catch (IOException e) {
			logger.error("Error reading hierarchy from file", e);
			return null;
		}
	}
	
	/**
	 * Read a PathObjectHierarchy from a saved data file (omitting all other contents).
	 * 
	 * @param file
	 * @return
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
					logger.error("Reached end of file...");
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
