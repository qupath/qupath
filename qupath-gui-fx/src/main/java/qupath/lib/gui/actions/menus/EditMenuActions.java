package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.Collections;
import java.util.List;

import org.controlsfx.control.action.Action;

import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.UndoRedoManager;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.tools.GuiTools;

public class EditMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	
	private Actions actions;
	
	EditMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.defaultActions = qupath.getDefaultActions();
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}

	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.Edit.name");
	}
	
	
	@ActionMenu("KEY:Menu.Edit.name")
	public class Actions {
		
		@ActionAccelerator("shortcut+z")
		public final Action UNDO;
		
		@ActionAccelerator("shortcut+shift+z")
		@ActionMenu("KEY:Menu.Edit.name.redo")
		@ActionDescription("KEY:Menu.Edit.description.redo")
		public final Action REDO;
		
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("KEY:Menu.Edit.Copy.name.selectedObjects")
		@ActionDescription("KEY:Menu.Edit.Copy.description.selectedObjects")
//		@ActionAccelerator("shortcut+c")
		public final Action COPY_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> Commands.copySelectedObjectsToClipboard(imageData));

		@ActionMenu("KEY:Menu.Edit.Copy.name.annotationObjects")
		@ActionDescription("KEY:Menu.Edit.Copy.description.annotationObjects")
		public final Action COPY_ANNOTATION_OBJECTS = qupath.createImageDataAction(imageData -> Commands.copyAnnotationsToClipboard(imageData));

		@ActionMenu("KEY:Menu.Edit.Copy.name")
		public final Action SEP_00 = ActionTools.createSeparator();

		@ActionMenu("KEY:Menu.Edit.Copy.name.currentViewer")
		@ActionDescription("KEY:Menu.Edit.Copy.description.currentViewer")
		public final Action COPY_VIEW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.VIEWER));
		
		@ActionMenu("KEY:Menu.Edit.Copy.name.mainWindowContent")
		@ActionDescription("KEY:Menu.Edit.Copy.description.mainWindowContent")
		public final Action COPY_WINDOW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_SCENE));
		
		@ActionMenu("KEY:Menu.Edit.Copy.name.mainWindowScreenshot")
		@ActionDescription("KEY:Menu.Edit.Copy.description.mainWindowScreenshot")
		public final Action COPY_WINDOW_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));			
		
		@ActionMenu("KEY:Menu.Edit.Copy.name.fullScreenshot")
		@ActionDescription("KEY:Menu.Edit.Copy.description.fullScreenshot")
		public final Action COPY_FULL_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.FULL_SCREENSHOT));

		
		@ActionMenu("KEY:Menu.Edit.name.paste")
		@ActionDescription("KEY:Menu.Edit.description.paste")
//		@ActionAccelerator("shortcut+v") // No shortcut because it gets fired too often
		public final Action PASTE = createAction(() -> Commands.pasteFromClipboard(qupath, false));

		@ActionMenu("KEY:Menu.Edit.name.pasteToCurrentPlane")
		@ActionDescription("KEY:Menu.Edit.description.pasteToCurrentPlane")
		public final Action PASTE_TO_PLANE = createAction(() -> Commands.pasteFromClipboard(qupath, true));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		public final Action PREFERENCES = defaultActions.PREFERENCES;
		
		@ActionMenu("KEY:Menu.Edit.name.resetPreferences")
		@ActionDescription("KEY:Menu.Edit.description.resetPreferences")
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
		
		private static void copyViewToClipboard(final QuPathGUI qupath, final GuiTools.SnapshotType type) {
			Image img = GuiTools.makeSnapshotFX(qupath, qupath.getViewer(), type);
			Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.IMAGE, img));
		}
	}


}
