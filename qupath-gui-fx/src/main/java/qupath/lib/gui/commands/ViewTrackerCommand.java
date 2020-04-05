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

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.recording.ViewTrackerControlPanel;

/**
 * Command to launch view tracker, which records how the slide is browsed.
 * 
 * @author Pete Bankhead
 *
 */
public class ViewTrackerCommand implements PathCommand {

	private QuPathGUI qupath;
	
	private Stage dialog = null;
	
	public ViewTrackerCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}
	
	private void makeDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Tracking");
		final ViewTrackerControlPanel panel = new ViewTrackerControlPanel(qupath, qupath.getViewer());
		StackPane pane = new StackPane(panel.getNode());
		dialog.setScene(new Scene(pane));
		dialog.setResizable(false);
		dialog.setAlwaysOnTop(true);
		dialog.setOnHidden(e -> {
			if (panel != null && panel.getViewTracker() != null)
				panel.getViewTracker().setRecording(false);
			dialog = null;
		});
	}
	
	@Override
	public void run() {
		if (dialog != null) {
			if (dialog.isShowing())
				dialog.toFront();
			else
				dialog.show();
			return;
		}
		makeDialog();
		dialog.show();
	}

}
