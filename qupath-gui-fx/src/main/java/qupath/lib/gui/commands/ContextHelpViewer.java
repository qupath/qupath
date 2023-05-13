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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.fx.utils.GridPaneUtils;

/**
 * Help window providing context-dependent help.
 * 
 * @author Pete Bankhead
 */
public class ContextHelpViewer {

	private static Map<QuPathGUI, ContextHelpViewer> INSTANCES = new ConcurrentHashMap<>();

	private StringProperty title = QuPathResources.getLocalizeResourceManager().createProperty("ContextHelp.title");
	private QuPathGUI qupath;
	
	private int iconSize = 16;

	private Stage stage = new Stage();
	private ObservableList<Window> windows;
	private EventHandler<MouseEvent> handler = this::handleMouseMove;

	private StringProperty defaultText = QuPathResources.getLocalizeResourceManager().createProperty("ContextHelp.defaultHelpText");
	private StringProperty helpText = new SimpleStringProperty(defaultText.get());

	private Label label;

	private Node lastNode;
	private VBox vbox;
	
	private ObservableList<HelpListEntry> allHelpEntries = FXCollections.observableArrayList();

	private ContextHelpViewer(QuPathGUI qupath) {
		this.qupath = qupath;
		
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
				createSelectionModelEntry(),
				createAnnotationsHiddenEntry(),
				createDetectionsHiddenEntry(),
				createPixelClassificationOverlayHiddenEntry(),
				createTMAGridHiddenEntry(),
				createNoImageEntry(),
				createNoProjectEntry(),
				createOpacityZeroEntry()
				);
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
		if (node instanceof Control) {
			return ((Control)node).getTooltip();
		}
		var tooltip = node.getProperties().get("javafx.scene.control.Tooltip");
		if (tooltip instanceof Tooltip)
			return (Tooltip)tooltip;
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
			QuPathResources.getLocalizeResourceManager().registerProperty(textProperty, key);
			if (graphic == null)
				graphic = createGraphicFromType(type);
			this.graphicProperty = new SimpleObjectProperty<>(graphic);
		}
		
		public ObjectProperty<Node> graphicProperty() {
			return graphicProperty;
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
	
	
	private  HelpListEntry createSelectionModelEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.selectionMode",
				createIcon(PathIcons.SELECTION_MODE));
		entry.visibleProperty().bind(
				PathPrefs.selectionModeProperty());
		return entry;
	}
	
	
	private  HelpListEntry createAnnotationsHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.annotationsHidden",
				createIcon(PathIcons.ANNOTATIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showAnnotationsProperty().not());
		return entry;
	}
	
	private  HelpListEntry createTMAGridHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.tmaCoresHidden",
				createIcon(PathIcons.TMA_GRID));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showTMAGridProperty().not());
		return entry;
	}
	
	private  HelpListEntry createDetectionsHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.detectionsHidden",
				createIcon(PathIcons.DETECTIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showDetectionsProperty().not());
		return entry;
	}
	
	private  HelpListEntry createPixelClassificationOverlayHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.pixelOverlayHidden",
				createIcon(PathIcons.PIXEL_CLASSIFICATION));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showPixelClassificationProperty().not());
		return entry;
	}
	
	private  HelpListEntry createOpacityZeroEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.opacityZero");
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().opacityProperty().lessThanOrEqualTo(0.0));
		return entry;
	}
	
	private  HelpListEntry createNoImageEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.noImage");
		entry.visibleProperty().bind(
				qupath.imageDataProperty().isNull());
		return entry;
	}
	
	private  HelpListEntry createNoProjectEntry() {
		var entry = HelpListEntry.createWarning(
				"ContextHelp.warning.noProject");
		entry.visibleProperty().bind(
				qupath.projectProperty().isNull());
		return entry;
	}
	
	Node createIcon(PathIcons icon) {
		return IconFactory.createNode(iconSize, iconSize, icon);
	}


}
