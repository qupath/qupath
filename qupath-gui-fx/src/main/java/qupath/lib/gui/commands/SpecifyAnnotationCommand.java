/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023, 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.commands;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PathClassListCell;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.text.MessageFormat;
import java.text.NumberFormat;

/**
 * Command to create a new rectangular/ellipse annotation object by 
 * specifying the coordinates for the bounding box.
 * 
 * @author Pete Bankhead
 *
 */
class SpecifyAnnotationCommand {

	private QuPathGUI qupath;
	private GridPane pane;

	public SpecifyAnnotationCommand(final QuPathGUI qupath) {
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


	private enum ROI_TYPE { RECTANGLE, ELLIPSE;
		@Override
		public String toString() {
            return QuPathResources.getString(switch (this) {
                case ELLIPSE -> "Commands.SpecifyAnnotation.ellipse";
                case RECTANGLE -> "Commands.SpecifyAnnotation.rectangle";
            });
		}
	}


	private void init() {
		pane = new GridPane();
		int row = 0;


		var cbMicrons = new CheckBox(MessageFormat.format(
				QuPathResources.getString("Commands.SpecifyAnnotation.use"),
				GeneralTools.micrometerSymbol()
		));
		var units = Bindings.createStringBinding(() -> {
			if (cbMicrons.isSelected())
				return GeneralTools.micrometerSymbol();
			return QuPathResources.getString("Commands.SpecifyAnnotation.px");
		}, cbMicrons.selectedProperty());


		var comboType = new ComboBox<>(
                FXCollections.observableArrayList(ROI_TYPE.values())
        );
		comboType.setMaxWidth(Double.MAX_VALUE);
		comboType.getSelectionModel().select(ROI_TYPE.RECTANGLE);


		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.typeDescription"),
				createLabelFor(comboType, QuPathResources.getString("Commands.SpecifyAnnotation.type")),
				comboType, comboType);

		var comboClassification = new ComboBox<>(
				qupath.getAvailablePathClasses()
				);
		comboClassification.setMaxWidth(Double.MAX_VALUE);
		comboClassification.setCellFactory(o -> {
			return new PathClassListCell();
		});
		//			comboClassification.cell;

		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.classificationDescription"),
				createLabelFor(comboClassification, QuPathResources.getString("Commands.SpecifyAnnotation.classification")),
				comboClassification, comboClassification);


		var tfX = new TextField("");
		var tfY = new TextField("");
		var tfWidth = new TextField("1000");
		var tfHeight = new TextField("1000");

		tfX.setPrefColumnCount(10);
		tfY.setPrefColumnCount(10);
		tfWidth.setPrefColumnCount(10);
		tfHeight.setPrefColumnCount(10);

		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.xOriginDescription"),
				createLabelFor(tfX, QuPathResources.getString("Commands.SpecifyAnnotation.xOrigin")),
				tfX,
				createBoundLabel(units));

		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.yOriginDescription"),
				createLabelFor(tfY, QuPathResources.getString("Commands.SpecifyAnnotation.yOrigin")),
				tfY,
				createBoundLabel(units));

		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.widthDescription"),
				createLabelFor(tfWidth, QuPathResources.getString("Commands.SpecifyAnnotation.width")),
				tfWidth,
				createBoundLabel(units));

		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.heightDescription"),
				createLabelFor(tfHeight, QuPathResources.getString("Commands.SpecifyAnnotation.height")),
				tfHeight,
				createBoundLabel(units));


		cbMicrons.setMaxWidth(Double.MAX_VALUE);
		GridPaneUtils.addGridRow(
				pane, row++, 0,
				MessageFormat.format(
						QuPathResources.getString("Commands.SpecifyAnnotation.specifyCoordinates"),
						GeneralTools.micrometerSymbol()
				),
				cbMicrons, cbMicrons, cbMicrons);

		var cbLock = new CheckBox(QuPathResources.getString("Commands.SpecifyAnnotation.setLocked"));
		cbLock.setMaxWidth(Double.MAX_VALUE);
		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.setLockedDescription"),
				cbLock, cbLock, cbLock);

		var tfName = new TextField("");
		GridPaneUtils.addGridRow(
				pane, row++, 0,
				QuPathResources.getString("Commands.SpecifyAnnotation.nameDescription"),
				createLabelFor(tfName, QuPathResources.getString("Commands.SpecifyAnnotation.name")),
				tfName,
				tfName);


		var btnAdd = new Button(QuPathResources.getString("Commands.SpecifyAnnotation.addAnnotation"));
		btnAdd.setOnAction(e -> {
			var viewer = qupath.getViewer();
			var imageData = viewer == null ? null : viewer.getImageData();
			if (imageData == null) {
				GuiTools.showNoImageError(QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotation"));
				return;
			}
			var server = imageData.getServer();
			var hierarchy = imageData.getHierarchy();

			double xScale = 1;
			double yScale = 1;
			PixelCalibration cal = server.getPixelCalibration();
			if (cbMicrons.isSelected()) {
				if (!cal.hasPixelSizeMicrons()) {
					Dialogs.showErrorMessage(
							QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotation"),
							QuPathResources.getString("Commands.SpecifyAnnotation.noPixelSizeAvailable")
					);
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
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotation"),
						QuPathResources.getString("Commands.SpecifyAnnotation.widthAndHeightMustBeSpecified")
				);
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
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotation"),
						QuPathResources.getString("Commands.SpecifyAnnotation.specifiedAnnotationTooLarge")
				);
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
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotation"),
						QuPathResources.getString("Commands.SpecifyAnnotation.noRoiTypeSelected")
				);
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

			hierarchy.addObject(annotation);
		});

		btnAdd.setMaxWidth(Double.MAX_VALUE);
		GridPaneUtils.addGridRow(pane, row++, 0, QuPathResources.getString("Commands.SpecifyAnnotation.createAnnotationWithSpecifiedOptions"),
				btnAdd, btnAdd, btnAdd
				);

		GridPaneUtils.setFillWidth(Boolean.TRUE,
				tfX, tfY, tfWidth, tfHeight, btnAdd, comboType);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS,
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


}