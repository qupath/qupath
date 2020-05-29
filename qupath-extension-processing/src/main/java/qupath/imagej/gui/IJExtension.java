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

package qupath.imagej.gui;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import javax.swing.SwingUtilities;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.detect.cells.PositiveCellDetection;
import qupath.imagej.detect.cells.SubcellularDetection;
import qupath.imagej.detect.cells.WatershedCellDetection;
import qupath.imagej.detect.cells.WatershedCellMembraneDetection;
import qupath.imagej.detect.dearray.TMADearrayerPluginIJ;
import qupath.imagej.detect.tissue.PositivePixelCounterIJ;
import qupath.imagej.detect.tissue.SimpleTissueDetection2;
import qupath.imagej.superpixels.DoGSuperpixelsPlugin;
import qupath.imagej.superpixels.SLICSuperpixelsPlugin;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.objects.TileClassificationsToAnnotationsPlugin;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupathj.QuPath_Send_Overlay_to_QuPath;
import qupathj.QuPath_Send_ROI_to_QuPath;

/**
 * QuPath extension &amp; associated static helper methods used to support integration of ImageJ with QuPath.
 * 
 * @author Pete Bankhead
 *
 */
public class IJExtension implements QuPathExtension {
	
	final private static Logger logger = LoggerFactory.getLogger(IJExtension.class);
	
	// Path to ImageJ - used to determine plugins directory
	private static StringProperty imageJPath = null;

	static {
		// Try to default to the most likely ImageJ path on a Mac
		if (GeneralTools.isMac() && new File("/Applications/ImageJ/").isDirectory())
			imageJPath = PathPrefs.createPersistentPreference("ijPath", "/Applications/ImageJ");
		else
			imageJPath = PathPrefs.createPersistentPreference("ijPath", null);
	}
	
	/**
	 * Set the path for a local ImageJ installation, if required. This can be used to help load external ImageJ plugins.
	 * @param path
	 */
	public static void setImageJPath(final String path) {
		imageJPath.set(path);
	}
	
	/**
	 * Get the path for a local ImageJ installation, if set.
	 * @return 
	 */
	public static String getImageJPath() {
		return imageJPath.get();
	}
	
	/**
	 * Property representing the path to a local ImageJ installation, or null if no path has been set.
	 * @return
	 */
	public static StringProperty imageJPathProperty() {
		return imageJPath;
	}
	

	/**
	 * Get an instance of ImageJ, or start one, for interactive use (with GUI displayed).
	 * 
	 * @return an ImageJ instance, or null if ImageJ could not be started
	 */
	public static synchronized ImageJ getImageJInstance() {
		if (SwingUtilities.isEventDispatchThread())
			return getImageJInstanceOnEDT();
		
		var ij = IJ.getInstance();
		if (ij != null) {
			ensurePluginsInstalled(ij);
			return ij;
		}
		
		// Try getting ImageJ without resorting to the EDT?
		return getImageJInstanceOnEDT();
	}
		
	private static Set<ImageJ> installedPlugins = Collections.newSetFromMap(new WeakHashMap<>());
	
	/**
	 * Ensure we have installed the necessary plugins.
	 * We might not if ImageJ has been launched elsewhere.
	 * @param imageJ the ImageJ instance for which the plugins should be installed.
	 */
	private static synchronized void ensurePluginsInstalled(ImageJ imageJ) {
		if (installedPlugins.contains(imageJ))
			return;
		if (!Platform.isFxApplicationThread() && !SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> ensurePluginsInstalled(imageJ));
			return;
		}
		logger.info("Installing QuPath plugins for ImageJ");
		Menus.installPlugin(QuPath_Send_ROI_to_QuPath.class.getName(), Menus.PLUGINS_MENU, "Send ROI to QuPath", "", imageJ);
		Menus.installPlugin(QuPath_Send_Overlay_to_QuPath.class.getName(), Menus.PLUGINS_MENU, "Send Overlay to QuPath", "", imageJ);
		installedPlugins.add(imageJ);
	}
		
	private static synchronized ImageJ getImageJInstanceOnEDT() {
		ImageJ ijTemp = IJ.getInstance();
		Prefs.setThreads(1); // Turn off ImageJ's multithreading, since we do our own
		if (ijTemp == null) {
			logger.info("Creating a new standalone ImageJ instance...");
	//		List<Menu> menusPrevious = new ArrayList<>(QuPathGUI.getInstance().getMenuBar().getMenus());
			try {
	//			Class<?> cls = IJ.getClassLoader().loadClass("MacAdapter");
	//			System.err.println("I LOADED: " + cls);
				// See http://rsb.info.nih.gov/ij/docs/menus/plugins.html for setting the plugins directory
				String ijPath = getImageJPath();
				if (ijPath != null)
					System.getProperties().setProperty("plugins.dir", ijPath);
				ijTemp = new ImageJ(ImageJ.STANDALONE);
			} catch (Exception e) {
				// There may be an error (e.g. on OSX when attempting to install an ApplicationListener), but one we can safely ignore -
				// so don't print a full stack trace & try to get an instance again
				logger.warn(e.getLocalizedMessage());
				ijTemp = IJ.getInstance();
			}
			if (ijTemp == null) {
				logger.error("Unable to start ImageJ");
				return null;
			}
		
			// ImageJ doesn't necessarily behave well when it is closed but windows are left open -
			// so here ensure that all remaining displayed images are closed
			final ImageJ ij = ijTemp;
			ij.exitWhenQuitting(false);
			ij.addWindowListener(new WindowAdapter() {
				
				@Override
				public void windowDeactivated(WindowEvent e) {
	//				ij.setMenuBar(null);
				}
				
				@Override
				public void windowLostFocus(WindowEvent e) {
	//				ij.setMenuBar(null);
				}
				
				@Override
				public void windowClosing(WindowEvent e) {
					// Spoiler alert: it *is* the EDT (as one would expect)
	//				System.err.println("EDT: " + SwingUtilities.isEventDispatchThread());
	//				System.err.println("Application thread: " + Platform.isFxApplicationThread());
					ij.requestFocus();
					for (Frame frame : Frame.getFrames()) {
						// Close any images we have open
						if (frame instanceof ImageWindow) {
							ImageWindow win = (ImageWindow)frame;
							ImagePlus imp = win.getImagePlus();
							if (imp != null)
								imp.setIJMenuBar(false);
							win.setVisible(false);
							if (imp != null) {
								// Save message still appears...
								imp.changes = false;
								// Initially tried to close, but then ImageJ hung
								// Flush was ok, unless it was selected to save changes - in which case that didn't work out
	//							imp.flush();
	//							imp.close();
								//								imp.flush();
							} else
								win.dispose();
						}
					}
					ij.removeWindowListener(this);
					IJ.wait(10);
					ij.setMenuBar(null);
				}
	
				@Override
				public void windowClosed(WindowEvent e) {}
	
			});
	
			logger.debug("Created ImageJ instance: {}", ijTemp);
		}
		
		// Make sure we have QuPath's custom plugins installed
		ensurePluginsInstalled(ijTemp);
		
		return ijTemp;
	}

	
	
	/**
	 * Extract a region of interest from an image as an ImageJ ImagePlus.
	 * @param server the image
	 * @param pathROI
	 * @param request
	 * @param setROI true if a ROI should be converted to the closest matching ImageJ {@code Roi} &amp; set on the image, false otherwise
	 * @return an {@link ImagePlus} wrapped in a {@link PathImage} to give additional calibration information
	 * @throws IOException 
	 */
	public static PathImage<ImagePlus> extractROI(ImageServer<BufferedImage> server, ROI pathROI, RegionRequest request, boolean setROI) throws IOException {
		setROI = setROI && (pathROI != null);
		// Ensure the ROI bounds & ensure it fits within the image
		Rectangle bounds = AwtTools.getBounds(request);
		if (bounds != null)
			bounds = bounds.intersection(new Rectangle(0, 0, server.getWidth(), server.getHeight()));
		if (bounds == null) {
			return null;
		}
	
		PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
		if (pathImage == null || pathImage.getImage() == null)
			return null;
	
	
	
		if (setROI) {
			ImagePlus imp = pathImage.getImage();
//			if (!(pathROI instanceof RectangleROI)) {
				Roi roi = IJTools.convertToIJRoi(pathROI, pathImage);
				imp.setRoi(roi);
//			}
		}
		return pathImage;
	}

	/**
	 * Similar to {@link #extractROI(ImageServer, ROI, RegionRequest, boolean)}, except that the title of the ImagePlus is set according to the parent object type (which is used to get the ROI).
	 * Specifically, if a TMA core is passed as a parent, then the core name will be included in the title.
	 * 
	 * @param server
	 * @param pathObject
	 * @param request
	 * @param setROI
	 * @return
	 * @throws IOException 
	 * 
	 * @see #extractROI(ImageServer, ROI, RegionRequest, boolean)
	 */
	public static PathImage<ImagePlus> extractROI(ImageServer<BufferedImage> server, PathObject pathObject, RegionRequest request, boolean setROI) throws IOException {
		PathImage<ImagePlus> pathImage = extractROI(server, pathObject.getROI(), request, setROI);
		IJTools.setTitleFromObject(pathImage, pathObject);
		return pathImage;
	}
	

	/**
	 * Extract an image region as an ImagePlus, optionally setting ImageJ Rois corresponding to QuPath objects.
	 * 
	 * @param server server from which pixels should be requested
	 * @param pathObject the primary object, which may have its ROI set on the image
	 * @param hierarchy object hierarchy containing objects whose ROIs should be added to the ImagePlus overlay
	 * @param request the region being requested
	 * @param setROI if true, the ROI of the pathObject will be set on the image as the 'main' ROI (i.e. not an overlay)
	 * @param options options determining which kinds of objects will have ROIs added, to match with the display in the QuPath viewer
	 * @return
	 * @throws IOException
	 */
	public static PathImage<ImagePlus> extractROIWithOverlay(ImageServer<BufferedImage> server, PathObject pathObject, PathObjectHierarchy hierarchy, RegionRequest request, boolean setROI, OverlayOptions options) throws IOException {
		ROI pathROI;
		if (pathObject == null || !pathObject.hasROI()) {
			pathROI = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane());
			//			logger.error("No ROI found to extract!");
			//			return null;
		} else
			pathROI = pathObject.getROI();

		// Extract the image
		PathImage<ImagePlus> pathImage = extractROI(server, pathROI, request, setROI);
		if (pathImage == null)
			return pathImage;

		// Add the overlay
		if (hierarchy != null) {
			ImagePlus imp = pathImage.getImage();
			var regionPredicate = PathObjectTools.createImageRegionPredicate(request);
			Overlay overlay = extractOverlay(hierarchy, request, options, p -> p != pathObject && regionPredicate.test(p));
			if (overlay.size() > 0) {
				imp.setOverlay(overlay);
			}
		}
		IJTools.setTitleFromObject(pathImage, pathObject);

		return pathImage;
	}

	
	/**
	 * Extract an ImageJ overlay for the specified region.
	 * @param hierarchy
	 * @param request
	 * @param options options to control which objects are being displayed
	 * @param filter optional additional filter used to determine which objects will be included (may be used in combination with options)
	 * @return
	 */
	public static Overlay extractOverlay(PathObjectHierarchy hierarchy, RegionRequest request, OverlayOptions options, Predicate<PathObject> filter) {
		Overlay overlay = new Overlay();
		
		double downsample = request.getDownsample();
		double xOrigin = -request.getX() / downsample;
		double yOrigin = -request.getY() / downsample;
		
		// TODO: Permit filling/unfilling ROIs
		for (PathObject child : hierarchy.getObjectsForRegion(PathObject.class, request, null)) {
			if (filter != null && !filter.test(child))
				continue;
			
			if (child.hasROI()) {
				
				// Check if this is displayed - skip it not
				if (options != null && 
						((child instanceof PathDetectionObject && !options.getShowDetections()) ||
						(child instanceof PathAnnotationObject && !options.getShowAnnotations()) ||
						(child instanceof TMACoreObject && !options.getShowTMAGrid())))
					continue;
				
				boolean isCell = child instanceof PathCellObject;
				
				Color color = ColorToolsAwt.getCachedColor(ColorToolsFX.getDisplayedColorARGB(child));
				if (!(isCell && (options == null || !options.getShowCellBoundaries()))) {
					Roi roi = IJTools.convertToIJRoi(child.getROI(), xOrigin, yOrigin, downsample);
					roi.setStrokeColor(color);
					roi.setName(child.getDisplayedName());
					//						roi.setStrokeWidth(2);
					overlay.add(roi);
				}
				if (isCell && (options == null || options.getShowCellNuclei())) {
					ROI nucleus = ((PathCellObject)child).getNucleusROI();
					if (nucleus == null)
						continue;
					Roi roi = IJTools.convertToIJRoi(((PathCellObject)child).getNucleusROI(), xOrigin, yOrigin, downsample);
					roi.setStrokeColor(color);
					roi.setName(child.getDisplayedName() + " - nucleus");
					overlay.add(roi);
				}
			}
		}
		return overlay;
	}
	
	
	/**
	 * Commands to install with the ImageJ extension.
	 */
	@SuppressWarnings("javadoc")
	public static class IJExtensionCommands {

		@ActionMenu("Analyze>Tiles & superpixels>")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("Analyze>Tiles & superpixels>")
		@ActionDescription("Create superpixel tiles using the SLIC method.")
		public final Action actionSLIC;
		
		@ActionMenu("Analyze>Tiles & superpixels>")
		@ActionDescription("Create superpixel tiles using a Difference of Gaussians method.")
		public final Action actionDoG;
		
		@ActionMenu("Analyze>Tiles & superpixels>")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Analyze>Tiles & superpixels>")
		@ActionDescription("Merge tiles sharing the same classification to become annotations.")
		public final Action actionTiles;
		
		
		@ActionMenu("Analyze>Cell detection>")		
		@ActionDescription("Default cell detection in QuPath. "
				+ "Note that this is general-purpose method, not optimized for any particular staining."
				+ "\n\nIt is essential to set the image type first (e.g. brightfield or fluorescence) before running this command.")
		public final Action actionCellDetection;

		@ActionMenu("Analyze>Cell detection>")		
		@ActionDescription("Equivalent to 'Cell detection', with additional parameters to set a threshold during detection to "
				+ "identify single-positive cells.")
		public final Action actionPositiveCellDetection;
		
		@ActionMenu("Analyze>Cell detection>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Analyze>Cell detection>")		
		@ActionDescription("Identify subcellular structures (e.g. spots of all kinds) within detected cells.")
		@Deprecated
		public final Action actionSubcellularDetection;

		@ActionMenu("Analyze>")
		public final Action SEP_2B = ActionTools.createSeparator();
		
		@ActionMenu("Analyze>Deprecated>")		
		@ActionDescription("Area-based quantification of positive pixels with DAB staining. "
				+ "This command does not handle large regions well; if possible, pixel classification should usually be used instead.")
		@Deprecated
		public final Action actionPixelCount;
		
//		@ActionMenu("TMA>")				
//		@ActionDescription("Identify cores and grid arrangement of a tissue microarray.")
//		public final Action actionTMADearray;
		
		
		@ActionMenu("Analyze>Deprecated>")		
		@ActionDescription("Detect large regions using a simple thresholding method. "
				+ "This command is not very flexible and lacks any preview of the results; if possible, pixel classification should usually be used instead.")
		@Deprecated
		public final Action actionSimpleTissueDetection;
		
		@ActionMenu("Analyze>Deprecated>")		
		@ActionDescription("Cell detection that uses membrane information to constrain cell boundary expansion. "
				+ "\n\nThis was designed specifically for hematoxylin and DAB staining, and works only where membrane staining is "
				+ "either very clear or absent. It is not recommended in general.")
		@Deprecated
		public final Action actionCellMembraneDetection;

		
		@ActionIcon(PathIcons.EXTRACT_REGION)
		@ActionMenu("Extensions>ImageJ>Send region to ImageJ")
		@ActionDescription("Extract the selected image region and send it to ImageJ.")
		public final Action actionExtractRegion;
				
		@ActionIcon(PathIcons.SCREENSHOT)
		@ActionMenu("Extensions>ImageJ>")
		@ActionDescription("Create a rendered (RGB) snapshot and send it to ImageJ.")
		public final Action actionSnapshot;
		
		@ActionMenu("Extensions>ImageJ>")
		public final Action SEP_3 = ActionTools.createSeparator();
		
		@ActionMenu("Extensions>ImageJ>")
		@ActionDescription("Set the plugins directory to use with QuPath's embedded version of ImageJ. "
				+ "\n\nThis can be set to the plugins directory of an existing ImageJ installation, to make the plugins associated "
				+ "with that installation available within QuPath.")
		public final Action actionPlugins = ActionTools.createAction(() -> promptToSetPluginsDirectory(), "Set plugins directory");
		
		@ActionMenu("Extensions>ImageJ>")
		public final Action SEP_4 = ActionTools.createSeparator();

		@ActionMenu("Extensions>ImageJ>")
		@ActionDescription("Run ImageJ macros within QuPath.")
		public final Action actionMacroRunner;
		
		IJExtensionCommands(QuPathGUI qupath) {
			
			// Experimental brush tool turned off for now
			ExtractRegionCommand commandExtractRegionCustom = new ExtractRegionCommand(qupath);
			actionExtractRegion = qupath.createImageDataAction(imageData -> commandExtractRegionCustom.run());
			actionExtractRegion.setLongText("Extract the selected image region and send it to ImageJ.");

			var screenshotCommand = new ScreenshotCommand(qupath);
			actionSnapshot = ActionTools.createAction(screenshotCommand, "Send snapshot to ImageJ");		
			
			actionMacroRunner = qupath.createPluginAction("ImageJ macro runner", new ImageJMacroRunner(qupath), null);
			
			actionSLIC = qupath.createPluginAction("SLIC superpixel segmentation", SLICSuperpixelsPlugin.class, null);
			actionDoG = qupath.createPluginAction("DoG superpixel segmentation", DoGSuperpixelsPlugin.class, null);
			actionTiles = qupath.createPluginAction("Tile classifications to annotations", TileClassificationsToAnnotationsPlugin.class, null);
			
			actionPixelCount = qupath.createPluginAction("Positive pixel count", PositivePixelCounterIJ.class, null);
			
//			actionTMADearray = qupath.createPluginAction("TMA dearrayer", TMADearrayerPluginIJ.class, null);
			
			actionSimpleTissueDetection = qupath.createPluginAction("Simple tissue detection", SimpleTissueDetection2.class, null);
			
			actionCellDetection = qupath.createPluginAction("Cell detection", WatershedCellDetection.class, null);
			actionPositiveCellDetection = qupath.createPluginAction("Positive cell detection", PositiveCellDetection.class, null);
			actionCellMembraneDetection = qupath.createPluginAction("Cell + membrane detection", WatershedCellMembraneDetection.class, null);
			actionSubcellularDetection = qupath.createPluginAction("Subcellular detection (experimental)", SubcellularDetection.class, null);
		}

	}
	
	
	static void promptToSetPluginsDirectory() {
		String path = getImageJPath();
		File dir = Dialogs.promptForDirectory(path == null ? null : new File(path));
		if (dir != null && dir.isDirectory())
			setImageJPath(dir.getAbsolutePath());
	}
	
	
	/**
	 * 
	 * Add some commands written using ImageJ to QuPath.
	 * 
	 * TODO: Clean up this code... it's incredibly messy!
	 * 
	 * @param qupath
	 */
	private void addQuPathCommands(final QuPathGUI qupath) {
		
		// Add a preference to set the ImageJ path
		qupath.getPreferencePane().addDirectoryPropertyPreference(
				imageJPath, "ImageJ plugins directory", "ImageJ",
				"Set the path to the 'plugins' directory of an existing ImageJ installation");
		
		var commands = new IJExtensionCommands(qupath);
		qupath.installActions(ActionTools.getAnnotatedActions(commands));
		
		// Add buttons to toolbar
		var toolbar = qupath.getToolBar();
		toolbar.getItems().add(new Separator(Orientation.VERTICAL));
		
		try {
			ImageView imageView = new ImageView(getImageJIcon(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE));
			Button btnImageJ = new Button();
			btnImageJ.setGraphic(imageView);
			btnImageJ.setTooltip(new Tooltip("ImageJ commands"));
			ContextMenu popup = new ContextMenu();
			popup.getItems().addAll(
					ActionTools.createMenuItem(commands.actionExtractRegion),
					ActionTools.createMenuItem(commands.actionSnapshot)
					);
			btnImageJ.setOnMouseClicked(e -> {
				popup.show(btnImageJ, e.getScreenX(), e.getScreenY());
			});
			toolbar.getItems().add(btnImageJ);
		} catch (Exception e) {
			logger.error("Error adding toolbar buttons", e);
		}
				
		// It's awkward, but we handle TMA dearraying separation so we can ensure it falls at the top of the list
		Menu menuTMA = qupath.getMenu("TMA", true);
		var actionTMADearray = qupath.createPluginAction("TMA dearrayer", TMADearrayerPluginIJ.class, null);
		actionTMADearray.setLongText("Identify cores and grid arrangement of a tissue microarray.");
		menuTMA.getItems().addAll(0,
				Arrays.asList(
						ActionTools.createMenuItem(actionTMADearray),
						new SeparatorMenuItem()
						)
				);
		
		qupath.getDefaultDragDropListener().addFileDropHandler((viewer, list) -> {
			// TODO: Handle embedding useful running info within ImageJ macro comments
			if (list.size() > 0 && list.get(0).getName().toLowerCase().endsWith(".ijm")) {
				String macro;
				try {
					macro = GeneralTools.readFileAsString(list.get(0).getAbsolutePath());
					qupath.runPlugin(new ImageJMacroRunner(qupath), macro, true);
				} catch (IOException e) {
					Dialogs.showErrorMessage("Error opening ImageJ macro", e);
					return false;
				}
				return true;
			}
			return false;
		});
		
	}

	
	/**
	 * Try to read the ImageJ icon from its jar.
	 * 
	 * @param width
	 * @param height
	 * @return
	 */
	public static Image getImageJIcon(final int width, final int height) {
		try {
			URL url = ImageJ.class.getClassLoader().getResource("microscope.gif");
			return new Image(url.toString(), width, height, true, true);
		} catch (Exception e) {
			logger.error("Unable to load ImageJ icon!", e);
		}	
		return null;
	}
	
	

	@Override
	public void installExtension(QuPathGUI qupath) {
		Prefs.setThreads(1); // We always want a single thread, due to QuPath's multithreading
//		Prefs.setIJMenuBar = false;
		addQuPathCommands(qupath);
	}


	@Override
	public String getName() {
		return "ImageJ extension";
	}


	@Override
	public String getDescription() {
		return "QuPath commands that enable integration with ImageJ - https://imagej.nih.gov/ij/";
	}



}
