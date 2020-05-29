/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ClassLoader} for loading QuPath extensions and other jars from the user directory.
 * 
 * @author Pete Bankhaad
 */
public class ExtensionClassLoader extends URLClassLoader {
	
	private final static Logger logger = LoggerFactory.getLogger(ExtensionClassLoader.class);

	/**
	 * Constructor.
	 */
	public ExtensionClassLoader() {
		super(new URL[0], QuPathGUI.class.getClassLoader());
	}

	/**
	 * Request that a specified JAR file be added to the classpath.
	 * 
	 * @param file
	 * @return
	 */
	boolean addJAR(final File file) {
		try (JarFile jar = new JarFile(file)) {
			if (jar.entries().hasMoreElements()) {
				addURL(file.toURI().toURL());
				return true;
			}
		} catch (IOException e) {
			logger.error("Unable to add file to classpath", e);
		}
		return false;
	}

	/**
	 * Ensure all Jars in the extensions directory (and one subdirectory down) are available
	 */
	void refresh() {
		File dirExtensions = QuPathGUI.getExtensionDirectory();
		if (dirExtensions == null) {
			logger.debug("Extensions directory is null - no extensions will be loaded");
			return;
		}
		if (!dirExtensions.isDirectory()) {
			logger.error("Invalid extensions directory! '{}' is not a directory.", dirExtensions);
			return;
		}
		refreshExtensions(dirExtensions);
		for (File dir : dirExtensions.listFiles()) {
			if (!dir.isHidden() && dir.isDirectory()) {
				Path dirPath = dir.toPath();
				if (Files.isSymbolicLink(dirPath))
					try {
						dir = Files.readSymbolicLink(dirPath).toFile();
					} catch (IOException e) {
						logger.error("Error refreshing extensions", e);
					}
				refreshExtensions(dir);
			}
		}
	}

	/**
	 * Ensure all Jars from the specified directory are available.
	 * 
	 * @param dirExtensions
	 */
	private void refreshExtensions(final File dirExtensions) {
		if (dirExtensions == null) {
			logger.debug("No extensions directory specified");				
			return;
		} else if (!dirExtensions.isDirectory()) {
			logger.warn("Cannot load extensions from " + dirExtensions + " - not a valid directory");	
			return;
		}
		logger.info("Refreshing extensions in " + dirExtensions);				
		for (File file : dirExtensions.listFiles()) {
			if (file.getName().toLowerCase().endsWith(".jar")) {
				try {
					addURL(file.toURI().toURL());
					logger.info("Added extension: " + file.getAbsolutePath());
				} catch (MalformedURLException e) {
					logger.debug("Error adding {} to classpath", file, e);
				}
			}
		}
	}

}