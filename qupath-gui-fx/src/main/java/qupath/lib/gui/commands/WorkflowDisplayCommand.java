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
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.panels.WorkflowCommandLogView;

/**
 * Show logged commands, and optionally generate a script.
 * 
 * @author Pete Bankhead
 *
 */
public class WorkflowDisplayCommand implements Runnable {

	private QuPathGUI qupath;
	private Stage dialog;
	private WorkflowCommandLogView view;
	
	public WorkflowDisplayCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.view = new WorkflowCommandLogView(qupath);
	}
	
	
	
	@Override
	public void run() {
		if (dialog == null)
			dialog = createDialog();
		dialog.show();
	}
	
	
	
	protected Stage createDialog() {
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Workflow viewer");
		Pane pane = view.getPane();
		dialog.setScene(new Scene(pane, 400, 400));
		return dialog;
	}
	
}