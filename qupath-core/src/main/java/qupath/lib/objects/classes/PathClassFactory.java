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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import qupath.lib.common.ColorTools;

/**
 * Factory for creating PathClasses.
 * 
 * This is used, rather than creating PathClass objects directly, to ensure that 
 * only one PathClass with a specified name (and, optionally, ancestry) can exist 
 * at any time.
 * 
 * @author Pete Bankhead
 *
 */
public class PathClassFactory {
	
//	public enum DEFAULT_PATH_CLASSES_ENUM {NUCLEUS, POSITIVE, NEGATIVE, ONE_PLUS, TWO_PLUS, THREE_PLUS, TUMOR, NON_TUMOR, STROMA, BACKGROUND};

//	public enum PathClasses { TUMOR, NON_TUMOR, STROMA, IMMUNE_CELLS, NUCLEUS, CELL, WHITESPACE, NEGATIVE, POSITIVE, ONE_PLUS, TWO_PLUS, THREE_PLUS, ARTIFACT, IMAGE_ROOT, NECROSIS, OTHER }
	public enum PathClasses { TUMOR, NON_TUMOR, STROMA, IMMUNE_CELLS, NUCLEUS, CELL, WHITESPACE, NEGATIVE, POSITIVE, ARTIFACT, IMAGE_ROOT, NECROSIS, OTHER, REGION }

	private static Map<PathClasses, PathClass> DEFAULT_PATH_CLASSES;
	
	private static Map<String, PathClass> mapPathBaseClasses = new HashMap<>();
	private static Map<String, PathClass> mapPathDerivedClasses = new HashMap<>();

	private final static PathClass NULL_CLASS = new PathClass();

	private final static String REGION = "Region";

	private final static String POSITIVE = "Positive";
	private final static String NEGATIVE = "Negative";
	private final static String ONE_PLUS = "1+";
	private final static String TWO_PLUS = "2+";
	private final static String THREE_PLUS = "3+";
	private static List<String> intensityClassNames = Arrays.asList(ONE_PLUS, TWO_PLUS, THREE_PLUS);
	
	static  {
		DEFAULT_PATH_CLASSES = new HashMap<>();
		DEFAULT_PATH_CLASSES.put(PathClasses.TUMOR, new PathClass("Tumor", ColorTools.makeRGB(200, 0, 0)));
		DEFAULT_PATH_CLASSES.put(PathClasses.NON_TUMOR, new PathClass("Non-tumor", ColorTools.makeRGB(140, 220, 90)));
		DEFAULT_PATH_CLASSES.put(PathClasses.STROMA, new PathClass("Stroma", ColorTools.makeRGB(150, 200, 150)));
		DEFAULT_PATH_CLASSES.put(PathClasses.IMMUNE_CELLS, new PathClass("Immune cells", ColorTools.makeRGB(160, 90, 160)));
		DEFAULT_PATH_CLASSES.put(PathClasses.NUCLEUS, new PathClass("Nucleus", ColorTools.makeRGB(20, 200, 20)));
		DEFAULT_PATH_CLASSES.put(PathClasses.CELL, new PathClass("Cell", ColorTools.makeRGB(220, 0, 0)));
		DEFAULT_PATH_CLASSES.put(PathClasses.WHITESPACE, new PathClass("Whitespace", ColorTools.makeRGB(180, 180, 180)));
		DEFAULT_PATH_CLASSES.put(PathClasses.POSITIVE, new PathClass(POSITIVE, ColorTools.makeRGB(200, 50, 50)));
		DEFAULT_PATH_CLASSES.put(PathClasses.NEGATIVE, new PathClass(NEGATIVE, ColorTools.makeRGB(90, 90, 180)));
//		DEFAULT_PATH_CLASSES.put(PathClasses.ONE_PLUS, new PathClass(ONE_PLUS, ColorTools.makeRGB(255, 215, 0)));
//		DEFAULT_PATH_CLASSES.put(PathClasses.TWO_PLUS, new PathClass(TWO_PLUS, ColorTools.makeRGB(225, 150, 50)));
//		DEFAULT_PATH_CLASSES.put(PathClasses.THREE_PLUS, new PathClass(THREE_PLUS, ColorTools.makeRGB(200, 50, 50)));
		DEFAULT_PATH_CLASSES.put(PathClasses.ARTIFACT, new PathClass("Artefact", ColorTools.makeRGB(180, 180, 180)));
		DEFAULT_PATH_CLASSES.put(PathClasses.IMAGE_ROOT,  new PathClass("Image", ColorTools.makeRGB(128, 128, 128)));
		
		DEFAULT_PATH_CLASSES.put(PathClasses.REGION, new PathClass(REGION, ColorTools.makeRGB(0, 0, 180)));

		DEFAULT_PATH_CLASSES.put(PathClasses.NECROSIS, new PathClass("Necrosis", ColorTools.makeRGB(50, 50, 50)));
		DEFAULT_PATH_CLASSES.put(PathClasses.OTHER, new PathClass("Other", ColorTools.makeRGB(255, 200, 0)));

		for (PathClass pathClass : DEFAULT_PATH_CLASSES.values())
			mapPathBaseClasses.put(pathClass.toString(), pathClass);
		
	}
	
	
	public static final Integer COLOR_POSITIVE = ColorTools.makeRGB(200, 50, 50);
	public static final Integer COLOR_NEGATIVE = ColorTools.makeRGB(90, 90, 180);
	public static final Integer COLOR_ONE_PLUS = ColorTools.makeRGB(255, 215, 0);
	public static final Integer COLOR_TWO_PLUS = ColorTools.makeRGB(225, 150, 50);
	public static final Integer COLOR_THREE_PLUS = ColorTools.makeRGB(200, 50, 50);
	
	

//	public static PathClass getPathClass(String name, Color color) {
//		return getPathClass(name, color == null ? null : color.getRGB());
//	}
	
	
	/**
	 * Get the 'Region' class.
	 * 
	 * This behaves slightly differently from other classes, e.g. it is not filled in when applied to
	 * annotations.  Consequently it is good to heavily annotated regions, or possibly detected tissue 
	 * containing further annotations inside.
	 * 
	 * @return
	 */
	public static PathClass getRegionClass() {
		return getDefaultPathClass(PathClasses.REGION);
	}
	
	/**
	 * Returns true if the PathClass represents a built-in intensity class.
	 * 
	 * Here, this means its name is equal to 1+, 2+ or 3+.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isDefaultIntensityClass(final PathClass pathClass) {
		return pathClass != null && intensityClassNames.contains(pathClass.getName());
	}
	
	public static boolean isOnePlus(final PathClass pathClass) {
		return pathClass != null && ONE_PLUS.equals(pathClass.getName());
	}

	public static boolean isTwoPlus(final PathClass pathClass) {
		return pathClass != null && TWO_PLUS.equals(pathClass.getName());
	}

	public static boolean isThreePlus(final PathClass pathClass) {
		return pathClass != null && THREE_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns true if the PathClass has the name "Positive".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isPositiveClass(final PathClass pathClass) {
		return pathClass != null && POSITIVE.equals(pathClass.getName());
	}
	
	public static boolean isPositiveOrPositiveIntensityClass(final PathClass pathClass) {
		return pathClass != null && (isPositiveClass(pathClass) || isOnePlus(pathClass) || isTwoPlus(pathClass) || isThreePlus(pathClass));
	}
	
	/**
	 * Returns true if the PathClass has the name "Negative".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isNegativeClass(final PathClass pathClass) {
		return pathClass != null && NEGATIVE.equals(pathClass.getName());
	}

	/**
	 * Get the first ancestor class that is not an intensity class (i.e. not negative, positive, 1+, 2+ or 3+).
	 * 
	 * This will return null if pathClass is null, or if no non-intensity classes are found.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static PathClass getNonIntensityAncestorClass(PathClass pathClass) {
		while (pathClass != null && (PathClassFactory.isPositiveOrPositiveIntensityClass(pathClass) || PathClassFactory.isNegativeClass(pathClass)))
			pathClass = pathClass.getParentClass();
		return pathClass;
	}
	
	
	public static PathClass getPathClass(String name, Integer rgb) {
		if (name == null || name.equals(NULL_CLASS.toString()) || name.equals(NULL_CLASS.getName()))
			return NULL_CLASS;
		PathClass pathClass = mapPathBaseClasses.get(name);
		if (pathClass == null) {
			if (rgb == null) {
				// Use default colors for intensity classes
				if (name.equals(ONE_PLUS)) {
					rgb = COLOR_ONE_PLUS;
				} else if (name.equals(TWO_PLUS)) {
					rgb = COLOR_TWO_PLUS;
				} else if (name.equals(THREE_PLUS))
					rgb = COLOR_THREE_PLUS;
				else if (name.equals(POSITIVE)) {
					rgb = COLOR_POSITIVE;
				} else if (name.equals(NEGATIVE)) {
					rgb = COLOR_NEGATIVE;
				} else {
					// Create a random color
					// Use the hashcode of the String as a seed - so that the same 
					// color is generated reproducibly for the same name.
					Random random = new Random(name.hashCode());
					rgb = ColorTools.makeRGB(
							random.nextInt(256),
							random.nextInt(256),
							random.nextInt(256));
				}
			}
			pathClass = new PathClass(null, name, rgb);
			mapPathBaseClasses.put(pathClass.toString(), pathClass);
		}
		return pathClass;
	}
	
	public static PathClass getPathClass(String name) {
		return getPathClass(name, (Integer)null);
	}
	
	
	public static boolean pathClassExists(String name) {
		if (name == null)
			return true;
		return mapPathBaseClasses.containsKey(name) || mapPathDerivedClasses.containsKey(name);
	}
	
	
	/**
	 * Return a singleton version of a specific PathClass.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static PathClass getSingletonPathClass(PathClass pathClass) {
		if (pathClass.getParentClass() == null)
			return getPathClass(pathClass.getName(), pathClass.getColor());
		return getDerivedPathClass(getSingletonPathClass(pathClass.getParentClass()), pathClass.getName(), pathClass.getColor());
	}
	
	
//	public static PathClass getDerivedPathClass(PathClass parentClass, String name, Color color) {
//		return getDerivedPathClass(parentClass, name, color == null ? null : color.getRGB());
//	}
	
	
	public static PathClass getDerivedPathClass(PathClass parentClass, String name, Integer rgb) {
		if (parentClass == null || parentClass.isDefault())
			return getPathClass(name, rgb);
		String nameNew = PathClass.derivedClassToString(parentClass, name);
//		mapPathDerivedClasses.clear();
		PathClass pathClass = mapPathDerivedClasses.get(nameNew);
		if (pathClass == null) {
			if (rgb == null) {
				boolean isTumor = DEFAULT_PATH_CLASSES.get(PathClasses.TUMOR) == parentClass;
				int parentRGB = parentClass.getColor();
				if (name.equals(ONE_PLUS)) {
					rgb = isTumor ? COLOR_ONE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.9);
				} else if (name.equals(TWO_PLUS)) {
					rgb = isTumor ? COLOR_TWO_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.6);
				} else if (name.equals(THREE_PLUS))
					rgb = isTumor ? COLOR_THREE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.4);
				else if (name.equals(POSITIVE)) {
					rgb = isTumor ? COLOR_POSITIVE : ColorTools.makeScaledRGB(parentRGB, 0.75);
				} else if (name.equals(NEGATIVE)) {
					rgb = isTumor ? COLOR_NEGATIVE : ColorTools.makeScaledRGB(parentRGB, 1.25);
				} else {
					double scale = 1.5;
					rgb = ColorTools.makeScaledRGB(parentRGB, scale);
				}
			}
//				rgb = new Color(parentClass.getColor()).brighter().getRGB();
			pathClass = new PathClass(parentClass, name, rgb);
			mapPathDerivedClasses.put(pathClass.toString(), pathClass);
		}
		return pathClass;
	}
	
	public static PathClass getOnePlus(PathClass parentClass, Integer color) {
		return getDerivedPathClass(parentClass, ONE_PLUS, color);
	}

	public static PathClass getTwoPlus(PathClass parentClass, Integer color) {
		return getDerivedPathClass(parentClass, TWO_PLUS, color);
	}

	public static PathClass getThreePlus(PathClass parentClass, Integer color) {
		return getDerivedPathClass(parentClass, THREE_PLUS, color);
	}
	
	public static PathClass getNegative(PathClass parentClass, Integer color) {
		return getDerivedPathClass(parentClass, NEGATIVE, color);
	}
	
	public static PathClass getPositive(PathClass parentClass, Integer color) {
		return getDerivedPathClass(parentClass, POSITIVE, color);
	}

	public static PathClass getNucleusClass() {
		return getPathClass("Nucleus");
	}
	
	public static PathClass getDefaultPathClass(PathClasses pathClass) {
		return DEFAULT_PATH_CLASSES.get(pathClass);
	}
	
	public static String getNegativeClassName() {
		return NEGATIVE;
	}

	public static String getPositiveClassName() {
		return POSITIVE;
	}

	/**
	 * Return a special 'null' class to represent no classification.
	 * 
	 * This is useful for displaying available classes; it should not be set as the class for any object.
	 * 
	 * @return
	 */
	public static PathClass getPathClassUnclassified() {
		return NULL_CLASS;
	}
	
//	public static PathClass

}
