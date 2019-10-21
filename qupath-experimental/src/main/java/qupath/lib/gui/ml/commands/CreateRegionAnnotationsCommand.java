package qupath.lib.gui.ml.commands;

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
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * Command to help create annotations defining regions that will be further annotated for 
 * algorithm training.
 * 
 * @author Pete Bankhead
 *
 */
public class CreateRegionAnnotationsCommand implements PathCommand {
	
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
		
		private final static Logger logger = LoggerFactory.getLogger(RegionMaker.class);
		
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
			if (comboClassification.getItems().contains(PathClassFactory.getPathClass(StandardPathClasses.REGION)))
				comboClassification.getSelectionModel().select(PathClassFactory.getPathClass(StandardPathClasses.REGION));
			else
				comboClassification.getSelectionModel().select(PathClassFactory.getPathClassUnclassified());
			
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
				DisplayHelpers.showNoImageError("Create region");
				return;
			}
			
			// Parse the user input
			double width = Double.parseDouble(tfRegionWidth.getText());
			double height = tfRegionHeight.getText().isEmpty() ? width : Double.parseDouble(tfRegionHeight.getText());
			RegionUnits requestedUnits = comboUnits.getSelectionModel().getSelectedItem();
			PathClass pathClass = comboClassification.getSelectionModel().getSelectedItem();
			if (pathClass == PathClassFactory.getPathClassUnclassified())
				pathClass = null;
			RegionLocation location = comboLocation.getSelectionModel().getSelectedItem();
			
			// Calibrate the width & height according to pixel size... if necessary
			if (requestedUnits == RegionUnits.MICRONS) {
				PixelCalibration cal = imageData.getServer().getPixelCalibration();
				double pixelWidthMicrons = cal.getPixelWidthMicrons();
				double pixelHeightMicrons = cal.getPixelHeightMicrons();
				if (!Double.isFinite(pixelWidthMicrons + pixelHeightMicrons)) {
					DisplayHelpers.showErrorMessage("Create region", "Pixel size not available! Please switch to creating the region in pixels instead.");
					return;
				}
				width /= pixelWidthMicrons;
				height /= pixelHeightMicrons;
			}
			
			// Check the pixels are in range
			if (width > viewer.getServerWidth() || height > viewer.getServerHeight()) {
				DisplayHelpers.showErrorMessage("Create region", String.format("Requested size %.1f x %.1f must be smaller than image size %d x %d!",
						width, height, viewer.getServerWidth(), viewer.getServerHeight()));
				return;
			}
			
			// Determine starting location
			double x;
			double y;
			switch (location) {
			case IMAGE_CENTER:
				x = (imageData.getServer().getWidth() - width) / 2.0;
				y = (imageData.getServer().getHeight() - height) / 2.0;
				break;
			case RANDOM:
				x = Math.random() * viewer.getServerWidth() - width;
				y = Math.random() * viewer.getServerHeight() - height;
				break;
			case VIEW_CENTER:
				x = viewer.getCenterPixelX() - width / 2.0;
				y = viewer.getCenterPixelY() - height / 2.0;
				break;
			default:
				DisplayHelpers.showErrorMessage("Create region", "Unknowing location " + location);
				return;
			}
			
			// Create an annotation
			PathObject annotation = PathObjects.createAnnotationObject(
					ROIs.createRectangleROI(x, y, width, height, ImagePlane.getPlane(viewer.getZPosition(), viewer.getTPosition())),
					pathClass);
			imageData.getHierarchy().addPathObject(annotation);
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
