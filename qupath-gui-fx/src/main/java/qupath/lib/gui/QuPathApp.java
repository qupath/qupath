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

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.ProjectIO;

/**
 * Launcher application to start QuPathGUI.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathApp extends Application {
	
	final static Logger logger = LoggerFactory.getLogger(QuPathApp.class);

	@Override
	public void start(Stage stage) throws Exception {
		// Handle params
		Parameters params = getParameters();
		Map<String, String> namedParams = params.getNamed();
//		List<String> unnamedParams = params.getUnnamed();
		
		// Create main GUI
		boolean quiet = Boolean.valueOf(namedParams.getOrDefault("quiet", null));
		QuPathGUI gui = new QuPathGUI(getHostServices(), stage, null, true, quiet);
		logger.info("Starting QuPath with parameters: " + params.getRaw());
		
		// Try to open a project and/or image, if possible
		String projectPath = namedParams.getOrDefault("project", null);
		String imagePath = namedParams.getOrDefault("image", null);
		if (projectPath != null) {
			var uri = GeneralTools.toURI(projectPath);
			var project = ProjectIO.loadProject(uri, BufferedImage.class);
			gui.setProject(project);
			// If the project is specified, try to open the named image within the project
			if (imagePath != null) {
				var entry = project.getImageList().stream().filter(p -> imagePath.equals(p.getImageName())).findFirst().orElse(null);
				if (entry == null) {
					logger.warn("No image found in project with name {}", imagePath);
				} else
					gui.openImageEntry(entry);
			}
		} else if (imagePath != null) {
			gui.openImage(imagePath, false, false);
		}
		
		gui.updateCursor();
		
		// Try to set a file handler, if supported
		logger.debug("Setting OpenFileHandler...");
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(Action.APP_OPEN_FILE)) {
				Desktop.getDesktop().setOpenFileHandler(e -> {
					logger.debug("OpenFileHandler is called! {}", e);
					var files = e.getFiles();
					if (files.isEmpty())
						return;
					if (files.size() > 1) {
						logger.warn("Received a request to open multiple files - will ignore! {}", files);
						return;
					}
					Platform.runLater(() -> gui.openImage(files.get(0).getAbsolutePath(), false, false));
				});	
			}
		}
		
		// Show setup if required, and if we haven't an argument specifying to skip
		// Store a value indicating the setup version... this means we can enforce running 
		// setup at a later date with a later version if new and necessary options are added
		int currentSetup = 1;
		int lastSetup = PathPrefs.getUserPreferences().getInt("qupathSetupValue", -1);
		if (!quiet && lastSetup != currentSetup) {
			Platform.runLater(() -> {
				if (gui.showSetupDialog()) {
					PathPrefs.getUserPreferences().putInt("qupathSetupValue", currentSetup);
					PathPrefs.savePreferences();
				}
			});
		}
		
	}

}