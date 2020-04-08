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
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.panels.MeasurementMapPanel;
import qupath.lib.gui.viewer.OverlayOptions;

/**
 * Command to show a measurement map, i.e. color-coding detections according to 
 * their measurement values.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementMapCommand implements Runnable {

	private QuPathGUI qupath;
	private Stage dialog = null;
	private MeasurementMapPanel panel;
	
	public MeasurementMapCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
//		if (dialog != null) {
//			if (dialog.isShowing())
//				dialog.toFront();
//			else {
//				panel.updateMeasurements();
//				dialog.sizeToScene();
//				dialog.show();
//			}
////			panel.updateMeasurements();
//			return;
//		}
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Measurement maps");
		
		panel = new MeasurementMapPanel(qupath);
		BorderPane pane = new BorderPane();
		pane.setCenter(panel.getPane());
		
		Scene scene = new Scene(pane, 300, 400);
		dialog.setScene(scene);
		dialog.setMinWidth(300);
		dialog.setMinHeight(400);
//		pane.setMinSize(300, 400);
//		dialog.setResizable(false);
		
		dialog.setOnCloseRequest(e -> {
			OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
			if (overlayOptions != null)
				overlayOptions.resetMeasurementMapper();
			dialog.hide();
		});

		
		dialog.show();
	}
	
}