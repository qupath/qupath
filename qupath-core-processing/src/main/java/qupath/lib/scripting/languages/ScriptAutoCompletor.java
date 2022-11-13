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

import java.util.List;
import qupath.lib.scripting.languages.AutoCompletions.Completion;

/**
 * Interface for classes that implement auto-completion (e.g. styling classes).
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public interface ScriptAutoCompletor {
	
	/**
	 * Get a list of possible auto-completions for the given text 
	 * with the caret at the specified position.
	 * @param text the full text
	 * @param pos the current caret position
	 * @return 
	 */
	public List<Completion> getCompletions(String text, int pos);
	
}
