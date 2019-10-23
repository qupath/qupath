package qupath.lib.gui.commands;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.projects.Project;

public class ProjectCheckUrisCommand implements PathCommand {
	
	private static int maxRecursiveSearchDepth = 8;
	
	private QuPathGUI qupath;
	
	public ProjectCheckUrisCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage("Check URIs", "No project open!");
			return;
		}
		try {
			// Show URI manager dialog if we have any missing URIs
			if (!ProjectCheckUrisCommand.checkURIs(project, false))
				return;
		} catch (IOException e) {
			DisplayHelpers.showErrorMessage("Update URIs", e);
		}
	}
	
	
	public static boolean checkURIs(Project<?> project, boolean onlyIfMissing) throws IOException {
		var manager = new ProjectUriManager(project);
		if (!onlyIfMissing || manager.countOriginalItems(UriStatus.MISSING) > 0) {
			return manager.showDialog();
		}
		return true;
	}
	
	
	static class ProjectUriManager {
		
		private Logger logger = LoggerFactory.getLogger(ProjectUriManager.class);
		
		private Project<?> project;
		
		private ObservableMap<UriItem, UriItem> replacements = FXCollections.observableMap(new HashMap<>());
		
		private GridPane pane = new GridPane();
		private TableView<UriItem> table = new TableView<>();
		private ObservableList<UriItem> allItems = FXCollections.observableArrayList();
		
		private BooleanProperty showMissing = new SimpleBooleanProperty(true);
		private BooleanProperty showValid = new SimpleBooleanProperty(true);
		private BooleanProperty showUnknown = new SimpleBooleanProperty(true);
		
		ProjectUriManager(Project<?> project) throws IOException {
			this.project = project;
			
			URI uriProject = project.getURI();
			URI uriProjectPrevious = project.getPreviousURI();
			
			// Get all the URIs
			Set<URI> imageUris = new LinkedHashSet<>();
			for (var entry : project.getImageList()) {
				imageUris.addAll(entry.getServerURIs());
			}
			
			// Get paths, if we can
			Path pathProject = uriProject == null ? null : GeneralTools.toPath(uriProject);
			Path pathPrevious = uriProjectPrevious == null ? null : GeneralTools.toPath(uriProjectPrevious);
			// We care about the directory rather than the actual file
			if (pathProject != null && !Files.isDirectory(pathProject)) {
				pathProject = pathProject.getParent();
			}
			if (pathPrevious != null && !Files.isDirectory(pathPrevious)) {
				pathPrevious = pathPrevious.getParent();
			}
			boolean tryRelative = pathProject != null && pathPrevious != null && !pathProject.equals(pathPrevious);
			
			// Map the URIs to a list of potential replacements
			List<UriItem> list = new ArrayList<>();
			int nMissing = 0;
			int nExists = 0;
			int nUnknown = 0;
			for (var temp : imageUris) {
				var item = new UriItem(temp);
				switch (item.getStatus()) {
				case MISSING:
					// Try to relativize the path to predict a likely replacement - if we have the same root
					Path pathItem = item.getPath();
					if (pathItem != null && Objects.equals(pathItem.getRoot(), pathProject.getRoot())) {
						if (tryRelative) {
							Path pathRelative = pathProject.resolve(pathPrevious.relativize(item.getPath()));
							if (Files.exists(pathRelative)) {
								URI uri2 = pathRelative.normalize().toUri().normalize();
								replacements.put(item, new UriItem(uri2));
							}
						}
					}
					nMissing++;
					break;
				case EXISTS:
					nExists++;
					break;
				case UNKNOWN:
				default:
					nUnknown++;
					break;
				}
				list.add(item);
			}
			allItems.setAll(list);
			
//			// We can return if we don't have any missing or unknown (or if we don't care about unknown)
//			if (nMissing == 0)
//				return;
			
			// Create a table view
			TableColumn<UriItem, UriItem> colOriginal = new TableColumn<>("Original URI");
			colOriginal.setCellValueFactory(item -> Bindings.createObjectBinding(() -> item.getValue()));
			colOriginal.setCellFactory(col -> new UriCell());
			table.getColumns().add(colOriginal);
			
			TableColumn<UriItem, UriItem> colReplacement = new TableColumn<>("Replacement URI");
			colReplacement.setCellValueFactory(item -> {
				return Bindings.createObjectBinding(() -> replacements.get(item.getValue()), replacements);
			});
			colReplacement.setCellFactory(col -> new UriCell());
			table.getColumns().add(colReplacement);
			
			FilteredList<UriItem> filteredList = allItems.filtered(new TableFilter());
			table.setItems(filteredList);
			
			showMissing.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			showUnknown.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			showValid.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			
			table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
			
			table.setPrefSize(600, 400);
			
			CheckBox cbMissing = new CheckBox(String.format("Show missing (%d)", nMissing));
			cbMissing.selectedProperty().bindBidirectional(showMissing);
			CheckBox cbValid = new CheckBox(String.format("Show valid (%d)", nExists));
			cbValid.selectedProperty().bindBidirectional(showValid);
			CheckBox cbUnknown = new CheckBox(String.format("Show unknown (%d)", nUnknown));
			cbUnknown.selectedProperty().bindBidirectional(showUnknown);

			Label labelReplacements = new Label();
			labelReplacements.textProperty().bind(Bindings.createStringBinding(() ->
				"Number of replacements: " + replacements.size()
				, replacements));
			
			Button btnSearch = new Button("Search...");
			btnSearch.setTooltip(new Tooltip("Choose a directory & search recursively for images inside"));
			btnSearch.setOnAction(e -> {
				var dir = QuPathGUI.getSharedDialogHelper().promptForDirectory(null);
				Map<String, List<UriItem>> missing = allItems.stream().filter(p -> p.getStatus() == UriStatus.MISSING && p.getPath() != null && replacements.get(p) == null)
						.collect(Collectors.groupingBy(p -> p.getPath().getFileName().toString()));
				
				searchDirectoriesRecursive(dir, missing, maxRecursiveSearchDepth);
			});
			
			int row = 0;
			PaneToolsFX.addGridRow(pane, row++, 0, null, table, table, table);
			PaneToolsFX.addGridRow(pane, row++, 0, null, labelReplacements, labelReplacements, btnSearch);
			PaneToolsFX.addGridRow(pane, row, 0, null, cbValid);
			PaneToolsFX.addGridRow(pane, row, 1, null, cbMissing);
			PaneToolsFX.addGridRow(pane, row++, 2, null, cbUnknown);
			
			PaneToolsFX.setFillWidth(Boolean.TRUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			PaneToolsFX.setHGrowPriority(Priority.ALWAYS, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			PaneToolsFX.setMaxWidth(Double.MAX_VALUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			GridPane.setHalignment(btnSearch, HPos.RIGHT);
			
			pane.setHgap(5);
			pane.setVgap(5);
			
			table.addEventHandler(KeyEvent.KEY_PRESSED, new TableCopyPasteHandler());
			table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		}
		
		
		void searchDirectoriesRecursive(File dir, Map<String, List<UriItem>> missing, int maxDepth) {
			if (dir == null || !dir.canRead() || !dir.isDirectory() || missing.isEmpty() || maxDepth <= 0)
				return;
			
			List<File> subdirs = new ArrayList<>();
			
			logger.debug("Searching {}", dir);
			var list = dir.listFiles();
			if (list == null)
				return;
			for (File f : list) {
				if (f == null)
					continue;
				if (f.isHidden())
					continue;
				else if (f.isFile()) {
					// If we find something with the correct name, update the URIs
					String name = f.getName();
					List<UriItem> myList = missing.remove(name);
					if (myList != null) {
						for (var item : myList)
							replacements.put(item, new UriItem(f.toURI()));
					}
					// Check if we are done
					if (missing.isEmpty())
						return;
				} else if (f.isDirectory()) {
					subdirs.add(f);
				}
			}
			for (File subdir : subdirs) {
				searchDirectoriesRecursive(subdir, missing, maxDepth-1);
			}
		}
		
		
		int countOriginalItems(UriStatus status) {
			int n = 0;
			for (var item : allItems) {
				if (item.getStatus() == status)
					n++;
			}
			return n;
		}

		int countReplacedItems(UriStatus status) {
			int n = 0;
			for (var item : allItems) {
				var item2 = replacements.getOrDefault(item, item);
				if (item2.getStatus() == status)
					n++;
			}
			return n;			
		}

		class TableFilter implements Predicate<UriItem> {

			@Override
			public boolean test(UriItem item) {
				switch (item.getStatus()) {
				case EXISTS:
					return showValid.get();
				case MISSING:
					return showMissing.get();
				case UNKNOWN:
				default:
					return showUnknown.get();
				}
			}
			
		}
		
		
		class TableCopyPasteHandler implements EventHandler<KeyEvent> {

			private KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
			private KeyCombination pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
			
			@Override
			public void handle(KeyEvent event) {
				if (copyCombo.match(event))
					handleCopy();
				else if (pasteCombo.match(event))
					handlePaste();
			}
			
		}
		
		boolean handleCopy() {
			StringBuilder sb = new StringBuilder();
			for (var uriItem : table.getSelectionModel().getSelectedItems()) {
				var uri = uriItem.getURI();
				if (sb.length() > 0)
					sb.append(System.lineSeparator());
				sb.append(uri.toString());
				sb.append("\t");
				var uri2 = replacements.get(uriItem);
				if (uri2 != null)
					sb.append(uri2.getURI().toString());
			}
			if (sb.length() == 0)
				return false;
			var content = new ClipboardContent();
			content.putString(sb.toString());
			Clipboard.getSystemClipboard().setContent(content);
			return true;
		}
		
		boolean handlePaste() {
			String s = Clipboard.getSystemClipboard().getString();
			if (s == null)
				return false;
			
			try (var scanner = new Scanner(s)) {
				while (scanner.hasNextLine()) {
					var line = scanner.nextLine();
					var split = line.split("\t");
					if (split.length > 1) {
						try {
							var uri1 = GeneralTools.toURI(split[0]);
							var uri2 = GeneralTools.toURI(split[1]);
							if (uri1 != null && uri2 != null) {
								var item1 = new UriItem(uri1);
								var item2 = new UriItem(uri2);
								if (table.getItems().contains(item1)) {
									if (item1.equals(item2))
										replacements.remove(item1);
									else
										replacements.put(item1, item2);
								} else
									logger.warn("Unable to find URI {} - will be skipped", item1);
							}
						} catch (Exception e) {
							logger.warn("Unable to parse URIs from {} ({})", line, e.getLocalizedMessage());
						}
					}
				}
			}
			return true;
			
		}
		
		
		int applyReplacements() throws IOException {
			Map<URI, URI> map = replacements.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getURI(), e -> e.getValue().getURI()));
			int count = 0;
			for (var entry : project.getImageList()) {
				if (entry.updateServerURIs(map))
					count++;
			}
			return count;
		}
		
		/**
		 * Returns true if the dialog was not cancelled (regardless of whether changes were made).
		 * @return
		 */
		boolean showDialog() {
			
			Dialog<ButtonType> dialog = new Dialog<>();
			dialog.setHeaderText("Images may have been deleted or moved!\nFix broken paths here by double-clicking on red entries and/or accepting QuPath's suggestions.");
			dialog.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
			((Button)dialog.getDialogPane().lookupButton(ButtonType.YES)).setText("Apply changes");
			((Button)dialog.getDialogPane().lookupButton(ButtonType.NO)).setText("Ignore");
			dialog.getDialogPane().setContent(pane);
			dialog.setTitle("Update URIs");
			dialog.setResizable(true);
			var btn = dialog.showAndWait().orElseGet(() -> ButtonType.CANCEL);
			if (btn.equals(ButtonType.CANCEL))
				return false;
			
			if (btn.equals(ButtonType.NO))
				return true;
			
//			if (DisplayHelpers.showConfirmDialog("Update URIs", pane)) {
				try {
					int n = applyReplacements();
					if (n <= 0) {
						DisplayHelpers.showInfoNotification("Update URIs", "No URIs updated!");
						return true;
					}
					if (n == 1) 
						DisplayHelpers.showInfoNotification("Update URIs", "URIs updated for 1 image");
					else if (n > 1)
						DisplayHelpers.showInfoNotification("Update URIs", "URIs updated for " + n + " images");
					project.syncChanges();
				} catch (IOException e) {
					DisplayHelpers.showErrorMessage("Update URIs", e);
				}
//			}
			return true;
		}
		
		
		
		class UriCell extends TableCell<UriItem, UriItem> implements EventHandler<MouseEvent> {
			
			private Tooltip tooltip = new Tooltip();

			UriCell() {
				super();
				setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
				addEventHandler(MouseEvent.MOUSE_CLICKED, this);
			}

			@Override
			protected void updateItem(UriItem item, boolean empty) {
				super.updateItem(item, empty);
				if (item == null || empty) {
					setTooltip(null);
					setText("");
					return;
				}
				setText(item.toString());
				tooltip.setText(item.toString());
				setTooltip(tooltip);
				
				switch (item.getStatus()) {
				case EXISTS:
					setStyle(null);
					break;
				case MISSING:
					setStyle("-fx-text-fill: red");
					break;
				case UNKNOWN:
				default:
					setStyle("-fx-text-fill: gray");
				}
			}

			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() != 2)
					return;
				var row = getTableRow();
				var uriOriginal = row.getItem();
				if (uriOriginal == null)
					return;
				var uriReplacement = replacements.get(uriOriginal);
				var defaultPath = uriReplacement == null ? uriOriginal.getURI().toString() : uriReplacement.getURI().toString();
				String path = QuPathGUI.getSharedDialogHelper().promptForFilePathOrURL("Change URI", defaultPath, null, null);
				if (path != null && !path.isBlank()) {
					URI uri = null;
					try {
						uri = GeneralTools.toURI(path);
					} catch (URISyntaxException e) {
						logger.error("Error parsing URI", e);
					}
					if (uri == null) {
						DisplayHelpers.showErrorMessage("Change URI", "Unable to parse URI from " + path);
					} else
						replacements.put(uriOriginal, new UriItem(uri));
				} else
					replacements.remove(uriOriginal);
			}

		}
		
		
	}
	
	
	static enum UriStatus { 
		EXISTS, MISSING, UNKNOWN;
	}
	
	static class UriItem {
		
				private URI uri;
		private Path path;
		
		UriItem(URI uri) {
			this.uri = uri;
			this.path = GeneralTools.toPath(uri);
		}
		
		UriStatus getStatus() {
			if (path == null)
				return UriStatus.UNKNOWN;
			if (Files.exists(path))
				return UriStatus.EXISTS;
			return UriStatus.MISSING;
		}
		
		URI getURI() {
			return uri;
		}
		
		Path getPath() {
			return path;
		}
		
		@Override
		public String toString() {
			if (path == null)
				return uri.toString();
			return path.toString();
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UriItem other = (UriItem) obj;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			return true;
		}
		
	}

}
