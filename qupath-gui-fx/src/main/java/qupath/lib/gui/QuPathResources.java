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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.prefs.PathPrefs;

/**
 * Load strings from the default resource bundle.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class QuPathResources {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathResources.class);
	
	private static final QuPathResourceControl CONTROL = new QuPathResourceControl();
	
	private static final String DEFAULT_BUNDLE = "qupath/lib/gui/localization/qupath-strings";
	
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
	
	
	private static ResourceBundle getBundleOrNull(String bundleName) {
		if (bundleName == null || bundleName.isEmpty())
			bundleName = DEFAULT_BUNDLE;
		try {
			return ResourceBundle.getBundle(bundleName, Locale.getDefault(Category.DISPLAY), ExtensionClassLoader.getInstance(), CONTROL);
		} catch (MissingResourceException e) {
			logger.error("Missing resource bundle {}", bundleName);
			return null;
		}
	}
	
	
	
	private static class QuPathResourceControl extends ResourceBundle.Control {
		
		private final static Logger logger = LoggerFactory.getLogger(QuPathResourceControl.class);
		
		@Override
		public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) 
				throws IllegalAccessException, InstantiationException, IOException {
			
			ResourceBundle bundle = super.newBundle(baseName, locale, format, loader, reload);
			if (bundle != null)
				return bundle;
			
			String bundleName = toBundleName(baseName, locale);
			logger.debug("Searching for {}", bundleName);
			var userPath = PathPrefs.getUserPath();
			if (userPath != null) {
				try {
					int ind = bundleName.replace('.', '/').lastIndexOf("/");
					String name = ind < 0 ? bundleName : bundleName.substring(ind+1);
					var path = Paths.get(userPath, "localization", name + ".properties");
					if (Files.isRegularFile(path)) {
						try (var reader = Files.newBufferedReader(path)) {
							logger.debug("Reading bundle from {}", path);
							return new PropertyResourceBundle(reader);
						}
					}
				} catch (Exception e) {
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
			
			return null;
		}
		
	}

}
