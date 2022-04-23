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

package qupath.lib.gui.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import javafx.stage.Window;

/**
 * Implementation of {@link QuPathChooser} using JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathChooserFX implements QuPathChooser {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathChooserFX.class);
	
	private Window ownerWindow = null;
	private FileChooser fileChooser = new FileChooser();
	private DirectoryChooser directoryChooser = new DirectoryChooser();
	
	private ExtensionFilter allFiles = new ExtensionFilter("All Files", "*.*");
	
	private File lastDir;
	
	/**
	 * Create a {@link QuPathChooser} using JavaFX.
	 * @param ownerWindow
	 */
	public QuPathChooserFX(final Window ownerWindow) {
		this.ownerWindow = ownerWindow;
	}
	

	@Override
	public void setLastDirectory(File dir) {
		if (dir == null)
			lastDir = null;
		else if (dir.isDirectory()) {
			if (dir.exists())
				lastDir = dir;
			return;
		} else
			setLastDirectory(dir.getParentFile());
	}

	@Override
	public File getLastDirectory() {
		if (lastDir != null && lastDir.isDirectory())
			return lastDir;
		return null;
	}
	
	private File getUsefulBaseDirectory(File dirBase) {
		if (dirBase == null || !dirBase.isDirectory())
			return getLastDirectory();
		else
			return dirBase;
	}

	@Override
	public File promptForDirectory(String title, File dirBase) {
		
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> promptForDirectory(title, dirBase));
		}
		
		File lastDir = getLastDirectory();
		directoryChooser.setTitle(title);
		directoryChooser.setInitialDirectory(getUsefulBaseDirectory(dirBase));
		File dirSelected = directoryChooser.showDialog(ownerWindow);
		if (dirSelected != null) {
			if (dirBase == null)
				setLastDirectory(dirSelected);
			else
				fileChooser.setInitialDirectory(lastDir);
		}
		
		logger.trace("Returning directory: {}", dirSelected);
		
		return dirSelected;
	}
	
	
	@Override
	public List<File> promptForMultipleFiles(String title, File dirBase, String filterDescription, String... exts) {
		
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> promptForMultipleFiles(title, dirBase, filterDescription, exts));
		}
		
		File lastDir = getLastDirectory();
		fileChooser.setInitialDirectory(getUsefulBaseDirectory(dirBase));
		if (title != null)
			fileChooser.setTitle(title);
		else
			fileChooser.setTitle("Choose file");
		if (filterDescription != null && exts != null && exts.length > 0) {
			ExtensionFilter filter = getExtensionFilter(filterDescription, exts);
			fileChooser.getExtensionFilters().setAll(filter, allFiles);
			fileChooser.setSelectedExtensionFilter(filter);
		} else {
			fileChooser.getExtensionFilters().clear();
			fileChooser.setSelectedExtensionFilter(null);
		}
		
		
		// Ensure we make our request on the correct thread
		List<File> filesSelected = fileChooser.showOpenMultipleDialog(ownerWindow);
		
		// Set the last directory if we aren't dealing with a directory that has been specifically requested this time
		if (filesSelected != null && !filesSelected.isEmpty()) {
			if (dirBase == null)
				setLastDirectory(filesSelected.get(0));
			else
				fileChooser.setInitialDirectory(lastDir);
		}
		
		logger.trace("Returning files: {}", filesSelected);
		
		return filesSelected;

	}
	
	
	/**
	 * Create extension filter, ensuring that the format is *.extension.
	 * 
	 * @param description
	 * @param extensions
	 * @return
	 */
	static ExtensionFilter getExtensionFilter(String description, String... extensions) {
		List<String> ext = new ArrayList<>();
		for (String e : extensions) {
			if (e.startsWith(".")) {
				e = "*" + e;
			} else if (!e.startsWith("*"))
				e = "*." + e;
			ext.add(e);
		}
		return new ExtensionFilter(description, ext);
	}
	

	@Override
	public File promptForFile(String title, File dirBase, String filterDescription, String... exts) {
		return promptForFile(ownerWindow, title, dirBase, filterDescription, exts);
	}
	
	
	private File promptForFile(Window owner, String title, File dirBase, String filterDescription, String... exts) {
		
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> promptForFile(title, dirBase, filterDescription, exts));
		}
		
		File lastDir = getLastDirectory();
		fileChooser.setInitialDirectory(getUsefulBaseDirectory(dirBase));
		if (title != null)
			fileChooser.setTitle(title);
		else
			fileChooser.setTitle("Choose file");
		if (filterDescription != null && exts != null && exts.length > 0) {
			ExtensionFilter filter = getExtensionFilter(filterDescription, exts);
			fileChooser.getExtensionFilters().setAll(filter, allFiles);
			fileChooser.setSelectedExtensionFilter(filter);
		} else {
			fileChooser.getExtensionFilters().clear();
			fileChooser.setSelectedExtensionFilter(null);
		}
		
		
		// Ensure we make our request on the correct thread
		File fileSelected = fileChooser.showOpenDialog(owner);
		
		// Set the last directory if we aren't dealing with a directory that has been specifically requested this time
		if (fileSelected != null) {
			if (dirBase == null)
				setLastDirectory(fileSelected);
			else
				fileChooser.setInitialDirectory(lastDir);
		}
		
		logger.trace("Returning file: {}", fileSelected);
		
		return fileSelected;

	}

	@Override
	public File promptForFile(File dirBase) {
		return promptForFile(null, dirBase, null);
	}

	@Override
	public File promptToSaveFile(String title, File dirBase, String defaultName, Map<String, String> filters) {
		
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> promptToSaveFile(title, dirBase, defaultName, filters));
		}

		File lastDir = getLastDirectory();
		fileChooser.setInitialDirectory(getUsefulBaseDirectory(dirBase));
		if (title != null)
			fileChooser.setTitle(title);
		else
			fileChooser.setTitle("Save");
		
		// Extract file extension from default name, if possible.
		// We want to use this 'provided extension' if it's valid, i.e. it's found within the list of extensions.
		// But we need to separately retain a default extension until we know if it's valid.
		// We don't want to strip away anything after a final dot that isn't an intended file extension.
		String providedExt = defaultName == null ? null : GeneralTools.getExtension(defaultName).orElse(null);
		String defaultExt = null;
		
		// Keep track of which extension filters we are using
		boolean useExtFilter = filters != null && !filters.isEmpty();
		var extFilterMap = new HashMap<ExtensionFilter, String>();
		fileChooser.getExtensionFilters().clear();
		fileChooser.setSelectedExtensionFilter(null);
		if (useExtFilter) {
			for (var entry : filters.entrySet()) {
				// Major annoyance: multipart extensions such as .ome.tif can be be automatically trimmed to the final component
				// Windows seems to handle it properly, macOS doesn't (I think Linux doesn't either, but not sure)
				var filterName = entry.getKey();
				var ext = entry.getValue();
				if (filterName != null && ext != null) {
//				if (filterName != null && ext != null && (GeneralTools.isWindows() || !GeneralTools.isMultipartExtension(ext))) {
					var filter = getExtensionFilter(filterName, ext);
					if (!filter.getExtensions().isEmpty()) {
						String currentExt = filter.getExtensions().get(0).substring(1);
						// Test if the provided extension is something we can use by default
						if (Objects.equals(currentExt, providedExt))
							defaultExt = providedExt;
						// If we don't have a default, use the current extension (unless we see later the provided extension is available)
						else if (defaultExt == null && !filter.getExtensions().isEmpty())
							defaultExt = currentExt;
					}
					extFilterMap.put(filter, ext);
					fileChooser.getExtensionFilters().add(filter);
					if (Objects.equals(ext, defaultExt))
						fileChooser.setSelectedExtensionFilter(filter);
				}
			}
		} else 
			defaultExt = providedExt;
		
		// Try to avoid doubling up on the extension
		if (defaultName != null) {
			if (defaultExt != null && GeneralTools.isMultipartExtension(defaultExt) && !defaultName.toLowerCase().endsWith(defaultExt.toLowerCase()))
				fileChooser.setInitialFileName(defaultName + defaultExt);
			else
				fileChooser.setInitialFileName(defaultName);
//			if (fileChooser.getSelectedExtensionFilter() != null)
//				fileChooser.setInitialFileName(GeneralTools.getNameWithoutExtension(new File(defaultName)));
//			else
//				fileChooser.setInitialFileName(defaultName);
		} else
			fileChooser.setInitialFileName(null);
		
		File fileSelected = fileChooser.showSaveDialog(ownerWindow);
		if (fileSelected != null) {
			// Only change the last directory if we didn't specify one
			if (dirBase == null)
				setLastDirectory(fileSelected);
			else
				fileChooser.setInitialDirectory(lastDir);
			
			// Ensure the extension is present
			String name = fileSelected.getName();
			String selectedExt = null;
			if (useExtFilter) {
				selectedExt = extFilterMap.getOrDefault(fileChooser.getSelectedExtensionFilter(), filters.values().iterator().next());
			}
			// Fix the extension if necessary
			if (selectedExt != null && !name.toLowerCase().endsWith(selectedExt.toLowerCase())) {
				// Handle multipart extensions; if we have the last part of the extension set, then remove it before adding the full thing
				var previousName = name;
				if (GeneralTools.isMultipartExtension(selectedExt)) {
					var lastExtPart = selectedExt.substring(selectedExt.lastIndexOf("."));
					if (name.toLowerCase().endsWith(lastExtPart.toLowerCase()))
						name = name.substring(0, name.length()-lastExtPart.length());
				}
				// Add the required extension
				if (name.endsWith("."))
					name = name.substring(0, name.length()-1);
				if (selectedExt.startsWith("."))
					name = name + selectedExt;
				else
					name = name + "." + selectedExt;
				fileSelected = new File(fileSelected.getParentFile(), name);
				logger.warn("Updating name from {} to {}", previousName, name);
			}
		}
		
		logger.trace("Returning file to save: {}", fileSelected);
		
		return fileSelected;
	}

	@Override
	public String promptForFilePathOrURL(String title, String defaultPath, File dirBase, String filterDescription, String... exts) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> promptForFilePathOrURL(title, defaultPath, dirBase, filterDescription, exts));
		}
		
		// Create dialog
        GridPane pane = new GridPane();
        
        Label label = new Label("Enter URL");
        TextField tf = new TextField();
        tf.setPrefWidth(400);
        
        Button button = new Button("Choose file");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
            	// Prefer to use this window as the parent
    			var owner = GuiTools.getWindow(button);
    			File file = promptForFile(owner, null, dirBase, filterDescription, exts);
            	if (file != null)
            		tf.setText(file.getAbsolutePath());
            }
        });
        
//        label.setPadding(new Insets(0, 0, 5, 0));
        PaneTools.addGridRow(pane, 0, 0, "Input URL or choose file", label, tf, button);
        pane.setHgap(5);
//        pane.setTop(label);
//        pane.setCenter(tf);
//        pane.setRight(button);
        
		
        // Show dialog
		if (defaultPath == null)
			tf.setText("");
		else
			tf.setText(defaultPath);
		
		Alert alert = new Alert(Alert.AlertType.NONE, "Enter image path (file or URL)", ButtonType.OK, ButtonType.CANCEL);
		if (title == null)
			alert.setTitle("Enter file path or URL");
		else
			alert.setTitle(title);
//	    alert.initModality(Modality.APPLICATION_MODAL);
//	    alert.initOwner(scene.getWindow());
	    
	    alert.getDialogPane().setContent(pane);
	    
	    String path = null;
	    Optional<ButtonType> result = alert.showAndWait();
	    if (result.isPresent() && result.get() == ButtonType.OK)
	    	path = tf.getText().trim();
	    
		logger.trace("Returning path: {}", path);
	    
		return path;
	}


	@Override
	public File promptToSaveFile(String title, File dirBase, String defaultName, String filterName, String ext) {
		if (filterName != null && !filterName.isEmpty() && ext != null && !ext.isEmpty())
			return promptToSaveFile(title, dirBase, defaultName, Map.of(filterName, ext));
		return promptToSaveFile(title, dirBase, defaultName, Collections.emptyMap());
	}

}