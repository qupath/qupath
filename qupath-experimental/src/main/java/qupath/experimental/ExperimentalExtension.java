package qupath.experimental;

import qupath.lib.ai.CreateRegionAnnotationsCommand;
import qupath.lib.ai.ExportTrainingRegionsCommand;
import qupath.lib.ai.SplitProjectTrainingCommand;
import qupath.lib.classifiers.gui.OpenCvClassifierCommand2;
import qupath.lib.classifiers.opencv.gui.PixelClassifierCommand;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
        QuPathGUI.addMenuItems(
                qupath.getMenu("Classify", true),
                QuPathGUI.createCommandAction(new PixelClassifierCommand(), "Pixel classifier (updated)"),
                QuPathGUI.createCommandAction(new OpenCvClassifierCommand2(qupath), "Object classifier (experimental)")
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
