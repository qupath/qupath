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
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
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

		// Create a suitable String to show for QuPath generally
		StringBuilder sbQuPath = new StringBuilder();
		String buildString = qupath.getBuildString();
		if (buildString != null && !buildString.trim().isEmpty()) {
			sbQuPath.append("QuPath").append("\n");
			sbQuPath.append(buildString).append("\n\n");
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
			File dirBase = currentFile;
			if (new File(dirBase, "licenses").isDirectory())
				fileLicenses = new File(dirBase, "THIRD-PARTY.txt");
			else if (new File(dirBase.getParentFile(), "licenses").isDirectory())
				fileLicenses = new File(new File(dirBase.getParentFile(), "licenses"), "THIRD-PARTY.txt");
			logger.debug("License file: {}", fileLicenses);
		}
		
		// Create a QuPath tab
		TextArea textAreaQuPath = getTextArea(sbQuPath.toString());
		Tab tabQuPath = new Tab("QuPath", textAreaQuPath);
		
		// Create a TabPane
		TabPane tabPane = new TabPane(tabQuPath);
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
		// Add extra tabs
		if (fileLicenses == null) {
			// Indicate no third-party info found - probably running from an IDE...?
			tabPane.getTabs().add(new Tab("Third party", new TextArea("No license information could be found for third party dependencies.")));
		} else {
			// Include a QuPath license more prominently as its own tab, if possible
			File filePrimaryLicense = new File(new File(fileLicenses.getParentFile(), "QuPath"), "LICENSE.txt");
			if (filePrimaryLicense.isFile()) {
				try {
					tabPane.getTabs().add(new Tab("License", new TextArea(GeneralTools.readFileAsString(filePrimaryLicense.getAbsolutePath()))));									
				} catch (Exception e) {
					logger.error("Could not show QuPath's primary license file");
				}
			}
			// Create a third-party licenses tab
			tabPane.getTabs().add(new Tab("Third party", createLicenseTreePane(fileLicenses.getParentFile())));
		}
		
		// Create and show dialog
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Licenses");
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

	/**
	 * Create a pane for displaying license information in a readable form.
	 * 
	 * @param dirBase
	 * @return
	 */
	private Pane createLicenseTreePane(final File dirBase) {
		TreeView<File> tree = new TreeView<>();
		tree.setRoot(new LicenseFileTreeItem(dirBase));
		tree.setShowRoot(false);
		
		TextArea textArea = new TextArea();
		textArea.setStyle("-fx-font-family: monospace");
		textArea.setWrapText(true);
		
		// Show content for selected node
		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n instanceof LicenseFileTreeItem)
				textArea.setText(((LicenseFileTreeItem)n).getContents());
		});
		
		tree.setCellFactory(n -> new FileTreeCell());
		
		Button btnThirdParty = new Button("Open licenses directory");
		File dirLicenses = dirBase;
		btnThirdParty.setOnAction(e -> {
			DisplayHelpers.openFile(dirLicenses);
		});
		btnThirdParty.setMaxWidth(Double.MAX_VALUE);
		

		BorderPane pane = new BorderPane();
		pane.setLeft(tree);
		pane.setCenter(textArea);
		pane.setBottom(btnThirdParty);
		
		return pane;
	}

	
	/**
	 * Display a file by using its name (rather than absolute path).
	 */
	static class FileTreeCell extends TreeCell<File> {
		
		@Override
        public void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
            	String name = item.getName();
            	if ("THIRD-PARTY.txt".equals(name))
            		name = "Summary";
                setText(name);
                setGraphic(null);
            }
        }
		
	}
	
	
	/**
	 * TreeItem to help with the display of license information.
	 */
	static class LicenseFileTreeItem extends TreeItem<File> {
		
		private static FileFirstComparator comparator = new FileFirstComparator();

		private boolean isDirectory;
		private boolean hasCheckedChildren;
		
		private String contents;

		LicenseFileTreeItem(final File file) {
			super(file);
			isDirectory = file.isDirectory();
			hasCheckedChildren = !isDirectory;
		}

		@Override
		public ObservableList<TreeItem<File>> getChildren() {
			if (!hasCheckedChildren) {
				hasCheckedChildren = true;
				super.getChildren().setAll(
						Arrays.stream(getValue().listFiles())
							.filter(f -> !f.isHidden())
							.sorted(comparator)
							.map(f -> new LicenseFileTreeItem(f))
							.collect(Collectors.toList()));
			}
			return super.getChildren();
		}
		
		public String getName() {
			return getValue().getName();
		}
		
		public String getContents() {
			if (contents == null) {
				if (isDirectory) {
					StringBuilder sb = new StringBuilder();
					for (TreeItem<File> child : getChildren()) {
						if (child instanceof LicenseFileTreeItem) {
							LicenseFileTreeItem item = (LicenseFileTreeItem)child;
							sb.append(item.getName());
							sb.append("\n");
							for (int i = 0; i < item.getName().length(); i++)
								sb.append("=");
							sb.append("\n\n");
							sb.append(item.getContents());
							// Don't add another END after a directory - will already be one after any previous files
							if (!item.isDirectory) {
								sb.append("\n\n\n");
								sb.append("==========================");
								sb.append("END");
								sb.append("==========================");
								sb.append("\n\n");
							}
						}
					}
					contents = sb.toString();
					
//					contents = "Directory";
				} else {
					try {
						contents = GeneralTools.readFileAsString(getValue().getAbsolutePath());
					} catch (IOException e) {
						contents = "Unable to read from " + getValue();
					}
				}
			}
			return contents;
		}

		@Override
		public boolean isLeaf() {
			return !isDirectory;
		}
		
		@Override
		public String toString() {
			return getName();
		}

	}
	
	/**
	 * Comparator that returns files before directories.
	 */
	static class FileFirstComparator implements Comparator<File> {

		@Override
		public int compare(File f1, File f2) {
			if (f1.isFile()) {
				if (!f2.isFile())
					return -1;
			} else if (f2.isFile())
				return 1;
			return f1.compareTo(f2);
		}
		
	}


}