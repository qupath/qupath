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
import java.io.IOException;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.Stage;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tma.TMAEntries.TMAEntry;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.regions.RegionRequest;

/**
 * The aim of this is to enable the exploration of TMA data from multiple images in a project.
 * <p>
 * In the end, it might not last... since this overlaps considerably with the aim of the TMASummaryViewer.
 * <p>
 * Therefore currently its primary task is to simply launch the TMASummaryViewer with the data it has gathered.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAExplorer implements Runnable {

	private static Logger logger = LoggerFactory.getLogger(TMAExplorer.class);

	private QuPathGUI qupath;
	
	private List<TMAEntry> entries = new ArrayList<>();
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public TMAExplorer(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		createAndShowStage();
	}
	
	private void createAndShowStage() {
		
		Project<BufferedImage> project = qupath.getProject();
		
		entries.clear();
		if (project != null) {
			
			// Create an output directory for the images
			File dirBaseImageOutput = Projects.getBaseDirectory(project);
			dirBaseImageOutput = new File(dirBaseImageOutput, "TMA");
			dirBaseImageOutput = new File(dirBaseImageOutput, "images");
			if (!dirBaseImageOutput.exists())
				dirBaseImageOutput.mkdirs();

			
			Map<String, RunningStatistics> statsMap = new HashMap<>();
			
			for (ProjectImageEntry<BufferedImage> imageEntry : project.getImageList()) {
				// Look for data file
				if (!imageEntry.hasImageData())
					continue;
				
				File dirImageOutput = new File(dirBaseImageOutput, imageEntry.getImageName());
				if (!dirImageOutput.exists())
					dirImageOutput.mkdirs();
				
				// Read data
				ImageData<BufferedImage> imageData;
				try {
					imageData = imageEntry.readImageData();
				} catch (IOException e) {
					logger.error("Error reading ImageData for " + imageEntry.getImageName(), e);
					continue;
				}
				TMAGrid tmaGrid = imageData.getHierarchy().getTMAGrid();
				if (tmaGrid == null) {
					logger.warn("No TMA data for {}", imageEntry.getImageName());
					continue;
				}
				
				// Figure out downsample value
				ImageServer<BufferedImage> server = imageData.getServer();
				double downsample = Math.round(5 / server.getPixelCalibration().getAveragedPixelSizeMicrons());
				
				// Read the TMA entries
				int counter = 0;
				for (TMACoreObject core : tmaGrid.getTMACoreList()) {
					counter++;
					String name = core.getName();
					if (name == null)
						name = Integer.toString(counter);
					File fileOutput = new File(dirImageOutput, name + ".jpg");
					if (!fileOutput.exists()) {
						try {
							RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, core.getROI());
							BufferedImage img = server.readBufferedImage(request);
							ImageIO.write(img, "jpg", fileOutput);
						} catch (Exception e) {
							logger.error("Unable to write {}", fileOutput.getAbsolutePath());
						}
					}

					var entry = TMAEntries.createDefaultTMAEntry(
							imageEntry.getImageName(),
							fileOutput.getAbsolutePath(),
							null,
							core.getName(),
							core.isMissing());
					
					MeasurementList ml = core.getMeasurementList();
					for (int i = 0; i < ml.size(); i++) {
						String measurement = ml.getMeasurementName(i);
						double val = ml.getMeasurementValue(i);
						entry.putMeasurement(measurement, val);
						if (!Double.isNaN(val)) {
							RunningStatistics stats = statsMap.get(measurement);
							if (stats == null) {
								stats = new RunningStatistics();
								statsMap.put(measurement, stats);
							}
							stats.addValue(val);
						}
					}
					entries.add(entry);
				}
				
				try {
					server.close();
				} catch (Exception e) {
					logger.warn("Problem closing server", e);
				}

			}
			
			// Loop through all entries and perform outlier count
			double k = 3;
			for (TMAEntry entry : entries) {
				int outlierCount = 0;
				for (Entry<String, RunningStatistics> statsEntry : statsMap.entrySet()) {
					RunningStatistics stats = statsEntry.getValue();
					double val = entry.getMeasurementAsDouble(statsEntry.getKey());
					if (!(val >= stats.getMean()-stats.getStdDev()*k && val <= stats.getMean()+stats.getStdDev()*k))
						outlierCount++;
				}
				entry.putMeasurement("Outlier count", outlierCount);
			}
			
		}
		
		
		Stage stage = new Stage();
		stage.initOwner(qupath.getStage());
		TMASummaryViewer summaryViewer = new TMASummaryViewer(stage);
		summaryViewer.setTMAEntries(entries);
		summaryViewer.getStage().show();
		
		
	}
	

}
