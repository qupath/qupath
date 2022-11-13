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

package qupath.lib.scripting.languages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Class to deal with script auto-completions.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class AutoCompletions {
	
	private static final Comparator<Completion> COMPARATOR = 
			Comparator.comparing(Completion::getCompletionText)
				.thenComparing(Completion::getDisplayText);

	
	/**
	 * Get a comparator to order completions.
	 * @return
	 */
	public static Comparator<Completion> getComparator() {
		return COMPARATOR;
	}
	
	/**
	 * Functional interface to extract a token from a string needed to determine 
	 * a completion.
	 * For example, given the string {@code var pathObject = PathObjects.crea} 
	 * the token would be {@code "PathObjects.crea"}.
	 */
	public static interface CompletionTokenizer {
		
		/**
		 * Get the token needed for the completion.
		 * @param text
		 * @param pos
		 * @return
		 */
		public String getToken(String text, int pos);
		
	}
	
	/**
	 * A completion tokenizer that simply takes the first part of the text up to the caret position.
	 */
	public static final CompletionTokenizer SUBSTRING_TOKENIZER = AutoCompletions::getSubstringToken;
	
	/**
	 * A completion tokenizer that extracts a token used to determine Java completions.
	 * 
	 */
	public static final CompletionTokenizer JAVA_TOKENIZER = AutoCompletions::getLastJavaToken;
	
	
	private static String getSubstringToken(String text, int lastPos) {
		return text.substring(0, lastPos);
	}
	
	
	private static String getLastJavaToken(String text, int pos) {
		String[] split = text.substring(0, pos).split("(\\s+)|(\\()|(\\))|(\\{)|(\\})|(\\[)|(\\])");
		String start;
		if (split.length == 0)
			start = "";
		else
			start = split[split.length-1].trim();
		return start;
	}
	
	/**
	 * A single completion.
	 * Instances must be able to determine whether they can provide a valid autocompletion, 
	 * given an input string and a caret position - and, if so, also supply the completion text to insert.
	 */
	public static interface Completion {
		
		/**
		 * Get the text that should be inserted for the full completion.
		 * @return
		 */
		public String getCompletionText();
		
		/**
		 * Get the text that should be displayed for this completion.
		 * This which may include additional information that isn't part of the completion itself
		 * (e.g. method parameters, return type).
		 * @return
		 */
		public String getDisplayText();
		
		/**
		 * Get the string to insert, given the provided text and position.
		 * This involves stripping off any overlapping part of the completion, 
		 * so that it can be inserted at pos.
		 * 
		 * @param text the full text
		 * @param pos the current caret position
		 * @param lastToken the final token, as output by {@link #getTokenizer()}.
		 *                  If null, the token will be calculated - but it can improve performance to precompute 
		 *                  tokens whenever multiple completions use the same way of determining tokens.
		 * @return the text to insert
		 */
		public String getInsertion(String text, int pos, String lastToken);
		
		/**
		 * Test if this completion is compatible with the provided text.
		 * 
		 * @param text the full text
		 * @param pos the current caret position
		 * @param lastToken the final token, as output by {@link #getTokenizer()}.
		 *                  If null, the token will be calculated - but it can improve performance to precompute 
		 *                  tokens whenever multiple completions use the same way of determining tokens.
		 * @return
		 */
		public boolean isCompatible(String text, int pos, String lastToken);
		
		/**
		 * Get the tokenizer needed to extract the relevant bit of the text to determine the validity and/or 
		 * insertion for the completion.
		 * <p>
		 * <b>Important!</b> This exists for efficiency, so that if many completions use the same tokenizer, 
		 * the (possibly long) text does not need to be re-tokenized each time.
		 * It is therefore important to return a shared instance, rather than a new object for each completion.
		 * 
		 * @return the completion tokenizer, which must not be null
		 * 
		 * @see AutoCompletions#SUBSTRING_TOKENIZER
		 * @see AutoCompletions#JAVA_TOKENIZER
		 */
		public default CompletionTokenizer getTokenizer() {
			return SUBSTRING_TOKENIZER;
		}
		
	}
	
	
	/**
	 * Create a new completion with fixed display and completion text.
	 * @param declaringClass the declaring class; choose null for static imports
	 * @param displayText the text to display
	 * @param completionText the text to use in the completion
	 * @return
	 */
	public static Completion createJavaCompletion(Class<?> declaringClass, String displayText, String completionText) {
		return new JavaCompletion(declaringClass, displayText, completionText);
	}
		
	/**
	 * Create a new completion for a class.
	 * @param cls the class to complete
	 * @return
	 */
	public static Completion createJavaCompletion(Class<?> cls) {
		return new JavaCompletion(cls, cls.getSimpleName(), cls.getSimpleName() + ".");
	}
	
	/**
	 * Create a new completion for a field.
	 * @param declaringClass the parent class; choose null for static imports
	 * @param field the field
	 * @return
	 */
	public static Completion createJavaCompletion(Class<?> declaringClass, Field field) {
		return createJavaCompletion(
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
	public static Completion createJavaCompletion(Class<?> declaringClass, Method method) {
		return createJavaCompletion(
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
	

	
	
	
	/**
	 * Autocompletion suggestion for Java code
	 */
	static class JavaCompletion implements Completion {
				
		private Class<?> parentClass;
		private String displayText;
		private String completionText;
		
		private JavaCompletion(Class<?> parentClass, String displayText, String completionText) {
			this.parentClass = parentClass;
			this.displayText = displayText;
			this.completionText = completionText;
		}
		
		@Override
		public String getDisplayText() {
			return displayText;
		}
		
		@Override
		public String getCompletionText() {
			return completionText;
		}
		
		@Override
		public String getInsertion(String fullText, int pos, String token) {
			if (token == null)
				token = getTokenizer().getToken(fullText, pos);
			
			// Easy approach (fails with classes)
			var completionText = getCompletionText();
			if (parentClass != null && !completionText.contains(".")) {
				// We could use the parent class name, but *possibly* 
				// we have used an alias during import
				int lastDot = token.lastIndexOf(".");
				if (lastDot >= 0 && lastDot != token.length()-1) {
					token = token.substring(lastDot+1);
				}
			}
			var insertion = completionText.startsWith(token) ? completionText.substring(token.length()) : completionText;// + "(";
			return insertion;
		}
		
		@Override
		public boolean isCompatible(String fullText, int pos, String token) {
			if (token == null)
				token = getTokenizer().getToken(fullText, pos);

			if (parentClass != null) {
				int ind = token.lastIndexOf(".");
				if (ind <= 0) {
					return (parentClass.getName().startsWith(token) || parentClass.getSimpleName().startsWith(token)) &&
						completionText.startsWith(token);
				}
//				if (ind == text.length()) {
//					text = text.substring(0, ind);
//					return parentClass.getName().equals(text) || parentClass.getSimpleName().equals(text);					
//				}
				String first = token.substring(0, ind);
				String second = token.substring(ind+1);
				if (parentClass.getName().equals(first) || parentClass.getSimpleName().equals(first))
					return completionText.startsWith(second);
				else
					return false;
			}
			return completionText.startsWith(token);
		}
		
		@Override
		public CompletionTokenizer getTokenizer() {
			return JAVA_TOKENIZER;
		}

		@Override
		public int hashCode() {
			return Objects.hash(completionText, displayText, parentClass);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			JavaCompletion other = (JavaCompletion) obj;
			return Objects.equals(completionText, other.completionText)
					&& Objects.equals(displayText, other.displayText) && Objects.equals(parentClass, other.parentClass);
		}
						
	}
	

}
