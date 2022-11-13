/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Static methods to import object(s) from different sources.
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
 */
public final class InteractiveObjectImporter {
	
	private static final Logger logger = LoggerFactory.getLogger(InteractiveObjectImporter.class);
	
	/**
	 * Mime type for GeoJson
	 */
	public static final String MIME_TYPE_GEOJSON = "application/geo+json";

	
	/**
	 * Get a {@link DataFormat} suitable for storing GeoJSON data on a clipboard.
	 * @return
	 */
	public static DataFormat getGeoJsonDataFormat() {
		var df = DataFormat.lookupMimeType(MIME_TYPE_GEOJSON);
		if (df == null)
			df = new DataFormat("application/geo+json");
		return df;
	}
	

	// Suppress default constructor for non-instantiability
	private InteractiveObjectImporter() {
		throw new AssertionError();
	}
	
	/**
	 * Prompt to import objects from a project entry.
	 * @param imageData the image to which the objects should be added
	 * @param entry the entry containing the objects to add
	 * @return true if objects were added, false otherwise
	 */
	public static boolean promptToImportObjectsProjectEntry(ImageData<BufferedImage> imageData, ProjectImageEntry<?> entry) {
		Objects.requireNonNull(imageData, "Can't import objects - ImageData is null");
		Objects.requireNonNull(entry, "Can't import objects - project entry is null");
		
		try {
			var hierarchy = entry.readHierarchy();
			
			var pathObjects = new ArrayList<>(hierarchy.getRootObject().getChildObjects());
			if (pathObjects.isEmpty()) {
				logger.warn("No objects found in entry {}", entry);
				return false;
			} else {
				return promptToImportObjects(imageData.getHierarchy(), pathObjects);
			}
		} catch (Exception e) {
			Dialogs.showErrorNotification("Import objects from project", e.getLocalizedMessage());
			return false;
		}
	}
	
	
	/**
	 * Try to read objects from the system clipboard.
	 * @param imageData the image to which the objects should be added
	 * @return true a list of objects read from the system clipboard, or empty list if none could be found
	 * @throws IOException 
	 * @throws JsonSyntaxException 
	 * @throws JsonParseException 
	 */
	public static List<PathObject> readObjectsFromClipboard(ImageData<BufferedImage> imageData) throws IOException, JsonSyntaxException, JsonParseException {
		Objects.requireNonNull(imageData, "Can't import objects - ImageData is null");
		
		var clipboard = Clipboard.getSystemClipboard();
		String geojson = (String)clipboard.getContent(getGeoJsonDataFormat());
		if (geojson == null) {
			geojson = (String)clipboard.getContent(DataFormat.PLAIN_TEXT);
		}
		
		if (geojson == null || !(geojson.contains("\"feature\"") || geojson.contains("\"geometry\""))) {
			return Collections.emptyList();
		}
		
		try (var stream = new ByteArrayInputStream(geojson.getBytes(StandardCharsets.UTF_8))) {
			return PathIO.readObjectsFromGeoJSON(stream);
		}
	}
	

	/**
	 * Prompt to paste objects from the system clipboard, if possible.
	 * @param imageData the image to which the objects should be added
	 * @return true if objects were added, false otherwise
	 */
	public static boolean promptToPasteObjectsFromClipboard(ImageData<BufferedImage> imageData) {
		try {
			var pathObjects = readObjectsFromClipboard(imageData);
			if (pathObjects.isEmpty()) {
				Dialogs.showWarningNotification("Paste objects", "No objects found on the clipboard!");
				return false;
			}
			return promptToImportObjects(imageData.getHierarchy(), pathObjects);
		} catch (Exception e) {
			Dialogs.showErrorMessage("Paste objects", "Unable to paste objects: " + e.getLocalizedMessage());
			return false;
		}
	}

		
		
	/**
	 * Prompt to import objects read from a file.
	 * 
	 * @param imageData the image to add the objects to
	 * @param file the file to read objects from; if null, a chooser will be shown
	 * @return true if objects were added, false otherwise
	 */
	public static boolean promptToImportObjectsFromFile(ImageData<BufferedImage> imageData, File file) {
		Objects.requireNonNull(imageData, "Can't import objects - file is null");

		if (file == null)
			file = Dialogs.promptForFile("Choose file to import", null, "QuPath objects", PathIO.getObjectFileExtensions(true).toArray(String[]::new));
		
		// User cancel
		if (file == null)
			return false;

		List<PathObject> pathObjects;
		try {
			pathObjects = PathIO.readObjects(file);
		} catch (IOException | IllegalArgumentException ex) {
			Dialogs.showErrorNotification("Error importing objects", ex.getLocalizedMessage());
			return false;
		}
		
		if (pathObjects.isEmpty()) {
			Dialogs.showWarningNotification("Import objects from file", "No objects found in " + file.getAbsolutePath());
			return false;
		}
		
		if (promptToImportObjects(imageData.getHierarchy(), pathObjects)) {
			// TODO: Add to workflow only if we also encode whether to update IDs
//			var map = Map.of("path", file.getPath());
//			String method = "Import objects";
//			String methodString = String.format("%s(%s%s%s)", "importObjectsFromFile", "\"", GeneralTools.escapeFilePath(file.getPath()), "\"");
//			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(method, map, methodString));
			return true;
		} else
			return false;		
		
	}
	
	/**
	 * Import a collection of objects to an object hierarchy, prompting to confirm and asking whether to update IDs.
	 * 
	 * @param hierarchy the object hierarchy
	 * @param pathObjects the new objects to add
	 * @return true if objects were added to the hierarchy, false otherwise (including if the pathobject list is empty)
	 */
	public static boolean promptToImportObjects(PathObjectHierarchy hierarchy, Collection<? extends PathObject> pathObjects) {
		Objects.requireNonNull(hierarchy, "Can't import objects - hierarchy is null");
		if (pathObjects.isEmpty()) {
			logger.debug("No objects to import to the hierarchy");
			return false;
		}
		
		// Get a set of all the objects to import
		var flatSet = pathObjects.stream(
				).flatMap(p -> PathObjectTools.getFlattenedObjectList(p, null, !p.isRootObject()).stream())
				.collect(Collectors.toSet());

		// Get a set of all the ids
		var idSet = flatSet.stream().map(p -> p.getID()).collect(Collectors.toCollection(HashSet::new));
		var existingIds = hierarchy.getFlattenedObjectList(null).stream().map(p -> p.getID()).collect(Collectors.toSet());
		boolean containsDuplicates = false;
		for (var id : idSet) {
			if (existingIds.contains(id)) {
				containsDuplicates = true;
			}
		}
		int nObjects = flatSet.size();
		String objString = nObjects == 1 ? "1 object" : nObjects + " objects";
		boolean fixDuplicates = false;
		String message;
		if (containsDuplicates) {
			message = "Update IDs for the new objects?\n\n"
					+ "This is strongly recommended to avoid multiple objects having the same ID.\n\n"
					+ "Only skip this step if you plan to handle duplicate IDs later.";
			var result = Dialogs.showYesNoCancelDialog("Import " + objString, message);
			if (result == DialogButton.CANCEL)
				return false;
			fixDuplicates = result == DialogButton.YES;
		} else {
			logger.info("Pasting {} - IDs unchanged (no duplicates)", objString);
		}

		if (fixDuplicates) {
			for (var toAdd : flatSet) {
				toAdd.refreshID();
			}
		} else if (containsDuplicates) {
			logger.warn("{} being added - IDs not updated, so there will be duplicates!", objString);
		}
		
		hierarchy.addObjects(pathObjects);
		return true;
	}
}