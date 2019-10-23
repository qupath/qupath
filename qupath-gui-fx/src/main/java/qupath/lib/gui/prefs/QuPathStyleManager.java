/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.prefs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;


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

	private static ObservableList<StylesheetOption> styles = FXCollections.observableArrayList(
			DEFAULT_STYLE,
//			new JavaFXStylesheet("Caspian", Application.STYLESHEET_CASPIAN),
			new CustomStylesheet("Modena Dark", "Darker version of JavaFX Modena stylesheet (slightly experimental)", "css/dark.css")
			);
	
	private static ObjectProperty<StylesheetOption> selectedStyle = new SimpleObjectProperty<>();
	
	static {
		// Add listener to adjust style as required
		selectedStyle.addListener((v, o, n) -> {
			if (n != null) {
				PathPrefs.getUserPreferences().put("qupathStylesheet", n.getName());
				n.setStylesheet();
			} else {
				// Default
				PathPrefs.getUserPreferences().remove("qupathStylesheet");
				Application.setUserAgentStylesheet(null);
			}
		});
		
		// Try to load preference
		Platform.runLater(() -> {
			String stylesheetName = PathPrefs.getUserPreferences().get("qupathStylesheet", null);
			if (stylesheetName != null) {
				for (StylesheetOption option : styles) {
					if (stylesheetName.equals(option.getName())) {
						selectedStyle.set(option);
					}
				}
			} else
				selectedStyle.set(DEFAULT_STYLE);
		});
	}
	
	public static boolean isDefaultStyle() {
		return DEFAULT_STYLE.equals(selectedStyle.get());
	}
	
	public static ObservableList<StylesheetOption> stylesProperty() {
		return styles;
	}

	public static ObjectProperty<StylesheetOption> selectedStyleProperty() {
		return selectedStyle;
	}

	
	public static interface StylesheetOption {
		
		public void setStylesheet();
		
		public String getDescription();
		
		public String getName();
		
	}
	
	
	static class JavaFXStylesheet implements StylesheetOption {
		
		private String name;
		private String cssName;
		
		JavaFXStylesheet(final String name, final String cssName) {
			this.name = name;
			this.cssName = cssName;
		}

		@Override
		public void setStylesheet() {
			Application.setUserAgentStylesheet(cssName);
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
	
	
	static class CustomStylesheet implements StylesheetOption {
		
		private String name;
		private String description;
		private String url;
		
		CustomStylesheet(final String name, final String description, final String url) {
			this.name = name;
			this.description = description;
			this.url = url;
		}

		@Override
		public void setStylesheet() {
			Application.setUserAgentStylesheet(null);
			// TODO: Check if a public alternative to StyleManager ever becomes available...
			// Unfortunately, for now we resort to using reflection
			try {
				Class<?> cStyleManager = Class.forName("com.sun.javafx.css.StyleManager");
				Object styleManager = cStyleManager.getMethod("getInstance").invoke(null);
				styleManager.getClass().getMethod("addUserAgentStylesheet", String.class).invoke(styleManager, url);
			} catch (Exception e) {
				logger.error("Unable to call addUserAgentStylesheet", e);
			}
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
	
}