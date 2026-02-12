/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

/**
 * Dialog box to export measurements for a project.
 */
public class MeasurementExportCommand implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);

	private static final String title = getResourceString("Measurements.Export.title");

	private final QuPathGUI qupath;

	private Dialog<ButtonType> dialog = null;

	private final ObjectProperty<Project<BufferedImage>> projectProperty = new SimpleObjectProperty<>();
	private final ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();

	private final ObservableList<ProjectImageEntry<BufferedImage>> availableImages = FXCollections.observableArrayList();
	private final ObservableList<ProjectImageEntry<BufferedImage>> selectedImages = FXCollections.observableArrayList();

	private final BooleanBinding noImagesSelected = Bindings.isEmpty(selectedImages);

	private final ObjectProperty<ExportObjectType> objectTypeProperty = new SimpleObjectProperty<>(ExportObjectType.ROOT);
	private final ObjectProperty<ExportSeparatorType> separatorTypeProperty = new SimpleObjectProperty<>(ExportSeparatorType.TAB);
	private final StringProperty outputPathProperty = new SimpleStringProperty("");
	private final ObservableList<String> columnsToInclude = FXCollections.observableArrayList();

	private static final ButtonType buttonTypeExport = new ButtonType(
			getResourceString("Measurements.Export.button.export"), ButtonData.OK_DONE);

	/**
	 * Total number of grid pane columns, used to determine column spans.
	 */
	private static final int MAX_GRID_PANE_COLUMNS = 4;

	/**
	 * Creates a simple GUI for MeasurementExporter.
	 * @param qupath the main QuPath instance
	 */
	public MeasurementExportCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.projectProperty.bind(qupath.projectProperty());
	}

	@Override
	public void run() {
		var project = projectProperty.get();
		if (project == null) {
			availableImages.clear();
			GuiTools.showNoProjectError(title);
			return;
		}
		if (dialog == null) {
			createDialog();
		}
		availableImages.setAll(project.getImageList());
		showDialog();
	}

	private static String getResourceString(String key) {
		return QuPathResources.getString(key);
	}

	private void createDialog() {
		// Set up grid pane
		GridPane pane = new GridPane();
		pane.setHgap(5.0);
		pane.setVgap(5.0);

		// Add main content
		addImageSelectionLists(pane);
		addOutputFileChoice(pane);
		addExportFileChoice(pane);
		addSeparatorChoice(pane);
		addPopulateColumns(pane);

		dialog = Dialogs.builder()
				.title(title)
				.resizable()
				.buttons(buttonTypeExport, ButtonType.CANCEL)
				.content(pane)
				.prefHeight(400)
				.prefWidth(600)
				.build();

		// Disable the export button if there is no output path or no images selected
		dialog.getDialogPane().lookupButton(buttonTypeExport).disableProperty().bind(outputPathProperty.isEmpty().or(noImagesSelected));
	}

	private void addImageSelectionLists(GridPane pane) {
		String sameImageWarning = getResourceString("Measurements.Export.sameImageWarning");
		var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, availableImages, new ArrayList<>(), sameImageWarning);
		pane.add(listSelectionView, 0, pane.getRowCount(), GridPane.REMAINING, 1);

		GridPaneUtils.setToExpandGridPaneHeight(listSelectionView);
		GridPaneUtils.setToExpandGridPaneWidth(listSelectionView);

		availableImages.addListener((ListChangeListener.Change<? extends ProjectImageEntry<BufferedImage>> c) -> {
			handleAvailableImageChange(
					c.getList(),
					listSelectionView.getSourceItems(),
					listSelectionView.getTargetItems());
		});

		Bindings.bindContent(selectedImages, listSelectionView.getTargetItems());
	}

	/**
	 * Handle the available image list changing (which usually means a project has changed).
	 */
	private static void handleAvailableImageChange(
											ObservableList<? extends ProjectImageEntry<BufferedImage>> availableImages,
											ObservableList<ProjectImageEntry<BufferedImage>> sourceImages,
											ObservableList<ProjectImageEntry<BufferedImage>> targetImages) {
		if (availableImages.isEmpty()) {
			sourceImages.clear();
			targetImages.clear();
		} else {
			// Remove images from existing target list if they are no longer in the project
			var newImageSet = new LinkedHashSet<>(availableImages);
			targetImages.removeIf(i -> !newImageSet.contains(i));

			// Match the source list to include all current non-target images
			targetImages.forEach(newImageSet::remove);
			sourceImages.setAll(newImageSet);
		}
	}

	private void addOutputFileChoice(GridPane pane) {
		var btnChooseFile = new Button(getResourceString("Measurements.Export.chooseFile.button"));
		btnChooseFile.setOnAction(this::handleChooseFileButtonClick);

		var tfOutputPath = new TextField();
		tfOutputPath.textProperty().bindBidirectional(outputPathProperty);
		tfOutputPath.setMaxWidth(Double.MAX_VALUE);
		btnChooseFile.setMaxWidth(Double.MAX_VALUE);

		addGridPaneRow(pane,
				new Tooltip(getResourceString("Measurements.Export.chooseFile.tooltip")),
				getResourceString("Measurements.Export.chooseFile.label"),
				tfOutputPath,
				btnChooseFile);
	}

	private void handleChooseFileButtonClick(ActionEvent e) {
		var separator = separatorTypeProperty.get();
		File pathOut = promptToChooseOutputFile(getDefaultOutputFile(), separator);
		if (pathOut != null) {
			outputPathProperty.set(pathOut.getAbsolutePath());
		}
	}

	private void addExportFileChoice(GridPane pane) {
		var comboObjectType = new ComboBox<ExportObjectType>();
		comboObjectType.getItems().setAll(ExportObjectType.values());
		comboObjectType.getSelectionModel().select(objectTypeProperty.get());
		comboObjectType.valueProperty().addListener((v, o, n) -> {
			if (n != null)
				this.objectTypeProperty.set(n);
		});

		addGridPaneRow(pane,
				new Tooltip(getResourceString("Measurements.Export.objectType.tooltip")),
				getResourceString("Measurements.Export.objectType.label"),
				comboObjectType);
	}

	private void addSeparatorChoice(GridPane pane) {
		var comboSeparator = new ComboBox<ExportSeparatorType>();
		comboSeparator.getItems().setAll(ExportSeparatorType.values());
		comboSeparator.getSelectionModel().selectFirst();

		separatorTypeProperty.bind(comboSeparator.getSelectionModel().selectedItemProperty());

		// Update non-empty filename when separator changes
		separatorTypeProperty.subscribe(n -> {
			if (n != null || !outputPathProperty.getValueSafe().isEmpty()) {
				outputPathProperty.set(fixFileExtension(outputPathProperty.get(), n));
			}
		});

		addGridPaneRow(pane,
				new Tooltip(getResourceString("Measurements.Export.separator.label")),
				getResourceString("Measurements.Export.separator.tooltip"),
				comboSeparator);
	}


	private void addPopulateColumns(GridPane pane) {
		var comboColumnsToInclude = new CheckComboBox<String>();
		comboColumnsToInclude.setShowCheckedCount(true);
		comboColumnsToInclude.titleProperty().bind(
				Bindings.createStringBinding(() -> columnsToInclude.isEmpty() ?
						getResourceString("Measurements.Export.columns.allColumns") : null, columnsToInclude));
		comboColumnsToInclude.setShowCheckedCount(false);
		FXUtils.installSelectAllOrNoneMenu(comboColumnsToInclude);

		Button btnPopulateColumns = new Button(getResourceString("Measurements.Export.columns.button.populate"));
		ProgressBar progressIndicator = new ProgressBar();
		progressIndicator.setPrefHeight(10);
		progressIndicator.setMinHeight(10);
		progressIndicator.setMaxWidth(Double.MAX_VALUE);
		progressIndicator.setProgress(1.0);

		var populatingColumns = progressIndicator.progressProperty().lessThan(1.0);

		progressIndicator.opacityProperty().bind(
				Bindings.when(populatingColumns)
						.then(1.0)
						.otherwise(0.0));

		Button btnResetColumns = new Button(getResourceString("Measurements.Export.columns.button.reset"));
		btnPopulateColumns.setOnAction(_ -> {
			populateColumns(comboColumnsToInclude, List.copyOf(selectedImages), progressIndicator);
		});

		btnPopulateColumns.disableProperty().bind(populatingColumns.or(noImagesSelected));
		comboColumnsToInclude.disableProperty().bind(populatingColumns.or(Bindings.isEmpty(comboColumnsToInclude.getItems())));
		btnResetColumns.disableProperty().bind(btnPopulateColumns.disableProperty());
		btnResetColumns.setOnAction(_ -> comboColumnsToInclude.getCheckModel().clearChecks());

		btnPopulateColumns.setMinWidth(75);
		btnResetColumns.setMinWidth(75);

		addGridPaneRow(pane,
				new Tooltip(getResourceString("Measurements.Export.columns.tooltip")),
				getResourceString("Measurements.Export.columns.label"),
				comboColumnsToInclude,
				btnPopulateColumns,
				btnResetColumns);

		pane.add(progressIndicator, 1, pane.getRowCount());

		Bindings.bindContent(columnsToInclude, comboColumnsToInclude.getCheckModel().getCheckedItems());
	}

	/**
	 * Add a new row to a grid pane with a label, main control (which fills the width) and optional extra controls (e.g., buttons).
	 * @param pane the grid pane
	 * @param tooltip optional tooltip; this will be installed in all controls that do not have their own tooltip
	 * @param labelText text to include on the label for the row
	 * @param rowFillControl the main control, position directly beside the label
	 * @param extraControls optional array of additional controls, position after the main control
	 */
	private static void addGridPaneRow(GridPane pane, Tooltip tooltip, String labelText, Control rowFillControl, Control... extraControls) {
		Label label = new Label(labelText);
		label.setMinWidth(Label.USE_PREF_SIZE);
		label.setLabelFor(rowFillControl);
		installTooltipIfNeeded(tooltip, label);

		rowFillControl.setMaxWidth(Double.MAX_VALUE);
		GridPane.setFillWidth(rowFillControl, Boolean.TRUE);
		GridPane.setHgrow(rowFillControl, Priority.ALWAYS);
		installTooltipIfNeeded(tooltip, rowFillControl);

		int row = pane.getRowCount();
		pane.add(label, 0, row);
		if (extraControls.length == 0) {
			pane.add(rowFillControl, 1, row, GridPane.REMAINING, 1);
		} else if (extraControls.length + 2 > MAX_GRID_PANE_COLUMNS) {
			// Find out we've made a mistake at compile time
			throw new IllegalArgumentException("Attempting to add more than " + MAX_GRID_PANE_COLUMNS + "columns to grid pane!");
		} else {
			int mainColSpan = MAX_GRID_PANE_COLUMNS - extraControls.length - 1;
			pane.add(rowFillControl, 1, row, mainColSpan, 1);
			for (int i = 0; i < extraControls.length; i++) {
				var control = extraControls[i];
				installTooltipIfNeeded(tooltip, control);
				pane.add(control, 1 + mainColSpan + i, row);
			}
		}
	}

	private static void installTooltipIfNeeded(Tooltip tooltip, Control control) {
		if (tooltip != null && control.getTooltip() == null) {
			control.setTooltip(tooltip);
		}
	}


	private void showDialog() {
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isEmpty() || result.get() != buttonTypeExport || result.get() == ButtonType.CANCEL)
			return;

		if (selectedImages.isEmpty()) {
			logger.debug("No images selected for export!");
			return;
		}

		outputPathProperty.set(fixFileExtension(outputPathProperty.get(), separatorTypeProperty.get()));

		File fileOutput = new File(outputPathProperty.get());
		if (fileOutput.getParent() == null || !fileOutput.getParentFile().isDirectory()) {
			fileOutput = promptToChooseOutputFile(getDefaultOutputFile(), separatorTypeProperty.get());
		}
		if (fileOutput == null)
			return;

		String[] include = columnsToInclude.toArray(String[]::new);
		String separatorString = separatorTypeProperty.get() == null ?
				PathPrefs.tableDelimiterProperty().get() :
				separatorTypeProperty.get().getSeparator();

		MeasurementExporter exporter = new MeasurementExporter()
				.imageList(selectedImages)
				.separator(separatorString)
				.includeOnlyColumns(include)
				.exportType(objectTypeProperty.get().getObjectType());

		doExportWithProgressDialog(exporter, fileOutput.getAbsolutePath());
	}

	private File getDefaultOutputFile() {
		var pathOutput = outputPathProperty.getValueSafe();
		var project = projectProperty.get();
		if (isValidOutputFilePath(pathOutput))
			return new File(pathOutput);
		else if (project != null) {
			var separator = separatorTypeProperty.get();
			return new File(Projects.getBaseDirectory(project), "measurements" + separator.getExtension());
		} else {
			return null;
		}
	}

	private static boolean isValidOutputFilePath(String path) {
		if (path == null || path.isEmpty())
			return false;
		var file = new File(path);
        return file.getParentFile() != null && file.getParentFile().isDirectory();
    }

	private static File promptToChooseOutputFile(File initialFile, ExportSeparatorType separator) {
		var ext = separator.getExtension();
		File pathOut = FileChoosers.promptToSaveFile(getResourceString("Measurements.Export.save.title"),
				initialFile,
				FileChoosers.createExtensionFilter(separator.getDescription(), ext),
				FileChoosers.createExtensionFilter("GZipped " + separator.getDescription(), ext + ".gz"));
		if (pathOut != null && pathOut.isDirectory()) {
			pathOut = new File(pathOut.getAbsolutePath(), "measurements" + ext);
		}
		return pathOut;
	}

	/**
	 * Update a file extension whenever the separator type may have changed, keeping the rest of the path the same.
	 * This should properly handle .gz being included as an additional extension.
	 */
	private static String fixFileExtension(String path, ExportSeparatorType separatorType) {
		if (path == null || path.isEmpty())
			return "";
		if (separatorType == null)
			return path;
		var ext = separatorType.getExtension();
		var pathLower = path.toLowerCase();
		if (pathLower.endsWith(ext)) {
			return pathLower;
		} else if (pathLower.endsWith(".gz")) {
			return fixFileExtension(path.substring(0, path.length()-3), separatorType) + ".gz";
		} else {
			return GeneralTools.stripExtension(path) + ext;
		}
	}


	private void doExportWithProgressDialog(MeasurementExporter exporter, String outputPath) {
		var worker = new MeasurementExportTask(exporter, outputPath);
		var progress = createProgressDialog(worker);
		runningTask.set(qupath.getThreadPoolManager().getSingleThreadExecutor(this).submit(worker));
		progress.show();
	}

	private ProgressDialog createProgressDialog(Task<?> worker) {
		ProgressDialog progress = new ProgressDialog(worker);
		progress.setWidth(600);
		progress.initOwner(qupath.getStage());
		progress.setTitle(title);
		progress.getDialogPane().setHeaderText(getResourceString("Measurements.Export.progress.header"));
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog(getResourceString("Measurements.Export.progress.cancel.title"),
					getResourceString("Measurements.Export.progress.cancel.text"))) {
				worker.cancel(true);
				progress.setHeaderText(getResourceString("Measurements.Export.progress.cancel.header"));
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});
		return progress;
	}


	private void populateColumns(CheckComboBox<String> comboColumnsToInclude, List<ProjectImageEntry<BufferedImage>> imageList, ProgressIndicator progressIndicator) {
		Set<String> allColumnsForCombo = Collections.synchronizedSet(new LinkedHashSet<>());
		progressIndicator.setProgress(0);
		CompletableFuture.runAsync(() -> {
				int n = imageList.size();
				int counter = 0;
				for (var entry : imageList) {
					try (var imageData = entry.readImageData()) {
						double progress = (double)counter / n;
						Platform.runLater(() -> progressIndicator.setProgress(progress));
						counter++;
						ObservableMeasurementTableData model = new ObservableMeasurementTableData();
						model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null,
								objectTypeProperty.get().getObjectType()));
						allColumnsForCombo.addAll(model.getAllNames());
					} catch (Exception ex) {
						logger.warn("Error loading columns for entry {}: {}", entry.getImageName(), ex.getMessage());
						logger.debug("{}", ex.getMessage(), ex);
					}
				}
			}).thenRunAsync(() -> {
				allColumnsForCombo.removeIf(Objects::isNull);
				comboColumnsToInclude.getItems().setAll(allColumnsForCombo);
				comboColumnsToInclude.getCheckModel().clearChecks();
				progressIndicator.setProgress(1);
		}, Platform::runLater);
		// Reset the checks
		comboColumnsToInclude.getCheckModel().clearChecks();
	}
	

	private static class MeasurementExportTask extends Task<Void> {
		
		private final String pathOut;
		private final MeasurementExporter exporter;
		
		MeasurementExportTask(MeasurementExporter exporter, String pathOut) {
			this.pathOut = pathOut;
			this.exporter = exporter;
		}


		@Override
		protected Void call() {
			long startTime = System.currentTimeMillis();

			try {
				exporter.progressMonitor(p -> updateProgress(p, 1.0))
						.exportMeasurements(new File(pathOut));
			} catch (IOException e) {
				logger.error("Export failed", e);
				Dialogs.showErrorMessage(
						getResourceString("Measurements.Export.failed.title"), e);
				return null;
			} catch (InterruptedException e) {
				Dialogs.showWarningNotification(
						getResourceString("Measurements.Export.interrupted.title"),
						getResourceString("Measurements.Export.interrupted.text"));
				logger.warn(e.getMessage(), e);
				return null;
			}
			
			long endTime = System.currentTimeMillis();
			
			long timeMillis = endTime - startTime;
            logger.info("Measurements export to {} ({} seconds)", pathOut,
					GeneralTools.formatNumber(timeMillis / 1000.0, 2));
			
			Dialogs.showInfoNotification(title, "Export complete!");
			return null;
		}
	}

	private enum ExportObjectType {
		ROOT(PathRootObject.class, getResourceString("Measurements.Export.type.image")),
		ANNOTATIONS(PathAnnotationObject.class, getResourceString("General.objects.annotations")),
		DETECTIONS(PathDetectionObject.class, getResourceString("General.objects.detections")),
		CELLS(PathCellObject.class, getResourceString("General.objects.cells")),
		TILES(PathTileObject.class, getResourceString("General.objects.tiles")),
		TMA_CORES(TMACoreObject.class, getResourceString("General.objects.tmaCores"));

		private final Class<? extends PathObject> type;
		private final String str;

		ExportObjectType(Class<? extends PathObject> type, String str) {
			this.type = type;
			this.str = str;
		}

		public Class<? extends PathObject> getObjectType() {
			return type;
		}

		@Override
		public String toString() {
			return this.str;
		}

	}

	private enum ExportSeparatorType {
		TAB("Tab", "Tab separated", ".tsv", "\t"),
		COMMA("Comma", "Comma separated", ".csv", ","),
		SEMICOLON("Semicolon", "Semicolon separated", ".csv", ";");

		private final String name;
		private final String description;
		private final String extension;
		private final String separator;

		ExportSeparatorType(String name, String description, String extension, String separator) {
			this.name = name;
			this.description = description;
			this.extension = extension;
			this.separator = separator;
		}

		public String getDescription() {
			return description;
		}

		public String getSeparator() {
			return separator;
		}

		public String getExtension() {
			return extension;
		}

		@Override
		public String toString() {
			return name + " (" + extension + ")";
		}

	}

}
