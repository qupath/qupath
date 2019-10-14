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

package qupath.opencv.gui;


import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_video.*;

import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

/**
 * Experimental method to try to align annotations to a TMA core.
 * <p>
 * The purpose of this was to handle annotations made on a second core / consecutive section (cytokeratin staining),
 * and then transferred.
 * <p>
 * It was, at best, a limited success.
 * 
 * @author Pete Bankhead
 *
 */
public class AlignCoreAnnotationsCV implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(AlignCoreAnnotationsCV.class);
	
	final private static String name = "Align annotations for TMA core";
	
	private final QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath QuPath instance where the command should be installed.
	 */
	public AlignCoreAnnotationsCV(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage(name, "A dearrayed TMA image is required");
			return;
		}
		
		PathObject pathObject = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		if (!(pathObject instanceof TMACoreObject && pathObject.hasChildren())) {
			DisplayHelpers.showErrorMessage(name, "Please select a TMA core containing annotations");
			return;			
		}

		new CoreAligner(imageData, pathObject).run();
	}
	
	
	
	static class CoreAligner implements Runnable {
		
		private ImageData<BufferedImage> imageData;
		private PathObject parentObject;
		
		private CoreAligner(final ImageData<BufferedImage> imageData, final PathObject parentObject) {
			this.imageData = imageData;
			this.parentObject = parentObject;
		}

		@Override
		public void run() {
			
			Collection<PathObject> pathObjects = PathObjectTools.getDescendantObjects(parentObject, null, PathAnnotationObject.class);
			Iterator<PathObject> iter = pathObjects.iterator();
			while (iter.hasNext()) {
				if (iter.next().hasChildren())
					iter.remove();
			}
			
			if (pathObjects.isEmpty()) {
				logger.error("No suitable annotations (without child objects) found for alignment in parent {}", parentObject);
				return;
			}
			
			// Read the image
			ImageServer<BufferedImage> server = imageData.getServer();
			double downsample = 1;
			double preferredPixelSizeMicrons = 4;
			PixelCalibration cal = server.getPixelCalibration();
			if (cal.hasPixelSizeMicrons() && cal.getAveragedPixelSizeMicrons() < preferredPixelSizeMicrons) {
				downsample = preferredPixelSizeMicrons / cal.getAveragedPixelSizeMicrons();
			}
			RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, parentObject.getROI());
			BufferedImage img = null;
			try {
				img = server.readBufferedImage(request);
			} catch (IOException e) {
				DisplayHelpers.showErrorMessage("Align cores", e);
				return;
			}
			
			// Convert to grayscale & get bytes
			byte[] bytesImage = getGrayscalePixelBytes(img);

			
			// Create the template mask
			AffineTransform transformForward = new AffineTransform();
			transformForward.scale(1.0/downsample, 1.0/downsample);
			transformForward.translate(-request.getX(), -request.getY());
			
			
			int w = img.getWidth();
			int h = img.getHeight();
			BufferedImage imgMask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g2d = imgMask.createGraphics();
			g2d.setTransform(transformForward);
			g2d.setColor(Color.WHITE);
			for (PathObject temp : pathObjects) {
				Shape shape = RoiTools.getShape(temp.getROI());
				g2d.fill(shape);
			}
			g2d.dispose();
			byte[] bytesTemplate = getGrayscalePixelBytes(imgMask);
			
			// Threshold the image
			threshold(bytesImage, bytesImage.length - countNonZero(bytesTemplate));


			// Create binary masks
			Mat matImage = new Mat(h, w, CV_8UC1, Scalar.ZERO);
			OpenCVTools.putPixelsUnsigned(matImage, bytesImage);
			Mat matTemplate = new Mat(h, w, CV_8UC1, Scalar.ZERO);
			OpenCVTools.putPixelsUnsigned(matTemplate, bytesTemplate);

			Mat warpMatrix = Mat.eye(2, 3, CV_32FC1).asMat();
			findTransformECC(matTemplate, matImage, warpMatrix, MOTION_EUCLIDEAN, new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 50, 0.001), null);
			logger.debug("Warp matrix: {}", warpMatrix);
			
			// Try to shift annotation
			AffineTransform transformRefine = new AffineTransform();
//			double[] data = warpMatrix.get(0, 0);
			Indexer indexerWarp = warpMatrix.createIndexer();
			transformRefine.setTransform(
					indexerWarp.getDouble(0, 0),
					indexerWarp.getDouble(1, 0), 
					indexerWarp.getDouble(0, 1), 
					indexerWarp.getDouble(1, 1), 
					indexerWarp.getDouble(0, 2), 
					indexerWarp.getDouble(1, 2));
			for (PathObject temp : pathObjects) {
				ROI roi = temp.getROI();
				Shape shape = RoiTools.getShape(roi);
				shape = transformForward.createTransformedShape(shape);
				shape = transformRefine.createTransformedShape(shape);
				try {
					shape = transformForward.createInverse().createTransformedShape(shape);
				} catch (NoninvertibleTransformException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				((PathROIObject)temp).setROI(RoiTools.getShapeROI(shape, roi.getImagePlane(), -1));
			}
			imageData.getHierarchy().fireHierarchyChangedEvent(this, parentObject);
			
			
			warpMatrix.release();
			matImage.release();
			matTemplate.release();
			
		}
		
		
		static byte[] getGrayscalePixelBytes(BufferedImage img) {
			img = convertToGrayscale(img);
			return ((DataBufferByte)img.getRaster().getDataBuffer()).getData(0);
		}
		
		
		static BufferedImage convertToGrayscale(BufferedImage img) {
			if (img.getType() == BufferedImage.TYPE_BYTE_GRAY)
				return img;
			
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			return img2;
		}

		
		static int countNonZero(byte[] values) {
			int count = 0;
			for (byte b : values) {
				if (b != (byte)0)
					count++;
			}
			return count;
		}
		
		
		static void threshold(byte[] values, int nPixelsAbove) {
			int threshold = computeThreshold(values, nPixelsAbove);
			for (int i = 0; i < values.length; i++) {
				int v = values[i] & 0xFF;
				if (v < threshold)
					values[i] = (byte)255;
				else
					values[i] = (byte)0;
			}
		}
		
		
		static int computeThreshold(byte[] values, int nPixelsAbove) {
			int[] histogram = compute8BitHistogram(values);
			int sum = 0;
			int idx = histogram.length - 1;
			while (idx >= 0 && sum < nPixelsAbove) {
				sum += histogram[idx];
				idx--;
			}
			return idx;
		}
		
		
		static int[] compute8BitHistogram(byte[] values) {
			int[] histogram = new int[256];
			for (byte b : values) {
				int v = b & 0xFF;
				histogram[v]++;
			}
			return histogram;
		}
		
		
	}
	
	
	
}


