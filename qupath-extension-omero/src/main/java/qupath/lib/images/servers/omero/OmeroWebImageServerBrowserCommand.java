package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
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
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageServer;
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
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.images.servers.omero.OmeroObjects.Project;
import qupath.lib.io.GsonTools;

/**
 * Command to browse a specified OMERO server.
 * 
 * @author Melvin Gelbard
 * 
 */
public class OmeroWebImageServerBrowserCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(OmeroWebImageServerBrowserCommand.class);
	private static final String BOLD = "-fx-font-weight: bold";
	
	private final QuPathGUI qupath;
	private OmeroWebImageServer server;
	private BorderPane mainPane;
	private ComboBox<Owner> comboOwner;
	private ComboBox<Group> comboGroup;
	private Set<Owner> owners;
	private Set<Group> groups;
	private TreeView<OmeroObject> tree;
	private TableView<Integer> description;
	private OmeroObject[] selectedObjects;
	private TextField filter;
	private Canvas canvas;
	private int imgPrefSize = 256;
	private ProgressIndicator progressIndicator;
	private Button moreInfoBtn;
	private Button importBtn;
	
	
	private StringConverter<Owner> ownerStringConverter;
	
	private Map<OmeroObjectType, BufferedImage> omeroIcons;
	
	// Get table item children in separate thread
	private ExecutorService executorTable;
	
	private Map<Integer, BufferedImage> thumbnailBank;	// To store thumbnails
	
	private List<OmeroObject> serverChildrenList;
	private Map<OmeroObject, List<OmeroObject>> projectMap;
	private Map<OmeroObject, List<OmeroObject>> datasetMap;
	
	private String[] projectAttributes;
	private String[] datasetAttributes;
	private String[] imageAttributes;
	
	private Integer[] imageIndices;
	private Integer[] datasetIndices;
	private Integer[] projectIndices;

	
    OmeroWebImageServerBrowserCommand(QuPathGUI qupath) {
    	this.qupath = qupath;
    }
    
    @Override
    public void run() {
    	// Initialize class variables
    	initializeVariables();
    	
    	// Need to choose which server to browse first
    	if (qupath.getImageData() != null) {
    		var serverTemp = qupath.getImageData().getServer();
    		if (serverTemp instanceof OmeroWebImageServer)
    			server = (OmeroWebImageServer) serverTemp;
    	}
    	if (server == null) {
    		var entry = Dialogs.showChoiceDialog("No open OMERO image", "Choose an entry for a server to browse", qupath.getProject().getImageList(), null);
    		if (entry == null)
    			return;
			try {
				ImageServer<BufferedImage> serverTemp = entry.getServerBuilder().build();
				if (serverTemp instanceof OmeroWebImageServer)
	    			server = (OmeroWebImageServer)serverTemp;
	    		else {
	    			Dialogs.showErrorMessage("Not an OMERO image", "Chosen image does not come from an OMERO server");
	    			return;
	    		}
			} catch (Exception ex) {
				Dialogs.showErrorMessage("Error", "An error occurred while processing " + entry);
				return;
			}
    	}
    	if (!server.getWebclient().loggedIn()) {
    		Dialogs.showErrorMessage("Not logged in", "You need to log in to the server first!");
    		return;
    	}

    	
		mainPane = new BorderPane();
		BorderPane serverInfoPane = new BorderPane();
		GridPane serverAttributePane = new GridPane();
		SplitPane browsePane = new SplitPane();
		GridPane browseLeftPane = new GridPane();
		GridPane browseRightPane = new GridPane();

		progressIndicator = new ProgressIndicator();
		progressIndicator.setPrefSize(20, 20);
		progressIndicator.setMinSize(20, 20);
		progressIndicator.setOpacity(0);
		
		// Info about the server to display at the top
		var hostLabel = new Label(server.getHost());
		var username = server.getWebclient().getUsername();
		var usernameText = username.isEmpty() ? new Label("public") : new Label(username);
		var nOpenImages = new Label(OmeroWebClients.getAllClients().get(server.getHost()).size() + "");
		hostLabel.setStyle(BOLD);
		usernameText.setStyle(BOLD);
		nOpenImages.setStyle(BOLD);
		
		serverAttributePane.addRow(0, new Label("Server: "), hostLabel);
		serverAttributePane.addRow(1, new Label("Username: "), usernameText);
		serverAttributePane.addRow(2, new Label("Open images: "), nOpenImages);
		serverInfoPane.setLeft(serverAttributePane);
		serverInfoPane.setRight(progressIndicator);
		
		executorTable = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("children-loader", true));
		
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
		
		OmeroObjectTreeItem root = new OmeroObjectTreeItem(new OmeroObjects.Server(server));
		tree.setRoot(root);
		tree.setShowRoot(false);
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tree.setCellFactory(n -> new OmeroObjectCell());
		tree.setOnMouseClicked(e -> {
	        if (e.getClickCount() == 2) {
	        	var selectedItem = tree.getSelectionModel().getSelectedItem();
	        	if (selectedItem != null && selectedItem.getValue().getType() == OmeroObjectType.IMAGE && isSupported(selectedItem.getValue())) {
	        		String type = selectedItem.getValue().getType().toString().toLowerCase();
	        		String url = String.format("%s://%s/webclient/?show=%s-%d", 
	        				server.getScheme(), 
	        				server.getHost(), 
	        				type, 
	        				selectedItem.getValue().getId()
	        				);
	        		ProjectCommands.promptToImportImages(qupath, url);
	        	}
	        }
	    });
		
		owners.add(Owner.getAllMembersOwner());
		comboOwner.getItems().setAll(owners);
		comboOwner.getSelectionModel().selectFirst();
		comboOwner.setConverter(ownerStringConverter);
		comboOwner.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> refreshTree());
		
		comboGroup.getItems().setAll(groups);
		try {
			JsonObject mapImageInfo = OmeroRequests.requestObjectInfo(server.getScheme(), server.getHost(), Integer.parseInt(server.getId()), OmeroObjectType.IMAGE);
					
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
			logger.error("Could not parse OMERO 'Group'");
			groups.add(Group.getAllGroupsGroup());
			comboGroup.getItems().setAll(groups);
			comboGroup.getSelectionModel().selectFirst();
		}
		comboGroup.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> refreshTree());
		
		
		description = new TableView<>();
		TableColumn<Integer, String> attributeCol = new TableColumn<>("Attribute");
		TableColumn<Integer, String> valueCol = new TableColumn<>("Value");

		attributeCol.setCellValueFactory(cellData -> {
			var selectedItems = tree.getSelectionModel().getSelectedItems();
			if (selectedItems.size() == 1 && selectedItems.get(0).getValue() != null) {
				var type = selectedItems.get(0).getValue().getType();
				if (type.equals(OmeroObjectType.PROJECT))
					return new ReadOnlyObjectWrapper<String>(projectAttributes[cellData.getValue()]);
				else if (type.equals(OmeroObjectType.DATASET))
					return new ReadOnlyObjectWrapper<String>(datasetAttributes[cellData.getValue()]);
				else if (type.equals(OmeroObjectType.IMAGE))
					return new ReadOnlyObjectWrapper<String>(imageAttributes[cellData.getValue()]);				
			}
			return new ReadOnlyObjectWrapper<String>("");
			
		});
		valueCol.setCellValueFactory(cellData -> {
			if (selectedObjects != null && selectedObjects.length == 1) 
				return getObjectInfo(cellData.getValue(), selectedObjects[0]);
			else
				return new ReadOnlyObjectWrapper<String>();
		});
		valueCol.setCellFactory(n -> new TableCell<Integer, String>() {
			@Override
	        protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				setText(item);
				setTooltip(new Tooltip(item));
			}
		});
		
		// Get thumbnails in separate thread
		ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			clearCanvas();
			if (n != null) {
				if (description.getPlaceholder() == null)
					description.setPlaceholder(new Label("Multiple elements selected"));
				selectedObjects = tree.getSelectionModel().getSelectedItems().stream()
						.map(item -> item.getValue())
						.toArray(OmeroObject[]::new);

				updateDescription();
				if (selectedObjects.length == 1) {
					var selectedObjectLocal = n.getValue();
					if (selectedObjects.length == 1 && selectedObjects[0].getType() == OmeroObjectType.IMAGE) {
						// Check if thumbnail was previously cached
						if (thumbnailBank.containsKey(selectedObjectLocal.getId()))
							setThumbnail(thumbnailBank.get(selectedObjectLocal.getId()));
						else {
							// Get thumbnail from JSON API in separate thread (and show progress indicator)
							progressIndicator.setOpacity(100);
							executor.submit(() -> {
								BufferedImage img = OmeroTools.getThumbnail(server, selectedObjectLocal.getId(), imgPrefSize);
								if (img != null) {
									thumbnailBank.put(selectedObjectLocal.getId(), img);
									setThumbnail(img);
								}
								Platform.runLater(() -> {
									progressIndicator.setOpacity(0);
								});		
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
				// Change text on "Open .." button
				Platform.runLater(() -> {
					if (selectedObjects.length > 1)
						importBtn.setText("Import selected");
					else if (selectedObjects[0].getType() == OmeroObjectType.IMAGE)
						importBtn.setText("Import image");
					else if (selectedObjects[0].getType() == OmeroObjectType.DATASET)
						importBtn.setText("Import dataset");
					else if (selectedObjects[0].getType() == OmeroObjectType.PROJECT)
						importBtn.setText("Import Omero project");
					
//					moreInfoBtn.setDisable(selectedObjects.length != 1);
					var allSupported = !List.of(selectedObjects).stream().allMatch(obj -> isSupported(obj));
					importBtn.setDisable(allSupported);
				});
			}
		});
		
		Button collapseBtn = new Button("Collapse all items");
		collapseBtn.setOnMouseClicked(e -> collapseTreeView(tree.getRoot()));
		
		moreInfoBtn = new Button("More info..");
		moreInfoBtn.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull().or(Bindings.size(tree.getSelectionModel().getSelectedItems()).isNotEqualTo(1)));
		moreInfoBtn.setOnMouseClicked(e -> new AdvancedObjectInfo(tree.getSelectionModel().getSelectedItem().getValue()));		
		filter.setPromptText("Search project");
		filter.textProperty().addListener((v, o, n) -> {
			refreshTree();
			if (n.isEmpty())
				collapseTreeView(tree.getRoot());
			else
				expandTreeView(tree.getRoot());
				
		});
		
		Button advancedSearchBtn = new Button("Advanced search");
		advancedSearchBtn.setOnAction(e -> new AdvancedSearch(server));
		
		PaneTools.addGridRow(browseLeftPane, 0, 0, "Filter by", comboGroup, comboOwner);
		PaneTools.addGridRow(browseLeftPane, 1, 0, null, tree, tree);
		PaneTools.addGridRow(browseLeftPane, 2, 0, "Expand/collapse items", collapseBtn, moreInfoBtn);
		PaneTools.addGridRow(browseLeftPane, 3, 0, null, filter, advancedSearchBtn);
		
		canvas = new Canvas();
		description.getColumns().addAll(attributeCol, valueCol);
		
		Button clipboardBtn = new Button("Copy URI to clipboard");
		clipboardBtn.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
		importBtn = new Button("Import image");
		importBtn.setDisable(true);
		
		clipboardBtn.setOnMouseClicked(e -> {
			var selected = tree.getSelectionModel().getSelectedItems();
			if (selected != null) {
				try {
					ClipboardContent content = new ClipboardContent();
					if (selected.size() == 1) {
						content.putString(getURI(selected.get(0).getValue()));						
					} else {
						List<String> URIs = new ArrayList<>();
						for (var item: selected) {
							URIs.add(getURI(item.getValue()));						
						}
						content.putString("[" + String.join(", ", URIs) + "]");						
					}
					Clipboard.getSystemClipboard().setContent(content);
				} catch (URISyntaxException ex) {
					logger.error("Could not copy to clipboard: {}", ex.getLocalizedMessage());
				}
			}
		});
		
		importBtn.setOnMouseClicked(e -> {
			String[] URIs = tree.getSelectionModel().getSelectedItems().stream()
					.flatMap(item -> {
						try {
							return OmeroTools.getURIs(URI.create(getURI(item.getValue()))).stream();
						} catch (URISyntaxException | IOException ex) {
							logger.error("Could not import " + item.getValue().getName() + ": {}", ex.getLocalizedMessage());
						}
						return null;
					}).map(item -> item.toString())
					  .toArray(String[]::new);
			ProjectCommands.promptToImportImages(qupath, URIs);
		});
		
		
		PaneTools.addGridRow(browseRightPane, 0, 0, null, canvas, canvas);
		PaneTools.addGridRow(browseRightPane, 1, 0, null, description, description);
		PaneTools.addGridRow(browseRightPane, 2, 0, null, clipboardBtn, importBtn);
        
        // Set HGrow and VGrow
		GridPane.setHgrow(comboOwner,  Priority.ALWAYS);
		GridPane.setHgrow(comboGroup,  Priority.ALWAYS);
		GridPane.setHgrow(description,  Priority.ALWAYS);
		GridPane.setHgrow(tree, Priority.ALWAYS);
		GridPane.setHgrow(moreInfoBtn, Priority.ALWAYS);
		GridPane.setHgrow(collapseBtn, Priority.ALWAYS);
		GridPane.setHgrow(filter, Priority.ALWAYS);
		GridPane.setHgrow(advancedSearchBtn, Priority.ALWAYS);
		GridPane.setHgrow(clipboardBtn, Priority.ALWAYS);
		GridPane.setHgrow(importBtn, Priority.ALWAYS);
		GridPane.setVgrow(description, Priority.ALWAYS);
		GridPane.setVgrow(tree, Priority.ALWAYS);
		
		// Set max width & height
		comboOwner.setMaxWidth(Double.MAX_VALUE);
		comboGroup.setMaxWidth(Double.MAX_VALUE);
		moreInfoBtn.setMaxWidth(Double.MAX_VALUE);
		collapseBtn.setMaxWidth(Double.MAX_VALUE);
		moreInfoBtn.setMaxWidth(Double.MAX_VALUE);
		collapseBtn.setMaxWidth(Double.MAX_VALUE);
        advancedSearchBtn.setMaxWidth(Double.MAX_VALUE);
        browseLeftPane.setMaxWidth(Double.MAX_VALUE);
        clipboardBtn.setMaxWidth(Double.MAX_VALUE);
        importBtn.setMaxWidth(Double.MAX_VALUE);
		description.setMaxHeight(Double.MAX_VALUE);
		
		// Set paddings & gaps
		serverInfoPane.setPadding(new Insets(5, 15, 5, 5));
		serverAttributePane.setHgap(10.0);
		
		// Set specific sizes
		browsePane.setPrefWidth(600.0);
		browsePane.setDividerPosition(0, 0.6);
		valueCol.setPrefWidth(150.0);
		
		mainPane.setTop(serverInfoPane);
		mainPane.setCenter(browsePane);
		browsePane.getItems().addAll(browseLeftPane, browseRightPane);
		
		Stage dialog = new Stage();
		dialog.sizeToScene();
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.setTitle("OMERO web server");
		dialog.setScene(new Scene(mainPane));
		dialog.setOnCloseRequest(e -> shutdownPool());
		dialog.showAndWait();
    }

    
    private Map<OmeroObjectType, BufferedImage> getOmeroIcons() {
    	Map<OmeroObjectType, BufferedImage> map = new HashMap<>();
		try {
			// Load project icon
			map.put(OmeroObjectType.PROJECT, OmeroRequests.requestIcon(server.getScheme(), server.getHost(), "folder16.png"));
			
			// Load dataset icon
			map.put(OmeroObjectType.DATASET, OmeroRequests.requestIcon(server.getScheme(), server.getHost(), "folder_image16.png"));
		} catch (IOException e) {
			logger.warn("Could not load OMERO icons: {}", e.getLocalizedMessage());
		}
		return map;
	}


	private String getURI(OmeroObject omeroObj) throws URISyntaxException {
		var apiUri = new URI(omeroObj.getAPIURLString());
		return String.format("%s://%s/webclient/?show=%s-%d", 
				apiUri.getScheme(), 
				apiUri.getHost(), 
				omeroObj.getType().toString().toLowerCase(), 
				omeroObj.getId()
				);
	}


	private void refreshTree() {
		tree.setRoot(null);
		tree.refresh();
		tree.setRoot(new OmeroObjectTreeItem(new OmeroObjects.Server(server)));
		tree.refresh();
	}


	private ObservableValue<String> getObjectInfo(Integer index, OmeroObject omeroObject) {
		if (omeroObject == null)
			return new ReadOnlyObjectWrapper<String>();
		String[] outString = null;
		String name = omeroObject.getName();
		String id = omeroObject.getId() + "";
		String owner = omeroObject.getOwner().getName();
		String group = omeroObject.getGroup().getName();
		if (omeroObject.getType().equals(OmeroObjectType.PROJECT)) {
			String description = ((Project)omeroObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = omeroObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, group, nChildren};
			
		} else if (omeroObject.getType().equals(OmeroObjectType.DATASET)) {
			String description = ((Dataset)omeroObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = omeroObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, group, nChildren};

		} else if (omeroObject.getType().equals(OmeroObjectType.IMAGE)) {
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
		if (selectedObjects.length == 1) {
			if (selectedObjects[0].getType().equals(OmeroObjectType.PROJECT)) {
				projectIndices = new Integer[projectAttributes.length];
				for (int index = 0; index < projectAttributes.length; index++) projectIndices[index] = index;
				indexList = FXCollections.observableArrayList(projectIndices);
				
			} else if (selectedObjects[0].getType().equals(OmeroObjectType.DATASET)) {
				datasetIndices = new Integer[datasetAttributes.length];
				for (int index = 0; index < datasetAttributes.length; index++) datasetIndices[index] = index;
				indexList = FXCollections.observableArrayList(datasetIndices);
				
			} else if (selectedObjects[0].getType().equals(OmeroObjectType.IMAGE)) {
				imageIndices = new Integer[imageAttributes.length];
				for (int index = 0; index < imageAttributes.length; index++) imageIndices[index] = index;
				indexList = FXCollections.observableArrayList(imageIndices);
			}
		}
		description.getItems().setAll(indexList);
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
		
		var wi =  SwingFXUtils.toFXImage(img, null);
		if (wi == null)
			return wi;
		else
			GuiTools.paintImage(canvas, wi);
		
		return wi;
	}



	
	
	private void setThumbnail(BufferedImage img) {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

		var wi = SwingFXUtils.toFXImage(img, null);
		if (wi == null)
			return;
		
		canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		canvas.setWidth(imgPrefSize);
		canvas.setHeight(imgPrefSize);
		GuiTools.paintImage(canvas, wi);
	}
	
	/**
	 * Return whether the image type is supported by QuPath
	 * @param omeroObj
	 * @return isSupported
	 */
	private boolean isSupported(OmeroObject omeroObj) {
		if (omeroObj == null || !omeroObj.getType().equals(OmeroObjectType.IMAGE))
			return true;
		return ((Image)omeroObj).getPixelType().equals("uint8") && 
				Integer.parseInt(getObjectInfo(7, omeroObj).getValue()) == 3;
		
	}
	
	private void clearCanvas() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
	}
	
	/**
	 * Set the specified item and its children to the specified expanded mode
	 * @param item
	 * @param expanded
	 */
	private void expandTreeView(TreeItem<OmeroObject> item){
	    if (item != null && !item.isLeaf()) {
	    	if (!(item.getValue().getType() == OmeroObjectType.SERVER))
	    		item.setExpanded(true);
	    	
    		for (var child: item.getChildren()) {
    			expandTreeView(child);
	        }
	    }
	}
	
	private void collapseTreeView(TreeItem<OmeroObject> item){
	    if (item != null && !item.isLeaf()) {
	    	if (item.getValue().getType() == OmeroObjectType.SERVER) {
	    		for (var child: item.getChildren()) {
	    			child.setExpanded(false);
	    		}
	    	}
	    }
	}
	
	/**
	 * This method exists just to ensure we're not using data from last time the browse server pane was requested.
	 */
	private void initializeVariables() {
		this.server = null;
		this.owners = new HashSet<>();
		this.groups = new HashSet<>();
		this.thumbnailBank = new ConcurrentHashMap<Integer, BufferedImage>();
		this.serverChildrenList = new ArrayList<>();
		this.projectMap = new ConcurrentHashMap<>();
		this.datasetMap = new ConcurrentHashMap<>();
		
		this.tree = new TreeView<>();
		this.comboGroup = new ComboBox<>();
		this.comboOwner = new ComboBox<>();
		this.filter = new TextField();
		this.selectedObjects = null;
		
		
		this.projectAttributes = new String[] {"Name", 
				"Id",
				"Description",
				"Owner",
				"Group",
				"Num. datasets"};
		
		this.datasetAttributes = new String[] {"Name", 
				"Id", 
				"Description",
				"Owner",
				"Group",
				"Num. images"};
		
		this.imageAttributes = new String[] {"Name", 
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
	}
	
	/**
	 * Display an OMERO object using its name.
	 */
	private class OmeroObjectCell extends TreeCell<OmeroObject> {
		
		Canvas canvas = new Canvas();
		
		@Override
        public void updateItem(OmeroObject item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
            	String name;
            	setOpacity(1.0);
            	if (item.getType().equals(OmeroObjectType.SERVER))
            		name = server.getHost();
            	else if (item.getType().equals(OmeroObjectType.IMAGE)) {
            		name = item.getName();
            		setGraphic(null);
            		if (!isSupported(item))
            			setOpacity(0.5);
            	} else {
            		// If it's either project or dataset, need to set graphic with icon
            		BufferedImage img = null;
            		if (item.getType().equals(OmeroObjectType.PROJECT)) {
            			name = item.getName() + " (" + item.getNChildren() + ")";
            			img = omeroIcons.get(OmeroObjectType.PROJECT);
            			
            		} else {
            			name = item.getName() + " (" + item.getNChildren() + ")";
            			img = omeroIcons.get(OmeroObjectType.DATASET);
            		}
            		
            		if (img != null) {
            			paintBufferedImageOnCanvas(img, canvas, 15);
            			setGraphic(canvas);
            		}
            	}
                setText(name);
            }
        }		
	}
	
	/**
	 * TreeItem to help with the display of Omero objects.
	 */
	private class OmeroObjectTreeItem extends TreeItem<OmeroObject> {
		
		private boolean computed = false;
		
		public OmeroObjectTreeItem(OmeroObject obj) {
			super(obj);
		}

		/**
		 * This method gets the children of the current tree item.
		 * Only the currently expanded items will call this method.
		 * <p>
		 * If we have never seen the current tree item, a JSON request
		 * will be sent to the OMERO API to get its children, this value 
		 * will then be stored (cache). If we have seen this tree item 
		 * before, it will simply return the stored value.
		 * 
		 * All stored values are in {@code projectMap} & {@code datasetMap}.
		 */
		@Override
		public ObservableList<TreeItem<OmeroObject>> getChildren() {
			if (!isLeaf() && !computed) {
				progressIndicator.setOpacity(100);
				var filterTemp = filter.getText();
				executorTable.submit(() -> {
					var omeroObj = this.getValue();
					
					// Get children and populate maps if necessary
					List<OmeroObject> children = getChildren(omeroObj);
					
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
								comboGroup.getItems().setAll(groups);
							});
						}
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

						
//						if (tempOwners.size() > owners.size()) {
//							owners.clear();
//							owners.add(Owner.getAllMembersOwner());
//							owners.addAll(tempOwners);
//						}
//							
//						// Only display Owners from selected Group
//						Set<Owner> ownersFromGroups;
//						var selectedGroup = comboGroup.getSelectionModel().getSelectedItem();
//						if (selectedGroup != null) {
//							// Note: this will not display Owners with no OmeroObject in this specific Group
//							var temp = children.stream()
//									.filter(e -> e.getGroup() == selectedGroup)
//									.map(e -> e.getOwner())
//									.collect(Collectors.toSet());
//							ownersFromGroups = new HashSet<>();
//							ownersFromGroups.add(Owner.getAllMembersOwner());
//							ownersFromGroups.addAll(temp);
//						} else 
//							ownersFromGroups = owners;
//
//						// Check if the set of Owners from the selected Group is the same as the
//						// one displayed in the comboOwner already (in which case no update is needed)
//						Set<Owner> tempSet = new HashSet<>();
//						tempSet.addAll(comboOwner.getItems());
//						if (!ownersFromGroups.equals(tempSet)) {
//							var selectedOwner = comboOwner.getSelectionModel().getSelectedItem();
//							// Update comboBox
//							Platform.runLater(() -> {
//								comboOwner.getItems().setAll(ownersFromGroups);
//								// Attempt not to change the currently selected owner if present in new Owner set
//								if (ownersFromGroups.contains(selectedOwner))
//									comboOwner.getSelectionModel().select(selectedOwner);
//								else
//									comboOwner.getSelectionModel().selectFirst(); // 'All members'
//							});
//						}
					}
					
					var items = children.parallelStream()
							.filter(e -> {
								Group selected = comboGroup.getSelectionModel().getSelectedItem();
								if (selected != null) {
									if (selected.equals(Group.getAllGroupsGroup()))
										return true;
									return e.getGroup().equals(selected);
								}
								return true;
							})
							.filter(e -> {
								Owner selected = comboOwner.getSelectionModel().getSelectedItem();
								if (selected != null) {
									if (selected.equals(Owner.getAllMembersOwner()))
										return true;
									return e.getOwner().equals(selected);
								}
								return true;
							})
							.filter(e -> matchesSearch(e, filterTemp))
							.map(e -> new OmeroObjectTreeItem(e))
							.collect(Collectors.toList());
					
					super.getChildren().setAll(items);
					computed = true;

					Platform.runLater(() -> progressIndicator.setOpacity(0));
					return super.getChildren();
				});
			}
			//progressIndicator.setOpacity(0);
			return super.getChildren();
		}
		
		
		@Override
		public boolean isLeaf() {
			var obj = this.getValue();
			if (obj.getType().equals(OmeroObjectType.SERVER))
				return false;
			if (obj.getType().equals(OmeroObjectType.IMAGE))
				return true;
			return obj.getNChildren() == 0;
		}
		
		private boolean matchesSearch(OmeroObject obj, String filter) {
			if (filter == null || filter.isEmpty())
				return true;
			
			if (obj.getType() == OmeroObjectType.SERVER)
				return true;
			
			if (obj.getParent().getType() == OmeroObjectType.SERVER)
				return obj.getName().toLowerCase().contains(filter.toLowerCase());
			
			return matchesSearch(obj.getParent(), filter);
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
			else if (omeroObj.getType() == OmeroObjectType.PROJECT && projectMap.containsKey((Project)omeroObj))
				return projectMap.get((Project)omeroObj);
			else if (omeroObj.getType() == OmeroObjectType.DATASET && datasetMap.containsKey((Dataset)omeroObj))
				return datasetMap.get((Dataset)omeroObj);
			else if (omeroObj.getType() == OmeroObjectType.IMAGE)
				return new ArrayList<OmeroObject>();
			
			List<OmeroObject> children;
			try {
				children = OmeroTools.readOmeroObjects(server, omeroObj);
				
				if (omeroObj.getType() == OmeroObjectType.SERVER)
					serverChildrenList = children;
				else if (omeroObj .getType() == OmeroObjectType.PROJECT)
					projectMap.put(omeroObj, children);
				else if (omeroObj.getType() == OmeroObjectType.DATASET)
					datasetMap.put(omeroObj, children);
			} catch (IOException e) {
				logger.error("Could not fetch server information: {}", e.getLocalizedMessage());
				return new ArrayList<OmeroObject>();
			}
			return children;
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
			this.tags = OmeroTools.readOmeroAnnotations(server, obj, OmeroAnnotationType.TAG);
			this.keyValuePairs = OmeroTools.readOmeroAnnotations(server, obj, OmeroAnnotationType.MAP);
//			this.tables = OmeroTools.getOmeroAnnotations(server, obj, OmeroAnnotationType.TABLE);
			this.attachments = OmeroTools.readOmeroAnnotations(server, obj, OmeroAnnotationType.ATTACHMENT);
			this.comments = OmeroTools.readOmeroAnnotations(server, obj, OmeroAnnotationType.COMMENT);
			this.ratings = OmeroTools.readOmeroAnnotations(server, obj, OmeroAnnotationType.RATING);
//			this.others = OmeroTools.getOmeroAnnotations(server, obj, OmeroAnnotationType.CUSTOM);
			
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
			Button plusBtn = new Button("+");
			tp.setText(title);
			tp.setGraphic(plusBtn);
			tp.setContentDisplay(ContentDisplay.RIGHT);
			plusBtn.translateXProperty().bind(Bindings.createDoubleBinding(
					() -> tp.getWidth() - plusBtn.getLayoutX() - plusBtn.getWidth() - 10,
				    tp.widthProperty())
			);
			plusBtn.setDisable(true);	// Not yet supported
			
			if (omeroAnnotations.getAnnotations().isEmpty())
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
			
			if (omeroAnnotations == null || 
					omeroAnnotations.getAnnotations() == null ||
					omeroAnnotations.getType() == null)
				return gp;
			
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
			
			plusBtn.setOnAction(e -> promptAddAnnotationsDialog(omeroAnnotations.getType()));
			
			sp.setContent(gp);
			tp.setContent(sp);
			return tp;
		}

		// TODO
		private boolean promptAddAnnotationsDialog(OmeroAnnotationType type) {
			GridPane gp = new GridPane();
			switch (type) {
			case TAG:
				String value = Dialogs.showInputDialog("Add " + type, "Value", "");
				return OmeroTools.writeTagAnnotation(server, obj, value);
			case MAP:
				TextField tfKey = new TextField();
				TextField tfValue = new TextField();
				PaneTools.addGridRow(gp, 0, 0, "Key", new Label("Key"), tfKey);
				PaneTools.addGridRow(gp, 1, 0, "Value", new Label("Value"), tfValue);
				
				var response = Dialogs.builder().content(gp).buttons(ButtonType.CANCEL, ButtonType.APPLY).build().showAndWait();
				if (response.get() == ButtonType.APPLY) {
					if (tfKey.getText().isEmpty() || tfValue.getText().isEmpty()) {
						Dialogs.showErrorMessage("Empty key/value", "Key/value cannot be empty.");
						return false;
					}
					logger.info("Sending new map annotation to OMERO.");
				}
				break;
			case ATTACHMENT:
				Dialogs.showErrorMessage("Not supported yet", "Not supported yet!");
				break;
			case COMMENT:
				value = Dialogs.showInputDialog("Add " + type, "Value", "");
				return OmeroTools.writeCommentAnnotation(server, obj, value);
			case RATING:
				Dialogs.showErrorMessage("Not supported yet", "Not supported yet!");
				break;
			default:
				Dialogs.showErrorMessage("Not supported yet", "Not supported yet!");
			}
			return false;

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
		private final ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("query-processing", true));
		
		// Load thumbnail in separate thread
		private ExecutorService executor2;
		
		private final Pattern patternRow = Pattern.compile("<tr id=\"(.+?)-(.+?)\".+?</tr>", Pattern.DOTALL | Pattern.MULTILINE);
	    private final Pattern patternDesc = Pattern.compile("<td class=\"desc\"><a>(.+?)</a></td>");
	    private final Pattern patternDate = Pattern.compile("<td class=\"date\">(.+?)</td>");
	    private final Pattern patternGroup = Pattern.compile("<td class=\"group\">(.+?)</td>");
	    private final Pattern patternLink = Pattern.compile("<td><a href=\"(.+?)\"");

	    private final Pattern[] patterns = new Pattern[] {patternDesc, patternDate, patternDate, patternGroup, patternLink};
		
		private AdvancedSearch(OmeroWebImageServer server) {
			
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
			groupCombo.setDisable(true);	// TODO: disabled for now because it would need to be synched with ownedByCombo..
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
				executor.submit(() -> searchQuery());
				
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
			importBtn.setMaxWidth(Double.MAX_VALUE);
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
							var uriStrings = URIs.parallelStream().map(uri -> uri.toString()).toArray(String[]::new);
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
				executor.shutdownNow();
				executor2.shutdownNow();
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
						server.getScheme(), 
						server.getHost(), 
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
			
			if (executor2 != null)
				executor2.shutdownNow();
			executor2 = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("batch-thumbnail-request", true));
			 
			for (var searchResult: thumbnailsToQuery) {
				executor2.submit(() -> {
					BufferedImage thumbnail = OmeroTools.getThumbnail(server, searchResult.id, imgPrefSize);
					if (thumbnail != null) {
						thumbnailBank.put(searchResult.id, thumbnail);	// 'Put' shouldn't need synchronized key
						Platform.runLater(() -> resultsTableView.refresh());
					}
				});
			}
		}
	}


	private class SearchResult {
		String type;
		int id;
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		String name;
		Date acquired;
		Date imported;
		String group;
		URL link;
		
		public SearchResult(String[] values) throws ParseException, MalformedURLException {
			this.type = values[0];
			this.id = Integer.parseInt(values[1]);
			this.name = values[2];
			this.acquired = dateFormat.parse(values[3]);
			this.imported = dateFormat.parse(values[4]);
			this.group = values[5];
			this.link = URI.create(server.getScheme() + "://" + server.getHost() + values[6]).toURL();
		}
	}
	
	/**
	 * Shutdown the pool that loads OMERO objects' children (treeView)
	 */
	public void shutdownPool() {
		executorTable.shutdownNow();
	}
}