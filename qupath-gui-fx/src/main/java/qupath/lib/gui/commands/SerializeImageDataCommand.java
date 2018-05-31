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

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.PathIO;

/**
 * Command to save the current ImageData.
 *
 * @author Pete Bankhead
 */
public class SerializeImageDataCommand implements PathCommand {

    final private QuPathGUI qupath;
    private boolean overwriteExisting = false;
    private boolean showSavePopUp;

    public SerializeImageDataCommand(final QuPathGUI qupath, final boolean overwriteExisting,
                                     final boolean showSavePopUp) {
        super();
        this.qupath = qupath;
        this.overwriteExisting = overwriteExisting;
        this.showSavePopUp = showSavePopUp;
    }

    @Override
    public void run() {
        ImageData<BufferedImage> imageData = qupath.getImageData();
        if (imageData == null) {
            DisplayHelpers.showErrorMessage("Serialization error", "No image data to save!");
            return;
        }
        String lastSavedPath = imageData.getLastSavedPath();
        if (lastSavedPath == null && qupath.getProject() != null) {
            // If we have a project, default to the 'correct' place to save it
            lastSavedPath = QuPathGUI.getImageDataFile(qupath.getProject(), qupath.getProject().getImageEntry(imageData.getServerPath())).getAbsolutePath();
        }
        File file = null;
        if (lastSavedPath != null) {
            // Use the last path, if required
            if (overwriteExisting)
                file = new File(lastSavedPath);
            if (file == null || !file.isFile()) {
                File fileDefault = new File(lastSavedPath);
                if (showSavePopUp) {
                    file = qupath.getDialogHelper().promptToSaveFile(null, fileDefault.getParentFile(), fileDefault.getName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
                } else {
                    file = fileDefault;
                }
            }
        } else {
            ImageServer<?> server = imageData.getServer();
            file = qupath.getDialogHelper().promptToSaveFile(null, null, server.getShortServerName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
        }
        if (file == null)
            return;

        PathIO.writeImageData(file, imageData);
    }


}