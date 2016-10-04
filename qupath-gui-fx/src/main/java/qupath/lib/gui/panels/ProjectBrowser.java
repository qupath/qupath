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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import java.util.Set;

import javax.imageio.ImageIO;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
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
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.io.PathIO;
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
				openImageEntry(getSelectedEntry());
				e.consume();
			}
		});

		tree.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				openImageEntry(getSelectedEntry());
				e.consume();
			}
		});

		TitledPane titledTree = new TitledPane("Image list", tree);
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


		Action actionOpenImage = new Action("Open image", e -> openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Remove image", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				logger.info("Removing entry {} from project {}", path.getValue(), project);
				project.removeImage((ProjectImageEntry<?>)path.getValue());
				model.rebuildModel();
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

		// TODO: Dynamically populate these options!
		Menu menuSort = new Menu("Sort by...");
		menuSort.getItems().addAll(
				ActionUtils.createMenuItem(createSortByKeyAction("None", null)),
				ActionUtils.createMenuItem(createSortByKeyAction("Marker", "Marker")),
				ActionUtils.createMenuItem(createSortByKeyAction("Slide ID", "Slide_ID")),
				ActionUtils.createMenuItem(createSortByKeyAction("Scanner", "Scanner"))
				);


		ContextMenu menu = new ContextMenu();
		menu.getItems().addAll(
				ActionUtils.createMenuItem(actionOpenImage),
				ActionUtils.createMenuItem(actionRemoveImage),
				new SeparatorMenuItem(),
				menuSort
				);

		return menu;

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
		BufferedImage img2 = server.getBufferedThumbnail(thumbnailWidth, thumbnailHeight, 0);
		if (newServer)
			server.close();
		if (img2 != null) {
			ImageIO.write(img2, THUMBNAIL_EXT, fileThumbnail);
		}
		return SwingFXUtils.toFXImage(img2, null);
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


	public static File getImageDataPath(final Project<?> project, final ProjectImageEntry<?> entry) {
		if (project == null || entry == null)
			return null;
		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;

		File dirData = new File(dirBase, "data");
		if (!dirData.exists())
			dirData.mkdir();
		return new File(dirData, entry.getImageName() + "." + PathPrefs.getSerializationExtension());
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


	void openImageEntry(ProjectImageEntry<BufferedImage> entry) {
		if (entry == null)
			return;
		// Check if we're changing ImageData
		ImageData<BufferedImage> imageData = getCurrentImageData();
		if (imageData != null && imageData.getServerPath().equals(entry.getServerPath()))
			return;
		// If the current ImageData belongs to the current project, and there have been any changes, serialize these
		if (imageData != null && project != null) {
			ProjectImageEntry<BufferedImage> entryPrevious = project.getImageEntry(imageData.getServerPath());
			File filePrevious = getImageDataPath(entryPrevious);
			if (filePrevious != null) {
				// Write if the ImageData has changed, of if it has not previously been written
				if (imageData.isChanged() || !filePrevious.exists()) {
					DialogButton response = DialogButton.YES;
					if (imageData.isChanged()) {
						response = DisplayHelpers.showYesNoCancelDialog("Save changes", "Save changes to " + entryPrevious.getImageName() + "?");
					}
					if (response == DialogButton.YES)
						PathIO.writeImageData(filePrevious, imageData);
					else if (response == DialogButton.CANCEL)
						return;
				}
			}
		}
		File fileData = getImageDataPath(entry);

		//		boolean rotate180 = true;
		// Check if we need to rotate the image
		String value = entry.getMetadataValue("rotate180");
		boolean rotate180 = value != null && value.toLowerCase().equals("true");

		if (fileData != null && fileData.isFile()) {
			// Open the image, and then the data if possible
			if (qupath.openImage(entry.getServerPath(), false, false, rotate180))
				qupath.openSavedData(qupath.getViewer(), fileData, true);
			else
				DisplayHelpers.showErrorMessage("Image open", "Unable to open image for path\n" + entry.getServerPath());
		} else
			qupath.openImage(entry.getServerPath(), false, false, rotate180);
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
			if (isCurrentImage(entry)) {
				setStyle("-fx-font-weight: bold;");
			} else
				setStyle("-fx-font-weight: normal;");
			
			if (entry == null) {
				setText(item.toString());
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