/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.scripting.QP;

@SuppressWarnings("javadoc")
public class TestPixelClassifiers {
	
	@Test
	public void test_saveThresholders() {
		// Ensure registered with GsonTools
		@SuppressWarnings("unused")
		var dummy = QP.getCoreClasses();
		
		// Make sure we can JSON serialize and deserialize a threshold classifier
		var classifier = PixelClassifiers.createThresholdClassifier(PixelCalibration.getDefaultInstance().createScaledInstance(2, 2),
				0, 1, PathClass.getInstance("Ignore*"), PathClass.getInstance("Tumor"));
		
		var gson = GsonTools.getInstance(true);
		var json = gson.toJson(classifier, PixelClassifier.class);
		assertNotNull(json);
		
		var classifier2 = gson.fromJson(json, PixelClassifier.class);
		assertEquals(classifier.getClass(), classifier2.getClass());
		var json2 = gson.toJson(classifier2, PixelClassifier.class);

		assertEquals(json.trim(), json2.trim());
	}

}
