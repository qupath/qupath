/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import qupath.lib.io.UriResource;
import qupath.lib.projects.Project;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;


/**
 * Fix broken URIs by using relative paths or prompting the user to select files.
 * This is intended to handle cases where files or projects have changed location, so that links need to be updated.
 * 
 * @author Pete Bankhead
 *
 */
public class UriUpdater {
	
	private static int maxRecursiveSearchDepth = 8;
	
	
	static int applyReplacements(Collection<? extends UriResource> uriResources, Map<SingleUriItem, SingleUriItem> replacements) throws IOException {
		Map<URI, URI> map = replacements.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getURI(), e -> e.getValue().getURI()));
		int count = 0;
		for (var entry : uriResources) {
			if (entry.updateURIs(map))
				count++;
		}
		return count;
	}
	
	/**
	 * Show dialog prompting the user to update URIs for missing files.
	 * Optionally provide previous and current base URIs. If not null, these will be used to relativize paths when searching for potential replacements URIs.
	 * Usually, these correspond to the current and previous paths for a {@link Project}.
	 * 
	 * @param <T>
	 * @param items the items containing URIs to check
	 * @param basePrevious optional previous base path
	 * @param baseCurrent optional current base path
	 * @param onlyPromptIfMissing only show a dialog if any URIs correspond to missing files
	 * @return the number of changes made, or -1 if the user cancelled the dialog.
	 * @throws IOException if there was a problem accessing the URIs
	 */
	public static <T extends UriResource> int promptToUpdateUris(Collection<T> items, URI basePrevious, URI baseCurrent, boolean onlyPromptIfMissing) throws IOException {
		
		var singleUriItems = getItems(items);
		
		if (onlyPromptIfMissing && singleUriItems.stream().allMatch(u -> u.getStatus() != UriStatus.MISSING))
			return 0;
		
		var manager = new UriManager(singleUriItems, basePrevious, baseCurrent);
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setHeaderText("Images may have been deleted or moved!\nFix broken paths here by double-clicking on red entries and/or accepting QuPath's suggestions.");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
		((Button)dialog.getDialogPane().lookupButton(ButtonType.YES)).setText("Apply changes");
		((Button)dialog.getDialogPane().lookupButton(ButtonType.NO)).setText("Ignore");
		dialog.getDialogPane().setContent(manager.pane);
		dialog.setTitle("Update URIs");
		dialog.setResizable(true);
		var btn = dialog.showAndWait().orElseGet(() -> ButtonType.CANCEL);
		if (btn.equals(ButtonType.CANCEL))
			return -1;
		
		if (btn.equals(ButtonType.NO))
			return 0;
		
		int n = 0;
		try {
			n = applyReplacements(items, manager.replacements);
			if (n <= 0) {
				Dialogs.showInfoNotification("Update URIs", "No URIs updated!");
			} else if (n == 1) 
				Dialogs.showInfoNotification("Update URIs", "1 URI updated");
			else if (n > 1)
				Dialogs.showInfoNotification("Update URIs", n + " URIs updated");
		} catch (IOException e) {
			Dialogs.showErrorMessage("Update URIs", e);
		}
		return n;
	}
	
	private static List<SingleUriItem> getItems(Collection<? extends UriResource> uriResources) throws IOException {
		// Get all the URIs
		Set<URI> imageUris = new LinkedHashSet<>();
		for (var item : uriResources) {
			imageUris.addAll(item.getURIs());
		}
		return imageUris.stream().map(u -> new SingleUriItem(u)).toList();
	}

	
	
	private static class UriManager {
		
		private static Logger logger = LoggerFactory.getLogger(UriManager.class);
		
		private ObservableMap<SingleUriItem, SingleUriItem> replacements = FXCollections.observableMap(new HashMap<>());
		
		private GridPane pane = new GridPane();
		private TableView<SingleUriItem> table = new TableView<>();
		private ObservableList<SingleUriItem> allItems = FXCollections.observableArrayList();
		
		private BooleanProperty showMissing = new SimpleBooleanProperty(true);
		private BooleanProperty showValid = new SimpleBooleanProperty(true);
		private BooleanProperty showUnknown = new SimpleBooleanProperty(true);
		
		private UriManager(Collection<SingleUriItem> items, URI basePrevious, URI baseCurrent) throws IOException {
			var replacements = findReplacements(items, basePrevious, baseCurrent);
			this.allItems.setAll(items);
			this.replacements.putAll(replacements);
			initialize();
		}
		
		
		private static Map<SingleUriItem, SingleUriItem> findReplacements(Collection<SingleUriItem> items, URI previousBaseUri, URI baseUri) {

			Map<SingleUriItem, SingleUriItem> replacements = new LinkedHashMap<>();

			// Get paths, if we can
			Path pathBase = baseUri == null ? null : GeneralTools.toPath(baseUri);
			Path pathPrevious = previousBaseUri == null ? null : GeneralTools.toPath(previousBaseUri);
			// We care about the directory rather than the actual file
			if (pathBase != null && !Files.isDirectory(pathBase)) {
				pathBase = pathBase.getParent();
			}
			if (pathPrevious != null && !Files.isDirectory(pathPrevious)) {
				pathPrevious = pathPrevious.getParent();
			}
			boolean tryRelative = pathBase != null && pathPrevious != null && !pathBase.equals(pathPrevious);

			// Map the URIs to a list of potential replacements
			for (var item : items) {
				if (item.getStatus() == UriStatus.MISSING) {
					Path pathItem = item.getPath();
					try {
						if (tryRelative &&
								pathItem != null &&
								pathPrevious != null &&
								Objects.equals(pathItem.getRoot(), pathPrevious.getRoot())
								) {
							Path pathRelative = pathBase.resolve(pathPrevious.relativize(pathItem));
							if (Files.exists(pathRelative)) {
								URI uri2 = pathRelative.normalize().toUri().normalize();
								replacements.put(item, new SingleUriItem(uri2));
							}
						}
					} catch (Exception e) {
						// Shouldn't occur (but being extra careful in case resolve/normalize/toUri or sth else complains)
						logger.warn("Error relativizing paths: {}", e.getLocalizedMessage());
						logger.debug(e.getLocalizedMessage(), e);
					}
				}
			}
			return replacements;
		}
				
		
		private void initialize() throws IOException {
			
			// Create a table view
			TableColumn<SingleUriItem, SingleUriItem> colOriginal = new TableColumn<>("Original URI");
			colOriginal.setCellValueFactory(item -> Bindings.createObjectBinding(() -> item.getValue()));
			colOriginal.setCellFactory(col -> new UriCell());
			table.getColumns().add(colOriginal);
			
			TableColumn<SingleUriItem, SingleUriItem> colReplacement = new TableColumn<>("Replacement URI");
			colReplacement.setCellValueFactory(item -> {
				return Bindings.createObjectBinding(() -> replacements.get(item.getValue()), replacements);
			});
			colReplacement.setCellFactory(col -> new UriCell());
			table.getColumns().add(colReplacement);
			
			FilteredList<SingleUriItem> filteredList = allItems.filtered(new TableFilter());
			table.setItems(filteredList);
			
			showMissing.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			showUnknown.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			showValid.addListener((v, o, n) -> filteredList.setPredicate(new TableFilter()));
			
			table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
			
			table.setPrefSize(600, 400);
			
			long nMissing = countOriginalItems(UriStatus.MISSING);
			long nExists = countOriginalItems(UriStatus.EXISTS);
			long nUnknown = countOriginalItems(UriStatus.UNKNOWN);
			
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
				var dir = Dialogs.getChooser(GuiTools.getWindow(btnSearch)).promptForDirectory(null);
				Map<String, List<SingleUriItem>> missing = allItems.stream().filter(p -> p.getStatus() == UriStatus.MISSING && p.getPath() != null && replacements.get(p) == null)
						.collect(Collectors.groupingBy(p -> p.getPath().getFileName().toString()));
				
				searchDirectoriesRecursive(dir, missing, maxRecursiveSearchDepth);
			});
			
			int row = 0;
			PaneTools.addGridRow(pane, row++, 0, null, table, table, table);
			PaneTools.addGridRow(pane, row++, 0, null, labelReplacements, labelReplacements, btnSearch);
			PaneTools.addGridRow(pane, row, 0, null, cbValid);
			PaneTools.addGridRow(pane, row, 1, null, cbMissing);
			PaneTools.addGridRow(pane, row++, 2, null, cbUnknown);
			
			PaneTools.setFillWidth(Boolean.TRUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			PaneTools.setHGrowPriority(Priority.ALWAYS, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			PaneTools.setMaxWidth(Double.MAX_VALUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
			GridPane.setHalignment(btnSearch, HPos.RIGHT);
			
			pane.setHgap(5);
			pane.setVgap(5);
			
			table.addEventHandler(KeyEvent.KEY_PRESSED, new TableCopyPasteHandler());
			table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		}
		
		
		private void searchDirectoriesRecursive(File dir, Map<String, List<SingleUriItem>> missing, int maxDepth) {
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
					List<SingleUriItem> myList = missing.remove(name);
					if (myList != null) {
						for (var item : myList)
							replacements.put(item, new SingleUriItem(f.toURI()));
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
		
		
		private int countOriginalItems(UriStatus status) {
			int n = 0;
			for (var item : allItems) {
				if (item.getStatus() == status)
					n++;
			}
			return n;
		}

//		private int countReplacedItems(UriStatus status) {
//			int n = 0;
//			for (var item : allItems) {
//				var item2 = replacements.getOrDefault(item, item);
//				if (item2.getStatus() == status)
//					n++;
//			}
//			return n;			
//		}

		class TableFilter implements Predicate<SingleUriItem> {

			@Override
			public boolean test(SingleUriItem item) {
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
								var item1 = new SingleUriItem(uri1);
								var item2 = new SingleUriItem(uri2);
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
		
		
		
		class UriCell extends TableCell<SingleUriItem, SingleUriItem> implements EventHandler<MouseEvent> {
			
			private Tooltip tooltip = new Tooltip();

			UriCell() {
				super();
				setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
				addEventHandler(MouseEvent.MOUSE_CLICKED, this);
			}

			@Override
			protected void updateItem(SingleUriItem item, boolean empty) {
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
				String path = Dialogs.getChooser(GuiTools.getWindow(this)).promptForFilePathOrURL("Change URI", defaultPath, null, null);
				if (path != null && !path.isBlank()) {
					URI uri = null;
					try {
						uri = GeneralTools.toURI(path);
					} catch (URISyntaxException e) {
						logger.error("Error parsing URI", e);
					}
					if (uri == null) {
						Dialogs.showErrorMessage("Change URI", "Unable to parse URI from " + path);
					} else
						replacements.put(uriOriginal, new SingleUriItem(uri));
				} else
					replacements.remove(uriOriginal);
			}

		}
		
		
	}
	
	
	static enum UriStatus { 
		EXISTS, MISSING, UNKNOWN;
	}
	
	private static class SingleUriItem {
		
		private URI uri;
		private Path path;
		
		SingleUriItem(URI uri) {
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
			SingleUriItem other = (SingleUriItem) obj;
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