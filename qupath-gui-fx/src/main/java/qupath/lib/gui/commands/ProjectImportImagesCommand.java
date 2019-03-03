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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Command to import image paths into an existing project.
 * 
 * @author Pete Bankhead
 */
public class ProjectImportImagesCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectImportImagesCommand.class);
	
	private static final String commandName = "Project: Import images";

	private QuPathGUI qupath;
	
	public ProjectImportImagesCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		if (qupath.getProject() == null) {
			DisplayHelpers.showErrorMessage(commandName, "No project open!");
			return;
		}
		
		
		ListView<String> listView = new ListView<>();
		listView.setPrefSize(480, 480);
		
		Button btnFile = new Button("Choose files");
		btnFile.setOnAction(e -> loadFromFileChooser(listView.getItems()));

		Button btnURL = new Button("Input URL");
		btnURL.setOnAction(e -> loadFromSingleURL(listView.getItems()));

		Button btnClipboard = new Button("From clipboard");
		btnClipboard.setOnAction(e -> loadFromClipboard(listView.getItems()));
		
		Button btnFileList = new Button("From path list");
		btnFileList.setOnAction(e -> loadFromTextFile(listView.getItems()));
		
		TitledPane paneList = new TitledPane("Image paths", listView);
		paneList.setCollapsible(false);
		
//		TilePane paneButtons = new TilePane();
//		paneButtons.getChildren().addAll(btnFile, btnURL, btnClipboard, btnFileList);
		GridPane paneButtons = PanelToolsFX.createColumnGridControls(btnFile, btnURL, btnClipboard, btnFileList);
		
		BorderPane pane = new BorderPane();
		pane.setCenter(paneList);
		pane.setBottom(paneButtons);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Import images to project");
		ButtonType typeImport = new ButtonType("Import", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImport, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(pane);
		
		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent() || result.get() != typeImport)
			return;
		
//		// Do the actual import
//		List<String> pathSucceeded = new ArrayList<>();
//		List<String> pathFailed = new ArrayList<>();
//		for (String path : listView.getItems()) {
//			if (qupath.getProject().addImage(path.trim()))
//				pathSucceeded.add(path);
//			else
//				pathFailed.add(path);
//		}
		
		
		List<String> pathSucceeded = new ArrayList<>();
		List<String> pathFailed = new ArrayList<>();
		var project = qupath.getProject();
		Task<Void> worker = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				long max = listView.getItems().size();
				AtomicLong counter = new AtomicLong(0L);
				
				listView.getItems().parallelStream().forEachOrdered(p -> {
					try (var server = ImageServerProvider.buildServer(p, BufferedImage.class)) {
						addImageAndSubImagesToProject(project, server);
						updateProgress(counter.incrementAndGet(), max);
					} catch (Exception e) {
						logger.warn("Exception adding " + p, e);
					}
				});
				
				updateProgress(max, max);
				return null;
	         }
		};
		ProgressDialog progress = new ProgressDialog(worker);
		progress.setTitle("Project import");
		qupath.submitShortTask(worker);
		progress.showAndWait();
		try {
			project.syncChanges();
		} catch (IOException e1) {
			DisplayHelpers.showErrorMessage("Sync project", e1);
		}
		qupath.refreshProject();
		
		StringBuilder sb = new StringBuilder();
		if (!pathSucceeded.isEmpty()) {
			sb.append("Successfully imported " + pathSucceeded.size() + " paths:\n");
			for (String path : pathSucceeded)
				sb.append("\t" + path + "\n");
			sb.append("\n");
			qupath.refreshProject();
			ProjectBrowser.syncProject(qupath.getProject());
		}
		if (!pathFailed.isEmpty()) {
			sb.append("Unable to import " + pathFailed.size() + " paths:\n");
			for (String path : pathFailed)
				sb.append("\t" + path + "\n");
			sb.append("\n");
		}
		if (!pathFailed.isEmpty()) {
			TextArea textArea = new TextArea();
			textArea.setText(sb.toString());
			if (pathSucceeded.isEmpty())
				DisplayHelpers.showErrorMessage(commandName, textArea);
			else
				DisplayHelpers.showMessageDialog(commandName, textArea);
		}
		logger.info(sb.toString());
		
//		TextArea textArea = new TextArea();
//		if (!pathSucceeded.isEmpty()) {
//			textArea.appendText("Successfully imported " + pathSucceeded.size() + " paths:\n");
//			for (String path : pathSucceeded)
//				textArea.appendText("\t" + path + "\n");
//			textArea.appendText("\n");
//		}
//		if (!pathFailed.isEmpty()) {
//			textArea.appendText("Unable to import " + pathFailed.size() + " paths:\n");
//			for (String path : pathFailed)
//				textArea.appendText("\t" + path + "\n");
//			textArea.appendText("\n");
//		}
//		textArea.setEditable(false);
//		DisplayHelpers.showMessageDialog(commandName, textArea);
	}
	
	
	boolean loadFromFileChooser(final List<String> list) {
		List<File> files = QuPathGUI.getSharedDialogHelper().promptForMultipleFiles(commandName, null, null);
		if (files == null)
			return false;
		boolean changes = false;
		for (File fileNew : files) {
			if (list.contains(fileNew.getAbsolutePath())) {
				DisplayHelpers.showErrorMessage(commandName, "List already contains " + fileNew.getName());
				continue;
			}
			list.add(fileNew.getAbsolutePath());
			changes = true;
		}
		return changes;
	}
	
	
	boolean loadFromSingleURL(final List<String> list) {
		String path = QuPathGUI.getSharedDialogHelper().promptForFilePathOrURL("Enter image path", null, null, null);
		if (path == null)
			return false;
		if (list.contains(path)) {
			DisplayHelpers.showErrorMessage(commandName, "List already contains " + path);
			return false;
		}
		list.add(path);
		return true;
	}
	

	int loadFromTextFile(final List<String> list) {
		File file = qupath.getDialogHelper().promptForFile(commandName, null, "Text file", new String[]{"txt", "csv"});
		if (file == null)
			return 0;
		if (file.length() / 1024 / 1024 > 5) {
			DisplayHelpers.showErrorMessage(commandName, String.format("%s is too large (%.2f MB) - \n"
					+ "please choose a text file containing only file paths or select another import option", file.getName(), file.length() / 1024.0 / 1024.0));
			return 0;
		}
		return loadFromTextFile(file, list);
	}
	
	
	int loadFromTextFile(final File file, final List<String> list) {
		Scanner scanner = null;
		int changes = 0;
		try {
			scanner = new Scanner(file);
			while (scanner.hasNextLine()) {
				String s = scanner.nextLine().trim();
				if (isPossiblePath(s) && !list.contains(s)) {
					list.add(s);
					changes++;
				}
			}
		} catch (FileNotFoundException e) {
			DisplayHelpers.showErrorMessage(commandName, "File " + file.getName() + " not found!");
			return 0;
		} finally {
			if (scanner != null)
				scanner.close();
		}
		return changes;
	}
	
	
	/**
	 * Load potential image paths into a list.
	 * 
	 * @param list
	 */
	int loadFromClipboard(final List<String> list) {
		int changes = 0;
		List<File> clipboardFiles = Clipboard.getSystemClipboard().getFiles();
		if (clipboardFiles != null) {
			for (File f : clipboardFiles) {
				if (f.isFile() || !list.contains(f.getAbsolutePath())) {
					list.add(f.getAbsolutePath());
					changes++;
				}
			}
		}
		if (changes > 0)
			return changes;
		
		String clipboardString = Clipboard.getSystemClipboard().getString();
		List<String> possiblePaths = new ArrayList<>();
		if (clipboardString != null) {
			for (String s : GeneralTools.splitLines(clipboardString)) {
				if (isPossiblePath(s.trim()))
					possiblePaths.add(s.trim());
			}
		}
		if (possiblePaths.isEmpty()) {
			DisplayHelpers.showErrorMessage(commandName, "Could not find any valid paths on the clipboard!");
			return 0;
		}
		possiblePaths.removeAll(list);
		list.addAll(possiblePaths);
		return possiblePaths.size();
	}
	
	
	static boolean isPossiblePath(final String path) {
		return path.toLowerCase().startsWith("http") || new File(path).isFile();
	}
	
	
	public static boolean addImageAndSubImagesToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server) {
		var subImages = server.getSubImageList();
		if (subImages.isEmpty())
			return addSingleImageToProject(project, server);
		boolean changes = false;
		for (var name : subImages) {
			// TODO: Consider using specifically this server class
			var path = server.getSubImagePath(name);
			try {
				var server2 = ImageServerProvider.buildServer(path, BufferedImage.class);
				changes = changes | addSingleImageToProject(project, server2);
			} catch (IOException e) {
				logger.warn("Could not build server for " + name + " (" + path + ")", e);
			}
		}
		return changes;
	}
	
	/**
	 * Add a single ImageServer to a project, without considering sub-images.
	 * <p>
	 * This includes an optional attempt to request a thumbnail; if this fails, the image will not be added.
	 * 
	 * @param project
	 * @param server
	 * @return
	 */
	public static boolean addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server) {
		ProjectImageEntry<BufferedImage> entry = null;
		try {
			var img = getThumbnailRGB(server, null);
			entry = project.addImage(server);
			if (entry != null)
				entry.setThumbnail(img);
		} catch (IOException e) {
			logger.warn("Error attempting to add " + server, e);
		}
		return entry != null;
	}
	
	
	static BufferedImage getThumbnailRGB(ImageServer<BufferedImage> server, ImageDisplay imageDisplay) throws IOException {
		var img2 = server.getDefaultThumbnail();
		// Try to write RGB images directly
		boolean success = false;
		if (imageDisplay == null && (server.isRGB() || img2.getType() == BufferedImage.TYPE_BYTE_GRAY)) {
			return resizeForThumbnail(img2);
		}
		if (!success) {
			// Try with display transforms
			if (imageDisplay == null)
				imageDisplay = new ImageDisplay(new ImageData<>(server));
			for (ChannelDisplayInfo info : imageDisplay.selectedChannels()) {
				imageDisplay.autoSetDisplayRange(info);
			}
			img2 = imageDisplay.applyTransforms(img2, null);
			return resizeForThumbnail(img2);
		}
		return img2;
	}
	
	private static int thumbnailWidth = 1000;
	private static int thumbnailHeight = 600;

	
	/**
	 * Resize an image so that its dimensions fit inside thumbnailWidth x thumbnailHeight.
	 * 
	 * Note: this assumes the image can be drawn to a Graphics object.
	 * 
	 * @param imgThumbnail
	 * @return
	 */
	static BufferedImage resizeForThumbnail(BufferedImage imgThumbnail) {
		double scale = Math.min((double)thumbnailWidth / imgThumbnail.getWidth(), (double)thumbnailHeight / imgThumbnail.getHeight());
		if (scale > 1)
			return imgThumbnail;
		BufferedImage imgThumbnail2 = new BufferedImage((int)(imgThumbnail.getWidth() * scale), (int)(imgThumbnail.getHeight() * scale), imgThumbnail.getType());
		Graphics2D g2d = imgThumbnail2.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(imgThumbnail, 0, 0, imgThumbnail2.getWidth(), imgThumbnail2.getHeight(), null);
		g2d.dispose();
		return imgThumbnail2;
	}
	
	
	
}