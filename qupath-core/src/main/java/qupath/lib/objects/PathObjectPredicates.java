/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;


/**
 * Classes to define JSON-serializable {@link Predicate}s for {@link PathObject}s.
 * 
 * @author Pete Bankhead
 */
public class PathObjectPredicates {
	
	private static SubTypeAdapterFactory<PathObjectPredicate> typeAdapterFactory = 
			GsonTools.createSubTypeAdapterFactory(PathObjectPredicate.class, "predicate_type")
			.registerSubtype(PathObjectClassPredicate.class, "classified")
			.registerSubtype(PathObjectClassNamePredicate.class, "classified-name")
			.registerSubtype(PathObjectClassPositivePredicate.class, "classified-positive")
			.registerSubtype(PathObjectAndPredicate.class, "and")
			.registerSubtype(PathObjectOrPredicate.class, "or")
			.registerSubtype(PathObjectNegatePredicate.class, "negate")
			.registerSubtype(PathObjectFilterPredicate.class, "filter")
			;
	
	static {
		GsonTools.getDefaultBuilder().registerTypeAdapterFactory(typeAdapterFactory);
	}
	

	public static interface PathObjectPredicate extends Predicate<PathObject> {
		
		public PathObjectPredicate and(PathObjectPredicate p);
		
		public PathObjectPredicate or(PathObjectPredicate p);
		
		@Override
		public default PathObjectPredicate negate() {
			return new PathObjectNegatePredicate(this);
		}
		
//		/**
//		 * Get as a standard Java Predicate
//		 * @return
//		 */
//		public default Predicate<PathObject> asPredicate() {
//			return p -> test(p);
//		}
		
	}
	
	static abstract class AbstractPathObjectPredicate implements PathObjectPredicate {
		
		@Override
		public PathObjectPredicate and(PathObjectPredicate p) {
			return new PathObjectAndPredicate(this, p);
		}
	
		@Override
		public PathObjectPredicate or(PathObjectPredicate p) {
			return new PathObjectOrPredicate(this, p);
		}
				
	}
	
	
	static class PathObjectFilterPredicate extends AbstractPathObjectPredicate {
		
		private PathObjectFilter filter;
		
		private PathObjectFilterPredicate(PathObjectFilter filter) {
			this.filter = filter;
		}

		@Override
		public boolean test(PathObject t) {
			return filter.test(t);
		}
		
		public void dummy() {}
		
	}
	
	static class PathObjectClassPositivePredicate extends AbstractPathObjectPredicate {
		
		private boolean allowGradedIntensity;
		
		PathObjectClassPositivePredicate(boolean allowGradedIntensity) {
			this.allowGradedIntensity = allowGradedIntensity;
		}
		
		@Override
		public boolean test(PathObject t) {
			PathClass pathClass = t.getPathClass();
			if (allowGradedIntensity)
				return PathClassTools.isPositiveClass(pathClass) || PathClassTools.isGradedIntensityClass(pathClass);
			else
				return PathClassTools.isPositiveClass(pathClass);
		}
		
	}


	static class PathObjectClassNamePredicate extends AbstractPathObjectPredicate {
		
		private Set<String> pathClassNames;
		
		private PathObjectClassNamePredicate(String... pathClassNames) {
			this.pathClassNames = new LinkedHashSet<>();
			for (var name : pathClassNames) {
				if (name == null)
					throw new IllegalArgumentException("Class name cannot be null!");
				this.pathClassNames.add(name);
			}
		}

		@Override
		public boolean test(PathObject t) {
			PathClass pathClass = t.getPathClass();
			for (var name : pathClassNames) {
				if (PathClassTools.containsName(pathClass, name))
					return true;
			}
			return false;
		}
		
	}
	
	static class PathObjectClassPredicate extends AbstractPathObjectPredicate {
		
		private Set<String> pathClasses;
		private boolean baseClass = false;
		
		private PathObjectClassPredicate(boolean baseClass, PathClass... pathClasses) {
			this.baseClass = baseClass;
			this.pathClasses = new LinkedHashSet<>();
			for (var pc : pathClasses) {
				if (pc == null)
					this.pathClasses.add(null);
				else
					this.pathClasses.add(pc.toString());
			}
		}

		@Override
		public boolean test(PathObject t) {
			PathClass pathClass = t.getPathClass();
			if (baseClass)
				pathClass = pathClass == null ? null : pathClass.getBaseClass();
			if (pathClass == null)
				return pathClasses.contains(null);
			else
				return pathClasses.contains(pathClass.toString());
		}
		
	}
	
	static class PathObjectAndPredicate extends AbstractPathObjectPredicate {
		
		private PathObjectPredicate predicate1;
		private PathObjectPredicate predicate2;
		
		private PathObjectAndPredicate(PathObjectPredicate p1, PathObjectPredicate p2) {
			this.predicate1 = p1;
			this.predicate2 = p2;
		}
		
		@Override
		public boolean test(PathObject t) {
			return predicate1.test(t) && predicate2.test(t);
		}
		
	}
	
	static class PathObjectOrPredicate extends AbstractPathObjectPredicate {
		
		private PathObjectPredicate predicate1;
		private PathObjectPredicate predicate2;
		
		private PathObjectOrPredicate(PathObjectPredicate p1, PathObjectPredicate p2) {
			this.predicate1 = p1;
			this.predicate2 = p2;
		}
		
		@Override
		public boolean test(PathObject t) {
			return predicate1.test(t) || predicate2.test(t);
		}
		
	}
	
	static class PathObjectNegatePredicate extends AbstractPathObjectPredicate {
		
		private PathObjectPredicate predicate;
		
		private PathObjectNegatePredicate(PathObjectPredicate p) {
			this.predicate = p;
		}
		
		@Override
		public boolean test(PathObject t) {
			return !predicate.test(t);
		}
		
	}
	
	public static PathObjectPredicate positiveClassification(boolean allowGradedIntensity) {
		return new PathObjectClassPositivePredicate(allowGradedIntensity);
	}
	
	public static PathObjectPredicate filter(PathObjectFilter filter) {
		return new PathObjectFilterPredicate(filter);
	}
	
	public static PathObjectPredicate exactClassification(PathClass... pathClasses) {
		if (pathClasses.length == 0)
			throw new IllegalArgumentException("No PathClasses specified!");
		return new PathObjectClassPredicate(false, pathClasses);
	}
	
	public static PathObjectPredicate containsClassification(String... names) {
		if (names.length == 0)
			throw new IllegalArgumentException("No PathClasses specified!");
		return new PathObjectClassNamePredicate(names);
	}
	
	public static PathObjectPredicate baseClassification(PathClass... pathClasses) {
		if (pathClasses.length == 0)
			throw new IllegalArgumentException("No PathClasses specified!");
		return new PathObjectClassPredicate(true, pathClasses);
	}
	

}
