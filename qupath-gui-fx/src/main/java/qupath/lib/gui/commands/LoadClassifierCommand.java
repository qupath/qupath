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

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.panels.classify.PathClassifierPanel;

/**
 * Simple command to load a classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class LoadClassifierCommand implements PathCommand {
	
	final private QuPathGUI qupath;
	
	private Stage dialog;
	
	public LoadClassifierCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (dialog == null)
			createDialog();
		
		if (dialog.isShowing())
			dialog.toFront();
		else {
			dialog.show();
		}
		
	}
	
	
	void createDialog() {
		dialog = new Stage();
		dialog.setTitle("Classifier");
		dialog.initOwner(qupath.getStage());
		BorderPane pane = new BorderPane();
		pane.setCenter(new PathClassifierPanel(qupath).getPane());
		pane.setPadding(new Insets(10, 10, 10, 10));
		dialog.setScene(new Scene(pane, 300, 400));
	}
	
	
}