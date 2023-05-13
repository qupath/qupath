package qupath.fx.controls;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;

import java.util.*;

/**
 * Control to display mouse and keyboard input when interacting with a window.
 * <p>
 * This is useful for demos and tutorials where shortcut keys are used.
 *
 * @author Pete Bankhead
 */
public class InputDisplay implements EventHandler<InputEvent> {

	private static final Logger logger = LoggerFactory.getLogger(InputDisplay.class);

	// Owner window (for stage positioning)
	private Window owner;

	// All windows to listen to
	private ObservableList<? extends Window> allWindows;

	private BooleanProperty showProperty = new SimpleBooleanProperty(false);

	private BooleanProperty showCloseButton = new SimpleBooleanProperty(true);

	private Stage stage;

	private FocusListener focusListener = new FocusListener();
	private KeyFilter keyFilter = new KeyFilter();
	private MouseFilter mouseFilter = new MouseFilter();
	private ScrollFilter scrollFilter = new ScrollFilter();

	private static final String inputDisplayClass = "input-display-pane";
	private static final String closeItemClass = "close-item";
	private static final String mouseItemClass = "mouse-item";
	private static final PseudoClass pseudoClassActive = PseudoClass.getPseudoClass("active");

	// Keys
	private Set<KeyCode> MODIFIER_KEYS = Set.of(
			KeyCode.SHIFT, KeyCode.SHORTCUT, KeyCode.COMMAND, KeyCode.CONTROL, KeyCode.ALT, KeyCode.ALT_GRAPH
	);

	private ObservableMap<String, String> modifiers = FXCollections.observableMap(new TreeMap<>());
	private ObservableMap<String, String> keys = FXCollections.observableMap(new TreeMap<>());

	// Buttons
	private BooleanProperty primaryDown = new SimpleBooleanProperty(false);
	private BooleanProperty secondaryDown = new SimpleBooleanProperty(false);
	private BooleanProperty middleDown = new SimpleBooleanProperty(false);

	// Scroll/wheel
	private BooleanProperty scrollLeft = new SimpleBooleanProperty(false);
	private BooleanProperty scrollRight = new SimpleBooleanProperty(false);
	private BooleanProperty scrollUp = new SimpleBooleanProperty(false);
	private BooleanProperty scrollDown = new SimpleBooleanProperty(false);

	/**
	 * Create an input display with the specified owner window.
	 * @param owner the owner used to position the input display, and when listening to input events.
	 *              If null, an input display is created to listen to all windows but without any owner.
	 */
	public InputDisplay(Window owner) {
		this(owner, owner == null ? Window.getWindows() : FXCollections.observableArrayList(owner));
	}

	/**
	 * Create an input display with the specified owner window and list of windows to listen to.
	 * To listen to input across all windows, use {@link Window#getWindows()}.
	 * @param owner the owner used to position the input display.
	 * @param windows the windows to listen to.
	 */
	public InputDisplay(Window owner, ObservableList<? extends Window> windows) {
		Objects.requireNonNull(windows, "An observable list of windows must be specified!");
		this.owner = owner;
		this.allWindows = windows;
		showProperty.addListener((v, o, n) -> updateShowStatus(n));
		for (var window : allWindows)
			addListenersToWindow(window);
		allWindows.addListener(this::handleWindowListChange);
	}

	private void handleWindowListChange(ListChangeListener.Change<? extends Window> change) {
		while (change.next()) {
			for (var window : change.getRemoved()) {
				removeListenersFromWindow(window);
			}
			for (var window : change.getAddedSubList()) {
				addListenersToWindow(window);
			}
		}
	}

	private void addListenersToWindow(Window window) {
		window.addEventFilter(InputEvent.ANY, this);
		window.focusedProperty().addListener(focusListener);
	}

	private void removeListenersFromWindow(Window window) {
		window.focusedProperty().removeListener(focusListener);
		window.removeEventFilter(InputEvent.ANY, this);
	}

	private void updateShowStatus(boolean doShow) {
		if (doShow) {
			if (stage == null) {
				stage = createStage();
				stage.initOwner(owner);
			}
			stage.setAlwaysOnTop(true);
			stage.show();
			stage.setOnCloseRequest(e -> {
				showProperty.set(false);
			});
		} else if (stage != null) {
			if (stage.isShowing())
				stage.hide();
			stage.hide();
		}
	}

	private Stage createStage() {

		logger.trace("Creating stage for input display");

		double keyPaneWidth = 225.0;
		double mousePaneWidth = 120;
		double spacing = 5;

		var pane = new AnchorPane();
		var stylesheetUrl = InputDisplay.class.getResource("/css/input-display.css").toExternalForm();
		pane.getStylesheets().add(stylesheetUrl);
		pane.getStyleClass().add(inputDisplayClass);

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


		// Add small node to close
		var closeImage = new SVGPath();
		closeImage.setContent("M 0 0 L 8 8 M 8 0 L 0 8");
		closeImage.getStyleClass().add(closeItemClass);
		var closeButton = new BorderPane(closeImage);
		closeButton.setOnMouseEntered( e -> closeImage.pseudoClassStateChanged(pseudoClassActive, true));
		closeButton.setOnMouseExited( e -> closeImage.pseudoClassStateChanged(pseudoClassActive, false));
		closeButton.setOnMouseClicked(e -> showProperty.set(false));
		closeButton.setCursor(Cursor.DEFAULT);
		pane.getChildren().add(closeButton);
		AnchorPane.setTopAnchor(closeButton, 7.0);
		AnchorPane.setLeftAnchor(closeButton, 7.0);
		closeButton.visibleProperty().bind(showCloseButton);


		// Set default location as the bottom left corner of the primary screen
		var screenBounds = Screen.getPrimary().getVisualBounds();
		double xPad = 10;
		double yPad = 10;

		// Create primary stage for display
		stage = new Stage();
		stage.initStyle(StageStyle.TRANSPARENT);
		var scene = new Scene(pane, keyPaneWidth + mousePaneWidth + spacing, 160, Color.TRANSPARENT);
		stage.setScene(scene);
		FXUtils.makeDraggableStage(stage);


		var tooltipClose = new Tooltip("Display input - double-click to close");
		Tooltip.install(pane, tooltipClose);

		// Locate at bottom left of the screen
		stage.setX(screenBounds.getMinX() + xPad);
		stage.setY(screenBounds.getMaxY() - scene.getHeight() - yPad);

		stage.getScene().setOnMouseClicked(e -> {
			if (!showCloseButton.get() && e.getClickCount() == 2) {
				hide();
			}
		});

		return stage;
	}


	public BooleanProperty showProperty() {
		return showProperty;
	}

	public void show() {
		showProperty.set(true);
	}

	public void hide() {
		showProperty.set(false);
	}


	@Override
	public void handle(InputEvent event) {
		// Return quickly if not showing
		if (!showProperty.get())
			return;
		// Handle according to event type
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
			if (!showProperty.get())
				return;
			if (newValue) {
				modifiers.clear();
				keys.clear();
			} else {
				primaryDown.set(false);
				secondaryDown.set(false);
				middleDown.set(false);
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
		labModifiers.getStyleClass().add("modifiers");
		labKeys.getStyleClass().add("keys");
		labHistory.getStyleClass().add("history");

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

		var rectPrimary = createButtonRectangle(25, 40);
		var rectSecondary = createButtonRectangle(25, 40);
		var rectMiddle = createButtonRectangle(8, 18);

		double gap = 5;
		rectMiddle.setTranslateX(rectPrimary.getWidth() + gap/2.0 - rectMiddle.getWidth()/2.0);

		rectSecondary.setTranslateX(rectPrimary.getWidth()+gap);

		rectMiddle.setStrokeWidth(8);
		rectMiddle.setStroke(Color.WHITE);
		rectMiddle.setTranslateY((rectPrimary.getHeight()-rectMiddle.getHeight())/2.0);
		var shapePrimary = Shape.subtract(rectPrimary, rectMiddle);
		shapePrimary.getStyleClass().setAll(rectPrimary.getStyleClass());
		var shapeSecondary = Shape.subtract(rectSecondary, rectMiddle);
		shapeSecondary.getStyleClass().setAll(rectSecondary.getStyleClass());
		rectMiddle.setStroke(null);
		rectMiddle.setStrokeWidth(2);

		primaryDown.addListener((v, o, n) -> shapePrimary.pseudoClassStateChanged(pseudoClassActive, n));
		secondaryDown.addListener((v, o, n) -> shapeSecondary.pseudoClassStateChanged(pseudoClassActive, n));
		middleDown.addListener((v, o, n) -> rectMiddle.pseudoClassStateChanged(pseudoClassActive, n));

		var group = new Group();
		group.getChildren().addAll(shapePrimary, shapeSecondary, rectMiddle);

		double arrowBase = 32;
		double arrowHeight = arrowBase / 2.0;

		var arrowUp = createArrow(arrowBase, arrowHeight, 0);
		var arrowDown = createArrow(arrowBase, arrowHeight, 180);
		var arrowLeft = createArrow(arrowBase, arrowHeight, -90);
		var arrowRight = createArrow(arrowBase, arrowHeight, 90);

		scrollUp.addListener((v, o, n) -> arrowUp.pseudoClassStateChanged(pseudoClassActive, n));
		scrollDown.addListener((v, o, n) -> arrowDown.pseudoClassStateChanged(pseudoClassActive, n));
		scrollLeft.addListener((v, o, n) -> arrowLeft.pseudoClassStateChanged(pseudoClassActive, n));
		scrollRight.addListener((v, o, n) -> arrowRight.pseudoClassStateChanged(pseudoClassActive, n));

		pane.getChildren().addAll(
				group,
				arrowUp, arrowDown, arrowLeft, arrowRight
		);

		AnchorPane.setTopAnchor(group, 20.0);
		AnchorPane.setLeftAnchor(group, width/2.0-group.getBoundsInLocal().getWidth()/2.0);

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

	Rectangle createButtonRectangle(double width, double height) {
		var rect = new Rectangle(width, height);
		rect.setArcHeight(8);
		rect.setArcWidth(8);
		rect.setStrokeWidth(2);
		rect.getStyleClass().add(mouseItemClass);
		return rect;
	}




	Polygon createArrow(double arrowBase, double arrowHeight, double rotate) {
		var arrow = new Polygon(
				-arrowBase/2.0, arrowHeight/2.0, 0, -arrowHeight/2.0, arrowBase/2.0, arrowHeight/2.0
		);
		arrow.setStrokeWidth(2);
		arrow.setRotate(rotate);
		arrow.getStyleClass().add(mouseItemClass);
		return arrow;
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
			// We might consider ignoring input events on TextInputControls
			// since their effects are already visible
//			if (event.getTarget() instanceof TextInputControl)
//				return;
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
				else if (event.getButton() == MouseButton.MIDDLE)
					middleDown.set(true);
			} else if (type == MouseEvent.MOUSE_RELEASED) {
				if (event.getButton() == MouseButton.PRIMARY)
					primaryDown.set(false);
				else if (event.getButton() == MouseButton.SECONDARY)
					secondaryDown.set(false);
				else if (event.getButton() == MouseButton.MIDDLE)
					middleDown.set(false);
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
				// Previously, there was support for 'invert scrolling' to do with different
				// macOS behavior. This isn't used anymore, but the code is left in case it's needed again.
				boolean invertScrolling = false;
				double direction = invertScrolling ? -1 : 1;
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

}