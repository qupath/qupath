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

package qupath.lib.gui.scripting.autocompletors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.scene.input.KeyEvent;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.scripting.QP;

/**
 * Auto-completor for Groovy code.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class GroovyAutoCompletor implements ScriptAutoCompletor {
	
	private static final Set<String> METHOD_NAMES = new HashSet<>();
	private final ScriptEditorControl control;
	private List<String> completions = new ArrayList<>();
	private int idx = 0;
	private Integer pos = null;
	private String start; // Starting text
	private String lastInsertion = null;
	
	
	static {
		for (Method method : QPEx.class.getMethods()) {
			// Exclude deprecated methods (don't want to encourage them...)
			if (method.getAnnotation(Deprecated.class) == null)
				METHOD_NAMES.add(method.getName());
		}
		
		// Remove the methods that come from the Object class...
		// they tend to be quite confusing
		for (Method method : Object.class.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
				METHOD_NAMES.remove(method.getName());
		}
		
		for (Field field : QPEx.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
				METHOD_NAMES.add(field.getName());
		}
		
		for (Class<?> cls : QP.getCoreClasses()) {
			int countStatic = 0;
			for (Method method : cls.getMethods()) {
				if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
					METHOD_NAMES.add(cls.getSimpleName() + "." + method.getName());
					countStatic++;
				}
			}
			if (countStatic > 0)
				METHOD_NAMES.add(cls.getSimpleName() + ".");
		}
		
//		for (Method method : ImageData.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathObjectHierarchy.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : TMACoreObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathCellObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathClassFactory.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
		METHOD_NAMES.add("print");
		METHOD_NAMES.add("println");
	}
	
	/**
	 * Constructor for Groovy auto-completor.
	 * @param control the script editor control onto which apply auto-completion
	 */
	public GroovyAutoCompletor(ScriptEditorControl control) {
		this.control = control;
	}
	
	@Override
	public void applyNextCompletion() {
		if (pos == null) {
			pos = control.getCaretPosition();
			String[] split = control.getText().substring(0, pos).split("(\\s+)|(\\()|(\\))|(\\{)|(\\})|(\\[)|(\\])");
			if (split.length == 0)
				start = "";
			else
				start = split[split.length-1].trim();
		}
		
		// Use all available completions if we have a dot included
		if (control.getText().contains("."))
			completions = METHOD_NAMES.stream()
					.filter(s -> s.startsWith(start))
					.sorted()
					.collect(Collectors.toList());
		else
			// Use only partial completions (methods, classes) if no dot
			completions = METHOD_NAMES.stream()
			.filter(s -> s.startsWith(start) && (!s.contains(".") || s.lastIndexOf(".") == s.length()-1))
			.sorted()
			.collect(Collectors.toList());
				
		if (completions.isEmpty())
			return;
		if (completions.size() == 0 && lastInsertion != null)
			return;
		if (lastInsertion != null && lastInsertion.length() > 0)
			control.deleteText(pos, pos + lastInsertion.length());
		lastInsertion = completions.get(idx).substring(start.length());// + "(";
		control.insertText(pos, lastInsertion);
		idx++;
		idx = idx % completions.size();
	}

	@Override
	public void resetCompletion(KeyEvent e) {
		if (!defaultCompletionCode.match(e)) {
			// Users usually type the CTRL key before the SPACE key, but we don't want to reset in that case
			if (!e.isControlDown()) {
				pos = null;
				lastInsertion = null;
				idx = 0;
			}
			return;
		}
	}
}
