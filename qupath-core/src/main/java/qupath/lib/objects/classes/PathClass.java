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
 * Representation of an object's classification - which can be defined using any unique string identifier (e.g. tumour, lymphocyte, gland, benign, malignant).
 * <p>
 * The constructors in this class should never be called directly, because there should only ever be one instance of each classification - 
 * shared among all objects with that classification.
 * This is important for checking if classifications are identical, and also assigning colors to them for display.
 * <p>
 * To achieve this, be sure to use one of the {@code getInstance()} methods each time you want to access or create a new {@link PathClass} instance.
 * <p>
 * This class has been with QuPath since the beginning, but was thoroughly revised for v0.4.0 to simplify the code, improve the validation, and 
 * make it easier to use.
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
		
		if (existingClasses.containsKey(getCacheString(this)))
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
	
	static String derivedClassToString(PathClass parent, String name) {
		return parent == null ? name : parent.toString() + ": " + name.strip();
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
	 * Return a view of this path class as an unmodifiable set, with 
	 * each element representing the name of a path class component names.
	 * <p>
	 * <b>Important!</b> If any path class component names are duplicates, these will 
	 * (necessarily) be removed from the set. Therefore it is <i>not</i> guaranteed that 
	 * calling {@link PathClass#fromSet(Set)} on the output will return the same {@link PathClass} object.
	 * <pre>{@code 
	 * var pathClass = ...;
	 * var pathClass2 = PathClass.fromSet(pathClass.toSet());
	 * assert pathClass == pathClass2; // This may not be true!
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
	
	/**
	 * Create a {@link PathClass} from a set of names, each giving a component of the class.
	 * This can be used to convert multiple classifications into a single representative object.
	 * @param names
	 * @return
	 */
	public static PathClass fromSet(Set<String> names) {
		return getInstance(names);
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
		if (this == PathClassFactory.getPathClassUnclassified())
			return Collections.emptyList();
		if (!isDerivedClass())
			return	Collections.singletonList(getName());
		return PathClassTools.splitNames(this);
	}
	
	
	private Set<String> createSet() {
		if (this == PathClassFactory.getPathClassUnclassified())
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
	
	
	
	static PathClass getNullClass() {
		return NULL_CLASS;
	}
	
	
	private static String getCacheString(PathClass pathClass) {
		// Avoid creating list if we don't have to
		if (pathClass == null || !pathClass.isDerivedClass())
			return getCacheString(pathClass.getName());
		return getCacheString(pathClass.toList());
	}

	private static String getCacheString(PathClass parent, String name) {
		if (parent == null)
			return getCacheString(name);
		return getCacheString(parent) + "\n" + getCacheString(name);
	}

	
	private static String getCacheString(Collection<String> names) {
		if (names.isEmpty())
			return "";
		if (names.contains(null))
			throw new IllegalArgumentException("PathClass cannot contain 'null' name: " + names);
		return names.stream().map(n -> validateNameStripped(n, false)).collect(Collectors.joining("\n"));
	}

	private static String getCacheString(String name) {
		if (name == null)
			return "";
		return validateNameStripped(name, false);
	}

	
	static PathClass getInstanceFromString(String string, Integer color) {
		var names = Arrays.stream(string.split(DELIMITER)).map(s -> s.strip()).collect(Collectors.toList());
		return getInstance(names, color);
	}

	public static PathClass getInstance(Collection<String> names) {
		return getInstance(names, null);
	}
	
	static PathClass getInstance(Collection<String> names, Integer color) {
		if (names.isEmpty())
			return NULL_CLASS;
		
		// Single name
		if (names.size() == 1)
			return getInstance(null, names.iterator().next(), color);
		
		// Multiple names - need derived PathClass
		// Attempt to speed things up for checking for cached classes
		var string = getCacheString(names);
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
	
	static PathClass getInstance(String name) {
		return getInstance(name, null);
	}
	
	static PathClass getInstance(String name, Integer color) {
		return getInstance(null, name, color);
	}
	
	
	static PathClass getInstance(PathClass parent, String name, Integer color) {
		if (parent == NULL_CLASS)
			parent = null;
		
		if (parent == null) {
			if (name == null)
				return NULL_CLASS;
			
			var string = getCacheString(name);
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
			
			var string = getCacheString(parent, name);
			var pathClass = existingClasses.getOrDefault(string, null);
			if (pathClass != null)
				return pathClass;
			
			var parent2 = parent;
			var rgb = getDefaultColor(null, name, color, string);
			synchronized (existingClasses) {
				return existingClasses.computeIfAbsent(string, s -> new PathClass(secret, parent2, name, rgb));
			}
		}
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
			} else {
				double scale = 1.5;
				return ColorTools.makeScaledRGB(parentRGB, scale);
			}
			
			
		}
		
		// Create a random color, using the cache code of the cacheName for reproducibility
		var random = new Random(cacheName.hashCode());
		return ColorTools.packRGB(
				random.nextInt(256),
				random.nextInt(256),
				random.nextInt(256)
				);
	}
	
	
	static synchronized PathClass getSingleton(PathClass pathClass) {
		if (pathClass == null)
			return null;
		// This can occur during deserialization
		if (!pathClass.isDerivedClass() && pathClass.getName() == null)
			return NULL_CLASS;
		var previous = existingClasses.putIfAbsent(getCacheString(pathClass), pathClass);
		return previous == null ? pathClass : previous;
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
	private static final Pattern PATTERN_NAME = Pattern.compile("[\\w\\p{L}\\d .#\\+\\*\\$\\?]+");
	
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
	
	
}