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

package qupath.lib.images.writers;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ImageWriterIJTest {

	@Test
	public void testWriters() {
		BufferedImage imgGray = createImage(BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage imgBGR = createImage(BufferedImage.TYPE_INT_BGR);
		BufferedImage imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
		BufferedImage imgARGB = createImage(BufferedImage.TYPE_INT_ARGB);

		var images = new BufferedImage[] {imgGray, imgBGR, imgRGB};

		for (var img : images) {
			testWriter(new PngWriter(), img);
			testWriter(new JpegWriter(), img);
		}
		// Can only write PNG with alpha channel
		testWriter(new PngWriter(), imgARGB);
	}

	void testWriter(ImageWriter<BufferedImage> writer, BufferedImage img) {
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(img, stream);
			bytes = stream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		try (var stream = new ByteArrayInputStream(bytes)) {
			var imgRead = ImageIO.read(stream);
			compareImages(img, imgRead);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error reading from byte array: " + e.getLocalizedMessage());
			return;
		}
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