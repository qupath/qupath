package qupath.lib.gui.align;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.global.opencv_video;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.controlsfx.control.CheckListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Affine;
import javafx.scene.transform.TransformChangedEvent;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.OpenCVTools;


/**
 * A user interface for interacting with multiple image overlays.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageAlignmentPane {
	
	private static Logger logger = LoggerFactory.getLogger(ImageAlignmentPane.class);
	
	private QuPathGUI qupath;
	private QuPathViewer viewer;
	
	private ObservableList<ImageData<BufferedImage>> images = FXCollections.observableArrayList();
	private ObjectProperty<ImageData<BufferedImage>> selectedImageData = new SimpleObjectProperty<>();
	private DoubleProperty rotationIncrement = new SimpleDoubleProperty(1.0);
		
	private StringProperty affineStringProperty;

	private Map<ImageData<BufferedImage>, ImageServerOverlay> mapOverlays = new WeakHashMap<>();
	private EventHandler<TransformChangedEvent> transformEventHandler = new EventHandler<TransformChangedEvent>() {
		@Override
		public void handle(TransformChangedEvent event) {
			affineTransformUpdated();
		}
	};
	
	private RefineTransformMouseHandler mouseEventHandler = new RefineTransformMouseHandler();
	
	private ObjectBinding<ImageServerOverlay> selectedOverlay = Bindings.createObjectBinding(
			() -> {
				return mapOverlays.get(selectedImageData.get());
			},
			selectedImageData);
	
	private BooleanBinding noOverlay = selectedOverlay.isNull();


	/**
	 * Constructor.
	 * @param qupath QuPath instance
	 */
	public ImageAlignmentPane(final QuPathGUI qupath) {
		
		this.qupath = qupath;
		this.viewer = qupath.getViewer();
		
		this.viewer.getView().addEventFilter(MouseEvent.ANY, mouseEventHandler);
		
		// Create left-hand pane for list
		CheckListView<ImageData<BufferedImage>> listImages = new CheckListView<>(images);
		listImages.setPrefHeight(300);
		listImages.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		listImages.setCellFactory(c -> new ImageEntryCell());
		
		selectedImageData.bind(listImages.getSelectionModel().selectedItemProperty());
		
		Button btnChooseImages = new Button("Choose images from project");
		btnChooseImages.disableProperty().bind(qupath.projectProperty().isNull());
		btnChooseImages.setMaxWidth(Double.MAX_VALUE);
		btnChooseImages.setOnAction(e -> promptToAddImages());
		

		FloatProperty opacityProperty = viewer.getOverlayOptions().opacityProperty();
		Slider sliderOpacity = new Slider(0, 1, opacityProperty.get());
		sliderOpacity.valueProperty().bindBidirectional(opacityProperty);
		sliderOpacity.setMaxWidth(Double.MAX_VALUE);
		Label labelOpacity = new Label("Opacity");
		labelOpacity.setLabelFor(sliderOpacity);

		GridPane paneList = new GridPane();
		paneList.add(listImages, 0, 0, 2, 1);
		paneList.add(btnChooseImages, 0, 1, 2, 1);
		paneList.add(labelOpacity, 0, 2);
		paneList.add(sliderOpacity, 1, 2);
		paneList.setVgap(5);
		paneList.setMaxWidth(Double.MAX_VALUE);
		GridPane.setFillHeight(listImages, Boolean.TRUE);
		GridPane.setFillWidth(listImages, Boolean.TRUE);
		GridPane.setFillWidth(btnChooseImages, Boolean.TRUE);
		GridPane.setFillWidth(sliderOpacity, Boolean.TRUE);
		GridPane.setHgrow(listImages, Priority.ALWAYS);
		GridPane.setHgrow(btnChooseImages, Priority.ALWAYS);
		GridPane.setHgrow(sliderOpacity, Priority.ALWAYS);
		GridPane.setVgrow(listImages, Priority.ALWAYS);

		// Create center pane for alignment

		// Handle rotation
		TextField tfRotationIncrement = new TextField("1");
		tfRotationIncrement.setPrefColumnCount(6);
		tfRotationIncrement.textProperty().addListener((v, o, n) -> {
			if (!n.isEmpty()) {
				try {
					rotationIncrement.set(Double.parseDouble(n));
				} catch (Exception e) {}
			}
		});
		Label labelRotationIncrement = new Label("Rotation increment: ");
		labelRotationIncrement.setLabelFor(tfRotationIncrement);
		Button btnRotateLeft = new Button("Rotate Left");
		Button btnRotateRight = new Button("Rotate Right");
		btnRotateLeft.setOnAction(e -> requestRotation(rotationIncrement.get()));
		btnRotateRight.setOnAction(e -> requestRotation(-rotationIncrement.get()));	

		btnRotateLeft.disableProperty().bind(noOverlay);
		btnRotateRight.disableProperty().bind(noOverlay);
		
		GridPane paneAlignment = new GridPane();
		paneAlignment.setHgap(5);
		paneAlignment.setVgap(5);
		int row = 0;
		int col = 0;
		paneAlignment.add(labelRotationIncrement, col++, row);
		paneAlignment.add(tfRotationIncrement, col++, row);
		paneAlignment.add(btnRotateLeft, col++, row);
		paneAlignment.add(btnRotateRight, col++, row++);

		Label labelTranslate = new Label("Adjust translation by clicking & dragging on the image with the 'Shift' key down");
		paneAlignment.add(labelTranslate, 0, row++, 4, 1);

		TextArea textArea = new TextArea();
		affineStringProperty = textArea.textProperty();
		textArea.setPrefRowCount(5);
		paneAlignment.add(textArea, 0, row++, 4, 1);

		TitledPane titledAlignment = new TitledPane("Interactive alignment", paneAlignment);

		// Auto-align
		TextField tfRequestedPixelSizeMicrons = new TextField("20");
		tfRequestedPixelSizeMicrons.setPrefColumnCount(6);
		Label labelRequestedPixelSizeMicrons = new Label("Pixel size");
		CheckBox cbUseAnnotations = new CheckBox("Use annotations");
		Button btnAutoAlign = new Button("Estimate transform");
		btnAutoAlign.setMaxWidth(Double.MAX_VALUE);
		btnAutoAlign.disableProperty().bind(noOverlay);
		
		btnAutoAlign.setOnAction(e -> {
			double requestedPixelSizeMicrons = Double.parseDouble(tfRequestedPixelSizeMicrons.getText());
			try {
				autoAlign(requestedPixelSizeMicrons);
			} catch (IOException e2) {
				DisplayHelpers.showErrorMessage("Alignment error", "Error requesting image region: " + e2.getLocalizedMessage());
				logger.error("Error in auto alignment", e2);
			}
		});
		
		// Need to update transform text with image
		selectedImageData.addListener((v, o, n) -> affineTransformUpdated());

		GridPane paneAutoAlign = new GridPane();
		paneAutoAlign.add(labelRequestedPixelSizeMicrons, 0, 0);
		paneAutoAlign.add(tfRequestedPixelSizeMicrons, 1, 0);
		paneAutoAlign.add(cbUseAnnotations, 2, 0);
		paneAutoAlign.add(btnAutoAlign, 3, 0);
		GridPane.setHgrow(btnChooseImages, Priority.ALWAYS);
//		paneAutoAlign.add(btnAutoAlign, 0, 1, 3, 1);
		paneAutoAlign.setVgap(5);
		paneAutoAlign.setHgap(5);

		TitledPane titledAutoAlign = new TitledPane("Auto-align", paneAutoAlign);

		Accordion paneMain = new Accordion(
				titledAlignment,
				titledAutoAlign);
//		VBox paneMain = new VBox(titledAlignment, titledAutoAlign);

		// Show only the current overlay on the viewer
		selectedOverlay.addListener((v, o, n) -> {
			if (o != null)
				viewer.getCustomOverlayLayers().remove(o);
			if (n != null)
				viewer.getCustomOverlayLayers().add(n);
		});

		// Bring panes together
		TitledPane titledList = new TitledPane("Image & overlays", paneList);
		titledList.setCollapsible(false);
		SplitPane pane = new SplitPane(titledList, paneMain);
		pane.setDividerPositions(0.35);


		// Add current image to list, if we have one
		ImageData<BufferedImage> imageDataCurrent = viewer.getImageData();
		if (imageDataCurrent != null) {
			listImages.getItems().add(imageDataCurrent);
		}


		Stage stage = new Stage();
		if (qupath != null)
			stage.initOwner(qupath.getStage());
		stage.setTitle("Image overlay alignment");

		Scene scene = new Scene(pane);
		stage.setScene(scene);

		stage.show();
		
		
		stage.setOnHiding(e -> {
			// Remove event filter & any overlays we created
			this.viewer.getView().removeEventFilter(MouseEvent.ANY, mouseEventHandler);
			this.viewer.getCustomOverlayLayers().removeAll(mapOverlays.values());
		});
		
	}
	
		
	void promptToAddImages() {
		// Get all the other project entries - except for the base image (which is fixed)
		Project<BufferedImage> project = qupath.getProject();
		List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>(project.getImageList());
		ImageData<BufferedImage> imageDataCurrent = viewer.getImageData();
		ProjectImageEntry<BufferedImage> currentEntry = project.getEntry(imageDataCurrent);
		if (currentEntry != null)
			entries.remove(currentEntry);
		
		// Find the entries currently selected
		Set<ProjectImageEntry<BufferedImage>> alreadySelected = 
				images.stream().map(i -> project.getEntry(i)).collect(Collectors.toSet());
		if (currentEntry != null)
			alreadySelected.remove(currentEntry);
		
		// Create a list to display, with the appropriate selections
		ListView<ProjectImageEntry<BufferedImage>>  list = new ListView<>();
		list.getItems().setAll(entries);
		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		for (int i = 0; i < entries.size(); i++) {
			if (alreadySelected.contains(entries.get(i)))
				list.getSelectionModel().select(i);
		}
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setHeaderText("Select images to include");
		dialog.getDialogPane().setContent(list);
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (result.orElse(ButtonType.CANCEL) == ButtonType.CANCEL)
			return;
		
		// We now need to add some & remove some (potentially)
		Set<ProjectImageEntry<BufferedImage>> toSelect = new LinkedHashSet<>(list.getSelectionModel().getSelectedItems());
		Set<ProjectImageEntry<BufferedImage>> toRemove = new HashSet<>(alreadySelected);
		toRemove.removeAll(toSelect);
		toSelect.removeAll(alreadySelected);
		
		// Rather convoluted... but remove anything that needs to go, from the list, map & overlay
		if (!toRemove.isEmpty()) {
			List<ImageData<BufferedImage>> imagesToRemove = new ArrayList<>();
			for (ImageData<BufferedImage> temp : images) {
				for (ProjectImageEntry<BufferedImage> entry : toRemove) {
					if (entry == currentEntry)
						imagesToRemove.add(temp);
				}
			}
			images.removeAll(imagesToRemove);
			for (ImageData<BufferedImage> temp : imagesToRemove) {
				ImageServerOverlay overlay = mapOverlays.remove(temp);
				if (overlay != null) {
					overlay.getAffine().removeEventHandler(TransformChangedEvent.ANY, transformEventHandler);
					viewer.getCustomOverlayLayers().remove(overlay);					
				}
			}
		}
		
		// Add any images that need to be added
		List<ImageData<BufferedImage>> imagesToAdd = new ArrayList<>();
		for (ProjectImageEntry<BufferedImage> temp : toSelect) {
			ImageData<BufferedImage> imageData = null;
			// Read annotations from any data file
			try {
				if (temp.hasImageData()) {
					imageData = temp.readImageData();
					Collection<PathObject> pathObjects = imageData.getHierarchy().getObjects(null, null);
					Set<PathObject> pathObjectsToRemove = pathObjects.stream().filter(p -> !p.isAnnotation()).collect(Collectors.toSet());
					imageData.getHierarchy().removeObjects(pathObjectsToRemove, true);
				} else {
					imageData = temp.readImageData();
				}
			} catch (IOException e) {
				logger.error("Unable to read ImageData for " + temp.getImageName(), e);
				continue;
			}
			ImageServerOverlay overlay = new ImageServerOverlay(viewer, imageData.getServer());
			overlay.getAffine().addEventHandler(TransformChangedEvent.ANY, transformEventHandler);
			mapOverlays.put(imageData, overlay);
//			viewer.getCustomOverlayLayers().add(overlay);
			imagesToAdd.add(imageData);
		}
		images.addAll(0, imagesToAdd);
		
	}
	
	
	void addImageData(final ImageData<BufferedImage> imageData) {
		ImageServerOverlay overlay = new ImageServerOverlay(viewer, imageData.getServer());
		mapOverlays.put(imageData, overlay);
		viewer.getCustomOverlayLayers().add(overlay);
		images.add(0, imageData);
	}
	
	
	
	private ImageServerOverlay getSelectedOverlay() {
		return mapOverlays.get(selectedImageData.get());
	}
	
	private void affineTransformUpdated() {
		ImageServerOverlay overlay = getSelectedOverlay();
		if (overlay == null) {
			affineStringProperty.set("No overlay selected");
			return;
		}
		Affine affine = overlay.getAffine();
		affineStringProperty.set(
				String.format("Transform: [\n" +
				"  %.3f, %.3f, %.3f,\n" + 
				"  %.3f, %.3f, %.3f\n" + 
				"]",
				affine.getMxx(), affine.getMxy(), affine.getTx(),
				affine.getMyx(), affine.getMyy(), affine.getTy())
				);
	}
	
	
	
	/**
	 * Ensure an image is 8-bit grayscale, creating a new image if necessary.
	 * 
	 * @param img
	 * @return
	 */
	static BufferedImage ensureGrayScale(BufferedImage img) {
		if (img.getType() == BufferedImage.TYPE_BYTE_GRAY)
			return img;
		BufferedImage imgGray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g2d = imgGray.createGraphics();
		g2d.drawImage(img, 0, 0, null);
		g2d.dispose();
		return imgGray;
	}

	/**
	 * Auto-align the selected image overlay with the base image in the viewer.
	 * 
	 * @param requestedPixelSizeMicrons
	 * @throws IOException 
	 */
	void autoAlign(double requestedPixelSizeMicrons) throws IOException {
		ImageData<BufferedImage> imageDataBase = viewer.getImageData();
		ImageData<BufferedImage> imageDataSelected = selectedImageData.get();
		if (imageDataBase == null) {
			DisplayHelpers.showNoImageError("Auto-alignment");
			return;
		}
		if (imageDataSelected == null) {
			DisplayHelpers.showErrorMessage("Auto-alignment", "Please ensure an image overlay is selected!");
			return;
		}
		if (imageDataBase == imageDataSelected) {
			DisplayHelpers.showErrorMessage("Auto-alignment", "Please select an image overlay, not the 'base' image from the viewer!");
			return;
		}
		ImageServerOverlay overlay = mapOverlays.get(imageDataSelected);
		autoAlign(imageDataBase.getServer(), imageDataSelected.getServer(), overlay.getAffine(), requestedPixelSizeMicrons);
	}

	static void autoAlign(ImageServer<BufferedImage> serverBase, ImageServer<BufferedImage> serverOverlay, Affine affine, double requestedPixelSizeMicrons) throws IOException {
		PixelCalibration calBase = serverBase.getPixelCalibration();
		double pixelSize = calBase.getAveragedPixelSizeMicrons();
		double downsample = 1;
		if (!Double.isFinite(pixelSize)) {
			while (serverBase.getWidth() / downsample > 2000)
				downsample++;
			logger.warn("Pixel size is unavailable! Default downsample value of {} will be used", downsample);
		} else {
			downsample = requestedPixelSizeMicrons / calBase.getAveragedPixelSizeMicrons();			
		}

		BufferedImage imgBase = serverBase.readBufferedImage(RegionRequest.createInstance(serverBase.getPath(), downsample, 0, 0, serverBase.getWidth(), serverBase.getHeight()));
		BufferedImage imgOverlay = serverOverlay.readBufferedImage(RegionRequest.createInstance(serverOverlay.getPath(), downsample, 0, 0, serverOverlay.getWidth(), serverOverlay.getHeight()));
		
		imgBase = ensureGrayScale(imgBase);
		imgOverlay = ensureGrayScale(imgOverlay);
		
		Mat matBase = OpenCVTools.imageToMat(imgBase);
		Mat matOverlay = OpenCVTools.imageToMat(imgOverlay);
		Mat matTransform = Mat.eye(2, 3, opencv_core.CV_32F).asMat();
		// Initialize using existing transform
//		affine.setToTransform(mxx, mxy, tx, myx, myy, ty);
		try (FloatIndexer indexer = matTransform.createIndexer()) {
			indexer.put(0, 0, (float)affine.getMxx());
			indexer.put(0, 1, (float)affine.getMxy());
			indexer.put(0, 2, (float)(affine.getTx() / downsample));
			indexer.put(1, 0, (float)affine.getMyx());
			indexer.put(1, 1, (float)affine.getMyy());
			indexer.put(1, 2, (float)(affine.getTy() / downsample));
//			System.err.println(indexer);
		} catch (Exception e) {
			logger.error("Error closing indexer", e);
		}
//		// Might want to mask out completely black pixels (could indicate missing data)?
//		def matMask = new opencv_core.Mat(matOverlay.size(), opencv_core.CV_8UC1, Scalar.ZERO);
		TermCriteria termCrit = new TermCriteria(TermCriteria.COUNT+TermCriteria.EPS, 100, 0.001);
		try {
			double result = opencv_video.findTransformECC(matBase, matOverlay, matTransform, opencv_video.MOTION_EUCLIDEAN, termCrit, null);
			logger.info("Transformation result: {}", result);
		} catch (Exception e) {
			DisplayHelpers.showErrorNotification("Estimate transform", "Unable to estimated transform - result did not converge");
			logger.error("Unable to estimate transform", e);
			return;
		}
		
		// To use the following function, images need to be the same size
//		def matTransform = opencv_video.estimateRigidTransform(matBase, matOverlay, false);
			
		Indexer indexer = matTransform.createIndexer();
		affine.setToTransform(
			indexer.getDouble(0, 0),
			indexer.getDouble(0, 1),
			indexer.getDouble(0, 2) * downsample,
			indexer.getDouble(1, 0),
			indexer.getDouble(1, 1),
			indexer.getDouble(1, 2) * downsample
			);
		indexer.release();
		
//		matMask.release();
		matBase.release();
		matOverlay.release();
		matTransform.release();
	}
	
	

	void requestShift(double dx, double dy) {
		ImageServerOverlay overlay = mapOverlays.get(selectedImageData.get());
		if (overlay == null) {
			DisplayHelpers.showErrorNotification("Shift overlay", "No overlay selected!");
			return;
		}
		double downsample = Math.max(1.0, viewer.getDownsampleFactor());
		overlay.getAffine().appendTranslation(dx * downsample, dy * downsample);
	}

	void requestRotation(double theta) {
		ImageServerOverlay overlay = mapOverlays.get(selectedImageData.get());
		if (overlay == null) {
			DisplayHelpers.showErrorNotification("Rotate overlay", "No overlay selected!");
			return;
		}
		overlay.getAffine().appendRotation(theta, viewer.getCenterPixelX(), viewer.getCenterPixelY());
	}
	
	
	static void requestShift(QuPathViewer viewer, Affine affine, double dx, double dy) {
		double downsample = Math.max(1.0, viewer.getDownsampleFactor());
		affine.appendTranslation(dx * downsample, dy * downsample);
	}

	static void requestRotation(QuPathViewer viewer, Affine affine, double theta) {
		affine.appendRotation(theta, viewer.getCenterPixelX(), viewer.getCenterPixelY());
	}
	
	
	
	/**
	 * An event handler to enable interactively adjusting overlay transforms.
	 */
	class RefineTransformMouseHandler implements EventHandler<MouseEvent> {
		
		private Point2D pDragging;
		
		@Override
		public void handle(MouseEvent event) {
			if (!event.isPrimaryButtonDown() || event.isConsumed())
				return;
			
			ImageServerOverlay overlay = getSelectedOverlay();
			if (overlay == null)
				return;
				
			if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
				pDragging = viewer.componentPointToImagePoint(event.getX(), event.getY(), pDragging, true);
				return;
			} else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
				Point2D p = viewer.componentPointToImagePoint(event.getX(), event.getY(), null, true);
				if (event.isShiftDown() && pDragging != null) {
					double dx = p.getX() - pDragging.getX();
					double dy = p.getY() - pDragging.getY();
					overlay.getAffine().appendTranslation(-dx, -dy);
					event.consume();
				}
				pDragging = p;
			}
		}
	}
	
	
	
	/**
	 * ListCell for displaying image overlays.
	 */
	class ImageEntryCell extends ListCell<ImageData<BufferedImage>> {

		final SimpleDateFormat dateFormat = new SimpleDateFormat();
		
		private StackPane label = new StackPane();
		private Canvas viewCanvas = new Canvas();

		public ImageEntryCell() {
			double viewWidth = 80;
			double viewHeight = 60;
			viewCanvas.setWidth(viewWidth);
			viewCanvas.setHeight(viewHeight);
			viewCanvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			label.getChildren().add(viewCanvas);
			label.setPrefSize(viewWidth, viewHeight);
		}

		@Override
		protected void updateItem(ImageData<BufferedImage> item, boolean empty) {
			super.updateItem(item, empty);

			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			
			if (item == viewer.getImageData())
				setStyle("-fx-font-weight: bold; -fx-font-family: arial");
			else 
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
			
			// Get the name from the project, if possible
			Project<BufferedImage> project = qupath.getProject();
			String name = ServerTools.getDisplayableImageName(item.getServer());
			if (project != null) {
				ProjectImageEntry<BufferedImage> entry = project.getEntry(item);
				if (entry != null)
					name = entry.getImageName();
			}
			setText(name);
			
			BufferedImage img = viewer.getImageRegionStore().getThumbnail(item.getServer(), 0, 0, true);
			Image image = SwingFXUtils.toFXImage(img, null);
			PaintingToolsFX.paintImage(viewCanvas, image);
			if (getGraphic() == null)
				setGraphic(label);
				
		}
		
		
	}
	
}
