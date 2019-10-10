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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.roi.interfaces.ROI;

/**
 * Very basic rotation slider.
 * 
 * Doesn't permit image to rotate beyond 90 degrees.
 * 
 * @author Pete Bankhead
 *
 */
public class RotateImageCommand implements PathCommand {

	final private static Logger logger = LoggerFactory.getLogger(RotateImageCommand.class);

	final private QuPathGUI qupath;

	private Stage dialog;
	private Slider slider;

	public RotateImageCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	private void makeDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Rotate view");

		BorderPane pane = new BorderPane();

		final Label label = new Label("0 degrees");
		label.setTextAlignment(TextAlignment.CENTER);
		QuPathViewer viewerTemp = qupath.getViewer();
		slider = new Slider(-90, 90, viewerTemp == null ? 0 : Math.toDegrees(viewerTemp.getRotation()));
		slider.setMajorTickUnit(10);
		slider.setMinorTickCount(5);
		slider.setShowTickMarks(true);
		slider.valueProperty().addListener((v, o, n) -> {
			QuPathViewer viewer = qupath.getViewer();
			if (viewer == null)
				return;
			double rotation = slider.getValue();
			label.setText(String.format("%.1f degrees", rotation));
			viewer.setRotation(Math.toRadians(rotation));
		});

		Button btnReset = new Button("Reset");
		btnReset.setOnAction(e -> slider.setValue(0));

		Button btnTMAAlign = new Button("Straighten TMA");
		btnTMAAlign.setOnAction(e -> {

			QuPathViewer viewer = qupath.getViewer();
			if (viewer == null)
				return;
			TMAGrid tmaGrid = viewer.getHierarchy().getTMAGrid();
			if (tmaGrid == null || tmaGrid.getGridWidth() < 2)
				return;
			// Determine predominent angle
			List<Double> angles = new ArrayList<>();
			for (int y = 0; y < tmaGrid.getGridHeight(); y++) {
				for (int x = 1; x < tmaGrid.getGridWidth(); x++) {
					TMACoreObject core1 = tmaGrid.getTMACore(y, x-1);
					TMACoreObject core2 = tmaGrid.getTMACore(y, x);
					if (core1.isMissing() || core2.isMissing())
						continue;
					ROI roi1 = core1.getROI();
					ROI roi2 = core2.getROI();
					double angle = Double.NaN;
					if (roi1 != null && roi2 != null) {
						double dx = roi2.getCentroidX() - roi1.getCentroidX();
						double dy = roi2.getCentroidY() - roi1.getCentroidY();
						angle = Math.atan2(dy, dx);
						//								angle = Math.atan(dy / dx);
					}
					if (!Double.isNaN(angle)) {
						logger.debug("Angle :" + angle);
						angles.add(angle);
					}
				}
			}
			// Compute median angle
			if (angles.isEmpty())
				return;
			Collections.sort(angles);
			double angleMedian = Math.toDegrees(angles.get(angles.size()/2));
			slider.setValue(angleMedian);

			logger.debug("Median angle: " + angleMedian);

		});

		GridPane panelButtons = PaneToolsFX.createColumnGridControls(
				btnReset,
				btnTMAAlign
				);
		panelButtons.setPrefWidth(300);
		
		slider.setPadding(new Insets(5, 0, 10, 0));

		pane.setTop(label);
		pane.setCenter(slider);
		pane.setBottom(panelButtons);
		pane.setPadding(new Insets(10, 10, 10, 10));

		Scene scene = new Scene(pane);
		dialog.setScene(scene);
//		dialog.sizeToScene();
		dialog.setResizable(false);
//		dialog.pack();
//		dialog.setLocationRelativeTo(qupath.getViewer());

//		dialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	}

	@Override
	public void run() {
		if (dialog != null && dialog.isShowing())
			return;
//		if (dialog == null)
			makeDialog();
		dialog.show();
	}
}