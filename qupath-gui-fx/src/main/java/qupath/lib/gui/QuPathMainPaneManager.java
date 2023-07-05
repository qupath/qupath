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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.tools.CommandFinderTools;

/**
 * Inelegantly named class to manage the main components of the main QuPath window.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class QuPathMainPaneManager {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathMainPaneManager.class);
	
	private BorderPane pane;
	
	private SplitPane splitPane;
	private Region mainViewerPane;
	
	private ToolBarComponent toolbar;
	private AnalysisTabPane analysisTabPane;
	
	private double lastDividerLocation;
	
	
	QuPathMainPaneManager(QuPathGUI qupath) {
		pane = new BorderPane();
		splitPane = new SplitPane();
		this.analysisTabPane = new AnalysisTabPane(qupath);
		
		var tabPane = analysisTabPane.getTabPane();
		tabPane.setMinWidth(300);
		tabPane.setPrefWidth(400);
		splitPane.setMinWidth(tabPane.getMinWidth() + 200);
		splitPane.setPrefWidth(tabPane.getPrefWidth() + 200);
		SplitPane.setResizableWithParent(tabPane, Boolean.FALSE);		
		
		var viewerRegion = qupath.getViewerManager().getRegion();
		mainViewerPane = CommandFinderTools.createCommandFinderPane(qupath, viewerRegion, CommandFinderTools.commandBarDisplayProperty());
		splitPane.getItems().addAll(tabPane, mainViewerPane);
		SplitPane.setResizableWithParent(viewerRegion, Boolean.TRUE);
		
		pane.setCenter(splitPane);
		
		toolbar = new ToolBarComponent(qupath.getToolManager(), qupath.getViewerActions(), qupath.getCommonActions(), qupath.getOverlayActions());
		pane.setTop(toolbar.getToolBar());
		
		setAnalysisPaneVisible(true);

	}

	AnalysisTabPane getAnalysisTabPane() {
		return analysisTabPane;
	}
	
	ProjectBrowser getProjectBrowser() {
		return analysisTabPane == null ? null : analysisTabPane.getProjectBrowser();
	}
	
	ToolBar getToolBar() {
		return toolbar.getToolBar();
	}

	
	Pane getMainPane() {
		return pane;
	}
	
	void setDividerPosition(double pos) {
		splitPane.setDividerPosition(0, pos);
	}
	
	void setAnalysisPaneVisible(boolean visible) {
		if (visible) {
			if (analysisPanelVisible())
				return;
			splitPane.getItems().setAll(analysisTabPane.getTabPane(), mainViewerPane);
			splitPane.setDividerPosition(0, lastDividerLocation);
			pane.setCenter(splitPane);
		} else {
			if (!analysisPanelVisible())
				return;
			lastDividerLocation = splitPane.getDividers().get(0).getPosition();
			pane.setCenter(mainViewerPane);				
		}
	}
	
	private boolean analysisPanelVisible() {
		return pane.getCenter() == splitPane;
	}
	
}