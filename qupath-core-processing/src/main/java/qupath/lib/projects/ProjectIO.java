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
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
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

import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Read/write QuPath projects.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectIO {

	final private static Logger logger = LoggerFactory.getLogger(ProjectIO.class);
	
	private static final String DEFAULT_PROJECT_NAME = "project";
	
	private static final String DEFAULT_PROJECT_EXTENSION = "qpproj";

	/**
	 * Read project from file.
	 * 
	 * @param fileProject
	 * @param cls
	 * @return
	 */
	public static <T> Project<T> loadProject(final File fileProject, final Class<T> cls) {
		if (fileProject == null) {
			return null;
		}

		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Project<T> project = new Project<T>(fileProject, cls);

			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			project.creationTimestamp = element.get("createTimestamp").getAsLong();
			project.modificationTimestamp = element.get("modifyTimestamp").getAsLong();
			
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
					project.setPathClasses(pathClasses);
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
				project.addImage(new ProjectImageEntry<>(project, path, name, description, metadataMap));
			}

			return project;
		} catch (Exception e) {
			logger.error("Unable to read project from " + fileProject.getAbsolutePath(), e);
		}
		return null;
	}


	/**
	 * Write project, overwriting existing file or using the default name.
	 * 
	 * Note: Behavior of this method changed after 0.1.3.
	 * 
	 * @param project
	 */
	public static void writeProject(final Project<?> project) {
		writeProject(project, null);
	}

	/**
	 * Write project, setting the name of the project file.
	 * 
	 * @param project
	 * @param name
	 */
	public static void writeProject(final Project<?> project, final String name) {
		File fileProject = getProjectFile(project, name);
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
		    
		    if (entry.hasDescription())
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
		
//		Map<String, ?> properties = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);
//		JsonWriter writer;
//		try {
//			writer = Json.createWriterFactory(properties).createWriter(new BufferedWriter(new FileWriter(fileProject)));
//			//			writer = Json.createWriter(new BufferedWriter(new FileWriter(fileProject)));
//			writer.writeObject(builder.build());
//			writer.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
	}


	/**
	 * Get a suitable project file.
	 * 
	 * Note: This behavior has changed from <= v0.1.2.
	 * The earlier code often ignored the <code>name</code>.
	 * 
	 * @param project
	 * @param name
	 * @return
	 */
	static File getProjectFile(final Project<?> project, String name) {
		if (project == null)
			return null;
		
		// Use default name
		if (name == null || name.length() == 0) {
			// If we already have a file, use that
			if (project.getFile() != null && (project.getFile() != project.getBaseDirectory()))
				return project.getFile();
			// Default to project.qpproj
			name = DEFAULT_PROJECT_NAME;
		}

		// We need a base directory
		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory()) {
			logger.warn("No base directory for project!");
			return null;
		}
		
		// Return File with extension
		if (!name.endsWith("."))
			name += ".";
		return new File(dirBase, name + ProjectIO.getProjectExtension());
	}
	
	
	/**
	 * Get a suitable project file.
	 * 
	 * This method has been deprecated as it can produce surprising results;
	 * specifically, the <code>name</code> is often ignored.
	 * 
	 * @getProjectFile
	 * 
	 * @param project
	 * @param name
	 * @return
	 */
	@Deprecated
	static File getProjectPath(final Project<?> project, String name) {
		if (project == null)
			return null;

		if (project.getFile() != null && project.getFile().isFile())
			return project.getFile();

		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;

		if (name == null || name.length() == 0)
			name = "project.";
		else if (!name.endsWith("."))
			name += ".";

		return new File(dirBase, name + ProjectIO.getProjectExtension());
	}


	/**
	 * Get the default extension for a QuPath project file.
	 * 
	 * @return qpproj
	 */
	public static String getProjectExtension() {
		return DEFAULT_PROJECT_EXTENSION;
	}




}
