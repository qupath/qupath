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

import java.awt.geom.AffineTransform;
import java.util.Arrays;

import org.locationtech.jts.geom.util.AffineTransformation;

import qupath.lib.roi.GeometryTools;

/**
 * Helper class for working with affine transforms.
 * <p>
 * QuPath's core dependencies have two affine transform implementations: {@link AffineTransform} from Java itself 
 * and {@link AffineTransformation} from Java Topology Suite.
 * Inconveniently, both are initialized from flattened double arrays using a different ordering (assuming 
 * columns-first or rows-first) - so it is easy to make mistakes.
 * <p>
 * QuPath primarily uses {@link AffineTransform} from Java.
 * This class exists to make creating a transform more explicit, and facilitate conversion when needed.
 * 
 * @author Pete Bankhead
 */
public class AffineTransforms {
	
	/**
	 * Create an affine transform from a 2x3 double array, or 3x3 if the last 
	 * row has the values [0, 0, 1] only.
	 * @param mat
	 * @return
	 * @throws IllegalArgumentException if the input has the wrong shape
	 */
	public static AffineTransform from2D(double[][] mat) throws IllegalArgumentException {
		// Accept 3x3 if the last row is valid
		if (mat.length == 3 && mat[2].length == 3 && Arrays.equals(mat[2], new double[] {0.0, 0.0, 1.0}))
			mat = new double[][] {mat[0], mat[1]};
		// Accept 2x3
		if (mat.length == 2) {
			if (mat[0].length == 3 && mat[1].length == 3)
				return new AffineTransform(mat[0][0], mat[1][0], mat[0][1], mat[1][1], mat[0][2], mat[1][2]);
		}
		throw new IllegalArgumentException("Transform matrix should have size double[2][3]");
	}
	
	/**
	 * Create an affine transform from a flat matrix with 6 elements, assumed to be in the order 
	 * {@code [[0, 1, 2], [3, 4, 5]]}.
	 * @param mat
	 * @return
	 * @throws IllegalArgumentException if the input has the wrong length
	 */
	public static AffineTransform fromRows(double... mat) throws IllegalArgumentException {
		if (mat.length == 6)
			return fromJTS(
					new AffineTransformation(mat)
					);
		throw new IllegalArgumentException("Flattened transform array should have length 6");		
	}
	
	/**
	 * Create an affine transform from a flat matrix with 6 elements, assumed to be in the order 
	 * {@code [[0, 2, 4], [1, 3, 5]]}.
	 * @param mat
	 * @return
	 * @throws IllegalArgumentException if the input has the wrong length
	 */
	public static AffineTransform fromColumns(double... mat) throws IllegalArgumentException {
		if (mat.length == 6)
			return new AffineTransform(mat);
		throw new IllegalArgumentException("Flattened transform array should have length 6");		
	}
	
	/**
	 * Create an affine transform representing scaling, optionally using a different scale for x and y.
	 * @param scaleX
	 * @param scaleY
	 * @return
	 */
	public static AffineTransform fromScale(double scaleX, double scaleY) {
		return AffineTransform.getScaleInstance(scaleX, scaleY);
	}
	
	/**
	 * Create an affine transform representing scaling, using the same scale for x and y.
	 * @param scale
	 * @return
	 */
	public static AffineTransform fromScale(double scale) {
		return fromScale(scale, scale);
	}	
	
	/**
	 * Create an affine transform representing the identity transform.
	 * @return
	 */
	public static AffineTransform identity() {
		return new AffineTransform();
	}
	
	/**
	 * Create a Java affine transform from a Java Topology Suite representation.
	 * @param transform
	 * @return
	 */
	public static AffineTransform fromJTS(AffineTransformation transform) {
		return GeometryTools.convertTransform(transform);
	}

	/**
	 * Create a Java Topology Suite affine transformation from a Java affine transform.
	 * @param transform
	 * @return
	 */
	public static AffineTransformation toJTS(AffineTransform transform) {
		return GeometryTools.convertTransform(transform);
	}

}
