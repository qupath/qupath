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


package qupath.lib.gui.localization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.fx.localization.LocalizedResourceManager;
import qupath.lib.gui.UserDirectoryManager;

/**
 * Load strings from the default resource bundle.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class QuPathResources {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathResources.class);
	
	private static final QuPathResourceControl CONTROL = new QuPathResourceControl();
	
	private static final String DEFAULT_BUNDLE = "qupath/lib/gui/localization/qupath-gui-strings";

	private static final LocalizedResourceManager LOCALIZED_RESOURCE_MANAGER = LocalizedResourceManager.createInstance(DEFAULT_BUNDLE, new QuPathResources.QuPathResourceControl());

	/**
	 * Get a localized resource manager, which can be used to manage localized strings,
	 * and update these whenever the locale preferences are updated.
	 * @return
	 */
	public static LocalizedResourceManager getLocalizedResourceManager() {
		return LOCALIZED_RESOURCE_MANAGER;
	}

	/**
	 * Get a string from the main {@link ResourceBundle} used for the QuPath user interface.
	 * <p>
	 * This helps separate user interface strings from the main Java code, so they can be 
	 * maintained more easily - and potentially could be translated into different languages 
	 * if required.
	 * @param key
	 * @return
	 */
	public static String getString(String key) {
		return getString(DEFAULT_BUNDLE, key);
	}
	
	public static String getString(String bundle, String key) {
		return getBundleOrNull(bundle).getString(key);
	}
	
	public static boolean hasString(String key) {
		return hasString(DEFAULT_BUNDLE, key);
	}
	
	public static boolean hasString(String bundleName, String key) {
		var bundle = getBundleOrNull(bundleName);
		if (bundle != null)
			return bundle.containsKey(key);
		return false;
	}
	
	public static boolean hasBundleForLocale(String bundle, Locale locale) {
		if (locale == Locale.US || locale == Locale.ENGLISH)
			return true;
		return CONTROL.hasBundle(bundle, locale, QuPathResources.class.getClassLoader());
	}
	
	public static boolean hasDefaultBundleForLocale(Locale locale) {
		return hasBundleForLocale(DEFAULT_BUNDLE, locale);
	}
	
	private static ResourceBundle getBundleOrNull(String bundleName) {
		if (bundleName == null || bundleName.isEmpty())
			bundleName = DEFAULT_BUNDLE;
		try {
			return ResourceBundle.getBundle(
					bundleName,
					Locale.getDefault(Category.DISPLAY),
					QuPathResources.class.getClassLoader(),
					CONTROL
			);
		} catch (MissingResourceException e) {
			logger.error("Missing resource bundle {}", bundleName);
			return null;
		}
	}
	
	
	
	static class QuPathResourceControl extends ResourceBundle.Control {
		
		private static final Logger logger = LoggerFactory.getLogger(QuPathResourceControl.class);
		
		// Directory containing the code
		private Path codePath;
		
		QuPathResourceControl() {
			try {
				codePath = Paths.get(
						QuPathResources.class
						.getProtectionDomain()
						.getCodeSource()
						.getLocation()
						.toURI())
						.getParent();
			} catch (Exception e) {
				logger.debug("Error identifying code directory: " + e.getLocalizedMessage(), e);
			}
		}
		
		@Override
		public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) 
				throws IllegalAccessException, InstantiationException, IOException {

			// Try to get a bundle the 'normal' way
			ResourceBundle bundle = super.newBundle(baseName, locale, format, loader, reload);
			if (bundle != null)
				return bundle;

			// If the bundle is not found in the default location, see if we need to use the extension class loader.
			// (The default is the app class loader, because extensions are in the unnamed module.)
			if (loader != QuPathResources.class.getClassLoader()) {
				bundle = super.newBundle(
						baseName,
						locale,
						format,
						QuPathResources.class.getClassLoader(),
						reload
				);
				if (bundle != null)
					return bundle;
			}

			return searchForBundle(baseName, locale);
		}
		
		@Override
		public List<String> getFormats(String baseName) {
			return Collections.singletonList("java.properties");
		}
		
		/**
		 * Attempt to determine whether a bundle exists for a given locale <i>without</i> loading it, 
		 * if possible. Note that in some instances loading may still be necessary.
		 * @param baseName
		 * @param locale
		 * @param loader
		 * @return
		 */
		private boolean hasBundle(String baseName, Locale locale, ClassLoader loader) {
			try {
				if (super.newBundle(baseName, locale, "java.properties", loader, false) != null)
					return true;
			} catch (Exception e) {
				logger.debug(e.getLocalizedMessage(), e);
			}
			return searchForBundlePath(baseName, locale) != null;
		}
		
		
		private ResourceBundle searchForBundle(String baseName, Locale locale) {
			Path propertiesPath = searchForBundlePath(baseName, locale);
			if (propertiesPath != null) {
				try (var reader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
					logger.debug("Reading bundle from {}", propertiesPath);
					return new PropertyResourceBundle(reader);
				} catch (Exception e) {
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
			return null;
		}
		
		
		private Path searchForBundlePath(String baseName, Locale locale) {
			String propertiesName = getShortPropertyFileName(baseName, locale);
			for (var localizationDirectory : getLocalizationDirectoryPaths()) {
				logger.debug("Searching for {} in {}", propertiesName, localizationDirectory);
				try {
					var propertiesPath = localizationDirectory.resolve(propertiesName);
					if (Files.isRegularFile(propertiesPath)) {
						return propertiesPath;
					}
				} catch (Exception e) {
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
			return null;
		}
		
		
		private String getShortPropertyFileName(String baseName, Locale locale) {
			String bundleName = toBundleName(baseName, locale);
			int ind = bundleName.replace('.', '/').lastIndexOf('/');
			String propertiesBaseName = ind < 0 ? bundleName : bundleName.substring(ind+1);
			return propertiesBaseName + ".properties";
		}
		
		
		private List<Path> getLocalizationDirectoryPaths() {
			List<Path> paths = new ArrayList<>();
			var userSearchPath = getUserLocalizationDirectoryOrNull();
			if (userSearchPath != null)
				paths.add(userSearchPath);
			var codeSearchPath = getCodeLocalizationDirectoryOrNull();
			if (codeSearchPath != null)
				paths.add(codeSearchPath);			
			return paths;
		}
		
		private static Path getDirectoryOrNull(Path path) {
			if (path != null && Files.isDirectory(path))
				return path;
			return null;
		}
		
		private Path getUserLocalizationDirectoryOrNull() {
			var userPath = UserDirectoryManager.getInstance().getLocalizationDirectoryPath();
			if (userPath != null)
				return getDirectoryOrNull(userPath);
			return null;
		}
		
		private Path getCodeLocalizationDirectoryOrNull() {
			if (codePath == null)
				return null;
			return getDirectoryOrNull(codePath.resolve(UserDirectoryManager.DIR_LOCALIZATION));
		}
		
		
	}

}
