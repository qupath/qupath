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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

	private final Supplier<Path> extensionsDirectorySupplier;

	private final Set<Path> loadedJars = new HashSet<>();

	private boolean isClosed = false;

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
					INSTANCE = new ExtensionClassLoader(ExtensionClassLoader::getDefaultExtensionsDirectory);
			}
		}
		return INSTANCE;
	}


	private static Path getDefaultExtensionsDirectory() {
		return UserDirectoryManager.getInstance().getExtensionsPath();
	}


	/**
	 * Directory containing extensions.
	 *
	 * This can contain any jars - all will be added to the search path when starting QuPath.
	 *
	 * @return
	 */
	public Path getExtensionsDirectory() {
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
		try {
			try (Stream<Path> walk = Files.walk(dirExtensions, FileVisitOption.FOLLOW_LINKS)) {
				walk
						.filter(this::isJarFile)
						.map(Path::toAbsolutePath)
						.distinct()
						.forEach(this::addJarFile);
			}
		} catch (IOException e) {
			logger.error("Exception refreshing extensions: " + e.getLocalizedMessage(), e);
		}
	}

	private void addJarFile(Path path) {
		try {
			if (loadedJars.add(path)) {
				if (isClosed) {
					logger.warn("Extension classloader has been closed - you need to restart QuPath to add new extensions");
					return;
				}
				var url = path.toUri().toURL();
				addURL(url);
				logger.info("Adding jar: {}", path);
			}
		} catch (Exception e) {
			logger.debug("Error adding " + path + " to classpath", e);
		}
	}

	@Override
	public void close() throws IOException {
		// Retain a flag to avoid failing to load jars silently (without logging)
		isClosed = true;
		super.close();
	}

	private boolean isJarFile(Path path) {
		return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar");
	}

}
