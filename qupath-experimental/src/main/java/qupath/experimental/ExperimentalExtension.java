package qupath.experimental;

import qupath.lib.ai.CreateRegionAnnotationsCommand;
import qupath.lib.ai.ExportTrainingRegionsCommand;
import qupath.lib.ai.SplitProjectTrainingCommand;
import qupath.lib.classifiers.gui.PixelClassifierApplyCommand;
import qupath.lib.classifiers.opencv.gui.PixelClassifierCommand;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifier;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifierDNN;
import qupath.lib.classifiers.pixel.PixelClassifiers;
import qupath.lib.classifiers.pixel.features.FeatureCalculators;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifier.class);
		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifierDNN.class);
    	FeatureCalculators.initialize();
    	
        QuPathGUI.addMenuItems(
                qupath.getMenu("Classify", true),
                QuPathGUI.createCommandAction(new PixelClassifierCommand(), "Pixel classifier (experimental)"),
                QuPathGUI.createCommandAction(new PixelClassifierApplyCommand(qupath), "Apply pixel classifier (experimental)")
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
