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

package qupath.lib.io;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


@SuppressWarnings("javadoc")
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
public class PointIOTest {

	static Map<Integer, Double[][]> map;
	static File file;

	/**
	 * Creates five sets of Point2's, representing the different types of Point2's:
	 * <li> 20 Point2's with different x and y coordinates, no class, no name, c=-1, z=0, t=0 </li>
	 * <li> 25 Point2's with different x and y coordinates, no class, no name, c=2, z=1, t=7 </li>
	 * <li> 30 Point2's with different x and y coordinates, class="Other", no name, c=-1, z=0, t=0 </li>
	 * <li> 35 Point2's with different x and y coordinates, no class, name="foo", c=-1, z=0, t=0 </li>
	 * <li> 40 Point2's with different x and y coordinates, class="Tumor", name="bar", c=-1, z=0, t=0 </li>
	 *
	 * N.B: Each sets has its own unique color.
	 */
	@BeforeAll
	public static void init() {
		map = new HashMap<Integer, Double[][]>();

		// Set 1
		double x = 5000;
		double y = 325;
		Double[][] coord1 = new Double[20][2];
		for (int i = 0; i < 20; i++) {
			coord1[i][0] = x + i*10;
			coord1[i][1] = y + i*10;
		}
		map.put(1, coord1);

		// Set 2
		x = 2430;
		y = 1563;
		Double[][] coord2 = new Double[25][2];
		for (int i = 0; i < 25; i++) {
			coord2[i][0] = x + i*11;
			coord2[i][1] = y + i*11;
		}
		map.put(2, coord2);

		// Set 3
		x = 10056;
		y = 29;
		Double[][] coord3 = new Double[30][2];
		for (int i = 0; i < 30; i++) {
			coord3[i][0] = x + i*12;
			coord3[i][1] = y + i*12;
		}
		map.put(3, coord3);

		// Set 4
		x = 12013;
		y = 5016;
		Double[][] coord4 = new Double[35][2];
		for (int i = 0; i < 35; i++) {
			coord4[i][0] = x + i*13;
			coord4[i][1] = y + i*13;
		}
		map.put(4, coord4);

		// Set 5
		x = 6521;
		y = 4012;
		Double[][] coord5 = new Double[40][2];
		for (int i = 0; i < 40; i++) {
			coord5[i][0] = x + i*14;
			coord5[i][1] = y + i*14;
		}
		map.put(5, coord5);
	}

	/**
	 * Uses the map generated in init() method and writes all its points down to a TSV file.
	 */
	@Test
	public void test1WritePoints() {
		List<PathObject> pathObjects = new ArrayList<>();
		Integer[] colors = new Integer[] {-14336, -13487566, null, -3342337, -1305168};

		for (var entry: map.entrySet()) {
			ArrayList<Point2> pointsList = new ArrayList<>();
			int c = entry.getKey() == 2 ? 2 : -1;
			int z = entry.getKey() == 2 ? 1 : 0;
			int t = entry.getKey() == 2 ? 7 : 0;

			for (var coord: entry.getValue()) {
				pointsList.add(new Point2(coord[0], coord[1]));
			}
			ROI points = ROIs.createPointsROI(pointsList, ImagePlane.getPlaneWithChannel(c, z, t));
			PathObject pathObject = PathObjects.createAnnotationObject(points);

			if (entry.getKey() == 3)
				pathObject.setPathClass(PathClassFactory.getPathClass("Other"));
			if (entry.getKey() == 4)
				pathObject.setName("foo");
			else if (entry.getKey() == 5) {
				pathObject.setPathClass(PathClassFactory.getPathClass("Tumor"));
				pathObject.setName("bar");
			}
			
			pathObject.setColorRGB(colors[entry.getKey()-1]);
			pathObjects.add(pathObject);
		}


		try {
			file = File.createTempFile("tmp", ".tsv");
			PointIO.writePoints(file, pathObjects);
		} catch (IOException e) {
			fail();
		}
	}


	@Test
	public void test2ReadPoints() {
		List<PathObject> pathObjects = null;
		List<Double[]> pointsList = new ArrayList<>();
		try {
			pathObjects = PointIO.readPoints(file);
			for (var pathObject: pathObjects) {
				Point2 point = pathObject.getROI().getAllPoints().get(0);
				pointsList.add(new Double[] {point.getX(), point.getY()});
			}

		} catch (IOException e) {
			fail();
		}

		// Check that we have the same number of sets as originally
		assertTrue(pathObjects.size() == 5);

		// Check that all coordinates are the same
		assertTrue(map.values().stream().allMatch(p -> {
				return Arrays.stream(p).anyMatch(o -> pointsList.stream().anyMatch(m -> Arrays.equals(m,  o)));
				})
			);

		// Check that classes were correctly assigned (Sets 3 & 5)
		assertTrue(pathObjects.stream()
				.filter(p -> isInside(map.get(3), p.getROI().getAllPoints().get(0)))
				.allMatch(q -> q.getPathClass().getName().equals("Other")));

		assertTrue(pathObjects.stream()
				.filter(p -> isInside(map.get(5), p.getROI().getAllPoints().get(0)))
				.allMatch(q -> q.getPathClass().getName().equals("Tumor")));

		// Check that name were correctly assigned (Sets 4 & 5)
		assertTrue(pathObjects.stream()
				.filter(p -> isInside(map.get(4), p.getROI().getAllPoints().get(0)))
				.allMatch(q -> q.getName().equals("foo")));

		assertTrue(pathObjects.stream()
				.filter(p -> isInside(map.get(5), p.getROI().getAllPoints().get(0)))
				.allMatch(q -> q.getName().equals("bar")));

		// Check C, Z and T
		assertTrue(pathObjects.stream()
				.allMatch(q -> {
					if (isInside(map.get(2), q.getROI().getAllPoints().get(0)))
							return (q.getROI().getC() == 2 &&
									q.getROI().getZ() == 1 &&
									q.getROI().getT() == 7);
					else
						return (q.getROI().getC() == -1 &&
								q.getROI().getZ() == 0 &&
								q.getROI().getT() == 0);
				}));

		file.delete();
	}


	/**
	 * Helper class for test2ReadPoints(). Returns true if point is inside the nested Double array.
	 * @param array
	 * @param point
	 * @return boolean
	 */
	static boolean isInside(Double[][] array, Point2 point) {
		boolean found = false;
		for (int i = 0; i < array.length; i++) {
				if (array[i][0] == point.getX() && array[i][1] == point.getY())
					found = true;
		}
		return found;
	}

}