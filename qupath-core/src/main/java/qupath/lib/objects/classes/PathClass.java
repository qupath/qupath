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

package qupath.lib.objects.classes;

import java.io.Serializable;

import qupath.lib.common.ColorTools;

/**
 * Representation of an object's classification - which can be defined using any unique string identifier (e.g. tumour, lymphocyte, gland, benign, malignant).
 * <p>
 * In order to keep the construction of PathClasses under control, they should be generated using the static methods within PathClassFactory.
 * 
 * @see PathClassFactory
 * 
 * @author Pete Bankhead
 *
 */
public class PathClass implements Comparable<PathClass>, Serializable {
	
	private static final long serialVersionUID = 1L;

	private static String defaultName = "Unclassified";
	
	private PathClass parentClass = null;
	private String name = null;
	private Integer colorRGB = ColorTools.makeRGB(64, 64, 64);

	PathClass() {}

	PathClass(PathClass parent, String name, Integer colorRGB) {
		this.parentClass = parent;
		this.name = name;
		if (colorRGB != null)
			this.colorRGB = colorRGB;
	}
	
	PathClass(String name, Integer colorRGB) {
		this(null, name, colorRGB);
	}
	
	public PathClass getParentClass() {
		return parentClass;
	}
	
//	/**
//	 * Derive a new PathClass from this one.
//	 * The purpose is to create multiple classes for sub-categorization e.g. of intensity.
//	 * 
//	 * @return
//	 */
//	public PathClass deriveClass(String name, Color color) {
//		if (color == null)
//			color = this.getColor();
//		PathClass childClass = new PathClass(name, color);
//		childClass.parentClass = this;
//		return childClass;
//	}
	
	public boolean isDerivedClass() {
		return parentClass != null;
	}
	
	/**
	 * Returns TRUE if this class, or any ancestor class, is equal to the specified parent class.
	 * 
	 * @param parentClass
	 * @return
	 */
	public boolean isDerivedFrom(PathClass parentClass) {
		PathClass pathClass = this;
		while (pathClass != null) {
			if (pathClass.equals(parentClass))
				return true;
			pathClass = pathClass.parentClass;
		}
		return false;
	}
	
	
	/**
	 * Returns {@code true} if this class is equal to the specified child class, 
	 * or an ancestor of that class.
	 * 
	 * @param childClass
	 * @return
	 */
	public boolean isAncestorOf(PathClass childClass) {
		PathClass pathClass = childClass;
		while (pathClass != null) {
			if (this.equals(pathClass))
				return true;
			pathClass = pathClass.parentClass;
		}
		return false;
	}
	
	
	/**
	 * Get the 'base' class, i.e. trace back through getParentClass() until no parent is available.
	 * 
	 * For a PathClass with no parent, this just returns itself.
	 * 
	 * @return
	 */
	public PathClass getBaseClass() {
		PathClass temp = this;
		while (temp.getParentClass() != null)
			temp = temp.getParentClass();
		return temp;
	}
	
	public void setColor(Integer colorRGB) {
		if (colorRGB == null || !colorRGB.equals(this.colorRGB))
			this.colorRGB = colorRGB;
	}
	
	public Integer getColor() {
		return colorRGB;
	}
	
	public String getName() {
		return name;
	}
	
	static String derivedClassToString(PathClass parent, String name) {
		return parent == null ? name : parent.toString() + ": " + name;
	}
	
	@Override
	public String toString() {
		if (name == null)
			return defaultName;
		if (isDerivedClass())
			return derivedClassToString(parentClass, name);
		else
			return name;
	}
	
	public boolean isDefault() {
		return name == null;
	}

	@Override
	public int compareTo(PathClass o) {
		if (name == null) {
			if (o.getName() == null)
				return 0;
			else
				return -1;
		} else if (o.getName() == null)
			return 1;
		return name.compareTo(o.getName());
	}
	
}