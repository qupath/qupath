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

package qupath.process.gui.commands;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ij.IJ;
import ij.ImagePlus;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command to help export training regions as labelled images to train AI algorithms elsewhere.
 * <p>
 * Note: This is a work in progress and needs to be revised to improve flexibility.
 * 
 * @author Pete Bankhead
 *
 */
public class ExportTrainingRegionsCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(ExportTrainingRegionsCommand.class);
	
	private final QuPathGUI qupath;
	private Stage stage;
	private TrainingRegionExporter exporter;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public ExportTrainingRegionsCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
//		if (stage == null) {
			stage = new Stage();
			stage.initOwner(qupath.getStage());
			stage.setTitle("Export training");
			exporter = new TrainingRegionExporter(qupath);
			stage.setScene(new Scene(exporter.getPane()));
//		}
		stage.show();
	}
	
	
	static class TrainingRegionExporter {
		
		private QuPathGUI qupath;
		private GridPane pane;
		
		private File dirExport = null;
		
		private ExecutorService executorService;
		
		private StringProperty description;
		private StringProperty exportResolutionString;
		private StringProperty classificationLabels;
		private BooleanProperty exportBoundaries;
		private BooleanProperty useMicronsPerPixel;
		private BooleanProperty exportParallel;
		private BooleanProperty useTrainTestMetadataValue;
		private DoubleProperty progressProperty;
		
		private boolean requestCancel = false;
		
		TrainingRegionExporter(QuPathGUI qupath) {
			this.qupath = qupath;
			init();
		}
		
		
		void cancelTasks() {
			this.requestCancel = true;
		}
		
		
		private void init() {
			int row = 0;
			pane = new GridPane();
			pane.setPadding(new Insets(10));
			pane.setVgap(5);
			pane.setHgap(5);
			
			executorService = qupath.createSingleThreadExecutor(this);
			
			// Export resolution (microns per pixel)
			TextField tfResolution = new TextField("1");
			exportResolutionString = tfResolution.textProperty();
			Label labelResolution = new Label("Export resolution");
			labelResolution.setLabelFor(tfResolution);
			tfResolution.setTooltip(new Tooltip("Define export resolution, in " + GeneralTools.micrometerSymbol() + " per pixel or in terms of downsampling"));
			
			// Allow to switch between microns per pixel & downsamples
			RadioButton rbMicrons = new RadioButton(GeneralTools.micrometerSymbol() + " per px");
			RadioButton rbDownsample = new RadioButton("Downsample");
			ToggleGroup group = new ToggleGroup();
			group.getToggles().setAll(rbMicrons, rbDownsample);
			group.selectToggle(rbMicrons);
			useMicronsPerPixel = rbMicrons.selectedProperty();
			rbMicrons.setTooltip(new Tooltip("Interpret the resolution as " + GeneralTools.micrometerSymbol() + " per pixel"));
			rbDownsample.setTooltip(new Tooltip("Interpret the resolution as a downsample factor (should be >= 1)"));
			
			pane.add(labelResolution, 0, row);
			pane.add(tfResolution, 1, row);
			row++;
			pane.add(rbMicrons, 0, row);
			pane.add(rbDownsample, 1, row);
			row++;
			
			// Labels & classifications
			TextArea textLabels = new TextArea();
			textLabels.setPrefRowCount(8);
			classificationLabels = textLabels.textProperty();
			updateClassificationLabels(qupath.getAvailablePathClasses());
			textLabels.setTooltip(new Tooltip("Classification labels in the exported labelled image -\nthis can be edited to assign multiple classifications to the same label"));
			
			Button btnResetClassifications = new Button("Reset all");
			btnResetClassifications.setOnAction(e -> updateClassificationLabels(qupath.getAvailablePathClasses()));
			btnResetClassifications.setMaxWidth(Double.MAX_VALUE);
			Button btnUpdateClassifications = new Button("From project");
			btnUpdateClassifications.setOnAction(e -> updateClassificationLabelsFromProject());
			btnUpdateClassifications.setMaxWidth(Double.MAX_VALUE);

			btnResetClassifications.setTooltip(new Tooltip("Reset the classification labels to the defaults shown under the 'Annotations' tab"));
			btnUpdateClassifications.setTooltip(new Tooltip("Update classification labels based on the classifications actually found within all images of the project"));

			pane.add(textLabels, 0, row, 2, 1);
			row++;
			GridPane.setHgrow(btnResetClassifications, Priority.ALWAYS);
			GridPane.setHgrow(btnUpdateClassifications, Priority.ALWAYS);
			pane.add(btnResetClassifications, 0, row);
			pane.add(btnUpdateClassifications, 1, row);
			row++;
						
			// TODO: Export boundaries channels
//			CheckBox cbExportBoundaries = new CheckBox("Export boundary channels");
//			exportBoundaries = cbExportBoundaries.selectedProperty();
//			pane.add(cbExportBoundaries, 0, row, 2, 1);
//			row++;
			
			// TODO: Export entire image, or only classified as region
			
			// TODO: Export file format (PNG or ImageJ TIFF)
			
			// Optionally add a description
			Label labelDescription = new Label("Description (optional)");
			TextArea textDescription = new TextArea();
			textDescription.setPrefRowCount(4);
			description = textDescription.textProperty();
			labelDescription.setLabelFor(textDescription);
			
			textDescription.setTooltip(new Tooltip("Optionally add a free-text description along with the exported JSON file"));
			
			pane.add(labelDescription, 0, row, 2, 1);
			row++;
			pane.add(textDescription, 0, row, 2, 1);
			row++;
			
			// Split by metadata key
			CheckBox cbSplitByMetadata = new CheckBox("Split by train/validation/test metadata");
			useTrainTestMetadataValue = cbSplitByMetadata.selectedProperty();
			cbSplitByMetadata.setTooltip(new Tooltip("Put exported images into separate sub-directories according to the metadata value " + SplitProjectTrainingCommand.TRAIN_VALIDATION_TEST_METADATA_KEY));
			pane.add(cbSplitByMetadata, 0, row, 2, 1);
			row++;
			
			// Export in parallel... maybe
			CheckBox cbParallelExport = new CheckBox("Parallel export");
			exportParallel = cbParallelExport.selectedProperty();
			cbParallelExport.setTooltip(new Tooltip("Export regions for multiple images in parallel (a bad idea if the data files are huge!)"));
			pane.add(cbParallelExport, 0, row, 2, 1);
			row++;
			
			// Export all
			Button btnExport = new Button("Export");
			btnExport.setMaxWidth(Double.MAX_VALUE);
			btnExport.setTooltip(new Tooltip("Select export directory & export training images"));
			pane.add(btnExport, 0, row, 2, 1);
			row++;
			
			ProgressBar progressBar = new ProgressBar(0.0);
			progressProperty = progressBar.progressProperty();
			progressBar.setTooltip(new Tooltip("Current export progress bar"));
			pane.add(progressBar, 0, row, 2, 1);
			progressBar.setMaxWidth(Double.MAX_VALUE);
			row++;
			
			// Behave a bit differently if running
			BooleanBinding isRunning = progressProperty.isNotEqualTo(0).and(progressProperty.isNotEqualTo(1));
			btnExport.setOnAction(e -> {
				if (isRunning.get())
					requestCancel = true;
				else
					doExport();
			});
			btnExport.textProperty().bind(Bindings.createStringBinding(() -> {
				if (isRunning.get())
					return "Cancel";
				else
					return "Export";
			}, isRunning));
			
			btnExport.disableProperty().bind(
					qupath.projectProperty().isNull());

		}
		
		/**
		 * Loop through project entries & add all represented classifications.
		 */
		void updateClassificationLabelsFromProject() {
			Project<?> project = qupath.getProject();
			if (project == null) {
				updateClassificationLabels(Collections.emptyList());
			}
			Set<PathClass> set = new TreeSet<>();
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				if (!entry.hasImageData())
					continue;
				try {
					PathObjectHierarchy hierarchy = entry.readHierarchy();
					int nullCount = 0;
					for (PathObject annotation : hierarchy.getAnnotationObjects()) {
						if (annotation.getPathClass() == null)
							nullCount++;
						else
							set.add(annotation.getPathClass());					
					}
					if (nullCount > 0) {
						logger.warn("{} contains {} annotations without classification - these will be skipped!", entry.getImageName(), nullCount);
					}
				} catch (IOException e) {
					logger.error("Error reading hierarchy", e);
				}
			}
			updateClassificationLabels(set);
		}

		/**
		 * Update classification labels string based on a specified list of {@code PathClass} objects.
		 * @param pathClasses
		 */
		void updateClassificationLabels(final Collection<PathClass> pathClasses) {
			String s = "";
			int i = 1;
			// Handle 'regions' separately
			PathClass regionClass = PathClassFactory.getPathClass(StandardPathClasses.REGION);
			for (PathClass pathClass : pathClasses) {
				if (pathClass == regionClass)
					continue;
				String name = pathClass == null ? null : pathClass.getName();
				if (name == null)
					continue;
				s += name + ": " + i + "\n";
				i++;
			}
			classificationLabels.set(s);
		}
		
		
		void doExport() {
			Project<BufferedImage> project = qupath.getProject();
			if (project == null) {
				return;
			}
			
			// Request export directory from the user
			File dir = promptForDirectory();
			if (dir == null)
				return;
			
			// Figure out mapping between PathClass names & labels
			Map<String, Integer> mapLabels = new LinkedHashMap<>();
			for (String line : classificationLabels.get().split("\n")) {
				int ind = line.lastIndexOf(":");
				String name;
				if (ind < 0)
					name = "NOT ANNOTATED";
				else
					name = line.substring(0, ind);
//				PathClass pathClass = PathClassFactory.getPathClass(line.substring(0, ind));
				String labelText = line.substring(ind+1).trim();
				try {
					Integer label = Integer.parseInt(labelText);					
					if (label > 255)
						throw new IllegalArgumentException("Label value " + label + " - should be <= 255");
					mapLabels.put(name, label);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Export error", "Invalid label '" + labelText + "' for " + name + " - all labels must be integers <= 255!");
					return;
				}
			}
			
			// Create a color model for easier visualization
			// Also figure out mapping to colors for painting
			int[] cmap = new int[256];
			Map<String, Color> mapLabelColor = new LinkedHashMap<>();
			List<PathClassDisplay> display = new ArrayList<>();
			for (Entry<String, Integer> entry : mapLabels.entrySet()) {
				int val = entry.getValue();
				mapLabelColor.put(entry.getKey(), new Color(val, val, val));
				// Set color for color model
				PathClass pathClass = PathClassFactory.getPathClass(entry.getKey());
				cmap[val] = pathClass.getColor();
				display.add(new PathClassDisplay(entry.getKey(), val, pathClass.getColor()));
			}
			IndexColorModel colorModel = new IndexColorModel(8, 256, cmap, 0, false, -1, DataBuffer.TYPE_BYTE);
			
			// Parse resolution - either downsample value, or microns per pixel
			Number resolution = null;
			try {
				resolution = NumberFormat.getInstance().parse(exportResolutionString.get());
			} catch (Exception e) {
				Dialogs.showErrorMessage("Export training", "Cannot part resolution from " + exportResolutionString.get() + " - must be a number!");
				return;
			}
			boolean useMPP = useMicronsPerPixel.get();
			
			// Find all project entries with associated data files
			List<ProjectImageEntry<BufferedImage>> entries = project.getImageList().stream().filter(entry -> entry.hasImageData()).collect(Collectors.toList());
			
			// Write JSON file with key
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Map<String, Object> map = new LinkedHashMap<>();
			if (useMPP)
				map.put("requestedPixelSizeMicrons", resolution.doubleValue());
			else
				map.put("downsample", resolution.doubleValue());
			if (!description.get().trim().isEmpty())
				map.put("description", description.get());
			map.put("classifications", display);
			File fileJSON = new File(dir, "training.json");
			try (PrintWriter writer = new PrintWriter(fileJSON)) {
				writer.print(gson.toJson(map));
			} catch (FileNotFoundException e) {
				logger.error("Unable to write JSON file " + fileJSON, e);
			}
			
			double resolutionValue = resolution.doubleValue();
			int target = entries.size();
			AtomicInteger nCompleted = new AtomicInteger(0);
			requestCancel = false;
			boolean splitByMetadata = useTrainTestMetadataValue.get();
			executorService.submit(() -> {
				// Do export in parallel, if requested
				Stream<ProjectImageEntry<BufferedImage>> stream = entries.stream();
				if (exportParallel.get())
					stream = stream.parallel();
				stream.forEach(entry -> {
					try {
						if (!requestCancel) {
							File dirOutput = dir;
							// Split using the metadata flag, if required
							if (splitByMetadata) {
								String value = entry.getMetadataValue(SplitProjectTrainingCommand.TRAIN_VALIDATION_TEST_METADATA_KEY);
								if (value == null) {
									value = SplitProjectTrainingCommand.VALUE_NONE;
								}
								dirOutput = new File(dir, value);
								if (!dirOutput.exists())
									dirOutput.mkdir();
							}
							exportLabelledImages(project, entry, dirOutput, useMPP, resolutionValue, mapLabelColor, colorModel);
						}
					} catch (Exception e) {
						logger.error("Error exporting image labels", e);
					} finally {
						Platform.runLater(() -> progressProperty.set(nCompleted.incrementAndGet()/(double)target));
					}
				});
			});
		}
		
		
		
		static class PathClassDisplay {
			
			public final String name;
			public final int label;
			public final RGB display;
			
			PathClassDisplay(final String name, final int label, final int rgb) {
				this.name = name;
				this.label = label;
				this.display = new RGB(rgb);
			}
			
			static class RGB {
				
				public final int red, green, blue;
				
				RGB(int val) {
					this.red = ColorTools.red(val);
					this.green = ColorTools.green(val);
					this.blue = ColorTools.blue(val);
				}
				
			}
			
		}
		
	
		
		
		void exportLabelledImages(final Project<BufferedImage> project, final ProjectImageEntry<BufferedImage> entry,
				final File dirOutput, final boolean useMPP, final double resolution, final Map<String, Color> mapLabelColor, final IndexColorModel colorModel) {
			
			
			// Read ImageData - be sure to get server path from the project
			if (!entry.hasImageData())
				return;
			ImageData<BufferedImage> imageData;
			try {
				imageData = entry.readImageData();
			} catch (IOException e) {
				logger.error("Unable to read ImageData for " + entry.getImageName(), e);
				return;
			}
			ImageServer<BufferedImage> server = imageData.getServer();
			PathObjectHierarchy hierarchy = imageData.getHierarchy();

			// Determine resolution
			double downsample;
			if (useMPP)
				downsample = resolution / server.getPixelCalibration().getAveragedPixelSizeMicrons();
			else
				downsample = resolution;
			
			// Split by region & non-region classified annotations
			PathClass regionClass = PathClassFactory.getPathClass(StandardPathClasses.REGION);
			List<PathObject> regionAnnotations = hierarchy.getAnnotationObjects().stream().filter(p -> p.getPathClass() == regionClass).collect(Collectors.toList());
			List<PathObject> otherAnnotations = hierarchy.getAnnotationObjects().stream().filter(p -> p.getPathClass() != regionClass && p.getROI().isArea()).collect(Collectors.toList());

			// Sort by area - we want to annotate largest regions first
			otherAnnotations.sort((a, b) -> -Double.compare(a.getROI().getArea(), b.getROI().getArea()));
						
			// If we don't have any regions, try full image
			if (regionAnnotations.isEmpty())
				regionAnnotations.add(PathObjects.createAnnotationObject(
						ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane())));
			
			// Loop through region annotations
			for (PathObject annotation : regionAnnotations) {
			    // Read the TMA core - we take care of downsampling ourselves afterwards
				RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, annotation.getROI());
			    BufferedImage img;
				try {
					img = server.readBufferedImage(request);
				} catch (IOException e1) {
					logger.error("Error exporting " + request, e1);
					continue;
				}

			    // Create the mask
			    int w = img.getWidth();
			    int h = img.getHeight();

			    // Create a temporary image for painting
			    BufferedImage imgTemp = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

			    Graphics2D g2d = imgTemp.createGraphics();
			    
			    // Fill in background, if available
			    Color colorBackground = mapLabelColor.getOrDefault("NOT ANNOTATED", null);
			    if (colorBackground != null) {
			    	g2d.setColor(colorBackground);
			    	g2d.fillRect(0, 0, w, h);
			    }
			    
			    g2d.scale(1.0/downsample, 1.0/downsample);
			    g2d.translate(-request.getX(), -request.getY());
			    int c = 0;
			    for (PathObject other : otherAnnotations) {
			        ROI roi = other.getROI();
			        if (!request.intersects(
			        		ImageRegion.createInstance(roi)))
			            continue;
			        // Paint the nucleus
//			        g2d.setBackground(Color.BLACK);
//			        g2d.clearRect(0, 0, w, h);
			        Color color = other.getPathClass() == null ? null : mapLabelColor.getOrDefault(other.getPathClass().getName(), null);
			        if (color == null) {
			            logger.warn("{}: No color for classification {}", entry.getImageName(), other.getPathClass());
			            continue;
			        }
			        g2d.setColor(color);
			        Shape shape = RoiTools.getShape(roi);
			        g2d.fill(shape);
			        c++;
			    }
			    g2d.dispose();

			    // Write images
			    String name = String.format("%s-(%.2f,%d,%d,%d,%d)",
			    		ServerTools.getDisplayableImageName(server),
			            request.getDownsample(), request.getX(), request.getY(), request.getWidth(), request.getHeight());

			    // Set color model
			    BufferedImage imgTempIndexed = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
			    imgTempIndexed.getRaster().setRect(imgTemp.getRaster());

			    try {
			    	ImagePlus imp = IJTools.convertToImagePlus("Image", server, img, request).getImage();
			    	IJ.saveAsTiff(imp, new File(dirOutput, name + ".tif").getAbsolutePath());
//				    ImageIO.write(img, "PNG", new File(dirOutput, name + ".png"));
				    ImageIO.write(imgTempIndexed, "PNG", new File(dirOutput, name + "-mask.png"));
			    } catch (Exception e) {
			    	logger.error("Error exporting for " + entry.getImageName(), e);
			    }

			    // Flag the empty ones for checking
			    if (c == 0)
			    	logger.warn("No classified annotations inside {}", name + ".png");
			}
		}
		
		
//		static class PathClassDisplay {
//			
//			public PathClassDisplay(final PathClass pathClass, Inte)
//			
//		}
		
		
		
		/**
		 * Prompt for an export directory.
		 * 
		 * @return the directory chosen by the user, or {@code null} if no directory is chosen.
		 */
		File promptForDirectory() {
			Project<?> project = qupath.getProject();
			if (dirExport == null && project != null) {
				dirExport = Projects.getBaseDirectory(project);
			}
			File dirSelected = Dialogs.getChooser(pane.getScene().getWindow()).promptForDirectory(dirExport);
			if (dirSelected == null)
				return null;
			dirExport = dirSelected;
			return dirExport;
		}
		
		
		public Pane getPane() {
			return pane;
		}
		
	}

}