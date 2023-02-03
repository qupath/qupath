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

import java.util.HashMap;
import java.util.Map;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.KeyCodeCombination;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;

/**
 * Manage (drawing) tool selection in a QuPath UI.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class ToolManager {
	
	private static final Logger logger = LoggerFactory.getLogger(ToolManager.class);
	
	private ObjectProperty<PathTool> selectedToolProperty = new SimpleObjectProperty<>(PathTools.MOVE);
	
	private ObservableList<PathTool> tools = FXCollections.observableArrayList(
			PathTools.MOVE, PathTools.RECTANGLE, PathTools.ELLIPSE, PathTools.LINE_OR_ARROW,
			PathTools.POLYGON, PathTools.POLYLINE, PathTools.BRUSH, PathTools.POINTS
			);
	
	private BooleanProperty lockSelectedToolProperty = new SimpleBooleanProperty(false);

	private Map<PathTool, Action> toolActions = new HashMap<>();
	
	private ObjectProperty<PathTool> previousSelectedToolProperty = new SimpleObjectProperty<>(PathTools.MOVE);

	private ToolManager() {}
	
	/**
	 * Create a new instance
	 * @return
	 */
	public static ToolManager create() {
		return new ToolManager();
	}
	
	
	/**
	 * Get a read-only list of all available tools.
	 * If you wish to add a new one, use {@link #installTool(PathTool, KeyCodeCombination)}.
	 * @return
	 */
	public ObservableList<PathTool> getTools() {
		return FXCollections.unmodifiableObservableList(tools);
	}
	
	/**
	 * Property containing the currently-selected {@link PathTool}.
	 * If this needs to be changed, use {@link #setSelectedTool(PathTool)} to ensure that 
	 * {@link #isToolSwitchingEnabled()} has an effect.
	 * @return
	 */
	public ReadOnlyObjectProperty<PathTool> selectedToolProperty() {
		return selectedToolProperty;
	}
	
	/**
	 * Property to request that the selected tool be locked.
	 * Calls to {@link #setSelectedTool(PathTool)} are discarded until the tool is unlocked.
	 * @return
	 */
	public BooleanProperty lockSelectedToolProperty() {
		return lockSelectedToolProperty;
	}

	
	/**
	 * Install a new tool for interacting with viewers.
	 * @param tool the tool to add
	 * @param accelerator an optional accelerator (may be null)
	 * @return true if the tool was added, false otherwise (e.g. if the tool had already been added)
	 */
	public boolean installTool(PathTool tool, KeyCodeCombination accelerator) {
		if (tool == null || tools.contains(tool))
			return false;
		// Keep the points tool last
		if (accelerator != null) {
			var action = getToolAction(tool);
			if (accelerator != null) {
				action.setAccelerator(accelerator);
			}
		}
		int ind = tools.indexOf(PathTools.POINTS);
		if (ind < 0)
			tools.add(tool);
		else
			tools.add(ind, tool);
		return true;
	}
	
	/**
	 * Programmatically select the active {@link PathTool}.
	 * This may fail if {@link #isToolSwitchingEnabled()} returns false.
	 * @param tool
	 */
	public void setSelectedTool(PathTool tool) {
		if (!isToolSwitchingEnabled()) {
			logger.warn("Mode switching currently disabled - cannot change to {}", tool);
			return;
		}
		// If the current tool is not move, record before switching to newly selected
		if (getSelectedTool() != PathTools.MOVE)
			previousSelectedToolProperty.set(getSelectedTool());
		selectedToolProperty.set(tool);
	}
	
	
	/**
	 * Get the value of {@link #selectedToolProperty()}.
	 * @return
	 */
	public PathTool getSelectedTool() {
		return selectedToolProperty().get();
	}
	
	/**
	 * Get the value of {@link #selectedToolProperty()}.
	 * @return
	 */
	public PathTool getPreviousSelectedTool() {
		return previousSelectedToolProperty.get();
	}
	
	/**
	 * Property storing the <i>previous</i> tool that was selected.
	 * This is useful for commands that might want to quickly toggle between tools.
	 * @return
	 */
	public ReadOnlyObjectProperty<PathTool> previousSelectedToolProperty() {
		return previousSelectedToolProperty;
	}
	
	
	/**
	 * Toggle whether the user is permitted to switch to a new active {@link PathTool}.
	 * This can be used to lock a tool temporarily.
	 * @param enabled
	 */
	public void setToolSwitchingEnabled(final boolean enabled) {
		lockSelectedToolProperty().set(!enabled);
	}
	
	/**
	 * Returns true if the user is able to activate another {@link PathTool}, false otherwise.
	 * @return
	 */
	public boolean isToolSwitchingEnabled() {
		return !lockSelectedToolProperty().get();
	}
	
	
	/**
	 * Get the action that corresponds to a specific {@link PathTool}, creating a new action if one does not already exist.
	 * @param tool
	 * @return
	 */
	public Action getToolAction(PathTool tool) {
		var action = toolActions.get(tool);
		if (action == null) {
			action = createToolAction(tool);
			toolActions.put(tool, action);
		}
		return action;
	}
	
	private Action createToolAction(final PathTool tool) {
		  var action = ActionTools.createSelectableCommandAction(new SelectableItem<>(selectedToolProperty, tool), tool.nameProperty(), tool.iconProperty(), null);
		  action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.contains(tool) || lockSelectedToolProperty.get(), lockSelectedToolProperty, tools));
		  return action;
	}
	
}
