/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022, 2024 QuPath developers, The University of Edinburgh
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

package qupath.imagej.gui;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.process.LUT;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.SingleChannelDisplayInfo;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.images.servers.ChannelDisplayTransformServer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Extract a region from QuPath, and display it within ImageJ.
 * <p>
 * This command also attempts to set properties appropriately so that it's possible to determine from 
 * within ImageJ exactly where in the image was selected.  This is needed to be able to transfer back 
 * Rois from ImageJ as QuPath objects.
 * 
 * @author Pete Bankhead
 *
 */
class ExtractRegionCommand implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(ExtractRegionCommand.class);

	/**
	 * Enum to control whether ROIs are included when sending regions to ImageJ.
	 */
	public enum RoiInclude {
		/**
		 * Include all ROIs
		 */
		YES,
		/**
		 * Include no ROIs
		 */
		NO,
		/**
		 * Only include non-rectangular ROIs.
		 * Because the image is a rectangle, a rectangular ROI includes the entire image -
		 * so is often not very useful.
		 */
		NON_RECTANGLE;

		public String toString() {
			return switch (this) {
				case YES -> "Yes";
				case NO -> "No";
				case NON_RECTANGLE -> "Non-rectangles only";
			};
		}
	}

	private QuPathGUI qupath;
	
	private static final String PIXELS_UNIT = "Pixels (downsample)";
	
	private DoubleProperty resolution = PathPrefs.createPersistentPreference("ext.ij.extract.resolution", 1.0);
	private StringProperty resolutionUnit = PathPrefs.createPersistentPreference("ext.ij.extract.resolutionUnit", PIXELS_UNIT);
	private ObjectProperty<RoiInclude> includeROI = PathPrefs.createPersistentPreference("ext.ij.extract.includeROI", RoiInclude.YES, RoiInclude.class);
	private BooleanProperty includeOverlay = PathPrefs.createPersistentPreference("ext.ij.extract.includeOverlay", true);
	private BooleanProperty doTransforms = PathPrefs.createPersistentPreference("ext.ij.extract.doTransforms", false);
	private BooleanProperty doZ = PathPrefs.createPersistentPreference("ext.ij.extract.doZ", false);
	private BooleanProperty doT = PathPrefs.createPersistentPreference("ext.ij.extract.doT", false);
	
	/**
	 * Constructor.
	 * @param qupath QuPath instance where the command should be installed.
	 */
	public ExtractRegionCommand(QuPathGUI qupath) {
//		super("Extract region (custom)", PathIconFactory.createIcon(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.EXTRACT_REGION));
		this.qupath = qupath;
	}
	
	/**
	 * Get the name of the command.
	 * @return
	 */
	public String getName() {
		return "Extract region (custom)";
	}
	

	@Override
	public void run() {
		QuPathViewer viewer = qupath.getViewer();
		ImageServer<BufferedImage> server = null;
		if (viewer != null)
			server = viewer.getServer();
		if (server == null)
			return;
		
		List<String> unitOptions = new ArrayList<>();
		unitOptions.add(PIXELS_UNIT);
		String unit = server.getPixelCalibration().getPixelWidthUnit();
		if (unit.equals(server.getPixelCalibration().getPixelHeightUnit()) && !unit.equals(PixelCalibration.PIXEL))
			unitOptions.add(unit);

		String resolutionUnit = this.resolutionUnit.get();
		if (!unitOptions.contains(resolutionUnit))
			resolutionUnit = PIXELS_UNIT;
		
		ParameterList params = new ParameterList()
				.addDoubleParameter("resolution", "Resolution", resolution.get(), null, "Resolution at which the image will be exported, defined as the 'pixel size' in Resolution units")
				.addChoiceParameter("resolutionUnit", "Resolution unit", resolutionUnit, unitOptions, "Units defining the export resolution; if 'pixels' then the resolution is the same as a downsample value")
				.addChoiceParameter("includeROI", "Include ROI", includeROI.get(), Arrays.asList(RoiInclude.values()), "Include the primary object defining the exported region as an active ROI in ImageJ")
				.addBooleanParameter("includeOverlay", "Include overlay", includeOverlay.get(), "Include any objects overlapping the exported region as ROIs on an ImageJ overlay")
				.addBooleanParameter("doTransforms", "Apply color transforms", doTransforms.get(), "Optionally apply any color transforms when sending the pixels to ImageJ")
				.addBooleanParameter("doZ", "All z-slices", doZ.get(), "Optionally include all slices of a z-stack")
				.addBooleanParameter("doT", "All timepoints", doT.get(), "Optionally include all timepoints of a time series")
				;

//		params.setHiddenParameters(unitOptions.size() <= 1, "resolutionUnit");
		params.setHiddenParameters(server.nZSlices() == 1, "doZ");
		params.setHiddenParameters(server.nTimepoints() == 1, "doT");
		
		if (!GuiTools.showParameterDialog("Send region to ImageJ", params))
			return;
		
		// Parse values - store as local variables now, make persistent later
		double resolution = params.getDoubleParameterValue("resolution");
		resolutionUnit = (String)params.getChoiceParameterValue("resolutionUnit");
		RoiInclude includeROI = (RoiInclude)params.getChoiceParameterValue("includeROI");
		boolean includeOverlay = params.getBooleanParameterValue("includeOverlay");
		boolean doTransforms = params.getBooleanParameterValue("doTransforms");
		boolean doZ = params.getBooleanParameterValue("doZ");
		boolean doT = params.getBooleanParameterValue("doT");

		// Now make persistent
		this.resolution.set(resolution);
		this.resolutionUnit.set(resolutionUnit);
		this.includeROI.set(includeROI);
		this.includeOverlay.set(includeOverlay);
		this.doTransforms.set(doTransforms);
		this.doZ.set(doZ);
		this.doT.set(doT);
		
		// Calculate downsample
		double downsample = resolution;
		if (!resolutionUnit.equals(PIXELS_UNIT))
			downsample = resolution / (server.getPixelCalibration().getPixelHeight().doubleValue()/2.0 + server.getPixelCalibration().getPixelWidth().doubleValue()/2.0);
		
		// Color transforms are (currently) only applied for brightfield images - for fluorescence we always provide everything as unchanged as possible
		var imageDisplay = viewer.getImageDisplay();
		List<ChannelDisplayInfo> selectedChannels = new ArrayList<>(imageDisplay.selectedChannels());
		List<ChannelDisplayInfo> channels = doTransforms && !selectedChannels.isEmpty() ? selectedChannels : null;
		if (channels != null)
			server = ChannelDisplayTransformServer.createColorTransformServer(server, channels);
		
		// We don't support applying gamma for now
		if (doTransforms) {
			var gamma = viewer.getGammaOp();
			if (gamma != null)
				logger.warn("Gamma transform not supported when sending image to ImageJ");
		}

		// Loop through all selected objects
		Collection<PathObject> pathObjects = viewer.getHierarchy().getSelectionModel().getSelectedObjects();
		if (pathObjects.isEmpty())
			pathObjects = Collections.singletonList(viewer.getHierarchy().getRootObject());
		List<ImagePlus> imps = new ArrayList<>();
		for (PathObject pathObject : pathObjects) {
			
			if (Thread.currentThread().isInterrupted() || IJ.escapePressed()) {
				logger.warn("Escape pressed! I will stop showing images.");
				IJ.resetEscape();
				return;
			}
			
			int width, height;
			if (pathObject == null || !pathObject.hasROI()) {
				width = server.getWidth();
				height = server.getHeight();
			} else {
				Rectangle bounds = AwtTools.getBounds(pathObject.getROI());
				width = bounds.width;
				height = bounds.height;
			}
			
			RegionRequest region;
			ROI roi = pathObject == null ? null : pathObject.getROI();
			if (roi == null || PathObjectTools.hasPointROI(pathObject)) {
				region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight(), viewer.getZPosition(), viewer.getTPosition());
			} else
				region = RegionRequest.createInstance(server.getPath(), downsample, roi);
			//					region = RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI(), viewer.getZPosition(), viewer.getTPosition());
	
			// Minimum size has been removed (v0.2.0-m4); returned regions should be at least 1x1 pixels
//			if (region.getWidth() / downsample < 8 || region.getHeight() / downsample < 8) {
//				DisplayHelpers.showErrorMessage("Send region to ImageJ", "The width & height of the extracted image must both be >= 8 pixels");
//				continue;
//			}

			// Calculate required z-slices and time-points
			int zStart = doZ ? 0 : region.getZ();
			int zEnd = doZ ? server.nZSlices() : region.getZ() + 1;
			int tStart = doT ? 0 : region.getT();
			int tEnd = doT ? server.nTimepoints() : region.getT() + 1;
			long nZ = zEnd - zStart;
			long nT = tEnd - tStart;
			
			int bytesPerPixel = server.isRGB() ? 4 : server.getPixelType().getBytesPerPixel() * server.nChannels();
			double memory = ((long)width * height * nZ * nT * bytesPerPixel) / (downsample * downsample);
			
			// TODO: Perform calculation based on actual amount of available memory
			long availableMemory = GeneralTools.estimateAvailableMemory();
			if (memory >= availableMemory * 0.95) {
				logger.error("Cannot extract region {} - estimated size is too large (approx. {} MB)", pathObject, GeneralTools.formatNumber(memory / (1024.0 * 1024.0), 2));
				Dialogs.showErrorMessage("Send region to ImageJ error", "Selected region is too large to extract - please selected a smaller region or use a higher downsample factor");
				continue;
			}
			if (memory / 1024 / 1024 > 100) {
				if (pathObjects.size() == 1 && !Dialogs.showYesNoDialog("Send region to ImageJ", String.format("Attempting to extract this region is likely to require > %.2f MB - are you sure you want to continue?", memory/1024/1024)))
					return;
			}

			// Determine if we really do what to send the ROI
			boolean doIncludeRoi = switch (includeROI) {
				case NON_RECTANGLE -> roi != null && !(roi instanceof RectangleROI);
				case YES -> true;
				case NO -> false;
			};

			// We should switch to the event dispatch thread when interacting with ImageJ
			try {
				ImagePlus imp;
				PathObjectHierarchy hierarchy = viewer.getHierarchy();
				OverlayOptions options = viewer.getOverlayOptions();
				if (zEnd - zStart > 1 || tEnd - tStart > 1) {
					// TODO: Handle overlays
					imp = IJTools.extractHyperstack(server, region, zStart, zEnd, tStart, tEnd);
					if (doIncludeRoi && roi != null) {
						Roi roiIJ = IJTools.convertToIJRoi(roi, imp.getCalibration(), region.getDownsample());
						imp.setRoi(roiIJ);
					}
					if (includeOverlay) {
						Overlay overlay = new Overlay();
						for (int t = tStart; t < tEnd; t++) {
							for (int z = zStart; z < zEnd; z++) {
								RegionRequest request2 = RegionRequest.createInstance(region.getPath(), region.getDownsample(), region.getX(), region.getY(), region.getWidth(), region.getHeight(), z, t);
								var regionPredicate = PathObjectTools.createImageRegionPredicate(request2);
								Overlay temp = IJExtension.extractOverlay(hierarchy, request2, options, p -> p != pathObject && regionPredicate.test(p));
								if (overlay == null)
									overlay = temp;
								for (int i = 0; i < temp.size(); i++) {
									Roi roiIJ = temp.get(i);
									roiIJ.setPosition(-1, z+1, t+1);
									overlay.add(roiIJ);
								}
							}							
						}
						if (overlay != null && overlay.size() > 0)
							imp.setOverlay(overlay);
					}
				} else if (includeOverlay)
					imp = IJExtension.extractROIWithOverlay(server, pathObject, hierarchy, region, doIncludeRoi, options).getImage();
				else
					imp = IJExtension.extractROIWithOverlay(server, pathObject, null, region, doIncludeRoi, options).getImage();
				
				// Set display ranges and invert LUTs if we should (and can)
				boolean invertLUTs = imageDisplay.useInvertedBackground();
				// We can't set the LUTs for an RGB image in ImageJ
//				if (invertLUTs)
//					imp = CompositeConverter.makeComposite(imp);
				if (imp instanceof CompositeImage impComp) {
					var tempChannels = channels == null ? imageDisplay.availableChannels() : channels;
					var availableSingleChannels = tempChannels.stream()
							.filter(c -> c instanceof SingleChannelDisplayInfo)
							.map(c -> (SingleChannelDisplayInfo)c)
							.toList();
					
					// If we're displaying with an inverted background, we need to set this property for the composite mode
					if (invertLUTs) {
						impComp.setProp("CompositeProjection", "invert");
					}
					if (availableSingleChannels.size() == impComp.getNChannels()) {
						for (int c = 0; c < availableSingleChannels.size(); c++) {
							var channel = availableSingleChannels.get(c);
							impComp.setPosition(c+1, 1, 1);
							// Need to invert the LUT to have a white background
							if (invertLUTs) {
								var lut = impComp.getChannelLut();
								int n = lut.getMapSize();
								var r = lut.getRed(n-1);
								var g = lut.getGreen(n-1);
								var b = lut.getBlue(n-1);
								var reds = new byte[n];
								var greens = new byte[n];
								var blues = new byte[n];
								for (int i = 0; i < n; i++) {
									reds[i] = (byte)(255 - (int)((255 - r)*(i/255.0)));
									greens[i] = (byte)(255 - (int)((255 - g)*(i/255.0)));
									blues[i] = (byte)(255 - (int)((255 - b)*(i/255.0)));
								}
								lut = new LUT(reds, greens, blues);
								impComp.setChannelLut(lut);
							}
							// Set the display range
							impComp.setDisplayRange(channel.getMinDisplay(), channel.getMaxDisplay());
						}
						impComp.setPosition(1, 1, 1);
					}
				} else if (selectedChannels.size() == 1 && imp.getType() != ImagePlus.COLOR_RGB) {
					// Setting the display range for non-RGB images can give unexpected results (changing pixel values)
					var channel = selectedChannels.getFirst();
					imp.setDisplayRange(channel.getMinDisplay(), channel.getMaxDisplay());
				}
				imps.add(imp);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Send region to ImageJ", e);
				logger.error(e.getMessage(), e);
				return;
			}
		}
		
		// Show all the images we've got
		if (!imps.isEmpty()) {
			SwingUtilities.invokeLater(() -> {
				boolean batchMode = Interpreter.batchMode;

				// Try to start an ImageJ instance, and return if this fails
				try {
					ImageJ ij = IJExtension.getImageJInstance();
					if (ij == null)
						return;
					ij.setVisible(true);
	
	
					Interpreter.batchMode = false; // Make sure we aren't in batch mode, so that image will display
					for (ImagePlus imp : imps) {
						if (IJ.escapePressed()) {
							logger.warn("Escape pressed - I'll stop showing images");
							IJ.resetEscape();
							return;
						}
						imp.show();
					}
				} finally {
					Interpreter.batchMode = batchMode;
				}
			});
		}
	}
	
	
}