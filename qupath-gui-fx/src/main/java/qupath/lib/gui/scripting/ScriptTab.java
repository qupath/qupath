/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class representing a script tab (e.g. on the right side of the script editor).
 * <p>
 * A {@link ScriptTab} object has:
 * <li>A main editor</li>
 * <li>A console</li>
 * <li>A language</li>
 * And is displayed on the right side of the {@link ScriptEditor}.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class ScriptTab {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptTab.class);
	
	private File file = null;
	private long lastModified = -1L;
	private String lastSavedContents = null;
	
	private static int untitledCounter = 0;
	
	private ScriptLanguage language;
	
	private BooleanProperty isModified = new SimpleBooleanProperty(false);
	
	private String name;
	
	private ScriptEditorControl<? extends Region> console;
	private ScriptEditorControl<? extends Region> editor;
	
	private boolean isRunning = false;
	
	ScriptTab(final ScriptEditorControl<? extends Region> editor, final ScriptEditorControl<? extends Region> console, final String script, final ScriptLanguage language) {
		this.editor = editor;
		this.console = console;
		initialize();
		this.language = language;
		if (script != null)
			editor.setText(script);
		untitledCounter++;
		name = "Untitled " + untitledCounter;
	}
	
	ScriptTab(final ScriptEditorControl<? extends Region> editor, final ScriptEditorControl<? extends Region> console, final File file) throws IOException {
		this.editor = editor;
		this.console = console;
		initialize();
		readFile(file);
	}
	
	/**
	 * Read the file, set the editor's text area with the content of the file 
	 * and return the detected language (based on the file extension).
	 * @param file the file to read
	 * @throws IOException
	 */
	protected void readFile(final File file) throws IOException {
		logger.info("Loading file {} to Script Editor", file.getAbsolutePath());
		String content = GeneralTools.readFileAsString(file);
		editor.setText(content);
		name = file.getName();
		this.file = file;
		lastModified = file.lastModified();
		lastSavedContents = content;
		this.language = ScriptLanguageProvider.getLanguageFromName(name);
//		scanner.close();
		updateIsModified();
	}
	
	
	protected void refreshFileContents() {
		try {
			if (file != null && file.lastModified() > lastModified) {
				logger.debug("Calling refresh!");
				readFile(file);
				updateIsModified();
			}
		} catch (IOException e) {
			logger.error("Cannot refresh script file", e);
		}
	}
	
	void initialize() {
		BorderPane panelMainEditor = new BorderPane();
		panelMainEditor.setCenter(editor.getRegion());

		var popup = console.getContextMenu();
		if (popup == null) {
			popup = new ContextMenu();
			popup.getItems().add(ActionUtils.createMenuItem(new Action("Copy", e -> console.copy())));
		}
		popup.getItems().add(ActionUtils.createMenuItem(new Action("Clear console", e -> console.setText(""))));
		popup.getStyleClass().setAll("context-menu");		
		console.setContextMenu(popup);
//		console.setPopup(popup);
		
 		updateIsModified();
	}
	
	ScriptEditorControl<? extends Region> getEditorControl() {
		return editor;
	}
	
	boolean hasScript() {
		return editor.getText().length() > 0;
	}

	ScriptEditorControl<? extends Region> getConsoleControl() {
		return console;
	}

	File getFile() {
		return file;
	}
	
	boolean fileExists() {
		return file != null && file.exists();
	}
	
	boolean isRunning() {
		return isRunning;
	}
	
	void setRunning(boolean running) {
		this.isRunning = running;
	}
	
//	public ReadOnlyBooleanProperty isModifiedProperty() {
//		return isModified;
//	}
	
	/**
	 * Return 
	 * 
	 * @return
	 */
	public boolean isModified() {
		return isModified.get();
	}
	
	/**
	 * Return the {@code isModifiedProperty} of this script tab (true if the 
	 * script is modified, i.e. it isn't the same as the last saved version).
	 * @return isModifiedProperty
	 */
	public BooleanProperty isModifiedProperty() {
		return isModified;
	}

	void updateIsModified() {
		boolean newState = !fileExists() || !editor.getText().equals(lastSavedContents); // TODO: Consider checking disk contents / timestamp
		if (isModified.get() == newState)
			return;
		isModified.set(newState);
	}
	
	void saveToFile(final String text, final File file) throws IOException {
		Files.writeString(file.toPath(), text);
		this.file = file;
		this.name = file.getName();
		this.lastSavedContents = text;
		this.lastModified = file.lastModified();
		updateIsModified();
	}
	
	ScriptLanguage getLanguage() {
		return language;
	}
	
	void setLanguage(final ScriptLanguage language) {
		this.language = language;
	}
	
	Set<String> getRequestedExtensions() {
		return language.getExtensions();
	}
	
	String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return isModified.get() ? "*" + name : name;
	}
}