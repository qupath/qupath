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

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.DeleteObjectsOnBoundsCommand;
import qupath.lib.gui.commands.objects.SplitAnnotationsByLineCommand;
import qupath.lib.gui.localization.QuPathResources;
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
	private CommonActions commonActions;
	
	private Actions actions;
	
	ObjectsMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
		this.commonActions = qupath.getCommonActions();
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
		return QuPathResources.getString("Menu.Objects");
	}

	
	@ActionMenu("Menu.Objects")
	public class Actions {
		
		@ActionMenu("Menu.Objects.Delete")
		public final DeleteActions deleteActions = new DeleteActions();

		@ActionMenu("Menu.Objects.Select")
		public final SelectActions selectActions = new SelectActions();
		
		@ActionMenu("Menu.Objects.Lock")
		public final LockActions lockActions = new LockActions();		

		public final Action SHOW_OBJECT_DESCRIPTIONS = commonActions.SHOW_OBJECT_DESCRIPTIONS;
		
		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.Objects.Annotations")
		public final AnnotationActions annotationActions = new AnnotationActions();
		
		@ActionConfig("Action.Objects.refreshIds")
		public final Action REFRESH_OBJECT_IDS = qupath.createImageDataAction(imageData -> Commands.refreshObjectIDs(imageData, false));

		@ActionConfig("Action.Objects.refreshDuplicateIds")
		public final Action REFRESH_DUPLICATE_OBJECT_IDS = qupath.createImageDataAction(imageData -> Commands.refreshObjectIDs(imageData, true));

	}

	public class DeleteActions {
		
		@ActionConfig("Action.Objects.Delete.selected")
		public final Action DELETE_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> GuiTools.promptToClearAllSelectedObjects(imageData));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionConfig("Action.Objects.Delete.all")
		public final Action DELETE_ALL = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, null));

		@ActionConfig("Action.Objects.Delete.annotations")
		public final Action DELETE_ANNOTATION = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathAnnotationObject.class));
		
		@ActionConfig("Action.Objects.Delete.detections")
		public final Action DELETE_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathDetectionObject.class));

		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionConfig("Action.Objects.Delete.imageBoundary")
		public final Action DELETE_IMAGE_BOUNDARY = qupath.createImageDataAction(Commands::removeOnImageBounds);

		private final DeleteObjectsOnBoundsCommand deleteOnBoundsCommand = new DeleteObjectsOnBoundsCommand(qupath);

		@ActionConfig("Action.Objects.Delete.selectedBoundary")
		public final Action DELETE_SELECTED_BOUNDARY = qupath.createImageDataAction(deleteOnBoundsCommand::runForImage);

	}

	public class SelectActions {
		
		@ActionConfig("Action.Objects.Select.reset")
		@ActionAccelerator("shortcut+alt+r")
		public final Action RESET_SELECTION = qupath.createImageDataAction(imageData -> Commands.resetSelection(imageData));

		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionConfig("Action.Objects.Select.tmaCores")
		@ActionAccelerator("shortcut+alt+t")
		public final Action SELECT_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, TMACoreObject.class));

		@ActionConfig("Action.Objects.Select.annotations")
		@ActionAccelerator("shortcut+alt+a")
		public final Action SELECT_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathAnnotationObject.class));

		@ActionMenu("Menu.Objects.Select.Detections")
		@ActionConfig("Action.Objects.Select.detections")
		@ActionAccelerator("shortcut+alt+d")
		public final Action SELECT_ALL_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathDetectionObject.class));

		@ActionMenu("Menu.Objects.Select.Detections")
		@ActionConfig("Action.Objects.Select.cells")
		@ActionAccelerator("shortcut+alt+c")
		public final Action SELECT_CELLS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathCellObject.class));
		
		@ActionMenu("Menu.Objects.Select.Detections")
		@ActionConfig("Action.Objects.Select.tiles")
		public final Action SELECT_TILES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathTileObject.class));

		public final Action SEP_3 = ActionTools.createSeparator();
		
		@ActionConfig("Action.Objects.Select.selectByClassification")
		public final Action SELECT_BY_CLASSIFICATION = qupath.createImageDataAction(imageData -> Commands.promptToSelectObjectsByClassification(qupath, imageData));

		@ActionConfig("Action.Objects.Select.selectOnCurrentPlane")
		public final Action SELECT_BY_PLANE = qupath.createViewerAction(viewer -> Commands.selectObjectsOnCurrentPlane(viewer));

		
	}

	public class LockActions {
		
		@ActionConfig("Action.Objects.Lock.lockSelected")
		@ActionAccelerator("shortcut+shift+k")
		public final Action LOCK_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> PathObjectTools.lockSelectedObjects(imageData.getHierarchy()));

		@ActionConfig("Action.Objects.Lock.unlockSelected")
		@ActionAccelerator("shortcut+alt+k")
		public final Action UNLOCK_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> PathObjectTools.unlockSelectedObjects(imageData.getHierarchy()));

		@ActionConfig("Action.Objects.Lock.toggleSelected")
		@ActionAccelerator("shortcut+k")
		public final Action TOGGLE_SELECTED_OBJECTS_LOCKED = qupath.createImageDataAction(imageData -> PathObjectTools.toggleSelectedObjectsLocked(imageData.getHierarchy()));
		
	}

	
	public class AnnotationActions {
		
		@ActionConfig("Action.Objects.Annotation.specify")
		public final Action SPECIFY_ANNOTATION = Commands.createSingleStageAction(() -> Commands.createSpecifyAnnotationDialog(qupath));

		@ActionAccelerator("shortcut+shift+a")
		@ActionConfig("Action.Objects.Annotation.fullImage")
		public final Action SELECT_ALL_ANNOTATION = qupath.createImageDataAction(imageData -> Commands.createFullImageAnnotation(qupath.getViewer()));

		public final Action SEP_5 = ActionTools.createSeparator();

		@ActionAccelerator("shortcut+shift+i")
		@ActionConfig("Action.Objects.Annotation.hierarchyInsert")
		public final Action INSERT_INTO_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.insertSelectedObjectsInHierarchy(imageData));

		@ActionAccelerator("shortcut+shift+r")
		@ActionConfig("Action.Objects.Annotation.hierarchyResolve")
		public final Action RESOLVE_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToResolveHierarchy(imageData));
		
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionAccelerator("shortcut+shift+t")
		@ActionConfig("Action.Objects.Annotation.transform")
		public final Action RIGID_OBJECT_EDITOR = qupath.createImageDataAction(imageData -> Commands.editSelectedAnnotation(qupath));

		@ActionAccelerator("shift+d")
		@ActionConfig("Action.Objects.Annotation.duplicate")
		public final Action ANNOTATION_DUPLICATE = qupath.createImageDataAction(imageData -> Commands.duplicateSelectedAnnotations(imageData));

		@ActionAccelerator("shortcut+shift+v")
		@ActionConfig("Action.Objects.Annotation.copyToCurrentPlane")
		public final Action ANNOTATION_COPY_TO_PLANE = qupath.createViewerAction(viewer -> Commands.copySelectedAnnotationsToCurrentPlane(viewer));

		@ActionAccelerator("shift+e")
		@ActionConfig("Action.Objects.Annotation.transferLast")
		public final Action TRANSFER_ANNOTATION = qupath.createImageDataAction(imageData -> qupath.getViewerManager().applyLastAnnotationToActiveViewer());

		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionConfig("Action.Objects.Annotation.expand")
		public final Action EXPAND_ANNOTATIONS = qupath.createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null);
		
		@ActionConfig("Action.Objects.Annotation.split")
		public final Action SPLIT_ANNOTATIONS = qupath.createPluginAction("Split annotations", SplitAnnotationsPlugin.class, null);

		private final SplitAnnotationsByLineCommand splitAnnotationsByLineCommand = new SplitAnnotationsByLineCommand();

		@ActionConfig("Action.Objects.Annotation.splitByLines")
		public final Action SPLIT_ANNOTATIONS_BY_LINES = qupath.createImageDataAction(imageData -> splitAnnotationsByLineCommand.run(imageData));

		@ActionConfig("Action.Objects.Annotation.removeFragmentsFillHoles")
		public final Action REMOVE_FRAGMENTS = qupath.createPluginAction("Remove fragments & holes", RefineAnnotationsPlugin.class, null);
		
		@ActionConfig("Action.Objects.Annotation.fillHoles")
		public final Action FILL_HOLES = qupath.createPluginAction("Fill holes", FillAnnotationHolesPlugin.class, null);

		public final Action SEP_8 = ActionTools.createSeparator();
		
		@ActionConfig("Action.Objects.Annotation.makeInverse")
		public final Action MAKE_INVERSE = qupath.createImageDataAction(imageData -> Commands.makeInverseAnnotation(imageData));
		
		@ActionConfig("Action.Objects.Annotation.mergeSelected")
		public final Action MERGE_SELECTED = qupath.createImageDataAction(imageData -> Commands.mergeSelectedAnnotations(imageData));
		
		@ActionConfig("Action.Objects.Annotation.simplify")
		public final Action SIMPLIFY_SHAPE = qupath.createImageDataAction(imageData -> Commands.promptToSimplifySelectedAnnotations(imageData, 1.0));
		
		
	}
	


}
