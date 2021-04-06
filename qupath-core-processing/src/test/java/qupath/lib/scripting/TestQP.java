/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.scripting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.io.PathIO.GeoJsonExportOptions;

@SuppressWarnings("javadoc")
public class TestQP {
	
	@Test
	public void test_parseEnumOptions() {
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, null), new GeoJsonExportOptions[0]);
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, "EXCLUDE_MEASUREMENTS"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.EXCLUDE_MEASUREMENTS});
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, "EXCLUDE_MEASUREMENTS", "FEATURE_COLLECTION"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.EXCLUDE_MEASUREMENTS, GeoJsonExportOptions.FEATURE_COLLECTION});
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, null, "FEATURE_COLLECTION"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.FEATURE_COLLECTION});
		
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, null), new ShapeFeatures[0]);
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, "AREA"),
				new ShapeFeatures[] {ShapeFeatures.AREA});
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, "AREA", "MAX_DIAMETER"),
				new ShapeFeatures[] {ShapeFeatures.AREA, ShapeFeatures.MAX_DIAMETER});
	}

}
