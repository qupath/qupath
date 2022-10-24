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

package qupath.lib.gui.commands;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.SelectableItem;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.LogManager.LogLevel;
import qupath.lib.gui.logging.TextAppendable;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.TextAreaControl;

/**
 * A viewer for log messages.
 * <p>
 * The default display is very basic, but log methods can be styled by passing an alternative {@link ScriptEditorControl} 
 * to be used instead.
 * 
 * @author Pete Bankhead
 *
 */
public class LogViewerCommand implements Runnable, TextAppendable {
	
	private static final Logger logger = LoggerFactory.getLogger(LogViewerCommand.class);
	
	private QuPathGUI qupath;
	private Stage dialog = null;
	
	private BorderPane pane;
	
	private BooleanProperty lockScroll = new SimpleBooleanProperty(true);
	private ContextMenu contextMenu;
	
	private ScriptEditorControl<?> control;
	
	private static List<Action> actionLogLevels = Arrays.asList(
			createLogLevelAction(LogLevel.ERROR),
			createLogLevelAction(LogLevel.WARN),
			createLogLevelAction(LogLevel.INFO),
			createLogLevelAction(LogLevel.DEBUG),
			createLogLevelAction(LogLevel.TRACE)
			);
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public LogViewerCommand(final QuPathGUI qupath) {
		this(qupath, new TextAreaControl(false));
	}
	
	
	private LogViewerCommand(final QuPathGUI qupath, ScriptEditorControl<?> control) {
		Objects.requireNonNull(control);
		this.qupath = qupath;
		LogManager.addTextAppendableFX(this);
		init();
		setLogControl(control);
	}

	@Override
	public void run() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> run());
			return;
		}
		if (dialog == null) {
			dialog = new Stage();
			dialog.setTitle("Log");

			Scene scene = new Scene(pane, 400, 300);
			dialog.setScene(scene);
//			dialog.getDialogPane().setContent(pane);
			dialog.setResizable(true);
			
			dialog.initModality(Modality.NONE);
			dialog.initOwner(qupath.getStage());
			dialog.setResizable(true);
		}
		dialog.show();
	}
	
	
	private static Action createLogLevelAction(LogLevel level) {
		var command = new SelectableItem<>(LogManager.rootLogLevelProperty(), level);
		return ActionTools.actionBuilder(e -> command.setSelected(true))
			.text(level.toString())
			.selectable(true)
			.selected(command.selectedProperty())
			.build();
	}
	
	
	private void init() {
		
		pane = new BorderPane();
		
		Action actionCopy = new Action("Copy", e -> {
			String text = control.getSelectedText();
			if (text == null || text.isEmpty())
				text = control.getText();
			ClipboardContent content = new ClipboardContent();
			content.putString(text);
			Clipboard.getSystemClipboard().setContent(content);
		});
		actionCopy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN));
		
		Action actionClear = new Action("Clear log", e -> control.clear());
		
		CheckMenuItem miLockScroll = new CheckMenuItem("Scroll to end");
		miLockScroll.selectedProperty().bindBidirectional(lockScroll);
		
		// Add context menu
		contextMenu = new ContextMenu();
		contextMenu.getItems().add(ActionUtils.createMenuItem(actionCopy));
		contextMenu.getItems().add(new SeparatorMenuItem());
		contextMenu.getItems().add(ActionUtils.createMenuItem(actionClear));
		contextMenu.getItems().add(miLockScroll);
		contextMenu.getItems().add(createLogLevelMenu());
		
		
		// Add actual menubar
		MenuBar menubar = new MenuBar();
		Menu menuFile = new Menu("File");
		MenuItem miSave = new MenuItem("Save log");
		miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCodeCombination.SHORTCUT_DOWN));
		miSave.setOnAction(e -> {
			File fileOutput = Dialogs.getChooser(dialog).promptToSaveFile("Save log", null, "log.txt", "Log files", ".txt");
			if (fileOutput == null)
				return;
			try {
				PrintWriter writer = new PrintWriter(fileOutput, StandardCharsets.UTF_8);
				writer.print(control.getText());
				writer.close();
			} catch (Exception ex) {
				logger.error("Problem writing log", ex);
			}
		});
		
		MenuItem miCloseWindow = new MenuItem("Close window");
		miCloseWindow.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCodeCombination.SHORTCUT_DOWN));
		miCloseWindow.setOnAction(e -> dialog.hide());
		menuFile.getItems().addAll(miSave, miCloseWindow);
		
		Menu menuEdit = new Menu("Edit");
		menuEdit.getItems().addAll(
				ActionUtils.createMenuItem(actionCopy),
				ActionUtils.createMenuItem(actionClear),
				createLogLevelMenu()
				);
		menubar.getMenus().addAll(menuFile, menuEdit);
		pane.setTop(menubar);
//		menubar.setUseSystemMenuBar(true);
		menubar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
		
	}
	
	/**
	 * Set a new control to display log information.
	 * This makes it possible to apply nicer styling.
	 * @param control
	 */
	public void setLogControl(ScriptEditorControl<?> control) {
		if (this.control == control)
			return;
		Objects.requireNonNull(control);
		// Copy over any existing text
		if (this.control != null) {
			control.setText(this.control.getText());
			this.control.getRegion().setOnContextMenuRequested(null);
		}
		
		this.control = control;
		control.setContextMenu(contextMenu);
		pane.setCenter(control.getRegion());
					
		// Keep scrolling to the last message
		control.textProperty().addListener((v, o, n) ->  {
			if (dialog != null && dialog.isShowing() && lockScroll.get())
				Platform.runLater(() -> control.requestFollowCaret());
		});
	}
	
	
	private static Menu createLogLevelMenu() {
		var menu = new Menu("Set log level");
		var group = new ToggleGroup();
		for (var action : actionLogLevels) {
			menu.getItems().add(ActionTools.createCheckMenuItem(action, group));
		}
		return menu;
	}

	@Override
	public void appendText(String text) {
		if (control != null)
			control.appendText(text);
	}

}