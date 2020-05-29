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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.controlsfx.control.GridCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Grid display of TMA cores.
 * <p>
 * This requires cores in memory, so does not scale wonderfully... but it can be quite useful for individual slides.
 * 
 * @author Pete Bankhead
 *
 */
class TMAGridView implements Runnable, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {
	
	final private static Logger logger = LoggerFactory.getLogger(TMAGridView.class);
	
	private QuPathGUI qupath;
	private Stage stage;
	
	private QuPathGridView grid = new QuPathGridView();
	private ObservableList<TMACoreObject> backingList = FXCollections.observableArrayList();
	private FilteredList<TMACoreObject> filteredList = new FilteredList<>(backingList);
	
	private ObservableMeasurementTableData model = new ObservableMeasurementTableData();

	private ImageData<BufferedImage> imageData;
	
	private StringProperty measurement = new SimpleStringProperty();
	private BooleanProperty showMeasurement = new SimpleBooleanProperty(true);
	private BooleanProperty descending = new SimpleBooleanProperty(false);
	private BooleanProperty doAnimate = new SimpleBooleanProperty(true);
	
	/**
	 * Max thumbnails to store in cache.
	 * This ends up ~< 90MB, assuming RGBA.
	 */
	private static int MAX_CACHE_SIZE = 250;
	
	/**
	 * Cache for storing image thumbnails
	 */
	private static Map<RegionRequest, Image> cache = Collections.synchronizedMap(new LinkedHashMap<RegionRequest, Image>() {
		private static final long serialVersionUID = 1L;
		@Override
		protected synchronized boolean removeEldestEntry(Map.Entry<RegionRequest, Image> eldest) {
			return size() > MAX_CACHE_SIZE;
		}

	});
	
	public static enum CoreDisplaySize {
			TINY("Tiny", 60),
			SMALL("Small", 100),
			MEDIUM("Medium", 200),
			LARGE("Large", 300);
		
		private String name;
		private int size;
		
		CoreDisplaySize(final String name, final int size) {
			this.name = name;
			this.size = size;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public int getSize() {
			return size;
		}
		
	};
	

	public TMAGridView(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.imageDataProperty().addListener(this);
	}

	@Override
	public void run() {
		if (stage == null)
			initializeGUI();
		else if (!stage.isShowing())
			stage.show();
		initializeData(qupath.getImageData());
	}
	
	private static void sortCores(final ObservableList<TMACoreObject> cores, final ObservableMeasurementTableData model, final String measurementName, final boolean doDescending) {
		cores.sort((t1, t2) -> {
			double m1 = model.getNumericValue(t1, measurementName);
			double m2 = model.getNumericValue(t2, measurementName);
			int comp;
			if (doDescending)
				comp = -Double.compare(m1, m2);
			else
				comp = Double.compare(m1, m2);
			if (comp == 0) {
				if (Double.isNaN(m1) && !Double.isNaN(m2))
					return doDescending ? 1 : -1;
				if (Double.isNaN(m2) && !Double.isNaN(m1))
					return doDescending ? -1 : 1;
				
				if (doDescending)
					comp = t2.getDisplayedName().compareTo(t1.getDisplayedName());
				else
					comp = t1.getDisplayedName().compareTo(t2.getDisplayedName());
			}
			return comp;
		});
	}
	
	

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		
		if (this.imageData != null) {
			imageData.getHierarchy().removePathObjectListener(this);
			this.imageData = null;
		}
		
		// Ensure we aren't holding on a reference to anything
		grid.getItems().clear();
		
		// Don't do anything if not displaying
		if (imageDataNew == null || stage == null || !stage.isShowing())
			return;
		
		// Initialize
		Platform.runLater(() -> initializeData(imageDataNew));
	}
	

	private void initializeData(ImageData<BufferedImage> imageData) {
		if (this.imageData != imageData) {
			if (this.imageData != null)
				this.imageData.getHierarchy().removePathObjectListener(this);
			this.imageData = imageData;
			if (imageData != null) {
				imageData.getHierarchy().addPathObjectListener(this);
			}
		}
		
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			model.setImageData(null, Collections.emptyList());
			grid.getItems().clear();
			return;
		}
		
		// Request all core thumbnails now
		List<TMACoreObject> cores = imageData.getHierarchy().getTMAGrid().getTMACoreList();

		ImageServer<BufferedImage> server = imageData.getServer();
						
		CountDownLatch latch = new CountDownLatch(cores.size());
		for (TMACoreObject core : cores) {
			ROI roi = core.getROI();
			if (roi != null) {
				qupath.submitShortTask(() -> {
					RegionRequest request = createRegionRequest(core);
					if (cache.containsKey(request)) {
						latch.countDown();
						return;
					}

					BufferedImage img;
					try {
						img = server.readBufferedImage(request);
					} catch (IOException e) {
						logger.debug("Unable to get tile for " + request, e);
						latch.countDown();
						return;
					}
					Image imageNew = SwingFXUtils.toFXImage(img, null);
					if (imageNew != null) {
						cache.put(request, imageNew);
//						Platform.runLater(() -> updateGridDisplay());
					}
					latch.countDown();
				});
			} else
				latch.countDown();
		}

		long startTime = System.currentTimeMillis();
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e1) {
			logger.debug("Loaded {} cores in 5 seconds", cores.size() - latch.getCount());
		}
		logger.debug("Countdown complete in {} seconds", (System.currentTimeMillis() - startTime)/1000.0);
		
		
		model.setImageData(imageData, cores);
		backingList.setAll(cores);
		
		String m = measurement.getValue();
		sortCores(backingList, model, m, descending.get());
		filteredList.setPredicate(p -> {
			return !(p.isMissing() || Double.isNaN(model.getNumericValue(p, m)));
		});
		grid.getItems().setAll(filteredList);
		
	}
	
	
	private void initializeGUI() {
		
//		grid.setVerticalCellSpacing(10);
//		grid.setHorizontalCellSpacing(5);
	
		
		ComboBox<CoreDisplaySize> comboDisplaySize = new ComboBox<>();
		comboDisplaySize.getItems().setAll(CoreDisplaySize.values());
		comboDisplaySize.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			grid.imageSize.set(n.getSize());
//			grid.setCellWidth(n.getSize());
//			grid.setCellHeight(n.getSize());
//			updateGridDisplay();
		});
		comboDisplaySize.getSelectionModel().select(CoreDisplaySize.SMALL);
		
		
		ComboBox<String> comboOrder = new ComboBox<>();
		comboOrder.getItems().setAll("Ascending", "Descending");
		comboOrder.getSelectionModel().select("Descending");
		descending.bind(Bindings.createBooleanBinding(() -> "Descending".equals(comboOrder.getSelectionModel().getSelectedItem()), comboOrder.getSelectionModel().selectedItemProperty()));
		

		ComboBox<String> comboMeasurement = new ComboBox<>();
		comboMeasurement.setItems(model.getMeasurementNames());
		if (!comboMeasurement.getItems().isEmpty())
			comboMeasurement.getSelectionModel().select(0);
		
		
		measurement.bind(comboMeasurement.getSelectionModel().selectedItemProperty());
		
		comboOrder.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
//			sortCores(backingList, model, comboMeasurement.getSelectionModel().getSelectedItem(), "Descending".equals(n));
//			filteredList.setPredicate(p -> !(p.isMissing() || Double.isNaN(model.getNumericValue(p, comboMeasurement.getSelectionModel().getSelectedItem()))));
			
			String m = measurement.getValue();
			sortCores(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(p.isMissing() || Double.isNaN(model.getNumericValue(p, m)));
			});

			
			grid.getItems().setAll(filteredList);
		});
		
		comboMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			String m = measurement.getValue();
			sortCores(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(p.isMissing() || Double.isNaN(model.getNumericValue(p, m)));
			});
			
//			sortCores(backingList, model, n, descending.get());
//			filteredList.setPredicate(p -> !(p.isMissing() || Double.isNaN(model.getNumericValue(p, n))));
			grid.getItems().setAll(filteredList);
		});
		
		
		CheckBox cbShowMeasurement = new CheckBox("Show measurement");
		showMeasurement.bind(cbShowMeasurement.selectedProperty());
		showMeasurement.addListener(c -> {
			String m = measurement.getValue();
			sortCores(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(p.isMissing() || Double.isNaN(model.getNumericValue(p, m)));
			});
			grid.getItems().setAll(filteredList);
		}); // Force an update
		
		
		CheckBox cbAnimation = new CheckBox("Animate");
		cbAnimation.setSelected(doAnimate.get());
		doAnimate.bindBidirectional(cbAnimation.selectedProperty());
		
		
		
		BorderPane pane = new BorderPane();
		
		ToolBar paneTop = new ToolBar();
		paneTop.getItems().add(new Label("Measurement"));
		paneTop.getItems().add(comboMeasurement);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(new Label("Order"));
		paneTop.getItems().add(comboOrder);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(cbShowMeasurement);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(new Label("Size"));
		paneTop.getItems().add(comboDisplaySize);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(cbAnimation);
		paneTop.setPadding(new Insets(10, 10, 10, 10));
		for (var item : paneTop.getItems()) {
			if (item instanceof Label) {
				((Label) item).setMinWidth(Label.USE_PREF_SIZE);
			}
		}
//		paneTop.setHgap(5);
//		paneTop.setVgap(5);
		
		comboMeasurement.setMaxWidth(Double.MAX_VALUE);
		comboOrder.setMaxWidth(Double.MAX_VALUE);
//		GridPane.setHgrow(comboMeasurement, Priority.SOMETIMES);
		
		pane.setTop(paneTop);
		
		ScrollPane scrollPane = new ScrollPane(grid);
		scrollPane.setFitToWidth(true);
//		scrollPane.setFitToHeight(true);
		scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		
		pane.setCenter(scrollPane);
//		if (grid.getSkin() != null)
//			((GridViewSkin<?>)grid.getSkin()).updateGridViewItems();
		
		
		Scene scene = new Scene(pane, 640, 480);
		
		stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setTitle("TMA grid view");
		stage.setScene(scene);
		stage.show();
	}
	
	
	
	private String getServerPath() {
		return imageData == null ? null : imageData.getServer().getPath();
	}
	
	private RegionRequest createRegionRequest(final PathObject core) {
		ROI roi = core.getROI();
		double downsample = Math.max(roi.getBoundsWidth(), roi.getBoundsHeight()) / CoreDisplaySize.LARGE.getSize();
//		downsample = Math.round(downsample);
		return RegionRequest.createInstance(getServerPath(), downsample, roi);
	}
	
	
	
	class TMACoreGridCell extends GridCell<TMACoreObject> {

		private ObservableMeasurementTableData model;
		private ObservableValue<String> measurement;
		private ObservableValue<Boolean> showMeasurement;
		
		private Canvas canvas = new Canvas();
		private double preferredSize = 100;
		private double padding;
		private TMACoreObject core;
		
		TMACoreGridCell(
				final ObservableMeasurementTableData model,
				final ObservableValue<String> measurement,
				final ObservableValue<Boolean> showMeasurement,
				final double padding) {
			this.model = model;
			this.measurement = measurement;
			this.showMeasurement = showMeasurement;
			this.padding = padding;
			canvas.setWidth(preferredSize);
			canvas.setHeight(preferredSize);
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		}

		public TMACoreObject getCore() {
			return core;
		}

		@Override
		protected void updateItem(TMACoreObject core, boolean empty) {
			super.updateItem(core, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			this.core = core;
			
			canvas.setWidth(getWidth()-padding*2);
			canvas.setHeight(getHeight()-padding*2-25);
			
//			this.setContentDisplay(ContentDisplay.CENTER);
//			this.setAlignment(Pos.CENTER);
			canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
//			canvas.setOnMouseClicked(e -> {
//				if (e.isShiftDown()) {
//					qupath.getImageData().getHierarchy().getSelectionModel().setSelectedObject(core);
//				}
//			});
			setGraphic(canvas);
			try {
				if (core == null) {
					setText(null);
					return;
				}
				
				RegionRequest request = createRegionRequest(core);
//				System.err.println(request + ": " + cache.containsKey(request));
				Image image = cache.get(request);
				if (image != null) {
					GuiTools.paintImage(canvas, image);
				} else
					logger.trace("No image found for {}", core);
			} catch (Exception e) {
				logger.error("Problem reading thumbnail for core {}: {}", core, e);
//				setGraphic(null);
			}
			
			
			if (measurement.getValue() != null && showMeasurement.getValue()) {
				double value = model.getNumericValue(core, measurement.getValue());
				setText(GeneralTools.formatNumber(value, 2));
				setTextAlignment(TextAlignment.CENTER);
				setAlignment(Pos.BOTTOM_CENTER);
			} else
				setText(" "); // To prevent jumping
			setContentDisplay(ContentDisplay.TOP);
		}

	}

	
	private boolean requestUpdate = false;
	
	private void requestUpdate(ImageData<BufferedImage> imageData) {
		requestUpdate = true;
		Platform.runLater(() -> processUpdateRequest(imageData));
	}
	
	private void processUpdateRequest(ImageData<BufferedImage> imageData) {
		if (!requestUpdate)
			return;
		Platform.runLater(() -> {
			requestUpdate = false;
			initializeData(imageData);
		});
	}


	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (!event.isChanging() && imageData != null && imageData.getHierarchy() == event.getHierarchy() && stage != null && stage.isShowing()) {
			// This is some fairly aggressive updating...
			requestUpdate(imageData);
		}
	}
	
	
	
	class QuPathGridView extends Pane {
		
		private ObservableList<TMACoreObject> list = FXCollections.observableArrayList();
		private WeakHashMap<Node, TranslateTransition> translationMap = new WeakHashMap<>();
		private WeakHashMap<TMACoreObject, Label> nodeMap = new WeakHashMap<>();
		
		private IntegerProperty imageSize = new SimpleIntegerProperty();
		
		QuPathGridView() {
			imageSize.addListener(v -> {
				updateChildren();
			});
			list.addListener(new ListChangeListener<TMACoreObject>() {
				@Override
				public void onChanged(javafx.collections.ListChangeListener.Change<? extends TMACoreObject> c) {
					updateChildren();
				}
			});
			updateChildren();
		}
		
		public ObservableList<TMACoreObject> getItems() {
			return list;
		}
		
		private void updateChildren() {
			List<Node> images = new ArrayList<>();
			for (TMACoreObject pathObject : list) {
				Label	 viewNode = nodeMap.get(pathObject);
				if (viewNode == null) {
					ImageView view = new ImageView();
					view.fitWidthProperty().bind(imageSize);
					view.fitHeightProperty().bind(imageSize);
					if (pathObject.hasROI()) {
						RegionRequest request = createRegionRequest(pathObject);
						Image image = cache.get(request);
						if (image != null)
							view.setImage(image);
					}
					viewNode = new Label("", view);
					Tooltip.install(viewNode, new Tooltip(pathObject.getName()));
					viewNode.setOnMouseClicked(e -> {
						if (imageData != null) {
							imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
							if (e.getClickCount() > 1 && pathObject.hasROI()) {
								ROI roi = pathObject.getROI();
								if (roi != null && qupath.getViewer().getImageData() == imageData)
									qupath.getViewer().setCenterPixelLocation(roi.getCentroidX(), roi.getCentroidY());
							}
						}
					});
					nodeMap.put(pathObject, viewNode);
				}
				images.add(viewNode);
			}
			updateMeasurementText();
			getChildren().setAll(images);
			
//			Parent p = getParent();
//			while (p != null && !(p instanceof ScrollPane))
//				p = p.getParent();
//			if (p != null)
//				((ScrollPane)p)
		}
		
		void updateMeasurementText() {
			String m = measurement == null ? null : measurement.get();
			for (Entry<TMACoreObject, Label> entry : nodeMap.entrySet()) {
				if (m == null || !showMeasurement.get())
					entry.getValue().setText(" ");
				else {
					double val = model.getNumericValue(entry.getKey(), m);
					entry.getValue().setText(GeneralTools.formatNumber(val, 3));
				}
				entry.getValue().setContentDisplay(ContentDisplay.TOP);
			}
		}
		
		
		@Override
		protected void layoutChildren() {
			super.layoutChildren();
			int padding = 5;
			
			int w = (int)getWidth();
//			int h = (int)getHeight();
			int dx = imageSize.get() + padding;
			int nx = (int)Math.floor(w / dx);
			int spaceX = (int)((w - (dx) * nx) / (nx)); // Space to divide equally
			
			int x = spaceX/2;
			int y = padding;
			for (Node node : getChildren()) {
				if (x + dx > w) {
					x = spaceX/2;
					if (node instanceof Label)
						y += ((Label)node).getHeight() + spaceX + 2;
					else
						y += imageSize.get() + spaceX + 2;
				}
				
//				if (node.getEffect() == null)
					node.setEffect(new DropShadow(8, -2, 2, Color.GRAY));
				
				if (doAnimate.get()) {
					TranslateTransition translate = translationMap.get(node);
					boolean doChanges = false;
					if (translate == null) {
						translate = new TranslateTransition(Duration.seconds(0.5));
						translate.setNode(node);
						translationMap.put(node, translate);
						doChanges = true;
					} else {
						if (!GeneralTools.almostTheSame(x, translate.getToX(), 0.001)
								|| !GeneralTools.almostTheSame(y, translate.getToY(), 0.001)) {
							translate.stop();
							translate.setDuration(Duration.seconds(0.5));
							doChanges = true;
						}
					}
					if (doChanges) {
						translate.setInterpolator(Interpolator.EASE_BOTH);
						translate.setFromX(node.getTranslateX());
						translate.setFromY(node.getTranslateY());
						translate.setToX(x);
						translate.setToY(y);
						translate.playFromStart();
					}
				} else {
					node.setTranslateX(x);
					node.setTranslateY(y);
				}
				
//				node.setLayoutX(x);
//				node.setLayoutY(y);
				x += (dx + spaceX);
			}
			
//			setHeight(y);
			setHeight(y + dx);
			setPrefHeight(y + dx);
		}
		
		
		
		
	}
	
	

}
