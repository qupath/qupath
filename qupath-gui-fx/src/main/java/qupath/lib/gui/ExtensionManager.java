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

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.common.Version;
import qupath.lib.gui.commands.Commands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;

/**
 * Manage loading extensions for a QuPathGUI instance.
 * 
 * @author Pete Bankhead
 * 
 * @since v0.5.0
 */
public class ExtensionManager {
	
	private static final Logger logger = LoggerFactory.getLogger(ExtensionManager.class);

	private QuPathGUI qupath;
	
	private Map<Class<? extends QuPathExtension>, QuPathExtension> loadedExtensions = new HashMap<>();

	private BooleanProperty refreshingExtensions = new SimpleBooleanProperty(false);
	
	private ExtensionManager(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	static ExtensionManager create(QuPathGUI qupath) {
		return new ExtensionManager(qupath);
	}
	
	/**
	 * @return a collection of extensions that are currently loaded
	 */
	public Collection<QuPathExtension> getLoadedExtensions() {
		return loadedExtensions.values();
	}
	
	/**
	 * Property indicating whether extensions are in the process of being refreshed.
	 * @return
	 */
	public ReadOnlyBooleanProperty refreshingExtensions() {
		return BooleanProperty.readOnlyBooleanProperty(refreshingExtensions);
	}
	
	/**
	 * Check the extensions directory, loading any new extensions found there.
	 * @param showNotification if true, display a notification if a new extension has been loaded
	 */
	public synchronized void refreshExtensions(final boolean showNotification) {
		
		if ("true".equalsIgnoreCase(System.getProperty("noextensions"))) {
			logger.info("Extensions will be skipped - 'noextensions' system property is set");
			return;
		}
		
		refreshingExtensions.set(true);
		
		// Refresh the extensions
		var extensionClassLoader = getExtensionClassLoader();
		extensionClassLoader.refresh();
		
		var extensionLoader = ServiceLoader.load(QuPathExtension.class, extensionClassLoader);

		// Sort the extensions by name, to ensure predictable loading order
		// (also, menus are in a better order if ImageJ extension installed before OpenCV extension)
		List<QuPathExtension> extensions = new ArrayList<>();
		Iterator<QuPathExtension> iterator = extensionLoader.iterator();
		while (iterator.hasNext()) {
			try {
				extensions.add(iterator.next());
			} catch (Throwable e) {
				if (qupath.getStage() != null && qupath.getStage().isShowing()) {
					Dialogs.showErrorMessage("Extension error", "Error loading extension - check 'View -> Show log' for details.");
				}
				logger.error(e.getLocalizedMessage(), e);
			}
		}
		Collections.sort(extensions, Comparator.comparing(QuPathExtension::getName));
		Version qupathVersion = QuPathGUI.getVersion();
		for (QuPathExtension extension : extensions) {
			if (!loadedExtensions.containsKey(extension.getClass())) {
				Version version = extension.getVersion();
				try {
					long startTime = System.currentTimeMillis();
					extension.installExtension(qupath);
					long endTime = System.currentTimeMillis();
					logger.info("Loaded extension {} ({} ms)", extension.getName(), endTime - startTime);
					if (version != null)
						logger.debug("{} was written for QuPath {}", extension.getName(), version);
					else
						logger.debug("{} does not report a compatible QuPath version", extension.getName());						
					loadedExtensions.put(extension.getClass(), extension);
					if (showNotification)
						Dialogs.showInfoNotification("Extension loaded",  extension.getName());
				} catch (Exception | LinkageError e) {
					String message = "Unable to load " + extension.getName();
					if (showNotification)
						Dialogs.showErrorNotification("Extension error", message);
					logger.error("Error loading extension " + extension + ": " + e.getLocalizedMessage(), e);
					if (!Objects.equals(qupathVersion, version)) {
						if (version == null)
							logger.warn("QuPath version for which the '{}' was written is unknown!", extension.getName());
						else if (version.equals(qupathVersion))
							logger.warn("'{}' reports that it is compatible with the current QuPath version {}", extension.getName(), qupathVersion);
						else
							logger.warn("'{}' was written for QuPath {} but current version is {}", extension.getName(), version, qupathVersion);
					}
					try {
						logger.error("It is recommended that you delete {} and restart QuPath",
								URLDecoder.decode(
										extension.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm(),
										StandardCharsets.UTF_8));
					} catch (Exception e2) {
						logger.debug("Error finding code source " + e2.getLocalizedMessage(), e2);
					}
					qupath.getCommonActions().SHOW_LOG.handle(null);
				}
			}
		}
		// Set the ImageServer to also look on the same search path
		List<ImageServerBuilder<?>> serverBuildersBefore = ImageServerProvider.getInstalledImageServerBuilders();
		ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));
		if (showNotification) {
			// A bit convoluted... but try to show new servers that have been loaded by comparing with the past
			List<String> serverBuilders = serverBuildersBefore.stream().map(s -> s.getName()).toList();
			List<String> serverBuildersUpdated = ImageServerProvider.getInstalledImageServerBuilders().stream().map(s -> s.getName()).toList();
			serverBuildersUpdated.removeAll(serverBuilders);
			for (String builderName : serverBuildersUpdated) {
				Dialogs.showInfoNotification("Image server loaded",  builderName);
			}
		}
		refreshingExtensions.set(false);
	}
	
	
	private ExtensionClassLoader getExtensionClassLoader() {
		return ExtensionClassLoader.getInstance();
	}
	
	
	
	/**
	 * Copy a collection of files to QuPath's extensions directory, notifying the user about
	 * what is done and prompting to create a user directory if needed.
	 * 
	 * @param files a collection of jar files for installation
	 */
	public void promptToCopyFilesToExtensionsDirectory(final Collection<File> files) {
		if (files.isEmpty()) {
			logger.debug("No extensions to install!");
			return;
		}

		var extensionClassLoader = getExtensionClassLoader();
		var dir = extensionClassLoader.getExtensionDirectory();
		
		if (dir == null || !Files.isDirectory(dir)) {
			logger.info("No extension directory found!");
			var dirUser = Commands.requestUserDirectory(true);
			if (dirUser == null)
				return;
			dir = extensionClassLoader.getExtensionDirectory();
		}
		// Create directory if we need it
		if (!Files.exists(dir)) {
			logger.info("Creating extensions directory: {}", dir);
			try {
				Files.createDirectories(dir);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Install extensions", "Error trying to install extensions: " + e.getLocalizedMessage());
				logger.error(e.getLocalizedMessage(), e);
			}
		}
		
		// Copy all files into extensions directory
		for (File file : files) {
			Path source = file.toPath();
			Path destination = dir.resolve(source.getFileName());
			if (destination.toFile().exists()) {
				// It would be better to check how many files will be overwritten in one go,
				// but this should be a pretty rare occurrence
				if (!Dialogs.showConfirmDialog("Install extension", "Overwrite " + destination.toFile().getName() + "?\n\nYou will have to restart QuPath to see the updates."))
					return;
			}
			try {
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Extension error", file + "\ncould not be copied, sorry");
				logger.error("Could not copy file {}", file, e);
				return;
			}
		}
		refreshExtensions(true);
	}
	
	
}