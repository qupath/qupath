/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command to help create annotations defining regions that will be further annotated for 
 * algorithm training.
 * 
 * @author Pete Bankhead
 *
 */
public class CreateRegionAnnotationsCommand implements Runnable {
	
	private final QuPathGUI qupath;
	private Stage stage;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public CreateRegionAnnotationsCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (stage == null) {
			stage = new Stage();
			stage.initOwner(qupath.getStage());
			stage.setTitle("Create regions");
			stage.setScene(new Scene(RegionMaker.create(qupath).getPane()));
		}
		stage.show();
	}
	
	
	static class RegionMaker {
		
		private static final Logger logger = LoggerFactory.getLogger(RegionMaker.class);
		
		public static enum RegionLocation {VIEW_CENTER, IMAGE_CENTER, RANDOM;
			
			@Override
			public String toString() {
				switch (this) {
				case IMAGE_CENTER:
					return "Image center";
				case RANDOM:
					return "Random";
				case VIEW_CENTER:
					return "Viewer center";
				default:
					return "Unknown";
				}
			}
		
		};
		
		public static enum RegionUnits {PIXELS, MICRONS;
			
			@Override
			public String toString() {
				switch (this) {
				case PIXELS:
					return "Pixels";
				case MICRONS:
					return GeneralTools.micrometerSymbol();
				default:
					return "Unknown";
				}
			}
		
		};
		
		private QuPathGUI qupath;
		private QuPathViewer viewerDefault;
		
		private GridPane pane = new GridPane();
		
		private TextField tfRegionWidth = new TextField("500");
		private TextField tfRegionHeight = new TextField("500");
		private ComboBox<RegionUnits> comboUnits = new ComboBox<>(FXCollections.observableArrayList(RegionUnits.values()));
		private ComboBox<PathClass> comboClassification = new ComboBox<>();
		private ComboBox<RegionLocation> comboLocation = new ComboBox<>(FXCollections.observableArrayList(RegionLocation.values()));
		
		private RegionMaker(final QuPathGUI qupath) {
			this.qupath = qupath;
			init();
		}
		
		private void init() {
			int row = 0;
			addLabelled("Width", tfRegionWidth, 0, row++, "Define region width");
			addLabelled("Height", tfRegionHeight, 0, row++, "Define region height");
			addLabelled("Size units", comboUnits, 0, row++, "Choose the units used to define the region width & height");
			addLabelled("Classification", comboClassification, 0, row++, "Choose the default classification to be applied to the region");

			if (qupath.getImageData() == null || !qupath.getImageData().getServer().getPixelCalibration().hasPixelSizeMicrons())
				comboUnits.getSelectionModel().select(RegionUnits.PIXELS);
			else
				comboUnits.getSelectionModel().select(RegionUnits.MICRONS);
			
			comboClassification.setItems(qupath.getAvailablePathClasses());
			if (comboClassification.getItems().contains(PathClass.StandardPathClasses.REGION))
				comboClassification.getSelectionModel().select(PathClass.StandardPathClasses.REGION);
			else
				comboClassification.getSelectionModel().select(PathClass.NULL_CLASS);
			
			comboLocation.getItems().setAll(RegionLocation.values());
			comboLocation.getSelectionModel().select(RegionLocation.VIEW_CENTER);
			addLabelled("Location", comboLocation, 0, row++, "Choose the default location for the region");
			
			Button btnCreateAnnotation = new Button("Create region");
			btnCreateAnnotation.setOnAction(e -> createAndAddRegion());
			pane.add(btnCreateAnnotation, 0, row++, 2, 1);
			
			pane.setVgap(5);
			pane.setHgap(5);
			pane.setPadding(new Insets(10));
			
			// Set max values to aid resizing
			btnCreateAnnotation.setMaxWidth(Double.MAX_VALUE);
			comboClassification.setMaxWidth(Double.MAX_VALUE);
			comboLocation.setMaxWidth(Double.MAX_VALUE);
			comboUnits.setMaxWidth(Double.MAX_VALUE);
			
			
		}
		
		
		public Pane getPane() {
			return pane;
		}
		
		
		private void createAndAddRegion() {
			QuPathViewer viewer = viewerDefault == null ? qupath.getViewer() : viewerDefault;
			if (viewer == null) {
				logger.error("Create region", "Cannot create region - no viewer specified!");
				return;
			}
			ImageData<?> imageData = viewer.getImageData();
			if (imageData == null) {
				Dialogs.showNoImageError("Create region");
				return;
			}
			
			// Parse the user input
			double width = Double.parseDouble(tfRegionWidth.getText());
			double height = tfRegionHeight.getText().isEmpty() ? width : Double.parseDouble(tfRegionHeight.getText());
			RegionUnits requestedUnits = comboUnits.getSelectionModel().getSelectedItem();
			PathClass pathClass = comboClassification.getSelectionModel().getSelectedItem();
			if (pathClass == PathClass.NULL_CLASS)
				pathClass = null;
			RegionLocation location = comboLocation.getSelectionModel().getSelectedItem();
			
			// Calibrate the width & height according to pixel size... if necessary
			if (requestedUnits == RegionUnits.MICRONS) {
				PixelCalibration cal = imageData.getServer().getPixelCalibration();
				double pixelWidthMicrons = cal.getPixelWidthMicrons();
				double pixelHeightMicrons = cal.getPixelHeightMicrons();
				if (!Double.isFinite(pixelWidthMicrons + pixelHeightMicrons)) {
					Dialogs.showErrorMessage("Create region", "Pixel size not available! Please switch to creating the region in pixels instead.");
					return;
				}
				width /= pixelWidthMicrons;
				height /= pixelHeightMicrons;
			}
			
			// Check the pixels are in range
			if (width > viewer.getServerWidth() || height > viewer.getServerHeight()) {
				Dialogs.showErrorMessage("Create region", String.format("Requested size %.1f x %.1f must be smaller than image size %d x %d!",
						width, height, viewer.getServerWidth(), viewer.getServerHeight()));
				return;
			}
			
			// If adding 
			PathObject parentObject = null;
			ROI rectangle = null;
			
			// Determine starting location
			switch (location) {
			case IMAGE_CENTER:
				double x = (imageData.getServer().getWidth() - width) / 2.0;
				double y = (imageData.getServer().getHeight() - height) / 2.0;
				rectangle = ROIs.createRectangleROI(x, y, width, height, viewer.getImagePlane());
				break;
			case RANDOM:
				parentObject = imageData.getHierarchy().getSelectionModel().getSelectedObject();
				var roi = parentObject == null ? null : parentObject.getROI();
				if (roi != null) {
					if (!(parentObject.isAnnotation() || parentObject.isTMACore())) {
						logger.warn("Ignoring parent object because it's not an annotation or a TMA core");
						parentObject = null;
						roi = null;
					}
					if (!roi.isArea() || roi.isEmpty()) {
						logger.warn("Ignoring parent object because its ROI does not define an area");
						parentObject = null;
						roi = null;
					}
				}
				// 	If we have a selected object, add annotations within it
				try {
					if (roi != null) {
						rectangle = RoiTools.createRandomRectangle(roi, width, height);
					} else {
						var region = ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition());
						rectangle = RoiTools.createRandomRectangle(region, width, height);
					}
				} catch (IllegalArgumentException e) {
					Dialogs.showErrorMessage("Create region", e.getLocalizedMessage());
					return;
				}
				if (rectangle == null) {
					Dialogs.showErrorMessage("Create region", "Unable to squeeze a rectangle of the specified size into the current ROI, sorry");
					return;
				}
				break;
			case VIEW_CENTER:
				rectangle = ROIs.createRectangleROI(
						viewer.getCenterPixelX() - width / 2.0,
						viewer.getCenterPixelY() - height / 2.0,
						width,
						height,
						viewer.getImagePlane());
				break;
			default:
				break;
			}
			
			if (rectangle == null) {
				Dialogs.showErrorMessage("Create region", "Unable to create a rectangle region, sorry");
				return;
			}
			
			if (rectangle.getBoundsX() < 0 || rectangle.getBoundsY() < 0 || 
					rectangle.getBoundsX() + rectangle.getBoundsWidth() > imageData.getServer().getWidth() || 
					rectangle.getBoundsY() + rectangle.getBoundsHeight() > imageData.getServer().getHeight()) {
				Dialogs.showErrorMessage("Create region", "Cannot create requested region - it would extend beyond the image bounds");
				return;
			}
			
			// Create an annotation
			PathObject annotation = PathObjects.createAnnotationObject(
					rectangle,
					pathClass);
			if (parentObject != null)
				imageData.getHierarchy().addObjectBelowParent(parentObject, annotation, true);
			else
				imageData.getHierarchy().addObject(annotation);
		}
		
		
		
		private void addLabelled(String labelText, Node node, int col, int row, String help) {
			Label label = new Label(labelText);
			label.setLabelFor(node);
			if (help != null) {
				Tooltip tip = new Tooltip(help);
				Tooltip.install(label, tip);
				Tooltip.install(node, tip);
			}
			pane.add(label, col, row);
			pane.add(node, col+1, row);
		}
		
		public static RegionMaker create(final QuPathGUI qupath) {
			return new RegionMaker(qupath);
		}
		
		
	}
	
	

}