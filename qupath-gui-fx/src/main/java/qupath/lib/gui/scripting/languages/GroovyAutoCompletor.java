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
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.scripting.QP;

/**
 * Auto-completor for Groovy code.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class GroovyAutoCompletor implements ScriptAutoCompletor {
	
	private final static Logger logger = LoggerFactory.getLogger(GroovyAutoCompletor.class);
	
	private static final Map<String, String> ALL_COMPLETIONS = new HashMap<>();
	
	
	static {
		for (Method method : QPEx.class.getMethods()) {
			// Exclude deprecated methods (don't want to encourage them...)
			if (method.getAnnotation(Deprecated.class) == null)
				ALL_COMPLETIONS.put(getMethodString(method, null), getMethodName(method));
		}
		
		// Remove the methods that come from the Object class...
		// they tend to be quite confusing
		for (Method method : Object.class.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
				ALL_COMPLETIONS.remove(getMethodString(method, null));
		}
		
		for (Field field : QPEx.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
				ALL_COMPLETIONS.put(field.getName(), field.getName());
			}
		}
		
		Set<Class<?>> classesToAdd = new HashSet<>(QP.getCoreClasses());

		for (Class<?> cls : classesToAdd) {
			addStaticMethods(cls);
		}
		
		ALL_COMPLETIONS.put("print", "print");
		ALL_COMPLETIONS.put("println", "println");
	}
	
	static int addStaticMethods(Class<?> cls) {
		int countStatic = 0;
		for (Method method : cls.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
				String prefix = cls.getSimpleName() + ".";
				if (ALL_COMPLETIONS.put(
						getMethodString(method, prefix),
						prefix + getMethodName(method)) == null)
					countStatic++;
			}
		}
		if (countStatic > 0) {
			var text = cls.getSimpleName() + ".";
			ALL_COMPLETIONS.put(text, text);
		}
		return countStatic;
	}
	
	static String getMethodName(Method method) {
		return method.getName() + "(" + ")";
	}
		
	static String getMethodString(Method method, String prefix) {
//		return method.getName();
		var sb = new StringBuilder();
//		sb.append(method.getReturnType().getSimpleName());
//		sb.append(" ");
		if (prefix != null)
			sb.append(prefix);
		sb.append(method.getName());
		sb.append("(");
		sb.append(Arrays.stream(method.getParameters()).map(p -> getParameterString(p)).collect(Collectors.joining(", ")));
		sb.append(")");
		return sb.toString();
	}
	
	private static String getParameterString(Parameter parameter) {
		if (parameter.isNamePresent())
			return parameter.getType().getSimpleName() + " " + parameter.getName();
		return parameter.getType().getSimpleName();
	}
	
	
	/**
	 * Empty constructor.
	 */
	public GroovyAutoCompletor() {
		// Empty constructor
	}
	
	@Override
	public Map<String, String> getCompletions(ScriptEditorControl control) {
		var start = getStart(control);
		
		// Use all available completions if we have a dot included
		Map<String, String> completions;
		if (control.getText().contains("."))
			completions = ALL_COMPLETIONS.entrySet()
					.stream()
					.filter(e -> e.getKey().startsWith(start) && !e.getKey().equals(start)) // Don't include start itself, since it looks like we have no completions
//					.sorted()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		else
			// Use only partial completions (methods, classes) if no dot
			completions = ALL_COMPLETIONS.entrySet()
			.stream()
			.filter(s -> s.getKey().startsWith(start) && (!s.getKey().contains(".") || s.getKey().lastIndexOf(".") == s.getKey().length()-1))
//			.sorted()
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		
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
	public void applyCompletion(ScriptEditorControl control, String completion) {
		var start = getStart(control);
		var insertion = completion.substring(start.length());// + "(";
		// Avoid adding if caret is already between parentheses
		if (insertion.startsWith("("))
			return;
		var pos = control.getCaretPosition();
		control.insertText(pos, insertion);
		if (insertion.endsWith(")") && control.getCaretPosition() > 0)
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
