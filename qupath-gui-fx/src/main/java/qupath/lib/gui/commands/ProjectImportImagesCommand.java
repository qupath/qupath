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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Command to import image paths into an existing project.
 * 
 * @author Pete Bankhead
 */
class ProjectImportImagesCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectImportImagesCommand.class);
		
	private final static String commandName = "Import images";
	
	private final static BooleanProperty pyramidalizeProperty = PathPrefs.createPersistentPreference("projectImportPyramidalize", true);
	private final static BooleanProperty importObjectsProperty = PathPrefs.createPersistentPreference("projectImportObjects", false);

	
	/**
	 * Prompt to import images to the current project.
	 * 
	 * 
	 * @param qupath QuPath instance, used to access the current project and stage
	 * @param builder if not null, this will be used to create the servers. If null, a combobox will be shown to choose an installed builder.
	 * @param defaultPaths URIs to use to prepopulate the list
	 * @return
	 */
	static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, ImageServerBuilder<BufferedImage> builder, String... defaultPaths) {
		var project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError(commandName);
			return Collections.emptyList();
		}
		
		ListView<String> listView = new ListView<>();
		listView.setPrefWidth(480);
		listView.setMinHeight(100);
		listView.getItems().addAll(defaultPaths);
		listView.setPlaceholder(new Label("Drag & drop image or project files for import, \nor choose from the options below"));
		
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
		
		class BuilderListCell extends ListCell<ImageServerBuilder<BufferedImage>> {
			@Override
			protected void updateItem(ImageServerBuilder<BufferedImage> item, boolean empty) {
	             super.updateItem(item, empty);
	             if (empty) {
	            	 setText(null);
	             } else {
	            	 if (item == null)
	            		 setText("Default (let QuPath decide)");
	            	 else
	            		 setText(item.getName());
	             }
	         }
		}
		
		boolean requestBuilder = builder == null;
		ComboBox<ImageServerBuilder<BufferedImage>> comboBuilder = new ComboBox<>();
		Label labelBuilder = new Label("Image provider");
		if (requestBuilder) {
			comboBuilder.setCellFactory(p -> new BuilderListCell());
			comboBuilder.setButtonCell(new BuilderListCell());
			List<ImageServerBuilder<BufferedImage>> availableBuilders = new ArrayList<>(ImageServerProvider.getInstalledImageServerBuilders(BufferedImage.class));
			if (!availableBuilders.contains(null))
				availableBuilders.add(0, null);
			comboBuilder.getItems().setAll(availableBuilders);
			comboBuilder.getSelectionModel().selectFirst();
			labelBuilder.setLabelFor(comboBuilder);
			labelBuilder.setMinWidth(Label.USE_PREF_SIZE);
		}
		
		ComboBox<ImageType> comboType = new ComboBox<>();
		comboType.getItems().setAll(ImageType.values());
		Label labelType = new Label("Set image type");
		labelType.setLabelFor(comboType);
		labelType.setMinWidth(Label.USE_PREF_SIZE);
				
		ComboBox<Rotation> comboRotate = new ComboBox<>();
		comboRotate.getItems().setAll(Rotation.values());
		Label labelRotate = new Label("Rotate image");
		labelRotate.setLabelFor(comboRotate);
		labelRotate.setMinWidth(Label.USE_PREF_SIZE);
		
		TextField tfArgs = new TextField();
		Label labelArgs = new Label("Optional args");
		labelArgs.setLabelFor(tfArgs);
		labelArgs.setMinWidth(Label.USE_PREF_SIZE);
		
		CheckBox cbPyramidalize = new CheckBox("Auto-generate pyramids");
		cbPyramidalize.setSelected(pyramidalizeProperty.get());
		
		CheckBox cbImportObjects = new CheckBox("Import objects");
		cbImportObjects.setSelected(importObjectsProperty.get());

		PaneTools.setMaxWidth(Double.MAX_VALUE, comboBuilder, comboType, comboRotate, cbPyramidalize, cbImportObjects, tfArgs);
		PaneTools.setFillWidth(Boolean.TRUE, comboBuilder, comboType, comboRotate, cbPyramidalize, cbImportObjects, tfArgs);
		PaneTools.setHGrowPriority(Priority.ALWAYS, comboBuilder, comboType, comboRotate, cbPyramidalize, cbImportObjects, tfArgs);
		
		GridPane paneType = new GridPane();
		paneType.setPadding(new Insets(5));
		paneType.setHgap(5);
		paneType.setVgap(5);
		int row = 0;
		if (requestBuilder)
			PaneTools.addGridRow(paneType, row++, 0, "Specify the library used to open images", labelBuilder, comboBuilder);
		PaneTools.addGridRow(paneType, row++, 0, "Specify the default image type for all images being imported (required for analysis, can be changed later under the 'Image' tab)", labelType, comboType);
		PaneTools.addGridRow(paneType, row++, 0, "Optionally rotate images on import", labelRotate, comboRotate);
		PaneTools.addGridRow(paneType, row++, 0, "Optionally pass reader-specific arguments to the image provider.\nUsually this should just be left empty.", labelArgs, tfArgs);
		PaneTools.addGridRow(paneType, row++, 0, "Dynamically create image pyramids for large, single-resolution images", cbPyramidalize, cbPyramidalize);
		PaneTools.addGridRow(paneType, row++, 0, "Read and import objects (e.g. annotations) from the image file, if possible", cbImportObjects, cbImportObjects);
		
		paneImages.setCenter(paneList);
		paneImages.setBottom(paneType);
		
//		TilePane paneButtons = new TilePane();
//		paneButtons.getChildren().addAll(btnFile, btnURL, btnClipboard, btnFileList);
		GridPane paneButtons = PaneTools.createColumnGridControls(btnFile, btnURL, btnClipboard, btnFileList);
		paneButtons.setHgap(5);
		paneButtons.setPadding(new Insets(5));
		
		BorderPane pane = new BorderPane();
		pane.setCenter(paneImages);
		pane.setBottom(paneButtons);
		
		// Support drag & drop for files
		pane.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
		pane.setOnDragDropped(e -> {
			Dragboard dragboard = e.getDragboard();
			if (dragboard.hasFiles()) {
		        logger.trace("Files dragged onto project import dialog");
				try {
					var paths = dragboard.getFiles()
							.stream()
							.filter(f -> f.isFile() && !f.isHidden())
							.map(f -> f.getAbsolutePath())
							.collect(Collectors.toList());
					paths.removeAll(listView.getItems());
					if (!paths.isEmpty())
						listView.getItems().addAll(paths);
				} catch (Exception ex) {
					Dialogs.showErrorMessage("Drag & Drop", ex);
				}
			}
			e.setDropCompleted(true);
			e.consume();
        });
		
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setResizable(true);
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Import images to project");
		ButtonType typeImport = new ButtonType("Import", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImport, ButtonType.CANCEL);
		ScrollPane scroll = new ScrollPane(pane);
		scroll.setFitToHeight(true);
		scroll.setFitToWidth(true);
		dialog.getDialogPane().setContent(scroll);
		
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
		boolean pyramidalize = cbPyramidalize.isSelected();
		boolean importObjects = cbImportObjects.isSelected();
		pyramidalizeProperty.set(pyramidalize);
		importObjectsProperty.set(importObjects);
		
		ImageServerBuilder<BufferedImage> requestedBuilder = requestBuilder ? comboBuilder.getSelectionModel().getSelectedItem() : builder;
		
		List<String> argsList = new ArrayList<>();
		
		String argsString = tfArgs.getText();
		// TODO: Use a smarter approach to splitting! Currently we support so few arguments that splitting on spaces should be ok... for now.
		String[] argsSplit = argsString == null || argsString.isBlank() ? new String[0] : argsString.split(" ");
		for (var a : argsSplit) {
			argsList.add(a);
		}
		if (rotation != null && rotation != Rotation.ROTATE_NONE) {
			argsList.add("--rotate");
			argsList.add(rotation.toString());
		}
		if (!argsList.isEmpty())
			logger.debug("Args: [{}]", argsList.stream().collect(Collectors.joining(", ")));
		String[] args = argsList.toArray(String[]::new);
		
		List<String> pathSucceeded = new ArrayList<>();
		List<String> pathFailed = new ArrayList<>();
		List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>();
		Task<Collection<ProjectImageEntry<BufferedImage>>> worker = new Task<>() {
			@Override
			protected Collection<ProjectImageEntry<BufferedImage>> call() throws Exception {
				AtomicLong counter = new AtomicLong(0L);
				
				List<String> items = new ArrayList<>(listView.getItems());

				updateMessage("Checking for compatible image readers...");
				
				// Limit the size of the thread pool
				// The previous use of a cached thread pool caused trouble when importing may large, non-pyramidal images
				var pool = Executors.newFixedThreadPool(PathPrefs.numCommandThreadsProperty().get(), ThreadTools.createThreadFactory("project-import", true));
//				var pool = Executors.newCachedThreadPool(ThreadTools.createThreadFactory("project-import", true));
				List<Future<List<ServerBuilder<BufferedImage>>>> results = new ArrayList<>();
				List<ProjectImageEntry<BufferedImage>> projectImages = new ArrayList<>();
				for (var item : items) {
					// Try to load items from a project if possible
					if (item.toLowerCase().endsWith(ProjectIO.DEFAULT_PROJECT_EXTENSION)) {
						try {
							var tempProject = ProjectIO.loadProject(GeneralTools.toURI(item), BufferedImage.class);
							projectImages.addAll(tempProject.getImageList());
						} catch (Exception e) {
							logger.error("Unable to add images from {} ({})", item, e.getLocalizedMessage());
						}
						continue;
					}
					results.add(pool.submit(() -> {
						try {
							var uri = GeneralTools.toURI(item);
							UriImageSupport<BufferedImage> support;
							if (requestedBuilder == null)
								support = ImageServers.getImageSupport(uri, args);
							else
								support = ImageServers.getImageSupport(requestedBuilder, uri, args);
							if (support != null)
								return support.getBuilders();
						} catch (Exception e) {
							logger.error("Unable to add {}");
							logger.error(e.getLocalizedMessage(), e);
						}
						return new ArrayList<ServerBuilder<BufferedImage>>();
					}));
				}
				
				List<ProjectImageEntry<BufferedImage>> failures = Collections.synchronizedList(new ArrayList<>());
				
				// If we have projects, try adding images from these first
				if (!projectImages.isEmpty()) {
					if (projectImages.size() == 1)
						updateMessage("Importing 1 image from existing projects");
					else
						updateMessage("Importing " + projectImages.size() + " images from existing projects");
					for (var temp : projectImages) {
						try {
							project.addDuplicate(temp, true);
						} catch (Exception e) {
							failures.add(temp);
						}
					}
				}
				
				// If we have 'standard' image paths, use these next
				List<ServerBuilder<BufferedImage>> builders = new ArrayList<>();
				for (var result : results) {
					try {
						builders.addAll(result.get());
					} catch (ExecutionException e) {
						logger.error("Execution exception importing image", e);
					}
				}
				
				long max = builders.size();
				if (!builders.isEmpty()) {
					if (max == 1)
						updateMessage("Adding 1 image to project");
					else
						updateMessage("Adding " + max + " images to project");
					
					// Add everything in order first
					List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>();
					for (var builder : builders) {
//						if (rotation != null && rotation != Rotation.ROTATE_NONE)
//							builder = RotatedImageServer.getRotatedBuilder(builder, rotation);
//						if (swapRedBlue)
//							builder = RearrangeRGBImageServer.getSwapRedBlueBuilder(builder);
						entries.add(project.addImage(builder));
					}
					
					// Initialize (the slow bit)
					int n = builders.size();
					for (var entry : entries) {
						pool.submit(() -> {
							try {
								initializeEntry(entry, type, pyramidalize, importObjects);
							} catch (Exception e) {
								failures.add(entry);
								logger.warn("Exception adding " + entry, e);
							} finally {
								long i = counter.incrementAndGet();
								updateProgress(i, max);
								String name = entry.getImageName();
								if (name != null) {
									updateMessage("Added " + i + "/" + n + " - "+ name);
								}
							}
						});
					}
				}
				pool.shutdown();
				try {
					pool.awaitTermination(60, TimeUnit.MINUTES);
				} catch (Exception e) {
					logger.error("Exception waiting for project import to complete: " + e.getLocalizedMessage(), e);
				}

				if (!failures.isEmpty()) {
					String message;
					if (failures.size() == 1)
						message = "Failed to load one image.";
					else
						message = "Failed to load " + failures.size() + " images.";
					if (requestedBuilder != null)
						message += "\nThe image type might not be supported by '" + requestedBuilder.getName() + "'";
					Dialogs.showErrorMessage("Import images", message);
					
					var toRemove = failures.stream().filter(p -> project.getImageList().contains(p)).collect(Collectors.toList());
					project.removeAllImages(toRemove, true);
				}
				
				// Now save changes
				project.syncChanges();
				
				
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
			Dialogs.showErrorMessage("Sync project", e1);
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

			TextArea textArea = new TextArea();
			textArea.setText(sb.toString());
			if (pathSucceeded.isEmpty())
				Dialogs.showErrorMessage(commandName, textArea);
			else
				Dialogs.showMessageDialog(commandName, textArea);
		}
		// TODO: Add failed and successful paths to pathFailed/pathSucceeded, so the line below prints something
		if (sb.length() > 0)
			logger.info(sb.toString());
		return entries;
	}
	
	
	
	public static ProjectImageEntry<BufferedImage> addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server, ImageType type) {
		try {
			var entry = project.addImage(server.getBuilder());
			initializeEntry(entry, type, false, false);
			return entry;
		} catch (Exception e) {
			return null;
		}
	}
	
	
	static boolean loadFromFileChooser(final List<String> list) {
		List<File> files = Dialogs.promptForMultipleFiles(commandName, null, null);
		if (files == null)
			return false;
		boolean changes = false;
		for (File fileNew : files) {
			if (list.contains(fileNew.getAbsolutePath())) {
				Dialogs.showErrorMessage(commandName, "List already contains " + fileNew.getName());
				continue;
			}
			list.add(fileNew.getAbsolutePath());
			changes = true;
		}
		return changes;
	}
	
	
	static boolean loadFromSingleURL(final List<String> list) {
		String path = Dialogs.promptForFilePathOrURL("Choose image path", null, null, null);
		if (path == null)
			return false;
		if (list.contains(path)) {
			Dialogs.showErrorMessage(commandName, "List already contains " + path);
			return false;
		}
		list.add(path);
		return true;
	}
	

	static int loadFromTextFile(final List<String> list) {
		File file = Dialogs.promptForFile(commandName, null, "Text file", new String[]{"txt", "csv"});
		if (file == null)
			return 0;
		if (file.length() / 1024 / 1024 > 5) {
			Dialogs.showErrorMessage(commandName, String.format("%s is too large (%.2f MB) - \n"
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
				} else
					logger.warn("Cannot find image for path {}", s.trim());
			}
		} catch (FileNotFoundException e) {
			Dialogs.showErrorMessage(commandName, "File " + file.getName() + " not found!");
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
				else
					logger.warn("Cannot find image for path {}", s.trim());
			}
		}
		if (possiblePaths.isEmpty()) {
			Dialogs.showErrorMessage(commandName, "Could not find any valid paths on the clipboard!");
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
			URI uri;
			if (path.toLowerCase().startsWith("http") || path.toLowerCase().startsWith("file"))
				uri = GeneralTools.toEncodedURI(path);
			else
				uri = new File(path).toURI();
			if ("file".equals(uri.getScheme())) {
				if (GeneralTools.toPath(uri).toFile().exists())
					return true;
				else {
					logger.warn("File {} does not exist!", GeneralTools.toPath(uri));
					return false;
				}
			}
			return true;
		} catch (Exception e) {
			logger.debug("Exception trying to parse path " + path + ": " + e.getLocalizedMessage(), e);
			return false;
		}
	}
	
	/**
	 * Add a single ImageServer to a project, without considering sub-images.
	 * <p>
	 * This includes an optional attempt to request a thumbnail; if this fails, the image will not be added.
	 * 
	 * @param entry the entry that should be initialized
	 * @param type the ImageType that should be set for each entry being added
	 * @param pyramidalizeSingleResolution if true, attempt to pyramidalize single-resolution image servers
	 * @param importObjects if true, read objects from the server - if available
	 * @return
	 * @throws Exception 
	 */
	static ProjectImageEntry<BufferedImage> initializeEntry(ProjectImageEntry<BufferedImage> entry, ImageType type, boolean pyramidalizeSingleResolution, boolean importObjects) throws Exception {
		try (ImageServer<BufferedImage> server = entry.getServerBuilder().build()) {
			var img = getThumbnailRGB(server, null);
			// Set the image name
			String name = ServerTools.getDisplayableImageName(server);
			entry.setImageName(name);
			// Write a thumbnail if we can
			entry.setThumbnail(img);
			
			// Pyramidalize this if we need to
			@SuppressWarnings("resource")
			ImageServer<BufferedImage> server2 = server;
			int minPyramidDimension = PathPrefs.minPyramidDimensionProperty().get();
			if (pyramidalizeSingleResolution && server.nResolutions() == 1 && Math.max(server.getWidth(), server.getHeight()) > minPyramidDimension) {
				var serverTemp = ImageServers.pyramidalize(server);
				if (serverTemp.nResolutions() > 1) {
					logger.debug("Auto-generating image pyramid for " + name);
					server2 = serverTemp;
				} else
					serverTemp.close();
			}
			
			// Initialize an ImageData object with a type, if required
			Collection<PathObject> pathObjects = importObjects && server2 instanceof PathObjectReader ? ((PathObjectReader)server2).readPathObjects() : Collections.emptyList();
			if (type != null || server != server2 || !pathObjects.isEmpty()) {
				var imageData = new ImageData<>(server2, type);
				if (!pathObjects.isEmpty())
					imageData.getHierarchy().addPathObjects(pathObjects);
				entry.saveImageData(imageData);
			}
			if (server != server2)
				server2.close();
		}
		return entry;
	}
	
	
//	/**
//	 * Add a single ImageServer to a project, without considering sub-images.
//	 * <p>
//	 * This includes an optional attempt to request a thumbnail; if this fails, the image will not be added.
//	 * 
//	 * @param project the project to which the entry should be added
//	 * @param server the server to add
//	 * @param type the ImageType that should be set for each entry being added
//	 * @return
//	 */
//	public static ProjectImageEntry<BufferedImage> addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server, ImageType type) {
//		ProjectImageEntry<BufferedImage> entry = null;
//		try {
//			var img = getThumbnailRGB(server, null);
//			entry = project.addImage(server);
//			if (entry != null) {
//				// Write a thumbnail if we can
//				entry.setThumbnail(img);
//				// Initialize an ImageData object with a type, if required
//				if (type != null) {
//					var imageData = new ImageData<>(server, type);
//					entry.saveImageData(imageData);
//				}
//			}
//		} catch (IOException e) {
//			logger.warn("Error attempting to add " + server, e);
//		}
//		return entry;
//	}
	
	
	public static BufferedImage getThumbnailRGB(ImageServer<BufferedImage> server, ImageDisplay imageDisplay) throws IOException {
		var img2 = server.getDefaultThumbnail(server.nZSlices()/2, 0);
		// Try to write RGB images directly
		boolean success = false;
		if (imageDisplay == null && (server.isRGB() || img2.getType() == BufferedImage.TYPE_BYTE_GRAY)) {
			return resizeForThumbnail(img2);
		}
		if (!success) {
			// Try with display transforms
			if (imageDisplay == null) {
				// By wrapping the thumbnail, we avoid slow z-stack/time series requests & determine brightness & contrast just from one plane
				var wrappedServer = new WrappedBufferedImageServer("Dummy", img2, server.getMetadata().getChannels());
				imageDisplay = new ImageDisplay(new ImageData<>(wrappedServer));
//				imageDisplay = new ImageDisplay(new ImageData<>(server));
			}
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