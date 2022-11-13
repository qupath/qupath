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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;
import qupath.lib.plugins.workflow.SimplePluginWorkflowStep;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowListener;
import qupath.lib.plugins.workflow.WorkflowStep;


/**
 * Show logged commands, and optionally generate a script.
 * 
 * @author Pete Bankhead
 *
 */
public class WorkflowCommandLogView implements ChangeListener<ImageData<BufferedImage>>, WorkflowListener {

	private static final Logger logger = LoggerFactory.getLogger(WorkflowCommandLogView.class);
	
	private QuPathGUI qupath;
	
	private BorderPane pane;
	
	private final boolean isStaticWorkflow;
	
	private ObjectProperty<Workflow> workflowProperty = new SimpleObjectProperty<>();
	private ListView<WorkflowStep> list = new ListView<>();
	
	private TableView<KeyValue<Object>> table = new TableView<>();
	
	private final KeyCodeCombination copyCombination = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
	
	/**
	 * Construct a view to display the workflow for the currently-active ImageData within a running QuPath instance.
	 * 
	 * @param qupath
	 */
	public WorkflowCommandLogView(final QuPathGUI qupath) {
		this.qupath = qupath;
//		this.viewer = qupath.getViewer();
//		viewer.addViewerListener(this);
		qupath.imageDataProperty().addListener(this);
		ImageData<BufferedImage> imageData = qupath.getImageData();
		isStaticWorkflow = false;
		if (imageData != null) {
			var workflow = imageData.getHistoryWorkflow();
			workflow.addWorkflowListener(this);
			workflowProperty.set(workflow);
			workflowUpdated(workflow);
		}
		list.setOnKeyPressed(e -> {
			if (copyCombination.match(e)) {
				copyScriptToClipboard(getSelectedIndices());
				e.consume();
			}
		});
	}

	/**
	 * Construct a view displaying a static workflow (i.e. not dependent on any particular ImageData).
	 * 
	 * @param qupath
	 * @param workflow
	 */
	public WorkflowCommandLogView(final QuPathGUI qupath, final Workflow workflow) {
		this.qupath = qupath;
		Objects.requireNonNull(workflow);
		workflowProperty.set(workflow);
		workflow.addWorkflowListener(this);
		this.list.getItems().addAll(workflow.getSteps());
		this.isStaticWorkflow = true;
	}
	
	/**
	 * Get the pane to add to a scene.
	 * @return
	 */
	public Pane getPane() {
		if (pane == null)
			pane = createPane();
		return pane;
	}
	
	protected BorderPane createPane() {
		BorderPane pane = new BorderPane();
		TableColumn<KeyValue<Object>, String> col1 = new TableColumn<>("Parameter");
		col1.setCellValueFactory(c -> c.getValue().keyProperty());
		TableColumn<KeyValue<Object>, Object> col2 = new TableColumn<>("Value");
		col2.setCellValueFactory(c -> c.getValue().valueProperty());
		col2.setCellFactory(t -> new ParameterTableCell<>());
		table.getColumns().add(col1);
		table.getColumns().add(col2);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
				
		SplitPane splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.VERTICAL);
		splitPane.getItems().addAll(list, table);
		
		// Allow multiple selections for static model
		list.getSelectionModel().setSelectionMode(isStaticWorkflow ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
		
		list.getSelectionModel().selectedItemProperty().addListener((e, f, g) -> {
			WorkflowStep step = list.getSelectionModel().getSelectedItem();
			populateList(table.getItems(), step);
		});
		
		final ContextMenu contextMenu = new ContextMenu();
		MenuItem miCopyCommand = new MenuItem("Copy command" + (isStaticWorkflow ? "s" : ""));
		miCopyCommand.setOnAction(e -> {
			List<Integer> indices = list.getSelectionModel().getSelectedIndices();
			if (indices == null || indices.isEmpty())
				return;
			copyScriptToClipboard(indices);
		});
		miCopyCommand.disableProperty().bind(workflowProperty.isNull());
		contextMenu.getItems().setAll(miCopyCommand);
		
		if (isStaticWorkflow) {
			MenuItem miRemoveSelected = new MenuItem("Remove selected items");
			miRemoveSelected.setOnAction(e -> {
				var steps = getSelectedIndices();
				if (steps == null || steps.isEmpty())
					return;
				String message = steps.size() == 1 ? "Remove workflow step?" : "Remove " + steps.size() + " workflow steps?";
				if (!Dialogs.showYesNoDialog("Remove workflow steps", message))
					return;
				Collections.sort(steps);
				for (int i = steps.size()-1; i >= 0; i--)
					getWorkflow().removeStep(steps.get(i));
//				workflow.removeSteps(steps);
			});
			miRemoveSelected.disableProperty().bind(workflowProperty.isNull());
			
			MenuItem miMoveUp = new MenuItem("Move up");
			miMoveUp.setOnAction(e -> {
				var indices = getSelectedIndices();
				if (indices == null || indices.isEmpty() || indices.get(0) <= 0)
					return;
				var workflow = getWorkflow();
				List<WorkflowStep> steps = new ArrayList<>(workflow.getSteps());
				WorkflowStep[] stepsRemoved = new WorkflowStep[indices.size()];
				workflow.removeSteps(steps);
				int[] newIndices = new int[indices.size()];
				for (int i = indices.size()-1; i >= 0; i--) {
					int ind = indices.get(i);
					int indNew = ind-1;
					newIndices[i] = indNew;
					stepsRemoved[i] = steps.remove(ind);
				}
				for (int i = 0; i < indices.size(); i++) {
					steps.add(newIndices[i], stepsRemoved[i]);
				}
				workflow.addSteps(steps);
				list.getSelectionModel().clearSelection();
				list.getSelectionModel().selectIndices(newIndices[0], newIndices);
			});
			miMoveUp.disableProperty().bind(workflowProperty.isNull());
			
			MenuItem miMoveDown = new MenuItem("Move down");
			miMoveDown.setOnAction(e -> {
				var indices = getSelectedIndices();
				var workflow = getWorkflow();
				if (indices == null || indices.isEmpty() || indices.get(indices.size()-1) >= workflow.size()-1)
					return;
				list.getSelectionModel().clearSelection();
				Collections.sort(indices);
				List<WorkflowStep> steps = new ArrayList<>(workflow.getSteps());
				WorkflowStep[] stepsRemoved = new WorkflowStep[indices.size()];
				workflow.removeSteps(steps);
				int[] newIndices = new int[indices.size()];
				for (int i = indices.size()-1; i >= 0; i--) {
					int ind = indices.get(i);
					int indNew = ind+1;
					newIndices[i] = indNew;
					stepsRemoved[i] = steps.remove(ind);
				}
				for (int i = 0; i < indices.size(); i++) {
					steps.add(newIndices[i], stepsRemoved[i]);
				}
				workflow.addSteps(steps);
				list.getSelectionModel().select(newIndices[0]);
				list.getSelectionModel().selectIndices(newIndices[0], newIndices);
				
//				int ind = list.getSelectionModel().getSelectedIndex();
//				if (ind < 0 || ind >= workflow.size()-1)
//					return;
//				List<WorkflowStep> steps = new ArrayList<>(workflow.getSteps());
//				WorkflowStep step = steps.remove(ind);
//				steps.add(ind+1, step);
//				workflow.removeSteps(steps);
//				workflow.addSteps(steps);
//				list.getSelectionModel().select(step);
			});
			miMoveDown.disableProperty().bind(workflowProperty.isNull());
			contextMenu.getItems().addAll(
					new SeparatorMenuItem(),
					miMoveUp,
					miMoveDown,
					new SeparatorMenuItem(),
					miRemoveSelected
					);
		}
		
		list.setCellFactory(new Callback<ListView<WorkflowStep>, ListCell<WorkflowStep>>(){

			@Override
			public ListCell<WorkflowStep> call(ListView<WorkflowStep> p) {
				ListCell<WorkflowStep> cell = new ListCell<>(){
					@Override
					protected void updateItem(WorkflowStep value, boolean bln) {
						super.updateItem(value, bln);
						if (value instanceof WorkflowStep)
							setText(value.getName());
						else if (value == null)
							setText(null);
						else
							setText(value.toString());
						
						setContextMenu(contextMenu);

						setOnMouseClicked(e -> {
							// Only handle double clicks
							if (!e.isPopupTrigger() && e.getClickCount() == 2)
								runWorkflowStepInteractively(qupath, value);
						});
						
						setOnKeyPressed(e -> {
							if (copyCombination.match(e)) {
								copyScriptToClipboard(getSelectedIndices());
								e.consume();
							}
						});
					}
				};
				return cell;
			}
		});
		
		
		pane.setCenter(splitPane);
		
		Button btnCreateScript = new Button("Create script");
		btnCreateScript.setMaxWidth(Double.MAX_VALUE);
		btnCreateScript.setOnAction(e -> showScript());
		btnCreateScript.disableProperty().bind(workflowProperty.isNull());

		Button btnCreateWorkflow = null;
		if (!isStaticWorkflow) {
			btnCreateWorkflow = new Button("Create workflow");
			btnCreateWorkflow.setMaxWidth(Double.MAX_VALUE);
			btnCreateWorkflow.setOnAction(e -> {
				var workflow = getWorkflow();
				if (workflow == null)
					return;
				Stage stage = new Stage();
				stage.initOwner(qupath.getStage());
				stage.setTitle("Workflow");
				Workflow workflowNew = new Workflow();
				workflowNew.addSteps(workflow.getSteps());
				stage.setScene(new Scene(new WorkflowCommandLogView(qupath, workflowNew).getPane(), 400, 600));
				stage.show();
			});
			btnCreateWorkflow.disableProperty().bind(workflowProperty.isNull());
			pane.setBottom(PaneTools.createColumnGridControls(btnCreateWorkflow, btnCreateScript));
		} else
			pane.setBottom(btnCreateScript);
		
//		Button btnGenerateScript = new Button("Generate script");
//		btnGenerateScript.setMaxWidth(Double.MAX_VALUE);
//		btnGenerateScript.setOnAction(e -> showScript());
//		pane.setBottom(btnGenerateScript);
		return pane;
	}
	
	private List<Integer> getSelectedIndices() {
		var workflow = getWorkflow();
		if (workflow == null)
			return null;
		return new ArrayList<>(list.getSelectionModel().getSelectedIndices());
	}
	
	
	private Workflow getWorkflow() {
		var workflow = workflowProperty.get();
		if (workflow == null) {
			logger.error("Workflow is null!");
		}
		return workflow;
	}
	
	
	private void copyScriptToClipboard(List<Integer> indices) {
		var steps = getWorkflow().getSteps();
		var script = indices.stream().map(index -> ((ScriptableWorkflowStep)steps.get(index)).getScript()).collect(Collectors.joining(System.lineSeparator()));
		ClipboardContent content = new ClipboardContent();
		content.putString(script);
		Clipboard.getSystemClipboard().setContent(content);
	}
	
	static class ParameterTableCell<S, T> extends TableCell<S, T> {
		
		private Tooltip tooltip = new Tooltip();
		
		ParameterTableCell() {
			super();
			setWrapText(true);
			setMaxHeight(Double.MAX_VALUE);
		}
		
		@Override
		public void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setText(null);
				setTooltip(null);
				return;
			}
//			String text;
//			if (item instanceof Number)
//				text = GeneralTools.formatNumber(((Number) item).doubleValue(), 5);
//			else
			String text = Objects.toString(item);
			setText(text);
			tooltip.setText(text);
			setTooltip(tooltip);
		}
		
	}
	
	
	/**
	 * Launch a plugin dialog for a specified WorkflowStep.
	 * 
	 * TODO: Run any scriptable step
	 * 
	 * @param qupath
	 * @param step
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void runWorkflowStepInteractively(final QuPathGUI qupath, final WorkflowStep step) {
		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError("Run workflow step");
			return;
		}
		if (step instanceof SimplePluginWorkflowStep) {
			if (step instanceof SimplePluginWorkflowStep) {
				SimplePluginWorkflowStep pluginStep = (SimplePluginWorkflowStep)step;
				String pluginClassName = pluginStep.getPluginClass();
				PathPlugin<BufferedImage> plugin = null;
				try {
					Class<? extends PathPlugin> cls = (Class<? extends PathPlugin>)Class.forName(pluginClassName);
					plugin = qupath.createPlugin(cls);
					
					Map<String, ?> parameterMap = pluginStep.getParameterMap();
					String arg = null;
					if (parameterMap != null && !parameterMap.isEmpty()) {
						arg = ParameterList.convertToJson(parameterMap);
					}
					
					qupath.runPlugin(plugin, arg, true);
					
				} catch (ClassNotFoundException e1) {
					Dialogs.showErrorNotification("Plugin class not found", "No plugin class found with name " + pluginClassName);
				} catch (Exception e1) {
					Dialogs.showErrorNotification("Plugin error", "Error running plugin " + plugin.getName() + " - see log for details");
					logger.error(e1.getLocalizedMessage(), e1);
				}
			}
		} else if (step instanceof ScriptableWorkflowStep) {
			// TODO: Run command script
		}
	}
	
	
	
	void showScript() {
		showScript(qupath.getScriptEditor(), workflowProperty.get());
	}
	
	/**
	 * Show a script in the script editor based on the specified workflow.
	 * @param scriptEditor
	 * @param workflow
	 */
	public static void showScript(final ScriptEditor scriptEditor, final Workflow workflow) {
		if (workflow == null)
			return;
		String script = workflow.createScript();
		logger.info("\n//---------------------------------\n" + script + "\n//---------------------------------");
		if (scriptEditor != null)
			scriptEditor.showScript("New script", script);
	}
	
	
	static void populateList(final ObservableList<KeyValue<Object>> list, final WorkflowStep step) {
		if (step == null) {
			list.clear();
			return;
		}
		ArrayList<KeyValue<Object>> listNew = new ArrayList<>();
		for (Entry<String, ?> entry : step.getParameterMap().entrySet()) {
			listNew.add(new KeyValue<>(entry.getKey(), entry.getValue()));
		}
		if (step instanceof ScriptableWorkflowStep) {
			var script = ((ScriptableWorkflowStep) step).getScript();
			listNew.add(new KeyValue<>("Script", script));
		}
		list.setAll(listNew);
	}
	
	
	private static class KeyValue<T> {
		
		private final StringProperty key = new SimpleStringProperty();
		private final ObjectProperty<T> value = new SimpleObjectProperty<>();
		
		KeyValue(final String key, final T value) {
			this.key.set(key);
			this.value.set(value);
		}
		
		public StringProperty keyProperty() {
			return key;
		}

		public ObjectProperty<T> valueProperty() {
			return value;
		}

	}
	
	
	@Override
	public void workflowUpdated(Workflow workflow) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> workflowUpdated(workflow));
			return;
		}
		if (workflow == null)
			list.getItems().clear();
		else {
			list.getItems().setAll(workflow.getSteps());
			populateList(table.getItems(), list.getSelectionModel().getSelectedItem());
		}
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		if (imageDataOld == imageDataNew)
			return;
		if (imageDataOld != null)
			imageDataOld.getHistoryWorkflow().removeWorkflowListener(this);
		
		if (imageDataNew != null) {
			imageDataNew.getHistoryWorkflow().addWorkflowListener(this);
			workflowProperty.set(imageDataNew.getHistoryWorkflow());
			list.getSelectionModel().clearSelection();
			Workflow workflow = imageDataNew.getHistoryWorkflow();
			list.getItems().setAll(workflow.getSteps());
			workflowUpdated(workflow);
		} else {
			workflowProperty.set(null);
			list.getItems().clear();
			workflowUpdated(null);
		}
	}
}