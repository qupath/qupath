package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.omero.OmeroObjects.Dataset;
import qupath.lib.images.servers.omero.OmeroObjects.Image;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.images.servers.omero.OmeroObjects.Project;
import qupath.lib.images.servers.omero.OmeroObjects.Server;

public class OmeroWebImageServerBrowser {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebImageServerBrowser.class);
	
	BorderPane mainPane;
	OmeroWebImageServer server;
	ComboBox<Owner> comboOwner = new ComboBox<>();
	List<Owner> owners = new ArrayList<>();
	TreeView<OmeroObject> tree;
	TableView<Integer> description;
	OmeroObject selectedObject;
	TextField filter = new TextField();
	Canvas canvas;
	int imgPrefSize = 256;
	
	Map<OmeroObject, BufferedImage> thumbnailBank = new HashMap<OmeroObject, BufferedImage>();	// To store thumbnails
	
	
	Map<OmeroObject, List<OmeroObject>> projectMap = new HashMap<>();
	Map<OmeroObject, List<OmeroObject>> datasetMap = new HashMap<>();
	
	String[] projectAttributes;
	String[] datasetAttributes;
	String[] imageAttributes;
	
	Integer[] imageIndices;
	Integer[] datasetIndices;
	Integer[] projectIndices;
	
	
    OmeroWebImageServerBrowser(OmeroWebImageServer server) {
    	this.server = server;

		mainPane = new BorderPane();
		selectedObject = null;
		
		GridPane topPane = new GridPane();
		GridPane leftPane = new GridPane();
		GridPane rightPane = new GridPane();
		
		tree = new TreeView<>();
		OmeroObjectTreeItem root = new OmeroObjectTreeItem(new OmeroObjects.Server(server));
		tree.setRoot(root);
		tree.setShowRoot(false);
		tree.setCellFactory(n -> new OmeroObjectCell());
		
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
				"Description",
				"Owner",
				"Num. datasets"};
		
		datasetAttributes = new String[] {"Name", 
				"Description",
				"Owner",
				"Num. images"};
		
		imageAttributes = new String[] {"Name", 
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
				if (n.getValue() instanceof Image) {
					if (thumbnailBank.containsKey(selectedObject))
						setThumbnail(thumbnailBank.get(selectedObject));
					else {
						executor.submit(() -> {
							try {
								
								BufferedImage img = getThumbnail(n.getValue().getId());
								thumbnailBank.put(selectedObject, img);
								Platform.runLater( () -> setThumbnail(img));							
							} catch (IOException e) {
								logger.warn("Error loading thumbnail: " + e.getLocalizedMessage(), e);
							}
						});					
					}					
				} else {
					// To avoid empty space at the top
					canvas.setWidth(0);
					canvas.setHeight(0);
				}
			}
		});
		
		PaneTools.addGridRow(topPane, 0, 0, "Server", new Label(server.getHost()));
		PaneTools.addGridRow(topPane, 1, 0, "Filter by owner", comboOwner);
		
		
		filter.setPromptText("Filter");
		filter.textProperty().addListener((v, o, n) -> refreshTree());
		
		PaneTools.addGridRow(leftPane, 0, 0, null, tree);
		PaneTools.addGridRow(leftPane, 1, 0, null, filter);
		
		canvas = new Canvas();
		description.getColumns().addAll(attributeCol, valueCol);
		
		PaneTools.addGridRow(rightPane, 0, 0, null, canvas);
		PaneTools.addGridRow(rightPane, 1, 0, null, description);
		
		
		mainPane.setTop(topPane);
		mainPane.setLeft(leftPane);
		mainPane.setRight(rightPane);
		
    	
    }


	private void refreshTree() {
		tree.setRoot(null);
		tree.refresh();
		tree.setRoot(new OmeroObjectTreeItem(new OmeroObjects.Server(server)));
		tree.refresh();
	}


	private ObservableValue<String> getObjectInfo(Integer index, OmeroObject omeroObject) {
		String[] outString = null;
		String name = selectedObject.getName();
		String owner = selectedObject.getOwner().getName();
		if (selectedObject.getType().toLowerCase().endsWith("#project")) {
			String description = ((Project)selectedObject).getDescription();
			String nChildren = selectedObject.getNChildren() + "";
			outString = new String[] {name, description, owner, nChildren};
			
		} else if (selectedObject.getType().toLowerCase().endsWith("#dataset")) {
			String description = ((Dataset)selectedObject).getDescription();
			String nChildren = selectedObject.getNChildren() + "";
			outString = new String[] {name, description, owner, nChildren};

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
			outString = new String[] {name, owner, acquisitionDate, width, height, c, z, t, pixelSizeX, pixelSizeY, pixelSizeZ, pixelType};
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
		
		public OmeroObjectTreeItem(OmeroObject value) {
			super(value);
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
				List<OmeroObject> children;
				if (this.getValue() instanceof Project) {
					
					// Check if we already have the Datasets for this Project (avoid sending request)
					if (projectMap.containsKey((Project)this.getValue())) {
						var temp = projectMap.get((Project)this.getValue()).stream()
								.map(e -> new OmeroObjectTreeItem(e))
								.collect(Collectors.toList());
						super.getChildren().setAll(temp);
						computed = true;
						return super.getChildren();
					}
				}
				else if (this.getValue() instanceof Dataset) {
					
					// Check if we already have the Images for this Dataset (avoid sending request)
					if (datasetMap.containsKey((Dataset)this.getValue())) {
						var temp = datasetMap.get((Dataset)this.getValue()).stream()
								.map(e -> new OmeroObjectTreeItem(e))
								.collect(Collectors.toList());
						super.getChildren().setAll(temp);
						computed = true;
						return super.getChildren();
					}
				} else if (this.getValue() instanceof Image)
					return FXCollections.observableArrayList(new ArrayList<TreeItem<OmeroObject>>());
				
				
				try {
					children = OmeroTools.getOmeroObjects(server, this.getValue());
					
					// If Server, get all owner to populate comboOwner
					if (this.getValue() instanceof Server) {
						owners.add(Owner.getAllMembersOwner());
						owners.addAll(children.stream().map(e -> e.getOwner()).filter(distinctByName(Owner::getName)).collect(Collectors.toList()));
					} else if (this.getValue() instanceof Project) {
						projectMap.put(this.getValue(), children);
					} else if (this.getValue() instanceof Dataset) {
						datasetMap.put(this.getValue(), children);
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
						.filter(e -> e.getName().contains(filter.getText()))
						.map(e -> new OmeroObjectTreeItem(e))
						.collect(Collectors.toList());
				super.getChildren().setAll(items);
				computed = true;
			}
			return super.getChildren();
			
		}
		
		
		@Override
		public boolean isLeaf() {
			if (this.getValue().getType().toLowerCase().endsWith("#server"))
				return false;
			if (this.getValue().getType().toLowerCase().endsWith("#image"))
				return true;
			if (this.getValue().getNChildren() == 0)
				return true;
			return false;
		}
		
		
		/**
		 * See {@link "https://stackoverflow.com/questions/23699371/java-8-distinct-by-property"}
		 * @param <T>
		 * @param keyExtractor
		 * @return
		 */
		public <T> Predicate<T> distinctByName(Function<? super T, ?> keyExtractor) {
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
	
	
	
	public BorderPane getPane() {
		return this.mainPane;
	}

}
