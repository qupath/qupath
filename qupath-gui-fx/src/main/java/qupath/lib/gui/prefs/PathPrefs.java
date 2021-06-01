/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.prefs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.ProjectIO;

/**
 * Central storage of QuPath preferences.
 * <p>
 * Most of these are 'persistent', and stored in a platform-dependent way using 
 * Java's Preferences API.
 * 
 * @author Pete Bankhead
 *
 */
public class PathPrefs {
	
	/**
	 * Name for preference node
	 */
	private final static String NODE_NAME = "io.github.qupath/0.3";

	/**
	 * Previous preference node, in case these need to be restored.
	 * For now, this isn't supported.
	 */
	@SuppressWarnings("unused")
	private final static String PREVIOUS_NODE_NAME = "io.github.qupath.0.2.0";

	private static Logger logger = LoggerFactory.getLogger(PathPrefs.class);
	
	/**
	 * Flag used to trigger when properties should be reset to their default values.
	 */
	private static BooleanProperty resetProperty = new SimpleBooleanProperty(Boolean.FALSE);

	private static BooleanProperty useSystemMenubar = createPersistentPreference("useSystemMenubar", true);
	
	/**
	 * Property used to specify whether the system menubar should be used.
	 * This should be bound bidirectionally to the corresponding property of any menubars created.
	 * @return
	 */
	public static BooleanProperty useSystemMenubarProperty() {
		return useSystemMenubar;
	}
	
	/**
	 * Export preferences to a stream.  Note that this will only export preferences that have been set explicitly; 
	 * some preferences may be 'missing' because their defaults were never changed.  This behavior may change in the future.
	 * 
	 * @param stream
	 * @throws IOException
	 * @throws BackingStoreException
	 */
	public static void exportPreferences(OutputStream stream) throws IOException, BackingStoreException {
		getUserPreferences().exportNode(stream);
	}
	
	/**
	 * Import preferences from a stream.
	 * <p>
	 * Note that if the plan is to re-import preferences previously exported by {@link #exportPreferences(OutputStream)} 
	 * then it may be worthwhile to {@link #resetPreferences()} first to handle the fact that preferences may not have been 
	 * saved because their default values were unchanged.
	 * 
	 * @param stream
	 * @throws IOException
	 * @throws InvalidPreferencesFormatException
	 */
	public static void importPreferences(InputStream stream) throws IOException, InvalidPreferencesFormatException  {
		Preferences.importPreferences(stream);
	}
	

	private static StringProperty scriptsPath = createPersistentPreference("scriptsPath", (String)null); // Base directory containing scripts
	
	private static IntegerProperty numCommandThreads = createPersistentPreference("Requested number of threads", Runtime.getRuntime().availableProcessors());
	
	/**
	 * Property specifying the preferred number of threads QuPath should use for multithreaded commands.
	 * @return
	 */
	public static IntegerProperty numCommandThreadsProperty() {
		return numCommandThreads;
	}
	
	
	private static BooleanProperty showImageNameInTitle = createPersistentPreference("showImageNameInTitle", Boolean.TRUE);
	
	/**
	 * Property specifying if QuPath should show the image title in the main window title.
	 * For privacy reasons it may be desirable to turn this off in some cases.
	 * @return
	 */
	public static BooleanProperty showImageNameInTitleProperty() {
		return showImageNameInTitle;
	}

	
	private static BooleanProperty doAutoUpdateCheck = createPersistentPreference("doAutoUpdateCheck", Boolean.TRUE);
	
	/**
	 * Check for updates when launching QuPath, if possible.
	 * @return
	 */
	public static BooleanProperty doAutoUpdateCheckProperty() {
		return doAutoUpdateCheck;
	}

	
	private static BooleanProperty maskImageNames = createPersistentPreference("maskImageNames", Boolean.FALSE);
	
	/**
	 * Request that image names are hidden within the user interface.
	 * @return
	 */
	public static BooleanProperty maskImageNamesProperty() {
		return maskImageNames;
	}
	
	private static ObjectProperty<Locale> defaultLocaleFormat = createPersistentPreference("localeFormat", Locale.Category.FORMAT, Locale.US);
	private static ObjectProperty<Locale> defaultLocaleDisplay = createPersistentPreference("localeDisplay", Locale.Category.DISPLAY, Locale.US);

	/**
	 * Get a property for setting the default {@link Locale} for {@link Category#FORMAT}.
	 * Setting this property also results in the Locale being changed to match.
	 * @return an object property to control the display locale
	 */
	public static ObjectProperty<Locale> defaultLocaleFormatProperty() {
		return defaultLocaleFormat;
	}
	
	/**
	 * Get a property for setting the default {@link Locale} for {@link Category#DISPLAY}.
	 * Setting this property also results in the Locale being changed to match.
	 * @return an object property to control the display locale
	 */
	public static ObjectProperty<Locale> defaultLocaleDisplayProperty() {
		return defaultLocaleDisplay;
	}
		
	private static IntegerProperty maxMemoryMB;
	
	/**
	 * Attempt to load user JVM defaults - may fail if packager.jar (and any required native library) isn't found.
	 * @return
	 */
	public static boolean hasJavaPreferences() {
		try {
			Path path = getConfigPath();
			if (path == null)
				return false;
			return Files.exists(path) && Files.isWritable(path);
		} catch (Exception e) {
			logger.error("Error trying to find config file", e);
			return false;
		}
	}
	
	
	static Path getConfigPath() throws IOException, URISyntaxException {
		Path path = Paths.get(
				PathPrefs.class
				.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()).getParent();
		return searchForConfigFile(path);
	}
	
	
	private static Path searchForConfigFile(Path dir) throws IOException {
		String configRequest = System.getProperty("qupath.config");
		try (var stream = Files.list(dir)) {
			var paths = stream.filter(
					p -> {
						// Look for the .cfg file, filtering if we have a system property specified
						String name = p.getFileName().toString();
						if (configRequest != null && !configRequest.isBlank())
							return name.toLowerCase().contains(configRequest.toLowerCase());
						return name.endsWith(".cfg") && !name.endsWith("(console).cfg");
					})
					.sorted(Comparator.comparingInt(p -> p.getFileName().toString().length()))
					.collect(Collectors.toList());
			if (paths.isEmpty())
				return null;
			// Return the shortest valid path found
			return paths.get(0);
		}
	}
	
	
	/**
	 * Get property representing the maximum memory for the Java Virtual Machine, 
	 * applied after restarting the application.
	 * <p>
	 * Setting this will attempt to set -Xmx by writing to a .cfg file in the home launch directory.
	 * <p>
	 * If successful, -Xmx will be set to the value that is specified or 512M, whichever is larger.
	 * 
	 * @return
	 */
	public synchronized static IntegerProperty maxMemoryMBProperty() {
		if (maxMemoryMB == null) {
			maxMemoryMB = createPersistentPreference("maxMemoryMB", -1);
			long requestedMaxMemoryMB = maxMemoryMB.get();
			long currentMaxMemoryMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
			if (requestedMaxMemoryMB > 0 && requestedMaxMemoryMB != currentMaxMemoryMB) {
				logger.debug("Requested max memory ({} MB) does not match the current max ({} MB) - resetting preference to default value", 
						requestedMaxMemoryMB, currentMaxMemoryMB);
				maxMemoryMB.set(-1);
			}
			// Update Java preferences for restart
			maxMemoryMB.addListener((v, o, n) -> {
				try {
					if (n.intValue() <= 512) {
						logger.warn("Cannot set memory to {}, must be >= 512 MB", n);
						n = 512;
					}
					// Note: with jpackage 14, the following was used
//					String memory = "-Xmx" + n.intValue() + "M";
					// With jpackage 15+, this should work
					String memory = "java-options=-Xmx" + n.intValue() + "M";
					Path config = getConfigPath();
					if (!Files.exists(config)) {
						logger.error("Cannot find config file!");
						return;
					}
					logger.info("Reading config file {}", config);
					List<String> lines = Files.readAllLines(config);
					int jvmOptions = -1;
					int argOptions = -1;
					int lineXx = -1;
					int lineXmx = -1;
					int i = 0;
					for (String line : lines) {
					    if (line.startsWith("[JVMOptions]") || line.startsWith("[JavaOptions]"))
					        jvmOptions = i;
					    if (line.startsWith("[ArgOptions]"))
					        argOptions = i;
					    if (line.toLowerCase().contains("-xx:maxrampercentage"))
					    	lineXx = i;
					    if (line.toLowerCase().contains("-xmx"))
					        lineXmx = i;
					    i++;
					}
					if (lineXx >= 0)
						lines.set(lineXx, memory);
					else if (lineXmx >= 0)
					    lines.set(lineXmx, memory);
					else if (argOptions > jvmOptions && jvmOptions >= 0) {
					    lines.add(jvmOptions+1, memory);
					} else {
					    logger.error("Cannot find where to insert memory request to .cfg file!");
					    return;
					}
					logger.info("Setting JVM option to {}", memory);
					Files.copy(config, Paths.get(config.toString() + ".bkp"), StandardCopyOption.REPLACE_EXISTING);
					Files.write(config, lines);
					return;
				} catch (AccessDeniedException e) {
					logger.error("I'm not allowed to access the config file - see the QuPath installation instructions to set the memory manually", e);
				} catch (Exception e) {
					logger.error("Unable to set max memory: " + e.getLocalizedMessage(), e);
				}
			});
		}
		return maxMemoryMB;
	}
	
	/**
	 * Get the {@link Preferences} object for storing user preferences.
	 * @return
	 */
	public static Preferences getUserPreferences() {
		return getUserPreferences(NODE_NAME, true);
	}
	
	private static Preferences getUserPreferences(String nodeName, boolean createIfNotExists) {
		Preferences prefs = Preferences.userRoot();
		try {
			for (var name : nodeName.split("/")) {
				if (!createIfNotExists && !prefs.nodeExists(name))
					return null;
				prefs = prefs.node(name);
			}
			return prefs;
		} catch (BackingStoreException e) {
			logger.debug("Exception loading " + nodeName + " (" + e.getLocalizedMessage() + ")", e);
			return null;
		}
	}
	
	
	/**
	 * Save the preferences.
	 * @return
	 */
	public synchronized static boolean savePreferences() {
		try {
			Preferences prefs = getUserPreferences();
			prefs.flush();
			logger.debug("Preferences have been saved");
			return true;
		} catch (BackingStoreException e) {
			logger.error("Failed to save preferences", e);
			return false;
		}		
	}
	
	/**
	 * Reset the preferences to their defaults. This requires QuPath to be restarted.
	 */
	public synchronized static void resetPreferences() {
		try {
			Preferences prefs = getUserPreferences();
			prefs.removeNode();
			prefs.flush();
			resetProperty.set(!resetProperty.get());
			logger.info("Preferences have been reset");
		} catch (BackingStoreException e) {
			logger.error("Failed to reset preferences", e);
		}
	}
	
	
	
	private static IntegerProperty scrollSpeedProperty = createPersistentPreference("Scroll speed %", 100);
	
	/**
	 * Percentage to scale scroll speed for zooming etc.
	 * Helps customize the viewer according to more/less enthusiastic input devices.
	 * 
	 * @return
	 */
	public static IntegerProperty scrollSpeedProperty() {
		return scrollSpeedProperty;
	}
	
	
	/**
	 * Get scroll speed scaled as a proportion and forced to be in the range 0-1. For example, 100% becomes 1.
	 * 
	 * @return
	 */
	public static double getScaledScrollSpeed() {
		double speed = scrollSpeedProperty.get() / 100.0;
		if (!Double.isFinite(speed) || speed <= 0)
			return 1;
		return speed;
	}
	
	
	private static IntegerProperty navigationSpeedProperty = createPersistentPreference("Navigation speed %", 100);
	
	/**
	 * Percentage to scale navigation speed.
	 * 
	 * @return navigationSpeedProperty
	 */
	public static IntegerProperty navigationSpeedProperty() {
		return navigationSpeedProperty;
	}
	
	
	/**
	 * Get navigation speed scaled as a proportion and forced to be in the range 0-1. For example, 100% becomes 1.
	 * 
	 * @return speed
	 */
	public static double getScaledNavigationSpeed() {
		double speed = navigationSpeedProperty.get() / 100.0;
		if (!Double.isFinite(speed) || speed <= 0)
			return 1;
		return speed;
	}
	
	private static BooleanProperty navigationAccelerationProperty = createPersistentPreference("Navigation acceleration effects", true);
	
	/**
	 * Apply acceleration/deceleration effects when holding and releasing navigation key.
	 * 
	 * @return navigationAccelerationProperty
	 */
	public static BooleanProperty navigationAccelerationProperty() {
		return navigationAccelerationProperty;
	}
	
	
	/**
	 * Get whether to apply the navigation acceleration (& deceleration) effects or not.
	 * 
	 * @return
	 */
	public static boolean getNavigationAccelerationProperty() {
		return navigationAccelerationProperty.get();
	}
	
	
	private static BooleanProperty skipMissingCoresProperty = createPersistentPreference("Skip missing TMA cores", false);
	
	/**
	 * Skip ('jump over') missing cores when navigating through TMA grids.
	 * 
	 * @return skipMissingCoresProperty
	 */
	public static BooleanProperty skipMissingCoresProperty() {
		return skipMissingCoresProperty;
	}
	
	/**
	 * Return whether the viewer skips missing TMA cores when navigating TMA grids 
	 * with arrow keys.
	 * 
	 * @return
	 */
	public static boolean getSkipMissingCoresProperty() {
		return skipMissingCoresProperty.get();
	}
	
	
	
	private static boolean showAllRGBTransforms = true;
	
	/**
	 * Request that all available color transforms are shown for RGB images.
	 * @return
	 */
	public static boolean getShowAllRGBTransforms() {
		return showAllRGBTransforms;
	}
	
	/**
	 * Path to a directory containing scripts for quick-access through the user interface.
	 * @return
	 */
	public static StringProperty scriptsPathProperty() {
		return scriptsPath;
	}
	

	private static BooleanProperty useTileBrush = createPersistentPreference("useTileBrush", false);

	/**
	 * Request that the brush tool automatically uses any available tiles, rather than creating 'circles' as normal.
	 * @return
	 */
	public static BooleanProperty useTileBrushProperty() {
		return useTileBrush;
	}

	private static BooleanProperty selectionMode = createTransientPreference("selectionMode", false);

	/**
	 * Convert drawing tools to select objects, rather than creating new objects.
	 * @return
	 */
	public static BooleanProperty selectionModeProperty() {
		return selectionMode;
	}

	
	
	private static BooleanProperty clipROIsForHierarchy = createPersistentPreference("clipROIsForHierarchy", false);

	/**
	 * Request ROIs to be clipped and inserted as the right place in the hierarchy when drawing 
	 * (to prevent overlapping ROIs being created accidentally).
	 * @return
	 */
	public static BooleanProperty clipROIsForHierarchyProperty() {
		return clipROIsForHierarchy;
	}

	
	
	private static BooleanProperty showExperimentalOptions = createPersistentPreference("showExperimentalOptions", true);
	
	/**
	 * Flag to indicate that menu items marked 'experimental' should be shown to the user.
	 * 
	 * @return
	 */
	public static BooleanProperty showExperimentalOptionsProperty() {
		return showExperimentalOptions;
	}
	
	
	private static BooleanProperty showTMAOptions = createPersistentPreference("showTMAOptions", true);

	/**
	 * Flag to indicate that the TMA menu should be shown to the user - only relevant when working with Tissue Microarrays.
	 * 
	 * @return
	 */
	public static BooleanProperty showTMAOptionsProperty() {
		return showTMAOptions;
	}
	
	private static BooleanProperty showLegacyOptions = createPersistentPreference("showLegacyOptions", false);

	/**
	 * Flag to indicate that the legacy options should be shown to the user - normally not desirable.
	 * 
	 * @return
	 */
	public static BooleanProperty showLegacyOptionsProperty() {
		return showLegacyOptions;
	}
	
	
	private static BooleanProperty doCreateLogFilesProperty = createPersistentPreference("requestCreateLogFiles", true);

	/**
	 * Request a log file to be generated.  Requires the <code>userPathProperty()</code> to be set to a directory.
	 * 
	 * @return
	 */
	public static BooleanProperty doCreateLogFilesProperty() {
		return doCreateLogFilesProperty;
	}
		
	
	private static StringProperty userPath = createPersistentPreference("userPath", (String)null); // Base directory containing extensions

	/**
	 * A path where additional files may be stored, such as extensions and log files.
	 * @return
	 */
	public static StringProperty userPathProperty() {
		return userPath;
	}
	
	/**
	 * Get the user path where additional files may be stored. This depends upon {@link #userPathProperty()}.
	 * @return
	 */
	public static String getUserPath() {
		return userPath.get();
	}
	
	/**
	 * Get the path to where extensions should be stored. This depends upon {@link #userPathProperty()}.
	 * @return
	 */
	public static String getExtensionsPath() {
		String userPath = getUserPath();
		if (userPath == null)
			return null;
		return new File(userPath, "extensions").getAbsolutePath();
	}
	
	/**
	 * Get the path to where log files should be written. This depends upon {@link #userPathProperty()}.
	 * @return
	 */
	public static String getLoggingPath() {
		String userPath = getUserPath();
		if (userPath == null)
			return null;
		return new File(userPath, "logs").getAbsolutePath();
	}
	
	
	private static BooleanProperty runStartupScript = createPersistentPreference("runStartupScript", false);
	
	/**
	 * Specify whether a startup script should be run when launching QuPath, if available.
	 * <p>
	 * This is a script called "startup.groovy", located in the user directory. It can be used as a simple way to 
	 * perform customizations (e.g. install commands or tools).
	 * The default value for this property is 'false', to ensure the user must specifically request that the script is executed.
	 * @return
	 * @see PathPrefs#userPathProperty()
	 */
	public static BooleanProperty runStartupScriptProperty() {
		return runStartupScript;
	}
	
	
	private static int nRecentProjects = 5;
	private static ObservableList<URI> recentProjects = FXCollections.observableArrayList();
	
	static {
		// Try to load the recent projects
		for (int i = 0; i < nRecentProjects; i++) {
			String project = getUserPreferences().get("recentProject" + i, null);
			if (project == null || project.length() == 0)
				break;
			// Only allow project files
			if (!(project.toLowerCase().endsWith(ProjectIO.getProjectExtension()))) {
				continue;
			}
			try {
				recentProjects.add(GeneralTools.toURI(project));
			} catch (URISyntaxException e) {
				logger.warn("Unable to parse URI from " + project, e);
			}
		}
		// Add a listener to keep storing the preferences, as required
		recentProjects.addListener((Change<? extends URI> c) -> {
			int i = 0;
			for (URI project : recentProjects) {
				getUserPreferences().put("recentProject" + i, project.toString());
				i++;
			}
			while (i < nRecentProjects) {
				getUserPreferences().put("recentProject" + i, "");
				i++;
			}
		});
	}
	
	/**
	 * Get a list of the most recent projects that were opened.
	 * @return
	 */
	public static ObservableList<URI> getRecentProjectList() {
		return recentProjects;
	}
	
	
	private static BooleanProperty invertScrolling = createPersistentPreference("invertScrolling", !GeneralTools.isMac());
	
	/**
	 * Invert the scrolling direction of the mouse applied to the viewer.
	 * This can be helpful when the scrolling direction feels unnatural... perhaps because of how the 'natural' system preference is set.
	 * @return
	 */
	public static BooleanProperty invertScrollingProperty() {
		return invertScrolling;
	}
	
	
	private static BooleanProperty invertZSlider = createPersistentPreference("invertZSlider", false);
	
	/**
	 * Invert the z-slider for the viewer. This can help if the location of the zero position seems counterintuitive.
	 * @return
	 */
	public static BooleanProperty invertZSliderProperty() {
		return invertZSlider;
	}
	
	
	private static DoubleProperty gridStartX = createPersistentPreference("gridStartX", 0.0);

	private static DoubleProperty gridStartY = createPersistentPreference("gridStartY", 0.0);
	
	private static DoubleProperty gridSpacingX = createPersistentPreference("gridSpacingX", 250.0);

	private static DoubleProperty gridSpacingY = createPersistentPreference("gridSpacingY", 250.0);

	private static BooleanProperty gridScaleMicrons = createPersistentPreference("gridScaleMicrons", true);


	/**
	 * Starting x coordinate for any counting grid (usually 0). This depends upon {@link #gridScaleMicronsProperty()}.
	 * @return
	 */
	public static DoubleProperty gridStartXProperty() {
		return gridStartX;
	}
	
	/**
	 * Starting y coordinate for any counting grid (usually 0). This depends upon {@link #gridScaleMicronsProperty()}.
	 * @return
	 */
	public static DoubleProperty gridStartYProperty() {
		return gridStartY;
	}

	/**
	 * Horizontal spacing between lines for any counting grid. This depends upon {@link #gridScaleMicronsProperty()}.
	 * @return
	 */
	public static DoubleProperty gridSpacingXProperty() {
		return gridSpacingX;
	}

	/**
	 * Vertical spacing between lines for any counting grid. This depends upon {@link #gridScaleMicronsProperty()}.
	 * @return
	 */
	public static DoubleProperty gridSpacingYProperty() {
		return gridSpacingY;
	}

	/**
	 * Define counting grid optionally displayed on any viewer using microns rather than pixel coordinates.
	 * @return
	 */
	public static BooleanProperty gridScaleMicronsProperty() {
		return gridScaleMicrons;
	}

	
	
	private static DoubleProperty autoBrightnessContrastSaturation = PathPrefs.createPersistentPreference("autoBrightnessContrastSaturationPercentage", 0.1);

	/**
	 * Controls percentage of saturated pixels to apply when automatically setting brightness/contrast.
	 * <p>
	 * A value of 1 indicates that approximately 1% dark pixels and 1% bright pixels should be saturated.
	 * @return 
	 */
	public static DoubleProperty autoBrightnessContrastSaturationPercentProperty() {
		return autoBrightnessContrastSaturation;
	}
	
	
	
	private static BooleanProperty keepDisplaySettings = createPersistentPreference("keepDisplaySettings", true);
	
	/**
	 * Retain display settings (channel colors, brightness/contrast) when opening new images 
	 * that have the same properties (channels, channel names, bit-depths).
	 * @return
	 */
	public static BooleanProperty keepDisplaySettingsProperty() {
		return keepDisplaySettings;
	}
		
	
	
	private static BooleanProperty doubleClickToZoom = createPersistentPreference("doubleClickToZoom", false);
	
	/**
	 * Request that double-clicking the viewer can be used to zoom in.
	 * @return
	 */
	public static BooleanProperty doubleClickToZoomProperty() {
		return doubleClickToZoom;
	}
	
	
	/**
	 * Enum defining how setting the image type should be handled for new images.
	 */
	public static enum ImageTypeSetting {
		/**
		 * Automatically estimate the image type
		 */
		AUTO_ESTIMATE,
		/**
		 * Prompt the user to specified the image type
		 */
		PROMPT,
		/**
		 * Do not set the image type
		 */
		NONE;
		
		@Override
		public String toString() {
			switch(this) {
			case AUTO_ESTIMATE:
				return "Auto estimate";
			case NONE:
				return "Unset";
			case PROMPT:
				return "Prompt";
			default:
				return "Unknown";
			}
		}
		
	}
		
	private static ObjectProperty<ImageTypeSetting> imageTypeSettingProperty = createPersistentPreference("imageTypeSetting", ImageTypeSetting.PROMPT, ImageTypeSetting.class);
	
	/**
	 * Specify how setting the image type should be handled for images when they are opened for the first time.
	 * @return
	 * @see ImageTypeSetting
	 */
	public static ObjectProperty<ImageTypeSetting> imageTypeSettingProperty() {
		return imageTypeSettingProperty;
	}
	
	
	private static BooleanProperty paintSelectedBounds = createTransientPreference("paintSelectedBounds", false);

	/**
	 * Specify whether the bounding box of selected objects should be painted.
	 * This offers an alternative to showing selected objects based on color.
	 * @return
	 */
	public static BooleanProperty paintSelectedBoundsProperty() {
		return paintSelectedBounds;
	}
	
	
	private static StringProperty tableDelimiter = createPersistentPreference("tableDelimiter", "\t");

	/**
	 * Delimiter to use when exporting tables. Default is {@code "\t"}. Commas should be used with caution because of potential localization trouble.
	 * @return
	 */
	public static StringProperty tableDelimiterProperty() {
		return tableDelimiter;
	}
	
	
	private static BooleanProperty enableFreehandTools = createPersistentPreference("enableFreehandTools", true);
	
	
	/**
	 * Enable polygon/polyline tools to support 'freehand' mode; this means that if the ROI is 
	 * started by dragging, then it will end by lifting the mouse (rather than requiring a double-click).
	 * 
	 * @return
	 */
	public static BooleanProperty enableFreehandToolsProperty() {
		return enableFreehandTools;
	}
	
	
	/**
	 * File extension used for serialization of ImageData (without the dot)
	 * @return
	 */
	public static String getSerializationExtension() {
		return "qpdata";
	}

	
	
	private static BooleanProperty useZoomGestures = createPersistentPreference("Use zoom gestures", false);
	private static BooleanProperty useRotateGestures = createPersistentPreference("Use rotate gestures", false);
	private static BooleanProperty useScrollGestures = createPersistentPreference("Use scroll gestures", false);
	
	/**
	 * Support zoom gestures for touchscreens and trackpads.
	 * @return
	 */
	public static BooleanProperty useZoomGesturesProperty() {
		return useZoomGestures;
	}
	
	/**
	 * Support rotate gestures for touchscreens and trackpads.
	 * @return
	 */
	public static BooleanProperty useRotateGesturesProperty() {
		return useRotateGestures;
	}
	
	/**
	 * Support scroll gestures for touchscreens and trackpads.
	 * @return
	 */
	public static BooleanProperty useScrollGesturesProperty() {
		return useScrollGestures;
	}
		
	
	
	
	private static BooleanProperty brushCreateNewObjects = createPersistentPreference("brushCreateNew", true);
	private static BooleanProperty brushScaleByMag = createPersistentPreference("brushScaleByMag", true);
	private static IntegerProperty brushDiameter = createPersistentPreference("brushDiameter", 50);
	
	/**
	 * Create new objects by default when drawing with the Brush tool. The alternative is to append (discontinuous) regions to existing annotations.
	 * @return
	 */
	public static BooleanProperty brushCreateNewObjectsProperty() {
		return brushCreateNewObjects;
	}
	
	/**
	 * Optionally scale the default brush tool diameter by the viewer magnification (downsample value).
	 * @return
	 * @see #brushDiameterProperty()
	 */
	public static BooleanProperty brushScaleByMagProperty() {
		return brushScaleByMag;
	}
	
	/**
	 * Default brush tool diameter, in pixels.
	 * @return
	 * @see #brushScaleByMagProperty()
	 */
	public static IntegerProperty brushDiameterProperty() {
		return brushDiameter;
	}
	
	private static BooleanProperty returnToMoveMode = createPersistentPreference("returnToMoveMode", true); // Return to the pan tool after drawing a ROI

	
	/**
	 * Request that the GUI returns to using the PAN tool after a ROI is drawn.
	 * This helps keep errant clicking under control, but not permitting new ROIs to be made without
	 * explicitly activating a ROI too
	 * @return
	 */
	public static BooleanProperty returnToMoveModeProperty() {
		return returnToMoveMode;
	}
	
	
	private static DoubleProperty tileCachePercentage = createPersistentPreference("tileCachePercentage", 25.0);
	
	/**
	 * Requested percentage of available memory to use for tile caching.
	 * @return
	 */
	public static DoubleProperty tileCachePercentageProperty() {
		return tileCachePercentage;
	}
	
	
	private static BooleanProperty useCalibratedLocationString = createPersistentPreference("useCalibratedLocationString", true);
	
	/**
	 * Show the cursor location on a viewer in calibrated units, rather than pixels.
	 * @return
	 */
	public static BooleanProperty useCalibratedLocationStringProperty() {
		return useCalibratedLocationString;
	}
	
	
	private static BooleanProperty showPointHulls = createTransientPreference("showPointHulls", false);
	private static BooleanProperty useSelectedColor = createTransientPreference("useSelectedColor", true);
	
	/**
	 * Use a specified color for highlighting selected objects in the viewer.
	 * @return
	 * @see #colorSelectedObjectProperty()
	 */
	public static BooleanProperty useSelectedColorProperty() {
		return useSelectedColor;
	}

	/**
	 * Show the convex hull for point annotations within the viewer.
	 * @return
	 */
	public static BooleanProperty showPointHullsProperty() {
		return showPointHulls;
	}
		
	
	private static BooleanProperty multipointTool = createTransientPreference("multipointTool", true);
	
	/**
	 * Create multiple points within the same annotation when using the counting tool.
	 * The alternative is to create a new annotation for each new point.
	 * @return
	 */
	public static BooleanProperty multipointToolProperty() {
		return multipointTool;
	}

	
	private static DoubleProperty tmaExportDownsampleProperty = createPersistentPreference("tmaExportDownsample", 4.0);

	/**
	 * Default downsample factor to use when exporting TMA cores.
	 * @return
	 */
	public static DoubleProperty tmaExportDownsampleProperty() {
		return tmaExportDownsampleProperty;
	}

	
	
	private static DoubleProperty viewerGammaProperty = createPersistentPreference("viewerGammaProperty", 1.0);

	/**
	 * Requested gamma value applied to the image in each viewer (for display only).
	 * @return
	 */
	public static DoubleProperty viewerGammaProperty() {
		return viewerGammaProperty;
	}
	
	
	private static IntegerProperty viewerBackgroundColor = createPersistentPreference("viewerBackgroundColor", ColorTools.packRGB(0, 0, 0));
	
	/**
	 * Color to paint behind any image.
	 * @return
	 */
	public static IntegerProperty viewerBackgroundColorProperty() {
		return viewerBackgroundColor;
	}

	
	private static IntegerProperty colorDefaultObjects = createPersistentPreference("colorDefaultAnnotations", ColorTools.packRGB(255, 0, 0));
		
	private static IntegerProperty colorSelectedObject = createPersistentPreference("colorSelectedObject", ColorTools.packRGB(255, 255, 0));
	private static IntegerProperty colorTMA = createPersistentPreference("colorTMA", ColorTools.packRGB(20, 20, 180));
	private static IntegerProperty colorTMAMissing = createPersistentPreference("colorTMAMissing", ColorTools.packARGB(50, 20, 20, 180));
	private static IntegerProperty colorTile = createPersistentPreference("colorTile", ColorTools.packRGB(80, 80, 80));
	
	/**
	 * The default color used to display objects of any type, where a default has not otherwise been specified.
	 * @return
	 */
	public static IntegerProperty colorDefaultObjectsProperty() {
		return colorDefaultObjects;
	}
	
	/**
	 * The default color used to display selected objects.
	 * @return
	 * @see #useSelectedColorProperty()
	 */
	public static IntegerProperty colorSelectedObjectProperty() {
		return colorSelectedObject;
	}
	
	/**
	 * The default color used to display TMA core objects.
	 * @return
	 */
	public static IntegerProperty colorTMAProperty() {
		return colorTMA;
	}
	
	/**
	 * The default color used to display missing TMA core objects.
	 * @return
	 */
	public static IntegerProperty colorTMAMissingProperty() {
		return colorTMAMissing;
	}
	
	/**
	 * The default color used to display tile objects.
	 * @return
	 */
	public static IntegerProperty colorTileProperty() {
		return colorTile;
	}
	
	
	private static ObjectProperty<PathClass> autoSetAnnotationClass = createTransientPreference("autoSetAnnotationClass", (PathClass)null);
	
	/**
	 * Classification that should automatically be applied to all new annotations. May be null.
	 * @return
	 */
	public static ObjectProperty<PathClass> autoSetAnnotationClassProperty() {
		return autoSetAnnotationClass;
	}
	
	
	private static BooleanProperty alwaysPaintSelectedObjects = createPersistentPreference("alwaysPaintSelectedObjects", true);
	
	/**
	 * Always paint selected objects in the viewer, even if the opacity setting is 0.
	 * @return 
	 */
	public static BooleanProperty alwaysPaintSelectedObjectsProperty() {
		return alwaysPaintSelectedObjects;
	}
	
	private static BooleanProperty viewerInterpolateBilinear = createPersistentPreference("viewerInterpolateBilinear", false);
	
	/**
	 * Request that images are displayed in viewers using bilinear interpolation.
	 * @return
	 */
	public static BooleanProperty viewerInterpolateBilinearProperty() {
		return viewerInterpolateBilinear;
	}
	
	
	/**
	 * Enum to control font size.
	 */
	@SuppressWarnings("javadoc")
	public static enum FontSize {
		TINY, SMALL, MEDIUM, LARGE, HUGE;
		
		/**
		 * Get the font size as a CSS-compatible string.
		 * @return
		 */
		public String getFontSize() {
			switch(this) {
			case HUGE:
				return "1.4em";
			case LARGE:
				return "1.2em";
			case MEDIUM:
				return "1.0em";
			case SMALL:
				return "0.8em";
			case TINY:
				return "0.6em";
			default:
				return "1em";
			}
		}
		
		@Override
		public String toString() {
			switch(this) {
			case HUGE:
				return "Huge";
			case LARGE:
				return "Large";
			case MEDIUM:
				return "Medium";
			case SMALL:
				return "Small";
			case TINY:
				return "Tiny";
			default:
				return "Unknown";
			}
		}
	}
	
	private static ObjectProperty<FontSize> fontSize = PathPrefs.createPersistentPreference(
			"locationFontSize", FontSize.MEDIUM, FontSize.class);
	
	/**
	 * Preferred font size in the viewer.
	 * @return
	 */
	public static ObjectProperty<FontSize> viewerFontSizeProperty() {
		return fontSize;
	}
	
	
	private static DoubleProperty allredMinPercentagePositive = createPersistentPreference("allredMinPercentagePositive", 0.0);
	
	/**
	 * The minimum positive percentage of cells for Allred proportion score to be non-zero.
	 * 
	 * Using the strict definition, this would be 0... however for image analysis this can be very non-robust,
	 * in that it allows a single false detection to have a very high influence on the score.
	 * 
	 * @return
	 */
	public static DoubleProperty allredMinPercentagePositiveProperty() {
		return allredMinPercentagePositive;
	}


	private static IntegerProperty minPyramidDimension = createPersistentPreference("minPyramidDimension", 4096);
	
	/**
	 * Minimum image width or height before pyramidalizing (if required).
	 * @return
	 */
	public static IntegerProperty minPyramidDimensionProperty() {
		return minPyramidDimension;
	}
	
	private static IntegerProperty pointRadiusProperty = createPersistentPreference("defaultPointRadius", 5);

	/**
	 * Radius of the circle used to draw individual points in a point annotation (in pixels). 
	 * @return
	 */
	public static IntegerProperty pointRadiusProperty() {
		return pointRadiusProperty;
	}

	
	
	
	/**
	 * Create a persistent property, which is one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static BooleanProperty createPersistentPreference(final String name, final boolean defaultValue) {
		BooleanProperty property = createTransientPreference(name, defaultValue);
		property.set(getUserPreferences().getBoolean(name, defaultValue));
		property.addListener((v, o, n) -> getUserPreferences().putBoolean(name, n));
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	/**
	 * Create a transient property, which is one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static BooleanProperty createTransientPreference(final String name, final boolean defaultValue) {
		return new SimpleBooleanProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, which is one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static IntegerProperty createPersistentPreference(final String name, final int defaultValue) {
		IntegerProperty property = createTransientPreference(name, defaultValue);
		property.set(getUserPreferences().getInt(name, defaultValue));
		property.addListener((v, o, n) -> getUserPreferences().putInt(name, n.intValue()));
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	/**
	 * Create a transient property, which is one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static IntegerProperty createTransientPreference(final String name, final int defaultValue) {
		return new SimpleIntegerProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, which is one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static DoubleProperty createPersistentPreference(final String name, final double defaultValue) {
		DoubleProperty property = createTransientPreference(name, defaultValue);
		property.set(getUserPreferences().getDouble(name, defaultValue));
		property.addListener((v, o, n) -> getUserPreferences().putDouble(name, n.doubleValue()));
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	/**
	 * Create a transient property, which is one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static DoubleProperty createTransientPreference(final String name, final double defaultValue) {
		return new SimpleDoubleProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, which is one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static StringProperty createPersistentPreference(final String name, final String defaultValue) {
		StringProperty property = createTransientPreference(name, defaultValue);
		property.set(getUserPreferences().get(name, defaultValue));
		property.addListener((v, o, n) -> {
			if (n == null)
				getUserPreferences().remove(name);
			else
				getUserPreferences().put(name, n);
		});
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	/**
	 * Create a persistent property, which is one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @param enumType 
	 * @return
	 */
	public static <T extends Enum<T>> ObjectProperty<T> createPersistentPreference(final String name, final T defaultValue, final Class<T> enumType) {
		ObjectProperty<T> property = createTransientPreference(name, defaultValue);
		try {
			property.set(
					Enum.valueOf(enumType, getUserPreferences().get(name, defaultValue.name()))
					);
		} catch (Throwable e) {
			logger.warn("Exception setting preference value for " + name, e);
			property.set(defaultValue);
		}
		property.addListener((v, o, n) -> {
			if (n == null)
				getUserPreferences().remove(name);
			else
				getUserPreferences().put(name, n.name());
		});
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	
	/**
	 * Create a preference for storing Locales.
	 * 
	 * This provides a more persistent way of setting the Locale than doing so directly.
	 * 
	 * @param name
	 * @param category
	 * @param defaultValue
	 * @return
	 */
	private static ObjectProperty<Locale> createPersistentPreference(final String name, final Category category, final Locale defaultValue) {
		ObjectProperty<Locale> property = new SimpleObjectProperty<>(defaultValue);
		logger.debug("Default Locale {} set to: {}", category, defaultValue);
		// Try to read a set value for the preference
		// Locale.US is (I think) the only one we're guaranteed to have - so use it to get the displayed name
		String currentValue = getUserPreferences().get(name, defaultValue.getDisplayName(Locale.US));
		if (currentValue != null) {
			boolean localeFound = false;
			for (Locale locale : Locale.getAvailableLocales()) {
				if (currentValue.equals(locale.getDisplayName(Locale.US))) {
//					System.err.println("Default for " + category + " is set to: " + currentValue);
					Locale.setDefault(category, locale);
					property.set(locale);
					logger.debug("Locale {} set to {}", category, locale);
					localeFound = true;
					break;
				}
			}
			if (!localeFound)
				logger.info("Could not find Locale {} for {} - value remains ", currentValue, category, Locale.getDefault(category));
		}
		property.addListener((v, o, n) -> {
			try {
				logger.debug("Setting Locale {} to: {}", category, n);
				if (n == null) {
					getUserPreferences().remove(name);
					Locale.setDefault(category, defaultValue);
				} else {
					getUserPreferences().put(name, n.getDisplayName(Locale.US));
					Locale.setDefault(category, n);
				}
			} catch (Exception e) {
				logger.error("Unable to set Locale for {} to {}", category, n);
			}
		});
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	
	/**
	 * Create a transient property, which is one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static StringProperty createTransientPreference(final String name, final String defaultValue) {
		return new SimpleStringProperty(null, name, defaultValue);
	}
	
	
	/**
	 * Create a transient property, which is one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static <T> ObjectProperty<T> createTransientPreference(final String name, final T defaultValue) {
		return new SimpleObjectProperty<>(null, name, defaultValue);
	}



	/**
	 * Simple class to represent a positive float property
	 */
	static class PositiveFloatThicknessProperty extends SimpleFloatProperty  {
		PositiveFloatThicknessProperty(final String name, final float val) {
			super(null, name, val);
		}
		
		@Override
		public void set(float thickness) {
			if (thickness > 0f)
	    		super.set(thickness);
	       	else
	       		logger.warn("Attempted to set stroke thickness to invalid value ({}) - will be ignored", thickness);
		}
	};
	
	
	private static FloatProperty createPersistentThicknessPreference(final String name, final float defaultValue) {
		FloatProperty property = new PositiveFloatThicknessProperty(name, defaultValue);
		property.set(getUserPreferences().getFloat(name, defaultValue));
		property.addListener((v, o, n) -> getUserPreferences().putFloat(name, n.floatValue()));
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	private static FloatProperty strokeThinThickness = createPersistentThicknessPreference("thinLineThickness", 2f);
	
	private static FloatProperty strokeThickThickness = createPersistentThicknessPreference("thickLineThickness", 2f);
	
	/**
	 * Preferred stroke thickness to use when drawing detections ROIs.
	 * This is defined in pixels at the full image resolution, and does not adapt to viewer magnification.
	 * @return
	 */
	public static FloatProperty detectionStrokeThicknessProperty() {
    	return strokeThinThickness;
    }
    
	/**
	 * Preferred stroke thickness to use when drawing annotation ROIs.
	 * This is defined in pixels, scaled according to the current viewer magnification.
	 * @return
	 */
    public static FloatProperty annotationStrokeThicknessProperty() {
    	return strokeThickThickness;
    }
    
    private static final BooleanProperty usePixelSnapping = createPersistentPreference("usePixelSnapping", true);
    
    /**
	 * If true, pixels should be snapped to integer coordinates when using the drawing tools.
	 * 
	 * @return
	 */
	public static BooleanProperty usePixelSnappingProperty() {
		return usePixelSnapping;
	}

}
