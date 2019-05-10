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

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
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

import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.URLTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.RotatedImageServer;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Projects as temporarily used for v0.2.0-m1 and v0.2.0-m2.
 * <p>
 * Included here as a temporary measure to ease transition to newer-style projects.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
class MilestoneProject {
	
	private static Logger logger = LoggerFactory.getLogger(MilestoneProject.class);
	
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
	
	private URI lastURI;
	
	/**
	 * Default classifications.
	 */
	private List<PathClass> pathClasses = new ArrayList<>();
	
	private boolean maskNames = false;
	
	private AtomicLong count = new AtomicLong(0L);
	
	private Map<String, DefaultProjectImageEntry> images = new LinkedHashMap<>();
	
	private long creationTimestamp;
	private long modificationTimestamp;
	
	MilestoneProject(final File file) {
		this.file = file;
		if (file.isDirectory()) {
			this.dirBase = file;
			this.file = getUniqueFile(dirBase, "project", ".qpproj");
		} else
			this.dirBase = file.getParentFile();
		creationTimestamp = System.currentTimeMillis();
		modificationTimestamp = System.currentTimeMillis();
	}
	
	
	public List<String> validateLocalPaths(boolean relativize) {
		var missing = new ArrayList<String>();
		var uriCurrent = getBaseDirectory().toURI().resolve("..");
		var lastParentURI = lastURI == null ? null : lastURI.resolve("..");
		var iterator = images.entrySet().iterator();
		var newMap = new LinkedHashMap<String, DefaultProjectImageEntry>();
		while (iterator.hasNext()) {
			var mapEntry = iterator.next();
			var entry = mapEntry.getValue();
			var path = mapEntry.getKey();
			if (path.startsWith("file")) {
				try {
					var uri = GeneralTools.toURI(path);
					var tempPath = GeneralTools.toPath(uri);
					if (Files.exists(tempPath)) {
						newMap.put(path, entry);
						continue;
					}
					if (relativize && lastParentURI != null) {
						var uriRelative = lastParentURI.relativize(uri);
						uri = uriCurrent.resolve(uriRelative);
						tempPath = GeneralTools.toPath(uri);
						if (Files.exists(tempPath)) {
							logger.info("Updating path {} to {}", path, uri);
							var serverPath = uri.toString();
							newMap.put(serverPath, 
									new DefaultProjectImageEntry(serverPath,
											entry.getImageName(),
											entry.getUniqueName(),
											entry.getDescription(),
											entry.getMetadataMap()));
							continue;
						}
					}
					newMap.put(path, entry);
					missing.add(path);
				} catch (URISyntaxException e) {
					logger.warn("Failed to create URI for " + path);
				}
			} else
				newMap.put(path, entry);
		}
		if (!images.equals(newMap)) {
			images.clear();
			images.putAll(newMap);
		}
		return missing;
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
	
	
	static MilestoneProject loadFromFile(File file) {
		var project = new MilestoneProject(file);
		project.loadProject();
		return project;
	}
	
	
	/**
	 * Get an unmodifiable list representing the <code>PathClass</code>es associated with this project.
	 * @return
	 */
	public List<PathClass> getPathClasses() {
		return Collections.unmodifiableList(pathClasses);
	}
	
	/**
	 * Update the available PathClasses.
	 * 
	 * @param pathClasses
	 * @return <code>true</code> if the stored values changed, false otherwise.
	 */
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

	public boolean addImage(final ProjectImageEntry<BufferedImage> entry) {
		return addImage(new DefaultProjectImageEntry(entry.getServerPath(), entry.getOriginalImageName(), null, entry.getDescription(), entry.getMetadataMap()));
	}
	
	
	private boolean addImage(final DefaultProjectImageEntry entry) {
		if (images.containsKey(entry.getServerPath()))
			return false;
		images.put(entry.getServerPath(), entry);
		return true;
	}
	
	File getFile() {
		return file;
	}
	
	public Path getPath() {
		return getFile().toPath();
	}
	
	public URI getURI() {
		return getFile().toURI();
	}
	
	public File getBaseDirectory() {
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
	

	public boolean addAllImages(final Collection<ProjectImageEntry<BufferedImage>> entries) {
		boolean changes = false;
		for (ProjectImageEntry<BufferedImage> entry : entries)
			changes = addImage(entry) | changes;
		return changes;
	}
	
	public int size() {
		return images.size();
	}

	public boolean isEmpty() {
		return images.isEmpty();
	}
	
	public DefaultProjectImageEntry addImage(final ImageServer<BufferedImage> server) {
		var entry = new DefaultProjectImageEntry(server.getPath(), server.getDisplayedImageName(), null, null, null);
		if (addImage(entry)) {
			return entry;
		}
		return null;
	}
	
	
	public DefaultProjectImageEntry getImageEntry(final String path) {
		return images.get(path);
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
	
	public void removeImage(final ProjectImageEntry<?> entry) {
		removeImage(entry.getServerPath());
	}

	public void removeAllImages(final Collection<ProjectImageEntry<BufferedImage>> entries) {
		for (ProjectImageEntry<BufferedImage> entry : entries)
			removeImage(entry);
	}
	
	public void removeImage(final String path) {
		images.remove(path);
	}
	
	public void syncChanges() throws IOException {
		writeProject(getFile());
		writePathClasses(pathClasses);
//		if (file.isDirectory())
//			file = new File(dirBase, "project.qpproj");
//		var json = new GsonBuilder().setLenient().setPrettyPrinting().create().toJson(this);
//		Files.writeString(file.toPath(), json);
//		logger.warn("Syncing project not yet implemented!");
	}
	
	/**
	 * Try syncing changes quietly, logging any exceptions.
	 */
	private void requestSyncQuietly() {
		try {
			syncChanges();
		} catch (IOException e) {
			logger.error("Error syncing project changes", e);
		}
	}
	
	public boolean getMaskImageNames() {
		return maskNames;
	}
	
	public void setMaskImageNames(boolean maskNames) {
		this.maskNames = maskNames;
	}

	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	public List<DefaultProjectImageEntry> getImageList() {
		List<DefaultProjectImageEntry> list = new ArrayList<>(images.values());
//		list.sort(ImageEntryComparator.instance);
		return list;
	}
	
	public ImageServer<BufferedImage> buildServer(final ProjectImageEntry<BufferedImage> entry) throws URISyntaxException, IOException {
		return ImageServerProvider.buildServer(entry.getServerPath(), BufferedImage.class);
	}
	
	
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
	
	public long getCreationTimestamp() {
		return creationTimestamp;
	}
	
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}
	
	
	private String EXT_SCRIPT = ".groovy";
	private String EXT_OBJECT_CLASSIFIER = ".classifier.pixels.json";
	private String EXT_PIXEL_CLASSIFIER = ".classifier.objects.json";
	
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
	
	
	/**
	 * Class to represent an image entry within a project.
	 * 
	 * This stores the path to the image, and some optional metadata.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	class DefaultProjectImageEntry {

		private transient String cleanedPath = null;
		
		private String uniqueName;
		
		private String serverPath;
		private URI uri;
		
		private String randomizedName = UUID.randomUUID().toString();
		
		private String imageName;
		private String description;

		private Map<String, String> metadata = new LinkedHashMap<>();
		
		DefaultProjectImageEntry(final String serverPath, final String imageName, final String uniqueName, final String description, final Map<String, String> metadataMap) {
//			this.project = project;
			this.serverPath = serverPath;
			this.uniqueName = uniqueName;
			
			// TODO: Check if this is a remotely acceptable way to achieve relative pathnames!  I suspect it is not really...
			try {
				File file = new File(serverPath);
				if (file.exists())
					uri = file.toURI();
				else
					uri = new URI(serverPath);
			} catch (URISyntaxException e) {
				logger.error("Not a valid URI!", e);
			}
			
			String projectPath = getBaseDirectory().getAbsolutePath();
			if (this.serverPath.startsWith(projectPath))
				this.serverPath = "{$PROJECT_DIR}" + this.serverPath.substring(projectPath.length());
			
			if (imageName == null) {
				if (URLTools.checkURL(serverPath))
					this.imageName = URLTools.getNameFromBaseURL(serverPath);
				else
					this.imageName = new File(serverPath).getName();
			} else
				this.imageName = imageName;
			
			if (description != null)
				setDescription(description);
			
			if (metadataMap != null)
				metadata.putAll(metadataMap);		
		}
		
		
		/**
		 * Get a name that uniquely identifies the image within this project.
		 * 
		 * @return
		 */
		public String getUniqueName() {
			if (uniqueName == null) {
				uniqueName = UUID.randomUUID().toString();
			}
			return uniqueName;
		}
		
		
		/**
		 * Get the path used to represent this image, which can be used to construct an <code>ImageServer</code>.
		 * <p>
		 * Note that this may have been cleaned up.
		 * 
		 * @see #getStoredServerPath
		 * 
		 * @return
		 */
		public String getServerPath() {
//			return serverPath;
			return uri == null ? serverPath : uri.toString();
		}

		/**
		 * Get a name that may be used for this entry.
		 * 
		 * This may be derived automatically from the server path, or set explicitly to be something else.
		 * 
		 * @return
		 */
		public String getImageName() {
			if (maskNames)
				return randomizedName;
			return imageName;
		}
		
		public String getOriginalImageName() {
			return imageName;
		}
		
		/**
		 * Same as <code>getServerPath</code>.
		 * 
		 * @see #getServerPath
		 * 
		 * @return
		 */
		public String getStoredServerPath() {
			return getServerPath();
		}
		
		public void setImageName(String name) {
			this.imageName = name;
		}
		
		/**
		 * Check if this image entry refers to a specified image according to its path.
		 * 
		 * @param serverPath
		 * @return <code>true</code> if the path is a match, <code>false</code> otherwise.
		 */
		public boolean sameServerPath(final String serverPath) {
			return getServerPath().equals(serverPath);
		}
		
		/**
		 * Remove a metadata value.
		 * 
		 * @param key
		 * @return
		 */
		public String removeMetadataValue(final String key) {
			return metadata.remove(key);
		}
		
		/**
		 * Request a metadata value.
		 * Note that this may return <code>null</code>.
		 * 
		 * @param key
		 * @return
		 */
		public String getMetadataValue(final String key) {
			return metadata.get(key);
		}

		/**
		 * Store a metadata value.
		 * This is intended as storage of short key-value pairs.
		 * Extended text should be stored under <code>setDescription</code>.
		 * 
		 * @param key
		 * @param value
		 * @return
		 */
		public String putMetadataValue(final String key, final String value) {
			return metadata.put(key, value);
		}
		
		/**
		 * Check if a metadata value is present for a specified key.
		 * 
		 * @param key
		 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
		 */
		public boolean containsMetadata(final String key) {
			return metadata.containsKey(key);
		}
		
		/**
		 * Get a description; this is free text describing the image.
		 * @return
		 */
		public String getDescription() {
			return description;
		}
		
		/**
		 * Set the description.
		 * 
		 * @see #getDescription
		 * @param description
		 */
		public void setDescription(final String description) {
			this.description = description;
		}
		
		/**
		 * Check if a description is present.
		 * 
		 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
		 */
		public boolean hasDescription() {
			return this.description != null && !this.description.isEmpty();
		}
		
		/**
		 * Remove all metadata.
		 */
		public void clearMetadata() {
			this.metadata.clear();
		}
		
		/**
		 * Get an unmodifiable view of the underlying metadata map.
		 * 
		 * @return
		 */
		public Map<String, String> getMetadataMap() {
			return Collections.unmodifiableMap(metadata);
		}
		
		/**
		 * Get an unmodifiable collection of the metadata map's keys.
		 * 
		 * @return
		 */
		public Collection<String> getMetadataKeys() {
			return Collections.unmodifiableSet(metadata.keySet());
		}
		
		
		public ImageServer<BufferedImage> buildImageServer() throws IOException {
			String value = metadata.getOrDefault("rotate180", "false");
			boolean rotate180 = value.toLowerCase().equals("true");
			var server = ImageServerProvider.buildServer(getServerPath(), BufferedImage.class);
			// TODO: Handle wrapped image servers
			if (rotate180)
				return new RotatedImageServer(server, RotatedImageServer.Rotation.ROTATE_180);
			
			var pathMetadata = getServerMetadataPath();
			if (Files.exists(pathMetadata)) {
				try (var reader = Files.newBufferedReader(pathMetadata)) {
					var metadata = gson.fromJson(reader, ImageServerMetadata.class);
					server.setMetadata(metadata);
				} catch (Exception e) {
					logger.warn("Unable to load server metadata from {}", pathMetadata);
				}
			}

			return server;
		}
		
		private Path getEntryPath(boolean create) throws IOException {
			var path = getEntryPath();
			if (create && !Files.exists(path))
				Files.createDirectories(path);
			return path;
		}
		
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
			return new ImageData<>(server);
		}

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
			
			// If successful, write the additional metadata
			var server = imageData.getServer();
			var pathServerMetadata = getServerMetadataPath();
			try (var out = Files.newBufferedWriter(pathServerMetadata, StandardOpenOption.CREATE)) {
				gson.toJson(server.getMetadata(), out);
			}
			
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
		
		public boolean hasImageData() {
			return Files.exists(getImageDataPath());
		}
		
		public synchronized PathObjectHierarchy readHierarchy() throws IOException {
			var path = getImageDataPath();
			if (Files.exists(path)) {
				try (var stream = Files.newInputStream(path)) {
					return PathIO.readHierarchy(stream);
				}
			}
			return new PathObjectHierarchy();
		}
		
		
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

		public synchronized BufferedImage getThumbnail() throws IOException {
			var path = getThumbnailPath();
			if (Files.exists(path)) {
				try (var stream = Files.newInputStream(path)) {
					return ImageIO.read(stream);
				}
			}
			return null;
		}

		public synchronized void setThumbnail(BufferedImage img) throws IOException {
			getEntryPath(true);
			var path = getThumbnailPath();
			try (var stream = Files.newOutputStream(path)) {
				ImageIO.write(img, "JPEG", stream);
			}
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
	<T> void writeProject(final File fileProject) {
		if (fileProject == null) {
			logger.error("No file found, cannot write project: {}", this);
			return;
		}

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
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
		
		JsonArray array = new JsonArray();
		for (var entry : getImageList()) {
			JsonObject jsonEntry = new JsonObject();
			jsonEntry.addProperty("path", entry.getServerPath());
		    jsonEntry.addProperty("name", entry.getOriginalImageName());
		    jsonEntry.addProperty("uniqueName", entry.getUniqueName());
		    
		    if (entry.getDescription() != null)
		    		jsonEntry.addProperty("description", entry.getDescription());

		    Map<String, String> metadata = entry.getMetadataMap();
		    if (!metadata.isEmpty()) {
		    	JsonObject metadataBuilder = new JsonObject();
		        for (Map.Entry<String, String> metadataEntry : metadata.entrySet())
		            metadataBuilder.addProperty(metadataEntry.getKey(), metadataEntry.getValue());
		        jsonEntry.add("metadata", metadataBuilder);
			}
			array.add(jsonEntry);
		}

		JsonObject builder = new JsonObject();
		builder.addProperty("version", LATEST_VERSION);
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", getModificationTimestamp());
		builder.addProperty("uri", fileProject.toURI().toString());
//		if (pathClassArray != null) {
//			builder.add("pathClasses", pathClassArray);			
//		}
		builder.add("images", array);

		// If we already have a project, back it up
		if (fileProject.exists()) {
			File fileBackup = new File(fileProject.getAbsolutePath() + ".backup");
			if (fileProject.renameTo(fileBackup))
				logger.debug("Existing project file backed up at {}", fileBackup.getAbsolutePath());
		}

		// Write project
		try (PrintWriter writer = new PrintWriter(fileProject)) {
			writer.write(gson.toJson(builder));
		} catch (FileNotFoundException e) {
			logger.error("Error writing project", e);
		}
	}
	
	
	void loadProject() {
		File fileProject = getFile();
		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			creationTimestamp = element.get("createTimestamp").getAsLong();
			modificationTimestamp = element.get("modifyTimestamp").getAsLong();
			lastURI = new URI(element.get("uri").getAsString());
			
			if (element.has("version"))
				version = element.get("version").getAsString();
			
//			JsonElement pathClassesElement = element.get("pathClasses");
//			if (pathClassesElement != null && pathClassesElement.isJsonArray()) {
//				try {
//					JsonArray pathClassesArray = pathClassesElement.getAsJsonArray();
//					List<PathClass> pathClasses = new ArrayList<>();
//					for (int i = 0; i < pathClassesArray.size(); i++) {
//						JsonObject pathClassObject = pathClassesArray.get(i).getAsJsonObject();
//						String name = pathClassObject.get("name").getAsString();
//						int color = pathClassObject.get("color").getAsInt();
//						PathClass pathClass = PathClassFactory.getPathClass(name, color);
//						pathClasses.add(pathClass);
//					}
//					setPathClasses(pathClasses);
//				} catch (Exception e) {
//					logger.error("Error parsing PathClass list", e);
//				}
//			}

			JsonArray images = element.getAsJsonArray("images");
			for (JsonElement imageElement : images) {
				JsonObject imageObject = imageElement.getAsJsonObject();
				JsonElement metadataObject = imageObject.get("metadata");
				Map<String, String> metadataMap = null;
				if (metadataObject != null) {
					JsonObject metadata = metadataObject.getAsJsonObject();
					if (metadata != null) {
						metadataMap = new HashMap<>();
						for (Entry<String, JsonElement> entry : metadata.entrySet()) {
							String value = entry.getValue().getAsString();
							if (value != null)
								metadataMap.put(entry.getKey(), value);
						}
					}
				}
				String description = null;
				if (imageObject.has("description"))
					description = imageObject.get("description").getAsString();
				String path = imageObject.get("path").getAsString();
				String name = imageObject.has("name") ? imageObject.get("name").getAsString() : null;
				String uniqueName = imageObject.has("uniqueName") ? imageObject.get("uniqueName").getAsString() : null;
				addImage(new DefaultProjectImageEntry(path, name, uniqueName, description, metadataMap));
			}
			pathClasses.addAll(loadPathClasses());
			
			List<String> troublesome = validateLocalPaths(true);
			if (!troublesome.isEmpty()) {
				logger.warn("Could not find {} image(s): {}", troublesome.size(), troublesome);
			}

		} catch (Exception e) {
			logger.error("Unable to read project from " + fileProject.getAbsolutePath(), e);
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
	
	
	Collection<PathClass> loadPathClasses() throws Exception {
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


	public String getVersion() {
		return version;
	}
	
	public ProjectResourceManager<String> getScripts() {
		return new ProjectResourceManager.StringFileResourceManager(getScriptsPath(), ".groovy");
	}

	public ProjectResourceManager<PathObjectClassifier> getObjectClassifiers() {
		return new ProjectResourceManager.SerializableFileResourceManager(getObjectClassifiersPath(), PathObjectClassifier.class);
	}

	public ProjectResourceManager<PixelClassifier> getPixelClassifiers() {
		return new ProjectResourceManager.JsonFileResourceManager(getPixelClassifiersPath(), PixelClassifier.class);
	}
	
}
