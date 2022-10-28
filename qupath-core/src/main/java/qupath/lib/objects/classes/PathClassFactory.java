/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.common.LogTools;

/**
 * Factory for creating PathClasses.
 * <p>
 * This must be used in favor of creating PathClass objects directly to ensure that 
 * only one PathClass with a specified name (and, optionally, ancestry) can exist at any time.
 * 
 * @author Pete Bankhead
 * @deprecated since v0.4.0 in favor of methods added to {@link PathClass} directly.
 */
@Deprecated
public final class PathClassFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(PathClassFactory.class);
	
	// Suppressed default constructor for non-instantiability
	private PathClassFactory() {
		throw new AssertionError();
	}
		
	/**
	 * Enum representing standard classifications. Exists mostly to ensure consisting naming (including capitalization).
	 */
	public enum StandardPathClasses { 
		/**
		 * Tumor classification
		 */
		TUMOR,
		/**
		 * Stroma classification
		 */
		STROMA,
		/**
		 * Immune cell classification
		 */
		IMMUNE_CELLS,
		/**
		 * Ignore classification, indicating what should not be further measured (e.g. background, whitespace)
		 */
		IGNORE,
		/**
		 * Root object classification
		 */
		IMAGE_ROOT,
		/**
		 * Necrosis classification
		 */
		NECROSIS,
		/**
		 * Other classification
		 */
		OTHER,
		/**
		 * Region class. This behaves slightly differently from other classes, e.g. it is not filled in when applied to
		 * annotations.  Consequently it is good to heavily annotated regions, or possibly detected tissue 
		 * containing further annotations inside.
		 */
		REGION,
		/**
		 * General class to represent something 'positive'
		 */
		POSITIVE,
		/**
		 * General class to represent something 'negative'
		 */
		NEGATIVE;
		
		
		PathClass getPathClass() {
			switch (this) {
			case IGNORE:
				return PathClass.getInstance("Ignore*", ColorTools.packRGB(180, 180, 180));
			case IMAGE_ROOT:
				return PathClass.getInstance("Image", ColorTools.packRGB(128, 128, 128));
			case IMMUNE_CELLS:
				return PathClass.getInstance("Immune cells", ColorTools.packRGB(160, 90, 160));
			case NECROSIS:
				return PathClass.getInstance("Necrosis", ColorTools.packRGB(50, 50, 50));
			case OTHER:
				return PathClass.getInstance("Other", ColorTools.packRGB(255, 200, 0));
			case REGION:
				return PathClass.getInstance("Region*", ColorTools.packRGB(0, 0, 180));
			case STROMA:
				return PathClass.getInstance("Stroma", ColorTools.packRGB(150, 200, 150));
			case TUMOR:
				return PathClass.getInstance("Tumor", ColorTools.packRGB(200, 0, 0));
			case POSITIVE:
				return PathClass.getPositive(null);
			case NEGATIVE:
				return PathClass.getNegative(null);
			default:
				throw new IllegalArgumentException("Unknown value!");
			}
		}
		
	}

	
	/**
	 * Get a {@link PathClass}, without specifying any color.
	 * @param name
	 * @return
	 */
	public static PathClass getPathClass(String name) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.fromString(String) instead");
		return PathClass.fromString(name, (Integer)null);
	}
		
	/**
	 * Get the PathClass object associated with a specific name. Note that this name must not contain newline; 
	 * doing so will result in an {@link IllegalArgumentException} being thrown. If the name contains colon characters, 
	 * it will be treated as a derived class.
	 * 
	 * @param name
	 * @param rgb
	 * @return
	 * @deprecated since v0.4.0 in favor of {@link PathClass#getInstance(String, Integer)} or 
	 *             {@link PathClass#fromString(String, Integer)}
	 */
	@Deprecated
	public static PathClass getPathClass(String name, Integer rgb) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.fromString(String, Integer) instead");
		if (name == null)
			return PathClass.NULL_CLASS;
		return PathClass.fromString(name, rgb);
	}
		
		
	/**
	 * Get a derived {@link PathClass} object representing all the provided names, 
	 * using default colors.
	 * <p>
	 * Note that names must not contain newline or colon characters; if they do an 
	 * {@link IllegalArgumentException} will be thrown.
	 * 
	 * @param baseName name of the base classification
	 * @param names array of names for each constituent part of the classification.
	 * 				For each name, a new class will be derived, starting from the base.
	 * @return a {@link PathClass}, as defined above
	 * @deprecated since v0.4.0 in favor of {@link PathClass#fromArray(String...)}
	 */
	@Deprecated
	public static PathClass getPathClass(String baseName, String... names) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.fromArray(String...) instead");
		if (names.length == 0)
			return PathClass.getInstance(baseName);
		List<String> list;
		if (names.length == 1)
			list = List.of(baseName, names[0]);
		else {
			list = new ArrayList<String>();
			list.add(baseName);
			for (var n : names)
				list.add(n);
		}
		return PathClass.fromCollection(list);
	}
	
	/**
	 * Get a PathClass object representing all the provided names.
	 * The first entry in the list corresponds to the base name.
	 * 
	 * @param names list of names for each constituent part of the classification.
	 * @return a {@link PathClass} containing all names
	 * @deprecated since v0.4.0 in favor of {@link PathClass#fromCollection(java.util.Collection)}
	 */
	@Deprecated
	public static PathClass getPathClass(List<String> names) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.fromCollection(Collection<String>) instead");
		return PathClass.fromCollection(names);
	}
	
	
	/**
	 * Return a singleton version of a specific PathClass.  This is useful during deserialization, which can 
	 * circumvent the 'right' way to request PathClass objects via static methods.
	 * 
	 * @param pathClass
	 * @return
	 * @deprecated since v0.4.0 in favor of {@link PathClass#getSingleton(PathClass)}
	 */
	@Deprecated
	public static PathClass getSingletonPathClass(PathClass pathClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getSingleton(PathClass) instead");
		return PathClass.getSingleton(pathClass);
	}
	
	/**
	 * Get a PathClass that has been derived from a parent class.
	 * 
	 * @param parentClass
	 * @param name
	 * @param rgb
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getInstance(PathClass, String, Integer)}
	 */
	@Deprecated
	public static PathClass getDerivedPathClass(PathClass parentClass, String name, Integer rgb) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getInstance(PathClass, String Integer) instead");
		return PathClass.getInstance(parentClass, name, rgb);
	}
	
	/**
	 * Get a standalone or derived 1+ classification, indicating weak positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getOnePlus(PathClass)}
	 */
	@Deprecated
	public static PathClass getOnePlus(PathClass parentClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getOnePlus(PathClass) instead");
		return PathClass.getInstance(parentClass, PathClass.NAME_ONE_PLUS, null);
	}

	/**
	 * Get a standalone or derived 2+ classification, indicating moderate positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getTwoPlus(PathClass)}
	 */
	@Deprecated
	public static PathClass getTwoPlus(PathClass parentClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getTwoPlus(PathClass) instead");
		return PathClass.getInstance(parentClass, PathClass.NAME_TWO_PLUS, null);
	}

	/**
	 * Get a standalone or derived 3+ classification, indicating strong positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getThreePlus(PathClass)}
	 */
	@Deprecated
	public static PathClass getThreePlus(PathClass parentClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getThreePlus(PathClass) instead");
		return PathClass.getInstance(parentClass, PathClass.NAME_THREE_PLUS, null);
	}
	
	/**
	 * Get a standalone or derived Negative classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getNegative(PathClass)}
	 */
	@Deprecated
	public static PathClass getNegative(PathClass parentClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getNegative(PathClass) instead");
		return PathClass.getInstance(parentClass, PathClass.NAME_NEGATIVE, null);
	}
	
	/**
	 * Get a standalone or derived Positive classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 * @deprecated since v0.4.0, use {@link PathClass#getPositive(PathClass)}
	 */
	@Deprecated
	public static PathClass getPositive(PathClass parentClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.getPositive(PathClass) instead");
		return PathClass.getInstance(parentClass, PathClass.NAME_POSITIVE, null);
	}
	
	/**
	 * Get a standard PathClass.
	 * @param pathClass
	 * @return
	 * @deprecated since v0.4.0, use {@link qupath.lib.objects.classes.PathClass.StandardPathClasses}
	 */
	@Deprecated
	public static PathClass getPathClass(StandardPathClasses pathClass) {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.StandardPathClasses instead");
		return pathClass.getPathClass();
	}

	/**
	 * Return a special 'null' class to represent no classification.
	 * <p>
	 * This is useful for displaying available classes; <i>it should not be set as the class for any object</i>, 
	 * rather an object that is unclassified should have a classification of null.
	 * @return
	 * @deprecated since v0.4.0, use instead {@link PathClass#NULL_CLASS}
	 */
	@Deprecated
	public static PathClass getPathClassUnclassified() {
		LogTools.warnOnce(logger, "PathClassFactory is deprecated since v0.4.0 - use PathClass.NULL_CLASS instead");
		return PathClass.NULL_CLASS;
	}
		

}
