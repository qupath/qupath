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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import org.controlsfx.control.ListSelectionView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Dialog box to export measurements
 * 
 * @author Melvin Gelbard
 *
 */

// TODO: Save current image(s)?
public class MeasurementExportCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private Dialog<ButtonType> dialog = null;
	private Project<BufferedImage> project;
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private String defSep = PathPrefs.getTableDelimiter();
	
	// GUI
	private TextField outputText = new TextField();
	private ComboBox<String> pathObjectCombo = new ComboBox<>();
	private ComboBox<String> separatorCombo = new ComboBox<>();
	private TextField includeText = new TextField();
	private TextField excludeText = new TextField();
	
	private ButtonType btnExport = new ButtonType("Export", ButtonData.OK_DONE);
	
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
			Dialogs.showErrorMessage("No open project", "Open a project to run this command!");
			return;
		}
		
		BorderPane mainPane = new BorderPane();
		
		BorderPane imageEntryPane = new BorderPane();
		GridPane optionPane = new GridPane();
		
		
		// TOP PANE (SELECT PROJECT ENTRY FOR EXPORT)
		project = qupath.getProject();
		ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView = new ListSelectionView<>();
		listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project, listSelectionView, previousImages, true);
		

		// BOTTOM PANE (OPTIONS)
		int row = 0;
		Label pathOutputLabel = new Label("Output File");
		var btnChooseFile = new Button("Choose path");
		btnChooseFile.setOnAction(e -> {
			String extSelected = separatorCombo.getSelectionModel().getSelectedItem();
			String ext = extSelected.equals("Tab (.tsv)") ? ".tsv" : ".csv";
			String extDesc = ext.equals(".tsv") ? "TSV (Tab delimited)" : "CSV (Comma delimited)";
			File pathOut = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Output file", null, "measurements" + ext, extDesc, ext);
			if (pathOut != null) {
				if (pathOut.isDirectory())
					pathOut = new File(pathOut.getAbsolutePath() + "/export" + ext);
				outputText.setText(pathOut.getAbsolutePath());
			}
		});
		
		optionPane.add(pathOutputLabel, 0, row);
		optionPane.add(outputText, 1, row);
		optionPane.add(btnChooseFile, 2, row++);
		pathOutputLabel.setLabelFor(outputText);
		

		Label pathObjectLabel = new Label("Apply on");
		pathObjectLabel.setLabelFor(pathObjectCombo);
		PaneTools.addGridRow(optionPane, row++, 0, "Choose to export either annotations or detections", pathObjectLabel, pathObjectCombo, pathObjectCombo, pathObjectCombo);
		pathObjectCombo.getItems().setAll("Annotations", "Detections");
		pathObjectCombo.getSelectionModel().selectFirst();


		Label separatorLabel = new Label("Separator");
		separatorLabel.setLabelFor(separatorCombo);
		separatorCombo.getItems().setAll("Tab (.tsv)", "Comma (.csv)");
		separatorCombo.getSelectionModel().selectFirst();
		PaneTools.addGridRow(optionPane, row++, 0, "Choose a value separator", separatorLabel, separatorCombo, separatorCombo);
		
		
		Label includeLabel = new Label("Columns to include (Optional)");
		includeLabel.setLabelFor(includeText);
		includeText.setPromptText("Image, Name, Class, ...");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the specific column(s) to include (case sensitive)", includeLabel, includeText, includeText);
		
		
		Label excludeLabel = new Label("Columns to exclude (Optional)");
		excludeLabel.setLabelFor(excludeText);
		excludeText.setPromptText("Image, Name, Class, ...");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the specific column(s) to include (case sensitive)", excludeLabel, excludeText, excludeText);
		
		
		// Add listener to separatorCombo
		separatorCombo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (outputText == null || n == null)
				return;
			String currentOut = outputText.getText();
			if (n.equals("Tab (.tsv)") && currentOut.endsWith(".csv"))
				outputText.setText(currentOut.replace(".csv", ".tsv"));
			else if (n.equals("Comma (.csv)") && currentOut.endsWith(".tsv"))
				outputText.setText(currentOut.replace(".tsv", ".csv"));
		});
		
		
		// Add listener to includeOnlyColumns
		includeText.textProperty().addListener((v, o, n) -> {
			if (!n.isEmpty()) {
				excludeText.setEditable(false);
				excludeText.setDisable(true);
			} else {
				excludeText.setEditable(true);
				excludeText.setDisable(false);
			}
				
		});

		optionPane.setVgap(6.0);
		optionPane.setHgap(10.0);
		optionPane.setPadding(new Insets(10.0));
		
		dialog = new Dialog<>();
		dialog.getDialogPane().setMinHeight(400);
		dialog.getDialogPane().setMinWidth(600);
		dialog.setTitle("Export measurements");
		dialog.getDialogPane().getButtonTypes().addAll(btnExport, ButtonType.CANCEL);
		dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
		dialog.getDialogPane().setContent(mainPane);		
		
		imageEntryPane.setCenter(listSelectionView)	;
		dialog.getDialogPane().lookupButton(btnExport).disableProperty().bind(Bindings.size(listSelectionView.getTargetItems()).isEqualTo(0));
		
		mainPane.setTop(imageEntryPane);
		//mainPane.setCenter(centrePane);
		mainPane.setBottom(optionPane);
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (!result.isPresent() || result.get() != btnExport || result.get() == ButtonType.CANCEL)
			return;
		
		String selectedItem = pathObjectCombo.getSelectionModel().getSelectedItem();
		boolean useDetections = selectedItem.equals("Detections") ? true : false;
		String[] exclude = Arrays.stream(excludeText.getText().split(",")).map(String::trim).toArray(String[]::new);
		String[] include = Arrays.stream(includeText.getText().split(",")).map(String::trim).toArray(String[]::new);
		String separator = separatorCombo.getSelectionModel().getSelectedItem().equals("Tab (.tsv)") ? "\t" : ", ";
		
		MeasurementExporter exporter;
		exporter = new MeasurementExporter()
			.imageList(listSelectionView.getTargetItems())
			.separator(separator)
			.excludeColumns(exclude)
			.includeOnlyColumns(include)
			.useDetections(useDetections);
		
		ExportTask worker = new ExportTask(exporter, outputText.getText());
		
		ProgressDialog progress = new ProgressDialog(worker);
		progress.setWidth(600);
		//progress.initOwner(dialog.getDialogPane().getScene().getWindow());
		progress.setTitle("Export measurements...");
		progress.getDialogPane().setHeaderText("Export measurement(s)");
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
	

	class ExportTask extends Task<Void> {
		
		private boolean quietCancel = false;
		private String pathOut;
		private List<ProjectImageEntry<BufferedImage>> imageList;
		private List<String> excludeColumns;
		private List<String> includeOnlyColumns;
		private String separator;
		
		// Default: Exporting annotations
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
					String[] header = data.get(0).split(separator);
					imageCols.put(entry, header);
					nImageEntries.put(entry, data.size()-1);
					
					for (String col: header) {
						if (!allColumns.contains(col)  && !excludeColumns.contains(col))
							allColumns.add(col);
					}
					
					// To keep the same column order, just delete non-relevant columns
					if (!includeOnlyColumns.get(0).isEmpty())
						allColumns.removeIf(n -> !includeOnlyColumns.contains(n));
					
					for (int i = 1; i < data.size(); i++) {
						
						String[] row = data.get(i).split(separator);
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
				boolean sepWithinCells = false;
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
								
								// Check for occurrences of the separator
								if (val.contains(separator) && !sepWithinCells) {
									sepWithinCells = true;
									logger.warn("Some measurement values contain the separator chosen, this might "
											+ "lead to inconsistencies in the output.");
								}
									
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
			
			Dialogs.showMessageDialog("Export completed", "Successful export!");
			return null;
		}
	}
}