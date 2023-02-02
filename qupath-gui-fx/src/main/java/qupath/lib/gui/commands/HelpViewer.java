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
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.PaneTools;

/**
 * Help viewer for context-dependent help.
 * 
 * @author Pete Bankhead
 */
public class HelpViewer {

	private static Map<QuPathGUI, HelpViewer> INSTANCES = new ConcurrentHashMap<>();

	private String title = "Context help";
	private QuPathGUI qupath;
	
	private int iconSize = 16;

	private Stage stage = new Stage();
	private ObservableList<Window> windows;
	private EventHandler<MouseEvent> handler = this::handleMouseMove;

	private String defaultText = "Mouse mouse to view help text";
	private StringProperty helpText = new SimpleStringProperty(defaultText);

	private Label label;

	private Node lastNode;
	private VBox vbox;
	
	private ObservableList<HelpListEntry> allHelpEntries = FXCollections.observableArrayList();

	private HelpViewer(QuPathGUI qupath) {
		this.qupath = qupath;
		windows = Window.getWindows().filtered(this::filterWindows);
		for (var w : windows) {
			addMouseListener(w);		
		}
		windows.addListener(this::handleWindowChange);
		stage = new Stage();

		stage.initOwner(qupath.getStage());

		stage.setTitle(title);
		var pane = new BorderPane();
		label = createHelpTextLabel();

		PaneTools.setToExpandGridPaneWidth(label);
		PaneTools.setToExpandGridPaneHeight(label);
		label.setPrefHeight(100.0);
		
		vbox = new VBox();
		var scrollPane = new ScrollPane(vbox);
		scrollPane.setFitToWidth(true);
		
		var splitPane = new SplitPane(label, scrollPane);
		splitPane.setOrientation(Orientation.VERTICAL);
		pane = new BorderPane(splitPane);
		
		createHelpLabels();

		stage.setResizable(true);
		var scene = new Scene(pane);
		stage.setWidth(300);
		stage.setHeight(200);

		stage.setScene(scene);
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
		label.setPadding(new Insets(5.0));
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
	 * Get the single {@link HelpViewer} instance associated with a specific 
	 * QuPath instance.
	 * @param qupath
	 * @return
	 */
	public static HelpViewer getInstance(QuPathGUI qupath) {
		return INSTANCES.computeIfAbsent(qupath, HelpViewer::new);
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
			helpText.set(defaultText);
			label.setOpacity(0.5);
		} else { 
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
		
		private HelpType type;
		private String text;
		private Node graphic;
		
		private BooleanProperty visibleProperty = new SimpleBooleanProperty(false);
		
		private HelpListEntry(HelpType type, String text, Node graphic) {
			this.type = type;
			this.text = text;
			this.graphic = graphic;
		}
		
		private BooleanProperty visibleProperty() {
			return visibleProperty;
		}
		
		static HelpListEntry createWarning(String text, Node graphic) {
			return new HelpListEntry(HelpType.WARNING, text, graphic);
		}

		static HelpListEntry createWarning(String text) {
			return createWarning(text, null);
		}
		
		static HelpListEntry createInfo(String text, Node graphic) {
			return new HelpListEntry(HelpType.INFO, text, graphic);
		}

		static HelpListEntry createInfo(String text) {
			return createInfo(text, null);
		}

		
	}
	
	
	Label createHelpLabel(HelpListEntry entry) {
		var label = new Label();
		label.setText(entry.text);
		Node typeGraphic = entry.graphic;
		if (typeGraphic == null) {
			switch (entry.type) {
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
		}
		label.setGraphic(typeGraphic);
		label.setPadding(new Insets(5.0));
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
				"Selection mode is active - this will switch drawing tools to become selection tools instead",
				createIcon(PathIcons.SELECTION_MODE));
		entry.visibleProperty().bind(
				PathPrefs.selectionModeProperty());
		return entry;
	}
	
	
	private  HelpListEntry createAnnotationsHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"Annotations are hidden",
				createIcon(PathIcons.ANNOTATIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showAnnotationsProperty().not());
		return entry;
	}
	
	private  HelpListEntry createDetectionsHiddenEntry() {
		var entry = HelpListEntry.createWarning(
				"Detections are hidden",
				createIcon(PathIcons.DETECTIONS));
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().showDetectionsProperty().not());
		return entry;
	}
	
	private  HelpListEntry createOpacityZeroEntry() {
		var entry = HelpListEntry.createWarning(
				"Opacity slider is zero (unselected objects won't be visible)");
		entry.visibleProperty().bind(
				qupath.getOverlayOptions().opacityProperty().lessThanOrEqualTo(0.0));
		return entry;
	}
	
	private  HelpListEntry createNoImageEntry() {
		var entry = HelpListEntry.createWarning(
				"No image is open in the current viewer");
		entry.visibleProperty().bind(
				qupath.imageDataProperty().isNull());
		return entry;
	}
	
	private  HelpListEntry createNoProjectEntry() {
		var entry = HelpListEntry.createWarning(
				"No project is open");
		entry.visibleProperty().bind(
				qupath.projectProperty().isNull());
		return entry;
	}
	
	
	Node createIcon(PathIcons icon) {
		return IconFactory.createNode(iconSize, iconSize, icon);
	}
	

}
