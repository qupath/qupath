package qupath.lib.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;
import org.controlsfx.glyphfont.Glyph;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import qupath.lib.gui.tools.MenuTools;

public class ActionTools {
	
	/**
	 * Builder class for custom {@link Action} objects.
	 * These can be used to create GUI components (e.g. buttons, menu items).
	 */
	public static class ActionBuilder {

		private Consumer<ActionEvent> handler;
		
		private static enum Keys {TEXT, LONG_TEXT, SELECTED, GRAPHIC, DISABLED, ACCELERATOR};
		private Map<Keys, Object> properties = new HashMap<>();

		
		ActionBuilder(String text, Consumer<ActionEvent> handler) {
			this.handler = handler;
			text(text);
		}
		
		ActionBuilder(Consumer<ActionEvent> handler) {
			this.handler = handler;
		}
		
		private ActionBuilder property(Keys key, Object value) {
			properties.put(key, value);
			return this;
		}
		
		/**
		 * Set the text property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder text(String value) {
			return property(Keys.TEXT, value);
		}
		
		/**
		 * Set the long text property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder longText(String value) {
			return property(Keys.LONG_TEXT, value);
		}
		
		/**
		 * Set the graphic property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder graphic(Node value) {
			return property(Keys.GRAPHIC, value);
		}
		
		/**
		 * Set the accelerator property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder accelerator(KeyCombination value) {
			return property(Keys.ACCELERATOR, value);
		}
		
		/**
		 * Set the selected property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder selected(boolean value) {
			return property(Keys.SELECTED, value);
		}
		
		/**
		 * Set the disabled property of the action.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder disabled(boolean value) {
			return property(Keys.DISABLED, value);
		}
		
		/**
		 * Bind the text property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder text(ObservableValue<String> value) {
			return property(Keys.TEXT, value);
		}
		
		/**
		 * Bind the long text property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder longText(ObservableValue<String> value) {
			return property(Keys.LONG_TEXT, value);
		}
		
		/**
		 * Bind the graphic property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder graphic(ObservableValue<Node> value) {
			return property(Keys.GRAPHIC, value);
		}
		
		/**
		 * Bind the accelerator property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder accelerator(ObservableValue<KeyCombination> value) {
			return property(Keys.ACCELERATOR, value);
		}
		
		/**
		 * Bind the selected property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder selected(ObservableValue<Boolean> value) {
			return property(Keys.SELECTED, value);
		}
		
		/**
		 * Bind the disabled property of the action to an {@link ObservableValue}.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder disabled(ObservableValue<Boolean> value) {
			return property(Keys.DISABLED, value);
		}
		
		@SuppressWarnings("unchecked")
		private static <T> void updateProperty(Property<T> property, Object value) {
			if (value instanceof ObservableValue)
				bindProperty(property, (ObservableValue<? extends T>)value);
			else
				setProperty(property, (T)value);
		}
		
		private static <T> void setProperty(Property<T> property, T value) {
			property.setValue(value);
		}
		
		private static <T> void bindProperty(Property<T> property, ObservableValue<? extends T> value) {
			property.bind(value);
		}
		
		/**
		 * Create an {@link Action} with this builder.
		 * @return
		 */
		public Action build() {
			var action = new Action(handler);
			for (var entry : properties.entrySet()) {
				var value = entry.getValue();
				switch (entry.getKey()) {
				case ACCELERATOR:
					updateProperty(action.acceleratorProperty(), value);
					break;
				case DISABLED:
					updateProperty(action.disabledProperty(), value);
					break;
				case GRAPHIC:
					updateProperty(action.graphicProperty(), value);
					break;
				case LONG_TEXT:
					updateProperty(action.longTextProperty(), value);
					break;
				case SELECTED:
					updateProperty(action.selectedProperty(), value);
					break;
				case TEXT:
					updateProperty(action.textProperty(), value);
					break;
				default:
					break;
				}
			}
			return action;
		}
		
	}
	
	public static ActionBuilder actionBuilder(String text, Consumer<ActionEvent> handler) {
		return new ActionBuilder(text, handler);
	}
	
	public static ActionBuilder actionBuilder(Consumer<ActionEvent> handler) {
		return new ActionBuilder(handler);
	}
	
	
	
	public static MenuItem getActionCheckBoxMenuItem(Action action, ToggleGroup group) {
		if (group != null)
			return MenuTools.createRadioMenuItem(action, group);
		else
			return MenuTools.createCheckMenuItem(action);
	}
	
	public static MenuItem getActionCheckBoxMenuItem(Action action) {
		return getActionCheckBoxMenuItem(action, null);
	}
	
	public static CheckBox getActionCheckBox(Action action, boolean hideActionText) {
		// Not sure why we have to bind?
		CheckBox button = ActionUtils.createCheckBox(action);
		button.selectedProperty().bindBidirectional(action.selectedProperty());
		if (hideActionText) {
			button.setTooltip(new Tooltip(button.getText()));
			button.setText("");
		}
		return button;
	}
	
	public static ToggleButton getActionToggleButton(Action action, boolean hideActionText, ToggleGroup group) {
		ToggleButton button = ActionUtils.createToggleButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
		if (hideActionText && action.getText() != null) {
			Tooltip.install(button, new Tooltip(action.getText()));
		}
		
		// Internally, ControlsFX duplicates graphics (or gives up) because Nodes can't appear multiple times the scene graph
		// Consequently, we need to bind changes to the text fill here so that they filter through
		if (action.getGraphic() instanceof Glyph) {
			var actionGraphic = (Glyph)action.getGraphic();
			var buttonGraphic = (Glyph)button.getGraphic();
			buttonGraphic.textFillProperty().bind(actionGraphic.textFillProperty());
		}
		
		if (group != null)
			button.setToggleGroup(group);
		return button;
	}

	public static ToggleButton getActionToggleButton(Action action, boolean hideActionText) {
		return getActionToggleButton(action, hideActionText, null);
	}
	
	public static ToggleButton getActionToggleButton(Action action, boolean hideActionText, ToggleGroup group, boolean isSelected) {
		ToggleButton button = getActionToggleButton(action, hideActionText, group);
		return button;
	}

	ToggleButton getActionToggleButton(Action action, boolean hideActionText, boolean isSelected) {
		return getActionToggleButton(action, hideActionText, null, isSelected);
	}
	
	public static Button getActionButton(Action action, boolean hideActionText) {
		Button button = ActionUtils.createButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
		if (hideActionText && action.getText() != null) {
			Tooltip.install(button, new Tooltip(action.getText()));
		}
		return button;
	}

}
