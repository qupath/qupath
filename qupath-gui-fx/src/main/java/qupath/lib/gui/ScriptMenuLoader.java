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

package qupath.lib.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;

/**
 * Helper class for creating a dynamic menu to a directory containing scripts.
 * 
 * @author Pete Bankhead
 *
 */
class ScriptMenuLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptMenuLoader.class);
	
	private ObservableStringValue scriptDirectory;
	private Menu menu;
	private MenuItem miSetPath;
	private MenuItem miCreateScript;
	private MenuItem miOpenDirectory;
		
	private static final String NO_SCRIPTS_NAME = "No scripts found";
	private MenuItem miPlaceholder = new MenuItem(NO_SCRIPTS_NAME);

	private ScriptEditor scriptEditor;
	
	ScriptMenuLoader(final String name, final ObservableStringValue scriptDirectory, final ScriptEditor editor) {
		this.menu = new Menu(name);
		this.scriptDirectory = scriptDirectory;
		this.scriptEditor = editor;
		
		var actionCreateScript = ActionTools.actionBuilder("New script...", e -> {
			String dir = scriptDirectory.get();
			if (dir == null) {
				Dialogs.showErrorMessage("New script error", "No script directory set!");
			}
			String scriptName = Dialogs.showInputDialog("New script", "Enter script name", "");
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
					Dialogs.showErrorMessage("New script error", "Unable to create new script!");
					logger.error("Create script error", e1);
				}
			}
			if (scriptEditor != null)
				scriptEditor.showScript(scriptFile);
			else
				QuPathGUI.getInstance().getScriptEditor().showScript(scriptFile);
		})
				.longText("Create a new script.")
				.build();
		
		// Command to open directory
		var actionOpenDirectory = ActionTools.actionBuilder("Open script directory",
				e -> {
					// Try to reveal directory in Finder/Windows Explorer etc.
					File dir = new File(scriptDirectory.get());
					if (!dir.exists()) {
						dir.mkdir();
					}
					GuiTools.openFile(dir);
				}
				)
				.disabled(Bindings.isNotNull(scriptDirectory).not())
				.longText("Open the script directory outside QuPath.")
				.build();
		
		
		if (scriptDirectory instanceof StringProperty) {
			var actionSetPath = ActionTools.actionBuilder("Set script directory...", e -> {
				File dirBase = scriptDirectory.get() == null ? null : new File(scriptDirectory.get());
				File dir = Dialogs.promptForDirectory("Set script directory", dirBase);
				if (dir != null)
					((StringProperty)scriptDirectory).set(dir.getAbsolutePath());
			})
					.longText("Set the directory containing scripts that should be shown in this menu.")
					.build();
			
			miSetPath = ActionTools.createMenuItem(actionSetPath);
		}
		miCreateScript = ActionTools.createMenuItem(actionCreateScript);
		miOpenDirectory = ActionTools.createMenuItem(actionOpenDirectory);
		
		menu.getItems().setAll(miPlaceholder);
		menu.setOnMenuValidation(e -> updateMenu());
	}
	
	/**
	 * Request that the contents of the menu be updated.
	 */
	private void updateMenu() {
		String scriptDir = scriptDirectory.get();
		try {
			if (scriptDir != null) {
				// Can only set script directory if we have a property, not just any observable string
				if (miSetPath != null)
					menu.getItems().setAll(miSetPath, miOpenDirectory, miCreateScript, new SeparatorMenuItem());
				else
					menu.getItems().setAll(miOpenDirectory, miCreateScript, new SeparatorMenuItem());
				Path path = Paths.get(scriptDir);
				if (path != null && path.getFileName() != null) {
					addMenuItemsForPath(menu, path);
				}
			} else if (miSetPath != null)
				menu.getItems().setAll(miSetPath);
			else
				menu.getItems().setAll(miPlaceholder);
		} catch (Exception e) {
			logger.warn("Unable to update scripts for path {} ({})", scriptDir, e.getLocalizedMessage());
			logger.debug("", e);
			menu.getItems().setAll(miPlaceholder);
		}
	}
	
	
	private void addMenuItemsForPath(final Menu menu, final Path dir) {
		
		if (!Files.isDirectory(dir))
			return;
		
		try (var stream = Files.list(dir)) {
			List<MenuItem> items = new ArrayList<>();
			for (var path : stream.sorted().collect(Collectors.toList())) {
				
				if (Files.isHidden(path))
					continue;
				
				if (Files.isDirectory(path)) {
					Menu subMenu = MenuTools.createMenu(path.getFileName().toString());
					subMenu.getItems().add(new MenuItem(NO_SCRIPTS_NAME));
					subMenu.setOnMenuValidation(e -> {
						subMenu.getItems().clear();
						addMenuItemsForPath(subMenu, path);
					});
					items.add(subMenu);
					continue;
				}
				
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
							var qupath = QuPathGUI.getInstance();
							try {
								qupath.runScript(scriptFile, null);
							} catch (IllegalArgumentException | ScriptException e1) {
								Dialogs.showErrorNotification("Run script", e1.getLocalizedMessage());
								logger.error(e1.getLocalizedMessage(), e);
							}
						}
					});
					items.add(item);
				}
			}
			if (!items.isEmpty()) {
				// Remove placeholder if available
				if (menu.getItems().size() == 1 && NO_SCRIPTS_NAME.equals(menu.getItems().get(0).getText()))
					menu.getItems().setAll(items);				
				else
					menu.getItems().addAll(items);				
			}
		} catch (IOException e) {
			logger.debug("Error adding menu item for {}", dir);
		}
		
	}
	
	
	public Menu getMenu() {
		return menu;
	}
	
}