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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ImageWriterTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command to export an image region using a specified downsample factor, with or without an overlay.
 * 
 * @author Pete Bankhead
 *
 */
public class ExportImageRegionCommand implements PathCommand {

	private QuPathGUI qupath;
	
	private DoubleProperty exportDownsample = PathPrefs.createPersistentPreference("exportRegionDownsample", 1.0);
	private StringProperty selectedImageType = PathPrefs.createPersistentPreference("exportRegionImageFormat", "PNG");
	private BooleanProperty includeOverlay = PathPrefs.createPersistentPreference("exportRegionIncludeOverlay", false);
	
	public ExportImageRegionCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || viewer.getServer() == null) {
			DisplayHelpers.showErrorMessage("Export image region", "No viewer & image selected!");
			return;
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		PathObject pathObject = viewer.getSelectedObject();
		ROI roi = pathObject == null ? null : pathObject.getROI();
		
		double regionWidth = roi == null ? server.getWidth() : roi.getBoundsWidth();
		double regionHeight = roi == null ? server.getHeight() : roi.getBoundsHeight();
		
		// Create a dialog
		GridPane pane = new GridPane();
		pane.add(new Label("Export format"), 0, 0);
		ComboBox<String> comboImageType = new ComboBox<>();
		comboImageType.getItems().setAll("PNG", "JPEG");
		comboImageType.setTooltip(new Tooltip("Choose export image format"));
		comboImageType.getSelectionModel().select(selectedImageType.get());
		comboImageType.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(comboImageType, Priority.ALWAYS);
		pane.add(comboImageType, 1, 0);
		
		pane.add(new Label("Downsample factor"), 0, 1);
		TextField tfDownsample = new TextField();
		pane.add(tfDownsample, 1, 1);
		tfDownsample.setTooltip(new Tooltip("Amount to scale down image - choose 1 to export at full resolution (note: for large images this may not succeed for memory reasons)"));
		ObservableDoubleValue downsample = Bindings.createDoubleBinding(() -> {
			try {
				return Double.parseDouble(tfDownsample.getText());
			} catch (NumberFormatException e) {
				return Double.NaN;
			}
		}, tfDownsample.textProperty());
		
		long maxPixels = 10000*5000;
		
		Label labelSize = new Label();
		labelSize.setMinWidth(400);
		labelSize.setTextAlignment(TextAlignment.CENTER);
		labelSize.setContentDisplay(ContentDisplay.CENTER);
		labelSize.setAlignment(Pos.CENTER);
		labelSize.setMaxWidth(Double.MAX_VALUE);
		labelSize.setTooltip(new Tooltip("Estimated size of exported image"));
		pane.add(labelSize, 0, 2, 2, 1);
		labelSize.textProperty().bind(Bindings.createStringBinding(() -> {
			if (!Double.isFinite(downsample.get())) {
				labelSize.setTextFill(Color.RED);
				return "Invalid downsample value!  Must be >= 1";
			}
			else {
				long w = (long)(regionWidth / downsample.get() + 0.5);
				long h = (long)(regionHeight / downsample.get() + 0.5);
				String warning = "";
				if (w * h > maxPixels) {
					labelSize.setTextFill(Color.RED);
					warning = " (too big!)";
				} else if (w < 5 || h < 5) {
					labelSize.setTextFill(Color.RED);
					warning = " (too small!)";					
				} else
					labelSize.setTextFill(Color.BLACK);
				return String.format("Output image size: %d x %d pixels%s",
						w, h, warning
						);
			}
		}, downsample));
		GridPane.setHgrow(labelSize, Priority.ALWAYS);
		
		CheckBox cbIncludeOverlay = new CheckBox("Include overlay");
		cbIncludeOverlay.selectedProperty().bindBidirectional(includeOverlay);
		pane.add(cbIncludeOverlay, 0, 3, 2, 1);
		
		tfDownsample.setText(Double.toString(exportDownsample.get()));
		
		pane.setVgap(5);
		pane.setHgap(5);
		
		if (!DisplayHelpers.showConfirmDialog("Export image region", pane))
			return;
		
		int w = (int)(regionWidth / downsample.get() + 0.5);
		int h = (int)(regionHeight / downsample.get() + 0.5);
		if (w * h > maxPixels) {
			DisplayHelpers.showErrorNotification("Export image region", "Requested export region too large - try selecting a smaller region, or applying a higher downsample factor");
			return;
		}
		
		if (downsample.get() < 1 || !Double.isFinite(downsample.get())) {
			DisplayHelpers.showErrorMessage("Export image region", "Downsample factor must be >= 1!");
			return;
		}
				
		exportDownsample.set(downsample.get());
		selectedImageType.set(comboImageType.getSelectionModel().getSelectedItem());
		
		// Create RegionRequest
		RegionRequest request = null;
		if (pathObject == null || !pathObject.hasROI())
			request = RegionRequest.createInstance(server.getPath(), exportDownsample.get(), 0, 0, server.getWidth(), server.getHeight());
		else
			request = RegionRequest.createInstance(server.getPath(), exportDownsample.get(), roi);				

		// Create a sensible default file name, and prompt for the actual name
		String ext = "JPEG".equals(selectedImageType.get()) ? "jpg" : selectedImageType.get().toLowerCase();
		String defaultName = roi == null ? server.getShortServerName() : 
			String.format("%s (%s, %d, %d, %d, %d)", server.getShortServerName(), GeneralTools.createFormatter(2).format(request.getDownsample()), request.getX(), request.getY(), request.getWidth(), request.getHeight());
		File fileOutput = qupath.getDialogHelper().promptToSaveFile("Export image region", null, defaultName, selectedImageType.get(), ext);
		if (fileOutput == null)
			return;
		
		if (includeOverlay.get())
			ImageWriterTools.writeImageRegionWithOverlay(viewer, request, fileOutput.getAbsolutePath());
		else
			ImageWriterTools.writeImageRegion(server, request, fileOutput.getAbsolutePath());
	}
	
}