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

package qupath.lib.gui.panels.classify;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.ViewerManager;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.RunSavedClassifierWorkflowStep;


/**
 * Create a pane used to display and run a classifier loaded from a file.
 * 
 * @author Pete Bankhead
 *
 */
public class PathClassifierPanel {

	private final static Logger logger = LoggerFactory.getLogger(PathClassifierPanel.class);

	private BorderPane pane = new BorderPane();

	private ViewerManager<?> manager;

	private String pathClassifier = null;
	private PathObjectClassifier classifier = null;

	private Button btnLoad = new Button("Load classifier");
	private Button btnRun = new Button("Run classifier");
	private TextArea textClassifier = new TextArea();

	public PathClassifierPanel(final ViewerManager<?> manager) {

		this.manager = manager;

		btnLoad.setOnAction(e -> {
			File file = QuPathGUI.getDialogHelper(btnLoad.getScene().getWindow()).promptForFile("Load classifier", null, "Classifiers", new String[]{PathPrefs.getClassifierExtension()});
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


		GridPane paneButtons = PaneToolsFX.createColumnGridControls(
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
		QuPathViewer viewer = manager.getViewer();
		if (viewer == null)
			return;
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (classifier == null || imageData == null || !classifier.isValid())
			return;


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
