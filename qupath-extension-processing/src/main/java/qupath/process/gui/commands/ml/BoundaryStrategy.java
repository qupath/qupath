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

package qupath.process.gui.commands.ml;

import qupath.lib.common.ColorTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

/**
 * Helper class for handling the boundaries of training annotations when creating a pixel classifier.
 * <p>
 * The purpose of this is to provide a mechanism for learning the separation between densely packed objects (e.g. nuclei).
 * 
 * @author Pete Bankhead
 */
public class BoundaryStrategy {
	
	private enum Strategy {
		SKIP, CLASSIFY, SAME, DERIVE;
	}
	
	private static BoundaryStrategy SKIP_STRATEGY = new BoundaryStrategy(Strategy.SKIP, null, 0);
	
	private Strategy strategy = Strategy.SKIP;
	private PathClass fixedClass;
	private double thickness = 1.0;
	
	private BoundaryStrategy(Strategy strategy, PathClass pathClass, double thickness) {
		this.strategy = strategy;
		this.fixedClass = pathClass;
		this.thickness = thickness;
	}
	
	/**
	 * Get the boundary thickness, in pixels.
	 * @return
	 */
	public double getBoundaryThickness() {
		return thickness;
	}
	
	/**
	 * Get the classification to be used for the annotation boundary, given the classification of the annotated region.
	 * Note that this returns null for 'ignored' or null classes.
	 * @param pathClass
	 * @return
	 * @see PathClassTools#isIgnoredClass(PathClass)
	 */
	public PathClass getBoundaryClass(PathClass pathClass) {
		if (pathClass == null || PathClassTools.isIgnoredClass(pathClass))
			return null;
		
		switch (strategy) {
		case CLASSIFY:
			return fixedClass;
		case DERIVE:
			return PathClass.getInstance(pathClass, "Boundary*", ColorTools.makeScaledRGB(pathClass.getColor(), 0.5));
		case SAME:
			return pathClass;
		case SKIP:
		default:
			return null;			
		}
	}
	
	@Override
	public String toString() {
		switch (strategy) {
		case CLASSIFY:
			return "Boundary strategy: Classify as " + fixedClass;
		case DERIVE:
			return "Boundary strategy: Create derived class";
		case SAME:
			return "Boundary strategy: Keep classification";
		case SKIP:
		default:
			return "Boundary strategy: Skip";			
		}
	}
	
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fixedClass == null) ? 0 : fixedClass.hashCode());
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
		long temp;
		temp = Double.doubleToLongBits(thickness);
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
		BoundaryStrategy other = (BoundaryStrategy) obj;
		if (fixedClass == null) {
			if (other.fixedClass != null)
				return false;
		} else if (!fixedClass.equals(other.fixedClass))
			return false;
		if (strategy != other.strategy)
			return false;
		if (Double.doubleToLongBits(thickness) != Double.doubleToLongBits(other.thickness))
			return false;
		return true;
	}

	/**
	 * Create a boundary strategy that trains a classifier for a specific PathClass for annotation boundaries.
	 * Note that if the pathClass is null or thickness &le; 0 this is the same as {@link #getSkipBoundaryStrategy()}
	 * @param pathClass
	 * @param thickness
	 * @return
	 */
	public static BoundaryStrategy getClassifyBoundaryStrategy(PathClass pathClass, double thickness) {
		if (pathClass == null || thickness <= 0)
			return getSkipBoundaryStrategy();
		return new BoundaryStrategy(Strategy.CLASSIFY, pathClass, thickness);
	}
	
	/**
	 * Create a boundary strategy that trains a classifier for a PathClass derived from the original classification for annotation boundaries.
	 * Note that if the thickness &le; 0 this is the same as {@link #getSkipBoundaryStrategy()}
	 * @param thickness
	 * @return
	 */
	public static BoundaryStrategy getDerivedBoundaryStrategy(double thickness) {
		if (thickness <= 0)
			return getSkipBoundaryStrategy();
		return new BoundaryStrategy(Strategy.DERIVE, null, thickness);
	}

	/**
	 * Create a boundary strategy that ignores boundaries, not using them for classifier training.
	 * @return
	 */
	public static BoundaryStrategy getSkipBoundaryStrategy() {
		return SKIP_STRATEGY;
	}
	
	/**
	 * Create a boundary strategy with the specified thickness.
	 * @param strategy method for handling boundaries
	 * @param thickness the required thickness
	 * @return
	 */
	public static BoundaryStrategy setThickness(BoundaryStrategy strategy, double thickness) {
		if (strategy.thickness == thickness)
			return strategy;
		if (thickness <= 0 || SKIP_STRATEGY.equals(strategy))
			return getSkipBoundaryStrategy();
		return new BoundaryStrategy(strategy.strategy, strategy.fixedClass, thickness);
	}

}