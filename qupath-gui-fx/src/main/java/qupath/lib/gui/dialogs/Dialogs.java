/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.dialogs;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.plugins.parameters.ParameterList;

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
	
	private final static Logger logger = LoggerFactory.getLogger(Dialogs.class);
	
	/**
	 * Possible buttons pressed in a yes/no/cancel dialog.
	 */
	public static enum DialogButton {
		/**
		 * "Yes" option
		 */
		YES,
		/**
		 * "No" option
		 */
		NO,
		/**
		 * "Cancel" option
		 */
		CANCEL
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
				.alertType(AlertType.NONE)
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
	 * @return a {@link DialogButton} indicating the response (YES, NO, CANCEL)
	 */
	public static DialogButton showYesNoCancelDialog(String title, String text) {
		var result = new Builder()
				.alertType(AlertType.NONE)
				.buttons(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
				.title(title)
				.content(createContentLabel(text))
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL);
		return getJavaFXPaneYesNoCancel(result);
	}
	
	
	private static DialogButton getJavaFXPaneYesNoCancel(final ButtonType buttonType) {
		if (buttonType == ButtonType.YES)
			return DialogButton.YES;
		if (buttonType == ButtonType.NO)
			return DialogButton.NO;
		if (buttonType == ButtonType.CANCEL)
			return DialogButton.CANCEL;
		return null;
	}
		

	/**
	 * Show a (modal) dialog for a specified ParameterList.
	 * 
	 * @param title
	 * @param params
	 * @return False if the user pressed 'cancel', true otherwise
	 */
	public static boolean showParameterDialog(String title, ParameterList params) {
		return showConfirmDialog(title, new ParameterPanelFX(params).getPane());
	}
	
	
	/**
	 * Show an input dialog requesting a numeric value. Only scientific notation and digits 
	 * with/without a decimal separator (e.g. ".") are permitted.
	 * <p>
	 * The returned value might still not be in a valid state, as 
	 * limited by {@link GuiTools#restrictTextFieldInputToNumber(javafx.scene.control.TextField, boolean)}.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return Number input by the user, or NaN if no valid number was entered, or null if cancel was pressed.
	 * @see GuiTools#restrictTextFieldInputToNumber(javafx.scene.control.TextField, boolean)
	 */
	public static Double showInputDialog(final String title, final String message, final Double initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextInputDialog dialog = new TextInputDialog(initialInput.toString());
			GuiTools.restrictTextFieldInputToNumber(dialog.getEditor(), true);
			dialog.setTitle(title);
			if (QuPathGUI.getInstance() != null)
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
			return GuiTools.callOnApplicationThread(() -> showInputDialog(title, message, initialInput));
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
			if (QuPathGUI.getInstance() != null)
				dialog.initOwner(getDefaultOwner());
			dialog.setHeaderText(null);
			dialog.setContentText(message);
			dialog.setResizable(true);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent())
			    return result.get();
		} else {
			return GuiTools.callOnApplicationThread(() -> showInputDialog(title, message, initialInput));
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
			if (QuPathGUI.getInstance() != null)
				dialog.initOwner(getDefaultOwner());
			dialog.getDialogPane().setHeaderText(null);
			if (message != null)
				dialog.getDialogPane().setContentText(message);
			Optional<T> result = dialog.showAndWait();
			if (result.isPresent())
				return result.get();
			return null;
		} else
			return GuiTools.callOnApplicationThread(() -> showChoiceDialog(title, message, choices, defaultChoice));
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
		showNotifications(createNotifications().title(title).text(message), AlertType.ERROR);
	}

	/**
	 * Show an error notification.
	 * @param title
	 * @param message
	 */
	public static void showErrorNotification(final String title, final String message) {
		logger.error(title + ": " + message);
		showNotifications(createNotifications().title(title).text(message), AlertType.ERROR);
	}

	/**
	 * Show a warning notification.
	 * @param title
	 * @param message
	 */
	public static void showWarningNotification(final String title, final String message) {
		logger.warn(title + ": " + message);
		showNotifications(createNotifications().title(title).text(message), AlertType.WARNING);
	}

	/**
	 * Show an info notification.
	 * @param title
	 * @param message
	 */
	public static void showInfoNotification(final String title, final String message) {
		logger.info(title + ": " + message);
		showNotifications(createNotifications().title(title).text(message), AlertType.INFORMATION);
	}

	/**
	 * Show a plain notification.
	 * @param title
	 * @param message
	 */
	public static void showPlainNotification(final String title, final String message) {
		logger.info(title + ": " + message);
		showNotifications(createNotifications().title(title).text(message), AlertType.NONE);
	}
	
	/**
	 * Show notification, making sure it is on the application thread
	 * @param notification
	 */
	private static void showNotifications(Notifications notification, AlertType type) {
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
		var stage = QuPathGUI.getInstance() == null ? null : QuPathGUI.getInstance().getStage();
		var notifications = Notifications.create();
		if (stage == null)
			return notifications;
		
		if (!QuPathStyleManager.isDefaultStyle())
			notifications = notifications.darkStyle();
		
		return notifications.owner(stage);
	}
	
	/**
	 * Show an error message that no image is available. This is included to help 
	 * standardize the message throughout the software.
	 * @param title
	 */
	public static void showNoImageError(String title) {
		showErrorMessage(title, "No image is available!");
	}
	
	/**
	 * Show an error message that no project is available. This is included to help 
	 * standardize the message throughout the software.
	 * @param title
	 */
	public static void showNoProjectError(String title) {
		showErrorMessage(title, "No project is available!");
	}
	
	
	/**
	 * Show an error message.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final String message) {
		logger.error(title + ": " + message);
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
		new Builder()
			.alertType(AlertType.INFORMATION)
			.title(title)
			.content(createContentLabel(message))
			.show();
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
	 * Prompt to open a list of files.
	 * 
	 * @param title
	 * @param dirBase
	 * @param filterDescription
	 * @param exts
	 * @return
	 */
	public static List<File> promptForMultipleFiles(String title, File dirBase, String filterDescription, String... exts) {
		return getSharedChooser().promptForMultipleFiles(title, dirBase, filterDescription, exts);
	}

	/**
	 * Prompt user to select a directory.
	 * 
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return selected directory, or null if no directory was selected
	 */
	public static File promptForDirectory(File dirBase) {
		return getSharedChooser().promptForDirectory(dirBase);
	}

	/**
	 * Prompt the user for a file with some kind of file dialog.
	 * @param title the title to display for the dialog (may be null to use default)
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param filterDescription description to (possibly) show for the file name filter (may be null if no filter should be used)
	 * @param exts optional array of file extensions if filterDescription is not null
	 * 
	 * @return the File selected by the user, or null if the dialog was cancelled
	 */
	public static File promptForFile(String title, File dirBase, String filterDescription, String... exts) {
		return getSharedChooser().promptForFile(title, dirBase, filterDescription, exts);
	}

	/**
	 * Prompt user to select a file.
	 * 
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return the File selected by the user, or null if the dialog was cancelled
	 */
	public static File promptForFile(File dirBase) {
		return getSharedChooser().promptForFile(dirBase);
	}

	/**
	 * Prompt user to select a file path to save.
	 * 
	 * @param title the title to display for the dialog (may be null)
	 * @param dirBase the base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param defaultName default file name
	 * @param filterName description to show for the file name filter (may be null if no filter should be used)
	 * @param ext extension that should be used for the saved file (may be empty or null if not specified)
	 * @return the File selected by the user, or null if the dialog was cancelled
	 */
	public static File promptToSaveFile(String title, File dirBase, String defaultName, String filterName, String ext) {
		return getSharedChooser().promptToSaveFile(title, dirBase, defaultName, filterName, ext);
	}

	/**
	 * Prompt user to select a file or input a URL.
	 * 
	 * @param title dialog title
	 * @param defaultPath default path to display - may be null
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param filterDescription description to (possibly) show for the file name filter (may be null if no filter should be used)
	 * @param exts optional array of file extensions if filterDescription is not null
	 * @return the path to the file or URL, or null if no path was provided.
	 */
	public static String promptForFilePathOrURL(String title, String defaultPath, File dirBase, String filterDescription,
			String... exts) {
		return getSharedChooser().promptForFilePathOrURL(title, defaultPath, dirBase, filterDescription, exts);
	}
	
	private static QuPathChooser defaultFileChooser = new QuPathChooserFX(null);
	private static Map<Window, QuPathChooser> fileChooserMap = new WeakHashMap<>();

	
	/**
	 * Get a default owner window.
	 * This is the main QuPath window, if available, unless we have any modal stages.
	 * If we do have modal stages, and one is in focus, use that.
	 * Otherwise, return null and let JavaFX figure out the owner.
	 * @return
	 */
	private static Window getDefaultOwner() {
		List<Stage> modalStages = Window.getWindows().stream()
				.filter(w -> w.isShowing() && w instanceof Stage)
				.map(w -> (Stage)w)
				.filter(s -> s.getModality() != Modality.NONE)
				.collect(Collectors.toList());
		if (modalStages.isEmpty()) {
			var qupath = QuPathGUI.getInstance();
			if (qupath != null)
				return qupath.getStage();
			return null;
		}
		var focussedStages = modalStages.stream()
				.filter(s -> s.isFocused())
				.collect(Collectors.toList());
		if (focussedStages.size() == 1)
			return focussedStages.get(0);
		return null;
	}
	
	/**
	 * Get a {@link QuPathChooser} instance linked to a specific window.
	 * This may both influence the display of the chooser (by setting the parent window) and the starting directory 
	 * (by remembering the last known directory for the chooser).
	 * @param window
	 * @return a {@link QuPathChooser} associated with the specified window.
	 */
	public static QuPathChooser getChooser(Window window) {
		if (window == null)
			return defaultFileChooser;
		return fileChooserMap.computeIfAbsent(window, w -> new QuPathChooserFX(w));
	}

	private static QuPathChooser getSharedChooser() {
		return getChooser(getDefaultOwner());
	}
	
	/**
	 * Create a new builder to generate a custom dialog.
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
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
			if (buttons != null)
				dialog.getDialogPane().getButtonTypes().setAll(buttons);
			
			// We do need to be able to close the dialog somehow
			if (dialog.getDialogPane().getButtonTypes().isEmpty()) {
				dialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> dialog.hide());
			}
			
			dialog.setResizable(resizable);
			dialog.initModality(modality);
			return dialog;
		}
		
		/**
		 * Show the dialog.
		 * This is similar to {@code build().show()} except that it will automatically 
		 * be called on the JavaFX application thread even if called from another thread.
		 */
		public void show() {
			if (GraphicsEnvironment.isHeadless()) {
				logger.warn("Cannot show dialog in headless mode!");
				return;
			}
			GuiTools.runOnApplicationThread(() -> build().show());
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
			return GuiTools.callOnApplicationThread(() -> build().showAndWait());
		}
		
	}
	
}