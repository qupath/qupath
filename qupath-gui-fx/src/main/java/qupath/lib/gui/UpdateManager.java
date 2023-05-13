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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import qupath.lib.common.Version;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.UpdateChecker;
import qupath.lib.gui.extensions.GitHubProject.GitHubRepo;
import qupath.lib.gui.extensions.UpdateChecker.ReleaseVersion;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.tools.GuiTools;
import qupath.fx.utils.GridPaneUtils;


/**
 * Class to handle the interactive user interface side of update checking.
 * The actual checks are performed using {@link UpdateChecker}.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class UpdateManager {
	
	private static final Logger logger = LoggerFactory.getLogger(UpdateManager.class);
	
	private QuPathGUI qupath;
	
	private UpdateManager(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	static UpdateManager create(QuPathGUI qupath) {
		return new UpdateManager(qupath);
	}
	
	/**
	 * Do an update check.
	 * @param isAutoCheck if true, avoid prompting the user unless an update is available. If false, the update has been explicitly 
	 *                    requested and so the user should be notified of the outcome, regardless of whether an update is found.
	 * @param updateCheckType
	 */
	synchronized void doUpdateCheck(AutoUpdateType updateCheckType, boolean isAutoCheck) {

		String title = "Update check";

		// Get a map of all the projects we can potentially check
		Map<GitHubRepo, Version> projects = new LinkedHashMap<>();
		Map<GitHubRepo, ReleaseVersion> projectUpdates = new LinkedHashMap<>();
		
		// Start with the main app
		var qupathVersion = QuPathGUI.getVersion();
		if (qupathVersion != null && qupathVersion != Version.UNKNOWN) {
			if (updateCheckType == AutoUpdateType.QUPATH_ONLY || updateCheckType == AutoUpdateType.QUPATH_AND_EXTENSIONS)
				projects.put(GitHubRepo.create("QuPath", "qupath", "qupath"), qupathVersion);
		}
		
		// Work through extensions
		if (updateCheckType == AutoUpdateType.QUPATH_AND_EXTENSIONS || updateCheckType == AutoUpdateType.EXTENSIONS_ONLY) {
			var extensionManager = qupath.getExtensionManager();
			for (var ext : extensionManager.getLoadedExtensions()) {
				var v = ext.getVersion();
				if (!(ext instanceof GitHubProject)) {
					// This also applies to built-in QuPath extensions
					logger.debug("Can't check for updates for {} (not a project with its own GitHub repo)", ext.getName());
				} else if (v != null && v != Version.UNKNOWN) {
					var project = (GitHubProject)ext;
					projects.put(project.getRepository(), v);
				} else {
					logger.warn("Can't check for updates for {} - unknown version", ext.getName());
				}
			}
		}

		// Report if there is nothing to update
		if (projects.isEmpty()) {
			if (isAutoCheck) {
				logger.warn("Cannot check for updates for this installation");
			} else {
				Dialogs.showMessageDialog(title, "Sorry, no update check is available for this installation");
			}
			return;
		}
		
		// Check for any updates
		for (var entry : projects.entrySet()) {
			try {
				var project = entry.getKey();
				logger.info("Update check for {}", project.getUrlString());
				var release = UpdateChecker.checkForUpdate(entry.getKey());
				if (release != null && release.getVersion() != Version.UNKNOWN && entry.getValue().compareTo(release.getVersion()) < 0) {
					logger.info("Found newer release for {} ({} -> {})", project.getName(), entry.getValue(), release.getVersion());
					projectUpdates.put(project, release);
				} else if (release != null) {
					logger.info("No newer release for {} ({} is newer than {})", project.getName(), entry.getValue(), release.getVersion());
				}
			} catch (Exception e) {
				logger.error("Update check failed for {}", entry.getKey());
				logger.debug(e.getLocalizedMessage(), e);
			}
		}
		PathPrefs.getUserPreferences().putLong("lastUpdateCheck", System.currentTimeMillis());
		
		// If we couldn't determine the version, tell the user only if this isn't the automatic check
		if (projectUpdates.isEmpty()) {
			if (!isAutoCheck)
				Dialogs.showMessageDialog(title, "No updates found!");
			return;
		}
		
		// Create a table showing the updates available
		var table = new TableView<GitHubRepo>();
		table.getItems().setAll(projectUpdates.keySet());
		
		var colRepo = new TableColumn<GitHubRepo, String>("Name");
		colRepo.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getName()));
		
		var colCurrent = new TableColumn<GitHubRepo, String>("Current version");
		colCurrent.setCellValueFactory(r -> new SimpleStringProperty(projects.get(r.getValue()).toString()));

		var colNew = new TableColumn<GitHubRepo, String>("New version");
		colNew.setCellValueFactory(r -> new SimpleStringProperty(projectUpdates.get(r.getValue()).getVersion().toString()));
		
		table.setRowFactory(r -> {
			var row = new TableRow<GitHubRepo>();
			row.itemProperty().addListener((v, o, n) -> {
				if (n == null) {
					row.setTooltip(null);
					row.setOnMouseClicked(null);
				} else {
					var release = projectUpdates.get(n);
					var uri = release.getUri();
					if (uri == null) {
						row.setTooltip(new Tooltip("No URL available, sorry!"));
						row.setOnMouseClicked(null);
					} else {
						row.setTooltip(new Tooltip(uri.toString()));
						row.setOnMouseClicked(e -> {
							if (e.getClickCount() > 1) {
								GuiTools.browseURI(uri);
							}
						});
					}
				}
			});
			return row;
		});
		
		table.getColumns().setAll(Arrays.asList(colRepo, colCurrent, colNew));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.setPrefHeight(200);
		table.setPrefWidth(500);
		
		
		var comboUpdates = new ComboBox<AutoUpdateType>();
		comboUpdates.getItems().setAll(AutoUpdateType.values());
		comboUpdates.getSelectionModel().select(PathPrefs.autoUpdateCheckProperty().get());
		var labelUpdates = new Label("Check for updates on startup:");
		labelUpdates.setLabelFor(comboUpdates);
		labelUpdates.setAlignment(Pos.CENTER_RIGHT);
		
		var paneUpdates = new GridPane();
		paneUpdates.add(labelUpdates, 0, 0);
		paneUpdates.add(comboUpdates, 1, 0);
		paneUpdates.setHgap(5);
		GridPaneUtils.setToExpandGridPaneWidth(comboUpdates);
		paneUpdates.setPadding(new Insets(5, 0, 0, 0));
		
		var pane = new BorderPane(table);
		pane.setBottom(paneUpdates);
		
		var result = new Dialogs.Builder()
				.buttons(ButtonType.OK)
				.title(title)
				.headerText("Updates are available!\nDouble-click an entry to open the webpage, if available.")
				.content(pane)
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;
		
		if (result) {
			PathPrefs.autoUpdateCheckProperty().set(comboUpdates.getSelectionModel().getSelectedItem());
		}
	}
	
	
	
	/**
	 * Check for any updates.
	 */
	void runAutomaticUpdateCheck() {
		// For automated checks, respect the user preferences for QuPath, extensions or neither
		AutoUpdateType checkType = PathPrefs.autoUpdateCheckProperty().get();
		boolean doAutoUpdateCheck = checkType != null && checkType != AutoUpdateType.NONE;
		if (!doAutoUpdateCheck) {
			logger.debug("No update check because of user preference ({})", checkType);
			return;
		}

		// Don't run auto-update check again if we already checked within the last hour
		long currentTime = System.currentTimeMillis();
		long lastUpdateCheck = PathPrefs.getUserPreferences().getLong("lastUpdateCheck", 0);
		double diffHours = (double)(currentTime - lastUpdateCheck) / (60L * 60L * 1000L);
		if (diffHours < 12) {
			logger.debug("Skipping update check (I already checked recently)");
			return;
		}
		runUpdateCheckInBackground(checkType, true);
	}
	
	
	void runManualUpdateCheck() {
		logger.debug("Manually requested update check - will search for QuPath and extensions");
		AutoUpdateType checkType = AutoUpdateType.QUPATH_AND_EXTENSIONS;
		runUpdateCheckInBackground(checkType, false);
	}
	
		
	private void runUpdateCheckInBackground(AutoUpdateType checkType, boolean isAutoCheck) {
		// Run the check in a background thread
		qupath.getThreadPoolManager().submitShortTask(() -> doUpdateCheck(checkType, isAutoCheck));
	}

}
