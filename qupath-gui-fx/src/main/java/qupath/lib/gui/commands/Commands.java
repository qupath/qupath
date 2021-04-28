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

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

import org.controlsfx.control.CheckListView;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import qupath.lib.gui.CircularSlider;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.panes.MeasurementMapPane;
import qupath.lib.gui.panes.PathClassPane;
import qupath.lib.gui.panes.WorkflowCommandLogView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMASummaryViewer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.GridLines;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.recording.ViewTrackerControlPane;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

/**
 * Helper class implementing simple 'single-method' commands for easy inclusion in the GUI.
 * 
 * @author Pete Bankhead
 *
 */
public class Commands {
	
	private static Logger logger = LoggerFactory.getLogger(Commands.class);
	
	/**
	 * Insert the selected objects in the hierarchy, resolving positions accordingly.
	 * <p>
	 * This causes smaller 'completely-contained' annotations to be positioned below larger containing annotations, 
	 * and detections to be assigned to other annotations based on centroid location.
	 * @param imageData the image data containing the hierarchy
	 */
	public static void insertSelectedObjectsInHierarchy(ImageData<?> imageData) {
		if (imageData == null)
			return;
		var hierarchy = imageData.getHierarchy();
		hierarchy.insertPathObjects(hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	/**
	 * Resolve parent-child relationships within the object hierarchy.
	 * This means that objects will be arranged hierarchically, rather than as a flat list.
	 * @param imageData the image data to process
	 */
	public static void promptToResolveHierarchy(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Resolve hierarchy");
			return;
		}
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		
		if (!Dialogs.showConfirmDialog("Resolve hierarchy",
				"Are you sure you want to resolve object relationships?\n" +
				"For large object hierarchies this can take a long time.")) {
			return;
		}
		hierarchy.resolveHierarchy();
		
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Resolve hierarchy",
				"resolveHierarchy()"));
	}
	
	
	/**
	 * Create a full image annotation for the image in the specified viewer.
	 * The z and t positions of the viewer will be used.
	 * @param viewer the viewer containing the image to be processed
	 */
	public static void createFullImageAnnotation(QuPathViewer viewer) {
		if (viewer == null)
			return;
		ImageData<?> imageData = viewer.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Check if we already have a comparable annotation
		int z = viewer.getZPosition();
		int t = viewer.getTPosition();
		ImageRegion bounds = viewer.getServerBounds();
		ROI roi = ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlane(z, t));
		for (PathObject pathObject : hierarchy.getAnnotationObjects()) {
			ROI r2 = pathObject.getROI();
			if (r2 instanceof RectangleROI && 
					roi.getBoundsX() == r2.getBoundsX() && 
					roi.getBoundsY() == r2.getBoundsY() && 
					roi.getBoundsWidth() == r2.getBoundsWidth() && 
					roi.getBoundsHeight() == r2.getBoundsHeight() &&
					roi.getImagePlane().equals(r2.getImagePlane())) {
				logger.info("Full image annotation already exists! {}", pathObject);
				viewer.setSelectedObject(pathObject);
				return;
			}
		}
		
		PathObject pathObject = PathObjects.createAnnotationObject(roi);
		hierarchy.addPathObject(pathObject);
		viewer.setSelectedObject(pathObject);
		
		// Log in the history
		if (z == 0 && t == 0)
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Create full image annotation", "createSelectAllObject(true);"));
		else
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Create full image annotation", String.format("createSelectAllObject(true, %d, %d);", z, t)));
	}
	
	
	
	private static Map<QuPathGUI, RigidObjectEditorCommand> rigidObjectEditorMap = new WeakHashMap<>();
	
	/**
	 * Prompt to edit the selected annotation by translation and rotation.
	 * <p>
	 * Note that this method may change in future versions to be tied to a specified image data, 
	 * rather than a specific QuPath instance.
	 * @param qupath the QuPath instance for which the object should be edited
	 */
	public static void editSelectedAnnotation(QuPathGUI qupath) {		
		var editor = rigidObjectEditorMap.computeIfAbsent(qupath, q -> new RigidObjectEditorCommand(q));
		editor.run();
	}
	
	/**
	 * Show a measurement table for all detection objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showDetectionMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathDetectionObject.class);
	}
	
	/**
	 * Show a measurement table for all cell objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showCellMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathCellObject.class);
	}
	
	/**
	 * Show a measurement table for all annotation objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showAnnotationMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathAnnotationObject.class);
	}
	
	/**
	 * Show a measurement table for all TMA core objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showTMAMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, TMACoreObject.class);
	}
	
	
	/**
	 * Prompt to estimate stain vectors for the specified image, using any current region of interest.
	 * @param imageData the image data for which stain vectors should be estimated
	 */
	public static void promptToEstimateStainVectors(ImageData<BufferedImage> imageData) {
		EstimateStainVectorsCommand.promptToEstimateStainVectors(imageData);
	}
	
	
	private static DoubleProperty exportDownsample = PathPrefs.createPersistentPreference("exportRegionDownsample", 1.0);
	
	private static ImageWriter<BufferedImage> lastWriter = null;

	/**
	 * Prompt to export the current image region selected in the viewer.
	 * @param viewer the viewer containing the image to export
	 * @param renderedImage if true, export the rendered (RGB) image rather than original pixel values
	 */
	public static void promptToExportImageRegion(QuPathViewer viewer, boolean renderedImage) {
		if (viewer == null || viewer.getServer() == null) {
			Dialogs.showErrorMessage("Export image region", "No viewer & image selected!");
			return;
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		if (renderedImage)
			server = RenderedImageServer.createRenderedServer(viewer);
		PathObject pathObject = viewer.getSelectedObject();
		ROI roi = pathObject == null ? null : pathObject.getROI();
		
		double regionWidth = roi == null ? server.getWidth() : roi.getBoundsWidth();
		double regionHeight = roi == null ? server.getHeight() : roi.getBoundsHeight();
		
		// Create a dialog
		GridPane pane = new GridPane();
		int row = 0;
		pane.add(new Label("Export format"), 0, row);
		ComboBox<ImageWriter<BufferedImage>> comboImageType = new ComboBox<>();
		
		Function<ImageWriter<BufferedImage>, String> fun = (ImageWriter<BufferedImage> writer) -> writer.getName();
		comboImageType.setCellFactory(p -> GuiTools.createCustomListCell(fun));
		comboImageType.setButtonCell(GuiTools.createCustomListCell(fun));
		
		var writers = ImageWriterTools.getCompatibleWriters(server, null);
		comboImageType.getItems().setAll(writers);
		comboImageType.setTooltip(new Tooltip("Choose export image format"));
		if (writers.contains(lastWriter))
			comboImageType.getSelectionModel().select(lastWriter);
		else
			comboImageType.getSelectionModel().selectFirst();
		comboImageType.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(comboImageType, Priority.ALWAYS);
		pane.add(comboImageType, 1, row++);
		
		TextArea textArea = new TextArea();
		textArea.setPrefRowCount(2);
		textArea.setEditable(false);
		textArea.setWrapText(true);
//		textArea.setPadding(new Insets(15, 0, 0, 0));
		comboImageType.setOnAction(e -> textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails()));			
		textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails());
		pane.add(textArea, 0, row++, 2, 1);
		
		var label = new Label("Downsample factor");
		pane.add(label, 0, row);
		TextField tfDownsample = new TextField();
		label.setLabelFor(tfDownsample);
		pane.add(tfDownsample, 1, row++);
		tfDownsample.setTooltip(new Tooltip("Amount to scale down image - choose 1 to export at full resolution (note: for large images this may not succeed for memory reasons)"));
		ObservableDoubleValue downsample = Bindings.createDoubleBinding(() -> {
			try {
				return Double.parseDouble(tfDownsample.getText());
			} catch (NumberFormatException e) {
				return Double.NaN;
			}
		}, tfDownsample.textProperty());
		
		// Define a sensible limit for non-pyramidal images
		long maxPixels = 10000*10000;
		
		Label labelSize = new Label();
		labelSize.setMinWidth(400);
		labelSize.setTextAlignment(TextAlignment.CENTER);
		labelSize.setContentDisplay(ContentDisplay.CENTER);
		labelSize.setAlignment(Pos.CENTER);
		labelSize.setMaxWidth(Double.MAX_VALUE);
		labelSize.setTooltip(new Tooltip("Estimated size of exported image"));
		pane.add(labelSize, 0, row++, 2, 1);
		labelSize.textProperty().bind(Bindings.createStringBinding(() -> {
			if (!Double.isFinite(downsample.get())) {
				labelSize.setStyle("-fx-text-fill: red;");
				return "Invalid downsample value!  Must be >= 1";
			}
			else {
				long w = (long)(regionWidth / downsample.get() + 0.5);
				long h = (long)(regionHeight / downsample.get() + 0.5);
				String warning = "";
				var writer = comboImageType.getSelectionModel().getSelectedItem();
				boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
				if (!supportsPyramid && w * h > maxPixels) {
					labelSize.setStyle("-fx-text-fill: red;");
					warning = " (too big!)";
				} else if (w < 5 || h < 5) {
					labelSize.setStyle("-fx-text-fill: red;");
					warning = " (too small!)";					
				} else
					labelSize.setStyle(null);
				return String.format("Output image size: %d x %d pixels%s",
						w, h, warning
						);
			}
		}, downsample, comboImageType.getSelectionModel().selectedIndexProperty()));
		
		tfDownsample.setText(Double.toString(exportDownsample.get()));
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, labelSize, textArea, tfDownsample, comboImageType);
		PaneTools.setHGrowPriority(Priority.ALWAYS, labelSize, textArea, tfDownsample, comboImageType);
		
		pane.setVgap(5);
		pane.setHgap(5);
		
		if (!Dialogs.showConfirmDialog("Export image region", pane))
			return;
		
		var writer = comboImageType.getSelectionModel().getSelectedItem();
		boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
		int w = (int)(regionWidth / downsample.get() + 0.5);
		int h = (int)(regionHeight / downsample.get() + 0.5);
		if (!supportsPyramid && w * h > maxPixels) {
			Dialogs.showErrorNotification("Export image region", "Requested export region too large - try selecting a smaller region, or applying a higher downsample factor");
			return;
		}
		
		if (downsample.get() < 1 || !Double.isFinite(downsample.get())) {
			Dialogs.showErrorMessage("Export image region", "Downsample factor must be >= 1!");
			return;
		}
				
		exportDownsample.set(downsample.get());
		
		// Now that we know the output, we can create a new server to ensure it is downsampled as the necessary resolution
		if (renderedImage && downsample.get() != server.getDownsampleForResolution(0))
			server = new RenderedImageServer.Builder(viewer).downsamples(downsample.get()).build();
		
//		selectedImageType.set(comboImageType.getSelectionModel().getSelectedItem());
		
		// Create RegionRequest
		RegionRequest request = null;
		if (pathObject != null && pathObject.hasROI())
			request = RegionRequest.createInstance(server.getPath(), exportDownsample.get(), roi);				

		// Create a sensible default file name, and prompt for the actual name
		String ext = writer.getDefaultExtension();
		String writerName = writer.getName();
		String defaultName = GeneralTools.getNameWithoutExtension(new File(ServerTools.getDisplayableImageName(server)));
		if (roi != null) {
			defaultName = String.format("%s (%s, x=%d, y=%d, w=%d, h=%d)", defaultName,
					GeneralTools.formatNumber(request.getDownsample(), 2),
					request.getX(), request.getY(), request.getWidth(), request.getHeight());
		}
		File fileOutput = Dialogs.promptToSaveFile("Export image region", null, defaultName, writerName, ext);
		if (fileOutput == null)
			return;
		
		try {
			if (request == null) {
				if (exportDownsample.get() == 1.0)
					writer.writeImage(server, fileOutput.getAbsolutePath());
				else
					writer.writeImage(ImageServers.pyramidalize(server, exportDownsample.get()), fileOutput.getAbsolutePath());
			} else
				writer.writeImage(server, request, fileOutput.getAbsolutePath());
			lastWriter = writer;
		} catch (IOException e) {
			Dialogs.showErrorMessage("Export region", e);
		}
	}
	
	
	/**
	 * Show a dialog displaying the extensions installed for a specified QuPath instance.
	 * @param qupath the QuPath instance
	 */
	public static void showInstalledExtensions(final QuPathGUI qupath) {
		ShowInstalledExtensionsCommand.showInstalledExtensions(qupath);
	}
	
	
	/**
	 * Show a simple dialog for viewing (and optionally removing) detection measurements.
	 * @param qupath
	 * @param imageData
	 */
	public static void showDetectionMeasurementManager(QuPathGUI qupath, ImageData<?> imageData) {
		MeasurementManager.showDetectionMeasurementManager(qupath, imageData);
	}
	
	
	/**
	 * Reset TMA metadata, if available.
	 * @param imageData
	 * @return true if changes were made, false otherwise
	 */
	public static boolean resetTMAMetadata(ImageData<?> imageData) {
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			logger.warn("No TMA grid available!");
			return false;
		}
		QP.resetTMAMetadata(imageData.getHierarchy(), true);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Reset TMA metadata", "resetTMAMetadata(true);"));		
		return true;
	}
	
	/**
	 * Create a command that generates a persistent single dialog on demand.
	 * A reference to the dialog can be retained, so that if the command is called again 
	 * either the original dialog is shown and/or brought to the front.
	 * @param supplier supplier function to generate the dialog on demand
	 * @return the action
	 */
	public static Action createSingleStageAction(Supplier<Stage> supplier) {
		var command = new SingleStageCommand(supplier);
		return new Action(e -> command.show());
	}
	
	static class SingleStageCommand {
		
		private Stage stage;
		private Supplier<Stage> supplier;
		
		SingleStageCommand(Supplier<Stage> supplier) {
			this.supplier = supplier;
		}
		
		void show() {
			if (stage == null) {
				stage = supplier.get();
			}
			if (stage.isShowing())
				stage.toFront();
			else
				stage.show();
		}
		
	}
	
	/**
	 * Create a dialog for displaying measurement maps.
	 * @param qupath the {@link QuPathGUI} instance to which the maps refer
	 * @return a measurement map dialog
	 */
	public static Stage createMeasurementMapDialog(QuPathGUI qupath) {
		var dialog = new Stage();
		if (qupath != null)
			dialog.initOwner(qupath.getStage());
		dialog.setTitle("Measurement maps");
		
		var panel = new MeasurementMapPane(qupath);
		BorderPane pane = new BorderPane();
		pane.setCenter(panel.getPane());
		
		Scene scene = new Scene(pane, 300, 400);
		dialog.setScene(scene);
		dialog.setMinWidth(300);
		dialog.setMinHeight(400);
//		pane.setMinSize(300, 400);
//		dialog.setResizable(false);
		
		dialog.setOnCloseRequest(e -> {
			OverlayOptions overlayOptions = qupath.getOverlayOptions();
			if (overlayOptions != null)
				overlayOptions.resetMeasurementMapper();
			dialog.hide();
		});
		dialog.setOnShowing(e -> {
			panel.updateMeasurements();
		});
		return dialog;
	}
	
	
	/**
	 * Show a script interpreter window for a Qupath instance.
	 * @param qupath the QuPath instance
	 */
	public static void showScriptInterpreter(QuPathGUI qupath) {
		var scriptInterpreter = new ScriptInterpreter(qupath, QuPathGUI.getExtensionClassLoader());
		scriptInterpreter.getStage().initOwner(qupath.getStage());
		scriptInterpreter.getStage().show();
	}
	
	
	/**
	 * Create and show a new input display dialog.
	 * <p>
	 * This makes input such as key-presses and mouse button presses visible on screen, and is therefore
	 *  useful for demos and tutorials where shortcut keys are used.
	 *  
	 * @param qupath the QuPath instance
	 */
	public static void showInputDisplay(QuPathGUI qupath) {
		try {
			new InputDisplayDialog(qupath.getStage()).show();
		} catch (Exception e) {
			Dialogs.showErrorMessage("Error showing input display", e);
		}
	}
	
	/**
	 * Create a window summarizing license information for QuPath and its third party dependencies.
	 * @param qupath the current QuPath instance
	 * @return a window to display license information
	 */
	public static Stage createLicensesWindow(QuPathGUI qupath) {
		return ShowLicensesCommand.createLicensesDialog(qupath);
	}
	
	
	/**
	 * Create a window summarizing key system information relevant for QuPath.
	 * @param qupath the current QuPath instance
	 * @return a window to display license information
	 */
	public static Stage createShowSystemInfoDialog(QuPathGUI qupath) {
		return ShowSystemInfoCommand.createShowSystemInfoDialog(qupath);
	}
	
	
	/**
	 * Show a dialog to adjust QuPath preferences.
	 * @param qupath the QuPath instance
	 * @return window to use to display preferences
	 */
	public static Stage createPreferencesDialog(QuPathGUI qupath) {
		
		var panel = qupath.getPreferencePane();
		
		var dialog = new Stage();
		dialog.initOwner(qupath.getStage());
//			dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Preferences");
		
		Button btnExport = new Button("Export");
		btnExport.setOnAction(e -> exportPreferences(dialog));
		btnExport.setMaxWidth(Double.MAX_VALUE);

		Button btnImport = new Button("Import");
		btnImport.setOnAction(e -> importPreferences(dialog));
		btnImport.setMaxWidth(Double.MAX_VALUE);
		
		Button btnReset = new Button("Reset");
		btnReset.setOnAction(e -> PathPrefs.resetPreferences());
		btnReset.setMaxWidth(Double.MAX_VALUE);
		
		GridPane paneImportExport = new GridPane();
		paneImportExport.addRow(0, btnImport, btnExport, btnReset);
		PaneTools.setHGrowPriority(Priority.ALWAYS, btnImport, btnExport, btnReset);
		paneImportExport.setMaxWidth(Double.MAX_VALUE);

//			Button btnClose = new Button("Close");
//			btnClose.setOnAction(e -> {
//				dialog.hide();
//			});
		
		BorderPane pane = new BorderPane();
		pane.setCenter(panel.getPropertySheet());
		pane.setBottom(paneImportExport);
		if (qupath != null && qupath.getStage() != null) {
			pane.setPrefHeight(Math.round(Math.max(300, qupath.getStage().getHeight()*0.75)));
		}
		paneImportExport.prefWidthProperty().bind(pane.widthProperty());
//			btnClose.prefWidthProperty().bind(pane.widthProperty());
		dialog.setScene(new Scene(pane));
		dialog.setMinWidth(300);
		dialog.setMinHeight(300);
		
		return dialog;
	}

	private static boolean exportPreferences(Stage parent) {
		var file = Dialogs.getChooser(parent).promptToSaveFile(
				"Export preferences", null, null, "Preferences file", "xml");
		if (file != null) {
			try (var stream = Files.newOutputStream(file.toPath())) {
				logger.info("Exporting preferences to {}", file.getAbsolutePath());
				PathPrefs.exportPreferences(stream);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage("Import preferences", e);
			}
		}
		return false;
	}
	
	private static boolean importPreferences(Stage parent) {
		var file = Dialogs.getChooser(parent).promptForFile(
				"Import preferences", null, "Preferences file", "xml");
		if (file != null) {
			try (var stream = Files.newInputStream(file.toPath())) {
				logger.info("Importing preferences from {}", file.getAbsolutePath());
				PathPrefs.importPreferences(stream);
				Dialogs.showMessageDialog("Import preferences", 
						"Preferences have been imported - please restart QuPath to see the changes.");
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage("Import preferences", e);
			}
		}
		return false;
	}
	
	
	/**
	 * Create a dialog for rotating the image in the current viewer (for display only).
	 * @param qupath the {@link QuPathGUI} instance
	 */
	// TODO: Restrict this command to an opened image
	public static void createRotateImageDialog(QuPathGUI qupath) {
		var rotationCommand = new RotateImageCommand(qupath).createDialog();
		rotationCommand.show();
	}
	
	/**
	 * Create a zoom in/out command action.
	 * @param qupath QuPath instance
	 * @param zoomAmount relative amount to zoom in (positive) or out (negative). Suggested value is +/-10.
	 * @return
	 */
	public static Action createZoomCommand(QuPathGUI qupath, int zoomAmount) {
		var command = new ZoomCommand(qupath.viewerProperty(), zoomAmount);
		return ActionTools.createAction(command);
	}
	
	
	/**
	 * Create a stage to prompt the user to specify an annotation to add.
	 * @param qupath
	 * @return 
	 */
	public static Stage createSpecifyAnnotationDialog(QuPathGUI qupath) {
		SpecifyAnnotationCommand pane = new SpecifyAnnotationCommand(qupath);
		var stage = new Stage();
		var scene = new Scene(pane.getPane());
		stage.setScene(scene);
		stage.setWidth(300);
		stage.setTitle("Specify annotation");
		stage.initOwner(qupath.getStage());
		return stage;
	}
	
	
	/**
	 * Prompt to save the specified {@link ImageData}.
	 * @param qupath
	 * @param imageData
	 * @param overwriteExisting
	 * @return
	 */
	public static boolean promptToSaveImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData, boolean overwriteExisting) {
		if (imageData == null) {
			Dialogs.showNoImageError("Serialization error");
			return false;
		}
		try {
			var project = qupath.getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry != null) {
				if (overwriteExisting || Dialogs.showConfirmDialog("Save changes", "Save changes to " + entry.getImageName() + "?")) {
					entry.saveImageData(imageData);
					return true;
				} else
					return false;
			} else {
				String lastSavedPath = imageData.getLastSavedPath();
				File file = null;
				if (lastSavedPath != null) {
					// Use the last path, if required
					if (overwriteExisting)
						file = new File(lastSavedPath);
					if (file == null || !file.isFile()) {
						File fileDefault = new File(lastSavedPath);
						file = Dialogs.promptToSaveFile(null, fileDefault.getParentFile(), fileDefault.getName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
					}
				}
				else {
					ImageServer<?> server = imageData.getServer();
					String name = ServerTools.getDisplayableImageName(server);
					if (name.contains(".")) {
						try {
							name = GeneralTools.getNameWithoutExtension(new File(name));
						} catch (Exception e) {}
					}
					file = Dialogs.promptToSaveFile(null, null, name, "QuPath Serialized Data", PathPrefs.getSerializationExtension());
				}
				if (file == null)
					return false;
				PathIO.writeImageData(file, imageData);
				return true;
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage("Save ImageData", e);
			return false;
		}
	}
	
	
	// TODO: Make the extension modifiable
	private static StringProperty defaultScreenshotExtension = PathPrefs.createPersistentPreference("defaultScreenshotExtension", "png");
	
	
	/**
	 * Save an image snapshot, prompting the user to select the output file.
	 * @param qupath the {@link QuPathGUI} instance to snapshot
	 * @param type the snapshot type
	 * @return true if a snapshot was saved, false otherwise
	 */
	public static boolean saveSnapshot(QuPathGUI qupath, GuiTools.SnapshotType type) {
		BufferedImage img = GuiTools.makeSnapshot(qupath, type);			
		
		String ext = defaultScreenshotExtension.get();
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(BufferedImage.class, ext);
		if (compatibleWriters.isEmpty()) {
			logger.error("No compatible image writers found for extension: " + ext);
			return false;
		}
		
		File fileOutput = Dialogs.promptToSaveFile(null, null, null, ext, ext);
		if (fileOutput == null)
			return false;
		
		// Loop through the writers and stop when we are successful
		for (var writer : compatibleWriters) {
			try {
				writer.writeImage(img, fileOutput.getAbsolutePath());
				return true;
			} catch (Exception e) {
				logger.error("Error saving snapshot " + type + " to " + fileOutput.getAbsolutePath(), e);
			}
		}
		return false;
	}
	
//	/**
//	 * Merge the points ROIs of different objects to create a single object containing all points with a specific {@link PathClass}.
//	 * @param imageData the image data containing points to merge
//	 * @param selectedOnly if true, use only classes found within the currently selected objects
//	 */
//	public static void mergePointsForClasses(ImageData<?> imageData, boolean selectedOnly) {
//		var hierarchy = imageData == null ? null : imageData.getHierarchy();
//		if (hierarchy == null) {
//			Dialogs.showNoImageError("Merge points");
//			return;
//		}
//		if (selectedOnly) {
//			PathObjectTools.mergePointsForSelectedObjectClasses(hierarchy);
//			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
//					"Merge points for selected classifications",
//					"mergePointsForSelectedObjectClasses();"
//					));
//		} else {
//			PathObjectTools.mergePointsForAllClasses(hierarchy);
//			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
//					"Merge points for all classifications",
//					"mergePointsForAllClasses();"
//					));
//		}
//	}
	
	/**
	 * Merge the currently-selected annotations for an image, replacing them with a single new annotation.
	 * @param imageData
	 */
	public static void mergeSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		logger.debug("Merging selected annotations");
		QP.mergeSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Merge selected annotations",
				"mergeSelectedAnnotations()"));
	}

	
	/**
	 * Duplicate the selected annotations.
	 * @param imageData
	 */
	public static void duplicateSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Duplicate annotations");
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObjectTools.duplicateSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(
				new DefaultScriptableWorkflowStep("Duplicate selected annotations",
						"duplicateSelectedAnnotations()"));
	}

	/**
	 * Make an inverse annotation for the selected objects, storing the command in the history workflow.
	 * @param imageData
	 * @see QP#makeInverseAnnotation(ImageData)
	 */
	public static void makeInverseAnnotation(ImageData<?> imageData) {
		if (imageData == null)
			return;
		logger.debug("Make inverse annotation");
		QP.makeInverseAnnotation(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Invert selected annotation",
				"makeInverseAnnotation()"));
	}
	
	
	/**
	 * Show a dialog to track the viewed region of an image.
	 * @param qupath
	 */
	public static void showViewTracker(QuPathGUI qupath) {
		var dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Tracking");
		final ViewTrackerControlPane panel = new ViewTrackerControlPane(qupath.getViewer());
		StackPane pane = new StackPane(panel.getNode());
		dialog.setScene(new Scene(pane));
		dialog.setResizable(false);
		dialog.setAlwaysOnTop(true);
		dialog.setOnHidden(e -> {
			if (panel != null)
				panel.setRecording(false);
		});
		dialog.show();
	}

	
	
//	/**
//	 * Combine the selected annotations for the image open in the specified viewer.
//	 * @param viewer viewer containing the image data
//	 * @param op the {@link CombineOp} operation to apply
//	 * @return true if changes were made, false otherwise
//	 */
//	public static boolean combineSelectedAnnotations(QuPathViewer viewer, RoiTools.CombineOp op) {
//		var hierarchy = viewer == null ? null : viewer.getImageData();
//		return combineSelectedAnnotations(hierarchy, op);
//	}
	
	/**
	 * Combine the selected annotations for the specified hierarchy.
	 * @param imageData the image data to process
	 * @param op the {@link CombineOp} operation to apply
	 * @return true if changes were made, false otherwise
	 */
	public static boolean combineSelectedAnnotations(ImageData<?> imageData, RoiTools.CombineOp op) {
		// TODO: CONSIDER MAKING THIS SCRIPTABLE!
		if (imageData == null) {
			Dialogs.showNoImageError("Combine annotations");
			return false;
		}
		var hierarchy = imageData.getHierarchy();
		// Ensure the main selected object is first in the list, if possible
		var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		var mainObject = hierarchy.getSelectionModel().getSelectedObject();
		if (mainObject != null && !selected.isEmpty() && !selected.get(0).equals(mainObject)) {
			selected.remove(mainObject);
			selected.add(0, mainObject);
		}
		return combineAnnotations(hierarchy, selected, op);
	}
	
	
	/**
	 * Combine all the annotations that overlap with a selected object.
	 * <p>
	 * The selected object should itself be an annotation.
	 * 
	 * @param hierarchy
	 * @param pathObjects
	 * @param op
	 * @return true if any changes were made, false otherwise
	 */
	static boolean combineAnnotations(PathObjectHierarchy hierarchy, List<PathObject> pathObjects, RoiTools.CombineOp op) {
		if (hierarchy == null || hierarchy.isEmpty() || pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Cannot combine - no annotations found");
			return false;
		}
		
		pathObjects = new ArrayList<>(pathObjects);
		PathObject pathObject = pathObjects.get(0);
		if (!pathObject.isAnnotation()) { // || !RoiTools.isShapeROI(pathObject.getROI())) {
			logger.warn("Combine annotations: No annotation with ROI selected");				
			return false;
		}
		var plane = pathObject.getROI().getImagePlane();
//		pathObjects.removeIf(p -> !RoiTools.isShapeROI(p.getROI())); // Remove any null or point ROIs, TODO: Consider supporting points
		pathObjects.removeIf(p -> !p.hasROI() || !p.getROI().getImagePlane().equals(plane)); // Remove any null or point ROIs, TODO: Consider supporting points
		if (pathObjects.isEmpty()) {
			logger.warn("Cannot combine annotations - only one suitable annotation found");
			return false;
		}
		
		var allROIs = pathObjects.stream().map(p -> p.getROI()).collect(Collectors.toCollection(() -> new ArrayList<>()));
		ROI newROI;
		
		switch (op) {
		case ADD:
			newROI = RoiTools.union(allROIs);
			break;
		case INTERSECT:
			newROI = RoiTools.intersection(allROIs);
			break;
		case SUBTRACT:
			var first = allROIs.remove(0);
			newROI = RoiTools.combineROIs(first, RoiTools.union(allROIs), op);
			break;
		default:
			throw new IllegalArgumentException("Unknown combine op " + op);
		}
	
		if (newROI == null) {
			logger.debug("No changes were made");
			return false;
		}
		
		PathObject newObject = null;
		if (!newROI.isEmpty()) {
			newObject = PathObjects.createAnnotationObject(newROI, pathObject.getPathClass());
			newObject.setName(pathObject.getName());
			newObject.setColorRGB(pathObject.getColorRGB());
		}

		// Remove previous objects
		hierarchy.removeObjects(pathObjects, true);
		if (newObject != null)
			hierarchy.addPathObject(newObject);
		return true;
	}
	
	/**
	 * Prompt to select objects according to their classifications.
	 * @param qupath
	 * @param imageData
	 */
	public static void promptToSelectObjectsByClassification(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null)
			return;
		var pathClass = Dialogs.showChoiceDialog("Select objects", "", qupath.getAvailablePathClasses(), null);
		if (pathClass == null)
			return;
		PathClassPane.selectObjectsByClassification(imageData, pathClass);
	}

	
	/**
	 * Prompt to delete objects of a specified type, or all objects.
	 * @param imageData
	 * @param cls
	 */
	public static void promptToDeleteObjects(ImageData<?> imageData, Class<? extends PathObject> cls) {
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Handle no specified class - indicates all objects of all types should be cleared
		if (cls == null) {
			int n = hierarchy.nObjects();
			if (n == 0)
				return;
			String message;
			if (n == 1)
				message = "Delete object?";
			else
				message = "Delete all " + n + " objects?";
			if (Dialogs.showYesNoDialog("Delete objects", message)) {
				hierarchy.clearAll();
				hierarchy.getSelectionModel().setSelectedObject(null);
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear all objects", "clearAllObjects();"));
			}
			return;
		}
		
		// Handle clearing TMA grid
		if (TMACoreObject.class.equals(cls)) {
			if (hierarchy.getTMAGrid() != null) {
				if (Dialogs.showYesNoDialog("Delete objects", "Clear TMA grid?")) {
					hierarchy.setTMAGrid(null);
					
					PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
					if (selected instanceof TMACoreObject)
						hierarchy.getSelectionModel().setSelectedObject(null);

					imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA Grid", "clearTMAGrid();"));
				}
				return;
			}
		}
		
		
		// Handle clearing objects of another specified type
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		if (pathObjects.isEmpty())
			return;
		int n = pathObjects.size();
		String message = n == 1 ? "Delete 1 object?" : "Delete " + n + " objects?";
		if (Dialogs.showYesNoDialog("Delete objects", message)) {
			hierarchy.removeObjects(pathObjects, true);
			
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (cls == PathDetectionObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear detections", "clearDetections();"));
			else if (cls == PathAnnotationObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear annotations", "clearAnnotations();"));
			else if (cls == TMACoreObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA grid", "clearTMAGrid();"));
			else
				logger.warn("Cannot clear all objects for class {}", cls);
		}
	}
	
	
	/**
	 * Reset QuPath's preferences, after confirming with the user.
	 * QuPath needs to be restarted for this to take effect.
	 * @return true if the preferences were reset, false otherwise
	 */
	public static boolean promptToResetPreferences() {
		if (Dialogs.showConfirmDialog("Reset Preferences", "Do you want to reset all custom preferences?\n\nYou may have to restart QuPath to see all changes.")) {
			PathPrefs.resetPreferences();
			return true;
		}
		else
			logger.info("Reset preferences command skipped!");
		return false;
	}

	
	
	/**
	 * Set the downsample factor for the specified viewer.
	 * @param viewer
	 * @param downsample
	 */
	public static void setViewerDownsample(QuPathViewer viewer, double downsample) {
		if (viewer != null)
			viewer.setDownsampleFactor(downsample);
	}
	
	
	/**
	 * Close the current project open in the {@link QuPathGUI}.
	 * @param qupath
	 */
	public static void closeProject(QuPathGUI qupath) {
		qupath.setProject(null);
	}
	
	
	/**
	 * Prompt the user to select an empty directory, and use this to create a new project and set it as active.
	 * @param qupath the {@link QuPathGUI} instance for which the project should be created.
	 * @return true if a project was created, false otherwise (e.g. the user cancelled).
	 */
	public static boolean promptToCreateProject(QuPathGUI qupath) {
		File dir = Dialogs.promptForDirectory(null);
		if (dir == null)
			return false;
		if (!dir.isDirectory()) {
			logger.error(dir + " is not a valid project directory!");
		}
		for (File f : dir.listFiles()) {
			if (!f.isHidden()) {
				logger.error("Cannot create project for non-empty directory {}", dir);
				Dialogs.showErrorMessage("Project creator", "Project directory must be empty!");
				return false;
			}
		}
		qupath.setProject(Projects.createProject(dir, BufferedImage.class));
		return true;
	}
	
	
	/**
	 * Prompt the user to open an existing project and set it as active.
	 * @param qupath the {@link QuPathGUI} instance for which the project should be opened.
	 * @return true if a project was opened, false otherwise (e.g. the user cancelled).
	 */

	public static boolean promptToOpenProject(QuPathGUI qupath) {
		File fileProject = Dialogs.promptForFile("Choose project file", null, "QuPath projects", new String[]{ProjectIO.getProjectExtension()});
		if (fileProject != null) {
			try {
				Project<BufferedImage> project = ProjectIO.loadProject(fileProject, BufferedImage.class);
				qupath.setProject(project);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage("Load project", "Could not read project from " + fileProject.getName());
			}
		}
		return false;
	}
	
	
	
	/**
	 * Open new window with the TMA data viewer.
	 * @param qupath current {@link QuPathGUI} instance (may be null).
	 */
	public static void launchTMADataViewer(QuPathGUI qupath) {
		Stage stage = new Stage();
		if (qupath != null)
			stage.initOwner(qupath.getStage());
		TMASummaryViewer tmaViewer = new TMASummaryViewer(stage);
		
		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData != null && imageData.getHierarchy().getTMAGrid() != null)
			tmaViewer.setTMAEntriesFromImageData(imageData);
		
		try {
			Screen screen = Screen.getPrimary();
			stage.setWidth(screen.getBounds().getWidth()*0.75);
			stage.setHeight(screen.getBounds().getHeight()*0.75);
		} catch (Exception e) {
			logger.error("Exception setting stage size", e);
		}
		
		stage.show();
	}
	
	/**
	 * Compute the distance between all detections and the closest annotation, for all annotation classifications.
	 * @param imageData the image data to process
	 */
	public static void distanceToAnnotations2D(ImageData<?> imageData) {
		String title = "Distance to annotations 2D";
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog(title, 
					"Distance to annotations command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		var result = Dialogs.showYesNoCancelDialog(title, "Split multi-part classifications?\nIf yes, each component of classifications such as \"Class1: Class2\" will be treated separately.");
		boolean doSplit = false;
		if (result == DialogButton.YES)
			doSplit = true;
		else if (result != DialogButton.NO)
			return;

		
		DistanceTools.detectionToAnnotationDistances(imageData, doSplit);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Distance to annotations 2D",
				doSplit ? "detectionToAnnotationDistances(true)" : "detectionToAnnotationDistances(false)"));
	}
	
	/**
	 * Compute the distance between the centroids of all detections, for all available classifications.
	 * @param imageData the image data to process
	 */
	public static void detectionCentroidDistances2D(ImageData<?> imageData) {
		String title = "Detection centroid distances 2D";
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog(title, 
					"Detection centroid distances command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		var result = Dialogs.showYesNoCancelDialog(title, "Split multi-part classifications?\nIf yes, each component of classifications such as \"Class1: Class2\" will be treated separately.");
		boolean doSplit = false;
		if (result == DialogButton.YES)
			doSplit = true;
		else if (result != DialogButton.NO)
			return;
		
		DistanceTools.detectionCentroidDistances(imageData, doSplit);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Detection centroid distances 2D",
				doSplit ? "detectionCentroidDistances(true)" : "detectionCentroidDistances(false)"));
	}
	
	
	/**
	 * Prompt to input the spacing for the grid lines optionally displayed on viewers.
	 * @param options the {@link OverlayOptions} that manage the grid lines.
	 */
	public static void promptToSetGridLineSpacing(OverlayOptions options) {
		GridLines gridLines = options.getGridLines();
		
		ParameterList params = new ParameterList()
				.addDoubleParameter("hSpacing", "Horizontal spacing", gridLines.getSpaceX())
				.addDoubleParameter("vSpacing", "Vertical spacing", gridLines.getSpaceY())
				.addBooleanParameter("useMicrons", "Use microns", gridLines.useMicrons());
		
		if (!Dialogs.showParameterDialog("Set grid spacing", params))
			return;
		
		gridLines = new GridLines();
		gridLines.setSpaceX(params.getDoubleParameterValue("hSpacing"));
		gridLines.setSpaceY(params.getDoubleParameterValue("vSpacing"));
		gridLines.setUseMicrons(params.getBooleanParameterValue("useMicrons"));
		
		options.gridLinesProperty().set(gridLines);
	}
	
	
	/**
	 * Reload the specified image data from a previously saved version,if available.
	 * @param qupath
	 * @param imageData
	 */
	public static void reloadImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Reload data");
			return;
		}
		// TODO: Support loading from a project as well
		
		var viewer = qupath.getViewers().stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);
		if (viewer == null) {
			Dialogs.showErrorMessage("Reload data", "Specified image data not found open in any viewer!");
			return;
		}

		// Check if we have a saved file
		File savedFile = imageData.getLastSavedPath() == null ? null : new File(imageData.getLastSavedPath());
		if (savedFile == null || !savedFile.isFile()) {
			Dialogs.showErrorMessage("Reload", "No previously saved data found!");
			return;
		}
		
		if (Dialogs.showConfirmDialog("Reload", "Revert to last saved version?  All changes will be lost.")) {
			try {
				var project = qupath.getProject();
				var entry = project == null ? null : project.getEntry(imageData);
				ImageData<BufferedImage> imageDataNew;
				if (entry != null) {
					logger.info("Reloading image data from project entry: {}", entry);
					imageDataNew = entry.readImageData();
				} else {
					logger.info("Reverting to last saved version: {}", savedFile.getAbsolutePath());
					imageDataNew = PathIO.readImageData(savedFile, null, imageData.getServer(), BufferedImage.class);
				}
				viewer.setImageData(imageDataNew);
			} catch (Exception e) {
				Dialogs.showErrorMessage("Reload", "Error reverting to previously saved file\n\n" + e.getLocalizedMessage());
			}
		}

	}
	
	
	
	/**
	 * Prompt to add shape features for selected objects.
	 * @param qupath current QuPath instance
	 */
	public static void promptToAddShapeFeatures(QuPathGUI qupath) {
		
		var listView = new CheckListView<ShapeFeatures>();
		listView.getItems().setAll(ShapeFeatures.values());
		listView.getCheckModel().checkAll();
		
		// This is to work around a bug in ControlsFX 11.0.1 that can throw a NPE if the parent is unavailable
		listView.setCellFactory(view -> {
            var cell = new CheckBoxListCell<ShapeFeatures>(item -> listView.getItemBooleanProperty(item));
            cell.focusedProperty().addListener((o, ov, nv) -> {
                if (nv) {
                	var parent = cell.getParent();
                	if (parent != null)
                		parent.requestFocus();
                }
            });
            return cell;
        });
		
		listView.setPrefHeight(Math.min(listView.getItems().size() * 30, 320));
		
		var pane = new BorderPane(listView);
		
		listView.setTooltip(new Tooltip("Choose shape features"));
		var label = new Label("Add shape features to selected objects.\nNote that not all measurements are compatible with all objects.");
		label.setTextAlignment(TextAlignment.CENTER);
		label.setPadding(new Insets(10));
		pane.setTop(label);
		
		var btnSelectAll = new Button("Select all");
		btnSelectAll.setOnAction(e -> listView.getCheckModel().checkAll());
		var btnSelectNone = new Button("Select none");
		btnSelectNone.setOnAction(e -> listView.getCheckModel().clearChecks());
		
		btnSelectAll.setMaxWidth(Double.MAX_VALUE);
		btnSelectNone.setMaxWidth(Double.MAX_VALUE);
		
		pane.setBottom(PaneTools.createColumnGrid(btnSelectAll, btnSelectNone));
		
		var dialog = Dialogs.builder()
				.title("Shape features")
				.content(pane)
				.modality(Modality.NONE)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.build();
		
		var btnApply = (Button)dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		btnApply.disableProperty().bind(qupath.imageDataProperty().isNull());
		
		btnApply.setOnAction(e -> requestShapeFeatures(qupath.getImageData(), listView.getCheckModel().getCheckedItems()));
		
		dialog.show();
		
//		var result = dialog.showAndWait();
//		if (result.orElse(ButtonType.CANCEL) == ButtonType.APPLY)
//			requestShapeFeatures(qupath.getImageData(), listView.getSelectionModel().getSelectedItems());
	}
		
		
	private static void requestShapeFeatures(ImageData<?> imageData, Collection<ShapeFeatures> features) {
		if (imageData == null)
			return;
		var featureArray = features.toArray(ShapeFeatures[]::new);
		if (featureArray.length == 0)
			return;
		Collection<PathObject> selected = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			Dialogs.showWarningNotification("Shape features", "No objects selected!");
		} else {
			selected = new ArrayList<>(selected);			
			String featureString = Arrays.stream(featureArray).map(f -> "\"" + f.name() + "\"").collect(Collectors.joining(", "));
			QP.addShapeMeasurements(imageData, selected, featureArray);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Add shape measurements",
					String.format("addShapeMeasurements(%s)", featureString)
					));
			
			if (selected.size() == 1)
				Dialogs.showInfoNotification("Shape features", "Shape features calculated for one object");
			else
				Dialogs.showInfoNotification("Shape features", "Shape features calculated for " + selected.size() + " objects");
		}
	}
	

	
	/**
	 * Convert detection objects to point annotations based upon their ROI centroids.
	 * @param imageData the image data to process
	 * @param preferNucleus if true, use a nucleus ROI for cell objects (if available
	 */
	public static void convertDetectionsToPoints(ImageData<?> imageData, boolean preferNucleus) {
		if (imageData == null) {
			Dialogs.showNoImageError("Convert detections to points");
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage("Detections to points", "No detections found!");
			return;
		}
		
		// Remove any detections that don't have a ROI - can't do much with them
		Iterator<PathObject> iter = pathObjects.iterator();
		while (iter.hasNext()) {
			if (!iter.next().hasROI())
				iter.remove();
		}
		
		if (pathObjects.isEmpty()) {
			logger.warn("No detections found with ROIs!");
			return;
		}
		
		// Check if existing objects should be deleted
		String message = pathObjects.size() == 1 ? "Delete detection after converting to a point?" :
			String.format("Delete %d detections after converting to points?", pathObjects.size());
		var button = Dialogs.showYesNoCancelDialog("Detections to points", message);
		if (button == Dialogs.DialogButton.CANCEL)
			return;
		
		boolean	deleteDetections = button == Dialogs.DialogButton.YES;		
		PathObjectTools.convertToPoints(hierarchy, pathObjects, preferNucleus, deleteDetections);
	}


	/**
	 * Show a prompt to selected annotations in a hierarchy.
	 * @param imageData the current image data
	 * @param altitudeThreshold default altitude value for simplification
	 */
	public static void promptToSimplifySelectedAnnotations(ImageData<?> imageData, double altitudeThreshold) {
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		List<PathObject> pathObjects = hierarchy.getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isAnnotation() && p.hasROI() && p.isEditable() && !p.getROI().isPoint())
				.collect(Collectors.toList());
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage("Simplify annotations", "No unlocked shape annotations selected!");
			return;
		}

		String input = Dialogs.showInputDialog("Simplify shape", 
				"Set altitude threshold in pixels (> 0; higher values give simpler shapes)", 
				Double.toString(altitudeThreshold));
		if (input == null || !(input instanceof String) || ((String)input).trim().length() == 0)
			return;
		try {
			altitudeThreshold = Double.parseDouble(((String)input).trim());
		} catch (NumberFormatException e) {
			logger.error("Could not parse altitude threshold from {}", input);
			return;
		}
		
		long startTime = System.currentTimeMillis();
		for (var pathObject : pathObjects) {
			ROI pathROI = pathObject.getROI();
			if (pathROI instanceof PolygonROI) {
				PolygonROI polygonROI = (PolygonROI)pathROI;
				pathROI = ShapeSimplifier.simplifyPolygon(polygonROI, altitudeThreshold);
			} else {
				pathROI = ShapeSimplifier.simplifyShape(pathROI, altitudeThreshold);
			}
			((PathAnnotationObject)pathObject).setROI(pathROI);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Shapes simplified in " + (endTime - startTime) + " ms");
		hierarchy.fireObjectsChangedEvent(hierarchy, pathObjects);
	}

	/**
	 * Select all objects (excluding the root object) in the imageData.
	 * 
	 * @param imageData
	 */
	public static void selectAllObjects(final ImageData<?> imageData) {
		// Select all objects
		QP.selectAllObjects(imageData.getHierarchy(), false);
		
		// Add this step to the history workflow
		Map<String, String> map = new HashMap<>();
		map.put("includeRootObject", "false");
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Select all objects", map, "selectAllObjects(false)");
		imageData.getHistoryWorkflow().addStep(newStep);
	}


	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData
	 * @param cls
	 */
	public static void selectObjectsByClass(final ImageData<?> imageData, final Class<? extends PathObject> cls) {
		if (cls == TMACoreObject.class)
			QP.selectTMACores(imageData.getHierarchy());
		else
			QP.selectObjectsByClass(imageData.getHierarchy(), cls);
		
		Map<String, String> params = Collections.singletonMap("Type", PathObjectTools.getSuitableName(cls, false));
		String method;
		if (cls == PathAnnotationObject.class)
			method = "selectAnnotations();";
		else if (cls == PathDetectionObject.class)
			method = "selectDetections();";
		else if (cls == TMACoreObject.class)
			method = "selectTMACores();";
		else if (cls == PathCellObject.class)
			method = "selectCells();";
		else if (cls == PathTileObject.class)
			method = "selectTiles();";
		else
			// TODO: Get a suitable name to disguise Java classes
			method = "selectObjectsByClass(" + cls.getName() + ");";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Select objects by class", params, method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Reset the selection for an image.
	 * @param imageData
	 */
	public static void resetSelection(final ImageData<?> imageData) {
		if (imageData == null) {
			logger.warn("No image available!");
			return;
		}
		
		// Do the action reset
		imageData.getHierarchy().getSelectionModel().clearSelection();
		
		// Log the appropriate command
		String method = "resetSelection();";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset selection", method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData
	 * @param cls
	 */
	public static void resetClassifications(final ImageData<?> imageData, final Class<? extends PathObject> cls) {
		if (imageData == null) {
			logger.warn("No classifications to reset!");
			return;
		}
		// Do the reset
		QP.resetClassifications(imageData.getHierarchy(), cls);
		
		// Log the appropriate command
		Map<String, String> params = Collections.singletonMap("Type", PathObjectTools.getSuitableName(cls, false));
		String method;
		if (cls == PathDetectionObject.class)
			method = "resetDetectionClassifications();";
		else // TODO: Get a suitable name to disguise Java classes
			method = "resetClassifications(" + cls.getName() + ");";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset classifications", params, method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}
	
	
	/**
	 * Create a dialog to show the workflow history for the current image data.
	 * @param qupath the QuPath instance
	 * @return a workflow display dialog
	 */
	public static Stage createWorkflowDisplayDialog(QuPathGUI qupath) {
		var view = new WorkflowCommandLogView(qupath);
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Workflow viewer");
		Pane pane = view.getPane();
		dialog.setScene(new Scene(pane, 400, 400));
		return dialog;
	}
	
	
	/**
	 * Show the QuPath script editor with a script corresponding to the command history of a specified image.
	 * @param qupath
	 * @param imageData
	 */
	public static void showWorkflowScript(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Show workflow script");
			return;
		}
		WorkflowCommandLogView.showScript(qupath.getScriptEditor(), imageData.getHistoryWorkflow());
	}
	
	
	/**
	 * Show the script editor, or bring the window to the front if it is already open.
	 * @param qupath
	 */
	public static void showScriptEditor(QuPathGUI qupath) {
		var scriptEditor = qupath.getScriptEditor();
		if (scriptEditor == null) {
			Dialogs.showErrorMessage("Script editor", "No script editor found!");
			return;
		}
		// Show script editor with a new script
		if ((scriptEditor instanceof Window) && ((Window)scriptEditor).isShowing())
			((Window)scriptEditor).toFront();
		else
			scriptEditor.showEditor();
	}

	/**
	 * Create a dialog to monitor memory usage.
	 * @param qupath
	 * @return
	 */
	public static Stage createMemoryMonitorDialog(QuPathGUI qupath) {
		return new MemoryMonitorDialog(qupath).getStage();
	}

	/**
	 * Show a mini viewer window associated with a specific viewer.
	 * @param viewer the viewer with which to associate this window
	 */
	public static void showMiniViewer(QuPathViewer viewer) {
		if (viewer == null)
			return;
		MiniViewers.createDialog(viewer, false).show();
	}

	/**
	 * Show a channel viewer window associated with a specific viewer.
	 * @param viewer the viewer with which to associate this window
	 */
	public static void showChannelViewer(QuPathViewer viewer) {
		if (viewer == null)
			return;
		MiniViewers.createDialog(viewer, true).show();
	}
	
	/**
	 * Show a dialog to import object(s) from a file.
	 * @param qupath
	 * @param imageData
	 */
	public static void runObjectImport(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		try {
			ImportObjectsCommand.runObjectImport(qupath);
		} catch (IOException e) {
			Dialogs.showErrorNotification("Import error", e.getLocalizedMessage());
		}

	}

	/**
	 * Show a dialog to export object(s) to a GeoJSON file.
	 * @param qupath
	 * @param imageData
	 */
	public static void runGeoJsonObjectExport(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		try {
			ExportObjectsCommand.runGeoJsonExport(qupath);
		} catch (IOException e) {
			Dialogs.showErrorNotification("Export error", e.getLocalizedMessage());
		}
	}

	
	
}