/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.roi;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;

/**
 * Simple class to store x,y coordinates as floating point arrays.
 * <p>
 * Future implementations may support other dimensions, stored in a single packed array.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultMutableVertices implements Vertices, MutableVertices {
	
	static Logger logger = LoggerFactory.getLogger(DefaultMutableVertices.class);
	
	private DefaultVertices vertices;
	
	DefaultMutableVertices(final DefaultVertices vertices) {
		this.vertices = vertices;
	}
	
	DefaultMutableVertices(final Vertices vertices) {
		this.vertices = new DefaultVertices(vertices.getX(null), vertices.getY(null), false);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#clear()
	 */
	@Override
	public void clear() {
		vertices.clear();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#add(double, double)
	 */
	@Override
	public void add(final double x, final double y) {
		vertices.add((float)x, (float)y);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#add(float, float)
	 */
	@Override
	public void add(final float x, final float y) {
		vertices.add(x, y);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#set(int, float, float)
	 */
	@Override
	public void set(final int idx, final float x, final float y) {
		vertices.set(idx, x, y);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#set(int, double, double)
	 */
	@Override
	public void set(final int idx, final double x, final double y) {
		vertices.set(idx, (float)x, (float)y);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#close()
	 */
	@Override
	public void close() {
		vertices.close();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return vertices.isEmpty();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#size()
	 */
	@Override
	public int size() {
		return vertices.size();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getX(float[])
	 */
	@Override
	public float[] getX(float[] xArray) {
		return vertices.getX(xArray);
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getY(float[])
	 */
	@Override
	public float[] getY(float[] yArray) {
		return vertices.getY(yArray);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getX(int)
	 */
	@Override
	public float getX(int idx) {
		return vertices.getX(idx);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#get(int)
	 */
	@Override
	public Point2 get(int idx) {
		return vertices.get(idx);
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getPoints()
	 */
	@Override
	public List<Point2> getPoints() {
		return vertices.getPoints();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getY(int)
	 */
	@Override
	public float getY(int idx) {
		return vertices.getY(idx);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#ensureCapacity(int)
	 */
	@Override
	public void ensureCapacity(final int capacity) {
		vertices.ensureCapacity(capacity);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#compact()
	 */
	@Override
	public void compact() {
		vertices.compact();
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#getVertices()
	 */
	@Override
	public Vertices getVertices() {
		return vertices;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.vertices.MutableVertices#translate(float, float)
	 */
	@Override
	public void translate(final float dx, final float dy) {
		vertices.translate(dx, dy);
	}

	@Override
	public Vertices duplicate() {
		return new DefaultMutableVertices((DefaultVertices)vertices.duplicate());
	}

//	@Override
//	public VerticesIterator getIterator() {
//		return vertices.getIterator();
//	}

//	@Override
//	public void writeExternal(ObjectOutput out) throws IOException {
//		out.writeInt(1); // Version
//		out.writeObject(vertices);
//	}
//
//	@Override
//	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//		logger.info("Reading vertices...");
//		int version = in.readInt();
//		logger.info("About to read vertices, version " + version);
//		if (version != 1)
//			logger.warn("Incompatible version number for vertices object!");
//		vertices = (DefaultVertices)in.readObject();
//		logger.info("Vertices... " + vertices);
//	}
	

}
