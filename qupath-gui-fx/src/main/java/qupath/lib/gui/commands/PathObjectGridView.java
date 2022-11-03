/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;
import java.util.WeakHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.interfaces.ROI;

/**
 * Grid display of objects.
 * <p>
 * Previously this was {@code TMAGridView}, but it was generalized for v0.4.0 to support other kinds of object.
 * <p>
 * This requires cores in memory, so does not scale wonderfully... but it can be quite useful for individual slides.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectGridView implements ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectGridView.class);
	
	private QuPathGUI qupath;
	private Stage stage;
	
	private StringProperty title = new SimpleStringProperty("Object grid view");
	
	private QuPathGridView grid = new QuPathGridView();
	
	private ComboBox<String> comboMeasurement;
	
	private ObservableList<PathObject> backingList = FXCollections.observableArrayList();
	private FilteredList<PathObject> filteredList = new FilteredList<>(backingList);
	
	private ObservableMeasurementTableData model = new ObservableMeasurementTableData();

	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	
	
	private StringProperty measurement = new SimpleStringProperty();
	private BooleanProperty showMeasurement = new SimpleBooleanProperty(true);
	private BooleanProperty descending = new SimpleBooleanProperty(false);
	private BooleanProperty doAnimate = new SimpleBooleanProperty(true);
	
	private Function<PathObjectHierarchy, Collection<? extends PathObject>> objectExtractor;

	
	public static enum GridDisplaySize {
			TINY("Tiny", 60),
			SMALL("Small", 100),
			MEDIUM("Medium", 200),
			LARGE("Large", 300);
		
		private String name;
		private int size;
		
		GridDisplaySize(final String name, final int size) {
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
	

	private PathObjectGridView(final QuPathGUI qupath, final Function<PathObjectHierarchy, Collection<? extends PathObject>> extractor) {
		this.qupath = qupath;
		this.objectExtractor = extractor;
		this.imageDataProperty().bind(qupath.imageDataProperty());
		this.imageDataProperty().addListener(this);
	}
	
	/**
	 * Create a grid view for a custom object extractor.
	 * @param qupath QuPath instance
	 * @param objectExtractor function to select the objects to display
	 * @return
	 */
	public static PathObjectGridView createGridView(QuPathGUI qupath, Function<PathObjectHierarchy, Collection<? extends PathObject>> objectExtractor) {
		return new PathObjectGridView(qupath, objectExtractor);
	}
	
	/**
	 * Create a grid view for TMA core objects.
	 * @param qupath
	 * @return
	 */
	public static PathObjectGridView createTmaCoreView(QuPathGUI qupath) {
		var view = createGridView(qupath, PathObjectGridView::getTmaCores);
		view.title.set("TMA core grid view");
		return view;
	}

	/**
	 * Create a grid view for annotations.
	 * @param qupath
	 * @return
	 */
	public static PathObjectGridView createAnnotationView(QuPathGUI qupath) {
		var view = createGridView(qupath, PathObjectGridView::getAnnotations);
		view.title.set("Annotation object grid view");
		return view;
	}

	
	private static List<PathObject> getTmaCores(PathObjectHierarchy hierarchy) {
		var grid = hierarchy == null ? null : hierarchy.getTMAGrid();
		if (grid != null)
			return new ArrayList<>(grid.getTMACoreList());
		else
			return Collections.emptyList();
	}
	
	private static List<PathObject> getAnnotations(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			return new ArrayList<>(hierarchy.getAnnotationObjects());
		else
			return Collections.emptyList();
	}
	
	/**
	 * Get the stage used to show the grid view.
	 * @return
	 */
	public Stage getStage() {
		if (stage == null)
			initializeGUI();
		return stage;
	}
	
	/**
	 * Create the stage and show the grid view.
	 */
	public void show() {
		var stage = getStage();
		if (!stage.isShowing())
			stage.show();
	}
	
	/**
	 * Refresh the data in the grid view
	 */
	public void refresh() {
		initializeData(qupath.getImageData());		
	}
	
	
	public ObjectProperty<ImageData<BufferedImage>> imageDataProperty() {
		return imageDataProperty;
	}
	
	
	private static void sortPathObjects(final ObservableList<? extends PathObject> cores, final ObservableMeasurementTableData model, final String measurementName, final boolean doDescending) {
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
		
		var currentImageData = imageDataProperty.get();
		if (currentImageData != null) {
			currentImageData.getHierarchy().removeListener(this);
			currentImageData = null;
		}
		
		// Ensure we aren't holding on a reference to anything
		grid.getItems().clear();
		
		// Listen for changes
		if (imageDataNew != null)
			imageDataNew.getHierarchy().addListener(this);
		
		// Don't do anything if not displaying
		if (imageDataNew == null || stage == null || !stage.isShowing())
			return;
		
		// Initialize
		Platform.runLater(() -> initializeData(imageDataNew));
	}
	

	private void initializeData(ImageData<BufferedImage> imageData) {
		
		List<PathObject> pathObjects = imageData == null ? Collections.emptyList() : new ArrayList<>(objectExtractor.apply(imageData.getHierarchy()));
		
		if (imageData == null || pathObjects.isEmpty()) {
			model.setImageData(null, Collections.emptyList());
			grid.getItems().clear();
			return;
		}
		
		model.setImageData(imageData, pathObjects);
		backingList.setAll(pathObjects);
		
		String m = measurement.getValue();
		sortPathObjects(backingList, model, m, descending.get());
		filteredList.setPredicate(p -> {
			return !(isMissingCore(p) || Double.isNaN(model.getNumericValue(p, m)));
		});
		grid.getItems().setAll(filteredList);
		
		// Select the first measurement if necessary
		var names = model.getMeasurementNames();
		if (m == null || !names.contains(m)) {
			if (!comboMeasurement.getItems().isEmpty())
				comboMeasurement.getSelectionModel().selectFirst();
		}
		
	}
	
	
	private void initializeGUI() {
		
//		grid.setVerticalCellSpacing(10);
//		grid.setHorizontalCellSpacing(5);
	
		
		ComboBox<GridDisplaySize> comboDisplaySize = new ComboBox<>();
		comboDisplaySize.getItems().setAll(GridDisplaySize.values());
		comboDisplaySize.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			grid.imageSize.set(n.getSize());
//			grid.setCellWidth(n.getSize());
//			grid.setCellHeight(n.getSize());
//			updateGridDisplay();
		});
		comboDisplaySize.getSelectionModel().select(GridDisplaySize.SMALL);
		
		
		ComboBox<String> comboOrder = new ComboBox<>();
		comboOrder.getItems().setAll("Ascending", "Descending");
		comboOrder.getSelectionModel().select("Descending");
		descending.bind(Bindings.createBooleanBinding(() -> "Descending".equals(comboOrder.getSelectionModel().getSelectedItem()), comboOrder.getSelectionModel().selectedItemProperty()));
		

		comboMeasurement = new ComboBox<>();
		comboMeasurement.setItems(model.getMeasurementNames());
		if (!comboMeasurement.getItems().isEmpty())
			comboMeasurement.getSelectionModel().select(0);
		
		
		measurement.bind(comboMeasurement.getSelectionModel().selectedItemProperty());
		
		comboOrder.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			String m = measurement.getValue();
			sortPathObjects(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(isMissingCore(p) || Double.isNaN(model.getNumericValue(p, m)));
			});

			
			grid.getItems().setAll(filteredList);
		});
		
		comboMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			String m = measurement.getValue();
			sortPathObjects(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(isMissingCore(p) || Double.isNaN(model.getNumericValue(p, m)));
			});
			grid.getItems().setAll(filteredList);
		});
		
		
		CheckBox cbShowMeasurement = new CheckBox("Show measurement");
		showMeasurement.bind(cbShowMeasurement.selectedProperty());
		showMeasurement.addListener(c -> {
			String m = measurement.getValue();
			sortPathObjects(backingList, model, m, descending.get());
			filteredList.setPredicate(p -> {
				return m == null || !(isMissingCore(p) || Double.isNaN(model.getNumericValue(p, m)));
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
		
		var scrollPane = new ScrollPane(grid);
		scrollPane.setFitToWidth(true);
//		scrollPane.setFitToHeight(true);
		scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		
		pane.setCenter(scrollPane);
//		if (grid.getSkin() != null)
//			((GridViewSkin<?>)grid.getSkin()).updateGridViewItems();
		
		
		Scene scene = new Scene(pane, 640, 480);
		
		stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.titleProperty().bindBidirectional(title);
		stage.setScene(scene);
		stage.setOnShowing(e -> refresh());
		stage.show();
	}
	
	
	/**
	 * Check if an object is a TMA core flagged as missing
	 * @param pathObject
	 * @return
	 */
	private static boolean isMissingCore(PathObject pathObject) {
		if (pathObject instanceof TMACoreObject)
			return ((TMACoreObject)pathObject).isMissing();
		return false;
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
		var imageData = imageDataProperty.get();
		if (!event.isChanging() && imageData != null && imageData.getHierarchy() == event.getHierarchy() && stage != null && stage.isShowing()) {
			// This is some fairly aggressive updating...
			requestUpdate(imageData);
		}
	}
	
	
	
	class QuPathGridView extends StackPane {
		
		private ObservableList<PathObject> list = FXCollections.observableArrayList();
		private WeakHashMap<Node, TranslateTransition> translationMap = new WeakHashMap<>();
		private WeakHashMap<PathObject, Label> nodeMap = new WeakHashMap<>();
		
		private IntegerProperty imageSize = new SimpleIntegerProperty();
		
		private Text textEmpty = new Text("No objects available!");
		
		QuPathGridView() {
			imageSize.addListener(v -> {
				updateChildren();
			});
			list.addListener(new ListChangeListener<PathObject>() {
				@Override
				public void onChanged(javafx.collections.ListChangeListener.Change<? extends PathObject> c) {
					updateChildren();
				}
			});
			updateChildren();
			textEmpty.setStyle("-fx-fill: -fx-text-base-color;");
			StackPane.setAlignment(textEmpty, Pos.CENTER);
		}
		
		public ObservableList<PathObject> getItems() {
			return list;
		}
		
		private void updateChildren() {
			if (list.isEmpty()) {
				getChildren().setAll(textEmpty);
				return;
			}
			List<Node> images = new ArrayList<>();
			for (PathObject pathObject : list) {
				Label viewNode = nodeMap.get(pathObject);
				if (viewNode == null) {
					
					var painter = PathObjectImageManagers.createImageViewPainter(
							 qupath.getViewer(), imageDataProperty.get().getServer(), true, 
							 ForkJoinPool.commonPool());

					var imageView = painter.getNode();
					imageView.fitWidthProperty().bind(imageSize);
					imageView.fitHeightProperty().bind(imageSize);
					
					painter.setPathObject(pathObject);
					
					viewNode = new Label("", imageView);
					StackPane.setAlignment(viewNode, Pos.TOP_LEFT);
					
					Tooltip.install(viewNode, new Tooltip(pathObject.getName()));
					viewNode.setOnMouseClicked(e -> {
						var imageData = imageDataProperty.get();
						if (imageData != null) {
							imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
							if (e.getClickCount() > 1 && pathObject.hasROI()) {
								ROI roi = pathObject.getROI();
								if (roi != null && qupath.getViewer().getImageData() == imageData)
									qupath.getViewer().setCenterPixelLocation(roi.getCentroidX(), roi.getCentroidY());
							}
						}
					});
					
					viewNode.setEffect(new DropShadow(8, -2, 2, Color.GRAY));
					nodeMap.put(pathObject, viewNode);
				}
				images.add(viewNode);
			}
			updateMeasurementText();
			getChildren().setAll(images);
		}
		
		void updateMeasurementText() {
			String m = measurement == null ? null : measurement.get();
			for (Entry<PathObject, Label> entry : nodeMap.entrySet()) {
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
			
			if (list.isEmpty()) {
				setHeight(200);
				setPrefHeight(200);
				return;
			}
			
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
