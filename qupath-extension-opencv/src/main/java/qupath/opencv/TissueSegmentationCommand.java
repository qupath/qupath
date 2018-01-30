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

package qupath.opencv;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_ml.*;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.PathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.opencv.processing.OpenCVTools;

/**
 * This is an *extremely* rough, very unfinished implementation of an (almost) pixel-based classifier for tissue identification.
 * 
 * It is intended for downsampled RGB whole slide images.
 * 
 * A later implementation would use better features - be they obtained using filters or using Haralick textures.
 * 
 * The biggest limitation is resolution and how this affects the number of features that can be calculated with reasonable 
 * memory requirements.
 * 
 * @author Pete Bankhead
 *
 */
public class TissueSegmentationCommand implements PathCommand, PathObjectHierarchyListener {

	private QuPathGUI qupath;
	
	private ImageData<BufferedImage> imageData;
	private PathObjectHierarchy hierarchy;
	private PathAnnotationObject annotation;
	
	private boolean isChanging = false;
	
	private BufferedImage img;
	private int[] buf;
	private float[] features;
	private int featureStride = 3;
	private BufferedImage imgMask;
	private float[] training;
	private int[] trainingResponses;
//	private Mat mat;
	
	public TissueSegmentationCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		this.imageData = qupath.getImageData();
		if (imageData == null)
			return;
		this.hierarchy = imageData.getHierarchy();
		
		double downsample = 25 / imageData.getServer().getAveragedPixelSizeMicrons();
		img = this.imageData.getServer().getBufferedThumbnail((int)(imageData.getServer().getWidth() / downsample + 0.5), -1, 0);
		
//		ConvolveOp op = new ConvolveOp(new Kernel(3, 3, new float[]{1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f}), ConvolveOp.EDGE_NO_OP, null);
//		img = op.filter(img, null);
//		img = op.filter(img, null);
//		img = op.filter(img, null);
		
		this.buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());

		float[] hsb = null;
		featureStride = 6;
		features = new float[buf.length*featureStride];
		for (int i = 0; i < buf.length; i++) {
			int val = buf[i];
			int red = ColorTools.red(val);
			int green = ColorTools.green(val);
			int blue = ColorTools.blue(val);
			hsb = Color.RGBtoHSB(red, green, blue, hsb);
			features[i*featureStride] = hsb[0];
			features[i*featureStride+1] = hsb[1];
			features[i*featureStride+2] = hsb[2];
			features[i*featureStride+3] = red;
			features[i*featureStride+4] = green;
			features[i*featureStride+5] = blue;
		}
		training = new float[features.length];
		trainingResponses = new int[features.length];
		
		imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		
		
		hierarchy.removePathObjectListener(this);
		hierarchy.addPathObjectListener(this);
//		this.mat = OpenCVHelpers.imageToMat(img);
//		Imgproc.cvtColor(this.mat, this.mat, Imgproc.COLOR_RGB2HSV);
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (img == null || isChanging || event.isChanging())
			return;
		
		List<PathObject> annotations = hierarchy.getObjects(null, PathAnnotationObject.class);
		if (annotation != null)
			annotations.remove(annotation);
		List<PathObject> background = new ArrayList<>();
		List<PathObject> foreground = new ArrayList<>();
		PathClass whitespaceClass = PathClassFactory.getDefaultPathClass(PathClasses.WHITESPACE);
		for (PathObject a : annotations) {
			if (a == annotation)
				continue;
			if (a.getPathClass() == whitespaceClass)
				background.add(a);
			else
				foreground.add(a);
		}
		
		if (background.isEmpty() || foreground.isEmpty())
			return;
		
		// Create labels
		Graphics2D g2d = imgMask.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
		g2d.scale((double)img.getWidth() / imageData.getServer().getWidth(),
				(double)img.getHeight() / imageData.getServer().getHeight());
		g2d.setColor(Color.GRAY);
		for (PathObject a : background) {
			g2d.draw(PathROIToolsAwt.getShape(a.getROI()));
		}
		g2d.setColor(Color.WHITE);
		for (PathObject a : foreground) {
			g2d.draw(PathROIToolsAwt.getShape(a.getROI()));
		}
		g2d.dispose();
		
		// Get the data to classify
		RTrees trees = RTrees.create();
		
		
		byte[] bytes = ((DataBufferByte)imgMask.getRaster().getDataBuffer()).getData();
		int n = 0;
		for (int i = 0; i < bytes.length; i++) {
			byte b = bytes[i];
			if (b == (byte)0)
				continue;
			if (b == (byte)255) {
				trainingResponses[n] = 2;
			} else {
				trainingResponses[n] = 1;				
			}
			for (int k = 0; k < featureStride; k++)
				training[n*featureStride+k] = features[i*featureStride+k];
			n++;
		}
		

		Mat matTraining = new Mat(n, featureStride, CV_32FC1);
		FloatBuffer bufferTraining = matTraining.createBuffer();
		bufferTraining.put(Arrays.copyOf(training, n*featureStride));
	    
		Mat matResponses = new Mat(n, 1, CV_32SC1);
		IntBuffer bufferResponses = matResponses.createBuffer();
		bufferResponses.put(Arrays.copyOf(trainingResponses, n));
		
		trees.train(matTraining, ROW_SAMPLE, matResponses);
		
		matTraining.release();
		matResponses.release();
		
		Mat samples = new Mat(buf.length, featureStride, CV_32FC1);
		OpenCVTools.putPixelsFloat(samples, features);
		Mat results = new Mat(buf.length, 1, CV_32SC1);
		trees.predict(samples, results, RTrees.PREDICT_AUTO);
		BufferedImage imgOutput = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
		float[] resultsArray = new float[buf.length];
		OpenCVTools.extractPixels(results, resultsArray);
		
		for (int i = 0; i < resultsArray.length; i++) {
			if (resultsArray[i] == 1f)
				imgOutput.setRGB(i % img.getWidth(), i / img.getWidth(), ColorTools.makeRGB(255, 0, 0));
			else if (resultsArray[i] == 2f)
				imgOutput.setRGB(i % img.getWidth(), i / img.getWidth(), ColorTools.makeRGB(255, 255, 255));
		}
		
		isChanging = true;
		hierarchy.fireHierarchyChangedEvent(this);
		isChanging = false;
	}

}
