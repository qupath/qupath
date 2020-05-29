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

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
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
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Basic log display functionality.
 * <p>
 * TODO: Nicer, color-coded implementation - possible using a ListView.
 * Ideally, this would also be filterable.
 * 
 * @author Pete Bankhead
 *
 */
public class LogViewerCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(LogViewerCommand.class);
	
	private QuPathGUI qupath;
	private Stage dialog = null;
	private TextArea textPane = new TextArea();
	
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
		this.qupath = qupath;
		LogManager.addTextAppendableFX(text -> textPane.appendText(text));
	}

	@Override
	public void run() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> run());
			return;
		}
		if (dialog == null)
			createDialog();
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
	
	
	private void createDialog() {
		dialog = new Stage();
		dialog.setTitle("Log");
		
		BorderPane pane = new BorderPane(textPane);
		
		Action actionCopy = new Action("Copy", e -> {
			String text = textPane.getSelectedText();
			if (text == null || text.isEmpty())
				text = textPane.getText();
			ClipboardContent content = new ClipboardContent();
			content.putString(text);
			Clipboard.getSystemClipboard().setContent(content);
		});
		actionCopy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN));
		
		Action actionClear = new Action("Clear log", e -> textPane.clear());
		
		CheckMenuItem miLockScroll = new CheckMenuItem("Scroll to end");
		miLockScroll.setSelected(true);
		
		// Add context menu
		ContextMenu menu = textPane.getContextMenu();
		if (menu == null) {
			menu = new ContextMenu();
			textPane.setContextMenu(menu);
			menu.getItems().add(ActionUtils.createMenuItem(actionCopy));
//			menu.getItems().add(new SeparatorMenuItem());
		} else
			menu.getItems().add(new SeparatorMenuItem());
		
		menu.getItems().add(ActionUtils.createMenuItem(actionClear));
		menu.getItems().add(miLockScroll);
		
		menu.getItems().add(createLogLevelMenu());
		
		
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
				writer.print(textPane.getText());
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
		
		Scene scene = new Scene(pane, 400, 300);
		dialog.setScene(scene);
//		dialog.getDialogPane().setContent(pane);
		dialog.setResizable(true);
		textPane.setEditable(false);
		
		// Keep scrolling to the last message
		textPane.textProperty().addListener((v, o, n) ->  {
			if (dialog.isShowing() && miLockScroll.isSelected())
				Platform.runLater(() -> textPane.setScrollTop(Double.MAX_VALUE));
		});

		dialog.initModality(Modality.NONE);
		dialog.initOwner(qupath.getStage());
		dialog.setResizable(true);
	}
	
	
	private static Menu createLogLevelMenu() {
		var menu = new Menu("Set log level");
		var group = new ToggleGroup();
		for (var action : actionLogLevels) {
			menu.getItems().add(ActionTools.createCheckMenuItem(action, group));
		}
		return menu;
	}

}