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

package qupath.lib.awt.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.util.AffineTransformation;

@SuppressWarnings("javadoc")
public class TestAffineTransforms {
	
	@Test
	public void testCreateTransform() {
		
		assertTrue(AffineTransforms.identity().isIdentity());
		
		checkTransformByRow(AffineTransforms.fromColumns(0, 1, 2, 3, 4, 5), 0, 2, 4, 1, 3, 5);
		checkTransformByRow(AffineTransforms.fromRows(0, 1, 2, 3, 4, 5), 0, 1, 2, 3, 4, 5);

		checkTransformByRow(AffineTransforms.from2D(new double[][] {{0, 1, 2}, {3, 4, 5}}), 0, 1, 2, 3, 4, 5);
		checkTransformByRow(AffineTransforms.from2D(new double[][] {{0, 1, 2}, {3, 4, 5}, {0, 0, 1}}), 0, 1, 2, 3, 4, 5);

		checkTransformByRow(AffineTransforms.fromScale(5.0), 5.0, 0.0, 0.0, 0.0, 5.0, 0.0);
		checkTransformByRow(AffineTransforms.fromScale(5.0, 2.0), 5.0, 0.0, 0.0, 0.0, 2.0, 0.0);

		var transformation = new AffineTransformation(new double[] {0, 1, 2, 3, 4, 5});
		checkTransformByRow(AffineTransforms.fromJTS(transformation), 0, 1, 2, 3, 4, 5);

		// Wrong last line
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.from2D(new double[][] {{0, 1, 2}, {3, 4, 5}, {0, 1, 0}}));
		// Wrong dimensions
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.from2D(new double[][] {{0, 1, 2}, {3, 4}}));

		// Wrong lengths
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows());
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows(0, 1, 2, 3, 4));
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows(0, 1, 2, 3, 4, 5, 6));

		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows());
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows(0, 1, 2, 3, 4));
		assertThrows(IllegalArgumentException.class, () -> AffineTransforms.fromRows(0, 1, 2, 3, 4, 5, 6));

	}
	
	/**
	 * Check transforms, from a flat matrix organized by rows.
	 * This isn't what AffineTransform itself returns, but I find it considerably more intuitive 
	 * (perhaps due to numpy familiarity).
	 * @param transform
	 * @param values
	 */
	private static void checkTransformByRow(AffineTransform transform, double... values) {
		var flat = new double[] {
			transform.getScaleX(), transform.getShearX(), transform.getTranslateX(),
			transform.getShearY(), transform.getScaleY(), transform.getTranslateY()			
		};
		assertArrayEquals(flat, values);
	}
	
	
}