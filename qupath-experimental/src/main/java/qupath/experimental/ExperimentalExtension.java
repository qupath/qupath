package qupath.experimental;

import org.bytedeco.openblas.global.openblas;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import qupath.experimental.commands.CellIntensityClassificationCommand;
import qupath.experimental.commands.CreateChannelTrainingImagesCommand;
import qupath.experimental.commands.CreateCompositeClassifierCommand;
import qupath.experimental.commands.CreateRegionAnnotationsCommand;
import qupath.experimental.commands.ExportTrainingRegionsCommand;
import qupath.experimental.commands.ObjectClassifierCommand;
import qupath.experimental.commands.ObjectClassifierLoadCommand;
import qupath.experimental.commands.PixelClassifierCommand;
import qupath.experimental.commands.PixelClassifierLoadCommand;
import qupath.experimental.commands.SimpleThresholdCommand;
import qupath.experimental.commands.SingleMeasurementClassificationCommand;
import qupath.experimental.commands.SplitProjectTrainingCommand;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.features.FeatureCalculators;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
	static {
		ObjectClassifiers.ObjectClassifierTypeAdapterFactory.registerSubtype(OpenCVMLClassifier.class);
		
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(PixelClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureCalculators.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureExtractors.getTypeAdapterFactory())
			.registerTypeAdapterFactory(ObjectClassifiers.getTypeAdapterFactory())
			.registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter());
	}
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
    	// TODO: Check if openblas multithreading continues to have trouble with Mac/Linux
    	if (!GeneralTools.isWindows())
    		openblas.blas_set_num_threads(1);
    	
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifier.class);
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifierDNN.class);
    	FeatureCalculators.initialize();
    	
    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Pixel classification", true),
                ActionTools.createAction(new PixelClassifierCommand(), "Train pixel classifier (experimental)", null, new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                ActionTools.createAction(new PixelClassifierLoadCommand(qupath), "Load pixel classifier (experimental)"),
                ActionTools.createAction(new SimpleThresholdCommand(qupath), "Create simple thresholder (experimental)")
        );

    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Object classification", true),
                ActionTools.createAction(new ObjectClassifierLoadCommand(qupath), "Load object classifier (New!)"),
                ActionTools.createAction(new CreateCompositeClassifierCommand(qupath), "Create composite object classifier (New!)"),
                ActionTools.createAction(new SingleMeasurementClassificationCommand(qupath), "Create single measurement classifier (New!)"),
                ActionTools.createAction(new CellIntensityClassificationCommand(qupath), "Set cell intensity classifications (New!)"),
                ActionTools.createAction(new ObjectClassifierCommand(qupath), "Train object classifier (New!)", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN))
                );

    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Extras", true),
                ActionTools.createAction(new CreateChannelTrainingImagesCommand(qupath), "Duplicate channel training images")
                );

    	
        MenuTools.addMenuItems(
                qupath.getMenu("Analyze", true),
                ActionTools.createAction(new InteractiveImageAlignmentCommand(qupath), "Interactive image alignment (experimental)")
        );
        
    	MenuTools.addMenuItems(
				qupath.getMenu("Extensions>AI", true),
				ActionTools.createAction(new SplitProjectTrainingCommand(qupath), "Split project train/validation/test"),
				ActionTools.createAction(new CreateRegionAnnotationsCommand(qupath), "Create region annotations"),
				ActionTools.createAction(new ExportTrainingRegionsCommand(qupath), "Export training regions")
				);

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
