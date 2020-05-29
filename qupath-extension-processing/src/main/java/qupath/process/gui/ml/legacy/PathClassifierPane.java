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

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.workflow.RunSavedClassifierWorkflowStep;


/**
 * Create a pane used to display and run an (old-style) detection classifier loaded from a file.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class PathClassifierPane {

	private final static Logger logger = LoggerFactory.getLogger(PathClassifierPane.class);

	private BorderPane pane = new BorderPane();

	private ObservableValue<? extends QuPathViewer> viewerValue;

	private String pathClassifier = null;
	private PathObjectClassifier classifier = null;

	private Button btnLoad = new Button("Load classifier");
	private Button btnRun = new Button("Run classifier");
	private TextArea textClassifier = new TextArea();

	/**
	 * Constructor.
	 * @param viewerValue observable value representing the active viewer
	 */
	public PathClassifierPane(final ObservableValue<? extends QuPathViewer> viewerValue) {

		this.viewerValue = viewerValue;

		btnLoad.setOnAction(e -> {
			File file = Dialogs.getChooser(btnLoad.getScene().getWindow()).promptForFile("Load classifier", null, "Classifiers", new String[]{ClassifierBuilderPane.extPathClassifier});
			if (file == null)
				return;

			PathObjectClassifier classifierLoading = PathClassifierTools.loadClassifier(file);
			if (classifierLoading == null)
				return;
			if (!classifierLoading.isValid()) {
				logger.error("Classifier is invalid!");
				return;
			}
			classifier = classifierLoading;
			runClassifier();
		});


		btnRun.setOnAction(e -> {
			if (classifier == null) {
				logger.error("No classifier to run!");
				return;
			}
			runClassifier();
		});


		GridPane paneButtons = PaneTools.createColumnGridControls(
				btnLoad,
				btnRun
				);
		pane.setTop(paneButtons);

		BorderPane panelDetails = new BorderPane();
		//		panelDetails.setBorder(BorderFactory.createTitledBorder("Classifier description"));
		textClassifier.setEditable(false);
		panelDetails.setCenter(textClassifier);
		pane.setCenter(panelDetails);
	}



	void runClassifier() {
		QuPathViewer viewer = viewerValue.getValue();
		if (viewer == null)
			return;
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (classifier == null || imageData == null || !classifier.isValid())
			return;

		Collection<PathObject> pathObjects = imageData.getHierarchy().getDetectionObjects();
		var availableFeatures = PathClassifierTools.getAvailableFeatures(pathObjects);
		var requiredFeatures = classifier.getRequiredMeasurements();
		String missingFeatures = requiredFeatures.stream()
										.filter(p -> !availableFeatures.contains(p))
										.collect(Collectors.joining("\n\t"));
		if (!missingFeatures.isEmpty())
			logger.warn("Detection objects are missing the following feature(s):\n\t" + missingFeatures +  "\nWill proceed with classification anyway..");
		
		// TODO: Set cursor

		//		QuPathGUI qupath = manager instanceof QuPathGUI ? (QuPathGUI)manager : null;
		//		Cursor cursor = SwingUtilities.getRoot(viewer).getCursor();
		//		SwingUtilities.getRoot(viewer).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			PathClassifierTools.runClassifier(imageData.getHierarchy(), classifier);

			// Log the classifier
			if (pathClassifier != null) {
				imageData.getHistoryWorkflow().addStep(new RunSavedClassifierWorkflowStep(pathClassifier));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			//			SwingUtilities.getRoot(viewer).setCursor(cursor);
			// Update displayed list - names may have changed - and classifier summary
			updateClassifierSummary();
		}
	}

	/**
	 * Get the pane, which may be added to a scene for display.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}



	void updateClassifierSummary() {
		if (classifier == null) {
			textClassifier.setText("No classifier available!");
			return;
		}
		StringBuilder sb = new StringBuilder();
		if (!classifier.isValid()) {
			sb.append("Classifier is invalid!\n\n");
		}
		long timestamp = classifier.getLastModifiedTimestamp();
		if (timestamp > 0) {
			SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss"); 
			Date date = new Date(timestamp);
			sb.append("Last modified at ").append(formatter.format(date)).append("\n\n");
		}
		sb.append(classifier.getDescription());
		textClassifier.setText(sb.toString());
	}



}
