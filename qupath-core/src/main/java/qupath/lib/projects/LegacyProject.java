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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import qupath.lib.common.URLTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Legacy Project implementation from QuPath 0.1.2 and earlier.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class LegacyProject<T> implements Project<T> {
	
	private static Logger logger = LoggerFactory.getLogger(LegacyProject.class);
	
	private File file;
	private File dirBase;
	private Class<T> cls;
	private String name = null;
	
	private List<PathClass> pathClasses = new ArrayList<>();
	
	private Map<String, ProjectImageEntry<T>> images = new LinkedHashMap<>();
	private long creationTimestamp;
	private long modificationTimestamp;
	
	LegacyProject(final File file, final Class<T> cls) {
		this.file = file;
		if (file.isDirectory() || !file.getParentFile().isDirectory())
			throw new IllegalArgumentException("File should be a project file within an existing directory!");
		this.dirBase = file.getParentFile();
		this.cls = cls;
		creationTimestamp = System.currentTimeMillis();
		modificationTimestamp = System.currentTimeMillis();
	}
	
	
	static <T> LegacyProject<T> readFromFile(final File file, final Class<T> cls) {
		var project = new LegacyProject<>(file, cls);
		if (file.exists())
			project.loadProject(file);
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
		if (this.pathClasses.size() == pathClasses.size() && this.pathClasses.containsAll(pathClasses))
			return false;
		this.pathClasses.clear();
		this.pathClasses.addAll(pathClasses);
		return true;
	}

	public boolean addImage(final ProjectImageEntry<T> entry) {
		if (images.containsKey(cleanServerPath(entry.getServerPath())))
			return false;
		images.put(cleanServerPath(entry.getServerPath()), entry);
		return true;
	}
	
	public File getFile() {
		return file;
	}
	
	public File getBaseDirectory() {
		return dirBase;
	}
	
	public boolean addAllImages(final Collection<ProjectImageEntry<T>> entries) {
		boolean changes = false;
		for (ProjectImageEntry<T> entry : entries)
			changes = addImage(entry) | changes;
		return changes;
	}
	
	public int size() {
		return images.size();
	}

	public boolean isEmpty() {
		return images.isEmpty();
	}

	public boolean addImagesForServer(final ImageServer<T> server) {
		
		List<String> subImages = server.getSubImageList();
		if (subImages.isEmpty()) {
			return addImage(new LegacyProjectImageEntry<>(this, server.getPath(), server.getDisplayedImageName(), null));
		}
		
		boolean changes = false;
		for (String name : subImages)
			// The sub image name might be the same across images, we should append the server displayed name to it, just to make sure it is unique
			changes = changes | addImage(new LegacyProjectImageEntry<>(this, server.getSubImagePath(name), server.getDisplayedImageName()+" ("+name+")", null));
		return changes;
	}
	
	
	public ProjectImageEntry<T> getImageEntry(final String path) {
		return images.get(cleanServerPath(path));
	}

	public String cleanServerPath(final String path) {
		String cleanedPath = path.replace("%20", " ").replace("%5C", "\\");
		cleanedPath = cleanedPath.replace("{$PROJECT_DIR}", getBaseDirectory().getAbsolutePath());
		return cleanedPath;
	}
	
	public boolean addImage(final String path) {
		try {
			ImageServer<T> server = ImageServerProvider.buildServer(path, cls);
			boolean changes = addImagesForServer(server);
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

	public void removeAllImages(final Collection<ProjectImageEntry<T>> entries) {
		for (ProjectImageEntry<T> entry : entries)
			removeImage(entry);
	}
	
	public void removeImage(final String path) {
		images.remove(path);
	}
	
	public void syncChanges() throws IOException {
		writeProject(this);
	}

	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	public List<ProjectImageEntry<T>> getImageList() {
		List<ProjectImageEntry<T>> list = new ArrayList<>(images.values());
//		list.sort(ImageEntryComparator.instance);
		return list;
	}
	
	public ImageServer<T> buildServer(final ProjectImageEntry<T> entry) {
		return ImageServerProvider.buildServer(entry.getServerPath(), cls);
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
		return "Project: " + getName();
	}
	
	
	public long getCreationTimestamp() {
		return creationTimestamp;
	}
	
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}
	
	
	
	
	
	void loadProject(final File fileProject) {
		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			creationTimestamp = element.get("createTimestamp").getAsLong();
			modificationTimestamp = element.get("modifyTimestamp").getAsLong();
			
			JsonElement pathClassesElement = element.get("pathClasses");
			if (pathClassesElement != null && pathClassesElement.isJsonArray()) {
				try {
					JsonArray pathClassesArray = pathClassesElement.getAsJsonArray();
					List<PathClass> pathClasses = new ArrayList<>();
					for (int i = 0; i < pathClassesArray.size(); i++) {
						JsonObject pathClassObject = pathClassesArray.get(i).getAsJsonObject();
						String name = pathClassObject.get("name").getAsString();
						int color = pathClassObject.get("color").getAsInt();
						PathClass pathClass = PathClassFactory.getPathClass(name, color);
						pathClasses.add(pathClass);
					}
					setPathClasses(pathClasses);
				} catch (Exception e) {
					logger.error("Error parsing PathClass list", e);
				}
			}

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
				addImage(new LegacyProjectImageEntry<>(this, path, name, description, metadataMap));
			}
		} catch (Exception e) {
			logger.error("Unable to read project from " + fileProject.getAbsolutePath(), e);
		}
	}
	
	
//	static class ImageEntryComparator implements Comparator<ProjectImageEntry<?>> {
//
//		static ImageEntryComparator instance = new ImageEntryComparator();
//		
//		@Override
//		public int compare(ProjectImageEntry<?> o1, ProjectImageEntry<?> o2) {
//			String n1 = o1.getImageName();
//			String n2 = o2.getImageName();
//			if (n1 == null) {
//				if (n2 == null)
//					return 0;
//				else
//					return 1;
//			} else if (n2 == null)
//				return -1;
//			return n1.compareTo(n2);
//		}
//		
//	}
	
	
	/**
	 * Write project, overwriting existing file or using the default name.
	 * 
	 * Note: Behavior of this method changed after 0.1.3.
	 * 
	 * @param project
	 */
	static void writeProject(final Project<?> project) {
		writeProject(project, null);
	}

	/**
	 * Write project, setting the name of the project file.
	 * 
	 * @param project
	 * @param name
	 */
	static void writeProject(final Project<?> project, final String name) {
		File fileProject = ProjectIO.getProjectFile(project, name);
		if (fileProject == null) {
			logger.error("No file found, cannot write project: {}", project);
			return;
		}

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		List<PathClass> pathClasses = project.getPathClasses();
		JsonArray pathClassArray = null;
		if (!pathClasses.isEmpty()) {
			pathClassArray = new JsonArray();
			for (PathClass pathClass : pathClasses) {
				JsonObject jsonEntry = new JsonObject();
				jsonEntry.addProperty("name", pathClass.toString());
				jsonEntry.addProperty("color", pathClass.getColor());
				pathClassArray.add(jsonEntry);
			}
		}		
		
		JsonArray array = new JsonArray();
		for (ProjectImageEntry<?> entry : project.getImageList()) {
			JsonObject jsonEntry = new JsonObject();
		    jsonEntry.addProperty("path", entry.getStoredServerPath());
		    jsonEntry.addProperty("name", entry.getImageName());
		    
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
		builder.addProperty("createTimestamp", project.getCreationTimestamp());
		builder.addProperty("modifyTimestamp", project.getModificationTimestamp());
		if (pathClassArray != null) {
			builder.add("pathClasses", pathClassArray);			
		}
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
	
	
	
	/**
	 * Class to represent an image entry within a project.
	 * 
	 * This stores the path to the image, and some optional metadata.
	 * 
	 * @author Pete Bankhead
	 *
	 * @param <T> Depends upon the project used; typically BufferedImage for QuPath
	 */
	// TODO: URGENTLY NEED TO CONSIDER ESCAPING CHARACTERS IN URLS MORE GENERALLY
	static class LegacyProjectImageEntry<T> implements ProjectImageEntry<T> {

		private Project<T> project;
		private transient String cleanedPath = null;
		
		private String serverPath;
		private String imageName;
		
		private Map<String, String> metadata = new HashMap<>();
		
		private String description;

		LegacyProjectImageEntry(final Project<T> project, final String serverPath, final String imageName, final String description, final Map<String, String> metadataMap) {
			this.project = project;
			this.serverPath = serverPath;
			
			// TODO: Check if this is a remotely acceptable way to achieve relative pathnames!  I suspect it is not really...
			String projectPath = project.getBaseDirectory().getAbsolutePath();
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
		
		LegacyProjectImageEntry(final Project<T> project, final String serverPath, final String imageName, final Map<String, String> metadataMap) {
			this(project, serverPath, imageName, null, metadataMap);
		}
		
		/**
		 * Get the path used to represent this image, which can be used to construct an <code>ImageServer</code>.
		 * 
		 * Note that this may have been cleaned up.
		 * 
		 * @see #getStoredServerPath
		 * 
		 * @return
		 */
		public String getServerPath() {
//			return serverPath;
			return getCleanedServerPath();
		}

		/**
		 * Get a name that may be used for this entry.
		 * 
		 * This may be derived automatically from the server path, or set explictly to be something else.
		 * 
		 * @return
		 */
		public String getImageName() {
			return imageName;
		}

		@Override
		public String toString() {
			String s = getImageName();
			if (!metadata.isEmpty())
				s += " - " + getMetadataSummaryString();
			return s;
			//			return getServerPath();
		}
		
		/**
		 * Get the path used to represent this image, as specified when this entry was created.
		 * 
		 * It is generally better to rely on <code>getServerPath</code>, especially if paths will be compared.
		 * 
		 * @see #getServerPath
		 * 
		 * @return
		 */
		public String getStoredServerPath() {
			return serverPath;
		}
		
		// TODO: Improve implementation!
		public String getCleanedServerPath() {
			if (cleanedPath != null)
				return cleanedPath;
			cleanedPath = project.cleanServerPath(serverPath);
			return cleanedPath;
		}
		
		public void setName(String name) {
			this.imageName = name;
		}
		
		/**
		 * This only returns the image name - which is a limitation of the legacy projects.
		 * @return
		 */
		public String getUniqueName() {
			return getImageName();
		}
		
		/**
		 * Check if this image entry refers to a specified image according to its path.
		 * 
		 * @param serverPath
		 * @return <code>true</code> if the path is a match, <code>false</code> otherwise.
		 */
		public boolean equalsServerPath(final String serverPath) {
			return getCleanedServerPath().equals(project.cleanServerPath(serverPath));
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


	}
	
}
