/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.NumberBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.actions.InfoMessage;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.ui.logviewer.ui.main.LogMessageCounts;
import qupath.ui.logviewer.ui.main.LogViewer;
import qupath.ui.logviewer.ui.textarea.TextAreaLogViewer;

import java.io.IOException;

/**
 * A viewer for log messages.
 *
 * @author Pete Bankhead
 *
 */
public class LogViewerCommand {

	private static final Logger logger = LoggerFactory.getLogger(LogViewerCommand.class);

	private Stage dialog = null;

	private Window parent;

	private Region logviewer;

	private LogMessageCounts counts;

	private LongProperty lastSeenErrorMessageCount = new SimpleLongProperty();

	private LongProperty errorMessageCounts = new SimpleLongProperty();

	private NumberBinding unseenMessageCounts = errorMessageCounts.subtract(lastSeenErrorMessageCount);

	private BooleanBinding hasUnseenErrors = unseenMessageCounts.greaterThan(0);

	private StringBinding errorMessageBinding = createErrorMessageBinding();

	private InfoMessage errorMessage = InfoMessage.error(errorMessageBinding, unseenMessageCounts);

	/**
	 * Constructor.
	 * @param parent
	 */
	public LogViewerCommand(Window parent) {
		this.parent = parent;
		try {
			LogViewer logviewer = new LogViewer();
			SystemMenuBar.manageChildMenuBar(logviewer.getMenubar());
			// Get counts so we can display warning badges
			this.counts = logviewer.getAllLogsMessageCounts();
			errorMessageCounts.bind(counts.errorLevelCountsProperty());

			// Fix cell size for better performance
			var table = logviewer.getTable();
			table.setFixedCellSize(25);
			this.logviewer = logviewer;
		} catch (IOException e) {
			logviewer = new TextAreaLogViewer();
			logger.error("Failed to create log viewer - using console instead", e);
		}
	}

	public void show() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> show());
			return;
		}
		if (dialog == null) {
			dialog = new Stage();
			dialog.setTitle("Log");

			errorMessageCounts.addListener((v, o, n) -> updateLastSeenErrors());
			dialog.showingProperty().addListener((v, o, n) -> updateLastSeenErrors());

			Scene scene = new Scene(logviewer);
			dialog.setScene(scene);
			dialog.setResizable(true);
			dialog.initModality(Modality.NONE);
			dialog.initOwner(parent);
			dialog.setResizable(true);
			dialog.show();
			dialog.sizeToScene();
			// TODO: It would be nice to figure this out automatically
			dialog.setMinWidth(600);
			dialog.setMinHeight(400);

//			dialog.setMinWidth(logviewer.getWidth());
//			dialog.setMinHeight(logviewer.getHeight() + 30);
			FXUtils.retainWindowPosition(dialog);
		} else {
			dialog.show();
		}
		if (logviewer instanceof LogViewer lv) {
			// Scroll to the bottom when showing
			var table = lv.getTable();
			table.scrollTo(table.getItems().size() - 1);
		}
	}

	private StringBinding createErrorMessageBinding() {
		return Bindings.createStringBinding(() -> {
			int value = unseenMessageCounts.intValue();
			if (value == 1)
				return "1 unseen error";
			else
				return value + " unseen errors";
		}, unseenMessageCounts);
	}

	private void updateLastSeenErrors() {
		if (dialog != null && dialog.isShowing())
			lastSeenErrorMessageCount.set(errorMessageCounts.get());
	}

	/**
	 * Get the counts of all messages logged by the log viewer.
	 * @return
	 */
	public LogMessageCounts getLogMessageCounts() {
		return counts;
	}

	/**
	 * Boolean binding indicating whether there are any unseen errors.
	 * 'Unseen' here means errors that occur since the log viewer was visible.
	 * @return
	 */
	public ObservableBooleanValue hasUnseenErrors() {
		return hasUnseenErrors;
	}


	private ObjectExpression<InfoMessage> infoMessage = Bindings.createObjectBinding(() -> {
		if (!hasUnseenErrors.get())
			return null;
		return errorMessage;
	}, hasUnseenErrors);

	/**
	 * Get a string expression to draw attention to error messages.
	 * @return a string expression that evaluates to the error message, or null if there are no errors
	 */
	public ObjectExpression<InfoMessage> getInfoMessage() {
		return infoMessage;
	}


}