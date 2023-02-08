package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.plugins.objects.FillAnnotationHolesPlugin;
import qupath.lib.plugins.objects.RefineAnnotationsPlugin;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;

public class ObjectsMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	
	private Actions actions;
	
	ObjectsMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
		this.defaultActions = qupath.getDefaultActions();
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.Objects.name");
	}

	
	@ActionMenu("KEY:Menu.Objects.name")
	public class Actions {
		
		@ActionDescription("Delete the currently selected objects.")
		@ActionMenu("Delete...>Delete selected objects")
		public final Action DELETE_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> GuiTools.promptToClearAllSelectedObjects(imageData));
		
		@ActionMenu("Delete...>")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionDescription("Delete all objects for the current image.")
		@ActionMenu("Delete...>Delete all objects")
		public final Action CLEAR_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, null));

		@ActionDescription("Delete all annotation objects for the current image.")
		@ActionMenu("Delete...>Delete all annotations")
		public final Action CLEAR_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathAnnotationObject.class));
		
		@ActionDescription("Delete all detection objects for the current image.")
		@ActionMenu("Delete...>Delete all detections")
		public final Action CLEAR_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathDetectionObject.class));

		@ActionDescription("Reset the selected objects for the current image.")
		@ActionMenu("Select...>Reset selection")
		@ActionAccelerator("shortcut+alt+r")
		public final Action RESET_SELECTION = qupath.createImageDataAction(imageData -> Commands.resetSelection(imageData));

		@ActionMenu("Select...>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionDescription("Select all TMA cores for the current image.")
		@ActionMenu("Select...>Select TMA cores")
		@ActionAccelerator("shortcut+alt+t")
		public final Action SELECT_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, TMACoreObject.class));

		@ActionDescription("Select all annotation objects for the current image.")
		@ActionMenu("Select...>Select annotations")
		@ActionAccelerator("shortcut+alt+a")
		public final Action SELECT_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathAnnotationObject.class));

		@ActionDescription("Select all detection objects for the current image (this includes cells and tiles).")
		@ActionMenu("Select...>Select detections...>Select all detections")
		@ActionAccelerator("shortcut+alt+d")
		public final Action SELECT_ALL_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathDetectionObject.class));

		@ActionDescription("Select all cell objects for the current image.")
		@ActionMenu("Select...>Select detections...>Select cells")
		@ActionAccelerator("shortcut+alt+c")
		public final Action SELECT_CELLS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathCellObject.class));
		
		@ActionDescription("Select all tile objects for the current image.")
		@ActionMenu("Select...>Select detections...>Select tiles")
		public final Action SELECT_TILES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathTileObject.class));

		@ActionMenu("Select...>")
		public final Action SEP_3 = ActionTools.createSeparator();
		
		@ActionDescription("Select objects based upon their classification.")
		@ActionMenu("Select...>Select objects by classification")
		public final Action SELECT_BY_CLASSIFICATION = qupath.createImageDataAction(imageData -> Commands.promptToSelectObjectsByClassification(qupath, imageData));

		@ActionDescription("Select all objects on the current plane visiible in the viewer.")
		@ActionMenu("Select...>Select objects on current plane")
		public final Action SELECT_BY_PLANE = qupath.createViewerAction(viewer -> Commands.selectObjectsOnCurrentPlane(viewer));

		@ActionDescription("Lock all currently selected objects.")
		@ActionMenu("Lock...>Lock selected objects")
		@ActionAccelerator("shortcut+shift+k")
		public final Action LOCK_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> PathObjectTools.lockSelectedObjects(imageData.getHierarchy()));

		@ActionDescription("Unlock all currently selected objects.")
		@ActionMenu("Lock...>Unlock selected objects")
		@ActionAccelerator("shortcut+alt+k")
		public final Action UNLOCK_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> PathObjectTools.unlockSelectedObjects(imageData.getHierarchy()));

		@ActionDescription("Toggle the 'locked' state of all currently selected objects.")
		@ActionMenu("Lock...>Toggle selected objects locked")
		@ActionAccelerator("shortcut+k")
		public final Action TOGGLE_SELECTED_OBJECTS_LOCKED = qupath.createImageDataAction(imageData -> PathObjectTools.toggleSelectedObjectsLocked(imageData.getHierarchy()));

		public final Action SHOW_OBJECT_DESCRIPTIONS = defaultActions.SHOW_OBJECT_DESCRIPTIONS;
		
		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionDescription("Create a rectangle or ellipse annotation with the specified properties.")
		@ActionMenu("Annotations...>Specify annotation")
		public final Action SPECIFY_ANNOTATION = Commands.createSingleStageAction(() -> Commands.createSpecifyAnnotationDialog(qupath));
		
		@ActionDescription("Create an annotation representing the full width and height of the current image.")
		@ActionMenu("Annotations...>Create full image annotation")
		@ActionAccelerator("shortcut+shift+a")
		public final Action SELECT_ALL_ANNOTATION = qupath.createImageDataAction(imageData -> Commands.createFullImageAnnotation(qupath.getViewer()));

		@ActionMenu("Annotations...>")
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionDescription("Insert the selected objects in the object hierarchy. " + 
				"This involves resolving parent/child relationships based upon regions of interest.")
		@ActionMenu("Annotations...>Insert into hierarchy")
		@ActionAccelerator("shortcut+shift+i")
		public final Action INSERT_INTO_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.insertSelectedObjectsInHierarchy(imageData));
		
		@ActionDescription("Resolve the object hierarchy by setting parent/child relationships "
				+ "between objects based upon regions of interest.")
		@ActionMenu("Annotations...>Resolve hierarchy")
		@ActionAccelerator("shortcut+shift+r")
		public final Action RESOLVE_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToResolveHierarchy(imageData));
		

		@ActionMenu("Annotations...>")
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionDescription("Interactively translate and/or rotate the current selected annotation.")
		@ActionMenu("Annotations...>Transform annotations")
		@ActionAccelerator("shortcut+shift+t")
		public final Action RIGID_OBJECT_EDITOR = qupath.createImageDataAction(imageData -> Commands.editSelectedAnnotation(qupath));
		
		@ActionDescription("Duplicate the selected annotations.")
		@ActionMenu("Annotations...>Duplicate selected annotations")
		@ActionAccelerator("shift+d")
		public final Action ANNOTATION_DUPLICATE = qupath.createImageDataAction(imageData -> Commands.duplicateSelectedAnnotations(imageData));

		@ActionDescription("Duplicate the selected objects and paste them on the current plane (z-slice and timepoint visible in the viewer).\n"
				+ "This avoids using the system clipboard. It is intended to help transfer annotations quickly across multidimensional images.")
		@ActionMenu("Annotations...>Copy annotations to current plane")
		@ActionAccelerator("shortcut+shift+v")
		public final Action ANNOTATION_COPY_TO_PLANE = qupath.createViewerAction(viewer -> Commands.copySelectedAnnotationsToCurrentPlane(viewer));

		@ActionDescription("Transfer the last annotation to the current image. "
				+ "This can be used to bring annotations from one viewer to another, or to recover "
				+ "an annotation that has just been deleted.")
		@ActionMenu("Annotations...>Transfer last annotation")
		@ActionAccelerator("shift+e")
		public final Action TRANSFER_ANNOTATION = qupath.createImageDataAction(imageData -> qupath.getViewerManager().applyLastAnnotationToActiveViewer());

		@ActionMenu("Annotations...>")
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionDescription("Expand (or contract) the selected annotations, "
				+ "optionally removing the interior.")
		@ActionMenu("Annotations...>Expand annotations")
		public final Action EXPAND_ANNOTATIONS = qupath.createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null);
		
		@ActionDescription("Split complex annotations that contain disconnected pieces into separate annotations.")
		@ActionMenu("Annotations...>Split annotations")
		public final Action SPLIT_ANNOTATIONS = qupath.createPluginAction("Split annotations", SplitAnnotationsPlugin.class, null);
		
		@ActionDescription("Remove small fragments of annotations that contain disconnected pieces.")
		@ActionMenu("Annotations...>Remove fragments & holes")
		public final Action REMOVE_FRAGMENTS = qupath.createPluginAction("Remove fragments & holes", RefineAnnotationsPlugin.class, null);
		
		@ActionDescription("Fill holes occurring inside annotations.")
		@ActionMenu("Annotations...>Fill holes")
		public final Action FILL_HOLES = qupath.createPluginAction("Fill holes", FillAnnotationHolesPlugin.class, null);

		@ActionMenu("Annotations...>")
		public final Action SEP_8 = ActionTools.createSeparator();
		
		@ActionDescription("Make annotations corresponding to the 'inverse' of the selected annotation. "
				+ "The inverse annotation contains 'everything else' outside the current annotation, constrained by its parent.")
		@ActionMenu("Annotations...>Make inverse")
		public final Action MAKE_INVERSE = qupath.createImageDataAction(imageData -> Commands.makeInverseAnnotation(imageData));
		
		@ActionDescription("Merge the selected annotations to become one, single annotation.")
		@ActionMenu("Annotations...>Merge selected")
		public final Action MERGE_SELECTED = qupath.createImageDataAction(imageData -> Commands.mergeSelectedAnnotations(imageData));
		
		@ActionDescription("Simplify the shapes of the current selected annotations. "
				+ "This removes vertices that are considered unnecessary, using a specified amplitude tolerance.")
		@ActionMenu("Annotations...>Simplify shape")
		public final Action SIMPLIFY_SHAPE = qupath.createImageDataAction(imageData -> Commands.promptToSimplifySelectedAnnotations(imageData, 1.0));
		
		
		@ActionDescription("Update all object IDs to ensure they are unique.")
		@ActionMenu("Refresh object IDs")
		public final Action REFRESH_OBJECT_IDS = qupath.createImageDataAction(imageData -> Commands.refreshObjectIDs(imageData, false));

		@ActionDescription("Update all duplicate object IDs to ensure they are unique.")
		@ActionMenu("Refresh duplicate object IDs")
		public final Action REFRESH_DUPLICATE_OBJECT_IDS = qupath.createImageDataAction(imageData -> Commands.refreshObjectIDs(imageData, true));

	}


}
