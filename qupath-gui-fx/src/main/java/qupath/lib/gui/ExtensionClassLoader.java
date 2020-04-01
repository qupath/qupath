package qupath.lib.gui;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * {@link ClassLoader} for loading QuPath extensions and other jars from the user directory.
 * 
 * @author Pete Bankhaad
 */
public class ExtensionClassLoader extends URLClassLoader {

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
			QuPathGUI.logger.error("Unable to add file to classpath", e);
		}
		return false;
	}

	/**
	 * Ensure all Jars in the extensions directory (and one subdirectory down) are available
	 */
	void refresh() {
		File dirExtensions = QuPathGUI.getExtensionDirectory();
		if (dirExtensions == null) {
			QuPathGUI.logger.debug("Extensions directory is null - no extensions will be loaded");
			return;
		}
		if (!dirExtensions.isDirectory()) {
			QuPathGUI.logger.error("Invalid extensions directory! '{}' is not a directory.", dirExtensions);
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
						QuPathGUI.logger.error("Error refreshing extensions", e);
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
			QuPathGUI.logger.debug("No extensions directory specified");				
			return;
		} else if (!dirExtensions.isDirectory()) {
			QuPathGUI.logger.warn("Cannot load extensions from " + dirExtensions + " - not a valid directory");	
			return;
		}
		QuPathGUI.logger.info("Refreshing extensions in " + dirExtensions);				
		for (File file : dirExtensions.listFiles()) {
			if (file.getName().toLowerCase().endsWith(".jar")) {
				try {
					addURL(file.toURI().toURL());
					QuPathGUI.logger.info("Added extension: " + file.getAbsolutePath());
				} catch (MalformedURLException e) {
					QuPathGUI.logger.debug("Error adding {} to classpath", file, e);
				}
			}
		}
	}

}