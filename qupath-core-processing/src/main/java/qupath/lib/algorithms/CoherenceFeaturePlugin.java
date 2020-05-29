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

package qupath.lib.algorithms;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to calculate coherence features for image tiles.
 * 
 * @author Pete Bankhead
 *
 */
public class CoherenceFeaturePlugin extends AbstractInteractivePlugin<BufferedImage> {
	
	private ParameterList params;
	
	/**
	 * Default constructor.
	 */
	public CoherenceFeaturePlugin() {
		
		params = new ParameterList().
				addDoubleParameter("magnification", "Magnification", 5).
				addChoiceParameter("stainChoice", "Stains", "Optical density", Arrays.asList("Optical density", "H-DAB", "H&E", "H-DAB (8-bit)", "H&E (8-bit)", "RGB", "Grayscale"));
		
		params.addDoubleParameter("tileSizeMicrons", "Tile diameter", 25, GeneralTools.micrometerSymbol(), "Size of image tile within which to calculate coherence");
		params.addDoubleParameter("tileSizePx", "Tile diameter", 200, "px (full resolution image)", "Size of image tile within which to calculate coherence");

		params.addBooleanParameter("includeStats", "Include basic statistics", true).
				addBooleanParameter("doCircular", "Use circular tiles", false);
	}
	
	
	
	static ImmutableDimension getPreferredTileSizePixels(final ImageServer<BufferedImage> server, final ParameterList params) {
		// Determine tile size
		int tileWidth, tileHeight;
		if (server.getPixelCalibration().hasPixelSizeMicrons()) {
			double tileSize = params.getDoubleParameterValue("tileSizeMicrons");
			tileWidth = (int)(tileSize / server.getPixelCalibration().getPixelWidthMicrons() + .5);
			tileHeight = (int)(tileSize / server.getPixelCalibration().getPixelHeightMicrons() + .5);
		} else {
			tileWidth = (int)(params.getDoubleParameterValue("tileSizePx") + .5);
			tileHeight = tileWidth;
		}
		return ImmutableDimension.getInstance(tileWidth, tileHeight);
	}
	
	static String getDiameterString(final ImageServer<BufferedImage> server, final ParameterList params) {
		if (server.getPixelCalibration().hasPixelSizeMicrons())
			return String.format("%.1f %s", params.getDoubleParameterValue("tileSizeMicrons"), GeneralTools.micrometerSymbol());
		else
			return String.format("%d px", (int)(params.getDoubleParameterValue("tileSizePx") + .5));
	}
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		final ParameterList params = getParameterList(imageData);
		final ImageServer<BufferedImage> server = imageData.getServer();
		tasks.add(new CoherenceRunnable(server, parentObject, params, imageData.getColorDeconvolutionStains()));
	}
	
	
	
	static class CoherenceRunnable implements Runnable {
		
		private static Logger logger = LoggerFactory.getLogger(CoherenceRunnable.class);
		
		private ImageServer<BufferedImage> server;
		private ParameterList params;
		private PathObject parentObject;
		private ColorDeconvolutionStains stains;
		
		public CoherenceRunnable(final ImageServer<BufferedImage> server, final PathObject parentObject, final ParameterList params, final ColorDeconvolutionStains stains) {
			this.server = server;
			this.parentObject = parentObject;
			this.params = params;
			this.stains = stains;
		}

		@Override
		public void run() {
			try {
				processObject(parentObject, params, server, stains);
			} catch (InterruptedException e) {
				logger.warn("Processing interrupted", e);
			} catch (IOException e) {
				logger.error("Unable to get pixels for " + parentObject, e);
			} finally {
				parentObject.getMeasurementList().close();
				server = null;
				params = null;
			}
		}
		
		
		@Override
		public String toString() {
			// TODO: Give a better toString()
			return "Haralick features";
		}
		
	}
	
	
	

	static boolean processObject(final PathObject pathObject, final ParameterList params, final ImageServer<BufferedImage> server, final ColorDeconvolutionStains stains) throws InterruptedException, IOException {
		String stainsName = (String)params.getChoiceParameterValue("stainChoice");
		double mag = params.getDoubleParameterValue("magnification");
		boolean includeStats = params.getBooleanParameterValue("includeStats");
		boolean doCircular = params.getBooleanParameterValue("doCircular");

		double downsample = server.getMetadata().getMagnification() / mag;
		
		ROI pathROI = pathObject.getROI();
		if (pathROI == null)
			return false;
		
		// Get bounds
		ImmutableDimension size = getPreferredTileSizePixels(server, params);
		
		if (size.getWidth() / downsample < 1 || size.getHeight() / downsample < 1)
			return false;
		
//		RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, (int)(pathROI.getCentroidX() + .5) - size.width/2, (int)(pathROI.getCentroidY() + .5) - size.height/2, size.width, size.height, pathROI.getT(), pathROI.getZ());
		
		// Try to align with pixel boundaries according to the downsample being used - otherwise, interpolation can cause some strange, pattern artefacts
		int xStart = (int)((int)(pathROI.getCentroidX() / downsample + .5) * downsample) - size.width/2;
		int yStart = (int)((int)(pathROI.getCentroidY() / downsample + .5) * downsample) - size.height/2;
		int width = Math.min(server.getWidth(), xStart + size.width) - xStart;
		int height = Math.min(server.getHeight(), yStart + size.height) - yStart;
		RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, xStart, yStart, width, height, pathROI.getT(), pathROI.getZ());

//		System.out.println(bounds);
//		System.out.println("Size: " + size);

		BufferedImage img = server.readBufferedImage(region);

		// Get a buffer containing the image pixels
		int w = img.getWidth();
		int h = img.getHeight();
		int[] buf = img.getRGB(0, 0, w, h, null, 0, w);

		// Create a color transformer to get the images we need
		float[] pixels = new float[buf.length];
		SimpleModifiableImage pxImg = SimpleImages.createFloatImage(pixels, w, h);
		
		MeasurementList measurementList = pathObject.getMeasurementList();
		
		String postfix = " (" + getDiameterString(server, params) + ")";

		if (stainsName.equals("H-DAB")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_DAB, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "DAB"+postfix, ColorTransformer.ColorTransformMethod.DAB_H_DAB, stains, includeStats, doCircular);
		}
		else if (stainsName.equals("H&E")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_E, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Eosin"+postfix, ColorTransformer.ColorTransformMethod.Eosin_H_E, stains, includeStats, doCircular);
		} else if (stainsName.equals("H-DAB (8-bit)")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_DAB_8_bit, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "DAB 8-bit"+postfix, ColorTransformer.ColorTransformMethod.DAB_H_DAB_8_bit, stains, includeStats, doCircular);
		}
		else if (stainsName.equals("H&E (8-bit)")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_E_8_bit, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Eosin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Eosin_H_E_8_bit, stains, includeStats, doCircular);
		} else if (stainsName.equals("Optical density")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "OD sum"+postfix, ColorTransformer.ColorTransformMethod.Optical_density_sum, stains, includeStats, doCircular);
		} else if (stainsName.equals("RGB")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Red"+postfix, ColorTransformer.ColorTransformMethod.Red, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Green"+postfix, ColorTransformer.ColorTransformMethod.Green, stains, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Blue"+postfix, ColorTransformer.ColorTransformMethod.Blue, stains, includeStats, doCircular);
		} else if (stainsName.equals("Grayscale")) {
			processTransformedImage(pxImg, buf, pixels, measurementList, "Grayscale"+postfix, ColorTransformer.ColorTransformMethod.RGB_mean, stains, includeStats, doCircular);
		}
		measurementList.close();
		
		return true;
		
	}
	
	
	static void processTransformedImage(SimpleModifiableImage pxImg, int[] buf, float[] pixels, MeasurementList measurementList, String name, ColorTransformer.ColorTransformMethod method, ColorDeconvolutionStains stains, boolean includeStats, boolean doCircular) {
		ColorTransformer.getTransformedPixels(buf, method, pixels, stains);
		
		if (doCircular) {
			double w = pxImg.getWidth();
			double h = pxImg.getHeight();
			double cx = (w-1) / 2;
			double cy = (h-1) / 2;
			double radius = Math.max(w, h) * .5;
			double distThreshold = radius * radius;
//			int count = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if ((cx - x)*(cx - x) + (cy - y)*(cy - y) > distThreshold)
						pxImg.setValue(x, y, Float.NaN);
//					else
//						count++;
				}			
			}
//			System.out.println("Masked count: " + count + " for dimension " + w + ", " + h);
		}
		
		
		if (includeStats)
			addBasicStatistics(pxImg, measurementList, name);
		addCoherenceFeature(computeCoherence(pxImg), measurementList, name);
	}
	
	

	static void addBasicStatistics(final SimpleImage img, final MeasurementList measurementList, final String name) {
		RunningStatistics stats = StatisticsHelper.computeRunningStatistics(img);
		measurementList.putMeasurement(name + " Mean", stats.getMean());
		measurementList.putMeasurement(name + " Min", stats.getMin());
		measurementList.putMeasurement(name + " Max", stats.getMax());
		measurementList.putMeasurement(name + " Range", stats.getRange());
		measurementList.putMeasurement(name + " Std.dev.", stats.getStdDev());
		
//		measurementList.putMeasurement(String.format("%s Mean", name), stats.getMean());
//		measurementList.putMeasurement(String.format("%s Min", name), stats.getMin());
//		measurementList.putMeasurement(String.format("%s Max", name), stats.getMax());
//		measurementList.putMeasurement(String.format("%s Range", name), stats.getRange());
//		measurementList.putMeasurement(String.format("%s Std.dev.", name), stats.getStdDev());
	}

	
	static double computeCoherence(final SimpleImage img) {
		
		int w = img.getWidth();
		int h = img.getHeight();
		
		// Compute partial derivatives
//		SimpleModifiableImage fy = new FloatArraySimpleImage(w, h);
//		SimpleModifiableImage fx = new FloatArraySimpleImage(w, h);
//		double numerator = 0;
//		double denominator = 0;
		double scale = 1.0/((w-2.)*(h-2.));
		double fxx = 0;
		double fyy = 0;
		double fxy = 0;
		for (int y = 1; y < h-1; y++) {
			for (int x = 1; x < w-1; x++) {
				double dx = (img.getValue(x+1, y) - img.getValue(x-1, y))/2;
				double dy = (img.getValue(x, y+1) - img.getValue(x, y-1))/2;
				if (Double.isNaN(dx) || Double.isNaN(dy))
					continue;
				fxx += scale * dx * dx;
				fyy += scale * dy * dy;
				fxy += scale * dx * dy;
			}
		}
		
		// Determine eigenvalues
		double trace = fxx + fyy;
		double det = fxx*fyy - fxy*fxy;
		double l1 = trace/2 + Math.sqrt(trace*trace/4 - det);
		double l2 = trace/2 - Math.sqrt(trace*trace/4 - det);
		if (l1 == l2)
			return 0;
		
		double ratio = (l1 - l2) / (l1 + l2);
		return ratio*ratio;
		
//		denominator = (fxx + fyy);
//		numerator = ((fyy - fxx)*(fyy - fxx) + 4*fxy);
//
//		System.out.println(numerator);
//		return Math.sqrt(numerator) / denominator;
////		return numerator / (denominator * denominator);
	}
	
	
	static void addCoherenceFeature(final double value, final MeasurementList measurementList, final String name) {
		measurementList.putMeasurement(String.format("%s coherence", name), value);
	}



	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.getParameters().get("tileSizeMicrons").setHidden(!hasMicrons);
		params.getParameters().get("tileSizePx").setHidden(hasMicrons);
		return params;
	}

	@Override
	public String getName() {
		return "Add coherence features";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public String getDescription() {
		return "Add coherence features to existing object measurements";
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		return runner.getImageData().getHierarchy().getDetectionObjects();
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		List<Class<? extends PathObject>> parents = new ArrayList<>();
		parents.add(PathDetectionObject.class);
		return parents;
	}

}
