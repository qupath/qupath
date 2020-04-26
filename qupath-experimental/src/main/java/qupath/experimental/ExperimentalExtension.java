package qupath.experimental;

import org.controlsfx.control.action.Action;

import qupath.experimental.commands.CellIntensityClassificationCommand;
import qupath.experimental.commands.CreateChannelTrainingImagesCommand;
import qupath.experimental.commands.CreateCompositeClassifierCommand;
import qupath.experimental.commands.CreateRegionAnnotationsCommand;
import qupath.experimental.commands.ObjectClassifierCommand;
import qupath.experimental.commands.ObjectClassifierLoadCommand;
import qupath.experimental.commands.PixelClassifierCommand;
import qupath.experimental.commands.PixelClassifierLoadCommand;
import qupath.experimental.commands.SimpleThresholdCommand;
import qupath.experimental.commands.SingleMeasurementClassificationCommand;
import qupath.experimental.commands.SplitProjectTrainingCommand;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOps;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
	static {
		ObjectClassifiers.ObjectClassifierTypeAdapterFactory.registerSubtype(OpenCVMLClassifier.class);
		
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(PixelClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureExtractors.getTypeAdapterFactory())
			.registerTypeAdapterFactory(ObjectClassifiers.getTypeAdapterFactory())
			.registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter());
	}
	
	
	@ActionMenu("Classify>Pixel classification>")
	@SuppressWarnings("javadoc")
	public class PixelClassificationCommands {
		
		@ActionMenu("Train pixel classifier")
		@ActionAccelerator("shortcut+shift+p")
		@ActionDescription("Train a pixel classifier. "
				+ "This can be used to quantify areas, or to generate or classify objects.")
		public final Action actionPixelClassifier;
		
		@ActionMenu("Create simple thresholder")
		@ActionDescription("Create a pixel classifier that simply applies a threshold to an image.")
		public final Action actionSimpleThreshold;

		@ActionMenu("Load pixel classifier")
		@ActionDescription("Load an existing pixel classifier. "
				+ "This can be used to apply the classifier to new images, but not to continue training.")
		public final Action actionLoadPixelClassifier;
				
		private PixelClassificationCommands(QuPathGUI qupath) {
			actionPixelClassifier = ActionTools.createAction(new PixelClassifierCommand(), "Train pixel classifier");
			actionLoadPixelClassifier = ActionTools.createAction(new PixelClassifierLoadCommand(qupath), "Load pixel classifier");
			actionSimpleThreshold = ActionTools.createAction(new SimpleThresholdCommand(qupath), "Create simple thresholder");
		}
		
	}
	
	@ActionMenu("Classify>Object classification>")
	@SuppressWarnings("javadoc")
	public class ObjectClassificationCommands {
				
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
		
		@ActionMenu("Load object classifier")
		@ActionDescription("Load an existing object classifier. "
				+ "This can be used to apply the classifier to new objects, but not to continue training.")
		public final Action actionLoadObjectClassifier;

		private ObjectClassificationCommands(QuPathGUI qupath) {
			actionLoadObjectClassifier = ActionTools.createAction(new ObjectClassifierLoadCommand(qupath));
			actionComposite = ActionTools.createAction(new CreateCompositeClassifierCommand(qupath));
			actionMeasurement = ActionTools.createAction(new SingleMeasurementClassificationCommand(qupath));
			actionIntensity = ActionTools.createAction(new CellIntensityClassificationCommand(qupath));
			actionObjectClassifier = ActionTools.createAction(new ObjectClassifierCommand(qupath));
		}
		
	}
	
	@SuppressWarnings("javadoc")
	public class OtherCommands {
		
		@ActionMenu("Analyze>Interactive image alignment")
		@ActionDescription("Experimental command to interactively align images using an Affine transform. "
				+ "This is currently not terribly useful in itself, but may be helpful as part of more complex scripting workflows.")
		public final Action actionInterativeAlignment;

		@ActionMenu("Classify>Training images>Duplicate channel training images")
		@ActionDescription("Duplicate an image in a project so that there is one duplicate for each channel of the image. "
				+ "\n\nThis can be used to train separate classifiers for different channels in multiplexed images, "
				+ "which are then merged to form a composite classifier.")
		public final Action actionChannelTraining;
		
		@ActionMenu("Classify>Training images>Create region annotations")
		@ActionDescription("Create annotations of fixed-size regions."
				+ "\n\nThis can be used to select representative regions of multiple images to train (usually pixel) classifier, "
				+ "in combination with 'Create combined training image'.")
		public final Action actionCreateRegions;

		@ActionMenu("Classify>Training images>Split project train/validation/test")
		@ActionDescription("Split images within a project into training, validation and test sets.")
		public final Action actionSplitProject;
				
//		@ActionMenu("Classify>Training images>Export training regions")
//		public final Action actionExportTraining;
		
		
		private OtherCommands(QuPathGUI qupath) {
			var channelTraining = new CreateChannelTrainingImagesCommand(qupath);
			actionChannelTraining = qupath.createImageDataAction(imageData -> channelTraining.run());
			
			var interactiveAlignment = new InteractiveImageAlignmentCommand(qupath);
			actionInterativeAlignment = qupath.createProjectAction(project -> interactiveAlignment.run());
			
			var splitProject = new SplitProjectTrainingCommand(qupath);
			actionSplitProject = qupath.createProjectAction(project -> splitProject.run());
			
			var createRegions = new CreateRegionAnnotationsCommand(qupath);
			actionCreateRegions = qupath.createImageDataAction(imageData -> createRegions.run());
			
//			var exportTraining = new ExportTrainingRegionsCommand(qupath);
//			actionExportTraining = qupath.createProjectAction(project -> exportTraining.run());
		}
		
	}
	
	
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	qupath.installActions(ActionTools.getAnnotatedActions(new PixelClassificationCommands(qupath)));
    	qupath.installActions(ActionTools.getAnnotatedActions(new ObjectClassificationCommands(qupath)));
    	qupath.installActions(ActionTools.getAnnotatedActions(new OtherCommands(qupath)));
    	
    	// This is needed for Gson serialization
    	new ImageOps();
    }

    @Override
    public String getName() {
        return "Experimental commands";
    }

    @Override
    public String getDescription() {
        return "New features that are still being developed or tested";
    }
}
