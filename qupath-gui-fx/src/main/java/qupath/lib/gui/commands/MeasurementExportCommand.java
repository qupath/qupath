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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
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

	private static final String title = "Export Measurements";

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

	private static final ButtonType buttonTypeExport = new ButtonType("Export", ButtonData.OK_DONE);
	
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
			GuiTools.showNoProjectError(title);
			return;
		}
		if (dialog == null) {
			createDialog();
		}
		updateAvailableImages(project);
		showDialog();
	}

	private void createDialog() {
		// Set up grid pane
		GridPane pane = new GridPane();
		pane.setHgap(5.0);
		pane.setVgap(5.0);

		// Add main content
		int row = 0;
		addImageSelectionLists(pane, row++);
		addOutputFileChoice(pane, row++);
		addExportFileChoice(pane, row++);
		addSeparatorChoice(pane, row++);
		addPopulateColumns(pane, selectedImages, row++);

		FXUtils.getContentsOfType(pane, Label.class, false).forEach(e -> e.setMinWidth(160));

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

	private void updateAvailableImages(Project<BufferedImage> project) {
		if (project == null)
			availableImages.clear();
		else
			availableImages.setAll(project.getImageList());
	}

	private void addImageSelectionLists(GridPane pane, int row) {
		String sameImageWarning = "A selected image is open in the viewer!\nData should be saved before exporting.";
		var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, availableImages, new ArrayList<>(), sameImageWarning);
		pane.add(listSelectionView, 0, row, GridPane.REMAINING, 1);

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

	private void addOutputFileChoice(GridPane pane, int row) {
		Label labelOutputFile = new Label("Output file");
		var btnChooseFile = new Button("Choose");
		btnChooseFile.setOnAction(this::handleChooseFileButtonClick);

		var tfOutputPath = new TextField();
		tfOutputPath.textProperty().bindBidirectional(outputPathProperty);
		labelOutputFile.setLabelFor(tfOutputPath);
		tfOutputPath.setMaxWidth(Double.MAX_VALUE);
		btnChooseFile.setMaxWidth(Double.MAX_VALUE);

		GridPaneUtils.setToExpandGridPaneWidth(tfOutputPath);
		GridPaneUtils.addGridRow(pane, row++, 0, "Choose output file", labelOutputFile, tfOutputPath, tfOutputPath, btnChooseFile, btnChooseFile);
	}

	private void handleChooseFileButtonClick(ActionEvent e) {
		var separator = separatorTypeProperty.get();
		File pathOut = promptToChooseOutputFile(getDefaultOutputFile(), separator);
		if (pathOut != null) {
			outputPathProperty.set(pathOut.getAbsolutePath());
		}
	}

	private void addExportFileChoice(GridPane pane, int row) {
		var comboObjectType = new ComboBox<ExportObjectType>();

		Label pathObjectLabel = new Label("Export type");
		pathObjectLabel.setLabelFor(comboObjectType);
		comboObjectType.getItems().setAll(ExportObjectType.values());
		comboObjectType.getSelectionModel().select(objectTypeProperty.get());
		comboObjectType.valueProperty().addListener((v, o, n) -> {
			if (n != null)
				this.objectTypeProperty.set(n);
		});

		GridPaneUtils.setToExpandGridPaneWidth(comboObjectType);
		GridPaneUtils.addGridRow(pane, row, 0, "Choose the export type", pathObjectLabel, comboObjectType, comboObjectType, comboObjectType, comboObjectType);
	}

	private void addSeparatorChoice(GridPane pane, int row) {
		var comboSeparator = new ComboBox<ExportSeparatorType>();
		separatorTypeProperty.bind(comboSeparator.getSelectionModel().selectedItemProperty());

		// Update non-empty filename when separator changes
		separatorTypeProperty.addListener((v, o, n) -> {
			if (n != null || !outputPathProperty.getValueSafe().isEmpty()) {
				outputPathProperty.set(fixFileExtension(outputPathProperty.get(), n));
			}
		});

		Label separatorLabel = new Label("Separator");
		separatorLabel.setLabelFor(comboSeparator);
		comboSeparator.getItems().setAll(ExportSeparatorType.values());
		comboSeparator.getSelectionModel().selectFirst();

		GridPaneUtils.setToExpandGridPaneWidth(comboSeparator);
		GridPaneUtils.addGridRow(pane, row++, 0, "Choose a value separator", separatorLabel, comboSeparator, comboSeparator, comboSeparator, comboSeparator);
	}


	private void addPopulateColumns(GridPane pane, ObservableList<ProjectImageEntry<BufferedImage>> selectedImages, int row) {
		Label includeLabel = new Label("Columns to include (Optional)");
		includeLabel.setMinWidth(Label.USE_PREF_SIZE);
		var comboColumnsToInclude = new CheckComboBox<String>();
		includeLabel.setLabelFor(comboColumnsToInclude);
		comboColumnsToInclude.setShowCheckedCount(true);
		FXUtils.installSelectAllOrNoneMenu(comboColumnsToInclude);

		Button btnPopulateColumns = new Button("Populate");
		ProgressBar progressIndicator = new ProgressBar();
		progressIndicator.setPrefHeight(10);
		progressIndicator.setMaxWidth(Double.MAX_VALUE);
		progressIndicator.setProgress(0);
		progressIndicator.setOpacity(0);
		Button btnResetColumns = new Button("Reset");
		pane.add(progressIndicator, 1, row++);
		btnPopulateColumns.setOnAction(e ->
				populateColumns(comboColumnsToInclude, List.copyOf(selectedImages), progressIndicator)
		);

		btnPopulateColumns.disableProperty().addListener((v, o, n) -> {
			if (n != null && n)
				comboColumnsToInclude.setDisable(true);
		});

		btnPopulateColumns.disableProperty().bind(noImagesSelected);
		btnResetColumns.disableProperty().bind(noImagesSelected);
		btnResetColumns.setOnAction(e -> comboColumnsToInclude.getCheckModel().clearChecks());

		GridPaneUtils.setToExpandGridPaneWidth(comboColumnsToInclude);
		btnPopulateColumns.setMinWidth(75);
		btnResetColumns.setMinWidth(75);

		GridPaneUtils.addGridRow(pane, row++, 0,
				"Choose the specific column(s) to include (default: all)",
				includeLabel, comboColumnsToInclude, btnPopulateColumns, btnResetColumns);


		Bindings.bindContent(columnsToInclude, comboColumnsToInclude.getCheckModel().getCheckedItems());
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
		if (file.getParentFile() == null || !file.getParentFile().isDirectory())
			return false;
		return true;
	}

	private static File promptToChooseOutputFile(File initialFile, ExportSeparatorType separator) {
		var ext = separator.getExtension();
		File pathOut = FileChoosers.promptToSaveFile("Output file",
				initialFile,
				FileChoosers.createExtensionFilter(separator.getDescription(), ext),
				FileChoosers.createExtensionFilter("GZipped " + separator.getDescription(), ext + ".gz"));
		if (pathOut != null && pathOut.isDirectory()) {
			pathOut = new File(pathOut.getAbsolutePath(), "measurements" + ext);
		}
		return pathOut;
	}


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
		progress.getDialogPane().setHeaderText("Exporting measurements...");
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog("Cancel export", "Are you sure you want to stop the export after the current image?")) {
				worker.cancel(true);
				progress.setHeaderText("Cancelling...");
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});
		return progress;
	}


	private void populateColumns(CheckComboBox<String> comboColumnsToInclude, List<ProjectImageEntry<BufferedImage>> imageList, ProgressIndicator progressIndicator) {
		comboColumnsToInclude.setDisable(true);
		Set<String> allColumnsForCombo = Collections.synchronizedSet(new LinkedHashSet<>());
		progressIndicator.setProgress(0);
		progressIndicator.setOpacity(1.0);
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
				comboColumnsToInclude.setDisable(false);
				progressIndicator.setOpacity(0.0);
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
				Dialogs.showErrorMessage("Export failed", e);
				return null;
			} catch (InterruptedException e) {
				Dialogs.showWarningNotification("Export interrupted", "Measurement export was cancelled");
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
		ROOT(PathRootObject.class, "Image"),
		ANNOTATIONS(PathAnnotationObject.class, "Annotations"),
		DETECTIONS(PathDetectionObject.class, "Detections"),
		CELLS(PathCellObject.class, "Cells"),
		TILES(PathTileObject.class, "Tiles"),
		TMA_CORES(TMACoreObject.class, "TMA cores");

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
