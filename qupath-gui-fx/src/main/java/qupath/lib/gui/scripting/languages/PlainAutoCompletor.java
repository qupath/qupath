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

import java.util.Collections;
import java.util.List;

import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Auto-completor for plain text (which means no auto-complete).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class PlainAutoCompletor implements ScriptAutoCompletor {
	
	@Override
	public List<Completion> getCompletions(ScriptEditorControl control) {
		return Collections.emptyList();
	}

}
