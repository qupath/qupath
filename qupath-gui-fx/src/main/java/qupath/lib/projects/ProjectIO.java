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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Read/write QuPath projects.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectIO {

	final private static Logger logger = LoggerFactory.getLogger(ProjectIO.class);

	/**
	 * Read project from file.
	 * 
	 * @param fileProject
	 * @param cls
	 * @return
	 * @throws FileNotFoundException
	 */
	public static <T> Project<T> loadProject(final File fileProject, final Class<T> cls) throws FileNotFoundException {
		if (fileProject == null) {
			return null;
		}


		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Project<T> project = new Project<T>(fileProject, cls);

			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			
			project.creationTimestamp = element.get("createTimestamp").getAsLong();
			project.modificationTimestamp = element.get("modifyTimestamp").getAsLong();

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
				project.addImage(new ProjectImageEntry<>(project, imageObject.get("path").getAsString(), imageObject.get("name").getAsString(), metadataMap));
			}

			return project;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}


	/**
	 * Write project with default name of 'project'
	 * 
	 * @param project
	 */
	public static void writeProject(final Project<?> project) {
		writeProject(project, "project");
	}

	/**
	 * Write project, setting the name of the project file.
	 * 
	 * @param project
	 * @param name
	 */
	public static void writeProject(final Project<?> project, final String name) {
		File fileProject = getProjectPath(project, name);
		if (fileProject == null) {
			logger.error("Cannot write project: {}", project);
			return;
		}

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		JsonArray array = new JsonArray();
		for (ProjectImageEntry<?> entry : project.getImageList()) {
			JsonObject jsonEntry = new JsonObject();
		    jsonEntry.addProperty("path", entry.getStoredServerPath());
		    jsonEntry.addProperty("name", entry.getImageName());

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
		builder.add("images", array);


		try (PrintWriter writer = new PrintWriter(fileProject)) {
			writer.write(gson.toJson(builder));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

//		Map<String, ?> properties = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);
//		JsonWriter writer;
//		try {
//			writer = Json.createWriterFactory(properties).createWriter(new BufferedWriter(new FileWriter(fileProject)));
//			//			writer = Json.createWriter(new BufferedWriter(new FileWriter(fileProject)));
//			writer.writeObject(builder.build());
//			writer.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
	}





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
		return "qpproj";
	}




}
