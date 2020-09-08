package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.omero.OmeroObjects.Dataset;
import qupath.lib.images.servers.omero.OmeroObjects.Image;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.images.servers.omero.OmeroObjects.Project;
import qupath.lib.images.servers.omero.OmeroObjects.Server;

class OmeroWebImageServerBrowser {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebImageServerBrowser.class);
	
	private QuPathGUI qupath;
	private OmeroWebImageServer server;
	private BorderPane mainPane;
	private GridPane clientPane;
	private GridPane serverPane;
	private ComboBox<Owner> comboOwner = new ComboBox<>();
	private List<Owner> owners = new ArrayList<>();
	private TreeView<OmeroObject> tree;
	private TableView<Integer> description;
	private OmeroObject selectedObject;
	private TextField filter = new TextField();
	private Canvas canvas;
	private int imgPrefSize = 256;
	private ProgressIndicator progressIndicator;
	private Button openBtn;
	
	private Node active = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.ACTIVE_SERVER);
	private Node inactive = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.INACTIVE_SERVER);
	
	// Get table item children in separate thread
	ExecutorService executorTable;
	
	private Map<OmeroObject, BufferedImage> thumbnailBank = new HashMap<OmeroObject, BufferedImage>();	// To store thumbnails
	
	
	private Map<OmeroObject, List<OmeroObject>> projectMap = new HashMap<>();
	private Map<OmeroObject, List<OmeroObject>> datasetMap = new HashMap<>();
	
	private String[] projectAttributes;
	private String[] datasetAttributes;
	private String[] imageAttributes;
	
	private Integer[] imageIndices;
	private Integer[] datasetIndices;
	private Integer[] projectIndices;
	
	
    OmeroWebImageServerBrowser(QuPathGUI qupath, OmeroWebImageServer server) {
    	this.qupath = qupath;
    	this.server = server;

		mainPane = new BorderPane();
		selectedObject = null;
		
		TabPane tabPane = new TabPane();
		
		BorderPane browsePane = new BorderPane();
		GridPane browseLeftPane = new GridPane();
		GridPane browseRightPane = new GridPane();
		
		clientPane = new GridPane();
		serverPane = new GridPane();

		progressIndicator = new ProgressIndicator();
		progressIndicator.setPrefSize(20, 20);
		progressIndicator.setMinSize(20, 20);
		progressIndicator.setOpacity(0);
		
		executorTable = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("children-loader", true));
		
		tree = new TreeView<>();
		OmeroObjectTreeItem root = new OmeroObjectTreeItem(new OmeroObjects.Server(server));
		tree.setRoot(root);
		tree.setShowRoot(false);
		tree.setCellFactory(n -> new OmeroObjectCell());
		
		comboOwner.setMaxWidth(Double.MAX_VALUE);
		owners.add(Owner.getAllMembersOwner());
		comboOwner.getItems().setAll(owners);
		comboOwner.getSelectionModel().selectFirst();
		comboOwner.setConverter(new StringConverter<Owner>() {

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
		});
		
		comboOwner.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> refreshTree());
		
		description = new TableView<>();
		TableColumn<Integer, String> attributeCol = new TableColumn<>("Attribute");
		TableColumn<Integer, String> valueCol = new TableColumn<>("Value");
		
		projectAttributes = new String[] {"Name", 
				"Id",
				"Description",
				"Owner",
				"Num. datasets"};
		
		datasetAttributes = new String[] {"Name", 
				"Id", 
				"Description",
				"Owner",
				"Num. images"};
		
		imageAttributes = new String[] {"Name", 
				"Id", 
				"Owner",
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

		
		attributeCol.setCellValueFactory(cellData -> {
			var type = tree.getSelectionModel().getSelectedItem().getValue().getType().toLowerCase();
			if (type.endsWith("#project"))
				return new ReadOnlyObjectWrapper<String>(projectAttributes[cellData.getValue()]);
			else if (type.endsWith("#dataset"))
				return new ReadOnlyObjectWrapper<String>(datasetAttributes[cellData.getValue()]);
			else if (type.endsWith("#image"))
				return new ReadOnlyObjectWrapper<String>(imageAttributes[cellData.getValue()]);
			return new ReadOnlyObjectWrapper<String>("");
			
		});
		
		valueCol.setCellValueFactory(cellData -> {
			if (selectedObject != null) 
				return getObjectInfo(cellData.getValue(), selectedObject);
			else return null;
		});
		
		// Get thumbnails in separate thread
		ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

        
		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			clearCanvas();
			if (n != null) {
				selectedObject = n.getValue();
				updateDescription();
				if (selectedObject instanceof Image) {
					// Change text on "Open .." button
					openBtn.setText("Open image");
					
					// Check if thumbnail was previously cached
					if (thumbnailBank.containsKey(selectedObject))
						setThumbnail(thumbnailBank.get(selectedObject));
					else {
						// Get thumbnail from JSON API in separate thread (and show progress indicator)
						progressIndicator.setOpacity(100);
						executor.submit(() -> {
							// TODO: fix these synchronized blocks, they don't work. If flicking through images quickly, mismatches happen
							synchronized(selectedObject) {
								synchronized(thumbnailBank) {
									try {
										BufferedImage img = getThumbnail(selectedObject.getId());
										
										thumbnailBank.put(selectedObject, img);
										Platform.runLater(() -> {
											setThumbnail(img);
											progressIndicator.setOpacity(0);
										});		
									} catch (IOException e) {
										logger.warn("Error loading thumbnail: " + e.getLocalizedMessage(), e);
									}
								}
							}
						});
					}
				} else {
					// To avoid empty space at the top
					canvas.setWidth(0);
					canvas.setHeight(0);
					
					// Change text on "Open .." button
					if (selectedObject instanceof Dataset)
						openBtn.setText("Open dataset");
					else if (selectedObject instanceof Project)
						openBtn.setText("Open project");
				}
			}
		});
		
		Button expandBtn = new Button("Expand all");
		Button collapseBtn = new Button("Collapse all");
		expandBtn.setMaxWidth(Double.MAX_VALUE);
		collapseBtn.setMaxWidth(Double.MAX_VALUE);

		expandBtn.setOnMouseClicked(e -> expandTreeView(tree.getRoot()));
		collapseBtn.setOnMouseClicked(e -> collapseTreeView(tree.getRoot()));
		GridPane expansionPane = new GridPane();
		PaneTools.addGridRow(expansionPane, 0, 0, "Expand/collapse items", expandBtn, collapseBtn);
		expansionPane.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(expandBtn, Priority.ALWAYS);
		GridPane.setHgrow(collapseBtn, Priority.ALWAYS);
		
		filter.setPromptText("Search image");
		filter.textProperty().addListener((v, o, n) -> {
			refreshTree();
			expandTreeView(tree.getRoot());
		});
		
		PaneTools.addGridRow(browseLeftPane, 0, 0, "Filter by owner", comboOwner);
		PaneTools.addGridRow(browseLeftPane, 1, 0, null, tree);
		PaneTools.addGridRow(browseLeftPane, 2, 0, null, expansionPane);
		PaneTools.addGridRow(browseLeftPane, 3, 0, null, filter);
		
		canvas = new Canvas();
		description.getColumns().addAll(attributeCol, valueCol);
		description.setMaxHeight(Double.MAX_VALUE);
		GridPane.setVgrow(description, Priority.ALWAYS);
		
		Button clipboardBtn = new Button("Copy URI to clipboard");
		openBtn = new Button("Open image");
		
		clipboardBtn.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
		openBtn.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
		
		
		GridPane actionBtnPane = new GridPane();
		PaneTools.addGridRow(actionBtnPane, 0, 0, null, clipboardBtn, openBtn);
		clipboardBtn.setMaxWidth(Double.MAX_VALUE);
		openBtn.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(clipboardBtn, Priority.ALWAYS);
		GridPane.setHgrow(openBtn, Priority.ALWAYS);
		
		clipboardBtn.setOnMouseClicked(e -> {
			var selected = tree.getSelectionModel().getSelectedItem();
			if (selected != null) {
				try {
					ClipboardContent content = new ClipboardContent();
					content.putString(getURI(selected.getValue()));
					Clipboard.getSystemClipboard().setContent(content);
				} catch (URISyntaxException ex) {
					logger.error("Could not copy to clipboard:", ex.getLocalizedMessage());
				}
				
			}
		});
		
		openBtn.setOnMouseClicked(e -> {
			String uri;
			try {
				uri = getURI(tree.getSelectionModel().getSelectedItem().getValue());
				qupath.openImage(uri, false, false);
			} catch (URISyntaxException ex) {
				logger.error("Could not open.", ex.getLocalizedMessage());
			}
			
			
		});
		
		
		PaneTools.addGridRow(browseRightPane, 0, 0, null, canvas);
		PaneTools.addGridRow(browseRightPane, 1, 0, null, description);
		PaneTools.addGridRow(browseRightPane, 2, 0, null, actionBtnPane);
		
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabPane.getTabs().add(new Tab("Browse", browsePane));
		tabPane.getTabs().add(new Tab("Client", clientPane));
		tabPane.getTabs().add(new Tab("Image servers", serverPane));

        // Anchor the tabs and progressIndicator
        AnchorPane anchor = new AnchorPane();
        anchor.getChildren().addAll(tabPane, progressIndicator);
        AnchorPane.setTopAnchor(progressIndicator, 3.0);
        AnchorPane.setRightAnchor(progressIndicator, 5.0);
        AnchorPane.setTopAnchor(tabPane, 1.0);
        AnchorPane.setRightAnchor(tabPane, 1.0);
        AnchorPane.setLeftAnchor(tabPane, 1.0);
        AnchorPane.setBottomAnchor(tabPane, 1.0);
		
		mainPane.setCenter(anchor);
		browsePane.setLeft(browseLeftPane);
		browsePane.setRight(browseRightPane);
		
		clientPane.addRow(0, getClientPane());
		serverPane.addRow(0, getServerPane());
    }

    
    private GridPane getClientPane() {
		GridPane mainBox = new GridPane();
		mainBox.setVgap(10.0);
		
		var rowIndex = 0;
		for (var client: OmeroWebClients.getAllClients().values()) {
			GridPane gridPane = new GridPane();
			GridPane infoPane = new GridPane();
			GridPane actionPane = new GridPane();
			
			String username = client.getUsername();
			List<OmeroWebImageServer> imageServers = client.getImageServers();
			int nServer = imageServers.size();
			Label userLabel = username.isEmpty() ? new Label("-") : new Label(username);
			PaneTools.addGridRow(infoPane, 0, 0, null, userLabel);
			
			GridPane imageServerPane = new GridPane();
			int imageServerRow = 0;
			for (var server: imageServers) {
				imageServerPane.addRow(imageServerRow++, new Label(server.getURIs() + ""));
			}
			
			TitledPane imageServersTitledPane = new TitledPane(nServer + " server(s)", imageServerPane);
			imageServersTitledPane.setMaxWidth(Double.MAX_VALUE);
			imageServersTitledPane.setExpanded(false);
			Platform.runLater(() -> {
				try {
					imageServersTitledPane.lookup(".title").setStyle("-fx-background-color: transparent");
					imageServersTitledPane.lookup(".title").setEffect(null);
					imageServersTitledPane.lookup(".content").setStyle("-fx-border-color: null");
				} catch (Exception e) {
					logger.error("Error setting Advanced options pane style", e);
				}
			});
			
			
			PaneTools.addGridRow(infoPane, 1, 0, null, imageServersTitledPane);
			
			Node state;
			state = client.loggedIn() ? active : inactive;
			
			Button refreshBtn = new Button("Refresh");
			Button disconnectBtn = new Button("Disconnect");
			PaneTools.addGridRow(actionPane, 0, 0, null, state, refreshBtn, disconnectBtn);
			
			refreshBtn.setOnAction(e -> {
				clientPane.getChildren().clear();
				clientPane.addRow(0, getClientPane());
			});
			
			
			PaneTools.addGridRow(gridPane, 0, 0, null, infoPane);
			PaneTools.addGridRow(gridPane, 0, 1, null, actionPane);
			
			GridPane.setHgrow(gridPane, Priority.ALWAYS);
			
			
			gridPane.setStyle("-fx-border-color: black;");
			mainBox.add(gridPane, 0, rowIndex++);
		}
		return mainBox;
    }

	private GridPane getServerPane() {
		GridPane mainBox = new GridPane();
		mainBox.addRow(0,  new Label("Todo"));
		return mainBox;
	}


	private String getURI(OmeroObject omeroObj) throws URISyntaxException {
		var apiUrl = new URI(omeroObj.getAPIURLString());
		var type = "project";
		if (omeroObj instanceof Dataset)
			type = "dataset";
		else if (omeroObj instanceof Image)
			type = "image";
		
		return apiUrl.getScheme() + "://" + apiUrl.getHost() + "/webclient/?show=" + type + "-" + omeroObj.getId();
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
		String name = selectedObject.getName();
		String id = selectedObject.getId() + "";
		String owner = selectedObject.getOwner().getName();
		if (selectedObject.getType().toLowerCase().endsWith("#project")) {
			String description = ((Project)selectedObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = selectedObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, nChildren};
			
		} else if (selectedObject.getType().toLowerCase().endsWith("#dataset")) {
			String description = ((Dataset)selectedObject).getDescription();
			if (description == null || description.isEmpty())
				description = "-";
			String nChildren = selectedObject.getNChildren() + "";
			outString = new String[] {name, id, description, owner, nChildren};

		} else if (selectedObject.getType().toLowerCase().endsWith("#image")) {
			Image obj = (Image)selectedObject;
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
			outString = new String[] {name, id, owner, acquisitionDate, width, height, c, z, t, pixelSizeX, pixelSizeY, pixelSizeZ, pixelType};
		}
		
		return new ReadOnlyObjectWrapper<String>(outString[index]);
	}


	private void updateDescription() {
		ObservableList<Integer> indexList = FXCollections.observableArrayList();
		if (selectedObject.getType().toLowerCase().endsWith("#project")) {
			projectIndices = new Integer[projectAttributes.length];
			for (int index = 0; index < projectAttributes.length; index++) projectIndices[index] = index;
			indexList = FXCollections.observableArrayList(projectIndices);
			
		} else if (selectedObject.getType().toLowerCase().endsWith("#dataset")) {
			datasetIndices = new Integer[datasetAttributes.length];
			for (int index = 0; index < datasetAttributes.length; index++) datasetIndices[index] = index;
			indexList = FXCollections.observableArrayList(datasetIndices);
			
		} else if (selectedObject.getType().toLowerCase().endsWith("#image")) {
			imageIndices = new Integer[imageAttributes.length];
			for (int index = 0; index < imageAttributes.length; index++) imageIndices[index] = index;
			indexList = FXCollections.observableArrayList(imageIndices);
			
		}
		description.getItems().setAll(indexList);
	}


	/**
	 * Display an OMERO object using its name.
	 */
	private class OmeroObjectCell extends TreeCell<OmeroObject> {
		
		@Override
        public void updateItem(OmeroObject item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
            	String name;
            	if (item.getType().toLowerCase().endsWith("server"))
            		name = server.getHost();
            	else if (item.getType().toLowerCase().endsWith("project"))
            		name = item.getName() + " (" + item.getNChildren() + ")";
            	else if (item.getType().toLowerCase().endsWith("dataset"))
                	name = item.getName() + " (" + item.getNChildren() + ")";
            	else
            		name = item.getName();

                setText(name);
                setGraphic(null);
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
					List<OmeroObject> children;
					var omeroObj = this.getValue();
					if (omeroObj instanceof Project) {
						// Check if we already have the Datasets for this Project (avoid sending request)
						if (projectMap.containsKey((Project)omeroObj)) {
							List<OmeroObjectTreeItem> temp = projectMap.get((Project)omeroObj).stream()
									.map(e -> new OmeroObjectTreeItem(e))
									.collect(Collectors.toList());
							super.getChildren().setAll(temp);
							computed = true;
							return super.getChildren();
						}
					}
					else if (omeroObj instanceof Dataset) {
						
						// Check if we already have the Images for this Dataset (avoid sending request)
						if (datasetMap.containsKey((Dataset)omeroObj)) {
							var temp = datasetMap.get((Dataset)omeroObj).stream()
									.map(e -> new OmeroObjectTreeItem(e))
									.collect(Collectors.toList());
							super.getChildren().setAll(temp);
							computed = true;
							return super.getChildren();
						}
					} else if (omeroObj instanceof Image)
						return FXCollections.observableArrayList(new ArrayList<TreeItem<OmeroObject>>());
					
					
					try {
						children = OmeroTools.getOmeroObjects(server, omeroObj);
						
						// If Server, get all owners to populate comboOwner
						if (omeroObj instanceof Server) {
							var temp = children.stream().map(e -> e.getOwner()).filter(distinctByName(Owner::getName)).collect(Collectors.toList());
							if (temp.size() > owners.size()) {
								owners.clear();
								owners.add(Owner.getAllMembersOwner());
								owners.addAll(temp);
								Platform.runLater(() -> {
									comboOwner.getItems().setAll(owners);
//								comboOwner.getSelectionModel().selectFirst();
								});								
							}
						} else if (omeroObj instanceof Project) {
							projectMap.put(omeroObj, children);
						} else if (omeroObj instanceof Dataset) {
							datasetMap.put(omeroObj, children);
						}
					} catch (IOException e) {
						logger.error("Couldn't fetch server information", e.getLocalizedMessage());
						return null;
					}
					
					var items = children.stream()
							.filter(e -> {
								var selected = comboOwner.getSelectionModel().getSelectedItem();
								if (selected != null) {
									if (selected.getName().equals("All members "))
										return true;
									return e.getOwner().getName().equals(comboOwner.getSelectionModel().getSelectedItem().getName());
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
			if (obj.getType().toLowerCase().endsWith("#server"))
				return false;
			if (obj.getType().toLowerCase().endsWith("#image"))
				return true;
			if (obj.getNChildren() == 0)
				return true;
			return false;
		}
		
		private boolean matchesSearch(OmeroObject obj, String filter) {
			if (filter == null || filter.isEmpty())
				return true;
			while (!(obj instanceof Image)) {
				var children = new OmeroObjectTreeItem(obj).getChildren();
//				for (var d: children) {
//					var match =  matchesSearch(d.getValue(), filter.toLowerCase());	
//					if (match)
//						return true;
//				}
				if (children.size() > 0)
					return true;
				return false;
			}
			
			if (obj.getName().toLowerCase().contains(filter))
				return true;
			return false;
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
	
	BufferedImage getThumbnail(int id) throws IOException {
		URL url;
		try {
			url = new URL(server.getScheme(), server.getHost(), "/webgateway/render_thumbnail/" + id + "/" + imgPrefSize);
		} catch (MalformedURLException e) {
			logger.warn(e.getLocalizedMessage());
			return null;
		}
		
		return ImageIO.read(url);
	}
	
	void setThumbnail(BufferedImage img) {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

		var wi =  SwingFXUtils.toFXImage(img, null);
		if (wi == null)
			return;
		else
			GuiTools.paintImage(canvas, wi);
		
		canvas.setWidth(imgPrefSize);
		canvas.setHeight(imgPrefSize);
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
	    	if (!(item.getValue() instanceof Server))
	    		item.setExpanded(true);
	    	
    		for (var child: item.getChildren()) {
    			expandTreeView(child);
	        }
	    }
	}
	
	private void collapseTreeView(TreeItem<OmeroObject> item){
	    if (item != null && !item.isLeaf()) {
	    	if (item.getValue() instanceof Server) {
	    		for (var child: item.getChildren()) {
	    			child.setExpanded(false);
	    		}
	    	}
	    }
	}

	public BorderPane getPane() {
		return this.mainPane;
	}

}
