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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.ListSelectionView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

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
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
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
 *
 */

public class MeasurementExportCommand implements Runnable {
	
	private QuPathGUI qupath;
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private Dialog<ButtonType> dialog = null;
	private Project<BufferedImage> project;
	private ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView;
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private String defSep = PathPrefs.tableDelimiterProperty().get();
	private ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("columnName-loader", true));
	private Class<? extends PathObject> type = PathRootObject.class;
	
	// GUI
	private TextField outputText = new TextField();
	private ComboBox<String> pathObjectCombo;
	private ComboBox<String> separatorCombo;
	private CheckComboBox<String> includeCombo;
	
	private ButtonType btnExport = new ButtonType("Export", ButtonData.OK_DONE);
	
	/**
	 * Creates a simple GUI for MeasurementExporter.
	 * @param qupath
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
			Dialogs.showNoProjectError("Export measurements");
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
		includeCombo = new CheckComboBox<String>();
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
			File pathOut = Dialogs.promptToSaveFile("Output file", Projects.getBaseDirectory(project), "measurements" + ext, extDesc, ext);
			if (pathOut != null) {
				if (pathOut.isDirectory())
					pathOut = new File(pathOut.getAbsolutePath() + File.separator + "measurements" + ext);
				outputText.setText(pathOut.getAbsolutePath());
			}
		});
		
		pathOutputLabel.setLabelFor(outputText);
		PaneTools.addGridRow(optionPane, row++, 0, "Choose output file", pathOutputLabel, outputText, outputText, btnChooseFile, btnChooseFile);
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
	
		PaneTools.addGridRow(optionPane, row++, 0, "Choose the export type", pathObjectLabel, pathObjectCombo, pathObjectCombo, pathObjectCombo, pathObjectCombo);

		Label separatorLabel = new Label("Separator");
		separatorLabel.setLabelFor(separatorCombo);
		separatorCombo.getItems().setAll("Tab (.tsv)", "Comma (.csv)", "Semicolon (.csv)");
		separatorCombo.getSelectionModel().selectFirst();
		PaneTools.addGridRow(optionPane, row++, 0, "Choose a value separator", separatorLabel, separatorCombo, separatorCombo, separatorCombo, separatorCombo);
		
		
		Label includeLabel = new Label("Columns to include (Optional)");
		includeLabel.setLabelFor(includeCombo);
		GuiTools.installSelectAllOrNoneMenu(includeCombo);
		
		Button btnPopulateColumns = new Button("Populate\t");
		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setPrefSize(20, 20);
		progressIndicator.setMinSize(20, 20);
		progressIndicator.setOpacity(0);
		Button btnResetColumns = new Button("Reset");
		PaneTools.addGridRow(optionPane, row++, 0, "Choose the specific column(s) to include (default: all)", includeLabel, includeCombo, btnPopulateColumns, progressIndicator, btnResetColumns);
		btnPopulateColumns.setOnAction(e -> {
			includeCombo.setDisable(true);
			Set<String> allColumnsForCombo = Collections.synchronizedSet(new LinkedHashSet<>());
			setType(pathObjectCombo.getSelectionModel().getSelectedItem());
			for (int i = 0; i < ProjectDialogs.getTargetItems(listSelectionView).size(); i++) {
				ProjectImageEntry<BufferedImage> entry = ProjectDialogs.getTargetItems(listSelectionView).get(i);
				int updatedEntries = i;
				executor.submit(() -> {
					try {
						progressIndicator.setOpacity(100);
						ImageData<?> imageData = entry.readImageData();
						ObservableMeasurementTableData model = new ObservableMeasurementTableData();
						model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
						allColumnsForCombo.addAll(model.getAllNames());
						imageData.getServer().close();

						if (updatedEntries == ProjectDialogs.getTargetItems(listSelectionView).size() - 1) {
							Platform.runLater(() -> {
								allColumnsForCombo.removeIf(n -> n == null);
								includeCombo.getItems().setAll(allColumnsForCombo);
								includeCombo.getCheckModel().clearChecks();
								includeCombo.setDisable(false);
							});
							progressIndicator.setOpacity(0);
						}
					} catch (Exception ex) {
						logger.warn("Error loading columns for entry " + entry.getImageName() + ": " + ex.getLocalizedMessage());
					}
				});
			}
			btnResetColumns.fire();
		});
		
		btnPopulateColumns.disableProperty().addListener((v, o, n) -> {
			if (n != null && n == true)
				includeCombo.setDisable(true);
		});
		
		var targetItemBinding = Bindings.size(listSelectionView.getTargetItems()).isEqualTo(0);
		btnPopulateColumns.disableProperty().bind(targetItemBinding);
		btnResetColumns.disableProperty().bind(targetItemBinding);
		btnResetColumns.setOnAction(e -> includeCombo.getCheckModel().clearChecks()); 
		
		
		// Add listener to separatorCombo
		separatorCombo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (outputText == null || n == null)
				return;
			String currentOut = outputText.getText();
			if (n.equals("Tab (.tsv)") && currentOut.endsWith(".csv"))
				outputText.setText(currentOut.replace(".csv", ".tsv"));
			else if ((n.equals("Comma (.csv)") || n.equals("Semicolon (.csv)")) && currentOut.endsWith(".tsv"))
				outputText.setText(currentOut.replace(".tsv", ".csv"));
		});

		PaneTools.getContentsOfType(optionPane, Label.class, false).forEach(e -> e.setMinWidth(160));
		PaneTools.setToExpandGridPaneWidth(outputText, pathObjectCombo, separatorCombo, includeCombo);
		btnPopulateColumns.setMinWidth(100);
		btnResetColumns.setMinWidth(75);
		
		dialog = Dialogs.builder()
				.title("Export measurements")
				.buttons(btnExport, ButtonType.CANCEL)
				.content(mainPane)
				.build();
		
		dialog.getDialogPane().setPrefSize(600, 400);
		imageEntryPane.setCenter(listSelectionView);
		
		// Set the disabledProperty according to (1) targetItems.size() > 0 and (2) outputText.isEmpty()
		var emptyOutputTextBinding = outputText.textProperty().isEqualTo("");
		dialog.getDialogPane().lookupButton(btnExport).disableProperty().bind(Bindings.or(emptyOutputTextBinding, targetItemBinding));
		
		mainPane.setTop(imageEntryPane);
		mainPane.setBottom(optionPane);
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (!result.isPresent() || result.get() != btnExport || result.get() == ButtonType.CANCEL)
			return;

		String curExt = Files.getFileExtension(outputText.getText());
		if (curExt.equals("") || (!curExt.equals("csv") && !curExt.equals("tsv"))) {
			curExt = curExt.length() > 1 ? "." + curExt : curExt;
			String extSelected = separatorCombo.getSelectionModel().getSelectedItem();
			String ext = extSelected.equals("Tab (.tsv)") ? ".tsv" : ".csv";
			outputText.setText(outputText.getText().substring(0, outputText.getText().length() - curExt.length()) + ext);
		}
		
		if (new File(outputText.getText()).getParent() == null) {
			String ext = Files.getFileExtension(outputText.getText()).equals("tsv") ? ".tsv": ".csv";
			String extDesc = ext.equals(".tsv") ? "TSV (Tab delimited)" : "CSV (Comma delimited)";
			File pathOut = Dialogs.promptToSaveFile("Output file", Projects.getBaseDirectory(project), outputText.getText(), extDesc, ext);
			if (pathOut == null)
				return;
			else
				outputText.setText(pathOut.getAbsolutePath());
		}
				
		var checkedItems = includeCombo.getCheckModel().getCheckedItems();
		String[] include = checkedItems.stream().collect(Collectors.toList()).toArray(new String[checkedItems.size()]);
		String separator = defSep;

		switch (separatorCombo.getSelectionModel().getSelectedItem()) {
		case "Tab (.tsv)":
			separator = "\t";
			break;
		case "Comma (.csv)":
			separator = ",";
			break;
		case "Semicolon (.csv)":
			separator = ";";
			break;
		};
		
		MeasurementExporter exporter;
		exporter = new MeasurementExporter()
			.imageList(ProjectDialogs.getTargetItems(listSelectionView))
			.separator(separator)
			.includeOnlyColumns(include)
			.exportType(type);
		
		ExportTask worker = new ExportTask(exporter, outputText.getText());
		
		ProgressDialog progress = new ProgressDialog(worker);
		progress.setWidth(600);
		progress.initOwner(qupath.getStage());
		progress.setTitle("Export measurements...");
		progress.getDialogPane().setHeaderText("Export measurements");
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog("Cancel export", "Are you sure you want to stop the export after the current image?")) {
				worker.quietCancel();
				progress.setHeaderText("Cancelling...");
//							worker.cancel(false);
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});
		
		// Create & run task
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
		progress.show();
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
			};
		}
	}
	

	class ExportTask extends Task<Void> {
		
		private boolean quietCancel = false;
		private String pathOut;
		private List<ProjectImageEntry<BufferedImage>> imageList;
		private List<String> excludeColumns;
		private List<String> includeOnlyColumns;
		private String separator;
		
		// Default: Exporting image
		private Class<? extends PathObject> type = PathAnnotationObject.class;
		
		
		public ExportTask(MeasurementExporter exporter, String pathOut) {
			this.pathOut = pathOut;
			this.imageList = exporter.getImageList();
			this.excludeColumns = exporter.getExcludeColumns();
			this.includeOnlyColumns = exporter.getIncludeColumns();
			if (exporter.getSeparator().isEmpty())
				this.separator = defSep;
			else
				this.separator = exporter.getSeparator();
			this.type = exporter.getType();
		}
		
		public void quietCancel() {
			this.quietCancel = true;
		}

		public boolean isQuietlyCancelled() {
			return quietCancel;
		}
		

		@Override
		protected Void call() {
			long startTime = System.currentTimeMillis();
	
			Map<ProjectImageEntry<?>, String[]> imageCols = new HashMap<ProjectImageEntry<?>, String[]>();
			Map<ProjectImageEntry<?>, Integer> nImageEntries = new HashMap<ProjectImageEntry<?>, Integer>();
			List<String> allColumns = new ArrayList<String>();
			Multimap<String, String> valueMap = LinkedListMultimap.create();
			File file = new File(pathOut);
			String pattern = "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
			
			int counter = 0;
			
			for (ProjectImageEntry<?> entry: imageList) {
				if (isQuietlyCancelled() || isCancelled()) {
					logger.warn("Export cancelled");
					return null;
				}
				
				updateProgress(counter, imageList.size()*2);
				counter++;
				updateMessage("Calculating measurements for " + entry.getImageName() + " (" + counter + "/" + imageList.size()*2 + ")");
				
				try {
					ImageData<?> imageData = entry.readImageData();
					ObservableMeasurementTableData model = new ObservableMeasurementTableData();
					model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
					List<String> data = SummaryMeasurementTableCommand.getTableModelStrings(model, separator, excludeColumns);
					
					// Get header
					String[] header;
					String headerString = data.get(0);
					if (headerString.chars().filter(e -> e == '"').count() > 1)
						header = headerString.split(separator.equals("\t") ? "\\" + separator : separator + pattern , -1);
					else
						header = headerString.split(separator);
					
					imageCols.put(entry, header);
					nImageEntries.put(entry, data.size()-1);
					
					for (String col: header) {
						if (!allColumns.contains(col)  && !excludeColumns.contains(col))
							allColumns.add(col);
					}
					
					// To keep the same column order, just delete non-relevant columns
					if (!includeOnlyColumns.isEmpty())
						allColumns.removeIf(n -> !includeOnlyColumns.contains(n));
					
					for (int i = 1; i < data.size(); i++) {
						String[] row;
						String rowString = data.get(i);
						
						// Check if some values in the row are escaped
						if (rowString.chars().filter(e -> e == '"').count() > 1)
							row = rowString.split(separator.equals("\t") ? "\\" + separator : separator + pattern , -1);
						else
							row = rowString.split(separator);
						
						// Put value in map
						for (int elem = 0; elem < row.length; elem++) {
							if (allColumns.contains(header[elem]))
								valueMap.put(header[elem], row[elem]);
						}
					}
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			}
	
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))){
				writer.println(String.join(separator, allColumns));
	
				Iterator[] its = new Iterator[allColumns.size()];
				for (int col = 0; col < allColumns.size(); col++) {
					its[col] = valueMap.get(allColumns.get(col)).iterator();
				}
				
				int counter2 = 0;
				for (ProjectImageEntry<?> entry: imageList) {
					if (isQuietlyCancelled() || isCancelled()) {
						logger.warn("Export cancelled with " + (imageList.size() - counter2) + " image(s) remaining");
						return null;
					}
					
					counter++;
					updateProgress(counter, imageList.size()*2);
					updateMessage("Exporting measurements of " + entry.getImageName() + " (" + counter + "/" + imageList.size()*2 + ")");
					
					for (int nObject = 0; nObject < nImageEntries.get(entry); nObject++) {
						for (int nCol = 0; nCol < allColumns.size(); nCol++) {
							if (Arrays.stream(imageCols.get(entry)).anyMatch(allColumns.get(nCol)::equals)) {
								String val = (String)its[nCol].next();
									
								// NaN values -> blank
								if (val.equals("NaN"))
									val = "";
								writer.print(val);
							}
							if (nCol < allColumns.size()-1)
								writer.print(separator);
						}
						writer.println();
					}
					counter2++;
				}
			} catch (FileNotFoundException e) {
				Dialogs.showMessageDialog("Export Failed", "Could not create output file. Export failed!");
				return null;
				
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
			
			long endTime = System.currentTimeMillis();
			
			long timeMillis = endTime - startTime;
			String time = null;
			if (timeMillis > 1000*60)
				time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
			else if (timeMillis > 1000)
				time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
			else
				time = String.format("Total processing time: %d milliseconds", timeMillis);
			logger.info("Processed {} images", imageList.size());
			logger.info(time);
			logger.info("Measurements exported to " + outputText.getText());
			
			Dialogs.showMessageDialog("Export completed", "Successful export!");
			return null;
		}
	}
}