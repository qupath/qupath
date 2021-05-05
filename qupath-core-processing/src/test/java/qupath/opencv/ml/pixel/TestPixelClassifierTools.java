/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2020 QuPath developers, The University of Edinburgh
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


package qupath.opencv.ml.pixel;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.ByteProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.processing.RoiLabeling;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;

/**
 * Test conversion of raster images (binary and labelled) to ROIs.
 * 
 * @author Pete Bankhead
 *
 */
class TestPixelClassifierTools {
	
	private static final Logger logger = LoggerFactory.getLogger(TestPixelClassifierTools.class);
	
	/**
	 * Optionally print areas (not just check they match)
	 */
	private static boolean printAreas = false;
	
	/**
	 * Turn this on with caution... can be extremely slow for large images
	 */
	private static boolean alwaysCheckValidity = false;
	
	private static int MAX_POINTS_FOR_VALIDITY = 50_000;

	
	@ParameterizedTest
	@MethodSource("qupath.lib.analysis.images.TestContourTracing#providePathsForTraceContours")
	void testTraceContours(Path path) throws Exception {
		long startTime = System.currentTimeMillis();
		
		var img = ImageIO.read(path.toUri().toURL());
		
		logger.debug("Tracing contours for {}", path.getFileName().toString());
		
		// Convert binary images to 0-1
		if (path.getFileName().toString().startsWith("binary")) {
			var buffer = (DataBufferByte)img.getRaster().getDataBuffer();
			byte[] bytes = buffer.getData();
			for (int i = 0; i < bytes.length; i++)
				if (bytes[i] != (byte)0)
					bytes[i] = (byte)1;
		}
		
		testImage(img);
		
		long time = System.currentTimeMillis() - startTime;
		logger.debug("Contours traced for {} in {} ms", path.getFileName().toString(), time);
	}

		
	void testImage(BufferedImage img) throws Exception {
		
		assertEquals(BufferedImage.TYPE_BYTE_GRAY, img.getType());
		
		// Create a histogram - entries will be used for object areas
		var pixels = img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), 0, (int[])null);
		int max = Arrays.stream(pixels).max().orElse(0);
		int[] hist = new int[max+1];
		for (int p : pixels)
			hist[p]++;
		
		// Check ImageJ labelling
		var bpLabels = new ByteProcessor(img); 
		var rois = RoiLabeling.labelsToConnectedROIs(bpLabels, max+1);
		for (int i = 0; i < max+1; i++) {
			var roi = rois[i];
			if (i >= max)
				assertNull(roi);
			else {
				bpLabels.setRoi(roi);
				int count = ImageStatistics.getStatistics(bpLabels).pixelCount;
				assertEquals(count, hist[i+1]);
				double area = ImageStatistics.getStatistics(bpLabels).area;
				assertEquals(area, hist[i+1]);
			}
		}
		
		// Create objects for all distinct pixel values (including 0)
		var classificationLabels = new LinkedHashMap<Integer, PathClass>();
		var classificationLabelsReverse = new HashMap<PathClass, Integer>();
		for (int i = 0; i <= max; i++) {
			var pathClass = PathClassFactory.getPathClass("Class " + i);
			classificationLabels.put(i, pathClass);
			classificationLabelsReverse.put(pathClass, i);
		}
		
		// Test object creation code
		ImageServer<BufferedImage> server = new WrappedBufferedImageServer(UUID.randomUUID().toString(), img);
		server.setMetadata(
				new ImageServerMetadata.Builder(server.getOriginalMetadata())
				.channelType(ChannelType.CLASSIFICATION)
				.classificationLabels(classificationLabels)
				.build()
				);
		
		// Check for a simple wrapped image server
		checkCreateObjects(server, hist, classificationLabelsReverse);
		checkCreateObjectsSplit(server, hist, classificationLabelsReverse);
		
		// Also check a pyramidal server - this allows us to use tiled requests as well,
		// which checks for appropriate merging of tiles
		var serverPyramidal = ImageServers.pyramidalizeTiled(server, 128, 128, 1.0, 4.0);
		checkCreateObjects(serverPyramidal, hist, classificationLabelsReverse);
		checkCreateObjectsSplit(server, hist, classificationLabelsReverse);
		server.close();
		serverPyramidal.close();

		
	}
	
	
	private void checkCreateObjects(ImageServer<BufferedImage> server, int[] hist, Map<PathClass, Integer> classificationLabelsReverse) {
		var hierarchy = new PathObjectHierarchy();
		boolean success = PixelClassifierTools.createObjectsFromPredictions(
				server,
				hierarchy,
				Collections.singleton(hierarchy.getRootObject()),
				r -> PathObjects.createAnnotationObject(r),
				0, 0);
		assertTrue(success);
		
		// Recall that we have an object for zero as well
		var annotations = new ArrayList<>(hierarchy.getAnnotationObjects());
		assertEquals(hist.length, annotations.size());
		
		// Check areas for all our annotations
		Collections.sort(annotations, Comparator.comparingInt(a -> classificationLabelsReverse.get(a.getPathClass())));
		for (var annotation : annotations) {
			int label = classificationLabelsReverse.get(annotation.getPathClass());
			var roi = annotation.getROI();
			double area = roi.getArea();
			if (printAreas)
				logger.debug(hist[label] + ": \t" + area);
			assertEquals(hist[label], area);
			var geom = roi.getGeometry();
			if (alwaysCheckValidity || geom.getNumPoints() < MAX_POINTS_FOR_VALIDITY) {
				var error = new IsValidOp(geom).getValidationError();
				if (error != null)
					logger.warn("{}", error);
				assertNull(error);				
			}
			assertEquals(hist[label], geom.getArea());
		}
		
	}
	
	private void checkCreateObjectsSplit(ImageServer<BufferedImage> server, int[] hist, Map<PathClass, Integer> classificationLabelsReverse) {
		var hierarchy = new PathObjectHierarchy();
		boolean success = PixelClassifierTools.createObjectsFromPredictions(
				server,
				hierarchy,
				Collections.singleton(hierarchy.getRootObject()),
				r -> PathObjects.createAnnotationObject(r),
				0, 0,
				CreateObjectOptions.DELETE_EXISTING, CreateObjectOptions.SPLIT);
		assertTrue(success);
		
		var annotations = hierarchy.getAnnotationObjects();
		
		// Check total areas for all our annotations
		for (var entry : classificationLabelsReverse.entrySet()) {
			var pathClass = entry.getKey();
			var label = entry.getValue();
			var totalArea = annotations.stream()
					.filter(a -> a.getPathClass() == pathClass)
					.mapToDouble(a -> a.getROI().getArea())
					.sum();
			assertEquals(hist[label], totalArea);
		}
		
	}
	

}
