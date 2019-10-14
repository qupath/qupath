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

package qupath.lib.gui.commands;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javafx.collections.ListChangeListener.Change;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;


/**
 * Simple dialog box for managing measurements - especially removing them.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementManager implements PathCommand {
	
	private QuPathGUI qupath;
	
	public MeasurementManager(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		createAndShowDialog();
	}
	
	private void createAndShowDialog() {
		
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("Measurement Manager", "No image selected!");
			return;
		}
		
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Get all the objects we have
		Map<Class<? extends PathObject>, List<PathObject>> map = new HashMap<>();
		Map<Class<? extends PathObject>, Set<String>> mapMeasurements = new HashMap<>();
		for (PathObject p : hierarchy.getFlattenedObjectList(null)) {
			
			List<String> names = p.getMeasurementList().getMeasurementNames();
			if (names.isEmpty())
				continue;
			
			List<PathObject> list = map.get(p.getClass());
			if (list == null) {
				list = new ArrayList<>();
				map.put(p.getClass(), list);
			}
			list.add(p);
			
			Set<String> setMeasurements = mapMeasurements.get(p.getClass());
			if (setMeasurements == null) {
				setMeasurements = new LinkedHashSet<>();
				mapMeasurements.put(p.getClass(), setMeasurements);
			}
			setMeasurements.addAll(p.getMeasurementList().getMeasurementNames());
		}
		
		// Check we have something to show
		if (map.isEmpty()) {
			DisplayHelpers.showErrorMessage("Measurement Manager", "No objects found!");
			return;
		}
		
		// Get a mapping of suitable names for each class
		Map<String, Class<? extends PathObject>> classMap = new TreeMap<>();
		map.keySet().stream().forEach(k -> classMap.put(PathObjectTools.getSuitableName(k, true), k));
		
		// Create a ComboBox to choose between object types
		ComboBox<String> comboBox = new ComboBox<>();
		comboBox.getItems().setAll(classMap.keySet());
		comboBox.setMaxWidth(Double.MAX_VALUE);
		BorderPane paneTop = new BorderPane(comboBox);
		Tooltip.install(paneTop, new Tooltip("Select an object type to view all associated measurements"));
		Label label = new Label("Object type: ");
		label.setMaxHeight(Double.MAX_VALUE);
		paneTop.setLeft(label);
		paneTop.setPadding(new Insets(0, 0, 10, 0));
		
		// Create a view to show the measurements
		TreeView<String> tree = new TreeView<>();
		tree.setShowRoot(false);
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		ContextMenu menu = new ContextMenu();
		MenuItem miExpand = new MenuItem("Expand all");
		miExpand.setOnAction(e -> setTreeItemsExpanded(tree.getRoot(), true));
		
		MenuItem miCollapse = new MenuItem("Collapse all");
		miCollapse.setOnAction(e -> {
			if (tree.getRoot() != null) {
				setTreeItemsExpanded(tree.getRoot(), false);
				tree.getRoot().setExpanded(true); // Need to expand the root, since it isn't visible
			}
		});
		menu.getItems().addAll(miExpand, miCollapse);
		tree.setContextMenu(menu);
		
		TitledPane titledMeasurements = new TitledPane("Measurements", tree);
		titledMeasurements.setCollapsible(false);
		titledMeasurements.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane paneMeasurements = new BorderPane(titledMeasurements);
		Button btnRemove = new Button("Delete selected");
		btnRemove.setMaxWidth(Double.MAX_VALUE);
		paneMeasurements.setBottom(btnRemove);
		btnRemove.setOnAction(e -> {
			Set<String> toRemove = new HashSet<>();
			for (TreeItem<String> treeItem : tree.getSelectionModel().getSelectedItems()) {
				MeasurementItem item = (MeasurementItem)treeItem;
				toRemove.addAll(item.getDescendantMeasurements());
			}
			if (toRemove.isEmpty()) {
				DisplayHelpers.showErrorMessage("Remove measurements", "No measurements selected!");
				return;
			}
			String number = toRemove.size() == 1 ? String.format("'%s'", toRemove.iterator().next()) : toRemove.size() + " measurements";
			if (DisplayHelpers.showConfirmDialog("Remove measurements", "Are you sure you want to permanently remove " + number + "?")) {
				Class<? extends PathObject> cls = classMap.get(comboBox.getSelectionModel().getSelectedItem());
				QP.removeMeasurements(hierarchy, cls, toRemove.toArray(new String[toRemove.size()]));
				
				// Keep for scripting
				WorkflowStep step = new DefaultScriptableWorkflowStep("Remove measurements",
						String.format("removeMeasurements(%s, %s);", cls.getName(), String.join(", ", toRemove.stream().map(m -> "\"" + m + "\"").collect(Collectors.toList())))
						);
				imageData.getHistoryWorkflow().addStep(step);
				
				// Update
				mapMeasurements.get(cls).removeAll(toRemove);
				tree.setRoot(new MeasurementItem(mapMeasurements.get(cls), ""));
				setTreeItemsExpanded(tree.getRoot(), true);
				titledMeasurements.setText("Measurements (" + mapMeasurements.get(cls).size() + ")");
			}
		});
		btnRemove.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
		
		// Operate on backspace too
		tree.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)
				btnRemove.fire();
		});

		// Listen for object type selections
		comboBox.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			Class<? extends PathObject> cls = classMap.get(n);
			tree.setRoot(new MeasurementItem(mapMeasurements.get(cls), ""));
			setTreeItemsExpanded(tree.getRoot(), true);
			titledMeasurements.setText("Measurements (" + mapMeasurements.get(cls).size() + ")");
		});
		
		
		// Try to select most sensible default
		for (String defaultName : Arrays.asList(
				PathObjectTools.getSuitableName(PathCellObject.class, true),
				PathObjectTools.getSuitableName(PathDetectionObject.class, true),
				PathObjectTools.getSuitableName(PathAnnotationObject.class, true),
				PathObjectTools.getSuitableName(TMACoreObject.class, true)
				)) {
			if (classMap.containsKey(defaultName)) {
				comboBox.getSelectionModel().select(defaultName);
				break;
			}			
		}
		

		
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Measurement Manager");
		
		BorderPane pane = new BorderPane();
		pane.setTop(paneTop);
		pane.setCenter(paneMeasurements);
		pane.setPadding(new Insets(10, 10, 10, 10));
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		if (screenSize != null && screenSize.getWidth() > 100)
			dialog.setScene(new Scene(pane, Math.min(600, screenSize.getWidth()/2), -1));
		else
			dialog.setScene(new Scene(pane));
		dialog.show();
		
	}
	
	
	static void setTreeItemsExpanded(final TreeItem<?> root, final boolean expand) {
		if (root == null)
			return;
		root.setExpanded(expand);
		for (TreeItem<?> item : root.getChildren()) {
			setTreeItemsExpanded(item, expand);
		}
	}
	
	
	
	static class MeasurementItem extends TreeItem<String> {
		
		private static String delimiter = ":";
		
        private String measurement;
        private String name;

        MeasurementItem(final String measurement, final String name) {
        	super(measurement);
        	this.measurement = measurement;
        	this.name = name.replace(delimiter, "").trim();
        	setValue(name);
        }
        
        MeasurementItem(final Collection<String> measurements, final String prefix) {
        	super();
        	
        	String[] splits = prefix.split(delimiter);
        	this.name = splits.length == 0 ? prefix : splits[splits.length-1];
        	
        	setValue(name + " (" + measurements.size() + ")");
        	
        	Map<String, List<String>> map = new TreeMap<>();
        	List<MeasurementItem> children = new ArrayList<>();
        	
        	for (String m : measurements) {
        		// Strip off prefix
        		String m2 = m.substring(prefix.length());
        		// Get next occurrence of delimiter after prefix
        		int ind = m2.indexOf(delimiter);
        		if (ind < 0 || ind == m2.length()-1) {
        			children.add(new MeasurementItem(m, m2));
        		} else {
        			// If we have a new prefix, record this
        			String newPrefix = m2.substring(0, ind+1);
        			List<String> newMeasurements = map.get(newPrefix);
        			if (newMeasurements == null) {
        				newMeasurements = new ArrayList<>();
            			map.put(newPrefix, newMeasurements);
        			}
    				newMeasurements.add(m);
        		}
        	}
        	
        	// Create the new non-leaf children
       		getChildren().addAll(
       				map.entrySet().stream().map(
       						entry -> new MeasurementItem(entry.getValue(), prefix + entry.getKey())).collect(Collectors.toList()));

       		
       		getChildren().addListener((Change<? extends TreeItem<String>> e) -> setValue(name + " (" + getChildren().size() + ")"));
       		
       		// Add the leaf children
       		getChildren().addAll(children);
        }
        
        
        public String getMeasurement() {
        	return measurement;
        }
        
        public Collection<String> getDescendantMeasurements() {
        	if (isLeaf())
        		return Collections.singleton(measurement);
        	Set<String> measurements = new TreeSet<>();
        	for (TreeItem<String> child : getChildren()) {
        		measurements.addAll(((MeasurementItem)child).getDescendantMeasurements());
        	}
        	return measurements;
        }
        
	}

}
