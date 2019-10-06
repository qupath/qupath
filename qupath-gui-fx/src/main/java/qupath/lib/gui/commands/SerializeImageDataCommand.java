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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;

/**
 * Command to save the current ImageData.
 * 
 * @author Pete Bankhead
 *
 */
public class SerializeImageDataCommand implements PathCommand {
		
		final private QuPathGUI qupath;
		private boolean overwriteExisting = false;
				
		public SerializeImageDataCommand(final QuPathGUI qupath, final boolean overwriteExisting) {
			super();
			this.qupath = qupath;
			this.overwriteExisting = overwriteExisting;
		}
		@Override
		public void run() {
			ImageData<BufferedImage> imageData = qupath.getImageData();
			if (imageData == null) {
				DisplayHelpers.showErrorMessage("Serialization error", "No image data to save!");
				return;
			}
			try {
				var project = qupath.getProject();
				var entry = project == null ? null : project.getEntry(imageData);
				if (entry != null) {
					if (overwriteExisting || DisplayHelpers.showConfirmDialog("Save changes", "Save changes to " + entry.getImageName() + "?")) {
							entry.saveImageData(imageData);
					} else
						return;
				} else {
					String lastSavedPath = imageData.getLastSavedPath();
					File file = null;
					if (lastSavedPath != null) {
						// Use the last path, if required
						if (overwriteExisting)
							file = new File(lastSavedPath);
						if (file == null || !file.isFile()) {
							File fileDefault = new File(lastSavedPath);
							file = qupath.getDialogHelper().promptToSaveFile(null, fileDefault.getParentFile(), fileDefault.getName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
						}
					}
					else {
						ImageServer<?> server = imageData.getServer();
						String name = ServerTools.getDisplayableImageName(server);
						if (name.contains(".")) {
							try {
								name = GeneralTools.getNameWithoutExtension(new File(name));
							} catch (Exception e) {}
						}
						file = qupath.getDialogHelper().promptToSaveFile(null, null, name, "QuPath Serialized Data", PathPrefs.getSerializationExtension());
					}
					if (file == null)
						return;
					
					PathIO.writeImageData(file, imageData);
				}
			} catch (IOException e) {
				DisplayHelpers.showErrorMessage("Save ImageData", e);
			}
		}
		
		
	}