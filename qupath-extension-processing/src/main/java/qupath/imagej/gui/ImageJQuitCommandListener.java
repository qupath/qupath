/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.imagej.gui;

import ij.CommandListener;
import ij.IJ;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.plugin.frame.Editor;
import javafx.application.Platform;
import qupath.lib.gui.prefs.SystemMenuBar;

import java.awt.*;
import java.util.Set;

/**
 * We need to use a CommandListener rather than a WindowListener,
 * because this is the only way we can intercept calls to save changes for any open images.
 */
class ImageJQuitCommandListener implements CommandListener {

    private final boolean blockQuit;

    ImageJQuitCommandListener(boolean blockQuit) {
        this.blockQuit = blockQuit;
    }

    @Override
    public String commandExecuting(String command) {
        if ("quit".equalsIgnoreCase(command)) {
            if (hasUnsavedChanges()) {
                if (!IJ.showMessageWithCancel("Close ImageJ", "Close all ImageJ windows without saving changes?"))
                    return null;
            }
            closeWindowsQuietly();
            // If we don't want to quit entirely (e.g. we have a single Fiji instance),
            // return null to block the command
            if (blockQuit)
                return null;
        }
        return command;
    }

    private boolean hasUnsavedChanges() {
        for (var frame : Frame.getFrames()) {
            if (!frame.isShowing())
                continue;
            if (frame instanceof ImageWindow win) {
                var imp = win.getImagePlus();
                if (imp != null && imp.changes)
                    return true;
            } else if (frame instanceof Editor editor) {
                if (editor.fileChanged())
                    return true;
            }
        }
        return false;
    }

    /**
     * Close all windows associated with ImageJ quietly, without prompting to save changes.
     */
    private void closeWindowsQuietly() {
        var ij = IJ.getInstance();
        if (ij == null) {
            return;
        }
        ij.requestFocus();
        var nonImageFrames = Set.of(WindowManager.getNonImageWindows());
        for (var frame : Frame.getFrames()) {
            if (frame instanceof ImageWindow win) {
                var imp = win.getImagePlus();
                if (imp != null) {
                    imp.setIJMenuBar(false);
                    imp.changes = false;
                    imp.close();
                }
            } else if (nonImageFrames.contains(frame)) {
                frame.setVisible(false);
                frame.dispose();
                WindowManager.removeWindow(frame);
            }
        }
        ij.setMenuBar(null);
        ij.setVisible(false);
        Platform.runLater(() -> SystemMenuBar.setOverrideSystemMenuBar(false));
    }

}
