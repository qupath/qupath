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

package qupath.lib.gui.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ChannelDisplayInfo.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.plugins.PluginRunnerFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.gui.tools.Charts;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.scripting.QP;

/**
 * Alternative to QP offering static methods of use for scripting, 
 * along with some extra methods that require access of GUI features.
 * 
 * @author Pete Bankhead
 *
 */
public class QPEx extends QP {

	final private static Logger logger = LoggerFactory.getLogger(QPEx.class);
	
	/**
	 * Placeholder for the path to the current project.
	 * May be used as follows:
	 * <pre>
	 *   var path = buildFilePath(PROJECT_BASE_DIR, 'subdir', 'name.txt')
	 * </pref>
	 */
	final public static String PROJECT_BASE_DIR = "{%PROJECT}";
	
	
	private final static List<Class<?>> CORE_CLASSES = Collections.unmodifiableList(Arrays.asList(
			// QuPath classes
			QuPathGUI.class,
			Dialogs.class,
			GuiTools.class,
			Charts.class,
			MenuTools.class,
			PaneTools.class,
			
			// JavaFX classes
			Platform.class
			));
	
	/**
	 * Get a list of core classes that are likely to be useful for scripting.
	 * The purpose of this is to allow users to find classes they are likely to need, 
	 * or to import these automatically at the beginning of scripts.
	 * @return
	 */
	public static List<Class<?>> getCoreClasses() {
		var list = new ArrayList<>(QP.getCoreClasses());
		list.addAll(CORE_CLASSES);
		return list;
	}
	
	/**
	 * Get a Java/Groovy-friendly multi-line String to import essential classes for scripting.
	 * @return
	 */
	static String getDefaultImports() {
		return getDefaultImports(false);
	}
	
	/**
	 * Get a Java/Groovy-friendly String to import essential classes for scripting.
	 * @param singleLine if true, return imports as a single line (separated by semi-colons)
	 * @return
	 */
	static String getDefaultImports(boolean singleLine) {
		List<String> imports = new ArrayList<>();
		for (var cls : QPEx.getCoreClasses())
			imports.add("import " + cls.getName());
		// Import script class statically and in the normal way
		imports.add("import " + QPEx.class.getName());
		imports.add("import static " + QPEx.class.getName() + ".*");
		if (singleLine)
			return String.join("; ", imports);
		return String.join(";"+System.lineSeparator(), imports);
	}
	
	
	/**
	 * Load ImageData from a file.
	 * 
	 * @param path Path to the file containing ImageData.
	 * @param setBatchData If true, the <code>setBatchImageData(ImageData)</code> will be called if the loading is successful.
	 * @return
	 * @throws IOException 
	 * 
	 * @see #setBatchImageData
	 */
	public static ImageData<BufferedImage> loadImageData(final String path, final boolean setBatchData) throws IOException {
		ImageData<BufferedImage> imageData = PathIO.readImageData(new File(resolvePath(path)), null, null, BufferedImage.class);
		if (setBatchData && imageData != null)
			setBatchImageData(imageData);
		return imageData;
	}
	

	public static void exportTMAData(final String path, final double downsampleFactor) {
		exportTMAData((ImageData<BufferedImage>)getCurrentImageData(), resolvePath(path), downsampleFactor);
	}
	
	private static String resolvePath(final String path) {
		String base = getProjectBaseDirectory();
		if (base != null)
			return path.replace(PROJECT_BASE_DIR, base);
		else if (path.contains(PROJECT_BASE_DIR))
			throw new IllegalArgumentException("Cannot resolve path '" + path + "' - no project base directory available");
		return
			path;
	}

	
	public static void exportTMAData(final ImageData<BufferedImage> imageData, final String path, final double downsampleFactor) {
		if (imageData == null)
			return;
		TMADataIO.writeTMAData(new File(resolvePath(path)), imageData, null, downsampleFactor);
	}
	
	
	public static QuPathGUI getQuPath() {
		return QuPathGUI.getInstance();
	}
	
	
	public static QuPathViewer getCurrentViewer() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		return qupath == null ? null : qupath.getViewer();
	}
	
	
	public static String buildFilePath(String...path) {
		File file = new File(resolvePath(path[0]));
		for (int i = 1; i < path.length; i++)
			file = new File(file, path[i]);
		return file.getAbsolutePath();
	}
	
	
	public static boolean mkdirs(String path) {
		File file = new File(resolvePath(path));
		if (!file.exists())
			return file.mkdirs();
		return false;
	}
	
	public static boolean fileExists(String path) {
		return new File(resolvePath(path)).exists();
	}

	public static boolean isDirectory(String path) {
		return new File(resolvePath(path)).isDirectory();
	}

	
	/**
	 * Get the base directory for the currently-open project, or null if no project is open.
	 * 
	 * This can be useful for setting e.g. save directories relative to the current project.
	 * 
	 * @return
	 */
	private static String getProjectBaseDirectory() {
		File dir = Projects.getBaseDirectory(getProject());
		return dir == null ? null : dir.getAbsolutePath();
	}
	
	/**
	 * Get the current project, or null if no project is open.
	 * 
	 * @return
	 */
	public static Project<BufferedImage> getProject() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			return qupath.getProject();
		else
			return null;
	}
	
	
	/**
	 * Get the project entry for the currently-open image within the current project, 
	 * or null if no project/image is open.
	 * 
	 * @return
	 */
	public static ProjectImageEntry<BufferedImage> getProjectEntry() {
		Project project = getProject();
		ImageData imageData = getCurrentImageData();
		if (project == null || imageData == null)
			return null;
		return project.getEntry(imageData);
	}
	
	
	/**
	 * Get the metadata value from the current project entry for the specified key, 
	 * or null if no such metadata value exists (or no project entry is open).
	 * 
	 * @param key
	 * @return
	 */
	public static String getProjectEntryMetadataValue(final String key) {
		ProjectImageEntry<BufferedImage> entry = getProjectEntry();
		if (entry == null)
			return null;
		return entry.getMetadataValue(key);
	}
	
	
	@SuppressWarnings("unchecked")
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
			PluginRunner runner;
			// TODO: Give potential of passing a plugin runner
			if (isBatchMode() || imageData != getQuPath().getImageData()) {
				runner = new CommandLinePluginRunner(imageData);
			}
			else {
				runner = new PluginRunnerFX(getQuPath());
			}
			completed = plugin.runPlugin(runner, args);
			cancelled = runner.isCancelled();
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
	
	
	
	public static File promptForFile(String[] extensions) {
		String filterDescription = extensions == null || extensions.length == 0 ? null : "Valid files";
		if (extensions != null && extensions.length == 0)
			extensions = null;
		return Dialogs.promptForFile(null, null, filterDescription, extensions);
	}
	
	/**
	 * Write a rendered image to the specified path. No overlay layers will be included.
	 * @param imageData
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(QuPathViewer, String)
	 */
	public static void writeRenderedImage(ImageData<BufferedImage> imageData, String path) throws IOException {
		var renderedServer = new RenderedImageServer.Builder(imageData).build();
		ImageWriterTools.writeImage(renderedServer, path);
	}
	
	/**
	 * Write a rendered image for the current viewer to the specified path.
	 * @param viewer
	 * @param path
	 * @throws IOException
	 * @see #writeRenderedImage(ImageData, String)
	 */
	public static void writeRenderedImage(QuPathViewer viewer, String path) throws IOException {
		var renderedServer = RenderedImageServer.createRenderedServer(viewer);
		ImageWriterTools.writeImage(renderedServer, path);
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
	 */
	public static void setChannelDisplayRange(ImageData<?> imageData, int channel, double minDisplay, double maxDisplay) {
		// Try to get an existing display if the image is currently open
		var viewer = getQuPath().getViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display = viewer == null ? new ImageDisplay((ImageData<BufferedImage>)imageData) : viewer.getImageDisplay();
		var available = display.availableChannels();
		if (channel < 0 || channel >= available.size()) {
			logger.warn("Channel {} is out of range ({}-{}) - cannot set display range", channel, 0, available.size()-1);
			return;
		}
		var info = display.availableChannels().get(channel);
		display.setMinMaxDisplay(info, (float)minDisplay, (float)maxDisplay);
		// Update the viewer is necessary
		if (viewer != null)
			viewer.repaintEntireImage();
	}
	
	/**
	 * Apply an object classifier to the current {@link ImageData}.
	 * This method throws an {@link IllegalArgumentException} if the classifier cannot be found.
	 * @param name the name of the classifier within the current project, or file path to a classifier to load from disk
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	public static void runObjectClassifier(String name) throws IllegalArgumentException {
		runObjectClassifier(getCurrentImageData(), name);
	}
	
	/**
	 * Apply an object classifier to the specified {@link ImageData}.
	 * This method throws an {@link IllegalArgumentException} if the classifier cannot be found.
	 * @param imageData 
	 * @param name the name of the classifier within the current project, or file path to a classifier to load from disk
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void runObjectClassifier(ImageData imageData, String name) throws IllegalArgumentException {
		if (imageData == null) {
			logger.warn("Cannot run object classifier - no ImageData available!");
			return;
		}
		ObjectClassifier classifier = loadObjectClassifier(name);
		
		var pathObjects = classifier.getCompatibleObjects(imageData);
		if (classifier.classifyObjects(imageData, pathObjects, true) > 0)
			imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
	}
	
	/**
	 * Load an object classifier for a project or file path.
	 * 
	 * @param name the name of the classifier within the current project, or file path to a classifier to load from disk
	 * @return the requested {@link ObjectClassifier}
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	public static ObjectClassifier loadObjectClassifier(String name) throws IllegalArgumentException {
		var project = getProject();
		ObjectClassifier classifier = null;
		Exception exception = null;
		if (project != null) {
			try {
				if (project.getObjectClassifiers().getNames().contains(name))
					classifier = project.getObjectClassifiers().get(name);
			} catch (Exception e) {
				exception = e;
				logger.debug("Object classifier '{}' not found in project", name);
			}
		}
		if (classifier == null) {
			try {
				var path = Paths.get(name);
				if (Files.exists(path))
					classifier = ObjectClassifiers.readClassifier(path);
			} catch (Exception e) {
				exception = e;
				logger.debug("Object classifier '{}' cannot be read from file", name);
			}
		}
		if (classifier == null) {
			throw new IllegalArgumentException("Unable to find object classifier " + name, exception);
		} else
			return classifier;
	}
	
	
	// TODO: Make loadPixelClassifier available whenever the code is refactored
//	/**
//	 * Load a pixel classifier for a project or file path.
//	 * 
//	 * @param name the name of the classifier within the current project, or file path to a classifier to load from disk
//	 * @return the requested {@link PixelClassifier}
//	 * @throws IllegalArgumentException if the classifier cannot be found
//	 */
//	public static PixelClassifier loadPixelClassifier(String name) throws IllegalArgumentException {
//		var project = getProject();
//		PixelClassifier classifier = null;
//		Exception exception = null;
//		if (project != null) {
//			try {
//				if (project.getPixelClassifiers().getNames().contains(name))
//					classifier = project.getPixelClassifiers().get(name);
//			} catch (Exception e) {
//				exception = e;
//				logger.debug("Pixel classifier '{}' not found in project", name);
//			}
//		}
//		if (classifier == null) {
//			try {
//				var path = Paths.get(name);
//				if (Files.exists(path))
//					classifier = PixelClassifiers.readClassifier(path);
//			} catch (Exception e) {
//				exception = e;
//				logger.debug("Pixel classifier '{}' cannot be read from file", name);
//			}
//		}
//		if (classifier == null) {
//			throw new IllegalArgumentException("Unable to find object classifier " + name, exception);
//		} else
//			return classifier;
//	}
	
	
	
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
	public static void setChannelDisplayRange(ImageData<?> imageData, String channelName, double minDisplay, double maxDisplay) {
		// Try to get an existing display if the image is currently open
		var viewer = getQuPath().getViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display = viewer == null ? new ImageDisplay((ImageData<BufferedImage>)imageData) : viewer.getImageDisplay();
		var available = display.availableChannels();
		ChannelDisplayInfo info = null;
		var serverChannels = imageData.getServer().getMetadata().getChannels();
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
	
	
	public static void saveAnnotationMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathAnnotationObject.class, path, includeColumns);
	}
	
	public static void saveTMAMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), TMACoreObject.class, path, includeColumns);
	}
	
	public static void saveDetectionMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathDetectionObject.class, path, includeColumns);
	}
	
	public static void saveImageMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathRootObject.class, path, includeColumns);
	}
	
	public static void saveImageMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathRootObject.class, path, includeColumns);
	}
	
	public static void saveAnnotationMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathAnnotationObject.class, path, includeColumns);
	}
	
	public static void saveTMAMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, TMACoreObject.class, path, includeColumns);
	}
	
	public static void saveDetectionMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathDetectionObject.class, path, includeColumns);
	}

	public static void saveMeasurements(final Class<? extends PathObject> type, final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), type, path, includeColumns);
	}
	
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
			for (String row : SummaryMeasurementTableCommand.getTableModelStrings(model, PathPrefs.tableDelimiterProperty().get(), excludeColumns))
				writer.println(row);
			writer.close();
		} catch (IOException e) {
			logger.error("Error writing file to " + fileOutput, e);
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
	
	
}