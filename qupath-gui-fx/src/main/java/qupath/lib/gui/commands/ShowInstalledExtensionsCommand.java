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

package qupath.lib.gui.commands;

import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.extensionmanager.gui.ExtensionManager;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Command to start an {@link ExtensionManager}.
 */
class ShowInstalledExtensionsCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(ShowInstalledExtensionsCommand.class);
	private static final Map<QuPathGUI, ShowInstalledExtensionsCommand> instances = new WeakHashMap<>();
	private final QuPathGUI qupath;
	private Stage dialog;

	private ShowInstalledExtensionsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	/**
	 * Show an {@link ExtensionManager}.
	 *
	 * @param qupath the QuPath GUI owning the window
	 */
	public static void showInstalledExtensions(QuPathGUI qupath) {
		getInstance(qupath).showInstalledExtensions();
	}

	private static ShowInstalledExtensionsCommand getInstance(QuPathGUI qupath) {
		return instances.computeIfAbsent(qupath, ShowInstalledExtensionsCommand::new);
	}

	private void showInstalledExtensions() {
		if (dialog == null) {
			dialog = createAndShowDialog(qupath);
		} else {
			dialog.show();
			dialog.requestFocus();
		}
	}

	private static Stage createAndShowDialog(QuPathGUI qupath) {
		Stage dialog = null;

		try {
			dialog = new ExtensionManager(
					QuPathGUI.getExtensionCatalogManager(),
					() -> Commands.requestUserDirectory(true)
			);

			FXUtils.addCloseWindowShortcuts(dialog);
			dialog.initOwner(qupath.getStage());
			dialog.show();
		} catch (IOException e) {
			logger.error("Unable to open extension control pane", e);

			Dialogs.showErrorMessage(
					QuPathResources.getString("ShowInstalledExtensionsCommand.extensionManager"),
					QuPathResources.getString("ShowInstalledExtensionsCommand.unableToOpen")
			);
		}
		return dialog;
	}
}
