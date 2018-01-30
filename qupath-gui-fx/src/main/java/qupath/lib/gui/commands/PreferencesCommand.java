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
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.panels.PreferencePanel;

/**
 * Command to show a basic property-editing window.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencesCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	private Stage dialog;
	private PreferencePanel panel;
	
	public PreferencesCommand(final QuPathGUI qupath, final PreferencePanel panel) {
		super();
		this.qupath = qupath;
		this.panel = panel;
	}

	@Override
	public void run() {
		if (dialog == null) {
			if (panel == null)
				panel = new PreferencePanel(qupath);
			
			dialog = new Stage();
			dialog.initOwner(qupath.getStage());
//			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.setTitle("Preferences");
			
			Button btnClose = new Button("Close");
			btnClose.setOnAction(e -> {
				dialog.hide();
			});
			
			BorderPane pane = new BorderPane();
			pane.setCenter(panel.getNode());
			pane.setBottom(btnClose);
			if (qupath != null && qupath.getStage() != null) {
				pane.setPrefHeight(Math.round(Math.max(300, qupath.getStage().getHeight()*0.75)));
			}
			btnClose.prefWidthProperty().bind(pane.widthProperty());
			dialog.setScene(new Scene(pane));
			dialog.setMinWidth(300);
			dialog.setMinHeight(300);
		}
		
		dialog.show();
	}

}
