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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.images.ImageData;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.Projects;
import qupath.lib.gui.scripting.DefaultScriptEditor;


/**
 * Drag and drop support for main QuPath application, which supports a range of different supported file types.
 * 
 * @author Pete Bankhead
 *
 */
public class DragDropFileImportListener implements EventHandler<DragEvent> {
	
	final private static Logger logger = LoggerFactory.getLogger(DragDropFileImportListener.class);

	private QuPathGUI qupath;
	
	private List<FileDropHandler> fileDropHandlers = new ArrayList<>();

	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public DragDropFileImportListener(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	/**
	 * Prepare a target node to accept drag and drop events.
	 * @param target
	 */
	public void setupTarget(final Node target) {
		target.setOnDragOver(event -> {
        	// Remove this condition if the user can drag onto anything, not only a canvas
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
		target.setOnDragDropped(this);
	}
	
	/**
	 * Prepare a target scene to accept drag and drop events.
	 * @param target
	 */
	public void setupTarget(final Scene target) {
		target.setOnDragOver(event -> {
        	// Remove this condition if the user can drag onto anything, not only a canvas
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
		target.setOnDragDropped(this);
	}
	
	
    @Override
    public void handle(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        Object source = event.getSource();
        // Look for the viewer that we dragged on to - may be null, if drag was on
        QuPathViewer viewer = null;
        for (QuPathViewer viewer2 : qupath.getViewers()) {
        	if (viewer2.getView() == source) {
        		viewer = viewer2;
        		break;
        	}
        }
        // If only one viewer is available, there is no ambiguity... use it
        if (viewer == null && qupath.getViewers().size() == 1)
        	viewer = qupath.getViewer();
        
		if (dragboard.hasFiles()) {
	        logger.debug("Files dragged onto {}", source);
			try {
				handleFileDrop(viewer, dragboard.getFiles());
			} catch (IOException e) {
				Dialogs.showErrorMessage("Drag & Drop", e);
			}
		}
		event.setDropCompleted(true);
		event.consume();
    }
    
    /**
     * Add a new FileDropHandler.
     * <p>
     * This may be called on a drag-and-drop application on the main window, if no other 
     * handler deals with the event.
     * 
     * @param handler
     */
	public void addFileDropHandler(final FileDropHandler handler) {
		this.fileDropHandlers.add(handler);
	}

	/**
	 * Remove a FileDropHandler.
	 * 
	 * @param handler
	 */
	public void removeFileDropHandler(final FileDropHandler handler) {
		this.fileDropHandlers.remove(handler);
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
    
    private void handleFileDropImpl(final QuPathViewer viewer, final List<File> list) throws IOException {
		
		// Shouldn't occur... but keeps FindBugs happy to check
		if (list == null) {
			logger.warn("No files given!");
			return;
		}

		
		// Check if we have only jar files
		int nJars = 0;
		for (File file : list) {
			if (file.getName().toLowerCase().endsWith(".jar"))
				nJars++;
		}
		if (nJars == list.size()) {
			if (qupath.canInstallExtensions())
				qupath.installExtensions(list);
			else
				Dialogs.showErrorMessage("Install extensions", "Sorry, extensions can only be installed when QuPath is run as a standalone application.");
			return;
		}
		

		// Try to get a hierarchy for importing ROIs
		ImageData<BufferedImage> imageData = viewer == null ? null : viewer.getImageData();
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();

		boolean singleFile = list != null && list.size() == 1;
		for (File file : list) {
			String fileName = file.getName().toLowerCase();

			// Check if this is a hierarchy file
			if (singleFile && (fileName.endsWith(PathPrefs.getSerializationExtension()))) {

				// If we have a different path, open as a new image
				if (viewer == null) {
					Dialogs.showErrorMessage("Open data", "Please drag the file onto a specific viewer to open!");
					break;
				}
				try {
					qupath.openSavedData(viewer, file, false, true);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Open image", e);
				}
				return;
			}
			
			// Check if this is a directory - if so, look for a single project file
			if (singleFile && file.isDirectory()) {
				// Identify all files in the directory, and also all potential project files
				File[] filesInDirectory = file.listFiles(f -> !f.isHidden());
				List<File> projectFiles = Arrays.stream(filesInDirectory).filter(f -> f.isFile() && 
						f.getAbsolutePath().toLowerCase().endsWith(ProjectIO.getProjectExtension())).collect(Collectors.toList());
				if (projectFiles.size() == 1) {
					file = projectFiles.get(0);
					logger.warn("Selecting project file {}", file);
				} else if (projectFiles.size() > 1) {
					// Prompt to select which project file to open
					logger.debug("Multiple project files found in directory {}", file);
					String[] fileNames = projectFiles.stream().map(f -> f.getName()).toArray(n -> new String[n]);
					String selectedName = Dialogs.showChoiceDialog("Select project", "Select project to open", fileNames, fileNames[0]);
					if (selectedName == null)
						return;
					file = new File(file, selectedName);
				} else if (filesInDirectory.length == 0) {
					// If we have an empty directory, offer to set it as a project
					if (Dialogs.showYesNoDialog("Create project", "Create project for empty directory?")) {
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
					Dialogs.showErrorMessage("Project error", e);
//					logger.error("Could not open as project file: {}", e);
				}
				return;
			}
			
			// Check if this is TMA dearraying data file
			if (singleFile && (fileName.endsWith(TMADataIO.TMA_DEARRAYING_DATA_EXTENSION))) {
				if (hierarchy == null)
					Dialogs.showErrorMessage("TMA grid import", "Please open an image first before importing a dearrayed TMA grid!");
				else {
					TMAGrid tmaGrid = TMADataIO.importDearrayedTMAData(file);
					if (tmaGrid != null) {
						if (hierarchy.isEmpty() || Dialogs.showYesNoDialog("TMA grid import", "Set TMA grid for existing hierarchy?"))
							hierarchy.setTMAGrid(tmaGrid);
					} else
						Dialogs.showErrorMessage("TMA grid import", "Could not parse TMA grid from " + file.getName());
				}
				return;
			}


			// Open Javascript
			ScriptEditor scriptEditor = qupath.getScriptEditor();
			if (scriptEditor instanceof DefaultScriptEditor && ((DefaultScriptEditor)scriptEditor).supportsFile(file)) {
				scriptEditor.showScript(file);
				return;
			}

			
			// Check handlers
			for (FileDropHandler handler : fileDropHandlers) {
				if (handler.handleFileDrop(viewer, list))
					return;
			}

			// Assume we have images
			if (singleFile && file.isFile()) {
				// Try to open as an image, if the extension is known
				if (viewer == null) {
					Dialogs.showErrorMessage("Open image", "Please drag the file only a specific viewer to open!");
					return;
				}
				qupath.openImage(viewer, file.getAbsolutePath(), true, true);
				return;
			} else if (qupath.getProject() != null) {
				// Try importing multiple images to a project
				String[] potentialFiles = list.stream().filter(f -> f.isFile()).map(f -> f.getAbsolutePath()).toArray(String[]::new);
				if (potentialFiles.length > 0) {
					ProjectCommands.promptToImportImages(qupath, potentialFiles);
					return;
				}
			}


		}
		if (qupath.getProject() == null) {
			if (list.size() > 1) {
				Dialogs.showErrorMessage("Drag & drop", "Could not handle multiple file drop - if you want to handle multiple images, you need to create a project first");
				return;
			}
    	}
		if (list.size() > 1)
			Dialogs.showErrorMessage("Drag & drop", "Sorry, I couldn't figure out what to do with these files - try opening one at a time");
		else
			Dialogs.showErrorMessage("Drag & drop", "Sorry, I couldn't figure out what to do with " + list.get(0).getName());
	}
    
    
    /**
     * Interface to define a new file drop handler for a particular kind of file.
     * 
     * @author Pete Bankhead
     *
     */
    public static interface FileDropHandler {
    	
    	/**
    	 * Handle file drop onto a viewer.
    	 * This makes it possible to drop images (for example) onto a specific viewer to open them in that viewer, 
    	 * irrespective of whether the viewer is active currently.
    	 * 
    	 * @param viewer the active viewer, or the viewer only which the files were dropped
    	 * @param list the dropped files
    	 * @return true if the handler processed the file drop event
    	 */
    	public boolean handleFileDrop(final QuPathViewer viewer, final List<File> list);
    	
    }
    
       
}