/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2020 QuPath developers, The University of Edinburgh
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

package qupath.opencv.tools;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.indexer.UShortIndexer;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Methods to help with requesting interpolated values.
 * Most commonly used with the indexer for an OpenCV {@link Mat}.
 * 
 * @author Pete Bankhead
 */
public class Interpolation {
	
	private static ThreadLocal<long[]> indsLocal = new ThreadLocal<>();
	
	
	/**
	 * Request a value from an {@link Indexer} using bilinear interpolation.
	 * Values requested out of range will be replaced by the closest in-range value.
	 * 
	 * @param indexer
	 * @param i requested row
	 * @param j requested column
	 * @return the interpolated value
	 */
	public static double interp2D(Indexer indexer, double i, double j) {
//		if (indexer instanceof FloatIndexer) {
//			return interp2D((FloatIndexer)indexer, i, j);
//		}
		long yi = (long)Math.floor(i);
		long xi = (long)Math.floor(j);
		double fx0y0 = get(indexer, yi, xi);
		double fx0y1 = get(indexer, yi+1, xi);
		double fx1y1 = get(indexer, yi+1, xi+1);
		double fx1y0 = get(indexer, yi, xi+1);
		return linearInterp2D(fx0y0, fx1y0, fx0y1, fx1y1, j-xi, i-yi);
	}

//	/**
//	 * Request a value from an {@link FloatIndexer} using bilinear interpolation.
//	 * @param indexer
//	 * @param i requested row
//	 * @param j requested column
//	 * @return the interpolated value
//	 */
//	public static double interp2D(FloatIndexer indexer, double i, double j) {
//		long xi = (long)Math.floor(j);
//		long yi = (long)Math.floor(i);
//		return linearInterp2D(
//				indexer.get(yi, xi),
//				indexer.get(yi, xi+1),
//				indexer.get(yi+1, xi),
//				indexer.get(yi+1, xi+1),
//				j-xi,
//				i-yi);
//	}
	
	/**
	 * Request a value from an {@link Indexer} using trilinear interpolation.
	 * Values requested out of range will be replaced by the closest in-range value.
	 * 
	 * @param indexer
	 * @param i
	 * @param j
	 * @param k
	 * @return the interpolated value
	 */
	public static double interp3D(Indexer indexer, double i, double j, double k) {
		long xi = (long)Math.floor(j);
		long yi = (long)Math.floor(i);
		long zi = (long)Math.floor(k);
		return linearInterp3D(
				get(indexer, yi, xi, zi),
				get(indexer, yi, xi+1, zi),
				get(indexer, yi+1, xi, zi),
				get(indexer, yi+1, xi+1, zi),
				get(indexer, yi, xi, zi+1),
				get(indexer, yi, xi+1, zi+1),
				get(indexer, yi+1, xi, zi+1),
				get(indexer, yi+1, xi+1, zi+1),
				j-xi,
				i-yi,
				k-zi);
	}
	
//	/**
//	 * Request a value from an {@link FloatIndexer} using trilinear interpolation.
//	 * @param indexer
//	 * @param i
//	 * @param j
//	 * @param k
//	 * @return the interpolated value
//	 */
//	public static double interp3D(FloatIndexer indexer, double i, double j, double k) {
//		long xi = (long)Math.floor(j);
//		long yi = (long)Math.floor(i);
//		long zi = (long)Math.floor(k);
//		return linearInterp3D(
//				get(indexer, yi, xi, zi),
//				get(indexer, yi, xi+1, zi),
//				get(indexer, yi+1, xi, zi),
//				get(indexer, yi+1, xi+1, zi),
//				get(indexer, yi, xi, zi+1),
//				get(indexer, yi, xi+1, zi+1),
//				get(indexer, yi+1, xi, zi+1),
//				get(indexer, yi+1, xi+1, zi+1),
//				j-xi,
//				i-yi,
//				k-zi);
//	}
	
	
	/**
	 * Include several specific 'get' method options for requesting pixels, because the general-purpose 
	 * 'getDouble' method of {@link Indexer} involves creating a long array for each request 
	 * and we'd prefer to avoid that if possible.
	 */


	private static double get(Indexer indexer, long i, long j, long k) {
		if (indexer instanceof FloatIndexer)
			return get((FloatIndexer)indexer, i, j, k);
		if (indexer instanceof UByteIndexer)
			return get((UByteIndexer)indexer, i, j, k);
		if (indexer instanceof UShortIndexer)
			return get((UShortIndexer)indexer, i, j, k);
		if (indexer instanceof DoubleIndexer)
			return get((DoubleIndexer)indexer, i, j, k);
		
		long[] inds = indsLocal.get();
		if (inds == null || inds.length != 3) {
			inds = new long[3];
			indsLocal.set(inds);
		}
		inds[0] = clipIndex(i, indexer.size(0));
		inds[1] = clipIndex(j, indexer.size(1));
		inds[2] = clipIndex(k, indexer.size(2));
		return indexer.getDouble(inds);
	}
	
	private static double get(Indexer indexer, long i, long j) {
		if (indexer instanceof FloatIndexer)
			return get((FloatIndexer)indexer, i, j);
		if (indexer instanceof UByteIndexer)
			return get((UByteIndexer)indexer, i, j);
		if (indexer instanceof UShortIndexer)
			return get((UShortIndexer)indexer, i, j);
		if (indexer instanceof DoubleIndexer)
			return get((DoubleIndexer)indexer, i, j);
		
		long[] inds = indsLocal.get();
		if (inds == null || inds.length != 2) {
			inds = new long[2];
			indsLocal.set(inds);
		}
		inds[0] = clipIndex(i, indexer.size(0));
		inds[1] = clipIndex(j, indexer.size(1));
		return indexer.getDouble(inds);
	}
		
	private static double get(UShortIndexer indexer, long i, long j, long k) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1)),
				clipIndex(k, indexer.size(2))
				);
	}
	
	private static double get(UShortIndexer indexer, long i, long j) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1))
				);
	}
	
	private static double get(DoubleIndexer indexer, long i, long j, long k) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1)),
				clipIndex(k, indexer.size(2))
				);
	}
	
	private static double get(DoubleIndexer indexer, long i, long j) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1))
				);
	}
	
	private static double get(UByteIndexer indexer, long i, long j, long k) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1)),
				clipIndex(k, indexer.size(2))
				);
	}
	
	private static double get(UByteIndexer indexer, long i, long j) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1))
				);
	}
	
	private static double get(FloatIndexer indexer, long i, long j, long k) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1)),
				clipIndex(k, indexer.size(2))
				);
	}
	
	private static double get(FloatIndexer indexer, long i, long j) {
		return indexer.get(
				clipIndex(i, indexer.size(0)),
				clipIndex(j, indexer.size(1))
				);
	}
	
	/**
	 * Clip an index to the range 0 - max-1.
	 * @param ind
	 * @param max
	 * @return
	 */
	private static long clipIndex(long ind, long max) {
		if (ind < 0)
			return 0;
		if (ind >= max)
			return max-1;
		return ind;
	}
	
	
	// TODO: Move these methods to another class
	private static double linearInterp1D(double fx0, double fx1, double x) {
		assert x >= 0 && x <= 1;
		return fx0 * (1 - x) + x * fx1;
	}

	private static double linearInterp2D(double fx0y0, double fx1y0, double fx0y1, double fx1y1, double x, double y) {
		double y0 = linearInterp1D(fx0y0, fx1y0, x);
		double y1 = linearInterp1D(fx0y1, fx1y1, x);
		return linearInterp1D(y0, y1, y);
	}

	private static double linearInterp3D(double fx0y0z0, double fx1y0z0, double fx0y1z0, double fx1y1z0,
			double fx0y0z1, double fx1y0z1, double fx0y1z1, double fx1y1z1,
			double x, double y, double z) {
		double z0 = linearInterp2D(fx0y0z0, fx1y0z0, fx0y1z0, fx1y1z0, x, y);
		double z1 = linearInterp2D(fx0y0z1, fx1y0z1, fx0y1z1, fx1y1z1, x, y);
		return linearInterp1D(z0, z1, z);
	}


}
