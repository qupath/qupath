/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects.classes;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import qupath.lib.common.ColorTools;

/**
 * Representation of an object's classification - which can be defined using any unique string identifier (e.g. tumour, lymphocyte, gland, benign, malignant).
 * <p>
 * The constructors in this class should never be called directly.
 * In order to keep the construction of PathClasses under control, they should be accessed using the static methods within {@link PathClassFactory}.
 * 
 * @see PathClassFactory
 * 
 * @author Pete Bankhead
 *
 */
public class PathClass implements Comparable<PathClass>, Serializable {
	
	private static final long serialVersionUID = 1L;

	private static String defaultName = "Unclassified";
	private static Integer DEFAULT_COLOR = ColorTools.packRGB(64, 64, 64);
	
	private final PathClass parentClass;
	private final String name;
	private Integer colorRGB;
	
	private final static UUID secret = UUID.randomUUID();
	
	/**
	 * Cached String representation
	 */
	private transient String stringRep = null;
	
	private static PathClass NULL_CLASS = new PathClass(secret);
	
	private static List<String> illegalCharacters = Arrays.asList("\n", ":", "\r");
	
	private static Map<String, PathClass> existingClasses = Collections.synchronizedMap(new HashMap<>());

	private PathClass(UUID mySecret) {
		if (!Objects.equals(secret, mySecret))
			throw new IllegalStateException("You should not access the PathClass constructor!");
		if (NULL_CLASS != null) {
			throw new IllegalStateException("The NULL PathClass should not be created more than once!");
		}
		parentClass = null;
		name = null;
		colorRGB = null;
//		if (!existingClasses.add(null))
//			throw new IllegalArgumentException("PathClass constructor has been called multiple times!");
	}

	/**
	 * This constructor should <i>not<i> be called explicitly; rather, use {@link PathClassFactory}. 
	 * <p>
	 * Only one instance of a PathClass should exist for any given name and list of ancestors.
	 * 
	 * @param parent
	 * @param name
	 * @param colorRGB
	 */
	private PathClass(UUID mySecret, PathClass parent, String name, Integer colorRGB) {
		if (!Objects.equals(secret, mySecret))
			throw new IllegalStateException("You should not access the PathClass constructor!");
		
		if (name != null)
			name = name.strip();

		if (!isValidName(name))
			throw new IllegalArgumentException(name + " is not a valid PathClass name!");
		
		this.parentClass = parent;
		this.name = name;
		
		if (colorRGB == null)
			this.colorRGB = DEFAULT_COLOR;
		else
			this.colorRGB = colorRGB;
		
		if (PathClassFactory.classExists(toString()))
			throw new IllegalStateException("Cannot create the same PathClass more than once!");
		
	}
	
	/**
	 * Return whether the specified name is a valid name for a PathClass.
	 * To be valid, it should be non-null, non-blank, and not contain any illegal characters (colons, linebreaks).
	 * @param name
	 */
	private static boolean isValidName(String name) {
		if (name == null || name.isBlank())
			return false;
		for (var illegal : illegalCharacters)
			if (name.contains(illegal))
				return false;
		return true;
	}
	
	synchronized static PathClass getNullClass() {
		return NULL_CLASS;
	}
	
	synchronized static PathClass getInstance(PathClass parent, String name, Integer colorRGB) {
		if (parent == getNullClass())
			parent = null;
		
		if (parent == null && name == null)
			return getNullClass();
		
		var pathClass = new PathClass(secret, parent, name, colorRGB);
		var s = pathClass.toString();
		
		// Make a last attempt to return an existing class with the same name, if we can
		var previous = existingClasses.putIfAbsent(s, pathClass);
		return previous == null ? pathClass : previous;
	}
	
	
	/**
	 * Get the parent classification, or null if this classification has no parent.
	 * @return
	 */
	public PathClass getParentClass() {
		return parentClass;
	}
	
	/**
	 * Returns {@code true} if {@code #getParentClass() != null}.
	 * @return
	 */
	public boolean isDerivedClass() {
		return parentClass != null;
	}
	
	/**
	 * Returns {@code true} if this class, or any ancestor class, is equal to the specified parent class.
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
	 * Get the 'base' class, i.e. trace back through {@link #getParentClass()} until no parent is available.
	 * <p>
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
	
	/**
	 * Set the color that should be used to display objects with this classification.
	 * @param colorRGB color, as a packed (A)RGB value
	 */
	public void setColor(Integer colorRGB) {
		if (colorRGB == null || !colorRGB.equals(this.colorRGB))
			this.colorRGB = colorRGB;
	}
	
	/**
	 * Get the color that should be used to display objects with this classification.
	 * @return packed (A)RGB value representing the classification color.
	 */
	public Integer getColor() {
		return colorRGB;
	}
	
	/**
	 * Get the name of this classification. 
	 * Note that this does not incorporate information from any parent classifications; to access this, 
	 * use {@link #toString()} instead.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	static String derivedClassToString(PathClass parent, String name) {
		return parent == null ? name : parent.toString() + ": " + name;
	}
	
	@Override
	public String toString() {
		if (stringRep == null) {
			if (name == null)
				stringRep = defaultName;
			else if (isDerivedClass())
				stringRep = derivedClassToString(parentClass, name);
			else
				stringRep = name;
		}
		return stringRep;
	}
	
	/**
	 * A PathClass is valid if its name is not null.
	 * <p>
	 * This should generally the case, but a single (invalid) PathClass with a null name 
	 * can be used to indicate the absence of a classification; however, it should <i>not</i> be assigned 
	 * to any object.  Rather, objects should be assigned either a valid PathClass or null to indicate 
	 * that they have no classification.
	 * 
	 * @return
	 */
	public boolean isValid() {
		return name != null;
	}

	/**
	 * This is now equivalent to {@code this.toString().compareTo(o.toString())}.
	 * <p>
	 * Note that in previous versions (&lt; 0.1.2), the comparison was made based on the name only.
	 * <p>
	 * This could result in unexpected behavior whenever comparing with equality and using 
	 * derived {@code PathClass} objects, because only the (final) name part was being compared 
	 * and this could potentially result in classifications (wrongly) being considered equal 
	 * (e.g. "Tumor: Positive" and "Stroma: Positive").
	 * <p>
	 * This was most significant when working with Groovy, where {@code == } is replaced by {@code compareTo}.
	 */
	@Override
	public int compareTo(PathClass o) {
		return toString().compareTo(o.toString());
		// Old behavior (v0.1.2) - can give unexpected results with Groovy == comparisons
//		if (name == null) {
//			if (o.getName() == null)
//				return 0;
//			else
//				return -1;
//		} else if (o.getName() == null)
//			return 1;
//		return name.compareTo(o.getName());
	}
	
	
	
}