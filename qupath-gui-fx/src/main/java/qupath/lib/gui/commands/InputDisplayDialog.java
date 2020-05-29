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

package qupath.lib.gui.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * QuPath command to display key-presses and mouse movement logged when interacting
 * with the main Window.
 * <p>
 * This is useful for demos and tutorials where shortcut keys are used.
 *
 * @author Pete Bankhead
 */
class InputDisplayDialog implements EventHandler<InputEvent> {

	private final static Logger logger = LoggerFactory.getLogger(InputDisplayDialog.class);

	private Window window;

	private Stage stage;

	private FocusListener focusListener = new FocusListener();
	private KeyFilter keyFilter = new KeyFilter();
	private MouseFilter mouseFilter = new MouseFilter();
	private ScrollFilter scrollFilter = new ScrollFilter();

	private Color colorActive = new Color(1, 1, 1, 0.6);
	private Color colorInactive = new Color(1, 1, 1, 0.1);

	// Keys
	private Set<KeyCode> MODIFIER_KEYS = new HashSet<>(
			Arrays.asList(KeyCode.SHIFT, KeyCode.SHORTCUT, KeyCode.COMMAND, KeyCode.CONTROL, KeyCode.ALT, KeyCode.ALT_GRAPH)
			);
	private ObservableMap<String, String> modifiers = FXCollections.observableMap(new TreeMap<String, String>());
	private ObservableMap<String, String> keys = FXCollections.observableMap(new TreeMap<String, String>());

	// Buttons
	private BooleanProperty primaryDown = new SimpleBooleanProperty(false);
	private BooleanProperty secondaryDown = new SimpleBooleanProperty(false);

	// Scroll/wheel
	private BooleanProperty scrollLeft = new SimpleBooleanProperty(false);
	private BooleanProperty scrollRight = new SimpleBooleanProperty(false);
	private BooleanProperty scrollUp = new SimpleBooleanProperty(false);
	private BooleanProperty scrollDown = new SimpleBooleanProperty(false);


	InputDisplayDialog(final Window window) {
		this.window = window;
	}

	private Stage createStage() {
		
		logger.trace("Creating stage for input display");

		double keyPaneWidth = 225.0;
		double mousePaneWidth = 100;
		double spacing = 5;

		var pane = new AnchorPane();
		pane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-background-radius: 10;");

		// Add to main pane
		var paneKeys = createKeyPane(keyPaneWidth);
		pane.getChildren().add(paneKeys);
		AnchorPane.setTopAnchor(paneKeys, 0.0);
		AnchorPane.setLeftAnchor(paneKeys, 0.0);


		// Create the mouse pane
		var paneMouse = createMousePane(mousePaneWidth);

		pane.getChildren().add(paneMouse);
		AnchorPane.setTopAnchor(paneMouse, 0.0);
		AnchorPane.setLeftAnchor(paneMouse, keyPaneWidth + 5);


		//	        // Add small node to close
		//	        var closeCircle = new Circle(4)
		////	        var closeCircle = new Text("x")
		//	        closeCircle.setStrokeWidth(1.5)
		//	        closeCircle.setStroke(new Color(1, 1, 1, 0.4))
		//	        closeCircle.setFill(null)
		////	        closeCircle.setOnMouseEntered { e -> closeCircle.setStroke(colorActive) }
		////	        closeCircle.setOnMouseExited() { e -> closeCircle.setStroke(new Color(1, 1, 1, 0.4)) }
		////	        var tooltipClose = new Tooltip("Close")
		////	        Tooltip.install(closeCircle, tooltipClose)
		//	        pane.getChildren().add(closeCircle)
		//	        AnchorPane.setTopAnchor(closeCircle, 5)
		//	        AnchorPane.setLeftAnchor(closeCircle, 5)


		// Set default location as the bottom left corner of the primary screen
		var screenBounds = Screen.getPrimary().getVisualBounds();
		double xPad = 10;
		double yPad = 10;

		// Create primary stage for display
		stage = new Stage();
		stage.initStyle(StageStyle.TRANSPARENT);
		var scene = new Scene(pane, keyPaneWidth + mousePaneWidth + spacing, 160, Color.TRANSPARENT);
		stage.setScene(scene);
		new MoveablePaneHandler(stage);

		var tooltipClose = new Tooltip("Display input - double-click to close");
		Tooltip.install(pane, tooltipClose);

		// Locate at bottom left of the screen
		stage.setX(screenBounds.getMinX() + xPad);
		stage.setY(screenBounds.getMaxY() - scene.getHeight() - yPad);

		stage.getScene().setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				stage.fireEvent(
						new WindowEvent(
								stage,
								WindowEvent.WINDOW_CLOSE_REQUEST
								)
						);
			}
		});
		return stage;
	}

	void show() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> show());
			return;
		}
		if (stage != null) {
			Dialogs.showErrorMessage("Show input", "Input display cannot be reused!");
			return;
		}

		window.addEventFilter(InputEvent.ANY, this);
		window.focusedProperty().addListener(focusListener);
		stage = createStage();
		stage.setAlwaysOnTop(true);
		stage.show();
		stage.setOnCloseRequest( e -> {
			window.focusedProperty().removeListener(focusListener);
			window.removeEventFilter(InputEvent.ANY, this);
		});
	}

	@Override
	public void handle(InputEvent event) {
		if (event instanceof KeyEvent)
			keyFilter.handle((KeyEvent) event);
		else if (event instanceof MouseEvent)
			mouseFilter.handle((MouseEvent) event);
		else if (event instanceof ScrollEvent)
			scrollFilter.handle((ScrollEvent) event);
	}


	void updateKeys(StringProperty textModifiers, StringProperty textKeys, StringProperty textHistory) {
		textModifiers.set(String.join(" + ", modifiers.keySet()));
		textKeys.set(String.join(" + ", keys.values()));
		List<String> allKeys = new ArrayList<>();
		if (!keys.isEmpty()) {
			allKeys.addAll(modifiers.keySet());
			allKeys.addAll(keys.values());
			textHistory.set("Last shortcut:\n" + String.join(" + ", allKeys));
		}
	}


	class FocusListener implements ChangeListener<Boolean> {

		@Override
		public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			if (newValue) {
				modifiers.clear();
				keys.clear();
			} else {
				primaryDown.set(false);
				secondaryDown.set(false);
				scrollLeft.set(false);
				scrollRight.set(false);
				scrollUp.set(false);
				scrollDown.set(false);
			}
		}
	}


	Pane createKeyPane(double width) {
		// Create labels for displaying keyboard info
		var labModifiers = new Label("");
		var labKeys = new Label("");
		var labHistory = new Label("");

		labModifiers.setPrefSize(width, 50);
		labKeys.setPrefSize(width, 50);
		labHistory.setPrefSize(width, 50);
		labModifiers.setAlignment(Pos.CENTER);
		labKeys.setAlignment(Pos.CENTER);
		labHistory.setAlignment(Pos.CENTER);
		labHistory.setTextAlignment(TextAlignment.CENTER);
		labModifiers.setStyle("-fx-text-fill: white; -fx-font-size: 24");
		labKeys.setStyle("-fx-text-fill: white; -fx-font-size: 32");
		labHistory.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.7); -fx-font-size: 14");

		// Listen for key changes
		var keyUpdater = new InvalidationListener() {
			@Override
			public void invalidated(Observable observable) {
				updateKeys(labModifiers.textProperty(), labKeys.textProperty(), labHistory.textProperty());
			}
		};
		modifiers.addListener(keyUpdater);
		keys.addListener(keyUpdater);

		// Create pane for displaying keyboard info
		var paneKeys = new GridPane();
		paneKeys.add(labModifiers, 0, 0);
		paneKeys.add(labKeys, 0, 1);
		paneKeys.add(labHistory, 0, 2);
		return paneKeys;
	}


	Pane createMousePane(double width) {
		var pane = new AnchorPane();

		var rectPrimary = createButtonRectangle(primaryDown);
		var rectSecondary = createButtonRectangle(secondaryDown);

		double arrowBase = 32;
		double arrowHeight = arrowBase / 2.0;

		var arrowUp = createArrow(scrollUp, arrowBase, arrowHeight, 0);
		var arrowDown = createArrow(scrollDown, arrowBase, arrowHeight, 180);
		var arrowLeft = createArrow(scrollLeft, arrowBase, arrowHeight, -90);
		var arrowRight = createArrow(scrollRight, arrowBase, arrowHeight, 90);

		pane.getChildren().addAll(
				rectPrimary,
				rectSecondary,
				arrowUp, arrowDown, arrowLeft, arrowRight
				);
		AnchorPane.setTopAnchor(rectPrimary, 20.);
		AnchorPane.setTopAnchor(rectSecondary, 20.);
		AnchorPane.setLeftAnchor(rectPrimary, 20.);
		AnchorPane.setLeftAnchor(rectSecondary, width-rectSecondary.getWidth()-20);

		double y = rectPrimary.getHeight() + 30;
		AnchorPane.setTopAnchor(arrowUp, y);
		AnchorPane.setTopAnchor(arrowDown, y + 60);
		AnchorPane.setTopAnchor(arrowLeft, y + 30);
		AnchorPane.setTopAnchor(arrowRight, y + 30);

		AnchorPane.setLeftAnchor(arrowUp, width/2.0-arrowBase/2.0);
		AnchorPane.setLeftAnchor(arrowDown, width/2.0-arrowBase/2.0);
		AnchorPane.setLeftAnchor(arrowLeft, width/2.0-arrowBase/2.0-arrowBase);
		AnchorPane.setLeftAnchor(arrowRight, width/2.0+arrowBase/2.0);

		return pane;
	}

	Rectangle createButtonRectangle(BooleanProperty isPressed) {
		var rect = new Rectangle(25, 40);
		rect.setArcHeight(8);
		rect.setArcWidth(8);
		rect.setStrokeWidth(2);
		var selected = colorActive;
		var deselected = colorInactive;
		rect.fillProperty().bind(createColorBinding(isPressed, selected, deselected));
		//	        rect.strokeProperty().bind(createColorBinding(isPressed, Color.WHITE, translucent))
		return rect;
	}

	Polygon createArrow(BooleanProperty isPressed, double arrowBase, double arrowHeight, double rotate) {
		var arrow = new Polygon(
				-arrowBase/2.0, arrowHeight/2.0, 0, -arrowHeight/2.0, arrowBase/2.0, arrowHeight/2.0
				);
		arrow.setStrokeWidth(2);
		arrow.setRotate(rotate);
		var selected = colorActive;
		var deselected = colorInactive;
		arrow.fillProperty().bind(createColorBinding(isPressed, selected, deselected));
		//	        rect.strokeProperty().bind(createColorBinding(isPressed, Color.WHITE, translucent))
		return arrow;
	}

	ObjectBinding<Paint> createColorBinding(BooleanProperty prop, Color selected, Color colorDeselected) {
		return Bindings.createObjectBinding(() -> prop.get() ? selected : colorDeselected, prop);
	}


	static String getTextForEvent(KeyEvent event) {
		String text = event.getText();
		if (event.getCode().isLetterKey())
			return text.toUpperCase();
		if (text.trim().isEmpty())
			return event.getCode().getName();
		return text;
	}


	/**
	 * Handler to log & display key events.
	 * This separates specific modifiers from other keys,
	 * and maintains a 'last shortcut' reference in case a key
	 * is pressed too quickly to catch what happened.
	 */
	class KeyFilter implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			var set = MODIFIER_KEYS.contains(event.getCode()) ? modifiers : keys;
			if (event.getEventType() == KeyEvent.KEY_PRESSED) {
				if (event.getCode() != null)
					set.put(event.getCode().getName(), getTextForEvent(event));
			} else if (event.getEventType() == KeyEvent.KEY_RELEASED)
				set.remove(event.getCode().getName());
		}
	}


	class MouseFilter implements EventHandler<MouseEvent> {

		@Override
		public void handle(MouseEvent event) {
			var type = event.getEventType();
			if (type == MouseEvent.MOUSE_PRESSED) {
				if (event.getButton() == MouseButton.PRIMARY)
					primaryDown.set(true);
				else if (event.getButton() == MouseButton.SECONDARY)
					secondaryDown.set(true);
			} else if (type == MouseEvent.MOUSE_RELEASED) {
				if (event.getButton() == MouseButton.PRIMARY)
					primaryDown.set(false);
				else if (event.getButton() == MouseButton.SECONDARY)
					secondaryDown.set(false);
			}
		}

	}


	class ScrollFilter implements EventHandler<ScrollEvent> {

		@Override
		public void handle(ScrollEvent event) {
			var type = event.getEventType();
			if (type == ScrollEvent.SCROLL_STARTED || type == ScrollEvent.SCROLL) {
				if (event.isInertia()) {
					scrollUp.set(false);
					scrollDown.set(false);
					scrollLeft.set(false);
					scrollRight.set(false);
					return;
				}
				double direction = PathPrefs.invertScrollingProperty().get() ? -1 : 1;
				scrollUp.set((event.getDeltaY() * direction) < -0.001);
				scrollDown.set((event.getDeltaY() * direction) > 0.001);
				scrollLeft.set((event.getDeltaX() * direction) < -0.001);
				scrollRight.set((event.getDeltaX() * direction) > 0.001);
			} else if (type == ScrollEvent.SCROLL_FINISHED) {
				scrollUp.set(false);
				scrollDown.set(false);
				scrollLeft.set(false);
				scrollRight.set(false);
			}
		}

	}


	/**
	 * Enable an undecorated stage to be moved by clicking and dragging within it.
	 * Requires the scene to be set. Note that this will set mouse event listeners.
	 */
	static class MoveablePaneHandler {

		private double xOffset = 0;
		private double yOffset = 0;

		MoveablePaneHandler(Stage stage) {
			var scene = stage.getScene();
			if (scene == null)
				throw new IllegalArgumentException("Scene must be set on the stage!");
			scene.setOnMousePressed(e -> {
				xOffset = stage.getX() - e.getScreenX();
				yOffset = stage.getY() - e.getScreenY();
			});
			scene.setOnMouseDragged(e -> {
				stage.setX(e.getScreenX() + xOffset);
				stage.setY(e.getScreenY() + yOffset);
			});
		}

	}

}