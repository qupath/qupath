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

package qupath.lib.gui.panels;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Component for previewing and selecting images within a project.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectBrowser implements ImageDataChangeListener<BufferedImage> {

	final static String THUMBNAIL_EXT = "jpg";

	final private static Logger logger = LoggerFactory.getLogger(ProjectBrowser.class);

	private Project<BufferedImage> project;

	// Requested thumbnail max dimensions
	private int thumbnailWidth = 1000;
	private int thumbnailHeight = 600;

	private QuPathGUI qupath;
	private BorderPane panel;

	private ProjectImageTreeModel model = new ProjectImageTreeModel(null);
	private TreeView<Object> tree = new TreeView<>();

	// Keep a record of servers we've requested - don't want to keep putting in requests if the server is unavailable
	private Set<String> serversRequested = new HashSet<>();
	
	private StringProperty descriptionText = new SimpleStringProperty();


	public ProjectBrowser(final QuPathGUI qupath) {
		this.project = qupath.getProject();
		this.qupath = qupath;

		qupath.addImageDataChangeListener(this);

		panel = new BorderPane();

		tree.setCellFactory(new Callback<TreeView<Object>, TreeCell<Object>>() {
			@Override public TreeCell<Object> call(TreeView<Object> treeView) {
				return new ImageEntryCell();
			}
		});

		tree.setRoot(null);


		tree.setContextMenu(getPopup());

		tree.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
		});

		tree.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
		});
		
//		TextArea textDescription = new TextArea();
		TextArea textDescription = new TextArea();
		textDescription.textProperty().bind(descriptionText);
		MasterDetailPane mdTree = new MasterDetailPane(Side.BOTTOM, tree, textDescription, false);
		mdTree.showDetailNodeProperty().bind(descriptionText.isNotNull());
		
		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			Object selected = n == null ? null : n.getValue();
			if (selected instanceof ProjectImageEntry)
				descriptionText.set(((ProjectImageEntry<?>)selected).getDescription());
			else
				descriptionText.set(null);
		});

		TitledPane titledTree = new TitledPane("Image list", mdTree);
		titledTree.setCollapsible(false);
		titledTree.setMaxHeight(Double.MAX_VALUE);
		
		BorderPane panelTree = new BorderPane();
		panelTree.setCenter(titledTree);

		panel.setCenter(panelTree);

		Button btnOpen = qupath.getActionButton(GUIActions.PROJECT_OPEN, false);
		Button btnCreate = qupath.getActionButton(GUIActions.PROJECT_NEW, false);
		Button btnAdd = qupath.getActionButton(GUIActions.PROJECT_IMPORT_IMAGES, false);
		GridPane paneButtons = PanelToolsFX.createColumnGridControls(btnCreate, btnOpen, btnAdd);
		paneButtons.prefWidthProperty().bind(panel.widthProperty());
		paneButtons.setPadding(new Insets(5, 5, 5, 5));
		panel.setTop(paneButtons);
	}



	ContextMenu getPopup() {

		Action actionOpenImage = new Action("Open image", e -> qupath.openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Remove image", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				ProjectImageEntry<?> entry = (ProjectImageEntry<?>)path.getValue();
				if (DisplayHelpers.showConfirmDialog("Delete project entry", "Remove " + entry.getImageName() + " from project?")) {
					logger.info("Removing entry {} from project {}", path.getValue(), project);
					project.removeImage(entry);
					model.rebuildModel();
				}
			} else {
				Collection<ProjectImageEntry<BufferedImage>> entries = getImageEntries(path, null);
				if (!entries.isEmpty() && (entries.size() == 1 || 
						DisplayHelpers.showYesNoDialog("Remove project entries", String.format("Remove %d entries?", entries.size())))) {
					logger.info("Removing {} entries from project {}", entries.size(), project);
					project.removeAllImages(entries);
					model.rebuildModel();
				}
			}
			ProjectIO.writeProject(project);
			if (tree != null) {
				boolean isExpanded = tree.getRoot() != null && tree.getRoot().isExpanded();
				tree.setRoot(model.getRootFX());
				tree.getRoot().setExpanded(isExpanded);
//				tree.refresh();
			}
		});
		
		Action actionSetImageName = new Action("Rename image", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				setProjectEntryImageName((ProjectImageEntry)path.getValue());
			}
		});
		
		// Refresh thumbnail according to current display settings
		Action actionRefreshThumbnail = new Action("Refresh thumbnail", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				ProjectImageEntry<BufferedImage> entry = (ProjectImageEntry<BufferedImage>)path.getValue();
				if (!isCurrentImage(entry)) {
					logger.warn("Cannot refresh entry for image that is not open!");
					return;
				}
				File fileThumbnail = getThumbnailFile(getProject(), entry);
				BufferedImage imgThumbnail = qupath.getViewer().getRGBThumbnail();
				imgThumbnail = resizeForThumbnail(imgThumbnail);
				try {
					ImageIO.write(imgThumbnail, THUMBNAIL_EXT, fileThumbnail);
				} catch (IOException e1) {
					logger.error("Error writing thumbnail", e1);
				}
				tree.refresh();
			}
		});
		
		// Edit the description for the image
		Action actionEditDescription = new Action("Edit description", e -> {
			Project<?> project = getProject();
			ProjectImageEntry<?> entry = getSelectedEntry();
			if (project != null && entry != null) {
				if (showDescriptionEditor(entry)) {
					descriptionText.set(entry.getDescription());
					ProjectIO.writeProject(project);						
				}
			} else {
				DisplayHelpers.showErrorMessage("Edit image description", "No entry is selected!");
			}
		});
		
		// Add a metadata value
		Action actionAddMetadataValue = new Action("Add metadata", e -> {
			Project<?> project = getProject();
			ProjectImageEntry<?> entry = getSelectedEntry();
			if (project != null && entry != null) {
				
				TextField tfMetadataKey = new TextField();
				TextField tfMetadataValue = new TextField();
				Label labKey = new Label("New key");
				Label labValue = new Label("New value");
				labKey.setLabelFor(tfMetadataKey);
				labValue.setLabelFor(tfMetadataValue);
				tfMetadataKey.setTooltip(new Tooltip("Enter the name for the metadata entry"));
				tfMetadataValue.setTooltip(new Tooltip("Enter the value for the metadata entry"));
				
				int nMetadataValues = entry.getMetadataKeys().size();
				
				GridPane pane = new GridPane();
				pane.setVgap(5);
				pane.setHgap(5);
				pane.add(labKey, 0, 0);
				pane.add(tfMetadataKey, 1, 0);
				pane.add(labValue, 0, 1);
				pane.add(tfMetadataValue, 1, 1);
				if (nMetadataValues > 0) {
					
					Label labelCurrent = new Label("Current metadata");
					TextArea textAreaCurrent = new TextArea();
					textAreaCurrent.setEditable(false);

					String keyString = entry.getMetadataSummaryString();
					if (keyString.isEmpty())
						textAreaCurrent.setText("No metadata entries yet");
					else
						textAreaCurrent.setText(keyString);
					textAreaCurrent.setPrefRowCount(3);
					labelCurrent.setLabelFor(textAreaCurrent);

					pane.add(labelCurrent, 0, 2);
					pane.add(textAreaCurrent, 1, 2);					
				}
				
				Dialog<ButtonType> dialog = new Dialog<>();
				dialog.setTitle("Metadata");
				dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
				dialog.getDialogPane().setHeaderText(entry.getImageName());
				dialog.getDialogPane().setContent(pane);
				Optional<ButtonType> result = dialog.showAndWait();
				if (result.isPresent() && result.get() == ButtonType.OK) {
					String key = tfMetadataKey.getText().trim();
					String value = tfMetadataValue.getText();
					if (key.isEmpty()) {
						logger.warn("Attempted to set metadata value for {}, but key was empty!", entry.getImageName());
					} else {
						entry.putMetadataValue(key, value);
						ProjectIO.writeProject(project);
					}
				}
							
			} else {
				DisplayHelpers.showErrorMessage("Edit image description", "No entry is selected!");
			}
		});
		
		// Open the project directory using Explorer/Finder etc.
		Action actionOpenProjectDirectory = new Action("Open project directory", e -> {
			try {
				Project<?> project = getProject();
				if (project == null)
					return;
				File dir = project.getBaseDirectory();
				if (dir.exists())
					Desktop.getDesktop().open(dir);
				else
					logger.warn("Cannot find project directory {}", dir.getAbsolutePath());
			} catch (IOException e1) {
				DisplayHelpers.showErrorMessage("Open project directory", e1);
			}
		});
		

		Menu menuSort = new Menu("Sort by...");
		ContextMenu menu = new ContextMenu();
		
		MenuItem miOpenProjectDirectory = ActionUtils.createMenuItem(actionOpenProjectDirectory);
		MenuItem miOpenImage = ActionUtils.createMenuItem(actionOpenImage);
		MenuItem miRemoveImage = ActionUtils.createMenuItem(actionRemoveImage);
		MenuItem miSetImageName = ActionUtils.createMenuItem(actionSetImageName);
		MenuItem miRefreshThumbnail = ActionUtils.createMenuItem(actionRefreshThumbnail);
		MenuItem miEditDescription = ActionUtils.createMenuItem(actionEditDescription);
		MenuItem miAddMetadata = ActionUtils.createMenuItem(actionAddMetadataValue);
		
		
		// Set visibility as menu being displayed
		menu.setOnShowing(e -> {
			TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
			boolean hasImageEntry = selected != null && selected.getValue() instanceof ProjectImageEntry;
			miOpenProjectDirectory.setVisible(project != null && project.getBaseDirectory().exists());
			miOpenImage.setVisible(hasImageEntry);
			miSetImageName.setVisible(hasImageEntry);
			miAddMetadata.setVisible(hasImageEntry);
			miEditDescription.setVisible(hasImageEntry);
			miRefreshThumbnail.setVisible(hasImageEntry && isCurrentImage((ProjectImageEntry<BufferedImage>)selected.getValue()));
			miRemoveImage.setVisible(project != null && !project.getImageList().isEmpty());
			
			if (project == null) {
				menuSort.setVisible(false);
				return;
			}
			Map<String, MenuItem> newItems = new TreeMap<>();
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				for (String key : entry.getMetadataKeys()) {
					if (!newItems.containsKey(key))
						newItems.put(key, ActionUtils.createMenuItem(createSortByKeyAction(key, key)));
				}
			}
			menuSort.getItems().setAll(newItems.values());
			
			menuSort.getItems().add(0, ActionUtils.createMenuItem(createSortByKeyAction("None", null)));
			menuSort.getItems().add(1, new SeparatorMenuItem());
			
			menuSort.setVisible(true);
			
			if (menu.getItems().isEmpty())
				e.consume();
		});
		
		SeparatorMenuItem separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(menuSort.visibleProperty());
		menu.getItems().addAll(
				miOpenImage,
				miRemoveImage,
				miSetImageName,
				miAddMetadata,
				miEditDescription,
				miRefreshThumbnail,
				separator,
				menuSort
				);
		
		separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(miOpenProjectDirectory.visibleProperty());
		if (Desktop.isDesktopSupported()) {
			menu.getItems().addAll(
					separator,
					miOpenProjectDirectory);
		}

		return menu;

	}
	
	
	boolean showDescriptionEditor(ProjectImageEntry<?> entry) {
		TextArea editor = new TextArea();
		editor.setWrapText(true);
		editor.setText(entry.getDescription());
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setTitle("Image description");
		dialog.getDialogPane().setHeaderText(entry.getImageName());
		dialog.getDialogPane().setContent(editor);
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK) {
			entry.setDescription(editor.getText());
			return true;
		}
		return false;
	}

	


	public boolean hasProject() {
		return getProject() != null;
	}


	public Project<BufferedImage> getProject() {
		return project;
	}


	public Pane getPane() {
		return panel;
	}


	public void setProject(final Project<BufferedImage> project) {
		if (this.project == project)
			return;
		this.project = project;
		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRootFX());
		tree.getRoot().setExpanded(true);
	}
	
	public void refreshProject() {
		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRootFX());
		tree.getRoot().setExpanded(true);		
	}


	void ensureServerInWorkspace(final ImageServer<BufferedImage> server) {
		if (server == null || project == null)
			return;

		//		project.addImagesForServer(server);
		//		ProjectImageEntry entry = new ProjectImageEntry(project, server.getPath(), server.getDisplayedImageName());

		if (project.addImagesForServer(server)) {
			ProjectImageEntry<BufferedImage> entry = project.getImageEntry(server.getPath());
			//			tree.setModel(new ProjectImageTreeModel(project)); // TODO: Update the model more elegantly!!!
			tree.setRoot(model.getRootFX());
			if (entry != null) {
				setSelectedEntry(tree, tree.getRoot(), entry);
			}

			ProjectIO.writeProject(project);
		}
	}



	@Override
	public void imageDataChanged(final ImageDataWrapper<BufferedImage> viewer, final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		if (imageDataNew == null || project == null)
			return;
		ProjectImageEntry<BufferedImage> entry = project.getImageEntry(imageDataNew.getServerPath());
		if (entry == null) {
			if (DisplayHelpers.showYesNoDialog("Add to project", "Add " + imageDataNew.getServer().getShortServerName() + " to project?"))
				ensureServerInWorkspace(imageDataNew.getServer());
		}
		else if (!entry.equals(getSelectedEntry()))
			setSelectedEntry(tree, tree.getRoot(), entry);
		//			list.setSelectedValue(entry, true);

		if (tree != null) {
			tree.refresh();
		}
	}



	static <T> boolean setSelectedEntry(TreeView<T> treeView, TreeItem<T> item, final T object) {
		if (item.getValue() == object) {
			treeView.getSelectionModel().select(item);
			return true;
		} else {
			for (TreeItem<T> child : item.getChildren()) {
				if (setSelectedEntry(treeView, child, object))
					return true;
			}
		}
		return false;
	}
	
	
	
	
	Image requestThumbnail(final String serverPath, final File fileThumbnail) throws IOException {
//		serversRequested.clear();
		
		// Check if we've already asked for this server... if so, stop
		if (serversRequested.contains(serverPath))
			return null;
		// Put in the request now
		serversRequested.add(serverPath);
		// Check if it exists
		if (fileThumbnail.exists())
			return SwingFXUtils.toFXImage(ImageIO.read(fileThumbnail), null);
		// Try to load the server
		ImageData<BufferedImage> imageData = getCurrentImageData();
		ImageServer<BufferedImage> server = null;
		boolean newServer = false;
		if (imageData != null && imageData.getServerPath().equals(serverPath))
			server = imageData.getServer();
		else {
			server = ImageServerProvider.buildServer(serverPath, BufferedImage.class);
			newServer = true;
		}
		BufferedImage img2 = QuPathGUI.getInstance().getImageRegionStore().getThumbnail(server, server.nZSlices()/2, 0, true);
		if (newServer)
			server.close();
		if (img2 != null) {
			// Try to write RGB images directly
			boolean success = false;
			if (server.isRGB() || img2.getType() == BufferedImage.TYPE_BYTE_GRAY) {
				img2 = resizeForThumbnail(img2);
				success = ImageIO.write(img2, THUMBNAIL_EXT, fileThumbnail);
			}
			if (!success) {
				// Try with display transforms
				ImageDisplay imageDisplay = new ImageDisplay(new ImageData<>(server), qupath.getImageRegionStore(), false);
				for (ChannelDisplayInfo info : imageDisplay.getSelectedChannels()) {
					imageDisplay.autoSetDisplayRange(info);
				}
				img2 = imageDisplay.applyTransforms(img2, null);
				img2 = resizeForThumbnail(img2);
				ImageIO.write(img2, THUMBNAIL_EXT, fileThumbnail);
			}
		}
		return SwingFXUtils.toFXImage(img2, null);
	}

	
	/**
	 * Resize an image so that its dimensions fit inside thumbnailWidth x thumbnailHeight.
	 * 
	 * Note: this assumes the image can be drawn to a Graphics object.
	 * 
	 * @param imgThumbnail
	 * @return
	 */
	BufferedImage resizeForThumbnail(BufferedImage imgThumbnail) {
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
	


	void requestThumbnailInBackground(final String serverPath, final File fileThumbnail) {
		// Don't do anything if already requested
		if (serversRequested.contains(serverPath))
			return;
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					Image image = requestThumbnail(serverPath, fileThumbnail);
					if (image != null)
						Platform.runLater(() -> tree.refresh());
				} catch (IOException e) {
					logger.error("Problem loading thumbnail for {}", serverPath, e);
				}
			}
		};
		qupath.submitShortTask(r);
	}



	//	@Override
	//	public void valueChanged(ListSelectionEvent e) {
	//		updateThumbnailForSelected();
	////		openSelectedImage();
	////		ProjectImageEntry entry = list.getSelectedValue();
	////		qupath.openWholeSlideServer(entry.getServerPath(), true, true);
	//	}



	//	boolean openImage(final String path) {
	//		
	//	}


	ImageData<BufferedImage> getCurrentImageData() {
		return qupath.getViewer().getImageData();
	}



	File getBaseDirectory() {
		return project == null ? null : project.getBaseDirectory();
	}


	File getImageDataPath(final ProjectImageEntry<?> entry) {
		return getImageDataPath(project, entry);
	}

	/**
	 * Get the file in which to save the ImageData for a specified project.
	 * 
	 * Deprecated now in favor of calling the static method in QuPathGUI instead.
	 * 
	 * @param project
	 * @param entry
	 * @return
	 */
	@Deprecated
	public static File getImageDataPath(final Project<?> project, final ProjectImageEntry<?> entry) {
		return QuPathGUI.getImageDataFile(project, entry);
	}


	File getProjectPath() {
		File dirBase = getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;
		return new File(dirBase, "project." + ProjectIO.getProjectExtension());
	}


	static File getThumbnailFile(final Project<?> project, final ProjectImageEntry<?> entry) {
		if (project == null || entry == null)
			return null;
		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;

		File dirData = new File(dirBase, "thumbnails");
		if (!dirData.exists())
			dirData.mkdir();
		return new File(dirData, entry.getImageName() + "." + THUMBNAIL_EXT);
	}


	boolean isCurrentImage(final ProjectImageEntry<?> entry) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null || entry == null)
			return false;
		return entry.equalsServerPath(imageData.getServerPath());
	}



	ProjectImageEntry<BufferedImage> getSelectedEntry() {
		TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue() instanceof ProjectImageEntry)
			return (ProjectImageEntry<BufferedImage>)selected.getValue();
		return null;
	}


	static <T> Collection<ProjectImageEntry<T>> getImageEntries(final TreeItem<?> item, Collection<ProjectImageEntry<T>> entries) {
		if (entries == null)
			entries = new HashSet<>();
		if (item.getValue() instanceof ProjectImageEntry)
			entries.add((ProjectImageEntry<T>)item.getValue());
		for (TreeItem<?> child : item.getChildren()) {
			entries = getImageEntries(child, entries);
		}
		return entries;
	}



	Action createSortByKeyAction(final String name, final String key) {
		return new Action(name, e -> {
			if (model == null)
				return;
			model.setMetadataKeys(key);
			ProjectImageEntry<BufferedImage> entrySelected = getSelectedEntry();
			tree.setRoot(model.getRootFX());
			tree.getRoot().setExpanded(true);
			if (entrySelected != null)
				setSelectedEntry(tree, tree.getRoot(), entrySelected);
		});
	}

	
	/**
	 * Prompt the user to set a new name for a ProjectImageEntry.
	 * 
	 * (Currently, this means creating a new entry with the required name, and adding it to the project instead of the current one.
	 * 
	 * @param entry
	 * @return True if the entry was changed, false otherwise.
	 */
	private boolean setProjectEntryImageName(final ProjectImageEntry<BufferedImage> entry) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			logger.error("Cannot set image name - project is null");
		}
		if (entry == null) {
			logger.error("Cannot set image name - entry is null");
		}
		
		String name = DisplayHelpers.showInputDialog("Set Image Name", "Enter the new image name", entry.getImageName());
		if (name == null)
			return false;
		if (name.trim().isEmpty() || name.equals(entry.getImageName())) {
			logger.warn("Cannot set image name to {} - will ignore", name);
		}
		
		// Try to set the name
		ProjectImageEntry<BufferedImage> entryNew = setProjectEntryImageName(project, entry, name);
		if (entry == entryNew)
			return false;
		
		model.rebuildModel();
		tree.setRoot(model.getRootFX());
		tree.getRoot().setExpanded(true);
		tree.refresh();
		if (recursiveSelectObject(tree, tree.getRoot(), entryNew)) {
			// Getting the scroll to behave intuitively is tricky...
//			int ind = tree.getSelectionModel().getSelectedIndex();
//			if (ind >= 0)
//				Platform.runLater(() -> tree.scrollTo(ind));
		}
		// Ensure we have an up-to-date title in QuPath
		qupath.updateTitle();
		return true;
	}
	
	
	/**
	 * Select the TreeItem where TreeItem.getValue() == object after searching recursively from the specified item.
	 * 
	 * @param tree
	 * @param item
	 * @param object
	 * @return Tree if a tree item was found and selected, false otherwise.
	 */
	private static <T> boolean recursiveSelectObject(final TreeView<T> tree, final TreeItem<T> item, final T object) {
		if (item.getValue() == object) {
			tree.getSelectionModel().select(item);
			return true;
		}
		for (TreeItem<T> item2 : item.getChildren()) {
			if (recursiveSelectObject(tree, item2, object))
				return true;
		}
		return false;
	}
	
	
	/**
	 * The the name for a specified ProjectImageEntry.
	 * 
	 * This works hard to do its job... including renaming any data files accordingly.
	 * 
	 * @param project
	 * @param entry
	 * @param name
	 * @return
	 */
	private static <T> ProjectImageEntry<T> setProjectEntryImageName(final Project<T> project, final ProjectImageEntry<T> entry, final String name) {
		
		if (entry.getImageName().equals(name)) {
			logger.info("Project image name already set to {} - will be left unchanged", name);
			return entry;
		}
		
		for (ProjectImageEntry<?> entry2 : project.getImageList()) {
			if (entry2.getImageName().equals(name)) {
				DisplayHelpers.showErrorMessage("Set Image Name", "Cannot set image name to " + name + " -\nan image with this name already exists in the project");
				return entry;
			}
		}
		
		project.removeImage(entry);
		File fileOld = QuPathGUI.getImageDataFile(project, entry);
		
		ProjectImageEntry<T> entryNew = new ProjectImageEntry<>(project, entry.getServerPath(), name, entry.getMetadataMap());
		project.addImage(entryNew);
		File fileNew = QuPathGUI.getImageDataFile(project, entryNew);
		
		// Rename the data file
		if (fileOld.exists()) {
			try {
				Files.move(fileOld.toPath(), fileNew.toPath(), StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				DisplayHelpers.showErrorMessage("Set Image Name", e);
			}
		}
		
		// Ensure the project is updated
		ProjectIO.writeProject(project);
		
		return entryNew;
	}
	


	// I fully admit this is a horrible design - cobbled together from an earlier Swing version
	// TODO: Greatly improve the project tree display...
	static class ProjectImageTreeModel {

		private Project<?> project;
		private Map<String, List<ProjectImageEntry<?>>> map = new HashMap<>();
		private List<String> mapKeyList = new ArrayList<>();

		private List<String> sortKeys = new ArrayList<>();
		private String PROJECT_KEY;
		private String DEFAULT_ROOT = "No project";
		private String UNASSIGNED_NODE = "(Unassigned)";

		ProjectImageTreeModel(final Project<?> project) {
			this(project, null);
		}


		ProjectImageTreeModel(final Project<?> project, final String metadataKey) {
			this.project = project;
			if (project == null)
				PROJECT_KEY = "Unnamed project";
			else
				PROJECT_KEY = project.getName();
			if (metadataKey != null)
				setMetadataKeys(metadataKey);
			//			rebuildModel();
		}

		public void setMetadataKeys(final String... metadataSortKeys) {
			sortKeys.clear();
			if (metadataSortKeys != null) {
				for (String key : metadataSortKeys) {
					if (key != null)
						sortKeys.add(key);
				}
			}
			rebuildModel();
		}

		public void resetMetadataKeys() {
			setMetadataKeys();
		}

		public void rebuildModel() {
			map.clear();
			mapKeyList.clear();
			if (project == null)
				return;

			// Populate the map
			String emptyKey = sortKeys.isEmpty() ? PROJECT_KEY : UNASSIGNED_NODE;
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				String localKey = emptyKey;
				for (String metadataKey : sortKeys) {
					String temp = entry.getMetadataValue(metadataKey);
					if (temp != null) {
						localKey = temp;
						break;						
					}
				}
				List<ProjectImageEntry<?>> list = map.get(localKey);
				if (list == null) {
					list = new ArrayList<>();
					map.put(localKey, list);
				}
				list.add(entry);
			}

			// Sort all the lists
			for (List<ProjectImageEntry<?>> list : map.values()) {
				list.sort(new Comparator<ProjectImageEntry<?>>() {
					@Override
					public int compare(ProjectImageEntry<?> o1, ProjectImageEntry<?> o2) {
						return o1.toString().compareTo(o2.toString());
					}
				});
			}

			// Populate the key list
			mapKeyList.addAll(map.keySet());
			Collections.sort(mapKeyList);
			// Ensure unassigned is at the end
			if (mapKeyList.remove(UNASSIGNED_NODE))
				mapKeyList.add(UNASSIGNED_NODE);




		}


		public TreeItem<Object> getRootFX() {
			rebuildModel();
			TreeItem<Object> root = new TreeItem<>(getRoot());
			List<TreeItem<Object>> items = root.getChildren();
			if (project != null) {
				if (sortKeys.isEmpty()) {
					for (ProjectImageEntry<?> entry : project.getImageList())
						items.add(new TreeItem<>(entry));
				} else {
					for (String key : mapKeyList) {
						TreeItem<Object> item = new TreeItem<>(key);
						for (ProjectImageEntry<?> entry : map.get(key))
							item.getChildren().add(new TreeItem<>(entry));
						items.add(item);
					}
				}
			}
			return root;
		}



		public Object getRoot() {
			return project == null ? DEFAULT_ROOT : PROJECT_KEY;
		}

		public Object getChild(Object parent, int index) {
			//			if (parent == project && project != null)
			//				return project.getImageList().get(index);

			if (project == null)
				return null;
			if (parent == PROJECT_KEY && !sortKeys.isEmpty())
				return mapKeyList.get(index);
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list.get(index);
		}

		public int getChildCount(Object parent) {
			if (project == null)
				return 0;
			if (parent == PROJECT_KEY && !sortKeys.isEmpty())
				return map.size();
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list == null ? 0 : list.size();
		}

		public boolean isLeaf(Object node) {
			return node instanceof ProjectImageEntry;
		}

		public int getIndexOfChild(Object parent, Object child) {
			if (project == null)
				return -1;
			if (parent == PROJECT_KEY && !sortKeys.isEmpty())
				return mapKeyList.indexOf(child);
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list == null ? -1 : list.indexOf(child);
		}

	}






	public class ImageEntryCell extends TreeCell<Object> {

		final SimpleDateFormat dateFormat = new SimpleDateFormat();
		
		private Tooltip tooltip = new Tooltip();
		private StackPane label = new StackPane();
		private ImageView viewTooltip = new ImageView();
		private Canvas viewCanvas = new Canvas();

		public ImageEntryCell() {
			double viewWidth = 50;
			double viewHeight = 40;
			viewTooltip.setFitHeight(250);
			viewTooltip.setFitWidth(250);
			viewTooltip.setPreserveRatio(true);
			viewCanvas.setWidth(viewWidth);
			viewCanvas.setHeight(viewHeight);
			viewCanvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			label.getChildren().add(viewCanvas);
			label.setPrefSize(viewWidth, viewHeight);
			
//			setOnDragDetected( event ->  {
//				if (isEmpty())
//					return;
//				Object item = getItem();
//				String path = null;
//				if (item instanceof ProjectImageEntry<?>)
//					path = ((ProjectImageEntry)item).getServerPath();
//				if (path == null)
//					return;
//				Dragboard db = startDragAndDrop(TransferMode.COPY);
//				ClipboardContent cc = new ClipboardContent();
//				cc.putString(path);
//				db.setContent(cc);
//				db.setDragView(snapshot(null, null));
//            });
		}

		@Override
		protected void updateItem(Object item, boolean empty) {
			super.updateItem(item, empty);

			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}

			ProjectImageEntry<?> entry = item instanceof ProjectImageEntry ? (ProjectImageEntry<?>)item : null;
			if (isCurrentImage(entry))
				setStyle("-fx-font-weight: bold; -fx-font-family: arial");
			else if (entry == null || getImageDataPath(entry).exists())
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
			else
				setStyle("-fx-font-style: italic; -fx-font-family: arial");
			
			if (entry == null) {
				setText(item.toString() + " (" + getTreeItem().getChildren().size() + ")");
				tooltip.setText(item.toString());
				setTooltip(tooltip);
				setGraphic(null);
			}
			else {
				// Set whatever tooltip we have
				tooltip.setGraphic(null);
				setTooltip(tooltip);

				setText(entry.getImageName());
				//	        	 String s = entry.toString();
				//	        	 File file = getImageDataPath(entry);
				//	        	 if (file != null && file.exists()) {
				//	        		 double sizeMB = file.length() / 1024.0 / 1024.0;
				//	        		 s = String.format("%s (%.2f MB)", s, sizeMB);
				//	        	 }

								StringBuilder sb = new StringBuilder();
				sb.append(entry.getImageName()).append("\n\n");
				if (!entry.getMetadataMap().isEmpty()) {
					for (Entry<String, String> mapEntry : entry.getMetadataMap().entrySet()) {
						sb.append(mapEntry.getKey()).append(":\t").append(mapEntry.getValue()).append("\n");
					}
					sb.append("\n");
				}
				File file = getImageDataPath(entry);
				if (file != null && file.exists()) {
					double sizeMB = file.length() / 1024.0 / 1024.0;
					sb.append(String.format("Data file:\t%.2f MB", sizeMB)).append("\n");
					sb.append("Modified:\t").append(dateFormat.format(new Date(file.lastModified())));
				} else
					sb.append("No data file");

				tooltip.setText(sb.toString());
				//	        	 Tooltip tooltip = new Tooltip(sb.toString());

				File fileThumbnail = getThumbnailFile(getProject(), entry);
				if (fileThumbnail == null) {
					setGraphic(null);
					return;
				}
				
				if (fileThumbnail.exists()) {
					Image image = new Image(fileThumbnail.toURI().toString(), false);
					viewTooltip.setImage(image);
					tooltip.setGraphic(viewTooltip);
					PaintingToolsFX.paintImage(viewCanvas, image);
					if (getGraphic() == null)
						setGraphic(label);
				} else {
					setGraphic(null);
					// Put in a request for the thumbnail on a background thread
					requestThumbnailInBackground(entry.getServerPath(), fileThumbnail);
//					requestThumbnailInBackground(entry.getServerPath(), fileThumbnail, viewGraphic, viewTooltip);
				}
				
			}
			
		}
		
		
	}


}