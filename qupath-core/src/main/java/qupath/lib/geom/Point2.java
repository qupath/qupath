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

package qupath.lib.geom;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A 2D point (x &amp; y coordinates).
 * 
 * @author Pete Bankhead
 *
 */
public class Point2 extends AbstractPoint implements Externalizable {
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(x);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point2 other = (Point2) obj;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		return true;
	}

	static Logger logger = LoggerFactory.getLogger(Point2.class);
	
	private double x, y;
	
	/**
	 * Default constructor for a point at location (0,0).
	 */
	public Point2() {}
	
	/**
	 * Point constructor.
	 * @param x
	 * @param y
	 */
	public Point2(final double x, final double y) {
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Get the x coordinate of this point.
	 * @return
	 */
	public double getX() {
		return x;
	}

	/**
	 * Get the y coordinate of this point.
	 * @return
	 */
	public double getY() {
		return y;
	}

	/**
	 * Calculate the squared distance between this point and a specified x and y location.
	 * @param x
	 * @param y
	 * @return
	 */
	public double distanceSq(final double x, final double y) {
		double dx = this.x - x;
		double dy = this.y - y;
		return dx * dx + dy * dy;
	}
	
	/**
	 * Calculate the distance between this point and a specified x and y location.
	 * @param x
	 * @param y
	 * @return
	 */
	public double distance(final double x, final double y) {
		return Math.sqrt(distanceSq(x, y));
	}
	
	/**
	 * Calculate the distance between this point and another point.
	 * @param p
	 * @return
	 */
	public double distance(final Point2 p) {
		return distance(p.getX(), p.getY());
	}

	@Override
	public double get(int dim) {
		if (dim == 0)
			return x;
		else if (dim == 1)
			return y;
		throw new IllegalArgumentException("Requested dimension " + dim + " for Point2 - allowable values are 0 and 1");
	}

	@Override
	public int dim() {
		return 2;
	}
	
	
	@Override
	public String toString() {
		return "Point: " + x + ", " + y;
	}
	

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(1); // Version
		out.writeDouble(x);
		out.writeDouble(y);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		in.skipBytes(4); // Version
		x = in.readDouble();
		y = in.readDouble();
	}

}
