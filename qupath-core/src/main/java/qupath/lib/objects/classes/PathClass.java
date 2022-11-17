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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;

/**
 * Representation of an object's classification - which can be defined using any unique string
 * identifier (e.g. tumour, lymphocyte, gland, benign, malignant).
 * <p>
 * The constructors in this class should never be called directly, because there should only ever
 * be one instance of each classification - 
 * shared among all objects with that classification.
 * This is important for checking if classifications are identical, and also assigning colors to them for display.
 * <p>
 * To achieve this, be sure to use one of the {@code getInstance()} or {@code fromXXX()} methods each time
 * you want to access or create a new {@link PathClass} instance.
 * <p>
 * This class has been with QuPath since the beginning, but was thoroughly revised for v0.4.0 to simplify the code,
 * improve the validation, and make it easier to use.
 * 
 * @see PathClassFactory
 * 
 * @author Pete Bankhead
 *
 */
public final class PathClass implements Comparable<PathClass>, Serializable {
	
	private static final Logger logger = LoggerFactory.getLogger(PathClass.class);
	
	private static final long serialVersionUID = 1L;
	
	private static boolean VALIDATION_STRICT = "true".equalsIgnoreCase(System.getProperty("pathClassString", "false").strip());
	
	/**
	 * Default name for a class representing "Positive" staining intensity
	 */
	public static final String NAME_POSITIVE = "Positive";
	
	/**
	 * Default name for a class representing "Negative" staining intensity
	 */
	public static final String NAME_NEGATIVE = "Negative";
	
	/**
	 * Default name for a class representing "1+" staining intensity (i.e. weakly positive)
	 */
	public static final String NAME_ONE_PLUS = "1+";
	
	/**
	 * Default name for a class representing "2+" staining intensity (i.e. moderately positive)
	 */
	public static final String NAME_TWO_PLUS = "2+";
	
	/**
	 * Default name for a class representing "3+" staining intensity (i.e. strongly positive)
	 */
	public static final String NAME_THREE_PLUS = "3+";
	
//	
//	public static Set<String> PERMITTED_CHARACTERS = Arrays.stream(
//			"[](){}\\/,''@£$#+-_"
//			.split("")).collect(Collectors.toSet());
	
		
	private static final Integer COLOR_POSITIVE = ColorTools.packRGB(200, 50, 50);
	private static final Integer COLOR_NEGATIVE = ColorTools.packRGB(90, 90, 180);
	private static final Integer COLOR_ONE_PLUS = ColorTools.packRGB(255, 215, 0);
	private static final Integer COLOR_TWO_PLUS = ColorTools.packRGB(225, 150, 50);
	private static final Integer COLOR_THREE_PLUS = ColorTools.packRGB(200, 50, 50);
	
	private static final String DELIMITER_DEFAULT = ":";
	
	/**
	 * Get the delimiter to use between names of the PathClass when converting to a string.
	 */
	public static final String DELIMITER = validDelimiterOrDefault(System.getProperty("pathClassDelimiter"));
	
	// Get a valid delimiter, or use the default
	private static String validDelimiterOrDefault(String delim) {
		if (delim == null || delim.isEmpty())
			return DELIMITER_DEFAULT;
		return delim.contains("\n") ? DELIMITER_DEFAULT : delim;
	}

	private static String defaultName = "Unclassified";
	private static Integer DEFAULT_COLOR = ColorTools.packRGB(64, 64, 64);
	
	private final PathClass parentClass;
	private final String name;
	private Integer colorRGB;
	
	// Included to thwart Groovy... we *really* don't want users accessing the 
	// PathClass constructor and creating multiple instances of (what should be) 
	// the same PathClass
	private static final UUID secret = UUID.randomUUID();
	
	// Without this, Groovy would allow {@code PathClass.secret}
	@SuppressWarnings("unused")
	private static final UUID getSecret() {
		throw new UnsupportedOperationException("Don't ask me to share my secret!");
	}

	/**
	 * Cached String representation
	 */
	private transient String stringRep = null;
	
	/**
	 * Default PathClass that represents no classification.
	 * Usually no classification is represented by {@code null}, so this is not normally 
	 * needed; however, sometimes it is required in contexts where a null is not permitted 
	 * (e.g. some collections).
	 */
	public static final PathClass NULL_CLASS = new PathClass(secret);
	
	private static final List<String> illegalCharacters = Arrays.asList("\n", ":", "\r");
	
	/**
	 * Cache of existing classes from toString() method
	 */
	private static final Map<String, PathClass> existingClasses = new ConcurrentHashMap<>();

	
	/**
	 * Cache the set representation
	 */
	private transient Set<String> set;
	
	/**
	 * Cache the list representation
	 */
	private transient List<String> list;

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
	 * This constructor should <i>not<i> be called explicitly; rather, use one of the static {@code getInstance()} methods.
	 * <p>
	 * Only one instance of a PathClass should exist for any given name and list of ancestors.
	 * 
	 * @param parent
	 * @param name
	 * @param colorRGB
	 */
	private PathClass(UUID mySecret, PathClass parent, String name, Integer colorRGB) {
		if (!Objects.equals(secret, mySecret))
			throw new IllegalStateException("You should not access the PathClass constructor! Use PathClass.getInstance() instead.");
		
		// Ensure name is not null
		name = validateNameNotNull(name, true);
		
		if (parent == null)
			logger.debug("Creating PathClass with name '{}'", name);
		else
			logger.debug("Deriving PathClass from {} with name '{}'", parent, name);
		
		// Strip leading and trailing whitespace
		name = validateNameStripped(name, VALIDATION_STRICT ? true : false);
		
		// Check not empty (or blank)
		name = validateNameNotBlank(name, true);

		// Previous (minimal) additional validation from v0.3.2 and before
		if (!isValidName(name))
			throw new IllegalArgumentException(name + " is not a valid PathClass name!");
		
		// More extensive validation for v0.4.0 and later
		name = validateNameCharacters(name, VALIDATION_STRICT ? true : false);
		
		this.parentClass = parent;
		this.name = name;
		
		if (colorRGB == null)
			this.colorRGB = DEFAULT_COLOR;
		else
			this.colorRGB = colorRGB;
		
		if (existingClasses.containsKey(createCacheString(this)))
			throw new IllegalStateException("Cannot create the same PathClass more than once!");
		
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
	 * Set the color as 8-bit RGB values
	 * @param red 
	 * @param green 
	 * @param blue 
	 * @since v0.4.0
	 */
	public void setColor(int red, int green, int blue) {
		setColor(ColorTools.packRGB(red, green, blue));
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
	
	@Override
	public String toString() {
		if (stringRep == null) {
			stringRep = toString(DELIMITER + " ");
		}
		return stringRep;
	}
	
	/**
	 * Create a string representation, using the specified delimiter between 
	 * elements of derived PathClasses.
	 * @param delimiter
	 * @return
	 */
	public String toString(String delimiter) {
		if (name == null)
			return defaultName;
		else if (isDerivedClass())
			return createString(parentClass, name, delimiter);
		else
			return name;
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
	 * Return a view of this path class as an unmodifiable set, with 
	 * each element representing the name of a path class component names.
	 * <p>
	 * <b>Important!</b> If any path class component names are duplicates, these will 
	 * (necessarily) be removed from the set. Therefore it is <i>not</i> guaranteed that 
	 * calling {@link PathClass#fromCollection(Collection)} on the output will return the same {@link PathClass} object.
	 * <pre>{@code 
	 * var pathClass = ...;
	 * var pathClass2 = PathClass.getInstance(pathClass.toSet());
	 * assert pathClass == pathClass2; // This may or may not be true!
	 * }</pre>
	 * <p>
	 * However the {@link PathClass} objects should be the same if the name components are all valid and 
	 * there are no duplicates (which should normally be the case).
	 * @return
	 */
	public Set<String> toSet() {
		if (set == null) {
			synchronized (this) {
				if (set == null)
					set = createSet();
			}
		}
		return set;
	}
	
	
	List<String> toList() {
		if (list == null) {
			synchronized (this) {
				if (list == null)
					list = createList();
			}
		}
		return list;
	}
	
	private List<String> createList() {
		if (this == PathClass.NULL_CLASS)
			return Collections.emptyList();
		if (!isDerivedClass())
			return	Collections.singletonList(getName());
		return PathClassTools.splitNames(this);
	}
	
	
	private Set<String> createSet() {
		if (this == PathClass.NULL_CLASS)
			return Collections.emptySet();
		if (!isDerivedClass())
			return	Collections.singleton(getName());
		return Collections.unmodifiableSet(
					new LinkedHashSet<>(
							PathClassTools.splitNames(this)
							)
					);
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
	
	
	/**
	 * Get the value of {@link #NULL_CLASS}, used to represent no classification.
	 * In most cases, {@code null} should be used instead; this exists only as a 
	 * representation in cases where {@code null} is not permitted (e.g. in some collection 
	 * implementations).
	 * @return
	 */
	public static PathClass getNullClass() {
		return NULL_CLASS;
	}
	
	/**
	 * Delimiter to use when creating the cache string
	 * Newline since that isn't normally permitted
	 */
	private static final String CACHE_DELIMITER = "\n";

	private static String createCacheStringForNameCollection(Collection<String> names) {
		return createStringForNameCollection(names, CACHE_DELIMITER);
	}

	private static String createCacheString(PathClass parent) {
		return createString(parent, null, CACHE_DELIMITER); // Use newlines, since they aren't permitted normally
	}

	private static String createCacheString(PathClass parent, String name) {
		return createString(parent, name, CACHE_DELIMITER); // Use newlines, since they aren't permitted normally
	}

	private static String createString(PathClass parent, String name, String delimiter) {
		if (parent == null || parent == NULL_CLASS)
			return createStringForSingleName(name);
		String start;
		if (!parent.isDerivedClass())
			start = createStringForSingleName(parent.getName());
		else
			start = createStringForNameCollection(parent.toList(), delimiter);
		if (name == null)
			return start;
		return start + delimiter + createStringForSingleName(name);
	}
	
	private static String createStringForNameCollection(Collection<String> names, String delimiter) {
		if (names.isEmpty())
			return "";
		if (names.stream().anyMatch(p -> p == null))
			throw new IllegalArgumentException("PathClass cannot contain 'null' name: " + names);
		return names.stream().map(n -> validateNameStripped(n, false)).collect(Collectors.joining(delimiter));
	}

	private static String createStringForSingleName(String name) {
		if (name == null)
			return "";
		return validateNameStripped(name, false);
	}

	/**
	 * Get a PathClass instance from a string representation, without specifying a default color.
	 * <p>
	 * This calls {@link #fromString(String, Integer)} with the second argument as {@code null}.
	 * @param string
	 * @return
	 */
	public static PathClass fromString(String string) {
		return fromString(string, null);
	}
	
	/**
	 * Get a PathClass instance from a string representation, optionally providing a default color 
	 * if a new instance needs to be created.
	 * <p>
	 * This ultimately calls {@link #getInstance(PathClass, String, Integer)} but differs in that it 
	 * accepts a string representation that may include the {@link #DELIMITER}.
	 * If so, this is used to split the string into different name components that are passed to 
	 * {@link #fromCollection(Collection, Integer)}.
	 * @param string a string representation containing one or more name elements, separated by {@link #DELIMITER}
	 * @param color a default color (optional, may be null)
	 * @return
	 */
	public static PathClass fromString(String string, Integer color) {
		if (string == null)
			return NULL_CLASS;
		var names = Arrays.stream(string.split(DELIMITER)).map(s -> s.strip()).collect(Collectors.toList());
		return fromCollection(names, color);
	}

	/**
	 * Get a PathClass using all the name elements specified in the collection, 
	 * without providing a default color.
	 * @param names
	 * @return
	 * @see #fromCollection(Collection)
	 */
	public static PathClass fromCollection(Collection<String> names) {
		return fromCollection(names, null);
	}
	
	/**
	 * Get a PathClass instance using all the name elements specified in 
	 * the collection, with optional default color if a new instance is created.
	 * The rules are:
	 * <ul>
	 * <li>If the collection is empty, {@link #NULL_CLASS} is returned</li>
	 * <li>If the collection has one element, this is equivalent to calling {@link #getInstance(String, Integer)}</li>
	 * <li>If the collection has multiple element, this is equivalent to creating a base class from 
	 * the first element and deriving subclassifications by calling  {@link #getInstance(PathClass, String, Integer)} 
	 * for each element, in the order returned by the collection's iterator</li>
	 * </ul>
	 * 
	 * @param names
	 * @param color
	 * @return
	 * @see #getInstance(PathClass, String, Integer)
	 */
	public static PathClass fromCollection(Collection<String> names, Integer color) {
		if (names.isEmpty())
			return NULL_CLASS;
		
		// Single name
		if (names.size() == 1)
			return getInstance(names.iterator().next(), color);
		
		// Multiple names - need derived PathClass
		// Attempt to speed things up for checking for cached classes
		var string = createCacheStringForNameCollection(names);
		var pathClass = existingClasses.getOrDefault(string, null);
		if (pathClass != null)
			return pathClass;
		
		// Need to build the class
		var list = new ArrayList<>(names);
		int n = list.size();
		for (int i = 0; i < n; i++) {
			// Use the color only for the last name
			pathClass = getInstance(pathClass, list.get(i), i == n-1 ? color : color);
		}
		return pathClass;
	}
	
	public static PathClass getInstance(String name) {
		return getInstance(name, null);
	}
	
	/**
	 * Get a base PathClass instance, without any parent PathClass.
	 * <p>
	 * This is equivalent to calling {@link #getInstance(PathClass, String, Integer)} with 
	 * the first argument as {@code null}.
	 * 
	 * @param name
	 * @param color
	 * @return
	 * @see #getInstance(PathClass, String, Integer)
	 */
	public static PathClass getInstance(String name, Integer color) {
		return getInstance(null, name, color);
	}
	
	
	/**
	 * Get a derived PathClass instance with the specified parent.
	 * <p>
	 * This will be derived from the parent PathClass (if provided) and have the specified 
	 * name, stripped to remove any leading or training whitespace.
	 * <p>
	 * Note that the name should generally be an alphanumeric string, optionally including 
	 * punctuation symbols but <b>not</b> including {@link #DELIMITER}.
	 * <p>
	 * The delimiter is currently a colon {@code ":"} but it is advised not to rely upon 
	 * this and to avoid punctuation where possible, because the delimiter may possibly change 
	 * in a future release - primarily because the choice of colon can be problematic in some 
	 * cases, e.g. <a href="https://github.com/qupath/qupath/issues/507">when using ontologies</a>.
	 * 
	 * 
	 * @param parent parent class (optional, may be null)
	 * @param name name of the PathClass
	 * @param color color to use if a new instance is created (may be null to use the default)
	 * @return a PathClass instance; the same instance will be returned given the same parent and name
	 * 
	 * @implSpec the color is only used if a new PathClass instance needs to be created.
	 *           If a suitable instance has already been created, then that will be returned instead 
	 *           and the color will not be created.
	 *           
	 * @see #fromString(String, Integer)
	 * @see #fromCollection(Collection, Integer)
	 */
	public static PathClass getInstance(PathClass parent, String name, Integer color) {
		if (parent == NULL_CLASS)
			parent = null;
		
		if (name != null && name.contains(DELIMITER)) {
//			if (parent == null) {
//				logger.warn("Name '{}' contains the delimiter '{}' - switching to use getInstanceFromString instead", name, DELIMITER);
//				return getInstanceFromString(name, color);
//			} else {
				throw new IllegalArgumentException(
						String.format("Name '%s' contains the delimiter '%s' - please use a valid name or getInstanceFromString()", 
								name, DELIMITER));				
//			}
		}
		
		if (parent == null) {
			if (name == null)
				return NULL_CLASS;
			
			var string = createStringForSingleName(name);
			var pathClass = existingClasses.getOrDefault(string, null);
			if (pathClass != null)
				return pathClass;
			
			var rgb = getDefaultColor(null, name, color, string);
			synchronized (existingClasses) {
				return existingClasses.computeIfAbsent(string, s -> new PathClass(secret, null, name, rgb));
			}
		} else {
			if (name == null)
				throw new IllegalArgumentException("Cannot derive a PathClass with a null name and non-null parent");
			
			var string = createCacheString(parent, name);
			var pathClass = existingClasses.getOrDefault(string, null);
			if (pathClass != null)
				return pathClass;
			
			var parent2 = parent;
			var rgb = getDefaultColor(parent, name, color, string);
			synchronized (existingClasses) {
				return existingClasses.computeIfAbsent(string, s -> new PathClass(secret, parent2, name, rgb));
			}
		}
	}
	
	
	/**
	 * Get a standalone or derived 1+ classification, indicating weak positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getOnePlus(PathClass parentClass) {
		return PathClass.getInstance(parentClass, PathClass.NAME_ONE_PLUS, null);
	}

	/**
	 * Get a standalone or derived 2+ classification, indicating moderate positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getTwoPlus(PathClass parentClass) {
		return PathClass.getInstance(parentClass, PathClass.NAME_TWO_PLUS, null);
	}

	/**
	 * Get a standalone or derived 3+ classification, indicating strong positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getThreePlus(PathClass parentClass) {
		return PathClass.getInstance(parentClass, PathClass.NAME_THREE_PLUS, null);
	}
	
	/**
	 * Get a standalone or derived Negative classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getNegative(PathClass parentClass) {
		return PathClass.getInstance(parentClass, PathClass.NAME_NEGATIVE, null);
	}
	
	/**
	 * Get a standalone or derived Positive classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getPositive(PathClass parentClass) {
		return PathClass.getInstance(parentClass, PathClass.NAME_POSITIVE, null);
	}
	
	
	
	private static Integer getDefaultColor(PathClass parent, String name, Integer integer, String cacheName) {
		if (integer != null)
			return integer;
		
		if (parent == null) {
			if (name.equals(NAME_ONE_PLUS)) {
				return ColorTools.makeScaledRGB(COLOR_ONE_PLUS, 1.25);
			} else if (name.equals(NAME_TWO_PLUS)) {
				return ColorTools.makeScaledRGB(COLOR_TWO_PLUS, 1.25);
			} else if (name.equals(NAME_THREE_PLUS))
				return ColorTools.makeScaledRGB(COLOR_THREE_PLUS, 1.25);
			else if (name.equals(NAME_POSITIVE)) {
				return ColorTools.makeScaledRGB(COLOR_POSITIVE, 1.25);
			} else if (name.equals(NAME_NEGATIVE)) {
				return ColorTools.makeScaledRGB(COLOR_NEGATIVE, 1.25);
			}
		} else {
			
			
			// Since 'Tumor' is so common as a class for QuPath applications, 
			// handle it using more distinctive colors; for others, use 'darkness' of the base color
			// TODO: Make sure 'Tumor' is standard name
			boolean isTumor = !parent.isDerivedClass() && "Tumor".equals(parent.getName());
			int parentRGB = parent.getColor();
			if (name.equals(NAME_ONE_PLUS)) {
				return isTumor ? COLOR_ONE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.9);
			} else if (name.equals(NAME_TWO_PLUS)) {
				return isTumor ? COLOR_TWO_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.6);
			} else if (name.equals(NAME_THREE_PLUS))
				return isTumor ? COLOR_THREE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.4);
			else if (name.equals(NAME_POSITIVE)) {
				return isTumor ? COLOR_POSITIVE : ColorTools.makeScaledRGB(parentRGB, 0.75);
			} else if (name.equals(NAME_NEGATIVE)) {
				return isTumor ? COLOR_NEGATIVE : ColorTools.makeScaledRGB(parentRGB, 1.25);
//			} else {
//				double scale = 1.5;
//				return ColorTools.makeScaledRGB(parentRGB, scale);
			}
			
			
		}
		
		// Create a random color, using the cache code of the cacheName for reproducibility
		// Reject very dark colors
		var random = new Random(cacheName.hashCode());
		int r = 0, g = 0, b = 0;
		while (r < 40 && g < 40 && b < 40) {
			r = random.nextInt(256);
			g = random.nextInt(256);
			b = random.nextInt(256);
		}
		return ColorTools.packRGB(r, g, b);
	}
	
	/**
	 * Get the singleton PathClass that is equivalent to the PathClass provided.
	 * <p>
	 * This is important because there should only ever be one PathClass instance for 
	 * any classification - and accessing PathClasses only via the {@code getInstance()} 
	 * methods here should ensure that.
	 * <p>
	 * However, if receiving a PathClass from some other source then it is <i>possible</i> 
	 * that the PathClass was created some other way and duplicates could emerge. 
	 * Calling this method returns resolves that problem by returning the single instance 
	 * that should be used.
	 * <p>
	 * This is significant if the PathClass has been created via Java deserialization, 
	 * which skips the required use of {@code getInstance()} by default.
	 * 
	 * @param pathClass
	 * @return either the input PathClass, or an equivalent that should be used
	 */
	public static synchronized PathClass getSingleton(PathClass pathClass) {
		if (pathClass == null)
			return null;
		// This can occur during deserialization
		if (!pathClass.isDerivedClass() && pathClass.getName() == null)
			return NULL_CLASS;
		var previous = existingClasses.putIfAbsent(createCacheString(pathClass), pathClass);
		return previous == null ? pathClass : previous;
	}
	
	/**
	 * Get a PathClass from an array of individual names.
	 * @param names
	 * @return
	 * @see #fromCollection(Collection)
	 * @see #getInstance(PathClass, String, Integer)
	 */
	public static PathClass fromArray(String... names) {
		return fromCollection(Arrays.asList(names));
	}
	
	
	
	private static String validateNameNotNull(String name, boolean exceptOnFail) {
		if (name == null && exceptOnFail)
			throw new IllegalArgumentException("Requested PathClass name cannot be null!");
		return name;
	}
	
	private static String validateNameNotBlank(String name, boolean exceptOnFail) {
		if (name.isBlank() && exceptOnFail)
			throw new IllegalArgumentException("Requested PathClass name cannot be blank!");
		return name;
	}
	
	private static String validateNameStripped(String name, boolean exceptOnFail) {
		var stripped = name.strip();
		if (name.length() != stripped.length()) {
			if (exceptOnFail)
				throw new IllegalArgumentException("Unsupported classification name '" + name + "' - leading or training whitespace is not allowed");
			else
				logger.warn("PathClass names should not contain leading or trailing whitespace - '{}' will be stripped to become '{}'", name, stripped);
		}
		return stripped;
	}
	
	/**
	 * Accept any letter (including from different languages), numbers, 
	 */
	private static final Pattern PATTERN_NAME = Pattern.compile("[\\w\\p{L}\\d\\p{Punct} ]+");
	
	private static String validateNameCharacters(String name, boolean exceptOnFail) {
		if (name.contains(DELIMITER))
			throw new IllegalArgumentException("Invalid PathClass name - contains delimiter: " + name);
		
		if (PATTERN_NAME.matcher(name).matches())
			return name;
		if (exceptOnFail)
			throw new IllegalArgumentException("Invalid PathClass name: " + name);
		else
			logger.warn("PathClass name '{}' contains invalid characters - this may fail on later QuPath versions", name);
		return name;
	}
	
	
	
	/**
	 * Enum representing standard classifications. Exists mostly to ensure consisting naming (including capitalization).
	 */
	public static class StandardPathClasses { 
		/**
		 * Tumor classification
		 */
		public static final PathClass TUMOR = getInstance("Tumor", ColorTools.packRGB(200, 0, 0));
		/**
		 * Stroma classification
		 */
		public static final PathClass STROMA = PathClass.getInstance("Stroma", ColorTools.packRGB(150, 200, 150));
		/**
		 * Immune cell classification
		 */
		public static final PathClass IMMUNE_CELLS = PathClass.getInstance("Immune cells", ColorTools.packRGB(160, 90, 160));
		/**
		 * Ignore classification, indicating what should not be further measured (e.g. background, whitespace)
		 */
		public static final PathClass IGNORE = PathClass.getInstance("Ignore*", ColorTools.packRGB(180, 180, 180));
		/**
		 * Root object classification
		 */
		public static final PathClass IMAGE_ROOT = PathClass.getInstance("Image", ColorTools.packRGB(128, 128, 128));
		/**
		 * Necrosis classification
		 */
		public static final PathClass NECROSIS = PathClass.getInstance("Necrosis", ColorTools.packRGB(50, 50, 50));
		/**
		 * Other classification
		 */
		public static final PathClass OTHER = PathClass.getInstance("Other", ColorTools.packRGB(255, 200, 0));
		/**
		 * Region class. This behaves slightly differently from other classes, e.g. it is not filled in when applied to
		 * annotations.  Consequently it is good to heavily annotated regions, or possibly detected tissue 
		 * containing further annotations inside.
		 */
		public static final PathClass REGION = PathClass.getInstance("Region*", ColorTools.packRGB(0, 0, 180));
		/**
		 * General class to represent something 'positive'
		 */
		public static final PathClass POSITIVE = PathClass.getPositive(null);
		/**
		 * General class to represent something 'negative'
		 */
		public static final PathClass NEGATIVE = PathClass.getNegative(null);
		
//		public static PathClass values() {
//			return new PathClass[] {
//					TUMOR, STROMA, IMMUNE_CELLS, IGNORE, NECROSIS, OTHER, REGION, POSITIVE, NEGATIVE
//			}
//		}

		
	}
	
	
}