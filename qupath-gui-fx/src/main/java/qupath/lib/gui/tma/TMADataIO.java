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

package qupath.lib.gui.tma;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.plugins.PluginRunnerFX;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.TMAGridOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.TMAScoreImporter;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.AbstractPlugin;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Static methods for exporting TMA data, optionally with small images.
 * 
 * @see qupath.lib.io.PathIO
 * 
 * @author Pete Bankhead
 *
 */
public class TMADataIO {

	final private static Logger logger = LoggerFactory.getLogger(TMADataIO.class);

	@SuppressWarnings("javadoc")
	final public static String TMA_DEARRAYING_DATA_EXTENSION = ".qptma";
	
	private static double preferredExportPixelSizeMicrons = 1.0;
	
	/**
	 * Write TMA summary data, without any image export.
	 * 
	 * @param file
	 * @param imageData
	 */
	public static void writeTMAData(File file, final ImageData<BufferedImage> imageData) {
		writeTMAData(file, imageData, null, -1);
	}
	
	/**
	 * Write TMA data in a human-readable (and viewable) way, with JPEGs and TXT/CSV files.
	 * 
	 * @param file
	 * @param imageData
	 * @param overlayOptions 
	 * @param downsampleFactor The downsample factor used for the TMA cores. If NaN, an automatic downsample value will be selected (&gt;= 1).  If &lt;= 0, no cores are exported.
	 */
	public static void writeTMAData(File file, final ImageData<BufferedImage> imageData, OverlayOptions overlayOptions, final double downsampleFactor) {
		if (imageData == null || imageData.getHierarchy() == null || imageData.getHierarchy().getTMAGrid() == null) {
			logger.error("No TMA data available to save!");
			return;
		}
		final ImageServer<BufferedImage> server = imageData.getServer();
		String coreExt = imageData.getServer().isRGB() ? ".jpg" : ".tif";
		if (file == null) {
			file = Dialogs.promptToSaveFile("Save TMA data", null, ServerTools.getDisplayableImageName(server), "TMA data", "qptma");
			if (file == null)
				return;
		} else if (file.isDirectory() || (!file.exists() && file.getAbsolutePath().endsWith(File.pathSeparator))) {
			// Put inside the specified directory
			file = new File(file, ServerTools.getDisplayableImageName(server) + TMA_DEARRAYING_DATA_EXTENSION);
			if (!file.getParentFile().exists())
				file.getParentFile().mkdirs();
		}
		final File dirData = new File(file + ".data");
		if (!dirData.exists())
			dirData.mkdir();

		// Write basic file info
		String delimiter = "\t";
		TMAGrid tmaGrid = imageData.getHierarchy().getTMAGrid();
		try {
			PrintWriter writer = new PrintWriter(file);
			writer.println(server.getPath());
			writer.println(ServerTools.getDisplayableImageName(server));
			writer.println();

			writer.println("TMA grid width: " + tmaGrid.getGridWidth());
			writer.println("TMA grid height: " + tmaGrid.getGridHeight());
			writer.println("Core name" + delimiter + "X" + delimiter + "Y" + delimiter + "Width" + delimiter + "Height" + delimiter + "Present" + delimiter + TMACoreObject.KEY_UNIQUE_ID);
			for (int row = 0; row < tmaGrid.getGridHeight(); row++) {
				for (int col = 0; col < tmaGrid.getGridWidth(); col++) {
					TMACoreObject core = tmaGrid.getTMACore(row, col);
					if (!core.hasROI()) {
						writer.println(core.getName() + delimiter + delimiter + delimiter + delimiter);
						continue;
					}
					ROI pathROI = core.getROI();
					int x = (int)pathROI.getBoundsX();
					int y = (int)pathROI.getBoundsY();
					int w = (int)Math.ceil(pathROI.getBoundsWidth());
					int h = (int)Math.ceil(pathROI.getBoundsHeight());
					String id = core.getUniqueID() == null ? "" : core.getUniqueID();
					writer.println(core.getName() + delimiter + x + delimiter + y + delimiter + w + delimiter + h + delimiter + !core.isMissing() + delimiter + id);
				}				
			}
			writer.close();
		} catch (Exception e) {
			logger.error("Error writing TMA data", e);
			return;
		}


		// Save the summary results
		ObservableMeasurementTableData tableData = new ObservableMeasurementTableData();
		tableData.setImageData(imageData, tmaGrid.getTMACoreList());
		SummaryMeasurementTableCommand.saveTableModel(tableData, new File(dirData, "TMA results - " + ServerTools.getDisplayableImageName(server) + ".txt"), Collections.emptyList());

		boolean outputCoreImages = Double.isNaN(downsampleFactor) || downsampleFactor > 0;
		if (outputCoreImages) {
			// Create new overlay options, if we don't have some already
			if (overlayOptions == null) {
				overlayOptions = new OverlayOptions();
				overlayOptions.setFillDetections(true);
			}
			final OverlayOptions options = overlayOptions;
			
			
			// Write an overall TMA map (for quickly checking if the dearraying is ok)
			File fileTMAMap = new File(dirData, "TMA map - " + ServerTools.getDisplayableImageName(server) + ".jpg");
			double downsampleThumbnail = Math.max(1, (double)Math.max(server.getWidth(), server.getHeight()) / 1024);
			RegionRequest request = RegionRequest.createInstance(server.getPath(), downsampleThumbnail, 0, 0, server.getWidth(), server.getHeight());
			OverlayOptions optionsThumbnail = new OverlayOptions();
			optionsThumbnail.setShowTMAGrid(true);
			optionsThumbnail.setShowGrid(false);
			optionsThumbnail.setShowAnnotations(false);
			optionsThumbnail.setShowDetections(false);
			try {
				var renderedServer = new RenderedImageServer.Builder(imageData)
						.layers(new TMAGridOverlay(overlayOptions))
						.downsamples(downsampleThumbnail)
						.build();
				ImageWriterTools.writeImageRegion(renderedServer, request, fileTMAMap.getAbsolutePath());
//				ImageWriters.writeImageRegionWithOverlay(imageData.getServer(), Collections.singletonList(new TMAGridOverlay(overlayOptions, imageData)), request, fileTMAMap.getAbsolutePath());
			} catch (IOException e) {
				logger.warn("Unable to write image overview", e);
			}

			final double downsample = Double.isNaN(downsampleFactor) ? (server.getPixelCalibration().hasPixelSizeMicrons() ? ServerTools.getDownsampleFactor(server, preferredExportPixelSizeMicrons) : 1) : downsampleFactor;
			
			// Creating a plugin makes it possible to parallelize & show progress easily
			var renderedImageServer = new RenderedImageServer.Builder(imageData)
					.layers(new HierarchyOverlay(null, options, imageData))
					.downsamples(downsample)
					.build();
			ExportCoresPlugin plugin = new ExportCoresPlugin(dirData, renderedImageServer, downsample, coreExt);
			PluginRunner<BufferedImage> runner;
			if (QuPathGUI.getInstance() == null || QuPathGUI.getInstance().getImageData() != imageData) {
				runner = new CommandLinePluginRunner<>(imageData);
				plugin.runPlugin(runner, null);
			} else {
				runner = new PluginRunnerFX(QuPathGUI.getInstance());				
				new Thread(() -> plugin.runPlugin(runner, null)).start();
			}
		}
	}
	
	

	/**
	 * Import a TMA grid from an exported TMA analysis file, i.e. with extension ".qptma"
	 * 
	 * @param file
	 * @return
	 */
	public static TMAGrid importDearrayedTMAData(final File file) {
		try (Scanner scanner = new Scanner(file)) {
			int gridWidth = -1;
			int gridHeight = -1;
			
			String line = null;
			while (scanner.hasNextLine()) {
				line = scanner.nextLine();
				if (line.startsWith("TMA grid width:"))
					gridWidth = Integer.parseInt(line.replace("TMA grid width:", "").trim());
				else if (line.startsWith("TMA grid height:"))
					gridHeight = Integer.parseInt(line.replace("TMA grid height:", "").trim());
				if (gridWidth >= 0 && gridHeight >= 0)
					break;
			}
			if (gridWidth <= 0 || gridHeight <= 0) {
				logger.error("No grid dimensions available!");
				return null;
			}
			Map<String, List<String>> csvData = TMAScoreImporter.readCSV(scanner);
			List<String> colNames = csvData.get("Core name");
			List<String> colX = csvData.get("X");
			List<String> colY = csvData.get("Y");
			List<String> colWidth = csvData.get("Width");
			List<String> colHeight = csvData.get("Height");
			List<String> colPresent = csvData.get("Present");
			List<String> colID = csvData.get(TMACoreObject.KEY_UNIQUE_ID);
			
			if (colX == null || colY == null || colWidth == null || colHeight == null) {
				logger.error("No core locations available!");
				return null;
			}
			
//			int count = 0;
			List<TMACoreObject> cores = new ArrayList<>();
			for (int i = 0; i < colX.size(); i++) {
				double x = Double.parseDouble(colX.get(i));
				double y = Double.parseDouble(colY.get(i));
				double w = Double.parseDouble(colWidth.get(i));
				double h = Double.parseDouble(colHeight.get(i));
				boolean present = colPresent == null ? true : Boolean.parseBoolean(colPresent.get(i));
				String name = colNames == null ? null : colNames.get(i);
				String id = colID == null ? null : colID.get(i);
				TMACoreObject core = PathObjects.createTMACoreObject(x, y, w, h, !present);
				if (name != null)
					core.setName(name);
				if (id != null && !id.isEmpty())
					core.setUniqueID(id);
				cores.add(core);
			}
			return DefaultTMAGrid.create(cores, gridWidth);
		} catch (FileNotFoundException e) {
			logger.error("Cannot find file: {}", file);
			return null;
		}
	}
	
	
	/**
	 * Plugin for exporting TMA core images.
	 * 
	 * The reason for implementing this as a plugin is to take advantage of multithreading and progress bars - 
	 * but it (intentionally) isn't scriptable since it is generally called from another (probably scriptable) command.
	 * 
	 * @author Pete Bankhead
	 */
	private static class ExportCoresPlugin extends AbstractPlugin<BufferedImage> {
		
		private double downsample;
		private File dir;
//		private OverlayOptions options;
		private String ext;
		private ImageServer<BufferedImage> renderedServer;
		
		private ExportCoresPlugin(final File dirOutput, final ImageServer<BufferedImage> renderedServer, final double downsample, final String ext) {
			this.dir = dirOutput;
			this.renderedServer = renderedServer;
			this.downsample = downsample;
			this.ext = ext;
		}

		/**
		 * Do not log a workflow step for this plugin.
		 * 
		 * @param imageData
		 * @param arg
		 */
		@Override
		protected void addWorkflowStep(final ImageData<BufferedImage> imageData, final String arg) {
			// Do nothing
		}
		
		@Override
		public String getName() {
			return "Export TMA cores";
		}

		@Override
		public String getDescription() {
			return "Export TMA cores & thumbnail images";
		}

		@Override
		public String getLastResultsDescription() {
			return null;
		}

		@Override
		protected boolean parseArgument(ImageData<BufferedImage> imageData, String arg) {
			return true;
		}

		@Override
		protected Collection<? extends PathObject> getParentObjects(PluginRunner<BufferedImage> runner) {
			return PathObjectTools.getTMACoreObjects(getHierarchy(runner), true);
		}

		@Override
		protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					// Write the core
					File fileOutput = new File(dir, parentObject.getName() + ext);
					RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, parentObject.getROI());
					try {
//						var img = imageData.getServer().readBufferedImage(request);
						ImageWriterTools.writeImageRegion(imageData.getServer(), request, fileOutput.getAbsolutePath());
						fileOutput = new File(dir, parentObject.getName() + "-overlay.jpg");
						// Pass in the image we have so that it will be drawn on top of
						ImageWriterTools.writeImageRegion(renderedServer, request, fileOutput.getAbsolutePath());
//						ImageWriters.writeImageRegionWithOverlay(img, imageData, options, request, fileOutput.getAbsolutePath());						
					} catch (IOException e) {
						logger.error("Unable to write " + request, e);
					}
					
					// The following code writes a PNG with transparency for the overlay, rather than a marked-up version of the original
//					fileOutput = new File(dir, parentObject.getName() + "-overlay.png");
//					img = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
//					// Pass in the image we have so that it will be drawn on top of
//					ImageWriterTools.writeImageRegionWithOverlay(img, imageData, options, request, fileOutput.getAbsolutePath());
				}
			};
			
			tasks.add(runnable);
		}
		
	}
	
}
