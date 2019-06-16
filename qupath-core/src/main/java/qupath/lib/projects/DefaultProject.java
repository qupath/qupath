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

package qupath.lib.projects;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Data structure to store multiple images, relating these to a file system.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultProject implements Project<BufferedImage> {

	public final static String IMAGE_ID = "PROJECT_ENTRY_ID";

	private static String ext = "qpproj";
	
	private static Logger logger = LoggerFactory.getLogger(DefaultProject.class);
	
	private static Gson gson = new GsonBuilder()
			.setLenient()
			.serializeSpecialFloatingPointValues()
			.setPrettyPrinting()
			.create();
	
	private final String LATEST_VERSION = GeneralTools.getVersion();
	
	
	private String version = null;

	/**
	 * Base directory.
	 */
	private File dirBase;

	/**
	 * Project file.
	 */
	private File file;
	
	/**
	 * Project name.
	 */
	private String name = null;
	
	/**
	 * The URI the last time this project was saved. This can be helpful when resolving relative paths.
	 */
	private URI previousURI;
	
	/**
	 * Default classifications.
	 */
	private List<PathClass> pathClasses = new ArrayList<>();
	
	private boolean maskNames = false;
	
	private Map<String, DefaultProjectImageEntry> images = new LinkedHashMap<>();
	
	private long creationTimestamp;
	private long modificationTimestamp;
	
	DefaultProject(final File file) {
		this.file = file;
		if (file.isDirectory()) {
			this.dirBase = file;
			this.file = getUniqueFile(dirBase, "project", ext);
		} else
			this.dirBase = file.getParentFile();
		creationTimestamp = System.currentTimeMillis();
		modificationTimestamp = System.currentTimeMillis();
	}
	
	
//	public List<String> validateLocalPaths(boolean relativize) {
//		var missing = new ArrayList<String>();
//		var uriCurrent = getBaseDirectory().toURI().resolve("..");
//		var lastParentURI = lastURI == null ? null : lastURI.resolve("..");
//		var iterator = images.entrySet().iterator();
//		var newMap = new LinkedHashMap<String, DefaultProjectImageEntry>();
//		while (iterator.hasNext()) {
//			var mapEntry = iterator.next();
//			var entry = mapEntry.getValue();
//			var path = mapEntry.getKey();
//			if (path.startsWith("file")) {
//				try {
//					var uri = GeneralTools.toURI(path);
//					var tempPath = GeneralTools.toPath(uri);
//					if (Files.exists(tempPath)) {
//						newMap.put(path, entry);
//						continue;
//					}
//					if (relativize && lastParentURI != null) {
//						var uriRelative = lastParentURI.relativize(uri);
//						uri = uriCurrent.resolve(uriRelative);
//						tempPath = GeneralTools.toPath(uri);
//						if (Files.exists(tempPath)) {
//							logger.info("Updating path {} to {}", path, uri);
//							var serverPath = uri.toString();
//							newMap.put(serverPath, 
//									new DefaultProjectImageEntry(serverPath,
//											entry.getImageName(),
//											entry.getUniqueName(),
//											entry.getDescription(),
//											entry.getMetadataMap()));
//							continue;
//						}
//					}
//					newMap.put(path, entry);
//					missing.add(path);
//				} catch (URISyntaxException e) {
//					logger.warn("Failed to create URI for " + path);
//				}
//			} else
//				newMap.put(path, entry);
//		}
//		if (!images.equals(newMap)) {
//			images.clear();
//			images.putAll(newMap);
//		}
//		return missing;
//	}
	
	@Override
	public URI getPreviousURI() {
		return previousURI;
	}
	
	
	/**
	 * Get a File with a unique name, derived by appending an integer to the name if necessary.
	 * <p>
	 * The result will be {@code new File(dir, name+ext)} if possible, or 
	 * {@code new File(dir, name+"-"+count+ext)} where {@code count} is the lowest positive integer 
	 * necessary to give a unique file.
	 * 
	 * @param dir
	 * @param name
	 * @param ext
	 * @return
	 */
	synchronized static File getUniqueFile(File dir, String name, String ext) {
		if (!ext.startsWith("."))
			ext = "." + ext;
		File file = new File(dir, name + ext);
		int count = 0;
		while (file.exists()) {
			count++;
			file = new File(dir, name + "-" + count + ext);
		}
		return file;
	}
	
	
	static DefaultProject loadFromFile(File file) throws IOException {
		var project = new DefaultProject(file);
		project.loadProject();
		return project;
	}
	
	
	/**
	 * Get an unmodifiable list representing the <code>PathClass</code>es associated with this project.
	 * @return
	 */
	@Override
	public List<PathClass> getPathClasses() {
		return Collections.unmodifiableList(pathClasses);
	}
	
	/**
	 * Update the available PathClasses.
	 * 
	 * @param pathClasses
	 * @return <code>true</code> if the stored values changed, false otherwise.
	 */
	@Override
	public boolean setPathClasses(Collection<? extends PathClass> pathClasses) {
		if (this.pathClasses.size() != pathClasses.size() || this.pathClasses.containsAll(pathClasses)) {
			this.pathClasses.clear();
			this.pathClasses.addAll(pathClasses);			
		}
		// Write path classes
		try {
			logger.debug("Writing PathClasses to project");
			writePathClasses(this.pathClasses);
		} catch (IOException e) {
			logger.warn("Unable to write classes to project", e);
		}
		return true;
	}

	private boolean addImage(final ProjectImageEntry<BufferedImage> entry) {
		if (entry instanceof DefaultProjectImageEntry)
			return addImage((DefaultProjectImageEntry)entry);
		try {
			return addImage(new DefaultProjectImageEntry(entry.buildImageServer(), null, entry.getDescription(), entry.getMetadataMap()));
		} catch (IOException e) {
			logger.error("Unable to add entry " + entry, e);
			return false;
		}
	}
	
	private boolean addImage(final DefaultProjectImageEntry entry) {
		if (images.containsKey(entry.getServerPath()))
			return false;
		images.put(entry.getID(), entry);
		return true;
	}
	
	private File getFile() {
		return file;
	}
	
	@Override
	public Path getPath() {
		return getFile().toPath();
	}
	
	@Override
	public URI getURI() {
		return getFile().toURI();
	}
	
	private File getBaseDirectory() {
		return dirBase;
	}
	
	private Path getBasePath() {
		return getBaseDirectory().toPath();
	}
	
	private Path getScriptsPath() {
		return Paths.get(getBasePath().toString(), "scripts");
	}
	
	private Path getClassifiersPath() {
		return Paths.get(getBasePath().toString(), "classifiers");
	}
	
	private Path getPixelClassifiersPath() {
		return Paths.get(getClassifiersPath().toString(), "pixel_classifiers");
	}

	private Path getObjectClassifiersPath() {
		return Paths.get(getClassifiersPath().toString(), "object_classifiers");
	}
	
	List<String> listFilenames(Path path, String ext) throws IOException {
		if (!Files.isDirectory(path))
			return Collections.emptyList();
		return Files.list(path).filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext)).map(p -> nameWithoutExtension(p, ext)).collect(Collectors.toList());
	}
	
	String nameWithoutExtension(Path path, String ext) {
		String name = path.getFileName().toString();
		if (name.endsWith(ext))
			return name.substring(0, name.length()-ext.length());
		return name;
	}
	
	public int size() {
		return images.size();
	}

	@Override
	public boolean isEmpty() {
		return images.isEmpty();
	}
	
	@Override
	public ProjectImageEntry<BufferedImage> addImage(final ImageServer<BufferedImage> server) throws IOException {
		var entry = new DefaultProjectImageEntry(server, null, null, null);
		if (addImage(entry)) {
			return entry;
		}
		return null;
	}
	
	@Override
	public ProjectImageEntry<BufferedImage> getEntry(final ImageData<BufferedImage> imageData) {
		return images.get(imageData.getProperty(IMAGE_ID));
	}

	public boolean addImage(final String path) {
		try {
			ImageServer<BufferedImage> server = ImageServerProvider.buildServer(path, BufferedImage.class);
			boolean changes = addImage(server) != null;
			server.close();
			return changes;
		} catch (Exception e) {
			logger.error("Error adding image: {} ({})", path, e.getLocalizedMessage());
			return false;
		}
	}
	
	@Override
	public void removeImage(final ProjectImageEntry<?> entry, boolean removeAllData) {
		removeImage(entry.getServerPath());
		if (removeAllData && entry instanceof DefaultProjectImageEntry) {
			((DefaultProjectImageEntry)entry).moveDataToTrash();
		}
	}

	@Override
	public void removeAllImages(final Collection<ProjectImageEntry<BufferedImage>> entries, boolean removeAllData) {
		for (ProjectImageEntry<BufferedImage> entry : entries)
			removeImage(entry, removeAllData);
	}
	
	public void removeImage(final String path) {
		images.remove(path);
	}
	
	@Override
	public void syncChanges() throws IOException {
		writeProject(getFile());
		writePathClasses(pathClasses);
//		if (file.isDirectory())
//			file = new File(dirBase, "project.qpproj");
//		var json = new GsonBuilder().setLenient().setPrettyPrinting().create().toJson(this);
//		Files.writeString(file.toPath(), json);
//		logger.warn("Syncing project not yet implemented!");
	}
	
	@Override
	public boolean getMaskImageNames() {
		return maskNames;
	}
	
	@Override
	public void setMaskImageNames(boolean maskNames) {
		this.maskNames = maskNames;
	}

	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	@Override
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		List<ProjectImageEntry<BufferedImage>> list = new ArrayList<>(images.values());
//		list.sort(ImageEntryComparator.instance);
		return list;
	}
	
	public ImageServer<BufferedImage> buildServer(final ProjectImageEntry<BufferedImage> entry) throws URISyntaxException, IOException {
		return ImageServerProvider.buildServer(entry.getServerPath(), BufferedImage.class);
	}
	
	
	@Override
	public String getName() {
		if (name != null)
			return name;
		if (dirBase == null || !dirBase.isDirectory()) {
			return "(Project directory missing)";
		}
		if (file != null && file.exists() && file != dirBase) {
			return dirBase.getName() + "/" + file.getName();
		}
		return dirBase.getName();
	}
	
	@Override
	public String toString() {
		return "Project: " + Project.getNameFromURI(getURI());
	}
	
	@Override
	public long getCreationTimestamp() {
		return creationTimestamp;
	}
	
	@Override
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}
	
	
	private String EXT_SCRIPT = ".groovy";
//	private String EXT_OBJECT_CLASSIFIER = ".classifier.pixels.json";
//	private String EXT_PIXEL_CLASSIFIER = ".classifier.objects.json";
	
	Path ensureDirectoryExists(Path path) throws IOException {
		if (!Files.isDirectory(path))
			Files.createDirectories(path);
		return path;
	}
	
	public List<String> listScripts() throws IOException {
		return listFilenames(getScriptsPath(), EXT_SCRIPT);
	}
	
	public String loadScript(String name) throws IOException {
		var path = Paths.get(getScriptsPath().toString(), name + EXT_SCRIPT);
		if (Files.exists(path))
			return Files.readString(path);
		throw new IOException("No script found with name '" + name + "'");
	}
	
	public void saveScript(String name, String script) throws IOException {
		var path = Paths.get(ensureDirectoryExists(getScriptsPath()).toString(), name + EXT_SCRIPT);
		Files.writeString(path, script, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
	}
	
	private AtomicLong counter = new AtomicLong(0L);
	
	/**
	 * Request all the (String) URI's found within a JSON object, searching recursively.
	 * 
	 * @param element
	 * @param uris
	 * @return
	 */
	static Collection<String> getURIsRecursive(JsonElement element, Collection<String> uris) {
		if (uris == null) {
			uris = new ArrayList<>();
		}
		if (element instanceof JsonObject) {
			JsonObject obj = (JsonObject)element;
			String uri = getStringField(obj, "uri");
			if (uri != null)
				uris.add(uri);			
			for (var entry : obj.entrySet()) {
				if (entry.getValue().isJsonObject())
					getURIsRecursive((JsonObject)entry.getValue(), uris);
			}
		} else if (element instanceof JsonArray) {
			JsonArray array = (JsonArray)element;
			for (int i = 0; i < array.size(); i++)
				getURIsRecursive(((JsonArray)array).get(i), uris);
		}
		return uris;
	}
	
	/**
	 * Get a String field with a given name from an object (non-recursive), or null if the field is not present.
	 * 
	 * @param obj
	 * @param name
	 * @return
	 */
	static String getStringField(JsonObject obj, String name) {
		if (obj.has(name)) {
			JsonElement element = obj.get(name);
			if (element.isJsonPrimitive()) {
				JsonPrimitive primitive = element.getAsJsonPrimitive();
				if (primitive.isString())
					return primitive.getAsString();			
			}
		}
		return null;
	}
	
	/**
	 * Request all the (String) URI's found within a JSON object, searching recursively.
	 * 
	 * @param obj
	 * @param replacements
	 * @param nReplacements running count of the number of replacements so far
	 * @return
	 */
	static int replaceURIsRecursive(JsonObject obj, Map<String, String> replacements, int nReplacements) {
		String uri = getStringField(obj, "uri");
		if (uri != null) {
			String replacement = replacements.getOrDefault(uri, null);
			if (replacement != null) {
				obj.addProperty("uri", replacement);
				nReplacements++;
			}
		}
		for (var entry : obj.entrySet()) {
			if (entry.getValue().isJsonObject())
				nReplacements = replaceURIsRecursive((JsonObject)entry.getValue(), replacements, nReplacements);
		}
		return nReplacements;
	}
	
	static int replaceURIsRecursive(JsonElement element, Map<String, String> replacements, int nReplacements) {
		if (element instanceof JsonObject)
			nReplacements = replaceURIsRecursive((JsonObject)element, replacements, nReplacements);
		else if (element instanceof JsonArray) {
			JsonArray array = (JsonArray)element;
			for (int i = 0; i < array.size(); i++)
				nReplacements = replaceURIsRecursive(array.get(i), replacements, nReplacements);
		}
		return nReplacements;
	}
	
	
	/**
	 * Class to represent an image entry within a project.
	 * <p>
	 * This stores the path to the image, and some optional metadata.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	class DefaultProjectImageEntry implements ProjectImageEntry<BufferedImage> {
		
		/**
		 * JSON representation of the server.
		 * Transient because it is stored separately in its own JSON file.
		 */
		private transient JsonElement server;
		
		/**
		 * Result of server.getPath()
		 */
		private String serverPath;
		
		/**
		 * Unique name that will be used to identify associated data files.
		 */
		private long entryID;

		/**
		 * Randomized name that will be used when masking image names.
		 */
		private String randomizedName = UUID.randomUUID().toString();
		
		/**
		 * Image name to display.
		 */
		private String imageName;
		
		/**
		 * Image description to display.
		 */
		private String description;

		/**
		 * Map of associated metadata for the entry.
		 */
		private Map<String, String> metadata = new LinkedHashMap<>();
		
		
		DefaultProjectImageEntry(final ImageServer<BufferedImage> server, final Long entryID, final String description, final Map<String, String> metadataMap) throws IOException {
			this.server = ImageServers.toJsonElement(server, false);
			if (entryID == null)
				this.entryID = counter.incrementAndGet();
			else
				this.entryID = entryID;
			
			// If successful, write the server (including metadata)
			this.server = ImageServers.toJsonElement(server, true);
			syncServer();
			this.serverPath = server.getPath();
			this.imageName = ServerTools.getDisplayableImageName(server);
			
			if (description != null)
				setDescription(description);
			
			if (metadataMap != null)
				metadata.putAll(metadataMap);		
		}
		
		DefaultProjectImageEntry(final DefaultProjectImageEntry entry) {
			this.server = entry.server;
			this.entryID = entry.entryID;
			this.serverPath = entry.serverPath;
			this.imageName = entry.imageName;
			this.description = entry.description;
			this.metadata = entry.metadata;
		}
		
		private transient ImageResourceManager<BufferedImage> imageManager = null;
		
		@Override
		public synchronized ProjectResourceManager<ImageServer<BufferedImage>> getImages() {
			if (imageManager == null) {
				imageManager = new ImageResourceManager<>(getPath(), BufferedImage.class);
			}
			return imageManager;
		}
		
		public String getID() {
			return Long.toString(entryID);
		}
		
		@Override
		public Collection<String> getServerURIs() throws IOException {
			ensureJsonServerCached();
			return getURIsRecursive(server, new TreeSet<>());
		}
		
		@Override
		public int updateServerURIs(Map<String, String> replacements) throws IOException {
			ensureJsonServerCached();
			int n = replaceURIsRecursive(server, replacements, 0);
			if (n > 0)
				syncServer();
			return n;
		}
		
		
		String getUniqueName() {
			return Long.toString(entryID);
		}
		
		@Override
		public String getServerPath() {
			return serverPath;
		}

		@Override
		public String getImageName() {
			if (maskNames)
				return randomizedName;
			return imageName;
		}
		
		@Override
		public String getOriginalImageName() {
			return imageName;
		}

		@Override
		public String toString() {
			String s = getImageName();
			if (!metadata.isEmpty())
				s += " - " + getMetadataSummaryString();
			return s;
		}
		
		@Override
		public void setImageName(String name) {
			this.imageName = name;
		}
		
		@Override
		public String removeMetadataValue(final String key) {
			return metadata.remove(key);
		}
		
		@Override
		public String getMetadataValue(final String key) {
			return metadata.get(key);
		}

		@Override
		public String putMetadataValue(final String key, final String value) {
			return metadata.put(key, value);
		}
		
		@Override
		public boolean containsMetadata(final String key) {
			return metadata.containsKey(key);
		}
		
		@Override
		public String getDescription() {
			return description;
		}
		
		@Override
		public void setDescription(final String description) {
			this.description = description;
		}
		
		@Override
		public void clearMetadata() {
			this.metadata.clear();
		}
		
		@Override
		public Map<String, String> getMetadataMap() {
			return Collections.unmodifiableMap(metadata);
		}
		
		@Override
		public Collection<String> getMetadataKeys() {
			return Collections.unmodifiableSet(metadata.keySet());
		}
		
		boolean ensureJsonServerCached() throws IOException {
			if (server == null) {
				var pathServerMetadata = getServerMetadataPath();
				if (Files.exists(pathServerMetadata)) {
					try (var reader = Files.newBufferedReader(pathServerMetadata)) {
						server = new JsonParser().parse(reader);
					}
				}				
			}
			return server != null;
		}
		
		@Override
		public ImageServer<BufferedImage> buildImageServer() throws IOException {
			ensureJsonServerCached();
			return ImageServers.fromJson(server);
		}
		
		private Path getEntryPath(boolean create) throws IOException {
			var path = getEntryPath();
			if (create && !Files.exists(path))
				Files.createDirectories(path);
			return path;
		}
		
		@Override
		public Path getEntryPath() {
			return Paths.get(getBasePath().toString(), "data", getUniqueName());
		}
		
		private Path getImageDataPath() {
			return Paths.get(getEntryPath().toString(), "data.qpdata");
		}
		
		private Path getServerMetadataPath() {
			return Paths.get(getEntryPath().toString(), "server.json");
		}
		
		private Path getDataSummaryPath() {
			return Paths.get(getEntryPath().toString(), "summary.json");
		}
		
		private Path getThumbnailPath() {
			return Paths.get(getEntryPath().toString(), "thumbnail.jpg");
		}

		@Override
		public synchronized ImageData<BufferedImage> readImageData() throws IOException {
			Path path = getImageDataPath();
			var server = buildImageServer();
			if (server == null)
				return null;
			ImageData<BufferedImage> imageData = null;
			if (Files.exists(path)) {
				try (var stream = Files.newInputStream(path)) {
					imageData = PathIO.readImageData(stream, null, server, BufferedImage.class);
					imageData.setLastSavedPath(path.toString(), true);
					return imageData;
				} catch (IOException e) {
					logger.error("Error reading image data from " + path, e);
				}
			}
			if (imageData == null)
				imageData = new ImageData<>(server);
			imageData.setProperty(IMAGE_ID, getID()); // Required to be able to test for the ID later
			return imageData;
		}

		@Override
		public synchronized void saveImageData(ImageData<BufferedImage> imageData) throws IOException {
			// Get entry path, creating if needed
			var pathEntry = getEntryPath(true);
			var pathData = getImageDataPath();
			
			// If we already have a file, back it up first
			var pathBackup = Paths.get(pathData.toString() + ".bkp");
			if (Files.exists(pathData))
				Files.move(pathData, pathBackup, StandardCopyOption.REPLACE_EXISTING);
			
			// Write to a temp file first
			long timestamp = 0L;
			try (var stream = Files.newOutputStream(pathData)) {
				PathIO.writeImageData(stream, imageData);
				imageData.setLastSavedPath(pathData.toString(), true);
				timestamp = Files.getLastModifiedTime(pathData).toMillis();
				// Delete backup file if it exists
				if (Files.exists(pathBackup))
					Files.delete(pathBackup);
			} catch (IOException e) {
				// Try to restore the backup
				Files.move(pathBackup, pathData, StandardCopyOption.REPLACE_EXISTING);				
				throw e;
			}
			
			// If successful, write the server (including metadata)
			this.server = ImageServers.toJsonElement(imageData.getServer(), true);
			syncServer();
			
			var pathSummary = getDataSummaryPath();
			try (var out = Files.newBufferedWriter(pathSummary, StandardOpenOption.CREATE)) {
				gson.toJson(new ImageDataSummary(imageData, timestamp), out);
			}			
			
			// A small text file with the name & path can help with generic searches using operating system 
			// (e.g. searching on Windows for the project entry)
			var pathDetails = Paths.get(pathEntry.toString(), "image.txt");
			var sb = new StringBuilder();
			sb.append("name=").append(getImageName()).append(System.lineSeparator());
			sb.append("path=").append(getServerPath()).append(System.lineSeparator());
			Files.writeString(pathDetails, sb.toString());
		}
		
		void syncServer() throws IOException {
			if (server != null) {
				var pathServerMetadata = getServerMetadataPath();
				ensureDirectoryExists(pathServerMetadata.getParent());
				try (var out = Files.newBufferedWriter(pathServerMetadata, StandardOpenOption.CREATE)) {
					gson.toJson(server, out);
				}
			} else
				logger.warn("Server is null - cannot synchronize!");
		}
		

		@Override
		public boolean hasImageData() {
			return Files.exists(getImageDataPath());
		}
		
		@Override
		public synchronized PathObjectHierarchy readHierarchy() throws IOException {
			var path = getImageDataPath();
			if (Files.exists(path)) {
				try (var stream = Files.newInputStream(path)) {
					return PathIO.readHierarchy(stream);
				}
			}
			return new PathObjectHierarchy();
		}
		
		
		@Override
		public String getSummary() {
			StringBuilder sb = new StringBuilder();
			sb.append(getImageName()).append("\n\n");
			if (!getMetadataMap().isEmpty()) {
				for (Entry<String, String> mapEntry : getMetadataMap().entrySet()) {
					sb.append(mapEntry.getKey()).append(":\t").append(mapEntry.getValue()).append("\n");
				}
				sb.append("\n");
			}

			File file = getImageDataPath().toFile();
			if (file != null && file.exists()) {
				double sizeMB = file.length() / 1024.0 / 1024.0;
				sb.append(String.format("Data file:\t%.2f MB", sizeMB)).append("\n");
//				sb.append("Modified:\t").append(dateFormat.format(new Date(file.lastModified())));
			} else
				sb.append("No data file");
			return sb.toString();
		}

		@Override
		public synchronized BufferedImage getThumbnail() throws IOException {
			var path = getThumbnailPath();
			if (Files.exists(path)) {
				try (var stream = Files.newInputStream(path)) {
					return ImageIO.read(stream);
				}
			}
			return null;
		}

		@Override
		public synchronized void setThumbnail(BufferedImage img) throws IOException {
			getEntryPath(true);
			var path = getThumbnailPath();
			try (var stream = Files.newOutputStream(path)) {
				ImageIO.write(img, "JPEG", stream);
			}
		}
		
		synchronized boolean moveDataToTrash() {
			Path path = getEntryPath();
			if (!Files.exists(path))
				return true;
			if (Desktop.isDesktopSupported()) {
				var desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH))
					return desktop.moveToTrash(path.toFile());
			}
			logger.warn("Unable to move {} to trash - please delete manually if required", path);
			return false;
		}
		
	}
	
	
	static class ImageDataSummary {
		
		private long timestamp;
		private ImageType imageType;
		private ServerSummary server;
		private HierarchySummary hierarchy;
		
		ImageDataSummary(ImageData<?> imageData, long timestamp) {
			this.imageType = imageData.getImageType();
			this.server = new ServerSummary(imageData.getServer());
			this.timestamp = timestamp;
			this.hierarchy = new HierarchySummary(imageData.getHierarchy());
		}
		
		@Override
		public String toString() {
			return gson.toJson(this);
		}
		
	}
	
	
	static class ServerSummary {
		
		private int width;
		private int height;
		private int sizeC;
		private int sizeZ;
		private int sizeT;
		
		ServerSummary(ImageServer<?> server) {
			this.width = server.getWidth();
			this.height = server.getHeight();
			this.sizeC = server.nChannels();
			this.sizeZ = server.nZSlices();
			this.sizeT = server.nTimepoints();
		}
		
	}
	
	
	static class HierarchySummary {
		
		private int nObjects;
		private Integer nTMACores;
		private Map<String, Long> objectTypeCounts;
		private Map<String, Long> annotationClassificationCounts;
		private Map<String, Long> detectionClassificationCounts;
		
		HierarchySummary(PathObjectHierarchy hierarchy) {
			Collection<PathObject> pathObjects = hierarchy.getObjects(null, null);
			this.nObjects = pathObjects.size();
			objectTypeCounts = pathObjects.stream()
					.collect(Collectors.groupingBy(p -> PathObjectTools.getSuitableName(p.getClass(), true), Collectors.counting()));
			annotationClassificationCounts = pathObjects.stream().filter(p -> p.isAnnotation())
					.collect(Collectors.groupingBy(p -> pathClassToString(p.getPathClass()), Collectors.counting()));
			detectionClassificationCounts = pathObjects.stream().filter(p -> p.isDetection())
					.collect(Collectors.groupingBy(p -> pathClassToString(p.getPathClass()), Collectors.counting()));
		}
		
		static String pathClassToString(PathClass pathClass) {
			return pathClass == null ? "Unclassified" : pathClass.toString();
		}
		
	}
	
	
	/**
	 * Write project, setting the name of the project file.
	 * 
	 * @param fileProject
	 */
	<T> void writeProject(final File fileProject) throws IOException {
		if (fileProject == null) {
			throw new IOException("No file found, cannot write project: " + this);
		}

		Gson gson = new GsonBuilder()
				.serializeSpecialFloatingPointValues()
				.setPrettyPrinting()
				.create();
		
//		List<PathClass> pathClasses = project.getPathClasses();
//		JsonArray pathClassArray = null;
//		if (!pathClasses.isEmpty()) {
//			pathClassArray = new JsonArray();
//			for (PathClass pathClass : pathClasses) {
//				JsonObject jsonEntry = new JsonObject();
//				jsonEntry.addProperty("name", pathClass.toString());
//				jsonEntry.addProperty("color", pathClass.getColor());
//				pathClassArray.add(jsonEntry);
//			}
//		}		
		
		JsonObject builder = new JsonObject();
		builder.addProperty("version", LATEST_VERSION);
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", getModificationTimestamp());
		builder.addProperty("uri", fileProject.toURI().toString());
		builder.addProperty("lastID", counter.get());
//		if (pathClassArray != null) {
//			builder.add("pathClasses", pathClassArray);			
//		}
		
//		JsonElement array = new JsonArray();
//		for (var entry : images.values()) {
//			entry.
//			builder.add("images", array);
//		}
		builder.add("images", gson.toJsonTree(images.values()));
		

		// If we already have a project, back it up
		if (fileProject.exists()) {
			File fileBackup = new File(fileProject.getAbsolutePath() + ".backup");
			if (fileProject.renameTo(fileBackup))
				logger.debug("Existing project file backed up at {}", fileBackup.getAbsolutePath());
		}

		// Write project
		try (PrintWriter writer = new PrintWriter(fileProject)) {
			writer.write(gson.toJson(builder));
		}
	}
	
	
	void loadProject() throws IOException {
		File fileProject = getFile();
		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			creationTimestamp = element.get("createTimestamp").getAsLong();
			modificationTimestamp = element.get("modifyTimestamp").getAsLong();
			previousURI = new URI(element.get("uri").getAsString());
			
			if (element.has("version"))
				version = element.get("version").getAsString();
			
			if (version == null || version.equals("v0.2.0-m1") || version.equals("v0.2.0-m2") || !element.has("lastID"))
				throw new IOException("Older projects are not supported in this version of QuPath, sorry!");
						
			long lastID = 0;
			List<DefaultProjectImageEntry> images = element.has("images") ? gson.fromJson(element.get("images"), new TypeToken<ArrayList<DefaultProjectImageEntry>>() {}.getType()) : Collections.emptyList();
			for (DefaultProjectImageEntry entry: images) {
				addImage(new DefaultProjectImageEntry(entry)); // Need to construct a new one to ensure project is set
				lastID = Math.max(lastID, entry.entryID);
			}
			
			if (element.has("lastID")) {
				lastID = Math.max(lastID, element.get("lastID").getAsLong());
			}
			counter.set(lastID);

			
			pathClasses.addAll(loadPathClasses());
			
//			List<String> troublesome = validateLocalPaths(true);
//			if (!troublesome.isEmpty()) {
//				logger.warn("Could not find {} image(s): {}", troublesome.size(), troublesome);
//			}

		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}
	
	
	void writePathClasses(Collection<PathClass> pathClasses) throws IOException {
		
		var path = Paths.get(ensureDirectoryExists(getClassifiersPath()).toString(), "classes.json");
		if (pathClasses == null || pathClasses.isEmpty()) {
			Files.deleteIfExists(path);
			return;
		}
		
		JsonArray pathClassArray = new JsonArray();
		for (PathClass pathClass : pathClasses) {
			JsonObject jsonEntry = new JsonObject();
			jsonEntry.addProperty("name", pathClass.toString());
			jsonEntry.addProperty("color", pathClass.getColor());
			pathClassArray.add(jsonEntry);
		}
		
		var element = new JsonObject();
		element.add("pathClasses", pathClassArray);
		try (var writer = Files.newBufferedWriter(path)) {
			gson.toJson(element, writer);
		}
	}
	
	
	Collection<PathClass> loadPathClasses() throws IOException {
		var path = Paths.get(ensureDirectoryExists(getClassifiersPath()).toString(), "classes.json");
		if (!Files.isRegularFile(path))
			return null;
		try (var reader = Files.newBufferedReader(path)) {
			var element = gson.fromJson(reader, JsonObject.class);
			JsonElement pathClassesElement = element.get("pathClasses");
			if (pathClassesElement != null && pathClassesElement.isJsonArray()) {
				JsonArray pathClassesArray = pathClassesElement.getAsJsonArray();
				List<PathClass> pathClasses = new ArrayList<>();
				for (int i = 0; i < pathClassesArray.size(); i++) {
					JsonObject pathClassObject = pathClassesArray.get(i).getAsJsonObject();
					if (pathClassObject.has("name")) {
						String name = pathClassObject.get("name").getAsString();
						Integer color = null;
						if (pathClassObject.has("color") && !pathClassObject.get("color").isJsonNull()) {
							color = pathClassObject.get("color").getAsInt();							
						}
						PathClass pathClass = PathClassFactory.getPathClass(name, color);
						if (color != null)
							pathClass.setColor(color); // Make sure we have the color we want
						pathClasses.add(pathClass);
					}
				}
				return pathClasses;
			} else
				return null;
		}
	}


	@Override
	public String getVersion() {
		return version;
	}
	
	@Override
	public ProjectResourceManager<String> getScripts() {
		return new StringFileResourceManager(getScriptsPath(), ".groovy");
	}


	@Override
	public ProjectResourceManager<PathObjectClassifier> getObjectClassifiers() {
		return new SerializableFileResourceManager(getObjectClassifiersPath(), PathObjectClassifier.class);
	}


	@Override
	public ProjectResourceManager<PixelClassifier> getPixelClassifiers() {
		return new JsonFileResourceManager(getPixelClassifiersPath(), PixelClassifier.class);
	}


	@Override
	public Project<BufferedImage> createSubProject(String name, Collection<ProjectImageEntry<BufferedImage>> entries) {
		if (!name.endsWith(ext)) {
			if (name.endsWith("."))
				name = name + ext;
			else
				name = name + "." + ext;
		}
		File file = new File(getBaseDirectory(), name);
		DefaultProject project = new DefaultProject(file);
		boolean changes = false;
		for (ProjectImageEntry<BufferedImage> entry : entries)
			changes = project.addImage(entry) | changes;
		return project;
	}
	
}
