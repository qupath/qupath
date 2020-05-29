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
		// If we have an unmatched argument, and no image specified, use it as a potential image
		String unnamed = params.getUnnamed().isEmpty() ? null : params.getUnnamed().get(0);
		String projectPath = namedParams.getOrDefault("project", null);
		String imagePath = namedParams.getOrDefault("image", unnamed);
		
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
		
		registerFileHandler(gui);
		gui.updateCursor();
		
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
	
	/**
	 * Register a file handler. This needs to be done early, before launching the app (at least for macOS), so that 
	 * the first event may be captured.
	 * <p>
	 * (Alas, here it seems to already be too late)
	 * @param qupath
	 * @return true if the file handler could be registered, false otherwise
	 */
	private static boolean registerFileHandler(QuPathGUI qupath) {
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(Action.APP_OPEN_FILE)) {
				logger.debug("Registering file handler");
				Desktop.getDesktop().setOpenFileHandler(e -> {
					logger.debug("OpenFileHandler is called! {}", e);
					var files = e.getFiles();
					if (files.isEmpty())
						return;
					if (files.size() > 1) {
						logger.warn("Received a request to open multiple files - will ignore! {}", files);
						return;
					}
					Platform.runLater(() -> qupath.openImage(files.get(0).getAbsolutePath(), false, false));
				});	
				return true;
			}
		}
		logger.debug("Unable to register file handler - operation not supported");
		return false;
	}
	

}