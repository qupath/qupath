/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui.scripting.completors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.scripting.QPEx;
import qupath.lib.scripting.languages.AutoCompletions;
import qupath.lib.scripting.languages.AutoCompletions.Completion;
import qupath.lib.scripting.languages.ScriptAutoCompletor;

/**
 * Default auto-completor for JVM-based languages, optionally including QuPath default imports.
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class DefaultAutoCompletor implements ScriptAutoCompletor {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultAutoCompletor.class);
	
	private static final Set<Completion> DEFAULT_QUPATH_JAVA_COMPLETIONS = new HashSet<>();
	
	static {
		
		for (Method method : QPEx.class.getMethods()) {
			// Exclude deprecated methods (don't want to encourage them...)
			if (method.getAnnotation(Deprecated.class) == null) {
				DEFAULT_QUPATH_JAVA_COMPLETIONS.add(AutoCompletions.createJavaCompletion(method.getDeclaringClass(), method));
				DEFAULT_QUPATH_JAVA_COMPLETIONS.add(AutoCompletions.createJavaCompletion(null, method));
			}
		}
		
//		// Remove the methods that come from the Object class...
//		// they tend to be quite confusing
//		for (Method method : Object.class.getMethods()) {
//			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
//				ALL_COMPLETIONS.remove(getMethodString(method, null));
//		}
		
		for (Field field : QPEx.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
				DEFAULT_QUPATH_JAVA_COMPLETIONS.add(AutoCompletions.createJavaCompletion(field.getDeclaringClass(), field));
				DEFAULT_QUPATH_JAVA_COMPLETIONS.add(AutoCompletions.createJavaCompletion(null, field));
			}
		}
		
		Set<Class<?>> classesToAdd = new HashSet<>(QPEx.getCoreClasses());

		for (Class<?> cls : classesToAdd) {
			addStaticMethods(cls, DEFAULT_QUPATH_JAVA_COMPLETIONS);
		}
		
	}
	
	static int addStaticMethods(Class<?> cls, Collection<? super Completion> completions) {
		int countStatic = 0;
		for (Method method : cls.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
				if (completions.add(AutoCompletions.createJavaCompletion(method.getDeclaringClass(), method)))
					countStatic++;
			}
		}
		if (countStatic > 0) {
			completions.add(AutoCompletions.createJavaCompletion(cls));
		}
		return countStatic;
	}
	
	
	private final Set<Completion> allCompletions = new HashSet<>();
	
	/**
	 * Constructor.
	 * @param addQuPathCompletions if true, add standard Java completions for core QuPath classes.
	 */
	public DefaultAutoCompletor(boolean addQuPathCompletions) {
		if (addQuPathCompletions)
			allCompletions.addAll(DEFAULT_QUPATH_JAVA_COMPLETIONS);
	}
	
	protected void addCompletion(Completion completion) {
		allCompletions.add(completion);		
	}

	protected void addCompletions(Completion... completions) {
		for (var c : completions)
			addCompletion(c);
	}
	
	protected void addCompletions(Collection<? extends Completion> completions) {
		for (var c : completions)
			addCompletion(c);
	}

	
	@Override
	public List<Completion> getCompletions(String text, int pos) {
		// Precompute all tokens
		var tokenMap = allCompletions.stream()
				.map(c -> c.getTokenizer())
				.distinct()
				.collect(Collectors.toMap(c -> c, c -> c.getToken(text, pos)));
		
		// Use all available completions if we have a dot included
		List<Completion> completions;
		if (text.contains("."))
			completions = allCompletions
					.stream()
					.filter(e -> e.isCompatible(text, pos, tokenMap.getOrDefault(e, null)))
//					.sorted()
					.collect(Collectors.toList());
		else
			// Use only partial completions (methods, classes) if no dot
			completions = allCompletions
			.stream()
			.filter(s -> s.isCompatible(text, pos, tokenMap.getOrDefault(s, null)) && (!s.getCompletionText().contains(".") || s.getCompletionText().lastIndexOf(".") == s.getCompletionText().length()-1))
//			.sorted()
			.collect(Collectors.toList());
		
		// Add a new class if needed
		// (Note this doesn't entirely work... since it doesn't handle the full class name later)
		var start = AutoCompletions.JAVA_TOKENIZER.getToken(text, pos);
		if (completions.isEmpty() && start.contains(".")) {
			var className = start.substring(0, start.lastIndexOf("."));
			try {
				var cls = Class.forName(className);
				if (addStaticMethods(cls, allCompletions) > 0) {
					return getCompletions(text, pos);
				}
			} catch (Exception e) {
				logger.debug("Unable to find autocomplete methods for class {}", className);
			}
		}
		
		return completions;
	}

}
