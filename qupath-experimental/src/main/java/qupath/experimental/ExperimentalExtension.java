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
import qupath.experimental.commands.SplitProjectTrainingCommand;
import qupath.experimental.commands.SvgExportCommand;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.common.GeneralTools;
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
                qupath.getMenu("File>Export images...", true),
                QuPathGUI.createCommandAction(new SvgExportCommand(qupath, SvgExportCommand.SvgExportType.SELECTED_REGION),
                		"Rendered SVG")
        );
    	MenuTools.addMenuItems(
                qupath.getMenu("File>Export snapshot...", true),
                QuPathGUI.createCommandAction(new SvgExportCommand(qupath, SvgExportCommand.SvgExportType.VIEWER_SNAPSHOT),
                		"Current viewer content (SVG)")
        );
    	
    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Pixel classification", true),
                QuPathGUI.createCommandAction(new PixelClassifierCommand(), "Train pixel classifier (experimental)", null, new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                QuPathGUI.createCommandAction(new PixelClassifierLoadCommand(qupath), "Load pixel classifier (experimental)"),
                QuPathGUI.createCommandAction(new SimpleThresholdCommand(qupath), "Create simple thresholder (experimental)")
        );

    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Object classification", true),
                QuPathGUI.createCommandAction(new ObjectClassifierLoadCommand(qupath), "Load object classifier (experimental)"),
                QuPathGUI.createCommandAction(new CreateCompositeClassifierCommand(qupath), "Create composite object classifier (experimental)"),
                QuPathGUI.createCommandAction(new CellIntensityClassificationCommand(qupath), "Set cell intensity classifications (experimental)"),
                QuPathGUI.createCommandAction(new ObjectClassifierCommand(qupath), "Train detection classifier (experimental)", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN))
                );

    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Extras", true),
                QuPathGUI.createCommandAction(new CreateChannelTrainingImagesCommand(qupath), "Duplicate channel training images")
                );

    	
        MenuTools.addMenuItems(
                qupath.getMenu("Analyze", true),
                QuPathGUI.createCommandAction(new InteractiveImageAlignmentCommand(qupath), "Interactive image alignment (experimental)")
        );
        
    	MenuTools.addMenuItems(
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
