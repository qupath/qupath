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

package qupath.lib.gui.helpers;

import java.text.NumberFormat;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Rectangle;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


/**
 * Panel used when specifying an annotation based upon its coordinates - 
 * useful when exactly the same size of annotation is needed (for some reason).
 * 
 * @author Pete Bankhead
 *
 */
public class AnnotationCreatorPanel {
	
	private QuPathGUI qupath;
	private GridPane pane;
	
	
	public AnnotationCreatorPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		init();
	}

	/**
	 * Create a label and associate it with a specified node.
	 * 
	 * @param node the node for which the label is being created
	 * @param text the label text
	 * @return
	 */
	public static Label createLabelFor(Node node, String text) {
		return createLabelFor(node, text, null);
	}
	
	/**
	 * Create a label and associate it with a specified node, optionally with a tooltip for both.
	 * <p>
	 * In addition to creating the label, the max width it set to Double.MAX_VALUE.
	 * 
	 * @param node the node for which the label is being created
	 * @param text the label text
	 * @param tooltip the tooltip to install for both node and label, or null if no tooltip should be added
	 * @return
	 */
	public static Label createLabelFor(Node node, String text, Tooltip tooltip) {
		var label = new Label(text);
		label.setLabelFor(node);
		label.setMaxWidth(Double.MAX_VALUE);
		if (tooltip != null) {
			label.setTooltip(tooltip);
			Tooltip.install(node, tooltip);
		}
		return label;
	}
	
	public static Label createBoundLabel(StringBinding binding) {
		var label = new Label();
		label.textProperty().bind(binding);
		return label;
	}
	
	
	private static enum ROI_TYPE { RECTANGLE, ELLIPSE; 
		@Override
		public String toString() {
			switch(this) {
			case ELLIPSE:
				return "Ellipse";
			case RECTANGLE:
				return "Rectangle";
			default:
				return "Unknown";
			}
		}
	}
	
	
	private void init() {
		pane = new GridPane();
		int row = 0;
		
		
		var cbMicrons = new CheckBox("Use " + GeneralTools.micrometerSymbol());
		var units = Bindings.createStringBinding(() -> {
			if (cbMicrons.isSelected())
				return GeneralTools.micrometerSymbol();
			return "px";
		}, cbMicrons.selectedProperty());
		
		
		var comboType = new ComboBox<ROI_TYPE>(
				FXCollections.observableArrayList(ROI_TYPE.values())
				);
		comboType.setMaxWidth(Double.MAX_VALUE);
		comboType.getSelectionModel().select(ROI_TYPE.RECTANGLE);
		
				
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Type of ROI to create",
				createLabelFor(comboType, "Type"),
				comboType, comboType);
		
		var comboClassification = new ComboBox<>(
				qupath.getAvailablePathClasses()
				);
		comboClassification.setMaxWidth(Double.MAX_VALUE);
		comboClassification.setCellFactory(o -> {
			return new ClassificationCell();
		});
//		comboClassification.cell;
		
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Classification for the annotation (may be empty if annotation should be unclassified)",
				createLabelFor(comboClassification, "Classification"),
				comboClassification, comboClassification);
		
		
		var tfX = new TextField("");
		var tfY = new TextField("");
		var tfWidth = new TextField("1000");
		var tfHeight = new TextField("1000");
		
		tfX.setPrefColumnCount(10);
		tfY.setPrefColumnCount(10);
		tfWidth.setPrefColumnCount(10);
		tfHeight.setPrefColumnCount(10);
		
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"X-coordinate of top left of annotation bounding box (if missing or < 0, annotation will be centered in current viewer)",
				createLabelFor(tfX, "X origin"),
				tfX,
				createBoundLabel(units));
		
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Y-coordinate of top left of annotation bounding box (if missing or < 0, annotation will be centered in current viewer)",
				createLabelFor(tfY, "Y origin"),
				tfY,
				createBoundLabel(units));
		
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Width of annotation bounding box (must be > 0)",
				createLabelFor(tfWidth, "Width"),
				tfWidth,
				createBoundLabel(units));
		
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Height of annotation bounding box (must be > 0)",
				createLabelFor(tfHeight, "Height"),
				tfHeight,
				createBoundLabel(units));
		
		
		cbMicrons.setMaxWidth(Double.MAX_VALUE);
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Specify coordinates in " + GeneralTools.micrometerSymbol() + " - pixel calibration information must be available",
				cbMicrons, cbMicrons, cbMicrons);
		
		var cbLock = new CheckBox("Set locked");
		cbLock.setMaxWidth(Double.MAX_VALUE);
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Set annotation as locked, so that it can't be immediately edited",
				cbLock, cbLock, cbLock);
		
		var tfName = new TextField("");
		PaneToolsFX.addGridRow(
				pane, row++, 0,
				"Name of annotation (can be empty)",
				createLabelFor(tfName, "Name"),
				tfName,
				tfName);
		
		
		var btnAdd = new Button("Add annotation");
		btnAdd.setOnAction(e -> {
			var viewer = qupath.getViewer();
			var imageData = viewer == null ? null : viewer.getImageData();
			if (imageData == null) {
				DisplayHelpers.showErrorMessage("Create annotation", "No image selected!");
				return;
			}
			var server = imageData.getServer();
			var hierarchy = imageData.getHierarchy();
			
			double xScale = 1;
			double yScale = 1;
			PixelCalibration cal = server.getPixelCalibration();
			if (cbMicrons.isSelected()) {
				if (!cal.hasPixelSizeMicrons()) {
					DisplayHelpers.showErrorMessage("Create annotation", "No pixel size information available! Try again using pixel units.");		
					return;
				}
				xScale = 1.0/cal.getPixelWidthMicrons();
				yScale = 1.0/cal.getPixelHeightMicrons();
			}
			
			var xOrig = tryToParse(tfX.getText());
			var yOrig = tryToParse(tfY.getText());
			var width = tryToParse(tfWidth.getText());
			var height = tryToParse(tfHeight.getText());
			
			if (width == null || width.doubleValue() <= 0 || height == null || height.doubleValue() <= 0) {
				DisplayHelpers.showErrorMessage("Create annotation", "Width and height must be specified, and > 0!");
				return;
			}
			
			double w = width.doubleValue() * xScale;
			double h = height.doubleValue() * yScale;
			
			// It helps to start at integer pixels, since otherwise the width can be surprising when exporting regions 
			// (since when requesting ROIs, Math.ceil is currently applied to ensure that the full ROI is included).
			double x;
			if (xOrig == null || xOrig.doubleValue() < 0)
				x = (int)Math.max(0, viewer.getCenterPixelX() - w / 2.0);
			else
				x = xOrig.doubleValue() * xScale;

			double y;
			if (yOrig == null || yOrig.doubleValue() < 0)
				y = (int)Math.max(viewer.getCenterPixelY() - h / 2.0, 0);
			else
				y = yOrig.doubleValue() * yScale;
			
			if (x + w > server.getWidth() || y + h > server.getHeight()) {
				DisplayHelpers.showErrorMessage("Create annotation", "Specified annotation is too large for the image bounds!");
				return;
			}
			
			int z = viewer.getZPosition();
			int t = viewer.getTPosition();
			ROI roi = null;
			switch (comboType.getSelectionModel().getSelectedItem()) {
			case ELLIPSE:
				roi = ROIs.createEllipseROI(x, y, w, h, ImagePlane.getPlane(z, t));
				break;
			case RECTANGLE:
				roi = ROIs.createRectangleROI(x, y, w, h, ImagePlane.getPlane(z, t));
				break;
			default:
				DisplayHelpers.showErrorMessage("Create annotation", "No ROI type selected!");
				return;
			}
			
			var pathClass = comboClassification.getSelectionModel().getSelectedItem();
			if (pathClass != null && !pathClass.isValid())
				pathClass = null;

			var annotation = PathObjects.createAnnotationObject(roi, pathClass);
			
			// Set name, if necessary
			var name = tfName.getText();
			if (name != null && !name.isEmpty())
				annotation.setName(name);
			
			if (cbLock.isSelected())
				((PathAnnotationObject)annotation).setLocked(true);
			
			hierarchy.addPathObject(annotation);
		});
		
		btnAdd.setMaxWidth(Double.MAX_VALUE);
		PaneToolsFX.addGridRow(pane, row++, 0, "Create annotation with specified options & add to object hierarchy",
				btnAdd, btnAdd, btnAdd
				);
		
		PaneToolsFX.setFillWidth(Boolean.TRUE,
				tfX, tfY, tfWidth, tfHeight, btnAdd, comboType);
		PaneToolsFX.setHGrowPriority(Priority.ALWAYS,
				tfX, tfY, tfWidth, tfHeight, btnAdd, comboType
				);
		
		pane.setHgap(5);
		pane.setVgap(5);
		pane.setPadding(new Insets(10));
		
	}
	

	static Number tryToParse(String text) {
		if (text == null || text.isBlank())
			return null;
		try {
			return NumberFormat.getInstance().parse(text);
		} catch (Exception e) {
			return null;
		}
	}
	
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	
	class ClassificationCell extends ListCell<PathClass> {

        @Override
        protected void updateItem(PathClass value, boolean empty) {
            super.updateItem(value, empty);
            int size = 10;
            if (value == null || empty) {
                setText(null);
                setGraphic(null);
            } else if (value.getName() == null) {
                setText("None");
                setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
            } else {
                setText(value.getName());
                setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
            }
        }

    }
	

}