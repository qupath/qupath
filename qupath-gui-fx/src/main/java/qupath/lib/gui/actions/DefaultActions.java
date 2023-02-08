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

import org.controlsfx.control.action.Action;

import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
import qupath.lib.gui.commands.BrightnessContrastCommand;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.ContextHelpViewer;
import qupath.lib.gui.commands.CountingPanelCommand;
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
public class DefaultActions {
	
	@ActionIcon(PathIcons.CONTRAST)
	@ActionAccelerator("shift+c")
	@ActionDescription("KEY:DefaultActions.description.showBrightnessContrast")
	public final Action BRIGHTNESS_CONTRAST;
	
	@ActionIcon(PathIcons.POINTS_TOOL)
	@ActionDescription("KEY:DefaultActions.description.showCountingTool")
	public final Action COUNTING_PANEL;

	@ActionDescription("KEY:DefaultActions.description.addTMANote")
	public final Action TMA_ADD_NOTE;
	
	@ActionDescription("KEY:DefaultActions.description.showPointConvexHull")
	public final Action CONVEX_POINTS;
	
	@ActionAccelerator("shortcut+shift+l")
	@ActionDescription("KEY:DefaultActions.description.showLog")
	public final Action SHOW_LOG;

	@ActionIcon(PathIcons.MEASURE)
	@ActionAccelerator("shift+a")
	@ActionDescription("KEY:DefaultActions.description.showAnalysisPane")
	public final Action SHOW_ANALYSIS_PANE;
	
	@ActionIcon(PathIcons.COG)
	@ActionAccelerator("shortcut+,")
	@ActionDescription("KEY:DefaultActions.description.showPrefPane")
	public final Action PREFERENCES;
	
	@ActionDescription("KEY:DefaultActions.description.objectDescriptions")
	public final Action SHOW_OBJECT_DESCRIPTIONS;
	
	@ActionDescription("KEY:DefaultActions.description.measureTMA")
	public final Action MEASURE_TMA;
	
	@ActionDescription("KEY:DefaultActions.description.measureAnnotations")
	public final Action MEASURE_ANNOTATIONS;
	
	@ActionDescription("KEY:DefaultActions.description.measureDetections")
	public final Action MEASURE_DETECTIONS;
	
	@ActionDescription("KEY:DefaultActions.description.gridViewAnnotations")
	public final Action MEASURE_GRID_ANNOTATIONS;

	@ActionDescription("KEY:DefaultActions.description.gridViewTMA")
	public final Action MEASURE_GRID_TMA_CORES;

	@ActionIcon(PathIcons.HELP)
	@ActionDescription("KEY:DefaultActions.description.showHelp")
	public final Action HELP_VIEWER;
	
	private QuPathGUI qupath;
	
	public DefaultActions(QuPathGUI qupath) {
		this.qupath = qupath;
		BRIGHTNESS_CONTRAST = ActionTools.createAction(new BrightnessContrastCommand(qupath), getName("showBrightnessContrast"));
		COUNTING_PANEL = ActionTools.createAction(new CountingPanelCommand(qupath), getName("showCountingTool"));
		TMA_ADD_NOTE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddNoteToSelectedCores(imageData), getName("addTMANote"));
		CONVEX_POINTS = ActionTools.createSelectableAction(PathPrefs.showPointHullsProperty(), getName("showPointConvexHull"));
		SHOW_LOG = ActionTools.createAction(() -> qupath.showLogWindow(), getName("showLog"));
		SHOW_ANALYSIS_PANE = ActionTools.createSelectableAction(qupath.showAnalysisPaneProperty(), getName("showAnalysisPane"));
		PREFERENCES = Commands.createSingleStageAction(() -> Commands.createPreferencesDialog(qupath), getName("showPrefPane"));
		SHOW_OBJECT_DESCRIPTIONS = Commands.createSingleStageAction(() -> Commands.createObjectDescriptionsDialog(qupath), getName("objectDescriptions"));
		MEASURE_TMA = qupath.createImageDataAction(imageData -> Commands.showTMAMeasurementTable(qupath, imageData), getName("measureTMA"));
		MEASURE_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.showAnnotationMeasurementTable(qupath, imageData),getName("measureAnnotations"));
		MEASURE_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.showDetectionMeasurementTable(qupath, imageData),getName("measureDetections"));
		MEASURE_GRID_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.showAnnotationGridView(qupath), getName("gridViewAnnotations"));
		MEASURE_GRID_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.showTMACoreGridView(qupath), getName("gridViewTMA"));
		HELP_VIEWER = Commands.createSingleStageAction(() -> ContextHelpViewer.getInstance(qupath).getStage(), getName("showHelp"));
		
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
	
	private static String getName(String key) {
		return QuPathResources.getString("DefaultActions.name." + key);
	}
	
}