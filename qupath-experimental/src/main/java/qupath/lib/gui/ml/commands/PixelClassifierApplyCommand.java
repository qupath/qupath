package qupath.lib.gui.ml.commands;

import java.io.IOException;
import java.util.Collection;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.ml.PixelClassifierStatic;

/**
 * Command to apply a pre-trained pixel classifier to an image.
 * <p>
 * TODO: This command is unfinished!
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierApplyCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public PixelClassifierApplyCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("Apply pixel classifier", "You need an image to run this command!");
			return;
		}		
		
		var project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage("Apply pixel classifier", "You need a project open to run this command!");
			return;
		}
		
		try {
			Collection<String> names = project.getPixelClassifiers().getNames();
			if (names.isEmpty()) {
				DisplayHelpers.showErrorMessage("Apply pixel classifier", "No pixel classifiers were found in the current project!");
				return;
			}
			String name = DisplayHelpers.showChoiceDialog("Pixel classifier", "Choose pixel classifier", names, names.iterator().next());
			if (name == null)
				return;
			
			// Apply the classification
			var classifier = project.getPixelClassifiers().getResource(name);
			var classifierServer = PixelClassifierStatic.applyClassifier(project, imageData, classifier, name);
			
			// Display on the image
//			var classifierServer = new PixelClassificationImageServer(viewer.getImageData(), classifier);
			imageData.setProperty("PIXEL_LAYER", classifierServer);
			viewer.repaint();
			
//			viewer.getCustomOverlayLayers().removeIf(v -> v instanceof PixelClassificationOverlay);
//			var classifierServer = new PixelClassificationImageServer(viewer.getImageData(), classifier);
//			var overlay = new PixelClassificationOverlay(viewer, classifierServer);
//			overlay.setLivePrediction(true);
//			viewer.getCustomOverlayLayers().add(overlay);
			
		} catch (IOException e) {
			DisplayHelpers.showErrorMessage("Apply pixel classifier", e);
		}
		
	}
	

}
