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

package qupath.lib.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.DefaultScriptEditor.Language;

/**
 * Helper class for creating a dynamic menu to a directory containing scripts.
 * 
 * @author Pete Bankhead
 *
 */
public class ScriptMenuLoader {
	
	private ObservableStringValue scriptDirectory;
	private Menu menu;
	private MenuItem miSetPath = new MenuItem("Set script directory...");
	private MenuItem miCreateScript = new MenuItem("New script...");
	private MenuItem miOpenDirectory = new MenuItem("Open script directory");
	
	private DefaultScriptEditor scriptEditor;
	
	ScriptMenuLoader(final String name, final ObservableStringValue scriptDirectory, final DefaultScriptEditor editor) {
		this.menu = new Menu(name);
		this.scriptDirectory = scriptDirectory;
		this.scriptEditor = editor;
		scriptDirectory.addListener((v) -> updateMenu()); // Rebuild the script menu next time
		
		this.miCreateScript.setOnAction(e -> {
			String dir = scriptDirectory.get();
			if (dir == null) {
				DisplayHelpers.showErrorMessage("New script error", "No script directory set!");
			}
			String scriptName = DisplayHelpers.showInputDialog("New script", "Enter script name", "");
			if (scriptName == null || scriptName.trim().isEmpty())
				return;
			if (!scriptName.contains("."))
				scriptName = scriptName + ".groovy";
			File scriptFile = new File(dir, scriptName);
			if (!scriptFile.exists()) {
				try {
					// If we need to create the scripts directory, try to do so
					// (This helps for project scripts, when the directory might not exist yet)
					File dirScripts = new File(dir);
					if (!dirScripts.exists())
						dirScripts.mkdir();
					scriptFile.createNewFile();
				} catch (Exception e1) {
					DisplayHelpers.showErrorMessage("New script error", "Unable to create new script!");
					QuPathGUI.logger.error("Create script error", e1);
				}
			}
			if (scriptEditor != null)
				scriptEditor.showScript(scriptFile);
			else
				QuPathGUI.getInstance().getScriptEditor().showScript(scriptFile);
		});
		
		// Command to open directory
		miOpenDirectory.disableProperty().bind(Bindings.isNotNull(scriptDirectory).not());
		miOpenDirectory.setOnAction(e -> {
			// Try to reveal directory in Finder/Windows Explorer etc.
			File dir = new File(scriptDirectory.get());
			if (!dir.exists()) {
				dir.mkdir();
			}
			DisplayHelpers.openFile(dir);
		});
		
		if (scriptDirectory instanceof StringProperty) {
			miSetPath.setOnAction(e -> {
				File dirBase = scriptDirectory.get() == null ? null : new File(scriptDirectory.get());
				File dir = QuPathGUI.getSharedDialogHelper().promptForDirectory(dirBase);
				if (dir != null)
					((StringProperty)scriptDirectory).set(dir.getAbsolutePath());
			});
		}
	}
	
	/**
	 * Request that the contents of the menu be updated.
	 */
	public void updateMenu() {
		String scriptDir = scriptDirectory.get();
		if (scriptDir != null) {
			Path path = Paths.get(scriptDir);
			// Can only set script directory if we have a property, not just any observable string
			if (scriptDirectory instanceof StringProperty)
				menu.getItems().setAll(miSetPath, miOpenDirectory, miCreateScript, new SeparatorMenuItem());
			else
				menu.getItems().setAll(miOpenDirectory, miCreateScript, new SeparatorMenuItem());
			if (path != null) {
				addMenuItemsForPath(menu, path, true);
			}
		} else if (scriptDirectory instanceof StringProperty)
			menu.getItems().setAll(miSetPath);
		else
			menu.getItems().clear();
	}
	
	
	/**
	 * Add menu items for each script.
	 * 
	 * @param menu
	 * @param path
	 * @param addDirectly
	 */
	private void addMenuItemsForPath(final Menu menu, final Path path, final boolean addDirectly) {
		
		if (Files.isDirectory(path)) {
			Menu subMenu = QuPathGUI.createMenu(path.getFileName().toString());
			
			try {
				Files.list(path).forEach(p -> addMenuItemsForPath(addDirectly ? menu : subMenu, p, false));
			} catch (IOException e) {
				QuPathGUI.logger.debug("Error adding menu item for {}", path);
			}
			// Don't add anything if the submenu is empty
			if (subMenu.getItems().isEmpty())
				return;
			// Add submenu itself, or its items directly
			if (!addDirectly) {
				menu.getItems().add(subMenu);
			}
		} else {
			if (scriptEditor == null || scriptEditor.supportsFile(path.toFile())) {
				String name = path.getFileName().toString();
				boolean cleanName = true;
				if (cleanName) {
					name = name.replace("_", " ");
					int dotInd = name.lastIndexOf(".");
					if (dotInd > 0)
						name = name.substring(0, dotInd);
				}
				MenuItem item = new MenuItem(name);
				item.setOnAction(e -> {
					File scriptFile = path.toFile();
					if (scriptEditor != null)
						scriptEditor.showScript(scriptFile);
					else {
						Language language = DefaultScriptEditor.getLanguageFromName(scriptFile.getName());		
						try {
							String script = GeneralTools.readFileAsString(scriptFile.getAbsolutePath());
							DefaultScriptEditor.executeScript(language, script, QuPathGUI.getInstance().getImageData(), true, null);
						} catch (Exception e2) {
							DisplayHelpers.showErrorMessage("Script error", e2);
						}
					}
					
//					scriptEditor.showScript(path.toFile());
				});
				menu.getItems().add(item);
			}
		}
	}
	
	
	
	public Menu getMenu() {
		return menu;
	}
	
}