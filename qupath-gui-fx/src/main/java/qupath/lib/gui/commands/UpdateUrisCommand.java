/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021, 2024 QuPath developers, The University of Edinburgh
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Predicate;
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
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.fx.utils.FXUtils;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.common.GeneralTools;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.io.UriResource;
import qupath.lib.io.UriUpdater;
import qupath.lib.io.UriUpdater.SingleUriItem;
import qupath.lib.io.UriUpdater.UriStatus;


/**
 * Fix broken URIs by using relative paths or prompting the user to select files.
 * This is intended to handle cases where files or projects have changed location, so that links need to be updated.
 * 
 * @author Pete Bankhead
 * @param <T> 
 *
 */
public class UpdateUrisCommand<T extends UriResource> {

	private static final Logger logger = LoggerFactory.getLogger(UpdateUrisCommand.class);
	
	private static int maxRecursiveSearchDepth = 8;
	
	private final UriUpdater<T> updater;

	private final ObservableMap<SingleUriItem, SingleUriItem> replacements = FXCollections.observableMap(new HashMap<>());

	private GridPane pane;
	private final TableView<SingleUriItem> table = new TableView<>();
	private final ObservableList<SingleUriItem> allItems = FXCollections.observableArrayList();

	private final BooleanProperty showMissing = new SimpleBooleanProperty(true);
	private final BooleanProperty showValid = new SimpleBooleanProperty(true);
	private final BooleanProperty showUnknown = new SimpleBooleanProperty(true);

	private UpdateUrisCommand(Collection<T> resources) throws IOException {
		updater = UriUpdater.create(resources, allItems, replacements);
		updater.searchDepth(maxRecursiveSearchDepth);
	}

	Pane getPane() {
		if (pane == null) {
			pane = new GridPane();
			initialize();
		}
		return pane;
	}

	UriUpdater<T> getUpdater() {
		return updater;
	}
	
	/**
	 * Show dialog prompting the user to update URIs for missing files.
	 * Optionally provide previous and current base URIs.
	 * If not null, these will be used to relativize paths when searching for potential replacements URIs.
	 * Usually, these correspond to the current and previous paths for a project.
	 * 
	 * @param <T>
	 * @param items the items containing URIs to check
	 * @param basePrevious optional previous base path
	 * @param baseCurrent optional current base path
	 * @param onlyPromptIfMissing only show a dialog if any URIs correspond to missing files
	 * @return the number of changes made, or -1 if the user cancelled the dialog.
	 * @throws IOException if there was a problem accessing the URIs
	 */
	public static <T extends UriResource> int promptToUpdateUris(Collection<T> items, URI basePrevious, URI baseCurrent,
																 boolean onlyPromptIfMissing) throws IOException {
		
		var manager = new UpdateUrisCommand<>(items);

		var updater = manager.getUpdater();
		int nMissing = updater.countMissing();
		if (onlyPromptIfMissing && nMissing == 0)
			return 0;

		if (basePrevious != null && baseCurrent != null) {
			updater.relative(basePrevious, baseCurrent);
			// Check if relativization updated all URIs *and* all were inside the current base.
			// If so, we assume that we have a self-contained project (or similar),
			// and so we can accept all changes without prompting the user.
			int nReplacements = updater.countReplacements();
			var replacements = updater.getReplacements();
			if (onlyPromptIfMissing && nReplacements == nMissing &&
					replacements.values().stream().allMatch(u -> u.getPath() != null && u.getPath().startsWith(baseCurrent.getPath()))) {
				if (nReplacements == 1)
					logger.info("Updated 1 relative URI");
				else
                    logger.info("Updated {} relative URIs", nReplacements);
				updater.applyReplacements();
				return nReplacements;
			}
		}

		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setHeaderText("Files may have been deleted or moved!\nFix broken paths here by double-clicking on red entries and/or accepting QuPath's suggestions.");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
		((Button)dialog.getDialogPane().lookupButton(ButtonType.YES)).setText("Apply changes");
		((Button)dialog.getDialogPane().lookupButton(ButtonType.NO)).setText("Ignore");
		dialog.getDialogPane().setContent(manager.getPane());
		dialog.setTitle("Update URIs");
		dialog.setResizable(true);
		var btn = dialog.showAndWait().orElse(ButtonType.CANCEL);
		if (btn.equals(ButtonType.CANCEL))
			return -1;
		
		if (btn.equals(ButtonType.NO))
			return 0;
		
		int n = 0;
		try {
			n = updater.applyReplacements();
			if (n <= 0) {
				Dialogs.showInfoNotification("Update URIs", "No URIs updated!");
			} else if (n == 1) {
				Dialogs.showInfoNotification("Update URIs", "1 URI updated");
			} else {
				Dialogs.showInfoNotification("Update URIs", n + " URIs updated");
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage("Update URIs", e);
			logger.error(e.getMessage(), e);
		}
		return n;
	}
	

	


	private void initialize() {

		// Create a table view
		TableColumn<SingleUriItem, SingleUriItem> colOriginal = new TableColumn<>("Original URI");
		colOriginal.setCellValueFactory(item -> Bindings.createObjectBinding(item::getValue));
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

		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

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
			var dir = FileChoosers.promptForDirectory(FXUtils.getWindow(btnSearch), "Search directory", null);
			if (dir == null) {
				logger.debug("Search for URIs cancelled!");
				return;
			}
			updater.searchPath(dir.toPath());
		});

		int row = 0;
		GridPaneUtils.addGridRow(pane, row++, 0, null, table, table, table);
		GridPaneUtils.addGridRow(pane, row++, 0, null, labelReplacements, labelReplacements, btnSearch);
		GridPaneUtils.addGridRow(pane, row, 0, null, cbValid);
		GridPaneUtils.addGridRow(pane, row, 1, null, cbMissing);
		GridPaneUtils.addGridRow(pane, row++, 2, null, cbUnknown);

		GridPaneUtils.setFillWidth(Boolean.TRUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, cbValid, cbMissing, cbUnknown, labelReplacements, table);
		GridPaneUtils.setMaxWidth(Double.MAX_VALUE, cbValid, cbMissing, cbUnknown, labelReplacements, table);
		GridPane.setHalignment(btnSearch, HPos.RIGHT);

		pane.setHgap(5);
		pane.setVgap(5);

		table.addEventHandler(KeyEvent.KEY_PRESSED, new TableCopyPasteHandler());
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
	}


	private int countOriginalItems(UriStatus status) {
		int n = 0;
		for (var item : allItems) {
			if (item.getStatus() == status)
				n++;
		}
		return n;
	}

	class TableFilter implements Predicate<SingleUriItem> {

		@Override
		public boolean test(SingleUriItem item) {
            return switch (item.getStatus()) {
                case EXISTS -> showValid.get();
                case MISSING -> showMissing.get();
                default -> showUnknown.get();
            };
		}

	}


	class TableCopyPasteHandler implements EventHandler<KeyEvent> {

		private final KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		private final KeyCombination pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

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
			if (!sb.isEmpty())
				sb.append(System.lineSeparator());
			sb.append(uri.toString());
			sb.append("\t");
			var uri2 = replacements.get(uriItem);
			if (uri2 != null)
				sb.append(uri2.getURI().toString());
		}
		if (sb.isEmpty())
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
							updater.makeReplacement(uri1, uri2);
							table.refresh();
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
			String path = FileChoosers.promptForFilePathOrURI(FXUtils.getWindow(this), "Change URI", defaultPath);
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
					updater.makeReplacement(uriOriginal.getURI(), uri);
			} else
				updater.makeReplacement(uriOriginal.getURI(), null);
			table.refresh();
		}

	}

}