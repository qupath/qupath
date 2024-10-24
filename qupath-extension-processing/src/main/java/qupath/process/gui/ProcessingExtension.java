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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.localization.QuPathResources;
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
	@ActionMenu("Menu.Analyze")
	public static class OpenCVCommands {
		
		@ActionMenu("Deprecated")
		@ActionConfig("Action.Processing.Spatial.delaunay")
		@Deprecated
		public final Action actionDelaunay;

		@ActionMenu("Menu.Analyze.CellDetection")
		@ActionConfig("Action.Processing.CellDetection.fastCellCounts")
		@Deprecated
		public final Action actionFastCellCounts;
		
		
		@ActionMenu("Menu.Analyze.DensityMaps")
		@ActionConfig("Action.Processing.DensityMaps.create")
		public final Action actionDensityMap;

		@ActionMenu("Menu.Analyze.DensityMaps")
		@ActionConfig("Action.Processing.DensityMaps.load")
		public final Action actionDensityMapLoad;

				
		private OpenCVCommands(QuPathGUI qupath) {
			actionDelaunay = qupath.createPluginAction("Delaunay cluster features 2D (deprecated)", DelaunayClusteringPlugin.class, null);
			actionFastCellCounts = qupath.createPluginAction("Fast cell counts (brightfield)", CellCountsCV.class, null);
			var densityMapCommand = new DensityMapCommand(qupath);
			actionDensityMap = qupath.createImageDataAction(imageData -> densityMapCommand.run());
			
			var commandLoad = LoadResourceCommand.createLoadDensityMapCommand(qupath);
			actionDensityMapLoad = qupath.createImageDataAction(imageData -> commandLoad.run());
		}

	}
	
	
	
	@ActionMenu(value = {"Menu.Classify", "Menu.Classify.PixelClassification"})
	@SuppressWarnings("javadoc")
	public class PixelClassificationCommands {
		
		@ActionConfig("Action.Processing.Classify.loadPixelClassifier")
		public final Action actionLoadPixelClassifier;

		public final Action SEP = ActionTools.createSeparator();
		
		@ActionAccelerator("shortcut+shift+p")
		@ActionConfig("Action.Processing.Classify.trainPixelClassifier")
		public final Action actionPixelClassifier;
		
		@ActionConfig("Action.Processing.Classify.createThresholder")
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
	
	@ActionMenu(value = {"Menu.Classify", "Menu.Classify.ObjectClassification"})
	@SuppressWarnings("javadoc")
	public class ObjectClassificationCommands {
				
		@ActionConfig("Action.Processing.Classify.loadObjectClassifier")
		public final Action actionLoadObjectClassifier;

		public final Action SEP = ActionTools.createSeparator();

		@ActionAccelerator("shortcut+shift+d")
		@ActionConfig("Action.Processing.Classify.trainObjectClassifier")
		public final Action actionObjectClassifier;

		@ActionConfig("Action.Processing.Classify.createSingleMeasurementClassifier")
		public final Action actionMeasurement;

		@ActionConfig("Action.Processing.Classify.createCompositeClassifier")
		public final Action actionComposite;
				
		@ActionConfig("Action.Processing.Classify.setCellIntensityClassifications")
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
	@ActionMenu(value = {"Menu.Classify", "Menu.Classify.TrainingImages"})
	public class OtherCommands {
		
		@ActionConfig("Action.Processing.Classify.createRegionAnnotations")
		public final Action actionCreateRegions;

		@ActionConfig("Action.Processing.Classify.createTrainingImage")
		public final Action actionTrainingImage;

		@ActionConfig("Action.Processing.Classify.createChannelTrainingImages")
		public final Action actionChannelTraining;
		
		@ActionConfig("Action.Processing.Classify.splitTrainValidationTest")
		public final Action actionSplitProject;
						
		private OtherCommands(QuPathGUI qupath) {
			
			actionTrainingImage = qupath.createProjectAction(project -> promptToCreateSparseServer(qupath));
			
			var channelTraining = new CreateChannelTrainingImagesCommand(qupath);
			actionChannelTraining = qupath.createImageDataAction(imageData -> channelTraining.run());
			
			var splitProject = new SplitProjectTrainingCommand(qupath);
			actionSplitProject = qupath.createProjectAction(project -> splitProject.run());
			
			var createRegions = new CreateRegionAnnotationsCommand(qupath);
			actionCreateRegions = qupath.createImageDataAction(imageData -> createRegions.run());
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
			var wandTool = PathTools.createTool(new WandToolEventHandler(qupath), "Wand",
					IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.WAND_TOOL));
			logger.debug("Installing wand tool");
			Platform.runLater(() -> {
				KeyCodeCombination keyCodeCombination = new KeyCodeCombination(KeyCode.W);
				qupath.getToolManager().installTool(wandTool, keyCodeCombination);
				qupath.getToolManager().getToolAction(wandTool).setLongText(String.format(
        				"""
						(%s) Click and drag to draw with a wand tool.
						Adjust brightness/contrast or wand preferences to customize the sensitivity and behavior.
						""",
						keyCodeCombination.getDisplayText()
				));
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
        return QuPathResources.getString("Extension.Processing");
    }

    @Override
    public String getDescription() {
        return QuPathResources.getString("Extension.Processing.description");
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