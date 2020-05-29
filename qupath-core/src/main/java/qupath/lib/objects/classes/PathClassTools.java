/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.objects.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import qupath.lib.common.ColorTools;

/**
 * Static methods for use with {@link PathClass} objects.
 * 
 * @author Pete Bankhead
 */
public class PathClassTools {

	/**
	 * Returns true if the PathClass represents a built-in intensity class.
	 * Here, this means its name is equal to "1+", "2+" or "3+".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isGradedIntensityClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.intensityClassNames.contains(pathClass.getName());
	}
	
	/**
	 * Returns true if the PathClass should be ignored from some operations.
	 * In practice, this checks if the name ends with an asterisk.
	 * It is useful to avoid generating objects for certain classes (e.g. Ignore*, Artefact*, Background*) 
	 * where these would not be meaningful.
	 * @param pathClass
	 * @return
	 */
	public static boolean isIgnoredClass(final PathClass pathClass) {
		return pathClass == null || pathClass.getName() == null || pathClass.getName().endsWith("*");
	}
	
	/**
	 * Returns true if the name of the class is "1+", indicating a weakly-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isOnePlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.ONE_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns true if the name of the class is "2+", indicating a moderately-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isTwoPlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.TWO_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns true if the name of the class is "3+", indicating a weakly-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isThreePlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.THREE_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns {@code true} if the PathClass has the name "Positive".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isPositiveClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.POSITIVE.equals(pathClass.getName());
	}
	
	/**
	 * Returns true if the name of the class is "Positive", "1+", "2+" or "3+", indicating positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isPositiveOrGradedIntensityClass(final PathClass pathClass) {
		return pathClass != null && (isPositiveClass(pathClass) || isOnePlus(pathClass) || isTwoPlus(pathClass) || isThreePlus(pathClass));
	}
	
	/**
	 * Returns true if the PathClass has the name "Negative".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isNegativeClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.NEGATIVE.equals(pathClass.getName());
	}

	/**
	 * Get the first ancestor class that is not an intensity class (i.e. not negative, positive, 1+, 2+ or 3+).
	 * <p>
	 * This will return null if pathClass is null, or if no non-intensity classes are found.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static PathClass getNonIntensityAncestorClass(PathClass pathClass) {
		while (pathClass != null && (isPositiveOrGradedIntensityClass(pathClass) || isNegativeClass(pathClass)))
			pathClass = pathClass.getParentClass();
		return pathClass;
	}
	
	/**
	 * Get a list containing the distinct names of all constituent parts of a {@link PathClass}.
	 * 
	 * @param pathClass the {@link PathClass} to split
	 * @return an empty list if the class has no name or is null, otherwise an ordered list containing 
	 * the result of calling {@code PathClass.getName()} for all derived classes, starting from the root. 
	 */
	public static List<String> splitNames(PathClass pathClass) {
		if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
			return Collections.emptyList();
		List<String> names = new ArrayList<>();
		while (pathClass != null) {
			names.add(pathClass.getName());
			pathClass = pathClass.getParentClass();
		}
		Collections.reverse(names);
		return names;
	}
	
	/**
	 * Remove duplicate names from a derived {@link PathClass}.
	 * @param pathClass the input {@link PathClass}, possibly containing elements with identical names
	 * @return a {@link PathClass} representing the same names, with duplicates removed if necessary
	 */
	public static PathClass uniqueNames(PathClass pathClass) {
		var names = splitNames(pathClass);
		var namesUnique = names.stream().distinct().collect(Collectors.toList());
		if (names.equals(namesUnique))
			return pathClass;
		return PathClassFactory.getPathClass(namesUnique);
	}
	
	/**
	 * Create a {@link PathClass} with name elements sorted alphabetically.
	 * This can be useful when comparing classifications that may have been derived independently, 
	 * and where the name order is unimportant.
	 * 
	 * @param pathClass the input {@link PathClass}
	 * @return a {@link PathClass} representing the same names sorted
	 * @see #sortNames(PathClass, Comparator)
	 */
	public static PathClass sortNames(PathClass pathClass) {
		return sortNames(pathClass, Comparator.naturalOrder());
	}
	
	/**
	 * Create a {@link PathClass} with name elements sorted using an arbitrary {@link Comparator}.
	 * This can be useful when comparing classifications that may have been derived independently, 
	 * and where the name order is unimportant.
	 * 
	 * @param pathClass the input {@link PathClass}
	 * @param comparator 
	 * @return a {@link PathClass} representing the same names sorted
	 * @see #sortNames(PathClass)
	 */
	public static PathClass sortNames(PathClass pathClass, Comparator<String> comparator) {
		var names = splitNames(pathClass);
		names.sort(comparator);
		return PathClassFactory.getPathClass(names);
	}
	
	/**
	 * Create a {@link PathClass} with specific name elements removed (if present) from an existing classification.
	 * 
	 * @param pathClass the input {@link PathClass}
	 * @param namesToRemove 
	 * @return a {@link PathClass} representing the classification with the required names removed
	 * @see #removeNames(PathClass, String... )
	 */
	public static PathClass removeNames(PathClass pathClass, Collection<String> namesToRemove) {
		var names = splitNames(pathClass);
		if (names.removeAll(namesToRemove))
			return PathClassFactory.getPathClass(names);
		return pathClass;
	}
	
	/**
	 * Create a {@link PathClass} with specific name elements removed (if present) from an existing classification.
	 * 
	 * @param pathClass the input {@link PathClass}
	 * @param namesToRemove 
	 * @return a {@link PathClass} representing the classification with the required names removed
	 * @see #removeNames(PathClass, Collection)
	 */
	public static PathClass removeNames(PathClass pathClass, String... namesToRemove) {
		return removeNames(pathClass, Arrays.asList(namesToRemove));
	}
	
	/**
	 * Merge two classifications together.
	 * Specifically, the name components of the additional class that are <i>not</i> already contained 
	 * within the base class will be appended, deriving a new class as required.
	 * <p>
	 * Note that if the additional class contains duplicate names these will not automatically be stripped 
	 * unless they are also present within the base class; use {@link #uniqueNames(PathClass)} if this is required.
	 * 
	 * @param baseClass base class, all name components will be retained
	 * @param additionalClass
	 * @return the merged classification, or null if both input classes are null
	 */
	public static PathClass mergeClasses(PathClass baseClass, PathClass additionalClass) {
		if (Objects.equals(baseClass, additionalClass))
			return baseClass;

		if (baseClass == PathClassFactory.getPathClassUnclassified())
			baseClass = null;
		
		if (additionalClass == PathClassFactory.getPathClassUnclassified())
			additionalClass = null;

		if (baseClass == null) {
			return additionalClass;
		}
		
		if (additionalClass == null)
			return baseClass;
		
		// Combine distinct names
		List<String> names = splitNames(additionalClass);
		PathClass output = baseClass;
		for (String name : names) {
			if (!containsName(baseClass, name))
				output = PathClassFactory.getDerivedPathClass(output, name, averageColors(baseClass.getColor(), additionalClass.getColor()));
		}
		return output;
	}
	
	static Integer averageColors(Integer rgb1, Integer rgb2) {
		if (Objects.equals(rgb1, rgb2))
			return rgb1;
		int r = (ColorTools.red(rgb1) + ColorTools.red(rgb2)) / 2;
		int g = (ColorTools.green(rgb1) + ColorTools.green(rgb2)) / 2;
		int b = (ColorTools.blue(rgb1) + ColorTools.blue(rgb2)) / 2;
		return ColorTools.makeRGB(r, g, b);
	}
	
	/**
	 * Query whether a {@link PathClass} or any of its ancestor classes contains a specified name.
	 * <p>
	 * For example a class {@code "CD3: CD8"} would return true for the name "CD3" or "CD8", but not anything else.
	 * 
	 * @param pathClass the classification to test
	 * @param name the name to search for
	 * @return true if the name is found, false otherwise
	 */
	public static boolean containsName(PathClass pathClass, String name) {
		if (pathClass == null)
			return false;
		while (pathClass != null) {
			if (name.equals(pathClass.getName()))
				return true;
			pathClass = pathClass.getParentClass();
		}
		return false;
	}

}