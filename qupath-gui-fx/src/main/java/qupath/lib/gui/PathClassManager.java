/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener.Change;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.io.PathIO;
import qupath.lib.objects.classes.PathClass;

/**
 * Manage the default list of {@linkplain PathClass PathClasses} that should be presented to a user.
 * This takes care of saving/loading the list, and ensuring that it is valid (e.g. always contains a 
 * null option, and never contains duplicates).
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class PathClassManager {
	
	private static final Logger logger = LoggerFactory.getLogger(PathClassManager.class);
	
	/**
	 * Default list of PathClasses to show in the UI
	 */
	private static final List<PathClass> DEFAULT_PATH_CLASSES = Collections.unmodifiableList(Arrays.asList(
			PathClass.NULL_CLASS,
			PathClass.StandardPathClasses.TUMOR,
			PathClass.StandardPathClasses.STROMA,
			PathClass.StandardPathClasses.IMMUNE_CELLS,
			PathClass.StandardPathClasses.NECROSIS,
			PathClass.StandardPathClasses.OTHER,
			PathClass.StandardPathClasses.REGION,
			PathClass.StandardPathClasses.IGNORE,
			PathClass.StandardPathClasses.POSITIVE,
			PathClass.StandardPathClasses.NEGATIVE
			));
	
	private ObservableList<PathClass> availablePathClasses = FXCollections.observableArrayList();;

	private PathClassManager() {
		this.initializePathClasses();
	}
	
	static PathClassManager create() {
		return new PathClassManager();
	}
	
	/**
	 * Initialize available PathClasses, either from saved list or defaults
	 */
	private void initializePathClasses() {
		Set<PathClass> pathClasses = new LinkedHashSet<>();		
		pathClasses.addAll(loadPathClassesFromPrefs());			
		if (pathClasses.isEmpty())
			resetAvailablePathClasses();
		else
			availablePathClasses.setAll(pathClasses);
		availablePathClasses.addListener(this::handlePathClassesChangeEvent);
	}
	
	
	/**
	 * Get an observable list of available PathClasses.
	 * @return
	 */
	public ObservableList<PathClass> getAvailablePathClasses() {
		return availablePathClasses;
	}
	
	
	/**
	 * Populate the availablePathClasses with a default list.
	 * 
	 * @return true if changes were mad to the available classes, false otherwise
	 */
	public boolean resetAvailablePathClasses() {
		return availablePathClasses.setAll(DEFAULT_PATH_CLASSES);
	}
	
	
	private void handlePathClassesChangeEvent(Change<? extends PathClass> c) {
		// We need a list for UI components (e.g. ListViews), but we want it to behave like a set
		// Therefore if we find some non-unique nor null elements, correct the list as soon as possible
		var list = c.getList();
		var set = new LinkedHashSet<PathClass>();
		set.add(PathClass.NULL_CLASS);
		set.addAll(list);
		set.remove(null);
		if (!(set.size() == list.size() && set.containsAll(list))) {
			logger.warn("Invalid PathClass list modification: {} will be corrected to {}", list, set);
			Platform.runLater(() -> availablePathClasses.setAll(set));
			return;
		}
	}
	
	
	/**
	 * Load PathClasses from preferences.
	 * Note that this also sets the color of any PathClass that is loaded,
	 * and is really only intended for use when initializing.
	 * 
	 * @return
	 */
	private static List<PathClass> loadPathClassesFromPrefs() {
		byte[] bytes = PathPrefs.getUserPreferences().getByteArray("defaultPathClasses", null);
		if (bytes == null || bytes.length == 0)
			return Collections.emptyList();
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		try (ObjectInputStream in = PathIO.createObjectInputStream(stream)) {
			List<PathClass> pathClassesOriginal = (List<PathClass>)in.readObject();
			List<PathClass> pathClasses = new ArrayList<>();
			for (PathClass pathClass : pathClassesOriginal) {
				PathClass singleton = PathClass.getSingleton(pathClass);
				// Ensure the color is set
				if (singleton != null && pathClass.getColor() != null)
					singleton.setColor(pathClass.getColor());
				pathClasses.add(singleton);
			}
			return pathClasses;
		} catch (Exception e) {
			logger.error("Error loading classes", e);
			return Collections.emptyList();
		}
	}
	
	
	/**
	 * Save available PathClasses to preferences.
	 */
	public void savePathClassesToPreferences() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(stream)) {
			List<PathClass> pathClasses = new ArrayList<>(availablePathClasses);
			out.writeObject(pathClasses);
			out.flush();
		} catch (IOException e) {
			logger.error("Error saving classes", e);
		}
		byte[] bytes = stream.toByteArray();
		if (bytes.length < 0.75*Preferences.MAX_VALUE_LENGTH)
			PathPrefs.getUserPreferences().putByteArray("defaultPathClasses", bytes);
		else
			logger.error("Classification list too long ({} bytes) - cannot save it to the preferences.", bytes.length);
	}
	

}
