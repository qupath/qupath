/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.actions;

import static qupath.lib.gui.actions.ActionTools.createAction;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionIcon;
import qupath.lib.gui.commands.BrightnessContrastCommand;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.ContextHelpViewer;
import qupath.lib.gui.commands.CountingPanelCommand;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory.PathIcons;

/**
 * Default actions associated with a specific QuPath instance.
 * These are useful for generating toolbars and context menus, ensuring that the same actions are used consistently.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class CommonActions {
	
	@ActionConfig("Action.File.Project.createProject")
	public final Action PROJECT_NEW;
	
	@ActionConfig("Action.File.Project.openProject")
	public final Action PROJECT_OPEN;
	
	@ActionConfig("Action.File.Project.openProject")
	public final Action PROJECT_ADD_IMAGES;
	
	
	@ActionIcon(PathIcons.CONTRAST)
	@ActionAccelerator("shift+c")
	@ActionConfig("CommonActions.showBrightnessContrast")
	public final Action BRIGHTNESS_CONTRAST;
	
	@ActionIcon(PathIcons.POINTS_TOOL)
	@ActionConfig("CommonActions.showCountingTool")
	public final Action COUNTING_PANEL;

	@ActionConfig("CommonActions.addTMANote")
	public final Action TMA_ADD_NOTE;
	
	@ActionConfig("CommonActions.showPointConvexHull")
	public final Action CONVEX_POINTS;
	
	@ActionAccelerator("shortcut+shift+l")
	@ActionConfig("CommonActions.showLog")
	public final Action SHOW_LOG;

	@ActionIcon(PathIcons.MEASURE)
	@ActionAccelerator("shift+a")
	@ActionConfig("CommonActions.showAnalysisPane")
	public final Action SHOW_ANALYSIS_PANE;
	
	@ActionIcon(PathIcons.COG)
	@ActionAccelerator("shortcut+,")
	@ActionConfig("CommonActions.showPrefPane")
	public final Action PREFERENCES;
	
	@ActionConfig("CommonActions.objectDescriptions")
	public final Action SHOW_OBJECT_DESCRIPTIONS;
	
	@ActionConfig("CommonActions.measureTMA")
	public final Action MEASURE_TMA;
	
	@ActionConfig("CommonActions.measureAnnotations")
	public final Action MEASURE_ANNOTATIONS;
	
	@ActionConfig("CommonActions.measureDetections")
	public final Action MEASURE_DETECTIONS;
	
	@ActionConfig("CommonActions.gridViewAnnotations")
	public final Action MEASURE_GRID_ANNOTATIONS;

	@ActionConfig("CommonActions.gridViewTMA")
	public final Action MEASURE_GRID_TMA_CORES;

	@ActionIcon(PathIcons.HELP)
	@ActionConfig("CommonActions.showHelp")
	public final Action HELP_VIEWER;
	
	private QuPathGUI qupath;
	
	public CommonActions(QuPathGUI qupath) {
		this.qupath = qupath;
		
		PROJECT_NEW = createAction(() -> Commands.promptToCreateProject(qupath));
		PROJECT_OPEN = createAction(() -> Commands.promptToOpenProject(qupath));
		PROJECT_ADD_IMAGES = createAction(() -> ProjectCommands.promptToImportImages(qupath));
		
		BRIGHTNESS_CONTRAST = ActionTools.createAction(new BrightnessContrastCommand(qupath));
		COUNTING_PANEL = ActionTools.createAction(new CountingPanelCommand(qupath));
		TMA_ADD_NOTE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddNoteToSelectedCores(imageData));
		CONVEX_POINTS = ActionTools.createSelectableAction(PathPrefs.showPointHullsProperty());
		SHOW_LOG = ActionTools.createAction(() -> qupath.showLogWindow());
		SHOW_ANALYSIS_PANE = ActionTools.createSelectableAction(qupath.showAnalysisPaneProperty());
		PREFERENCES = Commands.createSingleStageAction(() -> Commands.createPreferencesDialog(qupath));
		SHOW_OBJECT_DESCRIPTIONS = Commands.createSingleStageAction(() -> Commands.createObjectDescriptionsDialog(qupath));
		MEASURE_TMA = qupath.createImageDataAction(imageData -> Commands.showTMAMeasurementTable(qupath, imageData));
		MEASURE_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.showAnnotationMeasurementTable(qupath, imageData));
		MEASURE_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.showDetectionMeasurementTable(qupath, imageData));
		MEASURE_GRID_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.showAnnotationGridView(qupath));
		MEASURE_GRID_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.showTMACoreGridView(qupath));
		HELP_VIEWER = Commands.createSingleStageAction(() -> ContextHelpViewer.getInstance(qupath).getStage());
		
		// This has the effect of applying the annotations
		ActionTools.getAnnotatedActions(this);
	}
	
	/**
	 * Get the QuPath instance associated with these actions.
	 * @return
	 */
	public QuPathGUI getQuPath() {
		return qupath;
	}
	
}