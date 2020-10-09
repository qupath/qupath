package qupath.lib.images.servers.omero;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;

public class OmeroWebClientsCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebClientsCommand.class);

	
	private QuPathGUI qupath;
	private Button refreshBtn;
	
	OmeroWebClientsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	public void run() {
		BorderPane mainPane = new BorderPane();
		GridPane serverGrid = new GridPane();
		serverGrid.setVgap(10.0);
		
		var rowIndex = 0;
		var hostClientsMap = OmeroWebClients.getAllClients();
		for (var hostClientEntry: hostClientsMap.entrySet()) {
			if (hostClientEntry.getValue().isEmpty())
				continue;
			
			GridPane gridPane = new GridPane();
			GridPane infoPane = new GridPane();
			GridPane actionPane = new GridPane();
			
			GridPane imageServersPane = new GridPane();
			int imageServerRow = 0;
			var imageList = qupath.getProject().getImageList();
			String host = hostClientEntry.getKey();
			Optional<OmeroWebClient> clientsWithUsername = hostClientEntry.getValue().parallelStream().filter(e -> !e.getUsername().isEmpty()).findAny();
			Label userLabel = clientsWithUsername.isEmpty() ? new Label(host) : new Label(host + " (" + clientsWithUsername.get().getUsername() + ")");
			PaneTools.addGridRow(infoPane, 0, 0, null, userLabel);
			
			for (OmeroWebClient client: hostClientEntry.getValue()) {
				// Check if client's servers haven't been deleted from project
				if (imageList.parallelStream().anyMatch(e -> {
					try {
						return e.getServerURIs().iterator().next() == client.getURI();
					} catch (Exception ex) {
						logger.warn(ex.getLocalizedMessage());
					}
					return false;
				}))
					imageServersPane.addRow(imageServerRow++, new Label(client.getURI().toString()));
			}

			TitledPane imageServersTitledPane = new TitledPane(imageServerRow + " server(s)", imageServersPane);
			imageServersTitledPane.setMaxWidth(Double.MAX_VALUE);
			imageServersTitledPane.setExpanded(false);
			Platform.runLater(() -> {
				try {
					// These 2 next lines help prevent NPE
					imageServersTitledPane.applyCss();
					imageServersTitledPane.layout();
					imageServersTitledPane.lookup(".title").setStyle("-fx-background-color: transparent");
					imageServersTitledPane.lookup(".title").setEffect(null);
					imageServersTitledPane.lookup(".content").setStyle("-fx-border-color: null");
				} catch (Exception e) {
					logger.error("Error setting CSS style", e.getLocalizedMessage());
				}
			});

			PaneTools.addGridRow(infoPane, 1, 0, null, imageServersTitledPane);

			// Get first client in list that requires login, or the first one if all public
			OmeroWebClient clientToPing = clientsWithUsername.isEmpty() ? hostClientEntry.getValue().get(0) : clientsWithUsername.get();
			boolean loggedIn = clientToPing.loggedIn();
			
			Node state = createStateNode(clientToPing.loggedIn());
			Button connectionBtn = loggedIn ? new Button("Log out") : new Button("Log in");
			Button removeBtn = new Button("Remove");
			PaneTools.addGridRow(actionPane, 0, 0, null, state, connectionBtn, removeBtn);
			
			connectionBtn.setOnAction(e -> {
				if (connectionBtn.getText().equals("Log in")) {
					boolean success = true;
					
					// Check again the state, in case it wasn't refreshed in time
					if (!clientToPing.loggedIn())
						success = OmeroWebClients.logIn(clientToPing);
					
					// Change text on button if connection was successful
					if (success) {
						connectionBtn.setText("Log out");
						refreshBtn.fire();
					} else
						Dialogs.showErrorMessage("Connect to server", "Could not connect to server. Check the log for more info.");
				} else {
					// Check again the state, in case it wasn't refreshed in time
					if (clientToPing.loggedIn())
						OmeroWebClients.logOut(clientToPing);

					// Change text on button
					connectionBtn.setText("Log in");
					refreshBtn.fire();
				}
			});
			
			removeBtn.setOnMouseClicked(e -> {
				if (!clientsWithUsername.isEmpty() && qupath.getViewers().stream().anyMatch(viewer -> {
							if (viewer.getServer() == null)
								return false;
							return viewer.getServer().getURIs().iterator().next() == clientsWithUsername.get().getURI();
						})) {
					Dialogs.showMessageDialog("Remove server", "You need to close the image in the viewer first!");
					return;
				}
				if (!clientsWithUsername.isEmpty() && clientToPing.loggedIn())
					OmeroWebClients.logOut(clientToPing);
				OmeroWebClients.removeClient(clientToPing);
				refreshBtn.fire();
			});
			
			connectionBtn.setDisable(clientsWithUsername.isEmpty());
			if (!clientsWithUsername.isEmpty())
				removeBtn.disableProperty().bind(connectionBtn.textProperty().isEqualTo("Log out"));
			else
				removeBtn.setDisable(false);
			
			PaneTools.addGridRow(gridPane, 0, 0, null, infoPane);
			PaneTools.addGridRow(gridPane, 0, 1, null, actionPane);
			
			GridPane.setHgrow(gridPane, Priority.ALWAYS);
			GridPane.setHgrow(actionPane, Priority.ALWAYS);
			actionPane.setAlignment(Pos.CENTER_RIGHT);

			gridPane.setStyle("-fx-border-color: black;");
			serverGrid.add(gridPane, 0, rowIndex++);
		}
		
		if (serverGrid.getChildren().isEmpty())
			serverGrid.add(new Label("No OMERO server"), 0, 0);
			
		serverGrid.setMinWidth(250);
		mainPane.setTop(serverGrid);
		
		refreshBtn = new Button("Refresh");
		refreshBtn.setOnAction(e -> {
			logger.debug("SHOULD REFRESH NOW");
		});
		GridPane buttonPane = PaneTools.createColumnGridControls(refreshBtn);
		buttonPane.setHgap(10);
		buttonPane.setPadding(new Insets(5, 0, 5, 0));
		mainPane.setBottom(buttonPane);
				
		Stage dialog = new Stage();
		dialog.sizeToScene();
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.setTitle("OMERO web server");
		dialog.setScene(new Scene(mainPane));
		dialog.showAndWait();
	}
	
	
	private Node createStateNode(boolean loggedIn) {
		var state = loggedIn ? IconFactory.PathIcons.ACTIVE_SERVER : IconFactory.PathIcons.INACTIVE_SERVER;
		return IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, state);
	}

}
