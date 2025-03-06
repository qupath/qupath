/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020, 2023, 2025 QuPath developers, The University of Edinburgh
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

import javafx.scene.control.ProgressBar;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import qupath.fx.utils.FXUtils;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

/**
 * Dialog box to export measurements
 * 
 * @author Melvin Gelbard
 */
public class MeasurementExportCommand implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);

	private static final String title = "Export Measurements";

	private final QuPathGUI qupath;
	private final ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private Dialog<ButtonType> dialog = null;
	private Project<BufferedImage> project;
	private final List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private Class<? extends PathObject> type = PathRootObject.class;
	
	// GUI
	private final TextField outputText = new TextField();
	private ComboBox<String> pathObjectCombo;
	private ComboBox<String> separatorCombo;
	private CheckComboBox<String> includeCombo;
	
	private final ButtonType btnExport = new ButtonType("Export", ButtonData.OK_DONE);
	
	/**
	 * Creates a simple GUI for MeasurementExporter.
	 * @param qupath the main QuPath instance
	 */
	public MeasurementExportCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		createAndShowDialog();
	}
	
	private void createAndShowDialog() {
		project = qupath.getProject();
		if (project == null) {
			GuiTools.showNoProjectError(title);
			return;
		}
		
		BorderPane mainPane = new BorderPane();
		
		BorderPane imageEntryPane = new BorderPane();
		GridPane optionPane = new GridPane();
		optionPane.setHgap(5.0);
		optionPane.setVgap(5.0);
		
		
		// TOP PANE (SELECT PROJECT ENTRY FOR EXPORT)
		project = qupath.getProject();
		pathObjectCombo = new ComboBox<>();
		separatorCombo = new ComboBox<>();
		includeCombo = new CheckComboBox<>();
		String sameImageWarning = "A selected image is open in the viewer!\nData should be saved before exporting.";
		var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), previousImages, sameImageWarning);
		

		// BOTTOM PANE (OPTIONS)
		int row = 0;
		Label pathOutputLabel = new Label("Output file");
		var btnChooseFile = new Button("Choose");
		btnChooseFile.setOnAction(e -> {
			String extSelected = separatorCombo.getSelectionModel().getSelectedItem();
			String ext = extSelected.equals("Tab (.tsv)") ? ".tsv" : ".csv";
			String extDesc = ext.equals(".tsv") ? "TSV (Tab delimited)" : "CSV (Comma delimited)";
			File pathOut = FileChoosers.promptToSaveFile("Output file",
					new File(Projects.getBaseDirectory(project), "measurements" + ext),
					FileChoosers.createExtensionFilter(extDesc, ext));
			if (pathOut != null) {
				if (pathOut.isDirectory())
					pathOut = new File(pathOut.getAbsolutePath() + File.separator + "measurements" + ext);
				outputText.setText(pathOut.getAbsolutePath());
			}
		});
		
		pathOutputLabel.setLabelFor(outputText);
		GridPaneUtils.addGridRow(optionPane, row++, 0, "Choose output file", pathOutputLabel, outputText, outputText, btnChooseFile, btnChooseFile);
		outputText.setMaxWidth(Double.MAX_VALUE);
		btnChooseFile.setMaxWidth(Double.MAX_VALUE);
		

		Label pathObjectLabel = new Label("Export type");
		pathObjectLabel.setLabelFor(pathObjectCombo);
		pathObjectCombo.getItems().setAll("Image", "Annotations", "Detections", "Cells", "TMA cores");
		pathObjectCombo.getSelectionModel().selectFirst();
		pathObjectCombo.valueProperty().addListener((v, o, n) -> {
			if (n != null)
				setType(n);
		});
	
		GridPaneUtils.addGridRow(optionPane, row++, 0, "Choose the export type", pathObjectLabel, pathObjectCombo, pathObjectCombo, pathObjectCombo, pathObjectCombo);

		Label separatorLabel = new Label("Separator");
		separatorLabel.setLabelFor(separatorCombo);
		separatorCombo.getItems().setAll("Tab (.tsv)", "Comma (.csv)", "Semicolon (.csv)");
		separatorCombo.getSelectionModel().selectFirst();
		GridPaneUtils.addGridRow(optionPane, row++, 0, "Choose a value separator", separatorLabel, separatorCombo, separatorCombo, separatorCombo, separatorCombo);
		
		
		Label includeLabel = new Label("Columns to include (Optional)");
		includeLabel.setMinWidth(Label.USE_PREF_SIZE);
		includeLabel.setLabelFor(includeCombo);
		includeCombo.setShowCheckedCount(true);
		FXUtils.installSelectAllOrNoneMenu(includeCombo);
		
		Button btnPopulateColumns = new Button("Populate");
		ProgressBar progressIndicator = new ProgressBar();
		progressIndicator.setPrefHeight(10);
		progressIndicator.setMaxWidth(Double.MAX_VALUE);
//		progressIndicator.setMinSize(50, 50);
		progressIndicator.setProgress(0);
		progressIndicator.setOpacity(0);
		Button btnResetColumns = new Button("Reset");
		GridPaneUtils.addGridRow(optionPane, row++, 0,
				"Choose the specific column(s) to include (default: all)",
				includeLabel, includeCombo, btnPopulateColumns, btnResetColumns);
		optionPane.add(progressIndicator, 1, row++);
		btnPopulateColumns.setOnAction(e ->
				populateColumns(List.copyOf(listSelectionView.getTargetItems()), progressIndicator)
		);
		
		btnPopulateColumns.disableProperty().addListener((v, o, n) -> {
			if (n != null && n)
				includeCombo.setDisable(true);
		});
		
		var targetItemBinding = Bindings.size(listSelectionView.getTargetItems()).isEqualTo(0);
		btnPopulateColumns.disableProperty().bind(targetItemBinding);
		btnResetColumns.disableProperty().bind(targetItemBinding);
		btnResetColumns.setOnAction(e -> includeCombo.getCheckModel().clearChecks()); 
		
		
		// Add listener to separatorCombo
		separatorCombo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n == null)
				return;
			String currentOut = outputText.getText();
			if (n.equals("Tab (.tsv)") && currentOut.endsWith(".csv"))
				outputText.setText(currentOut.replace(".csv", ".tsv"));
			else if ((n.equals("Comma (.csv)") || n.equals("Semicolon (.csv)")) && currentOut.endsWith(".tsv"))
				outputText.setText(currentOut.replace(".tsv", ".csv"));
		});

		FXUtils.getContentsOfType(optionPane, Label.class, false).forEach(e -> e.setMinWidth(160));
		GridPaneUtils.setToExpandGridPaneWidth(outputText, pathObjectCombo, separatorCombo, includeCombo);
		btnPopulateColumns.setMinWidth(75);
		btnResetColumns.setMinWidth(75);
		
		dialog = Dialogs.builder()
				.title(title)
				.resizable()
				.buttons(btnExport, ButtonType.CANCEL)
				.content(mainPane)
				.build();
		
		dialog.getDialogPane().setPrefSize(600, 400);
		imageEntryPane.setCenter(listSelectionView);
		
		// Set the disabledProperty according to (1) targetItems.size() > 0 and (2) outputText.isEmpty()
		var emptyOutputTextBinding = outputText.textProperty().isEqualTo("");
		dialog.getDialogPane().lookupButton(btnExport).disableProperty().bind(Bindings.or(emptyOutputTextBinding, targetItemBinding));
		
		mainPane.setCenter(imageEntryPane);
		mainPane.setBottom(optionPane);
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (result.isEmpty() || result.get() != btnExport || result.get() == ButtonType.CANCEL)
			return;

		String curExt = GeneralTools.getExtension(outputText.getText()).orElse("");
		if (!curExt.equals(".csv") && !curExt.equals(".tsv")) {
			// Fix extension, if required
			String extSelected = separatorCombo.getSelectionModel().getSelectedItem();
			String ext = extSelected.equals("Tab (.tsv)") ? ".tsv" : ".csv";
			outputText.setText(outputText.getText().substring(0, outputText.getText().length() - curExt.length()) + ext);
		}
		
		if (new File(outputText.getText()).getParent() == null) {
			String ext = GeneralTools.getExtension(outputText.getText()).orElse("").equals(".tsv") ? ".tsv": ".csv";
			String extDesc = ext.equals(".tsv") ? "TSV (Tab delimited)" : "CSV (Comma delimited)";
			File pathOut = FileChoosers.promptToSaveFile("Output file",
					new File(Projects.getBaseDirectory(project), outputText.getText()),
					FileChoosers.createExtensionFilter(extDesc, ext));
			if (pathOut == null)
				return;
			else
				outputText.setText(pathOut.getAbsolutePath());
		}
				
		var checkedItems = includeCombo.getCheckModel().getCheckedItems();
		String[] include = checkedItems.stream().toList().toArray(new String[checkedItems.size()]);
		String separator = PathPrefs.tableDelimiterProperty().get();

        separator = switch (separatorCombo.getSelectionModel().getSelectedItem()) {
            case "Tab (.tsv)" -> "\t";
            case "Comma (.csv)" -> ",";
            case "Semicolon (.csv)" -> ";";
            default -> separator;
        };
		
		MeasurementExporter exporter = new MeasurementExporter()
			.imageList(listSelectionView.getTargetItems())
			.separator(separator)
			.includeOnlyColumns(include)
			.exportType(type);
		
		ExportTask worker = new ExportTask(exporter, outputText.getText());
		
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
		
		// Create & run task
		runningTask.set(qupath.getThreadPoolManager().getSingleThreadExecutor(this).submit(worker));
		progress.show();
	}

	private void populateColumns(List<ProjectImageEntry<BufferedImage>> imageList, ProgressIndicator progressIndicator) {
		includeCombo.setDisable(true);
		Set<String> allColumnsForCombo = Collections.synchronizedSet(new LinkedHashSet<>());
		setType(pathObjectCombo.getSelectionModel().getSelectedItem());
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
						model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
						allColumnsForCombo.addAll(model.getAllNames());
					} catch (Exception ex) {
						logger.warn("Error loading columns for entry {}: {}", entry.getImageName(), ex.getMessage());
						logger.debug("{}", ex.getMessage(), ex);
					}
				}
			}).thenRunAsync(() -> {
				allColumnsForCombo.removeIf(Objects::isNull);
				includeCombo.getItems().setAll(allColumnsForCombo);
				includeCombo.getCheckModel().clearChecks();
				includeCombo.setDisable(false);
				progressIndicator.setOpacity(0.0);
				progressIndicator.setProgress(1);
		}, Platform::runLater);
		// Reset the checks
		includeCombo.getCheckModel().clearChecks();
	}
	
	private void setType(String typeString){
		if (typeString != null) {
			switch (typeString) {
			case "Image":
				type = PathRootObject.class;
				break;
			case "Annotations":
				type = PathAnnotationObject.class;
				break;
			case "Detections":
				type = PathDetectionObject.class;
				break;
			case "Cells":
				type = PathCellObject.class;
				break;
			case "TMA cores":
				type = TMACoreObject.class;
				break;
			}
		}
	}
	

	static class ExportTask extends Task<Void> {
		
		private final String pathOut;
		private final MeasurementExporter exporter;
		
		public ExportTask(MeasurementExporter exporter, String pathOut) {
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
				Dialogs.showErrorMessage("Export failed", e);
				return null;
			} catch (InterruptedException e) {
				Dialogs.showErrorNotification("Export interrupted", "Measurement export was cancelled");
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
}
