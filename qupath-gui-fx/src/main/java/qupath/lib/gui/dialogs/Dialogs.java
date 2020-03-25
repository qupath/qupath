/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.dialogs;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
//			alert.setContentText(text);
			alert.getDialogPane().setContent(createContentLabel(text));
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else
			return GuiTools.callOnApplicationThread(() -> showConfirmDialog(title, text));
	}
	
	/**
	 * Show a message dialog (OK button only), with the content contained within a Node.
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showMessageDialog(final String title, final Node node) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
			alert.setTitle(title);
			alert.getDialogPane().setContent(node);
//			if (resizable) {
//				// Note: there is nothing to stop the dialog being shrunk to a ridiculously small size!
//				alert.setResizable(resizable);
//			}
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else {
			return GuiTools.callOnApplicationThread(() -> showMessageDialog(title, node));
		}
	}
	
	/**
	 * Show a standard message dialog.
	 * @param title
	 * @param message
	 * @return 
	 */
	public static boolean showMessageDialog(String title, String message) {
		logger.info("{}: {}", title, message);
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
			alert.setTitle(title);
			alert.getDialogPane().setHeader(null);
//			alert.getDialogPane().setContentText(message);
			alert.getDialogPane().setContent(createContentLabel(message));
			Optional<ButtonType> result = alert.showAndWait();
			return result.orElse(ButtonType.CANCEL) == ButtonType.OK;
		} else
			return GuiTools.callOnApplicationThread(() -> showMessageDialog(title, message));
	}
	
	/**
	 * Show a confirm dialog (OK/Cancel).
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showConfirmDialog(String title, Node node) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK, ButtonType.CANCEL);
			if (QuPathGUI.getInstance() != null)
				alert.initOwner(QuPathGUI.getInstance().getStage());
			alert.setTitle(title);
			alert.getDialogPane().setContent(node);
			alert.setResizable(true);
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else {
			return GuiTools.callOnApplicationThread(() -> showConfirmDialog(title, node));
		}
	}
	
	/**
	 * Show a Yes/No dialog.
	 * @param title
	 * @param text
	 * @return
	 */
	public static boolean showYesNoDialog(String title, String text) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> showYesNoDialog(title, text));
		}
		Alert alert = new Alert(AlertType.NONE);
		if (QuPathGUI.getInstance() != null)
			alert.initOwner(QuPathGUI.getInstance().getStage());
		alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
		alert.setTitle(title);
		alert.getDialogPane().setContent(createContentLabel(text));
		Optional<ButtonType> result = alert.showAndWait();
		boolean response = result.isPresent() && result.get() == ButtonType.YES;
		return response;
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
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> showYesNoCancelDialog(title, text));
		}
		// TODO: Check the order of buttons in Yes, No, Cancel dialog - seems weird on OSX
		Alert alert = new Alert(AlertType.NONE);
		alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
		alert.setTitle(title);
//			alert.setContentText(text);
		alert.getDialogPane().setContent(createContentLabel(text));
		Optional<ButtonType> result = alert.showAndWait();
		if (result.isPresent())
			return getJavaFXPaneYesNoCancel(result.get());
		else
			return DialogButton.CANCEL;
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
	 * Show an input dialog requesting a numeric value.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return Number input by the user, or NaN if no valid number was entered, or null if cancel was pressed.
	 */
	public static Double showInputDialog(final String title, final String message, final Double initialInput) {
		String result = showInputDialog(title, message, initialInput == null ? "" : initialInput.toString());
		if (result == null)
			return null;
		try {
			return Double.parseDouble(result);
		} catch (Exception e) {
			logger.error("Unable to parse numeric value from {}", result);
			return Double.NaN;
		}
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
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showErrorNotification(title, e));
			return;
		}
		String message = e.getLocalizedMessage();
		if (message != null && !message.isBlank() && !message.equals(title))
			logger.error(title + ": " + e.getLocalizedMessage(), e);
		else
			logger.error(title , e);
		if (message == null)
			message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please report it with 'Help > Report bug'.\n\n" + e;
		if (Platform.isFxApplicationThread()) {
			createNotifications().title(title).text(message).showError();
		} else {
			String finalMessage = message;
			Platform.runLater(() -> {
				createNotifications().title(title).text(finalMessage).showError();
			});
		}
	}

	/**
	 * Show an error notification.
	 * @param title
	 * @param message
	 */
	public static void showErrorNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showErrorNotification(title, message));
			return;
		}
		logger.error(title + ": " + message);
		createNotifications().title(title).text(message).showError();
	}

	/**
	 * Show a warning notification.
	 * @param title
	 * @param message
	 */
	public static void showWarningNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showWarningNotification(title, message));
			return;
		}
		logger.warn(title + ": " + message);
		createNotifications().title(title).text(message).showWarning();
	}

	/**
	 * Show an info notification.
	 * @param title
	 * @param message
	 */
	public static void showInfoNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showInfoNotification(title, message));
			return;
		}
		logger.info(title + ": " + message);
		createNotifications().title(title).text(message).showInformation();
	}

	/**
	 * Show a plain notification.
	 * @param title
	 * @param message
	 */
	public static void showPlainNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showPlainNotification(title, message));
			return;
		}
		logger.info(title + ": " + message);
		createNotifications().title(title).text(message).show();
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
	 * Show an error message.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final String message) {
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle(title);
				alert.getDialogPane().setHeaderText(null);
//				alert.setContentText(message);
				alert.getDialogPane().setContent(createContentLabel(message));
				alert.show();
			} else {
				Platform.runLater(() -> showErrorMessage(title, message));
				return;
			}
		}
		logger.error(title + ": " + message);
	}
	
	/**
	 * Show an error message, with the content defined within a {@link Node}.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final Node message) {
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle(title);
				alert.getDialogPane().setHeaderText(null);
				alert.getDialogPane().setContent(message);
				alert.show();
			} else {
				GuiTools.runOnApplicationThread(() -> showErrorMessage(title, message));
				return;
			}
		}
		logger.error(title + ": " + message);
	}

	/**
	 * Show a plain message.
	 * @param title
	 * @param message
	 */
	public static void showPlainMessage(final String title, final String message) {
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.getDialogPane().setHeaderText(null);
				alert.setTitle(title);
//				alert.setContentText(message);
				alert.getDialogPane().setContent(createContentLabel(message));
				alert.show();
			} else {
				Platform.runLater(() -> showPlainMessage(title, message));
				return;
			}
		}
		logger.info(title + ": " + message);
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
		return QuPathGUI.getSharedDialogHelper().promptForMultipleFiles(title, dirBase, filterDescription, exts);
	}

	/**
	 * Prompt user to select a directory.
	 * 
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return selected directory, or null if no directory was selected
	 */
	public static File promptForDirectory(File dirBase) {
		return QuPathGUI.getSharedDialogHelper().promptForDirectory(dirBase);
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
		return QuPathGUI.getSharedDialogHelper().promptForFile(title, dirBase, filterDescription, exts);
	}

	/**
	 * Prompt user to select a file.
	 * 
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return the File selected by the user, or null if the dialog was cancelled
	 */
	public static File promptForFile(File dirBase) {
		return QuPathGUI.getSharedDialogHelper().promptForFile(dirBase);
	}

	/**
	 * Prompt user to select a file path to save.
	 * 
	 * @param title the title to display for the dialog (may be null)
	 * @param dirBase the base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param defaultName default file name
	 * @param filterName description to show for the file name filter (may be null if no filter should be used)
	 * @param ext extension that should be used for the saved file (may be null if not specified)
	 * @return the File selected by the user, or null if the dialog was cancelled
	 */
	public static File promptToSaveFile(String title, File dirBase, String defaultName, String filterName, String ext) {
		return QuPathGUI.getSharedDialogHelper().promptToSaveFile(title, dirBase, defaultName, filterName, ext);
	}

	/**
	 * Prompt user to select a file or input a URL.

	 * @param title dialog title
	 * @param defaultPath default path to display - may be null
	 * @param dirBase base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param filterDescription description to (possibly) show for the file name filter (may be null if no filter should be used)
	 * @param exts optional array of file extensions if filterDescription is not null
	 * @return the path to the file or URL, or null if no path was provided.
	 */
	public static String promptForFilePathOrURL(String title, String defaultPath, File dirBase, String filterDescription,
			String... exts) {
		return QuPathGUI.getSharedDialogHelper().promptForFilePathOrURL(title, defaultPath, dirBase, filterDescription, exts);
	}
	
	/**
	 * Builder class to create a custom {@link Dialog}.
	 */
	public static class Builder {
		
		private AlertType alertType;
		private Window owner = QuPathGUI.getInstance() == null ? null : QuPathGUI.getInstance().getStage();
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
			resizable = false;
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
				dialog = new Alert(AlertType.NONE);
			else
				dialog = new Alert(alertType);
			dialog.initOwner(owner);
			dialog.setTitle(title);
			if (header != null)
				dialog.setHeaderText(header);
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
