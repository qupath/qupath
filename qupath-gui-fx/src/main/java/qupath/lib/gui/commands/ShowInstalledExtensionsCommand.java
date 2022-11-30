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
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;

/**
 * Command to list the names &amp; details of all installed extensions
 * 
 * @author Pete Bankhead
 *
 */
class ShowInstalledExtensionsCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(ShowInstalledExtensionsCommand.class);
	
	
	public static void showInstalledExtensions(final QuPathGUI qupath) {
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
		
		TitledPane titledExtensions = new TitledPane("Extensions", paneExtensions);
		PaneTools.simplifyTitledPane(titledExtensions, false);
		TitledPane titledServers = new TitledPane("Image Servers", paneServers);
		PaneTools.simplifyTitledPane(titledServers, false);
		
		VBox vbox = new VBox(
				titledExtensions,
				titledServers
		);
		
		var dir = QuPathGUI.getExtensionDirectory();
		if (dir != null) {
			var btnOpen = new Button("Open extensions directory");
			btnOpen.setOnAction(e -> GuiTools.browseDirectory(dir));
			btnOpen.setMaxWidth(Double.MAX_VALUE);
			vbox.getChildren().add(btnOpen);
//		} else {
//			var label = new Label("No user directory has been set for custom user extensions.\n"
//					+ "Drag an extensions jar file onto QuPath to set a user directory and install the extension.");
//			label.setTextAlignment(TextAlignment.CENTER);
//			label.setMaxWidth(Double.MAX_VALUE);
//			vbox.getChildren().add(label);
		}
		
		vbox.setPadding(new Insets(5));

		vbox.setMaxWidth(Double.POSITIVE_INFINITY);
		dialog.setScene(new Scene(new ScrollPane(vbox)));
		dialog.show();
	}
	
	
	private static void addEntry(final GridPane pane, final QuPathExtensionEntry entry, final int row) {
		
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
						GuiTools.openFile(dir);
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
		PaneTools.simplifyTitledPane(paneEntry, false);

		
//		Tooltip tooltip = new Tooltip(entry.getPathToJar());
		paneEntry.setTooltip(new Tooltip(entry.getDescription()));
//		textArea.setTooltip(tooltip);
		textArea.setMaxWidth(Double.MAX_VALUE);
		paneEntry.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(paneEntry, Priority.ALWAYS);
		GridPane.setFillWidth(paneEntry, Boolean.TRUE);
		
		pane.add(paneEntry, 1, row);
	}
	
	
	
	private static class QuPathExtensionEntry {
		
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
			var url = getURL();
			if (url == null)
				return "";
			try {
				var path = GeneralTools.toPath(url.toURI());
				if (path != null)
					return path.toString();
			} catch (URISyntaxException e) {
				logger.debug(e.getLocalizedMessage(), e);
			}
			return url.toString();
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
			if (extension instanceof QuPathExtension) {
				var v = ((QuPathExtension)extension).getVersion();
				if (v != null && !Objects.equals(v, Version.UNKNOWN))
					return v.toString();
			}
			URL url =  getURL();
			if (url.toString().endsWith(".jar")) {
				try (JarInputStream stream = new JarInputStream(url.openStream())) {
					if (stream != null) {
						  Manifest manifest = stream.getManifest();
						  if (manifest != null) {
							  Attributes mainAttributes = manifest.getMainAttributes();
							  return mainAttributes.getValue("Implementation-Version");	
						  }
					}
				} catch (IOException e) {
					logger.debug("Exception attempting to open manifest for {}: {}", extension, e.getLocalizedMessage());
				}					
			}
			return extension.getClass().getPackage() == null ? null : extension.getClass().getPackage().getImplementationVersion();
		}
		
	}

}
