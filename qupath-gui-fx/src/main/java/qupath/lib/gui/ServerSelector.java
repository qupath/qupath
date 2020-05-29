/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;

/**
 * Helper class for selecting one image server out of a collection.
 */
class ServerSelector {

	private static final Logger logger = LoggerFactory.getLogger(ServerSelector.class);
	
	private List<ImageServer<BufferedImage>> serverList = new ArrayList<>();
	private ImageServer<BufferedImage> selectedSeries = null;
	
	ServerSelector(Collection<ServerBuilder<BufferedImage>> builders) {
		// TODO: Build servers on demand, rather than all at the start
		for (var builder : builders) {
			try {
				serverList.add(builder.build());
			} catch (Exception e) {
				logger.warn("Unable to build series {}", builder);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public ImageServer<BufferedImage> promptToSelectServer() {	
		if (serverList.isEmpty()) {
			logger.warn("No series available!");
			return null;
		} else if (serverList.size() == 1) {
			logger.warn("Only one server available!");
			return serverList.get(0);
		}
		// Get thumbnails in separate thread
		ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

		ListView<ImageServer<BufferedImage>> listSeries = new ListView<>();
		listSeries.setPrefWidth(480);
		listSeries.setMinHeight(100);
		
		// thumbnailBank is the map for storing thumbnails
		Map<String, BufferedImage> thumbnailBank = new HashMap<String, BufferedImage>();
		for (ImageServer<BufferedImage> server: serverList) {
			executor.submit(() -> {
				try {
					thumbnailBank.put(server.getMetadata().getName(), ProjectCommands.getThumbnailRGB(server));
					Platform.runLater( () -> listSeries.refresh());
				} catch (IOException e) {
					logger.warn("Error loading thumbnail: " + e.getLocalizedMessage(), e);
				}
			});
		};
		
		double thumbnailSize = 80;
		listSeries.setCellFactory(v -> new ImageAndNameListCell(thumbnailBank, thumbnailSize, thumbnailSize));
		listSeries.getItems().setAll(serverList);

		
		// Info table - Changes according to selected series
		String[] attributes = new String[] {"Full Path", "Server Type", "Width", "Height", "Pixel Width", "Pixel Height", "Pixel Type", "Number of Channels", "Number of Resolutions"};
		Integer[] indices = new Integer[9];
		for (int index = 0; index < 9; index++) indices[index] = index;
		ObservableList<Integer> indexList = FXCollections.observableArrayList(indices);
		
		TableView<Integer> tableInfo = new TableView<>();
		tableInfo.setMinHeight(200);
		tableInfo.setMinWidth(500);
		
		// First column (attribute names)
		TableColumn<Integer, String> attributeCol = new TableColumn<Integer, String>("Attribute");
		attributeCol.setMinWidth(242);
		attributeCol.setResizable(false);
		attributeCol.setCellValueFactory(cellData -> {
			return new ReadOnlyObjectWrapper<String>(attributes[cellData.getValue()]);
		});
		
		// Second column (attribute values)
		TableColumn<Integer, String> valueCol = new TableColumn<Integer, String>("Value");
		valueCol.setMinWidth(242);
		valueCol.setResizable(false);
		valueCol.setCellValueFactory(cellData -> {
			if (selectedSeries != null) return getSeriesQuickInfo(selectedSeries, cellData.getValue());
			else return null;
		});
		
		
		// Adding the values on hover over the info table
		tableInfo.setRowFactory(tableView -> {
            final TableRow<Integer> row = new TableRow<>();
            row.hoverProperty().addListener((observable) -> {
                final var element = row.getItem();
                if (row.isHover() && selectedSeries != null) {
                	ObservableValue<String> value = getSeriesQuickInfo(selectedSeries, element);
                	Tooltip tooltip = new Tooltip(value.getValue());
                	Tooltip.install(row, tooltip);
                }
            });
            return row;
		});
		
		// Set items to info table
		tableInfo.setItems(indexList);
		tableInfo.getColumns().addAll(attributeCol, valueCol);
		

		// Pane structure
		BorderPane paneSelector = new BorderPane();
		BorderPane paneSeries = new BorderPane(listSeries);
		BorderPane paneInfo = new BorderPane(tableInfo);
		paneInfo.setMaxHeight(100);
		paneSelector.setCenter(paneSeries);
		paneSelector.setBottom(paneInfo);

		BorderPane pane = new BorderPane();
		pane.setCenter(paneSelector);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Open image");
		ButtonType typeImport = new ButtonType("Open", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImport, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(pane);
		
		listSeries.getSelectionModel().selectedItemProperty().addListener((obs, previousSelectedRow, selectedRow) -> {
		    if (selectedRow != null) {
		    	selectedSeries = selectedRow;
		    	indexList.removeAll(indexList);
		    	indexList.addAll(indices);
		    }
		});
		
		listSeries.setOnMouseClicked(new EventHandler<MouseEvent>() {

		    @Override
		    public void handle(MouseEvent click) {
		    	ImageServer<BufferedImage> selectedItem = listSeries.getSelectionModel().getSelectedItem();

		        if (click.getClickCount() == 2 && selectedItem != null) {
		        	Button okButton = (Button) dialog.getDialogPane().lookupButton(typeImport);
		        	okButton.fire();
		        }
		    }
		});
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		try {
			executor.shutdownNow();
		} catch (Exception e) {
			logger.warn(e.getLocalizedMessage(), e);
		} finally {
			selectedSeries = null;
			try {
				for (ImageServer<BufferedImage> server: serverList)
					server.close();
			} catch (Exception e) {
				logger.debug(e.getLocalizedMessage(), e);
			}
		}		
		
		if (!result.isPresent() || result.get() != typeImport || result.get() == ButtonType.CANCEL)
			return null;
		
		return listSeries.getSelectionModel().getSelectedItem();
	}
	
	
	private static ObservableValue<String> getSeriesQuickInfo(ImageServer<BufferedImage> imageServer, int index) {
		String filePath = imageServer.getURIs().iterator().next().toString();
		String serverType = imageServer.getServerType();
		String width = "" + imageServer.getWidth() + " px";
		String height = "" + imageServer.getHeight() + " px";
		double pixelWidthTemp = imageServer.getPixelCalibration().getPixelWidth().doubleValue();
		String pixelWidth = GeneralTools.formatNumber(pixelWidthTemp, 4) + " " + imageServer.getPixelCalibration().getPixelWidthUnit();
		double pixelHeightTemp = imageServer.getPixelCalibration().getPixelHeight().doubleValue();
		String pixelHeight = GeneralTools.formatNumber(pixelHeightTemp, 4) + " " + imageServer.getPixelCalibration().getPixelHeightUnit();
		String pixelType = imageServer.getPixelType().toString();
		String nChannels = String.valueOf(imageServer.nChannels());
		String nResolutions = String.valueOf(imageServer.nResolutions());
		String[] outString = new String[] {filePath, serverType, width, height, pixelWidth, pixelHeight, pixelType, nChannels, nResolutions};
		ObservableValue<String> out = new ReadOnlyObjectWrapper<String>(outString[index]);
		return out;
	}
	
	
	
	
	/**
	 * A {@link ListCell} that displays an image and associated name.
	 * The image is retrieved from a cache rather than loaded directly, therefore it is assumed that 
	 * the cache is populated elsewhere.
	 */
	static class ImageAndNameListCell extends ListCell<ImageServer<BufferedImage>> {
		
		private Map<String, BufferedImage> imageCache;
		final private Canvas canvas = new Canvas();
		
		private Image img;
		
		public ImageAndNameListCell(final Map<String, BufferedImage> imageCache, double imgWidth, double imgHeight) {
			super();
			this.imageCache = imageCache;
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			canvas.setWidth(imgWidth);
			canvas.setHeight(imgHeight);
			canvas.heightProperty().bind(canvas.widthProperty());
			setGraphicTextGap(10);
			setGraphic(canvas);
			setText(null);
		}


		@Override
		protected void updateItem(ImageServer<BufferedImage> entry, boolean empty) {
			super.updateItem(entry, empty);

			GraphicsContext gc = canvas.getGraphicsContext2D();        
			gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
			
			if (entry == null || empty) {
				setTooltip(null);
				setText(null);
				img = null;
				return;
			}
			
			String name = entry.getMetadata().getName();
			var thumbnail = imageCache.get(name);
			if (thumbnail != null)
				img =  SwingFXUtils.toFXImage(thumbnail, null);
			
			setText(name);
			
			if (img == null)
				return;
			else
				GuiTools.paintImage(canvas, img);

			// Setting tooltips on hover
			Tooltip tooltip = new Tooltip();
			ImageView imageView = new ImageView(img);
			imageView.setFitHeight(250);
			imageView.setPreserveRatio(true);
			tooltip.setGraphic(imageView);

			setTooltip(new Tooltip(name));

		}
		
	}
	
}