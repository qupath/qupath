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

package qupath.lib.regions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestRegionRequest {

	@Test
	public void testRegionRequests() {
		
		int width = 200;
		int height = 100;
		
		// Create a simple request, starting at the origin
		var request = RegionRequest.createInstance("anything", 2.0, 0, 0, width, height);
		assertEquals(request.getWidth(), width);
		assertEquals(request.getHeight(), height);

		// Dilate
		int padX = 5;
		int padY = 10;
		var request2 = request.pad2D(padX, padY);
		assertNotEquals(request2.getX(), request.getX());
		assertNotEquals(request2.getY(), request.getY());
		assertEquals(request2.getWidth(), width + padX * 2);
		assertEquals(request2.getHeight(), height + padY * 2);

		// Clip to the origin
		var request3 = request2.intersect2D(0, 0, width + padX, height + padY);
		assertEquals(request3.getX(), 0);
		assertEquals(request3.getY(), 0);
		assertEquals(request3.getWidth(), width + padX);
		assertEquals(request3.getHeight(), height + padY);
		
		// Intersect with the original (should gave the same result)
		var request4 = request2.intersect2D(request);
		assertEquals(request, request4);
		
		// Erode
		var request5 = request.pad2D(-padX, -padY);
		assertNotEquals(request5.getX(), request.getX());
		assertNotEquals(request5.getY(), request.getY());
		assertEquals(request5.getWidth(), width - padX * 2);
		assertEquals(request5.getHeight(), height - padY * 2);
		
		// Intersect with the original (should gave eroded result)
		var request6 = request5.intersect2D(request);
		assertEquals(request6, request5);
		
		// Change other properties
		var request7 = request.updateZ(2);
		assertNotEquals(request, request7);
		assertNotEquals(request.getImagePlane(), request7.getImagePlane());
		sameRegion2D(request, request7);
		
		var request8 = request.updateT(2);
		assertNotEquals(request, request8);
		assertNotEquals(request.getImagePlane(), request8.getImagePlane());
		sameRegion2D(request, request8);
		
		var request9 = request.updatePath("Anything else");
		assertNotEquals(request, request9);
		assertNotEquals(request.getPath(), request9.getPath());
		sameRegion2D(request, request9);
		
		// Pad again
		var request10 = request.pad2D(Padding.empty());
		assertEquals(request, request10);
		
		var request11 = request.pad2D(Padding.getPadding(padX, padY));
		assertEquals(request2, request11);
		
		int padX2 = 20;
		int padY2 = 30;
		var request12 = request.pad2D(Padding.getPadding(padX, padX2, padY, padY2));
		assertNotEquals(request, request12);
		assertEquals(request.getPath(), request12.getPath());
		assertEquals(request.getDownsample(), request12.getDownsample());
		assertTrue(samePlane(request, request12));
		assertEquals(request.getMinX()-padX, request12.getMinX());
		assertEquals(request.getMaxX()+padX2, request12.getMaxX());
		assertEquals(request.getMinY()-padY, request12.getMinY());
		assertEquals(request.getMaxY()+padY2, request12.getMaxY());
		assertEquals(request.getWidth() + padX + padX2, request12.getWidth());
		assertEquals(request.getHeight() + padY + padY2, request12.getHeight());
				
	}
	
	
	
	static boolean sameRegionAndPlane(ImageRegion r1, ImageRegion r2) {		
		return sameRegion2D(r1, r2) && samePlane(r1, r2);
	}
	
	static boolean samePlane(ImageRegion r1, ImageRegion r2) {		
		assertEquals(r1.getImagePlane(), r2.getImagePlane());
		return true;
	}
	
	static boolean sameRegion2D(ImageRegion r1, ImageRegion r2) {
		assertEquals(r1.getX(), r2.getX());
		assertEquals(r1.getY(), r2.getY());
		assertEquals(r1.getWidth(), r2.getWidth());
		assertEquals(r1.getHeight(), r2.getHeight());
		return true;
	}

}