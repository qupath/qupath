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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import javafx.beans.property.LongProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ButtonType;
import qupath.lib.common.Version;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.extensions.UpdateChecker;
import qupath.lib.gui.extensions.GitHubProject.GitHubRepo;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.tools.GuiTools;

/**
 * Class to handle the interactive user interface side of update checking.
 * The actual checks are performed using {@link UpdateChecker}.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class UpdateManager {
	
	private static final Logger logger = LoggerFactory.getLogger(UpdateManager.class);
	private static final int HOURS_BETWEEN_AUTO_UPDATES = 12;
	private static final LongProperty lastUpdateCheck = PathPrefs.createPersistentPreference("lastUpdateCheck", -1L);
	private final QuPathGUI qupath;
	
	private UpdateManager(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	/**
	 * Create an instance of the update manager.
	 *
	 * @param qupath the QuPath GUI instance to use when updating
	 * @return a new instance of the update manager
	 */
	public static UpdateManager create(QuPathGUI qupath) {
		return new UpdateManager(qupath);
	}

	/**
	 * Request an update of what is indicated by {@link PathPrefs#autoUpdateCheckProperty()}
	 * in a background thread.
	 * If an update was run in the past 12 hours (in this Java instance or another), calling this
	 * function will not perform the update check.
	 */
	public void runAutomaticUpdateCheck() {
		AutoUpdateType checkType = PathPrefs.autoUpdateCheckProperty().get();
		if (checkType == null || checkType == AutoUpdateType.NONE) {
			logger.debug("No update check because of user preference ({})", checkType);
			return;
		}

		long currentTime = System.currentTimeMillis();
		long lastUpdateCheckMillis = lastUpdateCheck.get();
		double diffHours = (double)(currentTime - lastUpdateCheckMillis) / (60L * 60L * 1000L);
		if (diffHours < HOURS_BETWEEN_AUTO_UPDATES) {
			logger.debug(
					"Skipping update check because it was already checked recently ({} hours ago, which is less than {} hours ago)",
					diffHours,
					HOURS_BETWEEN_AUTO_UPDATES
			);
			return;
		}

		logger.debug("Automatic update check started - will search for {}", checkType);
		runUpdateCheckInBackground(checkType, false);
	}

	/**
	 * Request an update check of QuPath and the installed extensions, to be done in a background thread.
	 * This will perform the check regardless of {@link PathPrefs#autoUpdateCheckProperty()}.
	 */
	public void runManualUpdateCheck() {
		logger.debug("Manual update check started - will search for QuPath and extensions");
		runUpdateCheckInBackground(AutoUpdateType.QUPATH_AND_EXTENSIONS, true);
	}

	private void runUpdateCheckInBackground(AutoUpdateType checkType, boolean showDialogs) {
		qupath.getThreadPoolManager().submitShortTask(() -> doUpdateCheck(checkType, showDialogs));
	}
	
	/**
	 * Do an update check.
	 *
	 * @param updateCheckType what to update
	 * @param showDialogs whether to show dialogs. If an update is found, a dialog with an
	 *                    {@link UpdateManagerContainer} will be shown no matter this parameter
	 */
	private synchronized void doUpdateCheck(AutoUpdateType updateCheckType, boolean showDialogs) {
		lastUpdateCheck.set(System.currentTimeMillis());

		List<UpdateManagerContainer.UpdateEntry> updateEntries = Stream.concat(
                getQuPathUpdate(updateCheckType).stream(),
				getExtensionUpdates(updateCheckType).stream()
		).toList();

		if (updateEntries.isEmpty()) {
			logger.info("No updates found");
            if (showDialogs) {
                Dialogs.showMessageDialog(
                        QuPathResources.getString("UpdateManager.updateCheck"),
                        QuPathResources.getString("UpdateManager.noUpdatesFound")
                );
            }
            return;
		}

		UpdateManagerContainer updateManagerContainer;
        try {
			updateManagerContainer = new UpdateManagerContainer(updateEntries);
        } catch (IOException e) {
			logger.error("Cannot create update manager window", e);
			if (showDialogs) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("UpdateManager.updateCheck"),
						QuPathResources.getString("UpdateManager.cannotCreateUpdateWindow")
				);
			}
			return;
        }

		boolean result = new Dialogs.Builder()
				.buttons(ButtonType.OK)
				.title(QuPathResources.getString("UpdateManager.updateCheck"))
				.headerText(QuPathResources.getString("UpdateManager.updatesAvailable"))
				.content(updateManagerContainer)
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;

		if (result && updateManagerContainer.getSelectedUpdateType() != null) {
			PathPrefs.autoUpdateCheckProperty().set(updateManagerContainer.getSelectedUpdateType());
		}
	}

	private Optional<UpdateManagerContainer.UpdateEntry> getQuPathUpdate(AutoUpdateType updateCheckType) {
		Version qupathVersion = QuPathGUI.getVersion();
		if (qupathVersion != null && qupathVersion != Version.UNKNOWN &&
				List.of(AutoUpdateType.QUPATH_ONLY, AutoUpdateType.QUPATH_AND_EXTENSIONS).contains(updateCheckType)
		) {
			GitHubRepo gitHubProject = GitHubRepo.create("QuPath", "qupath", "qupath");
			logger.debug("Update check for {}", gitHubProject.getUrlString());

			try {
				UpdateChecker.ReleaseVersion latestRelease = UpdateChecker.checkForUpdate(gitHubProject);

				if (latestRelease != null && latestRelease.getVersion() != Version.UNKNOWN && qupathVersion.compareTo(latestRelease.getVersion()) < 0) {
					logger.info("Found newer release for {} ({} -> {})", gitHubProject.getName(), qupathVersion, latestRelease.getVersion());

					return Optional.of(new UpdateManagerContainer.UpdateEntry(
                            gitHubProject.getName(),
                            qupathVersion.toString(),
                            latestRelease.getVersion().toString(),
                            latestRelease.getUri() == null ?
                                    () -> {} :
                                    () -> GuiTools.browseURI(latestRelease.getUri()),
                            latestRelease.getUri() == null ?
                                    QuPathResources.getString("UpdateManager.noAvailableUrl") :
                                    latestRelease.getUri().toString()
                    ));
				} else if (latestRelease != null) {
					logger.info("No newer release for {} (current {} vs upstream {})", gitHubProject.getName(), qupathVersion, latestRelease.getVersion());
				}
			} catch (Exception e) {
				logger.warn("Update check failed for {}", gitHubProject, e);
			}
		}

		return Optional.empty();
	}

	private List<UpdateManagerContainer.UpdateEntry> getExtensionUpdates(AutoUpdateType updateCheckType) {
		if (List.of(AutoUpdateType.QUPATH_AND_EXTENSIONS, AutoUpdateType.EXTENSIONS_ONLY).contains(updateCheckType)) {
			try {
				return QuPathGUI.getExtensionCatalogManager().getAvailableUpdates().get().stream()
						.map(extensionUpdate -> new UpdateManagerContainer.UpdateEntry(
								extensionUpdate.extensionName(),
								extensionUpdate.currentVersion(),
								extensionUpdate.newVersion(),
								() -> Commands.showInstalledExtensions(qupath),
								QuPathResources.getString("UpdateManager.openExtensionManager")
						))
						.toList();
			} catch (InterruptedException | ExecutionException e) {
				logger.warn("Cannot check updates on extensions", e);
			}
		}

		return List.of();
	}
}
