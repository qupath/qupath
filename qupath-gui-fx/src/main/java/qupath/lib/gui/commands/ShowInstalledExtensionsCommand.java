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
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;

/**
 * Command to list the names &amp; details of all installed extensions
 * 
 * @author Pete Bankhead
 *
 */
public class ShowInstalledExtensionsCommand implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(ShowInstalledExtensionsCommand.class);
	
	private QuPathGUI qupath;
	
	public ShowInstalledExtensionsCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Installed extensions");
		
		
		GridPane paneExtensions = new GridPane();
		paneExtensions.setPadding(new Insets(10, 10, 10, 10));
		paneExtensions.setHgap(5);
		paneExtensions.setVgap(10);
		
		int row = 0;
		int inc = 1;
		for (QuPathExtension extension : qupath.getLoadedExtensions()) {
			addEntry(paneExtensions, new QuPathExtensionEntry(extension), row);
			row += inc;
		}
		
		GridPane paneServers = new GridPane();
		paneServers.setPadding(new Insets(10, 10, 10, 10));
		paneServers.setHgap(5);
		paneServers.setVgap(10);
		row = 0;
		for (ImageServerBuilder<?> builder : ImageServerProvider.getInstalledImageServerBuilders()) {
			addEntry(paneServers, new QuPathExtensionEntry(builder), row);
			row += inc;
		}
		
		String titledPlainCSS = ShowInstalledExtensionsCommand.class.getClassLoader().getResource("css/titled_plain.css").toExternalForm();
		
		TitledPane titledExtensions = new TitledPane("Extensions", paneExtensions);
		titledExtensions.getStylesheets().add(titledPlainCSS);
		TitledPane titledServers = new TitledPane("Image Servers", paneServers);
		titledServers.getStylesheets().add(titledPlainCSS);
		
		VBox vbox = new VBox(
				titledExtensions,
				titledServers
		);

		vbox.setMaxWidth(Double.POSITIVE_INFINITY);
		dialog.setScene(new Scene(new ScrollPane(vbox)));
		dialog.show();
	}
	
	
	private static void addEntry(final GridPane pane, final QuPathExtensionEntry entry, final int row) {
//		TextArea textArea = new TextArea(entry.getDescription());
//		textArea.setPrefRowCount(3);
//		textArea.setWrapText(true);
//		textArea.setEditable(false);
//		textArea.setMinWidth(100);
//		
//		Label label = new Label(entry.getName());
//		label.setMaxWidth(Double.MAX_VALUE);
//		label.setTextAlignment(TextAlignment.RIGHT);
//		
//		Tooltip tooltip = new Tooltip(entry.getPathToJar());
//		label.setTooltip(tooltip);
//		textArea.setTooltip(tooltip);
//		textArea.setMaxWidth(Double.MAX_VALUE);
//		GridPane.setHgrow(textArea, Priority.ALWAYS);
//		GridPane.setFillWidth(textArea, Boolean.TRUE);
//		
//		pane.add(label, 1, row*2);
//		pane.add(textArea, 1, row*2+1);
		
		
		TextArea textArea = new TextArea(entry.getDescription());
		textArea.setPrefRowCount(3);
		textArea.setWrapText(true);
		textArea.setEditable(false);
		textArea.setMinWidth(100);
		textArea.setBorder(null);
//		textArea.getStylesheets().add(ShowInstalledExtensionsCommand.class.getClassLoader().getResource("css/text_area_transparent.css").toExternalForm());
		
		for (Node node : textArea.getChildrenUnmodifiable()) {
			if (node instanceof Region)
				((Region)node).setBorder(null);
		}
////		textArea.setStyle("-fx-focus-color: transparent; -fx-text-box-border: transparent;");
		
//		TextFlow textArea = new TextFlow(new Text(entry.getDescription()));
//		textArea.setBorder(null);
		
		BorderPane paneEntryMain = new BorderPane(textArea);
		Label labelPath = new Label("Path: " + entry.getPathToJar());
		labelPath.setPadding(new Insets(0, 0, 2, 0));
		File file = new File(entry.getPathToJar());
		if (file.exists()) {
			// Locate file with a double-click, if possible
			File dir = file.isDirectory() ? file : file.getParentFile();
				labelPath.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2) {
						DisplayHelpers.openFile(dir);
					}
				});
		}
		paneEntryMain.setBottom(labelPath);
		
		String name = entry.getName();
		String version = entry.getVersion();
		if (version != null)
			name = name + " (" + version + ")";
		
		TitledPane paneEntry = new TitledPane(name, paneEntryMain);
		paneEntry.setExpanded(false);
		paneEntry.setBorder(null);
		// Remove borders
		paneEntry.getStylesheets().add(ShowInstalledExtensionsCommand.class.getClassLoader().getResource("css/titled_plain.css").toExternalForm());

		
//		Tooltip tooltip = new Tooltip(entry.getPathToJar());
		paneEntry.setTooltip(new Tooltip(entry.getDescription()));
//		textArea.setTooltip(tooltip);
		textArea.setMaxWidth(Double.MAX_VALUE);
		paneEntry.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(paneEntry, Priority.ALWAYS);
		GridPane.setFillWidth(paneEntry, Boolean.TRUE);
		
		pane.add(paneEntry, 1, row);
		
		
//		Label label = new Label(entry.getName());
//		pane.add(label, 0, row);
//		label.setMinWidth(100);
////		pane.add(label, 0, row, 1, 2);
////		TextFlow textFlow = new TextFlow(new Text(entry.getDescription()));
////		pane.add(textFlow, 1, row);
////		GridPane.setHgrow(textFlow, Priority.ALWAYS);
//		
//		TextArea textArea = new TextArea(entry.getDescription());
//		textArea.setPrefColumnCount(40);
//		textArea.setPrefRowCount(3);
//		textArea.setWrapText(true);
//		textArea.setEditable(false);
////		textArea.setOpacity(0.5);
////		textArea.setStyle("-fx-background-color: transparent;");
//		// Not enough to actually achieve transparency... need to go to CSS...?
//		textArea.setBackground(Background.EMPTY);
//		for (Node node : textArea.getChildrenUnmodifiable()) {
//			if (node instanceof Region) {
//				((Region)node).setBackground(Background.EMPTY);
//			}
//		}
//		textArea.setMinWidth(100);
//		pane.add(textArea, 1, row);
//		
//		Tooltip tooltip = new Tooltip(entry.getPathToJar());
//		label.setTooltip(tooltip);
//		textArea.setTooltip(tooltip);
//		
////		pane.add(new Label(entry.getPathToJar()), 1, row+1);
//		GridPane.setHgrow(textArea, Priority.SOMETIMES);
	}
	
	
	
	private class QuPathExtensionEntry {
		
		private Object extension;
		
		QuPathExtensionEntry(final Object extension) {
			this.extension = extension;
		}

		/**
		 * Request the extension name.
		 * 
		 * @return
		 */
		public String getName() {
			if (extension instanceof QuPathExtension)
				return ((QuPathExtension) extension).getName();
			else if (extension instanceof ImageServerBuilder<?>)
				return ((ImageServerBuilder<?>) extension).getName();
			return null;
		}

		/**
		 * Request the extension description.
		 * 
		 * @return
		 */
		public String getDescription() {
			if (extension instanceof QuPathExtension)
				return ((QuPathExtension) extension).getDescription();
			else if (extension instanceof ImageServerBuilder<?>)
				return ((ImageServerBuilder<?>) extension).getDescription();
			return null;
		}
		
		URL getURL() {
			return extension.getClass().getProtectionDomain().getCodeSource().getLocation();
		}

		String getPathToJar() {
			return getURL().getPath();
		}
		
		/**
		 * Request the Implementation-Version for the jar containing the extension.
		 * <p>
		 * This tries in the first instance to locate it from a jar file directly.
		 * <p>
		 * If this fails, it falls back to requesting the value from the class package. 
		 * This was previously the only behavior (&lt; 0.1.2), but it resulted in 
		 * returning the wrong version number if the package was the same as one within
		 * another jar.
		 * 
		 * @return the String for the Implementation-Version, or null if no version could be found.
		 */
		public String getVersion() {
			URL url =  getURL();
			if (url.toString().endsWith(".jar")) {
				try (JarInputStream stream = new JarInputStream(url.openStream())) {
					if (stream != null) {
						  Manifest manifest = stream.getManifest();
						  Attributes mainAttributes = manifest.getMainAttributes();
						  return mainAttributes.getValue("Implementation-Version");						
					}
				} catch (IOException e) {
					logger.debug("Exception attempting to open manifest for {}: {}", extension, e.getLocalizedMessage());
				}					
			}
			return extension.getClass().getPackage() == null ? null : extension.getClass().getPackage().getImplementationVersion();
		}
		
	}

}
