/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Interface for classes that implement auto-completion (e.g. styling classes).
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public interface ScriptAutoCompletor {
	
	/**
	 * Try to match and auto-complete a method name.
	 * @param control the control onto which apply auto-completion.
	 * @return 
	 */
	default List<Completion> getCompletions(ScriptEditorControl control) {
		return Collections.emptyList();
	}
	
	/**
	 * Apply a specified completion to the provided control.
	 * This involves figuring out which text to insert and which is already present, so that the completion 
	 * behaves properly.
	 * @param control
	 * @param completion
	 */
	default void applyCompletion(ScriptEditorControl control, Completion completion) {
		throw new UnsupportedOperationException("Completions not supported!");
	}
	
	/**
	 * Autocompletion suggestion.
	 */
	static class Completion implements Comparable<Completion> {
		
		private static final Comparator<Completion> COMPARATOR = 
				Comparator.comparing(Completion::getCompletionText)
					.thenComparing(Completion::getDisplayText);
		
		private Class<?> parentClass;
		private String displayText;
		private String completionText;
		
		private Completion(Class<?> parentClass, String displayText, String completionText) {
			this.parentClass = parentClass;
			this.displayText = displayText;
			this.completionText = completionText;
		}
		
		/**
		 * Get the text that should be displayed for this completion.
		 * This which may include additional information that isn't part of the completion itself
		 * (e.g. method parameters, return type).
		 * @return
		 */
		public String getDisplayText() {
			return displayText;
		}
		
		/**
		 * Get the text that should be added for the completion.
		 * @return
		 */
		public String getCompletionText() {
			return completionText;
		}
		
		/**
		 * Get the string to insert, given the provided starting text.
		 * This involves stripping off any overlapping part of the completion, 
		 * so that it can be appended to the startText.
		 * @param startText existing text
		 * @return the text to insert
		 */
		public String getInsertion(String startText) {
			// Easy approach (fails with classes)
			var completionText = getCompletionText();
			if (parentClass != null && !completionText.contains(".")) {
				// We could use the parent class name, but *possibly* 
				// we have used an alias during import
				int lastDot = startText.lastIndexOf(".");
				if (lastDot >= 0 && lastDot != startText.length()-1) {
					startText = startText.substring(lastDot+1);
				}
			}
			var insertion = completionText.startsWith(startText) ? completionText.substring(startText.length()) : completionText;// + "(";
			return insertion;
		}
		
		/**
		 * Test if this completion is compatible with the provided text.
		 * @param text
		 * @return
		 */
		public boolean isCompatible(String text) {
			if (parentClass != null) {
				int ind = text.lastIndexOf(".");
				if (ind <= 0) {
					return (parentClass.getName().startsWith(text) || parentClass.getSimpleName().startsWith(text)) &&
						completionText.startsWith(text);
				}
//				if (ind == text.length()) {
//					text = text.substring(0, ind);
//					return parentClass.getName().equals(text) || parentClass.getSimpleName().equals(text);					
//				}
				String first = text.substring(0, ind);
				String second = text.substring(ind+1);
				if (parentClass.getName().equals(first) || parentClass.getSimpleName().equals(first))
					return completionText.startsWith(second);
				else
					return false;
			}
			return completionText.startsWith(text);
		}
		
		/**
		 * Create a new completion with fixed text.
		 * @param declaringClass the declaring class; choose null for static imports
		 * @param displayText the text to display
		 * @param completionText the text to use in the completion
		 * @return
		 */
		public static Completion create(Class<?> declaringClass, String displayText, String completionText) {
			return new Completion(declaringClass, displayText, completionText);
		}
		
		/**
		 * Create a new completion for a class.
		 * @param cls the class to complete
		 * @return
		 */
		public static Completion create(Class<?> cls) {
			return new Completion(cls, cls.getSimpleName(), cls.getSimpleName() + ".");
		}
		
		/**
		 * Create a new completion for a field.
		 * @param declaringClass the parent class; choose null for static imports
		 * @param field the field
		 * @return
		 */
		public static Completion create(Class<?> declaringClass, Field field) {
			return create(
					declaringClass,
					field.getType().getSimpleName() + " " + field.getName(),
					field.getName()
					);
		}
		
		/**
		 * Create a new completion for a method.
		 * @param declaringClass the parent class; choose null for static imports
		 * @param method the method
		 * @return
		 */
		public static Completion create(Class<?> declaringClass, Method method) {
			return create(
					declaringClass,
					getMethodString(method),
					getMethodName(method)
					);
		}
		
		private static String getMethodName(Method method) {
			return method.getName() + "(" + ")";
		}
			
		private static String getMethodString(Method method) {
			var sb = new StringBuilder();
			sb.append(method.getReturnType().getSimpleName());
			sb.append(" ");
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

		@Override
		public int compareTo(Completion other) {
			return COMPARATOR.compare(this, other);
		}

		
	}
	
}
