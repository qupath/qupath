/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.roi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;

/**
 * Simple class to store x,y coordinates as floating point arrays.
 * <p>
 * Vertices objects are immutable when passed around directly, but may be stored inside a MutableVertices object that
 * permits changes.
 * <p>
 * Future implementations may support other dimensions, stored in a single packed array.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultVertices implements Vertices {
	
	static Logger logger = LoggerFactory.getLogger(DefaultVertices.class);
	
	static int DEFAULT_CAPACITY = 16;
	
	private int size = 0;
	private float[] x;
	private float[] y;
	
	DefaultVertices(final float[] x, final float[] y, final boolean copyArrays) {
		if (x.length != y.length)
			throw new RuntimeException("Array lengths " + x.length + " and " + y.length + " do not match!");
		if (copyArrays) {
			this.x = Arrays.copyOf(x, x.length);
			this.y = Arrays.copyOf(y, y.length);
		} else {
			this.x = x;
			this.y = y;			
		}
		size = this.x.length;
	}
	
	DefaultVertices() {
		this(DEFAULT_CAPACITY);
	}
	
	DefaultVertices(final int capacity) {
		x = new float[capacity];
		y = new float[capacity];
		size = 0;
	}
	
	
	void clear() {
		size = 0;
	}
	
	void add(final float x, final float y) {
		if (size == this.x.length)
			ensureCapacity(size + 16);
		this.x[size] = x;
		this.y[size] = y;
		size++;
	}
	
	void set(final int idx, final float x, final float y) {
		this.x[idx] = x;
		this.y[idx] = y;
	}
	
	void close() {
//		// If we have a single value, duplicate it for closing
//		if (size == 1) {
//			ensureCapacity(2);
//			x[size] = this.x[0];
//			y[size] = this.y[0];
//			size++;
//		}
//		else
		if (size > 1) {
			if (x[0] != x[size-1] || y[0] != y[size-1]) {
				ensureCapacity(size+1);
				x[size] = this.x[0];
				y[size] = this.y[0];
				size++;
			}
		}
		compact();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return size == 0;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#size()
	 */
	@Override
	public int size() {
		return size;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#getX(float[])
	 */
	@Override
	public float[] getX(float[] xArray) {
		return getArray(x, xArray);
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#getY(float[])
	 */
	@Override
	public float[] getY(float[] yArray) {
		return getArray(y, yArray);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#get(int)
	 */
	@Override
	public Point2 get(int idx) {
		if (idx >= size)
			throw new ArrayIndexOutOfBoundsException(idx + " is greater than size " + size);
		return new Point2(x[idx], y[idx]);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#getX(int)
	 */
	@Override
	public float getX(int idx) {
		if (idx >= size)
			throw new ArrayIndexOutOfBoundsException(idx + " is greater than size " + size);
		return x[idx];
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#getY(int)
	 */
	@Override
	public float getY(int idx) {
		if (idx >= size)
			throw new ArrayIndexOutOfBoundsException(idx + " is greater than size " + size);
		return y[idx];
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#getPoints()
	 */
	@Override
	public List<Point2> getPoints() {
		List<Point2> points = new ArrayList<>();
		for (int i = 0; i < size; i++)
			points.add(new Point2(x[i], y[i]));
		return points;
	}
	
	/**
	 * Ensure the minimum capacity is at least equal to the specified parameter.
	 * Note that if N coordinates need to be added, then this method should be called with parameters size() + N.
	 * @param capacity
	 */
	void ensureCapacity(final int capacity) {
		if (capacity <= x.length)
			return;
		x = Arrays.copyOf(x, capacity);
		y = Arrays.copyOf(y, capacity);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.Vertices#compact()
	 */
	@Override
	public void compact() {
		if (size == x.length)
			return;
		x = Arrays.copyOf(x, size);
		y = Arrays.copyOf(y, size);
	}

	float[] getArray(final float[] src, float[] dst) {
		if (dst == null || dst.length < size())
			return Arrays.copyOf(src, size);
		System.arraycopy(src, 0, dst, 0, size);
		return dst;
	}
	
	void translate(final float dx, final float dy) {
		for (int i = 0; i < size; i++) {
			x[i] += dx;
			y[i] += dy;
		}
	}

	@Override
	public Vertices duplicate() {
		DefaultVertices v = new DefaultVertices(x.length);
		v.x = Arrays.copyOf(x, x.length);
		v.y = Arrays.copyOf(y, y.length);
		v.size = size;
		return v;
	}

//	@Override
//	public VerticesIterator getIterator() {
//		return new DefaultVerticesIterator(this);
//	}
//
//	
//	
//	static class DefaultVerticesIterator implements VerticesIterator {
//
//		final private DefaultVertices vertices;
//		private int ind = 0;
//
//		private DefaultVerticesIterator(final DefaultVertices vertices) {
//			this.vertices = vertices;
//		}
//
//		@Override
//		final public boolean hasNext() {
//			return ind < vertices.size();
//		}
//
//		@Override
//		final public void next() {
//			this.ind++;
//		}
//		
//		public float getX() {
//			return vertices.x[ind];
//		}
//
//		public float getY() {
//			return vertices.y[ind];
//		}
//
//		@Override
//		final public void currentVertex(float[] coords) {
//			coords[0] = vertices.x[ind];
//			coords[1] = vertices.y[ind];
//		}
//		
//	}
	
	
	
//	@Override
//	public void writeExternal(ObjectOutput out) throws IOException {
//		out.writeInt(1); // Version
//		out.writeInt(size); // Size
//		out.writeObject(x);
//		out.writeObject(y);
//	}
//
//	@Override
//	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//		int version = in.readInt();
//		if (version != 1)
//			logger.warn("Incompatible version number for vertices object!");
//		size = in.readInt();
//		x = (float[])in.readObject();
//		y = (float[])in.readObject();
//	}
	
	

}
