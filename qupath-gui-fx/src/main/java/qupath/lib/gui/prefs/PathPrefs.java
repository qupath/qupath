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

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Locale.Category;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

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
import qupath.lib.gui.helpers.CommandFinderTools.CommandBarDisplay;
import qupath.lib.projects.ProjectIO;

/**
 * Central storage of QuPath preferences.
 * 
 * Most of these are 'persistent', and stored in a platform-dependent way using 
 * Java's Preferences API.
 * 
 * @author Pete Bankhead
 *
 */
public class PathPrefs {
	
	final private static String NODE_NAME = "io.github.qupath";
	
	private static Logger logger = LoggerFactory.getLogger(PathPrefs.class);
	
	/**
	 * Flag used to trigger when properties should be reset to their default values.
	 */
	private static BooleanProperty resetProperty = new SimpleBooleanProperty(Boolean.FALSE);

	public static BooleanProperty useProjectImageCache = createPersistentPreference("useProjectImageCache", Boolean.FALSE);
	
	/**
	 * If true, then a 'cache' directory will be created within projects, and ImageServers that retrieve images in a lengthy way
	 * (e.g. via HTTP requests) are allowed/encouraged to store tiles there - to enable persistent caching.
	 * 
	 * Currently, this preference triggers the directory cache to be set within <code>URLHelpers</code>.
	 * 
	 * @return
	 * 
	 * @see qupath.lib.www.URLHelpers
	 */
	public static BooleanProperty useProjectImageCacheProperty() {
		return useProjectImageCache;
	}

	public static boolean useProjectImageCache() {
		return useProjectImageCache.get();
	}

	public static void setUseProjectImageCache(final boolean useCache) {
		useProjectImageCache.set(useCache);
	}

	private static StringProperty scriptsPath = createPersistentPreference("scriptsPath", (String)null); // Base directory containing scripts

	// Known whole slide image extensions
	private static String[] knownImageExtensions = new String[]{
		"tif",
		"tiff",
		"jpeg",
		"jpg", 
		"svs",
		"ndpi",
		"scn",
		"mrxs",
		"vms",
		"vmu",
		"svslide",
		"bif",
		"zvi",
		"lif"
	};
	
	
	
	
	
	private static IntegerProperty numCommandThreads = createPersistentPreference("Requested number of threads", Runtime.getRuntime().availableProcessors());
	
	public static Integer getNumCommandThreads() {
		return numCommandThreads.get();
	}
	
	public static void setNumCommandThreads(int n) {
		numCommandThreads.set(n);
	}
	
	public static IntegerProperty numCommandThreadsProperty() {
		return numCommandThreads;
	}
	
	
	
	private static BooleanProperty doAutoUpdateCheck = createPersistentPreference("doAutoUpdateCheck", Boolean.TRUE);
	
	public static BooleanProperty doAutoUpdateCheckProperty() {
		return doAutoUpdateCheck;
	}

	public static boolean doAutoUpdateCheck() {
		return doAutoUpdateCheck.get();
	}
	
	public static void setDoAutoUpdateCheck(final boolean doCheck) {
		doAutoUpdateCheck.set(doCheck);
	}

	
	
	
	private static ObjectProperty<Locale> defaultLocaleFormat = createPersistentPreference("localeFormat", Locale.Category.FORMAT, Locale.US);
	private static ObjectProperty<Locale> defaultLocaleDisplay = createPersistentPreference("localeDisplay", Locale.Category.DISPLAY, Locale.US);

//	private static ObjectProperty<Locale> defaultLocaleFormat = createPersistentPreference("localeFormat", Locale.Category.FORMAT, Locale.getDefault(Category.FORMAT));
//	private static ObjectProperty<Locale> defaultLocaleDisplay = createPersistentPreference("localeDisplay", Locale.Category.DISPLAY, Locale.getDefault(Category.DISPLAY));

	
	/**
	 * Get a property for setting the default Locale for a specified Category.
	 * 
	 * Setting this property also results in the Locale being changed to match.
	 * 
	 * @param category
	 * @return
	 */
	public static ObjectProperty<Locale> defaultLocaleProperty(final Category category) {
		if (category == Category.FORMAT)
			return defaultLocaleFormat;
		if (category == Category.DISPLAY)
			return defaultLocaleDisplay;
		return null;
	}
	
	public static Locale getDefaultLocale(final Category category) {
		return defaultLocaleProperty(category).get();
	}

	public static void setDefaultLocale(final Category category, final Locale locale) {
		defaultLocaleProperty(category).set(locale);
	}


	// This was a bit of a false lead in the attempt to set JVM options... 
//	private static Preferences getJavaPreferences() {
//		if (System.getProperty("app.preferences.id") == null)
//			return null;
////		return Preferences.userRoot().node(System.getProperty("app.preferences.id").replace(".", "/")).node("JVMUserOptions");
//		return Preferences.userRoot().node(System.getProperty("app.preferences.id")).node("JVMUserOptions");
//	}
	
	private static IntegerProperty maxMemoryMB;
	
	/**
	 * Attempt to load user JVM defaults - may fail if packager.jar (and any required native library) isn't found.
	 * 
	 * @return
	 */
	public static boolean hasJavaPreferences() {
		try {
			Class<?> clsJVM = Class.forName("jdk.packager.services.UserJvmOptionsService");
			Method methodGetDefaults = clsJVM.getMethod("getUserJVMDefaults");
			Object options = methodGetDefaults.invoke(null);
			return options != null;
		} catch (Throwable t) {
			logger.trace("Unable to load user JVM preferences", t);
			return false;
		}
	}
	
	/**
	 * Get property representing the maximum memory for the Java Virtual Machine, 
	 * applied after restarting the application.
	 * 
	 * Setting this will attempt to set -Xmx by means of UserJvmOptionsService.
	 * 
	 * If successful, any value &lt;= 0 will result in the -Xmx option being removed
	 * (i.e. reverting to the default).  Otherwise, -Xmx will be set to the value that is 
	 * specified or 100M, whichever is larger.
	 * 
	 * @return
	 */
	public static IntegerProperty maxMemoryMBProperty() {
		if (maxMemoryMB == null) {
			maxMemoryMB = createPersistentPreference("maxMemoryMB", -1);
			// Update Java preferences for restart
			maxMemoryMB.addListener((v, o, n) -> {
				try {
					// Use reflection to ease setup - packager.jar may well not be on classpath during development
					Class<?> clsJVM = Class.forName("jdk.packager.services.UserJvmOptionsService");
					Method methodGetDefaults = clsJVM.getMethod("getUserJVMDefaults");
					Method methodSetOptions = clsJVM.getMethod("setUserJVMOptions", Map.class);
					Object options = methodGetDefaults.invoke(null);
					if (n == null || n.intValue() <= 0) {
						logger.info("Resetting JVM options");
						methodSetOptions.invoke(options, Collections.emptyMap());
					} else {
						long val = Math.max(n.longValue(), 100);
						logger.info("Setting JVM option -Xmx" + val + "M");
						methodSetOptions.invoke(options, Collections.singletonMap("-Xmx", val + "M"));
					}
					return;
				} catch (Exception e) {
					logger.error("Unable to set max memory", e);
				}
			});
		}
		return maxMemoryMB;
	}
	
	
	public static Preferences getUserPreferences() {
		Preferences prefs = Preferences.userRoot();
		prefs = prefs.node(NODE_NAME);
		return prefs;
	}
	
	
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
	
	public static int getScrollSpeed() {
		return scrollSpeedProperty.get();
	}
	
	public static void setScrollSpeed(int speed) {
		scrollSpeedProperty.set(speed);
	}
	
	/**
	 * Percentage to scale scroll speed for zooming etc.
	 * 
	 * Helps customize the viewer according to mouse/less enthusiastic input devices.
	 * 
	 * @return
	 */
	public static IntegerProperty scrollSpeedProperty() {
		return scrollSpeedProperty;
	}
	
	
	/**
	 * Get scroll speed scaled into a proportion, i.e. 100% becomes 1.
	 * 
	 * This also enforces a range check to ensure if is finite and &gt; 0.
	 * 
	 * @return
	 */
	public static double getScaledScrollSpeed() {
		double speed = getScrollSpeed() / 100.0;
		if (!Double.isFinite(speed) || speed <= 0)
			return 1;
		return speed;
	}
	
	
	
	private static boolean showAllRGBTransforms = true;
	
	public static boolean getShowAllRGBTransforms() {
		return showAllRGBTransforms;
	}
	
	public static StringProperty scriptsPathProperty() {
		return scriptsPath;
	}
	
	public static String getScriptsPath() {
		return scriptsPath.get();
	}

	public static void setScriptsPath(final String path) {
		scriptsPath.set(path);
	}


	private static BooleanProperty useTileBrush = createPersistentPreference("useTileBrush", false);

	public static BooleanProperty useTileBrushProperty() {
		return useTileBrush;
	}

	public static void setUseTileBrush(final boolean useTiles) {
		useTileBrush.set(useTiles);
	}
	
	public static boolean getUseTileBrush() {
		return useTileBrush.get();
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
	
	
	
	private static ObjectProperty<CommandBarDisplay> commandBarDisplay;
	
	public static ObjectProperty<CommandBarDisplay> commandBarDisplayProperty() {
		if (commandBarDisplay == null) {
			String name = PathPrefs.getUserPreferences().get("commandFinderDisplayMode", CommandBarDisplay.NEVER.name());
			CommandBarDisplay display = CommandBarDisplay.valueOf(name);
			if (display == null)
				display = CommandBarDisplay.HOVER;
			commandBarDisplay = new SimpleObjectProperty<>(display);
			commandBarDisplay.addListener((v, o, n) -> {
				PathPrefs.getUserPreferences().put("commandFinderDisplayMode", n.name());
			});			
		}
		return commandBarDisplay;
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

	
	public static StringProperty userPathProperty() {
		return userPath;
	}
	
	public static String getUserPath() {
		return userPath.get();
	}
	
	public static String getExtensionsPath() {
		String userPath = getUserPath();
		if (userPath == null)
			return null;
		return new File(userPath, "extensions").getAbsolutePath();
	}
	
	public static String getLoggingPath() {
		String userPath = getUserPath();
		if (userPath == null)
			return null;
		return new File(userPath, "logs").getAbsolutePath();
	}

	public static void setUserPath(final String path) {
		userPath.set(path);
	}
	
	
	
	private static int nRecentProjects = 5;
	private static ObservableList<File> recentProjects = FXCollections.observableArrayList();
	
	static {
		// Try to load the recent projects
		for (int i = 0; i < nRecentProjects; i++) {
			String project = getUserPreferences().get("recentProject" + i, null);
			if (project == null || project.length() == 0)
				break;
			// Only allow project files
			if (!(project.toLowerCase().endsWith(ProjectIO.getProjectExtension()) && new File(project).isFile())) {
				continue;
			}
			recentProjects.add(new File(project));
		}
		// Add a listener to keep storing the preferences, as required
		recentProjects.addListener((Change<? extends File> c) -> {
			int i = 0;
			for (File project : recentProjects) {
				getUserPreferences().put("recentProject" + i, project.getAbsolutePath());
				i++;
			}
			while (i < nRecentProjects) {
				getUserPreferences().put("recentProject" + i, "");
				i++;
			}
		});
	}
	
	public static ObservableList<File> getRecentProjectList() {
		return recentProjects;
	}
	
	
	
	/**
	 * If true, all RGB color transforms are available (e.g. color deconvolution); if false, only the original RGB image may be viewed.
	 * The advantage of setting this to false is that, if only a viewer is needed, it will load faster this way.
	 * @param showTransforms
	 */
	public static void setShowAllRGBTransforms(boolean showTransforms) {
		showAllRGBTransforms = showTransforms;
	}

	
	/**
	 * Minimum dimension before an image is considered a whole slide image.  May be used to adjust processing accordingly.
	 * @return
	 */
	public static int getMinWholeSlideDimension() {
		return 5000;
	}
	
	
	
	public static String getDefaultScreenshotExtension() {
		return "png";
	}
	
	
	
	private static BooleanProperty invertScrolling = createPersistentPreference("invertScrolling", !GeneralTools.isMac());
	
	public static BooleanProperty invertScrollingProperty() {
		return invertScrolling;
	}
	
	public static boolean getInvertScrolling() {
		return invertScrolling.get();
	}
	
	public static void setInvertScrolling(boolean request) {
		invertScrolling.set(request);
	}
	
	
	
	private static BooleanProperty doubleClickToZoom = createPersistentPreference("doubleClickToZoom", false);
	
	public static BooleanProperty doubleClickToZoomProperty() {
		return doubleClickToZoom;
	}
	
	public static boolean getDoubleClickToZoom() {
		return doubleClickToZoom.get();
	}
	
	public static void setDoubleClickToZoom(boolean doDoubleClick) {
		doubleClickToZoom.set(doDoubleClick);
	}
	
	
	private static BooleanProperty autoEstimateImageType = createPersistentPreference("autoEstimateImageType", true);
	
	public static BooleanProperty autoEstimateImageTypeProperty() {
		return autoEstimateImageType;
	}
	
	public static boolean getAutoEstimateImageType() {
		return autoEstimateImageType.get();
	}
	
	public static void setAutoEstimateImageType(boolean autoSet) {
		autoEstimateImageType.set(autoSet);
	}

	
	
	/**
	 * Request that dragging to navigate an image continues to move the image until it comes to a standstill ever after the dragging operation has stopped.
	 * @return
	 */
	public static boolean requestDynamicDragging() {
		return true;
	}
	
	
	private static BooleanProperty paintSelectedBounds = createTransientPreference("paintSelectedBounds", false);

	public static BooleanProperty paintSelectedBoundsProperty() {
		return paintSelectedBounds;
	}
	
	public static boolean getPaintSelectedBounds() {
		return paintSelectedBounds.get();
	}

	public static void setPaintSelectedBounds(final boolean doPaintBounds) {
		paintSelectedBounds.set(doPaintBounds);
	}
	
	
	private static StringProperty tableDelimiter = createPersistentPreference("tableDelimiter", "\t");

	public static StringProperty tableDelimiterProperty() {
		return tableDelimiter;
	}

	public static String getTableDelimiter() {
		return tableDelimiter.get();
	}
	
	public static void setTableDelimiter(final String delimiter) {
		tableDelimiter.set(delimiter);
	}
	
	
	
	
	
	private static BooleanProperty trackCursorPosition = createPersistentPreference("trackCursorPosition", true);
	
	public static boolean getTrackCursorPosition() {
		return trackCursorPosition.get();
	}
	
	public static void setTrackCursorPosition(boolean track) {
		trackCursorPosition.set(track);
	}
	
	public static BooleanProperty trackCursorPositionProperty() {
		return trackCursorPosition;
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
	
	public static BooleanProperty useZoomGesturesProperty() {
		return useZoomGestures;
	}
	
	public static boolean getUseZoomGestures() {
		return useZoomGestures.get();
	}
	
	public static void setUseZoomGestures(boolean useZoom) {
		useZoomGestures.set(useZoom);
	}
	
	public static BooleanProperty useRotateGesturesProperty() {
		return useRotateGestures;
	}
	
	public static boolean getUseRotateGestures() {
		return useRotateGestures.get();
	}
	
	public static void setUseRotateGestures(boolean useRotate) {
		useRotateGestures.set(useRotate);
	}
	
	
	public static BooleanProperty useScrollGesturesProperty() {
		return useScrollGestures;
	}
	
	public static boolean getUseScrollGestures() {
		return useScrollGestures.get();
	}
	
	public static void setUseScrollGestures(boolean useScroll) {
		useScrollGestures.set(useScroll);
	}
	
	
	
	
	private static BooleanProperty brushCreateNewObjects = createPersistentPreference("brushCreateNew", true);
	private static BooleanProperty brushScaleByMag = createPersistentPreference("brushScaleByMag", true);
	private static IntegerProperty brushDiameter = createPersistentPreference("brushDiameter", 50);
	
	public static BooleanProperty brushCreateNewObjectsProperty() {
		return brushCreateNewObjects;
	}
	
	public static BooleanProperty brushScaleByMagProperty() {
		return brushScaleByMag;
	}
	
	public static IntegerProperty brushDiameterProperty() {
		return brushDiameter;
	}
	
	public static boolean getBrushCreateNewObjects() {
		return brushCreateNewObjects.get();
	}
	
	public static void setBrushCreateNewObjects(boolean create) {
		brushCreateNewObjects.set(create);
	}
	
	public static double getBrushDiameter() {
		return getUserPreferences().getDouble("brushDiameter", 50);
	}
	
	public static boolean getBrushScaleByMag() {
		return brushScaleByMag.get();
	}

	public static void setBrushScaleByMag(final boolean doScale) {
		brushScaleByMag.set(doScale);
	}

	public static void setBrushDiameter(final int diameter) {
		brushDiameter.set(diameter);
	}
	
	
	
	
	
	private static BooleanProperty returnToMoveMode = createPersistentPreference("returnToMoveMode", true); // Return to the pan tool after drawing a ROI

	
	/**
	 * Request that the GUI returns to using the PAN tool after a ROI is drawn.
	 * This helps keep errant clicking under control, but not permitting new ROIs to be made without
	 * explicitly activating a ROI too
	 * @return
	 */
	public static boolean getReturnToMoveMode() {
		return returnToMoveMode.get();
	}
	
	public static void setReturnToMoveMode(boolean returnToMove) {
		returnToMoveMode.set(returnToMove);
	}
	
	public static BooleanProperty returnToMoveModeProperty() {
		return returnToMoveMode;
	}
	
	
	
	/**
	 * An image with width &amp; height &lt; maxNonWholeTiledImageLength() should not be tiled -
	 * rather, the entirely image should always be read &amp; used
	 * @return
	 */
	public static int maxNonWholeTiledImageLength() {
		return 5000;
	}
	
	
	public static String[] getKnownImageExtensions() {
		return knownImageExtensions.clone();
	}
	
	
	/**
	 * Returns true if a filename ends with a known image file extension
	 * @param fileName
	 * @return
	 */
	public static boolean hasKnownImageFileExtension(String fileName) {
		for (String extKnown : knownImageExtensions) {
			if (fileName.endsWith(extKnown))
				return true;
		}
		return false;
	}

	
//	// Number of tiles to keep in cache when displaying image
//	public static int getTileCacheSize() {
//		// Could compute a nice value...
////		Runtime.getRuntime().maxMemory()
//		return 800;
//	}
	
	
	// Number of tiles to keep in cache when displaying image
		public static long getTileCacheSizeBytes() {
			// Try to compute a sensible value...
			Runtime rt = Runtime.getRuntime();
			long maxAvailable = rt.maxMemory(); // Max available memory
			long minRequired = 500L * 1024L * 1024L; // We need at least 500 MB for tolerable performance
			long memForCores = 250L * 1024L * 1024L * rt.availableProcessors(); // Want to leave ideally at least 250 MB per processor core
			long maxSensible = Math.min(maxAvailable/4, 1024L * 1024L * 1024L * 4L); // Shouldn't need more than 4 GB or 25% available memory... better free up memory for processing / objects
			long memoryEstimate = maxAvailable - memForCores;
			
//			long val = Math.min(Math.max(Math.min(minRequired, maxAvailable/2), memoryEstimate), maxSensible);
			long val = Math.min(Math.max(minRequired, memoryEstimate), maxSensible);
			logger.info(String.format("Tile cache size: %.2f MB", val/(1024.*1024.)));
			return val;
		}
	
	
	public static boolean showTMAToolTips() {
		return true;
	}

	private static BooleanProperty useCalibratedLocationString = createPersistentPreference("useCalibratedLocationString", true);
	
	public static BooleanProperty useCalibratedLocationStringProperty() {
		return useCalibratedLocationString;
	}
	
	
	private static BooleanProperty showPointHulls = createTransientPreference("showPointHulls", false);
	private static BooleanProperty useSelectedColor = createTransientPreference("useSelectedColor", true);
	
	public static BooleanProperty useSelectedColorProperty() {
		return useSelectedColor;
	}

	public static boolean getUseSelectedColor() {
		return useSelectedColor.get();
	}

	public static void setUseSelectedColor(boolean useSelected) {
		useSelectedColor.set(useSelected);
	}
	
	public static BooleanProperty showPointHullsProperty() {
		return showPointHulls;
	}
	
	public static boolean getShowPointHulls() {
		return showPointHulls.get();
	}

	public static void setShowPointHulls(boolean showHulls) {
		showPointHulls.set(showHulls);
	}
	
	
	private static DoubleProperty tmaExportDownsampleProperty = createPersistentPreference("tmaExportDownsample", 4.0);

	public static void setTMAExportDownsample(final double downsample) {
		tmaExportDownsampleProperty.set(downsample);
	}

	public static DoubleProperty tmaExportDownsampleProperty() {
		return tmaExportDownsampleProperty;
	}

	public static double getTMAExportDownsample() {
		return tmaExportDownsampleProperty.get();
	}
	
	
	
	private static DoubleProperty viewerGammaProperty = createPersistentPreference("viewerGammaProperty", 1.0);

	public static void setViewerGamma(final double gamma) {
		viewerGammaProperty.set(gamma);
	}

	/**
	 * Requested gamma value applied to the image in each viewer (for display only).
	 * @return
	 */
	public static DoubleProperty viewerGammaProperty() {
		return viewerGammaProperty;
	}

	public static double getViewerGamma() {
		return viewerGammaProperty.get();
	}
	

	
	/**
	 * Default pixel size preferred when exporting TMA cores - downsample may be adjusted accordingly.
	 * @return
	 */
	public static double getPreferredTMAExportPixelSizeMicrons() {
		return 1;
	}
	
	
//	/**
//	 * Directory used to cache images downloaded directly from a server, in the original format.
//	 * Most likely, this means storing a JPEG byte stream (not decompressing/recompressing) so that it
//	 * may be read locally next time, rather than requested once again.
//	 * @return
//	 */
//	public static File getImageCacheDirectory() {
//		return dirCache;
//	}
	
	
	private static IntegerProperty colorDefaultAnnotations = createPersistentPreference("colorDefaultAnnotations", ColorTools.makeRGB(255, 0, 0));
	
	private static String extPathClassifier = ".qpclassifier";
	
	private static IntegerProperty colorSelectedObject = createPersistentPreference("colorSelectedObject", ColorTools.makeRGB(255, 255, 0));
	private static IntegerProperty colorTMA = createPersistentPreference("colorTMA", ColorTools.makeRGB(20, 20, 180));
	private static IntegerProperty colorTMAMissing = createPersistentPreference("colorTMAMissing", ColorTools.makeRGBA(20, 20, 180, 50));
	private static IntegerProperty colorTile = createPersistentPreference("colorTile", ColorTools.makeRGB(80, 80, 80));
	
	public static Integer getTMACoreColor() {
		return colorTMA.get();
	}
	
	public static void setTMACoreColor(int color) {
		colorTMA.set(color);
	}

	public static Integer getTileColor() {
		return colorTile.get();
	}
	
	public static Integer getTMACoreMissingColor() {
		return colorTMAMissing.get();
	}
	
	public static void setTMACoreMissingColor(int color) {
		colorTMAMissing.set(color);
	}

	public static Integer getTMAGridColor() {
		return colorTMA.get();
	}
	
	public static IntegerProperty colorDefaultAnnotationsProperty() {
		return colorDefaultAnnotations;
	}

	public static Integer getColorDefaultAnnotations() {
		return colorDefaultAnnotations.get();
	}
	
	public static void setColorDefaultAnnotations(int color) {
		colorDefaultAnnotations.set(color);
	}

	public static void setSelectedObjectColor(int color) {
		colorSelectedObject.set(color);
	}

	public static IntegerProperty colorSelectedObjectProperty() {
		return colorSelectedObject;
	}
	
	public static IntegerProperty colorTMAProperty() {
		return colorTMA;
	}
	
	public static IntegerProperty colorTMAMissingProperty() {
		return colorTMAMissing;
	}
	
	public static IntegerProperty colorTileProperty() {
		return colorTile;
	}
	
	
	
	
	
	
	private static BooleanProperty autoSetAnnotationClass = createTransientPreference("autoSetAnnotationClass", false); // Request that newly-created annotations be automatically classified
	
	public static boolean getAutoSetAnnotationClass() {
		return autoSetAnnotationClass.get();
	}

	public static void setAutoSetAnnotationClass(boolean autoSet) {
		autoSetAnnotationClass.set(autoSet);
	}
	
	public static BooleanProperty autoSetAnnotationClassProperty() {
		return autoSetAnnotationClass;
	}
	
	
	/**
	 * Get the color that should be used to draw selected objects.
	 * This method may return null, in which case the object's own color should be used.
	 * @return
	 */
	public static Integer getSelectedObjectColor() {
		return colorSelectedObject.get();
	}

	public static String getClassifierExtension() {
		return extPathClassifier;
	}
	
//	public static double preferredPixelSizeMicronsForAnalysis() {
//		return preferredAnalysisPixelSizeMicrons;
//	}

	
	private static BooleanProperty autoCloseCommandList = createPersistentPreference("autoCloseCommandList", true); // Return to the pan tool after drawing a ROI
	
	public static void setAutoCloseCommandList(boolean autoClose) {
		autoCloseCommandList.set(autoClose);
	}
	
	public static BooleanProperty autoCloseCommandListProperty() {
		return autoCloseCommandList;
	}
	
	public static boolean getAutoCloseCommandList() {
		return autoCloseCommandList.get();
	}
	
	

 private static BooleanProperty viewerInterpolateBilinear = createPersistentPreference("viewerInterpolateBilinear", false);
	
	public static void setViewerInterpolationBilinear(boolean doBilinear) {
		viewerInterpolateBilinear.set(doBilinear);
	}
	
	public static BooleanProperty viewerInterpolateBilinearProperty() {
		return viewerInterpolateBilinear;
	}
	
	public static boolean getViewerInterpolationBilinear() {
		return viewerInterpolateBilinear.get();
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
	
	public static double getAllredMinPercentagePositive() {
		return allredMinPercentagePositive.get();
	}
	
	public static void setAllredMinPercentagePositive(final double percentage) {
		allredMinPercentagePositive.set(percentage);
	}
	
	
	
	protected static List<String> wholeSlideExtensions = Arrays.asList(new String[]{
			".svs", ".tif", ".tiff", ".vms", ".ndpi", ".scn", ".czi", ".zvi"}); //, ".mrxs"
		
	public static List<String> getWholeSlideExtensions() {
		return Collections.unmodifiableList(wholeSlideExtensions);
	}



	private static IntegerProperty defaultPointRadius = createPersistentPreference("defaultPointRadius", 5);

	public static int getDefaultPointRadius() {
		return defaultPointRadius.get();
	}

	public static void setDefaultPointRadius(final int radius) {
		defaultPointRadius.set(radius);
	}
	
	public static IntegerProperty defaultPointRadiusProperty() {
		return defaultPointRadius;
	}

	
	
	
	/**
	 * Create a persistent property, i.e. one that will be saved to/reloaded from the user preferences.
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
	 * Create a transient property, i.e. one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static BooleanProperty createTransientPreference(final String name, final boolean defaultValue) {
		return new SimpleBooleanProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, i.e. one that will be saved to/reloaded from the user preferences.
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
	 * Create a transient property, i.e. one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static IntegerProperty createTransientPreference(final String name, final int defaultValue) {
		return new SimpleIntegerProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, i.e. one that will be saved to/reloaded from the user preferences.
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
	 * Create a transient property, i.e. one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static DoubleProperty createTransientPreference(final String name, final double defaultValue) {
		return new SimpleDoubleProperty(null, name, defaultValue);
	}
	
	
	
	/**
	 * Create a persistent property, i.e. one that will be saved to/reloaded from the user preferences.
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
	 * Create a persistent property, i.e. one that will be saved to/reloaded from the user preferences.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static <T extends Enum<T>> ObjectProperty<T> createPersistentPreference(final String name, final T defaultValue, final Class<T> enumType) {
		ObjectProperty<T> property = createTransientPreference(name, defaultValue);
		property.set(
				Enum.valueOf(enumType, getUserPreferences().get(name, defaultValue.name()))
				);
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
	 * This provides a more persistnt way of setting the Locale than doing so directly.
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
					logger.info("Locale {} set to {}", category, locale);
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
	 * Create a transient property, i.e. one that won't be saved in the user preferences later.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	static StringProperty createTransientPreference(final String name, final String defaultValue) {
		return new SimpleStringProperty(null, name, defaultValue);
	}
	
	
	/**
	 * Create a transient property, i.e. one that won't be saved in the user preferences later.
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
		
		public void set(float thickness) {
			if (thickness > 0f)
	    		super.set(thickness);
	       	else
	       		logger.warn("Attempted to set stroke thickness to invalid value ({}) - will be ignored", thickness);
		}
	};
	
	public static FloatProperty createPersistentThicknessPreference(final String name, final float defaultValue) {
		FloatProperty property = new PositiveFloatThicknessProperty(name, defaultValue);
		property.set(getUserPreferences().getFloat(name, defaultValue));
		property.addListener((v, o, n) -> getUserPreferences().putFloat(name, n.floatValue()));
		// Triggered when reset is called
		resetProperty.addListener((c, o, v) -> property.setValue(defaultValue));
		return property;
	}
	
	private static FloatProperty strokeThinThickness = createPersistentThicknessPreference("thinLineThickness", 2f);
	
	private static FloatProperty strokeThickThickness = createPersistentThicknessPreference("thickLineThickness", 2f);
	
	public static FloatProperty strokeThinThicknessProperty() {
    	return strokeThinThickness;
    }
    
    public static FloatProperty strokeThickThicknessProperty() {
    	return strokeThickThickness;
    }
    
    public static void setThinStrokeThickness(float thickness) {
   		strokeThinThickness.set(thickness);
    }

    public static float getThinStrokeThickness() {
    	return strokeThinThickness.get();
    }
    
    public static void setThickStrokeThickness(float thickness) {
    	strokeThinThickness.set(thickness);
    }

    public static float getThickStrokeThickness() {
    	return strokeThickThickness.get();
    }	
	
	
	/*
	 * Default_Hematoxylin
	 * Default_DAB
	 * Default_Eosin
	 * 
	 * Membrane thickness
	 * 
	 * Data_directory
	 */

}
