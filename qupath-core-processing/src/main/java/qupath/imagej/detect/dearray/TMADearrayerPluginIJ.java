/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.detect.dearray;

import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.detect.dearray.TMADearrayer.TMAGridShape;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.SimplePluginWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;



/**
 * Plugin for automatically dearraying tissue microarrays (TMAs).
 * 
 * @author Pete Bankhead
 *
 */
public class TMADearrayerPluginIJ extends AbstractInteractivePlugin<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(TMADearrayerPluginIJ.class);
	
	private ParameterList params;
	
	transient private Dearrayer dearrayer;
	
	/**
	 * Default constructor.
	 */
	public TMADearrayerPluginIJ() {
		
		// Create default parameter list
		params = new ParameterList();
		params.addDoubleParameter("coreDiameterMM", "TMA core diameter", 1.2, "mm", "Enter the approximate diameter or each TMA core in mm");
		params.addDoubleParameter("coreDiameterPixels", "TMA core diameter", 5000, "px", "Enter the approximate diameter or each TMA core in pixels");
		
		params.addEmptyParameter("Enter the horizontal and vertical labels for each core, with a space between each label."); 
		params.addEmptyParameter("The number of labels will define the array dimensions.");
		params.addEmptyParameter("Labels may also be entered as a range (e.g. 1-10 or A-G).");
		
		params.addStringParameter("labelsHorizontal", "Column labels", "1-16", "Enter column labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		params.addStringParameter("labelsVertical", "Row labels", "A-J", "Enter row labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		
		params.addChoiceParameter("labelOrder", "Label order", "Row first", Arrays.asList("Column first", "Row first"), "Create TMA labels either in the form Row-Column or Column-Row");

		//-------------
//		gd.addMessage("Core size & density", boldFont);
		
		params.addIntParameter("densityThreshold", "Density threshold", 5, "%", 0, 100, "Cores where a low density of tissue can be detected and excluded from later analysis (marked as 'missing').\nSet to 0 to include all cores (i.e. do not apply a density threshold).");
		
//		gd.addMessage("Choose a scaling factor for the core ROIs.");
//		gd.addMessage("1 means the ROI will have the same diameter as the core specified above; higher values add padding.");
		params.addIntParameter("boundsScale", "Bounds scale factor", 105, "%", 50, 150, "Scaling factor to adjust the core size.\nA scale factor of 100% will give cores with the diameter specified above.\nA higher scale factor will increase the size, a lower factor will decrease the size.");

	}
	
	
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		List<Class<? extends PathObject>> list = new ArrayList<>(1);
		list.add(PathRootObject.class);
		return list;
	}
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		if (dearrayer == null)
			dearrayer = new Dearrayer();

		tasks.add(() -> {
			try {
				dearrayer.runDetection(imageData, getParameterList(imageData), null);
				TMAGrid tmaGrid = dearrayer.getTMAGrid();
				if (tmaGrid != null)
					imageData.getHierarchy().setTMAGrid(tmaGrid);
			} catch (Exception e) {
				logger.error("Error running TMA dearrayer", e);
			}
		});
	}
	
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		if (imageData.getServer().getPixelCalibration().hasPixelSizeMicrons()) {
			params.getParameters().get("coreDiameterPixels").setHidden(true);
			params.getParameters().get("coreDiameterMM").setHidden(false);
		}
		else {
			params.getParameters().get("coreDiameterPixels").setHidden(false);
			params.getParameters().get("coreDiameterMM").setHidden(true);
		}
		return params;
	}

	@Override
	public String getName() {
		return "TMA dearrayer";
	}

	@Override
	public String getLastResultsDescription() {
		return dearrayer == null ? "" : dearrayer.getLastResultsDescription();
	}

	
	static class Dearrayer implements ObjectDetector<BufferedImage> {
		
		final private static Logger logger = LoggerFactory.getLogger(Dearrayer.class);
		
		private ImageProcessor ip;
		
		private TMAGrid tmaGrid = null;
		
		private String lastMessage = null;
		
		private double fullCoreDiameterPx, downsample;
		private boolean isFluorescence;
		private String[] hLabels, vLabels;
		
		protected ByteProcessor bp = null;
		protected Polygon polyGrid = null;
		
		
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) throws IOException {
					
			double fullCoreDiameterPx;
			ImageServer<BufferedImage> server = imageData.getServer();
			PixelCalibration cal = server.getPixelCalibration();
			if (cal.hasPixelSizeMicrons())
				fullCoreDiameterPx = params.getDoubleParameterValue("coreDiameterMM") / cal.getAveragedPixelSizeMicrons() * 1000;
			else
				fullCoreDiameterPx = params.getDoubleParameterValue("coreDiameterPixels");

			String horizontalLabels = params.getStringParameterValue("labelsHorizontal").trim();
			String verticalLabels = params.getStringParameterValue("labelsVertical").trim();
			
			boolean horizontalLabelFirst = params.getChoiceParameterValue("labelOrder").toString().startsWith("Column");
			// TODO: Consider fluorescence mode in TMA dearraying
			boolean isFluorescence = imageData.isFluorescence();
		
			double densityThreshold = params.getIntParameterValue("densityThreshold") * 0.01;
			double roiScaleFactor = params.getIntParameterValue("boundsScale") * 0.01;
			logger.trace("ROI scale: " + roiScaleFactor);
		

			double maxDimLength = Math.max(server.getWidth(), server.getHeight());
			double dimRequested = 1200;
			double downsample = Math.pow(2, Math.round(Math.log(maxDimLength / dimRequested)/Math.log(2)));
			
//			// Compute alternative downsample factor based on requested pixel size
//			// This is likely to be a bit more reproducible - so use it instead
//			if (server.hasPixelSizeMicrons()) {
//				double preferredCoreDiameterPixels = 60;
//				double downsample2 = Math.round(fullCoreDiameterPx / preferredCoreDiameterPixels);
//				if (downsample2 > 1 && (maxDimLength / downsample2 < dimRequested*2)) {
//					downsample = downsample2;
//				}
//			}

			// Compute alternative downsample factor based on requested pixel size
			// This is likely to be a bit more reproducible - so use it instead
			PixelCalibration pixelCalibration = server.getPixelCalibration();
			if (pixelCalibration.hasPixelSizeMicrons()) {
				double preferredPixelSizeMicrons = 25;
				double downsample2 = Math.round(preferredPixelSizeMicrons / pixelCalibration.getAveragedPixelSizeMicrons());
				if (downsample2 > 1 && (maxDimLength / downsample2 < dimRequested*2))
					downsample = downsample2;
			}

			// Read the image
			PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server,
					RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight()));
//			PathImage<ImagePlus> pathImage = IJTools.createPathImage(server, downsample);
			ImagePlus imp = pathImage.getImage();

			if (imp.getType() == ImagePlus.COLOR_RGB || imp.getNChannels() == 1)
				ip = imp.getProcessor();
			else {
				ZProjector zProjector = new ZProjector(imp);
				zProjector.setMethod(ZProjector.AVG_METHOD);
				zProjector.doProjection();
				ip = zProjector.getProjection().getProcessor();
			}
			bp = null;
				
			String[] hLabelsSplit = PathObjectTools.parseTMALabelString(horizontalLabels);
			String[] vLabelsSplit = PathObjectTools.parseTMALabelString(verticalLabels);

			updateGrid(tmaGrid, downsample);
			tmaGrid = doDearraying(fullCoreDiameterPx, downsample, densityThreshold, roiScaleFactor, isFluorescence, hLabelsSplit, vLabelsSplit, horizontalLabelFirst);
			
			return tmaGrid == null ? null : new ArrayList<>(tmaGrid.getTMACoreList());
		}
		
		
		
		public boolean updateGrid(TMAGrid tmaGrid, double downsample) {
			if (tmaGrid == null)
				return false;
			polyGrid = new Polygon();
			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
				double x = core.getROI().getCentroidX() / downsample + .5;
				double y = core.getROI().getCentroidY() / downsample + .5;
				polyGrid.addPoint((int)x, (int)y);
			}
			return true;
		}
		
		
		
		public TMAGrid doDearraying(double fullCoreDiameterPx, double downsample, double densityThreshold, double roiScaleFactor, boolean isFluorescence, String[] hLabelsSplit, String[] vLabelsSplit, boolean horizontalLabelFirst) {
			lastMessage = null;
			
			// Determine whether to update the ByteProcessor
			if (this.fullCoreDiameterPx != fullCoreDiameterPx || this.downsample != downsample || this.isFluorescence != isFluorescence) {
				this.fullCoreDiameterPx = fullCoreDiameterPx;
				this.downsample = downsample;
				this.isFluorescence = isFluorescence;
				bp = null;
			}
			// Determine whether to recompute the grid
			boolean recomputeGrid = true; // bp == null || polyGrid == null; // TODO: Improve the efficiency here - it was previously posssible to avoid recomputing the grid, but this led to problems with an incorrect number of labels was provided
			if (hLabels == null || hLabels.length != hLabelsSplit.length || vLabels == null || vLabels.length != vLabelsSplit.length) {
				this.hLabels = hLabelsSplit;
				this.vLabels = vLabelsSplit;
				recomputeGrid = true;
				logger.info("Will (re)compute TMA grid...");
			}

			int nHorizontal = hLabelsSplit.length;
			int nVertical = vLabelsSplit.length;

			// Update core diameter according to downsample
			double coreDiameterPx = fullCoreDiameterPx / downsample;

			// Detect the cores, or potential core regions
			if (bp == null)
				bp = TMADearrayer.makeBinaryImage(ip, coreDiameterPx, null, isFluorescence);
			
			// Identify the grid
			if (recomputeGrid) {
				TMAGridShape tmaGridShape = TMADearrayer.detectTMACoresFromBinary(bp, coreDiameterPx, nHorizontal, nVertical, null);
				if (tmaGridShape == null || tmaGridShape.nHorizontal * tmaGridShape.nVertical == 0)
					polyGrid = null;
				else {
					polyGrid = tmaGridShape.polyGrid;
					nHorizontal = tmaGridShape.nHorizontal;
					nVertical = tmaGridShape.nVertical;
				}
			}
			
			// Check for interruptions...
			if (Thread.currentThread().isInterrupted() || polyGrid == null)
				return null;
			
			// Compute densities
			double[] coreDensities = TMADearrayer.computeDensities(bp, polyGrid, coreDiameterPx);
			
			// Update cores
			List<TMACoreObject> coords = new ArrayList<>();
			double coreSize = fullCoreDiameterPx * roiScaleFactor;
			int ind = 0;
			int nMissing = 0;
			for (int y = 0; y < nVertical; y++) {
				for (int x = 0; x < nHorizontal; x++) {
					if (ind >= polyGrid.npoints)
						break;
					double xx = polyGrid.xpoints[ind] * downsample;
					double yy = polyGrid.ypoints[ind] * downsample;
					
					String hLabel = hLabelsSplit[x];
					String vLabel = vLabelsSplit[y];
					String name = "";
					if (horizontalLabelFirst)
						name += hLabel + "-" + vLabel;
					else
						name += vLabel + "-" + hLabel;
					
					boolean missing = coreDensities[ind] < densityThreshold;
					TMACoreObject core = PathObjects.createTMACoreObject(xx, yy, coreSize, missing);
					core.setName(name);
					coords.add(core);
					ind++;
					if (missing)
						nMissing++;
				}
			}
			TMAGrid tmaGrid = DefaultTMAGrid.create(coords, nHorizontal);
			
			lastMessage = String.format("%d x %d TMA grid created (%d missing)", nHorizontal, nVertical, nMissing);
			
			return tmaGrid;
		}
		
		
		
		@Override
		public String getLastResultsDescription() {
			return lastMessage;
		}
		
		
		
		public TMAGrid getTMAGrid() {
			return tmaGrid;
		}
		
		
	}
	

	@Override
	public String getDescription() {
		return "Detect a grid of tissue cores on a Tissue Microarray";
	}




	@Override
	protected Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		return Collections.singletonList(runner.getImageData().getHierarchy().getRootObject());
	}
	
	
	@Override
	protected void addWorkflowStep(final ImageData<BufferedImage> imageData, final String arg) {
		WorkflowStep step = new SimplePluginWorkflowStep(getName(), (Class<? extends PathPlugin<?>>)getClass(), arg, "if (!isTMADearrayed()) {\n\t", "\n\treturn;\n}");
		imageData.getHistoryWorkflow().addStep(step);
		logger.info("{}", step);
	}
	

}