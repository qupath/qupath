/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.LogTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.Workflow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Primary class for loading/saving {@link ImageData} objects.
 */
public class PathIO {
	
	private static final Logger logger = LoggerFactory.getLogger(PathIO.class);
	
	/**
	 * Data file version identifier, written within the .qpdata file.
	 * Version 1.0 was the first...
	 * Version 2 switched to integers, and includes Locale information
	 * Version 3 stores JSON instead of a server path
	 * Version 4 stores PathObject UUIDs as a separate field
	 */
	private static final int DATA_FILE_VERSION = 3;
	
	/**
	 * Input filter for deserialization that is limited to QuPath-related classes.
	 */
	private static final ObjectInputFilter QUPATH_INPUT_FILTER = PathIO::qupathInputFilter;
	
	private PathIO() {}
	
	private static int requestedDataFileVersion = DATA_FILE_VERSION - 1;
	
	/**
	 * Get the requested version for .qpdata files.
	 * 
	 * @return
	 * @see #setRequestedDataFileVersion(int version)
	 * @see #getCurrentDataFileVersion()
	 */
	public static int getRequestedDataFileVersion() {
		return requestedDataFileVersion;
	}
	
	/**
	 * Get the current preferred data file version.
	 * @return
	 * @see #setRequestedDataFileVersion(int version)
	 * @see #getCurrentDataFileVersion()
	 */
	public static int getCurrentDataFileVersion() {
		return DATA_FILE_VERSION;
	}
	
	/**
	 * Set the requested version for .qpdata files.
	 * 
	 * <ul>
	 * <li><b>1.0</b> Initial version stored in very early .qpdata files (no longer supported)</li>
	 * <li><b>2</b> Switched versions to use integers, added Locale information (used in QuPath v0.1.2)</li>
	 * <li><b>3</b> Switched {@link ImageServer} paths to be a JSON representation rather than a single path/URL</li>
	 * <li><b>4</b> Added support for UUID to be stored in each {@link PathObject} (introduced QuPath v0.4.0)</li>
	 * </ul>
	 * 
	 * @param version integer representation of the requested version
	 * @see #getRequestedDataFileVersion()
	 * @see #getCurrentDataFileVersion()
	 * @since v0.4.0
	 * @throws IllegalArgumentException if the requested version is less than 2 or greater than {@link #getCurrentDataFileVersion()}
	 */
	public static void setRequestedDataFileVersion(int version) throws IllegalArgumentException {
		if (version < 2 || version > DATA_FILE_VERSION)
			throw new IllegalArgumentException("Requested data file version must be between 2 and " + DATA_FILE_VERSION);
		requestedDataFileVersion = version;
	}
	
	
	/**
	 * Read the server path from a serialized file, if present.  This is assumed to be the first line within the file.
	 * @param file
	 * @return The server path that is stored within the file, or null if no path could be found.
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws ClassNotFoundException 
	 * @deprecated This was useful in QuPath v0.1.2 and earlier, since all information to construct a server was stored within its path. 
	 *             In v0.2 and later, the server path is in general not sufficient to construct a server, and this method lingers 
	 *             only for backwards compatibility. It will be removed in a later version.
	 * @see #extractServerBuilder(Path)
	 */
	@Deprecated
	public static String readSerializedServerPath(final File file) throws FileNotFoundException, IOException, ClassNotFoundException {
		String serverPath = null;
		try (FileInputStream fileIn = new FileInputStream(file)) {
			ObjectInputStream inStream = createObjectInputStream(new BufferedInputStream(fileIn));
			
			// Check the first line, then read the server path if it is valid
			String firstLine = inStream.readUTF();
			if (firstLine.startsWith("Data file version")) {
				serverPath = (String)inStream.readObject();
				serverPath = serverPath.substring("Image path: ".length()).trim();
			}
		}
		return serverPath;
	}
	
	
	/**
	 * Extract a {@link ServerBuilder} from a serialized .qpdata file. 
	 * @param file
	 * @return
	 * @throws IOException if there is an error creating a {@link ServerBuilder}, or the file is not a valid QuPath data file.
	 * @since 0.3
	 */
	public static <T> ServerBuilder<T> extractServerBuilder(Path file) throws IOException {
		try (InputStream fileIn = Files.newInputStream(file)) {
			ObjectInputStream inStream = createObjectInputStream(new BufferedInputStream(fileIn));
			
			// Check the first line, then read the server path if it is valid
			String firstLine = inStream.readUTF();
			if (firstLine.startsWith("Data file version")) {
				return extractServerBuilder((String)inStream.readObject(), true);
			} else {
				throw new IOException(file + " does not appear to be a valid QuPath data file");
			}
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		} catch (IOException e) {
			throw e;
		}
	}
	
	
	/**
	 * Create a new {@link ObjectInputStream}, setting the default {@link ObjectInputFilter} for core QuPath-related 
	 * and Java classes only.
	 * @param stream
	 * @return
	 * @throws IOException
	 */
	public static ObjectInputStream createObjectInputStream(InputStream stream) throws IOException {
		ObjectInputStream inStream = new ObjectInputStream(stream);
		inStream.setObjectInputFilter(QUPATH_INPUT_FILTER);
		return inStream;
	}
	
	
	/**
	 * Extract a {@link ServerBuilder} from a String.
	 * This may represent an image path (for v0.2 and earlier) or JSON (from v0.3).
	 * 
	 * @param <T>
	 * @param serverString string including the builder as JSON, or a legacy image path
	 * @param warnIfInvalid log warnings if the server is a different version
	 * @return
	 * @throws IOException
	 */
	private static <T> ServerBuilder<T> extractServerBuilder(String serverString, boolean warnIfInvalid) throws IOException {
		if (serverString.startsWith("Image path: ")) {
			String serverPath = serverString.substring("Image path: ".length()).trim();
			URI uri = ImageServerProvider.legacyPathToURI(serverPath);
			if (warnIfInvalid)
				logger.warn("Attempting to extract server from legacy data file - this may result in errors");
			return DefaultImageServerBuilder.createInstance(null, uri);
		} else {
			String json = serverString;
			var wrapper = GsonTools.getInstance().fromJson(json, ServerBuilderWrapper.class);
			if (warnIfInvalid && !Objects.equals(wrapper.dataVersion, DATA_FILE_VERSION)) {
				logger.warn("Attempting to read data file version {} written by QuPath {} (expected data file version {})", wrapper.dataVersion, wrapper.qupathVersion, DATA_FILE_VERSION);
			}
			return (ServerBuilder<T>)wrapper.server;
		}
	}
	
	
	/**
	 * Helper class for serializing a server builder to JSON.
	 * By storing this as a separate object (rather than  {@link ServerBuilder} directly) it is easier 
	 * to maintain compatibility across versions.
	 * 
	 * @param <T>
	 */
	static class ServerBuilderWrapper<T> {
		
		private int dataVersion = -1;
		private String qupathVersion = null;
		private ServerBuilder<T> server;
		private String id;
		
		static <T> ServerBuilderWrapper<T>  create(ServerBuilder<T> builder, String id) {
			var wrapper = new ServerBuilderWrapper<T>();
			wrapper.dataVersion = DATA_FILE_VERSION;
			wrapper.qupathVersion = GeneralTools.getVersion();
			wrapper.server = builder;
			wrapper.id = id;
			return wrapper;
		}
		
	}


	private static <T> ImageData<T> readImageDataSerialized(final Path path, ImageServer<T> server) throws IOException {
		try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
			var imageData = readImageDataSerialized(stream, server);
			// Set the last saved path (actually the path from which this was opened)
			imageData.setLastSavedPath(path.toAbsolutePath().toString(), true);
			return imageData;
		}
	}

	private static <T> ImageData<T> readImageDataSerialized(final InputStream stream,
															ImageServer<T> server) throws FileNotFoundException, IOException {
		return readImageDataSerialized(stream, server, null);
	}


	@SuppressWarnings("unchecked")
	private static <T> ImageData<T> readImageDataSerialized(final InputStream stream, ImageServer<T> server,
															ServerBuilder<T> requestedServerBuilder) throws IOException {
		
		long startTime = System.currentTimeMillis();
		Locale locale = Locale.getDefault(Category.FORMAT);
		boolean localeChanged = false;

		try (ObjectInputStream inStream = createObjectInputStream(new BufferedInputStream(stream))) {
			
			ServerBuilder<T> serverBuilder = requestedServerBuilder;
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

			String serverString = (String)inStream.readObject();
			// Don't log warnings if we are provided with a server
			if (serverBuilder == null)
				serverBuilder = extractServerBuilder(serverString, true);

			while (true) {
				try {
					// Try to read a relevant object from the stream
					Object input = inStream.readObject();
					logger.trace("Read object: {}", input);

					// If we have a Locale, then set it
					if (input instanceof Locale) {
						if (input != locale) {
							Locale.setDefault(Category.FORMAT, (Locale) input);
							localeChanged = true;
						}
					} else if (input instanceof PathObjectHierarchy readHierarchy) {
						// Clumsy... but we need to ensure we have a fully-initialized hierarchy
						// (which deserialization alone doesn't achieve)
						hierarchy = new PathObjectHierarchy();
						hierarchy.setHierarchy(readHierarchy);
					} else if (input instanceof ImageData.ImageType readImageType) {
						imageType = readImageType;
					} else if ("EOF".equals(input)) {
						break;
					} else if (input instanceof ColorDeconvolutionStains readStains) {
						stains = readStains;
					} else if (input instanceof Workflow readWorkflow) {
						workflow = readWorkflow;
					} else if (input instanceof Map<?, ?> readPropertyMap) {
						propertyMap = (Map<String, Object>)readPropertyMap;
					} else if (input == null) {
						logger.debug("Null object will be skipped");
					} else
						logger.warn("Unsupported object of class {} will be skipped: {}", input.getClass().getName(), input);

				} catch (ClassNotFoundException e) {
					logger.error("Unable to find class: {}", e.getLocalizedMessage(), e);
				} catch (EOFException e) {
					// Try to recover from EOFExceptions - we may already have enough info
					logger.error("Reached end of file...");
					if (hierarchy == null)
						throw e;
					break;
				} catch (Exception e) {
					if (e instanceof IOException ioe)
						throw ioe;
					else
						throw new IOException(e);
				}
			}

			// Create a new ImageData
			ImageData<T> imageData;
			if (server != null)
				imageData = new ImageData<>(server, hierarchy, imageType);
			else if (serverBuilder != null)
				imageData = new ImageData<>(serverBuilder, hierarchy, imageType);
			else
				throw new IOException("Can't read ImageData without a server or server builder");

			// Set the other properties we have just read
			if (workflow != null) {
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
			if (hierarchy != null)
				logger.debug(String.format("Hierarchy with %d object(s) read in %.2f seconds",
						hierarchy.nObjects(),
						(endTime - startTime)/1000.));

			return imageData;
		} catch (ClassNotFoundException e1) {
			logger.warn("Stream does not appear to be a valid .qpdata file", e1);
			throw new IOException("Cannot read ImageData from stream", e1);
		} finally {
			if (localeChanged)
				Locale.setDefault(Category.FORMAT, locale);
		}
	}


	/**
	 * Try to update an original ImageData object to match a new one.
	 * @param imageDataOriginal
	 * @param imageDataNew
	 * @return the updated original if they refer to the same image, or the new ImageData otherwise
	 * @param <T>
	 */
	private static <T> ImageData<T> tryToUpdateImageData(ImageData<T> imageDataOriginal, ImageData<T> imageDataNew) {
		if (Objects.equals(imageDataOriginal.getServerBuilder(), imageDataNew.getServerBuilder())) {
			imageDataOriginal.setImageType(imageDataNew.getImageType());
			imageDataOriginal.getHierarchy().setHierarchy(imageDataNew.getHierarchy());
			imageDataOriginal.getHistoryWorkflow().clear();
			imageDataOriginal.getHistoryWorkflow().addSteps(imageDataNew.getHistoryWorkflow().getSteps());
			imageDataOriginal.setColorDeconvolutionStains(imageDataNew.getColorDeconvolutionStains());
			for (var entry : imageDataNew.getProperties().entrySet()) {
				imageDataOriginal.setProperty(entry.getKey(), entry.getValue());
			}
			imageDataOriginal.setLastSavedPath(imageDataNew.getLastSavedPath(), false);
			return imageDataOriginal;
		} else {
			return imageDataNew;
		}
	}

	/**
	 * Legacy method to read an {@link ImageData} from a file.
	 * <p>
	 * This previously tried to reuse an existing ImageData if it referred to the same ImageServer, but in practice
	 * it usually returned a new ImageData object instead.
	 * Therefore this method is deprecated, and {@link #readImageData(Path, ImageServer)} should be used instead.
	 *
	 * @param file the .qpdata file
	 * @param imageData optional existing ImageData, to update if possible
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @param cls the generic class for the ImageServer
	 * @return
	 * @throws IOException
	 * @deprecated since v0.6.0
	 */
	@Deprecated
	public static <T> ImageData<T> readImageData(final File file, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		return readImageData(file.toPath(), imageData, server, cls);
	}

	/**
	 * Legacy method to read an {@link ImageData} from a file.
	 * <p>
	 * This previously tried to reuse an existing ImageData if it referred to the same ImageServer, but in practice
	 * it usually returned a new ImageData object instead.
	 * Therefore this method is deprecated, and {@link #readImageData(Path, ImageServer)} should be used instead.
	 *
	 * @param path the .qpdata file
	 * @param imageData optional existing ImageData, to update if possible
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @param cls the generic class for the ImageServer
	 * @return
	 * @throws IOException
	 * @deprecated since v0.6.0
	 */
	@Deprecated
	public static <T> ImageData<T> readImageData(final Path path, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		LogTools.warnOnce(logger, "readImageData(Path, ImageData, ImageServer, Class) is deprecated and will be removed in a future version");
		var newImageData = readImageDataSerialized(path, server);
		if (imageData != null)
			return tryToUpdateImageData(imageData, newImageData);
		else
			return newImageData;
	}
	
	/**
	 * Legacy method to read an {@link ImageData} from a stream.
	 * <p>
	 * This previously tried to reuse an existing ImageData if it referred to the same ImageServer, but in practice
	 * it usually returned a new ImageData object instead.
	 * Therefore this method is deprecated, and {@link #readImageData(Path, ImageServer)} should be used instead.
	 *
	 * @param stream data stream containing the serialized ImageData
	 * @param imageData optional existing ImageData, to update if possible
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @param cls the generic class for the ImageServer
	 * @return
	 * @throws IOException
	 * @deprecated since v0.6.0
	 */
	@Deprecated
	public static <T> ImageData<T> readImageData(final InputStream stream, ImageData<T> imageData, ImageServer<T> server, Class<T> cls) throws IOException {
		LogTools.warnOnce(logger, "readImageData(InputStream, ImageData, ImageServer, Class) is deprecated and will be removed in a future version");
		var newImageData = readImageDataSerialized(stream, server);
		if (imageData != null)
			return tryToUpdateImageData(imageData, newImageData);
		else
			return newImageData;
	}

	/**
	 * Read an ImageData, optionally providing a serverBuilder for lazy image loading.
	 * @param path the .qpdata file to read
	 * @param serverBuilder the server builder to use, or null to use the one stored in the file
	 * @return
	 * @throws IOException
	 */
	public static <T> ImageData<T> readImageData(final Path path, ServerBuilder<T> serverBuilder) throws IOException {
		try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
			var imageData = readImageDataSerialized(stream, null, serverBuilder);
			// Set the last saved path (actually the path from which this was opened)
			imageData.setLastSavedPath(path.toAbsolutePath().toString(), true);
			return imageData;
		}
	}

	/**
	 * Read an ImageData, optionally providing a serverBuilder for lazy image loading.
	 * @param stream the .qpdata file to read
	 * @param serverBuilder the server builder to use, or null to use the one stored in the file
	 * @return
	 * @throws IOException
	 */
	public static <T> ImageData<T> readImageData(final InputStream stream, ServerBuilder<T> serverBuilder) throws IOException {
		return readImageDataSerialized(stream, null, serverBuilder);
	}


	/**
	 * Read and initialize an {@link ImageData} from a data file.
	 * @param file the .qpdata file to read
	 * @return
	 * @throws IOException
	 */
	public static <T> ImageData<T> readImageData(final File file) throws IOException {
		return readImageData(file.toPath());
	}

	/**
	 * Read and initialize an {@link ImageData} from a data file.
	 * @param path the .qpdata file to read
	 * @return
	 * @throws IOException
	 */
	public static <T> ImageData<T> readImageData(final Path path) throws IOException {
		return readImageDataSerialized(path, null);
	}

	
	/**
	 * Read and initialize an {@link ImageData} from a data file.
	 *
	 * @param file the .qpdata file to read
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.  Should be null to use the serialized path to build a new server.
	 * 								The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 								stored path is no longer accurate.
	 * @return
	 * @throws IOException
	 * @param <T> generic parameter for the {@link ImageServer}, usually BufferedImage
	 */
	public static <T> ImageData<T> readImageData(final File file, ImageServer<T> server) throws IOException {
		return readImageData(file.toPath(), server);
	}
	
	/**
	 * Read and initialize an {@link ImageData} from a data file.
	 *
	 * @param path path to data file
	 * @param server an ImageServer to use rather than any that might be stored within the serialized data.
	 *              Should be null to use the serialized path to build a new server.
	 * 				The main purpose of this is to make it possible to open ImageData where the original image location has been moved, so the
	 * 				stored path is no longer accurate.
	 * @return
	 * @throws IOException
	 * @param <T> generic parameter for the {@link ImageServer}, usually BufferedImage
	 */
	public static <T> ImageData<T> readImageData(final Path path, ImageServer<T> server) throws IOException {
		return readImageDataSerialized(path, server);
	}

	/**
	 * Write (binary) file containing {@link ImageData} for later use.
	 * 
	 * @param path
	 * @param imageData
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public static void writeImageData(final Path path, final ImageData<?> imageData) throws FileNotFoundException, IOException {
		writeImageData(path.toFile(), imageData);
	}
	
	/**
	 * Write (binary) file containing {@link ImageData} for later use.
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
			File fileCopy = Paths.get(file.toURI()).toFile();
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
			outStream.writeUTF("Data file version " + DATA_FILE_VERSION);
			
			// Write JSON object including QuPath version and ServerBuilder
			// Note that the builder may be null, in which case the server cannot be recreated
			var builder = imageData.getServerBuilder();
			String serverPath;
			if (builder == null) {
				var server = imageData.getServer();
				logger.warn("Server {} does not provide a builder - it will not be possible to recover the ImageServer from this data file", server);
				serverPath = server.getPath();
			} else {
				serverPath = imageData.getLastSavedPath();
			}
			var wrapper = ServerBuilderWrapper.create(builder, serverPath);
			String json = GsonTools.getInstance().toJson(wrapper);
			outStream.writeObject(json);
			
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
				if (serializableObject(entry.getValue()))
					map.put(entry.getKey(), entry.getValue());
				else
					logger.warn("Property not serializable and will not be saved!  Key: " + entry.getKey() + ", Value: " + entry.getValue());
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
	public static PathObjectHierarchy readHierarchy(final File file) throws IOException {
		return readHierarchy(file.toPath());
	}
	
	/**
	 * Read a hierarchy from a .qpdata file.
	 * 
	 * @param path
	 * @return
	 * @throws IOException 
	 */
	public static PathObjectHierarchy readHierarchy(final Path path) throws IOException {
		logger.info("Reading hierarchy from {}", path.getFileName().toString());
		try (var stream = Files.newInputStream(path)) {
			return readHierarchy(stream);
		}
	}
	
	/**
	 * Read a {@link PathObjectHierarchy} from a saved data file (omitting all other contents).
	 * 
	 * @param fileIn
	 * @return
	 * @throws IOException 
	 */
	public static PathObjectHierarchy readHierarchy(final InputStream fileIn) throws IOException {

		Locale locale = Locale.getDefault(Category.FORMAT);
		boolean localeChanged = false;

		try (ObjectInputStream inStream = createObjectInputStream(new BufferedInputStream(fileIn))) {
			
			if (!inStream.readUTF().startsWith("Data file version")) {
				logger.error("Input stream is not from a valid QuPath data file!");
			}
			while (true) {
				//					logger.debug("Starting read: " + inStream.available());
				try {
					// Try to read a relevant object from the stream
					Object input = inStream.readObject();
					logger.trace("Read object: {}", input);

					// Set locale - may be needed (although probably isn't...)
					if (input instanceof Locale) {
						if (input != locale) {
							Locale.setDefault(Category.FORMAT, (Locale)input);
							localeChanged = true;
						}
					} else if (input instanceof PathObjectHierarchy newHierarchy) {
						/* This would ideally be unnecessary, but it's needed to ensure that the PathObjectHierarchy
						 * has been property initialized.
						 * We can't count on the deserialized hierarchy being immediately functional.
						 */
						PathObjectHierarchy hierarchy = new PathObjectHierarchy();
						hierarchy.setHierarchy(newHierarchy);
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
	
	/**
	 * Read a list of {@link PathObject} from a file.
	 * In general {@link #readObjects(Path)} to be preferred for its more modern syntax.
	 * This exists for consistency with other QuPath methods that accept a {@link File} object as input.
	 * @param file
	 * @return
	 * @throws IOException
	 * @see #readObjects(Path)
	 */
	public static List<PathObject> readObjects(File file) throws IOException {
		return readObjects(file.toPath());
	}
	
	/**
	 * Read a list of {@link PathObject} from a file.
	 * <p>
	 * Currently, objects can be read from three main types of file:
	 * <ul>
	 * <li>GeoJSON, with extension .geojson or .json</li>
	 * <li>QuPath data file, with extension .qpdata</li>
	 * <li>A zip file containing one or more entries containing GeoJSON or QuPath serialized data</li>
	 * </ul>
	 * Note that this is subject to change, with support for other files possibly being added in the future.
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @see #readObjectsFromGeoJSON(InputStream)
	 */
	public static List<PathObject> readObjects(Path path) throws IOException {
		String name = path.getFileName().toString().toLowerCase();
		if (name.endsWith(".zip")) {
			// In case we have more than one compressed file, iterate through each entry
			try (var zipfs = FileSystems.newFileSystem(path, (ClassLoader) null)) {
				List<PathObject> allObjects = new ArrayList<>();
				for (var root : zipfs.getRootDirectories()) {
					var tempObjects = Files.walk(root).flatMap(p -> {
						if (Files.isRegularFile(p)) {
							try {
								var pathObjects = readObjects(p);
								if (pathObjects != null)
									return pathObjects.stream();
							} catch (Exception e) {
								logger.debug("Exception reading objects from {}", p);
							}
						}
						return new ArrayList<PathObject>().stream();
					}).toList();
					allObjects.addAll(tempObjects);
				}
				return allObjects;
			}
		}

		
		try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
			InputStream stream2;
			// Handle gzip compression
			if (name.endsWith(EXT_GZIP)) {
				stream2 = new GZIPInputStream(stream);
				name = name.substring(0, name.length()-EXT_GZIP.length());
			} else {
				stream2 = stream;
			}
			if (name.endsWith(EXT_JSON) || name.endsWith(EXT_GEOJSON)) {
				return readObjectsFromGeoJSON(stream2);
			}
			if (name.endsWith(EXT_DATA)) {
				return new ArrayList<>(readHierarchy(stream2).getRootObject().getChildObjects());	
			}
		}
			
		logger.debug("Unable to read objects from {}", path);
		return Collections.emptyList();
	}
	
	/**
	 * Read a list of {@link PathObject} from an input stream.
	 * <p>
	 * This will attempt to handle different GeoJSON representations by first deserializing to a JSON element.
	 * <p>
	 * If the element is a JSON object, its "type" property is checked and handled as follows
	 * <ul>
	 *  <li>"Feature": a single PathObject will be read</li>
	 *  <li>"FeatureCollection": a list of PathObject will be read</li>
	 *  <li>a valid geometry type: a Geometry will be read, converted to a ROI and subsequently to an annotation</li>
	 *  <li>anything else: the element is skipped, since a PathObject cannot be read from it
	 * </ul>
	 * If the element is a JSON array, its individual elements are handled as above.
	 * 
	 * @param stream the input stream containing JSON data to read
	 * @return a list containing any PathObjects that could be parsed from the stream
	 * @throws IOException
	 * @throws JsonSyntaxException 
	 * @throws JsonParseException 
	 */
	public static List<PathObject> readObjectsFromGeoJSON(InputStream stream) throws IOException, JsonSyntaxException, JsonParseException {
		var gson = GsonTools.getInstance();
		try (var reader = new InputStreamReader(new BufferedInputStream(stream), StandardCharsets.UTF_8)) {
			var element = gson.fromJson(reader, JsonElement.class);
			return GsonTools.parseObjectsFromGeoJSON(element);
		}
	}
	
	
	private static final String EXT_ZIP = ".zip";
	private static final String EXT_GZIP = ".gz";
	private static final String EXT_JSON = ".json";
	private static final String EXT_GEOJSON = ".geojson";
	private static final String EXT_DATA = ".qpdata";
	
	/**
	 * Get a list of known file extensions that may contain objects, optionally including compressed files.
	 * @param includeCompressed if true, include extensions for any compressed files that might contain objects (e.g. .zip)
	 * @return file extensions for files from which objects can be read.
	 * @see #readObjects(Path)
	 * @since v0.4.0
	 */
	public static List<String> getObjectFileExtensions(boolean includeCompressed) {
		if (includeCompressed)
			return Arrays.asList(EXT_JSON, EXT_GEOJSON, EXT_DATA, EXT_ZIP, EXT_GZIP);
		else
			return Arrays.asList(EXT_JSON, EXT_GEOJSON, EXT_DATA);
	}
	
	/**
	 * Get a list of known file extensions that may contain objects.
	 * @return file extensions for files from which objects can be read.
	 * @see #readObjects(Path)
	 * @deprecated use {@link #getObjectFileExtensions(boolean)} instead
	 */
	@Deprecated
	public static List<String> getObjectFileExtensions() {
		return getObjectFileExtensions(true);
	}
	
	/**
	 * Options to customize the export of PathObjects as GeoJSON.
	 */
	public enum GeoJsonExportOptions {
		/**
		 * Request pretty-printing for the JSON. This is more readable, but results in larger files.
		 */
		PRETTY_JSON,
		/**
		 * Optionally exclude measurements from objects. This can reduce the file size substantially if measurements are not needed.
		 */
		EXCLUDE_MEASUREMENTS,
		/**
		 * Request that objects are export as a FeatureCollection.
		 * If this is not specified, individual objects will be export as Features - in an array if necessary.
		 */
		FEATURE_COLLECTION
	}

	/**
	 * Export a collection of objects as a GeoJSON "FeatureCollection" to a file.
	 * @param file
	 * @param pathObjects
	 * @param options
	 * @throws IOException
	 * @see #exportObjectsAsGeoJSON(Path, Collection, GeoJsonExportOptions...)
	 */
	public static void exportObjectsAsGeoJSON(File file, Collection<? extends PathObject> pathObjects, GeoJsonExportOptions... options) throws IOException {
		exportObjectsAsGeoJSON(file.toPath(), pathObjects, options);
	}

	/**
	 * Export a collection of objects as a GeoJSON "FeatureCollection" to a file specified by its path.
	 * @param path
	 * @param pathObjects
	 * @param options
	 * @throws IOException
	 */
	public static void exportObjectsAsGeoJSON(Path path, Collection<? extends PathObject> pathObjects, GeoJsonExportOptions... options) throws IOException {
		String name = path.getFileName().toString();
		if (name.toLowerCase().endsWith(".zip")) {
			try (var zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				ZipEntry entry = new ZipEntry(GeneralTools.stripExtension(name) + ".geojson");
				zos.putNextEntry(entry);
				exportObjectsAsGeoJSON(zos, pathObjects, options);
				zos.closeEntry();
			}
		} else if (name.toLowerCase().endsWith(".gz")) {
			try (var gzos = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				exportObjectsAsGeoJSON(gzos, pathObjects, options);
			}
		} else {
			try (var stream = Files.newOutputStream(path)) {
				exportObjectsAsGeoJSON(stream, pathObjects, options);
			}
		}
	}

	/**
	 * Export a collection of objects as a GeoJSON "FeatureCollection" to an output stream.
	 * @param stream
	 * @param pathObjects
	 * @param options
	 * @throws IOException
	 */
	public static void exportObjectsAsGeoJSON(OutputStream stream, Collection<? extends PathObject> pathObjects, GeoJsonExportOptions... options) throws IOException {
		Collection<GeoJsonExportOptions> optionList = Arrays.asList(options);
		
		// If exclude measurements, 'transform' each PathObject to get rid of measurements
		if (optionList.contains(GeoJsonExportOptions.EXCLUDE_MEASUREMENTS))
			pathObjects = pathObjects.stream().map(e -> PathObjectTools.transformObject(e, null, false)).toList();
		
		var writer = new OutputStreamWriter(new BufferedOutputStream(stream), StandardCharsets.UTF_8);
		var gson = GsonTools.getInstance(optionList.contains(GeoJsonExportOptions.PRETTY_JSON));
		
		if (optionList.contains(GeoJsonExportOptions.FEATURE_COLLECTION))
			gson.toJson(FeatureCollection.wrap(pathObjects), writer);
		else if (pathObjects.size() == 1) {
			gson.toJson(pathObjects.iterator().next(), writer);
		} else {
			gson.toJson(pathObjects, new TypeToken<List<PathObject>>() {}.getType(), writer);				
		}
		writer.flush();
	}
	
	
	/**
	 * Get the extension of a file, or of all entries within a zip file.
	 * This is useful to check the contents of a file before attempting to read any of it,
	 * for example to confirm if the file is likely to contain images or objects.
	 * <p>
	 * Note that the identification of the zip file is based solely on the file extension, 
	 * but the attempt to extracted zipped entries will fail if the file is not a valid zip file.
	 * 
	 * @param path path representing a file or zip file
	 * @param zipExtensions optional list of extensions to treat as representing zip files. 
	 *                      If none are specified, the default is to use '.zip'. 
	 *                      If jar files should be included, use {@code unzippedExtensions(path, ".zip", ".jar")}.
	 * @return the file extensions for entries within a zip file, or the file extension of path itself if it is not 
	 *         identified as being a zip file. Note that all extensions are converted to lowercase.
	 * @throws IOException
	 * @implNote this returns original the extension for a non-zip file so that it may be used more easily within a stream.
	 * @since v0.4.0
	 */
	public static Set<String> unzippedExtensions(Path path, String... zipExtensions) throws IOException {
		var ext = GeneralTools.getExtension(path.getFileName().toString()).orElse(null);
		if (ext == null)
			return Collections.emptySet();
		var zipExts = zipExtensions.length == 0 ? Collections.singleton(EXT_ZIP) : Arrays.stream(zipExtensions).map(z -> z.toLowerCase()).collect(Collectors.toSet());
		ext = ext.toLowerCase();
		if (zipExts.contains(ext)) {
			// In case we have more than one compressed file, iterate through each entry
			Set<String> extensions = new LinkedHashSet<>();
			try (var zipfs = FileSystems.newFileSystem(path, (ClassLoader) null)) {
				for (var root : zipfs.getRootDirectories()) {
					var currentExtensions = Files.walk(root).map(p -> {
						if (Files.isRegularFile(p))
							return GeneralTools.getExtension(p.getFileName().toString()).orElse(null);
						else
							return null;
					}).filter(e -> e != null).collect(Collectors.toSet());
					extensions.addAll(currentExtensions);
				}
			}
			return extensions;
		} else if (ext.endsWith(".gz")) {
			return Collections.singleton(ext.substring(0, ext.length()-3));
		} else {
			return Collections.singleton(ext);
		}
	}
	
	private static boolean serializableObject(Object obj) {
		if (obj == null)
			return true;
		if (obj instanceof Serializable)
			return checkQuPathSerializableClass(obj.getClass());
		return false;
	}
	
	/**
	 * Check if a class is part of java.base or qupath.lib.
	 * @param serialClass
	 * @return
	 */
	private static boolean checkQuPathSerializableClass(Class<?> serialClass) {
		if (serialClass == null)
			return true;
		
		if (!(Serializable.class.isAssignableFrom(serialClass)))
			return false;
		
		// Require 
		if (checkClassLoader(serialClass)) {
		
			// Accept from java.base module
			var module = serialClass.getModule();
			if (module != null && Objects.equals(module.getName(), "java.base"))
				return true;

			// Accept from QuPath lib packages
			// TODO: Perform stricter check (and/or update for modularization)
			String packageName = serialClass.getPackageName();
			if (packageName != null && packageName.startsWith("qupath.lib"))
				return true;
		}
		logger.debug("Serialization not permitted for {}", serialClass);
		return false;
	}
	
	/**
	 * Check if a class was loaded by the bootstrap, platform or system classloader.
	 * @param serialClass
	 * @return
	 */
	private static boolean checkClassLoader(Class<?> serialClass) {
		if (serialClass == null)
			return true;
		var classloader = serialClass.getClassLoader();
		return classloader == null || classloader == ClassLoader.getPlatformClassLoader() || classloader == ClassLoader.getSystemClassLoader();
	}
	
	private static Status classLoaderInputFilter(FilterInfo filterInfo) {
		return checkClassLoader(filterInfo.serialClass()) ? Status.ALLOWED : Status.REJECTED;
	}
	
	private static Status qupathInputFilter(FilterInfo filterInfo) {
		return checkQuPathSerializableClass(filterInfo.serialClass()) ? Status.ALLOWED : Status.REJECTED;
	}
	
	
}
