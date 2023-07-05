/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.OpenFilesHandler;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

/**
 * Launcher application to start QuPath.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathApp extends Application {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathApp.class);

	@Override
	public void start(Stage stage) throws Exception {

		QuPathGUI qupath = QuPathGUI.createInstance(stage, getHostServices());
		
		// Delay logging until here, so that the UI has initialized and can display the message
		QuPathLaunchParameters params = QuPathLaunchParameters.parse(getParameters());
		logger.info("Starting QuPath with parameters: " + params.getRawParameters());

		Optional<String> projectPath = params.getProjectParameter();
		projectPath.ifPresent(s -> openProjectOrLogException(qupath, s));
		
		Optional<String> imagePath = params.getImageParameter();
		imagePath.ifPresent(s -> openImageOrLogException(qupath, s));
		
		tryToRegisterOpenFilesHandler(qupath);
		
		if (!params.requestQuietLaunch()) {
			
			if (PathPrefs.showStartupMessageProperty().get()) {
				showWelcomeMessage(qupath);
			}
			
			// If code is running from a directory (not a jar), we're likely running 
			// from source (not an official package) - so we don't want to check 
			// for updates unnecessarily.
			// This behavior might change in the future, since it might still be meaningful 
			// to check for extension updates.
			if (!isCodeRunningFromDirectory()) {
				qupath.requestAutomaticUpdateCheck();
			}
			
		}
		
	}
			
	private static void openProjectOrLogException(QuPathGUI qupath, String projectParameter) {
		try {
			tryToOpenProject(qupath, projectParameter);
		} catch (IOException | URISyntaxException e) {
			logger.error("Unable to open project " + projectParameter, e);
		}
	}

	private static void tryToOpenProject(QuPathGUI qupath, String projectParameter) throws IOException, URISyntaxException {
		var uri = GeneralTools.toURI(projectParameter);
		var project = ProjectIO.loadProject(uri, BufferedImage.class);
		qupath.setProject(project);
	}

	private static void openImageOrLogException(QuPathGUI qupath, String imageParameter) {
		// If the project is specified, try to open the named image within the project
		var project = qupath.getProject();
		try {
			if (project != null) {
				tryToOpenImageFromNamedProjectEntry(qupath, project, imageParameter);
			} else {
				tryToOpenImageFromPath(qupath, imageParameter);
			}
		} catch (Exception e) {
			logger.error("Unable to open image {}: {}", imageParameter, e.getLocalizedMessage());
			logger.debug(e.getLocalizedMessage(), e);
		}
	}
	
	private static void tryToOpenImageFromNamedProjectEntry(QuPathGUI qupath, Project<BufferedImage> project, String imageName) {
		var entry = project.getImageList().stream().filter(p -> imageName.equals(p.getImageName())).findFirst().orElse(null);
		if (entry == null) {
			logger.warn("No image found in project with name {}", imageName);
		} else
			qupath.openImageEntry(entry);
	}
	
	private static void tryToOpenImageFromPath(QuPathGUI qupath, String imagePath) throws IOException {
		qupath.openImage(qupath.getViewer(), imagePath);		
	}
	
	
	private static void showWelcomeMessage(QuPathGUI qupath) {
		var welcomeStage = WelcomeStage.getInstance(qupath);
		// It would be preferable to use show() and make the stage non-blocking,
		// but we also want to prevent the update check running while the dialog 
		// is visible (since the user may use it to change the auto update preference)
		welcomeStage.showAndWait();
	}


	private static boolean isCodeRunningFromDirectory() {
		return new File(qupath.lib.gui.QuPathGUI.class.getProtectionDomain().getCodeSource().getLocation().getFile()).isDirectory();
	}
		
	
	/**
	 * Helper class to parse the JavaFX Application Parameters relative to launching QuPath.
	 */
	private static class QuPathLaunchParameters {
		
		private Parameters params;
		
		private QuPathLaunchParameters(Parameters params) {
			this.params = params;
		}
		
		public static QuPathLaunchParameters parse(Parameters parameters) {
			return new QuPathLaunchParameters(parameters);
		}
		
		public boolean requestQuietLaunch() {
			String launchQuietly = params.getNamed().getOrDefault("quiet", null);
			if (launchQuietly == null)
				return false;
			else
				return Boolean.valueOf(launchQuietly);
		}
		
		public List<String> getRawParameters() {
			return params.getRaw();
		}
		
		public Optional<String> getProjectParameter() {
			var namedParams = params.getNamed();
			String projectPath = namedParams.getOrDefault("project", null);
			return Optional.ofNullable(projectPath);
		}
		
		public Optional<String> getImageParameter() {
			// If no image is specified, use the first unnamed parameter as a potential image
			var namedParams = params.getNamed();
			String imageName = namedParams.getOrDefault("image", null);
			if (imageName == null)
				return getFirstUnnamedParameter();
			return Optional.of(imageName);
		}
		
		private Optional<String> getFirstUnnamedParameter() {
			var unnamed = params.getUnnamed();
			if (unnamed.isEmpty())
				return Optional.empty();
			else
				return Optional.ofNullable(unnamed.get(0));
		}
		
	}
	
	
	/**
	 * Register a files handler using {@link Desktop}.
	 * This needs to be done early, before launching the app (at least for macOS), so that 
	 * the first event may be captured.
	 * 
	 * @param qupath
	 * @see Desktop#setOpenFileHandler(OpenFilesHandler)
	 */
	private static void tryToRegisterOpenFilesHandler(QuPathGUI qupath) {
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(Action.APP_OPEN_FILE)) {
				logger.debug("Registering files handler");
				var handler = new QuPathOpenFilesHandler(qupath);
				desktop.setOpenFileHandler(handler);	
				return;
			}
		}
		logger.debug("Unable to register file handler (operation not supported)");
	}

	
	
	private static class QuPathOpenFilesHandler implements OpenFilesHandler {
		
		private QuPathGUI qupath;
		
		private QuPathOpenFilesHandler(QuPathGUI qupath) {
			Objects.requireNonNull(qupath, "QuPathGUI must not be null!");
			this.qupath = qupath;
		}

		@Override
		public void openFiles(OpenFilesEvent e) {
			logger.debug("OpenFileHandler is called! {}", e);
			var files = e.getFiles();
			if (files.isEmpty())
				return;
			if (files.size() > 1) {
				logger.warn("Received a request to open multiple files - will ignore! {}", files);
				return;
			}
			Platform.runLater(() -> tryToOpenImage(files.get(0)));
		}
		
		private void tryToOpenImage(File file) {
			try {
				tryToOpenImageFromPath(qupath, file.getAbsolutePath());
			} catch (Exception e) {
				Dialogs.showErrorMessage("Open image", "Can't open image: " + e.getLocalizedMessage());
			}
		}
		
	}
	

}