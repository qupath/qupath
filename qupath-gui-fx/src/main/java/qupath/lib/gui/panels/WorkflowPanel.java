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

package qupath.lib.gui.panels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;


/**
 * Panel to show command history.
 * 
 * @author Pete Bankhead
 *
 */
public class WorkflowPanel {
	
	final private static Logger logger = LoggerFactory.getLogger(WorkflowPanel.class);
	
//	private QuPathGUI qupath;
	
	private BorderPane pane = new BorderPane();

	private WorkflowCommandLogView commandLogView;
	
	public WorkflowPanel(final QuPathGUI qupath) {
//		this.qupath = qupath;
		this.commandLogView = new WorkflowCommandLogView(qupath);
		
		TitledPane titledLog = new TitledPane("Command history", commandLogView.getPane());
		pane.setCenter(titledLog);
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
}
