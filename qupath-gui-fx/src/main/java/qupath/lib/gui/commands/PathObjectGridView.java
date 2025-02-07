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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PathObjectImageViewers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

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
	
	private final QuPathGUI qupath;
	private Stage stage;
	
	private final StringProperty title = new SimpleStringProperty(QuPathResources.getString("GridView.title"));
	
	private final QuPathGridView grid = new QuPathGridView();
	
	private ComboBox<String> comboSortBy;
	
	private final ObservableList<PathObject> backingList = FXCollections.observableArrayList();
	private final FilteredList<PathObject> filteredList = new FilteredList<>(backingList);
	
	private final ObservableMeasurementTableData model = new ObservableMeasurementTableData();

	private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	private final StringProperty measurement = new SimpleStringProperty();
	private final BooleanProperty showMeasurement = new SimpleBooleanProperty(true);
	private final BooleanProperty descending = new SimpleBooleanProperty(false);
	private final BooleanProperty doAnimate = new SimpleBooleanProperty(true);

	private final Function<PathObjectHierarchy, Collection<? extends PathObject>> objectExtractor;
	private ObservableList<PathClass> selectedClasses;


	public static enum GridDisplaySize {
			TINY("Tiny", 60),
			SMALL("Small", 100),
			MEDIUM("Medium", 200),
			LARGE("Large", 300);
		
		private final String name;
		private final int size;
		
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
		
	}
	

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
	 */
	public static PathObjectGridView createGridView(QuPathGUI qupath, Function<PathObjectHierarchy, Collection<? extends PathObject>> objectExtractor) {
		return new PathObjectGridView(qupath, objectExtractor);
	}
	
	/**
	 * Create a grid view for TMA core objects.
	 */
	public static PathObjectGridView createTmaCoreView(QuPathGUI qupath) {
		var view = createGridView(qupath, PathObjectGridView::getTmaCores);
		view.title.set(QuPathResources.getString("GridView.TMAGridView"));
		return view;
	}

	/**
	 * Create a grid view for annotations.
	 */
	public static PathObjectGridView createAnnotationView(QuPathGUI qupath) {
		var view = createGridView(qupath, PathObjectGridView::getAnnotations);
		view.title.set(QuPathResources.getString("GridView.AnnotationGridView"));
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
	 * @return The stage
	 */
	public Stage getStage() {
		if (stage == null) {
			initializeGUI();
			var owner = stage.getOwner();
			if (owner != null) {
				var screen = FXUtils.getScreen(owner);
				if (screen != null) {
					stage.setWidth(Math.min(850, screen.getVisualBounds().getWidth() * 0.8));
					stage.centerOnScreen();
				}
			}
		}
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
		if (measurementName == null) return;

		// special case for eg A-1, A-10 for TMA cores
		if (measurementName.equals(QuPathResources.getString("GridView.name"))) {
			GeneralTools.smartStringSort(cores, PathObject::getDisplayedName);
			if (!doDescending) {
				Collections.reverse(cores);
			}
			return;
		}

		Comparator<PathObject> sorter;
		if (measurementName.equals(QuPathResources.getString("GridView.classification"))) {
			sorter = (po1, po2) -> {
				Comparator<PathObject> comp = Comparator.comparing(po -> po.getPathClass() == null ? "Unclassified" : po.getPathClass().toString());
				return comp.compare(po1, po2);
			};
		} else {
			// if it's a measurement, then we're numeric sorting
			sorter = (po1, po2) -> {
				double m1 = model.getNumericValue(po1, measurementName);
				double m2 = model.getNumericValue(po2, measurementName);
				int comp;
				comp = Double.compare(m1, m2);
				// resolve ties by checking missingness, and then names
				if (comp == 0) {
					// todo: should missing values always be last?
					if (Double.isNaN(m1) && !Double.isNaN(m2))
						return 1;
					if (Double.isNaN(m2) && !Double.isNaN(m1))
						return -1;
					comp = po1.getDisplayedName().compareTo(po2.getDisplayedName());
				}
				return comp;
			};
		}
		if (doDescending) {
			sorter = sorter.reversed();
		}
		cores.sort(sorter);
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
		sortAndFilter();

		// Select the first measurement if necessary
		var names = model.getMeasurementNames();
		if (m == null || !names.contains(m)) {
			if (!comboSortBy.getItems().isEmpty())
				comboSortBy.getSelectionModel().selectFirst();
		}
		
	}
	
	
	private void initializeGUI() {

		ComboBox<GridDisplaySize> comboDisplaySize = new ComboBox<>();
		comboDisplaySize.getItems().setAll(GridDisplaySize.values());
		comboDisplaySize.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			grid.imageSize.set(n.getSize());
		});
		comboDisplaySize.getSelectionModel().select(GridDisplaySize.SMALL);
		
		
		ComboBox<String> comboOrder = new ComboBox<>();
		comboOrder.getItems().setAll(
				QuPathResources.getString("GridView.ascending"),
				QuPathResources.getString("GridView.descending"));
		comboOrder.getSelectionModel().select(QuPathResources.getString("GridView.descending"));
		descending.bind(Bindings.createBooleanBinding(() ->
						QuPathResources.getString("GridView.descending").equals(comboOrder.getSelectionModel().getSelectedItem()),
				comboOrder.getSelectionModel().selectedItemProperty()));
		descending.addListener((v, o, n) -> sortAndFilter());
		comboOrder.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> sortAndFilter());

		comboSortBy = new ComboBox<>();
		// todo: never needed now because we always have class?
		comboSortBy.setPlaceholder(createPlaceholderText(QuPathResources.getString("GridView.noMeasurements")));
		comboSortBy.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> sortAndFilter());

		var measureNames = model.getMeasurementNames();
		ObservableList<String> measureList = FXCollections.observableArrayList(measureNames);
		measureNames.addListener((ListChangeListener<String>) c -> {
            measureList.clear();
            measureList.add(QuPathResources.getString("GridView.classification"));
            measureList.addAll(measureNames);
        });
		measureList.add(QuPathResources.getString("GridView.classification"));
		measureList.add(QuPathResources.getString("GridView.name"));
		comboSortBy.setItems(measureList);
		if (!comboSortBy.getItems().isEmpty())
			comboSortBy.getSelectionModel().select(0);

		measurement.bind(comboSortBy.getSelectionModel().selectedItemProperty());

		CheckBox cbShowMeasurement = new CheckBox(QuPathResources.getString("GridView.showValue"));
		showMeasurement.bind(cbShowMeasurement.selectedProperty());
		showMeasurement.addListener(c -> updateMeasurement()); // Force an update

		CheckBox cbAnimation = new CheckBox(QuPathResources.getString("GridView.animate"));
		cbAnimation.setSelected(doAnimate.get());
		doAnimate.bindBidirectional(cbAnimation.selectedProperty());

		CheckComboBox<PathClass> classComboBox = new CheckComboBox<>();
		selectedClasses = classComboBox.getCheckModel().getCheckedItems();
		selectedClasses.addListener((ListChangeListener<PathClass>) c -> sortAndFilter());
		FXUtils.installSelectAllOrNoneMenu(classComboBox);
		classComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<PathClass>) c -> {
			classComboBox.setTitle(getCheckComboBoxText(classComboBox));
		});


		updateClasses(classComboBox);
		qupath.getImageData().getHierarchy().addListener(event -> updateClasses(classComboBox));

		classComboBox.getCheckModel().checkAll();

		BorderPane pane = new BorderPane();
		
		ToolBar paneTop = new ToolBar();
		paneTop.getItems().add(new Label(QuPathResources.getString("GridView.sortBy")));
		paneTop.getItems().add(comboSortBy);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(new Label(QuPathResources.getString("GridView.order")));
		paneTop.getItems().add(comboOrder);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(cbShowMeasurement);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(new Label(QuPathResources.getString("GridView.size")));
		paneTop.getItems().add(comboDisplaySize);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(new Label(QuPathResources.getString("GridView.classes")));
		paneTop.getItems().add(classComboBox);
		paneTop.getItems().add(new Separator(Orientation.VERTICAL));
		paneTop.getItems().add(cbAnimation);
		paneTop.setPadding(new Insets(10, 10, 10, 10));
		for (var item : paneTop.getItems()) {
			if (item instanceof Label) {
				((Label) item).setMinWidth(Label.USE_PREF_SIZE);
			}
		}

		comboSortBy.setMaxWidth(Double.MAX_VALUE);
		comboOrder.setMaxWidth(Double.MAX_VALUE);

		pane.setTop(paneTop);
		
		var scrollPane = new ScrollPane(grid);
		scrollPane.setFitToWidth(true);
		scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		
		pane.setCenter(scrollPane);

		Scene scene = new Scene(pane, 640, 480);
		
		stage = new Stage();
		FXUtils.addCloseWindowShortcuts(stage);
		stage.initOwner(qupath.getStage());
		stage.titleProperty().bindBidirectional(title);
		stage.setScene(scene);
		stage.setOnShowing(e -> refresh());
		stage.show();
	}

	private void updateClasses(CheckComboBox<PathClass> classComboBox) {
		// if a new class is added to the hierarchy, then update the list but leave the set of checked classes unchanged
		var previouslyChecked = new ArrayList<>(classComboBox.getCheckModel().getCheckedItems());
		List<PathClass> representedClasses = qupath.getImageData().getHierarchy().getFlattenedObjectList(null).stream()
				.filter(p -> !p.isRootObject())
				.map(PathObject::getPathClass)
				.filter(p -> p != null && p != PathClass.NULL_CLASS)
				.distinct()
				.collect(Collectors.toList());
		representedClasses.add(PathClass.NULL_CLASS);
		classComboBox.getItems().clear();
		classComboBox.getItems().addAll(representedClasses);
		classComboBox.getCheckModel().clearChecks();
		int[] inds = previouslyChecked.stream().mapToInt(representedClasses::indexOf).toArray();
		classComboBox.getCheckModel().checkIndices(inds);
	}


	private static String getCheckComboBoxText(CheckComboBox<PathClass> comboBox) {
		int n = comboBox.getCheckModel().getCheckedItems().stream()
				.filter(Objects::nonNull)
				.toList()
				.size();
		if (n == 0)
			return QuPathResources.getString("GridView.noClassSelected");
		if (n == 1)
			return comboBox.getCheckModel().getCheckedItems().getFirst().toString();
		return String.format(QuPathResources.getString("GridView.nClassSelected"), n);
	}

	private void updateMeasurement() {
		sortAndFilter();
	}

	private void sortAndFilter() {
		String m = measurement.getValue();
		sortPathObjects(backingList, model, m, descending.get());
		filteredList.setPredicate(p ->
				// no measurement selected, we're going by classification or name, or it's not missing, then keep
				(m == null
						|| m.equals(QuPathResources.getString("GridView.classification"))
						|| m.equals(QuPathResources.getString("GridView.name"))
						|| !isMissingCore(p))
						// pathclass is present and selected, or missing and we're showing unclassifier
						&& (selectedClasses.contains(p.getPathClass()) || (p.getPathClass() == null && selectedClasses.contains(PathClass.NULL_CLASS)))
		);
		Runnable r = () -> grid.getItems().setAll(filteredList);
		if (Platform.isFxApplicationThread()) {
			r.run();
		} else {
			Platform.runLater(r);
		}
	}


	/**
	 * Check if an object is a TMA core flagged as missing
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


	private static Text createPlaceholderText(String text) {
		return GuiTools.createPlaceholderText(text);
	}
	
	class QuPathGridView extends StackPane {
		
		private final ObservableList<PathObject> list = FXCollections.observableArrayList();
		private final WeakHashMap<Node, TranslateTransition> translationMap = new WeakHashMap<>();
		private final WeakHashMap<PathObject, Label> nodeMap = new WeakHashMap<>();
		
		private final IntegerProperty imageSize = new SimpleIntegerProperty();
		
		private final Text textEmpty = createPlaceholderText(QuPathResources.getString("GridView.noObjectsAvailable"));
		
		QuPathGridView() {
			imageSize.addListener(v -> updateChildren());
			list.addListener((ListChangeListener<PathObject>) c -> updateChildren());
			updateChildren();
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
				Label viewNode = nodeMap.computeIfAbsent(pathObject, po -> getLabel(pathObject));
				images.add(viewNode);
			}
			updateMeasurementText();
			getChildren().setAll(images);
		}

		private Label getLabel(PathObject pathObject) {
			var painter = PathObjectImageViewers.createImageViewer(
					qupath.getViewer(), imageDataProperty.get().getServer(), true);

			var imageView = painter.getNode();
			imageView.fitWidthProperty().bind(imageSize);
			imageView.fitHeightProperty().bind(imageSize);

			painter.setItem(pathObject);

			var out = new Label("", imageView);
			StackPane.setAlignment(out, Pos.TOP_LEFT);

			Tooltip.install(out, new Tooltip(pathObject.getName()));
			out.setOnMouseClicked(e -> {
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
			return out;
		}

		void updateMeasurementText() {
			String m = measurement == null ? null : measurement.get();
			for (Entry<PathObject, Label> entry : nodeMap.entrySet()) {
				if (m == null || !showMeasurement.get())
					entry.getValue().setText(" ");
				else {
					if (m.equals(QuPathResources.getString("GridView.classification"))) {
						PathClass pc = entry.getKey().getPathClass();
						String text = pc == null ? PathClass.getNullClass().toString() : pc.toString();
						entry.getValue().setText(text);
					} else if (m.equals(QuPathResources.getString("GridView.name"))) {
						entry.getValue().setText(entry.getKey().getDisplayedName());
					} else {
						double val = model.getNumericValue(entry.getKey(), m);
						entry.getValue().setText(GeneralTools.formatNumber(val, 3));
					}
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
			int dxy = imageSize.get() + padding;
			int w = Math.max(dxy, (int)getWidth());
			int nx = (int) (double) (w / dxy);
			nx = Math.max(1, nx);
			int spaceX = (w - dxy * nx) / nx; // Space to divide equally
			
			int x = spaceX/2;
			int y = padding;

			double h = imageSize.get();
			for (Node node : getChildren()) {
				if (node instanceof Label label) {
					h = label.getHeight();
				}
				if (x + dxy > w) {
					x = spaceX/2;
					y += (int) (h + spaceX + 2);
				}
				
				if (doAnimate.get()) {
					TranslateTransition translate = translationMap.get(node);
					boolean doChanges = false;
					if (translate == null) {
						translate = new TranslateTransition(Duration.seconds(0.25));
						translate.setNode(node);
						translationMap.put(node, translate);
						doChanges = true;
					} else {
						if (!GeneralTools.almostTheSame(x, translate.getToX(), 0.001)
								|| !GeneralTools.almostTheSame(y, translate.getToY(), 0.001)) {
							translate.stop();
							translate.setDuration(Duration.seconds(0.25));
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
				x += dxy + spaceX;
			}
			setHeight(y + h + padding);
			setPrefHeight(y + h + padding);
		}

	}

}
