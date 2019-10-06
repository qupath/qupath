/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
import qupath.lib.gui.commands.ProjectImportImagesCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
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
 * Drag 'n drop support for main QuPath application, which supports a range of different supported file types.
 * 
 * @author Pete Bankhead
 *
 */
public class DragDropFileImportListener implements EventHandler<DragEvent> {
	
	final private static Logger logger = LoggerFactory.getLogger(DragDropFileImportListener.class);

	private QuPathGUI gui;
	
	private List<FileDropHandler> fileDropHandlers = new ArrayList<>();

	public DragDropFileImportListener(final QuPathGUI gui) {
		this.gui = gui;
	}
	
	
	public void setupTarget(final Node target) {
		target.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
            	// Remove this condition if the user can drag onto anything, not only a canvas
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
		target.setOnDragDropped(this);
	}
	
	public void setupTarget(final Scene target) {
		target.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
            	// Remove this condition if the user can drag onto anything, not only a canvas
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
		target.setOnDragDropped(this);
	}
	
	
    @Override
    public void handle(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        Object source = event.getSource();
        // Look for the viewer that we dragged on to - may be null, if drag was on
        QuPathViewer viewer = null;
        for (QuPathViewer viewer2 : gui.getViewers()) {
        	if (viewer2.getView() == source) {
        		viewer = viewer2;
        		break;
        	}
        }
        // If only one viewer is available, there is no ambiguity... use it
        if (viewer == null && gui.getViewers().size() == 1)
        	viewer = gui.getViewer();
        
		if (dragboard.hasFiles()) {
	        logger.debug("Files dragged onto {}", source);
			try {
				handleFileDrop(viewer, dragboard.getFiles());
			} catch (IOException e) {
				DisplayHelpers.showErrorMessage("Drag & Drop", e);
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
    
    public void handleFileDrop(final QuPathViewer viewer, final List<File> list) throws IOException {
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
			if (gui.canInstallExtensions())
				gui.installExtensions(list);
			else
				DisplayHelpers.showErrorMessage("Install extensions", "Sorry, extensions can only be installed when QuPath is run as a standalone application.");
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
					DisplayHelpers.showErrorMessage("Open data", "Please drag the file onto a specific viewer to open!");
					break;
				}
				try {
					gui.openSavedData(viewer, file, false, true);
				} catch (Exception e) {
					DisplayHelpers.showErrorMessage("Open image", e);
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
					String selectedName = DisplayHelpers.showChoiceDialog("Select project", "Select project to open", fileNames, fileNames[0]);
					if (selectedName == null)
						return;
					file = new File(file, selectedName);
				} else if (filesInDirectory.length == 0) {
					// If we have an empty directory, offer to set it as a project
					if (DisplayHelpers.showYesNoDialog("Create project", "Create project for empty directory?")) {
						Project<BufferedImage> project = Projects.createProject(file, BufferedImage.class);
						gui.setProject(project);
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
					gui.setProject(project);
				} catch (Exception e) {
					DisplayHelpers.showErrorMessage("Project error", e);
//					logger.error("Could not open as project file: {}", e);
				}
				return;
			}
			
			// Check if this is TMA dearraying data file
			if (singleFile && (fileName.endsWith(TMADataIO.TMA_DEARRAYING_DATA_EXTENSION))) {
				if (hierarchy == null)
					DisplayHelpers.showErrorMessage("TMA grid import", "Please open an image first before importing a dearrayed TMA grid!");
				else {
					TMAGrid tmaGrid = TMADataIO.importDearrayedTMAData(file);
					if (tmaGrid != null) {
						if (hierarchy.isEmpty() || DisplayHelpers.showYesNoDialog("TMA grid import", "Set TMA grid for existing hierarchy?"))
							hierarchy.setTMAGrid(tmaGrid);
					} else
						DisplayHelpers.showErrorMessage("TMA grid import", "Could not parse TMA grid from " + file.getName());
				}
				return;
			}


			// Open Javascript
			ScriptEditor scriptEditor = gui.getScriptEditor();
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
					DisplayHelpers.showErrorMessage("Open image", "Please drag the file only a specific viewer to open!");
					return;
				}
				gui.openImage(viewer, file.getAbsolutePath(), true, true);
				return;
			} else if (gui.getProject() != null) {
				// Try importing multiple images to a project
				String[] potentialFiles = list.stream().filter(f -> f.isFile()).map(f -> f.getAbsolutePath()).toArray(String[]::new);
				if (potentialFiles.length > 0) {
					ProjectImportImagesCommand.promptToImportImages(gui, potentialFiles);
					return;
				}
			}


		}
		if (gui.getProject() == null) {
			if (list.size() > 1) {
				DisplayHelpers.showErrorMessage("Drag & drop", "Could not handle multiple file drop - if you want to handle multiple images, you need to create a project first");
				return;
			}
    	}
		if (list.size() > 1)
			DisplayHelpers.showErrorMessage("Drag & drop", "Sorry, I couldn't figure out what to do with these files - try opening one at a time");
		else
			DisplayHelpers.showErrorMessage("Drag & drop", "Sorry, I couldn't figure out what to do with " + list.get(0).getName());
	}
    
    
    /**
     * Interface to define a new file handler for a particular extension.
     * 
     * @author Pete Bankhead
     *
     */
    public static interface FileDropHandler {
    	
    	public boolean handleFileDrop(final QuPathViewer viewer, final List<File> list);
    	
    }
    
       
}