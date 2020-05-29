/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.images.servers.bioformats;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ome.OMETiffWriter;

@SuppressWarnings("javadoc")
public class TestOMETiffImageWriter {

	@Test
	public void testWriters() {
		BufferedImage imgGray = createImage(BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage imgBGR = createImage(BufferedImage.TYPE_INT_BGR);
		BufferedImage imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
		BufferedImage imgARGB = createImage(BufferedImage.TYPE_INT_ARGB);

		var images = new BufferedImage[] {imgGray, imgBGR, imgRGB, imgARGB};

		var writer = new OMETiffWriter();
		for (var img : images) {
			testWriter(writer, img);
		}
	}

	void testWriter(ImageWriter<BufferedImage> writer, BufferedImage img) {
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(img, stream);
			bytes = stream.toByteArray();
			assertNotNull(bytes);
			assertTrue(bytes.length > 0);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		// Haven't been able to read from in-memory image with Bio-Formats yet...
//		String id = "anything.ome.tif";
//		Location.mapFile(id, new ByteArrayHandle(bytes));
//		try (var reader = new ImageReader()) {
//			reader.setMetadataStore(MetadataTools.createOMEXMLMetadata());
//			reader.setId(id);
//			var bufferedReader = new BufferedImageReader(reader);
//			var imgRead = bufferedReader.openImage(0);
//			compareImages(img, imgRead);
//			bufferedReader.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//			fail("Error reading from byte array: " + e.getLocalizedMessage());
//			return;
//		}
	}

	static BufferedImage createImage(int type) {
		BufferedImage img = new BufferedImage(128, 128, type);
		var g2d = img.createGraphics();
		g2d.setColor(Color.MAGENTA);
		g2d.fillOval(0, 0, img.getWidth(), img.getHeight());
		g2d.setColor(Color.YELLOW);
		g2d.setStroke(new BasicStroke(2f));
		g2d.drawRect(10, 10, 64, 64);
		g2d.dispose();
		return img;
	}

	static void compareImages(BufferedImage imgOrig, BufferedImage imgRead) {
		Arrays.equals(getRGB(imgOrig), getRGB(imgRead));
	}

	static int[] getRGB(BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}
}