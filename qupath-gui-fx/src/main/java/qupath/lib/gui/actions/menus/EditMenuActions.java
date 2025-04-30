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

package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.Collections;
import java.util.List;

import org.controlsfx.control.action.Action;

import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.UndoRedoManager;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.GuiTools;

public class EditMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private CommonActions commonActions;
	
	private Actions actions;
	
	EditMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.commonActions = qupath.getCommonActions();
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}

	@Override
	public String getName() {
		return QuPathResources.getString("Menu.Edit");
	}
	
	
	@ActionMenu("Menu.Edit")
	public class Actions {
		
		@ActionAccelerator("shortcut+z")
		@ActionConfig("Action.Edit.undo")
		public final Action UNDO;
		
		@ActionAccelerator("shortcut+shift+z")
		@ActionConfig("Action.Edit.redo")
		public final Action REDO;
		
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("Menu.Edit.Copy")
		public final CopyActions copyActions = new CopyActions();
		
		@ActionConfig("Action.Edit.paste")
//		@ActionAccelerator("shortcut+v") // No shortcut because it gets fired too often
		public final Action PASTE = createAction(() -> Commands.pasteFromClipboard(qupath, false));

		@ActionConfig("Action.Edit.pasteToCurrentPlane")
		public final Action PASTE_TO_PLANE = createAction(() -> Commands.pasteFromClipboard(qupath, true));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		public final Action PREFERENCES = commonActions.PREFERENCES;
		
		@ActionConfig("Action.Edit.resetPreferences")
		public final Action RESET_PREFERENCES = createAction(() -> Commands.promptToResetPreferences());

		
		private Actions() {
			var undoRedo = qupath.getUndoRedoManager();
			UNDO = createUndoAction(undoRedo);
			REDO = createRedoAction(undoRedo);
		}
		
		private static Action createUndoAction(UndoRedoManager undoRedoManager) {
			Action actionUndo = new Action("Undo", e -> undoRedoManager.undoOnce());
			actionUndo.disabledProperty().bind(undoRedoManager.canUndo().not());
			return actionUndo;
		}
		
		private static Action createRedoAction(UndoRedoManager undoRedoManager) {
			Action actionRedo = new Action("Redo", e -> undoRedoManager.redoOnce());
			actionRedo.disabledProperty().bind(undoRedoManager.canRedo().not());
			return actionRedo;
		}
		
	}
	
	public class CopyActions {
		
		@ActionConfig("Action.Edit.Copy.selectedObjects")
//		@ActionAccelerator("shortcut+c")
		public final Action COPY_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> Commands.copySelectedObjectsToClipboard(imageData));

		@ActionConfig("Action.Edit.Copy.annotationObjects")
		public final Action COPY_ANNOTATION_OBJECTS = qupath.createImageDataAction(imageData -> Commands.copyAnnotationsToClipboard(imageData));

		public final Action SEP_00 = ActionTools.createSeparator();

		@ActionConfig("Action.Edit.Copy.currentViewer")
		public final Action COPY_VIEW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.VIEWER));
		
		@ActionConfig("Action.Edit.Copy.mainWindowContent")
		public final Action COPY_WINDOW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_SCENE));
		
		@ActionConfig("Action.Edit.Copy.mainWindowScreenshot")
		public final Action COPY_WINDOW_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));			
		
		@ActionConfig("Action.Edit.Copy.fullScreenshot")
		public final Action COPY_FULL_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.FULL_SCREENSHOT));
		
		
		private static void copyViewToClipboard(final QuPathGUI qupath, final GuiTools.SnapshotType type) {
			Image img = GuiTools.makeSnapshotFX(qupath, qupath.getViewer(), type);
			Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.IMAGE, img));
		}

		
	}


}
