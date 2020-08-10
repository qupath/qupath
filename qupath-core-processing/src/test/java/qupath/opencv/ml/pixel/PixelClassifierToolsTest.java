package qupath.opencv.ml.pixel;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
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
class PixelClassifierToolsTest {
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierToolsTest.class);
	
	/**
	 * Optionally print areas (not just check they match)
	 */
	private boolean printAreas = false;
	
	
	private List<String> excludeNames = Arrays.asList(
			"binary-noise-medium.png",
			"binary-noise-large.png"
			);

	/**
	 * Optionally check validity (can take a *very* long time with large geometries)
	 */
	private boolean checkValidity = excludeNames.size() >= 2;

	
	@Test
	void testCreateObjects() throws Exception {
		
		var path = Paths.get(getClass().getResource("/data").toURI());
		
		var pathList = Files.walk(path)
				.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".png"))
				.collect(Collectors.toCollection(ArrayList::new));
		
		if (pathList.isEmpty()) {
			throw new Exception("No paths found to test object creation!");
		}
		
		// Sort by file size - do simpler tests first
		Collections.sort(pathList, Comparator.comparingLong(p -> {
			try {
				return Files.size(p);
			} catch (Exception e) {
				return Long.MAX_VALUE;
			}
		}));
		
		for (var p : pathList) {
			String name = p.getFileName().toString();
			
			if (excludeNames.contains(name)) {
				logger.debug("Skipping {}", name);
				continue;
			}
			logger.debug("Testing {}", name);
			long time = testImage(p);
			logger.debug("Processing time: {} ms", time);
		}

	}
	
	long testImage(Path path) throws Exception {
		long startTime = System.currentTimeMillis();
		
		var img = ImageIO.read(path.toUri().toURL());
		
		// Convert binary images to 0-1
		if (path.getFileName().toString().startsWith("binary")) {
			var buffer = (DataBufferByte)img.getRaster().getDataBuffer();
			byte[] bytes = buffer.getData();
			for (int i = 0; i < bytes.length; i++)
				if (bytes[i] != (byte)0)
					bytes[i] = (byte)1;
		}
		
		testImage(img);
		
		return System.currentTimeMillis() - startTime;
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
				System.err.println(hist[label] + ": \t" + area);
			assertEquals(hist[label], area);
			var geom = roi.getGeometry();
			if (checkValidity) {
				var error = new IsValidOp(geom).getValidationError();
				if (error != null)
					logger.warn(error.getMessage());
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
