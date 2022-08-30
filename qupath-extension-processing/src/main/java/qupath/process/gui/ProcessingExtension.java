/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.process.gui;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.openblas.global.openblas;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_ml;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.scripting.QP;
import qupath.opencv.CellCountsCV;
import qupath.opencv.features.DelaunayClusteringPlugin;
import qupath.opencv.ops.ImageOps;
import qupath.process.gui.commands.CellIntensityClassificationCommand;
import qupath.process.gui.commands.CreateChannelTrainingImagesCommand;
import qupath.process.gui.commands.CreateCompositeClassifierCommand;
import qupath.process.gui.commands.CreateRegionAnnotationsCommand;
import qupath.process.gui.commands.CreateTrainingImageCommand;
import qupath.process.gui.commands.DensityMapCommand;
import qupath.process.gui.commands.ObjectClassifierCommand;
import qupath.process.gui.commands.ObjectClassifierLoadCommand;
import qupath.process.gui.commands.PixelClassifierCommand;
import qupath.process.gui.commands.SimpleThresholdCommand;
import qupath.process.gui.commands.SingleMeasurementClassificationCommand;
import qupath.process.gui.commands.SplitProjectTrainingCommand;
import qupath.process.gui.commands.ui.LoadResourceCommand;

/**
 * General-purpose processing and machine learning commands.
 */
public class ProcessingExtension implements QuPathExtension {
	
	private static final Logger logger = LoggerFactory.getLogger(ProcessingExtension.class);
	
	static {
		// TODO: Consider a better way to force initialization of key processing classes
		var qp = new QP();
	}
	
	
	/**
	 * Commands based on OpenCV.
	 */
	@SuppressWarnings("javadoc")
	public static class OpenCVCommands {
		
		@ActionMenu("Analyze>Spatial analysis>")
		@ActionDescription("Apply a Delaunay triangulation to detection objects based on their centroid locations. "
				+ "This helps identify clusters of objects neighboring one another."
				+ "\n\nNote this command is likely to be replaced in a future version.")
		public final Action actionDelaunay;

//		@ActionMenu("Analyze>Region identification>")
//		@ActionDescription("")
//		@Deprecated
//		public final Action actionCytokeratin;

		@ActionMenu("Analyze>Cell detection>")
		@ActionDescription("Fast cell counting for hematoxylin and DAB images.")
		@Deprecated
		public final Action actionFastCellCounts;
		
		
		@ActionMenu("Analyze>Density maps>Create density map")
		public final Action actionDensityMap;

		@ActionMenu("Analyze>Density maps>Load density map")
		public final Action actionDensityMapLoad;

				
		private OpenCVCommands(QuPathGUI qupath) {
			actionDelaunay = qupath.createPluginAction("Delaunay cluster features 2D", DelaunayClusteringPlugin.class, null);
//			actionCytokeratin = qupath.createPluginAction("Create cytokeratin annotations (experimental)", DetectCytokeratinCV.class, null);
			actionFastCellCounts = qupath.createPluginAction("Fast cell counts (brightfield)", CellCountsCV.class, null);
			var densityMapCommand = new DensityMapCommand(qupath);
			actionDensityMap = qupath.createImageDataAction(imageData -> densityMapCommand.run());
			
			var commandLoad = LoadResourceCommand.createLoadDensityMapCommand(qupath);
			actionDensityMapLoad = qupath.createImageDataAction(imageData -> commandLoad.run());

		}

	}
	
	
	
	@ActionMenu("Classify>Pixel classification>")
	@SuppressWarnings("javadoc")
	public class PixelClassificationCommands {
		
		@ActionMenu("Load pixel classifier")
		@ActionDescription("Load an existing pixel classifier. "
				+ "This can be used to apply the classifier to new images, but not to continue training.")
		public final Action actionLoadPixelClassifier;

		@ActionMenu("")
		public final Action SEP = ActionTools.createSeparator();
		
		@ActionMenu("Train pixel classifier")
		@ActionAccelerator("shortcut+shift+p")
		@ActionDescription("Train a pixel classifier. "
				+ "This can be used to quantify areas, or to generate or classify objects.")
		public final Action actionPixelClassifier;
		
		@ActionMenu("Create thresholder")
		@ActionDescription("Create a simple pixel classifier that applies a threshold to an image.")
		public final Action actionSimpleThreshold;
				
		private PixelClassificationCommands(QuPathGUI qupath) {
			
			var commandPixel = new PixelClassifierCommand();
			actionPixelClassifier = qupath.createImageDataAction(imageData -> commandPixel.run());
			
			var commandLoad = LoadResourceCommand.createLoadPixelClassifierCommand(qupath);
			actionLoadPixelClassifier = qupath.createImageDataAction(imageData -> commandLoad.run());
			
			var commandThreshold = new SimpleThresholdCommand(qupath);
			actionSimpleThreshold = qupath.createImageDataAction(imageData -> commandThreshold.run());
		}
		
	}
	
	@ActionMenu("Classify>Object classification>")
	@SuppressWarnings("javadoc")
	public class ObjectClassificationCommands {
				
		@ActionMenu("Load object classifier")
		@ActionDescription("Load an existing object classifier. "
				+ "This can be used to apply the classifier to new objects, but not to continue training.")
		public final Action actionLoadObjectClassifier;

		public final Action SEP = ActionTools.createSeparator();

		@ActionMenu("Train object classifier")
		@ActionAccelerator("shortcut+shift+d")
		@ActionDescription("Interactively train an object classifier using machine learning. "
				+ "This is useful whenever objects cannot be classified based on one measurement alone.")
		public final Action actionObjectClassifier;

		@ActionMenu("Create single measurement classifier")
		@ActionDescription("Create a simple object classifier that applies a threshold to a single measurement.")
		public final Action actionMeasurement;

		@ActionMenu("Create composite classifier")
		@ActionDescription("Combine multiple classifiers together to create a single classifier by applying them sequentially.")
		public final Action actionComposite;
				
		@ActionMenu("Set cell intensity classifications")
		@ActionDescription("Set cell intensity classifications based upon a single measurement. "
				+ "This is useful to calculate densities/percentages of positive cells or H-scores.")
		public final Action actionIntensity;
		
		
		private ObjectClassificationCommands(QuPathGUI qupath) {
			var commandLoad = new ObjectClassifierLoadCommand(qupath);
			actionLoadObjectClassifier = qupath.createImageDataAction(imageData -> commandLoad.run());
			
			var commandComposite = new CreateCompositeClassifierCommand(qupath);
			actionComposite = ActionTools.createAction(commandComposite);
			
			var commandSingle = new SingleMeasurementClassificationCommand(qupath);
			actionMeasurement = qupath.createImageDataAction(imageData -> commandSingle.run());
			
			var commandIntensity = new CellIntensityClassificationCommand(qupath);
			actionIntensity = qupath.createImageDataAction(imageData -> commandIntensity.run());
			
			var commandCreate = new ObjectClassifierCommand(qupath);
			actionObjectClassifier = qupath.createImageDataAction(imageData -> commandCreate.run());
		}
		
	}
	
	@SuppressWarnings("javadoc")
	public class OtherCommands {
		
		
		@ActionMenu("Classify>Training images>Create region annotations")
		@ActionDescription("Create annotations of fixed-size regions."
				+ "\n\nThis can be used to select representative regions of multiple images to train (usually pixel) classifier, "
				+ "in combination with 'Create training image'.")
		public final Action actionCreateRegions;

		@ActionDescription("Create an image comprised of regions extracted from multiple images in a project. " +
				"This can be useful for interactively training a classifier across a varied dataset.")
		@ActionMenu("Classify>Training images>Create training image")
		public final Action actionTrainingImage;

		@ActionMenu("Classify>Training images>Create duplicate channel training images")
		@ActionDescription("Duplicate an image in a project so that there is one duplicate for each channel of the image. "
				+ "\n\nThis can be used to train separate classifiers for different channels in multiplexed images, "
				+ "which are then merged to form a composite classifier.")
		public final Action actionChannelTraining;
		
		@ActionMenu("Classify>Training images>Split project train/validation/test")
		@ActionDescription("Split images within a project into training, validation and test sets.")
		public final Action actionSplitProject;
				
//		@ActionMenu("Classify>Training images>Export training regions")
//		public final Action actionExportTraining;
		
		
		private OtherCommands(QuPathGUI qupath) {
			
			actionTrainingImage = qupath.createProjectAction(project -> promptToCreateSparseServer(qupath));
			
			var channelTraining = new CreateChannelTrainingImagesCommand(qupath);
			actionChannelTraining = qupath.createImageDataAction(imageData -> channelTraining.run());
			
			var splitProject = new SplitProjectTrainingCommand(qupath);
			actionSplitProject = qupath.createProjectAction(project -> splitProject.run());
			
			var createRegions = new CreateRegionAnnotationsCommand(qupath);
			actionCreateRegions = qupath.createImageDataAction(imageData -> createRegions.run());
			
//			var exportTraining = new ExportTrainingRegionsCommand(qupath);
//			actionExportTraining = qupath.createProjectAction(project -> exportTraining.run());
		}
		
	}
	
	
	/**
	 * Prompt the user to create a sparse image server composed of annotated regions from images within the current QuPath project.
	 * @param qupath the current QuPath instance
	 */
	private static void promptToCreateSparseServer(QuPathGUI qupath) {
		var project = qupath.getProject();
		var entry = CreateTrainingImageCommand.promptToCreateTrainingImage(project, qupath.getAvailablePathClasses());
		qupath.refreshProject();
		if (entry != null) {
			qupath.openImageEntry(entry);
		}
	}
	
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
    	logger.debug("Installing extension");
    	
		qupath.installActions(ActionTools.getAnnotatedActions(new OpenCVCommands(qupath)));
    	qupath.installActions(ActionTools.getAnnotatedActions(new PixelClassificationCommands(qupath)));
    	qupath.installActions(ActionTools.getAnnotatedActions(new ObjectClassificationCommands(qupath)));
    	qupath.installActions(ActionTools.getAnnotatedActions(new OtherCommands(qupath)));
    	
    	installWand(qupath);
    	
    	// This is needed for Gson serialization
    	new ImageOps();
    }
    
    
    private void installWand(QuPathGUI qupath) {
    	var t = new Thread(() -> {
	    	// TODO: Check if openblas multithreading continues to have trouble with Mac/Linux
	    	if (!GeneralTools.isWindows()) {
	    		openblas.blas_set_num_threads(1);
	    	}
	    	
	    	// Ensure we can load core classes
	    	loadCoreClasses();
	    	
			// Install the Wand tool
			var wandTool = PathTools.createTool(new WandToolCV(qupath), "Wand",
					IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.WAND_TOOL));
			logger.debug("Installing wand tool");
			Platform.runLater(() -> {
				qupath.installTool(wandTool, new KeyCodeCombination(KeyCode.W));
				qupath.getToolAction(wandTool).setLongText(
						"Click and drag to draw with a wand tool. "
						+ "Adjust brightness/contrast or wand preferences to customize the sensitivity and behavior."
						);
			});
			
			logger.debug("Loading OpenCV classes");
		});
		t.start();
    }
    
    
    /**
     * Try to load core classes and report if we can't.
     * This is important because if class loading fails then the user isn't always notified in the log.
     * It also aims to overcome a subtle bug (seen on macOS) whereby density maps would fail if run 
     * before the wand tool (or another using opencv_imgproc), seemingly because of threading/file locks. 
     * This tended to only input the first installation; once opencv_imgproc was cached by JavaCPP, then 
     * it would always work.
     */
    private void loadCoreClasses() {
    	try {
    		logger.debug("Attempting to load core OpenCV classes");
    		Loader.load(opencv_core.class);
			Loader.load(opencv_imgproc.class);
			Loader.load(opencv_ml.class);
			Loader.load(opencv_dnn.class);
    	} catch (Throwable t) {
    		logger.error("Error loading OpenCV classes: " + t.getLocalizedMessage(), t);
    	}
    }
    

    @Override
    public String getName() {
        return "Processing extension";
    }

    @Override
    public String getDescription() {
        return "Core processing & classification commands";
    }
    
	@Override
	public Version getVersion() {
		return Version.parse(GeneralTools.getPackageVersion(ProcessingExtension.class));
	}
	
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return getVersion();
	}
	
}