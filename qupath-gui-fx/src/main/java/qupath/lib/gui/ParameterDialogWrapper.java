/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui;

import java.util.Collection;
import java.util.Collections;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.plugins.PathInteractivePlugin;
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
class ParameterDialogWrapper<T> {

	private Stage dialog;
	private ParameterPanelFX panel;
	private WorkflowStep lastWorkflowStep;

	/**
	 * Constructor.
	 * @param plugin plugin for which this dialog should be shown
	 * @param params parameters to display
	 * @param pluginRunner the {@link PluginRunner} that may be used to run this plugin if necessary
	 */
	public ParameterDialogWrapper(final PathInteractivePlugin<T> plugin, final ParameterList params, final PluginRunner<T> pluginRunner) {
		dialog = createDialog(plugin, params, pluginRunner);
	}

	/**
	 * Show the dialog. This method returns immediately, allow the dialog to remain open without blocking.
	 */
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

	/**
	 * Get the {@link ParameterList} corresponding to the displayed parameters.
	 * @return
	 */
	public ParameterList getParameterList() {
		return panel.getParameters();
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
		btnRun.textProperty().bind(Bindings.createStringBinding(() -> {
			if (btnRun.isDisabled())
				return "Please wait...";
			else
				return "Run";
		}, btnRun.disabledProperty()));

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

//				// Strip off any of our extra parameters
//				params.removeParameter(KEY_REGIONS);

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
						boolean success = plugin.runPlugin(pluginRunner, ParameterList.convertToJson(params));
						WorkflowStep lastStepNew = pluginRunner.getImageData().getHistoryWorkflow().getLastStep();
						if (success && lastStep != lastStepNew)
							lastWorkflowStep = lastStepNew;
						else
							lastWorkflowStep = null;
					} catch (Exception e) {
						Dialogs.showErrorMessage("Plugin error", e);
					} catch (OutOfMemoryError e) {
						// This doesn't actually work...
						Dialogs.showErrorMessage("Out of memory error", "Out of memory - try to close other applications, or decrease the number of parallel processors in the QuPath preferences");
					} finally {
						Platform.runLater(() -> {
							QuPathGUI.getInstance().pluginRunningProperty().set(false);
							dialog.getScene().setCursor(Cursor.DEFAULT);
							label.setText(plugin.getLastResultsDescription());
							btnRun.setDisable(false);
						});
					}
				}

			};
			Thread t = new Thread(runnable, "Plugin thread");
			QuPathGUI.getInstance().pluginRunningProperty().set(true);
			t.start();
		});

		BorderPane pane = new BorderPane();
		ScrollPane scrollPane = new ScrollPane();
		label.setMaxWidth(Double.MAX_VALUE);
		scrollPane.setContent(panel.getPane());
		scrollPane.setFitToWidth(true);
		pane.setCenter(scrollPane);

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
		return GuiTools.promptForParentObjects(plugin.getName(), runner.getImageData(), includeSelected, plugin.getSupportedParentObjectClasses());
	}

}