package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.io.IOException;
import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.gui.dialogs.Dialogs;
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
		return QuPathResources.getString("KEY:Menu.File.name");
	}
	
	
	@ActionMenu("KEY:Menu.File.name")
	public class Actions {
		
		@ActionDescription("Create a new project. " + 
				"Usually it's easier just to drag an empty folder onto QuPath to create a project, rather than navigate these menus.")
		@ActionMenu("KEY:Menu.File.Project.name.createProject")
		public final Action PROJECT_NEW = createAction(() -> Commands.promptToCreateProject(qupath));
		
		@ActionDescription("Open an existing project. " +
				"Usually it's easier just to drag a project folder onto QuPath to open it, rather than bother with this command.")
		@ActionMenu("KEY:Menu.File.Project.name.openProject")
		public final Action PROJECT_OPEN = createAction(() -> Commands.promptToOpenProject(qupath));
		
		@ActionDescription("Close the current project, including any images that are open.")
		@ActionMenu("KEY:Menu.File.Project.name.closeProject")
		public final Action PROJECT_CLOSE = qupath.createProjectAction(project -> Commands.closeProject(qupath));
		
		@ActionMenu("KEY:Menu.File.Project.name")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("Add images to the current project. " +
				"You can also add images by dragging files onto the main QuPath window.")
		@ActionMenu("KEY:Menu.File.Project.name.addImages")
		public final Action IMPORT_IMAGES = qupath.createProjectAction(project -> ProjectCommands.promptToImportImages(qupath));

		@ActionDescription("Export a list of the image paths for images in the current project.")
		@ActionMenu("Project...>Export image list")
		public final Action EXPORT_IMAGE_LIST = qupath.createProjectAction(project -> ProjectCommands.promptToExportImageList(project));	
		
		@ActionMenu("KEY:Menu.File.Project.name")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionDescription("Edit the metadata for the current project. " + 
				"By adding key-value properties to images, they can be sorted and queried more easily.")
		@ActionMenu("Project...>Edit project metadata")
		public final Action METADATA = qupath.createProjectAction(project -> ProjectCommands.showProjectMetadataEditor(project));
		
		@ActionDescription("Check the 'Uniform Resource Identifiers' for images in the current project. " +
				"This basically helps fix things whenever files have moved and images can no longer be found.")
		@ActionMenu("Project...>Check project URIs")
		public final Action CHECK_URIS = qupath.createProjectAction(project -> {
			try {
				ProjectCommands.promptToCheckURIs(project, false);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Check project URIs", e);
			}
		});
		
		@ActionMenu("Project...>")
		public final Action SEP_22= ActionTools.createSeparator();
		
		@ActionDescription("Import images from a legacy project (QuPath v0.1.2 or earlier)." +
				"\n\nNote that it is generally a bad idea to mix versions of QuPath for analysis, "
				+ "but this can be helpful to recover old data and annotations."
				+ "\n\nThe original images will need to be available, with the paths set correctly in the project file.")
		@ActionMenu("Project...>Import images from v0.1.2")
		public final Action IMPORT_IMAGES_LEGACY = qupath.createProjectAction(project -> ProjectCommands.promptToImportLegacyProject(qupath));


		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionDescription("Open an image in the current viewer, using a file chooser. " +
				"You can also just drag the file on top of the viewer.")
		@ActionMenu("Open...")
		@ActionAccelerator("shortcut+o")
		public final Action OPEN_IMAGE = createAction(() -> qupath.promptToOpenImageFile());
		
		@ActionDescription("Open an image in the current viewer, by entering the path to the image. " +
				"This can be used to add images that are not represented by local files (e.g. hosted by OMERO), " + 
				"but beware that a compatible image reader needs to be available to interpret them.")
		@ActionMenu("Open URI...")
		@ActionAccelerator("shortcut+shift+o")
		public final Action OPEN_IMAGE_OR_URL = createAction(() -> qupath.promptToOpenImageFileOrUri());
		
		@ActionDescription("Reload any previously-saved data for the current image. " +
				"This provides a more dramatic form of 'undo' (albeit without any 'redo' option).")
		@ActionMenu("Reload data")
		@ActionAccelerator("shortcut+r")
		public final Action RELOAD_DATA = qupath.createImageDataAction(imageData -> Commands.reloadImageData(qupath, imageData));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionDescription("Save a .qpdata file for this image, specifying the file path. " +
				"Warning! It is usually much better to use projects instead, and allow QuPath to decide where to store your data files.")
		@ActionMenu("Save As")
		@ActionAccelerator("shortcut+shift+s")
		public final Action SAVE_DATA_AS = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, false));

		@ActionDescription("Save a .qpdata file for this image. This command is best used within projects, where QuPath will choose the location to save the file.")
		@ActionMenu("Save")
		@ActionAccelerator("shortcut+s")
		public final Action SAVE_DATA = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, true));
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionDescription("Export an image region, by extracting the pixels from the original image.")
		@ActionMenu("Export images...>Original pixels")
		public final Action EXPORT_ORIGINAL = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), false));
		@ActionDescription("Export an image region, as an RGB image matching how it is displayed in the viewer.")
		@ActionMenu("Export images...>Rendered RGB (with overlays)")
		public final Action EXPORT_RENDERED = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), true));
		
		@ActionDescription("Export the area of the screen corresponding to the main QuPath window to the clipboard. " + 
				"This includes any additional overlapping windows and dialog boxes.")
		@ActionMenu("Export snapshot...>Main window screenshot")
		public final Action SNAPSHOT_WINDOW = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));
		
		@ActionDescription("Export the contents of the main QuPath window to the clipboard. " + 
				"This ignores any additional overlapping windows and dialog boxes.")
		@ActionMenu("Export snapshot...>Main window content")
		public final Action SNAPSHOT_WINDOW_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_SCENE));

		@ActionDescription("Export the contents of the current viewer to the clipboard. " + 
				"Note that this creates an RGB image, which does not necessarily contain the original pixel values.")
		@ActionMenu("Export snapshot...>Current viewer content")
		public final Action SNAPSHOT_VIEWER_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.VIEWER));
		
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionDescription("Import objects from GeoJSON or .qpdata files.")
		@ActionMenu("Import objects from file")
		public final Action OBJECT_IMPORT= qupath.createImageDataAction(imageData -> Commands.runObjectImport(qupath, imageData));

		@ActionDescription("Export objects in GeoJSON format to file.")
		@ActionMenu("Export objects as GeoJSON")
		public final Action EXPORT_GEOJSON = qupath.createImageDataAction(imageData -> Commands.runGeoJsonObjectExport(qupath, imageData));
		
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionDescription("Import a TMA map, e.g. a grid containing 'Unique ID' values for each core.")
		@ActionMenu("TMA data...>Import TMA map")
		public final Action TMA_IMPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToImportTMAData(imageData));

		@ActionDescription("Export TMA data for the current image, in a format compatible with the 'TMA data viewer'.")
		@ActionMenu("TMA data...>Export TMA data")
		public final Action TMA_EXPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToExportTMAData(qupath, imageData));
		
		@ActionDescription("Launch the 'TMA data viewer' to visualize TMA core data that was previously exported.")
		@ActionMenu("TMA data...>Launch TMA data viewer")
		public final Action TMA_VIEWER = createAction(() -> Commands.launchTMADataViewer(qupath));

		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionDescription("Quit QuPath.")
		@ActionMenu("Quit")
		public final Action QUIT = new Action("Quit", e -> qupath.sendQuitRequest());

	}


}
