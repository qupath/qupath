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
import java.net.URL;
import java.util.Collections;
import java.util.Scanner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

/**
 * QuPath command to display &amp; open sample scripts.
 * 
 * @author Pete Bankhead
 */
public class SampleScriptLoader implements PathCommand {

	final private static Logger logger = LoggerFactory.getLogger(SampleScriptLoader.class);

	private QuPathGUI qupath;
	private Stage stage;

	private ListView<Script> listScripts;
	private TextArea textArea;

	public SampleScriptLoader(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (stage == null)
			createStage();
		stage.show();
		stage.toFront();
	}

	private void createStage() {
		stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setTitle("Sample scripts");


		listScripts = new ListView<>();
		listScripts.setPlaceholder(new Label("No sample scripts available"));
		textArea = new TextArea();
		Button btnOpen = new Button("Open script");
		btnOpen.setMaxWidth(Double.MAX_VALUE);

		listScripts.setCellFactory(new Callback<ListView<Script>, ListCell<Script>>(){

			@Override
			public ListCell<Script> call(ListView<Script> p) {

				ListCell<Script> cell = new ListCell<Script>(){

					@Override
					protected void updateItem(Script value, boolean isEmpty) {
						super.updateItem(value, isEmpty);
						if (value == null || isEmpty) {
							setText(null);
							setGraphic(null);
							return;
						}
						setText(value.getDisplayedName());
						setTooltip(new Tooltip(value.getDescription()));
					}

				};

				return cell;
			}
		});
		
		// Relative path to sample scripts
		String scriptPath = "scripts/";
		try {
			// Load sample scripts
			File codeLocation = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
			if (codeLocation.isFile()) {
				// Read scripts from Jar file
				JarFile jar = new JarFile(codeLocation);
				for (JarEntry jarEntry : Collections.list(jar.entries())) {
					String name = jarEntry.getName();
					if (name.startsWith(scriptPath) && name.toLowerCase().endsWith(".groovy")) {
						try (Scanner scanner = new Scanner(jar.getInputStream(jarEntry))) {
							String scriptText = scanner.useDelimiter("\\Z").next();
							String scriptName = name.substring(scriptPath.length());
							Script script = new Script(scriptName, scriptText);
							listScripts.getItems().add(script);
							logger.debug("Read script from Jar: {}", name);
						} catch (IOException e) {
							logger.error("Error reading script from Jar", e);
						}
					}
				}
				jar.close();
			} else {
				// Read scripts from directory
				URL url = getClass().getClassLoader().getResource(scriptPath);
				for (File file : new File(url.toURI()).listFiles((File f) -> f.isFile() && f.getName().toLowerCase().endsWith(".groovy"))) {
					Script script = new Script(file.getName(), GeneralTools.readFileAsString(file.getAbsolutePath()));
					listScripts.getItems().add(script);
					logger.debug("Read script: {}", file.getName());
				}
			}
		} catch (Exception e) {
			logger.error("Error reading scripts", e);
		}

		listScripts.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n == null)
				textArea.setText("");
			else
				textArea.setText(n.getScript());
		});
		textArea.setWrapText(false);

		// Set actions to open scripts
		listScripts.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2)
				openScript(listScripts.getSelectionModel().getSelectedItem());
		});		
		btnOpen.setOnAction(e -> openScript(listScripts.getSelectionModel().getSelectedItem()));
		textArea.setFont(Font.font("monospaced"));

		BorderPane pane = new BorderPane();
		SplitPane splitPane = new SplitPane(listScripts, textArea);
		splitPane.setOrientation(Orientation.VERTICAL);
		pane.setCenter(splitPane);
		pane.setBottom(btnOpen);

		stage.setScene(new Scene(pane, 400, 400));
	}


	private void openScript(final Script script) {
		if (script != null)
			qupath.getScriptEditor().showScript(script.getName(), script.getScript());
	}


	/**
	 * Helper class to store a script for display.
	 */
	static class Script {

		private String name;
		private String script;
		private String description;

		public Script(final String name, final String script) {
			this.name = name;
			this.script = script;
			// Create description from initial comments, if possible
			int ind = script.indexOf("*/");
			if (script.startsWith("/*") && ind > 0) {
				StringBuilder sb = new StringBuilder();
				for (String line : GeneralTools.splitLines(script.substring(0, ind+2))) {
					int startInd = line.indexOf("*");
					String appendLine;
					if (startInd >= 0)
						appendLine = line.substring(startInd+1).trim();
					else
						appendLine = line.trim();
					if (appendLine.isEmpty())
						sb.append("\n");
					else
						sb.append(appendLine);
				}
				description = sb.toString().trim();
				if (description.endsWith("/"))
					description = description.substring(0, description.length()-1);
			} else
				description = "No description available";
		}

		public String getName() {
			return name;
		}
		
		public String getDisplayedName() {
			String displayedName = name;
			if (name.toLowerCase().endsWith(".groovy"))
				displayedName = name.substring(0, name.length()-".groovy".length());
			displayedName = displayedName.replace("_", " ");
			return displayedName;
		}

		public String getScript() {
			return script;
		}

		public String getDescription() {
			return description;
		}

		@Override
		public String toString() {
			return getName();
		}

	}

}