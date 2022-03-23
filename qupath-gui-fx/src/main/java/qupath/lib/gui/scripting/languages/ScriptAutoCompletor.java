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

import java.util.Collections;
import java.util.Map;

import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Interface for classes that implement auto-completion (e.g. styling classes).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public interface ScriptAutoCompletor {
	
	/**
	 * Try to match and auto-complete a method name.
	 * @param control the control onto which apply auto-completion.
	 * @return 
	 */
	default Map<String, String> getCompletions(ScriptEditorControl control) {
		return Collections.emptyMap();
	}
	
	/**
	 * Apply a specified completion to the provided control.
	 * This involves figuring out which text to insert and which is already present, so that the completion 
	 * behaves properly.
	 * @param control
	 * @param completion
	 */
	default void applyCompletion(ScriptEditorControl control, String completion) {
		throw new UnsupportedOperationException("Completions not supported!");
	}
	
	// May be required to provide more extensive completion
//	static class Completion {
//		
//		private String displayText;
//		private String completionText;
//		
//		public String getDisplayText() {
//			return displayText;
//		}
//		
//		public String getCompletionText() {
//			return completionText;
//		}
//		
//	}
	
}
