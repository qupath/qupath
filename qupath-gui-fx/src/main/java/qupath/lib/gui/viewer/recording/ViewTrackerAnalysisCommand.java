/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.viewer.recording;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Command to export view tracking information.
 * 
 * @author Pete Bankhead
 *
 */
class ViewTrackerExportCommand implements Runnable {
	
	private QuPathViewer viewer;
	private ViewTracker tracker;
	
	private Stage dialog;
	private TableView<ViewRecordingFrame> table = new TableView<>();
	
	/**
	 * Constructor.
	 * @param viewer the viewer being tracked
	 * @param tracker the tracker doing the tracking
	 */
	public ViewTrackerExportCommand(final QuPathViewer viewer, final ViewTracker tracker) {
		this.viewer = viewer;
		this.tracker = tracker;
	}

	@Override
	public void run() {
		if (dialog == null) {
			dialog = new Stage();
			if (QuPathGUI.getInstance() != null)
				dialog.initOwner(QuPathGUI.getInstance().getStage());
			dialog.setTitle("View tracker");
			
			for (int i = 0; i < nCols(tracker); i++) {
				final int col = i;
				TableColumn<ViewRecordingFrame, Object> column = new TableColumn<>(getColumnName(col));
				column.setCellValueFactory(new Callback<CellDataFeatures<ViewRecordingFrame, Object>, ObservableValue<Object>>() {
				     @Override
					public ObservableValue<Object> call(CellDataFeatures<ViewRecordingFrame, Object> frame) {
				         return new SimpleObjectProperty<>(getColumnValue(frame.getValue(), col));
				     }
				  });
				table.getColumns().add(column);
			}
			
			table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
			table.getSelectionModel().selectedItemProperty().addListener((v, o, frame) -> {
					if (frame != null)
						ViewTrackerPlayback.setViewerForFrame(viewer, frame);
			});
			refreshTracker();
			
			Button btnImport = new Button("Import");
			btnImport.setOnAction(e -> {
					if (ViewTrackers.handleImport(tracker)) {
						refreshTracker();
					}
			});
			
			Button btnExport = new Button("Export");
			btnExport.setOnAction(e -> {
					ViewTrackers.handleExport(tracker);
			});
			
			Button btnCopy = new Button("Copy to clipboard");
			btnCopy.setOnAction(e -> {
				ClipboardContent content = new ClipboardContent();
				content.putString(tracker.getSummaryString());
			    Clipboard clipboard = Clipboard.getSystemClipboard();
			    clipboard.setContent(content);
			});

			GridPane panelButtons = PaneTools.createColumnGridControls(
					btnImport,
					btnExport,
					btnCopy
					);
			
			
			BorderPane pane = new BorderPane();
			pane.setCenter(table);
			pane.setBottom(panelButtons);
			dialog.setScene(new Scene(pane));
		}
		dialog.show();
		dialog.toFront();
	}
	
	
	static Object getColumnValue(final ViewRecordingFrame frame, final int col) {
		switch (col) {
		case 0: return frame.getTimestamp();
		case 1: return frame.getImageBounds().x;
		case 2: return frame.getImageBounds().y;
		case 3: return frame.getImageBounds().width;
		case 4: return frame.getImageBounds().height;
		case 5: return frame.getSize().width;
		case 6: return frame.getSize().height;
		case 7: return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getX();
		case 8: return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getY();
		case 9: return frame.getEyePosition() == null ? "" : frame.getEyePosition().getX();
		case 10: return frame.getEyePosition() == null ? "" : frame.getEyePosition().getY();
		case 11: return frame.isEyeFixated() == null ? "" : frame.isEyeFixated();
		}
		return null;
	}
	
	static String getColumnName(int col) {
		switch (col) {
		case 0: return "Timestamp (ms)";
		case 1: return "X";
		case 2: return "Y";
		case 3: return "Width";
		case 4: return "Height";
		case 5: return "Canvas width";
		case 6: return "Canvas height";
		case 7: return "Cursor X";
		case 8: return "Cursor Y";
		case 9: return "Eye X";
		case 10: return "Eye Y";
		case 11: return "Fixated";
		}
		return null;
	}
	
	static int nCols(final ViewTracker tracker) {
		if (tracker == null)
			return 0;
		if (tracker.hasEyeTrackingData())
			return 12;
		return 9;
	}
	
	
	
	void refreshTracker() {
		List<ViewRecordingFrame> frames = new ArrayList<>();
		if (tracker == null)
			return;
		for (int i = 0; i < tracker.nFrames(); i++) {
			frames.add(tracker.getFrame(i));
		}
		table.getItems().setAll(frames);
	}
	
	
}