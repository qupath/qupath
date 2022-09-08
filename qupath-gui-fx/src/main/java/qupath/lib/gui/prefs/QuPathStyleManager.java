/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.prefs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import qupath.lib.common.GeneralTools;


/**
 * Class to facilitate the use of different styles within QuPath.
 * <p>
 * These register themselves with {@link PathPrefs} so that they can be persistent across restarts.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathStyleManager {
	
	private static Logger logger = LoggerFactory.getLogger(QuPathStyleManager.class);
	
	private static JavaFXStylesheet DEFAULT_STYLE = new JavaFXStylesheet("Modena Light", Application.STYLESHEET_MODENA);

	// Maintain a record of what we've added, so we can try to clean up later if needed
	private static List<String> previouslyAddedStyleSheets = new ArrayList<>();

	private static ObservableList<StyleOption> styles = FXCollections.observableArrayList(
			DEFAULT_STYLE,
			new CustomStylesheet("Modena Dark", "Darker version of JavaFX Modena stylesheet", "css/dark.css")
			);
	
	private static ObjectProperty<StyleOption> selectedStyle = PathPrefs.createPersistentPreference("qupathStylesheet", DEFAULT_STYLE, s -> s.getName(), QuPathStyleManager::findByName);

	
	private static StyleOption findByName(String name) {
		return styles.stream().filter(s -> Objects.equals(s.getName(), name)).findFirst().orElse(null);
	}
	
	/**
	 * Available font families.
	 */
	public static enum Fonts {
		/**
		 * JavaFX default. May not look great on macOS.
		 */
		DEFAULT,
		/**
		 * Preferred sans-serif font.
		 */
		SANS_SERIF,
		/**
		 * Preferred serif font.
		 */
		SERIF;
		
		private String getURL() {
			switch(this) {
			case SANS_SERIF:
				return "css/sans-serif.css";
			case SERIF:
				return "css/serif.css";
			case DEFAULT:
			default:
				return null;
			}
		}
		
		@Override
		public String toString() {
			switch(this) {
			case SANS_SERIF:
				return "Sans-serif";
			case SERIF:
				return "Serif";
			case DEFAULT:
			default:
				return "Default";
			}
		}
	}

	private static ObservableList<Fonts> availableFonts = FXCollections.observableArrayList(Fonts.values());

	private static ObjectProperty<Fonts> selectedFont = PathPrefs.createPersistentPreference("selectedFont", 
			GeneralTools.isMac() ? Fonts.SANS_SERIF : Fonts.DEFAULT, Fonts.class);

	static {
		// Add listener to adjust style as required
		selectedStyle.addListener((v, o, n) -> updateStyle());
		selectedFont.addListener((v, o, n) -> updateStyle());
	}
	
	private static void updateStyle() {
		// Support calling updateStyle from different threads
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(QuPathStyleManager::updateStyle);
			return;
		}
		StyleOption n = selectedStyle.get();
		if (n != null) {
			n.setStyle();
		} else {
			Application.setUserAgentStylesheet(null);
		}
		// Set the font if required
		Fonts font = selectedFont.get();
		if (font != null) {
			String url = font.getURL();
			if (url != null)
				addStyleSheets(url);
		}
	}
	
	/**
	 * Refresh the current style.
	 * This should not normally be required, but may be useful during startup to ensure 
	 * that the style is properly set at the appropriate time.
	 */
	public static void refresh() {
		updateStyle();
	}
	
	/**
	 * Check if the default JavaFX style is used.
	 * @return true if the default style is used, false otherwise.
	 */
	public static boolean isDefaultStyle() {
		return DEFAULT_STYLE.equals(selectedStyle.get());
	}
	
	/**
	 * Get the current available styles.
	 * @return
	 */
	public static ObservableList<StyleOption> availableStylesProperty() {
		return styles;
	}
	
	/**
	 * Get the current selected style.
	 * @return
	 */
	public static ObjectProperty<StyleOption> selectedStyleProperty() {
		return selectedStyle;
	}
	
	/**
	 * Get a list of available fonts.
	 * @return list of available fonts
	 */
	public static ObservableList<Fonts> availableFontsProperty() {
		return availableFonts;
	}
	
	/**
	 * Get the current selected font.
	 * @return
	 */
	public static ObjectProperty<Fonts> fontProperty() {
		return selectedFont;
	}

	/**
	 * Interface defining a style that may be applied to QuPath.
	 */
	public static interface StyleOption {
		
		/**
		 * Set the style for the QuPath application.
		 */
		public void setStyle();
		
		/**
		 * Get a user-friendly description of the style.
		 * @return
		 */
		public String getDescription();
		
		/**
		 * Get a user-friendly name for the style.
		 * @return
		 */
		public String getName();
		
	}
	
	
	static class JavaFXStylesheet implements StyleOption {
		
		private String name;
		private String cssName;
		
		JavaFXStylesheet(final String name, final String cssName) {
			this.name = name;
			this.cssName = cssName;
		}

		@Override
		public void setStyle() {
			Application.setUserAgentStylesheet(cssName);
			removePreviousStyleSheets(cssName);
		}

		@Override
		public String getDescription() {
			return "Built-in JavaFX stylesheet " + cssName;
		}

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	
	static class CustomStylesheet implements StyleOption {
		
		private String name;
		private String description;
		private String[] urls;
		
		CustomStylesheet(final String name, final String description, final String... urls) {
			this.name = name;
			this.description = description;
			this.urls = urls.clone();
		}

		@Override
		public void setStyle() {
			setStyleSheets(urls);
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	private static void setStyleSheets(String... urls) {
		Application.setUserAgentStylesheet(null);
		// Check if we need to do anything
		var toAdd = Arrays.asList(urls);
		if (previouslyAddedStyleSheets.equals(toAdd))
			return;
		// Replace previous stylesheets with the new ones
		removePreviousStyleSheets();
		addStyleSheets(urls);
	}
		
	private static void removePreviousStyleSheets(String... urls) {
		if (previouslyAddedStyleSheets.isEmpty())
			return;
		try {
			Class<?> cStyleManager = Class.forName("com.sun.javafx.css.StyleManager");
			Object styleManager = cStyleManager.getMethod("getInstance").invoke(null);
			Method m = styleManager.getClass().getMethod("removeUserAgentStylesheet", String.class);
			var iterator = previouslyAddedStyleSheets.iterator();
			while (iterator.hasNext()) {
				var url = iterator.next();
				iterator.remove();
				m.invoke(styleManager, url);
				logger.debug("Stylesheet removed {}", url);
			}
//			System.err.println("After removal: " + previouslyAddedStyleSheets);
		} catch (Exception e) {
			logger.error("Unable to call removeUserAgentStylesheet", e);
		}
	}
	
	private static void addStyleSheets(String... urls) {
		try {
			Class<?> cStyleManager = Class.forName("com.sun.javafx.css.StyleManager");
			Object styleManager = cStyleManager.getMethod("getInstance").invoke(null);
			Method m = styleManager.getClass().getMethod("addUserAgentStylesheet", String.class);
			for (String url : urls) {
				if (previouslyAddedStyleSheets.contains(url))
					continue;
				m.invoke(styleManager, url);
				previouslyAddedStyleSheets.add(url);
				logger.debug("Stylesheet added {}", url);
			}
//			System.err.println("After adding: " + previouslyAddedStyleSheets);
		} catch (Exception e) {
			logger.error("Unable to call addUserAgentStylesheet", e);
		}
	}
	
}