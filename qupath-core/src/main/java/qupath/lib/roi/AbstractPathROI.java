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

import org.locationtech.jts.geom.Geometry;

import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.jts.ConverterJTS;

/**
 * Abstract implementation of a ROI.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathROI implements ROI {
	
	// Dimension variables
	int c = -1; // Defaults to -1, indicating all channels
	int t = 0; // Defaults to 0, indicating first time point
	int z = 0; // Defaults to 0, indiciating first z-slice
	
	transient ImagePlane plane;
	
	public AbstractPathROI() {
		this(null);
	}
	
	public AbstractPathROI(ImagePlane plane) {
		super();
		if (plane == null)
			plane = ImagePlane.getDefaultPlane();
		this.c = plane.getC();
		this.z = plane.getZ();
		this.t = plane.getT();
	}
	
	@Override
	public ImagePlane getImagePlane() {
		if (plane == null)
			plane = ImagePlane.getPlaneWithChannel(c, z, t);
		return plane;
	}
	
//	Object asType(Class<?> cls) {
//		if (cls.isInstance(this))
//			return this;
//		
//		if (cls == Geometry.class)
//			return ConverterJTS.getGeometry(this);
//
//		if (cls == Shape.class)
//			return PathROIToolsAwt.getShape(this);
//		
//		throw new ClassCastException("Cannot convert " + t + " to " + cls);
//	}
	
	@Override
	public int getZ() {
		return z;
	}
	
	@Override
	public int getT() {
		return t;
	}

	@Override
	public int getC() {
		return c;
	}
	
	/**
	 * TRUE if the bounding box has zero area
	 */
	@Override
	public boolean isEmpty() {
		return getBoundsWidth() * getBoundsHeight() == 0;
	}
	
	@Override
	public String toString() {
//		Rectangle bounds = getBounds();
//		return String.format("%s (%d, %d, %d, %d)", getROIType(), bounds.x, bounds.y, bounds.width, bounds.height);
//		String name = getName();
//		if (name != null)
////			return name;			
//			return name + " - " + getROIType();			
		return getRoiName();
//		return String.format("%s (%.1f, %.1f)", getROIType(), getCentroidX(), getCentroidY());
//		return "Me";
	}
	

	@Override
	public boolean isLine() {
		return getRoiType() == RoiType.LINE;
	}
	
	@Override
	public boolean isArea() {
		return getRoiType() == RoiType.AREA;
	}
	
	@Override
	public boolean isPoint() {
		return getRoiType() == RoiType.POINT;
	}
	
	private static ConverterJTS converter = new ConverterJTS.Builder().build();
	
	@Override
	public Geometry getGeometry() {
		return converter.roiToGeometry(this);
	}
	
	
}