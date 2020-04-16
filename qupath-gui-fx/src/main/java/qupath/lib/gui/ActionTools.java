package qupath.lib.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import qupath.lib.gui.icons.IconFactory;

/**
 * Helper methods for generating and configuring {@linkplain Action Actions} and UI elements.
 * <p>
 * This has some similarities with {@link ActionUtils}, however has QuPath-specific behavior that make it 
 * a better choice when working with actions related to {@link QuPathGUI}.
 * 
 * @author Pete Bankhead
 */
public class ActionTools {
	
	private static Logger logger = LoggerFactory.getLogger(ActionTools.class);
	
	private static final String ACTION_KEY = ActionTools.class.getName();
	
	/**
	 * Builder class for custom {@link Action} objects.
	 * These can be used to create GUI components (e.g. buttons, menu items).
	 */
	public static class ActionBuilder {

		private Consumer<ActionEvent> handler;
		
		private static enum Keys {TEXT, LONG_TEXT, SELECTED, GRAPHIC, DISABLED, ACCELERATOR, SELECTABLE};
		private Map<Keys, Object> properties = new HashMap<>();

		ActionBuilder() {}
		
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
		 * Sets the selectable property of the action.
		 * @param isSelectable
		 * @return this builder
		 */
		public ActionBuilder selectable(boolean isSelectable) {
			return property(Keys.SELECTABLE, isSelectable);
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
		 * Bind the text property of the action to an {@link ObservableValue}, bidirectionally if possible.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder text(ObservableValue<String> value) {
			return property(Keys.TEXT, value);
		}
		
		/**
		 * Bind the long text property of the action to an {@link ObservableValue}, bidirectionally if possible.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder longText(ObservableValue<String> value) {
			return property(Keys.LONG_TEXT, value);
		}
		
		/**
		 * Bind the graphic property of the action to an {@link ObservableValue}, bidirectionally if possible.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder graphic(ObservableValue<Node> value) {
			return property(Keys.GRAPHIC, value);
		}
		
		/**
		 * Bind the accelerator property of the action to an {@link ObservableValue}, bidirectionally if possible.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder accelerator(ObservableValue<KeyCombination> value) {
			return property(Keys.ACCELERATOR, value);
		}
		
		/**
		 * Bind the selected property of the action to an {@link ObservableValue}, bidirectionally if possible.
		 * @param value
		 * @return this builder
		 */
		public ActionBuilder selected(ObservableValue<Boolean> value) {
			return property(Keys.SELECTED, value);
		}
		
		/**
		 * Bind the disabled property of the action to an {@link ObservableValue}, bidirectionally if possible.
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
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		private static <T> void bindProperty(Property<T> property, ObservableValue<? extends T> value) {
			if (value instanceof Property) {
				property.bindBidirectional((Property)value);
			} else
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
				case SELECTABLE:
					setSelectable(action, (Boolean)value);
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
	
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
	public @interface ActionMenu {
		String value();
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD})
	public @interface ActionMethod {}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface ActionAccelerator {
		String value();
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface ActionDescription {
		String value();
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface ActionIcon {
		IconFactory.PathIcons value();
	}
	
	public static List<Action> getAnnotatedActions(Object obj) {
		List<Action> actions = new ArrayList<>();
		
		Class<?> cls = obj instanceof Class<?> ? (Class<?>)obj : obj.getClass();
		
		// If the class is annotated with a menu, use that as a base; all other menus will be nested within this
		var menuAnnotation = cls.getAnnotation(ActionMenu.class);
		String baseMenu = menuAnnotation == null ? "" : menuAnnotation.value();
		
		// Get accessible fields corresponding to actions
		for (var f : cls.getDeclaredFields()) {
			if (!f.canAccess(obj))
				continue;
			try {
				var value = f.get(obj);
				if (value instanceof Action) {
					var action = (Action)value;
					parseAnnotations(action, f, baseMenu);
					actions.add(action);
				} else if (value instanceof Action[]) {
					for (var temp : (Action[])value) {
						parseAnnotations(temp, f, baseMenu);
						actions.add(temp);		
					}
				}
			} catch (Exception e) {
				logger.error("Error setting up action: {}", e.getLocalizedMessage(), e);
			}
		}
		
		// Get accessible & annotated methods that may be converted to actions
		for (var m : cls.getDeclaredMethods()) {
			if (!m.isAnnotationPresent(ActionMethod.class) || !m.canAccess(obj))
				continue;
			try {
				Action action = null;
				if (m.getParameterCount() == 0) {
					action = new Action(e -> {
						try {
							m.invoke(obj);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
							logger.error("Error invoking method: " + e1.getLocalizedMessage(), e1);
						}
					});
				} else {
					logger.warn("Only methods with 0 parameters can currently be converted to actions by annotation only!");
				}
				if (action != null) {
					parseAnnotations(action, m, baseMenu);
					actions.add(action);
				}
			} catch (Exception e) {
				logger.error("Error setting up action: {}", e.getLocalizedMessage(), e);
			}
		}
		
		return actions;
	}

	public static void parseAnnotations(Action action, AnnotatedElement element) {
		parseAnnotations(action, element, "");
	}
	
	public static void parseAnnotations(Action action, AnnotatedElement element, String baseMenu) {
		parseMenu(action, element.getAnnotation(ActionMenu.class), baseMenu);
		parseDescription(action, element.getAnnotation(ActionDescription.class));
		parseAccelerator(action, element.getAnnotation(ActionAccelerator.class));
		parseIcon(action, element.getAnnotation(ActionIcon.class));
		parseDeprecated(action, element.getAnnotation(Deprecated.class));
	}
	
	private static void parseDeprecated(Action action, Deprecated annotation) {
 		if (annotation != null) {
 			action.getProperties().put("DEPRECATED", Boolean.TRUE);
 			var text = action.getText();
 			if (text != null && !text.isEmpty() && !text.contains("(") && !action.textProperty().isBound())
 				action.setText(action.getText() + " (deprecated)");
 		}
	}
	
	private static void parseMenu(Action action, ActionMenu annotation, String baseMenu) {
		String menuString = baseMenu == null || baseMenu.isBlank() ? "" : baseMenu + ">";
		if (annotation != null)
			menuString += annotation.value();
		if (menuString.isEmpty())
			return;
		var ind = menuString.lastIndexOf(">");
		if (ind <= 0) {
			logger.warn("Invalid menu string {}, will skip {}", menuString, action);
			return;
		}
		var name = menuString.substring(ind+1);
		var menu = menuString.substring(0, ind);
		if (!name.isEmpty())
			action.setText(name);
		action.getProperties().put("MENU", menu);
	}
	
	private static void parseDescription(Action action, ActionDescription annotation) {
		if (annotation == null)
			return;
		var description = annotation.value();
		action.setLongText(description);
	}
	
	private static void parseIcon(Action action, ActionIcon annotation) {
		if (annotation == null)
			return;
		var icon = annotation.value();
		action.setGraphic(IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, icon));
	}
	
	private static void parseAccelerator(Action action, ActionAccelerator annotation) {
		if (annotation == null)
			return;
		var accelerator = annotation.value();
		if (!accelerator.isBlank()) {
			try {
				action.setAccelerator(KeyCombination.keyCombination(accelerator));
			} catch (Exception e) {
				logger.warn("Unable to parse key combination '{}', cannot create accelerator for {}", accelerator, action);					
			}
		}
	}
	
	private static <T extends Node> T includeAction(T node, Action action) {
		node.getProperties().put(ACTION_KEY, action);
		return node;
	}
	
	private static <T extends MenuItem> T includeAction(T item, Action action) {
		item.getProperties().put(ACTION_KEY, action);
		return item;
	}
	
	/**
	 * Add an Action to the properties of a Node, so that it may be retrieved later.
	 * @param node the node for which the action should be added
	 * @param action an action to store as the properties of the node
	 */
	public static void putActionProperty(Node node, Action action) {
		node.getProperties().put(ACTION_KEY, action);
	}
	
	/**
	 * Add an Action to the properties of a MenuItem, so that it may be retrieved later.
	 * @param node the node for which the action should be added
	 * @param action an action to store as the properties of the node
	 */
	public static void putActionProperty(MenuItem node, Action action) {
		node.getProperties().put(ACTION_KEY, action);
	}
	
	/**
	 * Retrieve an Action stored within the properties of a node, or null if no action is found.
	 * @param node
	 * @return
	 */
	public static Action getActionProperty(Node node) {
		return node.hasProperties() ? (Action)node.getProperties().get(ACTION_KEY) : null;
	}
	
	/**
	 * Retrieve an Action stored within the properties of a menu item, or null if no action is found.
	 * @param item
	 * @return
	 */
	public static Action getActionProperty(MenuItem item) {
		return (Action)item.getProperties().get(ACTION_KEY);
	}
	
	
	/**
	 * Specify that an {@link Action} has a meaningful 'selected' status.
	 * Such actions often relate to properties and lack action event handlers.
	 * @param action
	 * @return true if the action has been flagged as selectable, false otherwise.
	 */
	public static boolean isSelectable(Action action) {
		return Boolean.TRUE.equals(action.getProperties().get(qupath.lib.gui.ActionTools.ActionBuilder.Keys.SELECTABLE));
	}
	
	/**
	 * Set the selectable property for an action.
	 * @param action
	 * @param selectable
	 * @see #isSelectable(Action)
	 */
	public static void setSelectable(Action action, boolean selectable) {
		action.getProperties().put(qupath.lib.gui.ActionTools.ActionBuilder.Keys.SELECTABLE, selectable);
	}
	
	/**
	 * Create an action indicating that a separator should be added (e.g. to a menu or toolbar).
	 * @return
	 */
	public static Action createSeparator() {
		return new Action(null, null);
	}
	
	public static ActionBuilder actionBuilder(String text, Consumer<ActionEvent> handler) {
		return new ActionBuilder(text, handler);
	}
	
	public static ActionBuilder actionBuilder() {
		return new ActionBuilder();
	}
	
	public static ActionBuilder actionBuilder(Consumer<ActionEvent> handler) {
		return new ActionBuilder(handler);
	}
	
	public static MenuItem createMenuItem(Action action) {
		if (action.getText() == null || action == ActionUtils.ACTION_SEPARATOR) {
			return new SeparatorMenuItem();
		}
		MenuItem item;
		if (isSelectable(action))
			item = ActionUtils.createCheckMenuItem(action);
		else
			item = ActionUtils.createMenuItem(action);
		return includeAction(item, action);
	}
	
	public static MenuItem createCheckMenuItem(Action action, ToggleGroup group) {
		MenuItem item;
		if (group != null) {
			var menuItem = ActionUtils.createRadioMenuItem(action);
			menuItem.setToggleGroup(group);
			item = menuItem;
		} else
			item = ActionUtils.createCheckMenuItem(action);
		return includeAction(item, action);
	}
	
	public static MenuItem createCheckMenuItem(Action action) {
		return createCheckMenuItem(action, null);
	}
	
	public static CheckBox createCheckBox(Action action) {
		// Not sure why we have to bind?
		CheckBox button = ActionUtils.createCheckBox(action);
		button.selectedProperty().bindBidirectional(action.selectedProperty());
		return includeAction(button, action);
	}
	
	private static ToggleButton getActionToggleButton(Action action, boolean hideActionText, ToggleGroup group) {
		ToggleButton button = ActionUtils.createToggleButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
		if (hideActionText && action.getText() != null) {
			Tooltip.install(button, new Tooltip(action.getText()));
		}
		if (group != null)
			button.setToggleGroup(group);
		return includeAction(button, action);
	}

	public static ToggleButton createToggleButton(Action action, boolean hideActionText) {
		return getActionToggleButton(action, hideActionText, null);
	}
	
	static ToggleButton createToggleButton(Action action, boolean hideActionText, ToggleGroup group, boolean isSelected) {
		ToggleButton button = getActionToggleButton(action, hideActionText, group);
		return button;
	}

	static ToggleButton createToggleButton(Action action, boolean hideActionText, boolean isSelected) {
		return createToggleButton(action, hideActionText, null, isSelected);
	}
	
	public static Button createButton(Action action, boolean hideActionText) {
		Button button = ActionUtils.createButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
//		if (hideActionText && action.getText() != null) {
//			Tooltip.install(button, new Tooltip(action.getText()));
//		}
		return includeAction(button, action);
	}

	public static Action createSelectableAction(final ObservableValue<Boolean> property, final String name, final Node icon, final KeyCombination accelerator) {
		var action = actionBuilder()
			.text(name)
			.selected(property)
			.selectable(true)
			.accelerator(accelerator)
			.graphic(icon)
			.build();
		return action;
	}

	public static Action createSelectableAction(final ObservableValue<Boolean> property, final String name) {
		return createSelectableAction(property, name, (Node)null, null);
	}

	public static Action createAction(final Runnable command, final String name, final Node icon, final KeyCombination accelerator) {
		var action = actionBuilder(name, e -> command.run())
				.accelerator(accelerator)
				.graphic(icon)
				.build();
		return action;
	}

	public static Action createAction(final Runnable command, final String name) {
		return createAction(command, name, (Node)null, null);
	}

}
