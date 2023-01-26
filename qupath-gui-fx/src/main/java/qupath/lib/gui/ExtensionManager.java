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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.common.Version;
import qupath.lib.gui.dialogs.Dialogs;
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
	
	ExtensionManager(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	/**
	 * @return a collection of extensions that are currently loaded
	 */
	public Collection<QuPathExtension> getLoadedExtensions() {
		return loadedExtensions.values();
	}
	
	public ReadOnlyBooleanProperty refreshingExtensions() {
		return BooleanProperty.readOnlyBooleanProperty(refreshingExtensions);
	}
	
	/**
	 * Check the extensions directory, loading any new extensions found there.
	 * @param showNotification if true, display a notification if a new extension has been loaded
	 */
	public synchronized void refreshExtensions(final boolean showNotification) {
		
		refreshingExtensions.set(true);
		
		// Refresh the extensions
		var extensionClassLoader = ExtensionClassLoader.getInstance();
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
					qupath.getDefaultActions().SHOW_LOG.handle(null);
				}
			}
		}
		// Set the ImageServer to also look on the same search path
		List<ImageServerBuilder<?>> serverBuildersBefore = ImageServerProvider.getInstalledImageServerBuilders();
		ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));
		if (showNotification) {
			// A bit convoluted... but try to show new servers that have been loaded by comparing with the past
			List<String> serverBuilders = serverBuildersBefore.stream().map(s -> s.getName()).collect(Collectors.toList());
			List<String> serverBuildersUpdated = ImageServerProvider.getInstalledImageServerBuilders().stream().map(s -> s.getName()).collect(Collectors.toList());
			serverBuildersUpdated.removeAll(serverBuilders);
			for (String builderName : serverBuildersUpdated) {
				Dialogs.showInfoNotification("Image server loaded",  builderName);
			}
		}
		refreshingExtensions.set(false);
	}
	
	
	/**
	 * Install extensions while QuPath is running.
	 * 
	 * @param files A collection of jar files for installation.
	 */
	public void installExtensions(final Collection<File> files) {
		if (files.isEmpty()) {
			logger.debug("No extensions to install!");
			return;
		}

		File dir = QuPathGUI.getExtensionDirectory();
		if (dir == null || !dir.isDirectory()) {
			logger.info("No extension directory found!");
			var dirUser = QuPathGUI.requestUserDirectory(true);
			if (dirUser == null)
				return;
			dir = QuPathGUI.getExtensionDirectory();
		}
		// Create directory if we need it
		if (!dir.exists())
			dir.mkdir();
		
		// Copy all files into extensions directory
		Path dest = dir.toPath();
		for (File file : files) {
			Path source = file.toPath();
			Path destination = dest.resolve(source.getFileName());
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