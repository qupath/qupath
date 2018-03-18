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

package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import java.util.Locale.Category;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
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
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * A panel used for displaying basic info about an image, e.g. its path, width, height, pixel size etc.
 * 
 * It also includes displaying color deconvolution vectors for RGB brightfield images.
 * 
 * @author Pete Bankhead
 *
 */
public class PathImageDetailsPanel implements ImageDataChangeListener<BufferedImage>, PropertyChangeListener {
	
	final private static Logger logger = LoggerFactory.getLogger(PathImageDetailsPanel.class);
	
	private QuPathGUI qupath;
	
//	private BorderPane pane = new BorderPane();
	private StackPane pane = new StackPane();
	
	// TODO: Remove the Swing throwback...
	private PathImageDetailsTableModel model = new PathImageDetailsTableModel(null);
	
	private TableView<TableEntry> table = new TableView<>();
	private ListView<String> listImages = new ListView<>();
	private ListView<String> listAssociatedImages = new ListView<>();

	private TitledPane panelTable = new TitledPane("Properties", new StackPane(table));
	private TitledPane panelImages = new TitledPane("Image list", new StackPane(listImages));
	private TitledPane panelAssociatedImages = new TitledPane("Associated images", new StackPane(listAssociatedImages));
	
	private ImageData<BufferedImage> imageData;

	
	public PathImageDetailsPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.addImageDataChangeListener(this);

		// Create the table
		table.setMinHeight(200);
		table.setPrefHeight(250);
		table.setMaxHeight(Double.MAX_VALUE);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		TableColumn<TableEntry, String> columnName = new TableColumn<>("Name");
		columnName.setCellValueFactory(new PropertyValueFactory<>("name"));
		columnName.setEditable(false);
		columnName.setPrefWidth(150);
		TableColumn<TableEntry, Object> columnValue = new TableColumn<>("Value");
		columnValue.setCellValueFactory(new PropertyValueFactory<>("value"));
		columnValue.setEditable(false);
		columnValue.setPrefWidth(200);
		columnValue.setCellFactory(new Callback<TableColumn<TableEntry, Object>, TableCell<TableEntry, Object>>() {

			@Override
			public TableCell<TableEntry, Object> call(TableColumn<TableEntry, Object> param) {
				TableCell<TableEntry, Object> cell = new TableCell<TableEntry, Object>() {
					@Override
					protected void updateItem(Object item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setText(null);
							setGraphic(null);
							return;
						}
						//			             ComboBoxTableCell<TableEntry, Object>
						Color textColor = Color.BLACK;
						String text = item == null ? "" : item.toString();
						if (item instanceof double[]) {
							text = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])item, 2);
						} else if (item instanceof StainVector) {
							StainVector stain = (StainVector)item;
							textColor = getColorFX(stain.getColor());
						}
						//			             if (item instanceof ImageType) {
						//			            	 ComboBox<ImageType> combo = new ComboBox<>();
						//			            	 combo.getItems().addAll(ImageType.values());
						//			            	 combo.getSelectionModel().select((ImageType)item);
						//			            	 getChildren().add(combo);
						//			             } else
						setTextFill(textColor);
						setText(text);
						setTooltip(new Tooltip(text));
					}
				};
				
				cell.setOnMouseClicked(event -> {
					if (event.getClickCount() < 2)
						return;
					TableCell<TableEntry, Object> c = (TableCell<TableEntry, Object>)event.getSource();
					Object value = c.getItem();
					if (value instanceof StainVector || value instanceof double[])
						editStainVector(value);
					else if (value instanceof ImageType) {
						ImageType type = (ImageType)DisplayHelpers.showChoiceDialog("Image type", "Set image type", ImageType.values(), (ImageType)value);
						if (type != null)
							imageData.setImageType(type);
					}
				});
				
				return cell;
			}
		});
		table.getColumns().addAll(columnName, columnValue);
		
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
					File fileOutput = QuPathGUI.getDialogHelper(dialog).promptToSaveFile("Save image", null, name, "PNG", ".png");
					if (fileOutput != null) {
						try {
							ImageIO.write(img, "PNG", fileOutput);
						} catch (Exception e1) {
							DisplayHelpers.showErrorMessage("Save image", "Error saving " + fileOutput.getName() + "\n" + e1.getLocalizedMessage());
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
				Image img = SwingFXUtils.toFXImage(imageData.getServer().getAssociatedImage(name), null);
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
				menubar.setUseSystemMenuBar(true);
				pane.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
				
				dialog.setScene(scene);
				dialog.show();
			}
		);
		
		listImages.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				tryToOpenSelectedEntry();
				e.consume();
			}
		});

		listImages.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				tryToOpenSelectedEntry();
				e.consume();
			}
		});
		
		
		Accordion accordion = new Accordion(panelTable, panelImages, panelAssociatedImages);
		accordion.setExpandedPane(panelTable);
		pane.getChildren().add(accordion);
	}
	
	
	/**
	 * Try to open the selected image from the list.
	 * 
	 * This will involve prompting the user if required, so it is not guaranteed that this method will change 
	 * the current image.
	 */
	private void tryToOpenSelectedEntry() {
		String name = listImages.getSelectionModel().getSelectedItem();
		if (name == null)
			return;
		
		ImageServer<BufferedImage> serverPrevious = imageData.getServer();
		if (serverPrevious != null && name.equals(serverPrevious.getDisplayedImageName()))
			return;
		
		String newServerPath = serverPrevious.getSubImagePath(name);
		if (qupath.getProject() != null) {
			qupath.openImageEntry(qupath.getProject().getImageEntry(newServerPath));
			return;
		} else
			qupath.openImage(newServerPath, true, true, false);
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
				if (DisplayHelpers.showYesNoDialog("Color deconvolution stains", message)) {
					ImageServer<BufferedImage> server = imageData.getServer();
					BufferedImage img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), 1, pathROI));
					int rgb = ColorDeconvolutionHelper.getMedianRGB(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
					if (num >= 0) {
						value = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels(name, img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()), stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
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
		params.addStringParameter("collectiveName", "Collective name", collectiveNameBefore, "Enter collective name for all 3 stains (e.g. H-DAB Scanner A, H&E Scanner B)");
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
			params.addEmptyParameter("warning", warningMessage);

		// Disable editing the name if it should be fixed
		ParameterPanelFX parameterPanel = new ParameterPanelFX(params);
		parameterPanel.setParameterEnabled("name", editableName);;
		if (!DisplayHelpers.showConfirmDialog(title, parameterPanel.getPane()))
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
				stains = stains.changeStain(new StainVector(nameAfter, valuesParsed), num);					
			} catch (Exception e) {
				logger.error("Error setting stain vectors", e);
				DisplayHelpers.showErrorMessage("Set stain vectors", "Requested stain vectors are not valid!\nAre two stains equal?");
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



	public static Color getColorFX(final int rgb) {
		return Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
	}
	
	
	
	public Pane getContainer() {
		return pane;
	}
	
	
	
	public class TableEntry {
		
		private final int ind;
		
		public TableEntry(final int ind) {
			this.ind = ind;
		}
		
		public String getName() {
			return (String)model.getName(ind);
		}
		
		public Object getValue() {
			return model.getValue(ind);
		}
		
	}
	
	
	private void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData != null)
			this.imageData.removePropertyChangeListener(this);
		
		this.imageData = imageData;
		ImageServer<BufferedImage> server = null;
		if (imageData == null) {
			model.setImageData(null);
		} else {
			imageData.addPropertyChangeListener(this);
			server = imageData.getServer();
		}
		model.setImageData(imageData);
		
		ObservableList<TableEntry> list = FXCollections.observableArrayList();
		for (int i = 0; i < model.getRowCount(); i++)
			list.add(new TableEntry(i));
		table.setItems(list);
		
		if (server == null)
			listImages.getItems().clear();
		else if (!listImages.getItems().equals(server.getSubImageList()))
			listImages.getItems().setAll(server.getSubImageList());
		if (panelImages != null) {
			int nImages = listImages.getItems().size();
			if (nImages > 0) {
				listImages.getSelectionModel().select(server.getDisplayedImageName());
				updateThumbnail(server.getDisplayedImageName());
//				panelImages.setExpanded(true);
			}
			panelImages.setText("Image list (" + nImages + ")");
		}
		if (panelAssociatedImages != null) {
			if (server == null)
				listAssociatedImages.getItems().clear();
			else
				listAssociatedImages.getItems().setAll(server.getAssociatedImageList());
			
			int nAssociated = listAssociatedImages.getItems().size();
//			if (nAssociated == 0)
//				panelAssociatedImages.setText("Associated images (empty)");
//			else
//				panelAssociatedImages.setText("Associated images (" + nAssociated + ")");
			panelAssociatedImages.setText("Associated images (" + nAssociated + ")");
		}

//		panelRelated.revalidate();
			
//		// TODO: Deal with line breaks
//		labelPath.setText("<html><body style='width:100%'>" + server.getServerPath().replace("%20", " "));
	}
	
	
	private void updateThumbnail(String name) {
		// TODO: PUT THIS BACK IN PLACE!!!!!
//		PathImageServer server = imageData.getServer();
//		BufferedImage img = server.getBufferedThumbnail(400, -1, 0);
//		if (!(img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_INT_ARGB || img.getType() == BufferedImage.TYPE_INT_ARGB_PRE)) {
//			 BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
//			 Graphics2D g2d = img2.createGraphics();
//			 g2d.drawImage(img, 0, 0, null);
//			 g2d.dispose();
//			 img = img2;
//		}
//		labelPreview.setIcon(new ImageIcon(img.getScaledInstance(img.getWidth()/2, -1, BufferedImage.SCALE_SMOOTH)));

		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
//		// If we have another server, close it
//		if (!modelSubImages.getPathImageServer().usesBaseServer(server)) {
//			server.close();
//			System.out.println("Closed the server");
//		}
	}


	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		setImageData(imageData);
		
//		table.repaint();
	}


	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}
		
	
	static class PathImageDetailsTableModel {
		
		private ImageData<BufferedImage> imageData;
		
		protected enum ROW_TYPE {NAME, PATH, IMAGE_TYPE, BIT_DEPTH, MAGNIFICATION, WIDTH, HEIGHT, PIXEL_WIDTH, PIXEL_HEIGHT, SERVER_TYPE};

//		protected enum ROW_TYPE {PATH, IMAGE_TYPE, MAGNIFICATION, WIDTH, HEIGHT, PIXEL_WIDTH, PIXEL_HEIGHT,
//				CHANNEL_1, CHANNEL_1_STAIN, CHANNEL_2, CHANNEL_2_STAIN, CHANNEL_3, CHANNEL_3_STAIN
//		};
		
		private PathImageDetailsTableModel(ImageData<BufferedImage> imageData) {
			super();
			setImageData(imageData);
		}
		
		private void setImageData(ImageData<BufferedImage> imageData) {
			if (this.imageData == null || !this.imageData.equals(imageData)) {
				this.imageData = imageData;
			}
		}
		
		private int getNDetailRows() {
			return ROW_TYPE.values().length;
		}

		private int getRowCount() {
			if (imageData == null || imageData.getServer() == null)
				return 0;
			if (imageData.isBrightfield())
				return getNDetailRows() + 4; // Additional space is for color deconvolution values
			else
				return getNDetailRows();
		}
		
		private ROW_TYPE getRowType(int row) {
			ROW_TYPE[] types = ROW_TYPE.values();
			if (row < types.length)
				return (types[row]);
			return null;
		}
		
		private Object getName(int row) {
			ROW_TYPE rowType = getRowType(row);
			if (rowType == null) {
				row -= getNDetailRows();
				if (row < 3)
					return String.format("Stain %d", row+1);
				else if (row == 3)
					return "Background";
				else
					return null;
			}
			switch (rowType) {
			case NAME:
				return "Name";
			case PATH:
				return "Path";
			case IMAGE_TYPE:
				return "Image type";
			case BIT_DEPTH:
				return "Bit depth";
			case MAGNIFICATION:
				return "Magnification";
			case WIDTH:
				return "Width";
			case HEIGHT:
				return "Height";
			case PIXEL_WIDTH:
				return "Pixel width";
			case PIXEL_HEIGHT:
				return "Pixel height";
			case SERVER_TYPE:
				return "Server type";
			default:
				return null;
			}
		}
		
		private Object getValue(int row) {
			ROW_TYPE rowType = getRowType(row);
			if (rowType == null) {
				row -= getNDetailRows();
				if (row < 3)
					return imageData.getColorDeconvolutionStains().getStain(row+1);
				else if (row == 3) {
					ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
					double[] whitespace = new double[]{stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()};
					return whitespace;
				} else
					return null;
			}
			ImageServer<BufferedImage> server = imageData.getServer();
			switch (rowType) {
			case NAME:
				return server.getDisplayedImageName();
			case PATH:
				return server.getPath();
			case IMAGE_TYPE:
				return imageData.getImageType();
			case BIT_DEPTH:
				return server.isRGB() ? "8-bit (RGB)" : server.getBitsPerPixel();
			case MAGNIFICATION:
				return server.getMagnification();
			case WIDTH:
				if (server.hasPixelSizeMicrons())
					return String.format("%s px (%.2f %s)", server.getWidth(), server.getWidth() * server.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
				else
					return String.format("%s px", server.getWidth());
			case HEIGHT:
				if (server.hasPixelSizeMicrons())
					return String.format("%s px (%.2f %s)", server.getHeight(), server.getHeight() * server.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
				else
					return String.format("%s px", server.getHeight());
			case PIXEL_WIDTH:
				if (server.hasPixelSizeMicrons())
					return String.format("%.4f %s", server.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
				else
					return "Unknown";
			case PIXEL_HEIGHT:
				if (server.hasPixelSizeMicrons())
					return String.format("%.4f %s", server.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
				else
					return "Unknown";
			case SERVER_TYPE:
				return server.getServerType();
//			case TMA_GRID:
//				return new Boolean(details.hasTMAGrid());
			default:
				return null;
			}
		}

	}
		
}