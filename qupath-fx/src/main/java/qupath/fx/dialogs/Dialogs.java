package qupath.fx.dialogs;

import java.util.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.fx.utils.FXUtils;

/**
 * Collection of static methods to help with showing information to a user, 
 * as well as requesting some basic input.
 * <p>
 * In general, 'showABCMessage' produces a dialog box that requires input from the user.
 * By contrast, 'showABCNotification' shows a message that will disappear without user input.
 * 
 * @author Pete Bankhead
 *
 */
public class Dialogs {
	
	private static final Logger logger = LoggerFactory.getLogger(Dialogs.class);

	private static Window primaryWindow;

	private static ObservableList<String> knownExtensions = FXCollections.observableArrayList();
	
	/**
	 * Set the primary window, which will be used as the owner of dialogs
	 * if no other window takes precedence (e.g. because it is modal or in focus).
	 * @param window
	 * @see #getPrimaryWindow()
	 */
	public static void setPrimaryWindow(Window window) {
		primaryWindow = window;
	}

	/**
	 * Get the primary window.
	 * @return
	 * @see #setPrimaryWindow(Window)
	 */
	public static Window getPrimaryWindow() {
		return primaryWindow;
	}

	/**
	 * Get a modifiable list of known file extensions.
	 * This exists to make it possible to override the logic of 'everything after the
	 * last dot is the file extension', and support multi-part extensions such as
	 * {@code .tar.gz} or {@code .ome.tif}.
	 * @return
	 */
	public static ObservableList<String> getKnownFileExtensions() {
		return knownExtensions;
	}

	/**
	 * Show a confirm dialog (OK/Cancel).
	 * @param title
	 * @param text
	 * @return
	 */
	public static boolean showConfirmDialog(String title, String text) {
		return showConfirmDialog(title, createContentLabel(text));
	}
	
	/**
	 * Show a message dialog (OK button only), with the content contained within a Node.
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showMessageDialog(final String title, final Node node) {
		return new Builder()
				.buttons(ButtonType.OK)
				.title(title)
				.content(node)
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;
	}
	
	/**
	 * Show a standard message dialog.
	 * @param title
	 * @param message
	 * @return 
	 */
	public static boolean showMessageDialog(String title, String message) {
		return showMessageDialog(title, createContentLabel(message));
	}
	
	/**
	 * Show a confirm dialog (OK/Cancel).
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showConfirmDialog(String title, Node node) {
		return new Builder()
				.alertType(AlertType.CONFIRMATION)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.title(title)
				.content(node)
				.resizable()
				.showAndWait()
				.orElse(ButtonType.NO) == ButtonType.OK;
	}
	
	/**
	 * Show a Yes/No dialog.
	 * @param title
	 * @param text
	 * @return
	 */
	public static boolean showYesNoDialog(String title, String text) {
		return new Builder()
			.alertType(AlertType.NONE)
			.buttons(ButtonType.YES, ButtonType.NO)
			.title(title)
			.content(createContentLabel(text))
			.showAndWait()
			.orElse(ButtonType.NO) == ButtonType.YES;
	}
	
	/**
	 * Create a content label. This is patterned on the default behavior for {@link DialogPane} but 
	 * sets the min size to be the preferred size, which is necessary to avoid ellipsis when using long 
	 * Strings on Windows with scaling other than 100%.
	 * @param text
	 * @return
	 */
	private static Label createContentLabel(String text) {
		var label = new Label(text);
		label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        label.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        label.setWrapText(true);
        label.setPrefWidth(360);
        return label;
	}
	
	/**
	 * Show a Yes/No/Cancel dialog.
	 * @param title dialog box title
	 * @param text prompt message
	 * @return a {@link ButtonType} of YES, NO or CANCEL
	 */
	public static ButtonType showYesNoCancelDialog(String title, String text) {
		return new Builder()
				.alertType(AlertType.NONE)
				.buttons(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
				.title(title)
				.content(createContentLabel(text))
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL);
	}


	/**
	 * Show an input dialog requesting a numeric value. Only scientific notation and digits 
	 * with/without a decimal separator (e.g. ".") are permitted.
	 * <p>
	 * The returned value might still not be in a valid state, as 
	 * limited by {@link FXUtils#restrictTextFieldInputToNumber(javafx.scene.control.TextField, boolean)}.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return Number input by the user, or NaN if no valid number was entered, or null if cancel was pressed.
	 * @see FXUtils#restrictTextFieldInputToNumber(javafx.scene.control.TextField, boolean)
	 */
	public static Double showInputDialog(final String title, final String message, final Double initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextInputDialog dialog = new TextInputDialog(initialInput.toString());
			FXUtils.restrictTextFieldInputToNumber(dialog.getEditor(), true);
			dialog.setTitle(title);
			dialog.initOwner(getDefaultOwner());
			dialog.setHeaderText(null);
			dialog.setContentText(message);
			dialog.setResizable(true);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent()) {
				try {
					return Double.parseDouble(result.get());
				} catch (Exception e) {
					// Can still happen since the TextField restrictions allow intermediate (invalid) formats
					logger.error("Unable to parse numeric value from {}", result);
					return Double.NaN;
				}
			}
		} else
			return FXUtils.callOnApplicationThread(() -> showInputDialog(title, message, initialInput));
		return null;
	}
	
	/**
	 * Show an input dialog requesting a String input.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return
	 */
	public static String showInputDialog(final String title, final String message, final String initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextInputDialog dialog = new TextInputDialog(initialInput);
			dialog.setTitle(title);
			dialog.initOwner(getDefaultOwner());
			dialog.setHeaderText(null);
			dialog.setContentText(message);
			dialog.setResizable(true);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent())
			    return result.get();
		} else {
			return FXUtils.callOnApplicationThread(() -> showInputDialog(title, message, initialInput));
		}
		return null;
	}
	
	/**
	 * Show a choice dialog with an array of choices (selection from ComboBox or similar).
	 * @param <T>
	 * @param title dialog title
	 * @param message dialog prompt
	 * @param choices array of available options
	 * @param defaultChoice initial selected option
	 * @return chosen option, or {@code null} if the user cancels the dialog
	 */
	public static <T> T showChoiceDialog(final String title, final String message, final T[] choices, final T defaultChoice) {
		return showChoiceDialog(title, message, Arrays.asList(choices), defaultChoice);
	}
	
	/**
	 * Show a choice dialog with a collection of choices (selection from ComboBox or similar).
	 * @param <T>
	 * @param title dialog title
	 * @param message dialog prompt
	 * @param choices list of available options
	 * @param defaultChoice initial selected option
	 * @return chosen option, or {@code null} if the user cancels the dialog
	 */
	public static <T> T showChoiceDialog(final String title, final String message, final Collection<T> choices, final T defaultChoice) {
		if (Platform.isFxApplicationThread()) {
			ChoiceDialog<T> dialog = new ChoiceDialog<>(defaultChoice, choices);
			dialog.setTitle(title);
			dialog.initOwner(getDefaultOwner());
			dialog.getDialogPane().setHeaderText(null);
			if (message != null)
				dialog.getDialogPane().setContentText(message);
			Optional<T> result = dialog.showAndWait();
			return result.orElse(null);
		} else
			return FXUtils.callOnApplicationThread(() -> showChoiceDialog(title, message, choices, defaultChoice));
	}
	
	/**
	 * Show an error message, displaying the localized message of a {@link Throwable}.
	 * @param title
	 * @param e
	 */
	public static void showErrorMessage(final String title, final Throwable e) {
		String message = e.getLocalizedMessage();
		if (message == null)
			message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please report it with 'Help -> Report bug (web)'.\n\n" + e;
		showErrorMessage(title, message);
		logger.error(title, e);
	}
	
	/**
	 * Show an error notification, displaying the localized message of a {@link Throwable}.
	 * @param title
	 * @param e
	 */
	public static void showErrorNotification(final String title, final Throwable e) {
		String message = e.getLocalizedMessage();
		if (message != null && !message.isBlank() && !message.equals(title))
			logger.error(title + ": " + e.getLocalizedMessage(), e);
		else
			logger.error(title , e);
		if (message == null)
			message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please report it with 'Help > Report bug'.\n\n" + e;
		if (!isHeadless())
			showNotifications(createNotifications().title(title).text(message), AlertType.ERROR);
	}

	/**
	 * Show an error notification.
	 * @param title
	 * @param message
	 */
	public static void showErrorNotification(final String title, final String message) {
		logger.error(title + ": " + message);
		if (!isHeadless())
			showNotifications(createNotifications().title(title).text(message), AlertType.ERROR);
	}

	/**
	 * Show a warning notification.
	 * @param title
	 * @param message
	 */
	public static void showWarningNotification(final String title, final String message) {
		logger.warn(title + ": " + message);
		if (!isHeadless())
			showNotifications(createNotifications().title(title).text(message), AlertType.WARNING);
	}

	/**
	 * Show an info notification.
	 * @param title
	 * @param message
	 */
	public static void showInfoNotification(final String title, final String message) {
		logger.info(title + ": " + message);
		if (!isHeadless())
			showNotifications(createNotifications().title(title).text(message), AlertType.INFORMATION);
	}

	/**
	 * Show a plain notification.
	 * @param title
	 * @param message
	 */
	public static void showPlainNotification(final String title, final String message) {
		logger.info(title + ": " + message);
		if (!isHeadless())
			showNotifications(createNotifications().title(title).text(message), AlertType.NONE);
	}
	
	/**
	 * Show notification, making sure it is on the application thread
	 * @param notification
	 */
	private static void showNotifications(Notifications notification, AlertType type) {
		if (isHeadless()) {
			logger.warn("Cannot show notifications in headless mode!");
			return;
		}
		if (Platform.isFxApplicationThread()) {
			switch (type) {
			case CONFIRMATION:
				notification.showConfirm();
				break;
			case ERROR:
				notification.showError();
				break;
			case INFORMATION:
				notification.showInformation();
				break;
			case WARNING:
				notification.showWarning();
				break;
			case NONE:
			default:
				notification.show();
				break;			
			}
		} else
			Platform.runLater(() -> showNotifications(notification, type));
	}
	
	
	/**
	 * Necessary to have owner when calling notifications (bug in controlsfx?).
	 */
	private static Notifications createNotifications() {
		var stage = getDefaultOwner();
		var notifications = Notifications.create();
		if (stage == null)
			return notifications;

		// 'Notifications' has a fixed color based on light/dark mode
		// Here, we instead use the default color for text based on the current css for the scene
		var scene = stage.getScene();
		if (scene != null) {
			var url = Dialogs.class.getClassLoader().getResource("qupath/fx/dialogs/notificationscustom.css");
			String stylesheetUrl = url.toExternalForm();
			if (!scene.getStylesheets().contains(stylesheetUrl))
				scene.getStylesheets().add(stylesheetUrl);
			notifications.styleClass("custom");
		}

		return notifications.owner(stage);
	}


	/**
	 * Show an error message.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final String message) {
		logger.error(title + ": " + message);
		if (!isHeadless())
			showErrorMessage(title, createContentLabel(message));
	}
	
	/**
	 * Show an error message, with the content defined within a {@link Node}.
	 * @param title
	 * @param node
	 */
	public static void showErrorMessage(final String title, final Node node) {
		new Builder()
			.alertType(AlertType.ERROR)
			.title(title)
			.content(node)
			.show();
	}

	/**
	 * Show a plain message.
	 * @param title
	 * @param message
	 */
	public static void showPlainMessage(final String title, final String message) {
		logger.info(title + ": " + message);
		if (!isHeadless()) {
			new Builder()
				.alertType(AlertType.INFORMATION)
				.title(title)
				.content(createContentLabel(message))
				.show();
		}
	}
	
	/**
	 * Show a window containing plain text, with the specified properties.
	 * 
	 * @param owner
	 * @param title
	 * @param contents
	 * @param modality
	 * @param isEditable
	 */
	public static void showTextWindow(final Window owner, final String title, final String contents, final Modality modality, final boolean isEditable) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showTextWindow(owner, title, contents, modality, isEditable));
			return;
		}
		logger.info("{}\n{}", title, contents);
		Stage dialog = new Stage();
		if (owner == null)
			dialog.initOwner(getDefaultOwner());
		else
			dialog.initOwner(owner);
		dialog.initModality(modality);
		dialog.setTitle(title);
		
		TextArea textArea = new TextArea();
		textArea.setPrefColumnCount(60);
		textArea.setPrefRowCount(25);

		textArea.setText(contents);
		textArea.setWrapText(true);
		textArea.positionCaret(0);
		textArea.setEditable(isEditable);
		
		dialog.setScene(new Scene(textArea));
		dialog.show();
	}


	/**
	 * Get a default owner window.
	 * This is the main QuPath window, if available, unless we have any modal stages.
	 * If we do have modal stages, and one is in focus, use that.
	 * Otherwise, return null and let JavaFX figure out the owner.
	 * @return
	 */
	static Window getDefaultOwner() {
		// Check modality, then focus, then title
		Comparator<Window> comparator = Comparator.comparing(Dialogs::isModal)
				.thenComparing(Window::isFocused)
				.thenComparing(w -> w == primaryWindow)
				.thenComparing(Dialogs::getTitle);
		return Window.getWindows().stream()
				.sorted(comparator)
				.findFirst()
				.orElse(primaryWindow);
	}

	private static String getTitle(Window window) {
		String title = null;
		if (window instanceof Stage stage)
			title = stage.getTitle();
		return title == null ? "" : title;
	}

	private static boolean isModal(Window window) {
		if (window instanceof Stage stage)
			return stage.getModality() != Modality.NONE;
		return false;
	}


	/**
	 * Create a new builder to generate a custom dialog.
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	
	private static boolean isHeadless() {
		return Window.getWindows().isEmpty();
	}

	/**
	 * Builder class to create a custom {@link Dialog}.
	 */
	public static class Builder {
		
		private AlertType alertType;
		private Window owner = null;
		private String title = "";
		private String header = null;
		private String contentText = null;
		private Node expandableContent = null;
		private Node content = null;
		private boolean resizable = false;
		private double width = -1;
		private double height = -1;
		private double prefWidth = -1;
		private double prefHeight = -1;
		private List<ButtonType> buttons = null;
		private Modality modality = Modality.APPLICATION_MODAL;
		
		/**
		 * Specify the dialog title.
		 * @param title dialog title
		 * @return this builder
		 */
		public Builder title(String title) {
			this.title = title;
			return this;
		}
		
		/**
		 * Specify the dialog header text.
		 * This is text that is displayed prominently within the dialog.
		 * @param header dialog header
		 * @return this builder
		 * @see #contentText(String)
		 */
		public Builder headerText(String header) {
			this.header = header;
			return this;
		}
		
		/**
		 * Specify the dialog content text.
		 * This is text that is displayed within the dialog.
		 * @param content dialog content text
		 * @return this builder
		 * @see #headerText(String)
		 */
		public Builder contentText(String content) {
			this.contentText = content;
			return this;
		}
		
		/**
		 * Specify a {@link Node} to display within the dialog.
		 * @param content dialog content
		 * @return this builder
		 * @see #contentText(String)
		 */
		public Builder content(Node content) {
			this.content = content;
			return this;
		}
		
		/**
		 * Specify a {@link Node} to display within the dialog as expandable content, not initially visible.
		 * @param content dialog expandable content
		 * @return this builder
		 * @see #content(Node)
		 */
		public Builder expandableContent(Node content) {
			this.expandableContent = content;
			return this;
		}
		
		/**
		 * Specify the dialog owner.
		 * @param owner dialog title
		 * @return this builder
		 */
		public Builder owner(Window owner) {
			this.owner = owner;
			return this;
		}
		
		/**
		 * Make the dialog resizable (but default it is not).
		 * @return this builder
		 */
		public Builder resizable() {
			resizable = true;
			return this;
		}

		/**
		 * Specify that the dialog should be non-modal.
		 * By default, most dialogs are modal (and therefore block clicks to other windows).
		 * @return this builder
		 */
		public Builder nonModal() {
			this.modality = Modality.NONE;
			return this;
		}
		
		/**
		 * Specify the modality of the dialog.
		 * @param modality requested modality
		 * @return this builder
		 */
		public Builder modality(Modality modality) {
			this.modality = modality;
			return this;
		}
		
		/**
		 * Create a dialog styled as a specified alert type.
		 * @param type 
		 * @return this builder
		 */
		public Builder alertType(AlertType type) {
			alertType = type;
			return this;
		}
		
		/**
		 * Create a warning alert dialog.
		 * @return this builder
		 */
		public Builder warning() {
			return alertType(AlertType.WARNING);
		}
		
		/**
		 * Create an error alert dialog.
		 * @return this builder
		 */
		public Builder error() {
			return alertType(AlertType.ERROR);
		}
		
		/**
		 * Create an information alert dialog.
		 * @return this builder
		 */
		public Builder information() {
			return alertType(AlertType.INFORMATION);
		}
		
		/**
		 * Create an confirmation alert dialog.
		 * @return this builder
		 */
		public Builder confirmation() {
			return alertType(AlertType.CONFIRMATION);
		}
		
		/**
		 * Specify the buttons to display in the dialog.
		 * @param buttonTypes buttons to use
		 * @return this builder
		 */
		public Builder buttons(ButtonType... buttonTypes) {
			this.buttons = Arrays.asList(buttonTypes);
			return this;
		}
		
		/**
		 * Specify the buttons to display in the dialog.
		 * @param buttonNames names of buttons to use
		 * @return this builder
		 */
		public Builder buttons(String... buttonNames) {
			var list = new ArrayList<ButtonType>();
			for (String name : buttonNames) {
				ButtonType type;
				switch (name.toLowerCase()) {
				case "ok": type = ButtonType.OK; break;
				case "yes": type = ButtonType.YES; break;
				case "no": type = ButtonType.NO; break;
				case "cancel": type = ButtonType.CANCEL; break;
				case "apply": type = ButtonType.APPLY; break;
				case "close": type = ButtonType.CLOSE; break;
				case "finish": type = ButtonType.FINISH; break;
				case "next": type = ButtonType.NEXT; break;
				case "previous": type = ButtonType.PREVIOUS; break;
				default: type = new ButtonType(name); break;
				}
				list.add(type);
			}
			this.buttons = list;
			return this;
		}
		
		/**
		 * Specify the dialog width.
		 * @param width requested width
		 * @return this builder
		 */
		public Builder width(double width) {
			this.width = width;
			return this;
		}
		
		/**
		 * Specify the dialog height.
		 * @param height requested height
		 * @return this builder
		 */
		public Builder height(double height) {
			this.height = height;
			return this;
		}
		
		/**
		 * Specify the preferred width of the dialog pane.
		 * @param prefWidth preferred width
		 * @return this builder
		 * @since v0.4.0
		 */
		public Builder prefWidth(double prefWidth) {
			this.prefWidth = prefWidth;
			return this;
		}
		
		/**
		 * Specify the preferred height of the dialog pane.
		 * @param prefHeight preferred height
		 * @return this builder
		 * @since v0.4.0
		 */
		public Builder prefHeight(double prefHeight) {
			this.prefHeight = prefHeight;
			return this;
		}
		
		/**
		 * Specify the dialog height.
		 * @param width requested width
		 * @param height requested height
		 * @return this builder
		 */
		public Builder size(double width, double height) {
			this.width = width;
			this.height = height;
			return this;
		}
		
		/**
		 * Build the dialog.
		 * @return a {@link Dialog} created with the specified features.
		 */
		public Dialog<ButtonType> build() {
			Dialog<ButtonType> dialog;
			if (alertType == null)
				dialog = new Dialog<>();
			else
				dialog = new Alert(alertType);
			
			if (owner == null) {
				dialog.initOwner(getDefaultOwner());
			} else
				dialog.initOwner(owner);
			
			dialog.setTitle(title);
			if (header != null)
				dialog.setHeaderText(header);
			else
				// The alert type can make some rather ugly header text appear
				dialog.setHeaderText(null);
			if (contentText != null)
				dialog.setContentText(contentText);
			if (content != null)
				dialog.getDialogPane().setContent(content);
			if (expandableContent != null)
				dialog.getDialogPane().setExpandableContent(expandableContent);
			if (width > 0)
				dialog.setWidth(width);
			if (height > 0)
				dialog.setHeight(height);
			if (prefWidth > 0)
				dialog.getDialogPane().setPrefWidth(prefWidth);
			if (prefHeight > 0)
				dialog.getDialogPane().setPrefHeight(prefHeight);
			if (buttons != null)
				dialog.getDialogPane().getButtonTypes().setAll(buttons);
			
			// We do need to be able to close the dialog somehow
			if (dialog.getDialogPane().getButtonTypes().isEmpty()) {
				dialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> dialog.hide());
			}
			
			dialog.setResizable(resizable);
			dialog.initModality(modality);
			
			// There's sometimes annoying visual bug in dark mode that results in a white/light 
			// thin line at the bottom of the dialog - padding seems to fix it
			if (Insets.EMPTY.equals(dialog.getDialogPane().getPadding()))
				dialog.getDialogPane().setStyle("-fx-background-insets: -1; -fx-padding: 1px;");
			
			return dialog;
		}
		
		
		/**
		 * Show the dialog.
		 * This is similar to {@code build().show()} except that it will automatically 
		 * be called on the JavaFX application thread even if called from another thread.
		 */
		public void show() {
			if (isHeadless()) {
				logger.warn("Cannot show dialog in headless mode!");
				return;
			}
			FXUtils.runOnApplicationThread(() -> build().show());
		}
		
		/**
		 * Show the dialog.
		 * This is similar to {@code build().showAndWait()} except that it will automatically 
		 * be called on the JavaFX application thread even if called from another thread.
		 * Callers should be cautious that this does not result in deadlock (e.g. if called from 
		 * the Swing Event Dispatch Thread on some platforms).
		 * @return 
		 */
		public Optional<ButtonType> showAndWait() {
			return FXUtils.callOnApplicationThread(() -> build().showAndWait());
		}
		
	}
	
}