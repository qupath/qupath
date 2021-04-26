/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.omero.OmeroAnnotations.CommentAnnotation;
import qupath.lib.images.servers.omero.OmeroAnnotations.FileAnnotation;
import qupath.lib.images.servers.omero.OmeroAnnotations.LongAnnotation;
import qupath.lib.images.servers.omero.OmeroAnnotations.MapAnnotation;
import qupath.lib.images.servers.omero.OmeroAnnotations.OmeroAnnotationType;
import qupath.lib.images.servers.omero.OmeroAnnotations.TagAnnotation;
import qupath.lib.images.servers.omero.OmeroObjects.Dataset;
import qupath.lib.images.servers.omero.OmeroObjects.Group;
import qupath.lib.images.servers.omero.OmeroObjects.Image;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObjectType;
import qupath.lib.images.servers.omero.OmeroObjects.OrphanedFolder;
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.images.servers.omero.OmeroObjects.Project;
import qupath.lib.images.servers.omero.OmeroObjects.Server;
import qupath.lib.io.GsonTools;

/**
 * Command to browse a specified OMERO server.
 * 
 * @author Melvin Gelbard
 */
// TODO: Orphaned folder is still 'selectable' via arrow keys (despite being disabled), which looks like a JavaFX bug..
// TODO: If switching users while the browser is opened, nothing will load (but everything stays clickable).
public class OmeroWebImageServerBrowserCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(OmeroWebImageServerBrowserCommand.class);
	private static final String BOLD = "-fx-font-weight: bold";
	
	private final QuPathGUI qupath;
	private Stage dialog;

	// OmeroWebClient with server to browse
	private final OmeroWebClient client;
	private final URI serverURI;
	
	// GUI left
	private ComboBox<Owner> comboOwner;
	private ComboBox<Group> comboGroup;
	private Set<Owner> owners;
	private Set<Group> groups;
	private TreeView<OmeroObject> tree;
	private OrphanedFolder orphanedFolder;
	private TextField filter;
	
	// GUI right
	private TableView<Integer> description;
	private Canvas canvas;
	private int imgPrefSize = 256;
	
	// GUI top and down
	private Label loadingChildrenLabel;
	private Label loadingThumbnailLabel;
	private Label loadingOrphanedLabel;
	private Button importBtn;
	
	// Other
	private StringConverter<Owner> ownerStringConverter;
	private Map<OmeroObjectType, BufferedImage> omeroIcons;
	private ExecutorService executorTable;		// Get TreeView item children in separate thread
	private ExecutorService executorThumbnails;	// Get image thumbnails in separate thread
	
	// Browser data 'storage'
	private List<OmeroObject> serverChildrenList;
	private ObservableList<OmeroObject> orphanedImageList;
	private Map<OmeroObject, List<OmeroObject>> projectMap;
	private Map<OmeroObject, List<OmeroObject>> datasetMap;
	private Map<Integer, BufferedImage> thumbnailBank;
	private IntegerProperty currentOrphanedCount;
	
	private final String[] orphanedAttributes = new String[] {"Name"};
	
	private final String[] projectAttributes = new String[] {"Name", 
			"Id",
			"Description",
			"Owner",
			"Group",
			"Num. datasets"};
	
	private final String[] datasetAttributes = new String[] {"Name", 
			"Id", 
			"Description",
			"Owner",
			"Group",
			"Num. images"};
	
	private final String[] imageAttributes = new String[] {"Name", 
			"Id", 
			"Owner",
			"Group",
			"Acquisition date",
			"Image width",
			"Image height",
			"Num. channels",
			"Num. z-slices",
			"Num. timepoints",
			"Pixel size X",
			"Pixel size Y",
			"Pixel size Z",
			"Pixel type"};
    
    OmeroWebImageServerBrowserCommand(QuPathGUI qupath, OmeroWebClient client) {
    	this.qupath = qupath;
    	this.client = Objects.requireNonNull(client);
    	this.serverURI = client.getServerURI();
    }
    
    Stage getStage() {
    	return dialog;
    }
    
    @Override
    public void run() {
    	boolean loggedIn = true;
    	if (!client.isLoggedIn())
    		loggedIn = client.logIn();
    	
    	if (!loggedIn)
    		return;

    	// Initialize class variables
    	serverChildrenList = new ArrayList<>();
    	orphanedImageList = FXCollections.observableArrayList();
    	orphanedFolder = new OrphanedFolder(orphanedImageList);
    	currentOrphanedCount = orphanedFolder.getCurrentCountProperty();
		thumbnailBank = new ConcurrentHashMap<Integer, BufferedImage>();
		projectMap = new ConcurrentHashMap<>();
		datasetMap = new ConcurrentHashMap<>();
		executorTable = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("children-loader", true));
		executorThumbnails = Executors.newFixedThreadPool(PathPrefs.numCommandThreadsProperty().get(), ThreadTools.createThreadFactory("thumbnail-loader", true));
		
		tree = new TreeView<>();
		owners = new HashSet<>();
		groups = new HashSet<>();
		comboGroup = new ComboBox<>();
		comboOwner = new ComboBox<>();
		filter = new TextField();
    	
		BorderPane mainPane = new BorderPane();
		BorderPane serverInfoPane = new BorderPane();
		GridPane serverAttributePane = new GridPane();
		SplitPane browsePane = new SplitPane();
		GridPane browseLeftPane = new GridPane();
		GridPane browseRightPane = new GridPane();
		GridPane loadingInfoPane = new GridPane();

		var progressChildren = new ProgressIndicator();
		progressChildren.setPrefSize(15, 15);
		loadingChildrenLabel = new Label("Loading OMERO objects", progressChildren);
		

		var progressThumbnail = new ProgressIndicator();
		progressThumbnail.setPrefSize(15, 15);
		loadingThumbnailLabel = new Label("Loading thumbnail", progressThumbnail);
		loadingThumbnailLabel.setOpacity(0.0);

		var progressOrphaned = new ProgressIndicator();
		progressOrphaned.setPrefSize(15.0, 15.0);
		loadingOrphanedLabel = new Label();
		loadingOrphanedLabel.setGraphic(progressOrphaned);

		PaneTools.addGridRow(loadingInfoPane, 0, 0, "OMERO objects are loaded in the background", loadingChildrenLabel);
		PaneTools.addGridRow(loadingInfoPane, 1, 0, "OMERO objects are loaded in the background", loadingOrphanedLabel);
		PaneTools.addGridRow(loadingInfoPane, 2, 0, "Thumbnails are loaded in the background", loadingThumbnailLabel);
		
		// Info about the server to display at the top
		var hostLabel = new Label(serverURI.getHost());
		var usernameLabel = new Label();
		usernameLabel.textProperty().bind(Bindings.createStringBinding(() -> {
			if (client.getUsername().isEmpty() && client.isLoggedIn())
				return "public";
			else
				return client.getUsername();
		}, client.usernameProperty(), client.logProperty()));
		
		// 'Num of open images' text and number are bound to the size of client observable list
		var nOpenImagesText = new Label();
		var nOpenImages = new Label();
		nOpenImagesText.textProperty().bind(Bindings.createStringBinding(() -> "Open image" + (client.getURIs().size() > 1 ? "s" : "") + ": ", client.getURIs()));
		nOpenImages.textProperty().bind(Bindings.concat(Bindings.size(client.getURIs()), ""));
		hostLabel.setStyle(BOLD);
		usernameLabel.setStyle(BOLD);
		nOpenImages.setStyle(BOLD);
		
		Label isReachable = new Label();
		isReachable.graphicProperty().bind(Bindings.createObjectBinding(() -> OmeroTools.createStateNode(client.isLoggedIn()), client.logProperty()));

		serverAttributePane.addRow(0, new Label("Server: "), hostLabel, isReachable);
		serverAttributePane.addRow(1, new Label("Username: "), usernameLabel);
		serverAttributePane.addRow(2, nOpenImagesText, nOpenImages);
		serverInfoPane.setLeft(serverAttributePane);
		serverInfoPane.setRight(loadingInfoPane);
		
		// Get OMERO icons (project and dataset icons)
		omeroIcons = getOmeroIcons();
		
		// Create converter from Owner object to proper String
		ownerStringConverter = new StringConverter<Owner>() {
		    @Override
		    public String toString(Owner owner) {
		    	if (owner != null)
		    		return owner.getName();
		    	return null;
		    }

		    @Override
		    public Owner fromString(String string) {
		        return comboOwner.getItems().stream().filter(ap -> 
		            ap.getName().equals(string)).findFirst().orElse(null);
		    }
		};
		
		// Populate orphaned image list
		OmeroTools.populateOrphanedImageList(serverURI, orphanedFolder);
		currentOrphanedCount.bind(Bindings.createIntegerBinding(() -> Math.toIntExact(filterList(orphanedImageList, 
				comboGroup.getSelectionModel().getSelectedItem(), 
				comboOwner.getSelectionModel().getSelectedItem(),
				null).size()), 
					// Binding triggered when the following change: loadingProperty/selected Group/selected Owner
					orphanedFolder.getLoadingProperty(), comboGroup.getSelectionModel().selectedItemProperty(), comboOwner.getSelectionModel().selectedItemProperty())
				);

		// Bind the top label to the amount of orphaned images
		loadingOrphanedLabel.textProperty().bind(Bindings.when(orphanedFolder.getLoadingProperty()).then(Bindings.concat("Loading image list (")
				.concat(Bindings.size(orphanedFolder.getImageList()))
				.concat("/"+ orphanedFolder.getTotalChildCount() + ")")).otherwise(Bindings.concat("")));
		loadingOrphanedLabel.opacityProperty().bind(Bindings.createDoubleBinding(() -> orphanedFolder.getLoadingProperty().get() ? 1.0 : 0, orphanedFolder.getLoadingProperty()));
		
		OmeroObjectTreeItem root = new OmeroObjectTreeItem(new OmeroObjects.Server(serverURI));
		tree.setRoot(root);
		tree.setShowRoot(false);
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tree.setCellFactory(n -> new OmeroObjectCell());
		tree.setOnMouseClicked(e -> {
	        if (e.getClickCount() == 2) {
	        	var selectedItem = tree.getSelectionModel().getSelectedItem();
	        	if (selectedItem != null && selectedItem.getValue().getType() == OmeroObjectType.IMAGE && isSupported(selectedItem.getValue())) {
	        		if (qupath.getProject() == null)
	        			qupath.openImage(createObjectURI(selectedItem.getValue()), true, true);
	        		else
	        			ProjectCommands.promptToImportImages(qupath, createObjectURI(selectedItem.getValue()));
	        	}
	        }
	    });
		
		MenuItem moreInfoItem = new MenuItem("More info...");
		MenuItem openBrowserItem = new MenuItem("Open in browser");
	    MenuItem clipboardItem = new MenuItem("Copy to clipboard");
	    MenuItem collapseItem = new MenuItem("Collapse all items");

	    // 'More info..' will open new AdvancedObjectInfo pane
	    moreInfoItem.setOnAction(ev -> new AdvancedObjectInfo(tree.getSelectionModel().getSelectedItem().getValue()));
	    moreInfoItem.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull()
				.or(Bindings.size(tree.getSelectionModel().getSelectedItems()).isNotEqualTo(1)
				.or(Bindings.createBooleanBinding(() -> tree.getSelectionModel().getSelectedItem() != null && tree.getSelectionModel().getSelectedItem().getValue().getType() == OmeroObjectType.ORPHANED_FOLDER, 
						tree.getSelectionModel().selectedItemProperty()))));
	    
	    // Opens the OMERO object in a browser
	    openBrowserItem.setOnAction(ev -> {
	    	var selected = tree.getSelectionModel().getSelectedItems();
			if (selected != null && !selected.isEmpty() && selected.size() == 1)
				QuPathGUI.launchBrowserWindow(createObjectURI(selected.get(0).getValue()));
	    });
	    openBrowserItem.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull()
	    		.or(Bindings.size(tree.getSelectionModel().getSelectedItems()).isNotEqualTo(1)));
	    
	    // Clipboard action will *not* fetch all the images in the selected object(s)
	    clipboardItem.setOnAction(ev -> {
			var selected = tree.getSelectionModel().getSelectedItems();
			if (selected != null && !selected.isEmpty()) {
				ClipboardContent content = new ClipboardContent();
				List<String> uris = new ArrayList<>();
				for (var obj: selected) {
					// If orphaned get all children items and add them to list, else create URI for object
					if (obj.getValue().getType() == OmeroObjectType.ORPHANED_FOLDER)
						uris.addAll(getObjectsURI(obj.getValue()));
					else
						uris.add(createObjectURI(obj.getValue()));
				}
				
				if (uris.size() == 1)
					content.putString(uris.get(0));
				else
					content.putString("[" + String.join(", ", uris) + "]");
				Clipboard.getSystemClipboard().setContent(content);
				Dialogs.showInfoNotification("Copy URI to clipboard", "URI" + (uris.size() > 1 ? "s " : " ") + "successfully copied to clipboard");
			} else
				Dialogs.showWarningNotification("Copy URI to clipboard", "The item needs to be selected first!");
	    });
	    
	    // Collapse all items in the tree
	    collapseItem.setOnAction(ev -> collapseTreeView(tree.getRoot()));
	    
	    // Add the items to the context menu
	    tree.setContextMenu(new ContextMenu(moreInfoItem, openBrowserItem, clipboardItem, collapseItem));
		
		owners.add(Owner.getAllMembersOwner());
		comboOwner.getItems().setAll(owners);
		comboOwner.getSelectionModel().selectFirst();
		comboOwner.setConverter(ownerStringConverter);
		
		// Changing the ComboBox value refreshes the TreeView
		comboOwner.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> refreshTree());
		comboGroup.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> refreshTree());
		
		// If the currently opened image belongs to the server that we are browsing, switch combo to the relevant group
		var imageData = qupath.getImageData();
		if (imageData != null && (imageData.getServer() instanceof OmeroWebImageServer)) {
			var server = (OmeroWebImageServer)imageData.getServer();
			
			try {
				var tempImageURI = server.getURIs().iterator().next();
				if (OmeroTools.getServerURI(tempImageURI).equals(serverURI) && OmeroWebClient.canBeAccessed(tempImageURI, OmeroObjectType.IMAGE)) {
					try {
						JsonObject mapImageInfo = OmeroRequests.requestObjectInfo(serverURI.getScheme(), serverURI.getHost(), Integer.parseInt(server.getId()), OmeroObjectType.IMAGE);
						
						Group group = GsonTools.getInstance()
								.fromJson(mapImageInfo.get("data")
										.getAsJsonObject()
										.get("omero:details")
										.getAsJsonObject()
										.get("group"), OmeroObjects.Group.class);
						
						groups.add(group);
						comboGroup.getItems().setAll(groups);
						comboGroup.getSelectionModel().select(group);
					} catch (Exception ex) {
						logger.error("Could not parse OMERO group: {}", ex.getLocalizedMessage());
						groups.add(Group.getAllGroupsGroup());
						comboGroup.getItems().setAll(groups);
					}							
				} else {
					comboGroup.getItems().setAll(groups);
				}
			} catch (ConnectException ex) {
				logger.info("Will not fetch the current OMERO group.");
			}
		}
		// If nothing is selected (i.e. currently opened image is not from the same server/an error occurred), select first item
		if (comboGroup.getSelectionModel().isEmpty())
			comboGroup.getSelectionModel().selectFirst();
		
		description = new TableView<>();
		TableColumn<Integer, String> attributeCol = new TableColumn<>("Attribute");
		TableColumn<Integer, String> valueCol = new TableColumn<>("Value");
		
		// Set the width of the columns to half the table's width each
		attributeCol.prefWidthProperty().bind(description.widthProperty().divide(4));
		valueCol.prefWidthProperty().bind(description.widthProperty().multiply(0.75));

		attributeCol.setCellValueFactory(cellData -> {
			var selectedItems = tree.getSelectionModel().getSelectedItems();
			if (cellData != null && selectedItems.size() == 1 && selectedItems.get(0).getValue() != null) {
				var type = selectedItems.get(0).getValue().getType();
				if (type == OmeroObjectType.ORPHANED_FOLDER)
					return new ReadOnlyObjectWrapper<String>(orphanedAttributes[cellData.getValue()]);
				else if (type == OmeroObjectType.PROJECT)
					return new ReadOnlyObjectWrapper<String>(projectAttributes[cellData.getValue()]);
				else if (type == OmeroObjectType.DATASET)
					return new ReadOnlyObjectWrapper<String>(datasetAttributes[cellData.getValue()]);
				else if (type == OmeroObjectType.IMAGE)
					return new ReadOnlyObjectWrapper<String>(imageAttributes[cellData.getValue()]);				
			}
			return new ReadOnlyObjectWrapper<String>("");
			
		});
		valueCol.setCellValueFactory(cellData -> {
			var selectedItems = tree.getSelectionModel().getSelectedItems();
			if (cellData != null && selectedItems.size() == 1 && selectedItems.get(0).getValue() != null) 
				return getObjectInfo(cellData.getValue(), selectedItems.get(0).getValue());
			else
				return new ReadOnlyObjectWrapper<String>();
		});
		valueCol.setCellFactory(n -> new TableCell<Integer, String>() {
			@Override
	        protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
	            if (empty || item == null) {
	                setText(null);
	                setGraphic(null);
	            } else {
	            	setText(item);
	            	setTooltip(new Tooltip(item));	            	
	            }
			}
		});

		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			clearCanvas();
			if (n != null) {
				if (description.getPlaceholder() == null)
					description.setPlaceholder(new Label("Multiple elements selected"));
				var selectedItems = tree.getSelectionModel().getSelectedItems();

				updateDescription();
				if (selectedItems.size() == 1) {
					var selectedObjectLocal = n.getValue();
					if (selectedItems.get(0) != null && selectedItems.get(0).getValue().getType() == OmeroObjectType.IMAGE) {
						// Check if thumbnail was previously cached
						if (thumbnailBank.containsKey(selectedObjectLocal.getId()))
							paintBufferedImageOnCanvas(thumbnailBank.get(selectedObjectLocal.getId()), canvas, imgPrefSize);
						else {
							// Get thumbnail from JSON API in separate thread (and show progress indicator)
							loadingThumbnailLabel.setOpacity(1.0);
							executorThumbnails.submit(() -> {
								// Note: it is possible that another task for the same id exists, but it 
								// shouldn't cause inconsistent results anyway, since '1 id = 1 thumbnail'
								BufferedImage img = OmeroTools.getThumbnail(serverURI, selectedObjectLocal.getId(), imgPrefSize);
								if (img != null) {
									thumbnailBank.put(selectedObjectLocal.getId(), img);
									paintBufferedImageOnCanvas(thumbnailBank.get(selectedObjectLocal.getId()), canvas, imgPrefSize);
								}
								Platform.runLater(() -> loadingThumbnailLabel.setOpacity(0));		
							});							
						}
					} else {
						// To avoid empty space at the top
						canvas.setWidth(0);
						canvas.setHeight(0);
					}
				} else {
					// If multiple elements are selected, collapse canvas
					canvas.setWidth(0);
					canvas.setHeight(0);
				}
			}
		});
		
		filter.setPromptText("Filter project names");
		filter.textProperty().addListener((v, o, n) -> {
			refreshTree();
			if (n.isEmpty())
				collapseTreeView(tree.getRoot());
			else
				expandTreeView(tree.getRoot());
		});
		
		Button advancedSearchBtn = new Button("Advanced...");
		advancedSearchBtn.setOnAction(e -> new AdvancedSearch());
		GridPane searchAndAdvancedPane = new GridPane();
		PaneTools.addGridRow(searchAndAdvancedPane, 0, 0, null, filter, advancedSearchBtn);
		
		importBtn = new Button("Import image");
		
		// Text on button will change according to OMERO object selected
		importBtn.textProperty().bind(Bindings.createStringBinding(() -> {
			var selected = tree.getSelectionModel().getSelectedItems();
			if (selected.isEmpty())
				return "Import OMERO image to QuPath";
			else if (selected.size() > 1)
				return "Import selected to QuPath";
			else
				return "Import OMERO " + selected.get(0).getValue().getType().toString().toLowerCase() + " to QuPath";
		}, tree.getSelectionModel().selectedItemProperty()));
		
		// Disable import button if no item is selected or selected item is not compatible
		importBtn.disableProperty().bind(
				Bindings.size(tree.getSelectionModel().getSelectedItems()).lessThan(1).or(
						Bindings.createBooleanBinding(() -> !tree.getSelectionModel().getSelectedItems().stream().allMatch(obj -> isSupported(obj.getValue())),
								tree.getSelectionModel().selectedItemProperty())
						)
				);
		
		// Import button will fetch all the images in the selected object(s) and check their validity
		importBtn.setOnMouseClicked(e -> {
			var selected = tree.getSelectionModel().getSelectedItems();
			var validUris = selected.parallelStream()
					.flatMap(item -> {
						OmeroObject uri = item.getValue();
						if (uri.getType() == OmeroObjectType.PROJECT) {
							var temp = getChildren(uri);
							List<OmeroObject> out = new ArrayList<>();
							for (var subTemp: temp) {
								out.addAll(getChildren(subTemp));
							}
							return out.parallelStream();
						} else if (uri.getType() == OmeroObjectType.DATASET)
							return getChildren(uri).parallelStream();
						return Stream.of(uri);
					})
					.filter(obj -> isSupported(obj))
					.map(obj -> createObjectURI(obj))
					.toArray(String[]::new);
			if (validUris.length == 0) {
				Dialogs.showErrorMessage("No images", "No valid images found in selected item" + (selected.size() > 1 ? "s" : "") + "!");
				return;
			}
			if (qupath.getProject() == null) {
				if (validUris.length == 1)
					qupath.openImage(validUris[0], true, true);
				else
					Dialogs.showErrorMessage("Open OMERO images", "If you want to handle multiple images, you need to create a project first."); // Same as D&D for images
				return;
			}
			ProjectCommands.promptToImportImages(qupath, validUris);
		});

		PaneTools.addGridRow(browseLeftPane, 0, 0, "Filter by", comboGroup, comboOwner);
		PaneTools.addGridRow(browseLeftPane, 1, 0, null, tree, tree);
		PaneTools.addGridRow(browseLeftPane, 2, 0, null, searchAndAdvancedPane, searchAndAdvancedPane);
		PaneTools.addGridRow(browseLeftPane, 3, 0, null, importBtn, importBtn);
		
		canvas = new Canvas();
		canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		description.getColumns().add(attributeCol);
		description.getColumns().add(valueCol);
		
		PaneTools.addGridRow(browseRightPane, 0, 0, null, canvas);
		PaneTools.addGridRow(browseRightPane, 1, 0, null, description);
        
		// Set alignment of canvas (with thumbnail)
		GridPane.setHalignment(canvas, HPos.CENTER);

		// Set HGrow and VGrow
		GridPane.setHgrow(comboOwner, Priority.ALWAYS);
		GridPane.setHgrow(comboGroup, Priority.ALWAYS);
		GridPane.setHgrow(description, Priority.ALWAYS);
		GridPane.setHgrow(tree, Priority.ALWAYS);
		GridPane.setHgrow(filter, Priority.ALWAYS);
		GridPane.setHgrow(importBtn, Priority.ALWAYS);
		GridPane.setVgrow(description, Priority.ALWAYS);
		GridPane.setVgrow(tree, Priority.ALWAYS);
		
		// Set max width & height
		comboOwner.setMaxWidth(Double.MAX_VALUE);
		comboGroup.setMaxWidth(Double.MAX_VALUE);
		filter.setMaxWidth(Double.MAX_VALUE);
        browseLeftPane.setMaxWidth(Double.MAX_VALUE);
        importBtn.setMaxWidth(Double.MAX_VALUE);
		description.setMaxHeight(Double.MAX_VALUE);
		
		// Set paddings & gaps
		serverInfoPane.setPadding(new Insets(5, 15, 5, 5));
		serverAttributePane.setHgap(10.0);
		
		// Set specific sizes
		browsePane.setPrefWidth(700.0);
		browsePane.setDividerPosition(0, 0.5);
		
		mainPane.setTop(serverInfoPane);
		mainPane.setCenter(browsePane);
		browsePane.getItems().addAll(browseLeftPane, browseRightPane);
		
		
		dialog = new Stage();
		client.logProperty().addListener((v, o, n) -> {
			if (!n)
				requestClose();
		});
		dialog.sizeToScene();
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.setTitle("OMERO web server");
		dialog.setScene(new Scene(mainPane));
		dialog.setOnCloseRequest(e -> {
			shutdownPools();
			dialog = null;
			OmeroExtension.getOpenedBrowsers().remove(client);
		});
		dialog.showAndWait();
    }
    
	/**
	 * Return a list of all children of the specified omeroObj, either by requesting them 
	 * to the server or by retrieving the stored value from the maps. If a request was 
	 * necessary, the value will be stored in the map to avoid future unnecessary computation.
	 * <p>
	 * No filter is applied to the object's children.
	 * 
	 * @param omeroObj
	 * @return list of omeroObj's children
	 */
	private List<OmeroObject> getChildren(OmeroObject omeroObj) {
		// Check if we already have the children for this OmeroObject (avoid sending request)
		if (omeroObj.getType() == OmeroObjectType.SERVER && serverChildrenList.size() > 0)
			return serverChildrenList;
		else if (omeroObj.getType() == OmeroObjectType.ORPHANED_FOLDER && orphanedImageList.size() > 0)
			return orphanedImageList;
		else if (omeroObj.getType() == OmeroObjectType.PROJECT && projectMap.containsKey(omeroObj))
			return projectMap.get(omeroObj);
		else if (omeroObj.getType() == OmeroObjectType.DATASET && datasetMap.containsKey(omeroObj))
			return datasetMap.get(omeroObj);
		else if (omeroObj.getType() == OmeroObjectType.IMAGE)
			return new ArrayList<OmeroObject>();
		
		List<OmeroObject> children;
		try {
			// If orphaned folder, return all orphaned images
			if (omeroObj.getType() == OmeroObjectType.ORPHANED_FOLDER)
				return orphanedImageList;
			
			// Read children and populate maps
			children = OmeroTools.readOmeroObjects(serverURI, omeroObj);
			
			// If omeroObj is a Server, add all the orphaned datasets (orphaned images are in 'Orphaned images' folder)
			if (omeroObj.getType() == OmeroObjectType.SERVER) {
				children.addAll(OmeroTools.readOrphanedDatasets(serverURI, (Server)omeroObj));
				serverChildrenList = children;
			} else if (omeroObj .getType() == OmeroObjectType.PROJECT)
				projectMap.put(omeroObj, children);
			else if (omeroObj.getType() == OmeroObjectType.DATASET)
				datasetMap.put(omeroObj, children);
		} catch (IOException e) {
			logger.error("Could not fetch server information: {}", e.getLocalizedMessage());
			return new ArrayList<OmeroObject>();
		}
		return children;
	}

	private Map<OmeroObjectType, BufferedImage> getOmeroIcons() {
    	Map<OmeroObjectType, BufferedImage> map = new HashMap<>();
    	var scheme = serverURI.getScheme();
    	var host = serverURI.getHost();
		try {
			// Load project icon
			map.put(OmeroObjectType.PROJECT, OmeroRequests.requestIcon(scheme, host, "folder16.png"));
			
			// Load dataset icon
			map.put(OmeroObjectType.DATASET, OmeroRequests.requestIcon(scheme, host, "folder_image16.png"));
			
			// Load image icon
			map.put(OmeroObjectType.IMAGE, OmeroRequests.requestImageIcon(scheme, host, "image16.png"));
			
			// Load orphaned folder icon
			map.put(OmeroObjectType.ORPHANED_FOLDER, OmeroRequests.requestIcon(scheme, host, "folder_yellow16.png"));
			
		} catch (IOException e) {
			logger.warn("Could not load OMERO icons: {}", e.getLocalizedMessage());
		}
		return map;
	}
    
    /**
     * Return a list of Strings representing the {@code OmeroObject}s in the parameter list.
     * The returned Strings are the lower level of OMERO object possible (giving a Dataset 
     * object should return Images URI as Strings). The list is filter according to the current 
     * group/owner and filter text.
     * 
     * @param list of OmeroObjects
     * @return list of constructed Strings
     * @see OmeroTools#getURIs(URI)
     */
    private List<String> getObjectsURI(OmeroObject... list) {
    	List<String> URIs = new ArrayList<>();
    	for (OmeroObject obj: list) {
			if (obj.getType() == OmeroObjectType.ORPHANED_FOLDER) {
				var filteredList = filterList(((OrphanedFolder)obj).getImageList(), comboGroup.getSelectionModel().getSelectedItem(), comboOwner.getSelectionModel().getSelectedItem(), null);
				URIs.addAll(filteredList.stream().map(sub -> createObjectURI(sub)).collect(Collectors.toList()));
			} else {
				try {
					URIs.addAll(OmeroTools.getURIs(URI.create(createObjectURI(obj))).stream().map(e -> e.toString()).collect(Collectors.toList()));
				} catch (IOException ex) {
					logger.error("Could not get URI for " + obj.getName() + ": {}", ex.getLocalizedMessage());
				}
			}
    	}
    	return URIs;
    }

    /**
     * Reconstruct the URI of the given {@code OmeroObject} as a String.
     * @param omeroObj
     * @return
     */
	private String createObjectURI(OmeroObject omeroObj) {
		return String.format("%s://%s/webclient/?show=%s-%d", 
				serverURI.getScheme(), 
				serverURI.getHost(), 
				omeroObj.getType().toString().toLowerCase(), 
				omeroObj.getId()
				);
	}
	
	private static List<OmeroObject> filterList(List<OmeroObject> list, Group group, Owner owner, String filter) {
		return list.stream()
			.filter(e -> {
				if (group == null) return true;
				return group == Group.getAllGroupsGroup() ? true : e.getGroup().equals(group);
			})
			.filter(e -> {
				if (owner == null) return true;
				return owner == Owner.getAllMembersOwner() ? true : e.getOwner().equals(owner);
			})
			.filter(e -> matchesSearch(e, filter))
			.collect(Collectors.toList());
	}
	
	private static boolean matchesSearch(OmeroObject obj, String filter) {
		if (filter == null || filter.isEmpty())
			return true;
		
		if (obj.getType() == OmeroObjectType.SERVER)
			return true;
		
		if (obj.getParent().getType() == OmeroObjectType.SERVER)
			return obj.getName().toLowerCase().contains(filter.toLowerCase());
		
		return matchesSearch(obj.getParent(), filter);
	}

	private void refreshTree() {
		tree.setRoot(null);
		tree.refresh();
		tree.setRoot(new OmeroObjectTreeItem(new OmeroObjects.Server(serverURI)));
		tree.refresh();
	}

	private static ObservableValue<String> getObjectInfo(Integer index, OmeroObject omeroObject) {
		if (omeroObject == null)
			return new ReadOnlyObjectWrapper<String>();
		String[] outString = new String[0];
		String name = omeroObject.getName();
		String id = omeroObject.getId() + "";
		String owner = omeroObject.getOwner() == null ? null : omeroObject.getOwner().getName();
		String group = omeroObject.getGroup() == null ? null : omeroObject.getGroup().getName();
		if (omeroObject.getType() == OmeroObjectType.ORPHANED_FOLDER)
			outString = new String[] {name};
		else if (omeroObject.getType() == OmeroObjectType.PROJECT) {
			String description = ((Project)omeroObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = omeroObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, group, nChildren};
			
		} else if (omeroObject.getType() == OmeroObjectType.DATASET) {
			String description = ((Dataset)omeroObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = omeroObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, group, nChildren};

		} else if (omeroObject.getType() == OmeroObjectType.IMAGE) {
			Image obj = (Image)omeroObject;
			String acquisitionDate = obj.getAcquisitionDate() == -1 ? "-" : new Date(obj.getAcquisitionDate()*1000).toString();
			String width = obj.getImageDimensions()[0] + " px";
			String height = obj.getImageDimensions()[1] + " px";
			String c = obj.getImageDimensions()[2] + "";
			String z = obj.getImageDimensions()[3] + "";
			String t = obj.getImageDimensions()[4] + "";
			String pixelSizeX = obj.getPhysicalSizes()[0] == null ? "-" : obj.getPhysicalSizes()[0].getValue() + " " + obj.getPhysicalSizes()[0].getSymbol();
			String pixelSizeY = obj.getPhysicalSizes()[1] == null ? "-" : obj.getPhysicalSizes()[1].getValue() + " " + obj.getPhysicalSizes()[1].getSymbol();
			String pixelSizeZ = obj.getPhysicalSizes()[2] == null ? "-" : obj.getPhysicalSizes()[2].getValue() + obj.getPhysicalSizes()[2].getSymbol();
			String pixelType = obj.getPixelType();
			outString = new String[] {name, id, owner, group, acquisitionDate, width, height, c, z, t, pixelSizeX, pixelSizeY, pixelSizeZ, pixelType};
		}
		
		return new ReadOnlyObjectWrapper<String>(outString[index]);
	}

	private void updateDescription() {
		ObservableList<Integer> indexList = FXCollections.observableArrayList();
		var selectedItems = tree.getSelectionModel().getSelectedItems();
		if (selectedItems.size() == 1 && selectedItems.get(0) != null) {
			if (selectedItems.get(0).getValue().getType().equals(OmeroObjectType.ORPHANED_FOLDER)) {
				Integer[] orphanedIndices = new Integer[orphanedAttributes.length];
				for (int index = 0; index < orphanedAttributes.length; index++) orphanedIndices[index] = index;
				indexList = FXCollections.observableArrayList(orphanedIndices);
			} else if (selectedItems.get(0).getValue().getType().equals(OmeroObjectType.PROJECT)) {
				Integer[] projectIndices = new Integer[projectAttributes.length];
				for (int index = 0; index < projectAttributes.length; index++) projectIndices[index] = index;
				indexList = FXCollections.observableArrayList(projectIndices);
				
			} else if (selectedItems.get(0).getValue().getType().equals(OmeroObjectType.DATASET)) {
				Integer[] datasetIndices = new Integer[datasetAttributes.length];
				for (int index = 0; index < datasetAttributes.length; index++) datasetIndices[index] = index;
				indexList = FXCollections.observableArrayList(datasetIndices);
				
			} else if (selectedItems.get(0).getValue().getType().equals(OmeroObjectType.IMAGE)) {
				Integer[] imageIndices = new Integer[imageAttributes.length];
				for (int index = 0; index < imageAttributes.length; index++) imageIndices[index] = index;
				indexList = FXCollections.observableArrayList(imageIndices);
			}
		}
		description.getItems().setAll(indexList);
	}
	
	private void clearCanvas() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
	}
	
	/**
	 * Paint the specified image onto the specified canvas (of the preferred size).
	 * Additionally, it returns the {@code WritableImage} for further use.
	 * @param img
	 * @param canvas
	 * @param prefSize
	 * @return writable image
	 */
	private static WritableImage paintBufferedImageOnCanvas(BufferedImage img, Canvas canvas, int prefSize) {
		canvas.setWidth(prefSize);
		canvas.setHeight(prefSize);
		
		// Color the canvas in black, in case no new image can be painted
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		
		if (img == null)
			return null;
		
		var wi =  SwingFXUtils.toFXImage(img, null);
		if (wi == null)
			return wi;
		
		GuiTools.paintImage(canvas, wi);
		return wi;
	}
	
	/**
	 * Return whether the image type is supported by QuPath.
	 * @param omeroObj
	 * @return isSupported
	 */
	private static boolean isSupported(OmeroObject omeroObj) {
		if (omeroObj == null || omeroObj.getType() != OmeroObjectType.IMAGE)
			return true;
		return isUint8((Image)omeroObj) && has3Channels((Image)omeroObj);
	}
	
	private static boolean isUint8(Image image) {
		if (image == null)
			return false;
		return image.getPixelType().equals("uint8");
	}
	
	private static boolean has3Channels(Image image) {
		if (image == null)
			return false;
		return Integer.parseInt(getObjectInfo(7, image).getValue()) == 3;
	}
	
	/**
	 * Set the specified item and its children to the specified expanded mode
	 * @param item
	 */
	private static void expandTreeView(TreeItem<OmeroObject> item){
	    if (item != null && !item.isLeaf()) {
	    	if (!(item.getValue().getType() == OmeroObjectType.SERVER))
	    		item.setExpanded(true);
	    	
    		for (var child: item.getChildren()) {
    			expandTreeView(child);
	        }
	    }
	}
	
	/**
	 * Collapse the TreeView. The {@code item} value must be an {@code OmeroObjectType.SERVER} (root).
	 * @param item
	 */
	private static void collapseTreeView(TreeItem<OmeroObject> item){
	    if (item != null && !item.isLeaf() && item.getValue().getType() == OmeroObjectType.SERVER) {
    		for (var child: item.getChildren()) {
    			child.setExpanded(false);
    		}
	    }
	}
	
	/**
	 * Display an OMERO object using its name.
	 */
	private class OmeroObjectCell extends TreeCell<OmeroObject> {
		
		private Canvas iconCanvas = new Canvas();
		private Canvas tooltipCanvas = new Canvas();
		
		@Override
        public void updateItem(OmeroObject item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            // Since cells are recycled, make sure they are not disabled or transparent
        	setOpacity(1.0);
        	disableProperty().unbind();
        	setDisable(false);
        	paintBufferedImageOnCanvas(null, tooltipCanvas, 0);
        	
        	String name;
        	Tooltip tooltip = new Tooltip();
        	BufferedImage icon = omeroIcons.get(item.getType());
        	if (item.getType() == OmeroObjectType.SERVER)
        		name = serverURI.getHost();
        	else if (item.getType() == OmeroObjectType.PROJECT || item.getType() == OmeroObjectType.DATASET)
        		name = item.getName() + " (" + item.getNChildren() + ")";
        	else if (item.getType() == OmeroObjectType.ORPHANED_FOLDER) {
        		// No need for 'text', as we're using the graphic component of the cell for orphaned folder
        		setText("");
        		var label = new Label("", iconCanvas);
        		
        		// Bind the label property to display the total amount of loaded orphaned images (for this Group/Owner)
        		label.textProperty().bind(
        				Bindings.when(orphanedFolder.getLoadingProperty())
        					.then(Bindings.concat(item.getName(), " (loading...)"))
        					.otherwise(Bindings.concat(item.getName(), " (", currentOrphanedCount, ")")));

        		// If orphaned images are still loading, disable the cell (prevent weird and unnecessary errors)
        		disableProperty().bind(orphanedFolder.getLoadingProperty());
        		if (icon != null)
        			paintBufferedImageOnCanvas(icon, iconCanvas, 15);
        		// Orphaned object is still 'selectable' via arrows (despite being disabled), which looks like a JavaFX bug..
//            		orphanedFolder.getCurrentCountProperty().addListener((v, o, n) -> getDisclosureNode().setVisible(n.intValue() > 0 && !orphanedFolder.getLoadingProperty().get()));
        		tooltip.setText(item.getName());
        		setTooltip(tooltip);
        		setGraphic(label);
        		return;
        	} else if (item.getType() == OmeroObjectType.IMAGE) {
        		name = item.getName();
        		GridPane gp = new GridPane();
            	gp.addRow(0, tooltipCanvas, new Label(name));
            	if (!isSupported(item)) {
            		setOpacity(0.5);
            		Label notSupportedLabel = new Label("Image not supported:");
            		notSupportedLabel.setStyle("-fx-text-fill: red;");
            		
            		// Clarify to the user WHY it's not supported
            		Label uint8 = new Label();
            		if (isUint8((Image)item)) {
            			uint8.setText("- uint8 " + Character.toString((char)10003));
            		} else {
            			uint8.setText("- uint8 " + Character.toString((char)10007));
            			uint8.setStyle("-fx-text-fill: red;");
            		}
            		Label has3Channels = new Label();
            		if (has3Channels((Image)item)) {
            			has3Channels.setText("- 3 channels " + Character.toString((char)10003));
            		} else {
            			has3Channels.setText("- 3 channels " + Character.toString((char)10007));
            			has3Channels.setStyle("-fx-text-fill: red;");
            		}
            		gp.addRow(1, notSupportedLabel, new HBox(uint8, has3Channels));
            	}
            	
            	tooltip.setOnShowing(e -> {
            		// Image tooltip shows the thumbnail (could show icon for other items, but icon is very low quality)
            		if (thumbnailBank.containsKey(item.getId()))
            			paintBufferedImageOnCanvas(thumbnailBank.get(item.getId()), tooltipCanvas, 100);
            		else {
            			// Get thumbnail from JSON API in separate thread
            			executorThumbnails.submit(() -> {
            				var loadedImg = OmeroTools.getThumbnail(serverURI, item.getId(), imgPrefSize);
            				if (loadedImg != null) {
            					thumbnailBank.put(item.getId(), loadedImg);
            					Platform.runLater(() -> paintBufferedImageOnCanvas(loadedImg, tooltipCanvas, 100));
            				}
            			});							
            		}
            	});
            	setText(name);
            	setTooltip(tooltip);
            	tooltip.setGraphic(gp);
            } else {
            	name = item.getName();
            	if (!isSupported(item))
            		setOpacity(0.5);
            }
        	
        	// Paint icon
        	if (icon != null) {
    			paintBufferedImageOnCanvas(icon, iconCanvas, 15);
    			setGraphic(iconCanvas);
    		}
        	
        	if (item.getType() != OmeroObjectType.IMAGE) {
        		tooltip.setText(name);
        		tooltip.setGraphic(tooltipCanvas);        		
        	}
        	setTooltip(tooltip);
        	setText(name);
        }
	}
	
	/**
	 * TreeItem to help with the display of OMERO objects.
	 */
	private class OmeroObjectTreeItem extends TreeItem<OmeroObject> {
		
		private boolean computed = false;
		
		private OmeroObjectTreeItem(OmeroObject obj) {
			super(obj);
		}

		/**
		 * This method gets the children of the current tree item.
		 * Only the currently expanded items will call this method.
		 * <p>
		 * If we have never seen the current tree item, a JSON request
		 * will be sent to the OMERO API to get its children, this value 
		 * will then be stored (cached). If we have seen this tree item 
		 * before, it will simply return the stored value.
		 * 
		 * All stored values are in @ {@code serverChildrenList}, 
		 * {@code orphanedImageList}, {@code projectMap} & {@code datasetMap}.
		 */
		@Override
		public ObservableList<TreeItem<OmeroObject>> getChildren() {
			if (!isLeaf() && !computed) {
				loadingChildrenLabel.setOpacity(1.0);
				var filterTemp = filter.getText();
				
				// If submitting tasks to a shutdown executor, an Exception is thrown
				if (executorTable.isShutdown()) {
					loadingChildrenLabel.setOpacity(0);
					return FXCollections.observableArrayList();
				}
				
				executorTable.submit(() -> {
					var omeroObj = this.getValue();
					
					// Get children and populate maps if necessary
					List<OmeroObject> children = OmeroWebImageServerBrowserCommand.this.getChildren(omeroObj);
					
					Group currentGroup = comboGroup.getSelectionModel().getSelectedItem();
					Owner currentOwner = comboOwner.getSelectionModel().getSelectedItem();
					
					// If server, update list of groups/owners (and comboBoxes)
					if (omeroObj.getType() == OmeroObjectType.SERVER) {
						// Fetch ALL Groups and ALL Owners
						var tempGroups = children.stream()
								.map(e -> e.getGroup())
								.filter(distinctByName(Group::getName))
								.collect(Collectors.toList());
						var tempOwners = children.stream()
								.filter(e -> e.getGroup().equals(currentGroup))
								.map(e -> e.getOwner())
								.filter(distinctByName(Owner::getName))
								.collect(Collectors.toList());
						
						// If we suddenly found more Groups, update the set (shoudn't happen)
						if (tempGroups.size() > groups.size()) {
							groups.clear();
							groups.addAll(tempGroups);
							// Update comboBox
							Platform.runLater(() -> {
								var selectedItem = comboGroup.getSelectionModel().getSelectedItem();
								comboGroup.getItems().setAll(groups);
								if (selectedItem == null)
									comboGroup.getSelectionModel().selectFirst();
								else
									comboGroup.getSelectionModel().select(selectedItem);
							});
						}
						// First 'Owner' is always 'All members'
						tempOwners.add(0, Owner.getAllMembersOwner());
						if (!tempOwners.containsAll(comboOwner.getItems()) || !comboOwner.getItems().containsAll(tempOwners)) {
							Platform.runLater(() -> {
								comboOwner.getItems().setAll(tempOwners);
								// Attempt not to change the currently selected owner if present in new Owner set
								if (tempOwners.contains(currentOwner))
									comboOwner.getSelectionModel().select(currentOwner);
								else
									comboOwner.getSelectionModel().selectFirst(); // 'All members'
							});
						}
						if (owners.size() == 1)
							owners = new HashSet<>(tempOwners);
					}
						
					if (omeroObj.getType() == OmeroObjectType.ORPHANED_FOLDER)
						children = orphanedImageList;
				
					var items = filterList(children, comboGroup.getSelectionModel().getSelectedItem(), comboOwner.getSelectionModel().getSelectedItem(), filterTemp).stream()
							.map(e -> new OmeroObjectTreeItem(e))
							.collect(Collectors.toList());
					
					// Add an 'Orphaned Images' item to the server's children
					if (omeroObj.getType() == OmeroObjectType.SERVER && (filter == null || filterTemp.isEmpty()))
						items.add(new OmeroObjectTreeItem(orphanedFolder));

					Platform.runLater(() -> {
						super.getChildren().setAll(items);
						loadingChildrenLabel.setOpacity(0);
					});

					computed = true;
					return super.getChildren();
				});
			}
			return super.getChildren();
		}
		
		
		@Override
		public boolean isLeaf() {
			var obj = this.getValue();
			if (obj.getType() == OmeroObjectType.SERVER)
				return false;
			if (obj.getType() == OmeroObjectType.IMAGE)
				return true;
			return obj.getNChildren() == 0;
		}
		
		/**
		 * See {@link "https://stackoverflow.com/questions/23699371/java-8-distinct-by-property"}
		 * @param <T>
		 * @param keyExtractor
		 * @return
		 */
		private <T> Predicate<T> distinctByName(Function<? super T, ?> keyExtractor) {
		    Set<Object> seen = ConcurrentHashMap.newKeySet();
		    return t -> seen.add(keyExtractor.apply(t));
		}
	}
	
	
	private class AdvancedObjectInfo {
		
		private final OmeroObject obj;
		private final OmeroAnnotations tags;
		private final OmeroAnnotations keyValuePairs;
//		private final OmeroAnnotations tables;
		private final OmeroAnnotations attachments;
		private final OmeroAnnotations comments;
		private final OmeroAnnotations ratings;
//		private final OmeroAnnotations others;

		private AdvancedObjectInfo(OmeroObject obj) {			
			this.obj = obj;
			this.tags = OmeroTools.readOmeroAnnotations(serverURI, obj, OmeroAnnotationType.TAG);
			this.keyValuePairs = OmeroTools.readOmeroAnnotations(serverURI, obj, OmeroAnnotationType.MAP);
//			this.tables = OmeroTools.getOmeroAnnotations(serverURI, obj, OmeroAnnotationType.TABLE);
			this.attachments = OmeroTools.readOmeroAnnotations(serverURI, obj, OmeroAnnotationType.ATTACHMENT);
			this.comments = OmeroTools.readOmeroAnnotations(serverURI, obj, OmeroAnnotationType.COMMENT);
			this.ratings = OmeroTools.readOmeroAnnotations(serverURI, obj, OmeroAnnotationType.RATING);
//			this.others = OmeroTools.getOmeroAnnotations(serverURI, obj, OmeroAnnotationType.CUSTOM);
			
			showOmeroObjectInfo();
		}
		
		

		private void showOmeroObjectInfo() {
			BorderPane bp = new BorderPane();
			GridPane gp = new GridPane();
			
			Label nameLabel = new Label(obj.getName());
			nameLabel.setStyle(BOLD);
			
			int row = 0;
			PaneTools.addGridRow(gp, row++, 0, null, new TitledPane(obj.getType().toString() + " Details", createObjectDetailsPane(obj)));
			PaneTools.addGridRow(gp, row++, 0, null, createAnnotationsPane("Tags (" + tags.getSize() + ")", tags));
			PaneTools.addGridRow(gp, row++, 0, null, createAnnotationsPane("Key-Value Pairs (" + keyValuePairs.getSize() + ")", keyValuePairs));
//			PaneTools.addGridRow(gp, row++, 0, "Tables", new TitledPane("Tables", createAnnotationsPane(tables)));
			PaneTools.addGridRow(gp, row++, 0, null, createAnnotationsPane("Attachments (" + attachments.getSize() + ")", attachments));
			PaneTools.addGridRow(gp, row++, 0, null, createAnnotationsPane("Comments (" + comments.getSize() + ")", comments));
			PaneTools.addGridRow(gp, row++, 0, "Ratings", createAnnotationsPane("Ratings (" + ratings.getSize() + ")", ratings));
//			PaneTools.addGridRow(gp, row++, 0, "Others", new TitledPane("Others (" + others.getSize() + ")", createAnnotationsPane(others)));
			
			// Top: object name
			bp.setTop(nameLabel);
			
			// Center: annotations
			bp.setCenter(gp);
			
			// Set max width/height
			bp.setMaxWidth(500.0);
			bp.setMaxHeight(800.0);

			var dialog = Dialogs.builder()
					.content(bp)
					.title("More info")
					.build();
			
			// Resize Dialog when expanding/collapsing any TitledPane
			gp.getChildren().forEach(e -> {
				if (e instanceof TitledPane)
					((TitledPane)e).heightProperty().addListener((v, o, n) -> dialog.getDialogPane().getScene().getWindow().sizeToScene());
			});
			
			// Catch escape key pressed
			dialog.getDialogPane().getScene().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() == KeyCode.ESCAPE)
					((Stage)dialog.getDialogPane().getScene().getWindow()).close();
			});
			
			dialog.showAndWait();
		}

		/*
		 * Create a ScrollPane in which each row is an annotation value
		 */
		private Node createAnnotationsPane(String title, OmeroAnnotations omeroAnnotations) {
			TitledPane tp = new TitledPane();
			tp.setText(title);
			
			if (omeroAnnotations == null || 
				omeroAnnotations.getAnnotations() == null || 
				omeroAnnotations.getAnnotations().isEmpty() ||
				omeroAnnotations.getType() == null)
				return tp;

			ScrollPane sp = new ScrollPane();
			GridPane gp = new GridPane();
			gp.setHgap(50.0);
			gp.setVgap(1.0);
			sp.setMaxHeight(800.0);
			sp.setMaxWidth(500.0);
			sp.setMinHeight(50.0);
			sp.setMinWidth(50.0);
			sp.setPadding(new Insets(5.0, 5.0, 5.0, 5.0));
			
			var anns = omeroAnnotations.getAnnotations();
			String tooltip;
			switch (omeroAnnotations.getType()) {
			case TAG:
				for (var ann: anns) {
					var ann2 = (TagAnnotation)ann;
					var addedBy = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.addedBy().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					var creator = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.getOwner().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					tooltip = String.format("Added by: %s%sCreated by: %s", addedBy, System.lineSeparator(), creator);
					PaneTools.addGridRow(gp, gp.getRowCount(), 0, tooltip, new Label(ann2.getValue()));
				}
				break;
			case MAP:
				for (var ann: anns) {
					var ann2 = (MapAnnotation)ann;
					var addedBy = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.addedBy().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					var creator = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.getOwner().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					for (var value: ann2.getValues().entrySet())
						addKeyValueToGrid(gp, true, "Added by: " + addedBy + System.lineSeparator() + "Created by: " + creator, value.getKey(), value.getValue().isEmpty() ? "-" : value.getValue());
				}
				break;
			case ATTACHMENT:
				for (var ann: anns) {
					var ann2 = (FileAnnotation)ann;
					var addedBy = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.addedBy().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					var creator = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.getOwner().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					tooltip = String.format("Added by: %s%sCreated by: %s%sType: %s", addedBy, System.lineSeparator(), creator, System.lineSeparator(), ann2.getMimeType());
					PaneTools.addGridRow(gp, gp.getRowCount(), 0, tooltip, new Label(ann2.getFilename() + " (" + ann2.getFileSize() + " bytes)"));
				}
				break;
			case COMMENT:
				for (var ann: anns) {
					var ann2 = (CommentAnnotation)ann;
					var addedBy = omeroAnnotations.getExperimenters().parallelStream()
							.filter(e -> e .getId() == ann2.addedBy().getId())
							.map(e -> e.getFullName())
							.findAny().get();
					PaneTools.addGridRow(gp, gp.getRowCount(), 0, "Added by " + addedBy, new Label(ann2.getValue()));
				}
				break;
			case RATING:
				int rating = 0;
				for (var ann: anns) {
					var ann2 = (LongAnnotation)ann;
					rating += ann2.getValue();
				}
				
				for (int i = 0; i < Math.round(rating/anns.size()); i++)
					gp.add(IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.STAR), i, 0);
				gp.setHgap(10.0);
				break;
			default:
				logger.error("OMERO annotation not supported: {}", omeroAnnotations.getType());
			}
			
			sp.setContent(gp);
			tp.setContent(sp);
			return tp;
		}

		private Node createObjectDetailsPane(OmeroObject obj) {
			GridPane gp = new GridPane();
			
			addKeyValueToGrid(gp, true, "Id", "Id", obj.getId() + "");
			addKeyValueToGrid(gp, true, "Owner", "Owner", obj.getOwner().getName());
			addKeyValueToGrid(gp, false, "Group", "Group", obj.getGroup().getName());
			
			if (obj.getType() == OmeroObjectType.IMAGE) {
				Image temp = (Image)obj;
				
				gp.add(new Separator(), 0, gp.getRowCount() + 1, gp.getColumnCount(), 1);
				String acquisitionDate = temp.getAcquisitionDate() == -1 ? "-" : temp.getAcquisitionDate() + "";
				String pixelSizeX = temp.getPhysicalSizes()[0] == null ? "-" : temp.getPhysicalSizes()[0].getValue() + " " + temp.getPhysicalSizes()[0].getSymbol();
				String pixelSizeY = temp.getPhysicalSizes()[1] == null ? "-" : temp.getPhysicalSizes()[1].getValue() + " " + temp.getPhysicalSizes()[1].getSymbol();
				String pixelSizeZ = temp.getPhysicalSizes()[2] == null ? "-" : temp.getPhysicalSizes()[2].getValue() + temp.getPhysicalSizes()[2].getSymbol();

				addKeyValueToGrid(gp, true, "Acquisition date", "Acquisition date", acquisitionDate);
				addKeyValueToGrid(gp, true, "Image width", "Image width", temp.getImageDimensions()[0] + " px");
				addKeyValueToGrid(gp, true, "Image height", "Image height", temp.getImageDimensions()[1] + " px");
				addKeyValueToGrid(gp, true, "Num. channels", "Num. channels", temp.getImageDimensions()[2] + "");
				addKeyValueToGrid(gp, true, "Num. z-slices", "Num. z-slices", temp.getImageDimensions()[3] + "");
				addKeyValueToGrid(gp, true, "Num. timepoints", "Num. timepoints", temp.getImageDimensions()[4] + "");
				addKeyValueToGrid(gp, true, "Pixel size X", "Pixel size X", pixelSizeX);
				addKeyValueToGrid(gp, true, "Pixel size Y", "Pixel size Y", pixelSizeY);
				addKeyValueToGrid(gp, true, "Pixel size Z", "Pixel size Z", pixelSizeZ);
				addKeyValueToGrid(gp, false, "Pixel type", "Pixel type", temp.getPixelType());
			}
			
			gp.setHgap(50.0);
			gp.setVgap(1.0);
			return gp;
		}
		
		
		/**
		 * Append a key-value row to the end (bottom row) of the specified GridPane.
		 * @param gp
		 * @param addSeparator
		 * @param key
		 * @param value
		 */
		private void addKeyValueToGrid(GridPane gp, boolean addSeparator, String tooltip, String key, String value) {
			Label keyLabel = new Label(key);
			keyLabel.setStyle(BOLD);
			int row = gp.getRowCount();
			
			PaneTools.addGridRow(gp, row, 0, tooltip, keyLabel, new Label(value));
			if (addSeparator)
				gp.add(new Separator(), 0, row + 1, gp.getColumnCount(), 1);
		}
	}

	private class AdvancedSearch {
		
		private final TableView<SearchResult> resultsTableView = new TableView<>();
		private final ObservableList<SearchResult> obsResults = FXCollections.observableArrayList();
		
		private TextField searchTf;
		private CheckBox restrictedByName;
		private CheckBox restrictedByDesc;
		private CheckBox searchForImages;
		private CheckBox searchForDatasets;
		private CheckBox searchForProjects;
		private CheckBox searchForWells;
		private CheckBox searchForPlates;
		private CheckBox searchForScreens;
		private ComboBox<Owner> ownedByCombo;
		private ComboBox<Group> groupCombo;
		
		private final int prefScale = 50;
		
		private Button searchBtn;
		private ProgressIndicator progressIndicator2;
		
		// Search query in separate thread
		private final ExecutorService executorQuery = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("query-processing", true));
		
		// Load thumbnail in separate thread
		private ExecutorService executorThumbnail;
		
		private final Pattern patternRow = Pattern.compile("<tr id=\"(.+?)-(.+?)\".+?</tr>", Pattern.DOTALL | Pattern.MULTILINE);
	    private final Pattern patternDesc = Pattern.compile("<td class=\"desc\"><a>(.+?)</a></td>");
	    private final Pattern patternDate = Pattern.compile("<td class=\"date\">(.+?)</td>");
	    private final Pattern patternGroup = Pattern.compile("<td class=\"group\">(.+?)</td>");
	    private final Pattern patternLink = Pattern.compile("<td><a href=\"(.+?)\"");

	    private final Pattern[] patterns = new Pattern[] {patternDesc, patternDate, patternDate, patternGroup, patternLink};
		
		private AdvancedSearch() {
			
			BorderPane searchPane = new BorderPane();
			GridPane searchOptionPane = new GridPane();
			GridPane searchResultPane = new GridPane();
			
			// 'Query' pane
			GridPane queryPane = new GridPane();
			queryPane.setHgap(10.0);
			searchTf = new TextField();
			searchTf.setPromptText("Query");
			queryPane.addRow(0, new Label("Query:"), searchTf);
			
			
			// 'Restrict by' pane
			GridPane restrictByPane = new GridPane();
			restrictByPane.setHgap(10.0);
			restrictedByName = new CheckBox("Name");
			restrictedByDesc = new CheckBox("Description");
			restrictByPane.addRow(0, restrictedByName, restrictedByDesc);
			
			
			// 'Search for' pane
			GridPane searchForPane = new GridPane();
			searchForPane.setHgap(10.0);
			searchForPane.setVgap(10.0);
			searchForImages = new CheckBox("Images");
			searchForDatasets = new CheckBox("Datasets");
			searchForProjects = new CheckBox("Projects");
			searchForWells = new CheckBox("Wells");
			searchForPlates = new CheckBox("Plates");
			searchForScreens = new CheckBox("Screens");
			searchForPane.addRow(0,  searchForImages, searchForDatasets, searchForProjects);
			searchForPane.addRow(1,  searchForWells, searchForPlates, searchForScreens);
			for (var searchFor: searchForPane.getChildren()) {
				((CheckBox)searchFor).setSelected(true);
			}
			
			// 'Owned by' & 'Group' pane
			GridPane comboPane = new GridPane();
			comboPane.setHgap(10.0);
			comboPane.setHgap(10.0);
			ownedByCombo = new ComboBox<>();
			groupCombo = new ComboBox<>();
			ownedByCombo.setMaxWidth(Double.MAX_VALUE);
			groupCombo.setMaxWidth(Double.MAX_VALUE);
			ownedByCombo.getItems().setAll(owners);
			groupCombo.getItems().setAll(groups);
			ownedByCombo.getSelectionModel().selectFirst();
			groupCombo.getSelectionModel().selectFirst();
			ownedByCombo.setConverter(ownerStringConverter);
			PaneTools.addGridRow(comboPane, 0, 0, "Data owned by", new Label("Owned by:"),  ownedByCombo);
			PaneTools.addGridRow(comboPane, 1, 0, "Data from group", new Label("Group:"), groupCombo);
			
			// Button pane
			GridPane buttonPane = new GridPane();
			Button resetBtn = new Button("Reset");
			resetBtn.setOnAction(e -> {
				searchTf.setText("");
				for (var restrictBy: restrictByPane.getChildren()) {
					((CheckBox)restrictBy).setSelected(false);
				}
				for (var searchFor: searchForPane.getChildren()) {
					((CheckBox)searchFor).setSelected(true);
				}
				ownedByCombo.getSelectionModel().selectFirst();
				groupCombo.getSelectionModel().selectFirst();
				resultsTableView.getItems().clear();
			});
			searchBtn = new Button("Search");
			progressIndicator2 = new ProgressIndicator();
			progressIndicator2.setPrefSize(30, 30);
			progressIndicator2.setMinSize(30, 30);
			searchBtn.setOnAction(e -> {
				searchBtn.setGraphic(progressIndicator2);
				// Show progress indicator (loading)
				Platform.runLater(() -> {
					// TODO: next line doesn't work
					searchBtn.setGraphic(progressIndicator2);
					searchBtn.setText(null);
				});
				
				// Process the query in different thread
				executorQuery.submit(() -> searchQuery());
				
				// Reset 'Search' button
				Platform.runLater(() -> {
					searchBtn.setGraphic(null);
					searchBtn.setText("Search");
				});
			});
			resetBtn.setMaxWidth(Double.MAX_VALUE);
			searchBtn.setMaxWidth(Double.MAX_VALUE);
			GridPane.setHgrow(resetBtn, Priority.ALWAYS);
			GridPane.setHgrow(searchBtn, Priority.ALWAYS);
			buttonPane.addRow(0,  resetBtn, searchBtn);
			buttonPane.setHgap(5.0);
			
			Button importBtn = new Button("Import image");
			importBtn.disableProperty().bind(resultsTableView.getSelectionModel().selectedItemProperty().isNull());
			importBtn.setMaxWidth(Double.MAX_VALUE);
			importBtn.setOnAction(e -> {
				String[] URIs = resultsTableView.getSelectionModel().getSelectedItems().stream()
						.flatMap(item -> {
							try {
								return OmeroTools.getURIs(item.link.toURI()).stream();
							} catch (URISyntaxException | IOException ex) {
								logger.error("Error while opening " + item.name + ": {}", ex.getLocalizedMessage());
							}
							return null;
						}).map(item -> item.toString())
						  .toArray(String[]::new);
				if (URIs.length > 0)
					ProjectCommands.promptToImportImages(qupath, URIs);
				else
					Dialogs.showErrorMessage("No image found", "No image found in OMERO object.");
			});
			resultsTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
			
			
			int row = 0;
			PaneTools.addGridRow(searchOptionPane, row++, 0, "The query to search", queryPane);
			PaneTools.addGridRow(searchOptionPane, row++, 0, null, new Separator());
			PaneTools.addGridRow(searchOptionPane, row++, 0, "Restrict by", new Label("Restrict by:"));
			PaneTools.addGridRow(searchOptionPane, row++, 0, "Restrict by", restrictByPane);
			PaneTools.addGridRow(searchOptionPane, row++, 0, null, new Separator());
			PaneTools.addGridRow(searchOptionPane, row++, 0, "Search for", new Label("Search for:"));
			PaneTools.addGridRow(searchOptionPane, row++, 0, "Search for", searchForPane);
			PaneTools.addGridRow(searchOptionPane, row++, 0, null, new Separator());
			PaneTools.addGridRow(searchOptionPane, row++, 0, null, comboPane);
			PaneTools.addGridRow(searchOptionPane, row++, 0, null, buttonPane);
			PaneTools.addGridRow(searchOptionPane, row++, 0, "Import selected image", importBtn);
			
			TableColumn<SearchResult, SearchResult> typeCol = new TableColumn<>("Type");
		    TableColumn<SearchResult, String> nameCol = new TableColumn<>("Name");
		    TableColumn<SearchResult, String> acquisitionCol = new TableColumn<>("Acquired");
		    TableColumn<SearchResult, String> importedCol = new TableColumn<>("Imported");
		    TableColumn<SearchResult, String> groupCol = new TableColumn<>("Group");
		    TableColumn<SearchResult, SearchResult> linkCol = new TableColumn<>("Link");
		    
		    typeCol.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue()));
		    typeCol.setCellFactory(n -> new TableCell<SearchResult, SearchResult>() {
		    	
				@Override
		        protected void updateItem(SearchResult item, boolean empty) {
		            super.updateItem(item, empty);
		            BufferedImage img = null;
		            Canvas canvas = new Canvas(prefScale, prefScale);
		            
		            if (item == null || empty) {
						setTooltip(null);
						setText(null);
						return;
					}
		            
		            if (item.type.toLowerCase().equals("project"))
		            	img = omeroIcons.get(OmeroObjectType.PROJECT);
		            else if (item.type.toLowerCase().equals("dataset"))
		            	img = omeroIcons.get(OmeroObjectType.DATASET);
		            else {
		            	// To avoid ConcurrentModificationExceptions
						var it = thumbnailBank.keySet().iterator();
						synchronized (thumbnailBank) {
							while (it.hasNext()) {
								var id = it.next();
								if (id == item.id) {
									img = thumbnailBank.get(id);
									continue;
								}
							}							
						}
					}

		            if (img != null) {
		            	var wi = paintBufferedImageOnCanvas(img, canvas, prefScale);
		            	Tooltip tooltip = new Tooltip();
		            	if (item.type.toLowerCase().equals("image")) {
		            		// Setting tooltips on hover
		            		ImageView imageView = new ImageView(wi);
		            		imageView.setFitHeight(250);
		            		imageView.setPreserveRatio(true);
		            		tooltip.setGraphic(imageView);
		            	} else
		            		tooltip.setText(item.name);
		            			            	
		            	setText(null);
		            	setTooltip(tooltip);
		            }

		            setGraphic(canvas);
					setAlignment(Pos.CENTER);
				}
		    });
		    nameCol.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().name));
		    acquisitionCol.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().acquired.toString()));
		    importedCol.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().imported.toString()));
		    groupCol.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().group));
		    linkCol.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue()));
		    linkCol.setCellFactory(n -> new TableCell<SearchResult, SearchResult>() {
		        private final Button button = new Button("Link");

		        @Override
		        protected void updateItem(SearchResult item, boolean empty) {
		            super.updateItem(item, empty);
		            if (item == null) {
		                setGraphic(null);
		                return;
		            }

		            button.setOnAction(e -> QuPathGUI.launchBrowserWindow(item.link.toString()));
		            setGraphic(button);
		        }
		    });
		    
		    resultsTableView.getColumns().add(typeCol);
		    resultsTableView.getColumns().add(nameCol);
		    resultsTableView.getColumns().add(acquisitionCol);
		    resultsTableView.getColumns().add(importedCol);
		    resultsTableView.getColumns().add(groupCol);
		    resultsTableView.getColumns().add(linkCol);
			resultsTableView.setItems(obsResults);
			resultsTableView.getColumns().forEach(e -> e.setStyle( "-fx-alignment: CENTER;"));
			
			resultsTableView.setOnMouseClicked(e -> {
		        if (e.getClickCount() == 2) {
		        	var selectedItem = resultsTableView.getSelectionModel().getSelectedItem();
		        	if (selectedItem != null) {
						try {
							List<URI> URIs = OmeroTools.getURIs(selectedItem.link.toURI());
							var uriStrings = URIs.parallelStream().map(uriTemp -> uriTemp.toString()).toArray(String[]::new);
							if (URIs.size() > 0)
								ProjectCommands.promptToImportImages(qupath, uriStrings);
							else
								Dialogs.showErrorMessage("No image found", "No image found in OMERO object.");
						} catch (IOException | URISyntaxException ex) {
							logger.error("Error while importing " + selectedItem.name + ": {}", ex.getLocalizedMessage());
						}
		        	}
		        }
		    });
			
			resultsTableView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
				if (n == null)
					return;
				
				if (resultsTableView.getSelectionModel().getSelectedItems().size() == 1)
					importBtn.setText("Import " + n.type);
				else
					importBtn.setText("Import OMERO objects");
			});
			
			
			searchResultPane.addRow(0,  resultsTableView);
			searchOptionPane.setVgap(10.0);
			
			searchPane.setLeft(searchOptionPane);
			searchPane.setRight(searchResultPane);
			
			Insets insets = new Insets(10);
			BorderPane.setMargin(searchOptionPane, insets);
			BorderPane.setMargin(searchResultPane, insets);
			
			var dialog = Dialogs.builder().content(searchPane).build();
			dialog.setOnCloseRequest(e -> {
				// Make sure we're not still sending requests
				executorQuery.shutdownNow();
				executorThumbnail.shutdownNow();
			});
			dialog.showAndWait();
		}
		
		
		private void searchQuery() {
			List<SearchResult> results = new ArrayList<>();
			
			List<String> fields = new ArrayList<>();
			if (restrictedByName.isSelected()) fields.add("field=name");
			if (restrictedByDesc.isSelected()) fields.add("field=description");
			
			List<OmeroObjectType> datatypes = new ArrayList<>();
			if (searchForImages.isSelected()) datatypes.add(OmeroObjectType.IMAGE);
			if (searchForDatasets.isSelected()) datatypes.add(OmeroObjectType.DATASET);
			if (searchForProjects.isSelected()) datatypes.add(OmeroObjectType.PROJECT);
			if (searchForWells.isSelected()) datatypes.add(OmeroObjectType.WELL);
			if (searchForPlates.isSelected()) datatypes.add(OmeroObjectType.PLATE);
			if (searchForScreens.isSelected()) datatypes.add(OmeroObjectType.SCREEN);
			
			Owner owner = ownedByCombo.getSelectionModel().getSelectedItem();
			Group group = groupCombo.getSelectionModel().getSelectedItem();

			try {
				var response = OmeroRequests.requestAdvancedSearch(
						serverURI.getScheme(), 
						serverURI.getHost(), 
						searchTf.getText(), 
						fields.toArray(new String[0]), 
						datatypes.stream().map(e -> "datatype=" + e.toURLString()).toArray(String[]::new), 
						group, 
						owner
				);
				
				if (!response.contains("No results found"))
					results = parseHTML(response);
				
				populateThumbnailBank(results);
				updateTableView(results);
				
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage());
				Dialogs.showErrorMessage("Search query", "An error occurred. Check log for more information.");
				return;
			}
		}
		
		private List<SearchResult> parseHTML(String response) {
			List<SearchResult> searchResults = new ArrayList<>();
	        Matcher rowMatcher = patternRow.matcher(response);
	        while (rowMatcher.find()) {
	            String[] values = new String[7];
	            String row = rowMatcher.group(0);
	            values[0] = rowMatcher.group(1);
	            values[1] = rowMatcher.group(2);
	            String value = "";
	            
	            int nValue = 2;
	            for (var pattern: patterns) {
	                Matcher matcher = pattern.matcher(row);
	                if (matcher.find()) {
	                    value = matcher.group(1);
	                    row = row.substring(matcher.end());
	                }
	                values[nValue++] = value;
	            }
	            
	            try {
					SearchResult obj = new SearchResult(values);
					searchResults.add(obj);
				} catch (Exception e) {
					logger.error("Could not parse search result. {}", e.getLocalizedMessage());
				}
	        }
	        
	        return searchResults;
		}
		
		private void updateTableView(List<SearchResult> results) {
			resultsTableView.getItems().setAll(results);
			Platform.runLater(() -> resultsTableView.refresh());
		}
		
		/**
		 * Send a request to batch load thumbnails that are not already 
		 * stored in {@code thumbnailBank}.
		 * @param results 
		 */
		private void populateThumbnailBank(List<SearchResult> results) {
			
			List<SearchResult> thumbnailsToQuery = results.parallelStream()
					.filter(e -> {
						// To avoid ConcurrentModificationExceptions
						synchronized (thumbnailBank) {
							for (var id: thumbnailBank.keySet()) {
								if (id == e.id)
									return false;
							}
						}
						return true;
					})
					.collect(Collectors.toList());
			
			if (thumbnailsToQuery.isEmpty())
				return;
			
			if (executorThumbnail != null)
				executorThumbnail.shutdownNow();
			executorThumbnail = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("batch-thumbnail-request", true));
			 
			for (var searchResult: thumbnailsToQuery) {
				executorThumbnail.submit(() -> {
					BufferedImage thumbnail = OmeroTools.getThumbnail(serverURI, searchResult.id, imgPrefSize);
					if (thumbnail != null) {
						thumbnailBank.put(searchResult.id, thumbnail);	// 'Put' shouldn't need synchronized key
						Platform.runLater(() -> resultsTableView.refresh());
					}
				});
			}
		}
	}


	private class SearchResult {
		private String type;
		private int id;
		private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		private String name;
		private Date acquired;
		private Date imported;
		private String group;
		private URL link;
		
		private SearchResult(String[] values) throws ParseException, MalformedURLException {
			this.type = values[0];
			this.id = Integer.parseInt(values[1]);
			this.name = values[2];
			this.acquired = dateFormat.parse(values[3]);
			this.imported = dateFormat.parse(values[4]);
			this.group = values[5];
			this.link = URI.create(serverURI.getScheme() + "://" + serverURI.getHost() + values[6]).toURL();
		}
	}
	
	/**
	 * Request closure of the dialog
	 */
	void requestClose() {
    	if (dialog != null)
    		dialog.fireEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST));
	}
	
	/**
	 * Shutdown the pool that loads OMERO objects' children (treeView)
	 */
	void shutdownPools() {
		executorTable.shutdownNow();
		executorThumbnails.shutdownNow();
	}
}
