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
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

/**
 * Command to show license info for QuPath and any third-party dependencies.
 * 
 * @author Pete Bankhead
 *
 */
public class ShowLicensesCommand implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(ShowLicensesCommand.class);
	
	private QuPathGUI qupath;
	
	public ShowLicensesCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		logger.trace("Running 'Show licenses' command");
		
		StringBuilder sbQuPath = new StringBuilder();
		StringBuilder sbThirdParty = new StringBuilder();
		
		String buildString = qupath.getBuildString();
		if (buildString != null && !buildString.trim().isEmpty()) {
			sbQuPath.append("QuPath").append("\n");
			sbQuPath.append(buildString);
		}
		
		try {
			String licenseText = GeneralTools.readInputStreamAsString(ShowLicensesCommand.class.getResourceAsStream("/license/QuPathLicenseDescription.txt"));
			sbQuPath.append(licenseText);
		} catch (Exception e) {
			sbQuPath.append("Error reading license information!\n\n");
			sbQuPath.append("For the most up-to-date QuPath license information, see http://github.com/qupath/");
			logger.error("Cannot read license information", e);
		}
		
		// Try to get license file location
		// We assume it's in a license directory, located one level up from the current Jar
		File currentFile = null;
		try {
			currentFile = new File(ShowLicensesCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			logger.info("Running file: {}", currentFile);
		} catch (URISyntaxException e) {
			logger.error("Error identifying running file", e);
		}
		File fileLicenses = null;
		if (currentFile != null && currentFile.getName().toLowerCase().endsWith(".jar")) {
			File dirBase = currentFile.getParentFile();
//			logger.debug("Base directory: {}", dirBase);
			if (new File(dirBase, "licenses").isDirectory())
				fileLicenses = new File(dirBase, "THIRD-PARTY.txt");
			else if (new File(dirBase.getParentFile(), "licenses").isDirectory())
				fileLicenses = new File(new File(dirBase.getParentFile(), "licenses"), "THIRD-PARTY.txt");
			logger.debug("License file: {}", fileLicenses);
		}
		if (fileLicenses == null || !fileLicenses.isFile()) {
			sbThirdParty.append("No license information could be found for third party dependencies.");
		} else {
			sbThirdParty.append("THIRD PARTY LICENSES");
			sbThirdParty.append("\n");
			sbThirdParty.append("----------------------");
			sbThirdParty.append("\n");
			
			try {
				for (String line : Files.readAllLines(fileLicenses.toPath(), StandardCharsets.UTF_8)) {
					sbThirdParty.append(line);
					sbThirdParty.append("\n");
				}
			} catch (IOException e) {
				logger.error("Error reading license information", e);
			}
		}
		
		
		// Create dialog to show
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Licenses");
		
		TextArea textAreaQuPath = getTextArea(sbQuPath.toString());

		BorderPane paneThirdParty = new BorderPane(getTextArea(sbThirdParty.toString()));

		if (fileLicenses != null) {
			Button btnThirdParty = new Button("View third party licenses");
			File dirLicenses = fileLicenses.getParentFile();
			btnThirdParty.setOnAction(e -> {
				DisplayHelpers.openFile(dirLicenses);
			});
			btnThirdParty.setMaxWidth(Double.MAX_VALUE);
			paneThirdParty.setBottom(btnThirdParty);
		}
		
		
		Tab tabQuPath = new Tab("QuPath", textAreaQuPath);
		Tab tabThirdParty = new Tab("Third party", paneThirdParty);
		
		TabPane tabPane = new TabPane(tabQuPath, tabThirdParty);
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

		dialog.setScene(new Scene(tabPane));
		dialog.show();
	}
	
	
	private static TextArea getTextArea(final String contents) {
		TextArea textArea = new TextArea();
		textArea.setPrefColumnCount(60);
		textArea.setPrefRowCount(25);

		textArea.setText(contents);
		textArea.setWrapText(true);
		textArea.positionCaret(0);
		textArea.setEditable(false);
		return textArea;
	}
	
	

}
