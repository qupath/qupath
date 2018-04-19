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

package qupath.imagej.detect.features;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.filter.RankFilters;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.processing.ROILabeling;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.ShapeSimplifierAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;


/**
 * Experimental class to help with (one method of) scoring TMAs with immune cell markers.
 * 
 * The idea is that it looks at the distance of immune cells from tumor regions.
 * However a more refined approach is really needed in practice... it's just kept here for ideas &amp; inspiration.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class ImmuneScorerTMA extends AbstractInteractivePlugin<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(ImmuneScorerTMA.class);
	
	private ParameterList params;
	
	
	public ImmuneScorerTMA() {
		params = new ParameterList();
		params.addDoubleParameter("tumourHoleDiameterMicrons", "Maximum tumour space diameter", 25, GeneralTools.micrometerSymbol(), 
				"Choose the maximum diameter of a gap within a tumour region that should be filled when identifying infiltrating cells");
		params.addDoubleParameter("tumourHoleDiameterPixels", "Maximum tumour space diameter", 25, "pixels", 
				"Choose the maximum diameter of a gap within a tumour region that should be filled when identifying infiltrating cells");
	}
	
	

	@Override
	public String getName() {
		return "Immune Scorer for TMAs";
	}

	@Override
	public String getDescription() {
		return "Compute prototype immuno features for TMAs";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		Collection<Class<? extends PathObject>> parentClasses = new ArrayList<>();
		parentClasses.add(TMACoreObject.class);
		return parentClasses;
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		
		ImageServer<BufferedImage> server = imageData.getServer();
//		double fillRadiusTemp;
//		if (server != null && server.hasPixelSizeMicrons()) {
//			fillRadiusTemp = getParameterList(imageData).getDoubleParameterValue("tumourHoleDiameterMicrons") / 5; // No need to normalize further because we work at 1 micron per pixel
//		}
//		else {
//			fillRadiusTemp = getParameterList(imageData).getDoubleParameterValue("tumourHoleDiameterPixels") / 5;
//		}
//		final double fillRadius = fillRadiusTemp;
		final double pixelSize = imageData.getServer().hasPixelSizeMicrons() ? imageData.getServer().getAveragedPixelSizeMicrons() : 1;
		tasks.add(new Runnable() {

			@Override
			public void run() {
				try {
					if (!parentObject.hasChildren())
						return;
					
					// Create a binary image of tumour region
					ROI roi = parentObject.getROI();
					double downsample = Math.max(1.0, 1.0 / pixelSize);
					int w = (int)Math.ceil(roi.getBoundsWidth() / downsample);
					int h = (int)Math.ceil(roi.getBoundsHeight() / downsample);
					BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
					PathClass tumor = PathClassFactory.getPathClass("Tumor");
					Graphics2D g2d = img.createGraphics();
					if (downsample != 1)
						g2d.scale(1.0/downsample, 1.0/downsample);
					g2d.translate(-roi.getBoundsX(), -roi.getBoundsY());
					BasicStroke stroke = new BasicStroke((float)(downsample * 2)); // Effectively a dilation...
					g2d.setStroke(stroke);
					g2d.setColor(Color.WHITE);
//					int nCells = 0;
					for (PathObject pathObject : PathObjectTools.getFlattenedObjectList(parentObject, null, false)) {
						if (pathObject instanceof PathCellObject && tumor.equals(pathObject.getPathClass())) {
							Shape shape = PathROIToolsAwt.getShape(((PathCellObject)pathObject).getROI());
							g2d.fill(shape);
							g2d.draw(shape);
//							PathHierarchyPaintingHelper.paintROI(((PathCellObject)pathObject).getROI(), g2d, Color.WHITE, stroke, Color.WHITE);
//							nCells++;
						}
					}
					g2d.dispose();
					
					// Remove small areas of tumour cells
					double minPixels = 2500;
					ByteProcessor bp = new ByteProcessor(img);
					bp.resetMinAndMax();
					ROILabeling.removeSmallAreas(bp, minPixels, true);
					PathClass tumorDisplayClass = PathClassFactory.getPathClass("Tumor Region", ColorTools.makeRGB(160, 0, 0));
					List<PathObject> removeObjects = new ArrayList<>();
					for (PathObject temp : parentObject.getChildObjects()) {
						if (temp instanceof PathAnnotationObject && tumorDisplayClass.equals(temp.getPathClass()))
							removeObjects.add(temp);
					}
					if (!removeObjects.isEmpty())
						parentObject.removePathObjects(removeObjects);
					
					bp.resetRoi();
//					System.out.println("MEAN: " + bp.getStatistics());
					ROI roiTumour = null;
					if (bp.getStatistics().max > 0) {
						RankFilters rf = new RankFilters();
						rf.rank(bp, 5, RankFilters.MAX);
						rf.rank(bp, 5, RankFilters.MIN);
						bp.setThreshold(128, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
						Roi roiTumourSelected = new ThresholdToSelection().convert(bp);
						Calibration cal = new Calibration();
						cal.xOrigin = -roi.getBoundsX()/downsample;
						cal.yOrigin = -roi.getBoundsY()/downsample;
						roiTumour = ROIConverterIJ.convertToPathROI(roiTumourSelected, cal, downsample, -1, roi.getZ(), roi.getT());
						roiTumour = ShapeSimplifierAwt.simplifyShape((PathShape)roiTumour, 10);
						parentObject.addPathObject(new PathAnnotationObject(roiTumour, tumorDisplayClass));
						logger.trace("Added object!");
						
						imageData.getHierarchy().fireHierarchyChangedEvent(this);
					}
					
					double maxDistanceToTumour = 1000;
					
					FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, (byte)255, false);
					
					PathClass immuno = PathClassFactory.getPathClass("Immune cells");
					int rgb = immuno.getColor();
					double closeToTumorThreshold = 25;
					double colorScale = 1.5;
					rgb = ColorTools.makeRGB(
							(int)Math.min(255, (ColorTools.red(rgb)*colorScale)),
							(int)Math.min(255, (ColorTools.green(rgb)*colorScale)),
							(int)Math.min(255, (ColorTools.blue(rgb)*colorScale)));
					PathClass immunoClose = PathClassFactory.getDerivedPathClass(immuno, "Immune cells (near tumor)", rgb);
					immunoClose.setColor(rgb);
					int closeCount = 0;
					
					for (PathObject pathObject : PathObjectTools.getFlattenedObjectList(parentObject, null, false)) {
						MeasurementList ml = pathObject.getMeasurementList();
						if (pathObject instanceof PathCellObject && (immuno.equals(pathObject.getPathClass())) || immunoClose.equals(pathObject.getPathClass())) {
							ROI roi2 = ((PathCellObject)pathObject).getNucleusROI();
							if (roi2 == null)
								roi2 = pathObject.getROI();
							double x = (roi2.getCentroidX() - roi.getBoundsX()) / downsample;
							double y = (roi2.getCentroidY() - roi.getBoundsY()) / downsample;
							double distance = fpEDM.getf((int)(x+.5), (int)(y+.5)) * downsample * pixelSize;
														
//							// Cleaning up when I've made a mess...
//							while (ml.containsNamedMeasurement("Distance to tumour"))
//								ml.removeMeasurements("Distance to tumour");
							ml.putMeasurement("Distance to tumour", Math.min(distance, maxDistanceToTumour));
							ml.closeList();
							
							// Reclassify?
							if (distance < closeToTumorThreshold) {
								pathObject.setPathClass(immunoClose);
								closeCount++;
							}
							else
								pathObject.setPathClass(immuno);
							
						} else if (ml.containsNamedMeasurement("Distance to tumour")) {
							ml.removeMeasurements("Distance to tumour");
							ml.closeList();							
						}
					}
					
					// Measure number of 'close' immune cells per tumour mm^2
					if (server.hasPixelSizeMicrons()) {
						double tumourArea = (!(roiTumour instanceof PathArea)) ? 0 : ((PathArea)roiTumour).getScaledArea(server.getPixelWidthMicrons(), server.getPixelHeightMicrons());						
						double proportion = closeCount / (tumourArea / (1000 * 1000));
						parentObject.getMeasurementList().putMeasurement("Nearby cell per tumour mm^", proportion);
					}
					
					
					
//					fpEDM.resetMinAndMax();
//					new ImagePlus("Image", fpEDM).show();
					

				} catch (Exception e) {
					e.printStackTrace();
					throw(e);
				}
			}
		});
	}
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		ImageServer<? extends BufferedImage> server = imageData.getServer();
		boolean pixelSizeMicrons = server != null && server.hasPixelSizeMicrons();
		params.getParameters().get("tumourHoleDiameterMicrons").setHidden(!pixelSizeMicrons);
		params.getParameters().get("tumourHoleDiameterPixels").setHidden(pixelSizeMicrons);
		return params;
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
		List<PathObject> parents = new ArrayList<>();
		if (hierarchy.getTMAGrid() != null) {
			for (TMACoreObject core : hierarchy.getTMAGrid().getTMACoreList()) {
				if (core.hasChildren())
					parents.add(core);
			}
		}
//		// TODO: As a temporary workaround, permit a single selected annotation to be used as well
//		if (parents.isEmpty() && hierarchy.getSelectionModel().getSelectedPathObject() instanceof PathAnnotationObject) {
//			parents.add(hierarchy.getSelectionModel().getSelectedPathObject());
//		}
		return parents;
	}
	

}