/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Helper class for managing a user directory, and associated subdirectories.
 * <p>
 * Note that only the main user directory can be directly specified. 
 * Other directories are derived from this.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class UserDirectoryManager {
	
	private static final Logger logger = LoggerFactory.getLogger(UserDirectoryManager.class);
	
	/**
	 * Name of subdirectory containing extensions.
	 */
	public static final String DIR_EXTENSIONS = "extensions";

	/**
	 * Name of subdirectory containing css files for styling.
	 */
	public static final String DIR_CSS = "css";

	/**
	 * Name of subdirectory containing shared scripts.
	 */
	public static final String DIR_SCRIPTS = "scripts";

	/**
	 * Name of subdirectory containing colormaps.
	 */
	public static final String DIR_COLORMAPS = "colormaps";

	/**
	 * Name of subdirectory containing log files.
	 */
	public static final String DIR_LOGS = "logs";

	/**
	 * Name of subdirectory containing properties files for localization.
	 */
	public static final String DIR_LOCALIZATION = "localization";
	
	private static UserDirectoryManager INSTANCE;

	private ObjectProperty<Path> userDirectoryProperty = new SimpleObjectProperty<>();

	private ReadOnlyObjectProperty<Path> extensionsDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_EXTENSIONS);
	private ReadOnlyObjectProperty<Path> colormapsDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_COLORMAPS);
	private ReadOnlyObjectProperty<Path> cssDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_CSS);
	private ReadOnlyObjectProperty<Path> logDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_LOGS);
	private ReadOnlyObjectProperty<Path> localizationDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_LOCALIZATION);
	private ReadOnlyObjectProperty<Path> scriptsDirectoryProperty = createRelativePathProperty(userDirectoryProperty, DIR_SCRIPTS);

	
	private UserDirectoryManager() {
		PathPrefs.userPathProperty().addListener((v, o, n) -> syncPathPropertyToPrefs(n));
		userDirectoryProperty.addListener((v, o, n) -> syncPrefsToPathProperty(n));
		syncPathPropertyToPrefs(PathPrefs.userPathProperty().get());
	}
	
	/**
	 * Get the singleton instance of the UserDirectoryManager.
	 * @return
	 */
	public static UserDirectoryManager getInstance() {
		if (INSTANCE == null) {
			synchronized (UserDirectoryManager.class) {
				if (INSTANCE == null)
					INSTANCE = new UserDirectoryManager();
			}
		}
		return INSTANCE;
	}
	
	private void syncPathPropertyToPrefs(String path) {
		if (path == null)
			userDirectoryProperty.set(null);
		else {
			try {
				var newPath = Paths.get(path);
				if (Files.isDirectory(newPath))
					userDirectoryProperty.set(newPath);
				else {
					logger.error("Cannot set user directory - {} does note exist", newPath);
					var currentPath = userDirectoryProperty.get();
					if (currentPath == null || Files.isDirectory(currentPath))
						syncPrefsToPathProperty(currentPath);
					else
						userDirectoryProperty.set(null);
				}
			} catch (InvalidPathException e) {
				logger.error("Cannot set user path to {} ({})", path, e.getLocalizedMessage());
				logger.debug(e.getLocalizedMessage(), e);
			}
		}
	}
	
	private void syncPrefsToPathProperty(Path path) {
		if (path == null)
			PathPrefs.userPathProperty().set(null);
		else
			PathPrefs.userPathProperty().set(path.toString());
	}
	
	/**
	 * Create a read only property for a relative path of the user subdirectory.
	 * This can be used if the default subdirectories aren't sufficient.
	 * @param other
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> createRelativePathProperty(String other) {
		return createRelativePathProperty(userDirectoryProperty(), other);
	}
	
	private ReadOnlyObjectProperty<Path> createRelativePathProperty(ObservableValue<Path> path, String other) {
		var wrapper = new ReadOnlyObjectWrapper<Path>();
		path.addListener((v, o, n) -> wrapper.set(resolvePathOrNull(n, other)));
		return wrapper.getReadOnlyProperty();
	}
	
	private static Path resolvePathOrNull(Path path, String other) {
		if (path == null)
			return null;
		return path.resolve(other);
	}
	
	/**
	 * Property representing the user directory.
	 * This may be null if the directory has not been set.
	 * @return
	 */
	public ObjectProperty<Path> userDirectoryProperty() {
		return userDirectoryProperty;
	}
	
	/**
	 * Read only property representing the extensions directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_EXTENSIONS}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> extensionsDirectoryProperty() {
		return extensionsDirectoryProperty;
	}
	
	/**
	 * Read only property representing the css directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_CSS}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> cssDirectoryProperty() {
		return cssDirectoryProperty;
	}
	
	/**
	 * Read only property representing the localization directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_LOCALIZATION}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> localizationDirectoryProperty() {
		return localizationDirectoryProperty;
	}
	
	/**
	 * Read only property representing the log file directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_LOGS}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> logDirectoryProperty() {
		return logDirectoryProperty;
	}
	
	/**
	 * Read only property representing the shared scripts directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_SCRIPTS}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> scriptsDirectoryProperty() {
		return scriptsDirectoryProperty;
	}
	
	/**
	 * Read only property representing the custom colormaps directory.
	 * This is based upon {@link #userDirectoryProperty} and {@link #DIR_COLORMAPS}.
	 * @return
	 */
	public ReadOnlyObjectProperty<Path> colormapsDirectoryProperty() {
		return colormapsDirectoryProperty;
	}
	
	/**
	 * Get the user path where additional files may be stored.
	 * @return
	 */
	public Path getUserPath() {
		return userDirectoryProperty().get();
	}
	
	public Path getRelativePathOrNull(String other) {
		var userPath = getUserPath();
		if (userPath == null)
			return null;
		else
			return userPath.resolve(other);
	}
	
	/**
	 * Set the user path where additional files may be stored.
	 * Note that the specified path must exist and must represent a directory.
	 * @param path 
	 * @throws IllegalArgumentException if the path is not null, but does not represent a valid, existing directory
	 */
	public void setUserPath(Path path) throws IllegalArgumentException {
		if (path != null && Files.exists(path)) {
			if (!Files.isDirectory(path)) {
				throw new IllegalArgumentException("User path " + path + " exists, but is not a directory!");
			}
		}
		userDirectoryProperty.set(path);
	}
	
	/**
	 * Get the path to where extensions should be stored. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected extensions directory.
	 */
	public Path getExtensionsPath() {
		return extensionsDirectoryProperty.get();
	}
	
	/**
	 * Get the path to where user directory for storing CSS styles. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected CSS directory.
	 * @since v0.4.0
	 */
	public Path getCssStylesPath() {
		return cssDirectoryProperty().get();
	}
	
	/**
	 * Get the path to where log files should be written. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected log file directory.
	 */
	public Path getLogDirectoryPath() {
		return logDirectoryProperty().get();
	}
	
	/**
	 * Get the path to where localization property files should be written. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected localization file directory.
	 */
	public Path getLocalizationDirectoryPath() {
		return localizationDirectoryProperty().get();
	}
	
	/**
	 * Get the path to where colormaps should be written. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected directory.
	 */
	public Path getColormapsDirectoryPath() {
		return colormapsDirectoryProperty().get();
	}
	
	/**
	 * Get the path to where shared script files should be written. This depends upon {@link #userDirectoryProperty()}.
	 * @return the path if available, or null if {@link #getUserPath()} returns null
	 * @implSpec a non-null return value does not guarantee that the path exists, rather it just represents the expected directory.
	 */
	public Path getScriptsDirectoryPath() {
		return scriptsDirectoryProperty().get();
	}
	

}
