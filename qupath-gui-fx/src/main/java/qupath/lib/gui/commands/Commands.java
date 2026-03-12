/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.controlsfx.control.CheckListView;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.UserDirectoryManager;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.panes.MeasurementMapPane;
import qupath.lib.gui.panes.ObjectDescriptionPane;
import qupath.lib.gui.panes.WorkflowCommandLogView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMASummaryViewer;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
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
import qupath.lib.io.FeatureCollection;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
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
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
			GuiTools.showNoImageError(QuPathResources.getString("Commands.resolveHierarchy"));
			return;
		}
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		
		int nObjects = hierarchy.getAllObjects(false).size();
		String message = QuPathResources.getString("Commands.sureToResolveRelationships");
		if (nObjects > 100) {
			message += QuPathResources.getString("Commands.largeObjectHierarchies");
		}
		
		if (!Dialogs.showConfirmDialog(QuPathResources.getString("Commands.resolveHierarchy"), message)) {
			return;
		}
		hierarchy.resolveHierarchy();
		
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.resolveHierarchy"),
				"resolveHierarchy()"));
	}
	
	
	/**
	 * Create a full image annotation for the image in the specified viewer.
	 * The z and t positions of the viewer will be used.
	 * @param viewer the viewer containing the image to be processed
	 */
	public static void createFullImageAnnotation(QuPathViewer viewer) {
		// If we are using selection mode, we should select objects rather that create an annotation
		if (PathPrefs.selectionModeStatus().get()) {
			logger.debug("Select all objects (create full image annotation with selection mode on)");
			selectObjectsOnCurrentPlane(viewer);
			return;
		}

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
				logger.warn("Full image annotation already exists! {}", pathObject);
				viewer.setSelectedObject(pathObject);
				return;
			}
		}
		
		PathObject pathObject = PathObjects.createAnnotationObject(roi);
		hierarchy.addObject(pathObject);
		viewer.setSelectedObject(pathObject);
		
		// Log in the history
		if (z == 0 && t == 0)
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.createFullImageAnnotation"),
					"createFullImageAnnotation(true)"
			));
		else
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.createFullImageAnnotation"),
					String.format("createFullImageAnnotation(true, %d, %d)", z, t)
			));
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
	 * Show a grid view for annotation objects.
	 * @param qupath the QuPath instance
	 */
	public static void showAnnotationGridView(QuPathGUI qupath) {
		PathObjectGridView.createAnnotationView(qupath).show();
	}

	/**
	 * Show a grid view for TMA core objects.
	 * @param qupath the QuPath instance
	 */
	public static void showTMACoreGridView(QuPathGUI qupath) {
		PathObjectGridView.createTmaCoreView(qupath).show();
	}

	/**
	 * Show a measurement table for all detection objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showDetectionMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathObjectFilter.DETECTIONS_ALL);
	}
	
	/**
	 * Show a measurement table for all cell objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showCellMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathObjectFilter.CELLS);
	}
	
	/**
	 * Show a measurement table for all annotation objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showAnnotationMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathObjectFilter.ANNOTATIONS);
	}
	
	/**
	 * Show a measurement table for all TMA core objects.
	 * @param qupath the QuPath instance
	 * @param imageData the image data for which to show measurements
	 */
	public static void showTMAMeasurementTable(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		new SummaryMeasurementTableCommand(qupath).showTable(imageData, PathObjectFilter.TMA_CORES);
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
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.exportImageRegion"),
					QuPathResources.getString("Commands.noViewerImageSelected")
			);
			return;
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		if (renderedImage) {
			try {
				server = RenderedImageServer.createRenderedServer(viewer);
			} catch (IOException e) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.exportImageRegion"),
						QuPathResources.getString("Commands.unableToCreateRendered")
				);
				logger.error(e.getMessage(), e);
				return;
			}
		}
		PathObject pathObject = viewer.getSelectedObject();
		ROI roi = pathObject == null ? null : pathObject.getROI();
		
		double regionWidth = roi == null ? server.getWidth() : roi.getBoundsWidth();
		double regionHeight = roi == null ? server.getHeight() : roi.getBoundsHeight();
		
		// Create a dialog
		GridPane pane = new GridPane();
		int row = 0;
		pane.add(new Label(QuPathResources.getString("Commands.exportFormat")), 0, row);
		ComboBox<ImageWriter<BufferedImage>> comboImageType = new ComboBox<>();
		
		Function<ImageWriter<BufferedImage>, String> fun = (ImageWriter<BufferedImage> writer) -> writer.getName();
		comboImageType.setCellFactory(p -> FXUtils.createCustomListCell(fun));
		comboImageType.setButtonCell(FXUtils.createCustomListCell(fun));
		
		var writers = ImageWriterTools.getCompatibleWriters(server, null);
		comboImageType.getItems().setAll(writers);
		comboImageType.setTooltip(new Tooltip(QuPathResources.getString("Commands.chooseExportFormat")));
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
		
		var label = new Label(QuPathResources.getString("Commands.downsampleFactor"));
		pane.add(label, 0, row);
		TextField tfDownsample = new TextField();
		label.setLabelFor(tfDownsample);
		pane.add(tfDownsample, 1, row++);
		tfDownsample.setTooltip(new Tooltip(QuPathResources.getString("Commands.downsampleFactorDescription")));
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
		labelSize.setTooltip(new Tooltip(QuPathResources.getString("Commands.estimatedSize")));
		pane.add(labelSize, 0, row++, 2, 1);
		labelSize.textProperty().bind(Bindings.createStringBinding(() -> {
			if (!Double.isFinite(downsample.get())) {
				labelSize.setStyle("-fx-text-fill: red;");
				return QuPathResources.getString("Commands.invalidDownsample");
			}
			else {
				long w = (long)(regionWidth / downsample.get() + 0.5);
				long h = (long)(regionHeight / downsample.get() + 0.5);
				var writer = comboImageType.getSelectionModel().getSelectedItem();
				boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();

				if (!supportsPyramid && w * h > maxPixels) {
					labelSize.setStyle("-fx-text-fill: red;");
					return MessageFormat.format(QuPathResources.getString("Commands.outputImageSizeTooBig"), w, h);
				} else if (w < 5 || h < 5) {
					labelSize.setStyle("-fx-text-fill: red;");
					return MessageFormat.format(QuPathResources.getString("Commands.outputImageSizeTooSmall"), w, h);
				} else {
					labelSize.setStyle(null);
					return MessageFormat.format(QuPathResources.getString("Commands.outputImageSize"), w, h);
				}
			}
		}, downsample, comboImageType.getSelectionModel().selectedIndexProperty()));
		
		tfDownsample.setText(Double.toString(exportDownsample.get()));
		
		GridPaneUtils.setMaxWidth(Double.MAX_VALUE, labelSize, textArea, tfDownsample, comboImageType);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, labelSize, textArea, tfDownsample, comboImageType);
		
		pane.setVgap(5);
		pane.setHgap(5);
		
		if (!Dialogs.showConfirmDialog(QuPathResources.getString("Commands.exportImageRegion"), pane))
			return;
		
		var writer = comboImageType.getSelectionModel().getSelectedItem();
		boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
		int w = (int)(regionWidth / downsample.get() + 0.5);
		int h = (int)(regionHeight / downsample.get() + 0.5);
		if (!supportsPyramid && w * h > maxPixels) {
			Dialogs.showErrorNotification(
					QuPathResources.getString("Commands.exportImageRegion"),
					QuPathResources.getString("Commands.exportRegionTooLarge")
			);
			return;
		}
		
		if (downsample.get() < 1 || !Double.isFinite(downsample.get())) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.exportImageRegion"),
					QuPathResources.getString("Commands.downsampleGreaterThanOne")
			);
			return;
		}
				
		exportDownsample.set(downsample.get());
		
		// Now that we know the output, we can create a new server to ensure it is downsampled as the necessary resolution
		if (renderedImage && downsample.get() != server.getDownsampleForResolution(0)) {
			try {
				server = new RenderedImageServer.Builder(viewer).downsamples(downsample.get()).build();
			} catch (IOException e) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.exportImageRegion"),
						QuPathResources.getString("Commands.unableToCreateRendered")
				);
				logger.error(e.getMessage(), e);
				return;
			}
		}
		
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
		File fileOutput = FileChoosers.promptToSaveFile(QuPathResources.getString("Commands.exportImageRegion"),
				new File(defaultName),
				FileChoosers.createExtensionFilter(writerName, ext));
		if (fileOutput == null)
			return;
		
		try {
			if (request == null) {
				if (exportDownsample.get() == 1.0) {
					writer.writeImage(server, fileOutput.getAbsolutePath());
				} else {
					// On rare (hopefully?) occasions this can be problematic, including a black line as the final
					// row and/or column. The workaround is to create a RegionRequest and use that instead.
					// (The benefit of pyramidalization is that it can potentially do a better job of writing tiled
					// images... or at least, I presume that's why I did this)
					// See https://github.com/qupath/qupath/pull/1531
					writer.writeImage(ImageServers.pyramidalize(server, exportDownsample.get()), fileOutput.getAbsolutePath());
				}
			} else
				writer.writeImage(server, request, fileOutput.getAbsolutePath());
			lastWriter = writer;
		} catch (IOException e) {
			Dialogs.showErrorMessage(QuPathResources.getString("Commands.exportRegion"), e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
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
	 * @param qupath the qupath GUI
	 * @param imageData the image data
	 */
	public static void showDetectionMeasurementManager(QuPathGUI qupath, ImageData<?> imageData) {
		MeasurementManager.showDetectionMeasurementManager(qupath, imageData);
	}
	
	
	/**
	 * Reset TMA metadata, if available.
	 * @param imageData the image data
	 * @return true if changes were made, false otherwise
	 */
	public static boolean resetTMAMetadata(ImageData<?> imageData) {
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			logger.warn("No TMA grid available!");
			return false;
		}
		QP.resetTMAMetadata(imageData.getHierarchy(), true);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.resetTmaMetadata"),
				"resetTMAMetadata(true)"
		));
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
	
	/**
	 * Create a named command that generates a persistent single dialog on demand.
	 * A reference to the dialog can be retained, so that if the command is called again 
	 * either the original dialog is shown and/or brought to the front.
	 * @param supplier supplier function to generate the dialog on demand
	 * @param name the command name (for the Action)
	 * @return the action
	 */
	public static Action createSingleStageAction(Supplier<Stage> supplier, String name) {
		var command = new SingleStageCommand(supplier);
		return new Action(name, e -> command.show());
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
		FXUtils.addCloseWindowShortcuts(dialog);
		dialog.setTitle(QuPathResources.getString("Commands.measurementMaps"));
		
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
		var scriptInterpreter = new ScriptInterpreter(qupath, QuPathGUI.getExtensionCatalogManager().getExtensionClassLoader());
		scriptInterpreter.getStage().initOwner(qupath.getStage());
		scriptInterpreter.getStage().show();
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
		
		var dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		FXUtils.addCloseWindowShortcuts(dialog);
//			dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(QuPathResources.getString("Commands.preferences"));
		
		Button btnExport = new Button(QuPathResources.getString("Commands.export"));
		btnExport.setOnAction(e -> exportPreferences(dialog));
		btnExport.setMaxWidth(Double.MAX_VALUE);

		Button btnImport = new Button(QuPathResources.getString("Commands.import"));
		btnImport.setOnAction(e -> importPreferences(dialog));
		btnImport.setMaxWidth(Double.MAX_VALUE);
		
		Button btnReset = new Button(QuPathResources.getString("Commands.reset"));
		btnReset.setOnAction(e -> PathPrefs.resetPreferences());
		btnReset.setMaxWidth(Double.MAX_VALUE);
		
		GridPane paneImportExport = new GridPane();
		paneImportExport.addRow(0, btnImport, btnExport, btnReset);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, btnImport, btnExport, btnReset);
		paneImportExport.setMaxWidth(Double.MAX_VALUE);

		BorderPane pane = new BorderPane();
		var prefPane = qupath.getPreferencePane();
		pane.setCenter(prefPane.getPane());
		pane.setBottom(paneImportExport);
		if (qupath != null && qupath.getStage() != null) {
			pane.setPrefHeight(Math.round(Math.max(300, qupath.getStage().getHeight()*0.75)));
		}
		paneImportExport.prefWidthProperty().bind(pane.widthProperty());
//			btnClose.prefWidthProperty().bind(pane.widthProperty());
		dialog.setScene(new Scene(pane));
		dialog.setMinWidth(300);
		dialog.setMinHeight(300);
		
		// Refresh the editors in case the locale has changed
		// (we could/should check if this is required...)
		dialog.setOnShowing(e -> prefPane.refreshAllEditors());
		
		return dialog;
	}

	private static boolean exportPreferences(Stage parent) {
		var file = FileChoosers.promptToSaveFile(parent,
				QuPathResources.getString("Commands.exportPreferences"), null,
				FileChoosers.createExtensionFilter(QuPathResources.getString("Commands.preferencesFile"), "xml"));
		if (file != null) {
			try (var stream = Files.newOutputStream(file.toPath())) {
				logger.info("Exporting preferences to {}", file.getAbsolutePath());
				PathPrefs.exportPreferences(stream);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage(QuPathResources.getString("Commands.importPreferences"), e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
			}
		}
		return false;
	}
	
	private static boolean importPreferences(Stage parent) {
		var file = FileChoosers.promptForFile(parent,
				QuPathResources.getString("Commands.importPreferences"),
				FileChoosers.createExtensionFilter(QuPathResources.getString("Commands.preferencesFile"), "xml"));
		if (file != null) {
			try (var stream = Files.newInputStream(file.toPath())) {
				logger.info("Importing preferences from {}", file.getAbsolutePath());
				PathPrefs.importPreferences(stream);
				Dialogs.showMessageDialog(
						QuPathResources.getString("Commands.importPreferences"),
						QuPathResources.getString("Commands.preferencesImported")
				);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage(QuPathResources.getString("Commands.importPreferences"), e);
				logger.error(e.getMessage(), e);
			}
		}
		return false;
	}
	
	
	/**
	 * Create a dialog for rotating the image in the current viewer (for display only).
	 * @param qupath the {@link QuPathGUI} instance
	 */
	// TODO: Restrict this command to an opened image
	// TODO: Convert to returning Stage, then use createSingleStageAction
	public static void createRotateImageDialog(QuPathGUI qupath) {
		var rotationCommand = new RotateImageCommand(qupath).createDialog();
		rotationCommand.show();
	}
	
	/**
	 * Create a zoom in/out command action.
	 * @param qupath QuPath instance
	 * @param zoomAmount relative amount to zoom in (positive) or out (negative). Suggested value is +/-10.
	 * @return an action
	 */
	public static Action createZoomCommand(QuPathGUI qupath, int zoomAmount) {
		var command = new ZoomCommand(qupath.viewerProperty(), zoomAmount);
		return ActionTools.createAction(command);
	}
	
	
	/**
	 * Create a stage to prompt the user to specify an annotation to add.
	 * @param qupath the qupath GUI
	 * @return a stage
	 */
	public static Stage createSpecifyAnnotationDialog(QuPathGUI qupath) {
		SpecifyAnnotationCommand pane = new SpecifyAnnotationCommand(qupath);
		var stage = new Stage();
		FXUtils.addCloseWindowShortcuts(stage);
		var scene = new Scene(pane.getPane());
		stage.setScene(scene);
		stage.setWidth(300);
		stage.setMinHeight(200);
		stage.setMinWidth(200);
		stage.setTitle(QuPathResources.getString("Commands.specifyAnnotation"));
		stage.initOwner(qupath.getStage());
		return stage;
	}
	
	
	
	/**
	 * Create a stage to display object descriptions.
	 * @param qupath the qupath GUI
	 * @return a stage
	 */
	public static Stage createObjectDescriptionsDialog(QuPathGUI qupath) {
		return ObjectDescriptionPane.createWindow(qupath);
	}
	
	
	/**
	 * Prompt to save the specified {@link ImageData}.
	 * @param qupath the QuPath GUI
	 * @param imageData the image data
	 * @param overwriteExisting whether to overwrite existing image data
	 * @return whether the save succeeded
	 */
	public static boolean promptToSaveImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData, boolean overwriteExisting) {
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.serializationError"));
			return false;
		}
		try {
			var project = qupath.getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry != null) {
				if (overwriteExisting || Dialogs.showConfirmDialog(
						QuPathResources.getString("Commands.saveChanges"),
						MessageFormat.format(
								QuPathResources.getString("Commands.saveChangesTo"),
								entry.getImageName()
						)
				)) {
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
						file = FileChoosers.promptToSaveFile(
								null,
								fileDefault,
						FileChoosers.createExtensionFilter(
								QuPathResources.getString("Commands.quPathSerializedData"),
								PathPrefs.getSerializationExtension()
						));
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
					file = FileChoosers.promptToSaveFile(
							null,
							new File(name),
							FileChoosers.createExtensionFilter(
									QuPathResources.getString("Commands.quPathSerializedData"),
									PathPrefs.getSerializationExtension()
							)
					);
				}
				if (file == null)
					return false;
				PathIO.writeImageData(file, imageData);
				return true;
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage(QuPathResources.getString("Commands.saveImageData"), e);
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	
	// TODO: Make the extension modifiable
	private static StringProperty defaultScreenshotExtension = PathPrefs.createPersistentPreference("defaultScreenshotExtension", "*.png");

	private static File lastSnapshotDirectory = null;

	/**
	 * Save an image snapshot after a specified delay, prompting the user to select the output file.
	 * <br/>
	 * The delay is used to work around the fact that making a snapshot immediately can sometimes result
	 * in undesirable UI elements still being visible, e.g. the menu item used to trigger the snapshot.
	 * <br/>
	 * See https://github.com/qupath/qupath/issues/1854
	 * @param qupath the {@link QuPathGUI} instance to snapshot
	 * @param type the snapshot type
	 * @param delayMillis the delay in milliseconds
	 */
	public static void saveSnapshotWithDelay(QuPathGUI qupath, GuiTools.SnapshotType type, long delayMillis) {
		CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, Platform::runLater)
				.execute(() -> saveSnapshot(qupath, type));
	}

	/**
	 * Save an image snapshot, prompting the user to select the output file.
	 * @param qupath the {@link QuPathGUI} instance to snapshot
	 * @param type the snapshot type
	 * @return true if a snapshot was saved, false otherwise
	 */
	public static boolean saveSnapshot(QuPathGUI qupath, GuiTools.SnapshotType type) {
		BufferedImage img = GuiTools.makeSnapshot(qupath, type);

		String defaultExtension = defaultScreenshotExtension.get();

		List<FileChooser.ExtensionFilter> extensionFilters = new ArrayList<>(Arrays.asList(
				FileChoosers.createExtensionFilter("PNG", "png"),
				FileChoosers.createExtensionFilter("JPEG", "jpg", "jpeg"),
				FileChoosers.createExtensionFilter("TIFF", "tif", "tiff")
		));
		FileChooser.ExtensionFilter selectedFilter = extensionFilters
				.stream()
				.filter(e -> defaultExtension == null ? false : e.getExtensions().contains(defaultExtension))
				.findFirst()
				.orElse(extensionFilters.get(0));
		if (!Objects.equals(selectedFilter, extensionFilters.get(0))) {
			extensionFilters.remove(selectedFilter);
			extensionFilters.add(0, selectedFilter);
		}

		// Choose a suitable initial directory
		var initialDirectory = lastSnapshotDirectory;
		if (initialDirectory == null || !initialDirectory.exists()) {
			var project = qupath.getProject();
			if (project != null) {
				var path = project.getPath();
				if (path != null) {
					if (Files.isDirectory(path))
						initialDirectory = path.toFile();
					else if (Files.isRegularFile(path))
						initialDirectory = path.getParent().toFile();
				}
			}
		}

		var chooser = FileChoosers.buildFileChooser()
				.extensionFilters(extensionFilters)
				.selectedExtensionFilter(selectedFilter)
				.initialDirectory(initialDirectory)
				.build();

		File fileOutput = chooser.showSaveDialog(qupath.getStage());
		if (fileOutput == null)
			return false;
		lastSnapshotDirectory = fileOutput.getParentFile();

		String ext = GeneralTools.getExtension(fileOutput).orElse(null);
		List<ImageWriter<BufferedImage>> compatibleWriters = ext == null ? Collections.emptyList() :
				ImageWriterTools.getCompatibleWriters(BufferedImage.class, ext);
		if (compatibleWriters.isEmpty()) {
            logger.error("No compatible image writers found for extension: {}", ext);
			return false;
		}

		// Loop through the writers and stop when we are successful
		for (var writer : compatibleWriters) {
			try {
				writer.writeImage(img, fileOutput.getAbsolutePath());
				String extChosen = chooser.getSelectedExtensionFilter().getExtensions().get(0);
				defaultScreenshotExtension.set(extChosen);
				return true;
			} catch (Exception e) {
                logger.error("Error saving snapshot {} to {}", type, fileOutput.getAbsolutePath(), e);
			}
		}
		return false;
	}
	
	/**
	 * Merge the currently-selected annotations for an image, replacing them with a single new annotation.
	 * @param imageData the image data
	 */
	public static void mergeSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		logger.debug("Merging selected annotations");
		QP.mergeSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.mergeSelectedAnnotations"),
				"mergeSelectedAnnotations()"
		));
	}

	
	/**
	 * Duplicate the selected annotations.
	 * @param imageData the image data
	 */
	public static void duplicateSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.duplicateAnnotations"));
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObjectTools.duplicateSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.duplicateSelectedAnnotations"),
				"duplicateSelectedAnnotations()"
		));
	}
	
	/**
	 * Copy the selected objects and add them to the plane currently active in the viewer.
	 * @param viewer the viewer that determines the current image and plane
	 * @implNote this command is scriptable, but will store the plane in the script (since 
	 *           there is not necessarily a 'current plane' when running a script without a viewer)
	 */
	public static void copySelectedAnnotationsToCurrentPlane(QuPathViewer viewer) {
		var imageData = viewer == null ? null : viewer.getImageData();
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.copySelectedAnnotations"));
			return;
		}
		var plane = viewer.getImagePlane();
		int z = plane.getZ();
		int t = plane.getT();
		QP.copySelectedAnnotationsToPlane(z, t);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.copySelectedAnnotations"),
				String.format("copySelectedAnnotationsToPlane(%d, %d)", z, t)
		));
	}

	/**
	 * Make an inverse annotation for the selected objects, storing the command in the history workflow.
	 * @param imageData the image data
	 * @see QP#makeInverseAnnotation(ImageData)
	 */
	public static void makeInverseAnnotation(ImageData<?> imageData) {
		if (imageData == null)
			return;
		logger.debug("Make inverse annotation");
		QP.makeInverseAnnotation(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.invertSelectedAnnotation"),
				"makeInverseAnnotation()"
		));
	}
	
	
	/**
	 * Show a dialog to track the viewed region of an image.
	 * @param qupath the QuPath GUI
	 */
	public static void showViewTracker(QuPathGUI qupath) {
		new ViewTrackerControlPane(qupath).run();
	}

	
	/**
	 * Combine the selected annotations for the specified hierarchy.
	 * @param imageData the image data to process
	 * @param op the {@link CombineOp} operation to apply
	 * @return true if changes were made, false otherwise
	 */
	public static boolean combineSelectedAnnotations(ImageData<?> imageData, RoiTools.CombineOp op) {
		// TODO: CONSIDER MAKING THIS SCRIPTABLE!
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.combineAnnotations"));
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
	 * @param hierarchy the hierarchy
	 * @param pathObjects the objects
	 * @param op how to combine RIOs
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
			newObject.setColor(pathObject.getColor());
		}

		// Remove previous objects
		hierarchy.removeObjects(pathObjects, true);
		if (newObject != null)
			hierarchy.addObject(newObject);
		return true;
	}
	
	/**
	 * Prompt to select objects according to their classifications.
	 * @param qupath the QuPath GUI
	 * @param imageData the image data
	 */
	public static void promptToSelectObjectsByClassification(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null)
			return;
		var pathClass = Dialogs.showChoiceDialog(
				QuPathResources.getString("Commands.selectObjects"),
				"",
				qupath.getAvailablePathClasses(),
				null
		);
		if (pathClass == null)
			return;
		selectObjectsByClassification(imageData, pathClass);
	}
	
	
	/**
	 * Prompt to edit the name/color of a class.
	 * @param pathClass the path class
	 * @return whether the edit succeeds.
	 */
	public static boolean promptToEditClass(final PathClass pathClass) {
		if (pathClass == null || pathClass == PathClass.NULL_CLASS)
			return false;

		boolean defaultColor = pathClass == null; // todo: this can never be true, so this method contains redundant code

		BorderPane panel = new BorderPane();

		BorderPane panelName = new BorderPane();
		String name;
		Color color;

		if (defaultColor) {
			name = QuPathResources.getString("Commands.defaultObjectColor");
			color = ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get());
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
		} else {
			name = pathClass.toString();
			if (name == null)
				name = "";
			color = ColorToolsFX.getPathClassColor(pathClass);		
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
		}

		panel.setTop(panelName);
		ColorPicker panelColor = new ColorPicker(color);

		panel.setCenter(panelColor);

		if (!Dialogs.showConfirmDialog(QuPathResources.getString("Commands.editClass"), panel))
			return false;

		Color newColor = panelColor.getValue();

		Integer colorValue = newColor.isOpaque() ? ColorToolsFX.getRGB(newColor) : ColorToolsFX.getARGB(newColor);
		if (defaultColor) {
			if (newColor.isOpaque())
				PathPrefs.colorDefaultObjectsProperty().set(colorValue);
			else
				PathPrefs.colorDefaultObjectsProperty().set(colorValue);
		}
		else {
			pathClass.setColor(colorValue);
		}
		return true;
	}

	
	/**
	 * Select objects by classification, logging the step (if performed) in the history workflow.
	 * @param imageData the {@link ImageData} containing objects to be selected
	 * @param pathClasses classifications that will result in an object being selected
	 * @return true if a selection command was run, false otherwise (e.g. if no pathClasses were specified)
	 */
	public static boolean selectObjectsByClassification(ImageData<?> imageData, PathClass... pathClasses) {
		var hierarchy = imageData.getHierarchy();
		if (pathClasses.length == 0) {
			logger.warn("Cannot select objects by classification - no classifications selected!");
			return false;
		}
		QP.selectObjectsByPathClass(hierarchy, pathClasses);
		var s = Arrays.stream(pathClasses)
				.map(p -> p == null || p == PathClass.NULL_CLASS ? "null" : "\"" + p.toString() + "\"").collect(Collectors.joining(", "));
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.selectObjectsByClassification"),
				"selectObjectsByClassification(" + s + ");"
		));
		return true;
	}
	
	
	/**
	 * Prompt to delete objects of a specified type, or all objects.
	 * @param imageData the image data
	 * @param cls the type of object (if null, all)
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
				message = QuPathResources.getString("Commands.deleteObject");
			else
				message = MessageFormat.format(QuPathResources.getString("Commands.deleteAllNObjects"), n);
			if (Dialogs.showYesNoDialog(QuPathResources.getString("Commands.deleteObjects"), message)) {
				hierarchy.clearAll();
				hierarchy.getSelectionModel().setSelectedObject(null);
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
						QuPathResources.getString("Commands.deleteAllObjects"),
						"removeAllObjects()"
				));
			}
			return;
		}
		
		// Handle clearing TMA grid
		if (TMACoreObject.class.equals(cls)) {
			if (hierarchy.getTMAGrid() != null) {
				if (Dialogs.showYesNoDialog(
						QuPathResources.getString("Commands.deleteObjects"),
						QuPathResources.getString("Commands.removeTmaGridQuestion")
				)) {
					hierarchy.setTMAGrid(null);
					
					PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
					if (selected instanceof TMACoreObject)
						hierarchy.getSelectionModel().setSelectedObject(null);

					imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
							QuPathResources.getString("Commands.removeTmaGrid"),
							"removeTMAGrid()"
					));
				}
				return;
			}
		}
		
		
		// Handle clearing objects of another specified type
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		if (pathObjects.isEmpty())
			return;
		int n = pathObjects.size();
		String message = n == 1 ?
				QuPathResources.getString("Commands.deleteOneObject") :
				MessageFormat.format(QuPathResources.getString("Commands.deleteNObjects"), n);
		if (Dialogs.showYesNoDialog(QuPathResources.getString("Commands.deleteObjects"), message)) {
			hierarchy.removeObjects(pathObjects, true);
			
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (cls == PathDetectionObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
						QuPathResources.getString("Commands.deleteDetections"),
						"removeDetections()"
				));
			else if (cls == PathAnnotationObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
						QuPathResources.getString("Commands.deleteAnnotations"),
						"removeAnnotations()"
				));
			else if (cls == TMACoreObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
						QuPathResources.getString("Commands.deleteTmaGrid"),
						"removeTMAGrid()"
				));
			else
				logger.warn("Cannot remove all objects for class {}", cls);
		}
	}



	/**
	 * Delete all objects touching the image boundary.
	 * @param imageData the image data
	 */
	public static void removeOnImageBounds(ImageData<?> imageData) {
		if (imageData == null) {
			logger.warn("No image available!");
			return;
		}
		QP.removeObjectsTouchingImageBounds(imageData, null);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.removeObjectsOnBoundary"),
				"removeObjectsTouchingImageBounds()"
		));
	}

	
	/**
	 * Reset QuPath's preferences, after confirming with the user.
	 * QuPath needs to be restarted for this to take effect.
	 * @return true if the preferences were reset, false otherwise
	 */
	public static boolean promptToResetPreferences() {
		if (Dialogs.showConfirmDialog(
				QuPathResources.getString("Commands.resetPreferences"),
				QuPathResources.getString("Commands.resetPreferencesConfirmation")
		)) {
			PathPrefs.resetPreferences();
			return true;
		}
		else
			logger.info("Reset preferences command skipped!");
		return false;
	}

	
	
	/**
	 * Set the downsample factor for the specified viewer.
	 * @param viewer the QuPath viewer
	 * @param downsample the new downsample
	 */
	public static void setViewerDownsample(QuPathViewer viewer, double downsample) {
		if (viewer != null)
			viewer.setDownsampleFactor(downsample);
	}
	
	
	/**
	 * Close the current project open in the {@link QuPathGUI}.
	 * @param qupath The QuPath GUI
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
		File dir = FileChoosers.promptForDirectory(QuPathResources.getString("Commands.selectEmptyDirectory"), null);
		if (dir == null)
			return false;
		if (!dir.isDirectory()) {
            logger.error("{} is not a valid project directory!", dir);
		}
		for (File f : dir.listFiles()) {
			if (!f.isHidden()) {
				logger.error("Cannot create project for non-empty directory {}", dir);
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.projectCreator"),
						QuPathResources.getString("Commands.projectDirectoryEmpty")
				);
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
		File fileProject = FileChoosers.promptForFile(
				QuPathResources.getString("Commands.chooseProjectFile"),
				FileChoosers.createExtensionFilter(QuPathResources.getString("Commands.quPathProjects"), ProjectIO.getProjectExtension())
		);
		if (fileProject != null) {
			try {
				Project<BufferedImage> project = ProjectIO.loadProject(fileProject, BufferedImage.class);
				qupath.setProject(project);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.loadProject"),
						MessageFormat.format(QuPathResources.getString("Commands.couldNotReadProject"), fileProject.getName())
				);
				logger.error(e.getLocalizedMessage(), e);
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
		FXUtils.addCloseWindowShortcuts(stage);
		stage.setMinHeight(200);
		stage.setMinWidth(200);
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
	 * @param signedDistances if true, use signed distances
	 */
	public static void distanceToAnnotations2D(ImageData<?> imageData, boolean signedDistances) {
		String title = signedDistances ?
				QuPathResources.getString("Commands.signedDistanceToAnnotations") :
				QuPathResources.getString("Commands.distanceToAnnotations");
		if (imageData == null) {
			GuiTools.showNoImageError(title);
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog(title, QuPathResources.getString("Commands.distanceToAnnotationsOnly2d"))) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		var result = Dialogs.showYesNoCancelDialog(title, QuPathResources.getString("Commands.splitMultiPartClassifications"));
		boolean doSplit = false;
		if (result == ButtonType.YES)
			doSplit = true;
		else if (result != ButtonType.NO)
			return;

		if (signedDistances) {
			DistanceTools.detectionToAnnotationDistancesSigned(imageData, doSplit);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.signedDistanceToAnnotations"),
					doSplit ? "detectionToAnnotationDistancesSigned(true)" : "detectionToAnnotationDistancesSigned(false)"
			));
		} else {
			DistanceTools.detectionToAnnotationDistances(imageData, doSplit);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.distanceToAnnotations"),
					doSplit ? "detectionToAnnotationDistances(true)" : "detectionToAnnotationDistances(false)"
			));
		}
	}
	
	/**
	 * Compute the distance between the centroids of all detections, for all available classifications.
	 * @param imageData the image data to process
	 */
	public static void detectionCentroidDistances2D(ImageData<?> imageData) {
		String title = QuPathResources.getString("Commands.detectionCentroidDistances");
		if (imageData == null) {
			GuiTools.showNoImageError(title);
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog(title, QuPathResources.getString("Commands.detectionCentroidDistancesOnly2d"))) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		var result = Dialogs.showYesNoCancelDialog(title, QuPathResources.getString("Commands.splitMultiPartClassifications"));
		boolean doSplit = false;
		if (result == ButtonType.YES)
			doSplit = true;
		else if (result != ButtonType.NO)
			return;
		
		DistanceTools.detectionCentroidDistances(imageData, doSplit);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.detectionCentroidDistances"),
				doSplit ? "detectionCentroidDistances(true)" : "detectionCentroidDistances(false)"
		));
	}
	
	
	/**
	 * Prompt to input the spacing for the grid lines optionally displayed on viewers.
	 * @param options the {@link OverlayOptions} that manage the grid lines.
	 */
	public static void promptToSetGridLineSpacing(OverlayOptions options) {
		GridLines gridLines = options.getGridLines();
		
		ParameterList params = new ParameterList()
				.addDoubleParameter("hSpacing", QuPathResources.getString("Commands.horizontalSpacing"), gridLines.getSpaceX())
				.addDoubleParameter("vSpacing", QuPathResources.getString("Commands.verticalSpacing"), gridLines.getSpaceY())
				.addBooleanParameter("useMicrons", QuPathResources.getString("Commands.useMicrons"), gridLines.useMicrons());
		
		if (!GuiTools.showParameterDialog(QuPathResources.getString("Commands.setGridSpacing"), params))
			return;
		
		gridLines = new GridLines();
		gridLines.setSpaceX(params.getDoubleParameterValue("hSpacing"));
		gridLines.setSpaceY(params.getDoubleParameterValue("vSpacing"));
		gridLines.setUseMicrons(params.getBooleanParameterValue("useMicrons"));
		
		options.gridLinesProperty().set(gridLines);
	}
	
	
	
	/**
	 * Request the current user directory, optionally prompting the user to request a directory if none is available.
	 * @param promptIfMissing whether to prompt the user if the directory is not set yet
	 * @return the user directory, or null if none exists and the user did not create one
	 */
	public static File requestUserDirectory(boolean promptIfMissing) {
		
		var manager = UserDirectoryManager.getInstance();
		var pathUser = manager.getUserPath();
		if (pathUser != null && Files.isDirectory(pathUser))
			return pathUser.toFile();
		
		if (!promptIfMissing)
			return null;
		
		// Prompt to create an extensions directory
		File dirDefault = PathPrefs.getDefaultQuPathUserDirectory().toFile();
		String msg;
		if (dirDefault.exists()) {
			msg = MessageFormat.format(
					QuPathResources.getString("Commands.alreadyExists"),
					dirDefault.getAbsolutePath()
			);
		} else {
			msg = MessageFormat.format(
					QuPathResources.getString("Commands.createNewUserDirectory"),
					dirDefault.getAbsolutePath()
			);
		}
		
		ButtonType btUseDefault = new ButtonType(QuPathResources.getString("Commands.useDefault"), ButtonData.YES);
		ButtonType btChooseDirectory = new ButtonType(QuPathResources.getString("Commands.chooseDirectory"), ButtonData.NO);
		ButtonType btCancel = new ButtonType(QuPathResources.getString("Commands.cancel"), ButtonData.CANCEL_CLOSE);
		
		var result = Dialogs.builder()
			.title(QuPathResources.getString("Commands.chooseUserDirectory"))
			.headerText(QuPathResources.getString("Commands.noUserDirectorySet"))
			.contentText(msg)
			.buttons(btUseDefault, btChooseDirectory, btCancel)
			.showAndWait()
			.orElse(btCancel);
			
		if (result == btCancel) {
			logger.info("Dialog cancelled - no user directory set");
			return null;
		}
		if (result == btUseDefault) {
			if (!dirDefault.exists() && !dirDefault.mkdirs()) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.extensionError"),
						MessageFormat.format(
								QuPathResources.getString("Commands.unableToCreateDirectory"),
								dirDefault.getAbsolutePath()
						)
				);
				return null;
			}
			pathUser = dirDefault.toPath();
		} else {
			File dirUser = FileChoosers.promptForDirectory(QuPathResources.getString("Commands.setUserDirectory"), dirDefault);
			if (dirUser == null) {
				logger.info("No QuPath user directory set!");
				return null;
			}
			pathUser = dirUser.toPath();
		}
		manager.setUserPath(pathUser);
		return pathUser.toFile();
	}
	
	/**
	 * Reload the specified image data from a previously saved version,if available.
	 * @param qupath the QuPath GUI
	 * @param imageData the image data
	 */
	public static void reloadImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.reloadData"));
			return;
		}
		// TODO: Support loading from a project as well
		
		var viewer = qupath.getAllViewers().stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);
		if (viewer == null) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.reloadData"),
					QuPathResources.getString("Commands.imageDataNotFound")
			);
			return;
		}

		// Check if we have a saved file
		File savedFile = imageData.getLastSavedPath() == null ? null : new File(imageData.getLastSavedPath());
		if (savedFile == null || !savedFile.isFile()) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.reload"),
					QuPathResources.getString("Commands.noSavedDataFound")
			);
			return;
		}
		
		if (Dialogs.showConfirmDialog(
				QuPathResources.getString("Commands.reload"),
				QuPathResources.getString("Commands.reloadLastSavedVersion")
		)) {
			try {
				var project = qupath.getProject();
				var entry = project == null ? null : project.getEntry(imageData);
				ImageData<BufferedImage> imageDataNew;
				if (entry != null) {
					logger.info("Reloading image data from project entry: {}", entry);
					imageDataNew = entry.readImageData();
				} else {
					logger.info("Reverting to last saved version: {}", savedFile.getAbsolutePath());
					imageDataNew = PathIO.readImageData(savedFile, imageData.getServer());
				}
				viewer.setImageData(imageDataNew);
			} catch (Exception e) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Commands.reload"),
						MessageFormat.format(
								QuPathResources.getString("Commands.errorRevertingToSavedFile"),
								e.getLocalizedMessage()
						)
				);
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
		
		listView.setTooltip(new Tooltip(QuPathResources.getString("Commands.chooseShapeFeatures")));
		var label = new Label(QuPathResources.getString("Commands.addShapeFeaturesToSelectedObjects"));
		label.setTextAlignment(TextAlignment.CENTER);
		label.setPadding(new Insets(10));
		pane.setTop(label);
		
		var btnSelectAll = new Button(QuPathResources.getString("Commands.selectAll"));
		btnSelectAll.setOnAction(e -> listView.getCheckModel().checkAll());
		var btnSelectNone = new Button(QuPathResources.getString("Commands.selectNone"));
		btnSelectNone.setOnAction(e -> listView.getCheckModel().clearChecks());
		
		btnSelectAll.setMaxWidth(Double.MAX_VALUE);
		btnSelectNone.setMaxWidth(Double.MAX_VALUE);
		
		pane.setBottom(GridPaneUtils.createColumnGrid(btnSelectAll, btnSelectNone));
		
		var dialog = Dialogs.builder()
				.title(QuPathResources.getString("Commands.shapeFeatures"))
				.content(pane)
				.modality(Modality.NONE)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.build();
		
		var btnApply = (Button)dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		btnApply.disableProperty().bind(qupath.imageDataProperty().isNull());
		
		btnApply.setOnAction(e -> requestShapeFeatures(qupath.getImageData(), listView.getCheckModel().getCheckedItems()));
		dialog.show();
	}
		
		
	private static void requestShapeFeatures(ImageData<?> imageData, Collection<ShapeFeatures> features) {
		if (imageData == null)
			return;
		var featureArray = features.toArray(ShapeFeatures[]::new);
		if (featureArray.length == 0)
			return;
		Collection<PathObject> selected = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			Dialogs.showWarningNotification(
					QuPathResources.getString("Commands.shapeFeatures"),
					QuPathResources.getString("Commands.noObjectsSelected")
			);
		} else {
			selected = new ArrayList<>(selected);			
			String featureString = Arrays.stream(featureArray).map(f -> "\"" + f.name() + "\"").collect(Collectors.joining(", "));
			QP.addShapeMeasurements(imageData, selected, featureArray);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.addShapeMeasurements"),
					String.format("addShapeMeasurements(%s)", featureString)
			));
			
			if (selected.size() == 1)
				Dialogs.showInfoNotification(
						QuPathResources.getString("Commands.shapeFeatures"),
						QuPathResources.getString("Commands.shapeFeaturesCalculatedOneObject")
				);
			else
				Dialogs.showInfoNotification(
						QuPathResources.getString("Commands.shapeFeatures"),
						MessageFormat.format(
								QuPathResources.getString("Commands.shapeFeaturesCalculatedNObjects"),
								selected.size()
						)
				);
		}
	}
	

	
	/**
	 * Convert detection objects to point annotations based upon their ROI centroids.
	 * @param imageData the image data to process
	 * @param preferNucleus if true, use a nucleus ROI for cell objects (if available
	 */
	public static void convertDetectionsToPoints(ImageData<?> imageData, boolean preferNucleus) {
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.convertDetectionsToPoints"));
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.detectionsToPoints"),
					QuPathResources.getString("Commands.noDetectionsFound")
			);
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
		String message = pathObjects.size() == 1 ?
				QuPathResources.getString("Commands.deleteDetectionAfterConverting") :
				MessageFormat.format(QuPathResources.getString("Commands.deleteNDetectionAfterConverting"), pathObjects.size());
		var button = Dialogs.showYesNoCancelDialog(QuPathResources.getString("Commands.detectionsToPoints"), message);
		if (button == ButtonType.CANCEL)
			return;
		
		boolean	deleteDetections = button == ButtonType.YES;
		PathObjectTools.convertToPoints(hierarchy, pathObjects, preferNucleus, deleteDetections);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.convertDetectionsToPoints"),
				"convertDetectionsToPoints()"
		));
		if (deleteDetections) {
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					QuPathResources.getString("Commands.deleteDetections"),
					"removeDetections()"
			));
		}
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
				.toList();
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.simplifyAnnotations"),
					QuPathResources.getString("Commands.noUnlockedShapeAnnotationsSelected")
			);
			return;
		}

		String input = Dialogs.showInputDialog(
				QuPathResources.getString("Commands.simplifyShape"),
				QuPathResources.getString("Commands.setAltitudeThreshold"),
				Double.toString(altitudeThreshold)
		);
		if (input == null || input.trim().isEmpty())
			return;
		try {
			altitudeThreshold = Double.parseDouble(input.trim());
		} catch (NumberFormatException e) {
			logger.error("Could not parse altitude threshold from {}", input);
			return;
		}
		if (altitudeThreshold <= 0) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.simplifyShape"),
					QuPathResources.getString("Commands.amplitudeThresholdGreaterThanZero")
			);
			return;
		}
		
		long startTime = System.currentTimeMillis();
		QP.simplifySpecifiedAnnotations(pathObjects, altitudeThreshold);
		long endTime = System.currentTimeMillis();
        logger.debug("Shapes simplified in {} ms", endTime - startTime);
		hierarchy.fireObjectsChangedEvent(hierarchy, pathObjects);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.simplifySelectedAnnotations"),
				"simplifySelectedAnnotations(" + altitudeThreshold + ")"
		));
	}
	
	/**
	 * Select all the objects on the current plane of the viewer.
	 * @param viewer the QuPath GUI
	 */
	public static void selectObjectsOnCurrentPlane(QuPathViewer viewer) {
		if (viewer == null)
			return;
		ImageData<?> imageData = viewer.getImageData();
		if (imageData == null)
			return;
		
		int z = viewer.getZPosition();
		int t = viewer.getTPosition();
		QP.selectObjectsByPlane(z, t);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.selectObjectsOnPlane"),
				String.format("selectObjectsByPlane(%d, %d)", z, t)
		));
	}
	

	/**
	 * Select all objects (excluding the root object) in the imageData.
	 * 
	 * @param imageData the image data
	 */
	public static void selectAllObjects(final ImageData<?> imageData) {
		// Select all objects
		QP.selectAllObjects(imageData.getHierarchy(), false);
		
		// Add this step to the history workflow
		Map<String, String> map = new HashMap<>();
		map.put("includeRootObject", "false");
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.selectAllObjects"),
				map,
				"selectAllObjects(false)"
		));
	}


	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData the image data
	 * @param cls the type of object
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
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.selectObjectsByClass"),
				params,
				method
		);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Reset the selection for an image.
	 * @param imageData the image data
	 */
	public static void resetSelection(final ImageData<?> imageData) {
		if (imageData == null) {
			logger.warn("No image available!");
			return;
		}
		
		// Do the action reset
		imageData.getHierarchy().getSelectionModel().clearSelection();
		
		// Log the appropriate command
		String method = "resetSelection()";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep(QuPathResources.getString("Commands.resetSelection"), method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData the image data
	 * @param cls the type of object
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
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep(
				QuPathResources.getString("Commands.resetClassifications"),
				params,
				method
		);
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
		FXUtils.addCloseWindowShortcuts(dialog);
		dialog.setMinHeight(200);
		dialog.setMinWidth(200);
		dialog.initOwner(qupath.getStage());
		dialog.setTitle(QuPathResources.getString("Commands.workflowViewer"));
		Pane pane = view.getPane();
		dialog.setScene(new Scene(pane, 400, 400));
		return dialog;
	}
	
	
	/**
	 * Show the QuPath script editor with a script corresponding to the command history of a specified image.
	 * @param qupath the QuPath GUI
	 * @param imageData the image data
	 */
	public static void showWorkflowScript(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null) {
			GuiTools.showNoImageError(QuPathResources.getString("Commands.showWorkflowScript"));
			return;
		}
		WorkflowCommandLogView.showScript(qupath.getScriptEditor(), imageData.getHistoryWorkflow());
	}
	
	
	/**
	 * Show the script editor, or bring the window to the front if it is already open.
	 * @param qupath the QuPath GUI
	 */
	public static void showScriptEditor(QuPathGUI qupath) {
		var scriptEditor = qupath.getScriptEditor();
		if (scriptEditor == null) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.scriptEditor"),
					QuPathResources.getString("Commands.noScriptEditorFound")
			);
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
	 * @param qupath the QuPath GUI
	 * @return the dialog stage
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
	 * Refresh object IDs to ensure uniqueness.
	 * @param imageData the image data
	 * @param duplicatesOnly only refresh IDs that are duplicates of other IDs
	 */
	public static void refreshObjectIDs(ImageData<?> imageData, boolean duplicatesOnly) {
		 if (imageData == null)
			 return;
		 var hierarchy = imageData.getHierarchy();
		 if (duplicatesOnly) {
			 QP.refreshDuplicateIDs(hierarchy);
			 imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					 QuPathResources.getString("Commands.refreshDuplicateIDs"),
					 "refreshDuplicateIDs()"
			 ));
		 } else {
			 QP.refreshIDs(hierarchy);
			 imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					 QuPathResources.getString("Commands.refreshDuplicateIDs"),
					 "refreshIDs()"
			 ));
		 }
	}
	
	
	/**
	 * Show a dialog to import object(s) from a file.
	 * @param qupath the qupath GUI
	 * @param imageData the image data
	 */
	public static void runObjectImport(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		InteractiveObjectImporter.promptToImportObjectsFromFile(imageData, null);
	}
	
	/**
	 * Attempt to copy selected objects to the system clipboard, if available
	 * @param imageData the image data
	 */
	public static void copySelectedObjectsToClipboard(ImageData<BufferedImage> imageData) {
		var selected = imageData == null ? null : imageData.getHierarchy().getSelectionModel().getSelectedObjects();
		copyObjectsToClipboard(selected);
	}
	
	
	/**
	 * Attempt to annotation objects to the system clipboard, if available
	 * @param imageData the image data
	 */
	public static void copyAnnotationsToClipboard(ImageData<BufferedImage> imageData) {
		var annotations = imageData == null ? null : imageData.getHierarchy().getAnnotationObjects();
		copyObjectsToClipboard(annotations);		
	}
	
	
	private static void copyObjectsToClipboard(Collection<? extends PathObject> pathObjects) {
		if (pathObjects == null || pathObjects.isEmpty())
			return;
		
		int max = PathPrefs.maxObjectsToClipboardProperty().get();
		if (max >= 0 && max < pathObjects.size()) {
			Dialogs.showWarningNotification(
					QuPathResources.getString("Commands.copyObjectsToClipboard"),
					MessageFormat.format(
							QuPathResources.getString("Commands.numberOfSelectedObjectsExceedsMaximum"),
							pathObjects.size(),
							max
					)
			);
			return;
		}
		
		String json;
		try {
			if (pathObjects.size() == 1) {
				var gson = GsonTools.getInstance(true);
				json = gson.toJson(pathObjects.iterator().next());
			} else {
				// We could use pretty printing with a minimal indent
				// This avoids increasing the size enormously, while 
				// also avoiding the use of very long individual lines 
				// (which cause major problems if pasted into the script editor)
				var gson = GsonTools.getInstance(false);
				var writer = new StringWriter();
				var jsonWriter = gson.newJsonWriter(writer);
				jsonWriter.setIndent(" ");
				var features = FeatureCollection.wrap(pathObjects);
				gson.toJson(features, features.getClass(), jsonWriter);
				json = writer.toString();
			}
		} catch (Exception e) {
			return ;
		}
		
		var clipboard = Clipboard.getSystemClipboard();
		var content = new ClipboardContent();
		content.putString(json);
		var format = InteractiveObjectImporter.getGeoJsonDataFormat();
		if (format != null)
			content.put(format, json);
		clipboard.setContent(content);
	}
	
	
	/**
	 * Attempt to paste objects from the system clipboard to the current image, if available; 
	 * otherwise, check for text on the clipboard and paste it into a new script editor tab
	 * @param qupath the qupath GUI
	 * @param addToCurrentPlane if true, add the objects to the plane currently visible in the viewer 
	 *                          (and don't show any text if objects can't be found)
	 */
	public static void pasteFromClipboard(QuPathGUI qupath, boolean addToCurrentPlane) {
		var viewer = qupath.getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		// If we have an image and objects on the clipboard, paste them
		if (imageData != null) {
			try {
				var pathObjects = InteractiveObjectImporter.readObjectsFromClipboard(imageData);
				// Make sure all the objects are on the current plane if needed
				if (addToCurrentPlane) {
					var plane = viewer.getImagePlane();
					pathObjects = pathObjects.stream().map(p -> PathObjectTools.updatePlane(p, plane, false, true)).toList();
				}
				if (!pathObjects.isEmpty()) {
					InteractiveObjectImporter.promptToImportObjects(imageData.getHierarchy(), pathObjects);
					return;
				}
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		}
		// No objects - paste text in the script editor instead
		var text = (String)Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT);
		if (!addToCurrentPlane && text != null && !text.isEmpty() && qupath.getScriptEditor() != null) {
			qupath.getScriptEditor().showScript(QuPathResources.getString("Commands.clipboardText"), text);
		}
	}

	/**
	 * Show a dialog to export object(s) to a GeoJSON file.
	 * @param qupath the QuPath GUI
	 * @param imageData the image data
	 */
	public static void runGeoJsonObjectExport(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		try {
			ExportObjectsCommand.runGeoJsonExport(qupath);
		} catch (IOException e) {
			Dialogs.showErrorNotification(QuPathResources.getString("Commands.exportError"), e.getLocalizedMessage());
		}
	}
}
