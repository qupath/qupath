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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.bytedeco.opencv.opencv_dnn.ClassificationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.object.AbstractObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.UriResource;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.dnn.OpenCVDnn.ModelType;


/**
 * Initial implementation of a patch-based {@link ObjectClassifier} using an OpenCV-compatible DNN.
 * <p>
 * <b>Warning!</b> This implementation may change in the future.
 * 
 * @author Pete Bankhead
 * @version 0.3.0
 */
public class OpenCVModelObjectClassifier extends AbstractObjectClassifier<BufferedImage> implements UriResource {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenCVModelObjectClassifier.class);
	
	private OpenCVDnn model;
	private List<PathClass> pathClasses;
	private double requestedPixelSize = 1.0;
	private int width, height;
	
	private transient ClassificationModel classificationModel;

	@Override
	public Collection<PathClass> getPathClasses() {
		return Collections.unmodifiableList(pathClasses);
	}
	
	private ClassificationModel getModel() {
		if (classificationModel == null) {
			synchronized(this) {
				if (classificationModel == null)
					classificationModel = model.buildModel(ModelType.CLASSIFICATION);
			}
		}
		return classificationModel;
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
	public OpenCVModelObjectClassifier(PathObjectFilter filter, OpenCVDnn model, List<PathClass> pathClasses, int width, int height, double requestedPixelSize) {
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
		
		var model = getModel();
		
		var server = imageData.getServer();
		double ds;
		if (Double.isFinite(requestedPixelSize) && requestedPixelSize > 0) {
			ds = requestedPixelSize / server.getPixelCalibration().getAveragedPixelSize().doubleValue();
		} else {
			ds = 1.0;
		}
		double downsample = ds;
		
		// TODO: Fix this rather horrible approach that relies upon side-effects
		return (int)pathObjects.parallelStream().filter(p -> tryToClassify(model, p, server, downsample, i -> pathClasses.get(i))).count();
	}
	
	
	protected boolean tryToClassify(ClassificationModel model, PathObject pathObject, ImageServer<BufferedImage> server, double downsample, IntFunction<PathClass> classifier) {
		var previousClass = pathObject.getPathClass();
		try {
			return DnnTools.classify(model, pathObject, server, downsample, width, height, classifier, null);
		} catch (IOException e) {
			logger.warn("Error classifying object: " + e.getLocalizedMessage(), e);
			return pathObject.getPathClass() != previousClass;
		}
	}
	

	@Override
	public Map<String, Integer> getMissingFeatures(ImageData<BufferedImage> imageData,
			Collection<? extends PathObject> pathObjects) {
		// No missing features - we get them from the image
		return Collections.emptyMap();
	}

	@Override
	public Collection<URI> getURIs() throws IOException {
		return model.getURIs();
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		return model.updateURIs(replacements);
	}
	
	

}
