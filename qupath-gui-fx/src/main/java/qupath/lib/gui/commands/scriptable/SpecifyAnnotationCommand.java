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

package qupath.lib.gui.commands.scriptable;

import javafx.scene.Scene;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.AnnotationCreatorPanel;

/**
 * Command to create a new rectangular/ellipse annotation object by 
 * specifying the coordinates for the bounding box.
 * 
 * @author Pete Bankhead
 *
 */
public class SpecifyAnnotationCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	public SpecifyAnnotationCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	@Override
	public void run() {
		AnnotationCreatorPanel pane = new AnnotationCreatorPanel(qupath);
		
		var stage = new Stage();
		var scene = new Scene(pane.getPane());
		stage.setScene(scene);
		stage.setWidth(300);
		stage.setTitle("Specify annotation");
		stage.initOwner(qupath.getStage());
		stage.show();
		
	}

}
