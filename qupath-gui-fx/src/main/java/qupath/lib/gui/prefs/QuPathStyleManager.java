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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.tools.GuiTools;


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
	
	/**
	 * Main stylesheet, used to define new colors for QuPath.
	 * This should always be applied, since it defines colors that are required for scripting.
	 */
	private static final String STYLESHEET_MAIN = "css/main.css";

	/**
	 * Default dark stylesheet.
	 */
	private static final String STYLESHEET_DARK = "css/dark.css";

	/**
	 * Default JavaFX stylesheet
	 */
	private static final StyleOption DEFAULT_STYLE = new JavaFXStylesheet("Modena Light", Application.STYLESHEET_MODENA);

	/**
	 * Default QuPath stylesheet used for 'dark mode'
	 */
	private static final StyleOption DEFAULT_DARK_STYLE = new CustomStylesheet("Modena Dark", "Darker version of JavaFX Modena stylesheet", STYLESHEET_DARK);

	// Maintain a record of what stylesheets we've added, so we can try to clean up later if needed
	private static final List<String> previouslyAddedStyleSheets = new ArrayList<>();

	private static final ObservableList<StyleOption> styles = FXCollections.observableArrayList(
			DEFAULT_STYLE,
			DEFAULT_DARK_STYLE
			);
	
	private static final ObservableList<StyleOption> stylesUnmodifiable = FXCollections.unmodifiableObservableList(styles);
	
	private static ObjectProperty<StyleOption> selectedStyle;

	/**
	 * Find the first available {@link StyleOption} with the specified name.
	 * @param name
	 * @return
	 */
	private static StyleOption findByName(String name) {
		return styles.stream().filter(s -> Objects.equals(s.getName(), name)).findFirst().orElse(DEFAULT_STYLE);
	}
	
	/**
	 * Watch for custom styles, which the user may add, remove or modify.
	 */
	private static CssStylesWatcher watcher;
	
	/**
	 * Available font families.
	 */
	public static enum Fonts {
		/**
		 * JavaFX default. May not look great on macOS, which lacks support for bold font weight by default.
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

	private static ObservableList<Fonts> availableFonts = 
			FXCollections.unmodifiableObservableList(
					FXCollections.observableArrayList(Fonts.values()));

	private static ObjectProperty<Fonts> selectedFont = PathPrefs.createPersistentPreference("selectedFont", 
			GeneralTools.isMac() ? Fonts.SANS_SERIF : Fonts.DEFAULT, Fonts.class);

	static {
		
		/**
		 * Add custom user styles, if available.
		 * We need to do this before setting the default (since the last used style might be one of these).
		 */
		updateAvailableStyles();
		selectedStyle = PathPrefs.createPersistentPreference("qupathStylesheet", DEFAULT_STYLE, s -> s.getName(), QuPathStyleManager::findByName);
		
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
	 * Request that the list of available styles is updated.
	 * It makes sense to call this when a new user directory has been set, so that a check for CSS files 
	 * can be performed.
	 */
	public static void updateAvailableStyles() {
		
		// Make sure we're still watching the correct directory for custom styles
		var cssPathString = PathPrefs.getCssStylesPath();
		if (cssPathString != null) {
			// Create a new watcher if needed, or else check the CSS path is still correct
			var cssPath = Paths.get(cssPathString);
			if (watcher == null) {
				try {
					watcher = new CssStylesWatcher(cssPath);
					watcher.styles.addListener((Change<? extends StyleOption> c) -> {
						updateAvailableStyles();
					});
				} catch (Exception e) {
					logger.warn("Exception searching for css files: " + e.getLocalizedMessage(), e);
				}
			} else if (!Objects.equals(watcher.cssPath, cssPath)) {
				watcher.setCssPath(cssPath);
			}
		}
		
		
		// Cache the current selection, since it could become lost during the update
		var previouslySelected = selectedStyle == null ? null : selectedStyle.get();
		
		// Update all available styles
		if (watcher == null || watcher.styles.isEmpty())
			styles.setAll(DEFAULT_STYLE, DEFAULT_DARK_STYLE);
		else {
			var temp = new ArrayList<StyleOption>();
			temp.add(DEFAULT_STYLE);
			temp.add(DEFAULT_DARK_STYLE);
			temp.addAll(watcher.styles);
//			temp.sort(Comparator.comparing(StyleOption::getName));
			styles.setAll(temp);
		}
		
		// Reinstate the selection, or use the default if necessary
		if (selectedStyle != null) {
			if (previouslySelected == null || !styles.contains(previouslySelected))
				selectedStyle.set(DEFAULT_STYLE);
			else
				selectedStyle.set(previouslySelected);
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
	 * Get the current available styles as an observable list.
	 * The list is unmodifiable, since any changes should be made via {@link PathPrefs#getCssStylesPath()}.
	 * @return
	 */
	public static ObservableList<StyleOption> availableStylesProperty() {
		return stylesUnmodifiable;
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
	 * The list is unmodifiable, since this is primarily used to overcome issues with the default font on macOS 
	 * by changing the font family. More fine-grained changes can be made via css.
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
	
	
	/**
	 * Default JavaFX stylesheet.
	 */
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
			addStyleSheets(STYLESHEET_MAIN);
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

		@Override
		public int hashCode() {
			return Objects.hash(cssName, name);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			JavaFXStylesheet other = (JavaFXStylesheet) obj;
			return Objects.equals(cssName, other.cssName) && Objects.equals(name, other.name);
		}
		
	}
	
	
	/**
	 * Custom stylesheets, requiring one or more URLs (added on top of the default).
	 */
	static class CustomStylesheet implements StyleOption {
		
		private String name;
		private String description;
		private String[] urls;
		
		CustomStylesheet(final String name, final String description, final String... urls) {
			this.name = name;
			this.description = description;
			this.urls = urls.clone();
		}
		
		CustomStylesheet(final Path path) {
			this(GeneralTools.getNameWithoutExtension(path.toFile()), path.toString(), path.toUri().toString());
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
		
		/**
		 * Check if a specified url is used as part of this stylesheet.
		 * @param url
		 * @return
		 */
		private boolean containsUrl(String url) {
			for (var css: urls) {
				if (Objects.equals(url, css))
					return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(urls);
			result = prime * result + Objects.hash(description, name);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CustomStylesheet other = (CustomStylesheet) obj;
			return Objects.equals(description, other.description) && Objects.equals(name, other.name)
					&& Arrays.equals(urls, other.urls);
		}
		
	}
	
	private static void setStyleSheets(String... urls) {
		Application.setUserAgentStylesheet(null);
//		// Check if we need to do anything
//		var toAdd = Arrays.asList(urls);
//		if (previouslyAddedStyleSheets.equals(toAdd))
//			return;
		// Replace previous stylesheets with the new ones
		removePreviousStyleSheets();
		
		addStyleSheets(STYLESHEET_MAIN);
		
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
	
	
	/**
	 * Class to run a background thread that picks up changes to a directory containing CSS files, 
	 * and updates the current or available styles as required.
	 */
	private static class CssStylesWatcher implements Runnable {
		
		private static final ThreadFactory THREAD_FACTORY = ThreadTools.createThreadFactory("css-watcher", true);
		
		private Thread thread;
		
		private Path cssPath;
		private WatchService watcher;
		
		private ObservableList<StyleOption> styles = FXCollections.observableArrayList();
		
		private CssStylesWatcher(Path cssPath) {
			thread = THREAD_FACTORY.newThread(this);
			try {
				watcher = FileSystems.getDefault().newWatchService();
				logger.debug("Watching for changes in {}", cssPath);
			} catch (IOException e) {
				logger.error("Exception setting up CSS watcher: " + e.getLocalizedMessage(), e);
			}
			setCssPath(cssPath);
			thread.start();
		}
		
		private void setCssPath(Path cssPath) {
			if (Objects.equals(this.cssPath, cssPath))
				return;
			this.cssPath = cssPath;
			if (Files.isDirectory(cssPath)) {
				try {
					cssPath.register(watcher,
							StandardWatchEventKinds.ENTRY_MODIFY,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE);
					logger.debug("Watching for changes in {}", cssPath);
				} catch (IOException e) {
					logger.error("Exception setting up CSS watcher: " + e.getLocalizedMessage(), e);
				}
			}
			refreshStylesheets();
		}
		
		
		@Override
		public void run() {
			while (watcher != null) {
				
				WatchKey key = null;
				synchronized(this) {
					try {
						key = watcher.take();
						if (key == null)
							continue;
					} catch (InterruptedException e) {
						return;
					}
				}
				
				for (WatchEvent<?> ev: key.pollEvents()) {
					if (ev.kind() == StandardWatchEventKinds.OVERFLOW)
						continue;

					// Get the path to whatever changed
					var event = (WatchEvent<Path>)ev;
					var basePath = (Path)key.watchable();
					if (!Objects.equals(cssPath, basePath))
						continue;
					
					var path = basePath.resolve(event.context());
					
					// An existing stylesheet has changed
					if (ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY && Files.isRegularFile(path)) {
						try {
							var currentStyle = selectedStyle.get();
							if (currentStyle instanceof CustomStylesheet) {
								var currentCustomStyle = ((CustomStylesheet)currentStyle);
								var url = path.toUri().toString();
								if (currentCustomStyle.containsUrl(url)) {
									logger.info("Refreshing style {}", currentStyle.getName());
									refresh();
								}
								break;
							}
						} catch (Exception e) {
							logger.warn("Exception processing CSS refresh: " + e.getLocalizedMessage(), e);
						}
					} else {
						// For everything else, refresh the available stylesheets
						refreshStylesheets();
					}
					
				}

				boolean valid = key.reset();
				if (!valid) {
					break;
				}
			}
		}
		
		
		private void refreshStylesheets() {
			try {
				if (Files.isDirectory(cssPath)) {
					var newStyles = Files.list(cssPath)
						.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".css"))
						.map(path -> new CustomStylesheet(path))
						.sorted(Comparator.comparing(StyleOption::getName))
						.collect(Collectors.toList());
					GuiTools.runOnApplicationThread(() -> styles.setAll(newStyles));
					return;
				}
			} catch (IOException e) {
				logger.warn(e.getLocalizedMessage(), e);
			}
			GuiTools.runOnApplicationThread(() -> styles.clear());
		}
				
		
		
	}
	
	
}