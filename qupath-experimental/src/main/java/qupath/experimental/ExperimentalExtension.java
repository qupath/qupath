package qupath.experimental;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.opencv.ml.pixel.features.ColorTransforms;
import qupath.lib.gui.ml.commands.CreateRegionAnnotationsCommand;
import qupath.lib.gui.ml.commands.ExportTrainingRegionsCommand;
import qupath.lib.gui.ml.commands.PixelClassifierLoadCommand;
import qupath.lib.gui.ml.commands.PixelClassifierCommand;
import qupath.lib.gui.ml.commands.SimpleThresholdCommand;
import qupath.lib.gui.ml.commands.SplitProjectTrainingCommand;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.features.FeatureCalculators;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
	static {
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(PixelClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureCalculators.getTypeAdapterFactory())
			.registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter());
	}
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifier.class);
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifierDNN.class);
    	FeatureCalculators.initialize();
    	
        QuPathGUI.addMenuItems(
                qupath.getMenu("Classify>Pixel classification", true),
                QuPathGUI.createCommandAction(new PixelClassifierCommand(), "Train pixel classifier (experimental)", null, new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                QuPathGUI.createCommandAction(new PixelClassifierLoadCommand(qupath), "Load pixel classifier (experimental)"),
                QuPathGUI.createCommandAction(new SimpleThresholdCommand(qupath), "Create simple thresholder (experimental)")
//                QuPathGUI.createCommandAction(new OpenCvClassifierCommand2(qupath), "Object classifier (experimental)")
        );
        QuPathGUI.addMenuItems(
                qupath.getMenu("Analyze", true),
                QuPathGUI.createCommandAction(new InteractiveImageAlignmentCommand(qupath), "Interactive image alignment (experimental)")
        );
        
		QuPathGUI.addMenuItems(
				qupath.getMenu("Extensions>AI", true),
				QuPathGUI.createCommandAction(new SplitProjectTrainingCommand(qupath), "Split project train/validation/test"),
				QuPathGUI.createCommandAction(new CreateRegionAnnotationsCommand(qupath), "Create region annotations"),
				QuPathGUI.createCommandAction(new ExportTrainingRegionsCommand(qupath), "Export training regions")
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
