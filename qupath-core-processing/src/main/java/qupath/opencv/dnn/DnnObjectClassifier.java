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

package qupath.opencv.dnn;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.IntFunction;

import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import qupath.lib.classifiers.object.AbstractObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.UriResource;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;


/**
 * Initial implementation of a patch-based {@link ObjectClassifier} using an OpenCV-compatible DNN.
 * <p>
 * <b>Warning!</b> This implementation is likely to change in the future.
 * 
 * @author Pete Bankhead
 * @param <T> class of the blob used by the deep learning model
 * @version 0.3.0
 */
public class DnnObjectClassifier<T> extends AbstractObjectClassifier<BufferedImage> implements UriResource {
	
	private static final Logger logger = LoggerFactory.getLogger(DnnObjectClassifier.class);
	
	private DnnModel<T> model;
	private List<PathClass> pathClasses;
	private double requestedPixelSize = 1.0;
	private int width, height;
	
	boolean preferNucleus = true;
	private int batchSize = 4;
	
	@Override
	public Collection<PathClass> getPathClasses() {
		return Collections.unmodifiableList(pathClasses);
	}	

	/**
	 * Constructor.
	 * @param filter filter to select compatible options
	 * @param model wrapper for the DNN model, including optional preprocessing
	 * @param pathClasses ordered list of classifications, corresponding to the predicted labels
	 * @param width patch width, in pixels, at the classification size
	 * @param height patch height, in pixels, at the classification side
	 * @param requestedPixelSize requested pixel size, in calibrated units, used to calculate the downsample value
	 */
	public DnnObjectClassifier(PathObjectFilter filter, DnnModel<T> model, List<PathClass> pathClasses, int width, int height, double requestedPixelSize) {
		super(filter);
		this.model = model;
		this.pathClasses = new ArrayList<>(pathClasses);
		this.width = width;
		this.height = height;
		this.requestedPixelSize = requestedPixelSize;
	}

	@Override
	public int classifyObjects(ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects,
			boolean resetExistingClass) {
		
		var server = imageData.getServer();
		double ds;
		if (Double.isFinite(requestedPixelSize) && requestedPixelSize > 0) {
			ds = requestedPixelSize / server.getPixelCalibration().getAveragedPixelSize().doubleValue();
		} else {
			ds = 1.0;
		}
		double downsample = ds;
		
		// Use current pool
		var pool = ForkJoinPool.commonPool();
		var futures = new ArrayList<ForkJoinTask<Integer>>();
		for (var list : Lists.partition(Lists.newArrayList(pathObjects), Math.max(1, batchSize))) {
			futures.add(pool.submit(() -> tryToClassify(list, server, downsample, i -> pathClasses.get(i))));
		}
		int reclassified = 0;
		for (var task : futures) {
			try {
				reclassified += task.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return reclassified;
		
//		return (int)pathObjects.parallelStream().filter(p -> tryToClassify(p, server, downsample, i -> pathClasses.get(i))).count();
	}
	
	protected boolean tryToClassify(PathObject pathObject, ImageServer<BufferedImage> server, double downsample, IntFunction<PathClass> classifier) {
		return tryToClassify(Collections.singletonList(pathObject), server, downsample, classifier) != 0;
	}
	
	/**
	 * Try to classify a batch of objects.
	 * @param pathObjects
	 * @param server
	 * @param downsample
	 * @param classifier
	 * @return number of classified objects
	 */
	protected int tryToClassify(List<? extends PathObject> pathObjects, ImageServer<BufferedImage> server, double downsample, IntFunction<PathClass> classifier) {
		int count = 0;
		try {			
			Mat[] inputImages = new Mat[pathObjects.size()];
			int n = pathObjects.size();
			int i = 0;
			for (var pathObject : pathObjects) {
				var roi = PathObjectTools.getROI(pathObject, preferNucleus);
				if (roi == null) {
					logger.warn("Cannot classify an object without a ROI!");
					return 0;
				}
				Mat input = DnnTools.readPatch(server, roi, downsample, width, height);
				inputImages[i] = input;
				i++;
			}
			// TODO: Consider using batchConvertAndPredict instead
			
			var output = model.batchConvertAndPredict(inputImages);
//			var blob = model.getBlobFunction().toBlob(inputImages);
//			var prediction = model.getPredictionFunction().call(blob);
//			var output = model.getBlobFunction().fromBlob(prediction);
			
			assert output.size() == n;
			
			// Loop through objects and set classification
			for (i = 0; i < n; i++) {
				var idx = output.get(i).createIndexer();
				var sizes = idx.sizes();
				int dim = 0;
				// Find first matching dimension for number of classes
				int nClasses = pathClasses.size();
				while (dim < sizes.length) {
					if (sizes[dim] == nClasses)
						break;
					dim++;
				}
				if (dim == sizes.length) {
					if (nClasses == 1)
						logger.error("Unable to find classification axis in output! Sizes {} for single class", Arrays.toString(sizes));
					else
						logger.error("Unable to find classification axis in output! Sizes {} for {} classes", Arrays.toString(sizes), nClasses);
					throw new IllegalArgumentException("Unable to find classification axis in prediction output!");
				}
				
				var pathObject = pathObjects.get(i);
				
				// Get dimensions array
				long[] inds = sizes;
				Arrays.fill(inds, 0L);

				double maxPred = Double.NEGATIVE_INFINITY;
				int maxPredInd = -1;
				for (int d = 0; d < nClasses; d++) {
					inds[dim] = d;
					double pred = idx.getDouble(inds);
					if (pred > maxPred) {
						maxPred = pred;
						maxPredInd = d;
					}
				}
				var pathClassOld = pathObject.getPathClass();
				var pathClassNew = pathClasses.get(maxPredInd);
				if (pathClassOld != pathClassNew) {
					pathObject.setPathClass(pathClassNew);
					count++;
				}
			}
		} catch (IOException e) {
			logger.warn("Error classifying object: " + e.getLocalizedMessage(), e);
		}
		return count;
	}
	

	@Override
	public Map<String, Integer> getMissingFeatures(ImageData<BufferedImage> imageData,
			Collection<? extends PathObject> pathObjects) {
		// No missing features - we get them from the image
		return Collections.emptyMap();
	}

	@Override
	public Collection<URI> getURIs() throws IOException {
		if (model instanceof UriResource)
			return ((UriResource)model).getURIs();
		return Collections.emptyList();
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		if (model instanceof UriResource)
			return ((UriResource)model).updateURIs(replacements);
		return false;
	}

}
