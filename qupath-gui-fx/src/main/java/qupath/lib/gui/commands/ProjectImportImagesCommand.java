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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.GridPaneTools;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.RotatedImageServer;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
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
		promptToImportImages(qupath);
	}
	
	
	
	public static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, String... defaultPaths) {
		if (qupath.getProject() == null) {
			DisplayHelpers.showErrorMessage(commandName, "No project open!");
			return Collections.emptyList();
		}
		
		
		ListView<String> listView = new ListView<>();
		listView.setPrefSize(480, 480);
		listView.getItems().addAll(defaultPaths);
		
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

		BorderPane paneImages = new BorderPane();
		ComboBox<ImageType> comboType = new ComboBox<>();
		comboType.getItems().setAll(ImageType.values());
		Label labelType = new Label("Set image type");
		labelType.setLabelFor(comboType);
				
		ComboBox<Rotation> comboRotate = new ComboBox<>();
		comboRotate.getItems().setAll(Rotation.values());
		Label labelRotate = new Label("Rotate image");
		labelRotate.setLabelFor(comboRotate);

		GridPaneTools.setMaxWidth(Double.MAX_VALUE, comboType, comboRotate);
		GridPaneTools.setFillWidth(Boolean.TRUE, comboType, comboRotate);
		GridPaneTools.setHGrowPriority(Priority.ALWAYS, comboType, comboRotate);
		
		GridPane paneType = new GridPane();
		paneType.setPadding(new Insets(5));
		paneType.setHgap(5);
		paneType.setVgap(5);
		GridPaneTools.addGridRow(paneType, 0, 0, "Specify the default image type for all images being imported (required for analysis, can be changed later under the 'Image' tab)", labelType, comboType);
		GridPaneTools.addGridRow(paneType, 1, 0, "Optionally rotate images on import", labelRotate, comboRotate);
		
		paneImages.setCenter(paneList);
		paneImages.setBottom(paneType);
		
//		TilePane paneButtons = new TilePane();
//		paneButtons.getChildren().addAll(btnFile, btnURL, btnClipboard, btnFileList);
		GridPane paneButtons = PanelToolsFX.createColumnGridControls(btnFile, btnURL, btnClipboard, btnFileList);
		paneButtons.setHgap(5);
		paneButtons.setPadding(new Insets(5));
		
		BorderPane pane = new BorderPane();
		pane.setCenter(paneImages);
		pane.setBottom(paneButtons);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Import images to project");
		ButtonType typeImport = new ButtonType("Import", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImport, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(pane);
		
		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent() || result.get() != typeImport)
			return Collections.emptyList();
		
//		// Do the actual import
//		List<String> pathSucceeded = new ArrayList<>();
//		List<String> pathFailed = new ArrayList<>();
//		for (String path : listView.getItems()) {
//			if (qupath.getProject().addImage(path.trim()))
//				pathSucceeded.add(path);
//			else
//				pathFailed.add(path);
//		}
		
		ImageType type = comboType.getValue();
		Rotation rotation = comboRotate.getValue();
		
		List<String> pathSucceeded = new ArrayList<>();
		List<String> pathFailed = new ArrayList<>();
		var project = qupath.getProject();
		List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>();
		Task<Collection<ProjectImageEntry<BufferedImage>>> worker = new Task<>() {
			@Override
			protected Collection<ProjectImageEntry<BufferedImage>> call() throws Exception {
				AtomicLong counter = new AtomicLong(0L);
				
				// TODO: The parallel stream is bringing nothing here... refactor to return entries then add, or else add then sort later
				updateMessage("Identifying images...");
				List<ServerBuilder<BufferedImage>> builders = listView.getItems().parallelStream().map(p -> {
					try {
						var support = ImageServerProvider.getPreferredServerBuilder(BufferedImage.class, p);
						if (support != null)
							return support.getBuilders();
					} catch (IOException e) {
						logger.error("Unable to add {}", p);
					}
					return new ArrayList<ServerBuilder<BufferedImage>>();
				}).flatMap(List::stream).collect(Collectors.toList());

				long max = builders.size();

				updateMessage("Adding " + max + " images to project");

				// Add entries... we could (should?) parallelize, but that can mess up the ordering & requires more memory
				int n = builders.size();
				for (var builder : builders) {
					String name = null;
					try (var server =  builder.build()) {
						ImageServer<BufferedImage> server2 = server;
						if (rotation != null && rotation != Rotation.ROTATE_NONE) {
							server2 = new RotatedImageServer(server, rotation);
						}
						var entry = addSingleImageToProject(project, server2, type);
						if (entry != null) {
							entries.add(entry);
							name = entry.getImageName();
						}
					} catch (Exception e) {
						logger.warn("Exception adding " + builder, e);
					} finally {
						long i = counter.incrementAndGet();
						updateProgress(i, max);
						if (name != null) {
							updateMessage("Added " + i + "/" + n + " - "+ name);
						}
					}
				}
				
//				builders.parallelStream().forEach(builder -> {
////				builders.parallelStream().forEach(builder -> {
//					try (var server =  builder.build()) {
//						var entry = addSingleImageToProject(project, server);
//						updateMessage("Added " + entry.getImageName());
//					} catch (Exception e) {
//						logger.warn("Exception adding " + builder, e);
//					} finally {
//						updateProgress(counter.incrementAndGet(), max);
//					}
//				});
				
				updateProgress(max, max);
				return entries;
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
		return entries;
	}
	
	
	
	static boolean loadFromFileChooser(final List<String> list) {
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
	
	
	static boolean loadFromSingleURL(final List<String> list) {
		String path = QuPathGUI.getSharedDialogHelper().promptForFilePathOrURL("Choose image path", null, null, null);
		if (path == null)
			return false;
		if (list.contains(path)) {
			DisplayHelpers.showErrorMessage(commandName, "List already contains " + path);
			return false;
		}
		list.add(path);
		return true;
	}
	

	static int loadFromTextFile(final List<String> list) {
		File file = QuPathGUI.getSharedDialogHelper().promptForFile(commandName, null, "Text file", new String[]{"txt", "csv"});
		if (file == null)
			return 0;
		if (file.length() / 1024 / 1024 > 5) {
			DisplayHelpers.showErrorMessage(commandName, String.format("%s is too large (%.2f MB) - \n"
					+ "please choose a text file containing only file paths or select another import option", file.getName(), file.length() / 1024.0 / 1024.0));
			return 0;
		}
		return loadFromTextFile(file, list);
	}
	
	
	static int loadFromTextFile(final File file, final List<String> list) {
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
	static int loadFromClipboard(final List<String> list) {
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
	
	/**
	 * Checks is a path relates to an existing file, or a URI with a different scheme.
	 * @param path
	 * @return
	 */
	static boolean isPossiblePath(final String path) {
		try {
			var uri = GeneralTools.toURI(path);
			if ("file".equals(uri.getScheme()))
				return GeneralTools.toPath(uri).toFile().exists();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Add a single ImageServer to a project, without considering sub-images.
	 * <p>
	 * This includes an optional attempt to request a thumbnail; if this fails, the image will not be added.
	 * 
	 * @param project the project to which the entry should be added
	 * @param server the server to add
	 * @param type the ImageType that should be set for each entry being added
	 * @return
	 */
	public static ProjectImageEntry<BufferedImage> addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server, ImageType type) {
		ProjectImageEntry<BufferedImage> entry = null;
		try {
			var img = getThumbnailRGB(server, null);
			entry = project.addImage(server);
			if (entry != null) {
				// Write a thumbnail if we can
				entry.setThumbnail(img);
				// Initialize an ImageData object with a type, if required
				if (type != null) {
					var imageData = new ImageData<>(server, type);
					entry.saveImageData(imageData);
				}
			}
		} catch (IOException e) {
			logger.warn("Error attempting to add " + server, e);
		}
		return entry;
	}
	
	
	public static BufferedImage getThumbnailRGB(ImageServer<BufferedImage> server, ImageDisplay imageDisplay) throws IOException {
		var img2 = server.getDefaultThumbnail(server.nZSlices()/2, 0);
		// Try to write RGB images directly
		boolean success = false;
		if (imageDisplay == null && (server.isRGB() || img2.getType() == BufferedImage.TYPE_BYTE_GRAY)) {
			return resizeForThumbnail(img2);
		}
		if (!success) {
			// Try with display transforms
			if (imageDisplay == null)
				// By wrapping the thumbnail, we avoid slow z-stack/time series requests & determine brightness & contrast just from one plane
				imageDisplay = new ImageDisplay(new ImageData<>(new WrappedBufferedImageServer("Dummy", img2)));
//				imageDisplay = new ImageDisplay(new ImageData<>(server));
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