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
import java.io.IOException;
import java.util.function.Function;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;
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
//	private StringProperty selectedImageType = PathPrefs.createPersistentPreference("exportRegionImageFormat", "PNG");
	
	private ImageWriter<BufferedImage> lastWriter = null;
	private boolean renderedImage = false;
	
	/**
	 * Command to export image regions.
	 * @param qupath the QuPath GUI instance from which the current viewer will be identified.
	 * @param renderedImage if true, an RGB image will be export - rendering whatever overlays are currently visible. If false, raw pixels will be export where possible.
	 */
	public ExportImageRegionCommand(final QuPathGUI qupath, final boolean renderedImage) {
		this.qupath = qupath;
		this.renderedImage = renderedImage;
	}

	@Override
	public void run() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || viewer.getServer() == null) {
			DisplayHelpers.showErrorMessage("Export image region", "No viewer & image selected!");
			return;
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		if (renderedImage)
			server = RenderedImageServer.createRenderedServer(viewer);
		PathObject pathObject = viewer.getSelectedObject();
		ROI roi = pathObject == null ? null : pathObject.getROI();
		
		double regionWidth = roi == null ? server.getWidth() : roi.getBoundsWidth();
		double regionHeight = roi == null ? server.getHeight() : roi.getBoundsHeight();
		
		// Create a dialog
		GridPane pane = new GridPane();
		int row = 0;
		pane.add(new Label("Export format"), 0, row);
		ComboBox<ImageWriter<BufferedImage>> comboImageType = new ComboBox<>();
		
		Function<ImageWriter<BufferedImage>, String> fun = (ImageWriter<BufferedImage> writer) -> writer.getName();
		comboImageType.setCellFactory(p -> new StringifyListCell<>(fun));
		comboImageType.setButtonCell(new StringifyListCell<>(fun));
		
		var writers = ImageWriterTools.getCompatibleWriters(server, null);
		comboImageType.getItems().setAll(writers);
		comboImageType.setTooltip(new Tooltip("Choose export image format"));
		if (writers.contains(lastWriter))
			comboImageType.getSelectionModel().select(lastWriter);
		else
			comboImageType.getSelectionModel().selectFirst();
		comboImageType.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(comboImageType, Priority.ALWAYS);
		pane.add(comboImageType, 1, row++);
		
		TextArea textArea = new TextArea();
		textArea.setPrefRowCount(2);
		textArea.setEditable(false);
		textArea.setWrapText(true);
//		textArea.setPadding(new Insets(15, 0, 0, 0));
		comboImageType.setOnAction(e -> textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails()));			
		textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails());
		pane.add(textArea, 0, row++, 2, 1);
		
		var label = new Label("Downsample factor");
		pane.add(label, 0, row);
		TextField tfDownsample = new TextField();
		label.setLabelFor(tfDownsample);
		pane.add(tfDownsample, 1, row++);
		tfDownsample.setTooltip(new Tooltip("Amount to scale down image - choose 1 to export at full resolution (note: for large images this may not succeed for memory reasons)"));
		ObservableDoubleValue downsample = Bindings.createDoubleBinding(() -> {
			try {
				return Double.parseDouble(tfDownsample.getText());
			} catch (NumberFormatException e) {
				return Double.NaN;
			}
		}, tfDownsample.textProperty());
		
		// Define a sensible limit for non-pyramidal images
		long maxPixels = 10000*10000;
		
		Label labelSize = new Label();
		labelSize.setMinWidth(400);
		labelSize.setTextAlignment(TextAlignment.CENTER);
		labelSize.setContentDisplay(ContentDisplay.CENTER);
		labelSize.setAlignment(Pos.CENTER);
		labelSize.setMaxWidth(Double.MAX_VALUE);
		labelSize.setTooltip(new Tooltip("Estimated size of exported image"));
		pane.add(labelSize, 0, row++, 2, 1);
		labelSize.textProperty().bind(Bindings.createStringBinding(() -> {
			if (!Double.isFinite(downsample.get())) {
				labelSize.setStyle("-fx-text-fill: red;");
				return "Invalid downsample value!  Must be >= 1";
			}
			else {
				long w = (long)(regionWidth / downsample.get() + 0.5);
				long h = (long)(regionHeight / downsample.get() + 0.5);
				String warning = "";
				var writer = comboImageType.getSelectionModel().getSelectedItem();
				boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
				if (!supportsPyramid && w * h > maxPixels) {
					labelSize.setStyle("-fx-text-fill: red;");
					warning = " (too big!)";
				} else if (w < 5 || h < 5) {
					labelSize.setStyle("-fx-text-fill: red;");
					warning = " (too small!)";					
				} else
					labelSize.setStyle(null);
				return String.format("Output image size: %d x %d pixels%s",
						w, h, warning
						);
			}
		}, downsample, comboImageType.getSelectionModel().selectedIndexProperty()));
		
		tfDownsample.setText(Double.toString(exportDownsample.get()));
		
		PaneToolsFX.setMaxWidth(Double.MAX_VALUE, labelSize, textArea, tfDownsample, comboImageType);
		PaneToolsFX.setHGrowPriority(Priority.ALWAYS, labelSize, textArea, tfDownsample, comboImageType);
		
		pane.setVgap(5);
		pane.setHgap(5);
		
		if (!DisplayHelpers.showConfirmDialog("Export image region", pane))
			return;
		
		var writer = comboImageType.getSelectionModel().getSelectedItem();
		boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
		int w = (int)(regionWidth / downsample.get() + 0.5);
		int h = (int)(regionHeight / downsample.get() + 0.5);
		if (!supportsPyramid && w * h > maxPixels) {
			DisplayHelpers.showErrorNotification("Export image region", "Requested export region too large - try selecting a smaller region, or applying a higher downsample factor");
			return;
		}
		
		if (downsample.get() < 1 || !Double.isFinite(downsample.get())) {
			DisplayHelpers.showErrorMessage("Export image region", "Downsample factor must be >= 1!");
			return;
		}
				
		exportDownsample.set(downsample.get());
		
		// Now that we know the output, we can create a new server to ensure it is downsampled as the necessary resolution
		if (renderedImage && downsample.get() != server.getDownsampleForResolution(0))
			server = new RenderedImageServer.Builder(viewer).downsamples(downsample.get()).build();
		
//		selectedImageType.set(comboImageType.getSelectionModel().getSelectedItem());
		
		// Create RegionRequest
		RegionRequest request = null;
		if (pathObject != null && pathObject.hasROI())
			request = RegionRequest.createInstance(server.getPath(), exportDownsample.get(), roi);				

		// Create a sensible default file name, and prompt for the actual name
		String ext = writer.getDefaultExtension();
		String writerName = writer.getName();
		String defaultName = GeneralTools.getNameWithoutExtension(new File(ServerTools.getDisplayableImageName(server)));
		if (roi != null) {
			defaultName = String.format("%s (%s, x=%d, y=%d, w=%d, h=%d)", defaultName,
					GeneralTools.formatNumber(request.getDownsample(), 2),
					request.getX(), request.getY(), request.getWidth(), request.getHeight());
		}
		File fileOutput = qupath.getDialogHelper().promptToSaveFile("Export image region", null, defaultName, writerName, ext);
		if (fileOutput == null)
			return;
		
		try {
			if (request == null) {
				if (exportDownsample.get() == 1.0)
					writer.writeImage(server, fileOutput.getAbsolutePath());
				else
					writer.writeImage(ImageServers.pyramidalize(server, exportDownsample.get()), fileOutput.getAbsolutePath());
			} else
				writer.writeImage(server, request, fileOutput.getAbsolutePath());
			lastWriter = writer;
		} catch (IOException e) {
			DisplayHelpers.showErrorMessage("Export region", e);
		}
	}
	
	
	static class StringifyListCell<T> extends ListCell<T> {
		
		private Function<T, String> fun;
		
		StringifyListCell(Function<T, String> fun) {
			super();
			this.fun = fun;
		}
		
		@Override
		protected void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			if (empty)
				setText(null);
			else
				setText(fun.apply(item));
		}
		
	}
	
	
}