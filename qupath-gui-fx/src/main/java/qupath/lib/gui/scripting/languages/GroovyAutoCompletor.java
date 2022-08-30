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

package qupath.lib.gui.scripting.languages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Auto-completor for Groovy code.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class GroovyAutoCompletor implements ScriptAutoCompletor {
	
	private static final Logger logger = LoggerFactory.getLogger(GroovyAutoCompletor.class);
	
	private static final Set<Completion> ALL_COMPLETIONS = new HashSet<>();
	
	
	static {
		
		for (Method method : QPEx.class.getMethods()) {
			// Exclude deprecated methods (don't want to encourage them...)
			if (method.getAnnotation(Deprecated.class) == null) {
				ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(method.getDeclaringClass(), method));
				ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(null, method));
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
				ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(field.getDeclaringClass(), field));
				ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(null, field));
			}
		}
		
		Set<Class<?>> classesToAdd = new HashSet<>(QPEx.getCoreClasses());

		for (Class<?> cls : classesToAdd) {
			addStaticMethods(cls);
		}
		
		ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(null, "print", "print"));
		ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(null, "println", "println"));
	}
	
	static int addStaticMethods(Class<?> cls) {
		int countStatic = 0;
		for (Method method : cls.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
				if (ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(method.getDeclaringClass(), method)))
					countStatic++;
			}
		}
		if (countStatic > 0) {
			ALL_COMPLETIONS.add(ScriptAutoCompletor.Completion.create(cls));
		}
		return countStatic;
	}
	
	
	
	/**
	 * Empty constructor.
	 */
	public GroovyAutoCompletor() {
		// Empty constructor
	}
	
	@Override
	public List<Completion> getCompletions(ScriptEditorControl control) {
		var start = getStart(control);
		
		// Use all available completions if we have a dot included
		List<Completion> completions;
		if (control.getText().contains("."))
			completions = ALL_COMPLETIONS
					.stream()
					.filter(e -> e.isCompatible(start))
//					.sorted()
					.collect(Collectors.toList());
		else
			// Use only partial completions (methods, classes) if no dot
			completions = ALL_COMPLETIONS
			.stream()
			.filter(s -> s.isCompatible(start) && (!s.getCompletionText().contains(".") || s.getCompletionText().lastIndexOf(".") == s.getCompletionText().length()-1))
//			.sorted()
			.collect(Collectors.toList());
		
		// Add a new class if needed
		// (Note this doesn't entirely work... since it doesn't handle the full class name later)
		if (completions.isEmpty() && start.contains(".")) {
			var className = start.substring(0, start.lastIndexOf("."));
			try {
				var cls = Class.forName(className);
				if (addStaticMethods(cls) > 0) {
					return getCompletions(control);
				}
			} catch (Exception e) {
				logger.debug("Unable to find autocomplete methods for class {}", className);
			}
		}
		
		return completions;
	}
	
	@Override
	public void applyCompletion(ScriptEditorControl control, Completion completion) {
		var start = getStart(control);
		var insertion = completion.getInsertion(start);
		// Avoid inserting if caret is already between parentheses
		if (insertion == null || insertion.isEmpty() || insertion.startsWith("("))
			return;
		var pos = control.getCaretPosition();
		control.insertText(pos, insertion);
		// If we have a method that includes arguments, 
		// then we want to position the caret within the parentheses
		// (whereas for a method without arguments, we want the caret outside)
		if (insertion.endsWith("()") && control.getCaretPosition() > 0 && !completion.getDisplayText().endsWith("()"))
			control.positionCaret(control.getCaretPosition()-1);
	}
	
	private String getStart(ScriptEditorControl control) {
		var pos = control.getCaretPosition();
		String[] split = control.getText().substring(0, pos).split("(\\s+)|(\\()|(\\))|(\\{)|(\\})|(\\[)|(\\])");
		String start;
		if (split.length == 0)
			start = "";
		else
			start = split[split.length-1].trim();
		return start;
	}

}
