/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.layout.BorderPane;
import qupath.lib.gui.panes.AnnotationPane;
import qupath.lib.gui.panes.ImageDetailsPane;
import qupath.lib.gui.panes.ObjectDescriptionPane;
import qupath.lib.gui.panes.PathObjectHierarchyView;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.panes.SelectedMeasurementTableView;
import qupath.lib.gui.panes.WorkflowCommandLogView;
import qupath.lib.gui.tools.GuiTools;

class AnalysisTabPane {
	
	private QuPathGUI qupath;
	
	private ProjectBrowser projectBrowser;
	private ImageDetailsPane imageDetailsPane;
	private AnnotationPane annotationPane;
	private PathObjectHierarchyView hierarchyPane;
	private WorkflowCommandLogView workflowLogView;
	
	private TabPane tabPane = new TabPane();
	
	AnalysisTabPane(QuPathGUI qupath) {
		this.qupath = qupath;
		createPanes();
		createAndInitializeTabPane();
		addTabsForPanes();
		makeTabsUndockable();
	}
	
	
	private void createAndInitializeTabPane() {
		tabPane = new TabPane();
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabPane.setTabDragPolicy(TabDragPolicy.REORDER);
	}
	
	private void createPanes() {
		projectBrowser = createProjectBrowser();
		imageDetailsPane = createImageDetailsPane();
		annotationPane = createAnnotationPane();
		hierarchyPane = createHierarchyPane();
		workflowLogView = createWorkflowCommandLogView();
	}


	private void addTabsForPanes() {
				

		tabPane.getTabs().add(new Tab("Project", projectBrowser.getPane()));
		tabPane.getTabs().add(new Tab("Image", imageDetailsPane.getPane()));
		
		/*
		 * Create tabs.
		 * Note that we don't want ImageData/hierarchy events to be triggered for tabs that aren't visible,
		 * since these can be quite expensive.
		 * For that reason, we create new bindings.
		 * 
		 * TODO: Handle analysis pane being entirely hidden.
		 */
		
		// Create a tab for annotations
		var tabAnnotations = new Tab("Annotations");
		SplitPane splitAnnotations = new SplitPane();
		splitAnnotations.setOrientation(Orientation.VERTICAL);
		
		// Don't make updates if the tab isn't visible
		var annotationTabVisible = Bindings.createBooleanBinding(() -> {
			return tabAnnotations.getTabPane() == null || tabAnnotations.isSelected();
		}, tabAnnotations.tabPaneProperty(), tabAnnotations.selectedProperty());
		annotationPane.disableUpdatesProperty().bind(annotationTabVisible.not());
		var tabAnnotationsMeasurements = createMeasurementsAndDescriptionsPane(annotationTabVisible);
		splitAnnotations.getItems().addAll(annotationPane.getPane(), tabAnnotationsMeasurements);
		tabAnnotations.setContent(splitAnnotations);
		tabPane.getTabs().add(tabAnnotations);		
		
		// Create a tab for the full hierarchy
		var tabHierarchy = new Tab("Hierarchy");
		var hierarchyTabVisible = Bindings.createBooleanBinding(() -> {
			return tabHierarchy.getTabPane() == null || tabHierarchy.isSelected();
		}, tabHierarchy.tabPaneProperty(), tabHierarchy.selectedProperty());
		hierarchyPane.disableUpdatesProperty().bind(hierarchyTabVisible.not());
		var tabHierarchyMeasurements = createMeasurementsAndDescriptionsPane(hierarchyTabVisible);
		SplitPane splitHierarchy = new SplitPane();
		splitHierarchy.setOrientation(Orientation.VERTICAL);
		splitHierarchy.getItems().addAll(hierarchyPane.getPane(), tabHierarchyMeasurements);
		tabHierarchy.setContent(splitHierarchy);
		tabPane.getTabs().add(tabHierarchy);
		
		tabPane.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			// Update split locations if both tabs are in the tab pane
			if (tabAnnotations.getTabPane() != null && tabHierarchy.getTabPane() != null) {
				if (o == tabHierarchy) {
					splitHierarchy.setDividerPosition(0, splitAnnotations.getDividerPositions()[0]);
				} else if (o == tabAnnotations) {
					splitAnnotations.setDividerPosition(0, splitHierarchy.getDividerPositions()[0]);				
				}
			}
		});
		
		
		TitledPane titledLog = new TitledPane("Command history", workflowLogView.getPane());
		titledLog.setCollapsible(false);
		titledLog.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		var pane = new BorderPane(titledLog);
		tabPane.getTabs().add(new Tab("Workflow", pane));
	}
	
	private void makeTabsUndockable() {
		// Make the tabs undockable
		for (var tab : tabPane.getTabs()) {
			GuiTools.makeTabUndockable(tab);
			tab.setTooltip(
					new Tooltip("Switch to " + tab.getText() + " pane.\n"
							+ "You can also right-click a tab to undock it into its own window.\n"
							+ "Then close the window to make it a tab again.")
					);
		}
	}
	
	private ImageDetailsPane createImageDetailsPane() {
		return new ImageDetailsPane(qupath.imageDataProperty());
	}
	
	private ProjectBrowser createProjectBrowser() {
		return new ProjectBrowser(qupath);
	}
	
	private AnnotationPane createAnnotationPane() {
		return new AnnotationPane(qupath, qupath.imageDataProperty());
	}
	
	private PathObjectHierarchyView createHierarchyPane() {
		return new PathObjectHierarchyView(qupath, qupath.imageDataProperty());
	}
	
	private WorkflowCommandLogView createWorkflowCommandLogView() {
		return new WorkflowCommandLogView(qupath);
	}

	
	
	TabPane getTabPane() {
		return tabPane;
	}
	
	ProjectBrowser getProjectBrowser() {
		return projectBrowser;
	}
	
	
	/**
	 * Make a tab pane to show either measurements or descriptions for the selected object.
	 * Optionally provide a bindable value for visibility, since this can reduce expensive updates.
	 * @param visible
	 * @return
	 */
	private TabPane createMeasurementsAndDescriptionsPane(ObservableBooleanValue visible) {
		var tabpaneObjectsShared = new TabPane();
		var objectMeasurementsTable = new SelectedMeasurementTableView(qupath.imageDataProperty());
		tabpaneObjectsShared.setSide(Side.BOTTOM);
		var tabSharedTable = new Tab("Measurements", objectMeasurementsTable.getTable());
		tabpaneObjectsShared.getTabs().add(tabSharedTable);
		var descriptionPane = ObjectDescriptionPane.createPane(qupath.imageDataProperty(), true);
		var tabSharedDescription = new Tab("Description", descriptionPane);
		tabpaneObjectsShared.getTabs().add(tabSharedDescription);
		tabpaneObjectsShared.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
		if (visible != null) {
			objectMeasurementsTable.getTable().visibleProperty().bind(visible);
			descriptionPane.visibleProperty().bind(visible);
		}
		return tabpaneObjectsShared;
	}
	
}