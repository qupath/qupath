/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import qupath.ext.extensionmanager.gui.ExtensionManager;
import qupath.lib.common.GeneralTools;

import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.FileCopier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.UserDirectoryManager;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.InteractiveObjectImporter;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.Projects;
import qupath.lib.gui.scripting.DefaultScriptEditor;


/**
 * Drag and drop support for main QuPath application, which can support a range of different object types (Files, URLs, Strings,..)
 */
public class DragDropImportListener implements EventHandler<DragEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(DragDropImportListener.class);

	private static final Pattern GITHUB_BASE_PATTERN = Pattern.compile("https://github.com/.*");

	private final QuPathGUI qupath;
	
	private List<DropHandler<File>> dropHandlers = new ArrayList<>();

	private List<DropHandler<JsonElement>> jsonDropHandlers = new ArrayList<>();

	/**
	 * Flag to indicate that a task is currently running, and events should be dropped until it is finished
	 * (e.g. a dialog is showing and we need a response)
	 */
	private boolean taskRunning = false;
	
	/**
	 * Schedule long-running tasks
	 */
	private Timer timer = new Timer("drag-drop-timer", true);

	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public DragDropImportListener(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	/**
	 * Prepare a target node to accept drag and drop events.
	 * @param target
	 */
	public void setupTarget(final Node target) {
		target.setOnDragOver(this);
		target.setOnDragDropped(this);
		target.setOnDragDone(this);
	}
	
	/**
	 * Prepare a target scene to accept drag and drop events.
	 * @param target
	 */
	public void setupTarget(final Scene target) {
		target.setOnDragOver(this);
		target.setOnDragDropped(this);
		target.setOnDragDone(this);
	}
	
	
    @Override
    public void handle(DragEvent event) {
    	
    	// Reject drag/drop if a task is already running (e.g. we're waiting on a response 
    	// to a dialog that showed when opening an image)
    	if (taskRunning)
    		return;
    	
    	var type = event.getEventType();
    	if (type == DragEvent.DRAG_DONE) {
    		logger.debug("Drag-drop done");
    		return;
    	} else if (type == DragEvent.DRAG_OVER) {
    		// Start drag/drop
    		var dragboard = event.getDragboard();
    		if (dragboard.hasFiles() || dragboard.hasUrl() || dragboard.hasString()) {
    			event.acceptTransferModes(TransferMode.COPY);
                event.consume();
                return;
    		}
    	} else if (type != DragEvent.DRAG_DROPPED) {
    		logger.warn("Unexpected drag-drop event {}", event);
    		return;
    	}

        Dragboard dragboard = event.getDragboard();
        Object source = event.getSource();
        
        // Look for the viewer that we dragged on to - may be null, if drag was on
        QuPathViewer viewer = null;
        for (QuPathViewer viewer2 : qupath.getAllViewers()) {
        	if (viewer2.getView() == source) {
        		viewer = viewer2;
        		break;
        	}
        }
        
        // The gesture source is null if originating from another application
        // In that case, we don't want to process everything here because we can end up 
        // blocking the other application until things are finished
        // (i.e. weird non-responsiveness was spotted on macOS Finder,
        //  as well as odd shadowed icons lingering behind open dialogs on Windows)
        long delay = 0L;
        if (event.getGestureSource() == null) {
        	delay = 50L;
        	logger.debug("Setting drag-drop delay to {} ms", delay);
        }
        
        // If only one viewer is available, there is no ambiguity... use it
        if (viewer == null && qupath.getAllViewers().size() == 1)
        	viewer = qupath.getViewer();
        
        var files = dragboard.hasFiles() ? new ArrayList<>(dragboard.getFiles()) : null;
        var url = dragboard.getUrl();
		var string = dragboard.getString();
        var viewer2 = viewer;
        if (files != null || url != null || string != null) {
	        invokeLater(() -> {
	        	taskRunning = true;
	        	try {
					if (files != null) {
				        logger.debug("Files dragged onto {}", source);
						handleFileDrop(viewer2, files);
					} else if (url != null ) {
						logger.debug("URL {} dragged onto {}", url, source);
						handleURLDrop(viewer2, url);
					} else if (string != null) {
						logger.debug("Text dragged onto {}, treating as a URL", source);
						handleURLDrop(viewer2, string);
					}
				} catch (IOException | URISyntaxException | InterruptedException e) {
					Dialogs.showErrorMessage(QuPathResources.getString("DragDrop"), e);
					logger.error(e.getMessage(), e);
	        	} finally {
	        		taskRunning = false;
	        	}
	        }, delay);
			event.setDropCompleted(true);
        } else
        	event.setDropCompleted(false);
		event.consume();
    }
    
    
    /**
     * Invoke a task, possibly after a delay
     * @param runnable
     * @param millis
     */
    void invokeLater(Runnable runnable, long millis) {
    	assert Platform.isAccessibilityActive();
    	if (millis <= 0)
    		runnable.run();
    	else {
    		var task = new TimerTask() {
    			@Override
    			public void run() {
    				Platform.runLater(runnable);
    			}
    		};
    		timer.schedule(task, millis);
    	}
    }
    


	/**
     * Add a new File DropHandler.
     * <p>
     * This may be called on a drag-and-drop application on the main window, if no other 
     * handler deals with the event.
     * 
     * @param handler
     */
	public void addFileDropHandler(final DropHandler<File> handler) {
		this.dropHandlers.add(handler);
	}
	

	/**
	 * Remove a File DropHandler.
	 * 
	 * @param handler
	 */
	public void removeFileDropHandler(final DropHandler<File> handler) {
		this.dropHandlers.remove(handler);
	}

	/**
	 * Add a new DropHandler specifically for JSON elements.
	 * <p>
	 * This may be called when a json file is dropped on the main QuPath window.
	 * Handlers should quickly inspect the element and return if they cannot handle it.
	 *
	 * @param handler
	 */
	public void addJsonDropHandler(final DropHandler<JsonElement> handler) {
		this.jsonDropHandlers.add(handler);
	}


	/**
	 * Remove a JSON DropHandler.
	 *
	 * @param handler
	 */
	public void removeJsonDropHandler(final DropHandler<JsonElement> handler) {
		this.jsonDropHandlers.remove(handler);
	}
    
    void handleFileDrop(final QuPathViewer viewer, final List<File> list) throws IOException {
    	try {
    		handleFileDropImpl(viewer, list);
    	} catch (IOException e) {
    		throw e;
    	} catch (Throwable e) {
    		throw new IOException(e);
    	}
    }
    
    void handleURLDrop(final QuPathViewer viewer, final String url) throws IOException, URISyntaxException, InterruptedException {
    	try {
    		qupath.openImage(viewer, url, false, false);
    	} catch (IOException e) {
    		throw e;
    	} catch (Throwable e) {
    		throw new IOException(e);
    	}
    }


	private void handleFileDropImpl(QuPathViewer viewer, List<File> list) throws IOException {
		
		// Shouldn't occur... but keeps FindBugs happy to check
		if (list == null || list.isEmpty()) {
			logger.warn("No files given!");
			return;
		}

		// Check if we have only jar or css files
		int nJars = 0;
		int nCss = 0;
		int nJson = 0;
		for (File file : list) {
			// Use the canonical file in case we have a symlink
			var ext = GeneralTools.getExtension(file.getCanonicalFile()).orElse("").toLowerCase();
			if (ext.equals(".jar"))
				nJars++;
			else if (ext.equals(".css"))
				nCss++;
			else if (ext.equals(".json"))
				nJson++;
		}

		// If we only have jar files, treat them as extensions
		if (nJars == list.size()) {
			ExtensionManager.promptToCopyFilesToExtensionDirectory(
					list,
					QuPathGUI.getExtensionCatalogManager().getExtensionDirectoryPath(),
					() -> Commands.requestUserDirectory(true)
			);
			return;
		}
		
		// Handle properties files
		list = handlePropertiesFiles(list);
		if (list.isEmpty())
			return;
		
		// Handle installing CSS files (styles)
		if (nCss == list.size()) {
			QuPathStyleManager.installStyles(list);
			return;
		}

		// Handle JSON files
		if (nJson == list.size()) {
			List<JsonElement> elements = new ArrayList<>();
			var gson = GsonTools.getInstance();
			// TODO: Note that this is inefficient if we have GeoJSON that we don't handle, since the file is read twice
			for (var file : list) {
				try (var reader = Files.newBufferedReader(file.toPath())) {
					elements.add(gson.fromJson(reader, JsonElement.class));
				} catch (IOException ex) {
					logger.error("Could not read JSON file {}", file, ex);
				}
			}
			for (DropHandler<JsonElement> handler: jsonDropHandlers) {
				if (handler.handleDrop(viewer, elements))
					return;
			}
		}

		// Try to get a hierarchy for importing ROIs
		ImageData<BufferedImage> imageData = viewer == null ? null : viewer.getImageData();
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();

		// Some consumers can only handle one file
		boolean singleFile = list.size() == 1;
		
//		// Gather together the extensions - if this has length one, we know all the files have the same extension
//		Set<String> allExtensions = list.stream().map(f -> GeneralTools.getExtension(f).orElse("")).collect(Collectors.toSet());
		
		// If we have a zipped file, create a set that includes the files within the zip image
		// This helps us determine whether or not a zip file contains an image or objects, for example
		Set<String> allUnzippedExtensions = list.stream().flatMap(f -> {
			try {
				return PathIO.unzippedExtensions(f.getCanonicalFile().toPath()).stream();
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage(), e);
				return Arrays.stream(new String[0]);
			}
		}).collect(Collectors.toSet());
		
		// Extract the first (and possibly only) file
		File file = list.getFirst();

		// Get the name of the file using the canonical file, in case we have a symlink
		String fileName = file.getCanonicalFile().getName().toLowerCase();

		// Check if this is a hierarchy file
		if (singleFile && (fileName.endsWith(PathPrefs.getSerializationExtension()))) {

			// If we have a different path, open as a new image
			if (viewer == null) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("DragDrop.loadData"),
						QuPathResources.getString("DragDrop.loadMessage"));
				return;
			}
			try {
				// Check if we should be importing objects or opening the file
				if (imageData != null) {
					var dialog = new Dialog<ButtonType>();
					var btOpen = new ButtonType(QuPathResources.getString("DragDrop.openImage"));
					var btImport = new ButtonType(QuPathResources.getString("DragDrop.importObjects"));
					dialog.getDialogPane().getButtonTypes().setAll(btOpen, btImport, ButtonType.CANCEL);
					dialog.setTitle(QuPathResources.getString("DragDrop.openData"));
					dialog.setHeaderText(QuPathResources.getString("DragDrop.chooseData"));
					dialog.setContentText(QuPathResources.getString("DragDrop.chooseDataOptions"));
//						dialog.setHeaderText("What do you want to do?");
					var choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
					if (choice == ButtonType.CANCEL)
						return;
					if (choice == btImport) {
						InteractiveObjectImporter.promptToImportObjectsFromFile(imageData, file);
						return;
					}
				}
				qupath.openSavedData(viewer, file, false, true);
			} catch (Exception e) {
				Dialogs.showErrorMessage(QuPathResources.getString("DragDrop.loadData"), e);
				logger.error(e.getMessage(), e);
			}
			return;
		}
		
		// Check if this is a directory - if so, look for a single project file
		boolean maybeZarr = file.isDirectory() && file.getName().toLowerCase().endsWith(".zarr");
		if (singleFile && file.isDirectory() && !maybeZarr) {
			// Identify all files in the directory, and also all potential project files
			File[] filesInDirectory = file.listFiles(f -> !f.isHidden());
			if (filesInDirectory == null) {
				// This shouldn't happen because we already checked if it's a directory
				logger.warn("Could not list files in directory {}", file);
				filesInDirectory = new File[0];
			}
			List<File> projectFiles = Arrays.stream(filesInDirectory).filter(f -> f.isFile() &&
						f.getAbsolutePath().toLowerCase().endsWith(ProjectIO.getProjectExtension())).toList();
			if (projectFiles.size() == 1) {
				file = projectFiles.getFirst();
				fileName = file.getName().toLowerCase();
				logger.warn("Selecting project file {}", file);
			} else if (projectFiles.size() > 1) {
				// Prompt to select which project file to open
				logger.debug("Multiple project files found in directory {}", file);
				String[] fileNames = projectFiles.stream().map(File::getName).toArray(String[]::new);
				String selectedName = Dialogs.showChoiceDialog(
						QuPathResources.getString("DragDrop.selectProject"),
						QuPathResources.getString("DragDrop.selectProjectToOpen"), fileNames, fileNames[0]);
				if (selectedName == null)
					return;
				file = new File(file, selectedName);
				fileName = file.getName().toLowerCase();
			} else if (filesInDirectory.length == 0) {
				// If we have an empty directory, offer to set it as a project
				if (Dialogs.showYesNoDialog(
						QuPathResources.getString("DragDrop.createProject"),
						QuPathResources.getString("DragDrop.createProjectForEmptyDirectory"))) {
					Project<BufferedImage> project = Projects.createProject(file, BufferedImage.class);
					qupath.setProject(project);
					if (!project.isEmpty())
						project.syncChanges();
					return;
				} else
					// Can't do anything else with an empty folder
					return;
			}
		}

		// Check if this is a project
		if (singleFile && (fileName.endsWith(ProjectIO.getProjectExtension()))) {
			try {
				Project<BufferedImage> project = ProjectIO.loadProject(file, BufferedImage.class);
				qupath.setProject(project);
			} catch (Exception e) {
				logger.error("Could not open as project file - opening in the Script Editor instead", e);
				qupath.getScriptEditor().showScript(file);
			}
			return;
		}
		
		// Check if it is an object file in GeoJSON format (.geojson)
		if (PathIO.getObjectFileExtensions(false).containsAll(allUnzippedExtensions)) {
			if (imageData == null || hierarchy == null) {
				qupath.getScriptEditor().showScript(file);
				logger.info("Opening the dragged file in the Script Editor as there is no currently opened image in the viewer");
				return;
			}
			InteractiveObjectImporter.promptToImportObjectsFromFile(imageData, file);
			return;
		}
		
		// Check if this is TMA dearraying data file
		if (singleFile && (fileName.endsWith(TMADataIO.TMA_DEARRAYING_DATA_EXTENSION))) {
			if (hierarchy == null)
				Dialogs.showErrorMessage(
						QuPathResources.getString("DragDrop.TMAGridImport"),
						QuPathResources.getString("DragDrop.TMAGridImportImageError")
						);
			else {
				TMAGrid tmaGrid = TMADataIO.importDearrayedTMAData(file);
				if (tmaGrid != null) {
					if (hierarchy.isEmpty() || Dialogs.showYesNoDialog(
							QuPathResources.getString("DragDrop.TMAGridImport"),
							QuPathResources.getString("DragDrop.TMAGridSetHierarchy")
							))
						hierarchy.setTMAGrid(tmaGrid);
				} else
					Dialogs.showErrorMessage(
							QuPathResources.getString("DragDrop.TMAGrid"),
							String.format(QuPathResources.getString("DragDrop.TMAGridParseError"), file.getName()));
			}
			return;
		}

		
		// Check handlers
		for (DropHandler<File> handler: dropHandlers) {
			if (handler.handleDrop(viewer, list))
				return;
		}

		// Open file with an extension supported by the Script Editor
		ScriptEditor scriptEditor = qupath.getScriptEditor();
		if (scriptEditor instanceof DefaultScriptEditor && ((DefaultScriptEditor)scriptEditor).supportsFile(file)) {
			scriptEditor.showScript(file);
			return;
		}

		// Assume we have images
		if (singleFile && (file.isFile() || maybeZarr)) {
			// Try to open as an image, if the extension is known
			if (viewer == null) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("DragDrop.openImage"),
						QuPathResources.getString("DragDrop.specificViewer"));
				return;
			}
			qupath.openImage(viewer, file.getAbsolutePath(), true, true);
			return;
		} else if (qupath.getProject() != null) {
			// Try importing multiple images to a project
			String[] potentialFiles = list.stream().filter(File::isFile).map(File::getAbsolutePath).toArray(String[]::new);
			if (potentialFiles.length > 0) {
				ProjectCommands.promptToImportImages(qupath, potentialFiles);
				return;
			}
		}

		if (qupath.getProject() == null) {
			if (list.size() > 1) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("DragDrop"),
						QuPathResources.getString("DragDrop.projectForMultipleFiles"));
				return;
			}
    	}
		if (list.size() > 1)
			Dialogs.showErrorMessage(
					QuPathResources.getString("DragDrop"),
					QuPathResources.getString("DragDrop.couldNotHandleFiles"));
		else
			Dialogs.showErrorMessage(
					QuPathResources.getString("DragDrop"),
					String.format(QuPathResources.getString("DragDrop.couldNotHandleFile"), list.get(0).getName()));
	}
    
     
    private List<File> handlePropertiesFiles(List<File> files) {
    	var propertyFiles = files.stream().filter(f -> f.getName().toLowerCase().endsWith(".properties")).toList();
    	if (propertyFiles.isEmpty())
    		return files;
    	
    	new FileCopier()
    			.title("Install localization properties")
    			.relativeToUserDirectory()
    			.outputPath(UserDirectoryManager.DIR_LOCALIZATION)
    			.inputFiles(propertyFiles)
    			.doCopy();
    	
    	if (propertyFiles.size() == files.size())
    		return Collections.emptyList();
    	var remainingFiles = new ArrayList<>(files);
    	remainingFiles.removeAll(propertyFiles);
    	return remainingFiles;
    }
    
    
    /**
     * Interface to define a new drop handler.
     * 
     * @author Pete Bankhead
     * @author Melvin Gelbard
     * @param <T> 
     *
     */
     @FunctionalInterface
    public interface DropHandler<T> {
    	 
    	/**
    	 * Handle drop onto a viewer.
    	 * This makes it possible to drop images (for example) onto a specific viewer to open them in that viewer, 
    	 * irrespective of whether the viewer is active currently.
    	 * 
    	 * @param viewer the active viewer, or the viewer only which the object were dropped
    	 * @param list the dropped objects
    	 * @return true if the handler processed the drop event
    	 */
    	boolean handleDrop(final QuPathViewer viewer, final List<T> list);
    	
    }
    
       
}
