/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapType;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

/**
 * An {@link ImageDataOp} that generates pixels from the objects present in the {@link ImageData}.
 * 
 * @author Pete Bankhead
 */
class DensityMapDataOp implements ImageDataOp {
	
	private static final Logger logger = LoggerFactory.getLogger(DensityMapDataOp.class);
	
	static {
		ImageOps.registerDataOp(DensityMapDataOp.class, "data.op.density");
	}
	
	private DensityMapType densityType;
	private int radius;
	
	private Map<String, PathObjectPredicate> primaryObjects;
	private PathObjectPredicate allObjects;
	
	// These could be transient, since they can be built using the other fields
	private transient ImageOp op;
	private transient List<ImageChannel> channels;
	
	/**
	 *  * This involves filters (predicates) for:
	 * <ul>
	 *   <li><b>All objects:</b> identifying all objects under consideration (e.g. all detections, all cells, tumor cells)</li>
	 *   <li><b>Primary objects:</b> identifying one or more subsets of 'all objects' where the density is of interest (e.g. Positive cells)</li>
	 * </ul>
	 * The output is an image where each channel provides a relevant density value.
	 * The channels are ordered so densities of primary object filters are given first, and all objects last.
	 * 
	 * @param radius the radius (in downsampled pixel units) within which densities should be calculated
	 * @param primaryObjects zero or more primary object filters, with an associated name (used for the channel name)
	 * @param allObjects a single all objects filter to identify all objects of interest
	 * @param densityType the density map type, which defines how object counts within the defined radius are converted into density values
	 */
	public DensityMapDataOp(
			int radius,
			Map<String, PathObjectPredicate> primaryObjects,
			PathObjectPredicate allObjects,
			DensityMapType densityType) {
		
		Objects.requireNonNull(densityType);
		if (radius < 0)
			throw new IllegalArgumentException("Density map radius must be >= 0!");
		
		this.primaryObjects = new LinkedHashMap<>(primaryObjects);
		this.allObjects = allObjects;
		this.densityType = densityType;
		this.radius = radius;
		
		ensureInitialized();
	}
	
	private void ensureInitialized() {
		if (op != null)
			return;
		synchronized(this) {
			if (op == null)
				buildOpAndChannels();
		}
	}
	
	
	private void buildOpAndChannels() {
		
		logger.trace("Building density map op with type {}", densityType);
		
		String baseChannelName;
		ImageChannel lastChannel = null;
		List<ImageOp> sequentialOps = new ArrayList<>();
		switch (densityType) {
		case GAUSSIAN:
			if (radius > 0) {
				double sigma = radius;
				sequentialOps.add(
						ImageOps.Filters.gaussianBlur(sigma)
						);
				// Scale so that central value ~1 - this is a closer match to the alternative sum filter
				sequentialOps.add(
						ImageOps.Core.multiply(2 * Math.PI * sigma * sigma)
						);
			}
			baseChannelName = "Gaussian weighted counts ";
			break;
		case PERCENT:
			int[] extractInds = IntStream.range(0, primaryObjects.size()).toArray();
			
			if (radius > 0) {
				sequentialOps.add(ImageOps.Filters.sum(radius));
				sequentialOps.add(ImageOps.Core.round());
			}
			
			if (extractInds.length > 0) {
				// Duplicate the image, then
				// - On the first duplicate, remove the last channel and divide all the other channels by its values
				// - On the second duplicate, extract the last channel (so as to retain its values)
				// Finally merge the last channel back
				// The outcome should be that the last channel is unchanged, while the other channels are divided by the last 
				// channel elementwise.
				sequentialOps.addAll(Arrays.asList(
						ImageOps.Core.splitMerge(
								ImageOps.Core.sequential(
									ImageOps.Core.splitDivide(
											ImageOps.Channels.extract(extractInds),
											ImageOps.Core.sequential(
													ImageOps.Channels.extract(extractInds.length),
													ImageOps.Channels.repeat(extractInds.length)
													)
											),
									ImageOps.Core.multiply(100.0) // Convert to percent
									),
							ImageOps.Channels.extract(extractInds.length)
							)
						)
						);
			} else {
				// Values will be 1 and NaN (a possibly-zero value being divided by itself)
				sequentialOps.add(
						ImageOps.Core.splitDivide(
								null,
								null
								)
						);
			}
			
			baseChannelName = "";
			if (!primaryObjects.isEmpty())
				lastChannel = ImageChannel.getInstance(DensityMaps.CHANNEL_ALL_OBJECTS, null);
			break;
		case SUM:
		default:
			if (radius > 0) {
				sequentialOps.add(ImageOps.Filters.sum(radius));
				sequentialOps.add(ImageOps.Core.round());
			}
			baseChannelName = "";
		}

		var channelNames = primaryObjects.keySet().stream().map(n -> baseChannelName + n).toArray(String[]::new);
		var channels = new ArrayList<>(ImageChannel.getChannelList(channelNames));
		if (lastChannel != null)
			channels.add(lastChannel);
		if (channels.isEmpty())
			this.channels = Collections.singletonList(ImageChannel.getInstance(DensityMaps.CHANNEL_ALL_OBJECTS, null));
		else
			this.channels = Collections.unmodifiableList(channels);		
		sequentialOps.add(
				ImageOps.Core.ensureType(PixelType.FLOAT32)
				);
		this.op = ImageOps.Core.sequential(sequentialOps);
	}
	
	
	private int getChannelCount() {
		if (primaryObjects.size() == 0)
			return 1;
		if (densityType == DensityMapType.PERCENT)
			return primaryObjects.size() + 1;
		return primaryObjects.size();
	}

	@Override
	public Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		
		ensureInitialized();
		
		logger.trace("Applying density map op for {}", request);
		
		// Calculate how much padding we need
		var padding = op.getPadding();
		if (!padding.isEmpty()) {
			// Add padding to the request
			double downsample = request.getDownsample();
			var padding2 = Padding.getPadding(
					(int)Math.round(padding.getX1() * downsample),
					(int)Math.round(padding.getX2() * downsample),
					(int)Math.round(padding.getY1() * downsample),
					(int)Math.round(padding.getY2() * downsample)
					);
			request = request.pad2D(padding2);
		}
		
		// Get all objects within the padded region
		var allPathObjects = imageData.getHierarchy().getObjectsForRegion(null, request, null)
				.stream()
				.filter(allObjects)
				.collect(Collectors.toList());
		
		// TODO: Consider if we can optimize things when there are no objects
//		if (allPathObjects.isEmpty())
//			return null;
		
		if (allPathObjects.size() == 1)
			logger.trace("Generating counts tile for 1 object");
		else
			logger.trace("Generating counts tile for {} objects", allPathObjects.size());


		// Create an output mat
		int nChannels = getChannelCount();
		int width = (int)Math.round(request.getWidth() / request.getDownsample());
		int height = (int)Math.round(request.getHeight() / request.getDownsample());
		var mat = new Mat(height, width, opencv_core.CV_64FC(nChannels), Scalar.ZERO);
		DoubleIndexer idx = mat.createIndexer();
		
		// Get points representing all the centroids of each subpopulation of object
		// Use these to increment pixel values in a counts image
		int c = 0;
		for (var entry : primaryObjects.entrySet()) {
			var predicate = entry.getValue();
			var primaryROIs = allPathObjects
					.stream()
					.filter(predicate)
					.map(p -> PathObjectTools.getROI(p, true))
					.collect(Collectors.toList());
			
			var points = objectsToPoints(primaryROIs);
			incrementCounts(idx, points, request, width, height, c);
			c++;
		}
		
		// Get points for all objects, if we need them
		if (c < getChannelCount()) {
			var allObjectROIs = allPathObjects
					.stream()
					.map(p -> PathObjectTools.getROI(p, true))
					.collect(Collectors.toList());
			var points = objectsToPoints(allObjectROIs);
			incrementCounts(idx, points, request, width, height, c);
			c++;
		}
		idx.close();
		
		// Now apply the op
		var output = this.op.apply(mat);
		return output;
	}
	
	
	private static long incrementCounts(DoubleIndexer idx, List<Point2> points, RegionRequest request, int width, int height, int channel) {
		if (points.isEmpty())
			return 0;
		
		double offsetX = request.getX();
		double offsetY = request.getY();
		double downsample = request.getDownsample();
		
		long count = 0;
		
		for (var p : points) {
			int x = (int)((p.getX() - offsetX) / downsample);
			int y = (int)((p.getY() - offsetY) / downsample);
			if (x >= 0 && y >= 0 && x < width && y < height) {
				idx.put(y, x, channel, idx.get(y, x, channel) + 1);
				count++;
			}
		}
		return count;
	}
	
	
	static List<Point2> objectsToPoints(Collection<? extends ROI> roisToPoints) {
		if (roisToPoints.isEmpty())
			return Collections.emptyList();
		
		List<Point2> points = new ArrayList<>();
		for (var roi : roisToPoints) {
			if (roi.isPoint())
				points.addAll(roi.getAllPoints());
			else {
				points.add(new Point2(roi.getCentroidX(), roi.getCentroidY()));
			}
		}
		return points;
	}
	

	@Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
		// All images are supported
		return true;
	}

	/**
	 * Get the channels. In this case, the {@link ImageData} is irrelevant and may be null.
	 */
	@Override
	public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
		return getChannels();
	}
	
	/**
	 * The {@link ImageData} is really irrelevant to the channels.
	 * @return
	 */
	List<ImageChannel> getChannels() {
		ensureInitialized();
		return channels;
	}

	@Override
	public ImageDataOp appendOps(ImageOp... ops) {
		if (ops.length == 0)
			return this;
		ImageOp opNew;
		if (ops.length > 1)
			opNew = ImageOps.Core.sequential(ops);
		else
			opNew = ops[0];
		
		var dataOp = new DensityMapDataOp(radius, primaryObjects, allObjects, densityType);
		dataOp.op = ImageOps.Core.sequential(dataOp.op, opNew);
		dataOp.channels = opNew.getChannels(dataOp.channels);
		return dataOp;
	}

	@Override
	public PixelType getOutputType(PixelType inputType) {
		return PixelType.FLOAT32;
	}
	
	@Override
	public Collection<URI> getURIs() throws IOException {
		return op == null ? Collections.emptyList() : op.getURIs();
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		if (op == null)
			return false;
		return op.updateURIs(replacements);
	}
	

}
