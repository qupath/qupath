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


package qupath.lib.analysis.images;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test conversion of raster images (binary and labelled) to ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class TestContourTracing {
	
	private static final Logger logger = LoggerFactory.getLogger(TestContourTracing.class);
	
	/**
	 * Turn this on with caution... can be extremely slow for large images
	 */
	private static boolean alwaysCheckValidity = false;
	
	private static int MAX_POINTS_FOR_VALIDITY = 50_000;
	
	
	private static List<String> excludeNames = Arrays.asList(
			"binary-noise-medium.png",
			"binary-noise-large.png"
			);

	
	static List<Arguments> providePathsForTraceContours() throws Exception {
		
		var path = Paths.get(TestContourTracing.class.getResource("/data").toURI());
		
		return Files.walk(path)
				.filter(p -> usePath(p))
				.sorted(Comparator.comparingLong(p -> getSize(p)))
				.map(p -> Arguments.of(p))
				.collect(Collectors.toList());

	}
	
	private static long getSize(Path path) {
		try {
			return Files.size(path);
		} catch (IOException e) {
			return Long.MAX_VALUE;
		}
	}
	
	private static boolean usePath(Path path) {
		if (!Files.isRegularFile(path))
			return false;
		String name = path.getFileName().toString().toLowerCase();
		if (name.endsWith(".png")) {
			for (var exclude : excludeNames)
				if (name.contains(exclude))
					return false;
			return true;
		}
		return false;
	}
	
	
	@ParameterizedTest
	@MethodSource("providePathsForTraceContours")
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

		
	static void testImage(BufferedImage img) throws Exception {
		
		assertEquals(BufferedImage.TYPE_BYTE_GRAY, img.getType());
		
		// Create a histogram - entries will be used for object areas
		var pixels = img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), 0, (int[])null);
		int max = Arrays.stream(pixels).max().orElse(0);
		int[] hist = new int[max+1];
		for (int p : pixels)
			hist[p]++;
		
		// Check for a simple wrapped image server
		for (int i = 0; i < max; i++) {
			var geom = ContourTracing.createTracedGeometry(img.getRaster(), i, i, 0, null);
			assertEquals(hist[i], geom.getArea(), 0.000001);
			if (alwaysCheckValidity || geom.getNumPoints() < MAX_POINTS_FOR_VALIDITY) {
				var error = new IsValidOp(geom).getValidationError();
				if (error != null)
					logger.warn("{}", error);
				assertNull(error);				
			} else
				logger.debug("Validity check skipped ({} points)", geom.getNumPoints());
		}
	}
	

}
