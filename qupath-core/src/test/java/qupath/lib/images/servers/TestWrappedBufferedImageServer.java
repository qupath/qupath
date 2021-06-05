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

package qupath.lib.images.servers;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import qupath.lib.regions.RegionRequest;

@SuppressWarnings("javadoc")
public class TestWrappedBufferedImageServer {

	@Test
	public void test() {
		
		// Create RGB-compatible images showing something similar, but with different types
		var imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
		var types = new int[] {
				BufferedImage.TYPE_3BYTE_BGR,
				BufferedImage.TYPE_INT_BGR,
				BufferedImage.TYPE_USHORT_555_RGB,
				BufferedImage.TYPE_USHORT_565_RGB
				};
		
		// Ensure we get the same pixels when requesting different regions
		// The necessity of this traces back to https://bugs.openjdk.java.net/browse/JDK-4847156
		// (the key fix is actually in AbstractTileableImageServer)
		for (int type : types) {
			var imgTest = createImage(type);
			assertArrayEquals(getRGB(imgRGB), getRGB(imgTest));
			
			var serverRGB = new WrappedBufferedImageServer("RGB", imgRGB);
			var serverTest = new WrappedBufferedImageServer("Test " + type, imgTest);
			
			var regions = Arrays.asList(
					RegionRequest.createInstance("", 1, 0, 0, 100, 100),
					RegionRequest.createInstance("", 1, 20, 10, 30, 50),
					RegionRequest.createInstance("", 1, 5, 20, 63, 58)
					);
			
			try {
				for (var region : regions) {
					var tempRGB = serverRGB.readBufferedImage(region.updatePath(serverRGB.getPath()));
					var tempGBR = serverTest.readBufferedImage(region.updatePath(serverTest.getPath()));
					
					assertArrayEquals(getRGB(tempRGB), getRGB(tempGBR));
				}
				serverRGB.close();
				serverTest.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
	}
	
	
	private static int[] getRGB(BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}
	
	private static BufferedImage createImage(int type) {
		var img = new BufferedImage(100, 100, type);
		var g2d = img.createGraphics();
		g2d.setColor(Color.RED);
		g2d.fillOval(0, 0, 100, 100);
		g2d.setColor(Color.BLUE);
		g2d.drawLine(10, 10, 80, 80);
		g2d.dispose();
		return img;
	}

}