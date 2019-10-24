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

package qupath.lib.gui.helpers.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.helpers.dialogs.DialogHelper;
import javafx.stage.Window;

/**
 * Implementation of DialogHelper using JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class DialogHelperFX implements DialogHelper {
	
	private static final Logger logger = LoggerFactory.getLogger(DialogHelperFX.class);
	
	private Window ownerWindow = null;
	private FileChooser fileChooser = new FileChooser();
	private DirectoryChooser directoryChooser = new DirectoryChooser();
	
	private ExtensionFilter allFiles = new ExtensionFilter("All Files", "*.*");
	
	protected File lastDir;
	
	/**
	 * Create a {@link DialogHelper} using JavaFX.
	 * @param ownerWindow
	 */
	public DialogHelperFX(final Window ownerWindow) {
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
	
	protected File getUsefulBaseDirectory(File dirBase) {
		if (dirBase == null || !dirBase.isDirectory())
			return getLastDirectory();
		else
			return dirBase;
	}

	@Override
	public File promptForDirectory(File dirBase) {
		
		if (!Platform.isFxApplicationThread()) {
			return DisplayHelpers.callOnApplicationThread(() -> promptForDirectory(dirBase));
		}
		
		File lastDir = getLastDirectory();
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
			return DisplayHelpers.callOnApplicationThread(() -> promptForMultipleFiles(title, dirBase, filterDescription, exts));
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
		
		if (!Platform.isFxApplicationThread()) {
			return DisplayHelpers.callOnApplicationThread(() -> promptForFile(title, dirBase, filterDescription, exts));
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
		File fileSelected = fileChooser.showOpenDialog(ownerWindow);
		
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
	public File promptToSaveFile(String title, File dirBase, String defaultName, String filterName, String ext) {
		
		if (!Platform.isFxApplicationThread()) {
			return DisplayHelpers.callOnApplicationThread(() -> promptToSaveFile(title, dirBase, defaultName, filterName, ext));
		}

		File lastDir = getLastDirectory();
		fileChooser.setInitialDirectory(getUsefulBaseDirectory(dirBase));
		if (title != null)
			fileChooser.setTitle(title);
		else
			fileChooser.setTitle("Save");
		// Multipart extensions can be troublesome... don't set a filter for these unless on Windows (which seems to cope)
		if (filterName != null && ext != null && (GeneralTools.isWindows() || !GeneralTools.isMultipartExtension(ext))) {
			ExtensionFilter filter = getExtensionFilter(filterName, ext);
			fileChooser.getExtensionFilters().setAll(filter);
			fileChooser.setSelectedExtensionFilter(filter);
		} else {
			fileChooser.getExtensionFilters().clear();
			fileChooser.setSelectedExtensionFilter(null);
		}
		if (defaultName != null) {
			if (ext != null)
				fileChooser.setInitialFileName(GeneralTools.getNameWithoutExtension(new File(defaultName)));
			else
				fileChooser.setInitialFileName(defaultName);
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
			if (ext != null && !name.toLowerCase().endsWith(ext.toLowerCase())) {
				if (name.endsWith("."))
					name = name.substring(0, name.length()-1);
				if (ext.startsWith("."))
					name = name + ext;
				else
					name = name + "." + ext;
				fileSelected = new File(fileSelected.getParentFile(), name);
			}
		}
		
		logger.trace("Returning file to save: {}", fileSelected);

		
		return fileSelected;
	}

	@Override
	public String promptForFilePathOrURL(String title, String defaultPath, File dirBase, String filterDescription, String... exts) {
		if (!Platform.isFxApplicationThread()) {
			return DisplayHelpers.callOnApplicationThread(() -> promptForFilePathOrURL(title, defaultPath, dirBase, filterDescription, exts));
		}
		
		// Create dialog
        GridPane pane = new GridPane();
        
        Label label = new Label("Enter URL");
        TextField tf = new TextField();
        tf.setPrefWidth(400);
        
        Button button = new Button("Choose file");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
            	File file = promptForFile(null, dirBase, filterDescription, exts);
            	if (file != null)
            		tf.setText(file.getAbsolutePath());
            }
        });
        
//        label.setPadding(new Insets(0, 0, 5, 0));
        PaneToolsFX.addGridRow(pane, 0, 0, "Input URL or choose file", label, tf, button);
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

}