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

package qupath.imagej.gui;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.Color;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.swing.SwingUtilities;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.detect.dearray.TMADearrayerPluginIJ;
import qupath.imagej.detect.features.ImmuneScorerTMA;
import qupath.imagej.detect.nuclei.PositiveCellDetection;
import qupath.imagej.detect.nuclei.WatershedCellDetection;
import qupath.imagej.detect.nuclei.WatershedCellMembraneDetection;
import qupath.imagej.detect.nuclei.WatershedNucleusDetection;
import qupath.imagej.detect.tissue.PositivePixelCounterIJ;
import qupath.imagej.detect.tissue.SimpleTissueDetection;
import qupath.imagej.detect.tissue.SimpleTissueDetection2;
import qupath.imagej.gui.commands.ExtractRegionCommand;
import qupath.imagej.gui.commands.ScreenshotCommand;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.images.writers.TIFFWriterIJ;
import qupath.imagej.images.writers.ZipWriterIJ;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.plugins.ImageJMacroRunner;
import qupath.imagej.superpixels.DoGSuperpixelsPlugin;
import qupath.imagej.superpixels.SLICSuperpixelsPlugin;
import qupath.lib.analysis.objects.TileClassificationsToAnnotationsPlugin;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ImageWriterTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.helpers.PathObjectColorToolsAwt;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupathj.QUPath_Send_Overlay_to_QuPath;
import qupathj.QUPath_Send_ROI_to_QuPath;

/**
 * QuPath extension & associated static helper methods used to support integration of ImageJ with QuPath.
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
	
	public static void setImageJPath(final String path) {
		imageJPath.set(path);
	}
	
	public static String getImageJPath() {
		return imageJPath.get();
	}
	
	public static StringProperty imageJPathProperty() {
		return imageJPath;
	}
	

	/**
	 * Get an instance of ImageJ, or start one, for interactive use (with GUI displayed).
	 * Returns null if ImageJ could not be started.
	 * 
	 * @return
	 */
	public static synchronized ImageJ getImageJInstance() {
		if (SwingUtilities.isEventDispatchThread())
			return getImageJInstanceOnEDT();
		
		if (IJ.getInstance() != null)
			return IJ.getInstance();
		
		// Try getting ImageJ without resorting to the EDT?
		return getImageJInstanceOnEDT();
	}
		
		
	private static synchronized ImageJ getImageJInstanceOnEDT() {
		ImageJ ijTemp = IJ.getInstance();
		Prefs.setThreads(1); // Turn off ImageJ's multithreading, since we do our own
		if (ijTemp != null)
			return ijTemp;
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

		// Add some useful plugins
		Menus.installPlugin(QUPath_Send_ROI_to_QuPath.class.getName(), Menus.PLUGINS_MENU, "Send ROI to QuPath", "", ijTemp);
		Menus.installPlugin(QUPath_Send_Overlay_to_QuPath.class.getName(), Menus.PLUGINS_MENU, "Send Overlay to QuPath", "", ijTemp);

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
			public void windowClosed(WindowEvent e) {
//				for (Frame frame : Frame.getFrames()) {
//					// Close any images we have open
//					if (frame instanceof ImageWindow) {
//						ImageWindow win = (ImageWindow)frame;
//						win.setVisible(false);
//						ImagePlus imp = win.getImagePlus();
//						if (imp != null) {
//							imp.changes = false;
//							imp.close();
//							//								imp.flush();
//						} else
//							win.dispose();
//					}
//				}
//				ij.removeWindowListener(this);
//				ij.quit();
//				ij.dispose();
//				Platform.runLater(() -> {
//					QuPathGUI.getInstance().getMenuBar().setUseSystemMenuBar(false);
//					QuPathGUI.getInstance().getMenuBar().setUseSystemMenuBar(true);					
//				});
//				IJ.wait(10);
//				ij.setMenuBar(null);
			}

		});
		
//		// Unfortunately it isn't possible to use the system menubar when ImageJ is used...
//		if (GeneralTools.isMac()) {
//			Platform.runLater(() -> {
////				for (Menu menu : QuPathGUI.getInstance().getMenuBar().getMenus())
////					System.err.println("Menu: " + menu.getText());
////				System.err.println(QuPathGUI.getInstance().getMenuBar().getMenus());
//				QuPathGUI.getInstance().getMenuBar().setUseSystemMenuBar(false);
////				QuPathGUI.getInstance().getMenuBar().getMenus().setAll(menusPrevious);
//				QuPathGUI.getInstance().getMenuBar().setUseSystemMenuBar(true);
//			});
//		}

		logger.debug("Created ImageJ instance: {}", ijTemp);
		return ijTemp;
	}



	public static PathImage<ImagePlus> extractROIWithOverlay(ImageServer<BufferedImage> server, PathObject pathObject, PathObjectHierarchy hierarchy, RegionRequest request, boolean setROI, OverlayOptions options, ImageDisplay imageDisplay) {
		ROI pathROI;
		if (pathObject == null || !pathObject.hasROI()) {
			pathROI = new RectangleROI(0, 0, server.getWidth(), server.getHeight());
			//			logger.error("No ROI found to extract!");
			//			return null;
		} else
			pathROI = pathObject.getROI();

		// Extract the image
		PathImage<ImagePlus> pathImage = IJTools.extractROI(server, pathROI, request, setROI, imageDisplay);
		if (pathImage == null)
			return pathImage;

		// Add the overlay
		if (hierarchy != null) {
			ImagePlus imp = pathImage.getImage();
			Overlay overlay = new Overlay();
			
			// TODO: Permit filling/unfilling ROIs
			for (PathObject child : hierarchy.getObjectsForRegion(PathObject.class, request, null)) {
				if (child.equals(pathObject))
					continue;
				
				if (child.hasROI()) {
					
					// Check if this is displayed - skip it not
					if (options != null && 
							((child instanceof PathDetectionObject && !options.getShowObjects()) ||
							(child instanceof PathAnnotationObject && !options.getShowAnnotations()) ||
							(child instanceof TMACoreObject && !options.getShowTMAGrid())))
						continue;
					
					boolean isCell = child instanceof PathCellObject;
					
					Color color = PathObjectColorToolsAwt.getDisplayedColorAWT(child);
					if (!(isCell && !options.getShowCellBoundaries())) {
						Roi roi = ROIConverterIJ.convertToIJRoi(child.getROI(), pathImage);
						roi.setStrokeColor(color);
						roi.setName(child.getDisplayedName());
						//						roi.setStrokeWidth(2);
						overlay.add(roi);
					}
					
					// TODO: Permit cell boundaries/nuclei to be shown/hidden
					if (isCell && options.getShowCellNuclei()) {
						ROI nucleus = ((PathCellObject)child).getNucleusROI();
						if (nucleus == null)
							continue;
						Roi roi = ROIConverterIJ.convertToIJRoi(((PathCellObject)child).getNucleusROI(), pathImage);
						roi.setStrokeColor(color);
						roi.setName(child.getDisplayedName() + " - nucleus");
						overlay.add(roi);
						//							roi.setStrokeWidth(2);
					}
				}
			}
			if (overlay.size() > 0) {
				//			if (imp.getRoi() != null) {
				//				overlay.add(imp.getRoi());
				//				imp.killRoi();
				//			}
				imp.setOverlay(overlay);
			}
		}
		IJTools.setTitleFromObject(pathImage, pathObject);

		return pathImage;
	}

	
	
	
	
	/**
	 * 
	 * Add some commands written using ImageJ to QuPath.
	 * 
	 * TODO: Clean up this code... it's incredibly messy!
	 * 
	 * @param qupath
	 */
	public static void addQuPathCommands(final QuPathGUI qupath) {
		
		
		// Add a preference to set the ImageJ path
		qupath.getPreferencePanel().addDirectoryPropertyPreference(
				imageJPath, "ImageJ plugins directory", "ImageJ",
				"Set the path to the 'plugins' directory of an existing ImageJ installation");
		

		ImageWriterTools.registerImageWriter(new TIFFWriterIJ());
		ImageWriterTools.registerImageWriter(new ZipWriterIJ());

		// Experimental brush tool turned off for now
		//			qupath.getViewer().registerTool(Modes.BRUSH, new FancyBrushTool(qupath));

//		ExtractRegionCommand commandExtractRegion1 = new ExtractRegionCommand(qupath, 1, false);
//		ExtractRegionCommand commandExtractRegion2 = new ExtractRegionCommand(qupath, 2, false);
//		ExtractRegionCommand commandExtractRegion4 = new ExtractRegionCommand(qupath, 4, false);
		ExtractRegionCommand commandExtractRegionCustom = new ExtractRegionCommand(qupath, 2, true);

		PathCommand screenshotCommand = new ScreenshotCommand(qupath);
		
		// Add buttons to toolbar
		qupath.addToolbarSeparator();
		
		try {
			ImageView imageView = new ImageView(getImageJIcon(QuPathGUI.iconSize, QuPathGUI.iconSize));
			Button btnImageJ = new Button();
			btnImageJ.setGraphic(imageView);
			btnImageJ.setTooltip(new Tooltip("ImageJ commands"));
			ContextMenu popup = new ContextMenu();
			popup.getItems().addAll(
					QuPathGUI.createMenuItem(QuPathGUI.createCommandAction(commandExtractRegionCustom, "Send region to ImageJ", PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.EXTRACT_REGION), null)),
					QuPathGUI.createMenuItem(QuPathGUI.createCommandAction(screenshotCommand, "Send snapshot to ImageJ", PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.SCREENSHOT), null))
					);
			btnImageJ.setOnMouseClicked(e -> {
				popup.show(btnImageJ, e.getScreenX(), e.getScreenY());
			});
			
//			// Make it possible to set the ImageJ plugins path, to give easy access to user plugins
//			MenuItem miSetPluginsPath = new MenuItem("Set ImageJ plugins directory");
//			miSetPluginsPath.setOnAction(e -> {
//				String path = PathPrefs.getImageJPath();
//				File dir = qupath.getDialogHelper().promptForDirectory(new File(path));
//				if (dir != null)
//					PathPrefs.setImageJPath(dir.getAbsolutePath());
//			});
//			popup.getItems().addAll(new SeparatorMenuItem(), miSetPluginsPath);
			
			qupath.addToolbarButton(btnImageJ);
		} catch (Exception e) {
			logger.error("Error adding toolbar buttons", e);
			qupath.addToolbarCommand(commandExtractRegionCustom.getName(), commandExtractRegionCustom, PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.EXTRACT_REGION));
			qupath.addToolbarCommand("Make screenshot", screenshotCommand, PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.SCREENSHOT));
		}
		
		

		
		// Add an analysis menu
//		Menu menuAnalysis = qupath.getMenu("Analyze", true);
		
//		Menu menuFeatures = qupath.getMenu("Analyze>Calculate features", true);
		
		Menu menuRegions = qupath.getMenu("Analyze>Region identification", true);
		QuPathGUI.addMenuItems(menuRegions,
				qupath.createPluginAction("Positive pixel count (experimental)", PositivePixelCounterIJ.class, null, false),
				qupath.createPluginAction("DoG superpixel segmentation", DoGSuperpixelsPlugin.class, null, false),
				qupath.createPluginAction("SLIC superpixel segmentation (experimental)", SLICSuperpixelsPlugin.class, null, false),
//				qupath.createPluginAction("Gaussian superpixel segmentation", GaussianSuperpixelsPlugin.class, null, false),
				qupath.createPluginAction("Tile classifications to annotations", TileClassificationsToAnnotationsPlugin.class, null, false)				
				);
		
//		//			menuExperimental.add(new PathPluginAction("SVM classifier", SVMClassifierPlugin.class, qupath));
////		menuExperimental.add(new PathPluginAction<>("Experimental cell detection", WatershedCellDetection2.class, qupath));
////		menuExperimental.add(new PathPluginAction<>("Simple DAB quantification", SimpleDABQuantification.class, qupath));
////		menuExperimental.add(new PathPluginAction<>("Simple membrane detection (experimental)", SimpleMembraneDetection.class, qupath));
//		menuExperimental.getItems().add(new PathPluginAction("Compute experimental cell features", ExperimentalCellFeaturesPlugin.class, qupath));
//		menuExperimental.getItems().add(new PathPluginAction("Tile classifications to annotations", TileClassificationsToAnnotationsPlugin.class, qupath));

		// Put dearraying at the top of the TMA menu
		Menu menuTMA = qupath.getMenu("TMA", true);
		menuTMA.getItems().add(0,
				QuPathGUI.createMenuItem(qupath.createPluginAction("TMA dearrayer", TMADearrayerPluginIJ.class, null, false))
				);
		menuTMA.getItems().add(1,
				new SeparatorMenuItem()
				);
//		QuPathGUI.addMenuItems(
//				menuTMA,
//				null,
//				qupath.createPluginAction("TMA dearrayer", TMADearrayerPluginIJ.class, null, false)
//				);
		
		
		// Make it possible to set the ImageJ plugins path, to give easy access to user plugins
		MenuItem miSetPluginsPath = new MenuItem("Set ImageJ plugins directory");
		miSetPluginsPath.setOnAction(e -> {
			String path = getImageJPath();
			File dir = qupath.getDialogHelper().promptForDirectory(new File(path));
			if (dir != null)
				setImageJPath(dir.getAbsolutePath());
		});
					
		
		Menu menuAutomate = qupath.getMenu("Extensions>ImageJ", true);
		Action actionMacroRunner = qupath.createPluginAction("ImageJ macro runner", new ImageJMacroRunner(qupath), null);
		QuPathGUI.addMenuItems(menuAutomate,
				QuPathGUI.createMenuItem(QuPathGUI.createCommandAction(commandExtractRegionCustom, "Send region to ImageJ", PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.EXTRACT_REGION), null)),
				QuPathGUI.createMenuItem(QuPathGUI.createCommandAction(screenshotCommand, "Send snapshot to ImageJ", PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.SCREENSHOT), null)),
				null,
				miSetPluginsPath,
				null,
				actionMacroRunner
				);
		
		qupath.getDefaultDragDropListener().addFileDropHandler((viewer, list) -> {
			// TODO: Handle embedding useful running info within ImageJ macro comments
			if (list.size() > 0 && list.get(0).getName().toLowerCase().endsWith(".ijm")) {
				String macro;
				try {
					macro = GeneralTools.readFileAsString(list.get(0).getAbsolutePath());
					qupath.runPlugin(new ImageJMacroRunner(qupath), macro, true);
				} catch (IOException e) {
					DisplayHelpers.showErrorMessage("Error opening ImageJ macro", e);
					return false;
				}
				return true;
			}
			return false;
		});
		
		Menu menuPreprocessing = qupath.getMenu("Analyze>Preprocessing", true);
		QuPathGUI.addMenuItems(
				menuPreprocessing,
				null,
				qupath.createPluginAction("Simple tissue detection", SimpleTissueDetection2.class, null, true),
				qupath.createPluginAction("Simple tissue detection (legacy)", SimpleTissueDetection.class, null, false)
				);
		
		
		Menu menuCellAnalysis = qupath.getMenu("Analyze>Cell analysis", true);
		QuPathGUI.addMenuItems(
				menuCellAnalysis,
//				qupath.createPluginAction("Mean brown chromogen (legacy)", MeanBrownChromogenPlugin.class, null, false),
//				new SeparatorMenuItem(),
				qupath.createPluginAction("Cell detection", WatershedCellDetection.class, null, false),
				qupath.createPluginAction("Positive cell detection", PositiveCellDetection.class, null, false),
				qupath.createPluginAction("Cell + membrane detection", WatershedCellMembraneDetection.class, null, false),
//				qupath.createPluginAction("Cell + membrane detection + percentage (experimental)", WatershedCellMembraneDetectionWithBoundaries.class, null, false),
				qupath.createPluginAction("Positive nucleus detection (legacy)", WatershedNucleusDetection.class, null, false),
				new SeparatorMenuItem(),
//				qupath.createPluginAction("Lesion detection (experimental)", LesionDetector.class, null, false),
				new SeparatorMenuItem(),
				qupath.createPluginAction("Immune scorer (TMA)", ImmuneScorerTMA.class, null, false)
				);



//		// Add ImageJ-specific options to viewer's popup-menu
//		final QuPathViewer viewer = qupath.getViewer();
//		final JPopupMenu popup = viewer.getComponentPopupMenu() == null ? new JPopupMenu() : viewer.getComponentPopupMenu();
//
//		JMenu menuIJ = new JMenu("ImageJ");
//		menuIJ.add(actionScreenshot);
//		menuIJ.addSeparator();
//		menuIJ.add(actionExtractRegion1);
//		menuIJ.add(actionExtractRegion2);
//		menuIJ.add(actionExtractRegion4);
//		menuIJ.add(actionExtractRegionCustom);
//
//		popup.addSeparator();
//		popup.add(menuIJ);		
//
//		viewer.setComponentPopupMenu(popup);
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
