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

package qupath.lib.gui.commands;

import java.io.File;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.panels.PreferencePanel;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Command to show a basic property-editing window.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencesCommand implements PathCommand {
	
	private static Logger logger = LoggerFactory.getLogger(PreferencesCommand.class);
	
	private QuPathGUI qupath;
	
	private Stage dialog;
	private PreferencePanel panel;
	
	public PreferencesCommand(final QuPathGUI qupath, final PreferencePanel panel) {
		super();
		this.qupath = qupath;
		this.panel = panel;
	}

	@Override
	public void run() {
		if (dialog == null || true) {
			if (panel == null)
				panel = new PreferencePanel(qupath);
			
			dialog = new Stage();
			dialog.initOwner(qupath.getStage());
//			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.setTitle("Preferences");
			
			Button btnExport = new Button("Export");
			btnExport.setOnAction(e -> exportPreferences());
			btnExport.setMaxWidth(Double.MAX_VALUE);

			Button btnImport = new Button("Import");
			btnImport.setOnAction(e -> importPreferences());
			btnImport.setMaxWidth(Double.MAX_VALUE);
			
			Button btnReset = new Button("Reset");
			btnReset.setOnAction(e -> PathPrefs.resetPreferences());
			btnReset.setMaxWidth(Double.MAX_VALUE);
			
			GridPane paneImportExport = new GridPane();
			paneImportExport.addRow(0, btnImport, btnExport, btnReset);
			PaneToolsFX.setHGrowPriority(Priority.ALWAYS, btnImport, btnExport, btnReset);
			paneImportExport.setMaxWidth(Double.MAX_VALUE);

//			Button btnClose = new Button("Close");
//			btnClose.setOnAction(e -> {
//				dialog.hide();
//			});
			
			BorderPane pane = new BorderPane();
			pane.setCenter(panel.getNode());
			pane.setBottom(paneImportExport);
			if (qupath != null && qupath.getStage() != null) {
				pane.setPrefHeight(Math.round(Math.max(300, qupath.getStage().getHeight()*0.75)));
			}
			paneImportExport.prefWidthProperty().bind(pane.widthProperty());
//			btnClose.prefWidthProperty().bind(pane.widthProperty());
			dialog.setScene(new Scene(pane));
			dialog.setMinWidth(300);
			dialog.setMinHeight(300);
		}
		
		dialog.show();
	}

	private File dir = null;
	
	private boolean exportPreferences() {
		var file = QuPathGUI.getDialogHelper(dialog).promptToSaveFile(
				"Export preferences", dir, null, "Preferences file", "xml");
		if (file != null) {
			dir = file.getParentFile();
			try (var stream = Files.newOutputStream(file.toPath())) {
				logger.info("Exporting preferences to {}", file.getAbsolutePath());
				PathPrefs.exportPreferences(stream);
				return true;
			} catch (Exception e) {
				DisplayHelpers.showErrorMessage("Import preferences", e);
			}
		}
		return false;
	}
	
	private boolean importPreferences() {
		var file = QuPathGUI.getDialogHelper(dialog).promptForFile(
				"Import preferences", dir, "Preferences file", "xml");
		if (file != null) {
			dir = file.getParentFile();
			try (var stream = Files.newInputStream(file.toPath())) {
				logger.info("Importing preferences from {}", file.getAbsolutePath());
				PathPrefs.importPreferences(stream);
				DisplayHelpers.showMessageDialog("Import preferences", 
						"Preferences have been imported - please restart QuPath to see the changes.");
				return true;
			} catch (Exception e) {
				DisplayHelpers.showErrorMessage("Import preferences", e);
			}
		}
		return false;
	}
	
}
