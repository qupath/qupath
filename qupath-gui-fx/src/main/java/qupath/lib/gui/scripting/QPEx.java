/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.ThreadTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.display.settings.DisplaySettingUtils;
import qupath.lib.display.settings.ImageDisplaySettings;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.TaskRunnerFX;
import qupath.lib.gui.UserDirectoryManager;
import qupath.lib.gui.charts.Charts;
import qupath.lib.gui.measure.ui.SummaryMeasurementTable;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.gui.tools.MenuTools;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.UriUpdater;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.CommandLineTaskRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.TaskRunner;
import qupath.lib.plugins.TaskRunnerUtils;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;

/**
 * Alternative to QP offering static methods of use for scripting, 
 * along with some extra methods that require access of GUI features.
 * 
 * @author Pete Bankhead
 *
 */
public class QPEx extends QP {

	private static final Logger logger = LoggerFactory.getLogger(QPEx.class);
	
	
	private static final Collection<Class<?>> CORE_CLASSES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
			// QuPath classes
			QuPathGUI.class,
			Dialogs.class,
			FileChoosers.class,

			GuiTools.class,
			Charts.class,
			MenuTools.class,
			GridPaneUtils.class,
			
			LabeledImageServer.class,

			MeasurementExporter.class,
			
			PathPrefs.class,
			
			LogManager.class,
			
			// JavaFX classes
			Platform.class
			)));
	
	/**
	 * Get a list of core classes that are likely to be useful for scripting.
	 * The purpose of this is to allow users to find classes they are likely to need, 
	 * or to import these automatically at the beginning of scripts.
	 * @return
	 */
	public static Collection<Class<?>> getCoreClasses() {
		var set = new LinkedHashSet<>(QP.getCoreClasses());
		set.addAll(CORE_CLASSES);
		return set;
	}
	
	
	/**
	 * Export TMA summary data for the current image.
	 * @param path path to the export directory
	 * @param downsampleFactor downsample applied to each TMA core image
	 */
	public static void exportTMAData(final String path, final double downsampleFactor) throws IOException {
		exportTMAData((ImageData<BufferedImage>)getCurrentImageData(), resolvePath(path), downsampleFactor);
	}

	
	/**
	 * Export TMA summary data for the specified image.
	 * @param imageData the image containing TMA data to export
	 * @param path path to the export directory
	 * @param downsampleFactor downsample applied to each TMA core image
	 */
	public static void exportTMAData(final ImageData<BufferedImage> imageData, final String path, final double downsampleFactor) throws IOException {
		if (imageData == null)
			return;
		TMADataIO.writeTMAData(new File(resolvePath(path)), imageData, null, downsampleFactor);
	}
	
	/**
	 * Get the current QuPath instance.
	 * @return
	 */
	public static QuPathGUI getQuPath() {
		return QuPathGUI.getInstance();
	}
	
	/**
	 * Get the active viewer in the current QuPath instance.
	 * @return an active viewer, or null if no viewer is active in QuPath currently
	 */
	public static QuPathViewer getCurrentViewer() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		return qupath == null ? null : qupath.getViewer();
	}
	
	
	/**
	 * Locate a specified file based upon its name or path, with a search depth of 4.
	 * This first checks if the provided path is to a file that already exists.
	 * If it is not, then it searches recursively within the current project or 
	 * user directory (if available) up to a fixed search depth for a file with the same name.
	 * 
	 * @param nameOrPath the original name or path
	 * @return the identified file path, or the original file path if no update was found or required
	 * @throws IOException
	 * @see UriUpdater#locateFile(String, int, Path...)
	 */
	public static String locateFile(String nameOrPath) throws IOException {
		return locateFile(nameOrPath, 4);
	}
	
	/**
	 * Locate a specified file based upon its name or path.
	 * This first checks if the provided path is to a file that already exists.
	 * If it is not, then it searches recursively within the current project or 
	 * user directory (if available) up to a specified search depth for a file with the same name.
	 * 
	 * @param nameOrPath the original name or path
	 * @param searchDepth how deep to search subdirectories recursively
	 * @return the identified file path, or the original file path if no update was found or required
	 * @throws IOException
	 * @see UriUpdater#locateFile(String, int, Path...)
	 */
	public static String locateFile(String nameOrPath, int searchDepth) throws IOException {
		var paths = new ArrayList<Path>();
		var project = getProject();
		var path = project == null ? null : project.getPath();
		if (path != null)
			paths.add(path);
		var userPath = UserDirectoryManager.getInstance().getUserPath();
		if (userPath != null)
			paths.add(userPath);
		
		if (!paths.isEmpty()) {
			return UriUpdater.locateFile(nameOrPath, searchDepth, paths.toArray(Path[]::new));
		}
		return nameOrPath;
	}
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean runPlugin(final String className, final ImageData<?> imageData, final String args) throws InterruptedException {
		if (imageData == null)
			return false;
		
		boolean completed = false;
		String pluginName = className;
		boolean cancelled = false;
		try {
			Class<?> cPlugin = QP.class.getClassLoader().loadClass(className);
			Constructor<?> cons = cPlugin.getConstructor();
			final PathPlugin plugin = (PathPlugin)cons.newInstance();
			pluginName = plugin.getName();
			TaskRunner runner;
			// TODO: Give potential of passing a plugin runner
			var qupath = getQuPath();
			if (isBatchMode() || imageData != qupath.getImageData()) {
				runner = new CommandLineTaskRunner();
				completed = plugin.runPlugin(runner, imageData, args);
				cancelled = runner.isCancelled();
			}
			else {
				completed = qupath.runPlugin(plugin, args, false);
				cancelled = !completed;
//				runner = new PluginRunnerFX(qupath);
			}
		} catch (Exception e) {
			logger.error("Error running plugin {}: {}", className, e.getLocalizedMessage());
			logger.error("", e);
		}
		// Notify caller that this failed
		if (cancelled)
			throw new InterruptedException(pluginName + " cancelled!");
//		if (!completed) {
//			throw new InterruptedException(pluginName + " cancelled!");
//		}
		return completed;
	}
	
	
	public static boolean runPlugin(String className, String args) throws InterruptedException {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		return runPlugin(className, imageData, args);
	}
	
	
	static boolean isBatchMode() {
		return getQuPath() == null || !getQuPath().getStage().isShowing();
	}

	/**
	 * Create a task runner with the default number of threads defined by {@link ThreadTools#getParallelism()}.
	 * This will either be interactive (if QuPath is running, and the current image is open or headless.
	 * @return
	 */
	public static TaskRunner createTaskRunner() {
		return createTaskRunner(ThreadTools.getParallelism());
	}

	/**
	 * Create a task runner with the specified number of threads.
	 * This will either be interactive (if QuPath is running, and the current image is open or headless.
	 * @param nThreads number of threads for the task runner to use
	 * @return
	 */
	public static TaskRunner createTaskRunner(int nThreads) {
		if (isBatchMode() || getCurrentViewer().getImageData() != getCurrentImageData()) {
			logger.info("Creating headless task runner with {} threads", nThreads);
			return TaskRunnerUtils.getDefaultInstance().createHeadlessTaskRunner(nThreads);
		} else {
			logger.info("Creating interactive task runner with {} threads", nThreads);
			return new TaskRunnerFX(getQuPath(), nThreads);
		}
	}

	
	/**
	 * Prompt the user to select a file from a file chooser.
	 * @param extensions valid file extensions, or null if any file may be chosen.
	 * @return the file chosen by the user, or null if the dialog was cancelled
	 */
	public static File promptForFile(String... extensions) {
		String filterDescription = extensions == null || extensions.length == 0 ? null : "Valid files";
		if (extensions != null && extensions.length == 0)
			extensions = null;
		return FileChoosers.promptForFile(FileChoosers.createExtensionFilter(filterDescription, extensions));
	}
	
	/**
	 * Write a rendered image to the specified path. No overlay layers will be included.
	 * @param imageData
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(QuPathViewer, String)
	 */
	public static void writeRenderedImage(ImageData<BufferedImage> imageData, String path) throws IOException {
		writeRenderedImageRegion(imageData, null, path);
	}
	
	/**
	 * Write a rendered image for the current viewer to the specified path.
	 * @param viewer
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(ImageData, String)
	 */
	public static void writeRenderedImage(QuPathViewer viewer, String path) throws IOException {
		writeRenderedImageRegion(viewer, null, path);
	}
	
	/**
	 * Write a rendered image region to the specified path. No overlay layers will be included.
	 * @param imageData
	 * @param request
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(QuPathViewer, String)
	 */
	public static void writeRenderedImageRegion(ImageData<BufferedImage> imageData, RegionRequest request, String path) throws IOException {
		var renderedServer = new RenderedImageServer.Builder(imageData).build();
		if (request == null)
			ImageWriterTools.writeImage(renderedServer, path);
		else
			ImageWriterTools.writeImageRegion(renderedServer, request, path);
	}
	
	/**
	 * Write a rendered image region for the current viewer to the specified path.
	 * @param viewer
	 * @param request
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(ImageData, String)
	 */
	public static void writeRenderedImageRegion(QuPathViewer viewer, RegionRequest request, String path) throws IOException {
		var renderedServer = RenderedImageServer.createRenderedServer(viewer);
		if (request == null)
			ImageWriterTools.writeImage(renderedServer, path);
		else
			ImageWriterTools.writeImageRegion(renderedServer, request, path);
	}
	
	/**
	 * Write a JavaFX image to the specified path.
	 * @param image the image to write
	 * @param path the path to write the image
	 * @throws IOException
	 * @see #writeRenderedImage(ImageData, String)
	 */
	public static void writeImage(Image image, String path) throws IOException {
		writeImage(SwingFXUtils.fromFXImage(image, null), path);
	}
	

	/**
	 * Set the minimum and maximum display range for the current {@link ImageData} for a channel identified by number.
	 * @param channel channel number (0-based index)
	 * @param minDisplay
	 * @param maxDisplay
	 */
	public static void setChannelDisplayRange(int channel, double minDisplay, double maxDisplay) {
		setChannelDisplayRange(getCurrentImageData(), channel, minDisplay, maxDisplay);
	}

	/**
	 * Set the minimum and maximum display range for the specified {@link ImageData} for a channel identified by number.
	 * @param imageData
	 * @param channel channel number (0-based index)
	 * @param minDisplay
	 * @param maxDisplay
	 * @return true if it was possible to set the display range, false otherwise (e.g. if the image could not be accessed,
	 *         or the channel was out of range).
	 */
	public static boolean setChannelDisplayRange(ImageData<BufferedImage> imageData, int channel, double minDisplay, double maxDisplay) {
		// Try to get an existing display if the image is currently open
		var viewer = getQuPath().getAllViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display;
		if (viewer == null) {
			try {
				display = ImageDisplay.create(imageData);
			} catch (IOException e) {
				logger.warn("Unable to set the display range for {} - ImageDisplay could not be initialized",
						imageData, e);
				return false;
			}
		} else {
			display = viewer.getImageDisplay();
		}
		var available = display.availableChannels();
		if (channel < 0 || channel >= available.size()) {
			logger.warn("Channel {} is out of range ({}-{}) - cannot set display range", channel, 0, available.size()-1);
			return false;
		}
		var info = display.availableChannels().get(channel);
		display.setMinMaxDisplay(info, (float)minDisplay, (float)maxDisplay);
		// Update the viewer is necessary
		if (viewer != null)
			viewer.repaintEntireImage();
		return true;
	}
	
	/**
	 * Set the minimum and maximum display range for the current {@link ImageData} for a channel identified by name.
	 * @param channelName
	 * @param minDisplay
	 * @param maxDisplay
	 */
	public static void setChannelDisplayRange(String channelName, double minDisplay, double maxDisplay) {
		setChannelDisplayRange(getCurrentImageData(), channelName, minDisplay, maxDisplay);
	}

	/**
	 * Set the minimum and maximum display range for the specified {@link ImageData} for a channel identified by name.
	 * @param imageData
	 * @param channelName
	 * @param minDisplay
	 * @param maxDisplay
	 */
	public static void setChannelDisplayRange(ImageData<BufferedImage> imageData, String channelName, double minDisplay, double maxDisplay) {
		// Try to get an existing display if the image is currently open
		var viewer = getQuPath().getAllViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display;
		if (viewer == null) {
			try {
				display = ImageDisplay.create(imageData);
			} catch (IOException e) {
				logger.warn("Unable to set the display range for {} - ImageDisplay could not be initialized",
						imageData, e);
				return;
			}
		} else {
			display = viewer.getImageDisplay();
		}
		var available = display.availableChannels();
		ChannelDisplayInfo info = null;
		var serverChannels = imageData.getServerMetadata().getChannels();
		for (var c : available) {
			if (channelName.equals(c.getName())) {
				info = c;
				break;
			}
			// We also need to check the channel names, since the info might have adjusted them (e.g. by adding (C1) at the end)
			if (c instanceof DirectServerChannelInfo) {
				int channelNumber = ((DirectServerChannelInfo)c).getChannel();
				if (channelNumber >= 0 && channelNumber < serverChannels.size() && channelName.equals(serverChannels.get(channelNumber).getName())) {
					info = c;
					break;
				}
			}
		}
		if (info == null) {
			logger.warn("No channel found with name {} - cannot set display range", channelName);
			return;
		}
		display.setMinMaxDisplay(info, (float)minDisplay, (float)maxDisplay);
		// Update the viewer is necessary
		if (viewer != null)
			viewer.repaintEntireImage();
	}
	
	
	/**
	 * Save annotation measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveAnnotationMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathAnnotationObject.class, path, includeColumns);
	}
	
	/**
	 * Save TMA measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveTMAMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), TMACoreObject.class, path, includeColumns);
	}
	
	/**
	 * Save detection measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveDetectionMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathDetectionObject.class, path, includeColumns);
	}

	/**
	 * Save cell measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 * @since v0.6.0
	 */
	public static void saveCellMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathCellObject.class, path, includeColumns);
	}

	/**
	 * Save tile measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 * @since v0.6.0
	 */
	public static void saveTileMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathTileObject.class, path, includeColumns);
	}
	
	/**
	 * Save whole image measurements for the current image.
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveImageMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathRootObject.class, path, includeColumns);
	}
	
	/**
	 * Save whole image measurements for the specified image.
	 * @param imageData the image data
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveImageMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathRootObject.class, path, includeColumns);
	}
	
	/**
	 * Save annotation measurements for the specified image.
	 * @param imageData the image data
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveAnnotationMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathAnnotationObject.class, path, includeColumns);
	}
	
	/**
	 * Save TMA measurements for the specified image.
	 * @param imageData the image data
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveTMAMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, TMACoreObject.class, path, includeColumns);
	}
	
	/**
	 * Save detection measurements for the specified image.
	 * @param imageData the image data
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveDetectionMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathDetectionObject.class, path, includeColumns);
	}

	/**
	 * Save measurements for the current image for objects of a fixed type.
	 * @param type the type of objects to measure
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveMeasurements(final Class<? extends PathObject> type, final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), type, path, includeColumns);
	}
	
	/**
	 * Save measurements for the specified image for objects of a fixed type.
	 * @param imageData the image data
	 * @param type the type of objects to measure
	 * @param path file path describing where to write the results
	 * @param includeColumns specific columns to include, or empty to indicate that all measurements should be exported
	 */
	public static void saveMeasurements(final ImageData<?> imageData, final Class<? extends PathObject> type, final String path, final String... includeColumns) {
		File fileOutput = new File(resolvePath(path));
		if (fileOutput.isDirectory()) {
			String ext = ",".equals(PathPrefs.tableDelimiterProperty().get()) ? ".csv" : ".txt";
			fileOutput = new File(fileOutput, ServerTools.getDisplayableImageName(imageData.getServer()) + " " + PathObjectTools.getSuitableName(type, true) + ext);
		}
		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
		try (PrintWriter writer = new PrintWriter(fileOutput, StandardCharsets.UTF_8)) {
			Collection<String> excludeColumns;
			if (includeColumns.length == 0) {
				excludeColumns = Collections.emptyList();
			} else {
				excludeColumns = new LinkedHashSet<>(model.getAllNames());
				excludeColumns.removeAll(Arrays.asList(includeColumns));
			}
			for (String row : SummaryMeasurementTable.getTableModelStrings(model, PathPrefs.tableDelimiterProperty().get(), excludeColumns))
				writer.println(row);
			writer.close();
		} catch (IOException e) {
            logger.error("Error writing file to {}", fileOutput, e);
		}
	}
	
	/**
	 * Access a window currently open within QuPath by its title.
	 * @param title
	 * @return
	 */
	public static Window getWindow(String title) {
		for (var window : Window.getWindows()) {
			if (window instanceof Stage) {
				var stage = (Stage)window;
				if (title.equals(stage.getTitle()))
					return stage;
			}
		}
		return null;
	}
	
	/**
	 * Try to copy an object to the clipboard.
	 * This will attempt to perform a smart conversion; for example, if a window is provided a snapshot will be taken 
	 * and copied as an image.
	 * @param o the object to copy
	 */
	public static void copyToClipboard(Object o) {
		if (!Platform.isFxApplicationThread()) {
			Object o2 = o;
			Platform.runLater(() -> copyToClipboard(o2));
			return;
		}
		
		ClipboardContent content = new ClipboardContent();
		
		// Handle things that are (or could become) images
		if (o instanceof BufferedImage)
			o = SwingFXUtils.toFXImage((BufferedImage)o, null);
		if (o instanceof QuPathGUI)
			o = ((QuPathGUI)o).getStage();
		if (o instanceof QuPathViewer)
			o = ((QuPathViewer)o).getView();
		if (o instanceof Window)
			o = ((Window)o).getScene();
		if (o instanceof Scene)
			o = ((Scene)o).snapshot(null);
		if (o instanceof Node)
			o = ((Node)o).snapshot(null, null);
		if (o instanceof Image)
			content.putImage((Image)o);
		
		// Handle files
		List<File> files = null;
		if (o instanceof File)
			files = Arrays.asList((File)o);
		else if (o instanceof File[])
			files = Arrays.asList((File[])o);
		else if (o instanceof Collection) {
			files = new ArrayList<>();
			for (var something : (Collection<?>)o) {
				if (something instanceof File)
					files.add((File)something);
			}
		}
		if (files != null && !files.isEmpty())
			content.putFiles(files);
		
		// Handle URLs
		if (o instanceof URL)
			content.putUrl(((URL)o).toString());
		
		// Always put a String representation
		content.putString(o.toString());
		Clipboard.getSystemClipboard().setContent(content);
	}


	/**
	 * Load a display settings object from a file path or from the current project.
	 * @param name
	 * @return the settings if they could be read, or null otherwise
	 */
	public static ImageDisplaySettings loadDisplaySettings(String name) {
		var project = getProject();
		if (project != null) {
			var manager = DisplaySettingUtils.getResourcesForProject(project);
			try {
				if (manager.getNames().contains(name))
					return manager.get(name);
			} catch (IOException e) {
				logger.error("Error attempting to access resource manager for project {}", project, e);
			}
		}
		var path = Paths.get(name);
		if (Files.exists(path)) {
			try {
				return DisplaySettingUtils.parseDisplaySettings(path);
			} catch (IOException e) {
				logger.error("Error attempting to read {}", path, e);
			}
		}
		logger.warn("Cannot find display settings {} either as a file path or in the current project", name);
		return null;
	}

	/**
	 * Apply the display settings with the specified name or file path to the current version.
	 * This provides a convenient alternative to
	 * <p>
	 * <pre><code>
	 * var settings = loadDisplaySettings(name);
	 * var viewer = getCurrentViewer();
	 * if (settings != null)
	 *     applyDisplaySettings(viewer, settings);
	 * </code>
	 * </pre>
	 * @param name
	 * @return
	 * @see #loadDisplaySettings(String)
	 */
	public static boolean applyDisplaySettings(String name) {
		var settings = loadDisplaySettings(name);
		if (settings != null)
			return applyDisplaySettings(getCurrentViewer(), settings);
		else {
			logger.warn("Unable to load display settings from {}", name);
			return false;
		}
	}

	/**
	 * Apply the display settings to the current viewer.
	 * @param settings
	 * @return
	 */
	public static boolean applyDisplaySettings(ImageDisplaySettings settings) {
		return applyDisplaySettings(getCurrentViewer(), settings);
	}

	/**
	 * Apply the display settings to the specified viewer.
	 * @param viewer
	 * @param settings
	 * @return
	 */
	public static boolean applyDisplaySettings(QuPathViewer viewer, ImageDisplaySettings settings) {
		if (viewer != null && settings != null && DisplaySettingUtils.applySettingsToDisplay(viewer.getImageDisplay(), settings)) {
			maybeSyncSettingsAcrossViewers(viewer.getImageDisplay());
			return true;
		}
		return false;
	}

	private static void maybeSyncSettingsAcrossViewers(ImageDisplay display) {
		var qupath = getQuPath();
		if (qupath == null || display == null || !PathPrefs.keepDisplaySettingsProperty().get())
			return;
		for (var otherViewer : qupath.getAllViewers()) {
			if (!otherViewer.hasServer() || Objects.equals(display, otherViewer.getImageDisplay()))
				continue;
			otherViewer.getImageDisplay().updateFromDisplay(display);
		}
	}


	/**
	 * Show a measurement table for TMA core objects from the current image.
	 * @since v0.6.0
	 */
	public static void showTmaCoreMeasurementTable() {
		showTmaCoreMeasurementTable(getCurrentImageData());
	}

	/**
	 * Show a measurement table for TMA core objects from the specified image.
	 * @param imageData the image data
	 * @since v0.6.0
	 */
	public static void showTmaCoreMeasurementTable(ImageData<BufferedImage> imageData) {
		showMeasurementTable(imageData, PathObjectFilter.TMA_CORES);
	}

	/**
	 * Show a measurement table for all detection objects from the current image.
	 * @since v0.6.0
	 */
	public static void showDetectionMeasurementTable() {
		showDetectionMeasurementTable(getCurrentImageData());
	}

	/**
	 * Show a measurement table for all detection objects from the specified image.
	 * @param imageData the image data
	 * @since v0.6.0
	 */
	public static void showDetectionMeasurementTable(ImageData<BufferedImage> imageData) {
		showMeasurementTable(imageData, PathObjectFilter.DETECTIONS_ALL);
	}

	/**
	 * Show a measurement table for annotation objects from the current image.
	 * @since v0.6.0
	 */
	public static void showAnnotationMeasurementTable() {
		showAnnotationMeasurementTable(getCurrentImageData());
	}

	/**
	 * Show a measurement table for annotation objects from the specified image.
	 * @param imageData the image data
	 * @since v0.6.0
	 */
	public static void showAnnotationMeasurementTable(ImageData<BufferedImage> imageData) {
		showMeasurementTable(imageData, PathObjectFilter.ANNOTATIONS);
	}

	/**
	 * Show a measurement table for tile objects from the current image.
	 * @since v0.6.0
	 */
	public static void showCellMeasurementTable() {
		showCellMeasurementTable(getCurrentImageData());
	}

	/**
	 * Show a measurement table for cell objects from the specified image.
	 * @param imageData the image data
	 * @since v0.6.0
	 */
	public static void showCellMeasurementTable(ImageData<BufferedImage> imageData) {
		showMeasurementTable(imageData, PathObjectFilter.CELLS);
	}

	/**
	 * Show a measurement table for tile objects from the current image.
	 * @since v0.6.0
	 */
	public static void showTileMeasurementTable() {
		showTileMeasurementTable(getCurrentImageData());
	}

	/**
	 * Show a measurement table for tile objects from the specified image.
	 * @param imageData the image data
	 * @since v0.6.0
	 */
	public static void showTileMeasurementTable(ImageData<BufferedImage> imageData) {
		showMeasurementTable(imageData, PathObjectFilter.TILES);
	}

	/**
	 * Show a measurement table for the current image.
	 * <p>
	 * This method is provided for flexibility, and accepts an arbitrary predicate to select objects precisely.
	 * If the table should contain all objects of a specific type (e.g. detections, cells, annotations)
	 * then one of the related 'show' methods should be used instead, because they are better optimized and
	 * can log export scripts.
	 *
	 * @param filter the filter to use when selecting objects to display
	 * @see PathObjectFilter
	 * @since v0.6.0
	 */
	public static void showMeasurementTable(Predicate<PathObject> filter) {
		showMeasurementTable(getCurrentImageData(), filter);
	}

	/**
	 * Show a measurement table for the specified image.
	 * <p>
	 * This method is provided for flexibility, and accepts an arbitrary predicate to select objects precisely.
	 * If the table should contain all objects of a specific type (e.g. detections, cells, annotations)
	 * then one of the related 'show' methods should be used instead, because they are better optimized and
	 * can log export scripts.
	 * 
	 * @param imageData the image data
	 * @param filter the filter to use when selecting objects to display
	 * @see PathObjectFilter
	 * @since v0.6.0
	 */
	public static void showMeasurementTable(ImageData<BufferedImage> imageData, Predicate<PathObject> filter) {
		if (imageData == null) {
			logger.warn("Can't show measurement table - image data is null");
			return;
		}
		var command = new SummaryMeasurementTableCommand(QuPathGUI.getInstance());
		FXUtils.runOnApplicationThread(() -> command.showTable(imageData, filter));
	}
	
}
