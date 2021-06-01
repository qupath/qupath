/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

/**
 * A panel used for displaying basic info about an image, e.g. its path, width, height, pixel size etc.
 * <p>
 * It also includes displaying color deconvolution vectors for RGB brightfield images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageDetailsPane implements ChangeListener<ImageData<BufferedImage>>, PropertyChangeListener {
	
	final private static Logger logger = LoggerFactory.getLogger(ImageDetailsPane.class);
	
	private QuPathGUI qupath;
	
	private StackPane pane = new StackPane();
		
	private TableView<ImageDetailRow> table = new TableView<>();
	private ListView<String> listAssociatedImages = new ListView<>();

	private enum ImageDetailRow {
		NAME, URI, PIXEL_TYPE, MAGNIFICATION, WIDTH, HEIGHT, DIMENSIONS,
		PIXEL_WIDTH, PIXEL_HEIGHT, Z_SPACING, UNCOMPRESSED_SIZE, SERVER_TYPE, PYRAMID,
		METADATA_CHANGED, IMAGE_TYPE,
		STAIN_1, STAIN_2, STAIN_3, BACKGROUND;
	};
	
	private static List<ImageDetailRow> brightfieldRows = Arrays.asList(ImageDetailRow.values());
	private static List<ImageDetailRow> otherRows = new ArrayList<>(brightfieldRows);
	static {
		otherRows.remove(ImageDetailRow.STAIN_1);
		otherRows.remove(ImageDetailRow.STAIN_2);
		otherRows.remove(ImageDetailRow.STAIN_3);
		otherRows.remove(ImageDetailRow.BACKGROUND);
	}

	
	private ImageData<BufferedImage> imageData;

	/**
	 * Constructor.
	 * @param qupath QuPath instance
	 */
	public ImageDetailsPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.imageDataProperty().addListener(this);
		
		// Create the table
		table.setMinHeight(200);
		table.setPrefHeight(250);
		table.setMaxHeight(Double.MAX_VALUE);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		TableColumn<ImageDetailRow, String> columnName = new TableColumn<>("Name");
		columnName.setCellValueFactory(v -> new ReadOnlyStringWrapper(getName(v.getValue())));
		columnName.setEditable(false);
		columnName.setPrefWidth(150);
		TableColumn<ImageDetailRow, Object> columnValue = new TableColumn<>("Value");
		columnValue.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(getValue(v.getValue())));
		columnValue.setEditable(false);
		columnValue.setPrefWidth(200);
		columnValue.setCellFactory(new Callback<TableColumn<ImageDetailRow, Object>, TableCell<ImageDetailRow, Object>>() {

			@Override
			public TableCell<ImageDetailRow, Object> call(TableColumn<ImageDetailRow, Object> param) {
				TableCell<ImageDetailRow, Object> cell = new TableCell<ImageDetailRow, Object>() {
					@Override
					protected void updateItem(Object item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setText(null);
							setGraphic(null);
							return;
						}
						//			             ComboBoxTableCell<TableEntry, Object>
						String style = null;
						String text = item == null ? "" : item.toString();
						String tooltipText = text;
						if (item instanceof double[]) {
							text = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])item, 2);
						} else if (item instanceof StainVector) {
							StainVector stain = (StainVector)item;
							Integer color = stain.getColor();
							style = String.format("-fx-text-fill: rgb(%d, %d, %d);", ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color));
							tooltipText = "Double-click to set stain color (either type values or use a small rectangle ROI in the image)";
						} else {
							var type = getTableRow().getItem();
							if (type != null) {
								if (type.equals(ImageDetailRow.PIXEL_WIDTH) || type.equals(ImageDetailRow.PIXEL_HEIGHT) || type.equals(ImageDetailRow.Z_SPACING)) {
									if ("Unknown".equals(item))
										style = "-fx-text-fill: red;";
									tooltipText = "Double-click to set pixel calibration (can use a selected line or area ROI in the image)";
								} else if (type.equals(ImageDetailRow.METADATA_CHANGED))
									tooltipText = "Double-click to reset original metadata";
								else if (type.equals(ImageDetailRow.UNCOMPRESSED_SIZE))
									tooltipText = "Approximate memory required to store all pixels in the image uncompressed";
							}
						}
						setStyle(style);
						setText(text);
						setTooltip(new Tooltip(tooltipText));
					}
				};
				
				cell.setOnMouseClicked(event -> {
					if (event.getClickCount() < 2)
						return;
					TableCell<ImageDetailRow, Object> c = (TableCell<ImageDetailRow, Object>)event.getSource();
					Object value = c.getItem();
					if (value instanceof StainVector || value instanceof double[])
						editStainVector(value);
					else if (value instanceof ImageType) {
						promptToSetImageType(imageData);
					} else {
						// TODO: Support z-spacing
						var type = c.getTableRow().getItem();
						boolean metadataChanged = false;
						if (type == ImageDetailRow.PIXEL_WIDTH ||
								type == ImageDetailRow.PIXEL_HEIGHT ||
								type == ImageDetailRow.Z_SPACING) {
							metadataChanged = promptToSetPixelSize(imageData, type == ImageDetailRow.Z_SPACING);
						} else if (type == ImageDetailRow.MAGNIFICATION) {
							metadataChanged = promptToSetMagnification(imageData);
						} else if (type == ImageDetailRow.METADATA_CHANGED) {
							if (!hasOriginalMetadata(imageData.getServer())) {
								metadataChanged = promptToResetServerMetadata(imageData);
							}
						}
						if (metadataChanged) {
							c.getTableView().refresh();
							imageData.getHierarchy().fireHierarchyChangedEvent(this);
						}
					}
				});
				
				return cell;
			}
		});
		table.getColumns().add(columnName);
		table.getColumns().add(columnValue);
		
		setImageData(qupath.getImageData());

//		listImages.setOnMouseClicked(event -> {
//			if (event.getClickCount() < 2 || listImages.getSelectionModel().getSelectedItem() == null)
//				return;
//			}
//		);
		
		
		
		listAssociatedImages.setOnMouseClicked(event -> {
				if (event.getClickCount() < 2 || listAssociatedImages.getSelectionModel().getSelectedItem() == null)
					return;
				String name = listAssociatedImages.getSelectionModel().getSelectedItem();
				
				Stage dialog = new Stage();
				dialog.setTitle(name);
				dialog.initModality(Modality.NONE);
				dialog.initOwner(qupath.getStage());

				// Create menubar
				MenuBar menubar = new MenuBar();
				Menu menuFile = new Menu("File");
				MenuItem miClose = new MenuItem("Close");
				miClose.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
				miClose.setOnAction(e -> dialog.close());
				MenuItem miSave = new MenuItem("Save as PNG");
				miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
				miSave.setOnAction(e -> {
					BufferedImage img = imageData.getServer().getAssociatedImage(name);
					File fileOutput = Dialogs.getChooser(dialog).promptToSaveFile("Save image", null, name, "PNG", ".png");
					if (fileOutput != null) {
						try {
							ImageIO.write(img, "PNG", fileOutput);
						} catch (Exception e1) {
							Dialogs.showErrorMessage("Save image", "Error saving " + fileOutput.getName() + "\n" + e1.getLocalizedMessage());
						}
					}
				});
				menuFile.getItems().addAll(miSave, miClose);
				
				Menu menuEdit = new Menu("Edit");
				MenuItem miCopy = new MenuItem("Copy");
				miCopy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
				miCopy.setOnAction(e -> {
					Image img = SwingFXUtils.toFXImage(imageData.getServer().getAssociatedImage(name), null);
					ClipboardContent content = new ClipboardContent();
					content.putImage(img);
					Clipboard.getSystemClipboard().setContent(content);
				});
				
				
				menuEdit.getItems().add(miCopy);
				
				menubar.getMenus().addAll(menuFile, menuEdit);
				
				// Create image view
				var imgBuffered = imageData.getServer().getAssociatedImage(name);
				if (!BufferedImageTools.is8bitColorType(imgBuffered.getType()) && imgBuffered.getType() != BufferedImage.TYPE_BYTE_GRAY) {
					// By wrapping the thumbnail, we avoid slow z-stack/time series requests & determine brightness & contrast just from one plane
					var wrappedServer = new WrappedBufferedImageServer("Dummy", imgBuffered);
					var imageDisplay = new ImageDisplay(new ImageData<>(wrappedServer));
					for (ChannelDisplayInfo info : imageDisplay.selectedChannels()) {
						imageDisplay.autoSetDisplayRange(info);
					}
					imgBuffered = imageDisplay.applyTransforms(imgBuffered, null);
				}
				Image img = SwingFXUtils.toFXImage(imgBuffered, null);
				ImageView imageView = new ImageView(img);
				BorderPane pane = new BorderPane();
				imageView.fitWidthProperty().bind(pane.widthProperty());
				imageView.fitHeightProperty().bind(pane.heightProperty());
				imageView.setPreserveRatio(true);
				pane.setCenter(imageView);
				pane.setTop(menubar);
				Scene scene = new Scene(pane);
				pane.prefWidthProperty().bind(scene.widthProperty());
				pane.prefHeightProperty().bind(scene.heightProperty());
				menubar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
//				menubar.setUseSystemMenuBar(true);
				pane.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
				
				dialog.setScene(scene);
				dialog.show();
			}
		);
		
		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> table.refresh());

		MasterDetailPane mdPane = new MasterDetailPane(Side.BOTTOM);
		mdPane.setMasterNode(new StackPane(table));
		mdPane.setDetailNode(new StackPane(listAssociatedImages));
		mdPane.showDetailNodeProperty().bind(
				Bindings.createBooleanBinding(() -> !listAssociatedImages.getItems().isEmpty(),
						listAssociatedImages.getItems()));
		pane.getChildren().add(mdPane);
//		Accordion accordion = new Accordion(panelTable, panelAssociatedImages);
//		accordion.setExpandedPane(panelTable);
//		pane.getChildren().add(accordion);
	}
	
	
	static boolean hasOriginalMetadata(ImageServer<BufferedImage> server) {
		var metadata = server.getMetadata();
		var originalMetadata = server.getOriginalMetadata();
		return Objects.equals(metadata, originalMetadata);
	}
	
	
	static boolean promptToResetServerMetadata(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		if (hasOriginalMetadata(server)) {
			logger.info("ImageServer metadata is unchanged!");
			return false;
		}
		var originalMetadata = server.getOriginalMetadata();
		
		if (Dialogs.showConfirmDialog("Reset metadata", "Reset to original metadata?")) {
			imageData.updateServerMetadata(originalMetadata);
			return true;
		}
		return false;
	}
	
	
	static boolean promptToSetMagnification(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		Double mag = server.getMetadata().getMagnification();
		if (mag != null && !Double.isFinite(mag))
			mag = null;
		Double mag2 = Dialogs.showInputDialog("Set magnification", "Set magnification for full resolution image", mag);
		if (mag2 == null || Objects.equals(mag, mag2))
			return false;
		if (!Double.isFinite(mag2) && mag == null)
			return false;
		var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
			.magnification(mag2)
			.build();
		imageData.updateServerMetadata(metadata2);
		return true;
	}
	
	static boolean promptToSetPixelSize(ImageData<BufferedImage> imageData, boolean requestZSpacing) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		var roi = selected == null ? null : selected.getROI();
		
		PixelCalibration cal = server.getPixelCalibration();
		double pixelWidthMicrons = cal.getPixelWidthMicrons();
		double pixelHeightMicrons = cal.getPixelHeightMicrons();
		double zSpacingMicrons = cal.getZSpacingMicrons();
		
		// Use line or area ROI if possible
		if (!requestZSpacing && roi != null && !roi.isEmpty() && (roi.isArea() || roi.isLine())) {
			boolean setPixelHeight = true;
			boolean setPixelWidth = true;	
			String message;
			String units = GeneralTools.micrometerSymbol();
			
			double pixelWidth = cal.getPixelWidthMicrons();
			double pixelHeight = cal.getPixelHeightMicrons();
			if (!Double.isFinite(pixelWidth))
				pixelWidth = 1;
			if (!Double.isFinite(pixelHeight))
				pixelHeight = 1;
			
			Double defaultValue = null;
			if (roi.isLine()) {
				setPixelHeight = roi.getBoundsHeight() != 0;
				setPixelWidth = roi.getBoundsWidth() != 0;
				message = "Enter selected line length";
				defaultValue = roi.getScaledLength(pixelWidth, pixelHeight);
			} else {
				message = "Enter selected ROI area";
				units = units + "^2";
				defaultValue = roi.getScaledArea(pixelWidth, pixelHeight);
			}
//			if (setPixelHeight && setPixelWidth) {
//				defaultValue = server.getAveragedPixelSizeMicrons();
//			} else if (setPixelHeight) {
//				defaultValue = server.getPixelHeightMicrons();					
//			} else if (setPixelWidth) {
//				defaultValue = server.getPixelWidthMicrons();					
//			}
			if (Double.isNaN(defaultValue))
				defaultValue = 1.0;
			var params = new ParameterList()
					.addDoubleParameter("inputValue", message, defaultValue, units, "Enter calibrated value in " + units + " for the selected ROI to calculate the pixel size")
					.addBooleanParameter("squarePixels", "Assume square pixels", true, "Set the pixel width to match the pixel height");
			params.setHiddenParameters(setPixelHeight && setPixelWidth, "squarePixels");
			if (!Dialogs.showParameterDialog("Set pixel size", params))
				return false;
			Double result = params.getDoubleParameterValue("inputValue");
			setPixelHeight = setPixelHeight || params.getBooleanParameterValue("squarePixels");
			setPixelWidth = setPixelWidth || params.getBooleanParameterValue("squarePixels");
			
//			Double result = Dialogs.showInputDialog("Set pixel size", message, defaultValue);
//			if (result == null)
//				return false;
			
			double sizeMicrons;
			if (roi.isLine())
				sizeMicrons = result.doubleValue() / roi.getLength();
			else
				sizeMicrons = Math.sqrt(result.doubleValue() / roi.getArea());
			
			if (setPixelHeight)
				pixelHeightMicrons = sizeMicrons;
			if (setPixelWidth)
				pixelWidthMicrons = sizeMicrons;
		} else {
			// Prompt for all required values
			ParameterList params = new ParameterList()
					.addDoubleParameter("pixelWidth", "Pixel width", pixelWidthMicrons, GeneralTools.micrometerSymbol(), "Enter the pixel width")
					.addDoubleParameter("pixelHeight", "Pixel height", pixelHeightMicrons, GeneralTools.micrometerSymbol(), "Entry the pixel height")
					.addDoubleParameter("zSpacing", "Z-spacing", zSpacingMicrons, GeneralTools.micrometerSymbol(), "Enter the spacing between slices of a z-stack");
			params.setHiddenParameters(server.nZSlices() == 1, "zSpacing");
			if (!Dialogs.showParameterDialog("Set pixel size", params))
				return false;
			if (server.nZSlices() != 1) {
				zSpacingMicrons = params.getDoubleParameterValue("zSpacing");
			}
			pixelWidthMicrons = params.getDoubleParameterValue("pixelWidth");
			pixelHeightMicrons = params.getDoubleParameterValue("pixelHeight");
		}
		if ((pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) || (server.nZSlices() > 1 && zSpacingMicrons <= 0)) {
			if (!Dialogs.showConfirmDialog("Set pixel size", "You entered values <= 0, do you really want to remove this pixel calibration information?")) {
				return false;
			}
			zSpacingMicrons = server.nZSlices() > 1 && zSpacingMicrons > 0 ? zSpacingMicrons : Double.NaN;
			if (pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) {
				pixelWidthMicrons = Double.NaN;
				pixelHeightMicrons = Double.NaN;
			}
		}
		if (QP.setPixelSizeMicrons(imageData, pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons)) {
			// Log for scripts
			WorkflowStep step;
			if (server.nZSlices() == 1) {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f)", pixelWidthMicrons, pixelHeightMicrons);
				step = new DefaultScriptableWorkflowStep("Set pixel size " + GeneralTools.micrometerSymbol(), map, script);
			} else {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons,
						"zSpacingMicrons", zSpacingMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f, %f)", pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons);
				step = new DefaultScriptableWorkflowStep("Set pixel size " + GeneralTools.micrometerSymbol(), map, script);
			}
			imageData.getHistoryWorkflow().addStep(step);
			return true;
		} else
			return false;
	}
	
	
	/**
	 * Prompt the user to set the {@link ImageType} for the image.
	 * @param imageData
	 * @return
	 */
	public static boolean promptToSetImageType(ImageData<BufferedImage> imageData) {
		List<ImageType> values = Arrays.asList(ImageType.values());
		if (!imageData.getServer().isRGB()) {
			values =new ArrayList<>(values);
			values.remove(ImageType.BRIGHTFIELD_H_DAB);
			values.remove(ImageType.BRIGHTFIELD_H_E);
			values.remove(ImageType.BRIGHTFIELD_OTHER);
		}
		
		var dialog = new ChoiceDialog<>(imageData.getImageType(), values);
		dialog.setTitle("Image type");
		if (QuPathGUI.getInstance() != null)
			dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.getDialogPane().setHeaderText(null);
		dialog.getDialogPane().setContentText("Set image type");
		dialog.getDialogPane().setPrefWidth(400);
		
		var labelExplain = new Label("The image type influences some commands (e.g. cell detection) and should be set for every image. "
				+ "\n\nSelect an option below or in the preferences to customize how QuPath handles setting the image type when opening an image."
				+ "\n\n'Auto-estimate' is convenient to reduce annoying prompts, but the estimates are sometimes wrong. "
				+ "When this happens you can correct them by double-clicking "
				+ "the type under the 'Image' tab.");
		labelExplain.setWrapText(true);
		labelExplain.setPrefWidth(400);
		labelExplain.setMinHeight(Label.USE_PREF_SIZE);
		
		var comboSetType = new ComboBox<ImageTypeSetting>();
		comboSetType.getItems().setAll(ImageTypeSetting.values());
		comboSetType.getSelectionModel().select(PathPrefs.imageTypeSettingProperty().get());
		comboSetType.setMaxWidth(Double.MAX_VALUE);
		labelExplain.setPadding(new Insets(0, 0, 10, 0));
		var expandablePane = new BorderPane(labelExplain);
		expandablePane.setBottom(comboSetType);
		
		dialog.getDialogPane().setExpandableContent(expandablePane);
		
		var result = dialog.showAndWait();
		ImageType type = result.orElse(null);
		if (type == null)
			return false;
		
		if (comboSetType.getSelectionModel().getSelectedItem() != null)
			PathPrefs.imageTypeSettingProperty().set(comboSetType.getSelectionModel().getSelectedItem());

		if (type != imageData.getImageType()) {
			imageData.setImageType(type);
			return true;
		}
		return false;
	}
	
	
	
	void editStainVector(Object value) {
		if (!(value instanceof StainVector || value instanceof double[]))
			return;
		//					JOptionPane.showMessageDialog(null, "Modifying stain vectors not yet implemented...");

		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData == null)
			return;
		ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
		int num = -1; // Default to background values
		String name = null;
		String message = null;
		if (value instanceof StainVector) {

			StainVector stainVector = (StainVector)value;

			if (stainVector.isResidual() && imageData.getImageType() != ImageType.BRIGHTFIELD_OTHER) {
				logger.warn("Cannot set residual stain vector - this is computed from the known vectors");
				return;
			}
			num = stains.getStainNumber(stainVector);
			if (num <= 0) {
				logger.error("Could not identify stain vector " + stainVector + " inside " + stains);
				return;
			}
			name = stainVector.getName();
			message = "Set stain vector from ROI?";
		} else
			message = "Set color deconvolution background values from ROI?";

		ROI pathROI = imageData.getHierarchy().getSelectionModel().getSelectedROI();
		boolean wasChanged = false;
		String warningMessage = null;
		boolean editableName = imageData.getImageType() == ImageType.BRIGHTFIELD_OTHER;
		if (pathROI != null) {
			if ((pathROI instanceof RectangleROI) && 
					!pathROI.isEmpty() &&
					((RectangleROI) pathROI).getArea() < 500*500) {
				if (Dialogs.showYesNoDialog("Color deconvolution stains", message)) {
					ImageServer<BufferedImage> server = imageData.getServer();
					BufferedImage img = null;
					try {
						img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), 1, pathROI));
					} catch (IOException e) {
						Dialogs.showErrorMessage("Set stain vector", "Unable to read image region");
						logger.error("Unable to read region", e);
					}
					int rgb = ColorDeconvolutionHelper.getMedianRGB(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
					if (num >= 0) {
						StainVector vectorValue = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels(name, img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()), stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
						if (!Double.isFinite(vectorValue.getRed() + vectorValue.getGreen() + vectorValue.getBlue())) {
							Dialogs.showErrorMessage("Set stain vector",
									"Cannot set stains for the current ROI!\n"
									+ "It might be too close to the background color.");
							return;
						}
						value = vectorValue;
					} else {
						// Update the background
						value = new double[] {ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb)};
					}
					wasChanged = true;
				}
			} else {
				warningMessage = "Note: To set stain values from an image region, draw a small, rectangular ROI first";
			}
		}

		// Prompt to set the name / verify stains
		ParameterList params = new ParameterList();
		String title;
		String nameBefore = null;
		String valuesBefore = null;
		String collectiveNameBefore = stains.getName();
		String suggestedName;
		if (collectiveNameBefore.endsWith("default"))
			suggestedName = collectiveNameBefore.substring(0, collectiveNameBefore.lastIndexOf("default")) + "modified";
		else
			suggestedName = collectiveNameBefore;
		params.addStringParameter("collectiveName", "Collective name", suggestedName, "Enter collective name for all 3 stains (e.g. H-DAB Scanner A, H&E Scanner B)");
		if (value instanceof StainVector) {
			nameBefore = ((StainVector)value).getName();
			valuesBefore = ((StainVector)value).arrayAsString(Locale.getDefault(Category.FORMAT));
			params.addStringParameter("name", "Name", nameBefore, "Enter stain name")
			.addStringParameter("values", "Values", valuesBefore, "Enter 3 values (red, green, blue) defining color deconvolution stain vector, separated by spaces");
			title = "Set stain vector";
		} else {
			nameBefore = "Background";
			valuesBefore = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2);
			params.addStringParameter("name", "Stain name", nameBefore);
			params.addStringParameter("values", "Stain values", valuesBefore, "Enter 3 values (red, green, blue) defining background, separated by spaces");
			params.setHiddenParameters(true, "name");
			title = "Set background";
		}

		if (warningMessage != null)
			params.addEmptyParameter(warningMessage);

		// Disable editing the name if it should be fixed
		ParameterPanelFX parameterPanel = new ParameterPanelFX(params);
		parameterPanel.setParameterEnabled("name", editableName);;
		if (!Dialogs.showConfirmDialog(title, parameterPanel.getPane()))
			return;

		// Check if anything changed
		String collectiveName = params.getStringParameterValue("collectiveName");
		String nameAfter = params.getStringParameterValue("name");
		String valuesAfter = params.getStringParameterValue("values");
		if (collectiveName.equals(collectiveNameBefore) && nameAfter.equals(nameBefore) && valuesAfter.equals(valuesBefore) && !wasChanged)
			return;

		double[] valuesParsed = ColorDeconvolutionStains.parseStainValues(Locale.getDefault(Category.FORMAT), valuesAfter);
		if (valuesParsed == null) {
			logger.error("Input for setting color deconvolution information invalid! Cannot parse 3 numbers from {}", valuesAfter);
			return;
		}

		if (num >= 0) {
			try {
				stains = stains.changeStain(StainVector.createStainVector(nameAfter, valuesParsed[0], valuesParsed[1], valuesParsed[2]), num);					
			} catch (Exception e) {
				logger.error("Error setting stain vectors", e);
				Dialogs.showErrorMessage("Set stain vectors", "Requested stain vectors are not valid!\nAre two stains equal?");
			}
		} else {
			// Update the background
			stains = stains.changeMaxValues(valuesParsed[0], valuesParsed[1], valuesParsed[2]);
		}
		
		// Set the collective name
		stains = stains.changeName(collectiveName);

		imageData.setColorDeconvolutionStains(stains);
		
		qupath.getViewer().repaintEntireImage();
	}
	
	
	/**
	 * Get the {@link Pane} component for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}
	
	private void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData != null)
			this.imageData.removePropertyChangeListener(this);
		
		this.imageData = imageData;
		ImageServer<BufferedImage> server = null;
		if (imageData != null) {
			imageData.addPropertyChangeListener(this);
			server = imageData.getServer();
		}
		
		table.getItems().setAll(getRows());
		table.refresh();
		
		if (listAssociatedImages != null) {
			if (server == null)
				listAssociatedImages.getItems().clear();
			else
				listAssociatedImages.getItems().setAll(server.getAssociatedImageList());
		}
	}


	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		setImageData(imageData);
	}


	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}


	private List<ImageDetailRow> getRows() {
		if (imageData == null || imageData.getServer() == null)
			return Collections.emptyList();
		var list = new ArrayList<ImageDetailRow>();
		if (imageData.isBrightfield())
			list.addAll(brightfieldRows);
		else
			list.addAll(otherRows);
		if (imageData.getServer().nZSlices() == 1)
			list.remove(ImageDetailRow.Z_SPACING);
		return list;
	}

	private String getName(ImageDetailRow row) {
		switch (row) {
		case NAME:
			return "Name";
		case URI:
			if (imageData != null && imageData.getServer().getURIs().size() == 1)
				return "URI";
			return "URIs";
		case IMAGE_TYPE:
			return "Image type";
		case METADATA_CHANGED:
			return "Metadata changed";
		case PIXEL_TYPE:
			return "Pixel type";
		case MAGNIFICATION:
			return "Magnification";
		case WIDTH:
			return "Width";
		case HEIGHT:
			return "Height";
		case DIMENSIONS:
			return "Dimensions (CZT)";
		case PIXEL_WIDTH:
			return "Pixel width";
		case PIXEL_HEIGHT:
			return "Pixel height";
		case Z_SPACING:
			return "Z-spacing";
		case UNCOMPRESSED_SIZE:
			return "Uncompressed size";
		case SERVER_TYPE:
			return "Server type";
		case PYRAMID:
			return "Pyramid";
		case STAIN_1:
			return "Stain 1";
		case STAIN_2:
			return "Stain 2";
		case STAIN_3:
			return "Stain 3";
		case BACKGROUND:
			return "Background";
		default:
			return null;
		}
	}

	static String decodeURI(URI uri) {
		try {
			return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return uri.toString();
		}
	}

	private Object getValue(ImageDetailRow rowType) {
		if (imageData == null)
			return null;
		ImageServer<BufferedImage> server = imageData.getServer();
		PixelCalibration cal = server.getPixelCalibration();
		switch (rowType) {
		case NAME:
			var project = QuPathGUI.getInstance().getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry == null)
				return ServerTools.getDisplayableImageName(server);
			else
				return entry.getImageName();
		case URI:
			Collection<URI> uris = server.getURIs();
			if (uris.isEmpty())
				return "Not available";
			if (uris.size() == 1)
				return decodeURI(uris.iterator().next());
			return "[" + String.join(", ", uris.stream().map(ImageDetailsPane::decodeURI).collect(Collectors.toList())) + "]";
		case IMAGE_TYPE:
			return imageData.getImageType();
		case METADATA_CHANGED:
			return hasOriginalMetadata(imageData.getServer()) ? "No" : "Yes";
		case PIXEL_TYPE:
			String type = server.getPixelType().toString().toLowerCase();
			if (server.isRGB())
				type += " (rgb)";
			return type;
		case MAGNIFICATION:
			double mag = server.getMetadata().getMagnification();
			if (Double.isNaN(mag))
				return "Unknown";
			return mag;
		case WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getWidth(), server.getWidth() * cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getWidth());
		case HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getHeight(), server.getHeight() * cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getHeight());
		case DIMENSIONS:
			return String.format("%d x %d x %d", server.nChannels(), server.nZSlices(), server.nTimepoints());
		case PIXEL_WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case PIXEL_HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case Z_SPACING:
			if (cal.hasZSpacingMicrons())
				return String.format("%.4f %s", cal.getZSpacingMicrons(), GeneralTools.micrometerSymbol());
			else
				return "Unknown";
		case UNCOMPRESSED_SIZE:
			double size =
			server.getWidth()/1024.0 * server.getHeight()/1024.0 * 
			server.getPixelType().getBytesPerPixel() * server.nChannels() *
			server.nZSlices() * server.nTimepoints();
			String units = " MB";
			if (size > 1000) {
				size /= 1024.0;
				units = " GB";
			}
			return GeneralTools.formatNumber(size, 1) + units;
		case SERVER_TYPE:
			return server.getServerType();
		case PYRAMID:
			if (server.nResolutions() == 1)
				return "No";
			return GeneralTools.arrayToString(Locale.getDefault(), server.getPreferredDownsamples(), 1);
		case STAIN_1:
			return imageData.getColorDeconvolutionStains().getStain(1);
		case STAIN_2:
			return imageData.getColorDeconvolutionStains().getStain(2);
		case STAIN_3:
			return imageData.getColorDeconvolutionStains().getStain(3);
		case BACKGROUND:
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			double[] whitespace = new double[]{stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()};
			return whitespace;
		default:
			return null;
		}

	}
		
}