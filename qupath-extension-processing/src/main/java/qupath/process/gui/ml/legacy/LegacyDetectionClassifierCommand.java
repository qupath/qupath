/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.process.gui.ml.legacy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.opencv.classify.BayesClassifier;
import qupath.opencv.classify.BoostClassifier;
import qupath.opencv.classify.DTreesClassifier;
import qupath.opencv.classify.KNearestClassifier;
import qupath.opencv.classify.NeuralNetworksClassifier;
import qupath.opencv.classify.OpenCvClassifier;
import qupath.opencv.classify.RTreesClassifier;
import qupath.opencv.classify.SVMClassifier;


/**
 * Command used to create and show a suitable dialog box for interactive display of OpenCV classifiers.
 * 
 * @author Pete Bankhead
 *
 */
public class LegacyDetectionClassifierCommand implements Runnable {
	
	final private static String name = "Create detection classifier";
	
	private QuPathGUI qupath;
	
	// TODO: Check use of static dialog
	private Stage dialog;
	private ClassifierBuilderPane<OpenCvClassifier<?>> panel;

	/**
	 * Constructor.
	 * @param qupath QuPath instance where the command should be installed.
	 */
	public LegacyDetectionClassifierCommand(final QuPathGUI qupath) {
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
			RTreesClassifier defaultClassifier = new RTreesClassifier();
			List<OpenCvClassifier<?>> classifierList = 
					Arrays.asList(
					defaultClassifier, new DTreesClassifier(), new BoostClassifier(), new BayesClassifier(),
					new KNearestClassifier(), new SVMClassifier(), new NeuralNetworksClassifier());
			Collections.sort(classifierList, (c1, c2) -> c1.getName().compareTo(c2.getName()));
			panel = new ClassifierBuilderPane<>(qupath, classifierList, defaultClassifier);
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
			DialogButton button = Dialogs.showYesNoCancelDialog("Classifier builder", "Retain classifier for later use?");
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
		qupath.imageDataProperty().removeListener(panel);
		panel.setImageData(qupath.getImageData(), null);
		if (dialog != null)
			dialog.setOnCloseRequest(null);
		dialog = null;
		panel = null;
	}
	
	
	
	/**
	 * Create a dialog to load an (old-style) detection classifier.
	 * <p>
	 * Note: these classifiers are deprecated and will be removed in a later version.
	 * @param qupath the {@link QuPathGUI} instance to which the dialog relates
	 * @return a load detection classifier dialog
	 */
	@Deprecated
	public static Stage createLegacyLoadDetectionClassifierCommand(QuPathGUI qupath) {
		var dialog = new Stage();
		dialog.setTitle("Load detection classifier");
		dialog.initOwner(qupath.getStage());
		BorderPane pane = new BorderPane();
		pane.setCenter(new PathClassifierPane(qupath.viewerProperty()).getPane());
		pane.setPadding(new Insets(10, 10, 10, 10));
		dialog.setScene(new Scene(pane, 300, 400));
		return dialog;
	}
	
	
}
