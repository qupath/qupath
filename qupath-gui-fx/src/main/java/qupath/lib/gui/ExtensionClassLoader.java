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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ClassLoader} for loading QuPath extensions and other jars from the user directory.
 * 
 * @author Pete Bankhead
 */
public class ExtensionClassLoader extends URLClassLoader {

	private static final Logger logger = LoggerFactory.getLogger(ExtensionClassLoader.class);

	private static ExtensionClassLoader INSTANCE = null;

	private Supplier<Path> extensionsDirectorySupplier;

	private ExtensionClassLoader(Supplier<Path> extensionsDirectorySupplier) {
		super(new URL[0], QuPathGUI.class.getClassLoader());
		Objects.requireNonNull(extensionsDirectorySupplier, "A function is needed to determine the extensions directory!");
		this.extensionsDirectorySupplier = extensionsDirectorySupplier;
	}

	/**
	 * Get a singleton instance of the {@link ExtensionClassLoader}.
	 * @return
	 * @since v0.5.0
	 * 
	 * @implNote This was introduced in v0.5.0 to hide the constructor and avoid needing to request 
	 * the classloader via QuPathGUI. <i>However</i> the behavior may change in the future, so as to 
	 * avoid relying upon a single static instance.
	 */
	public static ExtensionClassLoader getInstance() {
		if (INSTANCE == null) {
			synchronized (ExtensionClassLoader.class) {
				if (INSTANCE == null)
					INSTANCE = new ExtensionClassLoader(() -> getExtensionsDirectory());
			}
		}
		return INSTANCE;
	}


	private static Path getExtensionsDirectory() {
		return UserDirectoryManager.getInstance().getUserPath();
	}


	/**
	 * Directory containing extensions.
	 * 
	 * This can contain any jars - all will be added to the search path when starting QuPath.
	 * 
	 * @return
	 */
	public Path getExtensionDirectory() {
		return extensionsDirectorySupplier == null ? null : extensionsDirectorySupplier.get();
	}

	/**
	 * Ensure all Jars in the extensions directory (and one subdirectory down) are available
	 */
	public void refresh() {
		Path dirExtensions = extensionsDirectorySupplier.get();
		if (dirExtensions == null) {
			logger.debug("Extensions directory is null - no extensions will be loaded");
			return;
		}
		if (!Files.exists(dirExtensions)) {
			logger.debug("No extensions directory exists at {}", dirExtensions);
			return;			
		}
		if (!Files.isDirectory(dirExtensions)) {
			logger.error("Invalid extensions directory! '{}' is not a directory.", dirExtensions);
			return;
		}
		int depth = 1;
		try {
			Files.walk(dirExtensions, depth, FileVisitOption.FOLLOW_LINKS)
				.filter(this::isJarFile)
				.map(p -> p.toAbsolutePath())
				.distinct()
				.forEach(this::addJarFile);
		} catch (IOException e) {
			logger.error("Exception refreshing extensions: " + e.getLocalizedMessage(), e);
		}
	}
	
	private void addJarFile(Path path) {
		try {
			addURL(path.toUri().toURL());
			logger.info("Adding jar: {}", path);
		} catch (MalformedURLException e) {
			logger.debug("Error adding " + path + " to classpath", e);
		}
	}
	
	private boolean isJarFile(Path path) {
		return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar");
	}
	

}
