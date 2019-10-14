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

package qupath.lib.gui.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.scriptable.SelectObjectsByClassCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.PathInteractivePlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.WorkflowStep;

/**
 * 
 * Wrapper used to display interactive plugins in a standardised way, creating a JavaFX GUI using the ParameterList.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class ParameterDialogWrapper<T> {

	final private static String KEY_REGIONS = "processRegions";

	Stage dialog;
	private ParameterPanelFX panel;
	private WorkflowStep lastWorkflowStep;

	public ParameterDialogWrapper(final PathInteractivePlugin<T> plugin, final ParameterList params, final PluginRunner<T> pluginRunner) {
		dialog = createDialog(plugin, params, pluginRunner);
	}

	public void showDialog() {
		// If we have no parameters, there is nothing to show... yet somehow we need to trigger the run button
		// (I realize this is exceedingly awkward...)
		if (panel.getParameters().getKeyValueParameters(false).isEmpty()) {
			for (var node : dialog.getScene().getRoot().getChildrenUnmodifiable()) {
				if (node instanceof Button && ((Button) node).getText().equals("Run")) {
					((Button)node).fire();
				}
			}
			return;
		}
		
		if (dialog.isShowing())
			dialog.toFront();
		dialog.show();
		double maxHeight = Screen.getPrimary().getBounds().getHeight() * 0.8;
		if (dialog.getHeight() > maxHeight) {
			dialog.setMaxHeight(maxHeight);
			dialog.centerOnScreen();			
		}
		dialog.toFront();

		dialog.requestFocus();
		Platform.runLater(() -> dialog.requestFocus());
	}

	public ParameterList getParameterList() {
		return panel.getParameters();
	}
	
	public Stage getDialog() {
		return dialog;
	}

	private Stage createDialog(final PathInteractivePlugin<T> plugin, final ParameterList params, final PluginRunner<T> pluginRunner) {
		panel = new ParameterPanelFX(params);
		panel.getPane().setPadding(new Insets(5, 5, 5, 5));

		//			panel.addParameterChangeListener(new ParameterChangeListener() {
		//
		//				@Override
		//				public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
		//					
		//					if (!plugin.requestLiveUpdate())
		//						return;
		//					
		//					PathObjectHierarchy hierarchy = pluginRunner.getHierarchy();
		//					if (hierarchy == null)
		//						return;
		//					
		//					Collection<Class<? extends PathObject>> supportedParents = plugin.getSupportedParentObjectClasses();
		//					
		//					PathObject selectedObject = pluginRunner.getSelectedObject();
		//					if (selectedObject == null) {
		//						if (supportedParents.contains(PathRootObject.class))
		//							Collections.singleton(hierarchy.getRootObject());
		//					} else if (supportedParents.contains(selectedObject.getClass()))
		//						Collections.singleton(selectedObject);
		//				}
		//				
		//			});


//		final Button btnRun = new Button("Run " + plugin.getName());
		final Button btnRun = new Button("Run");

		final Stage dialog = new Stage();
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			dialog.initOwner(qupath.getStage());
		dialog.setTitle(plugin.getName());

		final String emptyLabel = " \n";
		final Label label = new Label(emptyLabel);
		label.setStyle("-fx-font-weight: bold;");
		label.setPadding(new Insets(5, 5, 5, 5));
		label.setAlignment(Pos.CENTER);
		label.setTextAlignment(TextAlignment.CENTER);

		btnRun.setOnAction(e -> {

			// Check if we have the parent objects available to make this worthwhile
			if (plugin instanceof PathInteractivePlugin) {

				// Strip off any of our extra parameters
				params.removeParameter(KEY_REGIONS);

				boolean alwaysPrompt = plugin.alwaysPromptForObjects();
				ImageData<?> imageData = pluginRunner.getImageData();
				Collection<PathObject> selected = imageData == null ? Collections.emptyList() : imageData.getHierarchy().getSelectionModel().getSelectedObjects();
				Collection<? extends PathObject> parents = PathObjectTools.getSupportedObjects(selected, plugin.getSupportedParentObjectClasses());
				if (alwaysPrompt || parents == null || parents.isEmpty()) {
					if (!ParameterDialogWrapper.promptForParentObjects(pluginRunner, plugin, alwaysPrompt && !parents.isEmpty()))
						return;
				}
				//					promptForParentObjects
			}

			dialog.getScene().setCursor(Cursor.WAIT);
			btnRun.setDisable(true);
			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					try {
						WorkflowStep lastStep = pluginRunner.getImageData().getHistoryWorkflow().getLastStep();
						boolean success = plugin.runPlugin(pluginRunner, ParameterList.getParameterListJSON(params, "  "));
						WorkflowStep lastStepNew = pluginRunner.getImageData().getHistoryWorkflow().getLastStep();
						if (success && lastStep != lastStepNew)
							lastWorkflowStep = lastStepNew;
						else
							lastWorkflowStep = null;
					} catch (Exception e) {
						DisplayHelpers.showErrorMessage("Plugin error", e);
					} catch (OutOfMemoryError e) {
						// This doesn't actually work...
						DisplayHelpers.showErrorMessage("Out of memory error", "Out of memory - try to close other applications, or decrease the number of parallel processors in the QuPath preferences");
					} finally {
						Platform.runLater(() -> {
							dialog.getScene().setCursor(Cursor.DEFAULT);
							label.setText(plugin.getLastResultsDescription());
							btnRun.setDisable(false);
						});
					}
				}

			};
			Thread t = new Thread(runnable, "Plugin thread");
			t.start();
		});

		BorderPane pane = new BorderPane();
		ScrollPane scrollPane = new ScrollPane();
		BorderPane paneCenter = new BorderPane();
		paneCenter.setCenter(panel.getPane());
		paneCenter.setBottom(label);
		label.setMaxWidth(Double.MAX_VALUE);
//		scrollPane.setStyle("-fx-background-color:transparent;");
		scrollPane.setContent(panel.getPane());
		scrollPane.setFitToWidth(true);
		pane.setCenter(scrollPane);
//		pane.setCenter(panel.getPane());
		paneCenter.setPadding(new Insets(5, 5, 5, 5));

		btnRun.setMaxWidth(Double.MAX_VALUE);
		btnRun.setPadding(new Insets(5, 5, 5, 5));
		pane.setBottom(btnRun);

		Scene scene = new Scene(pane);
		dialog.setScene(scene);

		// Request focus, to make it easier to run from the keyboard
		btnRun.requestFocus();
		
		dialog.sizeToScene();

		return dialog;
	}
	
	/**
	 * Get the last WorkflowStep that was created from a successful run of this plugin.
	 * @return
	 */
	public WorkflowStep getLastWorkflowStep() {
		return lastWorkflowStep;
	}

	
	
	/**
	 * Get the parent objects to use when running the plugin, or null if no suitable parent objects are found.
	 * This involves prompting the user if multiple options are possible.
	 * 
	 * @param runner
	 * @param plugin
	 * @param includeSelected
	 * @return
	 */
	public static <T> boolean promptForParentObjects(final PluginRunner<T> runner, final PathInteractivePlugin<T> plugin, final boolean includeSelected) {
		return promptForParentObjects(runner, plugin, includeSelected, plugin.getSupportedParentObjectClasses());
	}
	
	/**
	 * Get the parent objects to use when running the plugin, or null if no suitable parent objects are found.
	 * This involves prompting the user if multiple options are possible.
	 * 
	 * @param runner
	 * @param plugin
	 * @param includeSelected
	 * @param supportedParents
	 * @return
	 */
	public static <T> boolean promptForParentObjects(final PluginRunner<T> runner, final PathPlugin<T> plugin, final boolean includeSelected, final Collection<Class<? extends PathObject>> supportedParents) {

		ImageData<T> imageData = runner.getImageData();
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return false;

		// Check what possible parent types are available
		Collection<PathObject> possibleParents = null;
		int nParents = 0;
		List<Class<? extends PathObject>> availableTypes = new ArrayList<>();
		for (Class<? extends PathObject> cls : supportedParents) {
			if (cls.equals(PathRootObject.class))
				continue;
			possibleParents = hierarchy.getObjects(possibleParents, cls);
			if (possibleParents.size() > nParents)
				availableTypes.add(cls);
			nParents = possibleParents.size();
		}

		// Create a map of potential choices
		LinkedHashMap<String, Class<? extends PathObject>> choices = new LinkedHashMap<>();
		for (Class<? extends PathObject> cls : availableTypes)
			choices.put(PathObjectTools.getSuitableName(cls, true), cls);
		if (supportedParents.contains(PathRootObject.class))
			choices.put("Entire image", PathRootObject.class);
		ArrayList<String> choiceList = new ArrayList<>(choices.keySet());
		
		// Add selected objects option, if required
		if (includeSelected)
			choiceList.add(0, "Selected objects");

		String name = plugin.getName();

		// Determine the currently-selected object
		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedObject();

		// If the currently-selected object is supported, use it as the parent
		if (!includeSelected && pathObjectSelected != null && !pathObjectSelected.isRootObject()) {
			if (supportedParents.contains(pathObjectSelected.getClass()))
				return true;
//			else {
//				String message = name + " does not support parent objects of type " + pathObjectSelected.getClass().getSimpleName();
//				DisplayHelpers.showErrorMessage(name + " error", message);
//				return false;
//			}
		}

		// If the root object is supported, and we don't have any of the other types, just run for the root object
		if (!includeSelected && availableTypes.isEmpty()) {
			if (supportedParents.contains(PathRootObject.class))
				return true;
			else {
				String message = name + " requires parent objects of one of the following types:";
				for (Class<? extends PathObject> cls : supportedParents)
					message += ("\n" + PathObjectTools.getSuitableName(cls, false));
				DisplayHelpers.showErrorMessage(name + " error", message);
				return false;
			}
		}

		// Prepare to prompt
		ParameterList paramsParents = new ParameterList();
		paramsParents.addChoiceParameter(KEY_REGIONS, "Process all", choiceList.get(0), choiceList);

		if (!DisplayHelpers.showParameterDialog("Process regions", paramsParents))
			return false;

		
		String choiceString = (String)paramsParents.getChoiceParameterValue(KEY_REGIONS);
		if (!"Selected objects".equals(choiceString))
			SelectObjectsByClassCommand.selectObjectsByClass(imageData, choices.get(choiceString));
		//			QP.selectObjectsByClass(hierarchy, choices.get(paramsParents.getChoiceParameterValue(InteractivePluginTools.KEY_REGIONS)));

		// Success!  Probably...
		return !hierarchy.getSelectionModel().noSelection();
	}

}