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

package qupath.opencv.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;

/**
 * Implementation of the 3D binary thinning algorithm of
 * <blockquote>
 * Lee et al. <br>
 * "Building skeleton models via 3-D medial surface/axis thinning algorithms." <br>
 * Computer Vision, Graphics, and Image Processing, 56(6):462–478, 1994.
 * </blockquote>
 * 
 * This is a new implementation, developed with reference to the original paper and 
 * two other implementations:
 * <ul>
 * 	<li>the ITK version from Hanno Homann (possibly-broken link http://hdl.handle.net/1926/1292)</li>
 * 	<li>the Skeletonize3D ImageJ plugin by Ignacio Arganda-Carreras</li>
 * </ul>
 * These were mostly used to help debug thorny issues around pixel ordering in the lookup 
 * tables. This implementation uses a different design with some additional optimizations, 
 * but should give the same results as "Skeletonize3D".
 * 
 * @author Pete Bankhead
 *
 */
public class Thinning {
	
	private final static Logger logger = LoggerFactory.getLogger(Thinning.class);
	
	private static final AdjacencyTree adjacencyTree = new AdjacencyTree();
	
	private static final int nBits = 1 << 26;
	private static BitSet bitLUTSet = new BitSet(nBits);
	private static BitSet bitLUT = new BitSet(nBits);
	
	private static enum Direction {
		NORTH, SOUTH, EAST, WEST, UP, DOWN;
	}
	
	/**
	 * Thin the binary image in {@link Mat}.
	 * Here, the image is assumed to be 2D or 3D; if 3D, then the z information is found along the 
	 * channels dimension.
	 * <p>
	 * The thinning is performed in-place.
	 * The image is converted to uint8 if required; non-zero pixels are considered foreground and zero 
	 * pixels background.
	 * <p>
	 * The resulting image has 0 in the background and retains the original (after conversion to 8-bit)
	 * value in the foreground (typically 1 or 255).
	 * 
	 * @param mat the image to thin
	 */
	public static void thin(Mat mat) {
		if (mat.depth() != opencv_core.CV_8U) {
			mat.convertTo(mat, opencv_core.CV_8U);
		}
		UByteIndexer idx = mat.createIndexer();
		thin(idx);
		idx.release();
	}
	
	
	static void thin(UByteIndexer idx) {
		
		byte[] neighbors = new byte[26];
		long iMax = idx.size(0);
		long jMax = idx.size(1);
		long kMax = idx.size(2);
		
		// Determine which directions are relevant (north, south, east, west, up, down)
		List<Direction> directions = new ArrayList<>();
		if (iMax > 1) {
			directions.add(Direction.NORTH);
			directions.add(Direction.SOUTH);
		}
		if (jMax > 1) {
			directions.add(Direction.EAST);
			directions.add(Direction.WEST);
		}
		if (kMax > 1) {
			directions.add(Direction.UP);
			directions.add(Direction.DOWN);
		}
		
		long nChanges = Long.MAX_VALUE;
		long iter = 0;
		List<long[]> simplePoints = new ArrayList<>();
		
		while (nChanges != 0L) {
		
			nChanges = 0L;
			
			for (Direction direction : directions) {
				
				// Loop through all pixels
				for (long k = 0; k < kMax; k++) {
					for (long i = 0; i < iMax; i++) {
						for (long j = 0; j < jMax; j++) {

							/**
							 * Check the current pixel is 'on'.
							 */
							if (idx.get(i, j, k) == (byte)0)
								continue;

							/**
							 * Check that the neighbor in the boundary direction is 'off'.
							 * If it is, we have a 'border point'.
							 */
							switch (direction) {
							case DOWN:
								if (k > 0 && idx.get(i, j, k-1) != (byte)0)
									continue;
								break;
							case EAST:
								if (j < jMax-1 && idx.get(i, j+1, k) != (byte)0)
									continue;
								break;
							case NORTH:
								if (i > 0 && idx.get(i-1, j, k) != (byte)0)
									continue;
								break;
							case SOUTH:
								if (i < iMax-1 && idx.get(i+1, j, k) != (byte)0)
									continue;
								break;
							case UP:
								if (k < kMax-1 && idx.get(i, j, k+1) != (byte)0)
									continue;
								break;
							case WEST:
								if (j > 0 && idx.get(i, j-1, k) != (byte)0)
									continue;
								break;
							default:
								// Shouldn't happen...
								logger.debug("Unknown boundary direction: {}", direction);
								continue;
							}

							/**
							 * Determine if boundary checks are needed.
							 */
							boolean checkBounds = i == 0 || j == 0 || k == 0 ||
									i == iMax-1 || j == jMax-1 || k == kMax-1;

							/**
							 * Extract pixel neighborhood, including a bit mask representing 'on' neighbours, 
							 * but excluding the central pixel.
							 */
							int bitMask;
							if (checkBounds)
								bitMask = getNeighborsWithBoundsCheck(idx, i, j, k, iMax, jMax, kMax, neighbors);
							else
								bitMask = getNeighbors(idx, i, j, k, neighbors);

							/**
							 * Check if we have a simple border point.
							 * If so, store it as a candidate for removal - but don't remove it yet, 
							 * to avoid propagating pixel changes during this directional iteration.
							 */
							if (isSimpleBorderPoint(neighbors, bitMask)) {
								simplePoints.add(new long[] {i, j, k});
							}
						}
					}
				}
				
				/**
				 * Loop through our candidate points for removal & actually remove them if the 
				 * simple point criterion holds.
				 */
				for (long[] point : simplePoints) {
										
					long i = point[0];
					long j = point[1];
					long k = point[2];
					
					getNeighborsWithBoundsCheck(idx, i, j, k, iMax, jMax, kMax, neighbors);

					boolean simplePoint = countConnectedObjects(neighbors) <= 1;
					if (!simplePoint)
						continue;

					// Using the LUT again gives slightly different results
//					boolean simplePoint = isSimpleBorderPoint(neighbors, bitSet);
//					if (!simplePoint)
//						continue;

					idx.put(i, j, k, 0);
					nChanges++;
				}
				simplePoints.clear();
				
			}
			
			iter++;
			logger.debug("Changes at iteration {}: {}", iter, + nChanges);
			
		}

		logger.info("Cached bitset values: {} ({} %)", 
				adjacencyTree.cachedCount,
				GeneralTools.formatNumber(adjacencyTree.cachedCount/(double)adjacencyTree.requestCount*100.0, 1));

	}
	
	/**
	 * Count the number of non-zero neighbors for every non-zero pixel in a binary image.
	 * This is useful for identify isolated, end and branch points.
	 * If thinning is required, this should be applied beforehand.
	 * @param idx
	 */
	static void countNeighbors(UByteIndexer idx) {
		long iMax = idx.size(0);
		long jMax = idx.size(1);
		long kMax = idx.size(2);
		byte[] neighbors = new byte[26];
		for (long k = 0; k < kMax; k++) {
			for (long i = 0; i < iMax; i++) {
				for (long j = 0; j < jMax; j++) {
					
					if (idx.get(i, j, k) == (byte)0)
						continue;
					
					/**
					 * Determine if boundary checks are needed.
					 */
					boolean checkBounds = i == 0 || j == 0 || k == 0 ||
							i == iMax-1 || j == jMax-1 || k == kMax-1;

					/**
					 * Extract pixel neighborhood, including a bit mask representing 'on' neighbours, 
					 * but excluding the central pixel.
					 */
					int bitMask;
					if (checkBounds)
						bitMask = getNeighborsWithBoundsCheck(idx, i, j, k, iMax, jMax, kMax, neighbors);
					else
						bitMask = getNeighbors(idx, i, j, k, neighbors);
					
					int n = Integer.bitCount(bitMask) + 1;
					idx.put(i, j, k, n);
				}
			}
		}
	}
	

	static boolean isSimpleBorderPoint(byte[] neighborhood, int bitMask) {
		/**
		 * If we have an isolated pixel or just one 'on' neighbor, we have an end point.
		 */
		int count = Integer.bitCount(bitMask);
		if (count <= 1)
			return false;
		
		assert bitMask >= 0 && bitMask <= nBits;
		
		adjacencyTree.requestCount++;
		if (!bitLUTSet.get(bitMask)) {
			boolean isSimpleBorderPoint = isSimpleBorderPoint(neighborhood);
			bitLUT.set(bitMask, isSimpleBorderPoint);
			bitLUTSet.set(bitMask, true);
			return isSimpleBorderPoint;
		} else
			adjacencyTree.cachedCount++;
		return bitLUT.get(bitMask);
	}
	
	
	private static boolean isSimpleBorderPoint(byte[] neighborhood) {
		/**
		 * Check if the Euler number changes if the central point is removed.
		 */
		if (!adjacencyTree.isEulerInvariant(neighborhood))
			return false;

		/**
		 * Count the number of connected objects in the neighborhood, with the center removed.
		 * If this is 1, we have a 'simple border point' that is a candidate for removal.
		 */
		return countConnectedObjects(neighborhood) <= 1;
	}
	
	
	
	
	static class Cube {
		
		/**
		 * Cube representing indices in a 3x3 neighborhood.
		 * The central pixel is set to -1.
		 */
		private final int[][][] cube = new int[3][3][3];
		
		private Cube() {
			int ind = 0;
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					for (int k = 0; k < 3; k++) {
						if (i == 1 && j == 1 && k == 1) {
							cube[i][j][k] = -1;
							continue;
						}
						cube[i][j][k] = ind;
						ind++;
					}					
				}				
			}
		}
		
		/**
		 * Split the cube into 8 octants, and store the indices for the pixels each octant -
		 * except for the central pixel.
		 * @return
		 */
		List<Octant> getOctants() {
			List<Octant> octants = new ArrayList<>();

			octants.add(new Octant(23, 24, 14, 15, 20, 21, 12));
			octants.add(new Octant(25, 22, 16, 13, 24, 21, 15));
			octants.add(new Octant(17, 20, 9, 12, 18, 21, 10));
			octants.add(new Octant(19, 22, 18, 21, 11, 13, 10));
			octants.add(new Octant(6, 14, 7, 15, 3, 12, 4));
			octants.add(new Octant(8, 7, 16, 15, 5, 4, 13));
			octants.add(new Octant(0, 9, 3, 12, 1, 10, 4));
			octants.add(new Octant(2, 1, 11, 10, 5, 4, 13));
			
			return octants;
		}
		
		
	}
	
	
	static class Octant {
		
		/**
		 * Integer representation of the indices
		 */
		private int[] inds;
		
		private Octant(int... inds) {
			this.inds = inds;
			assert this.inds.length == 7;
		}
		
		private boolean contains(int ind) {
			for (int i : inds) {
				if (i == ind)
					return true;
			}
			return false;
		}
		
		int eulerChar(byte[] neighbors) {
			// Initialize with 1, because we know the central element is 'on' -
			// and we don't have a stored index corresponding to it
			int n = 1;
			// Update bits corresponding to all other 'on' pixels
			for (int i = 0; i < 7; i++) {
				if (neighbors[inds[i]] != (byte)0)
					n |= (1 << (7 - i));
			}
			return n;
		}
		
		@Override
		public String toString() {
			return "Octant: (Indices: " + Arrays.stream(inds).mapToObj(i -> Integer.toString(i)).collect(Collectors.joining(", ")) + ")";
		}
		
	}
	
	
	static class AdjacencyTree {
		
		private static int[] lut = createDeltaG_26();

		private final List<Octant> octants;
		
		private final Octant[][] leaves;
		
		private long requestCount = 0L;
		private long cachedCount = 0L;
		
		private AdjacencyTree() {
			var cube = new Cube();
			octants = Collections.unmodifiableList(cube.getOctants());
			leaves = new Octant[26][];
			for (int i = 0; i < 26; i++) {
				int ind = i;
				leaves[i] = octants.stream().filter(o -> o.contains(ind)).toArray(Octant[]::new);
			}
			
		}
		
		public Octant getOctant(int ind) {
			return leaves[ind][0];
		}
		
		boolean isEulerInvariant(byte[] neighbors) {
			int euler = 0;
			for (var octant : octants) {
				euler += lut[octant.eulerChar(neighbors)];
			}
			return euler == 0;
		}
		
		void labelNeighbors(int ind, byte[] neighbors, byte label) {
			Octant octant = getOctant(ind);
			labelNeighbors(octant, neighbors, label);
		}
		
		void labelNeighbors(Octant octant, byte[] neighbors, byte label) {
			for (int i : octant.inds) {
				if (neighbors[i] == (byte)1) {
					neighbors[i] = label;
					for (var oct : leaves[i]) {
						if (oct != octant)
							labelNeighbors(oct, neighbors, label);
					}
				}
			}
		}
		
	}
	
	
	private static int countConnectedObjects(byte[] neighbours) {
		byte label = (byte)2;
		for (int ind = 0; ind < 26; ind++) {
			byte val = neighbours[ind];
			if (val == (byte)1) {
				adjacencyTree.labelNeighbors(ind, neighbours, label);
				label++;				
			}
		}
		return label - 2;
	}
	
	/**
	 * Extract a cube of pixels.
	 * @param idx
	 * @param i
	 * @param j
	 * @param k
	 * @param cube
	 * @return a packed integer representation of the region.
	 */
	private static int getNeighbors(UByteIndexer idx, long i, long j, long k, byte[] cube) {
		int count = 0;
		int ind = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				for (int dk = -1; dk <= 1; dk++) {
					// Skip the middle one!
					if (di == 0 && dj == 0 && dk == 0)
						continue;
					int val = idx.get(i+di, j+dj, k+dk);
					if (val != 0) {
						cube[ind] = (byte)1;
//						count++;
						count |= (1 << ind);
					} else
						cube[ind] = (byte)0;
					ind++;
				}
			}
		}
		return count;
	}
	
	/**
	 * Extract a cube of pixels, with bounds checking.
	 * 
	 * @param idx
	 * @param i
	 * @param j
	 * @param k
	 * @param sizeI
	 * @param sizeJ
	 * @param sizeK
	 * @param cube
	 * @return
	 */
	private static int getNeighborsWithBoundsCheck(UByteIndexer idx, long i, long j, long k, long sizeI, long sizeJ, long sizeK, byte[] cube) {
		int count = 0;
		int ind = 0;
		for (int di = -1; di <= 1; di++) {
			long ii = i + di;
			for (int dj = -1; dj <= 1; dj++) {
				long jj = j + dj;
				for (int dk = -1; dk <= 1; dk++) {
					// Skip the middle one!
					if (di == 0 && dj == 0 && dk == 0)
						continue;
					long kk = k + dk;
					// TODO: Improve efficiency
					int val;
					if (ii < 0 || jj < 0 || kk < 0 || ii >= sizeI || jj >= sizeJ || kk >= sizeK)
						val = 0;
					else
						val = idx.get(ii, jj, kk);
					
					if (val != 0) {
						cube[ind] = (byte)1;
//						count++;
						count |= (1 << ind);
					} else
						cube[ind] = (byte)0;
					ind++;
				}
			}
		}
		return count;
	}
	
	private static int[] createDeltaG_26() {
		int[] lut = new int[256];
		
		lut[1]   =  1;
		lut[3]   = -1;
		lut[5]   = -1;
		lut[7]   =  1;
		lut[9]   = -3;
		lut[11]  = -1;
		lut[13]  = -1;
		lut[15]  =  1;
		lut[17]  = -1;
		lut[19]  =  1;
		lut[21]  =  1;
		lut[23]  = -1;
		lut[25]  =  3;
		lut[27]  =  1;
		lut[29]  =  1;
		lut[31]  = -1;
		lut[33]  = -3;
		lut[35]  = -1;
		lut[37]  =  3;
		lut[39]  =  1;
		lut[41]  =  1;
		lut[43]  = -1;
		lut[45]  =  3;
		lut[47]  =  1;
		lut[49]  = -1;
		lut[51]  =  1;

		lut[53]  =  1;
		lut[55]  = -1;
		lut[57]  =  3;
		lut[59]  =  1;
		lut[61]  =  1;
		lut[63]  = -1;
		lut[65]  = -3;
		lut[67]  =  3;
		lut[69]  = -1;
		lut[71]  =  1;
		lut[73]  =  1;
		lut[75]  =  3;
		lut[77]  = -1;
		lut[79]  =  1;
		lut[81]  = -1;
		lut[83]  =  1;
		lut[85]  =  1;
		lut[87]  = -1;
		lut[89]  =  3;
		lut[91]  =  1;
		lut[93]  =  1;
		lut[95]  = -1;
		lut[97]  =  1;
		lut[99]  =  3;
		lut[101] =  3;
		lut[103] =  1;

		lut[105] =  5;
		lut[107] =  3;
		lut[109] =  3;
		lut[111] =  1;
		lut[113] = -1;
		lut[115] =  1;
		lut[117] =  1;
		lut[119] = -1;
		lut[121] =  3;
		lut[123] =  1;
		lut[125] =  1;
		lut[127] = -1;
		lut[129] = -7;
		lut[131] = -1;
		lut[133] = -1;
		lut[135] =  1;
		lut[137] = -3;
		lut[139] = -1;
		lut[141] = -1;
		lut[143] =  1;
		lut[145] = -1;
		lut[147] =  1;
		lut[149] =  1;
		lut[151] = -1;
		lut[153] =  3;
		lut[155] =  1;

		lut[157] =  1;
		lut[159] = -1;
		lut[161] = -3;
		lut[163] = -1;
		lut[165] =  3;
		lut[167] =  1;
		lut[169] =  1;
		lut[171] = -1;
		lut[173] =  3;
		lut[175] =  1;
		lut[177] = -1;
		lut[179] =  1;
		lut[181] =  1;
		lut[183] = -1;
		lut[185] =  3;
		lut[187] =  1;
		lut[189] =  1;
		lut[191] = -1;
		lut[193] = -3;
		lut[195] =  3;
		lut[197] = -1;
		lut[199] =  1;
		lut[201] =  1;
		lut[203] =  3;
		lut[205] = -1;
		lut[207] =  1;

		lut[209] = -1;
		lut[211] =  1;
		lut[213] =  1;
		lut[215] = -1;
		lut[217] =  3;
		lut[219] =  1;
		lut[221] =  1;
		lut[223] = -1;
		lut[225] =  1;
		lut[227] =  3;
		lut[229] =  3;
		lut[231] =  1;
		lut[233] =  5;
		lut[235] =  3;
		lut[237] =  3;
		lut[239] =  1;
		lut[241] = -1;
		lut[243] =  1;
		lut[245] =  1;
		lut[247] = -1;
		lut[249] =  3;
		lut[251] =  1;
		lut[253] =  1;
		lut[255] = -1;
		
		return lut;
	}

}
