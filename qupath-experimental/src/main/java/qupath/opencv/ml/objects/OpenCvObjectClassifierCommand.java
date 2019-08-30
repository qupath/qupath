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

package qupath.opencv.ml.objects;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.panels.classify.ClassifierBuilderPanel;


/**
 * Command used to create and show a suitable dialog box for interactive display of OpenCV classifiers.
 * <p>
 * This is intended as a replacement for 'Create detection classifier' in QuPath v0.1.2, supporting better 
 * classifier options and serialization.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCvObjectClassifierCommand implements PathCommand {
	
	final private static String name = "Create detection classifier";
	
	private QuPathGUI qupath;
	
	// TODO: Check use of static dialog
	private Stage dialog;
	private ClassifierBuilderPanel<PathObjectClassifier> panel;

	
	public OpenCvObjectClassifierCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			dialog.setTitle(name);
			
			BorderPane pane = new BorderPane();
			List<PathObjectClassifier> classifiers = OpenCVMLClassifier.createDefaultClassifiers();
			panel = new ClassifierBuilderPanel<>(qupath, classifiers, classifiers.get(0));
			pane.setCenter(panel.getPane());
			
			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToWidth(true);
			scrollPane.setFitToHeight(true);
			dialog.setScene(new Scene(scrollPane));
		}
		
		dialog.setOnCloseRequest(e -> {
			// If we don't have a classifier yet, just remove completely
			if (panel.getSelectedFeatures().isEmpty()) {
				resetPanel();
				return;
			}
			
			// If we have a classifier, give option to hide
			DialogButton button = DisplayHelpers.showYesNoCancelDialog("Classifier builder", "Retain classifier for later use?");
			if (button == DialogButton.CANCEL)
				e.consume();
			else if (button == DialogButton.NO) {
				resetPanel();
			}
		});
		
		dialog.show();
		dialog.setMinWidth(dialog.getWidth());
		if (dialog.getHeight() < javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()) {
			dialog.setMinHeight(dialog.getHeight()/2);
		}
//		if (dialog.getHeight() < javafx.stage.Screen.getPrimary().getVisualBounds().getHeight())
//			dialog.setResizable(false);
	}
	
	
	/**
	 * Handle cleanup whenever a dialog should be closed (and forgotten)
	 */
	private void resetPanel() {
		if (panel == null)
			return;
		qupath.removeImageDataChangeListener(panel);
		panel.setImageData(qupath.getImageData(), null);
		if (dialog != null)
			dialog.setOnCloseRequest(null);
		dialog = null;
		panel = null;
	}
	
	
}
