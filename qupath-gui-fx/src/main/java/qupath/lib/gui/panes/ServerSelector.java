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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;

/**
 * Helper class for selecting one image server out of a collection.
 * 
 */
public class ServerSelector {

	private static final Logger logger = LoggerFactory.getLogger(ServerSelector.class);
	
	private List<ImageServer<BufferedImage>> serverList = new ArrayList<>();
	private boolean closeAfterPrompt = false;
	
	private static boolean buildParallel = true;
	
	private static enum Attribute {
		PATH("Full Path"),
		TYPE("Server Type"),
		INDEX("Image index"), // Index in the list of builders (*sometimes* meaningful, e.g. when builders represent series in a Bio-Format server, in order)
		WIDTH("Width"),
		HEIGHT("Height"),
		DIMENSIONS("Dimensions (CZT)"),
		PIXEL_WIDTH("Pixel Width"),
		PIXEL_HEIGHT("Pixel Height"),
		PIXEL_TYPE("Pixel Type"),
		PYRAMID("Pyramid");
		
		private String text;
		
		private Attribute(String text) {
			this.text = text;
		}
		
		public String getText() {
			return text;
		}
		
	}
	
	public static ServerSelector createFromBuilders(Collection<? extends ServerBuilder<BufferedImage>> builders) {
		// TODO: Consider building servers on demand, rather than all at the start
		var stream = builders.stream();
		if (buildParallel)
			stream = stream.parallel();
		
		var list = stream.map(b -> {
			try {
				return b.build();
			} catch (Exception e) {
				logger.warn("Unable to build {}", b);
				return null;
			}
		}).filter(s -> s != null).collect(Collectors.toList());
		return new ServerSelector(list, true);
	}
	
	public static ServerSelector create(Collection<? extends ImageServer<BufferedImage>> servers) {
		return new ServerSelector(servers, false);
	}
	
	private ServerSelector(Collection<? extends ImageServer<BufferedImage>> servers, boolean closeAfterPrompt) {
		this.serverList.addAll(servers);
		this.closeAfterPrompt = closeAfterPrompt;
	}
	
	
	/**
	 * Prompt to select a single {@link ImageServer}.
	 * @param prompt a one-word prompt to use in the title or button; typically "Open", "Import" or "Select"
	 * @param alwaysShow if true, always show the prompt; if false, it won't be shown if it isn't necessary (i.e. there are 0 or 1 servers).
	 * @return the selected server, or null if no server was selected
	 */
	public ImageServer<BufferedImage> promptToSelectImage(String prompt, boolean alwaysShow) {	
		if (!alwaysShow) {
			if (serverList.isEmpty()) {
				logger.warn("No images available!");
				return null;
			} else if (serverList.size() == 1) {
				logger.warn("Only one image available!");
				return serverList.get(0);
			}
		}
		var result = promptToSelectServers(false, prompt);
		return result.isEmpty() ? null : result.iterator().next();
	}
	
	/**
	 * Prompt to select multiple {@linkplain ImageServer ImageServers}.
	 * @param prompt a one-word prompt to use in the title or button; typically "Open", "Import" or "Select"
	 * @return the selected servers, or empty list if no servers were selected
	 */
	public List<ImageServer<BufferedImage>> promptToSelectImages(String prompt) {	
		return promptToSelectServers(true, prompt);
	}
		
		
	@SuppressWarnings("unchecked")
	private List<ImageServer<BufferedImage>> promptToSelectServers(boolean multiSelection, String promptBase) {	
		
		var prompt = promptBase == null ? "Select" : promptBase;
		
		// Get thumbnails in separate thread
		ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

		ListView<ImageServer<BufferedImage>> listSeries = new ListView<>();
		listSeries.setPrefWidth(480);
		listSeries.setMinHeight(100);
		if (multiSelection)
			listSeries.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		else
			listSeries.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		
		// thumbnailBank is the map for storing thumbnails
		Map<String, BufferedImage> thumbnailBank = new HashMap<String, BufferedImage>();
		for (ImageServer<BufferedImage> server: serverList) {
			// Check the image size before trying to get a thumbnail
			double downsample = server.getDownsampleForResolution(server.nResolutions()-1);
			double width = server.getWidth() / downsample;
			double height = server.getHeight() / downsample;
			if (width * height < 10_000 * 10_000) {
				executor.submit(() -> {
					try {
						thumbnailBank.put(server.getMetadata().getName(), ProjectCommands.getThumbnailRGB(server));
						Platform.runLater( () -> listSeries.refresh());
					} catch (IOException e) {
						logger.warn("Error loading thumbnail: " + e.getLocalizedMessage(), e);
					}
				});
			} else {
				logger.warn("Image too big! Not generating thumbnail for {}", server);
			}
		};
		
		double thumbnailSize = 80;
		listSeries.setCellFactory(v -> new ImageAndNameListCell(thumbnailBank, thumbnailSize, thumbnailSize));
		
		// Create a filter to make finding images easier
		var items = FXCollections.observableArrayList(serverList).filtered(p -> true);
		
		var tfFilter = new TextField();
		var filterText = tfFilter.textProperty();
		ObservableValue<Predicate<ImageServer<BufferedImage>>> predicateProperty = Bindings.createObjectBinding(() -> {
			var text = filterText.get();
			if (text == null || text.isEmpty())
				return s -> true;
			var textLower = text.toLowerCase();
			return s -> {
				var name = s.getMetadata().getName();
				return name != null && name.toLowerCase().contains(textLower);
			};
		}, filterText);
		items.predicateProperty().bind(predicateProperty);
		listSeries.setItems(items);

		
		// Info table - Changes according to selected series
		TableView<Attribute> tableInfo = new TableView<>();
		tableInfo.setMinHeight(200);
		tableInfo.setMinWidth(500);
		
		// First column (attribute names)
		TableColumn<Attribute, String> attributeCol = new TableColumn<>("Attribute");
		attributeCol.setResizable(true);
		attributeCol.setCellValueFactory(cellData -> {
			return new ReadOnlyObjectWrapper<>(cellData.getValue() == null ? null : cellData.getValue().getText());
		});
		
		// Second column (attribute values)
		TableColumn<Attribute, String> valueCol = new TableColumn<>("Value");
		valueCol.setMinWidth(242);
		valueCol.setResizable(true);
		valueCol.setCellValueFactory(cellData -> {
			int ind = listSeries.getSelectionModel().getSelectedIndex();
			if (ind >= 0 && ind < serverList.size())
				return new ReadOnlyObjectWrapper<>(getServerAttribute(serverList.get(ind), cellData.getValue(), ind));
			else
				return null;
		});
		tableInfo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		
		
		// Adding the values on hover over the info table
		tableInfo.setRowFactory(tableView -> {
            final TableRow<Attribute> row = new TableRow<>();
            row.hoverProperty().addListener((observable) -> {
                final var element = row.getItem();
    			int ind = listSeries.getSelectionModel().getSelectedIndex();
                if (row.isHover() && ind >= 0 && ind < serverList.size()) {
                	var value = getServerAttribute(serverList.get(ind), element, ind);
                	Tooltip tooltip = new Tooltip(value);
                	Tooltip.install(row, tooltip);
                }
            });
            return row;
		});
		
		// Set items to info table
		tableInfo.getItems().setAll(Attribute.values());
		// Remove index as an attribute if we have images associated with different URIs
		if (!sameUri())
			tableInfo.getItems().remove(Attribute.INDEX);
		tableInfo.getColumns().addAll(attributeCol, valueCol);
		

		// Pane structure
		var paneSelector = new BorderPane();
		var paneSeries = new BorderPane(listSeries);
		var paneInfo = new BorderPane(tableInfo);
		paneInfo.setMaxHeight(100);
		paneSelector.setCenter(paneSeries);
		
		var paneFilter = new BorderPane(tfFilter);
		var labelFilter = new Label("Search: ");
		labelFilter.setAlignment(Pos.CENTER_LEFT);
		labelFilter.setMaxHeight(Double.MAX_VALUE);
		labelFilter.setLabelFor(tfFilter);
		paneFilter.setLeft(labelFilter);
		paneInfo.setBottom(paneFilter);
		paneFilter.setPadding(new Insets(5, 0, 5, 0));
		
		paneSelector.setBottom(paneInfo);

		BorderPane pane = new BorderPane();
		pane.setCenter(paneSelector);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		var qupath = QuPathGUI.getInstance();
		
		// Try to ensure we have a suitable owner, even if a progress dialog is visible
		var mainStage = qupath == null ? null : qupath.getStage();
		var owner = Window.getWindows().stream()
//				.filter(w -> w.isFocused())
				.map(w -> w instanceof Stage ? (Stage)w : null)
				.filter(s -> s != null)
				.filter(s -> s.isFocused() && s.getModality() != Modality.NONE)
				.findFirst()
				.orElse(mainStage);
		dialog.initOwner(owner);
		
		if (multiSelection)
			dialog.setTitle(prompt + " images");
		else
			dialog.setTitle(prompt + " image");
		ButtonType typeImportSelected = new ButtonType(prompt + " selected", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImportSelected, ButtonType.CANCEL);

		dialog.getDialogPane().setContent(pane);
		
		listSeries.getSelectionModel().selectedItemProperty().addListener((obs, previousSelectedRow, selectedRow) -> {
		    tableInfo.refresh();
		});
		
		// If we accept a single selection, then handle double click
		if (!multiSelection) {
			listSeries.setOnMouseClicked(click -> {
			    	var selectedItem = listSeries.getSelectionModel().getSelectedItem();
			        if (click.getClickCount() == 2 && selectedItem != null) {
			        	Button acceptButton = (Button) dialog.getDialogPane().lookupButton(typeImportSelected);
			        	acceptButton.fire();
			        }
			    });
		}
		
		var nSelected = Bindings.createIntegerBinding(() -> {
			return listSeries.getSelectionModel().getSelectedIndices().size();
		}, listSeries.getSelectionModel().getSelectedItems());
		
		var selectAll = Bindings.createBooleanBinding(() -> {
			var n = nSelected.get();
			return n == 0 || n >= serverList.size();
		}, nSelected);

		// Update button with the number currently selected
		var btnSelected = (Button)dialog.getDialogPane().lookupButton(typeImportSelected);
		if (multiSelection) {
			btnSelected.textProperty().bind(Bindings.createStringBinding(() -> {
				return selectAll.get() ? prompt + " all" : prompt + " " + nSelected.get();
			}, selectAll));
		} else {
			btnSelected.disableProperty().bind(nSelected.isNotEqualTo(1));
		}
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		Set<ImageServer<BufferedImage>> selectedToReturn;
		var resultType = result.orElse(ButtonType.CANCEL);
		if (resultType == ButtonType.CANCEL) {
			selectedToReturn = Collections.emptySet();
		} else if (multiSelection && selectAll.get()) {
			selectedToReturn = new LinkedHashSet<>(serverList);
		} else {
			selectedToReturn = new LinkedHashSet<>(listSeries.getSelectionModel().getSelectedItems());
		}
		
		var returnNothing = !result.isPresent() || result.get() != typeImportSelected || result.get() == ButtonType.CANCEL;
		if (returnNothing)
			selectedToReturn = Collections.emptySet();
		
		try {
			executor.shutdownNow();
		} catch (Exception e) {
			logger.warn(e.getLocalizedMessage(), e);
		} finally {
			// Close servers that we aren't returning, if required
			if (closeAfterPrompt) {
				try {
					for (ImageServer<BufferedImage> server: serverList) {
						if (!selectedToReturn.contains(server))
							server.close();
					}
				} catch (Exception e) {
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
		}		
		
		if (selectedToReturn.isEmpty())
			return Collections.emptyList();
		
		return new ArrayList<>(selectedToReturn);
	}
	
	
	
	private boolean sameUri() {
		var set = new HashSet<URI>();
		for (var server : serverList) {
			set.addAll(server.getURIs());
		}
		return set.size() <= 1;
	}
	
	
	
	private static String getServerAttribute(ImageServer<?> server, Attribute attribute, int index) {
		switch (attribute) {
		case INDEX:
			return String.valueOf(index);
		case PATH:
			var uris = server.getURIs();
			if (uris.size() == 1)
				return uriToString(uris.iterator().next());
			return "[" + server.getURIs().stream().map(ServerSelector::uriToString).collect(Collectors.joining(", ")) + "]";
		case PIXEL_HEIGHT:
			double pixelHeightTemp = server.getPixelCalibration().getPixelHeight().doubleValue();
			return GeneralTools.formatNumber(pixelHeightTemp, 4) + " " + server.getPixelCalibration().getPixelHeightUnit();
		case PIXEL_TYPE:
			return server.getPixelType().toString();
		case PIXEL_WIDTH:
			double pixelWidthTemp = server.getPixelCalibration().getPixelWidth().doubleValue();
			return GeneralTools.formatNumber(pixelWidthTemp, 4) + " " + server.getPixelCalibration().getPixelWidthUnit();
		case TYPE:
			return server.getServerType();
		case WIDTH:
			return server.getWidth() + " px";
		case HEIGHT:
			return server.getHeight() + " px";
		case DIMENSIONS:
			return String.format("%d x %d x %d", server.nChannels(), server.nZSlices(), server.nTimepoints());
		case PYRAMID:
			if (server.nResolutions() == 1)
				return "No";
			return GeneralTools.arrayToString(Locale.getDefault(Locale.Category.FORMAT), server.getPreferredDownsamples(), 1);
		default:
			return null;
		}
	}
	
	
	private static String uriToString(URI uri) {
		if (uri == null)
			return "";
		try {
			return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return uri.toString();
		}
	}
	
	
	
	
	/**
	 * A {@link ListCell} that displays an image and associated name.
	 * The image is retrieved from a cache rather than loaded directly, therefore it is assumed that 
	 * the cache is populated elsewhere.
	 */
	static class ImageAndNameListCell extends ListCell<ImageServer<BufferedImage>> {
		
		private Map<String, BufferedImage> imageCache;
		private final Canvas canvas = new Canvas();
		
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
			String text = name == null || name.isBlank() ? "(No image name)" : name + "\n";
			text = text + "(Image " + (getIndex()+1) + ")";
			
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

			setTooltip(new Tooltip(text));

		}
		
	}
	
}