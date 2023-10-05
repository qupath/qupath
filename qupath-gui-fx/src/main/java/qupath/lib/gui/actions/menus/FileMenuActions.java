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

import java.io.IOException;
import java.util.List;

import org.controlsfx.control.action.Action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.GuiTools;

public class FileMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	public FileMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
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
		return QuPathResources.getString("Menu.File");
	}
	
	
	@ActionMenu("Menu.File")
	public class Actions {
		
		@ActionMenu("Menu.File.Project")
		public final ProjectActions projectActions = new ProjectActions();

		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionConfig("Action.File.open")
		@ActionAccelerator("shortcut+o")
		public final Action OPEN_IMAGE = createAction(() -> qupath.promptToOpenImageFile());
		
		@ActionConfig("Action.File.openUri")
		@ActionAccelerator("shortcut+shift+o")
		public final Action OPEN_IMAGE_OR_URL = createAction(() -> qupath.promptToOpenImageFileOrUri());
		
		@ActionConfig("Action.File.reloadData")
		@ActionAccelerator("shortcut+r")
		public final Action RELOAD_DATA = qupath.createImageDataAction(imageData -> Commands.reloadImageData(qupath, imageData));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionConfig("Action.File.saveAs")
		@ActionAccelerator("shortcut+shift+s")
		public final Action SAVE_DATA_AS = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, false));

		@ActionConfig("Action.File.save")
		@ActionAccelerator("shortcut+s")
		public final Action SAVE_DATA = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, true));
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.File.ExportImage")
		public final ExportImageActions exportImageActions = new ExportImageActions();
		
		@ActionMenu("Menu.File.ExportSnapshot")
		public final ExportSnapshotActions exportSnapshotActions = new ExportSnapshotActions();

		
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionConfig("Action.File.importObjects")
		public final Action OBJECT_IMPORT= qupath.createImageDataAction(imageData -> Commands.runObjectImport(qupath, imageData));

		@ActionConfig("Action.File.exportGeoJSON")
		public final Action EXPORT_GEOJSON = qupath.createImageDataAction(imageData -> Commands.runGeoJsonObjectExport(qupath, imageData));
		
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionMenu("Menu.File.TMA")
		public final TmaActions tmaActions = new TmaActions();

		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionConfig("Action.File.quit")
		public final Action QUIT = new Action(e -> qupath.sendQuitRequest());

	}
	
	
	public class ProjectActions {

		private static final Logger logger = LoggerFactory.getLogger(ProjectActions.class);
		
		@ActionConfig("Action.File.Project.createProject")
		public final Action PROJECT_NEW = qupath.getCommonActions().PROJECT_NEW;
		
		@ActionConfig("Action.File.Project.openProject")
		public final Action PROJECT_OPEN = qupath.getCommonActions().PROJECT_OPEN;
		
		@ActionConfig("Action.File.Project.closeProject")
		public final Action PROJECT_CLOSE = qupath.createProjectAction(project -> Commands.closeProject(qupath));
		
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionConfig("Action.File.Project.addImages")
		public final Action IMPORT_IMAGES = qupath.getCommonActions().PROJECT_ADD_IMAGES;

		@ActionConfig("Action.File.Project.exportImageList")
		public final Action EXPORT_IMAGE_LIST = qupath.createProjectAction(project -> ProjectCommands.promptToExportImageList(project));	
		
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionConfig("Action.File.Project.editMetadata")
		public final Action METADATA = qupath.createProjectAction(project -> ProjectCommands.showProjectMetadataEditor(project));
		
		@ActionConfig("Action.File.Project.checkUris")
		public final Action CHECK_URIS = qupath.createProjectAction(project -> {
			try {
				ProjectCommands.promptToCheckURIs(project, false);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Check project URIs", e);
				logger.error(e.getMessage(), e);
			}
		});
		
		public final Action SEP_22= ActionTools.createSeparator();
		
		@ActionConfig("Action.File.Project.importLegacy")
		public final Action IMPORT_IMAGES_LEGACY = qupath.createProjectAction(project -> ProjectCommands.promptToImportLegacyProject(qupath));
		
	}
	
	
	public class ExportImageActions {
		
		@ActionConfig("Action.File.ExportImage.original")
		public final Action EXPORT_ORIGINAL = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), false));
		
		@ActionConfig("Action.File.ExportImage.rendered")
		public final Action EXPORT_RENDERED = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), true));

	}
	
	public class ExportSnapshotActions {
		
		@ActionConfig("Action.File.ExportSnapshot.windowScreenshot")
		public final Action SNAPSHOT_WINDOW = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));
		
		@ActionConfig("Action.File.ExportSnapshot.windowContent")
		public final Action SNAPSHOT_WINDOW_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_SCENE));

		@ActionConfig("Action.File.ExportSnapshot.viewerContent")
		public final Action SNAPSHOT_VIEWER_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.VIEWER));
		
	}
	
	
	public class TmaActions {
		
		@ActionConfig("Action.File.TMA.importMap")
		public final Action TMA_IMPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToImportTMAData(imageData));

		@ActionConfig("Action.File.TMA.exportData")
		public final Action TMA_EXPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToExportTMAData(qupath, imageData));
		
		@ActionConfig("Action.File.TMA.dataViewer")
		public final Action TMA_VIEWER = createAction(() -> Commands.launchTMADataViewer(qupath));
		
	}
	


}
