/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import org.locationtech.jts.geom.Geometry;
import qupath.lib.common.ColorTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.GeometryTools;

@SuppressWarnings("javadoc")
public class TestGsonTools {
	
	@Test
	public void test_PathClasses() throws IOException {
		
		testToJson(null);
		testToJson(PathClass.NULL_CLASS);
		testToJson(PathClass.getInstance("Tumor"));
		testToJson(PathClass.fromString("Tumor: Positive"));
		testToJson(PathClass.fromString("Tumor: Positive: Other", ColorTools.packRGB(1, 2, 3)));

		testToJson(PathClass.fromCollection(Arrays.asList("Class 1", "Class 2", "Class 3")));
	}
	
	private static void testToJson(PathClass pathClass) {
		
		var gson = GsonTools.getInstance();
		var json = gson.toJson(pathClass);
		
		var pathClass2 = gson.fromJson(json, PathClass.class);
		
		assertEquals(pathClass, pathClass2);
		assertSame(pathClass, pathClass2);
	}


	@Test
	public void test_Measurements() {
		// Test awkward (non-finite) measurement values
		// See https://github.com/qupath/qupath/issues/1293
		String json = """
				{
				  "type": "Feature",
				  "id": "7d9c0273-6584-4f4a-97ff-6a27dc8164e3",
				  "geometry": {
				    "type": "Polygon",
				    "coordinates": [
				      [
				        [60333, 50744],
				        [60727, 50744],
				        [60727, 51078],
				        [60333, 51078],
				        [60333, 50744]
				      ]
				    ]
				  },
				  "properties": {
				    "objectType": "annotation",
				    "measurements": {
				      "Not a number": NaN,
				      "Big": Infinity,
				      "Negative": -Infinity,
				      "Zero": 0.0,
				      "Fine": 2.5,
				      "Not a number string": "NaN",
				      "Big string": "Infinity",
				      "Negative string": "-Infinity",
				      "Zero string": "0.0",
				      "Fine string": "2.5"
				    }
				  }
				}
				""";
		var pathObject = GsonTools.getInstance().fromJson(json, PathObject.class);
		assertTrue(Double.isNaN(pathObject.getMeasurementList().get("Not a number")));
		assertTrue(Double.isNaN(pathObject.getMeasurementList().get("Not a number string")));
		assertEquals(Double.POSITIVE_INFINITY, pathObject.getMeasurementList().get("Big"));
		assertEquals(Double.POSITIVE_INFINITY, pathObject.getMeasurementList().get("Big string"));
		assertEquals(Double.NEGATIVE_INFINITY, pathObject.getMeasurementList().get("Negative"));
		assertEquals(Double.NEGATIVE_INFINITY, pathObject.getMeasurementList().get("Negative string"));
		assertEquals(0.0, pathObject.getMeasurementList().get("Zero"));
		assertEquals(0.0, pathObject.getMeasurementList().get("Zero string"));
		assertEquals(2.5, pathObject.getMeasurementList().get("Fine"));
		assertEquals(2.5, pathObject.getMeasurementList().get("Fine string"));
	}

	@Test
	public void test_GeoJson() {
		String json = """
				{
				  "type": "Polygon",
				  "coordinates": [
					[
					  [0, 80],
					  [0, 180],
					  [60, 180],
					  [60, 80],
					  [0, 80]
					]
				  ]
				}
				""";

		var geom = GsonTools.getInstance().fromJson(json, Geometry.class);
		assertTrue(geom.isValid());
		assertTrue(geom.isRectangle());
		assertFalse(geom.isEmpty());
		assertEquals(60, geom.getEnvelopeInternal().getWidth());
		assertEquals(100, geom.getEnvelopeInternal().getHeight());
		assertEquals(geom.getFactory(), GeometryTools.getDefaultFactory());
	}

	@Test
	public void test_TinyGeoJson() {
		// Very small geometries can get flattened to empty because of the PrecisionModel used -
		// but we expect them to remain valid
		String json = """
				{
				  "type": "Polygon",
				  "coordinates": [
					[
					  [0, 0.0000001],
					  [0, 0.0000002],
					  [60, 0.0000002],
					  [60, 0.0000001],
					  [0, 0.0000001]
					]
				  ]
				}
				""";

		var geom = GsonTools.getInstance().fromJson(json, Geometry.class);
		assertTrue(geom.isValid());
		assertTrue(geom.isEmpty());
		assertEquals(geom.getFactory(), GeometryTools.getDefaultFactory());
	}

	
}
