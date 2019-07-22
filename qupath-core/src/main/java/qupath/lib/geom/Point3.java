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

/**
 * A 3D point (x, y &amp; z coordinates).
 * 
 * @author Pete Bankhead
 *
 */
class Point3 extends AbstractPoint implements Externalizable {
	
	private double x, y, z;
	
	// For reasons of supporting Externalizable...
	public Point3() {}

	public Point3(final double x, final double y, final double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public double distanceSq(final double x, final double y, final double z) {
		double dx = this.x - x;
		double dy = this.y - y;
		double dz = this.z - z;
		return dx * dx + dy * dy + dz * dz;
	}
	
	public double distance(final double x, final double y, final double z) {
		return Math.sqrt(distanceSq(x, y, z));
	}
	
	public double distance(final Point3 p) {
		return distance(p.getX(), p.getY(), p.getZ());
	}

	@Override
	public double get(int dim) {
		if (dim == 0)
			return x;
		else if (dim == 1)
			return y;
		else if (dim == 2)
			return z;
		throw new RuntimeException("Requested dimension " + dim + " for Point3 - allowable values are 0, 1 and 2");
	}

	@Override
	public int dim() {
		return 3;
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(1); // Version
		out.writeDouble(x);
		out.writeDouble(y);
		out.writeDouble(z);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		in.skipBytes(4); // Version
		x = in.readDouble();
		y = in.readDouble();
		z = in.readDouble();
	}

}