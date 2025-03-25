/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.LongBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.InfoMessage;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Help window providing context-dependent help.
 * 
 * @author Pete Bankhead
 */
public class ContextHelpViewer {

	private static Map<QuPathGUI, ContextHelpViewer> INSTANCES = new ConcurrentHashMap<>();

	private StringProperty title = QuPathResources.getLocalizedResourceManager().createProperty("ContextHelp.title");
	private QuPathGUI qupath;
	
	private int iconSize = 16;

	private Stage stage;
	private ObservableList<Window> windows;
	private EventHandler<MouseEvent> handler = this::handleMouseMove;

	private StringProperty defaultText = QuPathResources.getLocalizedResourceManager().createProperty("ContextHelp.defaultHelpText");
	private StringProperty helpText = new SimpleStringProperty(defaultText.get());

	private Label label;

	private Node lastNode;
	private VBox vbox;
	
	private ObservableList<HelpListEntry> allHelpEntries = FXCollections.observableArrayList(
			(HelpListEntry e) -> new Observable[] {e.visibleProperty()});

	private LongBinding warningCount = Bindings.createLongBinding(() ->
		allHelpEntries.stream().filter(e -> e.visibleProperty().get() && e.getType() == HelpType.WARNING || e.getType() == HelpType.ERROR).count(),
		allHelpEntries);

	private LongBinding infoCount = Bindings.createLongBinding(() ->
					allHelpEntries.stream().filter(e -> e.visibleProperty().get() && e.getType() == HelpType.INFO).count(),
			allHelpEntries);

	private BooleanBinding hasWarnings = warningCount.greaterThan(0);
	private BooleanBinding hasInfo = infoCount.greaterThan(0);

	private InfoMessage warningMessage = InfoMessage.warning(warningCount);
	private InfoMessage infoMessage = InfoMessage.info(infoCount);

	private ObjectExpression<InfoMessage> infoOrWarningMessage = Bindings.createObjectBinding(() -> {
		if (hasWarnings.get())
			return warningMessage;
		else if (hasInfo.get())
			return infoMessage;
		else
			return null;
	}, hasWarnings, hasInfo);

	private ObjectProperty<ImageData<?>> imageDataProperty = new SimpleObjectProperty<>();

	private PropertyChangeListener imageDataPropertyChange = this::imageDataPropertyChange;

	private ObjectProperty<PixelCalibration> currentPixelSize = new SimpleObjectProperty<>();
	private BooleanBinding pixelCalibrationUnset = imageDataProperty.isNotNull().and(currentPixelSize.isNull()
			.or(currentPixelSize.isEqualTo(PixelCalibration.getDefaultInstance())));


	private static double dpiToMicrons(double dpi) {
		return 25400 / dpi;
	}

	private static Set<Double> typicalDpiPixelSizes = Set.of(72, 96, 120, 144, 300)
			.stream()
			.map(ContextHelpViewer::dpiToMicrons)
			.collect(Collectors.toSet());

	// Pixel size is a suspicion value, probably indicating dots per inch
	private BooleanBinding pixelSizeLikelyDpi = imageDataProperty.isNotNull().and(currentPixelSize.isNotNull())
			.and(Bindings.createBooleanBinding(() -> {
				var pixelSize = currentPixelSize.get();
				if (pixelSize == null)
					return false;
				if (pixelSize.getPixelWidthUnit() != null && pixelSize.getPixelWidthUnit().startsWith("inch"))
					return true;
				if (pixelSize.hasPixelSizeMicrons()) {
					for (double d : typicalDpiPixelSizes) {
						if (GeneralTools.almostTheSame(d, pixelSize.getAveragedPixelSizeMicrons(), 1e-3))
							return true;
					}
				}
				return false;
					}, currentPixelSize));

	private ObjectProperty<ImageData.ImageType> currentImageType = new SimpleObjectProperty<>();
	private BooleanBinding imageTypeUnset = imageDataProperty.isNotNull().and(currentImageType.isNull()
			.or(currentImageType.isEqualTo(ImageData.ImageType.UNSET)));

	private BooleanProperty largeNonPyramidalImage = new SimpleBooleanProperty(false);

	private BooleanProperty classificationAndDefaultObjectColorsSimilar = new SimpleBooleanProperty(false);

	private ContextHelpViewer(QuPathGUI qupath) {
		this.qupath = qupath;
		this.imageDataProperty.addListener(this::imageDataChanged);
		this.imageDataProperty.bind(qupath.imageDataProperty());
		
		initializeWindowListeners();

		label = createHelpTextLabel();
		
		vbox = new VBox();
		var scrollPane = new ScrollPane(vbox);
		scrollPane.setFitToWidth(true);
		
		var splitPane = new SplitPane(label, scrollPane);
		SplitPane.setResizableWithParent(label, Boolean.FALSE);
		splitPane.setOrientation(Orientation.VERTICAL);
		
		createHelpLabels();

		stage = createStage(new Scene(splitPane));
	}

	private void imageDataChanged(ObservableValue<? extends ImageData<?>> observable,
								  ImageData<?> oldValue, ImageData<?> newValue) {
		if (oldValue != null)
			oldValue.removePropertyChangeListener(imageDataPropertyChange);
		if (newValue != null) {
			newValue.addPropertyChangeListener(imageDataPropertyChange);
			currentPixelSize.set(newValue.getServer().getPixelCalibration());
			currentImageType.set(newValue.getImageType());
		} else {
			currentPixelSize.set(null);
			currentImageType.set(null);
		}
		updateLargeNonPyramidalProperty();
	}

	/**
	 * Property change listener for the current image data.
	 * @param evt
	 */
	private void imageDataPropertyChange(PropertyChangeEvent evt) {
		var imageData = imageDataProperty.get();
		if (imageData != null) {
			currentPixelSize.set(imageData.getServer().getPixelCalibration());
			currentImageType.set(imageData.getImageType());
		}
	}

	private void updateSimilarClassificationColors() {
		var pathClasses = qupath.getAvailablePathClasses();
		var defaultColor = PathPrefs.colorDefaultObjectsProperty();
		for (var pathClass : pathClasses) {
			if (similarColors(pathClass.getColor(), defaultColor.get())) {
				classificationAndDefaultObjectColorsSimilar.set(true);
				return;
			}
		}
		classificationAndDefaultObjectColorsSimilar.set(false);
	}

	private static boolean similarColors(Integer rgba1, Integer rgba2) {
		if (rgba1 == null || rgba2 == null)
			return false;
		double tol = 10.0;
		if (Math.abs(ColorTools.red(rgba1) - ColorTools.red(rgba2)) > tol)
			return false;
		if (Math.abs(ColorTools.green(rgba1) - ColorTools.green(rgba2)) > tol)
			return false;
		if (Math.abs(ColorTools.blue(rgba1) - ColorTools.blue(rgba2)) > tol)
			return false;
		return true;
	}

	private void updateLargeNonPyramidalProperty() {
		var imageData = imageDataProperty.get();
		if (imageData == null)
			largeNonPyramidalImage.set(false);
		else {
			var server = imageData.getServer();
			if (server.nResolutions() == 1 && Math.max(server.getWidth(), server.getHeight()) > 10_000) {
				largeNonPyramidalImage.set(true);
			} else {
				largeNonPyramidalImage.set(false);
			}
		}
	}
	
	private void initializeWindowListeners() {
		windows = Window.getWindows().filtered(this::filterWindows);
		for (var w : windows) {
			addMouseListener(w);		
		}
		windows.addListener(this::handleWindowChange);
	}
	
	private Stage createStage(Scene scene) {
		var stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setResizable(true);
		stage.titleProperty().bind(title);
		stage.setWidth(300);
		stage.setHeight(400);
		stage.setScene(scene);
		FXUtils.addCloseWindowShortcuts(stage);
		return stage;
	}
	
	
	private void createHelpLabels() {
		for (var entry : createHelpEntries()) {
			allHelpEntries.add(entry);
			createHelpLabel(entry);
		}
	}
	
	private List<HelpListEntry> createHelpEntries() {
		return Arrays.asList(
				createUnseenErrors(),
				createLargeNonPyramidal(),
				createPixelSizeMissing(),
				createPixelSizeLikelyDpi(),
				createImageTypeMissing(),
				createSelectionModelEntry(),
				createAnnotationsHiddenEntry(),
				createDetectionsHiddenEntry(),
				createPixelClassificationOverlayHiddenEntry(),
				createTMAGridHiddenEntry(),
				createHiddenObjectsPredicate(),
				createHiddenClassificationsEntry(),
				createNoImageEntry(),
				createNoProjectEntry(),
				createOpacityZeroEntry(),
				createGammaNotDefault(),
				createInvertedColors(),
				createNoChannelsVisible(),
				createColorsTooSimilar()
				);
	}

	/**
	 * Get a message that may be used to create a badge indicating that info or warning messages
	 * are available.
	 * @return
	 */
	public ObjectExpression<InfoMessage> getInfoMessage() {
		return infoOrWarningMessage;
	}
	
	private Label createHelpTextLabel() {
		var label = new Label();
		label.setContentDisplay(ContentDisplay.CENTER);
		label.setAlignment(Pos.CENTER);
		label.setTextAlignment(TextAlignment.CENTER);
		label.setWrapText(true);
		label.textProperty().bindBidirectional(helpText);
		label.setPadding(new Insets(10.0));
		
		GridPaneUtils.setToExpandGridPaneWidth(label);
		GridPaneUtils.setToExpandGridPaneHeight(label);
		label.setPrefHeight(100.0);

		return label;
	}
		

	private boolean filterWindows(Window window) {
		if (window == stage)
			return false;
		var mainStage = qupath.getStage();
		if (window == mainStage)
			return true;
		else if (window instanceof Stage) {
			if (((Stage)window).getOwner() == mainStage)
				return true;
		}
		return false;
	}

	/**
	 * Get the single {@link ContextHelpViewer} instance associated with a specific 
	 * QuPath instance.
	 * @param qupath
	 * @return
	 */
	public static ContextHelpViewer getInstance(QuPathGUI qupath) {
		return INSTANCES.computeIfAbsent(qupath, ContextHelpViewer::new);
	}

	/**
	 * Get the help viewer stage.
	 * @return
	 */
	public Stage getStage() {
		return stage;
	}


	private void handleWindowChange(Change<? extends Window> change) {
		while (change.next()) {
			for (var window : change.getRemoved()) {
				addMouseListener(window);
			} for (var window : change.getAddedSubList()) {
				removeMouseListener(window);
			}
		}
	}

	private void addMouseListener(Window window) {
		window.addEventFilter(MouseEvent.MOUSE_MOVED, handler);		
	}

	private void removeMouseListener(Window window) {
		window.addEventFilter(MouseEvent.MOUSE_MOVED, handler);		
	}


	private void handleMouseMove(MouseEvent event) {
		if (!stage.isShowing()) {
			lastNode = null;
			return;
		}
		var result = event.getPickResult();
		var node = result.getIntersectedNode();
		if (node == lastNode)
			return;
		lastNode = node;
		String help = findHelpText(event, node);
		if (help == null || help.isEmpty()) {
			helpText.bind(defaultText);
			label.setOpacity(0.5);
		} else { 
			helpText.unbind();
			helpText.set(help);
			label.setOpacity(1.0);
		}
	}


	private static String findHelpText(MouseEvent event, Node node) {
		if (node == null)
			return null;
		var help = getHelpOrTooltipText(node);
		if (help != null && !help.isBlank() && node.isVisible()) {
			var local = node.screenToLocal(event.getScreenX(), event.getScreenY());
			if (node.contains(local))
				return help;
		}
		return findHelpText(event, node.getParent());
	}


	private static String getHelpOrTooltipText(Node node) {
		if (node == null)
			return null;
		var help = node.getAccessibleHelp();
		if (help != null)
			return help;
		else
			return getTooltipText(node);
	}

	private static String getTooltipText(Node node) {
		var tooltip = tryToGetTooltip(node);
		if (tooltip != null)
			return tooltip.getText();
		return null;
	}

	private static Tooltip tryToGetTooltip(Node node) {
		if (node instanceof Control control) {
			var tt = control.getTooltip();
			if (tt != null)
				return tt;
		}
		var tooltip = node.getProperties().get("javafx.scene.control.Tooltip");
		if (tooltip instanceof Tooltip tt)
			return tt;
		return null;
	}
	
	static enum HelpType {INFO, WARNING, ERROR}

	static class HelpListEntry {
		
		private int iconSize = 16;
		
		private HelpType type;
		private StringProperty textProperty;
		private SimpleObjectProperty<Node> graphicProperty;
		
		private BooleanProperty visibleProperty = new SimpleBooleanProperty(false);
		
		private HelpListEntry(HelpType type, String key, Node graphic) {
			this.type = type;
			this.textProperty = new SimpleStringProperty();
			QuPathResources.getLocalizedResourceManager().registerProperty(textProperty, key);
			if (graphic == null)
				graphic = createGraphicFromType(type);
			this.graphicProperty = new SimpleObjectProperty<>(graphic);
		}
		
		public ObjectProperty<Node> graphicProperty() {
			return graphicProperty;
		}

		private HelpType getType() {
			return type;
		}

		public StringProperty textProperty() {
			return textProperty;
		}

		private BooleanProperty visibleProperty() {
			return visibleProperty;
		}
		
		static HelpListEntry createWarning(String key, Node graphic) {
			return new HelpListEntry(HelpType.WARNING, key, graphic);
		}

		static HelpListEntry createWarning(String key) {
			return createWarning(key, null);
		}
		
		static HelpListEntry createInfo(String key, Node graphic) {
			return new HelpListEntry(HelpType.INFO, key, graphic);
		}

		static HelpListEntry createInfo(String key) {
			return createInfo(key, null);
		}
		
		private Node createGraphicFromType(HelpType type) {
			Node typeGraphic = null;
			switch (type) {
			case INFO:
				typeGraphic = createIcon(PathIcons.INFO);
				typeGraphic.setStyle("-fx-text-fill: cornflowerblue;");
				break;
			case WARNING:
				typeGraphic = createIcon(PathIcons.WARNING);
				typeGraphic.setStyle("-fx-text-fill: -qp-script-warn-color;");
				break;
			case ERROR:
				typeGraphic = createIcon(PathIcons.WARNING);
				typeGraphic.setStyle("-fx-text-fill: -qp-script-error-color;");
				break;
			default:
				break;
			}
			return typeGraphic;
		}
		
		Node createIcon(PathIcons icon) {
			return IconFactory.createNode(iconSize, iconSize, icon);
		}
		
	}
	
	
	Label createHelpLabel(HelpListEntry entry) {
		var label = new Label();
		label.setGraphicTextGap(8.0);
		label.setAlignment(Pos.CENTER);
//		label.setTextAlignment(TextAlignment.CENTER);
		label.textProperty().bind(entry.textProperty());
		label.graphicProperty().bind(entry.graphicProperty());
		label.setPadding(new Insets(5.0, 10.0, 5.0, 10.0));
		label.setWrapText(true);
		entry.visibleProperty().addListener((v, o, n) -> {
			if (n)
				vbox.getChildren().add(label);
			else
				vbox.getChildren().remove(label);
		});
		if (entry.visibleProperty().get())
			vbox.getChildren().add(label);
		return label;
	}
	
	
	private HelpListEntry createSelectionModelEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.selectionMode",
				createIcon(PathIcons.SELECTION_MODE));
		entry.visibleProperty().bind(
				PathPrefs.selectionModeProperty());
		return entry;
	}

	private HelpListEntry createUnseenErrors() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.unseenErrors",
				createIcon(PathIcons.LOG_VIEWER));
		entry.visibleProperty().bind(
				qupath.getLogViewerCommand().hasUnseenErrors());
		return entry;
	}

	private HelpListEntry createHiddenObjectsPredicate() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.hiddenObjectsPredicate");
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showObjectPredicateProperty().isNotNull());
		return entry;
	}
	
	private HelpListEntry createAnnotationsHiddenEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.annotationsHidden",
				createIcon(PathIcons.ANNOTATIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showAnnotationsProperty().not());
		return entry;
	}
	
	private HelpListEntry createTMAGridHiddenEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.tmaCoresHidden",
				createIcon(PathIcons.TMA_GRID));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showTMAGridProperty().not());
		return entry;
	}

	private HelpListEntry createHiddenClassificationsEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.classificationsHidden");
		entry.visibleProperty().bind(Bindings.isEmpty(qupath.getOverlayOptions().selectedClassesProperty()).not());
		return entry;
	}
	
	private HelpListEntry createDetectionsHiddenEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.detectionsHidden",
				createIcon(PathIcons.DETECTIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showDetectionsProperty().not());
		return entry;
	}
	
	private HelpListEntry createPixelClassificationOverlayHiddenEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.pixelOverlayHidden",
				createIcon(PathIcons.PIXEL_CLASSIFICATION));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showPixelClassificationProperty().not());
		return entry;
	}
	
	private HelpListEntry createOpacityZeroEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.opacityZero");
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().opacityProperty().lessThanOrEqualTo(0.0));
		return entry;
	}
	
	private HelpListEntry createNoImageEntry() {
		var entry = HelpListEntry.createInfo(
				"ContextHelp.warning.noImage");
		entry.visibleProperty().bind(
				imageDataProperty.isNull());
		return entry;
	}
	
	private HelpListEntry createNoProjectEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.noProject");
		entry.visibleProperty().bind(
				qupath.projectProperty().isNull());
		return entry;
	}

	private HelpListEntry createGammaNotDefault() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.gamma");
		entry.visibleProperty().bind(
				PathPrefs.viewerGammaProperty().isNotEqualTo(1));
		return entry;
	}

	private HelpListEntry createInvertedColors() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.invertedBackground");
		entry.visibleProperty().bind(
				qupath.viewerProperty()
						.map(QuPathViewer::getImageDisplay)
						.flatMap(ImageDisplay::useInvertedBackgroundProperty));
		return entry;
	}

	private HelpListEntry createNoChannelsVisible() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.noChannels",
				createIcon(PathIcons.CONTRAST));
		var emptyChannels = qupath.viewerProperty()
				.map(QuPathViewer::getImageDisplay)
				.map(ImageDisplay::selectedChannels)
				.flatMap(Bindings::isEmpty);
		entry.visibleProperty().bind(qupath.imageDataProperty().isNotNull().and(Bindings.createBooleanBinding(
				() -> emptyChannels == null || emptyChannels.getValue() == null ? false : emptyChannels.getValue(), emptyChannels
		)));
		return entry;
	}

	private HelpListEntry createColorsTooSimilar() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.colorsSimilar");
		qupath.getAvailablePathClasses().addListener((Change<? extends PathClass> c) -> updateSimilarClassificationColors());
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> updateSimilarClassificationColors());
		entry.visibleProperty().bind(classificationAndDefaultObjectColorsSimilar);
		return entry;
	}

	private HelpListEntry createPixelSizeMissing() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.pixelSizeMissing");
		entry.visibleProperty().bind(
				pixelCalibrationUnset);
		return entry;
	}

	private HelpListEntry createPixelSizeLikelyDpi() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.pixelSizeDpi");
		entry.visibleProperty().bind(
				pixelSizeLikelyDpi);
		return entry;
	}

	private HelpListEntry createImageTypeMissing() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.imageTypeMissing");
		entry.visibleProperty().bind(
				imageTypeUnset);
		return entry;
	}

	private HelpListEntry createLargeNonPyramidal() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.largeNonPyramidalImage");
		entry.visibleProperty().bind(
				largeNonPyramidalImage);
		return entry;
	}
	
	Node createIcon(PathIcons icon) {
		return IconFactory.createNode(iconSize, iconSize, icon);
	}


}
