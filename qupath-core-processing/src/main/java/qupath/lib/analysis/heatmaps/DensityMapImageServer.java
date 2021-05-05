/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.analysis.heatmaps;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapNormalization;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.GeneratingImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

/**
 * An {@link ImageServer} that can be used to display the density of objects.
 * 
 * @author Pete Bankhead
 */
public class DensityMapImageServer extends AbstractTileableImageServer implements GeneratingImageServer<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapImageServer.class);
	
	/**
	 * Default maximum size in any dimension for the map.
	 */
	private static int defaultMaxSize = 1024;
	
	private ImageServerMetadata originalMetadata;
	
	private List<PointsWithPlane> primaryPoints;
	private PointsWithPlane denominatorPoints;
	
	// This is the radius of the filter actually used, at the resolution where it is computed
	private double downsampledRadiusPixels;
	
	// Downsampled pixel area; this is calculated during construction and not impacted by later pixel size changes
	private double downsampledPixelArea;
	
	private DensityMapNormalization normalization;
	private ColorModel colorModel;
	
	
	static Map<ImagePlane, List<Point2>> objectsToPoints(Collection<? extends ROI> roisToPoints) {
		Map<ImagePlane, List<Point2>> map = new HashMap<>();
		for (var roi : roisToPoints) {
			var list = map.computeIfAbsent(roi.getImagePlane(), n -> new ArrayList<>());
			if (roi.isPoint())
				list.addAll(roi.getAllPoints());
			else {
				list.add(new Point2(roi.getCentroidX(), roi.getCentroidY()));
			}
		}
		return map;
	}
	
	
	static DensityMapImageServer createDensityMap(
			ImageData<BufferedImage> imageData,
			double pixelSize,
			double radius,
			Map<String, Predicate<PathObject>> primaryObjects,
			Predicate<PathObject> allObjects,
			DensityMapNormalization normalization,
			ColorModel colorModel
			) {
		
		if (primaryObjects == null) {
			throw new IllegalArgumentException("Primary object filter is not defined - no densities can be calculated!");
		}

		var hierarchy = imageData.getHierarchy();
		var flattenedObjects = hierarchy.getFlattenedObjectList(null);
		
		if (pixelSize <= 0) {
			int maxSize = defaultMaxSize;
			var server = imageData.getServer();
			double actualPixelSize = server.getPixelCalibration().getAveragedPixelSize().doubleValue();
			
			double maxDownsample = Math.round(Math.max(1, Math.max(server.getWidth(), server.getHeight()) / maxSize));
			double minPixelSize = maxDownsample * actualPixelSize;
			if (radius > 0) {
				double radiusPixels = radius / actualPixelSize;
				double radiusDownsample = Math.round(radiusPixels / 10);
				pixelSize = radiusDownsample * actualPixelSize;
			}
			if (pixelSize < minPixelSize)
				pixelSize = minPixelSize;
		}
		
		List<PointsWithPlane> primaryPoints = new ArrayList<>();
		PointsWithPlane allPoints = null;
		
		// Get all the objects we can consider
		var allPathObjects = flattenedObjects
				.stream()
				.filter(p -> p.hasROI())
				.filter(allObjects)
				.collect(Collectors.toList());
		
		// Get points representing the centroids of all objects we can access
		var allROIs = allPathObjects
				.stream()
				.map(p -> PathObjectTools.getROI(p, true))
				.collect(Collectors.toList());
		allPoints = new PointsWithPlane("Counts", objectsToPoints(allROIs));

		// Get points representing all the centroids of each subpopulation of object
		for (var entry : primaryObjects.entrySet()) {
			var predicate = entry.getValue();
			var primaryROIs = allPathObjects
					.stream()
					.filter(predicate)
					.map(p -> PathObjectTools.getROI(p, true))
					.collect(Collectors.toList());
			primaryPoints.add(new PointsWithPlane(entry.getKey(), objectsToPoints(primaryROIs)));
		}
							
		var server = imageData.getServer();
		
		return new DensityMapImageServer(server, pixelSize, radius, primaryPoints, allPoints, normalization, colorModel);
	}
	
	
	static class PointsWithPlane {
		
		public final String name;
		public final Map<ImagePlane, List<Point2>> points;
		
		PointsWithPlane(String name, Map<ImagePlane, List<Point2>> points) {
			this.name = name;
			this.points = Collections.unmodifiableMap(new LinkedHashMap<>(points));
		}
		
	}
		
	
	DensityMapImageServer(ImageServer<BufferedImage> server,
			double pixelSize,
			double radius,
			List<PointsWithPlane> primaryPoints,
			PointsWithPlane denominatorPoints,
			DensityMapNormalization normalization,
			ColorModel colorModel) {
		super();

		this.colorModel = colorModel;
		this.normalization = normalization;
		
		this.primaryPoints = primaryPoints;
		this.denominatorPoints = denominatorPoints;
		
		var cal = server.getPixelCalibration();
		double downsample = pixelSize / cal.getAveragedPixelSize().doubleValue();
		var levels = new ImageResolutionLevel.Builder(server.getWidth(), server.getHeight())
			.addLevelByDownsample(downsample)
			.build();
		
//		System.err.println(downsample);
		downsampledRadiusPixels = radius / pixelSize;
		
		downsampledPixelArea = cal.getPixelWidth().doubleValue() * downsample
				* cal.getPixelHeight().doubleValue() * downsample;
		
		int maxTileSize = 4096;
		int tileWidth = Math.min(maxTileSize, levels.get(0).getWidth());
		int tileHeight = Math.min(maxTileSize, levels.get(0).getHeight());
		
		List<String> channelNames = new ArrayList<>();
		for (var p : primaryPoints)
			channelNames.add(p.name);
		channelNames.add("Counts");
		
		List<ImageChannel> channels = ImageChannel.getChannelList(channelNames.toArray(String[]::new));
		
//		if (denominatorPoints == null) {
//			channels = ImageChannel.getChannelList("Density counts");
//		} else {
//			channels = ImageChannel.getChannelList("Density", "Counts");
//		}
//			System.err.println(tileWidth + " x " + tileHeight);
		// Set metadata, using the underlying server as a basis
		this.originalMetadata = new ImageServerMetadata.Builder(server.getOriginalMetadata())
				.preferredTileSize(tileWidth, tileHeight)
				.levels(levels)
				.pixelType(PixelType.FLOAT32)
				.channels(channels)
				.rgb(false)
				.build();
	}
	
	
	/**
	 * Get the effective radius used to calculate the density map, in pixel units from the full resolution image.
	 * This may be different from the radius provided when constructing the density map, which may use calibrated units.
	 * <p>
	 * The value returned here should be consistent, regardless of whether the pixel calibration information 
	 * is adjusted.
	 * @return
	 */
	public double getDensityRadius() {
		return downsampledRadiusPixels * getDownsampleForResolution(0);
	}
	
	/**
	 * Query the normalization strategy for the density map.
	 * @return
	 */
	public DensityMapNormalization getNormalization() {
		return normalization;
	}
	
	
	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		// We don't try anything fine-grained here... just check planes
		if (denominatorPoints != null && denominatorPoints.points.containsKey(request.getPlane()))
			return false;
		for (var primary : primaryPoints) {
			if (primary.points.containsKey(request.getPlane()))
				return false;			
		}
		return true;
	}

	@Override
	public Collection<URI> getURIs() {
		return Collections.emptyList();
//		return server.getURIs();
	}

	@Override
	public String getServerType() {
		return "Density map server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		if (isEmptyRegion(tileRequest.getRegionRequest()))
			return null;
		
		var mat = buildDensityMap(tileRequest.getRegionRequest());
		if (mat == null)
			return null;
		
		var img = OpenCVTools.matToBufferedImage(mat, colorModel);
		mat.close();
		
		return img;
	}

	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		throw new UnsupportedOperationException("Cannot create ServerBuilder for " + getServerType());
	}

	@Override
	protected String createID() {
		return "Density map: " + UUID.randomUUID().toString();
//		return "Density map: " + server.getPath() + ": " + UUID.randomUUID().toString();
//		return "Density map: " + imageData.getServer().getPath() + ": " + GsonTools.getInstance().toJson(params);
	}
	
	
	private Mat buildDensityMap(RegionRequest request) {
		
		int nChannels = primaryPoints.size() + 1;
		int channelNormalize = -1;
		if (normalization == DensityMapNormalization.OBJECTS)
			channelNormalize = nChannels - 1;

		
		var plane = request.getPlane();
		
		double downsample = getDownsampleForResolution(0);
		
		// We need points that fall outside the request but within the filter radius, since they can contribute
		// TODO: Eliminate unnecessary padding (rounding can cause this check to fail)
		int requestPadding;
		if (request.getWidth() >= getWidth() && request.getHeight() >= getHeight()) {
			requestPadding = 0;
		} else {
			requestPadding = (int)Math.ceil(downsampledRadiusPixels * downsample); 
			if (normalization == DensityMapNormalization.GAUSSIAN)
				requestPadding *= 3;
		}
		var requestPadded = request.pad2D(requestPadding, requestPadding);
		
		// Compute width & height for the calculation image & for the target image (which may be the same)
		int width = (int)Math.round(requestPadded.getWidth() / downsample);
		int height = (int)Math.round(requestPadded.getHeight() / downsample);
		
		int targetWidth = (int)Math.round(request.getWidth() / downsample);
		int targetHeight = (int)Math.round(request.getHeight() / downsample);
		
		
		// Create point maps
		var mat = new Mat(height, width, opencv_core.CV_32FC(nChannels), Scalar.ZERO);
		try (FloatIndexer idx = mat.createIndexer()) {
			int c = 0;
			for (var primary : primaryPoints) {
				var points = primary.points.get(plane);
				if (points != null)
					incrementCounts(idx, c, points, downsample, requestPadded.getMinX(), requestPadded.getMinY());
				c++;
			}
			if (denominatorPoints != null) {
				var points = denominatorPoints.points.get(plane);
				incrementCounts(idx, c, points, downsample, requestPadded.getMinX(), requestPadded.getMinY());
			}
		}
		
//		var split = OpenCVTools.splitChannels(mat);
//		System.err.println("Primary sum: " + opencv_core.sumElems(split.get(0)));
//		System.err.println("Denominator sum: " + opencv_core.sumElems(split.get(1)));
		
		
		// Filter points to densities
		int kernelRadius = (int)Math.round(downsampledRadiusPixels);
		switch (normalization) {
//		case AREA:
//			diskFilter(mat, kernelRadius, true);
//			break;
		case GAUSSIAN:
			gaussianFilter(mat, downsampledRadiusPixels);
			break;
		case OBJECTS:
		case NONE:
		default:
			diskFilter(mat, kernelRadius, false);
			break;
		}
		
		// Compute ratios (if required) & apply rounding
		// The rounding is needed because floating point errors can be introduced during the filtering 
		// (probably connected to FFT?) - and int filtering won't necessarily work for all kernel sizes
		int nPrimaryChannels = primaryPoints.size();
		float normalize = 1.0f;
//		boolean roundValues = normalization != DensityMapNormalization.AREA && normalization != DensityMapNormalization.GAUSSIAN;
		boolean roundValues = normalization != DensityMapNormalization.GAUSSIAN;
		try (FloatIndexer idx = mat.createIndexer()) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (channelNormalize >= 0) {
						normalize = idx.get(y, x, channelNormalize);
						if (roundValues) {
							normalize = Math.round(normalize);
							idx.put(y, x, channelNormalize, normalize);
						} else if (Math.abs(normalize) < 1e-4)
							normalize = 0;
					}
					for (int c = 0; c < nPrimaryChannels; c++) {
						if (normalize == 0)
							idx.put(y, x, c, Float.NaN);
						else {
							float val = idx.get(y, x, c);
							if (roundValues)
								val = Math.round(val);
							val = val / normalize;
							idx.put(y, x, c, val);
						}
					}
				}						
			}
		}
		
		// Crop the images if needed
		int xCrop = (width - targetWidth) / 2;
		int yCrop = (height - targetHeight) / 2;
		if (xCrop > 0 && yCrop > 0) {
			var mat2 = OpenCVTools.crop(mat, xCrop, yCrop, targetWidth, targetHeight);
			mat.close();
			mat = mat2;
		}
				
		return mat;
	}
	
	
	private void gaussianFilter(Mat mat, double gaussianSigma) {
		int kSize = (int)Math.ceil(gaussianSigma * 4) * 2 + 1;
		var kernel = opencv_imgproc.getGaussianKernel(kSize, gaussianSigma);
		double maxVal = OpenCVTools.maximum(kernel);
		opencv_core.dividePut(kernel, maxVal);
		opencv_imgproc.sepFilter2D(mat, mat, -1, kernel, kernel);
		kernel.close();
	}
	
	private void diskFilter(Mat mat, int radius, boolean doMean) {
		if (radius <= 0)
			return;
		var kernelCenter = new Mat(radius*2+1, radius*2+1, opencv_core.CV_8UC1, Scalar.WHITE);
		try (UByteIndexer idxKernel = kernelCenter.createIndexer()) {
			idxKernel.put(radius, radius, 0);
		}
		var kernel = new Mat();
		opencv_imgproc.distanceTransform(kernelCenter, kernel, opencv_imgproc.DIST_L2, opencv_imgproc.DIST_MASK_PRECISE);
		opencv_imgproc.threshold(kernel, kernel, radius, 1, opencv_imgproc.THRESH_BINARY_INV);
		if (doMean) {
			// Count nonzero pixels
			double sum = opencv_core.sumElems(kernel).get();
			// Normalize using the area of a pixel
//			sum *= downsampledPixelArea;
			
//			System.err.println(sum.get());
			opencv_core.dividePut(kernel, sum);
//			var newSum = opencv_core.sumElems(kernel);
//			System.err.println("New sum: " + newSum);
		}
		
		// This seems to give weird shapes sometimes
//		var kernelCircle = new Mat(kernelRadius*2+1, kernelRadius*2+1, opencv_core.CV_32FC1, Scalar.ZERO);
//		opencv_imgproc.circle(kernelCircle, new Point(kernelRadius, kernelRadius), kernelRadius, Scalar.ONE, opencv_imgproc.FILLED, opencv_imgproc.FILLED, 0);
		
		// TODO: Note that I'm assuming the constant is 0... but can this be confirmed?!
//		opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, opencv_core.BORDER_REPLICATE);
		opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, opencv_core.BORDER_CONSTANT);
//		OpenCVTools.matToImagePlus("Kernel " + kernelRadius, kernel, kernelCircle).show();
		kernel.close();
		kernelCenter.close();
		
	}
	
	
	private static void incrementCounts(FloatIndexer idx, int channel, List<Point2> points, double downsample, double xOrigin, double yOrigin) {
		long height = idx.size(0);
		long width = idx.size(1);
		for (var p : points) {
			int x = (int)((p.getX() - xOrigin) / downsample);
			int y = (int)((p.getY() - yOrigin) / downsample);
			if (x >= 0 && x < width && y >= 0 && y < height)
				idx.put(y, x, channel, idx.get(y, x, channel) + 1);
		}
	}

}
