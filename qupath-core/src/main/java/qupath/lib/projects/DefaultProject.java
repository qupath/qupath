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

package qupath.lib.projects;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ResourceManager.ImageResourceManager;
import qupath.lib.projects.ResourceManager.Manager;

/**
 * Data structure to store multiple images, relating these to a file system.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultProject implements Project<BufferedImage> {

	public static final String IMAGE_ID = "PROJECT_ENTRY_ID";

	private static String ext = "qpproj";
	
	private static Logger logger = LoggerFactory.getLogger(DefaultProject.class);
	
	private final String LATEST_VERSION = GeneralTools.getVersion();
	private final Map<String, String> metadata;
	
	private String version = null;

	/**
	 * Base directory.
	 */
	private final File dirBase;

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
	
	private List<DefaultProjectImageEntry> images = new ArrayList<>();
	
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
		this.metadata = Collections.synchronizedMap(getStoredMetadata(this.dirBase.toPath()));
	}
	
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
	static synchronized File getUniqueFile(File dir, String name, String ext) {
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
		// Always write the classes because it's possible that at least the color has changed,
		// unless our project has never been written (in which case we don't want to write classes and nothing else)
		if (file.exists()) {
			try {
				logger.debug("Writing PathClasses to project");
				writePathClasses(this.pathClasses);
			} catch (IOException e) {
				logger.warn("Unable to write classes to project", e);
			}
		}
		return true;
	}

	private boolean addImage(final ProjectImageEntry<BufferedImage> entry) {
		if (entry instanceof DefaultProjectImageEntry)
			return addImage((DefaultProjectImageEntry)entry);
		try {
			// This is horribly inefficient, so hopefully isn't required...
			return addImage(new DefaultProjectImageEntry(entry.getServerBuilder(), null, entry.getImageName(), entry.getDescription(), entry.getMetadataMap()));
		} catch (IOException e) {
			logger.error("Unable to add entry " + entry, e);
			return false;
		}
	}
	
	private boolean addImage(final DefaultProjectImageEntry entry) {
		return images.add(entry);
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
	
	private Path getClassifiersPath() {
		return Paths.get(getBasePath().toString(), "classifiers");
	}
	
	List<String> listFilenames(Path path, String ext) throws IOException {
		if (!Files.isDirectory(path))
			return Collections.emptyList();
		
		try (var stream = Files.list(path)) {
			return stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext))
					.map(p -> nameWithoutExtension(p, ext))
					.toList();
		}
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
	public ProjectImageEntry<BufferedImage> addImage(final ServerBuilder<BufferedImage> builder) throws IOException {
		var entry = new DefaultProjectImageEntry(builder, null, null, null, null);
		if (addImage(entry)) {
			return entry;
		}
		return null;
	}
	
	@Override
	public ProjectImageEntry<BufferedImage> addDuplicate(final ProjectImageEntry<BufferedImage> entry, boolean copyData) throws IOException {
		var entryNew = new DefaultProjectImageEntry(entry.getServerBuilder(), null, entry.getImageName(), entry.getDescription(), entry.getMetadataMap());
		if (addImage(entryNew)) {
			if (copyData)
				entryNew.copyDataFromEntry(entry);
			else {
				var img = entry.getThumbnail();
				if (img != null)
					entryNew.setThumbnail(img);
			}
			// TODO: Consider whether description & metadata should be set
			// This hasn't been done at least in v0.5.x, but I'm not sure if that was intentional...
//			entryNew.setDescription(entry.getDescription());
//			for (String key : entry.getMetadataKeys())
//				entryNew.putMetadataValue(key, entry.getMetadataValue(key));
			return entryNew;
		}
		throw new IOException("Unable to add duplicate of " + entry);
	}

	
	@Override
	public ProjectImageEntry<BufferedImage> getEntry(final ImageData<BufferedImage> imageData) {
		Object id = imageData.getProperty(IMAGE_ID);
		for (var entry : images) {
			if (entry.getFullProjectEntryID().equals(id))
				return entry;
		}
		return null;
	}

	@Override
	public void removeImage(final ProjectImageEntry<?> entry, boolean removeAllData) {
		boolean couldRemove = images.remove(entry);
		// Need to make sure we only delete data if it's really inside this project!
		if (couldRemove && removeAllData && entry instanceof DefaultProjectImageEntry defaultEntry) {
			defaultEntry.moveDataToTrash();
		}
	}

	@Override
	public void removeAllImages(final Collection<ProjectImageEntry<BufferedImage>> entries, boolean removeAllData) {
		for (ProjectImageEntry<BufferedImage> entry : entries)
			removeImage(entry, removeAllData);
	}
	
	@Override
	public synchronized void syncChanges() throws IOException {
		writeProject(getFile());
		writePathClasses(pathClasses);
		setStoredMetadata(getBasePath(), metadata);
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
	 * Get an unmodifiable list of image entries for the project.
	 * 
	 * @return
	 */
	@Override
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		return Collections.unmodifiableList(images);
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
	
	
	Path ensureDirectoryExists(Path path) throws IOException {
		if (!Files.isDirectory(path))
			Files.createDirectories(path);
		return path;
	}
	
	private AtomicLong counter = new AtomicLong(0L);
	
	
	
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
		 * ServerBuilder. This should be lightweight & capable of being JSON-ified.
		 */
		private ServerBuilder<BufferedImage> serverBuilder;
		
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
		private Map<String, String> metadata = Collections.synchronizedMap(new LinkedHashMap<>());
		private final Set<String> tags = Collections.synchronizedSet(new LinkedHashSet<>());

		/**
		 * Store a soft reference to the thumbnail, so that it can be garbage collected if necessary.
		 * Intended to help with https://github.com/qupath/qupath/issues/1446 without a need to introduce
		 * another cache in the UI.
		 */
		private transient SoftReference<BufferedImage> cachedThumbnail;
		
		DefaultProjectImageEntry(final ServerBuilder<BufferedImage> builder) throws IOException {
			this(builder, null, null, null, null);
		}
		
		DefaultProjectImageEntry(final ServerBuilder<BufferedImage> builder, final Long entryID, final String imageName, final String description, final Map<String, String> metadataMap) throws IOException {
			this.serverBuilder = builder;
			if (entryID == null)
				this.entryID = counter.incrementAndGet();
			else
				this.entryID = entryID;
			
			if (imageName == null)
				this.imageName = "Image " + entryID;
			else
				this.imageName = imageName;
			
			if (description != null)
				setDescription(description);
			
			if (metadataMap != null)
				metadata.putAll(metadataMap);

			writeServerBuilder();
		}
		
		DefaultProjectImageEntry(final DefaultProjectImageEntry entry) {
			this.serverBuilder = entry.serverBuilder;
			this.entryID = entry.entryID;
			this.imageName = entry.imageName;
			this.description = entry.description;
			if (entry.metadata != null) {
				this.metadata.putAll(entry.metadata);
			}
			if (entry.tags != null) {
				this.tags.addAll(entry.tags);
			}
        }
		
		/**
		 * Copy the image data from another entry.
		 * If the current entry does not have a thumbnail then this will also be copied.
		 * 
		 * @param entry
		 * @throws IOException
		 */
		void copyDataFromEntry(final ProjectImageEntry<BufferedImage> entry) throws IOException {
			if (entry instanceof DefaultProjectImageEntry)
				copyDataFromEntry((DefaultProjectImageEntry)entry);
			else {
				if (entry.hasImageData())
					saveImageData(entry.readImageData());
				if (getThumbnail() == null) {
					var imgThumbnail = entry.getThumbnail();
					if (imgThumbnail != null)
						setThumbnail(imgThumbnail);
				}
			}
		}
		
		void copyDataFromEntry(final DefaultProjectImageEntry entry) throws IOException {
			// Ensure we have the necessary directory
			getEntryPath(true);
			if (Files.exists(entry.getImageDataPath()))
				Files.copy(entry.getImageDataPath(), getImageDataPath(), StandardCopyOption.REPLACE_EXISTING);
			if (Files.exists(entry.getDataSummaryPath()))
				Files.copy(entry.getDataSummaryPath(), getDataSummaryPath(), StandardCopyOption.REPLACE_EXISTING);
			if (Files.exists(entry.getServerPath()))
				Files.copy(entry.getServerPath(), getServerPath(), StandardCopyOption.REPLACE_EXISTING);
			if (getThumbnail() == null && Files.exists(entry.getThumbnailPath()))
				Files.copy(entry.getThumbnailPath(), getThumbnailPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		
		private transient ImageResourceManager<BufferedImage> imageManager = null;
		
		@Override
		public synchronized Manager<ImageServer<BufferedImage>> getImages() {
			if (imageManager == null) {
				imageManager = new ImageResourceManager<>(getPath(), BufferedImage.class);
			}
			return imageManager;
		}

		private String getFullProjectEntryID() {
			return file.getAbsolutePath() + "::" + getID();
		}
		
		@Override
		public String getID() {
			return Long.toString(entryID);
		}
		
		@Override
		public Collection<URI> getURIs() throws IOException {
			if (serverBuilder == null)
				return Collections.emptyList();
			return serverBuilder.getURIs();
		}
		
		@Override
		public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
			var builderBefore = serverBuilder;
			serverBuilder = serverBuilder.updateURIs(replacements);
			boolean changes = builderBefore != serverBuilder;
			if (changes)
				writeServerBuilder();
			return changes;
		}
		
		
		String getUniqueName() {
			return Long.toString(entryID);
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
		public String getDescription() {
			return description;
		}
		
		@Override
		public void setDescription(final String description) {
			this.description = description;
		}
		/**
		 * Returns a thread-safe and modifiable map containing the metadata
		 * of this entry.
		 * <p>
		 * Modifications to this map are saved when calling {@link #syncChanges()}.
		 *
		 * @return the metadata of this entry
		 */
		@Override
		public Map<String, String> getMetadata() {
			return metadata;
		}
		
		@Override
		public ServerBuilder<BufferedImage> getServerBuilder() {
			return serverBuilder;
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
		
		/**
		 * Get the path used to backup an ImageData while writing it
		 * @return
		 */
		private Path getBackupImageDataPath() {
			return Paths.get(getEntryPath().toString(), "data.qpdata.bkp");
		}
		
		private Path getDataSummaryPath() {
			return Paths.get(getEntryPath().toString(), "summary.json");
		}
		
		private Path getServerPath() {
			return Paths.get(getEntryPath().toString(), "server.json");
		}
		
		private Path getThumbnailPath() {
			return Paths.get(getEntryPath().toString(), "thumbnail.jpg");
		}

		@Override
		public synchronized ImageData<BufferedImage> readImageData() throws IOException {
			Path path = getImageDataPath();
			ImageData<BufferedImage> imageData = null;
			// TODO: Consider whether we can set the image name for the lazy-loaded server
			if (Files.exists(path)) {
				try {
					imageData = PathIO.readImageData(path, getServerBuilder());
				} catch (Exception e) {
					logger.error("Error reading image data from {}", path, e);
				}
			}
			// If we find a backup file, try to restore what we can from it
			// See https://github.com/qupath/qupath/issues/512
			if (imageData == null) {
				var pathBackup = getBackupImageDataPath();
				if (Files.exists(pathBackup)) {
					try {
						imageData = PathIO.readImageData(pathBackup, getServerBuilder());
						logger.warn("Restored previous ImageData from {}", pathBackup);
					} catch (IOException e) {
						logger.error("Error reading backup image data from {}", pathBackup, e);
					}
				}
			}
			
			if (imageData == null)
				imageData = new ImageData<>(getServerBuilder(), new PathObjectHierarchy(), ImageType.UNSET);
			imageData.setProperty(IMAGE_ID, getFullProjectEntryID()); // Required to be able to test for the ID later
			imageData.setChanged(false);
			// I don't like it either - but we want to ensure that the server name matches with the image entry name.
			// This can trigger lazy-loading of the server, but it's necessary to ensure that the server name is correct.
			imageData.updateServerMetadata(
					new ImageServerMetadata.Builder(imageData.getServerMetadata())
							.name(getImageName())
							.build()
			);
			return imageData;
		}

		@Override
		public synchronized void saveImageData(ImageData<BufferedImage> imageData) throws IOException {
			// Get entry path, creating if needed
			getEntryPath(true);
			var pathData = getImageDataPath();
			
			// If we already have a file, back it up first
			var pathBackup = getBackupImageDataPath();
			if (Files.exists(pathData))
				Files.move(pathData, pathBackup, StandardCopyOption.REPLACE_EXISTING);
			
			// Set the entry property, if needed
			// This handles cases where an ImageData is being moved to become part of this project, 
			// so that it can be recognized later in calls to Project.getEntry(entry)
			String id = getFullProjectEntryID();
			if (!Objects.equals(id, imageData.getProperty(IMAGE_ID))) {
				logger.warn("Updating ID property to {}", id);
				imageData.setProperty(IMAGE_ID, id);
			}
			
			// Write to a temp file first
			long timestamp = 0L;
			try (var stream = Files.newOutputStream(pathData)) {
				logger.debug("Saving image data to {}", pathData);
				PathIO.writeImageData(stream, imageData);
				imageData.setLastSavedPath(pathData.toString(), true);
				timestamp = Files.getLastModifiedTime(pathData).toMillis();
				// Delete backup file if it exists
				if (Files.exists(pathBackup))
					Files.delete(pathBackup);
			} catch (IOException e) {
				// Try to restore the backup
				if (Files.exists(pathBackup)) {
					logger.warn("Exception writing image file - attempting to restore {} from backup", pathData);
					Files.move(pathBackup, pathData, StandardCopyOption.REPLACE_EXISTING);				
				}
				throw e;
			}
			
			// If successful, write the server (including metadata)
			var currentServerBuilder = imageData.getServerBuilder();
			if (currentServerBuilder != null && !currentServerBuilder.equals(this.serverBuilder)) {
				this.serverBuilder = currentServerBuilder;
				writeServerBuilder();
				// This ensures that the metadata is updated in the project file
//				syncChanges();
			}
			
			var pathSummary = getDataSummaryPath();
			try (var out = Files.newBufferedWriter(pathSummary, StandardCharsets.UTF_8)) {
				GsonTools.getInstance().toJson(new ImageDataSummary(imageData, timestamp), out);
			}			

		}

		private void writeServerBuilder() throws IOException {
			// Write the server - it isn't used, but it may enable us to rebuild the server from the data directory
			// if the project is lost.
			// Note that before v0.5.0, this actually wrote the server builder - but this was missing type
			// information, so recovery of the actual server was difficult.
			getEntryPath(true); // Ensure the directory exists
			var pathServer = getServerPath();
			try (var out = Files.newBufferedWriter(pathServer, StandardCharsets.UTF_8)) {
				// Important to specify the class as ServerBuilder, so that the type adapter writes the type!
				GsonTools.getInstance(true).toJson(this.serverBuilder, ServerBuilder.class, out);
			} catch (Exception e) {
				logger.warn("Unable to write server to {}", pathServer);
				Files.deleteIfExists(pathServer);
			}
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
			sb.append(getImageName()).append("\n");
			sb.append("ID:\t").append(getID()).append("\n\n");
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
			long startTime = System.nanoTime();
			var thumbnail = cachedThumbnail == null ? null : cachedThumbnail.get();
			boolean cached = thumbnail != null;
			if (thumbnail == null) {
				var path = getThumbnailPath();
				if (Files.exists(path)) {
					try (var stream = Files.newInputStream(path)) {
						thumbnail = ImageIO.read(stream);
						cachedThumbnail = new SoftReference<>(thumbnail);
					}
				}
			}
			long endTime = System.nanoTime();
			if (cached)
				logger.trace("Thumbnail accessed from cache in {} ms", (endTime - startTime) / 1000000);
			else
				logger.trace("Thumbnail read in {} ms", (endTime - startTime) / 1000000);
			return thumbnail;
		}

		@Override
		public synchronized void setThumbnail(BufferedImage img) throws IOException {
			resetCachedThumbnail();
			getEntryPath(true);
			var path = getThumbnailPath();
			if (img == null) {
				// Reset the thumbnail
				if (Files.exists(path)) {
					logger.debug("Deleting thumbnail for {}", path);
					Files.delete(path);
				}
			} else {
				// Save the thumbnail
				try (var stream = Files.newOutputStream(path)) {
					logger.debug("Writing thumbnail to {}", path);
					ImageIO.write(img, "JPEG", stream);
				}
			}
		}

		/**
		 * Returns a thread-safe (see {@link Collections#synchronizedSet(Set)}) and modifiable set containing the tags
		 * of this entry.
		 * <p>
		 * Modifications to this set are saved when calling {@link #syncChanges()}.
		 *
		 * @return the set of tags of this entry
		 */
		@Override
		public Set<String> getTags() {
			return tags;
		}

		/**
		 * Reset the cached thumbnail, so that it will be reloaded next time it is requested.
		 */
		private synchronized void resetCachedThumbnail() {
			logger.trace("Resetting cached thumbnail for {}", getID());
			cachedThumbnail = null;
		}
		
		synchronized void moveDataToTrash() {
			Path path = getEntryPath();
			if (!Files.exists(path))
				return;
			if (Desktop.isDesktopSupported()) {
				var desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
					// See https://github.com/qupath/qupath/issues/1738
					// It's not clear if this will help, but Desktop sometimes cares about the event dispatch thread
					if (SwingUtilities.isEventDispatchThread()) {
						if (desktop.moveToTrash(path.toFile()))
							return;
					} else {
						SwingUtilities.invokeLater(() -> {
							if (!desktop.moveToTrash(path.toFile())) {
								logger.warn("Unable to move {} to trash - please delete manually if required", path);
							}
						});
						return;
					}
				}
			}
			logger.warn("Unable to move {} to trash - please delete manually if required", path);
		}
		
	}
	
	
	@SuppressWarnings("unused")
	static class ImageDataSummary {
		
		private long timestamp;
		private ImageType imageType;
		private ServerSummary server;
		private HierarchySummary hierarchy;
		
		ImageDataSummary(ImageData<?> imageData, long timestamp) {
			this.imageType = imageData.getImageType();
			if (imageData.isLoaded())
				this.server = new ServerSummary(imageData.getServer());
			else
				this.server = new ServerSummary(imageData.getServerMetadata());
			this.timestamp = timestamp;
			this.hierarchy = new HierarchySummary(imageData.getHierarchy());
		}
		
		@Override
		public String toString() {
			return GsonTools.getInstance().toJson(this);
		}
		
	}
	
	
	@SuppressWarnings("unused")
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

		ServerSummary(ImageServerMetadata metadata) {
			this.width = metadata.getWidth();
			this.height = metadata.getHeight();
			this.sizeC = metadata.getChannels().size();
			this.sizeZ = metadata.getSizeZ();
			this.sizeT = metadata.getSizeZ();
		}
		
	}
	
	
	@SuppressWarnings("unused")
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
	 * @param <T> 
	 * 
	 * @param fileProject
	 * @throws IOException 
	 */
	synchronized <T> void writeProject(final File fileProject) throws IOException {
		if (fileProject == null) {
			throw new IOException("No file found, cannot write project: " + this);
		}

		Gson gson = GsonTools.getInstance(true);
		
		JsonObject builder = new JsonObject();
		builder.addProperty("version", LATEST_VERSION);
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", getModificationTimestamp());
		builder.addProperty("uri", fileProject.toURI().toString());
		builder.addProperty("lastID", counter.get());

		builder.add("images", gson.toJsonTree(images));
		
		// Write project to a new file
		var pathProject = fileProject.toPath();
		var pathTempNew = new File(fileProject.getAbsolutePath() + ".tmp").toPath();
		logger.debug("Writing project to {}", pathTempNew);
		try (var writer = Files.newBufferedWriter(pathTempNew, StandardCharsets.UTF_8)) {
			gson.toJson(builder, writer);
		}
		
		// If we already have a project, back it up
		if (fileProject.exists()) {
			var pathBackup = new File(fileProject.getAbsolutePath() + ".backup").toPath();
			logger.debug("Backing up existing project to {}", pathBackup);
			Files.move(pathProject, pathBackup, StandardCopyOption.REPLACE_EXISTING);
		}
		
		// If this succeeded, rename files
		logger.debug("Renaming project to {}", pathProject);
		Files.move(pathTempNew, pathProject, StandardCopyOption.REPLACE_EXISTING);
	}
	
	
	void loadProject() throws IOException {
		File fileProject = getFile();
		try (BufferedReader fileReader = Files.newBufferedReader(fileProject.toPath(), StandardCharsets.UTF_8)) {
			Gson gson = GsonTools.getInstance();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			creationTimestamp = element.get("createTimestamp").getAsLong();
			modificationTimestamp = element.get("modifyTimestamp").getAsLong();
			if (element.has("uri")) {
				try {
					previousURI = new URI(element.get("uri").getAsString());
				} catch (URISyntaxException e) {
					logger.warn("Error parsing previous URI: " + e.getLocalizedMessage(), e);
				}
			} else
				logger.debug("No previous URI found in project");
			
			if (element.has("version"))
				version = element.get("version").getAsString();
			
			if (Arrays.asList("v0.2.0-m2", "v0.2.0-m1").contains(version)) {
				throw new IOException("Older projects written with " + version + " are not compatible with this version of QuPath, sorry!");				
			}
			if (version == null && !element.has("lastID")) {
				throw new IOException("QuPath project is missing a version number and last ID (was it written with an old version?)");
			}
						
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

		} catch (Exception e) {
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
			// Store any non-default (null) class
			if (pathClass != PathClass.NULL_CLASS) {
				JsonObject jsonEntry = new JsonObject();
				jsonEntry.addProperty("name", pathClass.toString());
				jsonEntry.addProperty("color", pathClass.getColor());
				pathClassArray.add(jsonEntry);
			}
		}
		
		var element = new JsonObject();
		element.add("pathClasses", pathClassArray);
		try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GsonTools.getInstance(true).toJson(element, writer);
		}
	}
	
	
	Collection<PathClass> loadPathClasses() throws IOException {
		var path = Paths.get(ensureDirectoryExists(getClassifiersPath()).toString(), "classes.json");
		if (!Files.isRegularFile(path))
			return Collections.emptyList();
		Gson gson = GsonTools.getInstance();
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			var element = gson.fromJson(reader, JsonObject.class);
			JsonElement pathClassesElement = element.get("pathClasses");
			if (pathClassesElement != null && pathClassesElement.isJsonArray()) {
				JsonArray pathClassesArray = pathClassesElement.getAsJsonArray();
				List<PathClass> pathClasses = new ArrayList<>();
				for (int i = 0; i < pathClassesArray.size(); i++) {
					JsonObject pathClassObject = pathClassesArray.get(i).getAsJsonObject();
					if (pathClassObject.has("name")) {
						String name = pathClassObject.get("name").getAsString();
						if (PathClass.NULL_CLASS.toString().equals(name)) {
							logger.debug("Skipping PathClass '{}' - shares a name with the null class", name);
							continue;
						}
						
						Integer color = null;
						if (pathClassObject.has("color") && !pathClassObject.get("color").isJsonNull()) {
							color = pathClassObject.get("color").getAsInt();							
						}
						PathClass pathClass = PathClass.fromString(name, color);
						if (color != null)
							pathClass.setColor(color); // Make sure we have the color we want
						pathClasses.add(pathClass);
					}
				}
				return pathClasses;
			} else
				return Collections.emptyList();
		}
	}


	@Override
	public String getVersion() {
		return version;
	}
	
//	@Override
//	public Manager<String> getScripts() {
//		return new ResourceManager.StringFileResourceManager(getScriptsPath(), ".groovy");
//	}
//
//
//	@Override
//	public Manager<ObjectClassifier<BufferedImage>> getObjectClassifiers() {
//		return new ResourceManager.JsonFileResourceManager(getObjectClassifiersPath(), ObjectClassifier.class);
//	}
//
//
//	@Override
//	public Manager<PixelClassifier> getPixelClassifiers() {
//		return new ResourceManager.JsonFileResourceManager(getPixelClassifiersPath(), PixelClassifier.class);
//	}
		
	@Override
	public <S, R extends S> Manager<R> getResources(String location, Class<S> cls, String ext) {
		var path = Paths.get(getBasePath().toString(), location);
		ext = ext.toLowerCase().strip();
		if (ext.startsWith("."))
			ext = ext.substring(1);
		switch (ext) {
		case "json":
			return new ResourceManager.JsonFileResourceManager(path, cls);
		}
		if (String.class.equals(cls))
			return (Manager)new ResourceManager.StringFileResourceManager(path, ext);
		return null;
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

	/**
	 * Returns a thread-safe and modifiable map containing the metadata
	 * of this project.
	 * <p>
	 * Modifications to this map are saved when calling {@link #syncChanges()}.
	 *
	 * @return the metadata of this project
	 */
	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	private static Map<String, String> getStoredMetadata(Path projectPath) {
		Map<String, String> metadata = new LinkedHashMap<>();

		Path metadataPath = getMetadataPath(projectPath);
		if (Files.exists(metadataPath)) {
			try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
				metadata.putAll(GsonTools.getInstance().fromJson(reader, new TypeToken<Map<String, String>>(){}.getType()));
			} catch (IOException e) {
				logger.error("Error while retrieving project metadata", e);
			}
		}

		return metadata;
	}

	private static void setStoredMetadata(Path projectPath, Map<String, String> metadata) {
		Path metadataPath = getMetadataPath(projectPath);

		try {
			Files.createDirectories(metadataPath.getParent());

			try (Writer writer = Files.newBufferedWriter(metadataPath, StandardCharsets.UTF_8)) {
				GsonTools.getInstance().toJson(metadata, writer);
			}
		} catch (IOException e) {
			logger.error("Error while saving project metadata", e);
		}
	}

	private static Path getMetadataPath(Path projectPath) {
		return Paths.get(projectPath.toString(), "data", "metadata.json");
	}
}
