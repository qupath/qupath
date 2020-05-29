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

package qupath.lib.gui.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.Charts;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.plugins.PluginRunnerFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
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

	final private static Logger logger = LoggerFactory.getLogger(QPEx.class);
	
	
	private final static List<Class<?>> CORE_CLASSES = Collections.unmodifiableList(Arrays.asList(
			// QuPath classes
			QuPathGUI.class,
			Dialogs.class,
			GuiTools.class,
			Charts.class,
			MenuTools.class,
			PaneTools.class,
			
			LabeledImageServer.class,
			
			LogManager.class,
			
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
	 * Export TMA summary data for the current image.
	 * @param path path to the export directory
	 * @param downsampleFactor downsample applied to each TMA core image
	 */
	public static void exportTMAData(final String path, final double downsampleFactor) {
		exportTMAData((ImageData<BufferedImage>)getCurrentImageData(), resolvePath(path), downsampleFactor);
	}

	
	/**
	 * Export TMA summary data for the specified image.
	 * @param imageData the image containing TMA data to export
	 * @param path path to the export directory
	 * @param downsampleFactor downsample applied to each TMA core image
	 */
	public static void exportTMAData(final ImageData<BufferedImage> imageData, final String path, final double downsampleFactor) {
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
	
	
	/**
	 * Prompt the user to select a file from a file chooser.
	 * @param extensions valid file extensions, or null if any file may be chosen.
	 * @return the file chosen by the user, or null if the dialog was cancelled
	 */
	public static File promptForFile(String... extensions) {
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
	 */
	public static void setChannelDisplayRange(ImageData<BufferedImage> imageData, int channel, double minDisplay, double maxDisplay) {
		// Try to get an existing display if the image is currently open
		var viewer = getQuPath().getViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display = viewer == null ? new ImageDisplay(imageData) : viewer.getImageDisplay();
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
		var viewer = getQuPath().getViewers().stream()
				.filter(v -> v.getImageData() == imageData)
				.findFirst()
				.orElse(null);
		ImageDisplay display = viewer == null ? new ImageDisplay(imageData) : viewer.getImageDisplay();
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