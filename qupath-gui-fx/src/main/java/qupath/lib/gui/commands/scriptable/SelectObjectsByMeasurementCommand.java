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

package qupath.lib.gui.commands.scriptable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;


/**
 * Select objects according to a specified predicate.
 * 
 * This code is rather experimental; one day it should be replaced by a more sophisticated expression parser.
 * 
 * @author Pete Bankhead
 *
 */
public class SelectObjectsByMeasurementCommand implements PathCommand {
	
	public final static Logger logger = LoggerFactory.getLogger(SelectObjectsByMeasurementCommand.class);
	
	private QuPathGUI qupath;
	private Stage dialog;
	
	private int lastCaretPos = 0;
	
	private ObservableList<String> features = FXCollections.observableArrayList();
//	private ParameterList params = new ParameterList().addStringParameter("keyPredicate", "Predicate", "");
	
	
	public SelectObjectsByMeasurementCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	@Override
	public void run() {
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null)
			return;
		
//		if (!DisplayHelpers.showParameterDialog("Select objects", params))
//			return;
		
		// Update the feature list to represent everything we've got
		features.setAll(PathClassifierTools.getAvailableFeatures(imageData.getHierarchy().getFlattenedObjectList(null)));
		
		if (dialog == null) {
			createDialog();
		}
		
		dialog.show();
		
//		selectObjectByScriptPredicate(imageData, params.getStringParameterValue("keyPredicate"));
	}
	
	
	private void createDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		
		GridPane paneText = new GridPane();

		Label labelInstructions = new Label("Build a command to select items, e.g.\nNucleus/Cell area ratio > 0.5\nNucleus: Area > 50 AND Nucleus: DAB OD mean > 0.2");
		paneText.add(labelInstructions, 0, 0, 2, 1);
		GridPane.setHgrow(labelInstructions, Priority.ALWAYS);
		labelInstructions.setContentDisplay(ContentDisplay.CENTER);
		labelInstructions.setAlignment(Pos.CENTER);
		labelInstructions.setTextAlignment(TextAlignment.CENTER);
		labelInstructions.setMaxWidth(Double.MAX_VALUE);

		ListView<String> listView = new ListView<>();
		listView.setItems(features);
		listView.setTooltip(new Tooltip("Double-click a measurement name to add it to the current command"));
		TitledPane titledList = new TitledPane("Available measurements", listView);
		titledList.setCollapsible(false);
		paneText.add(titledList, 0, 1, 2, 1);
		GridPane.setHgrow(titledList, Priority.ALWAYS);
		GridPane.setVgrow(titledList, Priority.ALWAYS);
		
		TextField tf = new TextField();
		tf.setPrefColumnCount(32);
		Tooltip tooltip = new Tooltip("Enter object selection command based on measurements");
		Label label = new Label("Select");
		label.setLabelFor(tf);
		label.setMinWidth(label.getPrefWidth());
		label.setTooltip(tooltip);
		tf.setTooltip(tooltip);
		paneText.add(label, 0, 2);
		paneText.add(tf, 1, 2);
		GridPane.setHgrow(tf, Priority.ALWAYS);
		
		// When focus is lost, the caret gets reset... so make sure it's kept
		lastCaretPos = 0;
		tf.focusedProperty().addListener((v, o, n) -> lastCaretPos = tf.getCaretPosition());
		
		// Whenever a feature is double-clicked, enter it
		listView.setOnMouseClicked(e-> {
			if (e.getClickCount() > 1) {
				String feature = listView.getSelectionModel().getSelectedItem() + " ";
				if (feature != null) {
					if (tf.getSelectedText() != null && tf.getSelectedText().length() > 0) {
						tf.replaceSelection(feature);
					} else
						tf.insertText(Math.max(0, lastCaretPos), feature);
//						tf.insertText(tf.getCaretPosition(), feature);
					tf.requestFocus();
					tf.selectPositionCaret(lastCaretPos + feature.length());
				}
			}
		});
		
		Label labelResults = new Label();
		labelResults.setContentDisplay(ContentDisplay.CENTER);
		labelResults.setAlignment(Pos.CENTER);
		labelResults.setMaxWidth(Double.MAX_VALUE);
		paneText.add(labelResults, 0, 3, 2, 1);
		GridPane.setHgrow(labelResults, Priority.ALWAYS);
		Paint colorStandard = labelResults.getTextFill();
		
		Button btnRun = new Button("Run");
		btnRun.setOnAction(event -> {
			try {
				ImageData<?> imageData = qupath.getImageData();
				String command = tf.getText().trim();
				QP.selectObjectsByMeasurement(imageData, command);
				int n = imageData.getHierarchy().getSelectionModel().getSelectedObjects().size();
				labelResults.setTextFill(colorStandard);
				if (n == 1)
					labelResults.setText("1 object selected");
				else
					labelResults.setText(n + " objects selected");
				
				// Log in workflow
				String stepName = "Select objects by measurements";
				WorkflowStep newStep = new DefaultScriptableWorkflowStep(stepName, "selectObjectsByMeasurement(\"" + command + "\")");
				WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
				if (lastStep != null && stepName.equals(lastStep.getName())) {
					imageData.getHistoryWorkflow().replaceLastStep(newStep);
				} else
					imageData.getHistoryWorkflow().addStep(newStep);
			} catch (Exception e) {
//				DisplayHelpers.showErrorMessage("Select objects", e.getMessage());
				labelResults.setTextFill(Color.RED);
				labelResults.setText("Error: " + e.getMessage());
			}
		});
		GridPane.setHgrow(btnRun, Priority.ALWAYS);
		paneText.add(btnRun, 0, 4, 2, 1);
		btnRun.prefWidthProperty().bind(paneText.widthProperty());
		
//		BorderPane pane = new BorderPane();
//		pane.setCenter(paneText);
//		pane.setBottom(btnRun);
		
		paneText.setHgap(5);
		paneText.setVgap(10);
		paneText.setPadding(new Insets(10, 10, 10, 10));

		Scene scene = new Scene(paneText);
		dialog.setTitle("Select objects by measurement");
		dialog.setScene(scene);
		dialog.sizeToScene();
//		dialog.setResizable(false);
	}
	

}
